package com.cuvm

import android.app.Application
import android.content.Context
import com.cuvm.data.AppDb
import com.cuvm.data.BackupRepo
import com.cuvm.data.ImageRepo
import com.cuvm.data.Prefs
import com.cuvm.data.VmRepo
import com.cuvm.engine.AppSpace
import com.cuvm.engine.ProotEngine
import com.cuvm.engine.VmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.app.configuration.ClientConfiguration

/** Process-wide singletons of the main process. Single user: no DI framework needed. */
object CuciVm {
    lateinit var db: AppDb; private set
    lateinit var images: ImageRepo; private set
    lateinit var vms: VmRepo; private set
    lateinit var prefs: Prefs; private set
    lateinit var engine: ProotEngine; private set
    lateinit var space: AppSpace; private set
    lateinit var runtime: VmRuntime; private set
    lateinit var backups: BackupRepo; private set

    fun init(app: Application) {
        db = AppDb.build(app)
        space = AppSpace()
        images = ImageRepo(app, db.dao())
        vms = VmRepo(app, db.dao(), space)
        prefs = Prefs(app)
        engine = ProotEngine(app)
        runtime = VmRuntime(app, vms, engine, space)
        backups = BackupRepo(app, vms, db.dao())
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            engine.install()
            images.syncCatalogue()
        }
    }
}

class CuciVmApp : Application() {
    // The app-space engine hosts virtual apps in stub processes of this same package:
    // it must hook every process before anything else runs.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        val core = BlackBoxCore.get()
        core.closeCodeInit()
        core.onBeforeMainApplicationAttach(this, base)
        core.doAttachBaseContext(base, object : ClientConfiguration() {
            override fun getHostPackageName(): String = base.packageName
        })
        core.onAfterMainApplicationAttach(this, base)
    }

    override fun onCreate() {
        super.onCreate()
        BlackBoxCore.get().doCreate()
        // Virtual-app and engine-server processes need none of CuciVM's own singletons.
        if (BlackBoxCore.get().isMainProcess) CuciVm.init(this)
    }
}
