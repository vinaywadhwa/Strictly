package com.vwap.strictly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vwap.strictly.core.StackFrame
import com.vwap.strictly.core.Violation
import com.vwap.strictly.internal.StrictlyRuntime
import com.vwap.strictly.theme.StrictlyBrand
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun ViolationDetailScreen(violation: Violation, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .systemBarsPadding(),
    ) {
        DetailHeader(
            violation = violation,
            onBack = onBack,
            onCopy = {
                copyToClipboard(context, violation.fullStackText())
                Toast.makeText(context, "Stack trace copied", Toast.LENGTH_SHORT).show()
            },
            onShare = { share(context, violation) },
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
        ) {
            MetaCard(violation)
            Spacer(modifier = Modifier.height(16.dp))
            SectionLabel("Stack trace")
            Spacer(modifier = Modifier.height(8.dp))
            StackTraceCard(violation = violation)
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
private fun DetailHeader(
    violation: Violation,
    onBack: () -> Unit,
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Surface(color = MaterialTheme.colorScheme.surface) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Column(modifier = Modifier.weight(1f).padding(start = 4.dp, end = 8.dp)) {
                    Text(
                        text = violation.title,
                        style = MaterialTheme.typography.titleMedium,
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
                IconButton(onClick = onCopy) {
                    Icon(
                        Icons.Filled.ContentCopy,
                        contentDescription = "Copy",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        Icons.Filled.Share,
                        contentDescription = "Share",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
    }
}

@Composable
private fun MetaCard(violation: Violation) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            MetaRow("Occurrences", "×${violation.occurrenceCount}")
            MetaRow("First seen", formatTime(violation.firstOccurrenceAtMillis))
            MetaRow("Last seen", formatTime(violation.lastOccurrenceAtMillis))
            MetaRow("Thread", violation.threadName)
            MetaRow("Fingerprint", violation.fingerprint, monospaced = true)
            if (violation.message.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = violation.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String, monospaced: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
    ) {
        Text(
            text = label,
            modifier = Modifier.width(96.dp),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (monospaced) FontFamily.Monospace else FontFamily.SansSerif,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
    )
}

/**
 * Stack trace renderer designed for fast scanning, not paper-print fidelity.
 *
 * Two structural moves drive the design:
 *
 *  1. Consecutive non-app frames collapse into a single foldable run. A
 *     typical StrictMode stack is 40-80 frames, almost all platform noise;
 *     folding it down to the 2-3 app frames + a few framework chips
 *     turns a wall of text into a glanceable shape.
 *  2. Each app frame renders as two lines — `Class.method` on top
 *     (the thing you mentally search for), `package · file:line` below
 *     (the thing you copy into a tracker). Halves the parse time vs
 *     a single mono blob.
 *
 * The frame Strictly already chose as the violation's surfaced origin
 * (`firstActionableFrame`) gets a star marker so the eye lands on it
 * immediately, and the run containing it auto-expands so it's never
 * hidden behind a fold. Tap any row to copy that single frame.
 */
@Composable
private fun StackTraceCard(violation: Violation) {
    val frames = violation.stackTrace
    val appPackages = remember { StrictlyRuntime.currentConfig()?.appPackages.orEmpty() }
    val runs = remember(frames, appPackages) { buildRuns(frames, appPackages) }
    val hasAnyAppFrame = runs.any { it is FrameRun.App }
    val actionable = violation.firstActionableFrame

    // Per-platform-run expanded state, lifted here so the parent can flatten the
    // run list into a single positioned row sequence (needed for first/last rail
    // termination). Indexed by runs[idx]; entries for app runs are unused.
    val platformExpanded = remember(runs) {
        Array(runs.size) { idx ->
            val r = runs[idx]
            if (r is FrameRun.Platform) {
                val containsActionable = r.frames.any { it.value === actionable }
                mutableStateOf(!hasAnyAppFrame || containsActionable)
            } else {
                mutableStateOf(false)
            }
        }
    }

    val entries = buildList<StackEntry> {
        runs.forEachIndexed { runIdx, run ->
            when (run) {
                is FrameRun.App -> run.frames.forEach { (i, f) ->
                    add(StackEntry.Frame(i, f, isApp = true, isActionable = f === actionable))
                }
                is FrameRun.Platform -> {
                    val expanded = platformExpanded[runIdx].value
                    if (expanded) {
                        run.frames.forEach { (i, f) ->
                            add(StackEntry.Frame(i, f, isApp = false, isActionable = f === actionable))
                        }
                        if (run.frames.size >= COLLAPSE_HINT_THRESHOLD) {
                            add(
                                StackEntry.Collapser(
                                    count = run.frames.size,
                                    packages = topPackagesIn(run.frames),
                                    expanded = true,
                                    onClick = { platformExpanded[runIdx].value = false },
                                ),
                            )
                        }
                    } else {
                        add(
                            StackEntry.Collapser(
                                count = run.frames.size,
                                packages = topPackagesIn(run.frames),
                                expanded = false,
                                onClick = { platformExpanded[runIdx].value = true },
                            ),
                        )
                    }
                }
            }
        }
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp)),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            entries.forEachIndexed { i, entry ->
                val drawTopRail = i > 0
                val drawBottomRail = i < entries.lastIndex
                when (entry) {
                    is StackEntry.Frame -> FrameRow(
                        index = entry.index,
                        frame = entry.frame,
                        isApp = entry.isApp,
                        isActionable = entry.isActionable,
                        drawTopRail = drawTopRail,
                        drawBottomRail = drawBottomRail,
                    )
                    is StackEntry.Collapser -> CollapsedRunRow(
                        count = entry.count,
                        packages = entry.packages,
                        expanded = entry.expanded,
                        onClick = entry.onClick,
                        drawTopRail = drawTopRail,
                        drawBottomRail = drawBottomRail,
                    )
                }
            }
        }
    }
}

private sealed interface StackEntry {
    data class Frame(
        val index: Int,
        val frame: StackFrame,
        val isApp: Boolean,
        val isActionable: Boolean,
    ) : StackEntry

    data class Collapser(
        val count: Int,
        val packages: List<String>,
        val expanded: Boolean,
        val onClick: () -> Unit,
    ) : StackEntry
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FrameRow(
    index: Int,
    frame: StackFrame,
    isApp: Boolean,
    isActionable: Boolean,
    drawTopRail: Boolean,
    drawBottomRail: Boolean,
) {
    val context = LocalContext.current
    val accent = if (isApp) StrictlyBrand.Primary else MaterialTheme.colorScheme.onSurfaceVariant
    val titleColor = if (isApp) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val subColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (isApp) 0.9f else 0.6f)
    val simpleClass = frame.className.substringAfterLast('.')
    val pkg = frame.className.substringBeforeLast('.', missingDelimiterValue = "")
    val isSynthetic = frame.isSyntheticArtifact()
    val location = when {
        isSynthetic -> "synthetic"
        frame.fileName != null && frame.lineNumber > 0 -> "${frame.fileName}:${frame.lineNumber}"
        frame.fileName != null -> frame.fileName
        else -> "unknown"
    }
    val subLine = if (pkg.isNotEmpty()) "$pkg · $location" else location

    val fileLineCopy = when {
        isSynthetic -> "$simpleClass.${frame.methodName}"
        frame.fileName != null && frame.lineNumber > 0 -> "${frame.fileName}:${frame.lineNumber}"
        frame.fileName != null -> frame.fileName
        else -> "$simpleClass.${frame.methodName}"
    }
    val methodCopy = "$simpleClass.${frame.methodName}"
    val frameCopy = frame.formatFull()

    var menuExpanded by remember { mutableStateOf(false) }
    val doCopy: (String, String) -> Unit = { value, label ->
        copyToClipboard(context, value)
        Toast.makeText(context, "Copied $label", Toast.LENGTH_SHORT).show()
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .combinedClickable(
                onClick = { doCopy(fileLineCopy, fileLineCopy) },
                onLongClick = { menuExpanded = true },
            )
            .padding(horizontal = 14.dp, vertical = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        RailColumn(drawTopRail = drawTopRail, drawBottomRail = drawBottomRail) {
            if (isActionable) {
                ActionableDot()
            } else {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(accent.copy(alpha = if (isApp) 0.9f else 0.45f)),
                )
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$simpleClass.${frame.methodName}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isApp) FontWeight.SemiBold else FontWeight.Normal,
                color = titleColor,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = subLine,
                style = MaterialTheme.typography.labelSmall,
                color = subColor,
                fontFamily = FontFamily.Monospace,
            )
        }
        Box {
            Text(
                text = "#$index",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.padding(start = 8.dp, top = 4.dp),
            )
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                DropdownMenuItem(
                    text = { Text("Copy $fileLineCopy") },
                    onClick = {
                        menuExpanded = false
                        doCopy(fileLineCopy, fileLineCopy)
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy full frame") },
                    onClick = {
                        menuExpanded = false
                        doCopy(frameCopy, "full frame")
                    },
                )
                DropdownMenuItem(
                    text = { Text("Copy $methodCopy") },
                    onClick = {
                        menuExpanded = false
                        doCopy(methodCopy, methodCopy)
                    },
                )
            }
        }
    }
}

@Composable
private fun CollapsedRunRow(
    count: Int,
    packages: List<String>,
    expanded: Boolean,
    onClick: () -> Unit,
    drawTopRail: Boolean,
    drawBottomRail: Boolean,
) {
    val pkgHint = if (packages.isEmpty()) "" else " (${packages.joinToString(", ")})"
    val label = if (expanded) "hide $count framework frames$pkgHint"
    else "$count framework frames$pkgHint"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RailColumn(drawTopRail = drawTopRail, drawBottomRail = drawBottomRail) {
            Icon(
                imageVector = if (expanded) Icons.Filled.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Collapse" else "Expand",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Leftmost column of every stack-trace row. Draws a vertical rail with a small
 * gap around the marker, plus the marker glyph centered on the rail. The rail
 * extends to row edges so adjacent rows visually meet at the boundary, giving
 * the impression of one continuous thread running through the whole stack.
 *
 * `drawTopRail` / `drawBottomRail` let the parent omit the half-segments at the
 * very first and very last row, so the rail terminates cleanly at the card
 * edges rather than dangling.
 *
 * The marker sits in a small backed circle that masks the rail behind it, so
 * the marker reads as a node rather than overlapping a line passing through.
 */
@Composable
private fun RailColumn(
    drawTopRail: Boolean,
    drawBottomRail: Boolean,
    marker: @Composable () -> Unit,
) {
    val railColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.45f)
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        .compositeOverSurface()
    Box(
        modifier = Modifier
            .width(20.dp)
            .fillMaxHeight(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .width(1.5.dp)
                .fillMaxHeight(0.5f)
                .background(if (drawTopRail) railColor else Color.Transparent),
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .width(1.5.dp)
                .fillMaxHeight(0.5f)
                .background(if (drawBottomRail) railColor else Color.Transparent),
        )
        // Backing chip cuts the rail line behind the marker so the marker reads
        // as a node, not an overlay. Sized slightly larger than the marker glyph
        // so the rail is reliably masked at all marker sizes (8dp dot, 10dp
        // actionable dot, 18dp chevron).
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(cardColor),
        )
        // Marker rendered as a sibling overlay rather than nested inside the
        // backing chip, so animated halos (eg the actionable pulse) can overflow
        // the 20dp column without being clipped.
        marker()
    }
}

/**
 * The actionable-frame marker: a 10dp solid red dot with a pulsing halo. The
 * halo expands from the dot's footprint to ~2.4x while fading from 45% alpha to
 * 0, looped every 1.6s. The solid dot stays static so the eye has a stable
 * anchor point; only the halo breathes, which reads as "alive" rather than
 * "blinking warning."
 *
 * Easing split is deliberate: FastOutSlowIn on scale (the halo accelerates
 * outward then settles), Linear on alpha (the fade is perceptually even). Same
 * duration on both, sharing the [rememberInfiniteTransition] so they stay
 * phase-locked across the row's lifetime.
 *
 * The semantics description still announces "Surfaced origin" to screen
 * readers, replacing the contentDescription the previous Star icon carried.
 */
@Composable
private fun ActionableDot() {
    val transition = rememberInfiniteTransition(label = "actionable-pulse")
    // Halo starts slightly larger than the solid dot so a thin ring is visible
    // at every cycle's start — without this offset, the halo spends the first
    // half-cycle hidden behind the solid and only becomes perceptible after
    // alpha has already started to drop.
    val haloScale by transition.animateFloat(
        initialValue = 1.2f,
        targetValue = 2.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-scale",
    )
    val haloAlpha by transition.animateFloat(
        initialValue = 0.5f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "halo-alpha",
    )
    Box(contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .scale(haloScale)
                .clip(CircleShape)
                .background(StrictlyBrand.Red.copy(alpha = haloAlpha)),
        )
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(StrictlyBrand.Red)
                .semantics { contentDescription = "Surfaced origin" },
        )
    }
}

@Composable
private fun Color.compositeOverSurface(): Color {
    val surface = MaterialTheme.colorScheme.surface
    val a = alpha
    return Color(
        red = red * a + surface.red * (1 - a),
        green = green * a + surface.green * (1 - a),
        blue = blue * a + surface.blue * (1 - a),
        alpha = 1f,
    )
}

private sealed interface FrameRun {
    val frames: List<IndexedValue<StackFrame>>
    data class App(override val frames: List<IndexedValue<StackFrame>>) : FrameRun
    data class Platform(override val frames: List<IndexedValue<StackFrame>>) : FrameRun
}

private fun buildRuns(frames: List<StackFrame>, appPackages: List<String>): List<FrameRun> {
    if (frames.isEmpty()) return emptyList()
    val out = mutableListOf<FrameRun>()
    var bucket = mutableListOf<IndexedValue<StackFrame>>()
    var bucketIsApp = frames.first().isUserCode(appPackages)
    frames.forEachIndexed { i, f ->
        val app = f.isUserCode(appPackages)
        if (app == bucketIsApp) {
            bucket.add(IndexedValue(i, f))
        } else {
            out.add(if (bucketIsApp) FrameRun.App(bucket) else FrameRun.Platform(bucket))
            bucket = mutableListOf(IndexedValue(i, f))
            bucketIsApp = app
        }
    }
    if (bucket.isNotEmpty()) {
        out.add(if (bucketIsApp) FrameRun.App(bucket) else FrameRun.Platform(bucket))
    }
    return out
}

/**
 * "User code" means: in one of the configured app packages AND not a dex-toolchain
 * synthetic. The synthetic check is what keeps `MainActivity$ExternalSyntheticLambda2`
 * and `$r8$lambda$...` bridges from looking like app frames just because their
 * className starts with `com.example.feedmaker.`. Synthetic frames stay in the data
 * but render alongside platform frames in the folded run.
 */
private fun StackFrame.isUserCode(appPackages: List<String>): Boolean =
    appPackages.any { className.startsWith(it) } && !isSyntheticArtifact()

/**
 * Frames the dex toolchain generated, not your editor. Reliable markers:
 *  - `$ExternalSyntheticLambda` in className: D8 desugaring of an invokedynamic lambda.
 *  - `$$ExternalSynthetic` in className: catches `ExternalSyntheticOuterClass`,
 *    `ExternalSyntheticApiModelOutline`, and friends that R8 inserts.
 *  - methodName starts with `$r8$lambda$`: R8's renamed bridge methods.
 *  - fileName is one of the placeholder strings the toolchain stamps on
 *    sourceless classes.
 */
private fun StackFrame.isSyntheticArtifact(): Boolean {
    if (className.contains("\$ExternalSyntheticLambda")) return true
    if (className.contains("\$\$ExternalSynthetic")) return true
    if (methodName.startsWith("\$r8\$lambda\$")) return true
    if (fileName == "D8\$\$SyntheticClass") return true
    if (fileName == "SyntheticClass") return true
    return false
}

private fun topPackagesIn(frames: List<IndexedValue<StackFrame>>): List<String> {
    return frames.asSequence()
        .map { it.value.className.split('.').take(2).joinToString(".") }
        .distinct()
        .take(2)
        .toList()
}

private const val COLLAPSE_HINT_THRESHOLD = 4

private fun Violation.fullStackText(): String {
    val header = buildString {
        append(title).append('\n')
        append("Occurrences: ×").append(occurrenceCount).append('\n')
        append("Thread: ").append(threadName).append('\n')
        if (message.isNotBlank()) {
            append("Message: ").append(message).append('\n')
        }
        append('\n')
    }
    val stack = stackTrace.joinToString("\n") { "\tat ${it.formatFull()}" }
    return header + stack
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(ClipboardManager::class.java) ?: return
    cm.setPrimaryClip(ClipData.newPlainText("Strictly violation", text))
}

private fun share(context: Context, violation: Violation) {
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "Strictly: ${violation.title}")
        putExtra(Intent.EXTRA_TEXT, violation.fullStackText())
    }
    context.startActivity(Intent.createChooser(shareIntent, "Share violation").apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    })
}

private fun formatTime(millis: Long): String {
    val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    return sdf.format(Date(millis))
}
