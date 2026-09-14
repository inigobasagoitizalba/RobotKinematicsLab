package com.robotkinematicslab.mobile.diagnostics.benchmark.report

import com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticProgressClass
import com.robotkinematicslab.mobile.diagnostics.benchmark.result.DiagnosticExpectedClass

data class DiagnosticExtremeRunSummary(
    val label: String,
    val runIndex: Int,
    val caseId: String,
    val status: String,
    val detailCode: String,
    val expectedClass: DiagnosticExpectedClass,
    val finalError: Double,
    val initialError: Double,
    val improvementRatio: Double,
    val iterations: Int,
    val progressClass: DiagnosticProgressClass,
    val transitionFromCaseId: String?,
    val transitionToCaseId: String
)
