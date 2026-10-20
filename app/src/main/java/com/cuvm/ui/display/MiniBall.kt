package com.cuvm.ui.display

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.ContentPaste
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.engine.VmState
import com.cuvm.ui.common.ConfirmDialog
import kotlin.math.roundToInt

private data class Action(val icon: ImageVector, val label: Int, val run: () -> Unit)

@Composable
fun BoxScope.MiniBall(
    selfId: String, onShutdown: () -> Unit, onToPhone: () -> Unit, onSettings: () -> Unit,
    onSwitch: (String) -> Unit, onKeyboard: () -> Unit, onPaste: () -> Unit,
) {
    var drag by remember { mutableStateOf(Offset.Zero) }
    var open by remember { mutableStateOf(false) }
    var confirmOff by remember { mutableStateOf(false) }
    var switching by remember { mutableStateOf(false) }
    val label = stringResource(R.string.vm_controls)

    Surface(
        shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh, shadowElevation = 8.dp,
        modifier = Modifier.align(Alignment.CenterEnd).padding(end = 4.dp)
            .offset { IntOffset(drag.x.roundToInt(), drag.y.roundToInt()) }
            .size(48.dp).alpha(0.85f)
            .pointerInput(Unit) { detectDragGestures { change, d -> change.consume(); drag += d } }
            .clickable { open = true }
            .semantics { contentDescription = label; role = Role.Button },
    ) {
        Icon(Icons.Outlined.Tune, null, Modifier.padding(12.dp), tint = MaterialTheme.colorScheme.primary)
    }

    if (open) {
        val actions = listOf(
            Action(Icons.Outlined.PowerSettingsNew, R.string.shutdown) { confirmOff = true },
            Action(Icons.Outlined.PhoneAndroid, R.string.to_phone, onToPhone),
            Action(Icons.Outlined.Keyboard, R.string.keyboard, onKeyboard),
            Action(Icons.Outlined.SwapHoriz, R.string.switch_vm) { switching = true },
            Action(Icons.Outlined.ContentPaste, R.string.paste_clipboard, onPaste),
            Action(Icons.Outlined.Settings, R.string.settings, onSettings),
            Action(Icons.AutoMirrored.Outlined.Undo, R.string.close) {},
        )
        Dialog(onDismissRequest = { open = false }) {
            Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                LazyVerticalGrid(GridCells.Fixed(3), Modifier.padding(12.dp)) {
                    items(actions) { a ->
                        Column(
                            Modifier.heightIn(min = 88.dp).clickable { open = false; a.run() }.padding(8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
                        ) {
                            Icon(a.icon, null, tint = MaterialTheme.colorScheme.onSurface)
                            Text(stringResource(a.label), style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center)
                        }
                    }
                }
            }
        }
    }

    if (confirmOff) ConfirmDialog(
        stringResource(R.string.shutdown), stringResource(R.string.shutdown_confirm_short), stringResource(R.string.confirm),
        onConfirm = onShutdown, onDismiss = { confirmOff = false },
    )

    if (switching) {
        val vms by CuciVm.vms.vms.collectAsStateWithLifecycle(emptyList())
        val states by CuciVm.runtime.states.collectAsStateWithLifecycle()
        val others = vms.filter { it.id != selfId && states[it.id] is VmState.Running }
        AlertDialog(
            onDismissRequest = { switching = false },
            title = { Text(stringResource(R.string.switch_vm)) },
            text = {
                Column {
                    if (others.isEmpty()) Text(stringResource(R.string.no_other_running))
                    others.forEach { o ->
                        Text(o.name, Modifier.fillMaxWidth().clickable { switching = false; onSwitch(o.id) }.padding(vertical = 14.dp))
                    }
                }
            },
            confirmButton = { TextButton(onClick = { switching = false }) { Text(stringResource(R.string.close)) } },
        )
    }
}
