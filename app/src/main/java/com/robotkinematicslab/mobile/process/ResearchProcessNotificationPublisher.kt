package com.robotkinematicslab.mobile.process

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.content.getSystemService
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.R
import com.robotkinematicslab.mobile.logging.AppLog

class ResearchProcessNotificationPublisher(private val context: Context) {
    private val notificationManager = requireNotNull(context.getSystemService<NotificationManager>())

    init {
        createChannels()
    }

    fun buildOngoing(processes: List<ResearchProcessSnapshot>): Notification {
        val summary = ResearchProcessSummary.from(processes)
        val active = summary.active
        val lead = active.firstOrNull()
        val title =
            when (active.size) {
                0 -> "Preparing research process"
                1 -> lead?.title.orEmpty()
                else -> "${active.size} research processes running"
            }
        val detail =
            when {
                lead == null -> "Preparing a visible background process."
                active.size == 1 -> lead.stage
                else ->
                    active.take(MAXIMUM_VISIBLE_ACTIVE_PROCESSES).joinToString("\n") {
                        "${it.title}: ${it.stage}"
                    } + if (active.size > MAXIMUM_VISIBLE_ACTIVE_PROCESSES) {
                        "\n+${active.size - MAXIMUM_VISIBLE_ACTIVE_PROCESSES} more active"
                    } else {
                        ""
                    }
            }
        val safeTitle = notificationText(title, MAXIMUM_TITLE_CHARACTERS)
        val safeDetail = notificationText(detail, MAXIMUM_DETAIL_CHARACTERS)
        val builder =
            Notification.Builder(context, ONGOING_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_research_process)
                .setContentTitle(safeTitle)
                .setContentText(safeDetail.lineSequence().firstOrNull().orEmpty())
                .setStyle(Notification.BigTextStyle().bigText(safeDetail))
                .setContentIntent(openAppIntent())
                .setCategory(Notification.CATEGORY_PROGRESS)
                .setOnlyAlertOnce(true)
                .setOngoing(true)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(buildPublicNotification(ongoing = true))
                .setColor(0xFF008C95.toInt())

        val progress = summary.overallProgressFraction
        if (progress == null) {
            builder.setProgress(0, 0, true)
        } else {
            builder.setProgress(100, (progress * 100.0).toInt().coerceIn(0, 100), false)
        }
        return builder.build()
    }

    fun updateOngoing(processes: List<ResearchProcessSnapshot>) {
        runCatching { notificationManager.notify(ONGOING_NOTIFICATION_ID, buildOngoing(processes)) }
            .onFailure { error ->
                AppLog.e(TAG) { "Could not update process notification | error=${error.javaClass.simpleName}" }
            }
    }

    fun postResult(process: ResearchProcessSnapshot) {
        val notification = buildResult(process) ?: return
        runCatching { notificationManager.notify(process.id, RESULT_NOTIFICATION_ID, notification) }
            .onFailure { error ->
                AppLog.e(TAG) { "Could not post process result | error=${error.javaClass.simpleName}" }
            }
    }

    fun buildResult(process: ResearchProcessSnapshot): Notification? {
        val title =
            when (process.status) {
                ResearchProcessStatus.SUCCEEDED -> "${process.title} complete"
                ResearchProcessStatus.PAUSED -> "${process.title} paused safely"
                ResearchProcessStatus.CANCELLED -> "${process.title} cancelled"
                ResearchProcessStatus.FAILED -> "${process.title} failed"
                else -> return null
            }
        val color =
            when (process.status) {
                ResearchProcessStatus.SUCCEEDED -> 0xFF2E7D32.toInt()
                ResearchProcessStatus.FAILED -> 0xFFC62828.toInt()
                else -> 0xFFEF6C00.toInt()
            }
        val safeTitle = notificationText(title, MAXIMUM_TITLE_CHARACTERS)
        val safeDetail =
            when (process.status) {
                ResearchProcessStatus.SUCCEEDED -> "Finished successfully. Open the app for verified results."
                ResearchProcessStatus.PAUSED -> "Paused at a safe boundary. Saved checkpoints remain available."
                ResearchProcessStatus.CANCELLED -> "Stopped safely. Open the app to review retained evidence."
                ResearchProcessStatus.FAILED -> "Open the app to review the error and recovery guidance."
                else -> return null
            }
        return Notification.Builder(context, RESULT_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_research_process)
                .setContentTitle(safeTitle)
                .setContentText(safeDetail)
                .setStyle(Notification.BigTextStyle().bigText(safeDetail))
                .setContentIntent(openAppIntent())
                .setAutoCancel(true)
                .setCategory(Notification.CATEGORY_STATUS)
                .setVisibility(Notification.VISIBILITY_PRIVATE)
                .setPublicVersion(buildPublicNotification(ongoing = false))
                .setColor(color)
                .build()
    }

    private fun createChannels() {
        notificationManager.createNotificationChannels(
            listOf(
                NotificationChannel(
                    ONGOING_CHANNEL_ID,
                    "Active research processes",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Persistent progress for datasets, diagnostics and on-device AI training."
                    setShowBadge(false)
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                },
                NotificationChannel(
                    RESULT_CHANNEL_ID,
                    "Research process results",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Completion, safe pause and error notifications for scientific processes."
                    lockscreenVisibility = Notification.VISIBILITY_PRIVATE
                }
            )
        )
    }

    private fun buildPublicNotification(ongoing: Boolean): Notification =
        Notification.Builder(context, if (ongoing) ONGOING_CHANNEL_ID else RESULT_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_research_process)
            .setContentTitle("Robot Kinematics Lab")
            .setContentText(if (ongoing) "A research process is active." else "A research process has finished.")
            .setCategory(if (ongoing) Notification.CATEGORY_PROGRESS else Notification.CATEGORY_STATUS)
            .build()

    private fun notificationText(value: String, maximumCharacters: Int): String =
        value
            .filter { character -> character == '\n' || !character.isISOControl() }
            .lineSequence()
            .joinToString("\n") { line -> line.trim().replace(WHITESPACE, " ") }
            .trim()
            .take(maximumCharacters)

    private fun openAppIntent(): PendingIntent =
        PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    companion object {
        private const val TAG = "ResearchProcessNotifications"
        const val ONGOING_NOTIFICATION_ID = 41_001
        const val RESULT_NOTIFICATION_ID = 41_002
        const val ONGOING_CHANNEL_ID = "research_processes"
        const val RESULT_CHANNEL_ID = "research_results"
        private const val MAXIMUM_VISIBLE_ACTIVE_PROCESSES = 3
        private const val MAXIMUM_TITLE_CHARACTERS = 100
        private const val MAXIMUM_DETAIL_CHARACTERS = 600
        private val WHITESPACE = Regex("[\\t\\x0B\\f ]+")
    }
}
