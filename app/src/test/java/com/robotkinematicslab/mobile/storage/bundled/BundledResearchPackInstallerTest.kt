package com.robotkinematicslab.mobile.storage.bundled

import com.robotkinematicslab.mobile.dataset.DatasetStorageRepository
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest
import java.util.Properties
import java.util.zip.GZIPOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BundledResearchPackInstallerTest {

    @Test
    fun productionCatalogDeclaresTheCompleteScaleMatrix() {
        val assetRoot = File("src/main/assets")
        assertTrue("Production research-pack assets are missing.", assetRoot.isDirectory)
        val root = Files.createTempDirectory("bundled-pack-production-catalog").toFile()
        val paths = AppStoragePaths(root.resolve("documents"), root.resolve("internal"))
        paths.ensureStructureAndMigrateLegacyData()
        val installer = BundledResearchPackInstaller(
            paths = paths,
            datasetRepository = DatasetStorageRepository(paths.datasetsDirectory),
            openAsset = { path -> assetRoot.resolve(path).inputStream() }
        )

        val status = installer.inspect()

        assertTrue(status.available)
        assertEquals("rkl-comprehensive-evidence-v2", status.packId)
        assertEquals(6, status.datasetCount)
        assertEquals(12, status.classifierModelCount)
        assertEquals(6, status.oneMicronModelCount)
        assertEquals(18, status.modelCount)
    }

    @Test
    fun importsDatasetAndModelEvidenceThenBecomesIdempotent() {
        val root = Files.createTempDirectory("bundled-pack-good").toFile()
        val paths = AppStoragePaths(root.resolve("documents"), root.resolve("internal"))
        paths.ensureStructureAndMigrateLegacyData()
        val csv = "header\nrow\n".toByteArray()
        val micronModel = "micron-model".toByteArray()
        val micronHistory = "epoch,loss\n1,0.1\n".toByteArray()
        val micronReport = "datasetPath=old\nmodelPath=old\nlearningCurvePath=old\n".toByteArray()
        val classifierModel = "classifier-model".toByteArray()
        val classifierHistory = "schemaVersion,runId\n".toByteArray()
        val classifierSummary = "runId=classifier-run\nrunName=Factory comparison\ndatasetPath=old\nhistoryCsvPath=old\nmodelPaths=old\n".toByteArray()
        val assets = mutableMapOf(
            "research_pack/datasets/example.csv.gz" to gzip(csv),
            "research_pack/models/micron.rkl-micron" to micronModel,
            "research_pack/training/micron/report.properties" to micronReport,
            "research_pack/training/micron/history.csv" to micronHistory,
            "research_pack/models/classifier.rklm" to classifierModel,
            "research_pack/training/classifier/summary.properties" to classifierSummary,
            "research_pack/training/classifier/history.csv" to classifierHistory
        )
        assets["research_pack/catalog.properties"] = catalog(
            csvSha = sha(csv),
            micronModelSha = sha(micronModel),
            micronModelBytes = micronModel.size,
            micronReportSha = sha(micronReport),
            micronReportBytes = micronReport.size,
            micronHistorySha = sha(micronHistory),
            micronHistoryBytes = micronHistory.size,
            classifierModelSha = sha(classifierModel),
            classifierModelBytes = classifierModel.size,
            classifierSummarySha = sha(classifierSummary),
            classifierSummaryBytes = classifierSummary.size,
            classifierHistorySha = sha(classifierHistory),
            classifierHistoryBytes = classifierHistory.size
        ).toByteArray()
        val repository = DatasetStorageRepository(paths.datasetsDirectory)
        val installer = BundledResearchPackInstaller(
            paths = paths,
            datasetRepository = repository,
            // The happy-path contract must not depend on how full the developer machine is.
            // Storage exhaustion has its own explicit test below.
            allocatableBytes = { Long.MAX_VALUE },
            openAsset = { path -> ByteArrayInputStream(requireNotNull(assets[path]) { path }) }
        )
        val staleTemporaryDirectories =
            listOf(
                paths.datasetsDirectory.resolve(".factory_example.csv.bundled-partial"),
                paths.oneMicronTrainingDirectory.resolve("micron-run/.one-micron-report.properties.bundled-partial"),
                paths.trainingDirectory.resolve("classifier-run/.summary.properties.bundled-partial"),
                paths.rootDirectory.resolve("..test-pack-v1.installed.properties.partial")
            ).onEach { temporary ->
                assertTrue(temporary.mkdirs())
                temporary.resolve("interrupted-write.txt").writeText("legacy residue")
            }

        val first = installer.installIfNeeded()
        assertTrue(first.status.installed)
        assertEquals(7, first.installedArtifacts)
        assertEquals("header\nrow\n", paths.datasetsDirectory.resolve("factory_example.csv").readText())
        val manifest = repository.loadManifest("Factory example")!!
        assertEquals(1L, manifest.rowCount)
        assertEquals(listOf("robot-a"), manifest.robotIds)

        val micronProperties = Properties().apply {
            paths.oneMicronTrainingDirectory.resolve("micron-run/one-micron-report.properties")
                .inputStream().use(::load)
        }
        assertEquals(paths.datasetsDirectory.resolve("factory_example.csv").absolutePath, micronProperties.getProperty("datasetPath"))
        assertEquals(paths.oneMicronModelsDirectory.resolve("micron.rkl-micron").absolutePath, micronProperties.getProperty("modelPath"))

        val classifierProperties = Properties().apply {
            paths.trainingDirectory.resolve("classifier-run/summary.properties").inputStream().use(::load)
        }
        assertEquals(paths.datasetsDirectory.resolve("factory_example.csv").absolutePath, classifierProperties.getProperty("datasetPath"))
        assertEquals(paths.modelsDirectory.resolve("classifier.rklm").absolutePath, classifierProperties.getProperty("modelPaths"))
        staleTemporaryDirectories.forEach { temporary -> assertTrue(temporary.isDirectory) }

        val second = installer.installIfNeeded()
        assertEquals(0, second.installedArtifacts)
        assertEquals(7, second.reusedArtifacts)
        assertTrue(installer.inspect().installed)
    }

    @Test
    fun rejectsCorruptAssetWithoutPublishingPartialDataset() {
        val root = Files.createTempDirectory("bundled-pack-corrupt").toFile()
        val paths = AppStoragePaths(root.resolve("documents"), root.resolve("internal"))
        paths.ensureStructureAndMigrateLegacyData()
        val csv = "header\nrow\n".toByteArray()
        val assets = mapOf(
            "research_pack/catalog.properties" to datasetOnlyCatalog("0".repeat(64)).toByteArray(),
            "research_pack/datasets/example.csv.gz" to gzip(csv)
        )
        val installer = BundledResearchPackInstaller(
            paths = paths,
            datasetRepository = DatasetStorageRepository(paths.datasetsDirectory),
            allocatableBytes = { Long.MAX_VALUE },
            openAsset = { path -> ByteArrayInputStream(requireNotNull(assets[path])) }
        )

        val failure = runCatching { installer.installIfNeeded() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertFalse(paths.datasetsDirectory.resolve("factory_example.csv").exists())
        assertFalse(installer.inspect().installed)
    }

    @Test
    fun refusesToOverwriteAUserModifiedReservedDataset() {
        val root = Files.createTempDirectory("bundled-pack-preserve").toFile()
        val paths = AppStoragePaths(root.resolve("documents"), root.resolve("internal"))
        paths.ensureStructureAndMigrateLegacyData()
        val csv = "header\nrow\n".toByteArray()
        val destination = paths.datasetsDirectory.resolve("factory_example.csv")
        destination.writeText("user extension")
        val assets = mapOf(
            "research_pack/catalog.properties" to datasetOnlyCatalog(sha(csv)).toByteArray(),
            "research_pack/datasets/example.csv.gz" to gzip(csv)
        )
        val installer = BundledResearchPackInstaller(
            paths = paths,
            datasetRepository = DatasetStorageRepository(paths.datasetsDirectory),
            allocatableBytes = { Long.MAX_VALUE },
            openAsset = { path -> ByteArrayInputStream(requireNotNull(assets[path])) }
        )

        val failure = runCatching { installer.installIfNeeded() }.exceptionOrNull()
        assertTrue(failure is IllegalArgumentException)
        assertEquals("user extension", destination.readText())
    }

    @Test
    fun refusesImportBeforeWritingWhenAndroidReportsInsufficientAllocatableSpace() {
        val root = Files.createTempDirectory("bundled-pack-no-space").toFile()
        val paths = AppStoragePaths(root.resolve("documents"), root.resolve("internal"))
        paths.ensureStructureAndMigrateLegacyData()
        val csv = "header\nrow\n".toByteArray()
        val assets = mapOf(
            "research_pack/catalog.properties" to datasetOnlyCatalog(sha(csv)).toByteArray(),
            "research_pack/datasets/example.csv.gz" to gzip(csv)
        )
        val installer = BundledResearchPackInstaller(
            paths = paths,
            datasetRepository = DatasetStorageRepository(paths.datasetsDirectory),
            allocatableBytes = { 0L },
            openAsset = { path -> ByteArrayInputStream(requireNotNull(assets[path])) }
        )

        val failure = runCatching { installer.installIfNeeded() }.exceptionOrNull()

        assertTrue(failure is IllegalArgumentException)
        assertTrue(failure?.message.orEmpty().contains("Not enough free storage"))
        assertFalse(paths.datasetsDirectory.resolve("factory_example.csv").exists())
        assertFalse(paths.rootDirectory.listFiles().orEmpty().any { it.name.contains("partial") })
    }

    private fun catalog(
        csvSha: String,
        micronModelSha: String,
        micronModelBytes: Int,
        micronReportSha: String,
        micronReportBytes: Int,
        micronHistorySha: String,
        micronHistoryBytes: Int,
        classifierModelSha: String,
        classifierModelBytes: Int,
        classifierSummarySha: String,
        classifierSummaryBytes: Int,
        classifierHistorySha: String,
        classifierHistoryBytes: Int
    ): String = datasetOnlyCatalog(csvSha).replace(
        "oneMicronRunCount=0\nclassifierRunCount=0",
        """oneMicronRunCount=1
oneMicronRun.0.runId=micron-run
oneMicronRun.0.displayName=Micron model
oneMicronRun.0.datasetId=example
oneMicronRun.0.modelAssetPath=research_pack/models/micron.rkl-micron
oneMicronRun.0.modelFileName=micron.rkl-micron
oneMicronRun.0.modelSha256=$micronModelSha
oneMicronRun.0.modelBytes=$micronModelBytes
oneMicronRun.0.reportAssetPath=research_pack/training/micron/report.properties
oneMicronRun.0.reportSha256=$micronReportSha
oneMicronRun.0.reportBytes=$micronReportBytes
oneMicronRun.0.historyAssetPath=research_pack/training/micron/history.csv
oneMicronRun.0.historySha256=$micronHistorySha
oneMicronRun.0.historyBytes=$micronHistoryBytes
classifierRunCount=1
classifierRun.0.runId=classifier-run
classifierRun.0.displayName=Classifier comparison
classifierRun.0.datasetId=example
classifierRun.0.summaryAssetPath=research_pack/training/classifier/summary.properties
classifierRun.0.summarySha256=$classifierSummarySha
classifierRun.0.summaryBytes=$classifierSummaryBytes
classifierRun.0.historyAssetPath=research_pack/training/classifier/history.csv
classifierRun.0.historySha256=$classifierHistorySha
classifierRun.0.historyBytes=$classifierHistoryBytes
classifierRun.0.modelCount=1
classifierRun.0.model.0.displayName=Classifier
classifierRun.0.model.0.assetPath=research_pack/models/classifier.rklm
classifierRun.0.model.0.fileName=classifier.rklm
classifierRun.0.model.0.sha256=$classifierModelSha
classifierRun.0.model.0.bytes=$classifierModelBytes"""
    )

    private fun datasetOnlyCatalog(csvSha: String): String = """schemaVersion=1
packId=test-pack-v1
displayName=Test pack
datasetCount=1
dataset.0.id=example
dataset.0.displayName=Factory example
dataset.0.assetPath=research_pack/datasets/example.csv.gz
dataset.0.fileName=factory_example.csv
dataset.0.sha256=$csvSha
dataset.0.rawBytes=11
dataset.0.compression=GZIP
dataset.0.rowCount=1
dataset.0.robotIds=robot-a
dataset.0.samplesPerRobot=1
dataset.0.randomSeed=2604
dataset.0.targetMode=MIXED
dataset.0.reachableFraction=0.5
dataset.0.filterMode=ALL
dataset.0.maxIterations=120
dataset.0.tolerance=0.0001
dataset.0.damping=0.05
dataset.0.maxStep=0.05
dataset.0.randomProtocol=rkl-splitmix64-seed-v1
dataset.0.createdAtEpochMillis=1
oneMicronRunCount=0
classifierRunCount=0
"""

    private fun gzip(bytes: ByteArray): ByteArray = ByteArrayOutputStream().use { output ->
        GZIPOutputStream(output).use { it.write(bytes) }
        output.toByteArray()
    }

    private fun sha(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
}
