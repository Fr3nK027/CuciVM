package com.cuvm.engine

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import java.net.InetSocketAddress
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Guest sound -> phone speaker. In the guest, PulseAudio plays into a null sink whose monitor is served
 * as raw PCM (48 kHz, s16le, stereo) by module-simple-protocol-tcp on 127.0.0.1:[port]; here it is
 * written to an AudioTrack. Reconnects while the VM runs (PulseAudio starts after the display).
 */
class AudioBridge(private val port: Int) {
    @Volatile var muted = false
    @Volatile private var running = true
    @Volatile private var socket: Socket? = null

    init {
        thread(name = "audio-$port", isDaemon = true) { loop() }
    }

    private fun loop() {
        val rate = 48000
        val min = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT)
        val track = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setBufferSizeInBytes(min * 2)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setPerformanceMode(AudioTrack.PERFORMANCE_MODE_LOW_LATENCY)
            .build()
        val buf = ByteArray(min)
        try {
            while (running) {
                try {
                    Socket().use { s ->
                        socket = s
                        s.connect(InetSocketAddress("127.0.0.1", port), 1000)
                        s.tcpNoDelay = true
                        track.play()
                        val input = s.getInputStream()
                        while (running) {
                            val n = input.read(buf)
                            if (n < 0) break
                            // Muted: keep draining the stream so the guest does not stall, just do not play it.
                            if (!muted) track.write(buf, 0, n - n % 4)
                        }
                    }
                } catch (_: Exception) {
                    // guest audio not up yet, or restarted
                }
                track.pause(); track.flush()
                if (running) Thread.sleep(1000)
            }
        } finally {
            track.release()
        }
    }

    fun close() {
        running = false
        runCatching { socket?.close() }
    }
}
