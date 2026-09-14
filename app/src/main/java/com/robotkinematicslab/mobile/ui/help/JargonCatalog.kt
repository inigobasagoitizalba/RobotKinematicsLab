package com.robotkinematicslab.mobile.ui.help

import java.util.Locale

/**
 * One searchable terminology registry for inline help and the complete glossary.
 *
 * Aliases are deliberately kept here instead of being scattered through screens. A displayed
 * abbreviation therefore opens the same explanation as its long form.
 */
data class JargonDefinition(
    val id: String,
    val term: String,
    val aliases: Set<String>,
    val plainMeaning: String,
    val whyItMatters: String
) {
    val searchableForms: List<String> =
        (aliases + term)
            .map(String::trim)
            .filter(String::isNotBlank)
            .distinctBy { it.lowercase(Locale.ROOT) }
            .sortedByDescending(String::length)
}

data class JargonMatch(
    val start: Int,
    val endExclusive: Int,
    val displayedText: String,
    val definition: JargonDefinition
)

object JargonCatalog {
    private val aliasesByTerm: Map<String, Set<String>> =
        mapOf(
            "DH parameters" to setOf("DH", "Denavit-Hartenberg", "DH parameter"),
            "Forward kinematics (FK)" to setOf("FK", "forward kinematics"),
            "Inverse kinematics (IK)" to setOf("IK", "inverse kinematics"),
            "Residual / final error" to setOf("residual", "final error", "Cartesian residual"),
            "Seed" to setOf("random seed", "seeds"),
            "Topology" to setOf("robot topology", "topologies"),
            "Reachable target" to setOf("reachable", "reachability"),
            "Point cloud" to setOf("point clouds"),
            "Surface shell" to setOf("workspace shell", "surface mesh"),
            "Dead space" to setOf("dead zone", "dead zones"),
            "Workspace convergence" to setOf("workspace convergence"),
            "Dataset manifest" to setOf("manifest"),
            "Continuous dataset growth" to setOf("continuous generation", "background generation"),
            "Atomic batch" to setOf("atomic commit", "committed batch"),
            "Class support" to setOf("class support", "class count"),
            "Macro-F1" to setOf("macro F1", "macro f1"),
            "Held-out test set" to setOf("held-out", "test split"),
            "Data leakage" to setOf("leakage"),
            "Feature profile" to setOf("feature set", "feature family"),
            "Feature count" to setOf("feature counts", "number of features"),
            "Deterministic recovery" to setOf("fallback solver", "hybrid recovery"),
            "Numerical drift" to setOf("rotation drift", "floating-point drift"),
            "Protected / unguarded branch" to setOf("protected branch", "unguarded branch"),
            "Fault injection" to setOf("injected fault", "fault-injected"),
            "Telemetry" to setOf("runtime telemetry"),
            "Foreground notification" to setOf("foreground service"),
            "Committed checkpoint" to setOf("checkpoint", "checkpoints"),
            "Provenance" to setOf("data provenance", "experiment provenance"),
            "Worker" to setOf("workers", "thread worker"),
            "App heap" to setOf("heap", "managed heap"),
            "Calibration" to setOf("calibration gap", "reliability curve"),
            "Integrated Gradients" to setOf("integrated gradients", "IG attribution"),
            "Feature ablation" to setOf("ablation", "ablation study"),
            "One-micron verification" to setOf("one micron", "1 micron", "one-micron", "1 µm", "1 μm"),
            "Jacobian" to setOf("Jacobian matrix"),
            "Singularity" to setOf("singularities", "near-singular"),
            "Condition number" to setOf("conditioning", "condition number"),
            "Non-finite value" to setOf("NaN", "infinity", "non-finite", "not finite"),
            "End effector" to setOf("end-effector", "tool centre point", "TCP"),
            "Revolute joint" to setOf("revolute", "rotary joint"),
            "Prismatic joint" to setOf("prismatic", "linear joint"),
            "Classification precision" to setOf("precision score", "positive predictive value"),
            "Recall" to setOf("recall score"),
            "Sensitivity analysis" to
                setOf(
                    "seed sensitivity",
                    "damping sensitivity",
                    "max-step sensitivity",
                    "numerical sensitivity",
                    "model sensitivity",
                    "perturbation sensitivity"
                ),
            "Numerical precision" to setOf("IK precision", "solver precision", "precision solver preset"),
            "Confusion matrix" to setOf("confusion matrices"),
            "Confidence interval" to setOf("confidence intervals", "CI"),
            "Standard deviation" to setOf("standard deviation", "std dev"),
            "Percentile" to setOf("percentiles"),
            "Outlier" to setOf("outliers"),
            "Latency" to setOf("latency", "response time"),
            "Throughput" to setOf("throughput", "calculations per second"),
            "Epoch" to setOf("epochs"),
            "Overfitting" to setOf("overfit", "overfitted"),
            "Mean squared error (MSE)" to setOf("MSE", "mean squared error"),
            "Area under the curve (AUC)" to setOf("AUC", "area under the curve"),
            "False positive" to setOf("false positives", "false accept", "false acceptance"),
            "False negative" to setOf("false negatives", "false reject", "false rejection"),
            "Damped least squares (DLS)" to setOf("DLS", "damped least squares", "damping"),
            "Singular value decomposition (SVD)" to setOf("SVD", "singular value decomposition"),
            "Manipulability" to setOf("manipulability index"),
            "Out-of-distribution (OOD)" to setOf("OOD", "out of distribution", "out-of-distribution"),
            "Root mean square (RMS)" to setOf("RMS", "root mean square"),
            "Logarithmic scale" to setOf("log scale", "log10", "logarithmic scale"),
            "Tail risk" to setOf("P95", "P99", "CVaR", "tail percentile", "tail risk"),
            "Expected calibration error (ECE)" to setOf("ECE", "expected calibration error"),
            "Brier score" to setOf("Brier score"),
            "Risk-coverage curve" to setOf("risk-coverage", "risk coverage"),
            "Conformal prediction" to setOf("conformal set", "conformal prediction"),
            "Multilayer perceptron (MLP)" to setOf("MLP", "multilayer perceptron"),
            "ONNX model" to setOf("ONNX"),
            "Logit" to setOf("logits"),
            "Garbage collection (GC)" to setOf("GC", "garbage collection"),
            "System on chip (SoC)" to setOf("SoC", "system on chip", "system-on-chip"),
            "SHA-256" to setOf("SHA256", "SHA-256", "checksum"),
            "Voxel" to setOf("voxels", "voxel grid"),
            "Area under the risk-coverage curve (AURC)" to setOf("AURC", "area under the risk-coverage curve"),
            "Wilson interval" to setOf("Wilson score interval"),
            "Marginal coverage" to setOf("marginal coverage"),
            "Backtracking" to setOf("backtracking step", "line-search backtracking"),
            "Iteration saturation" to setOf("iteration saturated", "iteration limit reached"),
            "Native heap" to setOf("native memory heap"),
            "Thermal headroom" to setOf("thermal margin"),
            "Joint limit" to setOf("joint limits", "angular limit", "angular limits", "travel limit", "travel limits"),
            "Robot pose" to setOf("pose", "poses"),
            "Dataset" to setOf("datasets", "data set", "data sets"),
            "CSV file" to setOf("CSV", "CSV files"),
            "Accepted / rejected case" to setOf("accepted case", "accepted cases", "rejected case", "rejected cases", "strict accepted"),
            "Solver iteration" to setOf("solver iterations", "maximum iterations", "max iterations"),
            "Maximum step" to setOf("max step", "maximum normalized step"),
            "Normalisation" to setOf("normalization", "normalised", "normalized"),
            "Scientific data split" to setOf("scientific split", "training split", "validation split", "untouched test partition"),
            "Comparison arm" to setOf("comparison arms", "model arm", "model arms", "same-seed arm"),
            "Model candidate" to setOf("model candidates", "candidate model", "candidate models"),
            "Mini-batch" to setOf("mini batch", "batch size"),
            "Learning rate" to setOf("learning rates"),
            "L2 regularisation" to setOf("L2 regularization", "L2"),
            "Hidden unit" to setOf("hidden units"),
            "Early stopping" to setOf("early-stopping", "early stopping patience"),
            "Balanced accuracy" to setOf("balanced-accuracy"),
            "Cross-entropy / log loss" to setOf("cross entropy", "cross-entropy", "log loss"),
            "Inference" to setOf("model inference", "inference cost", "inference rebuild"),
            "Attribution" to setOf("attributions", "signed attribution"),
            "SHAP value" to setOf("SHAP", "SHAP values", "SHAP interaction"),
            "Completeness error" to setOf("completeness gap"),
            "Decision margin" to setOf("classification margin"),
            "Deterministic reference solver" to setOf("oracle", "reference solver", "deterministic solver"),
            "Perturbation" to setOf("perturbations", "perturbed"),
            "Selective risk" to setOf("selective error"),
            "Retained coverage" to setOf("empirical coverage"),
            "Evidence artifact" to setOf("evidence artifacts", "artifact", "artifacts", "evidence pack"),
            "Quarantine" to setOf("quarantined"),
            "Logical CPU core" to setOf("logical cores", "CPU cores", "processor cores"),
            "Working-memory budget" to setOf("working memory", "memory budget"),
            "Thermal throttling" to setOf("throttling", "thermal throttle"),
            "Processing thread" to setOf("processing threads", "CPU thread", "CPU threads"),
            "Sample" to setOf("samples", "sample row", "sample rows"),
            "Append operation" to setOf("append", "appending", "appended rows"),
            "Scientific contract" to setOf("dataset contract", "inference contract", "feature contract", "schema contract"),
            "Input feature" to setOf("input features", "feature variable", "feature variables"),
            "Validation set" to setOf("validation data", "validation labels", "validation examples")
        )

    val definitions: List<JargonDefinition> =
        ResearchGlossaryCatalog.entries.map { entry -> entry.toDefinition() }

    private val indexedForms: List<Pair<String, JargonDefinition>> =
        definitions
            .flatMap { definition ->
                definition.searchableForms.map { form -> form to definition }
            }
            .sortedWith(
                compareByDescending<Pair<String, JargonDefinition>> { it.first.length }
                    .thenBy { it.first.lowercase(Locale.ROOT) }
            )

    /*
     * Compile the terminology matcher once. The first version built one Regex per alias for every
     * Text composable and every recomposition. On dense diagnostic screens that multiplied into
     * tens of thousands of Regex compilations and could stall Android's main thread while a
     * background calculation was also active. Ordering the alternatives longest-first preserves
     * the original "most specific term wins" contract in a single linear scan.
     */
    private val definitionByNormalizedForm: Map<String, JargonDefinition> =
        buildMap {
            indexedForms.forEach { (form, definition) ->
                putIfAbsent(form.lowercase(Locale.ROOT), definition)
            }
        }

    private val terminologyRegex: Regex? =
        indexedForms
            .map(Pair<String, JargonDefinition>::first)
            .takeIf(List<String>::isNotEmpty)
            ?.joinToString(separator = "|") { form -> Regex.escape(form) }
            ?.let { alternatives ->
                Regex(
                    pattern = "(?<![\\p{L}\\p{N}])(?:$alternatives)(?![\\p{L}\\p{N}])",
                    option = RegexOption.IGNORE_CASE
                )
            }

    /**
     * Finds non-overlapping whole terms. Longest aliases win, so "calibration gap" is not reduced
     * to "calibration" and "IK" is never matched inside an ordinary word.
     */
    fun findMatches(text: String): List<JargonMatch> {
        if (text.isBlank()) return emptyList()
        val matcher = terminologyRegex ?: return emptyList()
        return matcher.findAll(text).mapNotNull { match ->
            definitionByNormalizedForm[match.value.lowercase(Locale.ROOT)]?.let { definition ->
                JargonMatch(
                    start = match.range.first,
                    endExclusive = match.range.last + 1,
                    displayedText = match.value,
                    definition = definition
                )
            }
        }.toList()
    }

    private fun ResearchGlossaryEntry.toDefinition(): JargonDefinition =
        JargonDefinition(
            id = slug(term),
            term = term,
            aliases = aliasesByTerm[term].orEmpty(),
            plainMeaning = plainMeaning,
            whyItMatters = whyItMatters
        )

    private fun slug(value: String): String =
        value
            .lowercase(Locale.ROOT)
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
}
