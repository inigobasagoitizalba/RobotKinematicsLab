package com.robotkinematicslab.mobile.ui.shared.progress

import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticPerformanceSample
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressPhase
import com.robotkinematicslab.mobile.diagnostics.benchmark.DiagnosticProgressState
import com.robotkinematicslab.mobile.storage.project.ResearchProjectRepository
import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProjectTelemetryCatalogTest {

    @Test
    fun catalogReadsEveryProjectWithoutChangingTheActiveProject() {
        val root = Files.createTempDirectory("project-telemetry-catalog").toFile()
        val projects = ResearchProjectRepository(root) { 10_000L }
        val first = projects.createProject("Sawyer study", "Compare stable curves")
        val second = projects.createProject("KUKA study", "Independent evidence")
        val empty = projects.createProject("Empty study", "Must remain untouched by comparison browsing")
        projects.activateProject(first.id)

        saveSession(projects, first, "Sawyer loading")
        saveSession(projects, second, "KUKA loading")
        File(projects.projectRoot(second), "sessions/telemetry/corrupt/session.properties").apply {
            requireNotNull(parentFile).mkdirs()
            writeText("schemaVersion=999")
        }

        val entries = ProjectTelemetryCatalog(projects).listSessions()

        assertEquals(setOf(first.id, second.id), entries.map { it.project.id }.toSet())
        assertEquals(setOf("Sawyer loading", "KUKA loading"), entries.map { it.session.title }.toSet())
        assertEquals(first.id, projects.activeProject().id)
        assertEquals(entries.size, entries.map { it.selectionKey }.distinct().size)
        assertTrue(!File(projects.projectRoot(empty), "sessions/telemetry").exists())
    }

    @Test
    fun differentDiagramTitlesAreNotCollapsedWhenTheirSamplesMatch() {
        val root = Files.createTempDirectory("telemetry-title-fingerprint").toFile()
        val repository = TelemetrySessionRepository(root)
        val samples = samples()

        val first = repository.save("Study", "Chart preparation", "Overview", samples)
        val second = repository.save("Study", "Chart preparation", "Joint limits", samples)

        assertNotEquals(first.fingerprint, second.fingerprint)
        assertEquals(2, repository.listSessions().size)
    }

    private fun saveSession(
        repository: ResearchProjectRepository,
        project: com.robotkinematicslab.mobile.storage.project.ResearchProject,
        title: String
    ) {
        val telemetryRoot = File(repository.projectRoot(project), "sessions/telemetry")
        val saved = TelemetrySessionRepository(telemetryRoot).save(
            projectName = project.name,
            sessionType = "Diagnostic",
            title = title,
            samples = samples()
        )
        assertTrue(File(saved.directoryPath).isDirectory)
    }

    private fun samples(): List<DiagnosticPerformanceSample> =
        listOf(1, 2).map { index ->
            DiagnosticPerformanceSample.fromProgressState(
                sampleIndex = index,
                progressState =
                    DiagnosticProgressState(
                        isRunning = index < 2,
                        phase = if (index < 2) DiagnosticProgressPhase.AGGREGATING else DiagnosticProgressPhase.COMPLETED,
                        completedRuns = index,
                        totalRuns = 2,
                        runsPerSecond = 10.0,
                        estimatedSecondsRemaining = (2 - index).toDouble(),
                        elapsedSeconds = index.toDouble(),
                        message = "sample $index"
                    )
            )
        }
}
