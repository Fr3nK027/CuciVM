package com.cuvm.engine

import android.content.Context
import com.cuvm.data.Catalogue
import com.cuvm.data.GuestImage
import com.cuvm.data.Vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import org.tukaani.xz.XZInputStream
import java.util.TimeZone

interface GuestEngine {
    suspend fun prepare(vm: Vm, image: GuestImage, log: (String) -> Unit)
    fun start(vm: Vm, image: GuestImage, display: Int): Process
}

/** Linux userland under proot. Display: TigerVNC's Xvnc on 127.0.0.1:(5900 + display). */
class ProotEngine(private val context: Context) : GuestEngine {
    private val engineDir get() = File(context.filesDir, "engine")

    /** Copies proot + loader + libs out of assets. Write-then-rename so a running proot is never truncated. */
    fun install() {
        val lib = File(engineDir, "lib").apply { mkdirs() }
        val files = mapOf(
            "proot.bin" to File(engineDir, "proot"),
            "loader.bin" to File(engineDir, "loader"),
            "libtalloc.so.2.bin" to File(lib, "libtalloc.so.2"),
            "libandroid-shmem.so.bin" to File(lib, "libandroid-shmem.so"),
        )
        for ((asset, out) in files) {
            val tmp = File(out.path + ".new")
            context.assets.open("engine/$asset").use { i -> tmp.outputStream().use { i.copyTo(it) } }
            tmp.setExecutable(true, false)
            if (!tmp.renameTo(out)) throw IOException("cannot install $out")
        }
    }

    private fun rootfs(vm: Vm) = File(vm.dataPath, "rootfs")

    /** toybox tar flag from the file's magic bytes (downloads are stored without extension). */
    private fun compression(f: File): String {
        val m = ByteArray(6).also { b -> f.inputStream().use { it.read(b) } }
        return when {
            m[0] == 0x1f.toByte() && m[1] == 0x8b.toByte() -> "z"
            m[0] == 0xfd.toByte() && m[1] == '7'.code.toByte() && m[2] == 'z'.code.toByte() -> "J"
            m[0] == 'B'.code.toByte() && m[1] == 'Z'.code.toByte() && m[2] == 'h'.code.toByte() -> "j"
            else -> ""
        }
    }

    override suspend fun prepare(vm: Vm, image: GuestImage, log: (String) -> Unit) = withContext(Dispatchers.IO) {
        val root = rootfs(vm)
        if (!File(root, ".vmbox-extracted").exists()) {
            val tar = image.localPath ?: throw IOException("image not downloaded")
            log("extract")
            val tmp = File(vm.dataPath, "rootfs.tmp")
            root.deleteRecursively(); tmp.deleteRecursively(); tmp.mkdirs()
            // toybox tar has no xz of its own: unpack .xz here first.
            var input = File(tar)
            var flag = compression(input)
            if (flag == "J") {
                val plain = File(vm.dataPath, "rootfs.tar")
                XZInputStream(input.inputStream().buffered(1 shl 16)).use { i -> plain.outputStream().use { i.copyTo(it, 1 shl 16) } }
                input = plain; flag = ""
            }
            // Under proot with --link2symlink: Android forbids hard links for apps, proot turns them into symlinks.
            val p = withProot(ProcessBuilder(
                File(engineDir, "proot").path, "--link2symlink", "-0", "/system/bin/tar", "x${flag}f", input.path, "-C", tmp.path,
            ), File(vm.dataPath, "tmp").apply { mkdirs() })
                .redirectErrorStream(true).redirectOutput(File(vm.dataPath, "extract.log")).start()
            p.waitFor() // device nodes (dev/console...) fail as non-root; checked below instead
            if (input.path != tar) input.delete()
            // Some tarballs (proot-distro) wrap the system in one top-level folder.
            val only = tmp.listFiles()?.singleOrNull()
            val top = if (only != null && only.isDirectory && !File(tmp, "usr").exists()) only else tmp
            if (!top.renameTo(root)) throw IOException("extract failed")
            tmp.deleteRecursively()
            if (!File(root, "bin/sh").exists()) throw IOException("extract failed (see extract.log)")
            // Often symlinks into /run: replace with plain files.
            File(root, "etc/resolv.conf").apply { delete(); writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
            File(root, "etc/hosts").apply { delete(); writeText("127.0.0.1 localhost\n::1 localhost\n") }
            File(root, ".vmbox-extracted").createNewFile()
        }
        val setup = Catalogue.profile(image).setup
        if (setup.isNotEmpty() && !File(root, ".vmbox-setup").exists()) {
            log("setup")
            val p = guest(vm, setup)
                .redirectErrorStream(true).start()
            p.inputStream.bufferedReader().useLines { lines -> lines.forEach { log("setup: $it") } }
            if (p.waitFor() != 0) throw IOException("setup failed (network?)")
            File(root, ".vmbox-setup").createNewFile()
        }
    }

    override fun start(vm: Vm, image: GuestImage, display: Int): Process {
        val port = 5900 + display
        val session = Catalogue.profile(image).session
        val script = """
            rm -f /tmp/.X$display-lock /tmp/.X11-unix/X$display
            "$(command -v Xvnc || command -v Xtigervnc)" :$display -geometry ${vm.width}x${vm.height} -dpi ${vm.dpi} -depth 24 -SecurityTypes None -localhost -rfbport $port -AlwaysShared -noreset &
            i=0; while [ ! -e /tmp/.X11-unix/X$display ] && [ ${'$'}i -lt 40 ]; do sleep 0.25; i=${'$'}((i+1)); done
            export DISPLAY=:$display
            $session
        """.trimIndent()
        return guest(vm, script)
            .redirectErrorStream(true)
            .redirectOutput(File(vm.dataPath, "boot.log"))
            .start()
    }

    private fun guest(vm: Vm, script: String): ProcessBuilder {
        val tmp = File(vm.dataPath, "tmp").apply { mkdirs() }
        val args = mutableListOf(
            File(engineDir, "proot").path, "--kill-on-exit", "--link2symlink", "-0",
            "-r", rootfs(vm).path, "-b", "/dev", "-b", "/proc", "-b", "/sys",
        )
        if (vm.shareFolder) args += listOf("-b", "/storage/emulated/0/Download:/mnt/shared")
        args += listOf(
            "-w", "/root", "/usr/bin/env", "-i",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME=/root", "USER=root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "TZ=" + TimeZone.getDefault().id,
            "/bin/sh", "-c", script,
        )
        return withProot(ProcessBuilder(args), tmp)
    }

    private fun withProot(pb: ProcessBuilder, tmp: File) = pb.apply {
        environment()["LD_LIBRARY_PATH"] = File(engineDir, "lib").path
        environment()["PROOT_LOADER"] = File(engineDir, "loader").path
        environment()["PROOT_TMP_DIR"] = tmp.path
    }
}
