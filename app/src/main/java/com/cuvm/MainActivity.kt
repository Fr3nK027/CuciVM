package com.cuvm

import android.content.Intent
import android.os.Bundle
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cuvm.data.AppPrefs
import com.cuvm.ui.create.CreateVmScreen
import com.cuvm.ui.display.DisplayScreen
import com.cuvm.ui.home.HomeScreen
import com.cuvm.ui.profile.ActivationScreen
import com.cuvm.ui.profile.AppSettingsScreen
import com.cuvm.ui.profile.BackupScreen
import com.cuvm.ui.profile.InfoScreen
import com.cuvm.ui.settings.AdvancedScreen
import com.cuvm.ui.settings.HardwareScreen
import com.cuvm.ui.settings.LocationScreen
import com.cuvm.ui.settings.NetworkScreen
import com.cuvm.ui.settings.PhoneScreen
import com.cuvm.ui.settings.ResolutionScreen
import com.cuvm.ui.settings.SensorScreen
import com.cuvm.ui.settings.VmSettingsScreen
import com.cuvm.ui.theme.CuciVmTheme

class MainActivity : ComponentActivity() {
    /** VM id to show right away (intent extra "open_vm": shortcuts, tests). */
    private var openVm by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        openVm = intent?.getStringExtra(EXTRA_OPEN_VM)
        setContent {
            val prefs by CuciVm.prefs.flow.collectAsStateWithLifecycle(AppPrefs())
            CuciVmTheme(prefs.theme, prefs.style) { AppNav(openVm) { openVm = null } }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_OPEN_VM)?.let { openVm = it }
    }

    companion object { const val EXTRA_OPEN_VM = "open_vm" }
}

@Composable
private fun AppNav(openVm: String?, onOpened: () -> Unit) {
    val nav = rememberNavController()
    LaunchedEffect(openVm) {
        if (openVm != null) { nav.navigate("display/$openVm") { popUpTo("home") }; onOpened() }
    }
    val back: () -> Unit = { nav.popBackStack() }
    val vmArg = listOf(navArgument("id") { type = NavType.StringType })
    NavHost(nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                onCreate = { nav.navigate("create") },
                onOpen = { nav.navigate("display/$it") },
                onSettings = { nav.navigate("settings/$it") },
                onAdvanced = { nav.navigate("advanced/$it") },
                onBackup = { nav.navigate("backup") },
                onAppSettings = { nav.navigate("app_settings") },
                onActivation = { nav.navigate("activation") },
                onInfo = { nav.navigate("info") },
            )
        }
        composable("create") { CreateVmScreen(onBack = back, onDone = back) }
        composable("display/{id}", vmArg) {
            val id = it.arguments!!.getString("id")!!
            DisplayScreen(
                id,
                onToPhone = { nav.popBackStack("home", inclusive = false) },
                onSettings = { nav.navigate("settings/$id") },
                onSwitch = { other -> nav.navigate("display/$other") { popUpTo("home") } },
            )
        }
        composable("settings/{id}", vmArg) {
            val id = it.arguments!!.getString("id")!!
            VmSettingsScreen(id, onBack = back, onOpen = { sub -> nav.navigate("$sub/$id") }, onDeleted = { nav.popBackStack("home", false) })
        }
        composable("resolution/{id}", vmArg) { ResolutionScreen(it.arguments!!.getString("id")!!, back) }
        composable("sensors/{id}", vmArg) { SensorScreen(it.arguments!!.getString("id")!!, back) }
        composable("location/{id}", vmArg) { LocationScreen(it.arguments!!.getString("id")!!, back) }
        composable("network/{id}", vmArg) { NetworkScreen(it.arguments!!.getString("id")!!, back) }
        composable("phone/{id}", vmArg) { PhoneScreen(it.arguments!!.getString("id")!!, back) }
        composable("hardware/{id}", vmArg) { HardwareScreen(it.arguments!!.getString("id")!!, back) }
        composable("advanced/{id}", vmArg) { AdvancedScreen(it.arguments!!.getString("id")!!, back) }
        composable("backup") { BackupScreen(back) }
        composable("app_settings") { AppSettingsScreen(back) }
        composable("activation") { ActivationScreen(back) }
        composable("info") { InfoScreen(back) }
    }
}
