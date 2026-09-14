package com.robotkinematicslab.mobile.diagnostics.numericalsafety

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticJointMode
import com.robotkinematicslab.mobile.domain.config.IKConfig
import java.io.File
import java.util.Locale
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/** Opt-in desktop campaign that executes the exact Kotlin paths shipped in the Android app. */
class NumericalSafetyAuditBenchmarkTest {

    @Test
    fun runVersionedSafetyAblationWhenRequested() {
        assumeTrue(System.getenv("RKL_NUMERICAL_SAFETY_AUDIT") == "1")
        val output =
            File(
                System.getenv("RKL_NUMERICAL_SAFETY_OUTPUT")
                    ?: "audit-artifacts/numerical-safety-ablation"
            ).absoluteFile
        val seeds =
            (System.getenv("RKL_NUMERICAL_SAFETY_SEEDS")
                ?: System.getenv("RKL_NUMERICAL_SAFETY_SEED")
                ?: "2604")
                .split(',')
                .mapNotNull { it.trim().toIntOrNull() }
                .distinct()
                .takeIf(List<Int>::isNotEmpty)
                ?: listOf(2604)
        val baseConfig =
            NumericalSafetyExperimentConfig(
                linkCounts =
                    System.getenv("RKL_NUMERICAL_SAFETY_LINKS")
                        ?.split(',')
                        ?.mapNotNull { it.trim().toIntOrNull() }
                        ?.distinct()
                        ?.takeIf(List<Int>::isNotEmpty)
                        ?: listOf(3, 5, 10),
                jointModes = DiagnosticJointMode.entries,
                validTrialsPerTopology =
                    System.getenv("RKL_NUMERICAL_SAFETY_TRIALS")?.toIntOrNull() ?: 8,
                includeAdversarialProbes = true,
                includeUnguardedReference = true,
                carryStateBetweenTargets = true,
                randomSeed = seeds.first(),
                stressLevel = 0.9,
                ikConfig =
                    IKConfig(
                        maxIterations = System.getenv("RKL_NUMERICAL_SAFETY_ITERATIONS")?.toIntOrNull() ?: 800,
                        tolerance = System.getenv("RKL_NUMERICAL_SAFETY_TOLERANCE")?.toDoubleOrNull() ?: 1e-6,
                        damping = 0.01,
                        maxStep = 0.02
                    )
            )
        output.mkdirs()
        val reports =
            seeds.map { seed ->
                val report = NumericalSafetyExperiment().run(baseConfig.copy(randomSeed = seed))
                val seedOutput = if (seeds.size == 1) output else File(output, "seed-$seed")
                seedOutput.mkdirs()
                val readable = File(seedOutput, "REPORT.txt")
                val csv = File(seedOutput, "paired-trials.csv")
                NumericalSafetyReportWriter.writeReadableReport(report, readable)
                NumericalSafetyReportWriter.writeTrialCsv(report, csv)

                println("NUMERICAL_SAFETY_REPORT ${readable.absolutePath}")
                println(report.interpretation)
                assertTrue(report.trials.isNotEmpty())
                assertTrue(report.trials.none { it.guarded.unsafeSuccess })
                assertTrue(readable.isFile && readable.length() > 0L)
                assertTrue(csv.isFile && csv.readLines().size == report.trials.size * 2 + 1)
                report
            }
        if (reports.size > 1) {
            writeMultiSeedSummary(reports, File(output, "multi-seed-summary.csv"))
        }
    }

    private fun writeMultiSeedSummary(
        reports: List<NumericalSafetyReport>,
        file: File
    ) {
        file.bufferedWriter().use { writer ->
            writer.appendLine(
                "protocol,seed,validTrials,guardedCertified,guardedSuccessRate," +
                    "unguardedCertified,unguardedSuccessRate,successDeltaPercentagePoints," +
                    "pairedAdmissibleCount,pairedResidualReductionPercent,rawLimitViolationsPrevented," +
                    "unsafeSuccessesPrevented,faultNonFiniteEventsPrevented,meanTimeCostPercent"
            )
            reports.forEach { report ->
                val validImpact = requireNotNull(report.validImpact)
                writer.appendLine(
                    listOf(
                        report.protocolId,
                        report.config.randomSeed,
                        report.validGuardedSummary.trialCount,
                        report.validGuardedSummary.validatedSuccessCount,
                        decimal(validImpact.guardedSuccessRate),
                        report.validUnguardedSummary?.validatedSuccessCount ?: 0,
                        decimal(validImpact.unguardedSuccessRate),
                        decimal(validImpact.successRateDeltaPercentagePoints),
                        validImpact.pairedFiniteResidualCount,
                        decimal(validImpact.pairedResidualReductionPercent),
                        validImpact.rawLimitViolationsPrevented,
                        validImpact.unsafeSuccessesPrevented,
                        report.adversarialImpact?.rawNonFiniteEventsPrevented ?: 0,
                        decimal(validImpact.meanTimeCostPercent)
                    ).joinToString(",")
                )
            }
        }
        assertTrue(file.isFile && file.readLines().size == reports.size + 1)
    }

    private fun decimal(value: Double): String =
        when {
            value.isNaN() -> "NaN"
            value == Double.POSITIVE_INFINITY -> "Infinity"
            value == Double.NEGATIVE_INFINITY -> "-Infinity"
            else -> java.lang.String.format(Locale.US, "%.12g", value)
        }
}
