package com.robotkinematicslab.mobile.diagnostics.benchmark.validation

import com.robotkinematicslab.mobile.diagnostics.benchmark.config.DiagnosticJointMode

data class DiagnosticTopologyAuditRecord(
    val seed: Int,
    val linkCount: Int,
    val jointMode: DiagnosticJointMode,
    val acceptedForBenchmark: Boolean,
    val safeModeReliabilityClaimAllowed: Boolean,
    val summaryLabel: String,
    val issueCount: Int,
    val errorCount: Int,
    val warningCount: Int,
    val infoCount: Int,
    val estimatedReachMeters: Double,
    val revoluteCount: Int,
    val prismaticCount: Int,
    val prismaticRatio: Double,
    val issues: List<DiagnosticTopologyAuditIssue>
) {
    companion object {
        fun fromAuditResult(
            seed: Int,
            linkCount: Int,
            jointMode: DiagnosticJointMode,
            auditResult: DiagnosticTopologyAuditResult
        ): DiagnosticTopologyAuditRecord {
            val issues =
                auditResult.issues

            return DiagnosticTopologyAuditRecord(
                seed = seed,
                linkCount = linkCount,
                jointMode = jointMode,
                acceptedForBenchmark = auditResult.acceptedForBenchmark,
                safeModeReliabilityClaimAllowed = auditResult.safeModeReliabilityClaimAllowed,
                summaryLabel = auditResult.summaryLabel,
                issueCount = issues.size,
                errorCount =
                    issues.count {
                        it.severity == DiagnosticTopologyAuditSeverity.ERROR
                    },
                warningCount =
                    issues.count {
                        it.severity == DiagnosticTopologyAuditSeverity.WARNING
                    },
                infoCount =
                    issues.count {
                        it.severity == DiagnosticTopologyAuditSeverity.INFO
                    },
                estimatedReachMeters = auditResult.metrics.estimatedReachMeters,
                revoluteCount = auditResult.metrics.revoluteCount,
                prismaticCount = auditResult.metrics.prismaticCount,
                prismaticRatio = auditResult.metrics.prismaticRatio,
                issues = issues
            )
        }
    }
}
