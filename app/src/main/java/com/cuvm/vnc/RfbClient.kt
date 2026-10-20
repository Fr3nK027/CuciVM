package com.cuvm.vnc

import android.graphics.Bitmap
import com.cuvm.BuildConfig
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import java.util.concurrent.RejectedExecutionException

/**
 * Minimal RFB 3.8 client (RFC 6143): security None, encodings Raw, CopyRect, DesktopSize.
 * shortcut: no ZRLE/Tight yet, fine on localhost; add ZRLE (java.util.zip.Inflater) if frames lag.
 */
class RfbClient(
    private val host: String,
    private val port: Int,
    private val maxFps: Int,
    private val onFrame: (Bitmap) -> Unit,
    private val onCutText: (String) -> Unit,
    private val onClosed: (Throwable?) -> Unit,
) {
    private val socket = Socket()
    private lateinit var input: DataInputStream
    private lateinit var output: DataOutputStream
    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var closed = false
    /** Input events are only sent once the handshake is done (before that the size is 0 and the stream is mid-handshake). */
    @Volatile private var ready = false

    var width = 0; private set
    var height = 0; private set
    private var fb = ByteArray(0) // RGBX rows, the exact memory layout of an opaque ARGB_8888 Bitmap
    // Double buffer: the UI draws one bitmap while the next frame is copied into the other.
    private val bitmaps = arrayOfNulls<Bitmap>(2)
    private var back = 0

    fun connect() {
        Thread({
            try {
                socket.tcpNoDelay = true
                socket.connect(java.net.InetSocketAddress(host, port), 5000)
                input = DataInputStream(BufferedInputStream(socket.getInputStream(), 1 shl 16))
                output = DataOutputStream(BufferedOutputStream(socket.getOutputStream()))
                synchronized(this) { handshake() }
                ready = true
                loop()
            } catch (e: Throwable) {
                if (!closed) onClosed(e)
            } finally {
                close()
            }
        }, "rfb-$port").start()
    }

    private fun handshake() {
        val version = ByteArray(12).also { input.readFully(it) }
        if (!String(version).startsWith("RFB ")) throw IOException("not an RFB server")
        output.write("RFB 003.008\n".toByteArray()); output.flush()
        val n = input.readUnsignedByte()
        if (n == 0) throw IOException(readString())
        val types = ByteArray(n).also { input.readFully(it) }
        if (1.toByte() !in types) throw IOException("server needs a password (only None supported)")
        output.writeByte(1); output.flush()
        if (input.readInt() != 0) throw IOException(readString())
        output.writeByte(1); output.flush() // ClientInit: shared
        resize(input.readUnsignedShort(), input.readUnsignedShort())
        input.skipBytes(16) // server pixel format; we set our own
        input.skipBytes(input.readInt()) // desktop name

        // SetPixelFormat: 32bpp, depth 24, little endian, R at bit 0, G at 8, B at 16 -> bytes R,G,B,X:
        // what Android keeps in an ARGB_8888 Bitmap, so frames are copied in bulk with no per-pixel work.
        output.writeByte(0); output.write(ByteArray(3))
        output.writeByte(32); output.writeByte(24); output.writeByte(0); output.writeByte(1)
        output.writeShort(255); output.writeShort(255); output.writeShort(255)
        output.writeByte(0); output.writeByte(8); output.writeByte(16); output.write(ByteArray(3))
        // SetEncodings: CopyRect, Raw, DesktopSize
        val encs = intArrayOf(1, 0, -223)
        output.writeByte(2); output.writeByte(0); output.writeShort(encs.size)
        encs.forEach(output::writeInt)
        requestUpdate(incremental = false)
    }

    private fun readString(): String = ByteArray(input.readInt()).also { input.readFully(it) }.toString(Charsets.ISO_8859_1)

    private fun resize(w: Int, h: Int) {
        if (w !in 1..8192 || h !in 1..8192) throw IOException("bad desktop size ${w}x$h")
        width = w; height = h
        fb = ByteArray(w * h * 4)
        // X byte is 0: mark opaque so it is never treated as transparency.
        for (i in bitmaps.indices) bitmaps[i] = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).apply { setHasAlpha(false) }
    }

    private fun requestUpdate(incremental: Boolean) {
        output.writeByte(3); output.writeByte(if (incremental) 1 else 0)
        output.writeShort(0); output.writeShort(0); output.writeShort(width); output.writeShort(height)
        output.flush()
    }

    private fun loop() {
        while (!closed) {
            when (val type = input.readUnsignedByte()) {
                0 -> {
                    val started = System.currentTimeMillis()
                    // Pipelined: ask for the next frame now, the server builds it while this one is read.
                    // Frame pacing is the server's job (Xvnc -FrameRate).
                    synchronized(this) { requestUpdate(incremental = true) }
                    input.readByte()
                    repeat(input.readUnsignedShort()) {
                        val x = input.readUnsignedShort(); val y = input.readUnsignedShort()
                        val w = input.readUnsignedShort(); val h = input.readUnsignedShort()
                        val enc = input.readInt()
                        // A rect outside the framebuffer would crash or wrap into the next row.
                        if (enc != -223 && (x + w > width || y + h > height)) throw IOException("rect ${w}x$h+$x+$y outside ${width}x$height")
                        when (enc) {
                            0 -> {
                                val len = w * 4
                                if (x == 0 && w == width) input.readFully(fb, y * len, h * len) // full-width: one read
                                else for (row in 0 until h) input.readFully(fb, ((y + row) * width + x) * 4, len)
                            }
                            1 -> {
                                val sx = input.readUnsignedShort(); val sy = input.readUnsignedShort()
                                if (sx + w > width || sy + h > height) throw IOException("copyrect source outside framebuffer")
                                val len = w * 4
                                val tmp = ByteArray(len * h)
                                for (row in 0 until h) System.arraycopy(fb, ((sy + row) * width + sx) * 4, tmp, row * len, len)
                                for (row in 0 until h) System.arraycopy(tmp, row * len, fb, ((y + row) * width + x) * 4, len)
                            }
                            -223 -> resize(w, h)
                            else -> throw IOException("unexpected encoding $enc")
                        }
                    }
                    val bmp = bitmaps[back]!!
                    bmp.copyPixelsFromBuffer(ByteBuffer.wrap(fb))
                    onFrame(bmp)
                    back = 1 - back
                    if (BuildConfig.DEBUG) stats(System.currentTimeMillis() - started)
                }
                1 -> { input.readByte(); input.readUnsignedShort(); input.skipBytes(input.readUnsignedShort() * 6) }
                2 -> Unit // bell
                3 -> { input.skipBytes(3); onCutText(readString()) }
                else -> throw IOException("unexpected message $type")
            }
        }
    }

    // Debug builds: frames/s, decode+copy time and bytes/s every 5 s (logcat tag CuciVmRfb).
    private var statFrames = 0; private var statMs = 0L; private var statSince = System.currentTimeMillis()
    private fun stats(ms: Long) {
        statFrames++; statMs += ms
        val now = System.currentTimeMillis()
        if (now - statSince >= 5000) {
            android.util.Log.d("CuciVmRfb", "fps=%.1f decode=%.1fms %dx%d".format(statFrames * 1000f / (now - statSince), statMs.toFloat() / statFrames, width, height))
            statFrames = 0; statMs = 0; statSince = now
        }
    }

    private fun send(block: DataOutputStream.() -> Unit) {
        if (closed || !ready) return
        try {
            writer.execute {
                try { synchronized(this) { output.block(); output.flush() } } catch (_: Exception) { }
            }
        } catch (_: RejectedExecutionException) {
            // closing at the same moment: the event is moot
        }
    }

    fun pointer(x: Int, y: Int, buttons: Int) = send {
        writeByte(5); writeByte(buttons)
        writeShort(x.coerceIn(0, width - 1)); writeShort(y.coerceIn(0, height - 1))
    }

    fun key(keysym: Int, down: Boolean) = send {
        writeByte(4); writeByte(if (down) 1 else 0); writeShort(0); writeInt(keysym)
    }

    fun tap(keysym: Int) { key(keysym, true); key(keysym, false) }

    fun cutText(text: String) = send {
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        writeByte(6); write(ByteArray(3)); writeInt(bytes.size); write(bytes)
    }

    fun close() {
        if (closed) return
        closed = true
        try { socket.close() } catch (_: IOException) { }
        writer.shutdown()
    }
}
