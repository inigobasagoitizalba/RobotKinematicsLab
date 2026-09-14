package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticExpectedClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunKind
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticRunResult
import com.robotkinematicslab.mobile.diagnostics.benchmark.Layer1DiagnosticReport
import com.robotkinematicslab.mobile.ui.help.JargonAwareText

@Composable
fun DiagnosticRunPreviewCard(
    report: Layer1DiagnosticReport
) {
    val oracleRuns =
        report.runResults.filter {
            it.runKind == DiagnosticRunKind.ORACLE_REACHABLE_CHECK
        }

    val sequentialRuns =
        report.sequentialRuns()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF7F7F7)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "Run Preview",
                style = MaterialTheme.typography.titleMedium
            )

            Text(
                text = "Oracle checks",
                style = MaterialTheme.typography.titleSmall
            )

            oracleRuns.take(10).forEach { run ->
                RunPreviewRow(
                    runLabel = "Oracle ${run.runIndex}",
                    run = run
                )
            }

            Spacer(
                modifier = Modifier.height(6.dp)
            )

            Text(
                text = "First sequential runs",
                style = MaterialTheme.typography.titleSmall
            )

            sequentialRuns.take(20).forEach { run ->
                RunPreviewRow(
                    runLabel = "Run ${run.runIndex}",
                    run = run
                )
            }
        }
    }
}

@Composable
fun RunPreviewRow(
    runLabel: String,
    run: DiagnosticRunResult
) {
    val near =
        run.isNearConverged()

    val close =
        run.isCloseMiss()

    val far =
        run.isFarFailure()

    val background =
        when {
            run.solverAccepted ->
                Color(0xFFEAF8EA)

            near ->
                Color(0xFFFFF8E1)

            close ->
                Color(0xFFFFF3E0)

            run.expectedClass == DiagnosticExpectedClass.UNREACHABLE &&
                    !run.solverAccepted ->
                Color(0xFFEAF8EA)

            else ->
                Color(0xFFFFEEEE)
        }

    val diagnosticClass =
        when {
            run.solverAccepted ->
                "STRICT_SUCCESS"

            near ->
                "NEAR_CONVERGED_DIAGNOSTIC_ONLY"

            close ->
                "CLOSE_MISS"

            far ->
                "FAR_FAILURE"

            else ->
                "REJECTED"
        }

    DiagnosticDisclosureCard(
        title =
            "$runLabel — ${run.transitionFromCaseId ?: "START"} → " +
                "${run.transitionToCaseId}, ${run.expectedClass}",
        subtitle = "$diagnosticClass · final error ${formatDouble(run.finalError)} m",
        accentColor = background
    ) {
        Text("Diagnostic class: $diagnosticClass")
        Text("Progress class: ${run.progressClass}")
        JargonAwareText("Seed bucket: ${run.seedDistanceBucket}")
        Text("Strict accepted: ${run.solverAccepted}")
        Text("Near-converged: $near")
        Text("Close miss: $close")
        Text("Far failure: $far")
        Text("Status: ${run.status}")
        Text("Detail code: ${run.detailCode}")
        JargonAwareText("Initial error: ${formatDouble(run.initialError)} m")
        JargonAwareText("Final error: ${formatDouble(run.finalError)} m")
        Text("Improvement ratio: ${formatPercent(run.improvementRatio)}")
        Text("Seed minimum limit margin: ${formatPercent(run.seedMinNormalizedLimitMargin)}")
        JargonAwareText("Seed log10 condition number: ${formatDouble(run.seedLogConditionNumber)}")
        JargonAwareText("Iteration saturation: ${formatPercent(run.iterationSaturationRatio)}")
        Text("Joint delta norm: ${formatDouble(run.jointDeltaNorm)}")
        Text("Max joint movement: ${formatDouble(run.maxSingleJointMovement)}")
        JargonAwareText("Normalized joint travel RMS: ${formatDouble(run.normalizedJointTravelRms)}")
        Text("Final minimum limit margin: ${formatPercent(run.finalMinNormalizedLimitMargin)}")
        JargonAwareText("Backtracking retries: ${run.backtrackingRetryCount}")
        Text("Solve duration: ${formatDouble(run.solveDurationNanos / 1_000.0)} µs")
        Text("Near-limit joints: ${run.nearLimitJointNames}")
        Text("Joint-limit pressure: ${formatPercent(run.jointLimitPressureRatio)}")
        Text("Iterations: ${run.iterations}")
    }
}

@Composable
fun DiagnosticReadableReportCard(
    report: Layer1DiagnosticReport
) {
    val clipboardManager =
        LocalClipboardManager.current

    val reportText =
        report.toHumanReadableText()

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF111111)
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Button(
                onClick = {
                    clipboardManager.setText(
                        AnnotatedString(reportText)
                    )
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Copy Full Diagnostics")
            }

            Text(
                text = reportText,
                color = Color.White,
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
