package com.robotkinematicslab.mobile.ui.charts.guidance

/** The visual grammar used by a chart determines how it should be interpreted. */
enum class ChartGuideKind {
    LINE,
    SCATTER,
    HEAT_MAP,
    HISTOGRAM,
    BOX_PLOT,
    BAR,
    STACKED_SHARE,
    GAUGE,
    TIMELINE,
    SPATIAL,
    SUMMARY
}

enum class ChartReadingDirection(
    val badgeLabel: String,
    val explanation: String
) {
    HIGHER_TENDS_BETTER(
        badgeLabel = "↑ HIGHER TENDS BETTER",
        explanation = "Higher values are normally preferable, provided the comparison uses the same population, tolerance and evaluation split."
    ),
    LOWER_TENDS_BETTER(
        badgeLabel = "↓ LOWER TENDS BETTER",
        explanation = "Lower values are normally preferable, provided reliability, coverage or success has not been sacrificed to obtain them."
    ),
    TARGET_DEPENDENT(
        badgeLabel = "◎ TARGET DEPENDENT",
        explanation = "There is no universal best extreme. Interpret the value against the stated target, tolerance or scientific contract."
    ),
    PATTERN_NOT_RANK(
        badgeLabel = "◇ READ THE PATTERN",
        explanation = "This view is primarily for structure, clusters, transitions or imbalance; a larger number is not automatically better."
    ),
    TRADE_OFF(
        badgeLabel = "⇄ CHECK THE TRADE-OFF",
        explanation = "Several desirable outcomes compete here. Judge improvement only after checking the companion cost and reliability metrics."
    )
}

data class ChartGuide(
    val kind: ChartGuideKind,
    val typeLabel: String,
    val whatItShows: String,
    val unitOrScale: String?,
    val howToRead: String,
    val researchQuestion: String,
    val whyItMatters: String,
    val direction: ChartReadingDirection,
    val caution: String,
    val supportsDataInspector: Boolean
)

/**
 * One deterministic source of chart-reading copy for the whole application. It deliberately uses
 * cautious wording: UI guidance must not turn a descriptive plot into a causal or statistical claim.
 */
object ChartGuideFactory {

    fun forChart(
        kind: ChartGuideKind,
        title: String,
        subtitle: String? = null,
        xAxisLabel: String? = null,
        yAxisLabel: String? = null,
        supportsDataInspector: Boolean = false,
        directionOverride: ChartReadingDirection? = null
    ): ChartGuide {
        val context = listOfNotNull(title, subtitle, xAxisLabel, yAxisLabel).joinToString(" ")
        val axes = axisPhrase(xAxisLabel, yAxisLabel)
        val whatItShows =
            subtitle?.takeIf(String::isNotBlank)
                ?: defaultPurpose(kind = kind, title = title)
        val researchQuestion = researchQuestion(kind, axes)

        val specific = specificInterpretation(context.lowercase())
        return ChartGuide(
            kind = kind,
            typeLabel = typeLabel(kind),
            whatItShows = whatItShows,
            unitOrScale = inferUnitOrScale(if(kind == ChartGuideKind.BAR) xAxisLabel else yAxisLabel),
            howToRead = specific?.first?.let { "$it $axes" } ?: howToRead(kind, axes),
            researchQuestion = researchQuestion,
            whyItMatters = whyItMatters(kind),
            direction = directionOverride ?: inferDirection(context),
            caution = specific?.second ?: caution(kind),
            supportsDataInspector = supportsDataInspector
        )
    }

    /**
     * Direct chart cards predate the professional chart wrappers. Recognise only chart-like titles;
     * ordinary forms and settings cards remain visually unchanged.
     */
    fun inferForCard(title: String, subtitle: String?): ChartGuide? {
        val text = "$title ${subtitle.orEmpty()}".lowercase()
        val kind =
            when {
                text.containsAny("heat map", "heatmap", "matrix") -> ChartGuideKind.HEAT_MAP
                text.containsAny("scatter", "correlation") -> ChartGuideKind.SCATTER
                text.containsAny("box plot", "boxplot", "violin", "quartile") -> ChartGuideKind.BOX_PLOT
                text.containsAny("histogram", "distribution", "percentile", "cdf") -> ChartGuideKind.HISTOGRAM
                text.containsAny("timeline", "history", "learning curve", "trend") -> ChartGuideKind.TIMELINE
                text.containsAny("workspace", "3d", "spatial") -> ChartGuideKind.SPATIAL
                text.containsAny("pareto", "bar chart", " by topology", " by link", " by seed", " by expected class") ->
                    ChartGuideKind.BAR
                text.containsAny("donut", "proportion", "composition") -> ChartGuideKind.STACKED_SHARE
                text.containsAny("gauge") -> ChartGuideKind.GAUGE
                text.containsAny(
                    "chart",
                    "graph",
                    "summary",
                    "statistics",
                    "evidence",
                    "audit",
                    "verdict",
                    "residual"
                ) -> ChartGuideKind.SUMMARY
                else -> null
            }

        return kind?.let {
            forChart(
                kind = it,
                title = title,
                subtitle = subtitle
            )
        }
    }

    private fun specificInterpretation(context: String): Pair<String, String>? = when {
        "validation" in context && ("f1" in context || "epoch" in context) ->
            "Validation macro-F1 weights each class equally. Progress and a plateau suggest learning and convergence; oscillation suggests instability. Compare with training and independent-test evidence before attributing a decline to overfitting." to
            "Validation guides model selection. A peak or best epoch is not an independent-test result; consult the stored checkpoint and early-stopping criterion."
        "calibration" in context || "reliability" in context ->
            "Compare predicted confidence with the observed fraction correct in each supported bin. Near the ideal y=x line, an 80% confidence group is correct about 80% of the time. Check bin support and weighting." to
            "Aggregated calibration is not certification of a single prediction or physical IK safety. Sparse and absent bins limit conclusions."
        "training time" in context || "latency" in context ->
            "Compare the stated measured time using identical hardware, batching and measurement boundaries. Check sample size and variation before ranking close timings." to
            "Training time and inference latency measure different work. Throughput cannot be inferred from reciprocal latency without knowing concurrency and batching."
        "workspace" in context ->
            "Each observation belongs to the stated robot frame and sampling process. Examine coverage and cavities in relation to sample density and the physical limits." to
            "An unsampled location is not proof of unreachability. Sampled coverage is not continuous workspace certification."
        "confusion" in context || "recall" in context ->
            "Read the declared actual/predicted axes. Recall divides correct predictions for a class by all actual cases of that class. Inspect support and off-diagonal errors, not only the overall score." to
            "A class with no actual cases has no supported recall. Classifier outcomes do not replace deterministic physical verification."
        else -> null
    }

    private fun typeLabel(kind: ChartGuideKind): String =
        when (kind) {
            ChartGuideKind.LINE -> "Line chart"
            ChartGuideKind.SCATTER -> "Scatter plot"
            ChartGuideKind.HEAT_MAP -> "Heat map / matrix"
            ChartGuideKind.HISTOGRAM -> "Histogram / distribution"
            ChartGuideKind.BOX_PLOT -> "Box-and-whisker plot"
            ChartGuideKind.BAR -> "Comparative bar chart"
            ChartGuideKind.STACKED_SHARE -> "Composition chart"
            ChartGuideKind.GAUGE -> "Gauge"
            ChartGuideKind.TIMELINE -> "Ordered timeline"
            ChartGuideKind.SPATIAL -> "Spatial / 3D view"
            ChartGuideKind.SUMMARY -> "Scientific summary"
        }

    private fun defaultPurpose(kind: ChartGuideKind, title: String): String =
        when (kind) {
            ChartGuideKind.LINE -> "$title follows how one metric changes across an ordered variable."
            ChartGuideKind.SCATTER -> "$title places paired observations together to expose association, clusters and outliers."
            ChartGuideKind.HEAT_MAP -> "$title compares combinations of row and column categories through colour."
            ChartGuideKind.HISTOGRAM -> "$title shows the shape and concentration of observed values."
            ChartGuideKind.BOX_PLOT -> "$title compares medians, spread and extreme observations between groups."
            ChartGuideKind.BAR -> "$title compares the magnitude of named categories."
            ChartGuideKind.STACKED_SHARE -> "$title shows how a complete population is divided between categories."
            ChartGuideKind.GAUGE -> "$title places one value within a bounded operating range."
            ChartGuideKind.TIMELINE -> "$title preserves order so changes and state sequences remain visible."
            ChartGuideKind.SPATIAL -> "$title maps results back to physical or simulated position."
            ChartGuideKind.SUMMARY -> "$title condenses the evidence required to interpret the surrounding analysis."
        }

    private fun howToRead(kind: ChartGuideKind, axes: String): String =
        when (kind) {
            ChartGuideKind.LINE ->
                "Read from left to right. Follow the slope, turning points and distance between series. $axes"
            ChartGuideKind.SCATTER ->
                "Each dot is one observation. Look for direction, tightness, clusters and isolated points rather than one dot alone. $axes"
            ChartGuideKind.HEAT_MAP ->
                "Cross a row with a column, then translate the cell colour using this chart's legend. Missing cells mean no displayed observation, not zero. $axes"
            ChartGuideKind.HISTOGRAM ->
                "Bar height is the number or share of samples in a numeric interval. Read the centre, tails, skew and unusual secondary peaks. $axes"
            ChartGuideKind.BOX_PLOT ->
                "The centre line is the median, the box spans the middle 50%, and the whiskers show the displayed outer range. Compare both centre and spread. $axes"
            ChartGuideKind.BAR ->
                "Compare bar length between named categories, then verify whether the values are counts, rates or normalized scores. $axes"
            ChartGuideKind.STACKED_SHARE ->
                "Read every coloured segment as part of one total. Use the legend values for close comparisons because area and angle are approximate."
            ChartGuideKind.GAUGE ->
                "Read the labelled value first, then its position in the bounded scale. The colour is a status cue, not a substitute for the numeric threshold."
            ChartGuideKind.TIMELINE ->
                "Read from the first event to the last. Consecutive colour blocks reveal persistence, transitions and bursts. $axes"
            ChartGuideKind.SPATIAL ->
                "Rotate and zoom to inspect shape, density and cavities, while preserving the axis frame and colour legend as the scientific reference. $axes"
            ChartGuideKind.SUMMARY ->
                "Read the stated population and units first, then compare the displayed values against the experiment's tolerance and baseline."
        }

    private fun researchQuestion(kind: ChartGuideKind, axes: String): String =
        when (kind) {
            ChartGuideKind.LINE -> "Does the outcome improve, degrade, plateau or become unstable as the ordered variable changes?"
            ChartGuideKind.SCATTER -> "Do the variables move together, form distinct regimes or expose influential outliers?"
            ChartGuideKind.HEAT_MAP -> "Which row/column combinations concentrate good, poor or missing outcomes?"
            ChartGuideKind.HISTOGRAM -> "Where do most observations lie, and are long tails or multiple regimes hiding behind the average?"
            ChartGuideKind.BOX_PLOT -> "Which groups differ in typical value, variability or worst-case behaviour?"
            ChartGuideKind.BAR -> "Which categories dominate, and does that ranking remain meaningful after accounting for sample count?"
            ChartGuideKind.STACKED_SHARE -> "How is the same total divided, and is one outcome disproportionately common?"
            ChartGuideKind.GAUGE -> "Is the current value inside the declared acceptable operating region?"
            ChartGuideKind.TIMELINE -> "When do state changes occur, and are failures isolated or clustered in sequence?"
            ChartGuideKind.SPATIAL -> "Where in the workspace do outcomes change, and are there reachable gaps, boundaries or risk zones?"
            ChartGuideKind.SUMMARY -> "What claim can these values support, and what companion metric is needed before accepting it?"
        }

    private fun whyItMatters(kind: ChartGuideKind): String =
        when (kind) {
            ChartGuideKind.LINE -> "It reveals change, instability and diminishing returns that one final average can hide."
            ChartGuideKind.SCATTER -> "It exposes relationships, subgroups and exceptional cases that separate averages conceal."
            ChartGuideKind.HEAT_MAP -> "It localises combinations where behaviour improves, degrades or lacks enough evidence."
            ChartGuideKind.HISTOGRAM -> "It shows the complete spread and tails, where reliability failures often remain hidden."
            ChartGuideKind.BOX_PLOT -> "It compares both typical behaviour and variability instead of rewarding the mean alone."
            ChartGuideKind.BAR -> "It makes differences between named groups visible and directs attention to weak subgroups."
            ChartGuideKind.STACKED_SHARE -> "It shows whether the total is dominated by a desirable, undesirable or missing outcome."
            ChartGuideKind.GAUGE -> "It places the current result against a declared operating contract or threshold."
            ChartGuideKind.TIMELINE -> "It preserves order, making clustered failures and phase-dependent behaviour diagnosable."
            ChartGuideKind.SPATIAL -> "It connects numerical outcomes to physical regions of the robot workspace."
            ChartGuideKind.SUMMARY -> "It condenses the claim while pointing to the detailed evidence needed to verify it."
        }

    private fun caution(kind: ChartGuideKind): String =
        when (kind) {
            ChartGuideKind.LINE -> "A trend is descriptive, not proof of causation. Check seeds, uncertainty and identical axis scales before comparing runs."
            ChartGuideKind.SCATTER -> "Correlation is not causation. Overlapping dots and unequal sampling can hide density or exaggerate apparent structure."
            ChartGuideKind.HEAT_MAP -> "Colour depends on the legend range. Compare cell counts as well as colour; sparse averages can look more certain than they are."
            ChartGuideKind.HISTOGRAM -> "The chosen bin width changes the apparent shape. Confirm sample size and inspect percentiles before claiming a distributional difference."
            ChartGuideKind.BOX_PLOT -> "A box plot compresses the distribution. Groups with different sample counts or multimodal values require the raw points or histogram too."
            ChartGuideKind.BAR -> "Raw counts are not comparable when categories contain different sample totals. Prefer rates or show both numerator and denominator."
            ChartGuideKind.STACKED_SHARE -> "Small segment differences are hard to judge visually. Verify exact labels and ensure all charts use the same denominator."
            ChartGuideKind.GAUGE -> "A gauge summarizes one thresholded value and hides its distribution, uncertainty and history."
            ChartGuideKind.TIMELINE -> "Order can reveal clustering but not its cause. Compare the same seed protocol and case ordering between sessions."
            ChartGuideKind.SPATIAL -> "Perspective, occlusion and display sampling can mislead. Use numeric coordinates and exported rows for quantitative claims."
            ChartGuideKind.SUMMARY -> "Summary values can hide subgroup failures and uncertainty. Open the detailed plot or raw rows before making a reliability claim."
        }

    private fun inferDirection(text: String): ChartReadingDirection {
        val normalized = text.lowercase()
        // A CDF is interpreted by its horizontal displacement and curve shape, not by treating
        // its cumulative-probability Y value as universally high or low. A failure Pareto is a
        // ranked composition of causes, not an optimisation-front trade-off.
        if (normalized.containsAny("cdf", "cumulative distribution", "pareto failure", "failure pareto")) {
            return ChartReadingDirection.PATTERN_NOT_RANK
        }
        if (normalized.containsAny(
                "pareto",
                "trade-off",
                "tradeoff",
                "cost / quality",
                "cost/quality",
                "risk-coverage",
                "risk coverage"
            )
        ) {
            return ChartReadingDirection.TRADE_OFF
        }
        if (normalized.containsAny(
                "reliability curve",
                "confidence versus accuracy",
                "confidence vs accuracy",
                "expected class",
                "target range"
            )
        ) {
            return ChartReadingDirection.TARGET_DEPENDENT
        }
        if (normalized.containsAny(
                "unreachable accepted",
                "unreachable acceptance",
                "false positive",
                "false accept",
                "false negative",
                "false reject",
                "false rejection",
                "accuracy drop",
                "loss increase",
                "truth-loss increase",
                "calibration gap",
                "seed acceptance spread",
                "seed spread",
                "runtime risk",
                "numerical safety event",
                "mean squared error",
                " mse",
                "mse ",
                "ece",
                "brier",
                "out-of-distribution rate",
                "ood rate"
            )
        ) {
            return ChartReadingDirection.LOWER_TENDS_BETTER
        }
        if (normalized.containsAny("target", "tolerance", "delta")) {
            return ChartReadingDirection.TARGET_DEPENDENT
        }
        if (normalized.containsAny(
                "correlation",
                "scatter",
                "composition",
                "status by",
                "failure code",
                "accepted vs rejected",
                "accepted / rejected"
            )
        ) {
            return ChartReadingDirection.PATTERN_NOT_RANK
        }
        if (normalized.containsAny(
                "reduction",
                "saved",
                "throughput",
                "calculations per second",
                "oracle pass",
                "close-or-better",
                "certified result",
                "unreachable rejection"
            )
        ) {
            return ChartReadingDirection.HIGHER_TENDS_BETTER
        }

        val lower =
            normalized.containsAny(
                "error",
                "residual",
                "failure",
                "loss",
                "latency",
                "duration",
                "elapsed",
                "iteration",
                "pressure",
                "violation",
                "contamination",
                "distance",
                "drift",
                "variance",
                "condition number",
                "memory cost"
            )
        val higher =
            normalized.containsAny(
                "success",
                "acceptance",
                "accepted",
                "accuracy",
                "macro-f1",
                "macro f1",
                "reliability",
                "coverage",
                "margin",
                "progress",
                "convergence rate"
            )

        return when {
            lower && higher -> ChartReadingDirection.TRADE_OFF
            lower -> ChartReadingDirection.LOWER_TENDS_BETTER
            higher -> ChartReadingDirection.HIGHER_TENDS_BETTER
            else -> ChartReadingDirection.PATTERN_NOT_RANK
        }
    }

    private fun inferUnitOrScale(yAxisLabel: String?): String? {
        val axis = yAxisLabel?.trim()?.takeIf(String::isNotBlank) ?: return null
        val parenthesized = Regex("\\(([^)]+)\\)").findAll(axis).lastOrNull()?.groupValues?.getOrNull(1)
        return parenthesized?.takeIf(String::isNotBlank) ?: axis
    }

    private fun axisPhrase(xAxisLabel: String?, yAxisLabel: String?): String {
        val x = xAxisLabel?.takeIf(String::isNotBlank)
        val y = yAxisLabel?.takeIf(String::isNotBlank)
        return when {
            x != null && y != null -> "X represents $x; Y represents $y."
            x != null -> "The displayed axis represents $x."
            y != null -> "The displayed value represents $y."
            else -> ""
        }
    }

    private fun String.containsAny(vararg needles: String): Boolean =
        needles.any { needle -> contains(needle) }
}
