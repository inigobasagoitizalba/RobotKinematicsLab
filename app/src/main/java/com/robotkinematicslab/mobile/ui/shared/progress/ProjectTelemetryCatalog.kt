package com.robotkinematicslab.mobile.ui.shared.progress

import android.content.Context
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.io.File

internal data class ProjectTelemetrySession(
    val project: ResearchProject,
    val session: StoredTelemetrySession
) {
    val selectionKey: String = "${project.id}:${session.id}"
}

/**
 * Read-only index of diagram telemetry across isolated research projects.
 *
 * Each session is loaded through its own project root. No project is activated and no evidence is
 * copied, rewritten or merged on disk merely to compare it.
 */
internal class ProjectTelemetryCatalog(
    private val projectRepository: ResearchProjectRepository
) {
    constructor(context: Context) : this(ResearchProjectRepository(context))

    fun listSessions(): List<ProjectTelemetrySession> =
        projectRepository.listProjects()
            .flatMap { project ->
                TelemetrySessionRepository(telemetryRoot(project))
                    .listSessions()
                    .map { session -> ProjectTelemetrySession(project, session) }
            }
            .sortedWith(
                compareByDescending<ProjectTelemetrySession> { it.session.createdAtEpochMillis }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.project.name }
                    .thenBy { it.session.id }
            )

    private fun telemetryRoot(project: ResearchProject): File =
        File(projectRepository.projectRoot(project), TELEMETRY_RELATIVE_PATH)

    private companion object {
        const val TELEMETRY_RELATIVE_PATH = "sessions/telemetry"
    }
}
