package com.robotkinematicslab.mobile.dataset

import com.robotkinematicslab.mobile.reproducibility.ScientificRandomProtocol
import com.robotkinematicslab.mobile.provenance.AppBuildIdentity
import java.io.File
import java.nio.file.Files
import java.util.Properties
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class DatasetStorageRepositoryTest {

    @Test
    fun savedManifestCarriesExactProducerBuildIdentity() {
        val directory = Files.createTempDirectory("dataset-build-identity").toFile()
        val identity = AppBuildIdentity("com.test.rkl", "9.7", 97L, "research")
        val repository = DatasetStorageRepository(directory, identity)
        val manifest = DatasetManifest(
            datasetName = "Provenance",
            csvPath = repository.resolveCsvFile("Provenance").absolutePath,
            rowCount = 1L,
            generationCount = 1,
            robotIds = listOf("r1"),
            samplesPerRobotLastRun = 1,
            randomSeed = 42,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.5,
            filterMode = DatasetFilterMode.ALL,
            lastUpdatedEpochMillis = 1L
        )

        repository.saveManifest(manifest)

        val properties = Properties().apply {
            File(directory, "Provenance.properties").inputStream().use(::load)
        }
        assertEquals("com.test.rkl", properties.getProperty("producerApplicationId"))
        assertEquals("9.7", properties.getProperty("producerVersionName"))
        assertEquals("97", properties.getProperty("producerVersionCode"))
        assertEquals("research", properties.getProperty("producerBuildType"))
    }

    @Test
    fun manifestWithoutProtocol_isMarkedAsLegacyInsteadOfMislabelled() {
        val directory = Files.createTempDirectory("legacy-dataset-storage-test").toFile()
        val repository = DatasetStorageRepository(directory)
        val legacy =
            DatasetManifest(
                datasetName = "Legacy",
                csvPath = repository.resolveCsvFile("Legacy").absolutePath,
                rowCount = 10L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 10,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L
            )

        repository.saveManifest(legacy)
        val manifestFile = File(directory, "Legacy.properties")
        val properties = Properties()
        manifestFile.inputStream().buffered().use(properties::load)
        properties.remove("randomProtocol")
        manifestFile.outputStream().buffered().use { properties.store(it, "legacy") }

        assertEquals(
            ScientificRandomProtocol.LEGACY_UNVERSIONED_ID,
            repository.loadManifest("Legacy")?.randomProtocol
        )
    }

    @Test
    fun manifestCanBeSavedLoadedAndListed() {
        val directory = Files.createTempDirectory("dataset-storage-test").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Study / A")
        val expected =
            DatasetManifest(
                datasetName = "Study / A",
                csvPath = csv.absolutePath,
                rowCount = 1234L,
                generationCount = 2,
                robotIds = listOf("r1", "r2"),
                samplesPerRobotLastRun = 100,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 123456789L
            )

        repository.saveManifest(expected)

        assertEquals(expected, repository.loadManifest(expected.datasetName))
        assertEquals(listOf(expected), repository.listManifests())
        assertNotNull(repository.resolveCsvFile("!!!").name)
    }

    @Test
    fun appendTargetAcceptsNewNameOrMatchingManifestAndCsv() {
        val directory = Files.createTempDirectory("dataset-append-valid").toFile()
        val repository = DatasetStorageRepository(directory)

        val newTarget = repository.resolveAppendOrCreateTarget("New study")
        assertNull(newTarget.manifest)
        assertEquals(0L, newTarget.existingRowCount)
        assertTrue(!newTarget.csvFile.exists())

        val csv = repository.resolveCsvFile("Existing study")
        writeScientificCsv(csv, rows = 2)
        val expected =
            manifest(repository, "Existing study", updatedAt = 7L, csv = csv).copy(rowCount = 2L)
        repository.saveManifest(expected)

        val existingTarget = repository.resolveAppendOrCreateTarget("Existing study")
        assertEquals(expected, existingTarget.manifest)
        assertEquals(csv.canonicalFile, existingTarget.csvFile.canonicalFile)
        assertEquals(2L, existingTarget.existingRowCount)
    }

    @Test
    fun orphanCsvIsRejectedWithoutChangingItsBytes() {
        val directory = Files.createTempDirectory("dataset-append-orphan").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Orphan")
        writeScientificCsv(csv, rows = 1)
        val before = csv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Orphan")
        }

        assertTrue(error.message.orEmpty().contains("without a valid manifest"))
        assertTrue(before.contentEquals(csv.readBytes()))
    }

    @Test
    fun corruptManifestIsRejectedWithoutChangingCsvBytes() {
        val directory = Files.createTempDirectory("dataset-append-corrupt-manifest").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Corrupt append")
        writeScientificCsv(csv, rows = 1)
        repository.saveManifest(manifest(repository, "Corrupt append", updatedAt = 1L, csv = csv))
        File(directory, "Corrupt_append.properties").writeText("datasetName=Corrupt append\nrowCount=not-a-number\n")
        val before = csv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Corrupt append")
        }

        assertTrue(error.message.orEmpty().contains("unreadable or invalid"))
        assertTrue(before.contentEquals(csv.readBytes()))
    }

    @Test
    fun storageIndexSeparatesValidAndCorruptManifestsAndQuarantinePreservesCsvBytes() {
        val directory = Files.createTempDirectory("dataset-storage-corrupt-index").toFile()
        val repository = DatasetStorageRepository(directory)
        val validCsv = repository.resolveCsvFile("Valid").apply { writeText("valid csv bytes") }
        val validManifest = manifest(repository, "Valid", updatedAt = 20L, csv = validCsv)
        repository.saveManifest(validManifest)
        val corruptCsv = repository.resolveCsvFile("Damaged").apply { writeText("irreplaceable csv bytes") }
        repository.saveManifest(manifest(repository, "Damaged", updatedAt = 10L, csv = corruptCsv))
        val corruptFile = File(directory, "Damaged.properties")
        val corruptProperties = Properties().apply { corruptFile.inputStream().use(::load) }
        corruptProperties.setProperty("rowCount", "not-a-number")
        corruptFile.outputStream().use { corruptProperties.store(it, "intentionally damaged") }
        val corruptCsvBefore = corruptCsv.readBytes()
        val corruptManifestBefore = corruptFile.readBytes()

        val index = repository.inspectManifestIndex()

        assertEquals(listOf(validManifest), index.validManifests)
        assertEquals(listOf(validManifest), repository.listManifests())
        assertEquals(2, index.totalEntryCount)
        val damaged = index.corruptManifests.single()
        assertEquals("Damaged", damaged.datasetName)
        assertEquals(corruptCsv.absolutePath, damaged.referencedCsvPath)
        assertTrue(damaged.reason.contains("validation", ignoreCase = true))
        assertTrue(corruptCsvBefore.contentEquals(corruptCsv.readBytes()))

        val quarantined = repository.quarantineCorruptManifest(damaged)

        assertTrue(!corruptFile.exists())
        assertEquals("corrupt-manifests", requireNotNull(quarantined.parentFile).name)
        assertTrue(corruptManifestBefore.contentEquals(quarantined.readBytes()))
        assertTrue(corruptCsvBefore.contentEquals(corruptCsv.readBytes()))
        assertEquals(listOf(validManifest), repository.inspectManifestIndex().validManifests)
        assertTrue(repository.inspectManifestIndex().corruptManifests.isEmpty())
    }

    @Test
    fun validManifestCannotBeQuarantinedOrChangeItsCsv() {
        val directory = Files.createTempDirectory("dataset-storage-valid-quarantine-guard").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Protected").apply { writeText("protected csv bytes") }
        repository.saveManifest(manifest(repository, "Protected", updatedAt = 1L, csv = csv))
        val manifestFile = File(directory, "Protected.properties")
        val manifestBefore = manifestFile.readBytes()
        val csvBefore = csv.readBytes()
        val forgedCorruptEntry =
            CorruptDatasetManifest(
                fileName = manifestFile.name,
                manifestPath = manifestFile.absolutePath,
                datasetName = "Protected",
                referencedCsvPath = csv.absolutePath,
                reason = "forged",
                lastModifiedEpochMillis = manifestFile.lastModified()
            )

        assertThrows(IllegalStateException::class.java) {
            repository.quarantineCorruptManifest(forgedCorruptEntry)
        }

        assertTrue(manifestBefore.contentEquals(manifestFile.readBytes()))
        assertTrue(csvBefore.contentEquals(csv.readBytes()))
        assertEquals(1, repository.listManifests().size)
    }

    @Test
    fun manifestRowMismatchIsRejectedWithoutChangingCsvBytes() {
        val directory = Files.createTempDirectory("dataset-append-row-mismatch").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Mismatch")
        writeScientificCsv(csv, rows = 2)
        repository.saveManifest(manifest(repository, "Mismatch", updatedAt = 1L, csv = csv))
        val before = csv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Mismatch")
        }

        assertTrue(error.message.orEmpty().contains("contains 2 rows"))
        assertTrue(before.contentEquals(csv.readBytes()))
    }

    @Test
    fun manifestWithoutCsvIsRejectedBeforeGeneration() {
        val directory = Files.createTempDirectory("dataset-append-missing-csv").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Missing CSV")
        repository.saveManifest(manifest(repository, "Missing CSV", updatedAt = 1L, csv = csv))

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Missing CSV")
        }

        assertTrue(error.message.orEmpty().contains("CSV file is missing"))
        assertTrue(!csv.exists())
    }

    @Test
    fun incompleteBatchProvenanceIsRejectedWithoutChangingCsvBytes() {
        val directory = Files.createTempDirectory("dataset-append-incomplete-provenance").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Incomplete history")
        writeScientificCsv(csv, rows = 2)
        val incomplete =
            manifest(repository, "Incomplete history", updatedAt = 1L, csv = csv).copy(
                rowCount = 2L,
                generationCount = 2,
                batches = emptyList()
            )
        repository.saveManifest(incomplete)
        val before = csv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Incomplete history")
        }

        assertTrue(error.message.orEmpty().contains("incomplete or inconsistent batch provenance"))
        assertTrue(before.contentEquals(csv.readBytes()))
    }

    @Test
    fun incompatibleCsvSchemaIsRejectedWithoutChangingCsvBytes() {
        val directory = Files.createTempDirectory("dataset-append-schema-mismatch").toFile()
        val repository = DatasetStorageRepository(directory)
        val csv = repository.resolveCsvFile("Wrong schema").apply { writeText("wrong,header\nvalue,row\n") }
        repository.saveManifest(manifest(repository, "Wrong schema", updatedAt = 1L, csv = csv))
        val before = csv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Wrong schema")
        }

        assertTrue(error.message.orEmpty().contains("invalid schema"))
        assertTrue(before.contentEquals(csv.readBytes()))
    }

    @Test
    fun collidingSanitizedNames_neverLoadOrOverwriteEachOthersManifest() {
        val directory = Files.createTempDirectory("dataset-name-collision").toFile()
        val repository = DatasetStorageRepository(directory)
        val first = manifest(repository, "Study / A", 11L)
        repository.saveManifest(first)

        assertNull(repository.loadManifest("Study ? A"))
        val secondCsv = repository.resolveCsvFile("Study ? A")
        assertTrue(secondCsv.name != File(first.csvPath).name)
        val second = manifest(repository, "Study ? A", 22L, secondCsv)
        repository.saveManifest(second)

        assertEquals(first, repository.loadManifest("Study / A"))
        assertEquals(second, repository.loadManifest("Study ? A"))
        assertEquals(setOf(first, second), repository.listManifests().toSet())
    }

    @Test
    fun staleLegacyTemporaryDirectoryCannotBlockDatasetManifestPublication() {
        val directory = Files.createTempDirectory("dataset-manifest-stale-temp").toFile()
        val repository = DatasetStorageRepository(directory)
        val expected = manifest(repository, "Stale temporary state", 31L)
        val staleTemporaryEntry = File(directory, "Stale_temporary_state.properties.tmp")
        assertTrue(staleTemporaryEntry.mkdir())

        repository.saveManifest(expected)

        assertEquals(expected, repository.loadManifest(expected.datasetName))
        assertTrue(staleTemporaryEntry.isDirectory)
    }

    @Test
    fun appendTargetsForCollidingSanitizedNamesRemainIsolated() {
        val directory = Files.createTempDirectory("dataset-append-name-collision").toFile()
        val repository = DatasetStorageRepository(directory)
        val firstCsv = repository.resolveCsvFile("Study / A")
        writeScientificCsv(firstCsv, rows = 1)
        val first = manifest(repository, "Study / A", 11L, firstCsv)
        repository.saveManifest(first)

        val secondCsv = repository.resolveCsvFile("Study ? A")
        writeScientificCsv(secondCsv, rows = 2)
        val second = manifest(repository, "Study ? A", 22L, secondCsv).copy(rowCount = 2L)
        repository.saveManifest(second)

        val firstTarget = repository.resolveAppendOrCreateTarget(first.datasetName)
        val secondTarget = repository.resolveAppendOrCreateTarget(second.datasetName)
        assertEquals(first, firstTarget.manifest)
        assertEquals(second, secondTarget.manifest)
        assertTrue(firstTarget.csvFile.canonicalFile != secondTarget.csvFile.canonicalFile)
        assertEquals(1L, firstTarget.existingRowCount)
        assertEquals(2L, secondTarget.existingRowCount)
    }

    @Test
    fun manifestPointingOutsideManagedStorageCannotBecomeAnAppendTarget() {
        val directory = Files.createTempDirectory("dataset-append-managed-root").toFile()
        val outsideDirectory = Files.createTempDirectory("dataset-append-outside-root").toFile()
        val repository = DatasetStorageRepository(directory)
        val managedCsv = repository.resolveCsvFile("Escaped path")
        writeScientificCsv(managedCsv, rows = 1)
        repository.saveManifest(manifest(repository, "Escaped path", 1L, managedCsv))
        val manifestFile = File(directory, "Escaped_path.properties")
        val properties = Properties().apply { manifestFile.inputStream().use(::load) }
        val outsideCsv = File(outsideDirectory, "outside.csv")
        writeScientificCsv(outsideCsv, rows = 1)
        properties.setProperty("csvPath", outsideCsv.absolutePath)
        manifestFile.outputStream().use { properties.store(it, "outside path attack") }
        val outsideBefore = outsideCsv.readBytes()

        val error = assertThrows(IllegalStateException::class.java) {
            repository.resolveAppendOrCreateTarget("Escaped path")
        }

        assertTrue(error.message.orEmpty().contains("unreadable or invalid"))
        assertTrue(outsideBefore.contentEquals(outsideCsv.readBytes()))
    }

    @Test
    fun migratedManifest_resolvesTheOrganizedCsvCopyWithoutDeletingLegacyData() {
        val legacyDirectory = Files.createTempDirectory("legacy-dataset-location").toFile()
        val organizedDirectory = Files.createTempDirectory("organized-dataset-location").toFile()
        val legacyCsv = File(legacyDirectory, "Migrated.csv").apply { writeText("header\nvalue\n") }
        val organizedCsv = File(organizedDirectory, legacyCsv.name).apply { writeText(legacyCsv.readText()) }
        val repository = DatasetStorageRepository(organizedDirectory)
        val manifest =
            DatasetManifest(
                datasetName = "Migrated",
                csvPath = legacyCsv.absolutePath,
                rowCount = 1L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 1,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L
            )

        repository.saveManifest(manifest)

        assertEquals(organizedCsv.absolutePath, repository.loadManifest("Migrated")?.csvPath)
        assertEquals("header\nvalue\n", legacyCsv.readText())
    }

    @Test
    fun unsupportedFutureManifestIsExcludedFromDatasetsButRemainsVisibleAsDamaged() {
        val directory = Files.createTempDirectory("future-dataset-manifest").toFile()
        val repository = DatasetStorageRepository(directory)
        val manifest =
            DatasetManifest(
                datasetName = "Future",
                csvPath = repository.resolveCsvFile("Future").absolutePath,
                rowCount = 1L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 1,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L
            )
        repository.saveManifest(manifest)
        val file = File(directory, "Future.properties")
        val properties = Properties().apply { file.inputStream().use(::load) }
        properties.setProperty("schemaVersion", "999")
        file.outputStream().use { output -> properties.store(output, "future") }

        assertNull(repository.loadManifest("Future"))
        assertTrue(repository.listManifests().isEmpty())
        val damaged = repository.inspectManifestIndex().corruptManifests.single()
        assertEquals("Future", damaged.datasetName)
        assertTrue(damaged.reason.contains("unsupported", ignoreCase = true))
    }

    @Test
    fun invalidScientificManifestCannotEnterTheIndex() {
        val directory = Files.createTempDirectory("invalid-dataset-manifest").toFile()
        val repository = DatasetStorageRepository(directory)

        assertThrows(IllegalArgumentException::class.java) {
            DatasetManifest(
                datasetName = "Invalid",
                csvPath = repository.resolveCsvFile("Invalid").absolutePath,
                rowCount = -1L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 1,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = Double.NaN,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L
            )
        }
    }

    @Test
    fun completeBatchHistoryRoundTripsWithoutLosingEarlierContracts() {
        val directory = Files.createTempDirectory("batch-history-storage-test").toFile()
        val repository = DatasetStorageRepository(directory)
        val first = batch(0, 0L, 1e-4)
        val second = batch(1, 100L, 1e-6)
        val expected =
            DatasetManifest(
                datasetName = "Mixed history",
                csvPath = repository.resolveCsvFile("Mixed history").absolutePath,
                rowCount = 200L,
                generationCount = 2,
                robotIds = listOf("r1", "r2"),
                samplesPerRobotLastRun = 50,
                randomSeed = 43,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 2L,
                batches = listOf(first, second)
            )

        repository.saveManifest(expected)

        val actual = requireNotNull(repository.loadManifest(expected.datasetName))
        assertEquals(expected, actual)
        assertTrue(actual.hasCompleteBatchProvenance)
        assertEquals(1e-4, actual.batches.first().ikConfig.tolerance, 0.0)
        assertEquals(1e-6, actual.batches.last().ikConfig.tolerance, 0.0)
    }

    @Test
    fun corruptedBatchHistoryIsRejectedInsteadOfPartiallyLoaded() {
        val directory = Files.createTempDirectory("corrupt-batch-history-test").toFile()
        val repository = DatasetStorageRepository(directory)
        val manifest =
            DatasetManifest(
                datasetName = "Corrupt history",
                csvPath = repository.resolveCsvFile("Corrupt history").absolutePath,
                rowCount = 100L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 100,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L,
                batches = listOf(batch(0, 0L, 1e-4))
            )
        repository.saveManifest(manifest)
        val file = File(directory, "Corrupt_history.properties")
        val properties = Properties().apply { file.inputStream().use(::load) }
        properties.remove("batch.0.rowCount")
        file.outputStream().use { properties.store(it, "intentionally corrupt") }

        assertNull(repository.loadManifest(manifest.datasetName))
    }

    @Test
    fun pathologicalBatchCountIsRejectedBeforeBuildingAList() {
        val directory = Files.createTempDirectory("pathological-batch-count-test").toFile()
        val repository = DatasetStorageRepository(directory)
        val manifest =
            DatasetManifest(
                datasetName = "Pathological",
                csvPath = repository.resolveCsvFile("Pathological").absolutePath,
                rowCount = 1L,
                generationCount = 1,
                robotIds = listOf("r1"),
                samplesPerRobotLastRun = 1,
                randomSeed = 42,
                targetMode = DatasetTargetMode.MIXED,
                reachableFraction = 0.7,
                filterMode = DatasetFilterMode.ALL,
                lastUpdatedEpochMillis = 1L
            )
        repository.saveManifest(manifest)
        val file = File(directory, "Pathological.properties")
        val properties = Properties().apply { file.inputStream().use(::load) }
        properties.setProperty("batchCount", Int.MAX_VALUE.toString())
        file.outputStream().use { properties.store(it, "intentionally corrupt") }

        assertNull(repository.loadManifest(manifest.datasetName))
    }

    private fun batch(index: Int, rowStart: Long, tolerance: Double) =
        DatasetGenerationBatch(
            generationIndex = index,
            batchId = "batch-${index + 1}",
            rowStart = rowStart,
            rowCount = 100L,
            robotIds = listOf(if (index == 0) "r1" else "r2"),
            samplesPerRobot = 100,
            randomSeed = 42 + index,
            targetMode = DatasetTargetMode.MIXED,
            reachableFraction = 0.7,
            filterMode = DatasetFilterMode.ALL,
            createdAtEpochMillis = index.toLong() + 1L,
            ikConfig = com.robotkinematicslab.mobile.domain.config.IKConfig(tolerance = tolerance),
            metricPolicy = com.robotkinematicslab.mobile.diagnostics.metrics.DiagnosticMetricPolicy()
        )

    private fun manifest(
        repository: DatasetStorageRepository,
        name: String,
        updatedAt: Long,
        csv: File = repository.resolveCsvFile(name)
    ) = DatasetManifest(
        datasetName = name,
        csvPath = csv.absolutePath,
        rowCount = 1L,
        generationCount = 1,
        robotIds = listOf("r1"),
        samplesPerRobotLastRun = 1,
        randomSeed = 42,
        targetMode = DatasetTargetMode.MIXED,
        reachableFraction = 0.5,
        filterMode = DatasetFilterMode.ALL,
        lastUpdatedEpochMillis = updatedAt
    )

    private fun writeScientificCsv(file: File, rows: Int) {
        file.parentFile?.mkdirs()
        file.bufferedWriter().use { writer ->
            writer.appendLine(ScientificDatasetCsvWriter.HEADER.joinToString(","))
            repeat(rows) { index -> writer.appendLine("row-$index") }
        }
    }
}
