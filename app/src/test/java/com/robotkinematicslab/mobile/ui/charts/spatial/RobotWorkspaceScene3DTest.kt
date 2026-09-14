package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.dataset.DatasetRobotPresets
import com.robotkinematicslab.mobile.domain.DHParameter
import com.robotkinematicslab.mobile.domain.JointDefinition
import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.domain.RobotState
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalysisConfig
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceAnalyzer
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceSample
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceConvergenceCheckpoint
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceSampleKind
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass
import kotlin.math.PI
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RobotWorkspaceScene3DTest {

    @Test
    fun displayHonoursRevealTimeVisibilityAndHardRenderingCap() {
        val study =
            RobotWorkspaceAnalyzer().analyze(
                RobotDefinition(
                    "Display 2R",
                    listOf(DHParameter(0.0, 0.0, 0.5, 0.0), DHParameter(0.0, 0.0, 0.5, 0.0)),
                    listOf(
                        JointDefinition("J1", JointType.REVOLUTE, -PI, PI, 0.0),
                        JointDefinition("J2", JointType.REVOLUTE, -PI, PI, 0.0)
                    )
                ),
                RobotWorkspaceAnalysisConfig(sampleCount = 256, voxelResolution = 14, replicationCount = 2)
            )

        val none = displayWorkspaceVoxels(study, 0, RobotWorkspaceDisplayOptions(showUnobservedCandidates = true))
        val partial = displayWorkspaceVoxels(study, 20, RobotWorkspaceDisplayOptions(showUnobservedCandidates = true))
        val complete = displayWorkspaceVoxels(study, study.samples.size, RobotWorkspaceDisplayOptions(showUnobservedCandidates = true))
        val capped = displayWorkspaceVoxels(study, study.samples.size, RobotWorkspaceDisplayOptions(showUnobservedCandidates = true), 25)

        assertTrue(none.isEmpty())
        assertTrue(partial.none { it.sampleHitCount == 0 })
        assertTrue(complete.any { it.sampleHitCount == 0 })
        assertTrue(capped.size <= 25)
        assertEquals(capped.map { it.id }.distinct().size, capped.size)
    }

    @Test
    fun boundaryExtractionRemovesSharedInteriorFaces() {
        val single = observedVoxel(0, 0, 0)
        val adjacent = observedVoxel(1, 0, 0)

        assertEquals(
            6,
            extractVoxelBoundaryFaces(listOf(single), 1.0, WorkspaceSurfaceRegion.OBSERVED_REACHABLE).size
        )
        assertEquals(
            10,
            extractVoxelBoundaryFaces(
                listOf(single, adjacent),
                1.0,
                WorkspaceSurfaceRegion.OBSERVED_REACHABLE
            ).size
        )
    }

    @Test
    fun boundaryExtractionPreservesAnInternalCavity() {
        val hollowCube =
            buildList {
                for (x in 0..2) {
                    for (y in 0..2) {
                        for (z in 0..2) {
                            if (x != 1 || y != 1 || z != 1) add(observedVoxel(x, y, z))
                        }
                    }
                }
            }

        val faces =
            extractVoxelBoundaryFaces(
                hollowCube,
                cellSizeMeters = 1.0,
                region = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
            )

        assertEquals(60, faces.size)
        assertTrue(
            faces.any { face ->
                face.corners.all { corner -> corner.x == 0.5 } &&
                    face.center.y == 1.0 && face.center.z == 1.0
            }
        )
    }

    @Test
    fun jointLimitGuidesUseTheSameFramesAsCanonicalForwardKinematics() {
        val robot =
            RobotDefinition(
                "RP guide",
                listOf(
                    DHParameter(0.0, 0.1, 0.5, PI / 2.0),
                    DHParameter(0.0, 0.0, 0.2, 0.0)
                ),
                listOf(
                    JointDefinition("Shoulder", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Slide", JointType.PRISMATIC, 0.1, 0.4, 0.2)
                )
            )
        val state = listOf(0.3, 0.2)
        val fk = ForwardKinematicsSolver().solve(robot, RobotState(state))
        val guides = buildRobotJointLimitGuides(robot, state, visualRadiusMeters = 0.25)

        assertEquals(2, guides.size)
        assertVecEquals(fk.jointPositions[0], guides[0].origin)
        assertVecEquals(fk.jointPositions[1], guides[1].origin)
        assertEquals(1.0, guides[0].axis.norm(), 1e-12)
        assertEquals(1.0, guides[1].axis.norm(), 1e-12)
        assertTrue(guides[0].isFullRotation)
        assertFalse(guides[1].isFullRotation)
        assertVecEquals(guides[1].origin + guides[1].axis * 0.1, guides[1].boundaryPoints.first())
        assertVecEquals(guides[1].origin + guides[1].axis * 0.4, guides[1].boundaryPoints.last())
        assertVecEquals(guides[1].origin + guides[1].axis * 0.2, guides[1].currentPoint)
    }

    @Test
    fun candidateDeadSpaceSurfaceAppearsOnlyOnTheCompletedMap() {
        val study =
            RobotWorkspaceAnalyzer().analyze(
                RobotDefinition(
                    "Limited 1R",
                    listOf(DHParameter(0.0, 0.0, 0.7, 0.0)),
                    listOf(JointDefinition("Limited shoulder", JointType.REVOLUTE, -PI / 4.0, PI / 4.0, 0.0))
                ),
                RobotWorkspaceAnalysisConfig(sampleCount = 256, voxelResolution = 12, replicationCount = 2)
            )
        val options =
            RobotWorkspaceDisplayOptions(
                showReachableCore = false,
                showReachableBoundary = false,
                showUnobservedCandidates = true
            )

        val partial = buildWorkspaceSurfaceFaces(study, 128, options)
        val completed = buildWorkspaceSurfaceFaces(study, study.samples.size, options)

        assertTrue(partial.isEmpty())
        assertTrue(completed.isNotEmpty())
        assertTrue(completed.all { it.region == WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE })
        assertEquals(
            WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION,
            buildRobotWorkspaceEnvelopeConstruction(study).strategy
        )
    }

    @Test
    fun axisymmetricConstructionUsesActualBaseLimitsAndFiniteTriangularGeometry() {
        val robot =
            RobotDefinition(
                "Limited articulated 3R",
                listOf(
                    DHParameter(0.0, 0.2, 0.0, PI / 2.0),
                    DHParameter(0.0, 0.0, 0.65, 0.0),
                    DHParameter(0.0, 0.0, 0.45, 0.0)
                ),
                listOf(
                    JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Shoulder", JointType.REVOLUTE, -PI / 3.0, PI / 2.0, 0.1),
                    JointDefinition("Elbow", JointType.REVOLUTE, -2.0 * PI / 3.0, 2.0 * PI / 3.0, -0.2)
                )
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 512, voxelResolution = 16, replicationCount = 2)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP, construction.strategy)
        assertEquals(-PI, construction.sweepStartRadians, 1e-12)
        assertEquals(PI, construction.sweepEndRadians, 1e-12)
        assertTrue(construction.outerBoundary.isNotEmpty())
        assertTrue(construction.innerBoundary.isNotEmpty())
        assertTrue(construction.triangles.isNotEmpty())
        assertTrue(construction.hasInnerDeadSpace)
        assertTrue(
            construction.triangles.any { triangle ->
                triangle.region == WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE
            }
        )
        assertTrue(
            construction.triangles.all { triangle ->
                triangle.a.isFinite() && triangle.b.isFinite() && triangle.c.isFinite() &&
                    triangle.revealFraction in 0.0..1.0
            }
        )
        (0..20).forEach { step ->
            val values = requireNotNull(constructionJointValues(construction, robot, step / 20.0))
            assertEquals(robot.joints.size, values.size)
            values.forEachIndexed { index, value ->
                assertTrue(value in robot.joints[index].minValue..robot.joints[index].maxValue)
            }
        }
    }

    @Test
    fun nonAxialRobotUsesGeneralDhBoundaryReconstruction() {
        val robot =
            RobotDefinition(
                "Prismatic first axis",
                listOf(
                    DHParameter(0.0, 0.0, 0.0, 0.0),
                    DHParameter(0.0, 0.0, 0.6, 0.0)
                ),
                listOf(
                    JointDefinition("Lift", JointType.PRISMATIC, 0.0, 0.8, 0.2),
                    JointDefinition("Arm", JointType.REVOLUTE, -PI, PI, 0.0)
                )
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 256, voxelResolution = 12, replicationCount = 2)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(
            WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION,
            construction.strategy
        )
        assertTrue(construction.triangles.isNotEmpty())
        assertTrue(construction.outerBoundary.isEmpty())
        assertTrue(requireNotNull(constructionJointValues(construction, robot, 0.75)).all(Double::isFinite))
    }

    @Test
    fun prismaticPresetDoesNotCreateAnExteriorDeadSpaceIsland() {
        val robot =
            requireNotNull(
                DatasetRobotPresets().buildDefaults()
                    .firstOrNull { saved -> saved.id == "preset-04-prismatic" }
            ).robot
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 2_048, voxelResolution = 16, replicationCount = 2)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION, construction.strategy)
        assertEquals(0, construction.candidateDeadSpaceVoxelCount)
        assertFalse(construction.hasInnerDeadSpace)
    }

    @Test
    fun constructionTimelineHasStableStageBoundaries() {
        assertEquals(
            WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS,
            workspaceEnvelopeConstructionStage(0.0)
        )
        assertEquals(
            WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY,
            workspaceEnvelopeConstructionStage(0.10)
        )
        assertEquals(
            WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY,
            workspaceEnvelopeConstructionStage(0.36)
        )
        assertEquals(
            WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY,
            workspaceEnvelopeConstructionStage(0.56)
        )
        assertEquals(
            WorkspaceEnvelopeConstructionStage.FILL_SURFACE,
            workspaceEnvelopeConstructionStage(0.92)
        )
        assertEquals(1.0, workspaceEnvelopeStageFraction(1.0), 1e-12)
    }

    @Test
    fun openPlanarAnnulusIsNotMisclassifiedAsAClosedThreeDimensionalCavity() {
        val deadSpace = classifyInternalDeadSpaceVoxels(syntheticAnnularStudy())

        assertTrue(deadSpace.isEmpty())
    }

    @Test
    fun enclosedCavityClassifierKeepsOnlyTheTopologicallyInternalCell() {
        val study = syntheticEnclosedCavityStudy()

        val deadSpace = classifyInternalDeadSpaceVoxels(study)

        assertTrue(deadSpace.any { it.id == "5:5:5" })
        assertFalse(deadSpace.any { it.id == "9:9:9" })
    }

    @Test
    fun generalConstructionPreservesTenJointRobotStateDimension() {
        val jointCount = 10
        val robot =
            RobotDefinition(
                "Generic 10R",
                List(jointCount) { index ->
                    DHParameter(
                        theta = 0.0,
                        d = if (index % 3 == 0) 0.02 else 0.0,
                        a = 0.08,
                        alpha = if (index % 2 == 0) PI / 8.0 else -PI / 8.0
                    )
                },
                List(jointCount) { index ->
                    JointDefinition("J${index + 1}", JointType.REVOLUTE, -PI / 2.0, PI / 2.0, 0.0)
                }
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 256, voxelResolution = 10, replicationCount = 2)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION, construction.strategy)
        assertTrue(construction.triangles.isNotEmpty())
        assertEquals(
            WorkspaceEnvelopeConstructionStage.entries.toSet(),
            construction.stageMotions.keys
        )
        WorkspaceEnvelopeConstructionStage.entries.zipWithNext().forEach { (first, second) ->
            val firstMotion = requireNotNull(construction.stageMotion(first))
            val secondMotion = requireNotNull(construction.stageMotion(second))
            assertEquals(firstMotion.jointValues.last(), secondMotion.jointValues.first())
        }
        construction.stageMotions.values.forEach { motion ->
            assertEquals(motion.jointValues.size, motion.endEffectorPoints.size)
            motion.jointValues.zipWithNext().forEach { (first, second) ->
                robot.joints.indices.forEach { jointIndex ->
                    val span = robot.joints[jointIndex].maxValue - robot.joints[jointIndex].minValue
                    assertTrue(kotlin.math.abs(second[jointIndex] - first[jointIndex]) / span <= 0.035 + 1e-12)
                }
            }
        }
        (0..20).forEach { step ->
            val values = requireNotNull(constructionJointValues(construction, robot, step / 20.0))
            assertEquals(jointCount, values.size)
            assertTrue(values.all(Double::isFinite))
        }
    }

    @Test
    fun planarTwoLinkAnnulusMatchesItsAnalyticInnerAndOuterRadii() {
        val robot =
            RobotDefinition(
                "Analytic planar annulus",
                listOf(
                    DHParameter(0.0, 0.0, 0.7, 0.0),
                    DHParameter(0.0, 0.0, 0.3, 0.0)
                ),
                listOf(
                    JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Elbow", JointType.REVOLUTE, -PI, PI, 0.0)
                )
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 2_048, voxelResolution = 20, replicationCount = 4)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP, construction.strategy)
        assertEquals(1.0, construction.outerCurve.single().radiusMeters, 1e-9)
        assertEquals(0.4, construction.innerCurve.single().radiusMeters, 1e-9)
        assertTrue(construction.hasInnerDeadSpace)
        assertContinuousBoundedConstructionScript(robot, construction)
        val sweep = requireNotNull(construction.stageMotion(WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY))
        val sweptBaseAngles = sweep.jointValues.drop(sweep.traceStartIndex).map { it.first() }
        assertTrue(sweptBaseAngles.zipWithNext().all { (first, second) -> second + 1e-12 >= first })
        val sweptRadii =
            sweep.endEffectorPoints.drop(sweep.traceStartIndex).map { point ->
                kotlin.math.hypot(point.x, point.y)
            }
        assertTrue(sweptRadii.any { kotlin.math.abs(it - 1.0) < 1e-9 })
        assertTrue(sweptRadii.any { kotlin.math.abs(it - 0.4) < 1e-9 })
    }

    @Test
    fun equalPlanarLinksCorrectlyHaveNoResolvedCentralDeadSpace() {
        val robot =
            RobotDefinition(
                "Analytic planar disk",
                listOf(
                    DHParameter(0.0, 0.0, 0.5, 0.0),
                    DHParameter(0.0, 0.0, 0.5, 0.0)
                ),
                listOf(
                    JointDefinition("Base", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Elbow", JointType.REVOLUTE, -PI, PI, 0.0)
                )
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 1_024, voxelResolution = 18, replicationCount = 4)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(0.0, construction.innerCurve.single().radiusMeters, 1e-9)
        assertFalse(construction.hasInnerDeadSpace)
        assertContinuousBoundedConstructionScript(robot, construction)
    }

    @Test
    fun cylindricalRppWorkspaceRespectsAnalyticRadialAndVerticalLimits() {
        val robot =
            RobotDefinition(
                "Analytic cylindrical RPP",
                listOf(
                    DHParameter(0.0, 0.0, 0.0, -PI / 2.0),
                    DHParameter(0.0, 0.0, 0.0, PI / 2.0),
                    DHParameter(0.0, 0.0, 0.0, 0.0)
                ),
                listOf(
                    JointDefinition("Azimuth", JointType.REVOLUTE, -PI, PI, 0.0),
                    JointDefinition("Radius", JointType.PRISMATIC, 0.25, 0.75, 0.25),
                    JointDefinition("Height", JointType.PRISMATIC, 0.10, 0.60, 0.10)
                )
            )
        val solver = ForwardKinematicsSolver()
        listOf(0.0, PI / 2.0, PI).forEach { azimuth ->
            val position = solver.solve(robot, RobotState(listOf(azimuth, 0.25, 0.10))).endEffectorPosition
            assertEquals(0.25, kotlin.math.hypot(position.x, position.y), 1e-12)
            assertEquals(0.10, position.z, 1e-12)
        }
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 4_096, voxelResolution = 20, replicationCount = 4)
            )

        val construction = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP, construction.strategy)
        assertTrue(construction.outerCurve.all { kotlin.math.abs(it.radiusMeters - 0.75) < 0.035 })
        assertTrue(construction.innerCurve.all { kotlin.math.abs(it.radiusMeters - 0.25) < 0.035 })
        assertTrue(construction.outerCurve.minOf { it.heightMeters } >= 0.10 - 1e-12)
        assertTrue(construction.outerCurve.maxOf { it.heightMeters } <= 0.60 + 1e-12)
        assertTrue(construction.hasInnerDeadSpace)
        assertContinuousBoundedConstructionScript(robot, construction)
    }

    @Test
    fun generalConstructionScriptIsDeterministicAndNotSamplingOrder() {
        val robot =
            RobotDefinition(
                "General RP",
                listOf(
                    DHParameter(0.0, 0.0, 0.0, PI / 2.0),
                    DHParameter(0.0, 0.0, 0.4, 0.0)
                ),
                listOf(
                    JointDefinition("Lift", JointType.PRISMATIC, 0.0, 0.6, 0.2),
                    JointDefinition("Sweep", JointType.REVOLUTE, -PI, PI, 0.0)
                )
            )
        val study =
            RobotWorkspaceAnalyzer().analyze(
                robot,
                RobotWorkspaceAnalysisConfig(sampleCount = 512, voxelResolution = 14, replicationCount = 4)
            )

        val first = buildRobotWorkspaceEnvelopeConstruction(study)
        val second = buildRobotWorkspaceEnvelopeConstruction(study)

        assertEquals(first.stageMotions, second.stageMotions)
        val outer = requireNotNull(first.stageMotion(WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY))
        assertTrue(outer.jointValues.size > 2)
        assertFalse(outer.jointValues == study.samples.map(RobotWorkspaceSample::jointValues))
        outer.jointValues.zip(outer.endEffectorPoints).forEach { (jointValues, expectedPosition) ->
            val actual = solverPosition(robot, jointValues)
            assertVecEquals(expectedPosition, actual)
        }
    }

    private fun assertContinuousBoundedConstructionScript(
        robot: RobotDefinition,
        construction: RobotWorkspaceEnvelopeConstruction
    ) {
        assertEquals(WorkspaceEnvelopeConstructionStage.entries.toSet(), construction.stageMotions.keys)
        WorkspaceEnvelopeConstructionStage.entries.zipWithNext().forEach { (first, second) ->
            assertEquals(
                requireNotNull(construction.stageMotion(first)).jointValues.last(),
                requireNotNull(construction.stageMotion(second)).jointValues.first()
            )
        }
        construction.stageMotions.values.forEach { motion ->
            assertEquals(motion.jointValues.size, motion.endEffectorPoints.size)
            motion.jointValues.forEach { values ->
                assertEquals(robot.joints.size, values.size)
                values.forEachIndexed { index, value ->
                    assertTrue(value.isFinite())
                    assertTrue(value in robot.joints[index].minValue..robot.joints[index].maxValue)
                }
            }
            motion.jointValues.zipWithNext().forEach { (first, second) ->
                robot.joints.indices.forEach { index ->
                    val joint = robot.joints[index]
                    val span = joint.maxValue - joint.minValue
                    val direct = kotlin.math.abs(second[index] - first[index])
                    val shortest =
                        if (joint.type == JointType.REVOLUTE && span >= 2.0 * PI - 1e-6) {
                            minOf(direct, kotlin.math.abs(direct - 2.0 * PI))
                        } else {
                            direct
                        }
                    assertTrue(shortest / span <= 0.035 + 1e-12)
                }
            }
        }
    }

    private fun syntheticAnnularStudy(): RobotWorkspaceStudy {
        val resolution = 10
        val radius = 2.0
        val cellSize = radius * 2.0 / resolution
        val robot =
            RobotDefinition(
                "Synthetic annulus",
                listOf(DHParameter(0.0, 0.0, 1.0, 0.0)),
                listOf(JointDefinition("Axis", JointType.REVOLUTE, -PI / 2.0, PI / 2.0, 0.0))
            )
        val observed =
            List(24) { index ->
                val angle = 2.0 * PI * (index + 0.5) / 24.0
                RobotWorkspaceVoxel(
                    xIndex = (5 + 2.5 * kotlin.math.cos(angle)).toInt().coerceIn(0, 9),
                    yIndex = (5 + 2.5 * kotlin.math.sin(angle)).toInt().coerceIn(0, 9),
                    zIndex = 5,
                    center = Vec3(kotlin.math.cos(angle), kotlin.math.sin(angle), 0.0),
                    classification = WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY,
                    sampleHitCount = 1,
                    firstObservedSampleIndex = index
                )
            }.distinctBy(RobotWorkspaceVoxel::id)
        val candidates =
            listOf(
                RobotWorkspaceVoxel(5, 5, 5, Vec3(0.1, 0.1, 0.0), WorkspaceVoxelClass.UNOBSERVED_CANDIDATE, 0, null),
                RobotWorkspaceVoxel(9, 5, 5, Vec3(1.8, 0.0, 0.0), WorkspaceVoxelClass.UNOBSERVED_CANDIDATE, 0, null)
            )
        val samples =
            List(256) { index ->
                RobotWorkspaceSample(
                    sequenceIndex = index,
                    replicationIndex = index % 2,
                    kind = WorkspaceSampleKind.QUASI_RANDOM,
                    jointValues = listOf(0.0),
                    endEffector = Vec3(1.0, 0.0, 0.0)
                )
            }
        return RobotWorkspaceStudy(
            studyId = "synthetic-annulus",
            studyName = "Synthetic annulus",
            createdAtEpochMillis = 1L,
            robotFingerprint = "synthetic",
            robot = robot,
            config = RobotWorkspaceAnalysisConfig(sampleCount = 256, voxelResolution = resolution, replicationCount = 2),
            workerCount = 1,
            durationMillis = 1L,
            conservativeRadiusMeters = radius,
            voxelCellSizeMeters = cellSize,
            conservativeSphereVolumeCubicMeters = 4.0 * PI * radius * radius * radius / 3.0,
            classifiedEnvelopeVolumeCubicMeters = (observed.size + candidates.size) * cellSize * cellSize * cellSize,
            observedVoxelVolumeCubicMeters = observed.size * cellSize * cellSize * cellSize,
            unobservedCandidateVolumeCubicMeters = candidates.size * cellSize * cellSize * cellSize,
            observedEnvelopeFraction = observed.size.toDouble() / (observed.size + candidates.size),
            lastQuarterRelativeVolumeGain = 0.0,
            invalidSampleCount = 0,
            samples = samples,
            voxels = observed + candidates,
            convergence = listOf(WorkspaceConvergenceCheckpoint(256, observed.size, observed.size * cellSize * cellSize * cellSize))
        )
    }

    private fun syntheticEnclosedCavityStudy(): RobotWorkspaceStudy {
        val base = syntheticAnnularStudy()
        val shell =
            buildList {
                for (x in 4..6) {
                    for (y in 4..6) {
                        for (z in 4..6) {
                            if (x != 5 || y != 5 || z != 5) add(observedVoxel(x, y, z))
                        }
                    }
                }
            }
        val candidates =
            listOf(
                RobotWorkspaceVoxel(5, 5, 5, Vec3(5.0, 5.0, 5.0), WorkspaceVoxelClass.UNOBSERVED_CANDIDATE, 0, null),
                RobotWorkspaceVoxel(9, 9, 9, Vec3(9.0, 9.0, 9.0), WorkspaceVoxelClass.UNOBSERVED_CANDIDATE, 0, null)
            )
        val voxels = shell + candidates
        val cellVolume = base.voxelCellSizeMeters * base.voxelCellSizeMeters * base.voxelCellSizeMeters
        return base.copy(
            studyId = "synthetic-enclosed-cavity",
            studyName = "Synthetic enclosed cavity",
            classifiedEnvelopeVolumeCubicMeters = voxels.size * cellVolume,
            observedVoxelVolumeCubicMeters = shell.size * cellVolume,
            unobservedCandidateVolumeCubicMeters = candidates.size * cellVolume,
            observedEnvelopeFraction = shell.size.toDouble() / voxels.size,
            voxels = voxels,
            convergence = listOf(WorkspaceConvergenceCheckpoint(256, shell.size, shell.size * cellVolume))
        )
    }

    private fun observedVoxel(x: Int, y: Int, z: Int): RobotWorkspaceVoxel =
        RobotWorkspaceVoxel(
            xIndex = x,
            yIndex = y,
            zIndex = z,
            center = Vec3(x.toDouble(), y.toDouble(), z.toDouble()),
            classification = WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE,
            sampleHitCount = 1,
            firstObservedSampleIndex = 0
        )

    private fun assertVecEquals(expected: Vec3, actual: Vec3) {
        assertEquals(expected.x, actual.x, 1e-12)
        assertEquals(expected.y, actual.y, 1e-12)
        assertEquals(expected.z, actual.z, 1e-12)
    }

    private fun solverPosition(robot: RobotDefinition, jointValues: List<Double>): Vec3 =
        ForwardKinematicsSolver().solve(robot, RobotState(jointValues)).endEffectorPosition
}
