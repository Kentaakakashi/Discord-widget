package com.presencestudio

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.json.JSONArray
import org.json.JSONObject

data class Preset(
    val id: String,
    val name: String,
    val type: String = "Playing",
    val details: String = "",
    val state: String = "",
    val button1: String = "",
    val url1: String = "",
    val button2: String = "",
    val url2: String = "",
    val artworkKey: String = "",
    val artworkUri: String = "",
    val startTimestamp: Long = 0L,
    val endTimestamp: Long = 0L
)

private fun Preset.toJson() = JSONObject().apply {
    put("id", id); put("name", name); put("type", type); put("details", details)
    put("state", state); put("button1", button1); put("url1", url1)
    put("button2", button2); put("url2", url2); put("artworkKey", artworkKey)
    put("artworkUri", artworkUri); put("startTimestamp", startTimestamp)
    put("endTimestamp", endTimestamp)
}

private fun JSONObject.toPreset() = Preset(
    id = optString("id").ifBlank { "imported-${System.currentTimeMillis()}" },
    name = optString("name", "Untitled"),
    type = optString("type", "Playing"),
    details = optString("details"), state = optString("state"),
    button1 = optString("button1"), url1 = optString("url1"),
    button2 = optString("button2"), url2 = optString("url2"),
    artworkKey = optString("artworkKey"), artworkUri = optString("artworkUri"),
    startTimestamp = optLong("startTimestamp"), endTimestamp = optLong("endTimestamp")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences("presence_studio", MODE_PRIVATE)
        fun load(): List<Preset> {
            val raw = prefs.getString("presets", null) ?: return starterPresets()
            return try {
                val a = JSONArray(raw)
                (0 until a.length()).map { a.getJSONObject(it).toPreset() }
            } catch (_: Exception) { starterPresets() }
        }
        fun save(list: List<Preset>) {
            val a = JSONArray()
            list.forEach { a.put(it.toJson()) }
            prefs.edit().putString("presets", a.toString()).apply()
        }
        setContent { StudioScreen(load(), ::save) }
    }
}

private fun starterPresets() = listOf(
    Preset("theo", "Theo — Development", details = "Building my personal AI assistant",
        state = "Shipping features", button1 = "Repository", url1 = "https://github.com/"),
    Preset("gaming", "Gaming", details = "Playing a game", state = "Custom session"),
    Preset("movie", "Movie Night", type = "Watching", details = "Watching something good", state = "Cinema mode"),
    Preset("youtube", "YouTube", type = "Watching", details = "Watching videos", state = "Just chilling")
)

private val accent = Color(0xFF9B87F5)
private val bg = Color(0xFF0C0D12)
private val panel = Color(0xFF171821)
private val muted = Color(0xFF9A9AAF)

@Composable
private fun StudioScreen(initial: List<Preset>, save: (List<Preset>) -> Unit) {
    var presets by remember { mutableStateOf(initial) }
    var active by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Preset?>(null) }
    var notice by remember { mutableStateOf("Presets stay on this device • Discord publishing is not connected") }
    val context = androidx.compose.ui.platform.LocalContext.current

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri != null) try {
            context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { out ->
                out.write(JSONArray().apply { presets.forEach { put(it.toJson()) } }.toString(2))
            }
            notice = "Presets exported as JSON"
        } catch (e: Exception) { notice = "Export failed: ${e.message}" }
    }
    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) try {
            val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: error("Could not read file")
            val array = JSONArray(raw)
            val imported = (0 until array.length()).map { array.getJSONObject(it).toPreset() }
            if (imported.isEmpty()) error("The file contains no presets")
            presets = imported
            save(imported)
            active = null
            notice = "Imported ${imported.size} preset(s)"
        } catch (e: Exception) { notice = "Import failed: ${e.message}" }
    }

    fun persist(list: List<Preset>) { presets = list; save(list) }
    MaterialTheme(colorScheme = darkColorScheme(primary = accent, background = bg, surface = panel,
        onBackground = Color(0xFFEDEDF4), onSurface = Color(0xFFEDEDF4))) {
        Surface(Modifier.fillMaxSize(), color = bg) {
            if (editing != null) {
                Editor(editing!!, onCancel = { editing = null }, onSave = { p ->
                    persist(if (presets.any { it.id == p.id }) presets.map { if (it.id == p.id) p else it } else presets + p)
                    editing = null
                    notice = "Saved “${p.name}” locally"
                })
            } else {
                Column(Modifier.fillMaxSize().padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("PRESENCE STUDIO", color = accent, style = MaterialTheme.typography.labelMedium)
                            Text("Your activity library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        }
                        Icon(Icons.Default.Gamepad, null, tint = accent, modifier = Modifier.size(34.dp))
                    }
                    Spacer(Modifier.height(14.dp))
                    val selected = presets.firstOrNull { it.id == active }
                    Column(Modifier.fillMaxWidth().background(panel, RoundedCornerShape(20.dp)).padding(16.dp)) {
                        Text("ACTIVITY PREVIEW", color = muted, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(selected?.name ?: "No activity selected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(selected?.details ?: "Choose a preset below", color = muted)
                        Text(selected?.state ?: "Preview only — not published", color = Color(0xFF79D6A5))
                        if (selected != null && selected.endTimestamp > 0L) {
                            Text("Ends: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(selected.endTimestamp))}", color = muted)
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(notice, color = muted, style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }, modifier = Modifier.weight(1f)) { Text("Import JSON") }
                        OutlinedButton(onClick = { exportLauncher.launch("presence-presets.json") }, modifier = Modifier.weight(1f)) { Text("Export JSON") }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("SAVED PRESETS", color = muted, modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { editing = Preset("custom-${System.currentTimeMillis()}", "New presence") }) {
                            Icon(Icons.Default.Add, null); Text(" New")
                        }
                    }
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(9.dp), contentPadding = PaddingValues(vertical = 8.dp)) {
                        items(presets, key = { it.id }) { p ->
                            Row(Modifier.fillMaxWidth()
                                .background(if (active == p.id) Color(0xFF242036) else panel, RoundedCornerShape(16.dp))
                                .clickable { active = p.id; notice = "Selected “${p.name}” • preview only, not published" }
                                .padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Gamepad, null, tint = accent, modifier = Modifier.size(30.dp))
                                Spacer(Modifier.width(9.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p.name, fontWeight = FontWeight.SemiBold)
                                    Text("${p.type} • ${p.details}", color = muted, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                                }
                                IconButton(onClick = { editing = p }) { Icon(Icons.Default.Edit, "Edit", tint = accent) }
                                IconButton(onClick = {
                                    persist(presets.filterNot { it.id == p.id })
                                    if (active == p.id) active = null
                                }) { Icon(Icons.Default.Delete, "Delete", tint = muted) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Editor(p: Preset, onCancel: () -> Unit, onSave: (Preset) -> Unit) {
    var name by remember(p.id) { mutableStateOf(p.name) }
    var type by remember(p.id) { mutableStateOf(p.type) }
    var details by remember(p.id) { mutableStateOf(p.details) }
    var state by remember(p.id) { mutableStateOf(p.state) }
    var b1 by remember(p.id) { mutableStateOf(p.button1) }
    var u1 by remember(p.id) { mutableStateOf(p.url1) }
    var b2 by remember(p.id) { mutableStateOf(p.button2) }
    var u2 by remember(p.id) { mutableStateOf(p.url2) }
    var art by remember(p.id) { mutableStateOf(p.artworkKey) }
    var artUri by remember(p.id) { mutableStateOf(p.artworkUri) }
    var start by remember(p.id) { mutableStateOf(if (p.startTimestamp > 0L) p.startTimestamp.toString() else "") }
    var end by remember(p.id) { mutableStateOf(if (p.endTimestamp > 0L) p.endTimestamp.toString() else "") }
    val editorContext = androidx.compose.ui.platform.LocalContext.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            artUri = uri.toString()
            try { editorContext.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) } catch (_: Exception) {}
        }
    }
    Column(Modifier.fillMaxSize().padding(18.dp)) {
        Text("PRESET EDITOR", color = accent, style = MaterialTheme.typography.labelMedium)
        Text("Customize activity", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            item { OutlinedTextField(name, { name = it }, label = { Text("Preset name") }, modifier = Modifier.fillMaxWidth()) }
            item {
                Text("Activity type", color = muted)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Playing", "Watching", "Listening").forEach { FilterChip(selected = type == it, onClick = { type = it }, label = { Text(it) }) }
                }
            }
            item { OutlinedTextField(details, { details = it }, label = { Text("Details / first line") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(state, { state = it }, label = { Text("State / second line") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(art, { art = it }, label = { Text("Discord artwork asset key") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (artUri.isBlank()) "Choose artwork image" else "Change selected artwork") } }
            item { Text(if (artUri.isBlank()) "No local image selected" else "Image selected on this device", color = muted) }
            item { Text("TIMESTAMPS (Unix milliseconds; optional)", color = accent) }
            item { OutlinedTextField(start, { start = it.filter(Char::isDigit) }, label = { Text("Start timestamp") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(end, { end = it.filter(Char::isDigit) }, label = { Text("End timestamp") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("BUTTON 1", color = accent) }
            item { OutlinedTextField(b1, { b1 = it }, label = { Text("Button label") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(u1, { u1 = it }, label = { Text("Button URL") }, modifier = Modifier.fillMaxWidth()) }
            item { Text("BUTTON 2", color = accent) }
            item { OutlinedTextField(b2, { b2 = it }, label = { Text("Button label") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedTextField(u2, { u2 = it }, label = { Text("Button URL") }, modifier = Modifier.fillMaxWidth()) }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) { Text("Cancel") }
            Button(onClick = {
                onSave(p.copy(name = name.trim().ifBlank { "Untitled presence" }, type = type,
                    details = details.trim(), state = state.trim(), button1 = b1.trim(), url1 = u1.trim(),
                    button2 = b2.trim(), url2 = u2.trim(), artworkKey = art.trim(), artworkUri = artUri,
                    startTimestamp = start.toLongOrNull() ?: 0L, endTimestamp = end.toLongOrNull() ?: 0L))
            }, modifier = Modifier.weight(1f)) { Text("Save preset") }
        }
    }
}
