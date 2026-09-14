package com.robotkinematicslab.mobile.ml.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class FeatureDefinitionContractTest {
    @Test fun all468CardsMatchEveryEncoderPrefixAndHaveCompleteIndividualEvidence() {
        val cards=FeatureSetCatalog.classificationDefinitions()
        assertEquals(468,cards.size)
        assertEquals((1..468).toList(),cards.map { it.index })
        assertEquals(468,cards.map { it.technicalId }.distinct().size)
        FeatureSetCatalog.entries().forEach { entry ->
            val definitions=FeatureSetCatalog.definitions(entry)
            assertEquals(entry.featureNames,definitions.map { it.technicalId })
            assertEquals((1..entry.featureCount).toList(),definitions.map { it.index })
            assertEquals(64,FeatureSetCatalog.definitionFingerprint(entry).length)
            assertTrue(definitions.all { it.dependencies.isNotEmpty() && it.calculation.isNotBlank() && it.implementationSource.contains(".kt") })
        }
        assertTrue(cards[112].origin.contains("execution"))
        assertTrue(cards[113].limitations.contains("execution-path") || cards[113].limitations.contains("execution path"))
        assertTrue(cards[113].limitations.contains("Early success"))
        assertEquals("joint_10_seed",cards[107].technicalId)
        assertEquals("initial_cartesian_error",cards[108].technicalId)
        assertEquals("seed_end_effector_x",cards[130].technicalId)
        assertEquals("joint_10_active_theta_cos",cards[382].technicalId)
        assertEquals("research_v2_workspace_outer_pressure_squared",cards[383].technicalId)
        assertEquals("research_v2_joint_10_link_leverage_x_limit_pressure",cards[467].technicalId)
    }
    @Test fun integrityRejectsMissingDuplicateReorderedRenamedAndFormulaVersionDrift() {
        val cards=FeatureSetCatalog.classificationDefinitions()
        val names=cards.map { it.technicalId };val digest=FeatureDefinitionIntegrity.fingerprint(cards)
        val invalid=listOf(cards.dropLast(1),cards.toMutableList().apply { this[1]=this[0] },cards.reversed(),
            cards.toMutableList().apply { this[0]=this[0].copy(technicalId="changed") },
            cards.toMutableList().apply { this[0]=this[0].copy(calculation="jointCount / 100") },
            cards.toMutableList().apply { this[0]=this[0].copy(formulaVersion="unreviewed-v2") })
        invalid.forEach { assertThrows(IllegalArgumentException::class.java) { FeatureDefinitionIntegrity.validate(names,it,digest) } }
        assertThrows(IllegalArgumentException::class.java) { FeatureDefinitionIntegrity.validate(listOf("duplicate","duplicate"),cards.take(2)) }
        assertEquals("81089d92854e83baee560def88519ad7eb89b8fb364d1480b592bfd0cfcf42d3",digest)
    }
    @Test fun existingRangeParserUsesOneBasedPositionsAcrossFamilyBoundaries() {
        val cards=FeatureSetCatalog.classificationDefinitions()
        val range=FeatureIndexSelectionParser.parse("108-130",468).getOrThrow().map(cards::get)
        assertEquals(23,range.size);assertEquals("joint_10_seed",range.first().technicalId);assertEquals("workspace_boundary_proximity",range.last().technicalId)
        val subset=FeatureIndexSelectionParser.parse("201-205",468).getOrThrow().map(cards::get)
        assertEquals(listOf("seed_near_limit_fraction_10","seed_near_limit_fraction_20","seed_signed_position_mean","seed_signed_position_rms","seed_signed_position_max_abs"),subset.map { it.technicalId })
        assertEquals(114,FeatureIndexSelectionParser.parse("1-108,130,201-205",468).getOrThrow().size)
        assertTrue(FeatureIndexSelectionParser.parse("469",468).isFailure)
        assertTrue(FeatureIndexSelectionParser.parse("130-108",468).isFailure)
    }
    @Test fun verified361HasItsOwnIndicesAndDoesNotInheritClassifierOnlyStoredContextOrLogFloor() {
        val ik=FeatureSetCatalog.entries().single { it.domain==FeatureSetDomain.VERIFIED_IK && it.featureCount==361 }
        val cards=FeatureSetCatalog.definitions(ik)
        assertEquals("seed_end_effector_x",cards[108].technicalId)
        assertEquals("joint_10_active_theta_cos",cards[360].technicalId)
        assertFalse(cards.any { it.technicalId=="initial_error_available" })
        assertTrue(cards[5].calculation.contains("no1e-12 floor"))
        assertTrue(FeatureSetCatalog.classificationDefinitions()[5].calculation.contains("max("))
        assertEquals(109,FeatureSetCatalog.definition("seed_end_effector_x",FeatureSetDomain.VERIFIED_IK)?.index)
        assertEquals(131,FeatureSetCatalog.definition("seed_end_effector_x")?.index)
        assertNull(FeatureSetCatalog.definition("not_an_encoded_feature"))
    }
    @Test fun sourceFormulaChangesRequireExplicitDictionaryReview() {
        val sources=mapOf(
            "ml/data/ScientificDatasetTrainingReader.kt" to "84d2c44c1b59befb553118428649b647fb2fc3f4798451d8a047cb7de84141e7",
            "ml/data/ExpandedContextFeatureCalculator.kt" to "9cb70f818bb6f87a2146ecd78e4a7735194af3ae7fd41718ef721643efab4c61",
            "ml/data/ResearchContextFeatureCalculator.kt" to "848656d6264483663cc84a9defe55b7f862aa967dad0d54f333f0c410a75c677",
            "ml/ik/OneMicronIkFeatureEncoder.kt" to "5125a725d82974c26d5f7b7f7eba6dd506840f184661fbefe01d7d55aa63aae8",
            "diagnostics/metrics/DiagnosticDerivedMetricsCalculator.kt" to "9604a74d7351a4c1bb1ea2104bb1a8ce6f41c12174530cceba60bade8abb7b55",
            "diagnostics/metrics/DiagnosticRunMetricsCalculator.kt" to "663f80df641b800167bd1aabd09d75e0d47ae31262a5fa275ec28f2c01b31473",
            "diagnostics/metrics/DiagnosticMetricPolicy.kt" to "840327e2eb2d632a424ab0c189f28bd0bf9768d721e4cc8b293621c33dc72504",
            "solver/ik/InverseKinematicsSolver.kt" to "060c51d12936fad976a861661b5952ad882b4dc5d99df407284d9d27e9289107"
        )
        val repository=if(File("src/main/java").isDirectory) File("..") else File(".")
        sources.forEach { (path,expected) ->
            val file=File(repository,"app/src/main/java/com/robotkinematicslab/mobile/$path")
            assertTrue("Missing implementation source: $file",file.isFile)
            val actual=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("Encoder/source changed: $path. Review formulas, units, validity and cards before updating the pinned source hash.",expected,actual)
        }
    }
}
