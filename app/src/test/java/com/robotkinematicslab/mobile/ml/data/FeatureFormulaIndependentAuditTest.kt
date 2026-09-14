package com.robotkinematicslab.mobile.ml.data

import com.robotkinematicslab.mobile.dataset.ScientificDatasetCsvWriter
import com.robotkinematicslab.mobile.domain.*
import com.robotkinematicslab.mobile.domain.config.IKConfig
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.ml.ik.*
import java.nio.file.Files
import kotlin.math.*
import org.junit.Assert.*
import org.junit.Test

/** Closed-form fixtures, independent of the dictionary text and of model fitting. */
class FeatureFormulaIndependentAuditTest {
    private fun prism() = ExpandedContextInput(listOf("PRISMATIC"),listOf(0.0),listOf(0.0),listOf(0.0),listOf(0.0),
        listOf(0.0),listOf(2.0),listOf(1.0),listOf(.5),0.0,0.0,1.5,1e-6,.1,.25)
    private fun expanded(input: ExpandedContextInput) = ExpandedContextFeatureCalculator.featureNames(10)
        .zip(ExpandedContextFeatureCalculator.calculate(input,10).toList()).toMap()
    private fun research(input: ExpandedContextInput) = ResearchContextFeatureCalculator.featureNames(10)
        .zip(ResearchContextFeatureCalculator.calculate(input,10).toList()).toMap()
    private fun check(values: Map<String,Double>, name:String, expected:Double) = assertEquals(name,expected,values.getValue(name),1e-10)

    @Test fun prismaticClosedFormCoversGeometrySpectrumDlsLimitsAndPadding() {
        val values=expanded(prism())
        // J=(0,0,1), half-span=1m; Gram=diag(0,0,1), error=(0,0,1)m.
        check(values,"seed_end_effector_z",.5)
        check(values,"normalized_seed_end_effector_z",.25) // conservative reach=2m
        check(values,"recomputed_initial_error",1.0)
        check(values,"normalized_initial_error",.5)
        check(values,"target_elevation_sin",1.0)
        check(values,"jacobian_sigma_max",1.0)
        check(values,"jacobian_sigma_middle",0.0)
        check(values,"jacobian_condition_number",1.0) // expected task rank is 1, not 3
        check(values,"jacobian_task_rank_ratio",1.0/3.0)
        check(values,"jacobian_manipulability",1.0)
        check(values,"directional_manipulability",1.0)
        // lambda=max(.1, .5*error)=.5; dq=1/(1+.5²)=.8, clipped to .25.
        check(values,"dls_preview_lambda",.5)
        check(values,"dls_raw_step_l2",.8)
        check(values,"dls_normalized_step_l2",.8)
        check(values,"dls_max_step_scale",.25/.8)
        check(values,"dls_predicted_cartesian_step",.25)
        check(values,"dls_predicted_residual",.75)
        check(values,"dls_predicted_improvement_ratio",.25)
        check(values,"dls_error_alignment_cosine",1.0)
        check(values,"dls_min_directional_headroom",1.5/.8)
        check(values,"joint_1_seed_signed_position",-.5)
        check(values,"joint_1_seed_lower_margin",.25)
        check(values,"joint_1_seed_upper_margin",.75)
        check(values,"joint_1_seed_limit_barrier",ln(4.0))
        check(values,"joint_1_seed_home_delta_normalized",-.5)
        check(values,"joint_1_active_extent_ratio",.25)
        check(values,"joint_1_static_extent_ratio",0.0)
        check(values,"joint_type_transition_fraction",0.0)
        check(values,"longest_joint_type_run_fraction",1.0)
        values.filterKeys { it.startsWith("joint_2_") }.values.forEach { assertEquals(0.0,it,0.0) }
    }

    @Test fun researchUsesActiveExtentAndItsOwnMarginFloorsNotConservativeReach() {
        val values=research(prism())
        // Active reach=.5m, target radius=1.5m => utilization3, despite conservative reach2m.
        check(values,"research_v2_workspace_outer_pressure_squared",9.0)
        check(values,"research_v2_workspace_outer_pressure_fourth",81.0)
        check(values,"research_v2_workspace_boundary_inverse_distance",.5)
        check(values,"research_v2_joint_pressure_squared_mean",.25)
        check(values,"research_v2_joint_pressure_fourth_mean",.0625)
        check(values,"research_v2_joint_pressure_squared_rms",.25)
        check(values,"research_v2_link_extent_gini",0.0)
        check(values,"research_v2_joint_1_inverse_nearest_margin",4.0)
        check(values,"research_v2_joint_1_limit_exponential_pressure",exp(-2.5))
        check(values,"research_v2_joint_1_home_displacement_squared",.25)
        check(values,"research_v2_joint_1_home_displacement_x_limit_pressure",.0625)
        check(values,"research_v2_joint_1_link_leverage_x_limit_pressure",.25)
        check(research(prism().copy(seeds=listOf(0.0))),"research_v2_joint_1_inverse_nearest_margin",10000.0)
        values.filterKeys { it.startsWith("research_v2_joint_2_") }.values.forEach { assertEquals(0.0,it,0.0) }
    }

    @Test fun zeroJacobianDocumentsFiniteConventionsWithoutClaimingActualRankOne() {
        val input=prism().copy(jointTypes=listOf("REVOLUTE"),minimums=listOf(-1.0),maximums=listOf(1.0),homes=listOf(0.0),seeds=listOf(0.0),targetZ=0.0)
        val values=expanded(input)
        check(values,"jacobian_sigma_max",0.0)
        check(values,"log_jacobian_sigma_max",ln(1e-12))
        check(values,"jacobian_condition_number",1e12)
        check(values,"jacobian_reciprocal_condition",1e-12)
        check(values,"jacobian_task_rank_ratio",0.0)
        check(values,"jacobian_effective_rank",1.0) // exp(entropy0) is a convention at zero energy.
        check(values,"dls_min_directional_headroom",1e6)
        check(values,"target_azimuth_cos",0.0)
        check(values,"target_elevation_cos",0.0)
        assertTrue(values.values.all(Double::isFinite))
        assertThrows(IllegalArgumentException::class.java) { expanded(input.copy(theta=listOf(.1))) }
    }

    @Test fun importedConditionUsesBaseTenWhileRecomputedLogUsesNaturalLog() {
        val calculator=com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticDerivedMetricsCalculator()
        assertEquals(3.0,calculator.logConditionNumber(1000.0),0.0)
        assertEquals(12.0,calculator.logConditionNumber(Double.POSITIVE_INFINITY),0.0)
        assertTrue(calculator.logConditionNumber(Double.NaN).isNaN())
        val singular=prism().copy(jointTypes=listOf("REVOLUTE"),minimums=listOf(-1.0),maximums=listOf(1.0),homes=listOf(0.0),seeds=listOf(0.0),targetZ=0.0)
        check(expanded(singular),"log_recomputed_jacobian_condition",ln(1e12))
    }

    @Test fun realCsvBuildersPreservePrefixesAndVerifiedIkSkipsTheTwentyTwoCsvContextInputs() {
        val row=mapOf("schemaVersion" to ScientificDatasetCsvWriter.SCHEMA_VERSION,"robotId" to "formula-fixture","jointCount" to "1",
            "jointTypes" to "PRISMATIC","dhThetaRad" to "0","dhDMeters" to "0","dhAMeters" to "0","dhAlphaRad" to "0",
            "jointMinValues" to "0","jointMaxValues" to "2","jointHomeValues" to "1","seedJointValues" to ".5",
            "targetX" to "0","targetY" to "0","targetZ" to "1.5","ikMaxIterations" to "100","ikToleranceMeters" to "0.000001",
            "ikDamping" to ".1","ikMaxStep" to ".25","status" to "SUCCESS","detailCode" to "NONE","converged" to "true",
            "iterations" to "1","finalError" to "0","solverAccepted" to "true","acceptanceClass" to "ACCEPTED")
        val csv=Files.createTempFile("formula-encoder-fixture",".csv").toFile()
        // Synthetic valid CSV records exercise encoding only; they are not solver-produced evidence.
        csv.writeText(ScientificDatasetCsvWriter.HEADER.joinToString(",")+"\n"+
            (0 until 30).joinToString("\n",postfix="\n") { ScientificDatasetCsvWriter.HEADER.joinToString(",") { row[it].orEmpty() } })
        val profiles=listOf(TrainingFeatureProfile.BASELINE_KINEMATICS,TrainingFeatureProfile.CONTEXT_ENHANCED,TrainingFeatureProfile.CONTEXT_EXPANDED,TrainingFeatureProfile.CONTEXT_RESEARCH_V2)
        val vectors=profiles.map { profile ->
            val loaded=ScientificDatasetTrainingReader().load(csv,profile,30)
            requireNotNull(loaded.dataset) { loaded.errorMessage.orEmpty() }.samples.first().features
        }
        assertEquals(listOf(108,130,383,468),vectors.map { it.size })
        vectors.zipWithNext().forEach { (small,large) -> assertArrayEquals(small,large.copyOf(small.size),0f) }
        val enhanced=ScientificDatasetTrainingReader.featureNames(profiles[1]).zip(vectors[1].toList()).toMap()
        assertEquals(.25f,enhanced.getValue("seed_home_offset_rms"),0f) // full span, not half span
        assertEquals(0f,enhanced.getValue("initial_error_available"),0f)
        assertEquals(0f,enhanced.getValue("initial_cartesian_error"),0f)
        assertEquals(2f,enhanced.getValue("conservative_reach_bound"),0f)
        val robot=RobotDefinition("fixture",listOf(DHParameter(0.0,0.0,0.0,0.0)),listOf(JointDefinition("slide",JointType.PRISMATIC,0.0,2.0,1.0)))
        val ik=OneMicronIkFeatureEncoder.encode(robot,RobotState(listOf(.5)),Vec3(0.0,0.0,1.5),IKConfig(maxIterations=100,tolerance=1e-6,damping=.1,maxStep=.25),OneMicronIkFeatureProfile.PHYSICS_CONTEXT_361)
        assertEquals(361,ik.size)
        assertArrayEquals(vectors[0],ik.copyOf(108),0f)
        assertArrayEquals(vectors[2].copyOfRange(130,383),ik.copyOfRange(108,361),0f)
        assertEquals(0f,ik[18],0f) // joint2 present is padded, including its inactive trigonometric context.
    }
}
