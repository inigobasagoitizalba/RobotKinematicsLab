package com.robotkinematicslab.mobile.ui.navigation

/** A decoded navigation step. Invalid persisted route names are discarded during recovery. */
internal data class ProjectRouteBackResult(
    val destination: ProjectRoute?,
    val remainingRouteNames: ArrayList<String>,
    val discardedInvalidEntries: Int
)

internal fun appendProjectRoute(
    savedRouteNames: List<String>,
    current: ProjectRoute
): ArrayList<String> =
    ArrayList(
        savedRouteNames.mapNotNull(::projectRouteOrNull).map(ProjectRoute::name) + current.name
    )

internal fun popProjectRoute(savedRouteNames: List<String>): ProjectRouteBackResult {
    val decoded = savedRouteNames.mapNotNull(::projectRouteOrNull)
    return ProjectRouteBackResult(
        destination = decoded.lastOrNull(),
        remainingRouteNames = ArrayList(decoded.dropLast(1).map(ProjectRoute::name)),
        discardedInvalidEntries = savedRouteNames.size - decoded.size
    )
}

internal fun projectRouteOrNull(name: String): ProjectRoute? =
    runCatching { ProjectRoute.valueOf(name) }.getOrNull()
