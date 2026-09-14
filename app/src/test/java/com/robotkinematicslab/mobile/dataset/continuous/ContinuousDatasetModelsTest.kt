package com.robotkinematicslab.mobile.dataset.continuous

import com.robotkinematicslab.mobile.dataset.DatasetFilterMode
import com.robotkinematicslab.mobile.dataset.DatasetTargetMode
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.performance.compute.ComputeThermalLevel
import com.robotkinematicslab.mobile.performance.compute.DeviceComputeProfile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ContinuousDatasetModelsTest {

    @Test
    fun planRejectsUnsafeOrScientificallyInvalidInputs() {
        assertThrows(IllegalArgumentException::class.java) { plan().copy(datasetName = " ").validated() }
        assertThrows(IllegalArgumentException::class.java) { plan().copy(robotIds = emptyList()).validated() }
        assertThrows(IllegalArgumentException::class.java) { plan().copy(reachableFraction = Double.NaN).validated() }
        assertThrows(IllegalArgumentException::class.java) { plan().copy(cpuBudgetPercent = 101).validated() }
        assertThrows(IllegalArgumentException::class.java) { plan().copy(memoryBudgetPercent = 9).validated() }
    }

    @Test
    fun tenPercentBudgetReservesAndroidHeadroomAndUsesDutyCycle() {
        val policy = ContinuousDatasetResourceResolver.resolve(plan(), device())

        assertEquals(1, policy.workerCount)
        assertEquals(25, policy.rowsPerRobotPerBatch)
        assertEquals(0.8, policy.dutyCycle, 1e-12)
        assertFalse(policy.waitForDevice)
    }

    @Test
    fun severeHeatOrLowMemoryWaitsInsteadOfGenerating() {
        val thermal = ContinuousDatasetResourceResolver.resolve(
            plan().copy(cpuBudgetPercent = 50),
            device().copy(thermalLevel = ComputeThermalLevel.SEVERE)
        )
        val memory = ContinuousDatasetResourceResolver.resolve(
            plan(),
            device().copy(lowMemory = true)
        )

        assertTrue(thermal.waitForDevice)
        assertTrue(memory.waitForDevice)
        assertEquals(1, thermal.workerCount)
        assertEquals(1, memory.workerCount)
    }

    @Test
    fun moderateHeatReducesWorkersButDoesNotDeadlockTheQueue() {
        val policy = ContinuousDatasetResourceResolver.resolve(
            plan().copy(cpuBudgetPercent = 50),
            device().copy(thermalLevel = ComputeThermalLevel.MODERATE)
        )

        assertFalse(policy.waitForDevice)
        assertTrue(policy.workerCount in 1..4)
    }

    @Test
    fun maximumLegacyBudgetStillHonoursTheDeliveryWorkerCap() {
        val policy = ContinuousDatasetResourceResolver.resolve(
            plan().copy(cpuBudgetPercent = 100),
            device()
        )

        assertEquals(1, policy.workerCount)
        assertEquals(1.0, policy.dutyCycle, 1e-12)
        assertEquals(1_000, policy.rowsPerRobotPerBatch)
        assertFalse(policy.waitForDevice)
    }

    @Test
    fun forecastSevereThermalPressurePausesBeforeStatusMustBecomeSevere() {
        val policy = ContinuousDatasetResourceResolver.resolve(
            plan().copy(cpuBudgetPercent = 100),
            device().copy(thermalHeadroom = 1.01)
        )

        assertTrue(policy.waitForDevice)
        assertEquals(1, policy.workerCount)
    }

    @Test
    fun cooldownMatchesRequestedAverageDutyCycle() {
        assertEquals(3_000L, ContinuousDatasetResourceResolver.idleDelayMillis(1_000L, 0.25))
        assertEquals(0L, ContinuousDatasetResourceResolver.idleDelayMillis(1_000L, 1.0))
        assertThrows(IllegalArgumentException::class.java) {
            ContinuousDatasetResourceResolver.idleDelayMillis(1_000L, Double.NaN)
        }
    }

    private fun plan() = ContinuousDatasetPlan(
        datasetName = "continuous-study",
        robotIds = listOf("robot-a"),
        randomSeed = 2604,
        targetMode = DatasetTargetMode.MIXED,
        reachableFraction = 0.5,
        filterMode = DatasetFilterMode.ALL,
        ikConfig = IKConfig(maxIterations = 100, tolerance = 1e-6, damping = 0.01, maxStep = 0.02)
    )

    private fun device() = DeviceComputeProfile(
        logicalCpuCores = 8,
        totalSystemMemoryBytes = 8L * 1_024L * 1_024L * 1_024L,
        availableSystemMemoryBytes = 4L * 1_024L * 1_024L * 1_024L,
        lowMemoryThresholdBytes = 512L * 1_024L * 1_024L,
        appHeapLimitBytes = 512L * 1_024L * 1_024L,
        lowMemory = false,
        thermalLevel = ComputeThermalLevel.NONE,
        deviceName = "Test device"
    )
}
