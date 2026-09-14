package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.ml.ik.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class FeatureExperimentArchiveTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun original(): ArchivedFeatureConfiguration {
        val profile = TrainingFeatureProfile.CONTEXT_EXPANDED
        val names = ScientificDatasetTrainingReader.featureNames(profile)
        return ArchivedFeatureConfiguration("ordered", "Ordered experiment", profile.name, names, listOf(names[9], names[0], names[4]))
    }
    @Test fun savedPlanReopensInNewRepositoryWithExactOrderAndNames() {
        val file = File(temporary.root, "experiment.fexp")
        FeatureExperimentArchive(file, FeatureSetDomain.CLASSIFICATION).save(listOf(original()))
        assertEquals(listOf(original()), FeatureExperimentArchive(file, FeatureSetDomain.CLASSIFICATION).load())
    }
    @Test fun verifiedIkRoundTripRetainsIndependentDomain() {
        val config = FeatureExperimentArchive.verifiedIk(OneMicronFeatureSelectionSpec.complete(OneMicronIkFeatureProfile.KINEMATICS_108))
        val file = File(temporary.root, "ik.fexp")
        FeatureExperimentArchive(file, FeatureSetDomain.VERIFIED_IK).save(listOf(config))
        assertEquals(listOf(config), FeatureExperimentArchive(file, FeatureSetDomain.VERIFIED_IK).load())
        assertTrue(runCatching { FeatureExperimentArchive(file, FeatureSetDomain.CLASSIFICATION).load() }.isFailure)
    }
    @Test fun invalidContractsDoNotReplacePreviouslySavedPlan() {
        val file = File(temporary.root, "invalid.fexp")
        val repo = FeatureExperimentArchive(file, FeatureSetDomain.CLASSIFICATION)
        val config = original(); repo.save(listOf(config)); val bytes = file.readBytes()
        val invalid = listOf(config.copy(name = " "), config.copy(selectedNames = listOf("fabricated")), config.copy(sourceNames = config.sourceNames.reversed()), config.copy(selectedNames = listOf(config.selectedNames[0], config.selectedNames[0])))
        invalid.forEach { assertTrue(runCatching { repo.save(listOf(it)) }.isFailure); assertArrayEquals(bytes, file.readBytes()) }
        assertTrue(runCatching { repo.save(listOf(config, config.copy(id = "other", name = "Other"))) }.isFailure)
        assertTrue(runCatching { repo.save(listOf(config, config.copy(id = "other", selectedNames = listOf(config.sourceNames[3])))) }.isFailure)
        assertEquals(listOf(config), repo.load())
    }
    @Test fun sameSizeMutationTruncationAndMissingArchiveAreRejected() {
        val file = File(temporary.root, "corrupt.fexp")
        val repo = FeatureExperimentArchive(file, FeatureSetDomain.CLASSIFICATION)
        assertTrue(runCatching { repo.load() }.isFailure)
        repo.save(listOf(original())); val bytes = file.readBytes()
        val changed = bytes.copyOf(); changed[20] = (changed[20].toInt() xor 1).toByte(); file.writeBytes(changed)
        assertTrue(runCatching { repo.load() }.isFailure)
        file.writeBytes(bytes.copyOf(bytes.size - 7)); assertTrue(runCatching { repo.load() }.isFailure)
        // A correctly checksummed future format still must not be interpreted as v1.
        val futurePayload = bytes.copyOfRange(0, bytes.size - 32)
        futurePayload[7] = 2
        file.writeBytes(futurePayload + java.security.MessageDigest.getInstance("SHA-256").digest(futurePayload))
        assertTrue(runCatching { repo.load() }.isFailure)
    }
}
