package com.robotkinematicslab.mobile.diagnostics.benchmark.config

data class DiagnosticPerformanceConfig(
    val storeFullRunHistory: Boolean = true,
    val storePerCaseDetails: Boolean = true,
    val storeTransitionDetails: Boolean = true,
    val storeExtremeRunDetails: Boolean = true,

    val exportRunHistoryToCsv: Boolean = false,
    val runHistoryCsvPath: String? = null,

    val exportCaseResultsToCsv: Boolean = false,
    val caseResultsCsvPath: String? = null
)
