package app.pratyahara

import android.app.Application
import app.pratyahara.data.AndroidClock
import app.pratyahara.data.Store
import app.pratyahara.domain.Engine
import app.pratyahara.notifications.Notifier
import app.pratyahara.work.MaintenanceWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class PratyaharaApp : Application() {

    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /** Manual dependency wiring: small enough that a DI framework isn't worth it. */
    lateinit var engine: Engine
        private set

    override fun onCreate() {
        super.onCreate()
        engine = Engine(Store(this, appScope), AndroidClock(this))
        Notifier.createChannels(this)
        MaintenanceWorker.schedule(this)
        appScope.launch {
            engine.store.loaded.first { it }
            engine.applyDuePending()
        }
    }
}

val android.content.Context.engine: Engine get() = (applicationContext as PratyaharaApp).engine
