package com.robotkinematicslab.mobile.ui.charts.presentation

import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

class ChartFigureExporterTest {

    @Test
    fun safeFileStem_removesEmojiSpacesAndFileSeparators() {
        assertEquals(
            "training-error-model-a-b",
            ChartFigureExporter.safeFileStem("📈 Training error: model A / B")
        )
    }

    @Test
    fun safeFileStem_providesFallbackAndBoundsLength() {
        assertEquals("scientific-figure", ChartFigureExporter.safeFileStem("📈🧪"))
        assertTrue(ChartFigureExporter.safeFileStem("a".repeat(200)).length <= 64)
    }

    @Test
    fun automaticRelativeDirectory_organisesByFamilyDateExperimentAndType() {
        assertEquals(
            listOf("automatic", "training", "2026-09-10", "run-42", "execution-7", "box-plot")
                .joinToString(File.separator),
            ChartFigureExporter.automaticRelativeDirectory(
                collection = "Training",
                date = "2026-09-10",
                analysisId = "Run 42",
                executionId = "Execution 7",
                figureType = "Box plot"
            )
        )
    }

    @Test
    fun immutableDisplayName_neverOverwritesAnExistingFigure() {
        val directory = Files.createTempDirectory("figure-non-overwrite").toFile()
        val first = ChartFigureExporter.immutableDisplayName(directory, "macro-f1")
        File(directory, first).writeText("first immutable figure")
        val second = ChartFigureExporter.immutableDisplayName(directory, "macro-f1")

        assertEquals("macro-f1.png", first)
        assertEquals("macro-f1-2.png", second)
        assertEquals("first immutable figure", File(directory, first).readText())
    }

    @Test
    fun automaticExecutionIds_areUniqueAndRemainInTheirOwnPath() {
        val first = ChartFigureExporter.newAutomaticFigureExecutionId()
        val second = ChartFigureExporter.newAutomaticFigureExecutionId()

        assertNotEquals(first, second)
        val firstPath =
            ChartFigureExporter.automaticRelativeDirectory(
                collection = "training",
                date = "2026-09-10",
                analysisId = "analysis",
                executionId = first,
                figureType = "line"
            )
        val secondPath =
            ChartFigureExporter.automaticRelativeDirectory(
                collection = "training",
                date = "2026-09-10",
                analysisId = "analysis",
                executionId = second,
                figureType = "line"
            )
        assertNotEquals(firstPath, secondPath)
    }

    @Test
    fun manifest_roundTripsCompleteImmutableEntriesAndRejectsDuplicates() {
        val manifest = Files.createTempDirectory("figure-manifest").resolve("index.properties").toFile()
        val first = manifestEntry(relativePath = "automatic/training/run/line/figure-a.png", hash = "a".repeat(64))
        val second = manifestEntry(relativePath = "automatic/training/run/line/figure-b.png", hash = "b".repeat(64))

        ChartFigureExporter.appendManifestEntry(manifest, first)
        ChartFigureExporter.appendManifestEntry(manifest, second)

        assertEquals(listOf(first, second), ChartFigureExporter.readManifestEntries(manifest))
        assertThrows(IllegalArgumentException::class.java) {
            ChartFigureExporter.appendManifestEntry(manifest, first)
        }
    }

    @Test
    fun manifest_tracksExpectedGeneratedAndFailedOutcomesWithoutDuplicateRows() {
        val manifest = Files.createTempDirectory("figure-status-manifest").resolve("index.properties").toFile()
        val expected =
            manifestEntry(
                relativePath = "automatic/training/run/line/generated.png",
                hash = null,
                status = AutomaticFigureExportStatus.EXPECTED
            )
        ChartFigureExporter.appendManifestEntry(manifest, expected)
        ChartFigureExporter.appendManifestEntry(
            manifest,
            expected.copy(sha256 = "c".repeat(64), status = AutomaticFigureExportStatus.GENERATED)
        )

        val failed =
            manifestEntry(
                relativePath = "automatic/training/run/line/failed.png",
                hash = null,
                status = AutomaticFigureExportStatus.FAILED,
                failureMessage = "PNG compression failed"
            )
        ChartFigureExporter.appendManifestEntry(manifest, failed)

        val entries = ChartFigureExporter.readManifestEntries(manifest)
        assertEquals(2, entries.size)
        assertEquals(AutomaticFigureExportStatus.GENERATED, entries[0].status)
        assertEquals(AutomaticFigureExportStatus.FAILED, entries[1].status)
    }

    @Test
    fun manifest_rejectsCorruptHashesInsteadOfIndexingUnverifiableEvidence() {
        val manifest = Files.createTempDirectory("figure-corrupt-manifest").resolve("index.properties").toFile()

        assertThrows(IllegalArgumentException::class.java) {
            ChartFigureExporter.appendManifestEntry(
                manifest,
                manifestEntry(relativePath = "figure.png", hash = "not-a-sha256")
            )
        }
        assertTrue(ChartFigureExporter.readManifestEntries(manifest).isEmpty())
    }

    @Test
    fun automaticTrigger_changesForIntermediateDetailPresentationAndContext() {
        val points =
            (0 until 8_001).map { index ->
                ChartLinePoint(index.toDouble(), index.toDouble())
            }
        val baseline =
            ChartFigureExporter.automaticExportTrigger(
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataKey = points,
                presentationKey = "publication-lines-markers"
            )
        val changedMiddle = points.toMutableList().also { values ->
            values[4_003] = values[4_003].copy(y = 999_999.0)
        }
        val changedData =
            ChartFigureExporter.automaticExportTrigger(
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataKey = changedMiddle,
                presentationKey = "publication-lines-markers"
            )
        val changedPresentation =
            ChartFigureExporter.automaticExportTrigger(
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataKey = points,
                presentationKey = "publication-thin-lines-no-markers"
            )
        val changedContext =
            ChartFigureExporter.automaticExportTrigger(
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-2",
                title = "Macro-F1",
                figureType = "line",
                dataKey = points,
                presentationKey = "publication-lines-markers"
            )

        assertNotEquals(baseline.dataFingerprint, changedData.dataFingerprint)
        assertNotEquals(baseline.presentationFingerprint, changedPresentation.presentationFingerprint)
        assertNotEquals(baseline, changedContext)
        assertEquals(baseline.dataFingerprint, changedPresentation.dataFingerprint)
        assertEquals(baseline.presentationFingerprint, changedData.presentationFingerprint)
    }

    @Test
    fun interactive3dViews_areExplicitlyExcludedFromAutomaticStaticEvidence() {
        assertFalse(ChartFigureExporter.supportsAutomaticStaticExport("Interactive 3D Workspace"))
        assertFalse(ChartFigureExporter.supportsAutomaticStaticExport("3D Heat Map"))
        assertTrue(ChartFigureExporter.supportsAutomaticStaticExport("2D Acceptance Heat Map"))
    }

    @Test
    fun reusableGeneratedFigure_reusesTheVerifiedFinalFigureForTheSameRun() {
        val root = Files.createTempDirectory("figure-idempotency").toFile()
        val figure = File(root, "automatic/training/run/line/macro-f1.png")
        val figureDirectory = requireNotNull(figure.parentFile)
        figureDirectory.mkdirs()
        figure.writeBytes(byteArrayOf(1, 2, 3, 4))
        val manifest = File(requireNotNull(figureDirectory.parentFile), "figure-index.properties")
        ChartFigureExporter.appendManifestEntry(
            manifest,
            manifestEntry(
                relativePath = figure.relativeTo(root).path,
                hash = sha256(figure)
            )
        )

        val reused =
            ChartFigureExporter.reusableGeneratedFigure(
                manifestFile = manifest,
                figuresRoot = root,
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataFingerprint = "0123456789abcdef"
            )

        assertEquals(figure.canonicalFile, reused)
        assertEquals(1, figureDirectory.listFiles { file -> file.extension == "png" }?.size)
    }

    @Test
    fun reusableGeneratedFigure_rejectsEvidenceModifiedAfterFinalisation() {
        val root = Files.createTempDirectory("figure-tampering").toFile()
        val figure = File(root, "automatic/training/run/line/macro-f1.png")
        val figureDirectory = requireNotNull(figure.parentFile)
        figureDirectory.mkdirs()
        figure.writeText("original scientific evidence")
        val manifest = File(requireNotNull(figureDirectory.parentFile), "figure-index.properties")
        ChartFigureExporter.appendManifestEntry(
            manifest,
            manifestEntry(
                relativePath = figure.relativeTo(root).path,
                hash = sha256(figure)
            )
        )
        figure.writeText("modified after finalisation")

        assertThrows(IllegalStateException::class.java) {
            ChartFigureExporter.reusableGeneratedFigure(
                manifestFile = manifest,
                figuresRoot = root,
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataFingerprint = "0123456789abcdef"
            )
        }
    }

    @Test
    fun reusableGeneratedFigure_rejectsManifestPathsOutsideTheFigureLibrary() {
        val root = Files.createTempDirectory("figure-path-boundary").toFile()
        val outside = Files.createTempFile("outside-figure", ".png").toFile()
        outside.writeText("not project evidence")
        val manifest = File(root, "automatic/training/run/figure-index.properties")
        ChartFigureExporter.appendManifestEntry(
            manifest,
            manifestEntry(
                relativePath = outside.absolutePath,
                hash = sha256(outside)
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            ChartFigureExporter.reusableGeneratedFigure(
                manifestFile = manifest,
                figuresRoot = root,
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataFingerprint = "0123456789abcdef"
            )
        }
    }

    @Test
    fun reusableGeneratedFigureAcrossDates_reusesAnExecutionArchivedOnAnEarlierDay() {
        val root = Files.createTempDirectory("figure-cross-day-idempotency").toFile()
        val relativePath =
            "automatic/training/2026-09-09/analysis-1/run-1/line/macro-f1.png"
        val figure = File(root, relativePath)
        val figureDirectory = requireNotNull(figure.parentFile)
        figureDirectory.mkdirs()
        figure.writeText("yesterday's final figure")
        val manifest =
            File(
                File(File(root, "automatic/training/2026-09-09/analysis-1"), "run-1"),
                "figure-index.properties"
            )
        ChartFigureExporter.appendManifestEntry(
            manifest,
            manifestEntry(relativePath = relativePath, hash = sha256(figure))
        )

        val reused =
            ChartFigureExporter.reusableGeneratedFigureAcrossDates(
                figuresRoot = root,
                collection = "training",
                analysisId = "analysis-1",
                executionId = "run-1",
                title = "Macro-F1",
                figureType = "line",
                dataFingerprint = "0123456789abcdef"
            )

        assertEquals(figure.canonicalFile, reused)
    }

    private fun manifestEntry(
        relativePath: String,
        hash: String?,
        status: AutomaticFigureExportStatus = AutomaticFigureExportStatus.GENERATED,
        failureMessage: String? = null
    ) =
        AutomaticFigureManifestEntry(
            title = "Macro-F1",
            collection = "training",
            analysisId = "analysis-1",
            executionId = "run-1",
            figureType = "line",
            relativePath = relativePath,
            sha256 = hash,
            dataFingerprint = "0123456789abcdef",
            exportedAtEpochMillis = 1_789_000_000_000L,
            status = status,
            failureMessage = failureMessage
        )

    private fun sha256(file: File): String =
        MessageDigest.getInstance("SHA-256")
            .digest(file.readBytes())
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
}
