package com.robotkinematicslab.mobile.storage.evidence

import com.robotkinematicslab.mobile.provenance.AppBuildIdentity
import com.robotkinematicslab.mobile.storage.project.ResearchProject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.Random
import java.util.zip.ZipInputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class ProjectEvidencePackRepositoryTest {

    @Test
    fun refreshIndex_ignoresStaleLegacyTemporaryDirectoriesForEveryControlFile() {
        val root = Files.createTempDirectory("project-evidence-stale-temp").toFile()
        root.resolve("datasets/input.csv").writeUtf8("value\n1\n")
        val repository = repository(root)
        repository.refreshIndex()
        val staleEntries =
            controlFileNames.map { fileName ->
                root.resolve("$fileName.tmp").also { temporary ->
                    assertTrue(temporary.mkdir())
                }
            }

        val snapshot = repository.refreshIndex()

        controlFileNames.forEach { fileName ->
            assertTrue(root.resolve(fileName).isFile)
            assertTrue(snapshot.artifacts.any { artifact -> artifact.relativePath == fileName })
        }
        staleEntries.forEach { temporary -> assertTrue(temporary.isDirectory) }
    }

    @Test
    fun refreshIndex_createsPortableControlFilesWithProjectRelativeArtifactPaths() {
        val root = Files.createTempDirectory("project-evidence-relative").toFile()
        root.resolve("datasets/trial.csv").writeUtf8("left,right\n1,2\n")
        root.resolve("models/candidate.rklm").apply {
            parentFile?.mkdirs()
            writeBytes(byteArrayOf(1, 2, 3, 4))
        }
        root.resolve("datasets/uncommitted.tmp").writeUtf8("partial")
        root.resolve("datasets/interrupted.partial").writeUtf8("partial")
        root.resolve(".DS_Store").writeUtf8("finder metadata")
        val repository = repository(root)

        val snapshot = repository.refreshIndex()

        assertTrue(root.resolve("figures").isDirectory)
        controlFileNames.forEach { name -> assertTrue(root.resolve(name).isFile) }
        assertTrue(snapshot.artifacts.any { it.relativePath == "datasets/trial.csv" })
        assertTrue(snapshot.artifacts.any { it.relativePath == "models/candidate.rklm" })
        assertFalse(snapshot.artifacts.any { it.relativePath.endsWith(".tmp") })
        assertFalse(snapshot.artifacts.any { it.relativePath.endsWith(".partial") })
        assertFalse(snapshot.artifacts.any { it.relativePath == ".DS_Store" })

        val manifest = root.resolve(ProjectEvidencePackRepository.MANIFEST_FILE_NAME).readText()
        val index = root.resolve(ProjectEvidencePackRepository.INDEX_FILE_NAME).readText()
        assertTrue(manifest.contains("\"pathEncoding\": \"project-relative-v1\""))
        assertTrue(manifest.contains("\"path\":\"datasets/trial.csv\""))
        assertTrue(manifest.contains("\"path\":\"models/candidate.rklm\""))
        assertTrue(index.contains("\"datasets/trial.csv\""))
        assertFalse(manifest.contains(root.absolutePath))
        assertFalse(index.contains(root.absolutePath))
        assertFalse(manifest.contains("datasets\\\\trial.csv"))
    }

    @Test
    fun refreshIndex_recordsExactSha256InSnapshotManifestAndChecksumFile() {
        val root = Files.createTempDirectory("project-evidence-hash").toFile()
        val source = root.resolve("datasets/known.txt").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes("abc".toByteArray(Charsets.UTF_8))
        }
        val repository = repository(root)

        val first = repository.refreshIndex()
        val artifact = first.artifacts.single { it.relativePath == "datasets/known.txt" }
        val expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad"

        assertEquals(expected, artifact.sha256)
        assertTrue(
            root.resolve(ProjectEvidencePackRepository.CHECKSUM_FILE_NAME)
                .readLines()
                .contains("$expected  datasets/known.txt")
        )
        assertTrue(
            root.resolve(ProjectEvidencePackRepository.MANIFEST_FILE_NAME)
                .readText()
                .contains("\"sha256\":\"$expected\"")
        )

        source.writeBytes("abd".toByteArray(Charsets.UTF_8))
        val changed = repository.inspect().artifacts.single { it.relativePath == "datasets/known.txt" }
        assertNotEquals(expected, changed.sha256)
    }

    @Test
    fun portableZip_containsTheCompleteIndexedProjectUnderOneSafeFolder() {
        val root = Files.createTempDirectory("project-evidence-zip").toFile()
        val csvBytes = "a,b\n1,2\n".toByteArray(Charsets.UTF_8)
        val modelBytes = byteArrayOf(0, 1, 2, 3, 4, -1)
        root.resolve("datasets/input.csv").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(csvBytes)
        }
        root.resolve("models/model.rklm").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(modelBytes)
        }
        val repository = repository(root, projectId = "Project / Unsafe Name")
        val output = ByteArrayOutputStream()

        val snapshot = repository.writePortableZip(output)
        val entries = unzip(output.toByteArray())
        val prefix = "project-unsafe-name/"

        assertEquals("project-unsafe-name.rkl-project.zip", snapshot.archiveFileName)
        assertEquals(
            snapshot.artifacts.map { prefix + it.relativePath }.toSet(),
            entries.keys
        )
        assertArrayEquals(csvBytes, entries.getValue(prefix + "datasets/input.csv"))
        assertArrayEquals(modelBytes, entries.getValue(prefix + "models/model.rklm"))
        assertTrue(entries.containsKey(prefix + ProjectEvidencePackRepository.MANIFEST_FILE_NAME))
        assertFalse(entries.keys.any { it.startsWith("/") || ".." in it.split('/') })
        assertFalse(
            String(
                entries.getValue(prefix + ProjectEvidencePackRepository.MANIFEST_FILE_NAME),
                Charsets.UTF_8
            ).contains(root.absolutePath)
        )
    }

    @Test
    fun legacyPack_excludesGlobalIndexSiblingProjectsAndUnrelatedRootFiles() {
        val globalRoot = Files.createTempDirectory("project-evidence-legacy").toFile()
        globalRoot.resolve("datasets/legacy.csv").writeUtf8("value\nlegacy\n")
        globalRoot.resolve("robots/library.rklb").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(7, 8, 9))
        }
        globalRoot.resolve("projects/other-project/datasets/private.csv")
            .writeUtf8("secret\nother-project\n")
        globalRoot.resolve("project-index.properties").writeUtf8("activeProjectId=other-project")
        globalRoot.resolve("unrelated-root.txt").writeUtf8("not project evidence")
        val repository = repository(globalRoot, legacy = true)
        val output = ByteArrayOutputStream()

        val snapshot = repository.writePortableZip(output)
        val paths = snapshot.artifacts.map(ProjectArtifact::relativePath)
        val zipNames = unzip(output.toByteArray()).keys

        assertTrue("datasets/legacy.csv" in paths)
        assertTrue("robots/library.rklb" in paths)
        assertFalse(paths.any { it == "project-index.properties" })
        assertFalse(paths.any { it == "unrelated-root.txt" })
        assertFalse(paths.any { it == "projects" || it.startsWith("projects/") })
        assertFalse(zipNames.any { "/projects/" in it })
        assertFalse(zipNames.any { it.endsWith("/project-index.properties") })
        assertFalse(zipNames.any { it.endsWith("/unrelated-root.txt") })
    }

    @Test
    fun copyArtifact_rejectsTraversalAbsoluteAndAmbiguousPaths() {
        val root = Files.createTempDirectory("project-evidence-traversal").toFile()
        root.resolve("datasets/inside.txt").writeUtf8("inside")
        val outside = Files.createTempFile("project-evidence-outside", ".txt").toFile().apply {
            writeText("outside")
        }
        val repository = repository(root)

        listOf(
            "../${outside.name}",
            "datasets/../../${outside.name}",
            outside.absolutePath,
            "datasets\\inside.txt",
            "datasets//inside.txt",
            "datasets/./inside.txt",
            "datasets/../inside.txt",
            "\u0000"
        ).forEach { path ->
            assertThrows(IllegalArgumentException::class.java) {
                repository.copyArtifact(path, ByteArrayOutputStream())
            }
        }

        val copied = ByteArrayOutputStream()
        repository.copyArtifact("datasets/inside.txt", copied)
        assertEquals("inside", copied.toString(Charsets.UTF_8.name()))
    }

    @Test
    fun preview_isBoundedParsesQuotedTablesAndNeverDecodesBinaryArtifacts() {
        val root = Files.createTempDirectory("project-evidence-preview").toFile()
        root.resolve("datasets/table.csv").writeUtf8(
            buildList {
                add("\"label\",\"value,units\"")
                repeat(40) { index -> add("\"row-$index\",\"$index,$index\"") }
            }.joinToString("\n", postfix = "\n")
        )
        root.resolve("sessions/long.txt").writeUtf8("x".repeat(24_100))
        root.resolve("models/model.rklm").apply {
            requireNotNull(parentFile).mkdirs()
            writeBytes(byteArrayOf(0, -1, 13, 10, 42))
        }
        val repository = repository(root)

        val table = repository.preview("datasets/table.csv")
        assertEquals(32, table.lines.size)
        assertTrue(table.truncated)
        assertEquals(2, table.columnCount)
        assertEquals(listOf("label", "value,units"), table.columnNames)
        assertEquals(listOf("row-0", "0,0"), table.firstRowValues)

        val longText = repository.preview("sessions/long.txt")
        assertTrue(longText.truncated)
        assertEquals(1, longText.lines.size)
        assertTrue(longText.lines.single().endsWith(" …"))
        assertTrue(longText.lines.single().length <= 24_002)

        val binary = repository.preview("models/model.rklm")
        assertEquals(ProjectArtifactFormat.CLASSIFIER_MODEL, binary.artifact.format)
        assertTrue(binary.lines.isEmpty())
        assertFalse(binary.truncated)
        assertNull(binary.columnCount)
        assertTrue(binary.explanation.contains("binary artifact"))
    }

    @Test
    fun portableZip_abortsWhenAHashedSourceChangesAfterTheSnapshot() {
        val root = Files.createTempDirectory("project-evidence-changing-source").toFile()
        val incompressiblePadding = ByteArray(256 * 1024).also { Random(2604L).nextBytes(it) }
        root.resolve("000-padding.bin").writeBytes(incompressiblePadding)
        val changing = root.resolve("zz-changing.txt").apply {
            writeBytes("ORIGINAL".toByteArray(Charsets.UTF_8))
        }
        val repository = repository(root)
        val output = MutatingOutputStream(ByteArrayOutputStream()) {
            changing.writeBytes("MUTATED!".toByteArray(Charsets.UTF_8))
        }

        val failure = assertThrows(IllegalArgumentException::class.java) {
            repository.writePortableZip(output)
        }

        assertTrue(output.mutated)
        assertEquals("MUTATED!", changing.readText())
        assertTrue(failure.message.orEmpty().contains("Project changed while it was being exported"))
    }

    @Test
    fun refreshIndex_neverOverwritesAUserManagedReservedFile() {
        val root = Files.createTempDirectory("project-evidence-owned-controls").toFile()
        val userContext = root.resolve(ProjectEvidencePackRepository.CONTEXT_FILE_NAME).apply {
            writeText("My manually curated research notes", Charsets.UTF_8)
        }
        root.resolve("datasets/input.csv").writeUtf8("value\n1\n")
        val repository = repository(root)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            repository.refreshIndex()
        }

        assertTrue(failure.message.orEmpty().contains("was not overwritten"))
        assertEquals("My manually curated research notes", userContext.readText(Charsets.UTF_8))
    }

    @Test
    fun refreshIndex_rejectsNonPortableNamesBeforeWritingAnyIndex() {
        val root = Files.createTempDirectory("project-evidence-portable-name").toFile()
        root.resolve("datasets/bad\nrow.csv").writeUtf8("value\n1\n")
        val repository = repository(root)

        assertThrows(IllegalArgumentException::class.java) { repository.refreshIndex() }

        controlFileNames.forEach { name -> assertFalse(root.resolve(name).exists()) }
    }

    @Test
    fun checksums_coverManifestContextIndexAndPayloadButNotTheChecksumFileItself() {
        val root = Files.createTempDirectory("project-evidence-control-coverage").toFile()
        root.resolve("datasets/input.csv").writeUtf8("value\n1\n")
        val repository = repository(root)

        val snapshot = repository.refreshIndex()
        val checksums = root.resolve(ProjectEvidencePackRepository.CHECKSUM_FILE_NAME).readText()

        listOf(
            "datasets/input.csv",
            ProjectEvidencePackRepository.CONTEXT_FILE_NAME,
            ProjectEvidencePackRepository.INDEX_FILE_NAME,
            ProjectEvidencePackRepository.MANIFEST_FILE_NAME
        ).forEach { path -> assertTrue("Missing checksum for $path", checksums.contains("  $path\n")) }
        assertFalse(checksums.contains("  ${ProjectEvidencePackRepository.CHECKSUM_FILE_NAME}\n"))
        assertTrue(snapshot.artifacts.any { it.relativePath == ProjectEvidencePackRepository.CHECKSUM_FILE_NAME })
    }

    @Test
    fun copyArtifact_abortsWhenTheInspectedFileWasReplacedWithSameSizeContent() {
        val root = Files.createTempDirectory("project-evidence-copy-version").toFile()
        val source = root.resolve("datasets/input.csv").apply {
            parentFile?.mkdirs()
            writeText("value\n1\n", Charsets.UTF_8)
        }
        val repository = repository(root)
        val inspected = repository.refreshIndex().artifacts.single { it.relativePath == "datasets/input.csv" }
        source.writeText("value\n2\n", Charsets.UTF_8)

        val failure = assertThrows(IllegalArgumentException::class.java) {
            repository.copyArtifact(inspected, ByteArrayOutputStream())
        }

        assertTrue(failure.message.orEmpty().contains("changed after it was inspected"))
    }

    private fun repository(
        root: java.io.File,
        legacy: Boolean = false,
        projectId: String = if (legacy) "layer-1-research" else "project-alpha"
    ): ProjectEvidencePackRepository =
        ProjectEvidencePackRepository(
            project =
                ResearchProject(
                    id = projectId,
                    name = "Evidence project",
                    objective = "Keep every scientific artifact portable.",
                    createdAtEpochMillis = 1_000L,
                    updatedAtEpochMillis = 2_000L,
                    usesLegacyWorkspace = legacy
                ),
            projectRoot = root,
            buildIdentity = AppBuildIdentity("com.test.rkl", "9.7", 97L, "research"),
            clockMillis = { 3_000L }
        )

    private fun java.io.File.writeUtf8(value: String) {
        parentFile?.mkdirs()
        writeText(value, Charsets.UTF_8)
    }

    private fun unzip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = linkedMapOf<String, ByteArray>()
        ZipInputStream(bytes.inputStream().buffered()).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val content = ByteArrayOutputStream()
                zip.copyTo(content)
                entries[entry.name] = content.toByteArray()
                zip.closeEntry()
            }
        }
        return entries
    }

    private class MutatingOutputStream(
        private val delegate: OutputStream,
        private val mutate: () -> Unit
    ) : OutputStream() {
        var mutated: Boolean = false
            private set

        override fun write(value: Int) {
            mutateOnce()
            delegate.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            mutateOnce()
            delegate.write(buffer, offset, length)
        }

        override fun flush() = delegate.flush()

        override fun close() = delegate.close()

        private fun mutateOnce() {
            if (!mutated) {
                mutated = true
                mutate()
            }
        }
    }

    private companion object {
        val controlFileNames =
            setOf(
                ProjectEvidencePackRepository.CONTEXT_FILE_NAME,
                ProjectEvidencePackRepository.INDEX_FILE_NAME,
                ProjectEvidencePackRepository.MANIFEST_FILE_NAME,
                ProjectEvidencePackRepository.CHECKSUM_FILE_NAME
            )
    }
}
