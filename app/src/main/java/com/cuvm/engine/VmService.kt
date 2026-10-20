package com.cuvm.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.cuvm.MainActivity
import com.cuvm.R
import com.cuvm.CuciVm
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/** Keeps guest processes alive while any VM is active; stops itself when none is. */
class VmService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onCreate() {
        super.onCreate()
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        startForeground(ID, build(0))
        scope.launch {
            CuciVm.runtime.states.collectLatest { states ->
                val n = states.values.count { it.active }
                if (n == 0) stopSelf()
                else getSystemService(NotificationManager::class.java).notify(ID, build(n))
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP_ALL) CuciVm.runtime.stopAll()
        return START_NOT_STICKY
    }

    private fun build(running: Int): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, VmService::class.java).setAction(ACTION_STOP_ALL), PendingIntent.FLAG_IMMUTABLE,
        )
        return Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_vm)
            .setContentTitle(resources.getQuantityString(R.plurals.notif_running, running, running))
            .setContentIntent(open)
            .addAction(Notification.Action.Builder(null, getString(R.string.notif_stop_all), stop).build())
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() { scope.cancel(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val CHANNEL = "vms"
        const val ID = 1
        const val ACTION_STOP_ALL = "com.cuvm.STOP_ALL"
    }
}
