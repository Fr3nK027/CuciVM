package com.cuvm.engine

import android.content.pm.ApplicationInfo
import android.net.Uri
import com.cuvm.data.VmLocation
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.core.GmsCore
import top.niunaijun.blackbox.entity.location.BLocation
import top.niunaijun.blackbox.fake.frameworks.BLocationManager

/**
 * Android "VM" = one virtual user of the app-space engine (NewBlackbox, Apache-2.0).
 * Apps imported into it run natively, isolated from the phone and from other spaces.
 */
class AppSpace {
    private val core get() = BlackBoxCore.get()

    fun ensureUser(user: Int) {
        if (core.users.none { it.id == user }) core.createUser(user)
    }

    /** Apps the user put in the space; Google services added by the switch stay hidden. */
    fun apps(user: Int): List<ApplicationInfo> = allApps(user).filter { !GmsCore.isGoogleAppOrService(it.packageName) }

    private fun allApps(user: Int): List<ApplicationInfo> =
        core.getInstalledApplications(0, user).filter { it.packageName != BlackBoxCore.getHostPkg() }

    /** Clones an app already installed on the phone. */
    fun importInstalled(pkg: String, user: Int): Result<Unit> =
        result(core.installPackageAsUser(pkg, user)).onSuccess { if (needsGms(pkg)) enableGms(user) }

    /** Apps built with Google Play services declare its version in their manifest. */
    private fun needsGms(pkg: String) = runCatching {
        BlackBoxCore.getPackageManager().getApplicationInfo(pkg, android.content.pm.PackageManager.GET_META_DATA)
            .metaData?.containsKey("com.google.android.gms.version") == true
    }.getOrDefault(false)

    private fun enableGms(user: Int) {
        if (gmsSupported() && !gmsInstalled(user)) setGms(user, true)
    }

    fun importApk(uri: Uri, user: Int): Result<Unit> {
        val r = core.installPackageAsUser(uri, user)
        return result(r).onSuccess {
            val meta = runCatching {
                BlackBoxCore.getBPackageManager().getApplicationInfo(r.packageName, android.content.pm.PackageManager.GET_META_DATA, user)?.metaData
            }.getOrNull()
            if (meta?.containsKey("com.google.android.gms.version") == true) enableGms(user)
        }
    }

    private fun result(r: top.niunaijun.blackbox.entity.pm.InstallResult): Result<Unit> =
        if (r.success) Result.success(Unit) else Result.failure(IllegalStateException(r.msg ?: "install failed"))

    fun launch(pkg: String, user: Int, loc: VmLocation?, identity: com.cuvm.data.VmIdentity? = null): Boolean {
        applyLocation(pkg, user, loc)
        identity?.let { writeIdentity(user, it) }
        return core.launchApk(pkg, user)
    }

    /** What the space's apps see as this device (read by the engine when an app process starts). */
    private fun writeIdentity(user: Int, i: com.cuvm.data.VmIdentity) {
        val f = top.niunaijun.blackbox.core.SpaceIdentity.file(BlackBoxCore.getContext(), user)
        f.parentFile?.mkdirs()
        val p = java.util.Properties().apply {
            // Early VMs got placeholder makers ("CuciVM Virtual Phone"): those keep the phone's own.
            if (i.manufacturer != "CuciVM") {
                setProperty("model", i.model); setProperty("brand", i.brand); setProperty("manufacturer", i.manufacturer)
                setProperty("device", i.device); setProperty("product", i.product)
            }
            setProperty("serial", i.serial); setProperty("androidId", i.androidId); setProperty("imei", i.imei ?: "")
        }
        f.outputStream().use { p.store(it, null) }
    }

    fun remove(pkg: String, user: Int) = core.uninstallPackageAsUser(pkg, user)

    fun stopAll(user: Int) = allApps(user).forEach { core.stopPackage(it.packageName, user) }

    fun deleteUser(user: Int) = core.deleteUser(user)

    /** Google Play services from the phone, copied into this space (YouTube, Gmail... need them). */
    fun gmsSupported() = core.isSupportGms
    fun gmsInstalled(user: Int) = core.isInstallGms(user)
    fun setGms(user: Int, on: Boolean): Result<Unit> =
        if (on) result(core.installGms(user))
        else if (core.uninstallGms(user)) Result.success(Unit) else Result.failure(IllegalStateException("uninstall failed"))

    /** Wipes the space: all its apps and their data. */
    fun reset(user: Int) {
        deleteUser(user)
        core.createUser(user)
    }

    private fun applyLocation(pkg: String, user: Int, loc: VmLocation?) {
        val lm = BLocationManager.get()
        if (loc?.type == "specified" && loc.lat != null && loc.lng != null) {
            lm.setPattern(user, pkg, BLocationManager.OWN_MODE)
            lm.setLocation(user, pkg, BLocation(loc.lat, loc.lng))
        } else {
            lm.setPattern(user, pkg, BLocationManager.CLOSE_MODE)
        }
    }

    companion object {
        const val ENGINE = "app_space"
    }
}
