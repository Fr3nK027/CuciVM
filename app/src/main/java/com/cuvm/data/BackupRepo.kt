package com.cuvm.data

import android.content.Context
import android.net.Uri
import android.system.Os
import java.nio.file.Files
import com.cuvm.engine.deleteTree
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.util.UUID
import kotlin.concurrent.thread

/**
 * A VM backup is one .tar.gz chosen by the user (Storage Access Framework):
 *   cuvm-vm.json  - name, system and display settings
 *   rootfs/       - the guest filesystem, symlinks kept as symlinks
 * App-space VMs are not backed up: their apps and data live inside the engine, not in the VM folder.
 */
class BackupRepo(private val context: Context, private val vms: VmRepo, private val dao: VmDao) {
    private val manifest = "cuvm-vm.json"

    suspend fun backup(vm: Vm, target: Uri) = withContext(Dispatchers.IO) {
        val dir = File(vm.dataPath)
        if (!File(dir, "rootfs").exists()) throw IOException("nothing to back up yet: start the VM once first")
        File(dir, manifest).writeText(
            JSONObject()
                .put("version", 1).put("name", vm.name).put("imageId", vm.imageId).put("dataPath", vm.dataPath)
                .put("width", vm.width).put("height", vm.height).put("dpi", vm.dpi)
                .put("refreshRate", vm.refreshRate).put("navBar", vm.navBar).put("cutoutMode", vm.cutoutMode)
                .toString(),
        )
        val p = ProcessBuilder("/system/bin/tar", "czf", "-", "-C", dir.path, manifest, "rootfs")
            .redirectError(File(dir, "backup.log")).start()
        context.contentResolver.openOutputStream(target, "wt")?.use { out -> p.inputStream.use { it.copyTo(out, 1 shl 16) } }
            ?: throw IOException("cannot write the backup file")
        // Exit 1 = some files could not be read (sockets, device nodes); the system is still complete.
        if (p.waitFor() > 1) throw IOException("backup failed (see backup.log)")
    }

    private fun relink(root: File, oldPrefix: String, newPrefix: String) {
        Files.walk(root.toPath()).use { paths ->
            paths.filter { Files.isSymbolicLink(it) }.forEach { link ->
                val target = Os.readlink(link.toString())
                if (target.startsWith("$oldPrefix/")) {
                    Files.delete(link)
                    Os.symlink(newPrefix + target.removePrefix(oldPrefix), link.toString())
                }
            }
        }
    }

    suspend fun restore(source: Uri): Vm = withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "vms/" + UUID.randomUUID()).apply { mkdirs() }
        try {
            val p = ProcessBuilder("/system/bin/tar", "xzf", "-", "-C", dir.path)
                .redirectError(File(dir, "restore.log")).start()
            // Feed the file into tar's stdin on its own thread while we wait.
            val feeder = thread(name = "restore-feed") {
                runCatching {
                    context.contentResolver.openInputStream(source)?.use { i -> p.outputStream.use { i.copyTo(it, 1 shl 16) } }
                }
                runCatching { p.outputStream.close() }
            }
            val code = p.waitFor()
            feeder.join()
            val meta = File(dir, manifest)
            if (!meta.exists() || !File(dir, "rootfs").exists()) throw IOException("not a CuciVM backup")
            if (code > 1) throw IOException("restore failed (see restore.log)")
            val m = JSONObject(meta.readText())
            val image = dao.image(m.getString("imageId")) ?: throw IOException("system ${m.getString("imageId")} is not in the catalogue")
            val vm = vms.create(image, m.getInt("width"), m.getInt("height"), m.getInt("dpi"), m.getString("name") + " (backup)")
            try { place(dir, vm, m) } catch (e: Exception) { vms.delete(vm); throw e }
            vm
        } catch (e: Exception) {
            dir.deleteTree()
            throw e
        }
    }

    private suspend fun place(dir: File, vm: Vm, m: JSONObject) {
            // Move the restored files into the new VM's folder; it is not extracted or set up again.
            val target = File(vm.dataPath)
            target.deleteTree()
            if (!dir.renameTo(target)) throw IOException("cannot place the restored VM")
            // Former hard links are symlinks with absolute targets inside the old VM folder: repoint them.
            m.optString("dataPath").takeIf { it.isNotEmpty() && it != vm.dataPath }?.let { old -> relink(File(target, "rootfs"), old, vm.dataPath) }
            vms.update(vm.copy(refreshRate = m.optInt("refreshRate", 60), navBar = m.optString("navBar", vm.navBar), cutoutMode = m.optString("cutoutMode", vm.cutoutMode)))
    }
}
