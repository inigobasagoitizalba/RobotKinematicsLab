package com.robotkinematicslab.mobile.performance.compute

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SafeComputePolicyResolverTest {
    @Test
    fun retiredPerformanceModeIsMigratedIntoTheDeliverySafetyEnvelope() {
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(preset = ComputeResourcePreset.PERFORMANCE),
                device(cores = 8)
            )

        assertEquals(1, policy.reservedCpuCores)
        assertEquals(2, policy.safeMaximumWorkers)
        assertEquals(1, policy.effectiveWorkerCount)
        assertEquals(ComputeResourcePreset.BALANCED, policy.preset)
        assertTrue(policy.effectiveWorkerCount < policy.logicalCpuCores)
    }

    @Test
    fun legacyCustomRequestCannotCrossTheDeliveryHardCeilings() {
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(
                    preset = ComputeResourcePreset.CUSTOM,
                    customWorkerCount = 64,
                    requestedWorkingMemoryPercent = 100
                ),
                device(cores = 4)
            )

        assertEquals(2, policy.safeMaximumWorkers)
        assertEquals(1, policy.effectiveWorkerCount)
        assertEquals(ComputeResourcePreset.BALANCED, policy.preset)
        assertTrue(policy.workingMemoryBudgetBytes <= policy.appHeapLimitBytes * 35L / 100L)
    }

    @Test
    fun thermalAndMemoryPressureForceSingleWorker() {
        val settings = ComputeResourceSettings(preset = ComputeResourcePreset.PERFORMANCE)
        val hot =
            SafeComputePolicyResolver.resolve(
                settings,
                device(cores = 8, thermal = ComputeThermalLevel.SEVERE)
            )
        val pressured =
            SafeComputePolicyResolver.resolve(
                settings,
                device(cores = 8, lowMemory = true)
            )

        assertEquals(1, hot.effectiveWorkerCount)
        assertEquals(1, pressured.effectiveWorkerCount)
    }

    @Test
    fun forecastHeadroomBacksOffBeforeSevereThermalStatusArrives() {
        val nearLimit =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(preset = ComputeResourcePreset.PERFORMANCE),
                device(cores = 8).copy(thermalHeadroom = 0.9)
            )
        val exhausted =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(preset = ComputeResourcePreset.PERFORMANCE),
                device(cores = 8).copy(thermalHeadroom = 1.0)
            )

        assertEquals(1, nearLimit.effectiveWorkerCount)
        assertEquals(1, exhausted.effectiveWorkerCount)
    }

    @Test
    fun extremeSyntheticMemoryValuesCannotOverflowTheSafetyBudget() {
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(
                    preset = ComputeResourcePreset.PERFORMANCE,
                    requestedWorkingMemoryPercent = Int.MAX_VALUE
                ),
                DeviceComputeProfile(
                    logicalCpuCores = Int.MAX_VALUE,
                    totalSystemMemoryBytes = Long.MAX_VALUE,
                    availableSystemMemoryBytes = Long.MAX_VALUE,
                    lowMemoryThresholdBytes = -1L,
                    appHeapLimitBytes = Long.MAX_VALUE,
                    lowMemory = false,
                    thermalLevel = ComputeThermalLevel.NONE,
                    deviceName = "Synthetic boundary device"
                )
            )

        val maximumAllowed =
            Long.MAX_VALUE / 100L * 35L + (Long.MAX_VALUE % 100L) * 35L / 100L
        assertTrue(policy.workingMemoryBudgetBytes > 0L)
        assertTrue(policy.workingMemoryBudgetBytes <= maximumAllowed)
        assertTrue(policy.effectiveWorkerCount <= SafeComputePolicyResolver.MAXIMUM_APP_WORKERS)
        assertTrue(policy.effectiveWorkerCount < policy.logicalCpuCores)
    }

    @Test
    fun smallHeapAndSystemCapAreNeverInflatedToAnUnsafeMinimum() {
        val actualHeap = 4L * 1_024L * 1_024L
        val availableAboveThreshold = 2L * 1_024L * 1_024L
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(
                    preset = ComputeResourcePreset.BALANCED,
                    requestedWorkingMemoryPercent = 35
                ),
                device(cores = 4).copy(
                    appHeapLimitBytes = actualHeap,
                    availableSystemMemoryBytes = 3L * 1_024L * 1_024L,
                    lowMemoryThresholdBytes = 1L * 1_024L * 1_024L
                )
            )

        assertEquals(actualHeap, policy.appHeapLimitBytes)
        assertEquals(availableAboveThreshold / 4L, policy.workingMemoryBudgetBytes)
        assertEquals(256, policy.estimatedSafeTrainingRows)
        assertTrue(policy.workingMemoryBudgetBytes <= actualHeap)
    }

    @Test
    fun subRowBudgetReportsZeroSafeRowsInsteadOfInventingOneThousand() {
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(
                    preset = ComputeResourcePreset.CUSTOM,
                    requestedWorkingMemoryPercent = 10
                ),
                device(cores = 2).copy(
                    appHeapLimitBytes = 1_024L,
                    availableSystemMemoryBytes = 1_024L,
                    lowMemoryThresholdBytes = 0L
                )
            )

        assertEquals(0, policy.estimatedSafeTrainingRows)
        assertTrue(policy.workingMemoryBudgetBytes < 2_048L)
    }

    @Test
    fun batchWorkerAllocationHonorsRuntimePressureAndUsefulWork() {
        assertEquals(1, ComputeWorkerAllocation.resolve(64, 1, 128))
        assertEquals(3, ComputeWorkerAllocation.resolve(8, 8, 3))
        assertThrows(IllegalArgumentException::class.java) {
            ComputeWorkerAllocation.resolve(8, 8, 0)
        }
    }

    @Test
    fun repositoryPersistsAndSanitizesUserSelection() {
        val directory = Files.createTempDirectory("compute-settings").toFile()
        val repository = ComputeSettingsRepository(directory)

        repository.save(
            ComputeResourceSettings(
                preset = ComputeResourcePreset.CUSTOM,
                customWorkerCount = 999,
                requestedWorkingMemoryPercent = 99
            )
        )
        val restored = repository.load()

        assertEquals(ComputeResourcePreset.BALANCED, restored.preset)
        assertEquals(2, restored.customWorkerCount)
        assertEquals(35, restored.requestedWorkingMemoryPercent)
        File(directory, "compute-settings.properties").writeText("preset=NOT_A_MODE\ncustomWorkerCount=-4\n")
        val recovered = repository.load()
        assertEquals(ComputeResourcePreset.BALANCED, recovered.preset)
        assertEquals(1, recovered.customWorkerCount)
    }

    @Test
    fun staleLegacyTemporaryDirectoryCannotBlockComputeSettingsPublication() {
        val directory = Files.createTempDirectory("compute-settings-stale-temp").toFile()
        val staleTemporaryEntry = File(directory, "compute-settings.properties.tmp")
        assertTrue(staleTemporaryEntry.mkdir())
        val repository = ComputeSettingsRepository(directory)
        val legacyRequest = ComputeResourceSettings(
            preset = ComputeResourcePreset.CUSTOM,
            customWorkerCount = 4,
            requestedWorkingMemoryPercent = 40
        )
        val expected = ComputeResourceSettings(
            preset = ComputeResourcePreset.BALANCED,
            customWorkerCount = 2,
            requestedWorkingMemoryPercent = 35
        )

        assertEquals(expected, repository.save(legacyRequest))
        assertEquals(expected, repository.load())
        assertTrue(staleTemporaryEntry.isDirectory)
    }

    @Test
    fun computePolicyCacheRejectsClockRollbackAndExpiredOrImpossibleTimestamps() {
        assertTrue(computePolicyCacheIsFresh(nowMillis = 1_999L, cachedAtMillis = 1_000L))
        assertTrue(!computePolicyCacheIsFresh(nowMillis = 999L, cachedAtMillis = 1_000L))
        assertTrue(!computePolicyCacheIsFresh(nowMillis = 2_000L, cachedAtMillis = 1_000L))
        assertTrue(!computePolicyCacheIsFresh(nowMillis = Long.MAX_VALUE, cachedAtMillis = -1L))
    }

    @Test
    fun retiredResearchMaximumUsesThirtyFivePercentOfGrantedHeapAtMost() {
        val grantedHeap = 2L * 1_024L * 1_024L * 1_024L
        val policy =
            SafeComputePolicyResolver.resolve(
                ComputeResourceSettings(
                    preset = ComputeResourcePreset.PERFORMANCE,
                    requestedWorkingMemoryPercent = 10
                ),
                DeviceComputeProfile(
                    logicalCpuCores = 12,
                    totalSystemMemoryBytes = 16L * 1_024L * 1_024L * 1_024L,
                    availableSystemMemoryBytes = 12L * 1_024L * 1_024L * 1_024L,
                    lowMemoryThresholdBytes = 1L * 1_024L * 1_024L * 1_024L,
                    appHeapLimitBytes = grantedHeap,
                    lowMemory = false,
                    thermalLevel = ComputeThermalLevel.NONE,
                    deviceName = "Research phone"
                )
            )

        assertEquals(ComputeResourcePreset.BALANCED, policy.preset)
        assertEquals(1, policy.effectiveWorkerCount)
        assertEquals(2, policy.safeMaximumWorkers)
        val expectedBudget =
            grantedHeap / 100L * 10L + (grantedHeap % 100L) * 10L / 100L
        assertEquals(expectedBudget, policy.workingMemoryBudgetBytes)
        assertTrue(policy.workingMemoryBudgetBytes < policy.appHeapLimitBytes)
    }

    private fun device(
        cores: Int,
        thermal: ComputeThermalLevel = ComputeThermalLevel.NONE,
        lowMemory: Boolean = false
    ): DeviceComputeProfile =
        DeviceComputeProfile(
            logicalCpuCores = cores,
            totalSystemMemoryBytes = 8L * 1_024L * 1_024L * 1_024L,
            availableSystemMemoryBytes = 4L * 1_024L * 1_024L * 1_024L,
            lowMemoryThresholdBytes = 512L * 1_024L * 1_024L,
            appHeapLimitBytes = 512L * 1_024L * 1_024L,
            lowMemory = lowMemory,
            thermalLevel = thermal,
            deviceName = "Test device"
        )
}
