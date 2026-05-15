package com.vwap.strictly.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vwap.strictly.Strictly
import java.io.File
import java.net.URL

/**
 * The sample exists to:
 * 1. Prove Strictly's auto-init works — there's no Application subclass,
 *    no manual install call, yet the live notification + shortcut appear.
 * 2. Give the dev a one-tap way to trigger each kind of violation so they
 *    can see the UI behaviour: how the notification updates, how the list
 *    de-dupes, what a stack trace looks like in the detail view.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    SampleHome()
                }
            }
        }
    }
}

@Composable
private fun SampleHome() {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Strictly Sample",
            style = MaterialTheme.typography.headlineMedium,
        )
        Text(
            text = "Tap any trigger to fire a StrictMode violation. Watch the live notification update, and long-press the app icon for a shortcut into the detail view.",
            style = MaterialTheme.typography.bodyMedium,
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { triggerDiskRead() },
        ) { Text("Disk read on main thread") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { triggerDiskWrite() },
        ) { Text("Disk write on main thread") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { triggerNetwork() },
        ) { Text("Network on main thread") }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = { triggerScrollStorm() },
        ) { Text("Burst of 50 identical disk reads (test de-dupe)") }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { Strictly.openDetailScreen(context) },
        ) { Text("Open Strictly detail screen") }

        OutlinedButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = { Strictly.clear() },
        ) { Text("Clear violations") }
    }
}

// ---- Violation triggers ----

private fun triggerDiskRead() {
    val temp = File.createTempFile("strictly_sample", ".txt").apply { deleteOnExit() }
    temp.writeText("hello")
    @Suppress("UNUSED_VARIABLE")
    val unused = temp.readText() // disk read on main thread
}

private fun triggerDiskWrite() {
    val temp = File.createTempFile("strictly_sample_write", ".txt").apply { deleteOnExit() }
    temp.writeText("hello") // disk write on main thread
}

private fun triggerNetwork() {
    runCatching {
        URL("https://example.com").openConnection().getInputStream().close()
    }
}

private fun triggerScrollStorm() {
    val temp = File.createTempFile("strictly_sample_storm", ".txt").apply { deleteOnExit() }
    temp.writeText("hello")
    repeat(50) { temp.readText() }
}
