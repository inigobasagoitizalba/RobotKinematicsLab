package com.robotkinematicslab.mobile.ui.dataset

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.storage.DatasetBuilderDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetCreationValidationTest {
    @Test
    fun validScientificDraftPassesValidation() {
        assertNull(validDraft())
    }

    @Test
    fun invalidIdentityPointsToIdentityDisclosure() {
        assertEquals(
            DatasetCreationSection.IDENTITY,
            validDraft(datasetName = "\n")?.section
        )
    }

    @Test
    fun missingRobotPointsToRobotDisclosure() {
        assertEquals(
            DatasetCreationSection.ROBOTS,
            validDraft(selectedRobotCount = 0)?.section
        )
    }

    @Test
    fun malformedSamplingInputPointsToSamplingDisclosure() {
        assertEquals(
            DatasetCreationSection.SAMPLING,
            validDraft(samplesPerRobotText = "ten thousand")?.section
        )
    }

    @Test
    fun unsafeSolverInputPointsToIkDisclosure() {
        assertEquals(
            DatasetCreationSection.IK,
            validDraft(toleranceText = "NaN")?.section
        )
    }

    @Test
    fun simultaneousCorruptionReportsEveryResponsibleSectionAndField() {
        val issues =
            validateDatasetCreationDraftAll(
                datasetName = "\n",
                selectedRobotCount = 0,
                samplesPerRobotText = "zero",
                randomSeedText = "seed",
                reachablePercentText = "NaN",
                maxIterationsText = "0",
                toleranceText = "Infinity",
                dampingText = "0",
                maxStepText = "-1"
            )

        assertEquals(
            setOf(
                DatasetCreationSection.IDENTITY,
                DatasetCreationSection.ROBOTS,
                DatasetCreationSection.SAMPLING,
                DatasetCreationSection.IK
            ),
            issues.map(DatasetCreationValidationIssue::section).toSet()
        )
        assertEquals(issues.size, issues.map(DatasetCreationValidationIssue::fieldId).distinct().size)
        assertTrue(issues.all { it.message.isNotBlank() })
    }

    @Test
    fun manualRowPlanAccepts1756ButRejectsAmbiguousAndOverflowingCounts() {
        assertEquals(
            DatasetRowPlan(requestedSavedRows = 5_268, maximumSolverAttempts = 158_040),
            datasetRowPlan("1756", selectedRobotCount = 3)
        )
        assertNull(datasetRowPlan("1.756", selectedRobotCount = 3))
        assertNull(datasetRowPlan("1,756", selectedRobotCount = 3))
        assertNull(datasetRowPlan("0", selectedRobotCount = 3))
        assertNull(datasetRowPlan("-2", selectedRobotCount = 3))
        assertNull(datasetRowPlan("1000000", selectedRobotCount = 3_000))
    }

    @Test
    fun contextualRobotManagerKeepsValidChoicesAndCannotCorruptTheRestOfTheDraft() {
        val before =
            DatasetBuilderDraft(
                datasetName = "append-study",
                samplesPerRobotText = "1756",
                randomSeedText = "2604",
                reachablePercentText = "61.5",
                targetMode = DatasetTargetMode.MIXED,
                filterMode = DatasetFilterMode.REJECTED_ONLY,
                appendToExisting = true,
                maxIterationsText = "321",
                toleranceText = "1e-6",
                dampingText = "0.03",
                maxStepText = "0.04",
                selectedRobotIds = setOf("kept", "new-robot", "deleted")
            )

        val returned =
            before.copy(
                selectedRobotIds =
                    retainedDatasetRobotSelection(
                        selectedRobotIds = before.selectedRobotIds,
                        availableRobotIds = setOf("kept", "new-robot", "unselected")
                    )
            )

        assertEquals(setOf("kept", "new-robot"), returned.selectedRobotIds)
        assertEquals(
            before.copy(selectedRobotIds = emptySet()),
            returned.copy(selectedRobotIds = emptySet())
        )
    }

    @Test
    fun contextualRobotManagerRejectsGhostSelectionsInsteadOfApplyingAnotherRobot() {
        assertEquals(
            emptySet<String>(),
            retainedDatasetRobotSelection(
                selectedRobotIds = setOf("deleted", "unknown"),
                availableRobotIds = setOf("different-robot")
            )
        )
    }

    @Test
    fun validDraftBuildsTheExactExecutedConfigurationAndInvalidTextBuildsNothing() {
        val robots = DatasetRobotPresets().buildDefaults().take(2)
        val valid =
            datasetGenerationConfigOrNull(
                datasetName = "  exact-plan  ",
                selectedRobots = robots,
                samplesPerRobotText = "1756",
                randomSeedText = "2604",
                reachablePercentText = "61.5",
                targetMode = DatasetTargetMode.MIXED,
                filterMode = DatasetFilterMode.REJECTED_ONLY,
                appendToExisting = true,
                maxIterationsText = "321",
                toleranceText = "1e-6",
                dampingText = "0.03",
                maxStepText = "0.04"
            )

        requireNotNull(valid)
        assertEquals("exact-plan", valid.datasetName)
        assertEquals(robots, valid.robots)
        assertEquals(1756, valid.samplesPerRobot)
        assertEquals(2604, valid.randomSeed)
        assertEquals(0.615, valid.reachableFraction, 0.0)
        assertEquals(DatasetFilterMode.REJECTED_ONLY, valid.filterMode)
        assertEquals(321, valid.ikConfig.maxIterations)
        assertEquals(1e-6, valid.ikConfig.tolerance, 0.0)
        assertEquals(0.03, valid.ikConfig.damping, 0.0)
        assertEquals(0.04, valid.ikConfig.maxStep, 0.0)

        assertNull(
            datasetGenerationConfigOrNull(
                datasetName = "exact-plan",
                selectedRobots = robots,
                samplesPerRobotText = "1.756",
                randomSeedText = "2604",
                reachablePercentText = "61.5",
                targetMode = DatasetTargetMode.MIXED,
                filterMode = DatasetFilterMode.REJECTED_ONLY,
                appendToExisting = true,
                maxIterationsText = "321",
                toleranceText = "1e-6",
                dampingText = "0.03",
                maxStepText = "0.04"
            )
        )
    }

    private fun validDraft(
        datasetName: String = "thesis-validation",
        selectedRobotCount: Int = 1,
        samplesPerRobotText: String = "10000",
        randomSeedText: String = "2604",
        reachablePercentText: String = "70",
        maxIterationsText: String = "250",
        toleranceText: String = "1e-6",
        dampingText: String = "0.01",
        maxStepText: String = "0.2"
    ): DatasetCreationValidationIssue? =
        validateDatasetCreationDraft(
            datasetName = datasetName,
            selectedRobotCount = selectedRobotCount,
            samplesPerRobotText = samplesPerRobotText,
            randomSeedText = randomSeedText,
            reachablePercentText = reachablePercentText,
            maxIterationsText = maxIterationsText,
            toleranceText = toleranceText,
            dampingText = dampingText,
            maxStepText = maxStepText
        )
}
