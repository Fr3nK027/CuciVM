package com.cuvm.engine

import android.content.pm.ApplicationInfo
import android.net.Uri
import com.cuvm.data.VmLocation
import top.niunaijun.blackbox.BlackBoxCore
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

    fun apps(user: Int): List<ApplicationInfo> =
        core.getInstalledApplications(0, user).filter { it.packageName != BlackBoxCore.getHostPkg() }

    /** Clones an app already installed on the phone. */
    fun importInstalled(pkg: String, user: Int): Result<Unit> = result(core.installPackageAsUser(pkg, user))

    fun importApk(uri: Uri, user: Int): Result<Unit> = result(core.installPackageAsUser(uri, user))

    private fun result(r: top.niunaijun.blackbox.entity.pm.InstallResult): Result<Unit> =
        if (r.success) Result.success(Unit) else Result.failure(IllegalStateException(r.msg ?: "install failed"))

    fun launch(pkg: String, user: Int, loc: VmLocation?): Boolean {
        applyLocation(pkg, user, loc)
        return core.launchApk(pkg, user)
    }

    fun remove(pkg: String, user: Int) = core.uninstallPackageAsUser(pkg, user)

    fun stopAll(user: Int) = apps(user).forEach { core.stopPackage(it.packageName, user) }

    fun deleteUser(user: Int) = core.deleteUser(user)

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
