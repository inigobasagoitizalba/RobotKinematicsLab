package com.robotkinematicslab.mobile.ui.shared.progress

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample

internal enum class TelemetryDashboardCategory(
    val label: String,
    val icon: String,
    val description: String
) {
    FLOW("Speed", "↗", "Throughput and completion estimate"),
    SUSTAINED("Sustain", "≈", "Normalised retention, thermal rise and process utilisation"),
    MEMORY("Memory", "▦", "Runtime, native heap and device memory"),
    CPU("CPU", "◉", "Scheduler time, load and core frequency"),
    GARBAGE_COLLECTION("GC", "↻", "Collection counters, duration and pressure"),
    ALLOCATIONS("Hotspots", "◇", "Tracked allocation sections over time")
}

internal data class TelemetryChartPoint(
    val sampleIndex: Int,
    val elapsedSeconds: Double,
    val value: Double,
    val breakBefore: Boolean = false
)

internal data class TelemetryChartSeries(
    val id: String,
    val label: String,
    val points: List<TelemetryChartPoint>
)

internal data class TelemetryChartGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val unit: String,
    val series: List<TelemetryChartSeries>
)

internal data class TelemetryFinalBar(
    val label: String,
    val value: Double,
    val capacity: Double,
    val unit: String
)

internal enum class TelemetryEvidenceStatus {
    AVAILABLE,
    WAITING,
    UNAVAILABLE
}

internal data class TelemetryChartEvidence(
    val status: TelemetryEvidenceStatus,
    val capturedPointCount: Int,
    val availableSeriesCount: Int,
    val expectedSeriesCount: Int,
    val title: String,
    val detail: String
) {
    val hasPartialSeries: Boolean
        get() = availableSeriesCount in 1 until expectedSeriesCount
}

internal fun preferredTelemetryChartGroup(
    groups: List<TelemetryChartGroup>
): TelemetryChartGroup? =
    groups.firstOrNull { group -> group.series.any { it.points.isNotEmpty() } }
        ?: groups.firstOrNull()

internal fun assessTelemetryChartEvidence(
    group: TelemetryChartGroup,
    capturedSampleCount: Int,
    capturedDurationSeconds: Double,
    finished: Boolean
): TelemetryChartEvidence {
    val availableSeries = group.series.count { it.points.isNotEmpty() }
    val pointCount = group.series.sumOf { it.points.size }
    if (pointCount > 0) {
        return TelemetryChartEvidence(
            status = TelemetryEvidenceStatus.AVAILABLE,
            capturedPointCount = pointCount,
            availableSeriesCount = availableSeries,
            expectedSeriesCount = group.series.size,
            title = "Telemetry evidence available",
            detail =
                if (availableSeries in 1 until group.series.size) {
                    "$availableSeries of ${group.series.size} signals were exposed for this run. Missing signals remain explicitly absent."
                } else {
                    "All exposed signals contain measured values."
                }
        )
    }

    if (!finished) {
        val waitingForInterval = capturedSampleCount < 2 || capturedDurationSeconds <= 0.0
        return TelemetryChartEvidence(
            status = TelemetryEvidenceStatus.WAITING,
            capturedPointCount = 0,
            availableSeriesCount = 0,
            expectedSeriesCount = group.series.size,
            title = if (waitingForInterval) "Waiting for a second timed sample" else "Waiting for an exposed measurement",
            detail =
                if (waitingForInterval) {
                    "A time-series needs at least two measurements separated in time. The run can continue normally."
                } else {
                    "The operation is still running, but this metric has not been exposed yet. Android may hide optional device counters."
                }
        )
    }

    val timelineTooShort = capturedSampleCount < 2 || capturedDurationSeconds <= 0.0
    return TelemetryChartEvidence(
        status = TelemetryEvidenceStatus.UNAVAILABLE,
        capturedPointCount = 0,
        availableSeriesCount = 0,
        expectedSeriesCount = group.series.size,
        title = if (timelineTooShort) "Run completed without a measurable timeline" else "Metric unavailable for this run",
        detail =
            if (timelineTooShort) {
                "The calculation result remains valid, but the run ended before two time-separated samples existed. Use a longer workload to study a trend."
            } else {
                unavailableTelemetryDetail(group.id)
            }
    )
}

private fun unavailableTelemetryDetail(groupId: String): String =
    when (groupId) {
        "system_load", "cpu_frequency", "thermal_timeline" ->
            "Android or the device vendor did not expose this optional sensor or operating-system metric. No value has been invented."

        "gc_counts", "gc_time", "gc_count_rate", "gc_time_rate" ->
            "This Android runtime did not expose compatible ART garbage-collection counters for the captured interval."

        "hotspot_calls", "hotspot_total", "hotspot_latency" ->
            "No instrumented allocation section ran during this operation, so there is no hotspot evidence to plot."

        "eta" ->
            "The run never produced a positive calculation rate from which a remaining-time estimate could be computed."

        else ->
            "No finite, compatible measurement was exposed for this chart. Raw telemetry keeps the unavailable values visible as N/A."
    }

internal fun buildTelemetryChartGroups(
    samples: List<DiagnosticPerformanceSample>
): Map<TelemetryDashboardCategory, List<TelemetryChartGroup>> {
    val cleanSamples =
        samples
            .asSequence()
            .filter { it.elapsedSeconds.isFinite() }
            .sortedWith(compareBy<DiagnosticPerformanceSample> { it.elapsedSeconds }.thenBy { it.sampleIndex })
            .distinctBy { it.sampleIndex }
            .toList()

    val flow =
        listOf(
            chart(
                id = "throughput",
                title = "Calculation rate",
                subtitle = "Recorded work/s from the operation: Single Run uses completed work units / elapsed seconds since start. Work units are not predictions or epochs; other operations retain their recorded rate definition.",
                unit = "work/s",
                samples = cleanSamples,
                definitions = listOf(SeriesDefinition("rate", "Rate") { it.calculationsPerSecond })
            ),
            chart(
                id = "eta",
                title = "Estimated time remaining",
                subtitle = "Recorded estimate in seconds: remaining work / recorded positive work rate. This is an estimate, not a measured completion time.",
                unit = "s",
                samples = cleanSamples,
                definitions = listOf(SeriesDefinition("eta", "ETA") { it.estimatedSecondsRemaining })
            )
        )

    val memory =
        listOf(
            chart(
                id = "runtime_memory",
                title = "Java / runtime memory",
                subtitle = "App managed heap: used = committed total − free; maximum is the process heap ceiling. MiB = 1,048,576 bytes. This is not total device memory.",
                unit = "MiB",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("used", "Used") { it.runtimeMemoryUsedMb },
                        SeriesDefinition("free", "Free") { it.runtimeMemoryFreeMb },
                        SeriesDefinition("total", "Total") { it.runtimeMemoryTotalMb },
                        SeriesDefinition("max", "Maximum") { it.runtimeMemoryMaxMb }
                    )
            ),
            chart(
                id = "native_heap",
                title = "Native heap",
                subtitle = "Native allocator heap: allocated, free and size; separate from the managed heap. MiB = 1,048,576 bytes. Neither is the app’s full resident memory.",
                unit = "MiB",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("allocated", "Allocated") { it.nativeHeapAllocatedMb },
                        SeriesDefinition("free", "Free") { it.nativeHeapFreeMb },
                        SeriesDefinition("size", "Size") { it.nativeHeapSizeMb }
                    )
            ),
            chart(
                id = "system_memory",
                title = "System memory",
                subtitle = "Device-wide available and total memory reported by Android, in MiB. Do not compare its capacity ratio with the app heap ceiling.",
                unit = "MiB",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("available", "Available") { it.availableSystemMemoryMb },
                        SeriesDefinition("total", "Total") { it.totalSystemMemoryMb }
                    )
            )
        )

    val retention = com.robotkinematicslab.mobile.diagnostics.benchmark.analysis.SustainedPerformanceAnalyzer.analyze(cleanSamples)
    val sustained =
        listOf(
            TelemetryChartGroup(
                id = "sustained_retention",
                title = "Sustained performance retention",
                subtitle = "Each signal = 100 × measurement / its own early-window median (first max(1, N/4) timed samples, missing values excluded within that window). Throughput reference ${retention.earlyThroughput.takeIf(Double::isFinite)?.toString() ?: "N/A"} work/s; CPU reference ${retention.earlyCpuFrequencyMhz.takeIf(Double::isFinite)?.toString() ?: "N/A"} MHz. For example, 47.37% means 0.4737 of that signal’s reference, not work completed. Summary compares the last-window median to the same reference.",
                unit = "%",
                series =
                    listOf(
                        normalizedSeries("throughput_retention", "Throughput retained", cleanSamples, retention.earlyThroughput) {
                            it.calculationsPerSecond.takeIf { value -> value >= 0.0 } ?: Double.NaN
                        },
                        normalizedSeries("frequency_retention", "CPU frequency retained", cleanSamples, retention.earlyCpuFrequencyMhz) {
                            it.cpuFrequencyAverageMhz.takeIf { value -> value > 0.0 } ?: Double.NaN
                        }
                    )
            ),
            chart(
                id = "thermal_timeline",
                title = "Measured temperature timeline",
                subtitle = "Battery and SoC temperatures when the device vendor exposes them; missing sensors remain absent.",
                unit = "°C",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("battery", "Battery") { it.batteryTemperatureCelsius },
                        SeriesDefinition("soc", "SoC") { it.socTemperatureCelsius }
                    )
            ),
            TelemetryChartGroup(
                id = "process_utilisation",
                title = "Process CPU utilisation",
                subtitle = "Adjacent process CPU-time delta / wall-time delta × 100; 100% = one CPU core, so multi-core use may exceed 100%. GC duty = adjacent ART GC-duration delta / the same elapsed interval × 100; it is not necessarily stopped-app time.",
                unit = "%",
                series =
                    listOf(
                        deltaPercentSeries("process_cpu", "Process CPU", cleanSamples) { it.processCpuTimeMs },
                        deltaPercentSeries("gc_duty", "GC duty", cleanSamples) { it.gcTimeMs }
                    )
            )
        )

    val cpu =
        listOf(
            chart(
                id = "system_load",
                title = "System load average",
                subtitle = "Unitless operating-system load signal when the vendor exposes it.",
                unit = "load",
                samples = cleanSamples,
                definitions = listOf(SeriesDefinition("load", "Load average") { it.systemLoadAverage })
            ),
            chart(
                id = "cpu_time",
                title = "CPU time",
                subtitle = "Cumulative process and sampler-thread CPU time.",
                unit = "ms",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("process", "Process") { it.processCpuTimeMs.validCounter() },
                        SeriesDefinition("thread", "Current thread") { it.currentThreadCpuTimeMs.validCounter() }
                    )
            ),
            chart(
                id = "cpu_frequency",
                title = "Readable core frequency",
                subtitle = "Minimum, arithmetic average and maximum MHz across readable cores at each capture. The readable core count may change; this is not clock speed averaged over the sampling interval.",
                unit = "MHz",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("min", "Minimum") { it.cpuFrequencyMinMhz },
                        SeriesDefinition("average", "Average") { it.cpuFrequencyAverageMhz },
                        SeriesDefinition("max", "Maximum") { it.cpuFrequencyMaxMhz }
                    )
            )
        )

    val gcCountRates =
        rateSeries(
            id = "gc_rate",
            label = "GC count",
            samples = cleanSamples,
            value = { it.gcCount.validCounter() }
        )

    val blockingGcCountRates =
        rateSeries(
            id = "blocking_gc_rate",
            label = "Blocking GC count",
            samples = cleanSamples,
            value = { it.blockingGcCount.validCounter() }
        )

    val gcTimeRates =
        rateSeries(
            id = "gc_time_rate",
            label = "GC time",
            samples = cleanSamples,
            value = { it.gcTimeMs.validCounter() }
        )

    val blockingGcTimeRates =
        rateSeries(
            id = "blocking_gc_time_rate",
            label = "Blocking GC time",
            samples = cleanSamples,
            value = { it.blockingGcTimeMs.validCounter() }
        )

    val runtimeMemoryRates =
        rateSeries(
            id = "runtime_delta",
            label = "Runtime used",
            samples = cleanSamples,
            value = { it.runtimeMemoryUsedMb },
            allowNegativeDelta = true
        )

    val nativeMemoryRates =
        rateSeries(
            id = "native_delta",
            label = "Native allocated",
            samples = cleanSamples,
            value = { it.nativeHeapAllocatedMb },
            allowNegativeDelta = true
        )

    val garbageCollection =
        listOf(
            chart(
                id = "gc_counts",
                title = "Garbage-collection counters",
                subtitle = "Cumulative total and blocking collector invocations.",
                unit = "count",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("gc", "GC count") { it.gcCount.validCounter() },
                        SeriesDefinition("blocking", "Blocking GC") { it.blockingGcCount.validCounter() }
                    )
            ),
            chart(
                id = "gc_time",
                title = "Garbage-collection time",
                subtitle = "Cumulative ART duration of all GC runs and of blocking GC runs (milliseconds); not a measurement of all application pauses.",
                unit = "ms",
                samples = cleanSamples,
                definitions =
                    listOf(
                        SeriesDefinition("gc", "GC time") { it.gcTimeMs.validCounter() },
                        SeriesDefinition("blocking", "Blocking GC time") { it.blockingGcTimeMs.validCounter() }
                    )
            ),
            TelemetryChartGroup(
                id = "gc_count_rate",
                title = "Collection rate",
                subtitle = "Counter deltas divided by the real interval between samples.",
                unit = "count/s",
                series = listOf(gcCountRates, blockingGcCountRates)
            ),
            TelemetryChartGroup(
                id = "gc_time_rate",
                title = "Collection-time rate",
                subtitle = "Milliseconds spent in collection per elapsed second.",
                unit = "ms/s",
                series = listOf(gcTimeRates, blockingGcTimeRates)
            ),
            TelemetryChartGroup(
                id = "allocation_pressure",
                title = "Memory-change pressure",
                subtitle = "Signed memory change per second; negative values can indicate reclamation.",
                unit = "MiB/s",
                series = listOf(runtimeMemoryRates, nativeMemoryRates)
            )
        )

    val hotspotLabels =
        cleanSamples.flatMap { it.allocationHotspots }
            .groupBy { it.label }.entries
            .sortedByDescending { (_, values) -> values.maxOf { it.totalPositiveDeltaMb } }
            .map { it.key }

    val allocations =
        listOf(
            hotspotChart(
                id = "hotspot_calls",
                title = "Allocation hotspot calls",
                subtitle = "Cumulative invocation count for every tracked section.",
                unit = "calls",
                samples = cleanSamples,
                labels = hotspotLabels,
                value = { it.callCount.toDouble() }
            ),
            hotspotChart(
                id = "hotspot_total",
                title = "Positive allocation by hotspot",
                subtitle = "Sum of max(0, heap used after − before) for instrumented calls only. MiB; concurrent work and GC affect this proxy, so it is not exact allocated bytes or an allocation profiler.",
                unit = "MiB",
                samples = cleanSamples,
                labels = hotspotLabels,
                value = { it.totalPositiveDeltaMb }
            ),
            hotspotChart(
                id = "hotspot_latency",
                title = "Average hotspot duration",
                subtitle = "Accumulated measured wall-clock duration / recorded call count for each instrumented section (ms/call). Concurrent sections may overlap; totals are not additive.",
                unit = "ms",
                samples = cleanSamples,
                labels = hotspotLabels,
                value = { it.averageElapsedMs }
            )
        )

    return linkedMapOf(
        TelemetryDashboardCategory.FLOW to flow,
        TelemetryDashboardCategory.SUSTAINED to sustained,
        TelemetryDashboardCategory.MEMORY to memory,
        TelemetryDashboardCategory.CPU to cpu,
        TelemetryDashboardCategory.GARBAGE_COLLECTION to garbageCollection,
        TelemetryDashboardCategory.ALLOCATIONS to allocations
    ).mapValues { (_, groups) -> groups.map { group ->
        val positions = cleanSamples.mapIndexed { index, sample -> sample.sampleIndex to index }.toMap()
        group.copy(series = group.series.map { series ->
            series.copy(points = series.points.mapIndexed { index, point ->
                val previous = series.points.getOrNull(index - 1)
                point.copy(breakBefore = previous != null &&
                    ((positions[point.sampleIndex] ?: -1) != (positions[previous.sampleIndex] ?: -2) + 1 ||
                        point.elapsedSeconds <= previous.elapsedSeconds))
            })
        })
    } }
}

private fun normalizedSeries(
    id: String,
    label: String,
    samples: List<DiagnosticPerformanceSample>,
    baseline: Double,
    value: (DiagnosticPerformanceSample) -> Double
): TelemetryChartSeries {
    return TelemetryChartSeries(
        id = id,
        label = label,
        points = samples.mapNotNull { sample ->
            val measured = value(sample)
            if (measured.isFinite() && measured >= 0.0 && baseline.isFinite() && baseline > 0.0) {
                TelemetryChartPoint(sample.sampleIndex, sample.elapsedSeconds, measured / baseline * 100.0)
            } else {
                null
            }
        }
    )
}

private fun deltaPercentSeries(
    id: String,
    label: String,
    samples: List<DiagnosticPerformanceSample>,
    value: (DiagnosticPerformanceSample) -> Long
): TelemetryChartSeries = TelemetryChartSeries(
    id = id,
    label = label,
    points = samples.zipWithNext().mapNotNull { (previous, current) ->
        val elapsedMillis = (current.elapsedSeconds - previous.elapsedSeconds) * 1_000.0
        val previousValue = value(previous)
        val currentValue = value(current)
        if (elapsedMillis <= 0.0 || previousValue < 0L || currentValue < previousValue) return@mapNotNull null
        TelemetryChartPoint(
            sampleIndex = current.sampleIndex,
            elapsedSeconds = current.elapsedSeconds,
            value = (currentValue - previousValue).toDouble() / elapsedMillis * 100.0
        )
    }
)

internal fun buildFinalTelemetryBars(
    sample: DiagnosticPerformanceSample
): List<TelemetryFinalBar> {
    return listOf(
        TelemetryFinalBar("Runtime used", sample.runtimeMemoryUsedMb, sample.runtimeMemoryMaxMb, "MiB"),
        TelemetryFinalBar("Runtime free", sample.runtimeMemoryFreeMb, sample.runtimeMemoryMaxMb, "MiB"),
        TelemetryFinalBar("Runtime total", sample.runtimeMemoryTotalMb, sample.runtimeMemoryMaxMb, "MiB"),
        TelemetryFinalBar("Runtime maximum", sample.runtimeMemoryMaxMb, sample.runtimeMemoryMaxMb, "MiB"),
        TelemetryFinalBar("Native allocated", sample.nativeHeapAllocatedMb, sample.nativeHeapSizeMb, "MiB"),
        TelemetryFinalBar("Native free", sample.nativeHeapFreeMb, sample.nativeHeapSizeMb, "MiB"),
        TelemetryFinalBar("System available", sample.availableSystemMemoryMb, sample.totalSystemMemoryMb, "MiB"),
        TelemetryFinalBar("System total", sample.totalSystemMemoryMb, sample.totalSystemMemoryMb, "MiB")
    ).filter { bar ->
        bar.value.isFinite() && bar.capacity.isFinite() && bar.capacity > 0.0
    }
}

private data class SeriesDefinition(
    val id: String,
    val label: String,
    val value: (DiagnosticPerformanceSample) -> Double
)

private fun chart(
    id: String,
    title: String,
    subtitle: String,
    unit: String,
    samples: List<DiagnosticPerformanceSample>,
    definitions: List<SeriesDefinition>
): TelemetryChartGroup {
    return TelemetryChartGroup(
        id = id,
        title = title,
        subtitle = subtitle,
        unit = unit,
        series =
            definitions.map { definition ->
                TelemetryChartSeries(
                    id = definition.id,
                    label = definition.label,
                    points =
                        samples.mapNotNull { sample ->
                            val value = definition.value(sample)
                            if (value.isFinite()) {
                                TelemetryChartPoint(sample.sampleIndex, sample.elapsedSeconds, value)
                            } else {
                                null
                            }
                        }
                )
            }
    )
}

private fun rateSeries(
    id: String,
    label: String,
    samples: List<DiagnosticPerformanceSample>,
    value: (DiagnosticPerformanceSample) -> Double,
    allowNegativeDelta: Boolean = false
): TelemetryChartSeries {
    val points =
        samples.zipWithNext().mapNotNull { (previous, current) ->
            val elapsed = current.elapsedSeconds - previous.elapsedSeconds
            val previousValue = value(previous)
            val currentValue = value(current)
            if (elapsed <= 0.0 || !elapsed.isFinite() || !previousValue.isFinite() || !currentValue.isFinite()) {
                return@mapNotNull null
            }
            val delta = currentValue - previousValue
            if (!allowNegativeDelta && delta < 0.0) {
                return@mapNotNull null
            }
            TelemetryChartPoint(
                sampleIndex = current.sampleIndex,
                elapsedSeconds = current.elapsedSeconds,
                value = delta / elapsed
            )
        }
    return TelemetryChartSeries(id = id, label = label, points = points)
}

private fun hotspotChart(
    id: String,
    title: String,
    subtitle: String,
    unit: String,
    samples: List<DiagnosticPerformanceSample>,
    labels: List<String>,
    value: (com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticAllocationHotspot) -> Double
): TelemetryChartGroup {
    return TelemetryChartGroup(
        id = id,
        title = title,
        subtitle = subtitle,
        unit = unit,
        series =
            labels.mapIndexed { index, label ->
                TelemetryChartSeries(
                    id = "hotspot_${index}_${label.hashCode()}",
                    label = label,
                    points =
                        samples.mapNotNull { sample ->
                            val hotspot = sample.allocationHotspots.firstOrNull { it.label == label }
                                ?: return@mapNotNull null
                            val measured = value(hotspot)
                            if (measured.isFinite()) {
                                TelemetryChartPoint(sample.sampleIndex, sample.elapsedSeconds, measured)
                            } else {
                                null
                            }
                        }
                )
            }
    )
}

private fun Long.validCounter(): Double =
    if (this >= 0L) toDouble() else Double.NaN


/** One lossless adapter for the full shared inspector; sample identity remains inspectable. */
internal fun telemetryInspectorPoints(series: TelemetryChartSeries): List<com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint> =
    series.points.map { com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint(
        x = it.elapsedSeconds, y = it.value, breakBefore = it.breakBefore,
        label = "Sample ${it.sampleIndex}") }


internal fun latestTelemetrySample(samples: List<DiagnosticPerformanceSample>): DiagnosticPerformanceSample? =
    samples.filter { it.elapsedSeconds.isFinite() }.maxWithOrNull(compareBy<DiagnosticPerformanceSample> { it.elapsedSeconds }.thenBy { it.sampleIndex })

internal fun DiagnosticPerformanceSample.recordedTelemetrySnapshot() =
    com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticSystemTelemetry(
        usedRuntimeMemoryMb = runtimeMemoryUsedMb, freeRuntimeMemoryMb = runtimeMemoryFreeMb,
        totalRuntimeMemoryMb = runtimeMemoryTotalMb, maxRuntimeMemoryMb = runtimeMemoryMaxMb,
        nativeHeapAllocatedMb = nativeHeapAllocatedMb, nativeHeapFreeMb = nativeHeapFreeMb, nativeHeapSizeMb = nativeHeapSizeMb,
        availableSystemMemoryMb = availableSystemMemoryMb, totalSystemMemoryMb = totalSystemMemoryMb, lowMemory = lowMemory,
        cpuCoreCount = cpuCoreCount, systemLoadAverage = systemLoadAverage, processCpuTimeMs = processCpuTimeMs,
        currentThreadCpuTimeMs = currentThreadCpuTimeMs,
        cpuFrequencySnapshot = com.robotkinematicslab.mobile.diagnostics.benchmark.runtime.DiagnosticCpuFrequencySnapshot(
            readableCoreCount = readableCpuFrequencyCoreCount, minFrequencyMhz = cpuFrequencyMinMhz,
            maxFrequencyMhz = cpuFrequencyMaxMhz, averageFrequencyMhz = cpuFrequencyAverageMhz),
        gcCount = gcCount, gcTimeMs = gcTimeMs, blockingGcCount = blockingGcCount, blockingGcTimeMs = blockingGcTimeMs,
        allocationHotspots = allocationHotspots, batteryLevelPercent = batteryLevelPercent,
        batteryTemperatureCelsius = batteryTemperatureCelsius, isCharging = isCharging,
        thermalStatus = thermalStatus, socTemperatureCelsius = socTemperatureCelsius,
        note = note + " Per-core frequency lists and thermal-zone lists are not stored in timeline samples.")
