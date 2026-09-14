package com.robotkinematicslab.mobile.ui.charts.presentation

import android.graphics.BitmapFactory
import android.provider.MediaStore
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.linecharts.ProfessionalLineChart
import com.robotkinematicslab.mobile.storage.AppStoragePaths
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ChartPresentationUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun chartFigureControl_updatesGlobalPresetAndCustomLayers() {
        var currentPreferences by mutableStateOf(ChartPresentationPreferences.explorationDefaults())

        composeRule.setContent {
            MaterialTheme {
                ProvideChartPresentation(
                    controller =
                        ChartPresentationController(
                            preferences = currentPreferences,
                            update = { currentPreferences = it }
                        )
                ) {
                    ProfessionalLineChart(
                        title = "Training reliability",
                        subtitle = "Three-seed mean",
                        points =
                            listOf(
                                ChartLinePoint(108.0, 0.49),
                                ChartLinePoint(383.0, 0.62)
                            ),
                        xAxisLabel = "Feature count",
                        yAxisLabel = "Macro-F1",
                        color = Color.Blue
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chart-figure-button:Training reliability").performClick()
        composeRule.onNodeWithTag(CHART_PRESENTATION_DIALOG_TAG).assertIsDisplayed()
        composeRule.onNodeWithTag("chart-preset:publication").performClick()

        composeRule.runOnIdle {
            assertEquals(ChartPresentationPreset.PUBLICATION, currentPreferences.preset)
            assertFalse(currentPreferences.showGrid)
            assertTrue(currentPreferences.showLines)
        }

        composeRule.onNodeWithTag("chart-layer:Gridlines").performClick()

        composeRule.runOnIdle {
            assertEquals(ChartPresentationPreset.CUSTOM, currentPreferences.preset)
            assertTrue(currentPreferences.showGrid)
        }
    }

    @Test
    fun layerRowExposesOneCheckboxDecisionInsteadOfAButtonWithNestedControl() {
        composeRule.setContent {
            MaterialTheme {
                ChartPresentationSettingsContent(
                    preferences = ChartPresentationPreferences.explorationDefaults(),
                    onPreferencesChange = {}
                )
            }
        }

        composeRule.onNodeWithTag("chart-layer:Gridlines")
            .assert(
                SemanticsMatcher.expectValue(
                    SemanticsProperties.Role,
                    Role.Checkbox
                )
            )
    }

    @Test
    fun savePng_capturesTheRenderedFigureAndWritesItToAndroidPictures() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefix = "export-instrumentation"
        deleteExportFixtures(prefix)

        composeRule.setContent {
            MaterialTheme {
                ProvideChartPresentation(
                    controller =
                        ChartPresentationController(
                            // Verify the explicit gallery artifact below independently of
                            // automatic project-library archival and its shared status label.
                            preferences =
                                ChartPresentationPreferences.publicationDefaults()
                                    .copy(autoSaveFigures = false),
                            update = {}
                        )
                ) {
                    ProfessionalLineChart(
                        title = "Export instrumentation",
                        subtitle = "PNG export fixture",
                        points =
                            listOf(
                                ChartLinePoint(0.0, 0.2),
                                ChartLinePoint(1.0, 0.8)
                            ),
                        xAxisLabel = "Time (s)",
                        yAxisLabel = "Score",
                        color = Color.Blue
                    )
                }
            }
        }

        composeRule.onNodeWithTag("chart-figure-button:Export instrumentation").performClick()
        composeRule.onNodeWithTag("chart-export-png").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            // Shared status can also describe automatic archival. Wait for this manual PNG.
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ? AND ${MediaStore.Images.Media.IS_PENDING} = 0",
                arrayOf("$prefix%"),
                null
            )?.use { it.count > 0 } == true &&
                composeRule.onAllNodesWithTag("chart-export-status").fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("chart-export-status")
            .assertIsDisplayed()
            .assertTextContains("Figure saved.")

        val exportedImages =
            context.contentResolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Images.Media._ID),
                "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
                arrayOf("$prefix%"),
                null
            )?.use { cursor ->
                buildList {
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext()) {
                        add(
                            android.content.ContentUris.withAppendedId(
                                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                                cursor.getLong(idColumn)
                            )
                        )
                    }
                }
            }.orEmpty()
        assertTrue(exportedImages.isNotEmpty())
        val decodedBitmap =
            context.contentResolver.openInputStream(exportedImages.first()).use(BitmapFactory::decodeStream)
        assertTrue(decodedBitmap != null && decodedBitmap.width > 0 && decodedBitmap.height > 0)

        deleteExportFixtures(prefix)
    }

    @Test
    fun automaticPng_isGroupedWithItsScientificProvenanceSidecar() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val collection = "stress confirmation"
        val analysisId = "grouped export"
        val executionId = "instrumented-${System.nanoTime()}"
        val figureType = "line chart"
        val dataFingerprint = ChartFigureExporter.automaticDataFingerprint("grouped export")
        val relativeDirectory =
            ChartFigureExporter.automaticRelativeDirectory(
                collection = collection,
                date = LocalDate.now().toString(),
                analysisId = analysisId,
                executionId = executionId,
                figureType = figureType
            )
        val destination = AppStoragePaths(context).figuresDirectory.resolve(relativeDirectory)

        val result =
            runBlocking {
                ChartFigureExporter.saveAutomaticPng(
                    context = context,
                    title = "Automatic Grouping Verification",
                    collection = collection,
                    analysisId = analysisId,
                    executionId = executionId,
                    figureType = figureType,
                    dataFingerprint = dataFingerprint,
                    image = ImageBitmap(16, 16)
                )
            }

        assertTrue(result.isSuccess)
        val exported = result.getOrThrow()
        val png = AppStoragePaths(context).figuresDirectory.resolve(requireNotNull(exported.relativePath))
        val sidecar = File(png.parentFile, "${png.name.removeSuffix(".png")}.properties")
        assertEquals(destination.canonicalFile, png.parentFile?.canonicalFile)
        assertTrue(png.isFile && png.length() > 0L)
        assertTrue(sidecar.isFile && sidecar.length() > 0L)
        val metadata = java.util.Properties().apply { sidecar.inputStream().use(::load) }
        assertEquals("automatic-final-figure", metadata.getProperty("scientificRole"))
        assertEquals(collection, metadata.getProperty("collection"))
        assertEquals(analysisId, metadata.getProperty("analysisId"))
        assertEquals(executionId, metadata.getProperty("executionId"))
        assertEquals(figureType, metadata.getProperty("figureType"))
        assertEquals(dataFingerprint, metadata.getProperty("dataFingerprint"))

        png.delete()
        sidecar.delete()
    }

    private fun deleteExportFixtures(prefix: String) {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        context.contentResolver.delete(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?",
            arrayOf("$prefix%")
        )
    }
}
