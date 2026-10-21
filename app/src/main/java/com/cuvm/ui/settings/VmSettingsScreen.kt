package com.cuvm.ui.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.padding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.data.Vm
import com.cuvm.engine.VmState
import com.cuvm.engine.active
import com.cuvm.ui.common.CategoryHeader
import com.cuvm.ui.common.ChoiceDialog
import com.cuvm.ui.common.ConfirmDialog
import com.cuvm.ui.common.DetailScaffold
import com.cuvm.ui.common.PrefRow
import com.cuvm.ui.common.SwitchRow
import com.cuvm.ui.common.TextDialog
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private enum class Dlg { REFRESH, CUTOUT, NAV, RENAME, RESET, REPAIR, DELETE }

@Composable
fun VmSettingsScreen(id: String, onBack: () -> Unit, onOpen: (String) -> Unit, onDeleted: () -> Unit) {
    val vm by CuciVm.vms.vm(id).collectAsStateWithLifecycle(null)
    val state by CuciVm.runtime.state(id).collectAsStateWithLifecycle(VmState.Stopped)
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var image by remember { mutableStateOf("") }
    var engineType by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf<String?>(null) }   // backup in progress / result message
    val v = vm ?: return
    LaunchedEffect(v.imageId) { CuciVm.vms.image(v.imageId)?.let { image = it.name; engineType = it.engine } }
    val savedMsg = stringResource(R.string.backup_saved)
    val backupFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/gzip")) { uri ->
        if (uri != null) scope.launch {
            busy = ""
            busy = runCatching { CuciVm.backups.backup(v, uri) }.fold({ savedMsg }, { it.message ?: it.toString() })
        }
    }
    fun save(f: (Vm) -> Vm) = scope.launch { CuciVm.vms.update(f(v)) }
    // Download folder is bound into the guest only once storage access is granted (legacy storage, targetSdk 28).
    val storage = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        if (r.values.all { it }) save { it.copy(shareFolder = true) }
    }
    val running = state.active
    val restartNote = if (running) stringResource(R.string.applies_next_boot) else null

    DetailScaffold(stringResource(R.string.vm_settings), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            CategoryHeader(stringResource(R.string.cat_system))
            PrefRow(stringResource(R.string.guest_system), image)
            if (engineType == "app_space" && CuciVm.space.gmsSupported()) {
                var gms by remember { mutableStateOf<Boolean?>(null) }
                var gmsBusy by remember { mutableStateOf(false) }
                LaunchedEffect(v.id) { gms = withContext(Dispatchers.IO) { CuciVm.space.gmsInstalled(CuciVm.vms.spaceUser(v)) } }
                SwitchRow(stringResource(R.string.google_services), gms == true,
                    if (gmsBusy) stringResource(R.string.google_services_busy) else stringResource(R.string.google_services_summary)) { on ->
                    if (gmsBusy) return@SwitchRow
                    gmsBusy = true
                    scope.launch {
                        withContext(Dispatchers.IO) { CuciVm.space.setGms(CuciVm.vms.spaceUser(v), on) }
                        gms = withContext(Dispatchers.IO) { CuciVm.space.gmsInstalled(CuciVm.vms.spaceUser(v)) }
                        gmsBusy = false
                    }
                }
            }

            CategoryHeader(stringResource(R.string.cat_display))
            PrefRow(stringResource(R.string.resolution), restartNote ?: "${v.width} × ${v.height} · DPI ${v.dpi}") { onOpen("resolution") }
            PrefRow(stringResource(R.string.refresh_rate), "${v.refreshRate} FPS") { dlg = Dlg.REFRESH }
            PrefRow(stringResource(R.string.cutout), stringResource(if (v.cutoutMode == "overlap") R.string.cutout_overlap else R.string.cutout_exclude)) { dlg = Dlg.CUTOUT }

            CategoryHeader(stringResource(R.string.cat_navigation))
            PrefRow(stringResource(R.string.nav_bar), navLabel(v.navBar)) { dlg = Dlg.NAV }
            PrefRow(stringResource(R.string.floating_window), stringResource(R.string.floating_window_summary)) { onOpen("advanced") }

            CategoryHeader(stringResource(R.string.cat_virtual))
            Text(stringResource(R.string.android_only_note), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp))
            PrefRow(stringResource(R.string.sensors), stringResource(R.string.sensors_summary)) { onOpen("sensors") }
            SwitchRow(stringResource(R.string.camera), v.camera) { c -> save { it.copy(camera = c) } }
            PrefRow(stringResource(R.string.location), stringResource(R.string.location_summary)) { onOpen("location") }
            PrefRow(stringResource(R.string.network), stringResource(R.string.network_summary)) { onOpen("network") }
            PrefRow(stringResource(R.string.phone), stringResource(R.string.phone_summary)) { onOpen("phone") }
            PrefRow(stringResource(R.string.hardware), stringResource(R.string.hardware_summary)) { onOpen("hardware") }

            CategoryHeader(stringResource(R.string.cat_sharing))
            SwitchRow(stringResource(R.string.share_clipboard), v.shareClipboard) { c -> save { it.copy(shareClipboard = c) } }
            SwitchRow(stringResource(R.string.share_folder), v.shareFolder, stringResource(if (v.shareFolder) R.string.share_folder_on else R.string.off)) { c ->
                if (c) storage.launch(arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE))
                else save { it.copy(shareFolder = false) }
            }
            SwitchRow(stringResource(R.string.host_notifications), v.hostNotifications) { c -> save { it.copy(hostNotifications = c) } }

            if (engineType != "app_space") {
                CategoryHeader(stringResource(R.string.backup_restore))
                PrefRow(stringResource(R.string.backup_vm), if (running) stringResource(R.string.stop_first) else stringResource(R.string.backup_vm_summary), enabled = !running && busy != "") {
                    backupFile.launch(v.name.replace(Regex("[^A-Za-z0-9._-]"), "_") + ".cucivm.tar.gz")
                }
            }

            CategoryHeader(stringResource(R.string.cat_power))
            if (running) PrefRow(stringResource(R.string.shutdown_vm)) { CuciVm.runtime.stop(id) }
            else PrefRow(stringResource(R.string.start_vm)) { CuciVm.runtime.start(id) }

            CategoryHeader(stringResource(R.string.cat_manage))
            PrefRow(stringResource(R.string.rename), v.name) { dlg = Dlg.RENAME }
            PrefRow(stringResource(R.string.reset_vm), stringResource(R.string.reset_vm_summary), enabled = !running) { dlg = Dlg.RESET }
            PrefRow(stringResource(R.string.repair_vm), stringResource(R.string.repair_vm_summary), enabled = !running) { dlg = Dlg.REPAIR }
            PrefRow(stringResource(R.string.delete_vm), if (running) stringResource(R.string.stop_first) else null, enabled = !running) { dlg = Dlg.DELETE }
        }
    }

    busy?.let { msg ->
        if (msg.isEmpty()) androidx.compose.material3.AlertDialog(onDismissRequest = {}, confirmButton = {},
            title = { Text(stringResource(R.string.backup_vm)) },
            text = { androidx.compose.material3.LinearProgressIndicator(Modifier.padding(top = 8.dp)) })
        else androidx.compose.material3.AlertDialog(onDismissRequest = { busy = null },
            confirmButton = { androidx.compose.material3.TextButton(onClick = { busy = null }) { Text(stringResource(R.string.ok)) } },
            text = { Text(msg) })
    }
    when (dlg) {
        Dlg.REFRESH -> TextDialog(stringResource(R.string.refresh_rate), v.refreshRate.toString(), numeric = true,
            validate = { it.toIntOrNull() in 1..120 }, onOk = { t -> save { it.copy(refreshRate = t.toInt()) } }, onDismiss = { dlg = null })
        Dlg.CUTOUT -> ChoiceDialog(stringResource(R.string.cutout),
            listOf("overlap" to stringResource(R.string.cutout_overlap), "exclude" to stringResource(R.string.cutout_exclude)),
            v.cutoutMode, { c -> save { it.copy(cutoutMode = c) } }, { dlg = null })
        Dlg.NAV -> ChoiceDialog(stringResource(R.string.nav_bar),
            listOf("none", "back_home_recent", "recent_home_back").map { it to navLabel(it) },
            v.navBar, { c -> save { it.copy(navBar = c) } }, { dlg = null })
        Dlg.RENAME -> TextDialog(stringResource(R.string.rename), v.name, validate = { it.isNotBlank() && it.length <= 60 },
            onOk = { t -> save { it.copy(name = t) } }, onDismiss = { dlg = null })
        Dlg.RESET -> ConfirmDialog(stringResource(R.string.reset_vm), stringResource(R.string.reset_vm_confirm), stringResource(R.string.reset), danger = true,
            onConfirm = { scope.launch { CuciVm.vms.reset(v) } }, onDismiss = { dlg = null })
        Dlg.REPAIR -> ConfirmDialog(stringResource(R.string.repair_vm), stringResource(R.string.repair_vm_confirm), stringResource(R.string.repair),
            onConfirm = { scope.launch { CuciVm.vms.repair(v) } }, onDismiss = { dlg = null })
        Dlg.DELETE -> ConfirmDialog(stringResource(R.string.delete_vm), stringResource(R.string.delete_vm_confirm, v.name), stringResource(R.string.delete), danger = true,
            onConfirm = { scope.launch { CuciVm.vms.delete(v); onDeleted() } }, onDismiss = { dlg = null })
        null -> Unit
    }
}

@Composable
private fun navLabel(v: String) = stringResource(
    when (v) { "none" -> R.string.nav_none; "recent_home_back" -> R.string.nav_keys_alt; else -> R.string.nav_keys }
)
