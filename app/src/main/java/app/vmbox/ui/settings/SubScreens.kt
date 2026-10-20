package app.vmbox.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.vmbox.R
import app.vmbox.VmBox
import app.vmbox.data.Identity
import app.vmbox.data.VmIdentity
import app.vmbox.data.VmNetwork
import app.vmbox.ui.common.CategoryHeader
import app.vmbox.ui.common.ChoiceDialog
import app.vmbox.ui.common.DetailScaffold
import app.vmbox.ui.common.PrefRow
import app.vmbox.ui.common.SwitchRow
import app.vmbox.ui.common.TextDialog
import app.vmbox.ui.create.ResolutionPicker
import app.vmbox.ui.create.Resolution
import kotlinx.coroutines.launch

/** One editable text field: label, current value, how to write it back. */
private class Field(val label: String, val value: String?, val validate: (String) -> Boolean = { true }, val save: (String) -> Unit)

@Composable
private fun FieldList(title: String, onBack: () -> Unit, groups: List<Pair<String?, List<Field>>>, extra: @Composable () -> Unit = {}) {
    var editing by remember { mutableStateOf<Field?>(null) }
    DetailScaffold(title, onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            groups.forEach { (header, fields) ->
                header?.let { CategoryHeader(it) }
                fields.forEach { f -> PrefRow(f.label, f.value?.ifEmpty { null } ?: stringResource(R.string.not_set)) { editing = f } }
            }
            extra()
        }
    }
    editing?.let { f -> TextDialog(f.label, f.value ?: "", validate = f.validate, onOk = f.save, onDismiss = { editing = null }) }
}

@Composable
fun ResolutionScreen(id: String, onBack: () -> Unit) {
    val vm by VmBox.vms.vm(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val v = vm ?: return
    DetailScaffold(stringResource(R.string.resolution), onBack) { mod ->
        ResolutionPicker(Resolution(v.width, v.height, v.dpi), { r ->
            scope.launch { VmBox.vms.update(v.copy(width = r.w, height = r.h, dpi = r.dpi)) }
        }, mod)
    }
}

@Composable
fun SensorScreen(id: String, onBack: () -> Unit) {
    val sensors by VmBox.vms.sensors(id).collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf<String?>(null) }
    val modes = listOf("host" to stringResource(R.string.sensor_host), "present_only" to stringResource(R.string.sensor_present), "off" to stringResource(R.string.sensor_off))
    DetailScaffold(stringResource(R.string.sensors), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            sensors.forEach { s -> PrefRow(sensorLabel(s.sensor), modes.first { it.first == s.mode }.second) { editing = s.sensor } }
        }
    }
    editing?.let { name ->
        val s = sensors.first { it.sensor == name }
        ChoiceDialog(sensorLabel(name), modes, s.mode, { m -> scope.launch { VmBox.vms.updateSensor(s.copy(mode = m)) } }, { editing = null })
    }
}

@Composable
private fun sensorLabel(s: String) = stringResource(
    when (s) {
        "gyroscope" -> R.string.sensor_gyro; "accelerometer" -> R.string.sensor_accel; "orientation" -> R.string.sensor_orientation
        "temperature" -> R.string.sensor_temperature; "pressure" -> R.string.sensor_pressure; "gravity" -> R.string.sensor_gravity
        "light" -> R.string.sensor_light; else -> R.string.sensor_proximity
    }
)

@Composable
fun LocationScreen(id: String, onBack: () -> Unit) {
    val loc by VmBox.vms.location(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    var picking by remember { mutableStateOf(false) }
    val l = loc ?: return
    val coords = if (l.lat != null && l.lng != null) "%.5f, %.5f".format(l.lat, l.lng) else null
    FieldList(
        stringResource(R.string.location), onBack,
        listOf(null to listOf(Field(stringResource(R.string.location_point), coords, ::parseLatLng) { t ->
            parseLatLng2(t)?.let { (a, b) -> scope.launch { VmBox.vms.updateLocation(l.copy(type = "specified", lat = a, lng = b)) } }
        })),
    ) {
        PrefRow(stringResource(R.string.location_type), stringResource(if (l.type == "specified") R.string.location_specified else R.string.location_device)) { picking = true }
    }
    if (picking) ChoiceDialog(
        stringResource(R.string.location_type),
        listOf("device_realtime" to stringResource(R.string.location_device), "specified" to stringResource(R.string.location_specified)),
        l.type, { t -> if (t == "device_realtime" || coords != null) scope.launch { VmBox.vms.updateLocation(l.copy(type = t)) } }, { picking = false },
    )
}

private fun parseLatLng2(s: String): Pair<Double, Double>? {
    val p = s.split(',', ' ', ';').filter { it.isNotBlank() }.mapNotNull { it.toDoubleOrNull() }
    return if (p.size == 2 && p[0] in -90.0..90.0 && p[1] in -180.0..180.0) p[0] to p[1] else null
}
private fun parseLatLng(s: String) = parseLatLng2(s) != null

private val MAC = Regex("^([0-9a-fA-F]{2}:){5}[0-9a-fA-F]{2}$")
private val IPV4 = Regex("^((25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(25[0-5]|2[0-4]\\d|1?\\d?\\d)$")

@Composable
fun NetworkScreen(id: String, onBack: () -> Unit) {
    val net by VmBox.vms.network(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val n = net ?: return
    fun save(f: (VmNetwork) -> VmNetwork) = scope.launch { VmBox.vms.updateNetwork(f(n)) }
    val host = stringResource(R.string.use_host)
    FieldList(
        stringResource(R.string.network), onBack,
        listOf(
            stringResource(R.string.wifi) to listOf(
                Field("SSID", n.wifiSsid, { it.isNotBlank() && it.length <= 32 }) { t -> save { it.copy(wifiSsid = t) } },
                Field("BSSID", n.wifiBssid, { MAC.matches(it) }) { t -> save { it.copy(wifiBssid = t.lowercase()) } },
            ),
            stringResource(R.string.nic) to listOf(
                Field(stringResource(R.string.mac_address), n.nicMac, { MAC.matches(it) }) { t -> save { it.copy(nicMac = t.lowercase()) } },
                Field("IPv4", n.ipv4, { IPV4.matches(it) }) { t -> save { it.copy(ipv4 = t) } },
                Field(stringResource(R.string.gateway), n.gatewayIpv4, { IPV4.matches(it) }) { t -> save { it.copy(gatewayIpv4 = t) } },
                Field(stringResource(R.string.ipv6_prefix), if (n.ipv6PrefixMode == "host") host else n.ipv6Prefix) { t ->
                    save { if (t.isBlank()) it.copy(ipv6PrefixMode = "host", ipv6Prefix = null) else it.copy(ipv6PrefixMode = "custom", ipv6Prefix = t) }
                },
                Field("DNS (IPv4)", if (n.dns4Mode == "host") host else n.dns4, { it.isBlank() || IPV4.matches(it) }) { t ->
                    save { if (t.isBlank()) it.copy(dns4Mode = "host", dns4 = null) else it.copy(dns4Mode = "custom", dns4 = t) }
                },
            ),
        ),
    ) {
        SwitchRow(stringResource(R.string.use_device_wifi_list), n.useDeviceWifiList) { c -> save { it.copy(useDeviceWifiList = c) } }
    }
}

@Composable
fun PhoneScreen(id: String, onBack: () -> Unit) {
    val ident by VmBox.vms.identity(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val i = ident ?: return
    fun save(f: (VmIdentity) -> VmIdentity) = scope.launch { VmBox.vms.updateIdentity(f(i)) }
    FieldList(
        stringResource(R.string.phone), onBack,
        listOf(null to listOf(
            Field(stringResource(R.string.phone_type), i.phoneType, { it in setOf("GSM", "CDMA", "NONE") }) { t -> save { it.copy(phoneType = t) } },
            Field("IMEI", i.imei, { it.length == 15 && it.all(Char::isDigit) && it.last() - '0' == Identity.luhnDigit(it.dropLast(1)) }) { t -> save { it.copy(imei = t) } },
            Field(stringResource(R.string.baseband), i.baseband) { t -> save { it.copy(baseband = t) } },
            Field(stringResource(R.string.carrier_code), i.carrierMccMnc, { it.length in 5..6 && it.all(Char::isDigit) }) { t -> save { it.copy(carrierMccMnc = t) } },
            Field(stringResource(R.string.carrier_name), i.carrierName) { t -> save { it.copy(carrierName = t) } },
            Field(stringResource(R.string.network_type), i.networkType) { t -> save { it.copy(networkType = t) } },
        )),
    ) {
        SwitchRow(stringResource(R.string.sim_inserted), i.simInserted) { c -> save { it.copy(simInserted = c) } }
        SwitchRow(stringResource(R.string.roaming), i.roaming) { c -> save { it.copy(roaming = c) } }
        PrefRow(stringResource(R.string.new_imei), stringResource(R.string.new_imei_summary)) { save { it.copy(imei = Identity.imei()) } }
    }
}

@Composable
fun HardwareScreen(id: String, onBack: () -> Unit) {
    val ident by VmBox.vms.identity(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val i = ident ?: return
    fun save(f: (VmIdentity) -> VmIdentity) = scope.launch { VmBox.vms.updateIdentity(f(i)) }
    val nonBlank: (String) -> Boolean = { it.isNotBlank() && it.length <= 64 }
    FieldList(
        stringResource(R.string.hardware), onBack,
        listOf(
            stringResource(R.string.device_info) to listOf(
                Field(stringResource(R.string.model), i.model, nonBlank) { t -> save { it.copy(model = t) } },
                Field(stringResource(R.string.brand), i.brand, nonBlank) { t -> save { it.copy(brand = t) } },
                Field(stringResource(R.string.product), i.product, nonBlank) { t -> save { it.copy(product = t) } },
                Field(stringResource(R.string.device), i.device, nonBlank) { t -> save { it.copy(device = t) } },
                Field(stringResource(R.string.manufacturer), i.manufacturer, nonBlank) { t -> save { it.copy(manufacturer = t) } },
            ),
            stringResource(R.string.device_ids) to listOf(
                Field(stringResource(R.string.serial), i.serial, { it.matches(Regex("[A-Za-z0-9]{6,20}")) }) { t -> save { it.copy(serial = t) } },
                Field("Android ID", i.androidId, { it.matches(Regex("[0-9a-f]{16}")) }) { t -> save { it.copy(androidId = t) } },
                Field(stringResource(R.string.processor), i.processorInfo) { t -> save { it.copy(processorInfo = t) } },
            ),
        ),
    ) {
        PrefRow(stringResource(R.string.reset_ids), stringResource(R.string.reset_ids_summary)) {
            val fresh = Identity.newIdentity(i.vmId, 0)
            save { it.copy(serial = fresh.serial, androidId = fresh.androidId) }
        }
    }
}

@Composable
fun AdvancedScreen(id: String, onBack: () -> Unit) {
    val vm by VmBox.vms.vm(id).collectAsStateWithLifecycle(null)
    val scope = rememberCoroutineScope()
    val v = vm ?: return
    fun save(f: (app.vmbox.data.Vm) -> app.vmbox.data.Vm) = scope.launch { VmBox.vms.update(f(v)) }
    DetailScaffold(stringResource(R.string.advanced_options), onBack) { mod ->
        Column(mod.fillMaxSize().verticalScroll(rememberScrollState())) {
            CategoryHeader(stringResource(R.string.cat_window))
            SwitchRow(stringResource(R.string.small_window), v.smallWindow, stringResource(R.string.small_window_summary)) { c -> save { it.copy(smallWindow = c) } }
            SwitchRow(stringResource(R.string.show_mini_ball), v.miniBall) { c -> save { it.copy(miniBall = c) } }
            CategoryHeader(stringResource(R.string.cat_boot))
            SwitchRow(stringResource(R.string.boot_background), v.bootInBackground) { c -> save { it.copy(bootInBackground = c) } }
            SwitchRow(stringResource(R.string.boot_animation), v.bootAnimation) { c -> save { it.copy(bootAnimation = c) } }
        }
    }
}
