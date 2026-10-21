package com.cuvm.engine

import android.content.Context
import android.system.Os
import android.system.OsConstants
import com.cuvm.R
import com.cuvm.data.Catalogue
import com.cuvm.data.GuestImage
import com.cuvm.data.Vm
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext
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
    /** Extract/setup process per VM, so a Stop during first boot can end it. */
    private val running = java.util.concurrent.ConcurrentHashMap<String, Process>()

    fun cancelSetup(vmId: String) { running.remove(vmId)?.destroy() }

    /**
     * Ends every process of a VM. Signalling proot alone is not enough: it ignores SIGTERM, and if it is
     * killed its tracees are detached and keep running. So: find proot by its rootfs argument, take its
     * whole descendant tree, SIGTERM it (desktops save and quit), then SIGKILL what is left.
     */
    fun stopTree(vm: Vm, graceMs: Long = 5000) {
        val tree = descendants(prootPids(vm))
        tree.forEach { runCatching { Os.kill(it, OsConstants.SIGTERM) } }
        val until = System.currentTimeMillis() + graceMs
        while (System.currentTimeMillis() < until && tree.any { File("/proc/$it").exists() }) Thread.sleep(100)
        descendants(prootPids(vm)).plus(tree).forEach { runCatching { Os.kill(it, OsConstants.SIGKILL) } }
    }

    private fun prootPids(vm: Vm): List<Int> {
        val marker = rootfs(vm).path
        return procPids().filter { pid ->
            val cmd = runCatching { File("/proc/$pid/cmdline").readText() }.getOrDefault("")
            cmd.contains("proot") && cmd.contains(marker)
        }
    }

    private fun procPids() = File("/proc").list()?.mapNotNull { it.toIntOrNull() } ?: emptyList()

    private fun descendants(roots: List<Int>): Set<Int> {
        val parent = procPids().associateWith { pid ->
            // /proc/<pid>/stat: "pid (comm) state ppid ..."; comm may contain spaces, so parse after ')'.
            runCatching { File("/proc/$pid/stat").readText().substringAfterLast(')').trim().split(' ')[1].toInt() }.getOrDefault(-1)
        }
        val out = roots.toMutableSet()
        var grew = true
        while (grew) {
            grew = false
            for ((pid, ppid) in parent) if (ppid in out && out.add(pid)) grew = true
        }
        return out
    }

    private fun track(vmId: String, p: Process): Process = p.also { running[vmId] = it }

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


    /** Name of the first entry of a (plain or gzip) tar, for detecting a wrapping folder. */
    private fun firstEntry(f: File, flag: String): String? = runCatching {
        val raw = f.inputStream().buffered()
        val input = if (flag == "z") java.util.zip.GZIPInputStream(raw) else raw
        input.use { val h = ByteArray(100); var n = 0; while (n < 100) { val r = it.read(h, n, 100 - n); if (r < 0) break; n += r }
            String(h, 0, h.indexOf(0).let { z -> if (z < 0) n else z }, Charsets.UTF_8) }
    }.getOrNull()

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
            root.deleteTree(); root.mkdirs()
            // toybox tar has no xz of its own: unpack .xz here first.
            var input = File(tar)
            var flag = compression(input)
            val plain = File(vm.dataPath, "rootfs.tar")
            try {
                if (flag == "J") {
                    XZInputStream(input.inputStream().buffered(1 shl 16)).use { i -> plain.outputStream().use { i.copyTo(it, 1 shl 16) } }
                    input = plain; flag = ""
                }
                // Some tarballs (proot-distro) wrap the system in one top-level folder: strip it.
                val strip = if (wrappedInFolder(firstEntry(input, flag))) "1" else "0"
                // Straight into the final folder, under proot --link2symlink: Android forbids hard links for
                // apps, proot turns them into symlinks, and their targets must be final paths (no rename after).
                val log = File(vm.dataPath, "extract.log")
                val p = withProot(ProcessBuilder(
                    File(engineDir, "proot").path, "--link2symlink", "-0",
                    "/system/bin/tar", "x${flag}f", input.path, "--strip-components", strip, "-C", root.path,
                ), File(vm.dataPath, "tmp").apply { mkdirs() })
                    .redirectErrorStream(true).redirectOutput(log).start().let { track(vm.id, it) }
                // Device nodes (dev/console...) cannot be created by an app; anything else is a real failure.
                val code = p.waitFor()
                running.remove(vm.id)
                coroutineContext.ensureActive() // stopped meanwhile
                if (code != 0) {
                    val bad = log.readLines().filter { it.startsWith("tar:") && !it.contains("had errors") && !it.contains("dev/") }
                    if (bad.isNotEmpty()) throw IOException("extract failed: ${bad.first()}")
                }
            } finally {
                plain.delete()
            }
            // bin/sh is often an absolute symlink (/bin/busybox): check the link itself, not its host target.
            if (!java.nio.file.Files.exists(File(root, "bin/sh").toPath(), java.nio.file.LinkOption.NOFOLLOW_LINKS)) throw IOException("extract failed (see extract.log)")
            // Often symlinks into /run: replace with plain files.
            File(root, "etc/resolv.conf").apply { delete(); writeText("nameserver 1.1.1.1\nnameserver 8.8.8.8\n") }
            File(root, "etc/hosts").apply { delete(); writeText("127.0.0.1 localhost\n::1 localhost\n") }
            File(root, ".vmbox-extracted").createNewFile()
        }
        val setup = Catalogue.profile(image).setup
        if (setup.isNotEmpty() && !File(root, ".vmbox-setup").exists()) {
            log("setup")
            val p = track(vm.id, guest(vm, setup).redirectErrorStream(true).start())
            p.inputStream.bufferedReader().useLines { lines -> lines.forEach { log("setup: $it") } }
            val code = p.waitFor()
            running.remove(vm.id)
            coroutineContext.ensureActive() // stopped meanwhile
            if (code != 0) throw IOException("setup failed (network?)")
            File(root, ".vmbox-setup").createNewFile()
        }
    }

    override fun start(vm: Vm, image: GuestImage, display: Int): Process {
        val port = 5900 + display
        val session = Catalogue.profile(image).session
        // The phone's Download folder as a desktop icon, so files (setup.exe...) are one tap away.
        val link = "/root/Desktop/" + context.getString(R.string.phone_downloads).replace("'", "")
        // Old links (another app language) are found by their target and removed first.
        val shared = "find /root/Desktop -maxdepth 1 -lname /mnt/shared -exec rm -f {} + 2>/dev/null; " +
            if (vm.shareFolder) "mkdir -p /root/Desktop && ln -sfn /mnt/shared '$link'" else "true"
        val script = """
            rm -f /tmp/.X$display-lock /tmp/.X11-unix/X$display
            "$(command -v Xvnc || command -v Xtigervnc)" :$display -geometry ${vm.width}x${vm.height} -dpi ${vm.dpi} -depth 24 -SecurityTypes None -localhost -rfbport $port -AlwaysShared -noreset -CompareFB=0 -FrameRate=${vm.refreshRate} &
            i=0; while [ ! -e /tmp/.X11-unix/X$display ] && [ ${'$'}i -lt 40 ]; do sleep 0.25; i=${'$'}((i+1)); done
            export DISPLAY=:$display
            # Sound: PulseAudio into a null sink, its monitor served as raw PCM to CuciVM (see AudioBridge).
            if command -v pulseaudio >/dev/null; then
              pulseaudio --daemonize=yes -n --exit-idle-time=-1 --log-target=file:/tmp/pulse.log                 -L "module-null-sink sink_name=cuvm rate=48000"                 -L "module-native-protocol-unix auth-anonymous=1 socket=/tmp/pulse.sock"                 -L "module-simple-protocol-tcp source=cuvm.monitor record=true playback=false listen=127.0.0.1 port=${AUDIO_BASE + display} rate=48000 format=s16le channels=2"
              export PULSE_SERVER=unix:/tmp/pulse.sock
            fi
            $shared
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
            // Android has no /dev/shm; browsers and Wine need one.
            "-b", File(vm.dataPath, "shm").apply { mkdirs() }.path + ":/dev/shm",
        )
        if (vm.shareFolder) args += listOf("-b", "/storage/emulated/0/Download:/mnt/shared")
        args += listOf(
            "-w", "/root", "/usr/bin/env", "-i",
            "PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin",
            "HOME=/root", "USER=root", "TERM=xterm-256color", "LANG=C.UTF-8",
            "TZ=" + TimeZone.getDefault().id,
            // Firefox's own sandbox (seccomp, namespaces) cannot start under proot; the VM is the sandbox.
            "MOZ_FAKE_NO_SANDBOX=1", "MOZ_DISABLE_CONTENT_SANDBOX=1", "MOZ_DISABLE_GMP_SANDBOX=1",
            "MOZ_DISABLE_RDD_SANDBOX=1", "MOZ_DISABLE_SOCKET_PROCESS_SANDBOX=1",
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

private val ROOT_DIRS = setOf("bin", "boot", "dev", "etc", "home", "lib", "lib64", "media", "mnt", "opt", "proc",
    "root", "run", "sbin", "srv", "sys", "tmp", "usr", "var")

/** First tar entry "debian-trixie-aarch64/bin" -> wrapped in a folder; "./bin", "bin/", "etc/x" -> not. */
internal fun wrappedInFolder(first: String?): Boolean {
    val path = first?.trimStart('/') ?: return false
    val top = path.substringBefore('/')
    return path.contains('/') && top.isNotEmpty() && top != "." && top !in ROOT_DIRS
}

/** Guest audio PCM port = AUDIO_BASE + X display number (display port is 5900 + display). */
const val AUDIO_BASE = 4700
