package com.vwap.strictly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.vwap.strictly.core.Session
import com.vwap.strictly.export.SessionExporter
import com.vwap.strictly.theme.StrictlyBrand

/**
 * Modal bottom sheet that exports a [Session] as either JSON or Markdown.
 *
 * Step 1: format picker — two big cards, JSON and Markdown.
 * Step 2: preview + copy/share — the rendered text in a scrollable monospace
 *   block with Copy (puts on clipboard, shows toast) and Share (fires
 *   ACTION_SEND so any AI app, email, Slack, etc, can receive it).
 *
 * Back arrow in step 2 returns to step 1 so the user can switch format
 * without re-opening the sheet from the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExportSheet(
    session: Session,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var format by remember { mutableStateOf<ExportFormat?>(null) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            when (val f = format) {
                null -> FormatPicker(
                    session = session,
                    onPick = { format = it },
                )
                else -> ExportPreview(
                    session = session,
                    format = f,
                    onBack = { format = null },
                )
            }
        }
    }
}

internal enum class ExportFormat(val displayName: String, val mime: String, val extension: String) {
    Json("JSON", "application/json", "json"),
    Markdown("Markdown", "text/markdown", "md"),
}

@Composable
private fun FormatPicker(session: Session, onPick: (ExportFormat) -> Unit) {
    Text(
        text = "Export session",
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
    Spacer(modifier = Modifier.size(4.dp))
    Text(
        text = "${session.uniqueCount} unique violations · ${session.totalEvents} events",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(modifier = Modifier.size(20.dp))
    FormatOption(
        icon = Icons.Filled.DataObject,
        title = "JSON",
        subtitle = "Schema-versioned. Best for AI agents (Claude, Cursor) that parse structure.",
        accent = StrictlyBrand.Primary,
        onClick = { onPick(ExportFormat.Json) },
    )
    Spacer(modifier = Modifier.size(10.dp))
    FormatOption(
        icon = Icons.Filled.Description,
        title = "Markdown",
        subtitle = "Tables, code blocks, stack traces. Best for pasting into a Slack thread, PR review, or design doc.",
        accent = StrictlyBrand.Amber,
        onClick = { onPick(ExportFormat.Markdown) },
    )
}

@Composable
private fun FormatOption(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    accent: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
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
                .padding(horizontal = 14.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(accent.copy(alpha = 0.14f))
                    .border(1.dp, accent.copy(alpha = 0.35f), RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(icon, contentDescription = null, tint = accent)
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ExportPreview(
    session: Session,
    format: ExportFormat,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val payload = remember(session.id, session.lastEventAtMillis, format) {
        when (format) {
            ExportFormat.Json -> SessionExporter.toJson(session)
            ExportFormat.Markdown -> SessionExporter.toMarkdown(session)
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${format.displayName} preview",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "${payload.length} chars",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = { copyToClipboard(context, payload, format) }) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy to clipboard")
        }
        IconButton(onClick = { sharePayload(context, payload, format) }) {
            Icon(Icons.Filled.IosShare, contentDescription = "Share")
        }
    }
    Spacer(modifier = Modifier.size(8.dp))
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f),
                shape = RoundedCornerShape(12.dp),
            )
            .heightIn(min = 220.dp, max = 360.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
    ) {
        Column(
            modifier = Modifier
                .padding(PaddingValues(horizontal = 12.dp, vertical = 10.dp))
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Top,
        ) {
            Text(
                text = payload,
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

private fun copyToClipboard(context: Context, text: String, format: ExportFormat) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("strictly-${format.extension}", text))
    Toast.makeText(context, "Copied as ${format.displayName}", Toast.LENGTH_SHORT).show()
}

private fun sharePayload(context: Context, text: String, format: ExportFormat) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = format.mime
        putExtra(Intent.EXTRA_TEXT, text)
        putExtra(Intent.EXTRA_SUBJECT, "Strictly session export")
    }
    context.startActivity(Intent.createChooser(intent, "Share Strictly session").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}
