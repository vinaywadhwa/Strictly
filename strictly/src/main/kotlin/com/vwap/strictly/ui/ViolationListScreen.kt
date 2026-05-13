package com.vwap.strictly.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwap.strictly.core.Violation
import com.vwap.strictly.core.ViolationType
import com.vwap.strictly.theme.StrictlyBrand

@Composable
internal fun ViolationListScreen(
    violations: List<Violation>,
    onViolationClick: (Violation) -> Unit,
    onClearAll: () -> Unit,
    onMarkBaseline: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        StrictlyHeader(
            count = violations.size,
            totalEvents = violations.sumOf { it.occurrenceCount },
            onMarkBaseline = onMarkBaseline,
            onClearAll = onClearAll,
        )
        if (violations.isEmpty()) {
            EmptyState()
        } else {
            ViolationList(violations = violations, onViolationClick = onViolationClick)
        }
    }
}

@Composable
private fun StrictlyHeader(
    count: Int,
    totalEvents: Int,
    onMarkBaseline: () -> Unit,
    onClearAll: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandMark()
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "strictly",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.SansSerif,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = headerSubtitle(count, totalEvents),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onMarkBaseline) {
                    Icon(
                        Icons.Filled.Bookmark,
                        contentDescription = "Mark baseline",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onClearAll) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = "Clear all",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

private fun headerSubtitle(unique: Int, events: Int): String = when {
    unique == 0 -> "No violations yet · the app's been well behaved"
    unique == 1 -> "1 unique violation · $events occurrence${if (events == 1) "" else "s"}"
    else -> "$unique unique violations · $events occurrences"
}

@Composable
private fun BrandMark() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StrictlyBrand.Primary),
        contentAlignment = Alignment.Center,
    ) {
        // Whistle silhouette via material icon as a near-substitute for the
        // intended custom mark. Easy to swap for an actual vector drawable later.
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = StrictlyBrand.OnPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun ViolationList(
    violations: List<Violation>,
    onViolationClick: (Violation) -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(violations, key = { it.id }) { v ->
            ViolationCard(violation = v, onClick = { onViolationClick(v) })
        }
        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun ViolationCard(violation: Violation, onClick: () -> Unit) {
    val severity = severityFor(violation.occurrenceCount)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TypeBadge(violation.type, severity = severity)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = violation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = violation.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            CountChip(violation.occurrenceCount, severity = severity)
            Spacer(modifier = Modifier.width(2.dp))
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

private enum class Severity { Low, Medium, High }

private fun severityFor(count: Int): Severity = when {
    count >= 50 -> Severity.High
    count >= 5 -> Severity.Medium
    else -> Severity.Low
}

@Composable
private fun TypeBadge(type: ViolationType, severity: Severity) {
    val tone = when (severity) {
        Severity.Low -> MaterialTheme.colorScheme.primary
        Severity.Medium -> StrictlyBrand.Amber
        Severity.High -> StrictlyBrand.Red
    }
    val initial = type.displayName.firstOrNull()?.uppercase() ?: "?"
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(RoundedCornerShape(9.dp))
            .background(tone.copy(alpha = 0.14f))
            .border(1.dp, tone.copy(alpha = 0.35f), RoundedCornerShape(9.dp)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}

@Composable
private fun CountChip(count: Int, severity: Severity) {
    val tone = when (severity) {
        Severity.Low -> MaterialTheme.colorScheme.primary
        Severity.Medium -> StrictlyBrand.Amber
        Severity.High -> StrictlyBrand.Red
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "×$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandMark()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "All clear",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Strictly is listening. The moment something blocks the main thread or leaks resources, you'll see it here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
