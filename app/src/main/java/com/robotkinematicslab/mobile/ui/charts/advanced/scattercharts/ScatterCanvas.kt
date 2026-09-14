package com.robotkinematicslab.mobile.ui.charts.advanced.scattercharts

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartPoint
import com.robotkinematicslab.mobile.ui.charts.advanced.drawChartGrid
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun ScatterCanvas(
    points: List<ChartPoint>,
    stats: ScatterStats,
    xAxisLabel: String,
    yAxisLabel: String,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    showAxisLabels: Boolean = true,
    showPointLabels: Boolean = false,
    onPointSelected: ((ScatterSelection) -> Unit)? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val cleanPoints =
        remember(points) {
            cleanScatterPoints(points)
        }
    val safeScale = safeChartScale(scale, 24f)

    val interactiveModifier =
        if (onPointSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanPoints, stats, safeScale, offsetX, offsetY) {
                detectTapGestures { tap ->
                    findScatterSelection(
                        points = cleanPoints,
                        stats = stats,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = safeScale,
                        offsetX = offsetX,
                        offsetY = offsetY,
                        leftPadding = 46.dp.toPx(),
                        rightPadding = 18.dp.toPx(),
                        topPadding = 20.dp.toPx(),
                        bottomPadding = 42.dp.toPx(),
                        maximumDistance = 28.dp.toPx()
                    )?.let(onPointSelected)
                }
            }
        }

    Canvas(
        modifier = interactiveModifier
    ) {
        val leftPadding =
            if (showAxisLabels) {
                46.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val rightPadding =
            if (showAxisLabels) {
                18.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val topPadding =
            if (showAxisLabels) {
                20.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val bottomPadding =
            if (showAxisLabels) {
                42.dp.toPx()
            } else {
                12.dp.toPx()
            }

        val chartWidth =
            (size.width - leftPadding - rightPadding)
                .coerceAtLeast(1f)

        val chartHeight =
            (size.height - topPadding - bottomPadding)
                .coerceAtLeast(1f)

        val bodyLeft =
            leftPadding

        val bodyTop =
            topPadding

        val zoomedWidth =
            chartWidth * safeScale

        val zoomedHeight =
            chartHeight * safeScale

        val clampedOffsetX =
            hardClampScatterOffset(
                offset = offsetX,
                viewportSize = chartWidth,
                contentSize = zoomedWidth
            )

        val clampedOffsetY =
            hardClampScatterOffset(
                offset = offsetY,
                viewportSize = chartHeight,
                contentSize = zoomedHeight
            )

        val visibleWindow =
            computeVisibleScatterWindow(
                stats = stats,
                chartWidth = chartWidth,
                chartHeight = chartHeight,
                zoomedWidth = zoomedWidth,
                zoomedHeight = zoomedHeight,
                offsetX = clampedOffsetX,
                offsetY = clampedOffsetY
            )

        drawRoundScatterBackground(
            left = 0f,
            top = 0f,
            width = size.width,
            height = size.height,
            publicationMode = publicationMode
        )

        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.clipRect(
                bodyLeft,
                bodyTop,
                bodyLeft + chartWidth,
                bodyTop + chartHeight
            )

            drawChartGrid(
                left = bodyLeft + clampedOffsetX,
                top = bodyTop + clampedOffsetY,
                width = zoomedWidth,
                height = zoomedHeight,
                showGridLines = presentation.showGrid
            )

            val radius =
                scatterPointRadiusDp(
                    pointCount = cleanPoints.size,
                    scale = safeScale
                ).dp.toPx()

            val occupiedLabels = mutableListOf<com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect>()
            val labelBounds = com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect(bodyLeft, bodyTop, bodyLeft + chartWidth, bodyTop + chartHeight)
            cleanPoints.forEachIndexed { index, point ->
                val x =
                    bodyLeft +
                            clampedOffsetX +
                            zoomedWidth * ((point.x - stats.minX) / stats.xRange).toFloat()

                val y =
                    bodyTop +
                            clampedOffsetY +
                            zoomedHeight -
                            zoomedHeight * ((point.y - stats.minY) / stats.yRange).toFloat()

                if (
                    x >= bodyLeft - radius &&
                    x <= bodyLeft + chartWidth + radius &&
                    y >= bodyTop - radius &&
                    y <= bodyTop + chartHeight + radius
                ) {
                    drawCircle(
                        color = point.color,
                        radius = radius,
                        center = Offset(
                            x = x,
                            y = y
                        )
                    )

                    if (
                        (showPointLabels && safeScale >= 2.0f) ||
                        (presentation.showValueLabels && cleanPoints.size <= 24)
                    ) {
                        drawPointLabel(
                            label = point.label.ifBlank { "P${index + 1}" },
                            x = x,
                            y = y,
                            color = Color(0xFF202124),
                            bounds = labelBounds,
                            occupied = occupiedLabels
                        )
                    }
                }
            }

            canvas.nativeCanvas.restore()
        }

        if (showAxisLabels) {
            drawScatterAxisLayer(
                xAxisLabel = xAxisLabel,
                yAxisLabel = yAxisLabel,
                left = bodyLeft,
                top = bodyTop,
                width = chartWidth,
                height = chartHeight,
                visibleWindow = visibleWindow
            )
        }
    }
}

private fun DrawScope.drawRoundScatterBackground(
    left: Float,
    top: Float,
    width: Float,
    height: Float,
    publicationMode: Boolean
) {
    drawRoundRect(
        color = if (publicationMode) Color.White else Color(0xFFF0F2F5),
        topLeft = Offset(
            x = left,
            y = top
        ),
        size = Size(
            width = width,
            height = height
        ),
        cornerRadius = CornerRadius(
            x = 12.dp.toPx(),
            y = 12.dp.toPx()
        )
    )
}

private fun DrawScope.drawPointLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color,
    bounds: com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect,
    occupied: MutableList<com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect>
) {
    if (label.isBlank()) {
        return
    }

    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.LEFT
                textSize = 10.dp.toPx()
                this.color = color.toArgb()
            }

        val rect = com.robotkinematicslab.mobile.ui.charts.advanced.placeChartLabel(x, y,
            paint.measureText(label), paint.fontMetrics.descent - paint.fontMetrics.ascent, bounds, occupied, 6.dp.toPx())
        if(rect != null) {
            occupied += rect
            canvas.nativeCanvas.drawText(label, rect.left, rect.top - paint.fontMetrics.ascent, paint)
        }
    }
}

internal fun hardClampScatterOffset(
    offset: Float,
    viewportSize: Float,
    contentSize: Float
): Float {
    if (
        !offset.isFinite() ||
        !viewportSize.isFinite() ||
        !contentSize.isFinite() ||
        viewportSize <= 0f ||
        contentSize <= 0f
    ) {
        return 0f
    }

    if (contentSize <= viewportSize) {
        return 0f
    }

    val minOffset =
        viewportSize - contentSize

    val maxOffset =
        0f

    return offset.coerceIn(
        minimumValue = minOffset,
        maximumValue = maxOffset
    )
}
