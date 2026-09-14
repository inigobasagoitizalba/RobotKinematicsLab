package com.robotkinematicslab.mobile.ui.charts.spatial

import com.robotkinematicslab.mobile.math.utility.Vec3
import com.robotkinematicslab.mobile.render.buildRobotSceneViewport
import com.robotkinematicslab.mobile.render.projectRobotScenePoint
import com.robotkinematicslab.mobile.render.targetYFromSlider
import com.robotkinematicslab.mobile.ui.launch.buildLaunchSceneFrame
import java.io.File
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Anti-corruption contracts for the animated and interactive Canvas surfaces.
 *
 * Pixel delivery and gesture dispatch still require Android instrumentation, but these tests keep
 * the pure frame/camera mappings and the Compose wiring from collapsing into a single snapshot.
 */
class InteractiveCanvasMotionContractTest {

    @Test
    fun launchChoreographyProducesManyDistinctContinuousFrames() {
        val frames =
            List(181) { index ->
                buildLaunchSceneFrame(index.toDouble() / 181.0)
            }

        assertEquals(181, frames.distinct().size)
        frames.zipWithNext().forEachIndexed { index, (before, after) ->
            val displacement = (after.dataCore - before.dataCore).norm()
            assertTrue("Launch frame $index did not advance", displacement > 1e-6)
            assertTrue("Launch frame $index jumped discontinuously", displacement < 0.03)
        }
    }

    @Test
    fun workspaceProjectionRespondsToEveryContinuousCameraStep() {
        val bounds =
            WorkspaceBounds3D(
                minimum = Vec3(-2.0, -2.0, -2.0),
                maximum = Vec3(2.0, 2.0, 2.0),
                center = Vec3.ZERO,
                radius = 2.0
            )
        val point = Vec3(0.83, 0.41, -0.57)
        val projected =
            List(121) { index ->
                val fraction = index / 120f
                projectWorkspacePoint(
                    point = point,
                    bounds = bounds,
                    widthPx = 900f,
                    heightPx = 620f,
                    camera =
                        WorkspaceCamera(
                            yaw = -1.1f + fraction * 2.2f,
                            pitch = -0.5f + fraction,
                            zoom = 0.75f + fraction * 1.5f
                        )
                )
            }

        assertEquals(121, projected.distinct().size)
        projected.zipWithNext().forEachIndexed { index, (before, after) ->
            assertTrue(
                "Camera step $index produced a frozen projection",
                abs(after.x - before.x) + abs(after.y - before.y) > 1e-4f
            )
        }
    }

    @Test
    fun robotTargetSliderAndCameraConsumeEveryIntermediatePosition() {
        val target = Vec3(0.25, 0.0, -0.4)
        val sliderValues =
            List(101) { index ->
                targetYFromSlider(
                    touchY = 20f + index * 2.8f,
                    sliderTop = 20f,
                    sliderBottom = 300f,
                    currentTarget = target,
                    workspaceRadius = 2.5
                ).y
            }
        assertEquals(101, sliderValues.distinct().size)
        assertTrue(sliderValues.zipWithNext().all { (before, after) -> after < before })

        val viewport = buildRobotSceneViewport(720f, 480f, 2.5)
        val cameraFrames =
            List(101) { index ->
                val fraction = index / 100f
                projectRobotScenePoint(
                    target,
                    viewport,
                    yaw = -0.9f + fraction * 1.8f,
                    pitch = -0.4f + fraction * 0.8f
                )
            }
        assertEquals(101, cameraFrames.distinct().size)
    }

    @Test
    fun envelopeTimelineMapsTheWholeSliderToManyFramesAndEveryStage() {
        val frames =
            List(201) { index ->
                val progress = index / 200.0
                workspaceEnvelopeConstructionStage(progress) to
                    workspaceEnvelopeStageFraction(progress)
            }

        assertTrue("Construction timeline collapsed to too few frames", frames.distinct().size > 190)
        assertEquals(
            WorkspaceEnvelopeConstructionStage.entries.toSet(),
            frames.map { it.first }.toSet()
        )
    }

    @Test
    fun everyInteractive3dCanvasKeepsContinuousGestureWiring() {
        val robotScene = normalizedSource("render/RobotScene3D.kt")
        assertContainsAll(
            "RobotScene3D",
            robotScene,
            "Canvas(",
            "detectDragGestures",
            "yaw += dragAmount.x",
            "pitch = (pitch - dragAmount.y",
            "targetYFromSlider(",
            "onTargetSelected(updated)"
        )

        val diagnosticScene = normalizedSource("ui/charts/spatial/WorkspaceScene3D.kt")
        val robotWorkspaceScene = normalizedSource("ui/charts/spatial/RobotWorkspaceScene3D.kt")
        listOf(
            "WorkspaceScene3D" to diagnosticScene,
            "RobotWorkspaceScene3D" to robotWorkspaceScene
        ).forEach { (name, source) ->
            assertContainsAll(
                name,
                source,
                "Canvas(",
                "rememberUpdatedState(camera)",
                ".pointerInput(Unit)",
                "detectTransformGestures",
                "val cameraAtEvent = currentCamera",
                "yaw = cameraAtEvent.yaw +",
                "pitch = (cameraAtEvent.pitch -",
                "zoom = (cameraAtEvent.zoom * zoom)",
                "currentOnCameraChange("
            )
        }

        val launchScene = normalizedSource("ui/launch/AppLaunchScreen.kt")
        assertContainsAll(
            "LaunchWireframeScene",
            launchScene,
            "rememberInfiniteTransition",
            "infiniteRepeatable(",
            "List(LAUNCH_FRAME_COUNT)",
            "LAUNCH_FRAME_COUNT = 540",
            "detectDragGestures",
            "manualYaw = normalizedLaunchYaw(manualYaw + dragAmount.x"
        )
    }

    @Test
    fun workspacePlaybackKeepsARealLoopPauseResumeAndContinuousSliders() {
        val source = normalizedSource("ui/workspace/RobotWorkspacePanel.kt")

        assertContainsAll(
            "RobotWorkspacePanel playback",
            source,
            "while (isActive && playing)",
            "delay(WORKSPACE_PLAYBACK_FRAME_INTERVAL_MILLIS)",
            "WORKSPACE_PLAYBACK_FRAME_INTERVAL_MILLIS = 33L",
            "val elapsedSeconds =",
            "constructionProgress = (constructionProgress + elapsedSeconds",
            "playbackPosition = (playbackPosition + playbackRate * elapsedSeconds)",
            "if (playing) { playing = false } else {",
            "playing = true",
            "value = constructionProgress.toFloat()",
            "constructionProgress = it.toDouble()",
            "value = playbackPosition.toFloat()",
            "playbackPosition = it.toDouble()"
        )
        assertTrue(
            "Both sliders must pause before applying a manually selected frame",
            Regex("onValueChange = \\{ playing = false (constructionProgress|playbackPosition) = it\\.toDouble\\(\\)")
                .findAll(source)
                .count() >= 2
        )
    }

    @Test
    fun everyProfessionalCanvasKeepsItsTapAndContinuousPanZoomWiring() {
        val families =
            listOf(
                "boxplotcharts/BoxPlot",
                "heatmaps/HeatMap",
                "histogramcharts/Histogram",
                "horizontalbarcharts/HorizontalBar",
                "linecharts/LineChart",
                "scattercharts/Scatter",
                "timelinecharts/Timeline"
            )

        families.forEach { family ->
            val directory = family.substringBeforeLast('/')
            val stem = family.substringAfterLast('/')
            val inspector = normalizedSource("ui/charts/advanced/$directory/${stem}InspectorDialog.kt")
            assertContainsAll(
                "$stem inspector",
                inspector,
                ".pointerInput(Unit)",
                "detectTransformGestures",
                "scale = newScale"
            )

            if (stem != "HeatMap") {
                val canvas = normalizedSource("ui/charts/advanced/$directory/${stem}Canvas.kt")
                assertContainsAll("$stem canvas", canvas, ".pointerInput(", "detectTapGestures")
            }
        }

        val stickyHeatMap = normalizedSource("ui/charts/advanced/heatmaps/StickyHeatMapCanvas.kt")
        assertContainsAll("StickyHeatMap canvas", stickyHeatMap, ".pointerInput(", "detectTapGestures")

        val processTimeline = normalizedSource("ui/shared/progress/DiagnosticProcessTimeline.kt")
        assertContainsAll(
            "ui/shared/progress/DiagnosticProcessTimeline.kt",
            processTimeline,
            "Canvas(",
            ".combinedClickable(",
            "role = Role.Button"
        )
        val telemetryDashboard = normalizedSource("ui/shared/progress/DiagnosticTelemetryDashboard.kt")
        assertContainsAll(
            "ui/shared/progress/DiagnosticTelemetryDashboard.kt",
            telemetryDashboard,
            "Canvas(",
            ".pointerInput(",
            "detectTapGestures"
        )
    }

    private fun normalizedSource(relativePath: String): String =
        locateSource(relativePath).readText().replace(Regex("\\s+"), " ").trim()

    private fun locateSource(relativePath: String): File {
        val suffix = "app/src/main/java/com/robotkinematicslab/mobile/$relativePath"
        val start = File(requireNotNull(System.getProperty("user.dir")))
        return generateSequence(start) { it.parentFile }
            .take(8)
            .map { root -> File(root, suffix) }
            .firstOrNull(File::isFile)
            ?: error("Cannot locate production source $suffix from ${start.absolutePath}")
    }

    private fun assertContainsAll(name: String, source: String, vararg expected: String) {
        expected.forEach { fragment ->
            assertTrue("$name lost its interactive motion contract: $fragment", source.contains(fragment))
        }
    }
}
