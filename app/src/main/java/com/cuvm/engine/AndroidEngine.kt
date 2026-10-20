package com.cuvm.engine

import android.content.Context
import android.system.Os
import com.cuvm.data.Vm
import org.apache.commons.compress.archivers.sevenz.SevenZFile
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.util.Locale
import java.util.TimeZone
import java.util.zip.ZipFile

/**
 * Android guest on the Twoyi container (MPL-2.0). The guest ROM and libs hardcode /data/data/io.twoyi;
 * our package name has the same length, so the path is patched byte for byte.
 * Only one Android VM runs at a time: the ROM expects its rootfs at <dataDir>/rootfs, a symlink we point
 * at the VM being started.
 */
class AndroidEngine(private val context: Context) {
    private val dataDir get() = context.dataDir
    val libsDir get() = File(context.filesDir, "engine/android")
    val loader get() = File(libsDir, "libloader.so")
    val openGl get() = File(libsDir, "libOpenglRender.so")
    val twoyi get() = File(libsDir, "libtwoyi.so")
    val adb get() = File(libsDir, "libadb.so")
    fun rootfs(vm: Vm) = File(vm.dataPath, "android/rootfs")

    /** Extracts libs (once) and this VM's ROM (once per VM) from the Twoyi release APK. */
    fun prepare(vm: Vm, apk: String, log: (String) -> Unit) {
        if (!File(libsDir, ".ready").exists()) {
            log("extract")
            libsDir.deleteRecursively(); libsDir.mkdirs()
            ZipFile(apk).use { zip ->
                for (name in listOf("libtwoyi.so", "libloader.so", "libOpenglRender.so", "libadb.so")) {
                    val out = File(libsDir, name)
                    zip.getInputStream(zip.getEntry("lib/arm64-v8a/$name") ?: throw IOException("$name missing"))
                        .use { i -> out.outputStream().use { i.copyTo(it) } }
                    Patch.file(out)
                    out.setExecutable(true, false)
                }
            }
            File(libsDir, ".ready").createNewFile()
        }
        val root = rootfs(vm)
        if (!File(root, ".cuvm-extracted").exists()) {
            val base = root.parentFile!!
            base.deleteRecursively(); base.mkdirs()
            val tmp = File(base, "rootfs.7z")
            log("extract")
            ZipFile(apk).use { zip ->
                zip.getInputStream(zip.getEntry("assets/rootfs.7z")).use { i -> tmp.outputStream().use { i.copyTo(it) } }
            }
            extract7z(tmp, base) { log("extract:$it") }
            tmp.delete()
            log("patch")
            // Files.walk does not follow symlinks: absolute links in the ROM point at host paths.
            Files.walk(root.toPath()).use { s ->
                s.filter { Files.isRegularFile(it, LinkOption.NOFOLLOW_LINKS) && Files.size(it) < (32L shl 20) }
                    .forEach { Patch.file(it.toFile()) }
            }
            File(root, ".cuvm-extracted").createNewFile()
        }
        writeProps(vm)
    }

    /** 7z from p7zip keeps the unix mode in the high 16 bits of the attributes (flag 0x8000). */
    private fun extract7z(archive: File, into: File, progress: (Int) -> Unit) {
        SevenZFile.builder().setFile(archive).get().use { z ->
            val total = z.entries.count().coerceAtLeast(1)
            var n = 0
            val buf = ByteArray(1 shl 16)
            while (true) {
                val e = z.nextEntry ?: break
                val out = File(into, e.name)
                if (!out.canonicalPath.startsWith(into.canonicalPath)) throw IOException("bad path ${e.name}")
                val mode = if (e.hasWindowsAttributes && (e.windowsAttributes and 0x8000) != 0) e.windowsAttributes ushr 16 else 0
                when {
                    e.isDirectory -> out.mkdirs()
                    (mode and 0xF000) == 0xA000 -> {
                        val target = ByteArray(e.size.toInt()).also { readFully(z, it) }.toString(Charsets.UTF_8)
                        out.parentFile?.mkdirs(); out.delete()
                        Os.symlink(target, out.path)
                    }
                    else -> {
                        out.parentFile?.mkdirs()
                        out.outputStream().use { o -> while (true) { val r = z.read(buf); if (r < 0) break; o.write(buf, 0, r) } }
                    }
                }
                if (mode != 0 && (mode and 0xF000) != 0xA000) Os.chmod(out.path, mode and 0xFFF)
                if (++n % 500 == 0) progress(n * 100 / total)
            }
        }
    }

    private fun readFully(z: SevenZFile, b: ByteArray) {
        var off = 0
        while (off < b.size) { val r = z.read(b, off, b.size - off); if (r < 0) break; off += r }
    }

    private fun writeProps(vm: Vm) {
        val vendor = File(rootfs(vm), "system/vendor").apply { mkdirs() }
        File(vendor, "default.prop").writeText(
            """
            persist.sys.language=${Locale.getDefault().language}
            persist.sys.country=${Locale.getDefault().country}
            persist.sys.timezone=${TimeZone.getDefault().id}
            ro.sf.lcd_density=${vm.dpi}
            """.trimIndent() + "\n"
        )
    }

    /** Called in the :android process right before the renderer starts init. */
    fun ensureBootFiles(root: File) {
        listOf("dev/input", "dev/socket", "dev/maps").forEach { File(root, it).mkdirs() }
        File(dataDir, "socket").mkdirs()
        relink(File(dataDir, "rootfs"), root.path)
        relink(File(dataDir, "loader64"), loader.path)
        killGuestProcesses()
    }

    private fun relink(link: File, target: String) {
        link.delete() // removes the link itself, not its target
        Os.symlink(target, link.path)
    }

    /** Guest processes run through libloader.so; nothing else of ours does. */
    fun killGuestProcesses() {
        val me = android.os.Process.myPid()
        File("/proc").listFiles { f -> f.name.all(Char::isDigit) }?.forEach { p ->
            val pid = p.name.toInt()
            if (pid == me) return@forEach
            val exe = try { Os.readlink("/proc/$pid/exe") } catch (_: Exception) { return@forEach }
            if (exe.startsWith(libsDir.path) || exe.contains("/rootfs/system/")) android.os.Process.killProcess(pid)
        }
    }
}

/** Same-length replace of the hardcoded container path. */
object Patch {
    private val from = "/data/data/io.twoyi".toByteArray()
    private val to = "/data/data/com.cuvm".toByteArray()

    init { check(from.size == to.size) }

    fun bytes(b: ByteArray): Int {
        var hits = 0
        var i = 0
        while (i <= b.size - from.size) {
            var j = 0
            while (j < from.size && b[i + j] == from[j]) j++
            if (j == from.size) { System.arraycopy(to, 0, b, i, to.size); hits++; i += from.size } else i++
        }
        return hits
    }

    fun file(f: File) {
        val b = f.readBytes()
        if (bytes(b) > 0) f.writeBytes(b)
    }
}
