package com.robotkinematicslab.mobile.ui.diagnostic

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.robotkinematicslab.mobile.ui.help.JargonAwareText
import com.robotkinematicslab.mobile.ui.shared.AppDisclosureSection
import kotlin.math.max

@Composable
@OptIn(ExperimentalFoundationApi::class)
fun DiagnosticDisclosureCard(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    accentColor: Color? = null,
    content: @Composable () -> Unit
) {
    AppDisclosureSection(
        title = title,
        summary = subtitle,
        modifier = modifier,
        containerColor = accentColor,
        content = { content() }
    )
}

@Composable
fun SummaryRow(
    label: String,
    value: String
) {
    val colors = MaterialTheme.colorScheme
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(
                    color = colors.surface.copy(alpha = 0.86f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        JargonAwareText(
            text = label,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyMedium,
            color = colors.onSurfaceVariant,
            maxLines = 5,
            softWrap = true
        )
        // Result values may contain identifiers supplied by the user. Glossary matching belongs
        // on the stable, authored label rather than on arbitrary stored data.
        Text(
            text = value,
            modifier = Modifier.weight(0.9f),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = colors.onSurface,
            textAlign = TextAlign.End,
            maxLines = 5,
            softWrap = true
        )
    }
}

@Composable
fun StatusCard(
    message: String
) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(
            containerColor = colors.secondaryContainer.copy(alpha = 0.72f)
        )
    ) {
        JargonAwareText(
            text = message,
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
fun DiagnosticBar(
    label: String,
    numerator: Int,
    denominator: Int
) {
    val safeDenominator =
        max(denominator, 0)

    val ratio =
        if (safeDenominator <= 0) {
            0f
        } else {
            (numerator.toFloat() / safeDenominator.toFloat())
                .coerceIn(0f, 1f)
        }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        JargonAwareText(
            text = "$label: $numerator / $safeDenominator"
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .background(
                    color = Color(0xFFE0E0E0),
                    shape = RoundedCornerShape(100.dp)
                )
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio)
                    .height(12.dp)
                    .background(
                        color = Color(0xFF2E7D32),
                        shape = RoundedCornerShape(100.dp)
                    )
            )
        }
    }
}

@Composable
fun VerdictReasonRow(
    passed: Boolean,
    text: String
) {
    val prefix =
        if (passed) {
            "✓"
        } else {
            "⚠"
        }

    JargonAwareText(
        text = "$prefix $text",
        style = MaterialTheme.typography.bodySmall
    )
}

@Composable
fun AutoCheckRow(
    label: String,
    passed: Boolean,
    detail: String
) {
    val symbol =
        if (passed) {
            "PASS"
        } else {
            "CHECK"
        }

    SummaryRow(
        label = "$symbol — $label",
        value = detail
    )
}
