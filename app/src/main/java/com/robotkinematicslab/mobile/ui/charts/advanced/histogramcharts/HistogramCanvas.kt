package com.robotkinematicslab.mobile.ui.charts.advanced.histogramcharts

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
import com.robotkinematicslab.mobile.ui.charts.advanced.drawChartGrid
import com.robotkinematicslab.mobile.ui.charts.advanced.safeChartScale
import com.robotkinematicslab.mobile.ui.charts.presentation.ChartPresentationPreset
import com.robotkinematicslab.mobile.ui.charts.presentation.LocalChartPresentationController

@Composable
internal fun HistogramCanvas(
    bins: List<HistogramBin>,
    stats: HistogramStats,
    xAxisLabel: String,
    yAxisLabel: String,
    color: Color,
    modifier: Modifier = Modifier,
    scale: Float = 1f,
    offsetX: Float = 0f,
    showAxisLabels: Boolean = true,
    showBinLabels: Boolean = false,
    onBinSelected: ((HistogramSelection) -> Unit)? = null
) {
    val presentation = LocalChartPresentationController.current.preferences
    val publicationMode = presentation.preset == ChartPresentationPreset.PUBLICATION
    val cleanBins =
        remember(bins) {
            bins.filter {
                it.count >= 0 &&
                        it.start.isFinite() &&
                        it.end.isFinite()
            }
        }
    val safeScale = safeChartScale(scale, 24f)

    val interactiveModifier =
        if (onBinSelected == null || !showAxisLabels) {
            modifier
        } else {
            modifier.pointerInput(cleanBins, safeScale, offsetX) {
                detectTapGestures { tap ->
                    findHistogramSelection(
                        bins = cleanBins,
                        tapX = tap.x,
                        tapY = tap.y,
                        viewportWidth = size.width.toFloat(),
                        viewportHeight = size.height.toFloat(),
                        scale = safeScale,
                        offsetX = offsetX,
                        leftPadding = 46.dp.toPx(),
                        rightPadding = 18.dp.toPx(),
                        topPadding = 24.dp.toPx(),
                        bottomPadding = 42.dp.toPx()
                    )?.let(onBinSelected)
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
                24.dp.toPx()
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

        val clampedOffsetX =
            hardClampHistogramOffset(
                offset = offsetX,
                viewportSize = chartWidth,
                contentSize = zoomedWidth
            )

        val visibleWindow =
            computeVisibleHistogramWindow(
                bins = cleanBins,
                chartWidth = chartWidth,
                zoomedWidth = zoomedWidth,
                offsetX = clampedOffsetX,
                maxY = stats.maxBinCount
            )

        drawRoundHistogramBackground(
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
                top = bodyTop,
                width = zoomedWidth,
                height = chartHeight,
                showGridLines = presentation.showGrid
            )

            val binCount =
                cleanBins.size.coerceAtLeast(1)

            val gap =
                when {
                    safeScale >= 4f -> 3.dp.toPx()
                    binCount <= 20 -> 3.dp.toPx()
                    binCount <= 50 -> 2.dp.toPx()
                    else -> 1.dp.toPx()
                }

            val rawBarWidth =
                zoomedWidth / binCount.toFloat()

            val visibleBarCount =
                (chartWidth / rawBarWidth)
                    .toInt()
                    .coerceAtLeast(1)

            val corner =
                histogramBarCornerDp(
                    visibleBinCount = visibleBarCount
                ).dp.toPx()

            cleanBins.forEachIndexed { index, bin ->
                val left =
                    bodyLeft +
                            clampedOffsetX +
                            index.toFloat() * rawBarWidth +
                            gap / 2f

                val right =
                    bodyLeft +
                            clampedOffsetX +
                            (index + 1).toFloat() * rawBarWidth -
                            gap / 2f

                if (right >= bodyLeft && left <= bodyLeft + chartWidth) {
                    val barWidth =
                        (right - left).coerceAtLeast(1f)

                    val normalizedHeight =
                        bin.count.toFloat() / stats.maxBinCount.coerceAtLeast(1).toFloat()

                    val barHeight =
                        chartHeight * normalizedHeight

                    val top =
                        bodyTop + chartHeight - barHeight

                    drawRoundRect(
                        color = color,
                        topLeft = Offset(
                            x = left,
                            y = top
                        ),
                        size = Size(
                            width = barWidth,
                            height = barHeight.coerceAtLeast(0f)
                        ),
                        cornerRadius = CornerRadius(
                            x = corner,
                            y = corner
                        )
                    )

                    if (
                        (showBinLabels || presentation.showValueLabels) &&
                        (safeScale >= 2f || cleanBins.size <= 20) &&
                        barWidth >= 10.dp.toPx() &&
                        bin.count > 0
                    ) {
                        val labelY =
                            if (barHeight >= 18.dp.toPx()) {
                                top + 12.dp.toPx()
                            } else {
                                top - 4.dp.toPx()
                            }.coerceIn(
                                minimumValue = bodyTop + 10.dp.toPx(),
                                maximumValue = bodyTop + chartHeight - 4.dp.toPx()
                            )

                        val labelColor =
                            if (barHeight >= 18.dp.toPx()) {
                                Color.White
                            } else {
                                Color(0xFF202124)
                            }

                        drawHistogramBinLabel(
                            label = bin.count.toString(),
                            x = left + barWidth / 2f,
                            y = labelY,
                            color = labelColor
                        )
                    }
                }
            }

            canvas.nativeCanvas.restore()
        }

        if (showAxisLabels) {
            drawHistogramAxisLayer(
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

private fun DrawScope.drawRoundHistogramBackground(
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

private fun DrawScope.drawHistogramBinLabel(
    label: String,
    x: Float,
    y: Float,
    color: Color
) {
    drawIntoCanvas { canvas ->
        val paint =
            Paint().apply {
                isAntiAlias = true
                textAlign = Paint.Align.CENTER
                textSize = 8.dp.toPx()
                this.color = color.toArgb()
            }

        canvas.nativeCanvas.drawText(
            label.take(8),
            x,
            y,
            paint
        )
    }
}
