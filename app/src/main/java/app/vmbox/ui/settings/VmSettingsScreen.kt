package app.vmbox.ui.settings

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
import app.vmbox.R
import app.vmbox.VmBox
import app.vmbox.data.Vm
import app.vmbox.engine.VmState
import app.vmbox.engine.active
import app.vmbox.ui.common.CategoryHeader
import app.vmbox.ui.common.ChoiceDialog
import app.vmbox.ui.common.ConfirmDialog
import app.vmbox.ui.common.DetailScaffold
import app.vmbox.ui.common.PrefRow
import app.vmbox.ui.common.SwitchRow
import app.vmbox.ui.common.TextDialog
import kotlinx.coroutines.launch

private enum class Dlg { REFRESH, CUTOUT, NAV, RENAME, RESET, REPAIR, DELETE }

@Composable
fun VmSettingsScreen(id: String, onBack: () -> Unit, onOpen: (String) -> Unit, onDeleted: () -> Unit) {
    val vm by VmBox.vms.vm(id).collectAsStateWithLifecycle(null)
    val state by VmBox.runtime.state(id).collectAsStateWithLifecycle(VmState.Stopped)
    val scope = rememberCoroutineScope()
    var dlg by remember { mutableStateOf<Dlg?>(null) }
    var image by remember { mutableStateOf("") }
    val v = vm ?: return
    LaunchedEffect(v.imageId) { image = VmBox.vms.image(v.imageId)?.name ?: "" }
    fun save(f: (Vm) -> Vm) = scope.launch { VmBox.vms.update(f(v)) }
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

            CategoryHeader(stringResource(R.string.cat_power))
            if (running) PrefRow(stringResource(R.string.shutdown_vm)) { VmBox.runtime.stop(id) }
            else PrefRow(stringResource(R.string.start_vm)) { VmBox.runtime.start(id) }

            CategoryHeader(stringResource(R.string.cat_manage))
            PrefRow(stringResource(R.string.rename), v.name) { dlg = Dlg.RENAME }
            PrefRow(stringResource(R.string.reset_vm), stringResource(R.string.reset_vm_summary), enabled = !running) { dlg = Dlg.RESET }
            PrefRow(stringResource(R.string.repair_vm), stringResource(R.string.repair_vm_summary), enabled = !running) { dlg = Dlg.REPAIR }
            PrefRow(stringResource(R.string.delete_vm), if (running) stringResource(R.string.stop_first) else null, enabled = !running) { dlg = Dlg.DELETE }
        }
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
            onConfirm = { scope.launch { VmBox.vms.reset(v) } }, onDismiss = { dlg = null })
        Dlg.REPAIR -> ConfirmDialog(stringResource(R.string.repair_vm), stringResource(R.string.repair_vm_confirm), stringResource(R.string.repair),
            onConfirm = { scope.launch { VmBox.vms.repair(v) } }, onDismiss = { dlg = null })
        Dlg.DELETE -> ConfirmDialog(stringResource(R.string.delete_vm), stringResource(R.string.delete_vm_confirm, v.name), stringResource(R.string.delete), danger = true,
            onConfirm = { scope.launch { VmBox.vms.delete(v); onDeleted() } }, onDismiss = { dlg = null })
        null -> Unit
    }
}

@Composable
private fun navLabel(v: String) = stringResource(
    when (v) { "none" -> R.string.nav_none; "recent_home_back" -> R.string.nav_keys_alt; else -> R.string.nav_keys }
)
