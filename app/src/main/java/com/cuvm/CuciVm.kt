package com.cuvm

import android.app.Application
import com.cuvm.data.AppDb
import com.cuvm.data.ImageRepo
import com.cuvm.data.Prefs
import com.cuvm.data.VmRepo
import com.cuvm.engine.ProotEngine
import com.cuvm.engine.VmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide singletons. Single user, single process: no DI framework needed. */
object CuciVm {
    lateinit var db: AppDb; private set
    lateinit var images: ImageRepo; private set
    lateinit var vms: VmRepo; private set
    lateinit var prefs: Prefs; private set
    lateinit var engine: ProotEngine; private set
    lateinit var runtime: VmRuntime; private set

    fun init(app: Application) {
        db = AppDb.build(app)
        images = ImageRepo(app, db.dao())
        vms = VmRepo(app, db.dao())
        prefs = Prefs(app)
        engine = ProotEngine(app)
        runtime = VmRuntime(app, vms, engine)
        CoroutineScope(SupervisorJob() + Dispatchers.IO).launch {
            engine.install()
            images.syncCatalogue()
        }
    }
}

class CuciVmApp : Application() {
    override fun onCreate() {
        super.onCreate()
        CuciVm.init(this)
    }
}
