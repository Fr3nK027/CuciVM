package com.cuvm.ui.display

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.cuvm.CuciVm
import com.cuvm.R
import com.cuvm.data.Vm
import com.cuvm.engine.VmState
import com.cuvm.ui.common.ConfirmDialog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Launcher of an app-space VM: the apps cloned into it, plus import. */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AppSpaceScreen(vm: Vm, onToPhone: () -> Unit, onSettings: () -> Unit) {
    val state by CuciVm.runtime.state(vm.id).collectAsStateWithLifecycle(VmState.Stopped)
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    val ctx = LocalContext.current
    var importing by remember { mutableStateOf(false) }
    var refresh by remember { mutableIntStateOf(0) }
    var removing by remember { mutableStateOf<ApplicationInfo?>(null) }
    val user by produceState<Int?>(null, vm.id) { value = CuciVm.vms.spaceUser(vm) }
    LaunchedEffect(state) { if (state is VmState.Stopped || state is VmState.Error) CuciVm.runtime.start(vm.id) }
    val apps by produceState<List<ApplicationInfo>?>(null, user, refresh, state) {
        val u = user ?: return@produceState
        if (state !is VmState.Running) return@produceState
        value = withContext(Dispatchers.IO) { runCatching { CuciVm.space.apps(u) }.getOrDefault(emptyList()) }
    }
    val failed = stringResource(R.string.app_start_failed)

    if (importing && user != null) {
        ImportAppsScreen(user!!, onDone = { importing = false; refresh++ })
        return
    }
    BackHandler(onBack = onToPhone)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(vm.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = onToPhone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.to_phone)) } },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.settings)) } },
            )
        },
        floatingActionButton = {
            if (state is VmState.Running) ExtendedFloatingActionButton(
                onClick = { importing = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text(stringResource(R.string.import_apps)) },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val list = apps
            when {
                state is VmState.Error -> Text(stringResource(R.string.state_error, (state as VmState.Error).message), color = MaterialTheme.colorScheme.error, modifier = Modifier.align(Alignment.Center).padding(32.dp))
                list == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                list.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(stringResource(R.string.space_empty_title), style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
                    Spacer(Modifier.heightIn(min = 8.dp))
                    Text(stringResource(R.string.space_empty_body), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                }
                else -> LazyVerticalGrid(GridCells.Adaptive(88.dp), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    items(list, key = { it.packageName }) { app ->
                        AppTile(
                            app,
                            onClick = {
                                scope.launch {
                                    val ok = withContext(Dispatchers.IO) {
                                        runCatching { CuciVm.space.launch(app.packageName, user!!, CuciVm.vms.locationNow(vm.id)) }.getOrDefault(false)
                                    }
                                    if (!ok) snack.showSnackbar(failed)
                                }
                            },
                            onLongClick = { removing = app },
                        )
                    }
                }
            }
        }
    }

    removing?.let { app ->
        val label = remember(app) { app.loadLabel(ctx.packageManager).toString() }
        ConfirmDialog(
            stringResource(R.string.remove_app), stringResource(R.string.remove_app_confirm, label), stringResource(R.string.delete), danger = true,
            onConfirm = { scope.launch { withContext(Dispatchers.IO) { CuciVm.space.remove(app.packageName, user!!) }; refresh++ } },
            onDismiss = { removing = null },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppTile(app: ApplicationInfo, onClick: () -> Unit, onLongClick: () -> Unit) {
    val pm = LocalContext.current.packageManager
    val icon = rememberIcon(app, pm)
    val label = remember(app) { app.loadLabel(pm).toString() }
    Column(
        Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = stringResource(R.string.remove_app)).padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) Image(icon, contentDescription = null, modifier = Modifier.size(56.dp)) else Spacer(Modifier.size(56.dp))
        Text(label, style = MaterialTheme.typography.labelMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
    }
}

@Composable
private fun rememberIcon(app: ApplicationInfo, pm: PackageManager): ImageBitmap? {
    val bmp by produceState<ImageBitmap?>(null, app.packageName) {
        value = withContext(Dispatchers.IO) { runCatching { app.loadIcon(pm).toBitmap(144, 144).asImageBitmap() }.getOrNull() }
    }
    return bmp
}

/** Phone apps to clone into the space, or an APK file. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ImportAppsScreen(user: Int, onDone: () -> Unit) {
    val ctx = LocalContext.current
    val pm = ctx.packageManager
    val scope = rememberCoroutineScope()
    val snack = remember { SnackbarHostState() }
    var query by remember { mutableStateOf("") }
    var showSystem by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var done by remember { mutableStateOf(setOf<String>()) }
    val failed = stringResource(R.string.import_failed)
    BackHandler(onBack = onDone)

    val host by produceState<List<Pair<ApplicationInfo, String>>?>(null) {
        value = withContext(Dispatchers.IO) {
            pm.getInstalledApplications(0)
                .filter { it.packageName != ctx.packageName && pm.getLaunchIntentForPackage(it.packageName) != null }
                .map { it to it.loadLabel(pm).toString() }
                .sortedBy { it.second.lowercase() }
        }
    }
    val installed by produceState(emptySet<String>(), user) {
        value = withContext(Dispatchers.IO) { runCatching { CuciVm.space.apps(user).map { it.packageName }.toSet() }.getOrDefault(emptySet()) }
    }

    fun import(key: String, block: () -> Result<Unit>) {
        busy = key
        scope.launch {
            val r = withContext(Dispatchers.IO) { runCatching { block() }.getOrElse { Result.failure(it) } }
            busy = null
            if (r.isSuccess) done = done + key else snack.showSnackbar(failed + ": " + (r.exceptionOrNull()?.message ?: ""))
        }
    }
    val apk = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) import(uri.toString()) { CuciVm.space.importApk(uri, user) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.import_apps)) },
                navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snack) },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(query, { query = it }, singleLine = true, label = { Text(stringResource(R.string.search)) }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                FilterChip(selected = showSystem, onClick = { showSystem = !showSystem }, label = { Text(stringResource(R.string.system_apps)) })
            }
            OutlinedButton(onClick = { apk.launch(arrayOf("application/vnd.android.package-archive")) }, modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Text(stringResource(R.string.import_apk_file))
            }
            val list = host
            if (list == null) Box(Modifier.fillMaxSize()) { CircularProgressIndicator(Modifier.align(Alignment.Center)) }
            else LazyColumn {
                val shown = list.filter { (a, l) ->
                    (showSystem || a.flags and ApplicationInfo.FLAG_SYSTEM == 0) &&
                        (query.isBlank() || l.contains(query, true) || a.packageName.contains(query, true))
                }
                items(shown, key = { it.first.packageName }) { (app, label) ->
                    val icon = rememberIcon(app, pm)
                    Row(Modifier.fillMaxWidth().heightIn(min = 64.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        if (icon != null) Image(icon, null, Modifier.size(40.dp)) else Spacer(Modifier.size(40.dp))
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(app.packageName, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        val pkg = app.packageName
                        when {
                            busy == pkg -> CircularProgressIndicator(Modifier.size(24.dp))
                            pkg in installed || pkg in done -> Text(stringResource(R.string.imported), color = MaterialTheme.colorScheme.primary)
                            else -> TextButton(enabled = busy == null, onClick = { import(pkg) { CuciVm.space.importInstalled(pkg, user) } }) {
                                Text(stringResource(R.string.import_one))
                            }
                        }
                    }
                }
            }
        }
    }
}
