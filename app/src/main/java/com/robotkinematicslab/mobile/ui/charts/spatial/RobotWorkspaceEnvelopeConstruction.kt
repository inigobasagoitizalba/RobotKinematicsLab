package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.domain.JointType
import com.robotkinematicslab.mobile.domain.RobotDefinition
import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.solver.fk.ForwardKinematicsSolver
import com.robotkinematicslab.mobile.solver.fk.MutableFKPositionOnlyResult
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceSample
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.sin

enum class WorkspaceEnvelopeConstructionStrategy {
    AXISYMMETRIC_JOINT_SWEEP,
    GENERAL_DH_BOUNDARY_RECONSTRUCTION
}

enum class WorkspaceEnvelopeConstructionStage {
    FREEZE_REFERENCE_AXIS,
    TRACE_OUTER_BOUNDARY,
    TRACE_INNER_BOUNDARY,
    SWEEP_BOUNDARY,
    FILL_SURFACE
}

internal data class WorkspaceBoundaryPose(
    val radiusMeters: Double,
    val heightMeters: Double,
    val jointValues: List<Double>
)

internal data class WorkspaceRadialPoint(
    val radiusMeters: Double,
    val heightMeters: Double
)

internal data class WorkspaceEnvelopeTriangle(
    val a: Vec3,
    val b: Vec3,
    val c: Vec3,
    val region: WorkspaceSurfaceRegion,
    val revealFraction: Double
) {
    val center: Vec3
        get() = (a + b + c) / 3.0

    val normal: Vec3
        get() = (b - a).cross(c - a)
}

/**
 * A deterministic robot motion used by one construction stage.
 *
 * The motion is calculated from actual joint-limit-respecting FK samples. [traceStartIndex]
 * separates a repositioning prefix from the part that genuinely traces the requested boundary;
 * this prevents the renderer from drawing a false straight line while the robot moves between
 * disconnected boundary regions.
 */
internal data class WorkspaceConstructionMotion(
    val jointValues: List<List<Double>>,
    val endEffectorPoints: List<Vec3>,
    val traceStartIndex: Int = 0,
    val traceRegion: WorkspaceSurfaceRegion? = null
) {
    init {
        require(jointValues.size == endEffectorPoints.size)
        require(traceStartIndex in 0..jointValues.size.coerceAtLeast(1))
    }
}

internal data class RobotWorkspaceEnvelopeConstruction(
    val strategy: WorkspaceEnvelopeConstructionStrategy,
    val outerBoundary: List<WorkspaceBoundaryPose>,
    val innerBoundary: List<WorkspaceBoundaryPose>,
    val outerCurve: List<WorkspaceRadialPoint>,
    val innerCurve: List<WorkspaceRadialPoint>,
    val triangles: List<WorkspaceEnvelopeTriangle>,
    val sweepStartRadians: Double,
    val sweepEndRadians: Double,
    val frozenBaseRadians: Double,
    val stageMotions: Map<WorkspaceEnvelopeConstructionStage, WorkspaceConstructionMotion>,
    val hasInnerDeadSpace: Boolean,
    val candidateDeadSpaceVoxelCount: Int
)

internal fun buildRobotWorkspaceEnvelopeConstruction(
    study: RobotWorkspaceStudy
): RobotWorkspaceEnvelopeConstruction {
    val robot = study.robot
    val firstJoint = robot.joints.firstOrNull()
    val firstJointSpan = firstJoint?.let { it.maxValue - it.minValue } ?: 0.0
    if (firstJoint?.type == JointType.REVOLUTE && firstJointSpan >= TWO_PI - FULL_ROTATION_TOLERANCE) {
        val radialProfile = buildRadialProfile(study)
        if (radialProfile != null) {
            val sweepStart = firstJoint.minValue
            val sweepEnd = minOf(firstJoint.maxValue, sweepStart + TWO_PI)
            val frozenBase = firstJoint.homeValue.coerceIn(sweepStart, sweepEnd)
            val outerCurve = smoothAndDensify(radialProfile.outer.map(WorkspaceBoundaryPose::toRadialPoint))
            val innerCurve = smoothAndDensify(radialProfile.inner.map(WorkspaceBoundaryPose::toRadialPoint))
            val deadSpaceThreshold = study.voxelCellSizeMeters * 0.65
            val hasInnerDeadSpace = innerCurve.any { it.radiusMeters > deadSpaceThreshold }
            val axisymmetricDeadSpaceVoxelCount =
                if (hasInnerDeadSpace) {
                    countAxisymmetricDeadSpaceVoxels(study, innerCurve)
                } else {
                    0
                }
            val stageMotions =
                buildAxisymmetricConstructionMotions(
                    robot = robot,
                    outerBoundary = radialProfile.outer,
                    innerBoundary = radialProfile.inner,
                    frozenBaseRadians = frozenBase,
                    sweepStartRadians = sweepStart,
                    sweepEndRadians = sweepEnd,
                    hasInnerDeadSpace = hasInnerDeadSpace
                )
            return RobotWorkspaceEnvelopeConstruction(
                strategy = WorkspaceEnvelopeConstructionStrategy.AXISYMMETRIC_JOINT_SWEEP,
                outerBoundary = radialProfile.outer,
                innerBoundary = radialProfile.inner,
                outerCurve = outerCurve,
                innerCurve = innerCurve,
                triangles =
                    buildSurfaceOfRevolution(
                        outerCurve = outerCurve,
                        innerCurve = innerCurve,
                        sweepStartRadians = sweepStart,
                        sweepEndRadians = sweepEnd
                    ),
                sweepStartRadians = sweepStart,
                sweepEndRadians = sweepEnd,
                frozenBaseRadians = frozenBase,
                stageMotions = stageMotions,
                hasInnerDeadSpace = hasInnerDeadSpace,
                candidateDeadSpaceVoxelCount = axisymmetricDeadSpaceVoxelCount
            )
        }
    }

    val internalDeadSpace = classifyInternalDeadSpaceVoxels(study)
    val reachable =
        study.voxels.filter {
            it.classification == WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE ||
                it.classification == WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY
        }
    val rawTriangles =
        buildImplicitBoundaryTriangles(
            voxels = reachable,
            study = study,
            region = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
        ) +
            buildImplicitBoundaryTriangles(
                voxels = internalDeadSpace,
                study = study,
                region = WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE
            )
    val generalPlan = buildGeneralConstructionPlan(study, internalDeadSpace)
    val triangles = assignGeneralRevealFractions(rawTriangles, generalPlan)
    return RobotWorkspaceEnvelopeConstruction(
        strategy = WorkspaceEnvelopeConstructionStrategy.GENERAL_DH_BOUNDARY_RECONSTRUCTION,
        outerBoundary = emptyList(),
        innerBoundary = emptyList(),
        outerCurve = emptyList(),
        innerCurve = emptyList(),
        triangles = triangles,
        sweepStartRadians = -PI,
        sweepEndRadians = PI,
        frozenBaseRadians = 0.0,
        stageMotions = generalPlan.motions,
        hasInnerDeadSpace = internalDeadSpace.isNotEmpty(),
        candidateDeadSpaceVoxelCount = internalDeadSpace.size
    )
}

/** Counts only unobserved cells inside the reconstructed radial cavity.
 *
 * The directional classifier operates in a three-dimensional conservative sphere. For a
 * lower-dimensional planar arm it can therefore include off-plane cells that are not represented
 * by the displayed annulus. This strategy-specific count keeps the label and the red geometry in
 * the same evidence domain.
 */
private fun countAxisymmetricDeadSpaceVoxels(
    study: RobotWorkspaceStudy,
    innerCurve: List<WorkspaceRadialPoint>
): Int {
    if (innerCurve.isEmpty()) return 0
    val minimumHeight = innerCurve.minOf(WorkspaceRadialPoint::heightMeters)
    val maximumHeight = innerCurve.maxOf(WorkspaceRadialPoint::heightMeters)
    val heightMargin = study.voxelCellSizeMeters * AXISYMMETRIC_HEIGHT_MARGIN_CELLS
    val radialMargin = study.voxelCellSizeMeters * INTERNAL_ENVELOPE_MARGIN_CELLS
    return study.voxels.count { voxel ->
        if (
            voxel.classification != WorkspaceVoxelClass.UNOBSERVED_CANDIDATE ||
            voxel.center.z !in (minimumHeight - heightMargin)..(maximumHeight + heightMargin)
        ) {
            false
        } else {
            val innerRadius = interpolateRadialRadius(innerCurve, voxel.center.z)
            hypot(voxel.center.x, voxel.center.y) + radialMargin < innerRadius
        }
    }
}

private fun interpolateRadialRadius(
    curve: List<WorkspaceRadialPoint>,
    heightMeters: Double
): Double {
    if (curve.size == 1) return curve.single().radiusMeters
    val upperIndex = curve.indexOfFirst { point -> point.heightMeters >= heightMeters }
    if (upperIndex <= 0) return curve.first().radiusMeters
    if (upperIndex < 0) return curve.last().radiusMeters
    val lower = curve[upperIndex - 1]
    val upper = curve[upperIndex]
    val span = upper.heightMeters - lower.heightMeters
    if (abs(span) <= DIRECTION_EPSILON) return (lower.radiusMeters + upper.radiusMeters) / 2.0
    val fraction = ((heightMeters - lower.heightMeters) / span).coerceIn(0.0, 1.0)
    return lower.radiusMeters + (upper.radiusMeters - lower.radiusMeters) * fraction
}

private data class RevealAnchor(
    val point: Vec3,
    val revealFraction: Double
)

/**
 * Builds the literal construction demonstrated by the classical work-envelope method:
 * freeze the full-turn base, trace the radial exterior/interior profile, then revolve that
 * complete profile around the base axis. Angular slices are traversed in alternating directions,
 * like a raster scan, so consecutive poses stay close instead of describing an artificial helix.
 */
private fun buildAxisymmetricConstructionMotions(
    robot: RobotDefinition,
    outerBoundary: List<WorkspaceBoundaryPose>,
    innerBoundary: List<WorkspaceBoundaryPose>,
    frozenBaseRadians: Double,
    sweepStartRadians: Double,
    sweepEndRadians: Double,
    hasInnerDeadSpace: Boolean
): Map<WorkspaceEnvelopeConstructionStage, WorkspaceConstructionMotion> {
    val home = robot.joints.map { joint -> joint.homeValue }

    fun atBase(pose: WorkspaceBoundaryPose, angle: Double): List<Double> =
        pose.jointValues.toMutableList().apply {
            this[0] = angle.coerceIn(robot.joints[0].minValue, robot.joints[0].maxValue)
        }

    val frozenOuter =
        outerBoundary.map { pose -> atBase(pose, frozenBaseRadians) }.ifEmpty { listOf(home) }
    val frozenInner = innerBoundary.map { pose -> atBase(pose, frozenBaseRadians) }

    val approachValues = densifyJointPath(listOf(home, frozenOuter.first()), robot)
    val outerValues = densifyJointPath(frozenOuter, robot)

    val innerTraceValues =
        if (hasInnerDeadSpace) densifyJointPath(frozenInner, robot) else emptyList()
    val innerTransitionValues =
        if (innerTraceValues.isNotEmpty()) {
            densifyJointPath(listOf(outerValues.last(), innerTraceValues.first()), robot)
        } else {
            listOf(outerValues.last())
        }
    val innerValues = concatenateJointPaths(innerTransitionValues, innerTraceValues)
    val innerTraceStart =
        if (innerTraceValues.isEmpty()) innerValues.size
        else (innerTransitionValues.size - 1).coerceAtLeast(0)

    val sweepProfileInner = if (hasInnerDeadSpace) innerBoundary else emptyList()
    val sweepWaypoints = ArrayList<List<Double>>()
    for (slice in 0..AXISYMMETRIC_SWEEP_SLICES) {
        val fraction = slice.toDouble() / AXISYMMETRIC_SWEEP_SLICES
        val angle = sweepStartRadians + (sweepEndRadians - sweepStartRadians) * fraction
        val outerSlice = outerBoundary.map { pose -> atBase(pose, angle) }.ifEmpty { listOf(home) }
        val innerSlice = sweepProfileInner.map { pose -> atBase(pose, angle) }
        val sliceWaypoints =
            if (slice % 2 == 0) {
                outerSlice + innerSlice.asReversed()
            } else {
                innerSlice + outerSlice.asReversed()
            }
        sliceWaypoints.forEach { values ->
            if (sweepWaypoints.lastOrNull() != values) sweepWaypoints += values
        }
    }
    if (sweepWaypoints.isEmpty()) sweepWaypoints += outerValues.last()
    val sweepTransitionValues =
        densifyJointPath(listOf(innerValues.last(), sweepWaypoints.first()), robot)
    val sweepValues =
        densifyJointPath(
            concatenateJointPaths(sweepTransitionValues, sweepWaypoints),
            robot
        )

    return mapOf(
        WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS to
            buildMotion(robot, approachValues),
        WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY to
            buildMotion(
                robot = robot,
                jointValues = outerValues,
                traceRegion = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
            ),
        WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY to
            buildMotion(
                robot = robot,
                jointValues = innerValues,
                traceStartIndex = innerTraceStart,
                traceRegion =
                    if (innerTraceValues.isEmpty()) null
                    else WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE
            ),
        WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY to
            buildMotion(
                robot = robot,
                jointValues = sweepValues,
                traceStartIndex = (sweepTransitionValues.size - 1).coerceAtLeast(0),
                traceRegion = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
            ),
        WorkspaceEnvelopeConstructionStage.FILL_SURFACE to
            buildMotion(robot, listOf(sweepValues.last()))
    )
}

private data class GeneralConstructionPlan(
    val motions: Map<WorkspaceEnvelopeConstructionStage, WorkspaceConstructionMotion>,
    val revealAnchors: Map<WorkspaceSurfaceRegion, List<RevealAnchor>>
)

/**
 * Converts unordered coverage samples into a deterministic construction script.
 *
 * Sampling order must never be used as robot motion: the sequence deliberately jumps around
 * joint space to improve statistical coverage. Here, reachable boundary cells are split into the
 * exterior frontier and the reachable rim beside candidate internal dead space. Spatially diverse
 * representatives are then ordered by a bounded joint/cartesian travel cost and every transition
 * is densified. The resulting script is smooth for any number or mixture of supported joints.
 */
private fun buildGeneralConstructionPlan(
    study: RobotWorkspaceStudy,
    internalDeadSpace: List<RobotWorkspaceVoxel>
): GeneralConstructionPlan {
    val robot = study.robot
    val sampleBySequence = study.samples.associateBy(RobotWorkspaceSample::sequenceIndex)
    val internalKeys =
        internalDeadSpace.mapTo(HashSet(internalDeadSpace.size * 2)) { voxel ->
            ImplicitGridPoint(voxel.xIndex, voxel.yIndex, voxel.zIndex)
        }
    val reachableBoundary =
        study.voxels.filter { voxel ->
            voxel.classification == WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY &&
                voxel.firstObservedSampleIndex != null
        }
    val innerVoxels =
        reachableBoundary.filter { voxel ->
            val key = ImplicitGridPoint(voxel.xIndex, voxel.yIndex, voxel.zIndex)
            FACE_NEIGHBOUR_OFFSETS.any { delta ->
                ImplicitGridPoint(key.x + delta.x, key.y + delta.y, key.z + delta.z) in internalKeys
            }
        }
    val innerVoxelIds = innerVoxels.mapTo(HashSet(innerVoxels.size * 2), RobotWorkspaceVoxel::id)
    val outerVoxels = reachableBoundary.filterNot { it.id in innerVoxelIds }.ifEmpty { reachableBoundary }

    fun samplesFor(voxels: List<RobotWorkspaceVoxel>): List<RobotWorkspaceSample> =
        voxels.mapNotNull { voxel -> voxel.firstObservedSampleIndex?.let(sampleBySequence::get) }
            .distinctBy(RobotWorkspaceSample::sequenceIndex)

    val home = robot.joints.map { joint -> joint.homeValue }
    val outerCandidates = samplesFor(outerVoxels).ifEmpty { study.samples }
    val outerWaypoints =
        orderedBoundaryRepresentatives(
            candidates = outerCandidates,
            robot = robot,
            initialJointValues = home,
            conservativeRadiusMeters = study.conservativeRadiusMeters,
            maximumPoints = MAXIMUM_GENERAL_OUTER_WAYPOINTS
        )
    val innerWaypoints =
        orderedBoundaryRepresentatives(
            candidates = samplesFor(innerVoxels),
            robot = robot,
            initialJointValues = outerWaypoints.lastOrNull()?.jointValues ?: home,
            conservativeRadiusMeters = study.conservativeRadiusMeters,
            maximumPoints = MAXIMUM_GENERAL_INNER_WAYPOINTS
        )

    val outerJointWaypoints = outerWaypoints.map(RobotWorkspaceSample::jointValues).ifEmpty { listOf(home) }
    val outerMotionValues = densifyJointPath(outerJointWaypoints, robot)
    val approachValues = densifyJointPath(listOf(home, outerMotionValues.first()), robot)

    val innerTraceValues = densifyJointPath(innerWaypoints.map(RobotWorkspaceSample::jointValues), robot)
    val innerTransitionValues =
        if (innerTraceValues.isNotEmpty()) {
            densifyJointPath(listOf(outerMotionValues.last(), innerTraceValues.first()), robot)
        } else {
            listOf(outerMotionValues.last())
        }
    val innerMotionValues = concatenateJointPaths(innerTransitionValues, innerTraceValues)
    val innerTraceStart =
        if (innerTraceValues.isEmpty()) innerMotionValues.size else (innerTransitionValues.size - 1).coerceAtLeast(0)

    val reconstructionValues = ArrayList<List<Double>>()
    fun append(path: List<List<Double>>): IntRange {
        if (path.isEmpty()) return IntRange.EMPTY
        val start =
            if (reconstructionValues.isEmpty()) {
                0
            } else {
                reconstructionValues.lastIndex
            }
        path.forEach { values ->
            if (reconstructionValues.lastOrNull() != values) reconstructionValues += values
        }
        return start..reconstructionValues.lastIndex
    }

    val reconstructionStart = innerMotionValues.lastOrNull() ?: outerMotionValues.last()
    append(densifyJointPath(listOf(reconstructionStart, outerMotionValues.first()), robot))
    val outerRange = append(outerMotionValues)
    val innerRange =
        if (innerTraceValues.isNotEmpty()) {
            append(densifyJointPath(listOf(outerMotionValues.last(), innerTraceValues.first()), robot))
            append(innerTraceValues)
        } else {
            IntRange.EMPTY
        }
    if (reconstructionValues.isEmpty()) reconstructionValues += home

    val approach = buildMotion(robot, approachValues)
    val outer =
        buildMotion(
            robot = robot,
            jointValues = outerMotionValues,
            traceStartIndex = 0,
            traceRegion = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
        )
    val inner =
        buildMotion(
            robot = robot,
            jointValues = innerMotionValues,
            traceStartIndex = innerTraceStart,
            traceRegion =
                if (innerTraceValues.isEmpty()) null else WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE
        )
    val reconstruction = buildMotion(robot, reconstructionValues)
    val fill = buildMotion(robot, listOf(reconstructionValues.last()))

    fun anchorsFor(range: IntRange): List<RevealAnchor> {
        if (range.isEmpty() || reconstruction.endEffectorPoints.isEmpty()) return emptyList()
        val denominator = reconstruction.endEffectorPoints.lastIndex.coerceAtLeast(1).toDouble()
        val valid = range.first.coerceAtLeast(0)..range.last.coerceAtMost(reconstruction.endEffectorPoints.lastIndex)
        val stride = ((valid.last - valid.first + 1) / MAXIMUM_REVEAL_ANCHORS).coerceAtLeast(1)
        return valid.filterIndexed { index, _ -> index % stride == 0 }
            .take(MAXIMUM_REVEAL_ANCHORS)
            .map { index -> RevealAnchor(reconstruction.endEffectorPoints[index], index / denominator) }
            .let { anchors ->
                if (anchors.lastOrNull()?.point == reconstruction.endEffectorPoints[valid.last]) anchors
                else anchors + RevealAnchor(reconstruction.endEffectorPoints[valid.last], valid.last / denominator)
            }
    }

    return GeneralConstructionPlan(
        motions =
            mapOf(
                WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS to approach,
                WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY to outer,
                WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY to inner,
                WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY to reconstruction,
                WorkspaceEnvelopeConstructionStage.FILL_SURFACE to fill
            ),
        revealAnchors =
            mapOf(
                WorkspaceSurfaceRegion.OBSERVED_REACHABLE to anchorsFor(outerRange),
                WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE to anchorsFor(innerRange)
            )
    )
}

private fun buildMotion(
    robot: RobotDefinition,
    jointValues: List<List<Double>>,
    traceStartIndex: Int = 0,
    traceRegion: WorkspaceSurfaceRegion? = null
): WorkspaceConstructionMotion {
    val output = MutableFKPositionOnlyResult()
    val solver = ForwardKinematicsSolver()
    val points =
        jointValues.map { values ->
            check(solver.solvePositionOnlyInto(robot, values, output)) {
                "A validated workspace construction state failed canonical forward kinematics."
            }
            Vec3(output.x, output.y, output.z)
        }
    return WorkspaceConstructionMotion(
        jointValues = jointValues,
        endEffectorPoints = points,
        traceStartIndex = traceStartIndex,
        traceRegion = traceRegion
    )
}

private fun orderedBoundaryRepresentatives(
    candidates: List<RobotWorkspaceSample>,
    robot: RobotDefinition,
    initialJointValues: List<Double>,
    conservativeRadiusMeters: Double,
    maximumPoints: Int
): List<RobotWorkspaceSample> {
    if (candidates.isEmpty()) return emptyList()
    val unique = candidates.distinctBy(RobotWorkspaceSample::sequenceIndex)
    val seed =
        unique.minWithOrNull(
            compareBy<RobotWorkspaceSample> { sample ->
                normalizedJointDistance(initialJointValues, sample.jointValues, robot)
            }.thenBy(RobotWorkspaceSample::sequenceIndex)
        ) ?: return emptyList()
    val selected = mutableListOf(seed)
    val remaining = unique.filterTo(mutableListOf()) { it.sequenceIndex != seed.sequenceIndex }
    while (selected.size < maximumPoints && remaining.isNotEmpty()) {
        val next =
            remaining.maxWithOrNull(
                compareBy<RobotWorkspaceSample> { candidate ->
                    selected.minOf { chosen -> (candidate.endEffector - chosen.endEffector).norm() }
                }.thenByDescending { candidate -> candidate.sequenceIndex }
            ) ?: break
        selected += next
        remaining.remove(next)
    }

    val route = ArrayList<RobotWorkspaceSample>(selected.size)
    val unvisited = selected.toMutableList()
    var currentJointValues = initialJointValues
    var currentPoint = seed.endEffector
    while (unvisited.isNotEmpty()) {
        val next =
            unvisited.minWithOrNull(
                compareBy<RobotWorkspaceSample> { candidate ->
                    val jointTravel = normalizedJointDistance(currentJointValues, candidate.jointValues, robot)
                    val cartesianTravel =
                        (candidate.endEffector - currentPoint).norm() /
                            conservativeRadiusMeters.coerceAtLeast(DIRECTION_EPSILON)
                    jointTravel + cartesianTravel * CARTESIAN_ROUTE_WEIGHT
                }.thenBy(RobotWorkspaceSample::sequenceIndex)
            ) ?: break
        route += next
        unvisited.remove(next)
        currentJointValues = next.jointValues
        currentPoint = next.endEffector
    }
    return improveOpenRoute(route, robot, conservativeRadiusMeters)
}

private fun improveOpenRoute(
    route: List<RobotWorkspaceSample>,
    robot: RobotDefinition,
    conservativeRadiusMeters: Double
): List<RobotWorkspaceSample> {
    if (route.size < 4) return route
    val improved = route.toMutableList()
    fun cost(first: RobotWorkspaceSample, second: RobotWorkspaceSample): Double =
        normalizedJointDistance(first.jointValues, second.jointValues, robot) +
            (first.endEffector - second.endEffector).norm() /
                conservativeRadiusMeters.coerceAtLeast(DIRECTION_EPSILON) * CARTESIAN_ROUTE_WEIGHT
    repeat(ROUTE_IMPROVEMENT_PASSES) {
        var changed = false
        for (left in 1 until improved.lastIndex - 1) {
            for (right in left + 1 until improved.lastIndex) {
                val before = cost(improved[left - 1], improved[left]) + cost(improved[right], improved[right + 1])
                val after = cost(improved[left - 1], improved[right]) + cost(improved[left], improved[right + 1])
                if (after + ROUTE_IMPROVEMENT_EPSILON < before) {
                    improved.subList(left, right + 1).reverse()
                    changed = true
                }
            }
        }
        if (!changed) return improved
    }
    return improved
}

private fun densifyJointPath(
    waypoints: List<List<Double>>,
    robot: RobotDefinition
): List<List<Double>> {
    if (waypoints.isEmpty()) return emptyList()
    if (waypoints.size == 1) return waypoints
    return buildList {
        add(waypoints.first())
        waypoints.zipWithNext().forEach { (start, end) ->
            val maximumNormalizedDelta =
                start.indices.maxOf { index ->
                    normalizedSingleJointDelta(start[index], end[index], robot, index)
                }
            val steps = ceil(maximumNormalizedDelta / MAXIMUM_NORMALIZED_JOINT_STEP).toInt().coerceAtLeast(1)
            for (step in 1..steps) {
                interpolateJointPath(listOf(start, end), step.toDouble() / steps, robot)?.let(::add)
            }
        }
    }
}

private fun normalizedSingleJointDelta(
    first: Double,
    second: Double,
    robot: RobotDefinition,
    jointIndex: Int
): Double {
    val joint = robot.joints[jointIndex]
    val span = (joint.maxValue - joint.minValue).coerceAtLeast(DIRECTION_EPSILON)
    val direct = abs(second - first)
    val distance =
        if (joint.type == JointType.REVOLUTE && span >= TWO_PI - FULL_ROTATION_TOLERANCE) {
            minOf(direct, abs(direct - TWO_PI), abs(direct + TWO_PI))
        } else {
            direct
        }
    return distance / span
}

private fun concatenateJointPaths(vararg paths: List<List<Double>>): List<List<Double>> =
    buildList {
        paths.forEach { path ->
            path.forEach { values -> if (lastOrNull() != values) add(values) }
        }
    }

private fun assignGeneralRevealFractions(
    triangles: List<WorkspaceEnvelopeTriangle>,
    plan: GeneralConstructionPlan
): List<WorkspaceEnvelopeTriangle> =
    triangles.map { triangle ->
        val anchors = plan.revealAnchors[triangle.region].orEmpty()
        if (anchors.isEmpty()) {
            triangle
        } else {
            val nearest = anchors.minByOrNull { anchor -> (triangle.center - anchor.point).norm() }
            triangle.copy(revealFraction = nearest?.revealFraction ?: triangle.revealFraction)
        }
    }

/**
 * Finds only topologically enclosed unobserved cells.
 *
 * Every candidate touching the outside of the sampled conservative domain is flood-filled as
 * exterior. What remains is surrounded by observed reachability and can safely be displayed as a
 * candidate internal cavity. Open annular/cylindrical cavities are handled separately by the
 * axisymmetric radial-profile strategy; using radial heuristics here produced false red islands
 * outside prismatic workspaces.
 */
internal fun classifyInternalDeadSpaceVoxels(study: RobotWorkspaceStudy): List<RobotWorkspaceVoxel> {
    val candidates = study.voxels.filter { it.classification == WorkspaceVoxelClass.UNOBSERVED_CANDIDATE }
    if (candidates.isEmpty()) return emptyList()

    val domain = study.voxels.mapTo(HashSet(study.voxels.size * 2)) { voxel ->
        ImplicitGridPoint(voxel.xIndex, voxel.yIndex, voxel.zIndex)
    }
    val candidateByKey = candidates.associateBy { voxel ->
        ImplicitGridPoint(voxel.xIndex, voxel.yIndex, voxel.zIndex)
    }
    val exterior = HashSet<ImplicitGridPoint>(candidateByKey.size * 2)
    val frontier = java.util.ArrayDeque<ImplicitGridPoint>()
    candidateByKey.keys.forEach { key ->
        if (FACE_NEIGHBOUR_OFFSETS.any { delta -> key + delta !in domain }) {
            exterior += key
            frontier += key
        }
    }
    while (frontier.isNotEmpty()) {
        val current = frontier.removeFirst()
        FACE_NEIGHBOUR_OFFSETS.forEach { delta ->
            val neighbour = current + delta
            if (neighbour in candidateByKey && exterior.add(neighbour)) frontier += neighbour
        }
    }
    return candidates.filterNot { voxel ->
        ImplicitGridPoint(voxel.xIndex, voxel.yIndex, voxel.zIndex) in exterior
    }
}

internal fun workspaceEnvelopeConstructionStage(progress: Double): WorkspaceEnvelopeConstructionStage {
    val bounded = progress.coerceIn(0.0, 1.0)
    return when {
        bounded < FREEZE_STAGE_END -> WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS
        bounded < OUTER_STAGE_END -> WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY
        bounded < INNER_STAGE_END -> WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY
        bounded < SWEEP_STAGE_END -> WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY
        else -> WorkspaceEnvelopeConstructionStage.FILL_SURFACE
    }
}

internal fun workspaceEnvelopeStageFraction(progress: Double): Double {
    val bounded = progress.coerceIn(0.0, 1.0)
    val (start, end) =
        when (workspaceEnvelopeConstructionStage(bounded)) {
            WorkspaceEnvelopeConstructionStage.FREEZE_REFERENCE_AXIS -> 0.0 to FREEZE_STAGE_END
            WorkspaceEnvelopeConstructionStage.TRACE_OUTER_BOUNDARY -> FREEZE_STAGE_END to OUTER_STAGE_END
            WorkspaceEnvelopeConstructionStage.TRACE_INNER_BOUNDARY -> OUTER_STAGE_END to INNER_STAGE_END
            WorkspaceEnvelopeConstructionStage.SWEEP_BOUNDARY -> INNER_STAGE_END to SWEEP_STAGE_END
            WorkspaceEnvelopeConstructionStage.FILL_SURFACE -> SWEEP_STAGE_END to 1.0
        }
    return ((bounded - start) / (end - start)).coerceIn(0.0, 1.0)
}

internal fun constructionJointValues(
    construction: RobotWorkspaceEnvelopeConstruction,
    robot: RobotDefinition,
    progress: Double
): List<Double>? {
    if (robot.joints.isEmpty()) return null
    val stage = workspaceEnvelopeConstructionStage(progress)
    val stageFraction = workspaceEnvelopeStageFraction(progress)
    val motion = construction.stageMotion(stage) ?: return null
    val motionFraction =
        if (stage == WorkspaceEnvelopeConstructionStage.FILL_SURFACE) 1.0 else stageFraction
    val values = interpolateJointPath(motion.jointValues, motionFraction, robot) ?: return null
    return values.mapIndexed { index, value ->
        val joint = robot.joints[index]
        value.coerceIn(joint.minValue, joint.maxValue)
    }
}

internal fun RobotWorkspaceEnvelopeConstruction.stageMotion(
    stage: WorkspaceEnvelopeConstructionStage
): WorkspaceConstructionMotion? = stageMotions[stage]

internal fun envelopePoint(point: WorkspaceRadialPoint, angleRadians: Double): Vec3 =
    Vec3(
        x = point.radiusMeters * cos(angleRadians),
        y = point.radiusMeters * sin(angleRadians),
        z = point.heightMeters
    )

private data class RadialProfile(
    val outer: List<WorkspaceBoundaryPose>,
    val inner: List<WorkspaceBoundaryPose>
)

private fun buildRadialProfile(study: RobotWorkspaceStudy): RadialProfile? {
    if (study.samples.isEmpty()) return null
    val minimumZ = study.samples.minOf { it.endEffector.z }
    val maximumZ = study.samples.maxOf { it.endEffector.z }
    val zRange = maximumZ - minimumZ
    val requestedBins = study.config.voxelResolution.coerceIn(MINIMUM_PROFILE_BINS, MAXIMUM_PROFILE_BINS)
    val binCount = if (zRange <= study.voxelCellSizeMeters * 0.25) 1 else requestedBins
    val bins = Array(binCount) { mutableListOf<RobotWorkspaceSample>() }
    study.samples.forEach { sample ->
        val bin =
            if (binCount == 1) {
                0
            } else {
                floor((sample.endEffector.z - minimumZ) / zRange * binCount)
                    .toInt()
                    .coerceIn(0, binCount - 1)
            }
        bins[bin] += sample
    }
    val populated = bins.filter(List<RobotWorkspaceSample>::isNotEmpty)
    if (populated.isEmpty()) return null
    val outer = selectContinuousBoundary(populated, study.robot, chooseOuter = true)
    val inner = selectContinuousBoundary(populated, study.robot, chooseOuter = false)
    return RadialProfile(outer = outer, inner = inner)
}

private fun selectContinuousBoundary(
    populatedBins: List<List<RobotWorkspaceSample>>,
    robot: RobotDefinition,
    chooseOuter: Boolean
): List<WorkspaceBoundaryPose> {
    var previous: RobotWorkspaceSample? = null
    return populatedBins.map { samples ->
        val extreme =
            if (chooseOuter) samples.maxOf(::radialDistance) else samples.minOf(::radialDistance)
        val radialSpan = samples.maxOf(::radialDistance) - samples.minOf(::radialDistance)
        val tolerance = max(radialSpan * CONTINUOUS_BOUNDARY_EXTREME_FRACTION, CONTINUOUS_BOUNDARY_EPSILON)
        val nearExtreme =
            samples.filter { sample ->
                val difference = abs(radialDistance(sample) - extreme)
                difference <= tolerance
            }
        val selected =
            previous?.let { prior ->
                nearExtreme.minWithOrNull(
                    compareBy<RobotWorkspaceSample> { sample ->
                        normalizedJointDistance(prior.jointValues, sample.jointValues, robot)
                    }.thenBy { sample -> abs(radialDistance(sample) - extreme) }
                )
            } ?: nearExtreme.minByOrNull { sample -> abs(radialDistance(sample) - extreme) }
            ?: if (chooseOuter) samples.maxBy(::radialDistance) else samples.minBy(::radialDistance)
        previous = selected
        selected.toBoundaryPose()
    }.sortedBy { it.heightMeters }
}

private fun normalizedJointDistance(
    first: List<Double>,
    second: List<Double>,
    robot: RobotDefinition
): Double =
    first.indices.sumOf { index ->
        val joint = robot.joints[index]
        val span = (joint.maxValue - joint.minValue).coerceAtLeast(DIRECTION_EPSILON)
        val direct = abs(second[index] - first[index])
        val distance =
            if (joint.type == JointType.REVOLUTE && span >= TWO_PI - FULL_ROTATION_TOLERANCE) {
                minOf(direct, abs(direct - TWO_PI), abs(direct + TWO_PI))
            } else {
                direct
            }
        distance / span
    }

private fun radialDistance(sample: RobotWorkspaceSample): Double = hypot(sample.endEffector.x, sample.endEffector.y)

private fun RobotWorkspaceSample.toBoundaryPose(): WorkspaceBoundaryPose =
    WorkspaceBoundaryPose(
        radiusMeters = radialDistance(this),
        heightMeters = endEffector.z,
        jointValues = jointValues
    )

private fun WorkspaceBoundaryPose.toRadialPoint(): WorkspaceRadialPoint =
    WorkspaceRadialPoint(radiusMeters = radiusMeters, heightMeters = heightMeters)

private fun smoothAndDensify(points: List<WorkspaceRadialPoint>): List<WorkspaceRadialPoint> {
    if (points.size <= 1) return points
    var smoothed = points
    repeat(PROFILE_SMOOTHING_PASSES) {
        val source = smoothed
        smoothed =
            source.mapIndexed { index, point ->
                val previous = source[(index - 1).coerceAtLeast(0)]
                val next = source[(index + 1).coerceAtMost(source.lastIndex)]
                WorkspaceRadialPoint(
                    radiusMeters = (previous.radiusMeters + point.radiusMeters * 2.0 + next.radiusMeters) / 4.0,
                    heightMeters = point.heightMeters
                )
            }
        }
    return buildList {
        smoothed.zipWithNext().forEachIndexed { index, (start, end) ->
            if (index == 0) add(start)
            for (step in 1..PROFILE_INTERPOLATION_STEPS) {
                val fraction = step.toDouble() / PROFILE_INTERPOLATION_STEPS
                add(
                    WorkspaceRadialPoint(
                        radiusMeters = start.radiusMeters + (end.radiusMeters - start.radiusMeters) * fraction,
                        heightMeters = start.heightMeters + (end.heightMeters - start.heightMeters) * fraction
                    )
                )
            }
        }
    }
}

private fun buildSurfaceOfRevolution(
    outerCurve: List<WorkspaceRadialPoint>,
    innerCurve: List<WorkspaceRadialPoint>,
    sweepStartRadians: Double,
    sweepEndRadians: Double
): List<WorkspaceEnvelopeTriangle> {
    if (outerCurve.isEmpty() || innerCurve.isEmpty()) return emptyList()
    val sweepSpan = max(sweepEndRadians - sweepStartRadians, 1e-9)
    val angularSegments =
        ((sweepSpan / TWO_PI) * FULL_SWEEP_SEGMENTS)
            .toInt()
            .coerceIn(MINIMUM_SWEEP_SEGMENTS, FULL_SWEEP_SEGMENTS)
    val triangles = ArrayList<WorkspaceEnvelopeTriangle>()
    for (segment in 0 until angularSegments) {
        val firstAngle = sweepStartRadians + sweepSpan * segment / angularSegments
        val secondAngle = sweepStartRadians + sweepSpan * (segment + 1) / angularSegments
        val reveal = (segment + 1).toDouble() / angularSegments
        addRevolvedStrip(triangles, outerCurve, firstAngle, secondAngle, WorkspaceSurfaceRegion.OBSERVED_REACHABLE, reveal)
        addRevolvedStrip(triangles, innerCurve, firstAngle, secondAngle, WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE, reveal)
        addRadialCap(
            triangles,
            innerCurve.first(),
            outerCurve.first(),
            firstAngle,
            secondAngle,
            reveal
        )
        addDeadSpaceAxialCap(
            destination = triangles,
            boundary = innerCurve.first(),
            firstAngle = firstAngle,
            secondAngle = secondAngle,
            reveal = reveal
        )
        if (outerCurve.size > 1 || innerCurve.size > 1) {
            addRadialCap(
                triangles,
                innerCurve.last(),
                outerCurve.last(),
                firstAngle,
                secondAngle,
                reveal
            )
            addDeadSpaceAxialCap(
                destination = triangles,
                boundary = innerCurve.last(),
                firstAngle = secondAngle,
                secondAngle = firstAngle,
                reveal = reveal
            )
        }
    }
    return triangles.filter { it.normal.norm() > TRIANGLE_AREA_EPSILON }
}

private fun addRevolvedStrip(
    destination: MutableList<WorkspaceEnvelopeTriangle>,
    curve: List<WorkspaceRadialPoint>,
    firstAngle: Double,
    secondAngle: Double,
    region: WorkspaceSurfaceRegion,
    reveal: Double
) {
    curve.zipWithNext().forEach { (lower, upper) ->
        addQuadAsTriangles(
            destination,
            envelopePoint(lower, firstAngle),
            envelopePoint(lower, secondAngle),
            envelopePoint(upper, secondAngle),
            envelopePoint(upper, firstAngle),
            region,
            reveal
        )
    }
}

private fun addRadialCap(
    destination: MutableList<WorkspaceEnvelopeTriangle>,
    inner: WorkspaceRadialPoint,
    outer: WorkspaceRadialPoint,
    firstAngle: Double,
    secondAngle: Double,
    reveal: Double
) {
    addQuadAsTriangles(
        destination,
        envelopePoint(inner, firstAngle),
        envelopePoint(inner, secondAngle),
        envelopePoint(outer, secondAngle),
        envelopePoint(outer, firstAngle),
        WorkspaceSurfaceRegion.OBSERVED_REACHABLE,
        reveal
    )
}

private fun addDeadSpaceAxialCap(
    destination: MutableList<WorkspaceEnvelopeTriangle>,
    boundary: WorkspaceRadialPoint,
    firstAngle: Double,
    secondAngle: Double,
    reveal: Double
) {
    val axis = Vec3(0.0, 0.0, boundary.heightMeters)
    destination +=
        WorkspaceEnvelopeTriangle(
            a = axis,
            b = envelopePoint(boundary, secondAngle),
            c = envelopePoint(boundary, firstAngle),
            region = WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE,
            revealFraction = reveal
        )
}

private fun addQuadAsTriangles(
    destination: MutableList<WorkspaceEnvelopeTriangle>,
    a: Vec3,
    b: Vec3,
    c: Vec3,
    d: Vec3,
    region: WorkspaceSurfaceRegion,
    reveal: Double
) {
    destination += WorkspaceEnvelopeTriangle(a, b, c, region, reveal)
    destination += WorkspaceEnvelopeTriangle(a, c, d, region, reveal)
}

private data class ImplicitGridPoint(val x: Int, val y: Int, val z: Int) {
    operator fun plus(other: ImplicitGridPoint): ImplicitGridPoint =
        ImplicitGridPoint(x + other.x, y + other.y, z + other.z)
}

private fun buildImplicitBoundaryTriangles(
    voxels: List<RobotWorkspaceVoxel>,
    study: RobotWorkspaceStudy,
    region: WorkspaceSurfaceRegion
): List<WorkspaceEnvelopeTriangle> {
    if (voxels.isEmpty()) return emptyList()
    val resolution = study.config.voxelResolution
    val occupied = voxels.mapTo(HashSet(voxels.size * 2)) { ImplicitGridPoint(it.xIndex, it.yIndex, it.zIndex) }
    val triangles = ArrayList<WorkspaceEnvelopeTriangle>()
    for (x in -1 until resolution) {
        for (y in -1 until resolution) {
            for (z in -1 until resolution) {
                val cube = cubeCorners(x, y, z)
                CUBE_TETRAHEDRA.forEach { tetrahedron ->
                    polygoniseTetrahedron(
                        points = tetrahedron.map(cube::get),
                        occupied = occupied,
                        worldPoint = { point -> implicitWorldPoint(point, study) },
                        region = region,
                        destination = triangles
                    )
                }
            }
        }
    }
    if (triangles.size <= MAXIMUM_IMPLICIT_TRIANGLES_PER_REGION) return triangles
    val stride = (triangles.size + MAXIMUM_IMPLICIT_TRIANGLES_PER_REGION - 1) / MAXIMUM_IMPLICIT_TRIANGLES_PER_REGION
    return triangles.filterIndexed { index, _ -> index % stride == 0 }.take(MAXIMUM_IMPLICIT_TRIANGLES_PER_REGION)
}

private fun polygoniseTetrahedron(
    points: List<ImplicitGridPoint>,
    occupied: Set<ImplicitGridPoint>,
    worldPoint: (ImplicitGridPoint) -> Vec3,
    region: WorkspaceSurfaceRegion,
    destination: MutableList<WorkspaceEnvelopeTriangle>
) {
    val inside = points.filter { it in occupied }
    val outside = points.filterNot { it in occupied }
    if (inside.isEmpty() || outside.isEmpty()) return

    fun midpoint(first: ImplicitGridPoint, second: ImplicitGridPoint): Vec3 =
        (worldPoint(first) + worldPoint(second)) / 2.0

    val created =
        when (inside.size) {
            1 -> {
                val anchor = inside.single()
                listOf(
                    WorkspaceEnvelopeTriangle(
                        midpoint(anchor, outside[0]),
                        midpoint(anchor, outside[1]),
                        midpoint(anchor, outside[2]),
                        region,
                        revealFractionFor(midpoint(anchor, outside[0]))
                    )
                )
            }
            3 -> {
                val anchor = outside.single()
                listOf(
                    WorkspaceEnvelopeTriangle(
                        midpoint(anchor, inside[0]),
                        midpoint(anchor, inside[2]),
                        midpoint(anchor, inside[1]),
                        region,
                        revealFractionFor(midpoint(anchor, inside[0]))
                    )
                )
            }
            2 -> {
                val p00 = midpoint(inside[0], outside[0])
                val p01 = midpoint(inside[0], outside[1])
                val p10 = midpoint(inside[1], outside[0])
                val p11 = midpoint(inside[1], outside[1])
                listOf(
                    WorkspaceEnvelopeTriangle(p00, p01, p10, region, revealFractionFor(p00)),
                    WorkspaceEnvelopeTriangle(p01, p11, p10, region, revealFractionFor(p01))
                )
            }
            else -> emptyList()
        }
    destination += created.filter { it.normal.norm() > TRIANGLE_AREA_EPSILON }
}

private fun cubeCorners(x: Int, y: Int, z: Int): List<ImplicitGridPoint> =
    listOf(
        ImplicitGridPoint(x, y, z),
        ImplicitGridPoint(x + 1, y, z),
        ImplicitGridPoint(x + 1, y + 1, z),
        ImplicitGridPoint(x, y + 1, z),
        ImplicitGridPoint(x, y, z + 1),
        ImplicitGridPoint(x + 1, y, z + 1),
        ImplicitGridPoint(x + 1, y + 1, z + 1),
        ImplicitGridPoint(x, y + 1, z + 1)
    )

private fun implicitWorldPoint(point: ImplicitGridPoint, study: RobotWorkspaceStudy): Vec3 {
    val minimum = -study.conservativeRadiusMeters
    val cell = study.voxelCellSizeMeters
    return Vec3(
        minimum + (point.x + 0.5) * cell,
        minimum + (point.y + 0.5) * cell,
        minimum + (point.z + 0.5) * cell
    )
}

private fun revealFractionFor(point: Vec3): Double =
    ((atan2(point.y, point.x) + PI) / TWO_PI).coerceIn(0.0, 1.0)

private fun interpolateJointPath(
    path: List<List<Double>>,
    fraction: Double,
    robot: RobotDefinition
): List<Double>? {
    if (path.isEmpty()) return null
    if (path.size == 1) return path.single()
    val position = fraction.coerceIn(0.0, 1.0) * path.lastIndex
    val leftIndex = floor(position).toInt().coerceIn(0, path.lastIndex)
    val rightIndex = (leftIndex + 1).coerceAtMost(path.lastIndex)
    val localFraction = position - leftIndex
    return path[leftIndex].indices.map { jointIndex ->
        val left = path[leftIndex][jointIndex]
        val right = path[rightIndex][jointIndex]
        val joint = robot.joints[jointIndex]
        val span = joint.maxValue - joint.minValue
        val directDelta = right - left
        val delta =
            if (joint.type == JointType.REVOLUTE && span >= TWO_PI - FULL_ROTATION_TOLERANCE) {
                when {
                    directDelta > PI -> directDelta - TWO_PI
                    directDelta < -PI -> directDelta + TWO_PI
                    else -> directDelta
                }
            } else {
                directDelta
            }
        wrapToJointRange(left + delta * localFraction, joint.minValue, joint.maxValue, joint.type)
    }
}

private fun wrapToJointRange(value: Double, minimum: Double, maximum: Double, type: JointType): Double {
    if (type != JointType.REVOLUTE || maximum - minimum < TWO_PI - FULL_ROTATION_TOLERANCE) return value
    var wrapped = value
    while (wrapped < minimum) wrapped += TWO_PI
    while (wrapped > maximum) wrapped -= TWO_PI
    return wrapped.coerceIn(minimum, maximum)
}

private val CUBE_TETRAHEDRA =
    listOf(
        listOf(0, 5, 1, 6),
        listOf(0, 1, 2, 6),
        listOf(0, 2, 3, 6),
        listOf(0, 3, 7, 6),
        listOf(0, 7, 4, 6),
        listOf(0, 4, 5, 6)
    )

private val FACE_NEIGHBOUR_OFFSETS =
    listOf(
        ImplicitGridPoint(1, 0, 0),
        ImplicitGridPoint(-1, 0, 0),
        ImplicitGridPoint(0, 1, 0),
        ImplicitGridPoint(0, -1, 0),
        ImplicitGridPoint(0, 0, 1),
        ImplicitGridPoint(0, 0, -1)
    )

private const val FREEZE_STAGE_END = 0.10
private const val OUTER_STAGE_END = 0.36
private const val INNER_STAGE_END = 0.56
private const val SWEEP_STAGE_END = 0.92
private const val TWO_PI = 2.0 * PI
private const val FULL_ROTATION_TOLERANCE = 1e-6
private const val MINIMUM_PROFILE_BINS = 12
private const val MAXIMUM_PROFILE_BINS = 32
private const val PROFILE_INTERPOLATION_STEPS = 3
private const val PROFILE_SMOOTHING_PASSES = 3
private const val FULL_SWEEP_SEGMENTS = 72
private const val MINIMUM_SWEEP_SEGMENTS = 12
private const val AXISYMMETRIC_SWEEP_SLICES = 36
private const val MAXIMUM_GENERAL_OUTER_WAYPOINTS = 72
private const val MAXIMUM_GENERAL_INNER_WAYPOINTS = 48
private const val MAXIMUM_REVEAL_ANCHORS = 128
private const val MAXIMUM_NORMALIZED_JOINT_STEP = 0.035
private const val CARTESIAN_ROUTE_WEIGHT = 0.40
private const val ROUTE_IMPROVEMENT_PASSES = 3
private const val ROUTE_IMPROVEMENT_EPSILON = 1e-10
private const val MAXIMUM_IMPLICIT_TRIANGLES_PER_REGION = 45_000
private const val TRIANGLE_AREA_EPSILON = 1e-12
private const val INTERNAL_ENVELOPE_MARGIN_CELLS = 0.55
private const val AXISYMMETRIC_HEIGHT_MARGIN_CELLS = 0.75
private const val DIRECTION_EPSILON = 1e-12
private const val CONTINUOUS_BOUNDARY_EXTREME_FRACTION = 0.035
private const val CONTINUOUS_BOUNDARY_EPSILON = 1e-9
