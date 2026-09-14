package com.robotkinematicslab.mobile.ui.shared.resources

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

enum class ResourceLoadLevel(
    val displayName: String,
    val explanation: String
) {
    CONSERVATIVE(
        displayName = "Conservative",
        explanation = "Keeps generous device headroom"
    ),
    MODERATE(
        displayName = "Moderate",
        explanation = "Uses a balanced share of the safe allowance"
    ),
    HIGH(
        displayName = "High",
        explanation = "Uses most of the safe allowance"
    )
}

/**
 * Converts a bounded user choice into a consistent, three-level visual warning.
 * The classification is relative to the safe range exposed by that control; it
 * does not weaken or replace the underlying Android resource safety policy.
 */
object ResourceLoadClassifier {
    fun classify(
        value: Int,
        minimum: Int,
        maximum: Int
    ): ResourceLoadLevel {
        if (maximum <= minimum) return ResourceLoadLevel.CONSERVATIVE
        val safeValue = value.coerceIn(minimum, maximum)
        val fraction = (safeValue - minimum).toDouble() / (maximum - minimum).toDouble()
        return when {
            fraction <= 0.35 -> ResourceLoadLevel.CONSERVATIVE
            fraction <= 0.70 -> ResourceLoadLevel.MODERATE
            else -> ResourceLoadLevel.HIGH
        }
    }
}

data class ResourceLoadColors(
    val accent: Color,
    val container: Color,
    val onAccent: Color
)

@Composable
fun resourceLoadColors(level: ResourceLoadLevel): ResourceLoadColors {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.35f
    val accent =
        when (level) {
            ResourceLoadLevel.CONSERVATIVE -> if (darkSurface) Color(0xFF63D471) else Color(0xFF2E7D32)
            ResourceLoadLevel.MODERATE -> if (darkSurface) Color(0xFFFFB74D) else Color(0xFFEF6C00)
            ResourceLoadLevel.HIGH -> if (darkSurface) Color(0xFFFF6B6B) else Color(0xFFC62828)
        }
    return ResourceLoadColors(
        accent = accent,
        container = accent.copy(alpha = if (darkSurface) 0.18f else 0.10f),
        onAccent = if (accent.luminance() > 0.48f) Color(0xFF111111) else Color.White
    )
}

@Composable
fun ResourceLoadBadge(
    level: ResourceLoadLevel,
    modifier: Modifier = Modifier,
    prefix: String? = null
) {
    val colors = resourceLoadColors(level)
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(999.dp),
        color = colors.container,
        border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.65f))
    ) {
        Text(
            text = listOfNotNull(prefix, level.displayName).joinToString(" · "),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = colors.accent
        )
    }
}

@Composable
fun ResourceLoadLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        ResourceLoadLevel.entries.forEach { level ->
            ResourceLoadBadge(level = level, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ResourceLoadChoiceButton(
    selected: Boolean,
    label: String,
    level: ResourceLoadLevel,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    showLevelInLabel: Boolean = true
) {
    val colors = resourceLoadColors(level)
    val visibleLabel = if (showLevelInLabel) "$label · ${level.displayName}" else label
    val selectionModifier =
        modifier.semantics {
            this.selected = selected
            role = Role.RadioButton
        }
    if (selected) {
        Button(
            onClick = onClick,
            enabled = enabled,
            modifier = selectionModifier,
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.onAccent
                )
        ) { Text(visibleLabel) }
    } else {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = selectionModifier,
            border = BorderStroke(1.dp, colors.accent.copy(alpha = 0.72f)),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = colors.accent)
        ) { Text(visibleLabel) }
    }
}
