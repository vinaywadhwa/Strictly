package com.vwap.strictly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
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
import com.vwap.strictly.Strictly
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.theme.StrictlyBrand
import com.vwap.strictly.theme.ThemeMode
import kotlinx.coroutines.flow.StateFlow

/**
 * Single bottom sheet that exposes every Strictly toggle worth flipping at
 * runtime: the HTTP server, the MCP setup helper, and the "wipe all sessions"
 * nuclear option.
 *
 * Designed for the "I just installed Strictly, what do I do now?" moment.
 * Tap the gear in the top right of the session list, see every meaningful
 * lever in one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsSheet(
    httpRunning: StateFlow<Boolean>,
    httpDesired: StateFlow<Boolean>,
    httpLastError: StateFlow<String?>,
    httpPort: Int,
    httpHasSecret: Boolean,
    onHttpToggle: (Boolean) -> Unit,
    onHttpRetry: () -> Unit,
    sessionsState: StateFlow<List<SessionSummary>>,
    themeMode: StateFlow<ThemeMode>,
    onThemeChange: (ThemeMode) -> Unit,
    onWipeAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val running by httpRunning.collectAsState()
    val desired by httpDesired.collectAsState()
    val lastError by httpLastError.collectAsState()
    val sessions by sessionsState.collectAsState()
    val currentThemeMode by themeMode.collectAsState()
    var wipeDialogVisible by remember { mutableStateOf(false) }

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
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionTitle("Settings")

            HttpServerCard(
                running = running,
                desired = desired,
                port = httpPort,
                hasSecret = httpHasSecret,
                lastError = lastError,
                onToggle = onHttpToggle,
                onRetry = onHttpRetry,
            )

            McpSetupCard(
                running = running,
                port = httpPort,
                hasSecret = httpHasSecret,
            )

            StorageCard(
                sessionCount = sessions.size,
                totalEvents = sessions.sumOf { it.totalEvents },
                onWipe = { wipeDialogVisible = true },
            )

            ThemeCard(
                themeMode = currentThemeMode,
                onThemeChange = onThemeChange,
            )

            FooterText()
        }
    }

    if (wipeDialogVisible) {
        AlertDialog(
            onDismissRequest = { wipeDialogVisible = false },
            title = { Text("Wipe all sessions?", fontWeight = FontWeight.SemiBold) },
            text = {
                Text(
                    "Deletes every recorded session (${sessions.size}) from disk. The current session is cleared and a new one starts. Logcat output is preserved.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    wipeDialogVisible = false
                    onWipeAll()
                }) {
                    Text(
                        text = "Wipe all",
                        color = StrictlyBrand.Red,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { wipeDialogVisible = false }) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface,
    )
}

@Composable
private fun HttpServerCard(
    running: Boolean,
    desired: Boolean,
    port: Int,
    hasSecret: Boolean,
    lastError: String?,
    onToggle: (Boolean) -> Unit,
    onRetry: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Debug HTTP server",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = serverStatusText(desired, running, port, lastError),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (lastError != null) StrictlyBrand.Red else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    // Bind to *desired* (the persisted intent) instead of
                    // *running* (the actual state). A bind failure must not
                    // silently flip the toggle back behind the user.
                    checked = desired,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = StrictlyBrand.OnPrimary,
                        checkedTrackColor = StrictlyBrand.Primary,
                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                        uncheckedBorderColor = MaterialTheme.colorScheme.outline,
                    ),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Binds to 127.0.0.1 only. Toggle on so the MCP (via ADB port-forward) can read sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (running) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = StrictlyBrand.Primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Listening at http://127.0.0.1:$port",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (hasSecret) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Auth: X-Strictly-Secret header required.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            if (desired && !running && lastError != null) {
                Spacer(modifier = Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable(onClick = onRetry)
                        .background(StrictlyBrand.Primary.copy(alpha = 0.14f))
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "Retry",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StrictlyBrand.Primary,
                    )
                }
            }
        }
    }
}

private fun serverStatusText(
    desired: Boolean,
    running: Boolean,
    port: Int,
    lastError: String?,
): String = when {
    lastError != null -> lastError
    running -> "Running on port $port"
    desired -> "Starting…"
    else -> "Off (off by default, toggle on when you want it)"
}

@Composable
private fun McpSetupCard(
    running: Boolean,
    port: Int,
    hasSecret: Boolean,
) {
    val context = LocalContext.current
    val adbCommand = remember(port) { "adb forward tcp:$port tcp:$port" }
    val mcpJson = remember(port, hasSecret) { buildMcpJson(port, hasSecret) }
    val cliSnippet = remember(port, hasSecret) { buildCliSnippet(port, hasSecret) }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Connect to your AI agent",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (running) {
                    "Pipe live violations into Claude Code / Cursor / any MCP-aware tool. Three small steps:"
                } else {
                    "Turn the HTTP server on above first. Then three small steps:"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(10.dp))
            CodeBlock(
                title = "1. Bridge host → device (USB or emulator)",
                code = adbCommand,
                onCopy = { copyToClipboard(context, adbCommand, "Strictly ADB forward") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            CodeBlock(
                title = "2. Register the MCP (Claude Code CLI)",
                code = cliSnippet,
                onCopy = { copyToClipboard(context, cliSnippet, "Strictly MCP CLI snippet") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            CodeBlock(
                title = "or 2b. mcp.json (any MCP client)",
                code = mcpJson,
                onCopy = { copyToClipboard(context, mcpJson, "Strictly MCP JSON config") },
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Works the same on a USB-attached phone or an emulator. Step 1 needs adb on PATH plus USB debugging on. Skip step 1 if you already ran it during this adb session.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CodeBlock(title: String, code: String, onCopy: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f),
                shape = RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onCopy, modifier = Modifier.size(28.dp)) {
                Icon(
                    Icons.Filled.ContentCopy,
                    contentDescription = "Copy",
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        Text(
            text = code,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun StorageCard(
    sessionCount: Int,
    totalEvents: Int,
    onWipe: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Storage",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = "$sessionCount session${if (sessionCount == 1) "" else "s"} · $totalEvents event${if (totalEvents == 1) "" else "s"} on disk",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable(onClick = onWipe)
                    .background(StrictlyBrand.Red.copy(alpha = 0.14f))
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DeleteSweep,
                        contentDescription = null,
                        tint = StrictlyBrand.Red,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Wipe all",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = StrictlyBrand.Red,
                    )
                }
            }
        }
    }
}

@Composable
private fun FooterText() {
    val context = LocalContext.current
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.RadioButtonUnchecked,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(12.dp),
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Strictly · 0.1.1",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "Built by @vinaywadhwa",
            style = MaterialTheme.typography.labelSmall,
            color = StrictlyBrand.Primary,
            modifier = Modifier.clickable {
                val intent = Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/vinaywadhwa"),
                )
                context.startActivity(intent)
            },
        )
    }
}

@Composable
private fun ThemeCard(
    themeMode: ThemeMode,
    onThemeChange: (ThemeMode) -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                shape = RoundedCornerShape(14.dp),
            ),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Theme",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { mode ->
                    val selected = mode == themeMode
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) {
                                    StrictlyBrand.Primary
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                                },
                            )
                            .clickable { onThemeChange(mode) }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = mode.label,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selected) {
                                StrictlyBrand.OnPrimary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

private fun buildCliSnippet(port: Int, hasSecret: Boolean): String {
    val envArg = if (hasSecret) " -e STRICTLY_SECRET=<paste-your-secret>" else ""
    return "claude mcp add strictly -e STRICTLY_URL=http://127.0.0.1:$port$envArg -- npx -y strictly-mcp"
}

private fun buildMcpJson(port: Int, hasSecret: Boolean): String {
    val env = buildString {
        append("      \"STRICTLY_URL\": \"http://127.0.0.1:$port\"")
        if (hasSecret) {
            append(",\n      \"STRICTLY_SECRET\": \"<paste-your-secret>\"")
        }
    }
    return """
{
  "mcpServers": {
    "strictly": {
      "command": "npx",
      "args": ["-y", "strictly-mcp"],
      "env": {
$env
      }
    }
  }
}
""".trim()
}

private fun copyToClipboard(context: Context, text: String, label: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "Copied", Toast.LENGTH_SHORT).show()
}

// Suppress unused: kept for future "server not running" affordances.
@Suppress("unused")
@Composable
private fun ErrorBadge() {
    Icon(
        Icons.Filled.Error,
        contentDescription = null,
        tint = StrictlyBrand.Red,
        modifier = Modifier.size(14.dp),
    )
}
