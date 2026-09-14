package com.robotkinematicslab.mobile.diagnostics.benchmark.planning

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticBenchmarkConfig
import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

class DiagnosticBenchmarkPlanner {

    companion object {
        private const val SAFE_MIN_LINK_COUNT = 2
        private const val SAFE_MAX_LINK_COUNT = 10
        private const val EXPERIMENTAL_MAX_LINK_COUNT = 100
        private const val NORMAL_MAX_SAMPLES_PER_LINK_COUNT = 5_000
        private const val MAX_UNLIMITED_SAMPLE_COUNT = 1_000_000
        private const val LARGE_BENCHMARK_WARNING_THRESHOLD = 50_000
    }

    fun buildPlan(
        config: DiagnosticBenchmarkConfig
    ): DiagnosticBenchmarkPlan {

        val requestedSingleLinkCount =
            config.topology.robotLinkCount.coerceAtLeast(SAFE_MIN_LINK_COUNT)

        val requestedMinLinkCount =
            config.topology.minLinkCount.coerceAtLeast(SAFE_MIN_LINK_COUNT)

        val requestedMaxLinkCount =
            config.topology.maxLinkCount.coerceAtLeast(requestedMinLinkCount)

        val requestedRangeContainsExperimentalLinks =
            requestedMaxLinkCount > SAFE_MAX_LINK_COUNT

        val requestedSingleLinkIsExperimental =
            requestedSingleLinkCount > SAFE_MAX_LINK_COUNT

        val experimentalMode =
            config.topology.experimentalModeEnabled ||
                    requestedRangeContainsExperimentalLinks ||
                    requestedSingleLinkIsExperimental

        val allowedMaxLinkCount =
            if (experimentalMode) {
                requestedMaxLinkCount.coerceAtMost(EXPERIMENTAL_MAX_LINK_COUNT)
            } else {
                requestedMaxLinkCount.coerceAtMost(SAFE_MAX_LINK_COUNT)
            }

        val allowedMinLinkCount =
            requestedMinLinkCount.coerceIn(
                SAFE_MIN_LINK_COUNT,
                allowedMaxLinkCount
            )

        val usesRangeMode =
            requestedMinLinkCount != requestedMaxLinkCount

        val linkCounts =
            if (usesRangeMode) {
                (allowedMinLinkCount..allowedMaxLinkCount).toList()
            } else {
                val allowedSingleLinkCount =
                    if (experimentalMode) {
                        requestedSingleLinkCount.coerceIn(
                            SAFE_MIN_LINK_COUNT,
                            EXPERIMENTAL_MAX_LINK_COUNT
                        )
                    } else {
                        requestedSingleLinkCount.coerceIn(
                            SAFE_MIN_LINK_COUNT,
                            SAFE_MAX_LINK_COUNT
                        )
                    }

                listOf(allowedSingleLinkCount)
            }

        val safeLinkCounts =
            linkCounts.filter {
                it in SAFE_MIN_LINK_COUNT..SAFE_MAX_LINK_COUNT
            }

        val experimentalLinkCounts =
            linkCounts.filter {
                it > SAFE_MAX_LINK_COUNT
            }

        val samplesPerLinkCount =
            if (config.sampling.unlimitedSamplesEnabled) {
                config.sampling.unlimitedSampleCount.coerceIn(
                    1,
                    MAX_UNLIMITED_SAMPLE_COUNT
                )
            } else {
                config.sampling.samplesPerLinkCount.coerceIn(
                    1,
                    NORMAL_MAX_SAMPLES_PER_LINK_COUNT
                )
            }

        val topologyModeCount =
            if (config.topology.runAllTopologies) {
                DiagnosticJointMode.entries.size
            } else {
                1
            }

        val seedCount =
            config.seeds.seeds.distinct().size.coerceAtLeast(1)

        val totalPlannedSequentialRuns =
            linkCounts.size.toLong() *
                    samplesPerLinkCount.toLong() *
                    seedCount.toLong() *
                    topologyModeCount.toLong()

        val strongReliabilityClaimAllowed =
            experimentalLinkCounts.isEmpty() &&
                    !config.sampling.unlimitedSamplesEnabled &&
                    !experimentalMode

        val warnings =
            buildList {

                if (experimentalMode) {
                    add(
                        "Experimental mode is active. Results above 10 links are experimental and should not be used for strong reliability claims."
                    )
                }

                if (experimentalLinkCounts.isNotEmpty()) {
                    add(
                        "Experimental link counts requested: ${experimentalLinkCounts.joinToString()}. Only link counts 2–10 are safe-mode results."
                    )
                }

                if (safeLinkCounts.isNotEmpty() && experimentalLinkCounts.isNotEmpty()) {
                    add(
                        "This benchmark contains mixed safe and experimental link counts. Safe subset: ${safeLinkCounts.joinToString()}."
                    )
                }

                if (config.sampling.unlimitedSamplesEnabled) {
                    add(
                        "Unlimited sample mode is active. This can take a long time and may affect app responsiveness."
                    )
                }

                if (
                    config.sampling.unlimitedSamplesEnabled &&
                    config.sampling.unlimitedSampleCount > NORMAL_MAX_SAMPLES_PER_LINK_COUNT
                ) {
                    add(
                        "Manual sample count is above the normal 5,000-example slider range."
                    )
                }

                if (
                    !config.sampling.unlimitedSamplesEnabled &&
                    config.sampling.samplesPerLinkCount > NORMAL_MAX_SAMPLES_PER_LINK_COUNT
                ) {
                    add(
                        "Requested sample count exceeded the normal range and was clamped to 5,000."
                    )
                }

                if (totalPlannedSequentialRuns > LARGE_BENCHMARK_WARNING_THRESHOLD) {
                    add(
                        "Large benchmark planned: $totalPlannedSequentialRuns sequential runs. Runtime may be significant."
                    )
                }

                if (linkCounts.isEmpty()) {
                    add(
                        "No link counts were planned. Check the requested link-count range."
                    )
                }
            }

        val reliabilityClaim =
            when {
                experimentalLinkCounts.isNotEmpty() ->
                    "EXPERIMENTAL RESULT ONLY. Link counts above 10 are outside safe-mode reliability claims."

                config.sampling.unlimitedSamplesEnabled ->
                    "SAFE-LINK EXPERIMENTAL SAMPLE RESULT. Link counts are safe, but unlimited sample mode was enabled."

                strongReliabilityClaimAllowed ->
                    "SAFE-MODE RESULT. Link counts are within the supported 2–10 range."

                else ->
                    "DIAGNOSTIC RESULT. Review benchmark warnings before using this as a reliability claim."
            }

        return DiagnosticBenchmarkPlan(
            linkCounts = linkCounts,
            safeLinkCounts = safeLinkCounts,
            experimentalLinkCounts = experimentalLinkCounts,
            samplesPerLinkCount = samplesPerLinkCount,
            totalPlannedSequentialRuns = totalPlannedSequentialRuns,
            isExperimental = experimentalMode,
            isUnlimited = config.sampling.unlimitedSamplesEnabled,
            strongReliabilityClaimAllowed = strongReliabilityClaimAllowed,
            warnings = warnings,
            reliabilityClaim = reliabilityClaim
        )
    }
}
