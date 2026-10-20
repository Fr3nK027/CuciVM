package app.vmbox

import android.app.Application
import app.vmbox.data.AppDb
import app.vmbox.data.ImageRepo
import app.vmbox.data.Prefs
import app.vmbox.data.VmRepo
import app.vmbox.engine.ProotEngine
import app.vmbox.engine.VmRuntime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Process-wide singletons. Single user, single process: no DI framework needed. */
object VmBox {
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

class VmBoxApp : Application() {
    override fun onCreate() {
        super.onCreate()
        VmBox.init(this)
    }
}
