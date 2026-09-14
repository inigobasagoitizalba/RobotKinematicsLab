package com.robotkinematicslab.mobile.process

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.robotkinematicslab.mobile.logging.AppLog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

class ResearchProcessForegroundService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var observationJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        val publisher = ResearchProcessNotificationPublisher(this)
        val coordinator = ResearchProcessCoordinator.get(this)
        var lastPublished = coordinator.processes.value
        startVisible(publisher.buildOngoing(lastPublished))
        observationJob =
            serviceScope.launch {
                while (isActive) {
                    val processes = coordinator.processes.value
                    if (processes.none { it.status.isActive }) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                        break
                    }
                    if (processes != lastPublished) {
                        publisher.updateOngoing(processes)
                        lastPublished = processes
                    }
                    delay(MINIMUM_NOTIFICATION_UPDATE_INTERVAL_MILLIS)
                }
            }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // A deliberate removal from Recents is treated as a full close. Scientific
        // engines still own their atomic checkpoints and recover them on next launch.
        ResearchProcessCoordinator.get(this).cancelAll(
            "The app was removed from Recents. Saved checkpoints remain available."
        )
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        observationJob?.cancel()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startVisible(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                ResearchProcessNotificationPublisher.ONGOING_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(ResearchProcessNotificationPublisher.ONGOING_NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val MINIMUM_NOTIFICATION_UPDATE_INTERVAL_MILLIS = 500L

        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, ResearchProcessForegroundService::class.java)
                )
            }.onFailure { error ->
                AppLog.e(TAG) {
                    "Could not start visible research process service | error=${error.message}"
                }
            }
        }

        private const val TAG = "ResearchProcessService"
    }
}
