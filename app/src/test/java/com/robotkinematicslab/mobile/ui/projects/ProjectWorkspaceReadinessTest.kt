package com.robotkinematicslab.mobile.ui.projects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectWorkspaceReadinessTest {
    @Test
    fun savedRobotsWithoutActiveRobotAreDescribedAccurately() {
        val step = recommendedNextStep(
            summary =
                ProjectWorkspaceSummary(
                    robotReady = false,
                    robotCount = 10,
                    diagnosticSessionCount = 0,
                    datasetCount = 0,
                    trainingRunCount = 0,
                    modelCount = 0
                ),
            onRobotLab = {},
            onDataset = {},
            onTraining = {},
            onDiagnostics = {}
        )

        assertEquals("Apply a robot in Robot Lab", step.title)
        assertTrue(step.reason.contains("10 saved robot definitions"))
        assertTrue(step.reason.contains("none is currently applied"))
    }

    @Test
    fun noSavedRobotsStillRequestsDefinition() {
        val step = recommendedNextStep(
            summary = ProjectWorkspaceSummary(false, 0, 0, 0, 0, 0),
            onRobotLab = {},
            onDataset = {},
            onTraining = {},
            onDiagnostics = {}
        )

        assertEquals("Define the robot", step.title)
    }
}
