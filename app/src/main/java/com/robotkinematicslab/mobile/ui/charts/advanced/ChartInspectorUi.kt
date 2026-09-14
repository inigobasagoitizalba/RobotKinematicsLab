package com.robotkinematicslab.mobile.ui.charts.advanced

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.onboarding.TutorialTargets
import com.robotkinematicslab.mobile.ui.onboarding.tutorialAnchor

internal const val CHART_INSPECTOR_PLOT_TAG = "chart-inspector-plot"
internal const val CHART_INSPECTOR_RESET_TAG = "chart-inspector-reset"
internal const val CHART_INSPECTOR_ZOOM_IN_TAG = "chart-inspector-zoom-in"
internal const val CHART_INSPECTOR_ZOOM_OUT_TAG = "chart-inspector-zoom-out"
internal const val CHART_INSPECTOR_SELECTION_TAG = "chart-inspector-selection"

internal fun steppedInspectorZoom(
    currentScale: Float,
    factor: Float,
    maximumScale: Float
): Float {
    require(currentScale.isFinite() && currentScale >= 1f)
    require(factor.isFinite() && factor > 0f)
    require(maximumScale.isFinite() && maximumScale >= 1f)
    return (currentScale * factor).coerceIn(1f, maximumScale)
}

@Composable
internal fun ChartInspectorZoomControls(
    zoomText: String,
    scale: Float,
    maximumScale: Float,
    onScaleChange: (Float) -> Unit,
    onFit: () -> Unit
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .testTag("chart-inspector-gestures")
                .tutorialAnchor(TutorialTargets.ChartGestures),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = {
                    onScaleChange(
                        steppedInspectorZoom(
                            currentScale = scale,
                            factor = 1f / INSPECTOR_ZOOM_STEP,
                            maximumScale = maximumScale
                        )
                    )
                },
                enabled = scale > 1.001f,
                modifier = Modifier.weight(1f).testTag(CHART_INSPECTOR_ZOOM_OUT_TAG)
            ) {
                Text("− Zoom")
            }
            OutlinedButton(
                onClick = {
                    onScaleChange(
                        steppedInspectorZoom(
                            currentScale = scale,
                            factor = INSPECTOR_ZOOM_STEP,
                            maximumScale = maximumScale
                        )
                    )
                },
                enabled = scale < maximumScale - 0.001f,
                modifier = Modifier.weight(1f).testTag(CHART_INSPECTOR_ZOOM_IN_TAG)
            ) {
                Text("+ Zoom")
            }
            Button(
                onClick = onFit,
                modifier = Modifier.weight(1f).testTag(CHART_INSPECTOR_RESET_TAG)
            ) {
                Text("Fit")
            }
        }

        JargonAwareText(
            text = zoomText,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFF5F6368),
            maxLines = 3,
            softWrap = true
        )
    }
}

private const val INSPECTOR_ZOOM_STEP = 1.5f

@Composable
internal fun ChartInspectorSelectionCard(
    title: String,
    values: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .testTag(CHART_INSPECTOR_SELECTION_TAG)
                .background(
                    color = colors.surface.copy(alpha = 0.96f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        JargonAwareText(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = colors.onSurface,
            maxLines = 3,
            softWrap = true
        )

        values.forEach { (label, value) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                JargonAwareText(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 3,
                    softWrap = true
                )
                JargonAwareText(
                    text = value,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.End,
                    maxLines = 3,
                    softWrap = true
                )
            }
        }
    }
}
