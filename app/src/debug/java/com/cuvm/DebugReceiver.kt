package com.cuvm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Debug builds only: drive VMs from adb for automated tests.
 *   adb shell am broadcast -n com.cuvm/.DebugReceiver -a com.cuvm.debug.LIST
 *   ... -a com.cuvm.debug.START|STOP|REPAIR|RESET --es name "Programmi Windows_3"
 *   ... -a com.cuvm.debug.SYNC   (re-read the online catalogue)
 * Results go to logcat, tag CuciVmDebug.
 */
class DebugReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val vms = CuciVm.vms.vms.first()
                val vm = intent.getStringExtra("name")?.let { n -> vms.firstOrNull { it.name == n } }
                when (intent.action?.substringAfterLast('.')) {
                    "LIST" -> vms.forEach { log("${it.name} | ${it.id} | ${it.width}x${it.height} | ${CuciVm.runtime.current(it.id)}") }
                    "SYNC" -> { CuciVm.images.syncCatalogue(); log("catalogue synced") }
                    "START" -> vm?.let { CuciVm.runtime.start(it.id); log("start ${it.name}") }
                    "STOP" -> vm?.let { CuciVm.runtime.stop(it.id); log("stop ${it.name}") }
                    "REPAIR" -> vm?.let { CuciVm.vms.repair(it); log("repair ${it.name}") }
                    "RESET" -> vm?.let { CuciVm.vms.reset(it); log("reset ${it.name}") }
                    // --es path <file in app storage>
                    "BACKUP" -> vm?.let {
                        val f = java.io.File(context.getExternalFilesDir(null), intent.getStringExtra("path") ?: "backup.tar.gz")
                        CuciVm.backups.backup(it, android.net.Uri.fromFile(f)); log("backup ${it.name} -> $f ${f.length()} bytes")
                    }
                    "RESTORE" -> {
                        val f = java.io.File(context.getExternalFilesDir(null), intent.getStringExtra("path") ?: "backup.tar.gz")
                        val v = CuciVm.backups.restore(android.net.Uri.fromFile(f)); log("restored ${v.name} ${v.id}")
                    }
                    // --ez on true|false
                    "RES" -> vm?.let { CuciVm.vms.update(it.copy(width = intent.getIntExtra("w", it.width), height = intent.getIntExtra("h", it.height), dpi = intent.getIntExtra("dpi", it.dpi))); log("res ${it.name}") }
                    "SHARE" -> vm?.let { CuciVm.vms.update(it.copy(shareFolder = intent.getBooleanExtra("on", true))); log("shareFolder ${it.name}") }
                    "SMALLWINDOW" -> vm?.let { CuciVm.vms.update(it.copy(smallWindow = intent.getBooleanExtra("on", true))); log("smallWindow ${it.name}") }
                    // App spaces: --es name <vm> --es pkg <package>
                    "GMS" -> vm?.let {
                        val u = CuciVm.vms.spaceUser(it)
                        log("gms supported=${CuciVm.space.gmsSupported()} set=${CuciVm.space.setGms(u, intent.getBooleanExtra("on", true))} now=${CuciVm.space.gmsInstalled(u)}")
                    }
                    // --es path <apk in the app's external files dir>
                    "APK" -> vm?.let {
                        val f = java.io.File(context.getExternalFilesDir(null), intent.getStringExtra("path") ?: "test.apk")
                        val user = CuciVm.vms.spaceUser(it)
                        CuciVm.space.ensureUser(user)
                        log("apk $f: ${CuciVm.space.importApk(android.net.Uri.fromFile(f), user)}")
                    }
                    "IMPORT", "LAUNCH", "APPS" -> {
                        val v = vm ?: return@launch log("no VM")
                        val user = CuciVm.vms.spaceUser(v)
                        CuciVm.space.ensureUser(user)
                        val pkg = intent.getStringExtra("pkg") ?: ""
                        when (intent.action?.substringAfterLast('.')) {
                            "IMPORT" -> log("import $pkg: ${CuciVm.space.importInstalled(pkg, user)}")
                            "LAUNCH" -> log("launch $pkg: ${CuciVm.space.launch(pkg, user, CuciVm.vms.locationNow(v.id))}")
                            else -> CuciVm.space.apps(user).forEach { log("app ${it.packageName}") }
                        }
                    }
                    // -es image <catalogue id>
                    "DOWNLOAD" -> intent.getStringExtra("image")?.let { CuciVm.images.download(it); log("download $it") }
                    "IMAGES" -> CuciVm.images.images.first().forEach { log("${it.id} | ${it.status} | ${it.engine}") }
                    // -es image <id> -es name <vm name> -ei w 1280 -ei h 720 -ei dpi 120
                    "CREATE" -> {
                        val img = CuciVm.vms.image(intent.getStringExtra("image") ?: "") ?: return@launch log("no such image")
                        val v = CuciVm.vms.create(
                            img, intent.getIntExtra("w", 1280), intent.getIntExtra("h", 720), intent.getIntExtra("dpi", 120),
                            intent.getStringExtra("vmname") ?: CuciVm.vms.defaultName(img),
                        )
                        log("created ${v.name} ${v.id}")
                    }
                    else -> log("unknown action ${intent.action}")
                }
                if (vm == null && intent.hasExtra("name")) log("no VM named ${intent.getStringExtra("name")}")
            } catch (e: Exception) {
                log("error: $e")
            } finally {
                pending.finish()
            }
        }
    }

    private fun log(s: String) { Log.i("CuciVmDebug", s) }
}
