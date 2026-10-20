package app.vmbox.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Mirrors replica/schema.sql. Times are epoch millis UTC, ids are UUID strings.

@Entity(tableName = "guest_image", indices = [Index("status")])
data class GuestImage(
    @PrimaryKey val id: String,
    val os: String,              // linux | windows_apps | windows_full | android | other
    val name: String,
    val versionLabel: String,
    val arch: String,            // arm64 | arm32 | x86_64
    val engine: String,          // proot | qemu | android_container
    val source: String,          // catalogue | custom
    val downloadUrl: String?,
    val sha256: String?,
    val sizeBytes: Long,
    val localPath: String?,
    val status: String,          // remote | downloading | ready | failed
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "vm",
    foreignKeys = [ForeignKey(GuestImage::class, ["id"], ["imageId"], onDelete = ForeignKey.RESTRICT)],
    indices = [Index("imageId"), Index("sortOrder")],
)
data class Vm(
    @PrimaryKey val id: String,
    val imageId: String,
    val name: String,
    val sortOrder: Int = 0,
    val width: Int,
    val height: Int,
    val dpi: Int,
    val refreshRate: Int = 60,
    val cutoutMode: String = "overlap",          // overlap | exclude
    val navBar: String = "back_home_recent",     // none | back_home_recent | recent_home_back
    val ramMb: Int = 2048,
    val cpuCores: Int = 4,
    val diskGb: Int = 16,
    val gpuMode: String = "software",
    val shareClipboard: Boolean = true,
    val shareFolder: Boolean = false,
    val hostNotifications: Boolean = false,
    val camera: Boolean = false,
    val muted: Boolean = false,
    val miniBall: Boolean = true,
    val smallWindow: Boolean = false,
    val bootInBackground: Boolean = false,
    val bootAnimation: Boolean = false,
    val dataPath: String,
    val lastError: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Entity(
    tableName = "vm_identity",
    foreignKeys = [ForeignKey(Vm::class, ["id"], ["vmId"], onDelete = ForeignKey.CASCADE)],
)
data class VmIdentity(
    @PrimaryKey val vmId: String,
    val model: String,
    val brand: String,
    val product: String,
    val device: String,
    val manufacturer: String,
    val serial: String,
    val androidId: String,
    val processorInfo: String?,
    val phoneType: String = "GSM",
    val imei: String?,
    val baseband: String?,
    val simInserted: Boolean = true,
    val carrierMccMnc: String?,
    val carrierName: String?,
    val networkType: String = "LTE",
    val signal: String = "good",
    val roaming: Boolean = false,
    val updatedAt: Long,
)

@Entity(
    tableName = "vm_network",
    foreignKeys = [ForeignKey(Vm::class, ["id"], ["vmId"], onDelete = ForeignKey.CASCADE)],
)
data class VmNetwork(
    @PrimaryKey val vmId: String,
    val wifiSsid: String,
    val wifiBssid: String,
    val useDeviceWifiList: Boolean = false,
    val nicMac: String,
    val ipv4: String,
    val gatewayIpv4: String,
    val ipv6PrefixMode: String = "host",
    val ipv6Prefix: String? = null,
    val dns4Mode: String = "host",
    val dns4: String? = null,
    val updatedAt: Long,
)

@Entity(
    tableName = "vm_sensor",
    primaryKeys = ["vmId", "sensor"],
    foreignKeys = [ForeignKey(Vm::class, ["id"], ["vmId"], onDelete = ForeignKey.CASCADE)],
)
data class VmSensor(
    val vmId: String,
    val sensor: String,   // gyroscope | accelerometer | orientation | ...
    val mode: String,     // off | present_only | host
)

@Entity(
    tableName = "vm_location",
    foreignKeys = [ForeignKey(Vm::class, ["id"], ["vmId"], onDelete = ForeignKey.CASCADE)],
)
data class VmLocation(
    @PrimaryKey val vmId: String,
    val type: String = "device_realtime",   // device_realtime | specified
    val lat: Double? = null,
    val lng: Double? = null,
)

val SENSORS = listOf("gyroscope", "accelerometer", "orientation", "temperature", "pressure", "gravity", "light", "proximity")
