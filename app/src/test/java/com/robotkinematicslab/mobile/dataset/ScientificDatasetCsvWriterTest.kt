package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy
import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScientificDatasetCsvWriterTest {

    @Test
    fun appendWritesHeaderOnceAndKeepsColumnCountStable() {
        val file = Files.createTempFile("scientific-dataset", ".csv").toFile()
        val robot = DatasetRobotPresets().buildDefaults().first()

        ScientificDatasetCsvWriter().use { writer ->
            writer.open(file, append = false)
            writer.write(sampleRow(robot, 0L, "batch-1"))
        }

        ScientificDatasetCsvWriter().use { writer ->
            writer.open(file, append = true)
            writer.write(sampleRow(robot, 1L, "batch-2"))
        }

        val lines = file.readLines()
        assertEquals(3, lines.size)
        assertEquals(1, lines.count { it.startsWith("schemaVersion,") })
        assertEquals(ScientificDatasetCsvWriter.HEADER.size, lines[1].split(',').size)
        assertEquals(ScientificDatasetCsvWriter.HEADER.size, lines[2].split(',').size)
        assertTrue(ScientificDatasetCsvWriter.HEADER.contains("randomProtocol"))
        assertTrue(lines[2].contains("batch-2"))
    }

    private fun sampleRow(robot: SavedRobot, index: Long, batchId: String): DatasetRow {
        val jointValues = robot.robot.joints.map { it.homeValue }
        return DatasetRow(
            globalRowIndex = index,
            batchId = batchId,
            baseRandomSeed = 42,
            robotRandomSeed = 99,
            robotId = robot.id,
            robot = robot.robot,
            ikConfig = IKConfig(),
            metricPolicy = DiagnosticMetricPolicy(),
            sampleIndex = index.toInt(),
            targetClass = "FK_PROVEN_REACHABLE",
            targetSamplingStrategy = "UNIFORM_JOINT_SPACE_FK",
            targetSourceJointValues = jointValues,
            seedJointValues = jointValues,
            targetX = 0.1,
            targetY = 0.2,
            targetZ = 0.3,
            solutionJointValues = jointValues,
            solverAccepted = true,
            acceptanceClass = DatasetAcceptanceClass.ACCEPTED.name,
            status = "SUCCESS",
            detailCode = "NONE",
            converged = true,
            finalError = 0.0,
            iterations = 1,
            initialError = 0.2,
            improvement = 0.2,
            improvementRatio = 1.0,
            progressClass = "SOLVED",
            seedDistanceBucket = "MEDIUM",
            iterationSaturationRatio = 0.01,
            jointDeltaNorm = 0.1,
            maxSingleJointMovement = 0.1,
            seedMinNormalizedLimitMargin = 0.5,
            seedConditionNumber = 2.0,
            seedLogConditionNumber = 0.301,
            normalizedJointTravelRms = 0.1,
            finalMinNormalizedLimitMargin = 0.4,
            backtrackingRetryCount = 0,
            solveDurationNanos = 100L,
            nearLimitJointCount = 0,
            nearLimitJointNames = emptyList(),
            jointLimitPressureRatio = 0.0
        )
    }
}
