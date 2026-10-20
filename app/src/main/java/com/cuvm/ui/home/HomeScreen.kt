package com.cuvm.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.AddCircleOutline
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PowerSettingsNew
import androidx.compose.material.icons.outlined.StayCurrentPortrait
import androidx.compose.material.icons.automirrored.outlined.VolumeOff
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.data.AppPrefs
import com.cuvm.data.Vm
import com.cuvm.engine.VmState
import com.cuvm.engine.active
import com.cuvm.ui.common.ConfirmDialog
import com.cuvm.ui.common.PrefRow
import com.cuvm.ui.profile.ProfileTab
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    onCreate: () -> Unit, onOpen: (String) -> Unit, onSettings: (String) -> Unit, onAdvanced: (String) -> Unit,
    onBackup: () -> Unit, onAppSettings: () -> Unit, onActivation: () -> Unit, onInfo: () -> Unit,
) {
    val vms by CuciVm.vms.vms.collectAsStateWithLifecycle(emptyList())
    val states by CuciVm.runtime.states.collectAsStateWithLifecycle()
    val prefs by CuciVm.prefs.flow.collectAsStateWithLifecycle(AppPrefs())
    val scope = rememberCoroutineScope()
    var tab by rememberSaveable { mutableStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val pager = rememberPagerState { vms.size }
    val current = vms.getOrNull(pager.currentPage)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(if (tab == 0) R.string.tab_vm else R.string.tab_you), style = MaterialTheme.typography.headlineMedium) },
                actions = {
                    if (tab == 0) {
                        val grid = prefs.homeLayout == "grid"
                        IconButton(onClick = { scope.launch { CuciVm.prefs.setLayout(if (grid) "pager" else "grid") } }) {
                            Icon(if (grid) Icons.Outlined.StayCurrentPortrait else Icons.Outlined.GridView, stringResource(R.string.change_layout))
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.AddCircleOutline, stringResource(R.string.more_options)) }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                DropdownMenuItem(text = { Text(stringResource(R.string.create_vm)) }, onClick = { menu = false; onCreate() })
                                DropdownMenuItem(text = { Text(stringResource(R.string.backup_restore)) }, onClick = { menu = false; onBackup() })
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.small_window)) }, enabled = current != null,
                                    onClick = { menu = false; current?.let { onAdvanced(it.id) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.preview_mode)) },
                                    trailingIcon = { Checkbox(checked = prefs.previewMode, onCheckedChange = null) },
                                    onClick = { scope.launch { CuciVm.prefs.setPreview(!prefs.previewMode) } },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.advanced_options)) }, enabled = current != null,
                                    onClick = { menu = false; current?.let { onAdvanced(it.id) } },
                                )
                            }
                        }
                    }
                },
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(tab == 0, { tab = 0 }, { Icon(Icons.Filled.Android, null) }, label = { Text(stringResource(R.string.tab_vm)) })
                NavigationBarItem(tab == 1, { tab = 1 }, { Icon(Icons.Filled.Person, null) }, label = { Text(stringResource(R.string.tab_you)) })
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when {
                tab == 1 -> ProfileTab(onAppSettings, onActivation, onInfo)
                vms.isEmpty() -> EmptyHome(onCreate)
                prefs.homeLayout == "grid" -> LazyVerticalGrid(
                    GridCells.Fixed(2), contentPadding = PaddingValues(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    items(vms, key = { it.id }) { vm ->
                        VmCard(vm, states[vm.id] ?: VmState.Stopped, compact = true, onOpen, onSettings, Modifier.aspectRatio(0.58f))
                    }
                }
                else -> Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                    HorizontalPager(pager, Modifier.weight(1f), contentPadding = PaddingValues(horizontal = 48.dp), pageSpacing = 16.dp) { page ->
                        val vm = vms[page]
                        Box(Modifier.fillMaxSize().padding(vertical = 24.dp), contentAlignment = Alignment.Center) {
                            VmCard(vm, states[vm.id] ?: VmState.Stopped, compact = false, onOpen, onSettings, Modifier.fillMaxWidth().aspectRatio(9f / 16f, matchHeightConstraintsFirst = true))
                        }
                    }
                    Text("${pager.currentPage + 1}/${vms.size}", color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 12.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyHome(onCreate: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(stringResource(R.string.empty_home_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.empty_home_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        FilledTonalButton(onClick = onCreate) { Text(stringResource(R.string.create_vm)) }
    }
}

@Composable
fun VmCard(vm: Vm, state: VmState, compact: Boolean, onOpen: (String) -> Unit, onSettings: (String) -> Unit, modifier: Modifier = Modifier) {
    val scope = rememberCoroutineScope()
    var confirmStop by remember { mutableStateOf(false) }
    OutlinedCard(modifier, border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                vm.name, style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            StateBlock(vm, state, compact, onOpen)
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                IconButton(onClick = { if (state.active) confirmStop = true else { CuciVm.runtime.start(vm.id); if (!vm.bootInBackground) onOpen(vm.id) } }) {
                    Icon(Icons.Outlined.PowerSettingsNew, stringResource(if (state.active) R.string.shutdown else R.string.start), tint = MaterialTheme.colorScheme.primary)
                }
                val muteLabel = stringResource(if (vm.muted) R.string.audio_off else R.string.audio_on)
                IconToggleButton(
                    checked = vm.muted, onCheckedChange = { scope.launch { CuciVm.vms.update(vm.copy(muted = it)) } },
                    modifier = Modifier.semantics { stateDescription = muteLabel },
                ) {
                    Icon(
                        if (vm.muted) Icons.AutoMirrored.Outlined.VolumeOff else Icons.AutoMirrored.Outlined.VolumeUp,
                        stringResource(R.string.mute), tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = { onSettings(vm.id) }) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.settings), tint = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
    if (confirmStop) ConfirmDialog(
        stringResource(R.string.shutdown), stringResource(R.string.shutdown_confirm, vm.name), stringResource(R.string.confirm),
        onConfirm = { CuciVm.runtime.stop(vm.id) }, onDismiss = { confirmStop = false },
    )
}

@Composable
private fun StateBlock(vm: Vm, state: VmState, compact: Boolean, onOpen: (String) -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
        val text = stateText(state)
        Text(
            text, textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
            style = if (compact) MaterialTheme.typography.bodyMedium else MaterialTheme.typography.bodyLarge,
            color = if (state is VmState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        when (state) {
            is VmState.Preparing, VmState.Booting, VmState.Stopping -> CircularProgressIndicator(Modifier.size(40.dp))
            is VmState.Running -> FilledTonalButton(onClick = { onOpen(vm.id) }) { Text(stringResource(R.string.open)) }
            else -> FilledTonalButton(onClick = { CuciVm.runtime.start(vm.id); if (!vm.bootInBackground) onOpen(vm.id) }) {
                Text(stringResource(if (state is VmState.Error) R.string.retry else R.string.start))
            }
        }
    }
}

@Composable
fun stateText(state: VmState): String = when (state) {
    VmState.Stopped -> stringResource(R.string.tagline)
    is VmState.Preparing -> when {
        state.step == "extract" -> stringResource(R.string.state_extract)
        state.step.startsWith("extract:") -> stringResource(R.string.state_extract) + " " + state.step.removePrefix("extract:") + "%"
        state.step == "patch" -> stringResource(R.string.state_patch)
        state.step.startsWith("setup") -> stringResource(R.string.state_setup) + state.step.removePrefix("setup").removePrefix(":").let { if (it.isBlank()) "" else "\n" + it.trim() }
        else -> stringResource(R.string.state_booting)
    }
    VmState.Booting -> stringResource(R.string.state_booting)
    is VmState.Running -> stringResource(R.string.state_running)
    VmState.Stopping -> stringResource(R.string.state_stopping)
    is VmState.Error -> stringResource(R.string.state_error, state.message)
}
