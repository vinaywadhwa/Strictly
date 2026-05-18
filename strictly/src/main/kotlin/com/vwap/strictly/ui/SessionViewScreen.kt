package com.vwap.strictly.ui

import android.widget.Toast
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwap.strictly.core.Session
import com.vwap.strictly.core.Violation
import com.vwap.strictly.core.ViolationType
import com.vwap.strictly.theme.StrictlyBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Single-session view. Shows every unique violation captured in [session],
 * with an export icon, a delete icon, and a back arrow to the session list.
 *
 * If [isLive] is true the delete icon clears the current session in place.
 * Otherwise it deletes the archived session and pops back to the list.
 */
@Composable
internal fun SessionViewScreen(
    session: Session,
    isLive: Boolean,
    onBack: () -> Unit,
    onViolationClick: (Violation) -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    var confirmDeleteVisible by remember { mutableStateOf(false) }
    val violations = session.violations.values.toList()
        .sortedByDescending { it.lastOccurrenceAtMillis }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        SessionViewHeader(
            session = session,
            isLive = isLive,
            onBack = onBack,
            onExport = onExport,
            onDelete = {
                if (violations.isEmpty()) {
                    Toast.makeText(context, "Nothing to delete", Toast.LENGTH_SHORT).show()
                } else {
                    confirmDeleteVisible = true
                }
            },
        )
        if (violations.isEmpty()) {
            EmptySessionState(isLive = isLive)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(violations, key = { it.id }) { v ->
                    ViolationRow(violation = v, onClick = { onViolationClick(v) })
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }

    if (confirmDeleteVisible) {
        ConfirmDeleteDialog(
            isLive = isLive,
            uniqueCount = violations.size,
            totalEvents = violations.sumOf { it.occurrenceCount },
            onConfirm = {
                confirmDeleteVisible = false
                onDelete()
                Toast.makeText(
                    context,
                    if (isLive) "Current session cleared" else "Session deleted",
                    Toast.LENGTH_SHORT,
                ).show()
            },
            onDismiss = { confirmDeleteVisible = false },
        )
    }
}

@Composable
private fun SessionViewHeader(
    session: Session,
    isLive: Boolean,
    onBack: () -> Unit,
    onExport: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = hostAppLabel(context),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = formatHeaderSub(session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onExport) {
                    Icon(
                        Icons.Filled.IosShare,
                        contentDescription = "Export",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = if (isLive) "Clear current session" else "Delete session",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

private val HEADER_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

private fun hostAppLabel(context: android.content.Context): String =
    runCatching {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: context.packageName

private fun formatHeaderSub(s: Session): String {
    val unique = s.uniqueCount
    val events = s.totalEvents
    val timePart = HEADER_FORMAT.format(Date(s.startedAtMillis))
    val versionPart = if (s.appVersionName.isNotEmpty()) " · v${s.appVersionName}" else ""
    return when {
        unique == 0 -> "$timePart · no violations yet$versionPart"
        unique == 1 -> "$timePart · 1 unique · $events event${if (events == 1) "" else "s"}$versionPart"
        else -> "$timePart · $unique unique · $events events$versionPart"
    }
}

@Composable
private fun ViolationRow(violation: Violation, onClick: () -> Unit) {
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
            TypeInitial(violation.type, severity = severity)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = violation.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                val frame = violation.firstActionableFrame
                if (frame != null) {
                    val methodSig = if (frame.lineNumber > 0) "${frame.methodName}:${frame.lineNumber}"
                    else frame.methodName
                    Text(
                        text = methodSig,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                    val secondLine = if (violation.isThirdPartyOrigin) {
                        frame.className.substringBeforeLast('.', missingDelimiterValue = frame.className)
                    } else {
                        frame.className.substringAfterLast('.')
                    }
                    Text(
                        text = secondLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontFamily = if (violation.isThirdPartyOrigin) FontFamily.Monospace else FontFamily.SansSerif,
                    )
                } else {
                    Text(
                        text = "unknown origin",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
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
private fun TypeInitial(type: ViolationType, severity: Severity) {
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
private fun EmptySessionState(isLive: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = if (isLive) "All clear so far" else "No violations",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isLive) {
                "Strictly is listening. The moment something blocks the main thread or leaks resources, it'll show up here."
            } else {
                "This session ended without recording any violations."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    isLive: Boolean,
    uniqueCount: Int,
    totalEvents: Int,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLive) "Clear current session?" else "Delete this session?",
                fontWeight = FontWeight.SemiBold,
            )
        },
        text = {
            val summary = if (isLive) {
                "Wipes $uniqueCount unique violation${if (uniqueCount == 1) "" else "s"} ($totalEvents event${if (totalEvents == 1) "" else "s"}) from the live session. Archived sessions are untouched. Logcat output is preserved."
            } else {
                "Removes this archived session ($uniqueCount unique, $totalEvents events) from disk. The live session is untouched."
            }
            Text(text = summary)
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    text = if (isLive) "Clear" else "Delete",
                    color = StrictlyBrand.Red,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
