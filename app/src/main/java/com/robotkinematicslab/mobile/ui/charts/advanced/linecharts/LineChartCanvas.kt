package com.robotkinematicslab.mobile.ui.charts.advanced.linecharts

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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.charts.advanced.ChartLinePoint
import com.robotkinematicslab.mobile.ui.charts.advanced.drawChartGrid
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale

@Composable
internal fun LineChartCanvas(
    points: List<ChartLinePoint>,
    stats: LineChartStats,
    xAxisLabel: String,
    yAxisLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetX: Float = 0f,
    offsetY: Float = 0f,
    showAxisLabels: Boolean = true,
    showPointLabels: Boolean = false,
    showGridLines: Boolean = true,
    showLines: Boolean = true,
    showMarkers: Boolean = true,
    publicationMode: Boolean = false,
    onPointSelected: ((LineChartSelection) -> Unit)? = null,
    referenceDiagonal: Boolean = false
) {
    val cleanPoints =
        remember(points) {
            cleanLineChartPoints(points)
        }
    val safeScale = safeChartScale(scale, 24f)

    val interactiveModifier =
        if (onPointSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanPoints, stats, safeScale, offsetX, offsetY, showPointLabels) {
                detectTapGestures { tap ->
                    findLineChartSelection(
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
                        plotInset = (if (showPointLabels) 18.dp else 8.dp).toPx(),
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

        val plotInset =
            if (showPointLabels) {
                18.dp.toPx()
            } else {
                8.dp.toPx()
            }

        val plotWidth =
            (chartWidth - plotInset * 2f)
                .coerceAtLeast(1f)

        val plotHeight =
            (chartHeight - plotInset * 2f)
                .coerceAtLeast(1f)

        val zoomedWidth =
            plotWidth * safeScale

        val zoomedHeight =
            plotHeight * safeScale

        val clampedOffsetX =
            hardClampLineChartOffset(
                offset = offsetX,
                viewportSize = plotWidth,
                contentSize = zoomedWidth
            )

        val clampedOffsetY =
            hardClampLineChartOffset(
                offset = offsetY,
                viewportSize = plotHeight,
                contentSize = zoomedHeight
            )

        val visibleWindow =
            computeVisibleLineChartWindow(
                stats = stats,
                chartWidth = plotWidth,
                chartHeight = plotHeight,
                zoomedWidth = zoomedWidth,
                zoomedHeight = zoomedHeight,
                offsetX = clampedOffsetX,
                offsetY = clampedOffsetY
            )

        drawRoundLineChartBackground(
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
                left = bodyLeft + plotInset + clampedOffsetX,
                top = bodyTop + plotInset + clampedOffsetY,
                width = zoomedWidth,
                height = zoomedHeight,
                showGridLines = showGridLines
            )

            val pointRadius =
                lineChartPointRadiusDp(
                    pointCount = cleanPoints.size,
                    scale = safeScale
                ).dp.toPx()

            val strokeWidth =
                lineChartStrokeWidthDp(
                    pointCount = cleanPoints.size,
                    scale = safeScale
                ).dp.toPx()

            val drawMarkersForCurrentDensity =
                showMarkers &&
                    (
                        cleanPoints.size <= 1 ||
                            zoomedWidth / (cleanPoints.size - 1).coerceAtLeast(1) >= 4.dp.toPx()
                    )

            if(referenceDiagonal) {
                drawLine(color=Color.Gray,
                    start=Offset(bodyLeft+plotInset+clampedOffsetX,bodyTop+plotInset+clampedOffsetY+zoomedHeight),
                    end=Offset(bodyLeft+plotInset+clampedOffsetX+zoomedWidth,bodyTop+plotInset+clampedOffsetY),
                    strokeWidth=2.dp.toPx(),pathEffect=androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8.dp.toPx(),6.dp.toPx())))
            }
            val path =
                Path()

            var hasStartedPath =
                false

            cleanPoints.forEach { point ->
                val x =
                    bodyLeft +
                            plotInset +
                            clampedOffsetX +
                            zoomedWidth * lineChartNormalizedPosition(point.x, stats.minX, stats.maxX)

                val y =
                    bodyTop +
                            plotInset +
                            clampedOffsetY +
                            zoomedHeight -
                            zoomedHeight * lineChartNormalizedPosition(point.y, stats.minY, stats.maxY)

                if (!hasStartedPath || point.breakBefore) {
                    path.moveTo(
                        x = x,
                        y = y
                    )
                    hasStartedPath = true
                } else {
                    path.lineTo(
                        x = x,
                        y = y
                    )
                }
            }

            if (showLines) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(
                        width = strokeWidth
                    )
                )
            }

            val occupiedLabels = mutableListOf<com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect>()
            if (drawMarkersForCurrentDensity) cleanPoints.forEachIndexed { index, point ->
                val x =
                    bodyLeft +
                            plotInset +
                            clampedOffsetX +
                            zoomedWidth * lineChartNormalizedPosition(point.x, stats.minX, stats.maxX)

                val y =
                    bodyTop +
                            plotInset +
                            clampedOffsetY +
                            zoomedHeight -
                            zoomedHeight * lineChartNormalizedPosition(point.y, stats.minY, stats.maxY)

                if (
                    x >= bodyLeft - pointRadius &&
                    x <= bodyLeft + chartWidth + pointRadius &&
                    y >= bodyTop - pointRadius &&
                    y <= bodyTop + chartHeight + pointRadius
                ) {
                    drawCircle(
                        color = color,
                        radius = pointRadius,
                        center = Offset(
                            x = x,
                            y = y
                        )
                    )

                    if (showPointLabels) {
                        drawLinePointLabel(
                            label = formatLineChartDouble(point.y, digits = 3),
                            x = x,
                            y = y,
                            bounds = com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect(bodyLeft, bodyTop, bodyLeft + chartWidth, bodyTop + chartHeight),
                            occupied = occupiedLabels,
                            color = Color(0xFF202124)
                        )
                    }
                }
            }

            canvas.nativeCanvas.restore()
        }

        if (showAxisLabels) {
            drawLineChartAxisLayer(
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

private fun DrawScope.drawRoundLineChartBackground(
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

private fun DrawScope.drawLinePointLabel(
    label: String,
    x: Float,
    y: Float,
    bounds: com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect,
    occupied: MutableList<com.robotkinematicslab.mobile.ui.charts.advanced.ChartLabelRect>,
    color: Color
) {
    if (label.isBlank()) {
        return
    }

    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        paint.textAlign = Paint.Align.LEFT
        paint.textSize = 10.dp.toPx()
        val rect = com.robotkinematicslab.mobile.ui.charts.advanced.placeChartLabel(x, y, paint.measureText(label), paint.fontMetrics.descent - paint.fontMetrics.ascent, bounds, occupied, 6.dp.toPx())
        if(rect != null) {
            occupied += rect
            canvas.nativeCanvas.drawText(label, rect.left, rect.top - paint.fontMetrics.ascent, paint)
        }
    }
}
