package com.pixelpocket.gallery.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pixelpocket.gallery.data.SaveFormat
import java.text.DateFormat
import java.util.Date
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: GalleryViewModel) {
    val prefs = vm.prefs
    var format by remember { mutableStateOf(prefs.saveFormat) }
    var quality by remember { mutableFloatStateOf(prefs.saveQuality.toFloat()) }
    var album by remember { mutableStateOf(prefs.saveAlbum) }
    var columns by remember { mutableStateOf(prefs.gridColumns) }

    val close = {
        prefs.saveAlbum = album
        vm.screen = Screen.Gallery
    }
    BackHandler { close() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = close) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(Modifier.fillMaxWidth()) {
                ListItem(
                    leadingContent = { Icon(Icons.Filled.AccountCircle, null) },
                    headlineContent = { Text(prefs.userName, fontWeight = FontWeight.SemiBold) },
                    supportingContent = {
                        Column {
                            Text(prefs.email)
                            if (prefs.phone.isNotBlank()) Text(prefs.phone)
                            if (prefs.registeredAt > 0) {
                                Text(
                                    "Member since " + DateFormat.getDateInstance().format(Date(prefs.registeredAt)),
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    },
                )
            }

            Text("Gallery", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Grid columns")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (2..6).forEach { n ->
                    FilterChip(
                        selected = columns == n,
                        onClick = {
                            columns = n
                            vm.updateGridColumns(n)
                        },
                        label = { Text("$n") },
                    )
                }
            }

            Text("Saving edited images", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Text("Default format")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SaveFormat.entries.forEach { f ->
                    FilterChip(
                        selected = format == f,
                        onClick = {
                            format = f
                            prefs.saveFormat = f
                        },
                        label = { Text(f.label) },
                    )
                }
            }
            if (format.supportsQuality) {
                Text("Default quality: ${quality.roundToInt()}%")
                Slider(
                    value = quality,
                    onValueChange = { quality = it },
                    onValueChangeFinished = { prefs.saveQuality = quality.roundToInt() },
                    valueRange = 10f..100f,
                )
            }
            OutlinedTextField(
                value = album,
                onValueChange = { album = it.replace("/", "") },
                label = { Text("Save to album (Pictures/…)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                "Cropped images are saved as a new copy; your originals are never changed.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
