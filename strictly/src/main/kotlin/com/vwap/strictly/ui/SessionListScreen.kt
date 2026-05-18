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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vwap.strictly.core.SessionSummary
import com.vwap.strictly.theme.StrictlyBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Landing screen. Lists every recorded Strictly session, most-recently-active
 * at the top. Each row identifies its session by start timestamp; the
 * current-process session has no special badge because recency-sort already
 * conveys "this is the newest one" and a "Live" badge that lingers across
 * days of the same process is misleading more often than it is useful.
 */
@Composable
internal fun SessionListScreen(
    sessions: List<SessionSummary>,
    onSessionClick: (SessionSummary) -> Unit,
    onSettingsClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        SessionListHeader(
            sessionCount = sessions.size,
            onSettingsClick = onSettingsClick,
        )
        if (sessions.isEmpty()) {
            EmptySessionsState()
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 12.dp, horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(sessions, key = { it.id }) { s ->
                    SessionCard(
                        summary = s,
                        onClick = { onSessionClick(s) },
                    )
                }
                item { Spacer(modifier = Modifier.height(24.dp)) }
            }
        }
    }
}

@Composable
private fun SessionListHeader(
    sessionCount: Int,
    onSettingsClick: () -> Unit,
) {
    val context = LocalContext.current
    Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BrandSquare()
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
                        text = headerSubtitle(sessionCount, hostAppLabel(context)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onSettingsClick) {
                    Icon(
                        Icons.Filled.Settings,
                        contentDescription = "Settings",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

private fun headerSubtitle(count: Int, appLabel: String): String = when (count) {
    0 -> "$appLabel · no sessions yet"
    1 -> "$appLabel · 1 session"
    else -> "$appLabel · $count sessions"
}

private fun hostAppLabel(context: android.content.Context): String =
    runCatching {
        context.applicationInfo.loadLabel(context.packageManager).toString()
    }.getOrNull()?.takeIf { it.isNotBlank() } ?: context.packageName

@Composable
private fun BrandSquare() {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(StrictlyBrand.Primary),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.MusicNote,
            contentDescription = null,
            tint = StrictlyBrand.OnPrimary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun SessionCard(
    summary: SessionSummary,
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SessionInitial(summary)
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = formatSessionTitle(summary),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = formatSubLine(summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (summary.appVersionName.isNotEmpty()) {
                    Text(
                        text = "v${summary.appVersionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            CountChipForSession(summary.uniqueCount)
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

@Composable
private fun SessionInitial(summary: SessionSummary) {
    val tone = if (summary.uniqueCount == 0) {
        MaterialTheme.colorScheme.primary
    } else if (summary.uniqueCount >= 20) {
        StrictlyBrand.Red
    } else if (summary.uniqueCount >= 5) {
        StrictlyBrand.Amber
    } else {
        MaterialTheme.colorScheme.primary
    }
    val initial = summary.topType?.displayName?.firstOrNull()?.uppercase() ?: "·"
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
private fun CountChipForSession(count: Int) {
    val tone = when {
        count >= 20 -> StrictlyBrand.Red
        count >= 5 -> StrictlyBrand.Amber
        count == 0 -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(tone.copy(alpha = 0.14f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = "$count",
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            color = tone,
        )
    }
}

private val DAY_FORMAT = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())

private fun formatSessionTitle(s: SessionSummary): String {
    val now = System.currentTimeMillis()
    val elapsedMin = (now - s.lastEventAtMillis) / 60_000
    return when {
        elapsedMin < 1 -> "Just now"
        elapsedMin < 60 -> "$elapsedMin min ago"
        elapsedMin < 60 * 24 -> "${elapsedMin / 60} hr ago"
        else -> DAY_FORMAT.format(Date(s.lastEventAtMillis))
    }
}

private fun formatSubLine(s: SessionSummary): String {
    val unique = s.uniqueCount
    val events = s.totalEvents
    val typePart = s.topType?.displayName ?: "no violations"
    return when {
        unique == 0 -> "No violations yet"
        unique == 1 -> "1 unique · $events event${if (events == 1) "" else "s"} · $typePart"
        else -> "$unique unique · $events events · top: $typePart"
    }
}

@Composable
private fun EmptySessionsState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        BrandSquare()
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = "No sessions yet",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Strictly starts a new session every time the app process launches. As soon as something trips StrictMode, the session will show up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
