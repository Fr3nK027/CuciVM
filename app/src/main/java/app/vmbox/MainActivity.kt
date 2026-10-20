package app.vmbox

import android.os.Bundle
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
import app.vmbox.data.AppPrefs
import app.vmbox.ui.create.CreateVmScreen
import app.vmbox.ui.display.DisplayScreen
import app.vmbox.ui.home.HomeScreen
import app.vmbox.ui.profile.ActivationScreen
import app.vmbox.ui.profile.AppSettingsScreen
import app.vmbox.ui.profile.BackupScreen
import app.vmbox.ui.profile.InfoScreen
import app.vmbox.ui.settings.AdvancedScreen
import app.vmbox.ui.settings.HardwareScreen
import app.vmbox.ui.settings.LocationScreen
import app.vmbox.ui.settings.NetworkScreen
import app.vmbox.ui.settings.PhoneScreen
import app.vmbox.ui.settings.ResolutionScreen
import app.vmbox.ui.settings.SensorScreen
import app.vmbox.ui.settings.VmSettingsScreen
import app.vmbox.ui.theme.VmBoxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val prefs by VmBox.prefs.flow.collectAsStateWithLifecycle(AppPrefs())
            VmBoxTheme(prefs.theme, prefs.style) { AppNav() }
        }
    }
}

@Composable
private fun AppNav() {
    val nav = rememberNavController()
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
