package com.robotkinematicslab.mobile.process

import android.Manifest
import android.app.ActivityManager
import android.app.Notification
import android.content.pm.ApplicationInfo
import android.content.ComponentName
import android.os.Build
import android.os.SystemClock
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.app.NotificationManager
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.robotkinematicslab.mobile.MainActivity
import com.robotkinematicslab.mobile.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser

@RunWith(AndroidJUnit4::class)
class ResearchProcessPlatformContractTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun longProcessServiceIsPrivateAndRequestsItsPlatformPermissions() {
        val packageInfo =
            context.packageManager.getPackageInfo(
                context.packageName,
                PackageManager.GET_PERMISSIONS
            )
        val requestedPermissions = packageInfo.requestedPermissions.orEmpty().toSet()

        assertTrue(Manifest.permission.FOREGROUND_SERVICE in requestedPermissions)
        assertTrue(Manifest.permission.POST_NOTIFICATIONS in requestedPermissions)
        // The strict robot-reference viewer downloads pinned manufacturer PDFs only after
        // explicit consent, then validates their signature, size, digest and page count.
        assertTrue(Manifest.permission.INTERNET in requestedPermissions)

        val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 0)
        assertFalse(applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC != 0)

        val serviceInfo =
            context.packageManager.getServiceInfo(
                ComponentName(context, ResearchProcessForegroundService::class.java),
                0
            )
        assertNotNull(serviceInfo)
        assertFalse(serviceInfo.exported)
        // The callback is intentional: it lets the coordinator request a cooperative stop/checkpoint
        // before the service removes its ongoing notification.
        assertFalse(serviceInfo.flags and ServiceInfo.FLAG_STOP_WITH_TASK != 0)
    }

    @Test
    fun publisherCreatesSeparateProgressAndResultChannels() {
        ResearchProcessNotificationPublisher(context)
        val notificationManager = context.getSystemService(NotificationManager::class.java)

        assertNotNull(
            notificationManager.getNotificationChannel(
                ResearchProcessNotificationPublisher.ONGOING_CHANNEL_ID
            )
        )
        assertNotNull(
            notificationManager.getNotificationChannel(
                ResearchProcessNotificationPublisher.RESULT_CHANNEL_ID
            )
        )
    }

    @Test
    fun scientificProjectFilesAreExcludedFromBackupAndDeviceTransfer() {
        val expected =
            setOf(
                "file" to "RobotKinematicsLab",
                "external" to "Documents/RobotKinematicsLab"
            )

        assertTrue(readExclusions(R.xml.backup_rules).containsAll(expected))
        assertTrue(readExclusions(R.xml.data_extraction_rules).containsAll(expected))
    }

    @Test
    fun notificationPayloadsArePrivateBoundedAndKeepRawErrorsInsideTheApp() {
        val publisher = ResearchProcessNotificationPublisher(context)
        val now = System.currentTimeMillis()
        val rawSecret = "/data/user/0/${context.packageName}/files/private-project.csv"
        val active =
            ResearchProcessSnapshot(
                id = "privacy-contract",
                title = "Research " + "x".repeat(300),
                kind = ResearchProcessKind.DATASET,
                status = ResearchProcessStatus.RUNNING,
                progressFraction = 0.5,
                stage = "Writing verified rows",
                detail = rawSecret,
                startedAtEpochMillis = now,
                updatedAtEpochMillis = now,
                canCancel = true
            )

        val ongoing = publisher.buildOngoing(listOf(active))
        assertEquals(Notification.VISIBILITY_PRIVATE, ongoing.visibility)
        assertTrue(ongoing.extras.getString(Notification.EXTRA_TITLE).orEmpty().length <= 100)
        assertFalse(ongoing.extras.getString(Notification.EXTRA_BIG_TEXT).orEmpty().contains(rawSecret))
        assertEquals("Robot Kinematics Lab", ongoing.publicVersion.extras.getString(Notification.EXTRA_TITLE))

        val result = requireNotNull(publisher.buildResult(active.copy(status = ResearchProcessStatus.FAILED)))
        assertEquals(Notification.VISIBILITY_PRIVATE, result.visibility)
        assertFalse(result.extras.getString(Notification.EXTRA_BIG_TEXT).orEmpty().contains(rawSecret))
        assertEquals("Robot Kinematics Lab", result.publicVersion.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun notificationPipelinePublishesLiveProgressThenACompletionResult() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
        ) {
            InstrumentationRegistry.getInstrumentation().uiAutomation.grantRuntimePermission(
                context.packageName,
                Manifest.permission.POST_NOTIFICATIONS
            )
        }
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.cancelAll()
        // Notification cancellation is asynchronous in system_server. Let it settle so it cannot
        // remove the fresh foreground notification posted immediately afterwards.
        SystemClock.sleep(250L)
        val coordinator = ResearchProcessCoordinator.get(context)
        resetCoordinator(coordinator)
        val processId = "notification-platform-contract"
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        val reporter = coordinator.beginExternal(
            id = processId,
            title = "Contract dataset",
            kind = ResearchProcessKind.DATASET,
            stage = "Sampling",
            detail = "Collecting deterministic rows."
        )

        try {
            reporter.report(
                progressFraction = 0.42,
                stage = "Sampling",
                detail = "42 of 100 rows"
            )
            awaitForegroundService()
            val publisher = ResearchProcessNotificationPublisher(context)
            val ongoing = publisher.buildOngoing(coordinator.processes.value)
            assertEquals("Contract dataset", ongoing.extras.getString(Notification.EXTRA_TITLE))
            assertEquals(42, ongoing.extras.getInt(Notification.EXTRA_PROGRESS))

            reporter.completed("100 rows were stored and verified.")
            val terminal = requireNotNull(coordinator.snapshot(processId))
            val result = requireNotNull(publisher.buildResult(terminal))
            assertEquals(
                "Contract dataset complete",
                result.extras.getString(Notification.EXTRA_TITLE)
            )
            assertEquals(Notification.VISIBILITY_PRIVATE, result.visibility)
            assertTrue(result.extras.getString(Notification.EXTRA_TEXT).orEmpty().contains("verified results"))
        } finally {
            reporter.cancelled("Instrumentation contract complete.")
            coordinator.clearFinished()
            notificationManager.cancelAll()
            activityScenario.close()
        }
    }

    @Test
    fun coordinatorOwnedProcessCanBeCancelledImmediatelyWithoutASeparateEngineCallback() {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        val coordinator = ResearchProcessCoordinator.get(context)
        resetCoordinator(coordinator)
        val processId = "immediate-cancel-contract-${System.nanoTime()}"
        val activityScenario = ActivityScenario.launch(MainActivity::class.java)
        val launched =
            coordinator.launch(
                id = processId,
                title = "Immediate cancellation contract",
                kind = ResearchProcessKind.ANALYSIS
            ) { reporter ->
                reporter.report(0.1, "Running", "Waiting for cancellation.")
                kotlinx.coroutines.delay(30_000L)
            }

        try {
            assertTrue(launched.isSuccess)
            assertTrue(coordinator.requestCancel(processId))
            repeat(50) {
                if (coordinator.snapshot(processId)?.status == ResearchProcessStatus.CANCELLED) return@repeat
                SystemClock.sleep(50L)
            }
            assertEquals(ResearchProcessStatus.CANCELLED, coordinator.snapshot(processId)?.status)
            assertFalse(coordinator.requestCancel(processId))
        } finally {
            launched.getOrNull()?.cancel()
            coordinator.clearFinished()
            notificationManager.cancelAll()
            activityScenario.close()
        }
    }

    @Test
    fun staleReporterCannotOverwriteANewerRunWithTheSameProcessId() {
        val coordinator = ResearchProcessCoordinator.get(context)
        resetCoordinator(coordinator)
        val processId = "stale-reporter-contract-${System.nanoTime()}"
        val oldReporter =
            coordinator.beginExternal(
                id = processId,
                title = "First lifecycle",
                kind = ResearchProcessKind.ANALYSIS,
                stage = "First stage",
                detail = "First lifecycle is active."
            )
        oldReporter.completed(
            "First lifecycle completed.",
            ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "old-run")
        )
        val currentReporter =
            coordinator.beginExternal(
                id = processId,
                title = "Second lifecycle",
                kind = ResearchProcessKind.ANALYSIS,
                stage = "Second stage",
                detail = "Second lifecycle is active."
            )

        try {
            oldReporter.report(0.9, "Stale update", "This must be ignored.")
            oldReporter.failed("This must not close the second lifecycle.")

            val current = requireNotNull(coordinator.snapshot(processId))
            assertEquals("Second lifecycle", current.title)
            assertEquals("Second stage", current.stage)
            assertTrue(current.status.isActive)
            assertEquals(null, current.resultReference)

            oldReporter.completed(
                "A stale completion must be ignored.",
                ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "stale-run")
            )
            currentReporter.completed(
                "Second lifecycle completed.",
                ResearchResultReference(ResearchResultDestination.TRAINING_RUN, "current-run")
            )
            assertEquals(ResearchProcessStatus.SUCCEEDED, coordinator.snapshot(processId)?.status)
            assertEquals("current-run", coordinator.snapshot(processId)?.actionableResult?.artifactId)
            assertEquals(2, coordinator.processes.value.count { it.id == processId })
            assertEquals(
                setOf("old-run", "current-run"),
                coordinator.processes.value.mapNotNull { it.actionableResult?.artifactId }.toSet()
            )
        } finally {
            currentReporter.cancelled("Instrumentation contract complete.")
            coordinator.clearFinished()
        }
    }

    @Test
    fun activeExternalProcessCanRebindItsSafeStopAfterReattachment() {
        val coordinator = ResearchProcessCoordinator.get(context)
        resetCoordinator(coordinator)
        val processId = "external-rebind-contract-${System.nanoTime()}"
        val initialReporter =
            coordinator.beginExternal(
                id = processId,
                title = "Reattached lifecycle",
                kind = ResearchProcessKind.DATASET,
                stage = "Running",
                detail = "Waiting for a lifecycle owner."
            )
        var stopRequests = 0
        val reboundReporter =
            coordinator.beginExternal(
                id = processId,
                title = "Reattached lifecycle",
                kind = ResearchProcessKind.DATASET,
                stage = "Running",
                detail = "A lifecycle owner is attached.",
                cancellationAction = { stopRequests += 1 }
            )

        try {
            assertTrue(coordinator.snapshot(processId)?.canCancel == true)
            assertTrue(coordinator.requestCancel(processId))
            assertEquals(1, stopRequests)
            assertEquals(ResearchProcessStatus.PAUSING, coordinator.snapshot(processId)?.status)
        } finally {
            reboundReporter.paused("Instrumentation contract complete.")
            initialReporter.cancelled("Stale reporter cleanup must be ignored.")
            coordinator.clearFinished()
        }
    }

    @Test
    fun externalSafeStopFailureIsContainedAndDoesNotPretendTheWorkStopped() {
        val coordinator = ResearchProcessCoordinator.get(context)
        resetCoordinator(coordinator)
        val processId = "external-cancel-failure-${System.nanoTime()}"
        val reporter =
            coordinator.beginExternal(
                id = processId,
                title = "External cancellation contract",
                kind = ResearchProcessKind.DATASET,
                stage = "Writing an atomic batch",
                detail = "The external engine owns the safe checkpoint.",
                cancellationAction = { error("Injected cancellation bridge failure") }
            )

        try {
            assertFalse(coordinator.requestCancel(processId))
            val snapshot = requireNotNull(coordinator.snapshot(processId))
            assertEquals(ResearchProcessStatus.RUNNING, snapshot.status)
            assertEquals("Still running", snapshot.stage)
            assertTrue(snapshot.detail.contains("remains active"))
        } finally {
            reporter.cancelled("Instrumentation contract complete.")
            coordinator.clearFinished()
        }
    }

    @Suppress("DEPRECATION")
    private fun awaitForegroundService() {
        val activityManager = context.getSystemService(ActivityManager::class.java)
        repeat(30) {
            val running =
                activityManager.getRunningServices(Int.MAX_VALUE).any {
                    it.service.className == ResearchProcessForegroundService::class.java.name && it.foreground
                }
            if (running) return
            SystemClock.sleep(100L)
        }
        throw AssertionError("Research process foreground service did not become active within 3 seconds.")
    }

    private fun resetCoordinator(coordinator: ResearchProcessCoordinator) {
        coordinator.cancelAll("Instrumentation contract reset.")
        coordinator.clearFinished()
    }

    private fun readExclusions(resourceId: Int): Set<Pair<String, String>> {
        val parser = context.resources.getXml(resourceId)
        return try {
            buildSet {
                while (parser.eventType != XmlPullParser.END_DOCUMENT) {
                    if (parser.eventType == XmlPullParser.START_TAG && parser.name == "exclude") {
                        val domain = parser.getAttributeValue(null, "domain")
                        val path = parser.getAttributeValue(null, "path")
                        if (domain != null && path != null) add(domain to path)
                    }
                    parser.next()
                }
            }
        } finally {
            parser.close()
        }
    }
}
