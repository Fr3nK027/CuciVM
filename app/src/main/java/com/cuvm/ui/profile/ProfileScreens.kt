package com.cuvm.ui.profile

import android.annotation.SuppressLint
import android.app.LocaleManager
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.LocaleList
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.cuvm.R
import com.cuvm.CuciVm
import com.cuvm.data.AppPrefs
import com.cuvm.ui.common.CategoryHeader
import com.cuvm.ui.common.ChoiceDialog
import com.cuvm.ui.common.DetailScaffold
import com.cuvm.ui.common.PrefRow
import kotlinx.coroutines.launch

@Composable
fun ProfileTab(onAppSettings: () -> Unit, onActivation: () -> Unit, onInfo: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        PrefRow(stringResource(R.string.app_settings), stringResource(R.string.app_settings_summary), onClick = onAppSettings)
        PrefRow(stringResource(R.string.activation), stringResource(R.string.activation_summary), onClick = onActivation)
        PrefRow(stringResource(R.string.info), stringResource(R.string.info_summary), onClick = onInfo)
    }
}

@Composable
fun AppSettingsScreen(onBack: () -> Unit) {
    val prefs by CuciVm.prefs.flow.collectAsStateWithLifecycle(AppPrefs())
    val scope = rememberCoroutineScope()
    val ctx = LocalContext.current
    var dlg by remember { mutableStateOf<String?>(null) }
    val langs = listOf("" to stringResource(R.string.follow_system), "it" to "Italiano", "en" to "English")
    val currentLang = if (Build.VERSION.SDK_INT >= 33)
        ctx.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags().substringBefore('-') else ""
    val themes = listOf("system" to stringResource(R.string.follow_system), "dark" to stringResource(R.string.theme_dark), "light" to stringResource(R.string.theme_light))
    val styles = listOf("slate" to stringResource(R.string.style_slate), "wallpaper" to stringResource(R.string.style_wallpaper))

    DetailScaffold(stringResource(R.string.app_settings), onBack) { mod ->
        Column(mod.fillMaxSize()) {
            CategoryHeader(stringResource(R.string.language))
            PrefRow(stringResource(R.string.language), langs.firstOrNull { it.first == currentLang }?.second ?: langs[0].second, enabled = Build.VERSION.SDK_INT >= 33) { dlg = "lang" }
            CategoryHeader(stringResource(R.string.appearance))
            PrefRow(stringResource(R.string.dark_theme), themes.first { it.first == prefs.theme }.second) { dlg = "theme" }
            PrefRow(stringResource(R.string.theme_style), styles.first { it.first == prefs.style }.second) { dlg = "style" }
        }
    }
    when (dlg) {
        "lang" -> ChoiceDialog(stringResource(R.string.language), langs, currentLang, { tag ->
            if (Build.VERSION.SDK_INT >= 33) ctx.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        }, { dlg = null })
        "theme" -> ChoiceDialog(stringResource(R.string.dark_theme), themes, prefs.theme, { scope.launch { CuciVm.prefs.setTheme(it) } }, { dlg = null })
        "style" -> ChoiceDialog(stringResource(R.string.theme_style), styles, prefs.style, { scope.launch { CuciVm.prefs.setStyle(it) } }, { dlg = null })
    }
}

/** Android 12+ kills apps with many child processes; a Linux desktop has many. Guide the user past it. */
@SuppressLint("BatteryLife")
@Composable
fun ActivationScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var refresh by remember { mutableIntStateOf(0) }
    // Re-read the state every time the user comes back from system settings.
    androidx.compose.runtime.LaunchedEffect(owner) { owner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { refresh++ } }
    val phantomOff = remember(refresh) {
        Settings.Global.getString(ctx.contentResolver, "settings_enable_monitor_phantom_procs") == "false"
    }
    val batteryOk = remember(refresh) { ctx.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(ctx.packageName) }
    val devOn = remember(refresh) { Settings.Global.getInt(ctx.contentResolver, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1 }

    DetailScaffold(stringResource(R.string.activation), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Column(Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                val ready = phantomOff && batteryOk
                Text(
                    stringResource(if (ready) R.string.activation_ready else R.string.activation_needed),
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                    color = if (ready) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                )
                Text(stringResource(R.string.activation_body), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            CategoryHeader(stringResource(R.string.step_1))
            PrefRow(
                stringResource(R.string.child_process_limit),
                stringResource(if (phantomOff) R.string.child_process_off else if (devOn) R.string.child_process_on else R.string.dev_options_locked),
            ) {
                val i = if (devOn) Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) else Intent(Settings.ACTION_DEVICE_INFO_SETTINGS)
                ctx.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            CategoryHeader(stringResource(R.string.step_2))
            PrefRow(stringResource(R.string.battery_opt), stringResource(if (batteryOk) R.string.battery_opt_off else R.string.battery_opt_on)) {
                ctx.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
            CategoryHeader(stringResource(R.string.adb_alternative))
            Text(
                "adb shell settings put global settings_enable_monitor_phantom_procs false",
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace),
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }
}

@Composable
fun InfoScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val version = remember { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }
    DetailScaffold(stringResource(R.string.info), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                Text(version ?: "", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(R.string.info_about), textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp))
            }
            CategoryHeader(stringResource(R.string.licences))
            PrefRow("PRoot", "GPL-2.0 · termux/proot")
            PrefRow("TigerVNC (Xvnc)", stringResource(R.string.licence_in_guest, "GPL-2.0"))
            PrefRow("Alpine Linux", stringResource(R.string.licence_alpine))
            PrefRow("AndroidX, Jetpack Compose, OkHttp", "Apache-2.0")
        }
    }
}

@Composable
fun BackupScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    var status by remember { mutableStateOf<String?>(null) }
    var working by remember { mutableStateOf(false) }
    val done = stringResource(R.string.restore_done)
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            working = true
            status = runCatching { CuciVm.backups.restore(uri) }.fold({ done.format(it.name) }, { it.message ?: it.toString() })
            working = false
        }
    }
    DetailScaffold(stringResource(R.string.backup_restore), onBack) { mod ->
        Column(mod.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text(stringResource(R.string.backup_how), textAlign = TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
            androidx.compose.material3.FilledTonalButton(onClick = { pick.launch(arrayOf("application/gzip", "application/x-gzip", "*/*")) }, enabled = !working, modifier = Modifier.padding(top = 24.dp)) {
                Text(stringResource(R.string.restore_from_file))
            }
            if (working) androidx.compose.material3.LinearProgressIndicator(Modifier.padding(top = 16.dp))
            status?.let { Text(it, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 16.dp)) }
        }
    }
}
