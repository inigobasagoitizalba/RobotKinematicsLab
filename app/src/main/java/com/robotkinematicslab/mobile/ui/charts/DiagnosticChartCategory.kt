package com.robotkinematicslab.mobile.ui.charts

enum class DiagnosticChartCategory(
    val symbol: String,
    val title: String,
    val description: String
) {
    OVERVIEW(
        symbol = "📊",
        title = "Overview",
        description = "Core safety, acceptance, verdict, and dataset trust charts."
    ),

    LINK_COUNT_SCALING(
        symbol = "🔗",
        title = "Link Count Scaling",
        description = "How reliability changes from low-link to high-link robots."
    ),

    SEED_SENSITIVITY(
        symbol = "🌱",
        title = "Seed Sensitivity",
        description = "How stable results are across deterministic random seeds."
    ),

    TOPOLOGY(
        symbol = "🦾",
        title = "Topology / Joint Mode",
        description = "AUTO, revolute-only, prismatic-only, and mixed topology comparison."
    ),

    STATUS_FAILURE_CODES(
        symbol = "⚠️",
        title = "Status & Failure Codes",
        description = "Solver statuses and detail-code distributions."
    ),

    ERROR_DISTRIBUTION(
        symbol = "🎯",
        title = "Error Distribution",
        description = "Final error, initial error, and residual-error distributions."
    ),

    ITERATIONS_CONVERGENCE(
        symbol = "⏱️",
        title = "Iterations & Convergence",
        description = "Iteration usage, saturation, and convergence cost."
    ),

    TRANSITIONS(
        symbol = "🔁",
        title = "Transitions",
        description = "Sequential from-case to to-case transition behavior."
    ),

    JOINT_LIMITS(
        symbol = "🧱",
        title = "Joint Limits",
        description = "Clamp pressure, near-limit joints, and limit-related failures."
    ),

    SEED_DISTANCE(
        symbol = "📍",
        title = "Seed Distance",
        description = "How seed distance affects convergence and error."
    ),

    PROGRESS_CLASSES(
        symbol = "📈",
        title = "Progress Classes",
        description = "Solved, near-solved, close-miss, far-failure, stalled, and worsened runs."
    ),

    PER_CASE(
        symbol = "🧪",
        title = "Per-Case",
        description = "Target-by-target reliability and error behavior."
    ),

    CORRELATIONS(
        symbol = "🔬",
        title = "Correlations",
        description = "Scatter-style relationships between error, iterations, seed distance, and limits."
    ),

    HEAT_MAPS(
        symbol = "🗺️",
        title = "Heat maps",
        description = "Transition, seed, link-count, topology, and failure-code matrix views."
    ),

    SCIENTIFIC_REPORT(
        symbol = "📑",
        title = "Scientific Report",
        description = "Paper-style reliability curves, CDFs, box plots, and confidence summaries."
    ),

    SOLVER_COMPARISON(
        symbol = "⚙️",
        title = "Solver Comparison",
        description = "Current-report baseline and solver-preset comparison readiness."
    ),

    EXPORT_BACKED_CHARTS(
        symbol = "📦",
        title = "Export-ready data & charts",
        description = "Review available CSV/JSON evidence, saved chart images, and report-bundle readiness."
    )
}

enum class DiagnosticChartViewMode(
    val title: String,
    val description: String
) {
    ALL_CHARTS(
        title = "All charts",
        description = "Complete analysis for the selected scientific topic."
    ),
    MATRIX_EXPLORER(
        title = "Matrix explorer",
        description = "Heat-map view filtered to the selected scientific topic."
    )
}

internal val diagnosticAnalysisCategories: List<DiagnosticChartCategory> =
    DiagnosticChartCategory.entries.filterNot {
        it == DiagnosticChartCategory.HEAT_MAPS
    }

internal fun DiagnosticChartCategory.supportsMatrixExplorer(): Boolean {
    return this in
            setOf(
                DiagnosticChartCategory.OVERVIEW,
                DiagnosticChartCategory.LINK_COUNT_SCALING,
                DiagnosticChartCategory.SEED_SENSITIVITY,
                DiagnosticChartCategory.TOPOLOGY,
                DiagnosticChartCategory.STATUS_FAILURE_CODES,
                DiagnosticChartCategory.ERROR_DISTRIBUTION,
                DiagnosticChartCategory.ITERATIONS_CONVERGENCE,
                DiagnosticChartCategory.TRANSITIONS,
                DiagnosticChartCategory.JOINT_LIMITS,
                DiagnosticChartCategory.PROGRESS_CLASSES,
                DiagnosticChartCategory.PER_CASE
            )
}
