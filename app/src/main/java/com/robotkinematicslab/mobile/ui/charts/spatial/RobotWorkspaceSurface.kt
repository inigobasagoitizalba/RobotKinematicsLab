package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceStudy
import com.robotkinematicslab.mobile.workspace.analysis.RobotWorkspaceVoxel
import com.robotkinematicslab.mobile.workspace.analysis.WorkspaceVoxelClass

enum class RobotWorkspaceRenderMode {
    POINT_CLOUD,
    SURFACE_SHELL,
    ANIMATED_CONSTRUCTION
}

internal enum class WorkspaceSurfaceRegion {
    OBSERVED_REACHABLE,
    UNOBSERVED_CANDIDATE
}

internal data class WorkspaceSurfaceFace(
    val sourceVoxelId: String,
    val region: WorkspaceSurfaceRegion,
    val corners: List<Vec3>,
    val center: Vec3
)

internal fun buildWorkspaceSurfaceFaces(
    study: RobotWorkspaceStudy,
    revealedSampleCount: Int,
    options: RobotWorkspaceDisplayOptions,
    maximumFaces: Int = MAXIMUM_WORKSPACE_SURFACE_FACES
): List<WorkspaceSurfaceFace> {
    require(maximumFaces > 0)
    val revealIndex = revealedSampleCount.coerceAtLeast(0) - 1
    val reachable =
        study.voxels.filter { voxel ->
            val classVisible =
                when (voxel.classification) {
                    WorkspaceVoxelClass.OBSERVED_REACHABLE_CORE -> options.showReachableCore
                    WorkspaceVoxelClass.OBSERVED_REACHABLE_BOUNDARY -> options.showReachableBoundary
                    WorkspaceVoxelClass.UNOBSERVED_CANDIDATE -> false
                }
            classVisible && (voxel.firstObservedSampleIndex ?: Int.MAX_VALUE) <= revealIndex
        }
    val unobserved =
        if (options.showUnobservedCandidates && revealedSampleCount >= study.samples.size) {
            study.voxels.filter { it.classification == WorkspaceVoxelClass.UNOBSERVED_CANDIDATE }
        } else {
            emptyList()
        }

    val faces =
        extractVoxelBoundaryFaces(
            voxels = reachable,
            cellSizeMeters = study.voxelCellSizeMeters,
            region = WorkspaceSurfaceRegion.OBSERVED_REACHABLE
        ) +
            extractVoxelBoundaryFaces(
                voxels = unobserved,
                cellSizeMeters = study.voxelCellSizeMeters,
                region = WorkspaceSurfaceRegion.UNOBSERVED_CANDIDATE
            )
    if (faces.size <= maximumFaces) return faces
    val stride = (faces.size + maximumFaces - 1) / maximumFaces
    return faces.filterIndexed { index, _ -> index % stride == 0 }.take(maximumFaces)
}

/** Extracts only exposed voxel faces. Shared interior faces are deliberately omitted. */
internal fun extractVoxelBoundaryFaces(
    voxels: List<RobotWorkspaceVoxel>,
    cellSizeMeters: Double,
    region: WorkspaceSurfaceRegion
): List<WorkspaceSurfaceFace> {
    require(cellSizeMeters.isFinite() && cellSizeMeters > 0.0)
    if (voxels.isEmpty()) return emptyList()
    val occupied = voxels.mapTo(HashSet(voxels.size * 2), RobotWorkspaceVoxel::gridKey)
    val half = cellSizeMeters / 2.0
    val faces = ArrayList<WorkspaceSurfaceFace>()
    voxels.sortedWith(compareBy<RobotWorkspaceVoxel> { it.xIndex }.thenBy { it.yIndex }.thenBy { it.zIndex })
        .forEach { voxel ->
            FACE_DIRECTIONS.forEach { direction ->
                val neighbour =
                    WorkspaceGridKey(
                        voxel.xIndex + direction.dx,
                        voxel.yIndex + direction.dy,
                        voxel.zIndex + direction.dz
                    )
                if (neighbour !in occupied) {
                    val corners = direction.corners(voxel.center, half)
                    faces +=
                        WorkspaceSurfaceFace(
                            sourceVoxelId = voxel.id,
                            region = region,
                            corners = corners,
                            center = corners.reduce { accumulator, point -> accumulator + point } / corners.size.toDouble()
                        )
                }
            }
        }
    return faces
}

private data class WorkspaceGridKey(val x: Int, val y: Int, val z: Int)

private fun RobotWorkspaceVoxel.gridKey(): WorkspaceGridKey = WorkspaceGridKey(xIndex, yIndex, zIndex)

private data class FaceDirection(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val corners: (Vec3, Double) -> List<Vec3>
)

private val FACE_DIRECTIONS =
    listOf(
        FaceDirection(1, 0, 0) { c, h ->
            listOf(Vec3(c.x + h, c.y - h, c.z - h), Vec3(c.x + h, c.y + h, c.z - h), Vec3(c.x + h, c.y + h, c.z + h), Vec3(c.x + h, c.y - h, c.z + h))
        },
        FaceDirection(-1, 0, 0) { c, h ->
            listOf(Vec3(c.x - h, c.y - h, c.z - h), Vec3(c.x - h, c.y - h, c.z + h), Vec3(c.x - h, c.y + h, c.z + h), Vec3(c.x - h, c.y + h, c.z - h))
        },
        FaceDirection(0, 1, 0) { c, h ->
            listOf(Vec3(c.x - h, c.y + h, c.z - h), Vec3(c.x - h, c.y + h, c.z + h), Vec3(c.x + h, c.y + h, c.z + h), Vec3(c.x + h, c.y + h, c.z - h))
        },
        FaceDirection(0, -1, 0) { c, h ->
            listOf(Vec3(c.x - h, c.y - h, c.z - h), Vec3(c.x + h, c.y - h, c.z - h), Vec3(c.x + h, c.y - h, c.z + h), Vec3(c.x - h, c.y - h, c.z + h))
        },
        FaceDirection(0, 0, 1) { c, h ->
            listOf(Vec3(c.x - h, c.y - h, c.z + h), Vec3(c.x + h, c.y - h, c.z + h), Vec3(c.x + h, c.y + h, c.z + h), Vec3(c.x - h, c.y + h, c.z + h))
        },
        FaceDirection(0, 0, -1) { c, h ->
            listOf(Vec3(c.x - h, c.y - h, c.z - h), Vec3(c.x - h, c.y + h, c.z - h), Vec3(c.x + h, c.y + h, c.z - h), Vec3(c.x + h, c.y - h, c.z - h))
        }
    )

private const val MAXIMUM_WORKSPACE_SURFACE_FACES = 9_000
