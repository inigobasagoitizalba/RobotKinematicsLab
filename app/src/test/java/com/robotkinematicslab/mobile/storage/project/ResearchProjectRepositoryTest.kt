package com.robotkinematicslab.mobile.storage.project

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ResearchProjectRepositoryTest {

    @Test
    fun firstUse_indexesExistingWorkspaceWithoutMovingIt() {
        val root = Files.createTempDirectory("project-index-existing").toFile()
        val evidence = root.resolve("datasets/existing.csv").apply {
            parentFile?.mkdirs()
            writeText("existing evidence")
        }

        val repository = ResearchProjectRepository(root) { 1_000L }
        val project = repository.listProjects().single()

        assertEquals(ResearchProjectRepository.LEGACY_PROJECT_ID, project.id)
        assertTrue(project.usesLegacyWorkspace)
        assertEquals(root.absolutePath, repository.projectRoot(project).absolutePath)
        assertEquals("existing evidence", evidence.readText())
    }

    @Test
    fun newProjects_receiveDistinctPersistentRoots() {
        val root = Files.createTempDirectory("project-index-isolation").toFile()
        val firstRepository = ResearchProjectRepository(root) { 2_000L }

        val created = firstRepository.createProject("Sawyer tolerance study", "Compare micron-level feature profiles.")
        val reopened = ResearchProjectRepository(root) { 3_000L }
        val restored = reopened.listProjects().single { it.id == created.id }

        assertFalse(restored.usesLegacyWorkspace)
        assertNotEquals(root.absolutePath, reopened.projectRoot(restored).absolutePath)
        assertTrue(reopened.projectRoot(restored).isDirectory)
        assertEquals("Sawyer tolerance study", restored.name)
        assertEquals("Compare micron-level feature profiles.", restored.objective)
    }

    @Test
    fun activatingProject_changesOnlyTheSelectedWorkspacePointer() {
        val root = Files.createTempDirectory("project-index-active").toFile()
        val repository = ResearchProjectRepository(root) { 4_000L }
        val created = repository.createProject("Independent run", "Fresh evidence only")

        repository.activateProject(created.id)

        assertEquals(created.id, ResearchProjectRepository(root) { 5_000L }.activeProject().id)
        assertEquals(
            root.resolve("projects/${created.id}").absolutePath,
            repository.activeProjectRoot().absolutePath
        )
        assertTrue(root.resolve("project-index.properties").exists())
    }

    @Test
    fun corruptIndex_recoversExistingProjectsFromDurableMetadata() {
        val root = Files.createTempDirectory("project-index-corrupt").toFile()
        val original = ResearchProjectRepository(root) { 6_000L }
        val created = original.createProject("Recoverable study", "Keep scientific evidence reachable")
        root.resolve("projects/${created.id}/datasets/evidence.csv").apply {
            parentFile?.mkdirs()
            writeText("evidence")
        }
        root.resolve("project-index.properties").writeText("schemaVersion=999\nprojectIds=unknown")

        val recovered = ResearchProjectRepository(root) { 7_000L }

        assertEquals(ResearchProjectRepository.LEGACY_PROJECT_ID, recovered.activeProject().id)
        assertEquals(2, recovered.listProjects().size)
        assertEquals("Recoverable study", recovered.listProjects().single { it.id == created.id }.name)
        assertEquals(
            "evidence",
            root.resolve("projects/${created.id}/datasets/evidence.csv").readText()
        )
        assertTrue(root.resolve("project-index.properties.corrupt-7000").exists())
    }

    @Test
    fun unindexedProjectDirectory_isRecoveredInsteadOfSilentlyHidden() {
        val root = Files.createTempDirectory("project-index-orphan").toFile()
        ResearchProjectRepository(root) { 9_000L }
        val evidence = root.resolve("projects/orphan-study/datasets/evidence.csv").apply {
            parentFile?.mkdirs()
            writeText("orphan evidence")
        }

        val recovered = ResearchProjectRepository(root) { 10_000L }
        val project = recovered.listProjects().single { it.id == "orphan-study" }

        assertEquals("Recovered project (orphan-study)", project.name)
        assertEquals("orphan evidence", evidence.readText())
        assertTrue(root.resolve("projects/orphan-study/project.properties").isFile)
    }

    @Test
    fun separateRepositoryInstances_doNotOverwriteEachOthersProjects() {
        val root = Files.createTempDirectory("project-index-concurrent").toFile()
        val first = ResearchProjectRepository(root) { 11_000L }
        val second = ResearchProjectRepository(root) { 11_000L }

        val firstProject = first.createProject("First", "One")
        val secondProject = second.createProject("Second", "Two")
        val ids = ResearchProjectRepository(root) { 12_000L }.listProjects().map { it.id }.toSet()

        assertTrue(firstProject.id in ids)
        assertTrue(secondProject.id in ids)
        assertEquals(3, ids.size)
    }

    @Test
    fun staleLegacyTemporaryDirectoryCannotBlockProjectPublication() {
        val root = Files.createTempDirectory("project-index-stale-temp").toFile()
        val repository = ResearchProjectRepository(root) { 12_500L }
        val staleTemporaryEntry = root.resolve("project-index.properties.tmp")
        assertTrue(staleTemporaryEntry.mkdir())

        val created = repository.createProject("Recovered publication", "Ignore stale temporary state")
        val reopened = ResearchProjectRepository(root) { 12_501L }

        assertEquals(created, reopened.listProjects().single { it.id == created.id })
        assertTrue(staleTemporaryEntry.isDirectory)
    }

    @Test
    fun invalidProjectMetadata_isRejectedWithoutChangingIndex() {
        val root = Files.createTempDirectory("project-index-invalid").toFile()
        val repository = ResearchProjectRepository(root) { 8_000L }
        val before = root.resolve("project-index.properties").readText()

        val blankFailure = runCatching { repository.createProject("   ", "objective") }.exceptionOrNull()
        val longFailure = runCatching { repository.createProject("valid", "x".repeat(281)) }.exceptionOrNull()

        assertTrue(blankFailure is IllegalArgumentException)
        assertTrue(longFailure is IllegalArgumentException)
        assertEquals(before, root.resolve("project-index.properties").readText())
    }

    @Test
    fun unicodeProjectText_roundTripsButUnsupportedControlCharactersCannotReachStorage() {
        val root = Files.createTempDirectory("project-index-unicode").toFile()
        val repository = ResearchProjectRepository(root) { 13_000L }
        val created = repository.createProject(
            "Cinemática de 精密 robot 🤖",
            "Comparar precisión\ncon trazabilidad\tcompleta"
        )
        val reopened = ResearchProjectRepository(root) { 14_000L }
            .listProjects()
            .single { it.id == created.id }
        assertEquals("Cinemática de 精密 robot 🤖", reopened.name)
        assertEquals("Comparar precisión con trazabilidad completa", reopened.objective)
        val afterValidRoundTrip = root.resolve("project-index.properties").readBytes()

        assertThrows(IllegalArgumentException::class.java) {
            repository.createProject("Unsafe\u0000name", "Objective")
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.createProject("Unsafe\nname", "Objective")
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.updateProject(created.id, created.name, "Unsafe\u0000objective")
        }
        assertTrue(afterValidRoundTrip.contentEquals(root.resolve("project-index.properties").readBytes()))
    }

    @Test
    fun forgedProjectObjects_cannotResolveOutsideManagedStorageOrClaimLegacyRoot() {
        val root = Files.createTempDirectory("project-root-boundary").toFile()
        val repository = ResearchProjectRepository(root) { 15_000L }

        val traversal = ResearchProject(
            id = "../../outside",
            name = "Forged",
            objective = "",
            createdAtEpochMillis = 1L,
            updatedAtEpochMillis = 1L,
            usesLegacyWorkspace = false
        )
        val forgedLegacy = traversal.copy(id = "forged", usesLegacyWorkspace = true)

        assertThrows(IllegalArgumentException::class.java) { repository.projectRoot(traversal) }
        assertThrows(IllegalArgumentException::class.java) { repository.projectRoot(forgedLegacy) }
        assertFalse(requireNotNull(root.parentFile).resolve("outside").exists())
    }

    @Test
    fun unknownProjectActions_areRejectedWithoutChangingPersistentState() {
        val root = Files.createTempDirectory("project-index-unknown").toFile()
        val repository = ResearchProjectRepository(root) { 16_000L }
        val before = root.resolve("project-index.properties").readBytes()

        assertThrows(IllegalArgumentException::class.java) {
            repository.activateProject("does-not-exist")
        }
        assertThrows(IllegalArgumentException::class.java) {
            repository.updateProject("does-not-exist", "Name", "Objective")
        }

        assertTrue(before.contentEquals(root.resolve("project-index.properties").readBytes()))
        assertEquals(ResearchProjectRepository.LEGACY_PROJECT_ID, repository.activeProject().id)
    }

    @Test
    fun corruptProjectMetadata_isPreservedBeforeIndexBackedRepair() {
        val root = Files.createTempDirectory("project-metadata-corrupt").toFile()
        val repository = ResearchProjectRepository(root) { 17_000L }
        val created = repository.createProject("Durable name", "Durable objective")
        val metadata = root.resolve("projects/${created.id}/project.properties")
        val corruptBytes = "schemaVersion=1\nid=${created.id}\ncreatedAt=9\nupdatedAt=2\n".toByteArray()
        metadata.writeBytes(corruptBytes)

        val repaired = repository.listProjects().single { it.id == created.id }
        val backup = root.resolve("projects/${created.id}/project.properties.corrupt-17000")

        assertEquals("Durable name", repaired.name)
        assertTrue(backup.isFile)
        assertTrue(corruptBytes.contentEquals(backup.readBytes()))
        assertTrue(metadata.readText().contains("name=Durable name"))
    }
}
