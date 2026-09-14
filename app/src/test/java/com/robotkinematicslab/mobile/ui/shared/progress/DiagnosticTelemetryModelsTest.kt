package com.robotkinematicslab.mobile.ui.shared.progress

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticAllocationHotspot
import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticCpuFrequencySnapshot
import com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticSystemTelemetry
import java.nio.file.Files
import java.io.File
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticTelemetryModelsTest {

    @Test
    fun compatibleMeasurementsBecomeSeparateScientificCharts() {
        val samples = listOf(sample(1, 1.0, 10L, 4.0), sample(2, 3.0, 16L, 2.0))
        val groups = buildTelemetryChartGroups(samples)

        val flow = requireNotNull(groups[TelemetryDashboardCategory.FLOW])
        val eta = flow.single { it.id == "eta" }
        assertEquals("s", eta.unit)
        assertEquals(listOf(4.0, 2.0), eta.series.single().points.map { it.value })

        val runtime = requireNotNull(groups[TelemetryDashboardCategory.MEMORY])
            .single { it.id == "runtime_memory" }
        assertEquals("MiB", runtime.unit)
        assertEquals(listOf("Used", "Free", "Total", "Maximum"), runtime.series.map { it.label })

        val gcRate = requireNotNull(groups[TelemetryDashboardCategory.GARBAGE_COLLECTION])
            .single { it.id == "gc_count_rate" }
            .series.first { it.id == "gc_rate" }
        assertEquals(3.0, gcRate.points.single().value, 1e-12)

        val sustained = requireNotNull(groups[TelemetryDashboardCategory.SUSTAINED])
        assertTrue(sustained.any { it.id == "sustained_retention" })
        assertTrue(sustained.any { it.id == "process_utilisation" })

        val hotspotCalls = requireNotNull(groups[TelemetryDashboardCategory.ALLOCATIONS])
            .single { it.id == "hotspot_calls" }
        assertEquals(listOf(10.0, 16.0), hotspotCalls.series.single().points.map { it.value })
    }

    @Test
    fun finalArchiveInventoryContainsEveryTelemetryChartAndSnapshotExactlyOnce() {
        val samples = listOf(sample(1, 1.0, 10L, 4.0), sample(2, 3.0, 16L, 2.0))

        val figures = buildFinalTelemetryFigureSpecs(samples)

        assertEquals(REQUIRED_FINAL_TELEMETRY_FIGURE_IDS, figures.map { it.id })
        assertEquals(20, figures.size)
        assertEquals(figures.size, figures.map { it.figureType }.distinct().size)
        assertTrue(figures.last().bars.isNotEmpty())
        assertTrue(figures.dropLast(1).all { it.bars.isEmpty() })
    }

    @Test
    fun finalArchiveStillRecordsUnavailableVendorTelemetryAsExplicitFigures() {
        val unavailable = sample(1, 1.0, 10L, Double.NaN).copy(
            systemLoadAverage = Double.NaN,
            cpuFrequencyMinMhz = Double.NaN,
            cpuFrequencyAverageMhz = Double.NaN,
            cpuFrequencyMaxMhz = Double.NaN,
            batteryTemperatureCelsius = Double.NaN,
            socTemperatureCelsius = Double.NaN,
            allocationHotspots = emptyList()
        )

        val figures = buildFinalTelemetryFigureSpecs(listOf(unavailable))

        assertEquals(REQUIRED_FINAL_TELEMETRY_FIGURE_IDS, figures.map { it.id })
        assertTrue(figures.single { it.id == "eta" }.series.all { it.points.isEmpty() })
        assertTrue(figures.single { it.id == "thermal_timeline" }.series.all { it.points.isEmpty() })
        assertTrue(figures.single { it.id == "hotspot_calls" }.series.isEmpty())
    }

    @Test
    fun finalFigureKeysCoverTheCompleteTimelineRatherThanOnlyTheFirstOrLastSample() {
        val original = listOf(
            sample(1, 1.0, 10L, 4.0),
            sample(2, 2.0, 12L, 3.0),
            sample(3, 3.0, 16L, 2.0)
        )
        val changedMiddle = original.toMutableList().also { samples ->
            samples[1] = samples[1].copy(calculationsPerSecond = 9_999.0)
        }

        assertTrue(finalTelemetryArchiveKey(original) != finalTelemetryArchiveKey(changedMiddle))
        assertTrue(
            buildFinalTelemetryFigureSpecs(original).single { it.id == "throughput" }.dataKey !=
                buildFinalTelemetryFigureSpecs(changedMiddle).single { it.id == "throughput" }.dataKey
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyFinalTelemetryCannotProduceMisleadingFigures() {
        buildFinalTelemetryFigureSpecs(emptyList())
    }

    @Test
    fun displayAndFinalRenderingKeepEveryTelemetryPointIncludingIntermediateSpike() {
        val spikeIndex = 503
        val points =
            (0 until 1_000).map { index ->
                TelemetryChartPoint(
                    sampleIndex = index,
                    elapsedSeconds = index / 10.0,
                    value = if (index == spikeIndex) 1_000_000.0 else index.toDouble()
                )
            }
        val previewPoints = downsampleTelemetryPoints(points, maximumPoints = 240)
        val exportedPoints = downsampleTelemetryPoints(points, maximumPoints = 600)

        assertEquals(points, previewPoints)
        assertEquals(points, exportedPoints)
        assertEquals(1_000, previewPoints.size)
        assertEquals(1_000, exportedPoints.size)
        assertEquals(points[spikeIndex], previewPoints[spikeIndex])
        assertEquals(points[spikeIndex], exportedPoints[spikeIndex])
    }

    @Test
    fun finalTelemetrySpecsRetainEveryCapturedSamplePastFormerExportLimit() {
        val samples =
            (1..1_001).map { index ->
                sample(index, index / 10.0, index.toLong(), 2_000.0 - index).copy(
                    calculationsPerSecond = if (index == 607) 999_999.0 else index.toDouble()
                )
            }

        val throughput =
            buildFinalTelemetryFigureSpecs(samples)
                .single { it.id == "throughput" }
                .series.single()

        assertEquals(samples.size, throughput.points.size)
        assertEquals(999_999.0, throughput.points.single { it.sampleIndex == 607 }.value, 0.0)
    }

    @Test
    fun preferredChartSkipsAnUnsupportedMetricWhenMeasuredEvidenceExists() {
        val empty = TelemetryChartGroup("system_load", "System load", "", "cores", listOf(TelemetryChartSeries("load", "Load", emptyList())))
        val measured = TelemetryChartGroup(
            "cpu_time",
            "CPU time",
            "",
            "ms",
            listOf(TelemetryChartSeries("process", "Process", listOf(TelemetryChartPoint(1, 0.0, 10.0))))
        )

        assertEquals("cpu_time", preferredTelemetryChartGroup(listOf(empty, measured))?.id)
    }

    @Test
    fun completedUnsupportedMetricIsNotReportedAsStillCollecting() {
        val group = TelemetryChartGroup(
            "system_load",
            "System load",
            "",
            "cores",
            listOf(TelemetryChartSeries("load", "Load", emptyList()))
        )

        val evidence = assessTelemetryChartEvidence(group, capturedSampleCount = 5, capturedDurationSeconds = 2.0, finished = true)

        assertEquals(TelemetryEvidenceStatus.UNAVAILABLE, evidence.status)
        assertEquals("Metric unavailable for this run", evidence.title)
        assertFalse(evidence.detail.contains("collect", ignoreCase = true))
    }

    @Test
    fun runningChartExplainsThatItNeedsTimedEvidence() {
        val group = TelemetryChartGroup(
            "eta",
            "ETA",
            "",
            "s",
            listOf(TelemetryChartSeries("eta", "ETA", emptyList()))
        )

        val evidence = assessTelemetryChartEvidence(group, capturedSampleCount = 1, capturedDurationSeconds = 0.0, finished = false)

        assertEquals(TelemetryEvidenceStatus.WAITING, evidence.status)
        assertTrue(evidence.title.contains("second timed sample"))
    }

    @Test
    fun sessionsRoundTripAndIdenticalEvidenceIsDeduplicated() {
        val root = Files.createTempDirectory("telemetry-session-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val samples = listOf(sample(1, 1.0, 10L, 4.0), sample(2, 3.0, 16L, 2.0))

        val first = repository.save("Thesis A", "Diagnostic", "Run 1", samples)
        val duplicate = repository.save("Thesis A", "Diagnostic", "Run 1", samples)
        val restored = repository.listSessions().single()

        assertEquals(first.id, duplicate.id)
        assertEquals(first.id, restored.id)
        assertEquals("Thesis A", restored.projectName)
        assertEquals(2, restored.sampleCount)
        assertTrue(restored.chartGroups[TelemetryDashboardCategory.MEMORY].orEmpty().isNotEmpty())
    }

    @Test
    fun sessionsDifferingOnlyInAnIntermediateSampleAreNotDeduplicated() {
        val root = Files.createTempDirectory("telemetry-fingerprint-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val firstSamples = listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0), sample(3, 3.0, 16L, 2.0))
        val changedSamples = firstSamples.toMutableList().also { samples ->
            samples[1] = samples[1].copy(calculationsPerSecond = 9_999.0)
        }

        val first = repository.save("Thesis A", "Diagnostic", "Run", firstSamples)
        val changed = repository.save("Thesis A", "Diagnostic", "Run", changedSamples)

        assertTrue(first.fingerprint != changed.fingerprint)
        assertEquals(2, repository.listSessions().size)
    }

    @Test
    fun unsupportedTelemetryManifestIsIgnored() {
        val root = Files.createTempDirectory("telemetry-schema-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save("Thesis A", "Diagnostic", "Run", listOf(sample(1, 1.0, 10L, 4.0)))
        val manifestFile = File(saved.directoryPath, "session.properties")
        val properties = Properties().apply { manifestFile.inputStream().use(::load) }
        properties.setProperty("schemaVersion", "999")
        manifestFile.outputStream().use { output -> properties.store(output, "future") }

        assertTrue(repository.listSessions().isEmpty())
    }

    @Test
    fun integrityFingerprintRejectsMisleadingSampleCount() {
        val root = Files.createTempDirectory("telemetry-manifest-integrity-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save(
            "Thesis A",
            "Diagnostic",
            "Run",
            listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0))
        )
        val manifestFile = File(saved.directoryPath, "session.properties")
        val properties = Properties().apply { manifestFile.inputStream().use(::load) }
        properties.setProperty("sampleCount", "999")
        manifestFile.outputStream().use { output -> properties.store(output, "tampered") }

        assertTrue(repository.listSessions().isEmpty())
    }

    @Test
    fun sessionWrittenBeforeIntegrityMetadataRemainsReadable() {
        val root = Files.createTempDirectory("telemetry-legacy-integrity-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save(
            "Thesis A",
            "Diagnostic",
            "Legacy run",
            listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0))
        )
        val manifestFile = File(saved.directoryPath, "session.properties")
        val properties = Properties().apply { manifestFile.inputStream().use(::load) }
        properties.remove("curvesSha256")
        properties.remove("integritySha256")
        manifestFile.outputStream().use { output -> properties.store(output, "legacy") }

        assertEquals(saved.id, repository.listSessions().single().id)
    }

    @Test
    fun legacySessionWithConflictingCurveMetadataIsRejected() {
        val root = Files.createTempDirectory("telemetry-legacy-metadata-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save(
            "Thesis A",
            "Diagnostic",
            "Legacy run",
            listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0))
        )
        val manifestFile = File(saved.directoryPath, "session.properties")
        val properties = Properties().apply { manifestFile.inputStream().use(::load) }
        properties.remove("curvesSha256")
        properties.remove("integritySha256")
        manifestFile.outputStream().use { output -> properties.store(output, "legacy") }
        val curves = File(saved.directoryPath, "telemetry-curves.tsv")
        val lines = curves.readLines().toMutableList()
        val conflictingRow = lines[2].split('\t').toMutableList().also { fields -> fields[1] = "1" }
        lines[2] = conflictingRow.joinToString("\t")
        curves.writeText(lines.joinToString("\n", postfix = "\n"))

        assertTrue(repository.listSessions().isEmpty())
    }

    @Test
    fun corruptTelemetryCurveRowRejectsTheWholeSessionInsteadOfLoadingPartialEvidence() {
        val root = Files.createTempDirectory("telemetry-corrupt-row-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save(
            "Thesis A",
            "Diagnostic",
            "Run",
            listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0))
        )
        val curves = File(saved.directoryPath, "telemetry-curves.tsv")
        curves.appendText("\nMEMORY\ttruncated-corrupt-row")

        assertTrue(repository.listSessions().isEmpty())
    }

    @Test
    fun missingTelemetryCurveHeaderRejectsTheWholeSession() {
        val root = Files.createTempDirectory("telemetry-missing-header-test").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save(
            "Thesis A",
            "Diagnostic",
            "Run",
            listOf(sample(1, 1.0, 10L, 4.0), sample(2, 2.0, 12L, 3.0))
        )
        val curves = File(saved.directoryPath, "telemetry-curves.tsv")
        curves.writeText(curves.readLines().drop(1).joinToString("\n"))

        assertTrue(repository.listSessions().isEmpty())
    }

    @Test
    fun memoryPreservesEveryRawMeasurementForShortTypicalAndLongSessions() {
        listOf(2, 76, 137, 10_000).forEach { count ->
            val raw = (1..count).map { sample(it, it * 0.25, it.toLong(), 1.0) }
            val before = raw.toList()
            val group = buildTelemetryChartGroups(raw).getValue(TelemetryDashboardCategory.MEMORY).first()
            val used = group.series.first { it.id == "used" }
            assertEquals(count, used.points.size)
            assertEquals(raw.map { it.runtimeMemoryUsedMb }, used.points.map { it.value })
            assertEquals(raw.map { it.elapsedSeconds }, telemetryInspectorPoints(used).map { it.x })
            assertEquals(used.points.map { it.value }, telemetryInspectorPoints(used).map { it.y })
            assertEquals(used.points, telemetryPointsForRendering(used.points))
            assertEquals("Sample $count", telemetryInspectorPoints(used).last().label)
            assertEquals("MiB", group.unit)
            val summary = com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.SustainedPerformanceAnalyzer.analyze(raw)
            assertEquals(raw.maxOf { it.runtimeMemoryUsedMb }, summary.peakRuntimeMemoryMb, 0.0)
            assertEquals(before, raw)
        }
    }

    @Test
    fun missingMemoryIsNotInventedFromSampleCountAndGapsReachInspectorAndStorage() {
        val raw = (1..137).map { sample(it, it.toDouble(), it.toLong(), 1.0).copy(
            runtimeMemoryUsedMb = if (it == 2) Double.NaN else it.toDouble(),
            nativeHeapAllocatedMb = Double.NaN) }
        val groups = buildTelemetryChartGroups(raw)
        val runtime = groups.getValue(TelemetryDashboardCategory.MEMORY).first()
        val used = runtime.series.first { it.id == "used" }
        assertEquals(136, used.points.size)
        assertTrue(used.points.first { it.sampleIndex == 3 }.breakBefore)
        assertTrue(telemetryInspectorPoints(used).first { it.label == "Sample 3" }.breakBefore)
        assertTrue(groups.getValue(TelemetryDashboardCategory.MEMORY).first { it.id == "native_heap" }
            .series.first { it.id == "allocated" }.points.isEmpty())
        val root = Files.createTempDirectory("telemetry-gap-roundtrip").toFile()
        val repository = TelemetrySessionRepository(root)
        val saved = repository.save("Study", "Training", "137 captures", raw)
        val file = File(saved.directoryPath, "telemetry-curves.tsv")
        val bytes = file.readBytes()
        val restored = repository.listSessions().single().chartGroups.getValue(TelemetryDashboardCategory.MEMORY)
            .first { it.id == "runtime_memory" }.series.first { it.id == "used" }
        assertEquals(used.points, restored.points)
        assertTrue(bytes.contentEquals(file.readBytes()))
    }

    @Test
    fun normalizedCurveUsesTheSameEarlyCapturedWindowAsSummaryWhenValuesAreMissing() {
        val raw = (0 until 8).map { index -> sample(index, index.toDouble(), 1L, 1.0).copy(
            calculationsPerSecond = when(index) { 0 -> Double.NaN; 1 -> 100.0; else -> 50.0 },
            cpuFrequencyAverageMhz = if(index == 0) Double.NaN else 2000.0) }
        val evidence = com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.SustainedPerformanceAnalyzer.analyze(raw)
        val group = buildTelemetryChartGroups(raw).getValue(TelemetryDashboardCategory.SUSTAINED).first()
        val rate = group.series.first { it.id == "throughput_retention" }
        assertEquals(100.0, evidence.earlyThroughput, 0.0)
        assertEquals(50.0, evidence.throughputRetentionPercent, 0.0)
        assertEquals(evidence.throughputRetentionPercent, rate.points.last().value, 0.0)
        assertTrue(group.subtitle.contains("100.0 work/s"))
        assertTrue(group.subtitle.contains("2000.0 MHz"))
        val absentEarly = raw.map { if(it.sampleIndex < 2) it.copy(calculationsPerSecond = Double.NaN) else it }
        assertTrue(buildTelemetryChartGroups(absentEarly).getValue(TelemetryDashboardCategory.SUSTAINED).first()
            .series.first().points.isEmpty())
    }

    @Test
    fun missingFinalHotspotCannotEraseEarlierInstrumentedEvidence() {
        val raw = listOf(sample(1, 1.0, 10L, 1.0), sample(2, 2.0, 20L, 1.0).copy(allocationHotspots = emptyList()))
        val groups = buildTelemetryChartGroups(raw).getValue(TelemetryDashboardCategory.ALLOCATIONS)
        assertEquals(listOf(10.0), groups.first().series.single().points.map { it.value })
    }

    @Test
    fun zeroThroughputIsMeasuredStallRatherThanAnAbsentValue() {
        val raw = (0 until 8).map { sample(it, it.toDouble(), 1L, 1.0).copy(calculationsPerSecond = if(it < 2) 100.0 else 0.0) }
        val evidence = com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.SustainedPerformanceAnalyzer.analyze(raw)
        val series = buildTelemetryChartGroups(raw).getValue(TelemetryDashboardCategory.SUSTAINED).first().series.first()
        assertEquals(0.0, evidence.throughputRetentionPercent, 0.0)
        assertEquals(8, series.points.size)
        assertEquals(0.0, series.points.last().value, 0.0)
    }

    @Test
    fun rawSnapshotAndAdjacentGcRatesMatchTheFinalPlottedCapture() {
        val raw = (1..4).map { sample(it, it * 0.05, it.toLong(), 1.0).copy(gcCount = it * it.toLong()) }
        val final = requireNotNull(latestTelemetrySample(raw.reversed()))
        val snapshot = final.recordedTelemetrySnapshot()
        assertEquals(final.runtimeMemoryUsedMb, snapshot.usedRuntimeMemoryMb, 0.0)
        assertEquals(final.gcCount, snapshot.gcCount)
        assertEquals(final.cpuFrequencyAverageMhz, snapshot.cpuFrequencySnapshot.averageFrequencyMhz, 0.0)
        val gc = buildTelemetryChartGroups(raw).getValue(TelemetryDashboardCategory.GARBAGE_COLLECTION)
            .first { it.id == "gc_count_rate" }.series.first().points.last()
        assertEquals(gc.value, buildGcRateSnapshot(raw.reversed()).gcCountPerSecond, 0.0)
        val bars = buildFinalTelemetryFigureSpecs(raw.reversed()).last().bars
        assertEquals(final.runtimeMemoryUsedMb, bars.first { it.label == "Runtime used" }.value, 0.0)
    }

    private fun sample(
        index: Int,
        elapsedSeconds: Double,
        hotspotCalls: Long,
        etaSeconds: Double
    ): DiagnosticPerformanceSample {
        val telemetry =
            DiagnosticSystemTelemetry(
                usedRuntimeMemoryMb = 20.0 + index,
                freeRuntimeMemoryMb = 10.0 - index,
                totalRuntimeMemoryMb = 32.0,
                maxRuntimeMemoryMb = 256.0,
                nativeHeapAllocatedMb = 8.0 + index,
                nativeHeapFreeMb = 4.0,
                nativeHeapSizeMb = 16.0,
                availableSystemMemoryMb = 2_000.0 - index,
                totalSystemMemoryMb = 8_000.0,
                systemLoadAverage = 1.0 + index,
                processCpuTimeMs = 100L * index,
                currentThreadCpuTimeMs = 40L * index,
                cpuFrequencySnapshot = DiagnosticCpuFrequencySnapshot(2, 900.0, 2_100.0, 1_500.0),
                gcCount = if (index == 1) 4L else 10L,
                gcTimeMs = if (index == 1) 8L else 20L,
                blockingGcCount = if (index == 1) 1L else 3L,
                blockingGcTimeMs = if (index == 1) 2L else 6L,
                allocationHotspots =
                    listOf(
                        DiagnosticAllocationHotspot(
                            label = "IK solve",
                            callCount = hotspotCalls,
                            totalPositiveDeltaMb = hotspotCalls / 10.0,
                            averagePositiveDeltaKb = 4.0,
                            worstPositiveDeltaMb = 0.5,
                            totalElapsedMs = hotspotCalls,
                            averageElapsedMs = 1.5
                        )
                    )
            )
        return DiagnosticPerformanceSample.fromProgressState(
            sampleIndex = index,
            progressState =
                DiagnosticProgressState(
                    isRunning = index < 2,
                    phase = if (index < 2) DiagnosticProgressPhase.SEQUENTIAL_RUNS else DiagnosticProgressPhase.COMPLETED,
                    completedRuns = index,
                    totalRuns = 2,
                    runsPerSecond = 12.0 + index,
                    estimatedSecondsRemaining = etaSeconds,
                    elapsedSeconds = elapsedSeconds,
                    telemetry = telemetry
                )
        ).copy(timestampMs = index * 1_000L)
    }
}
