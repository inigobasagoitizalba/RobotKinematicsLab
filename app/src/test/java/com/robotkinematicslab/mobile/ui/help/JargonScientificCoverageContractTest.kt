package com.robotkinematicslab.mobile.ui.help

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Protects the shared glossary as a scientific UI contract, not merely a list of strings. */
class JargonScientificCoverageContractTest {

    @Test
    fun glossaryCoversTheCriticalKinematicsNumericsMlAndRuntimeVocabulary() {
        val terms = JargonCatalog.definitions.map(JargonDefinition::term).toSet()
        val requiredByDomain =
            mapOf(
                "kinematics" to
                    setOf(
                        "DH parameters",
                        "Forward kinematics (FK)",
                        "Inverse kinematics (IK)",
                        "Jacobian",
                        "Singularity",
                        "End effector",
                        "Revolute joint",
                        "Prismatic joint"
                    ),
                "numerical safety" to
                    setOf(
                        "Tolerance",
                        "Residual / final error",
                        "Condition number",
                        "Non-finite value",
                        "Numerical drift",
                        "Fault injection"
                    ),
                "machine learning" to
                    setOf(
                        "Macro-F1",
                        "Classification precision",
                        "Recall",
                        "Sensitivity analysis",
                        "Numerical precision",
                        "Feature count",
                        "Confusion matrix",
                        "Held-out test set",
                        "Data leakage",
                        "Overfitting",
                        "Mean squared error (MSE)",
                        "Area under the curve (AUC)"
                    ),
                "evidence and runtime" to
                    setOf(
                        "Confidence interval",
                        "Standard deviation",
                        "Percentile",
                        "Provenance",
                        "Telemetry",
                        "Latency",
                        "Throughput",
                        "App heap"
                    ),
                "novice decision vocabulary" to
                    setOf(
                        "Joint limit",
                        "Robot pose",
                        "Dataset",
                        "CSV file",
                        "Accepted / rejected case",
                        "Solver iteration",
                        "Maximum step",
                        "Scientific data split",
                        "Comparison arm",
                        "Model candidate",
                        "Mini-batch",
                        "Learning rate",
                        "Early stopping",
                        "Balanced accuracy",
                        "Cross-entropy / log loss",
                        "Inference",
                        "Attribution",
                        "Deterministic reference solver",
                        "Perturbation",
                        "Selective risk",
                        "Retained coverage",
                        "Evidence artifact",
                        "Quarantine",
                        "Logical CPU core",
                        "Working-memory budget",
                        "Thermal throttling",
                        "Processing thread",
                        "Sample",
                        "Append operation",
                        "Scientific contract",
                        "Input feature",
                        "Validation set"
                    )
            )

        requiredByDomain.forEach { (domain, required) ->
            assertTrue("Missing $domain terms: ${required - terms}", terms.containsAll(required))
        }
    }

    @Test
    fun everySearchableFormHasOneUnambiguousOwnerAndResolvesBackToIt() {
        val owners =
            JargonCatalog.definitions
                .flatMap { definition ->
                    definition.searchableForms.map { form ->
                        form.lowercase(Locale.ROOT) to definition.id
                    }
                }
                .groupBy(keySelector = Pair<String, String>::first, valueTransform = Pair<String, String>::second)

        val ambiguous = owners.filterValues { ids -> ids.distinct().size > 1 }
        assertTrue("Ambiguous glossary aliases: $ambiguous", ambiguous.isEmpty())

        JargonCatalog.definitions.forEach { definition ->
            definition.searchableForms.forEach { form ->
                val matches = JargonCatalog.findMatches(form)
                assertTrue("No resolver match for '$form'", matches.isNotEmpty())
                assertEquals("Wrong owner for '$form'", definition.id, matches.first().definition.id)
                assertEquals("Wrong source slice for '$form'", form, matches.first().displayedText)
            }
        }
    }

    @Test
    fun resolverPreservesExactSourceRangesAcrossUnicodeAndRepeatedTerms() {
        val text = "At 1 µm, FK checks the end-effector; FK rejects NaN."
        val matches = JargonCatalog.findMatches(text)

        assertEquals(
            listOf("One-micron verification", "Forward kinematics (FK)", "End effector", "Forward kinematics (FK)", "Non-finite value"),
            matches.map { it.definition.term }
        )
        matches.forEach { match ->
            assertEquals(match.displayedText, text.substring(match.start, match.endExclusive))
        }
        assertTrue(matches.zipWithNext().all { (left, right) -> left.endExclusive <= right.start })
    }

    @Test
    fun shortScientificAliasesNeverLeakIntoOrdinaryWords() {
        val ordinaryText =
            "A hiking workerish epochal application recalls a precise skin and a cinnamon recipe."

        assertTrue(JargonCatalog.findMatches(ordinaryText).isEmpty())
    }

    @Test
    fun contextSeparatesClassificationMetricsFromNumericalSensitivityAndInputWidth() {
        val text =
            "Classification precision and recall are reported separately from seed sensitivity, " +
                "IK precision and feature count."

        assertEquals(
            listOf(
                "Classification precision",
                "Recall",
                "Sensitivity analysis",
                "Numerical precision",
                "Feature count"
            ),
            JargonCatalog.findMatches(text).map { match -> match.definition.term }
        )

        val presetNames = JargonCatalog.findMatches("Balanced / Precision / Exploration / Custom")
        assertTrue("A bare preset name must not be explained as classification precision", presetNames.isEmpty())
    }

    @Test
    fun noviceFacingFormsResolveWithoutRequiringAcronymKnowledge() {
        val text =
            "A scientific split compares model arms before inference; rejected cases and CSV files remain evidence artifacts."

        assertEquals(
            listOf(
                "Scientific data split",
                "Comparison arm",
                "Inference",
                "Accepted / rejected case",
                "CSV file",
                "Evidence artifact"
            ),
            JargonCatalog.findMatches(text).map { it.definition.term }
        )
    }
}
