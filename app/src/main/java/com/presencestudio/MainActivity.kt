package com.presencestudio

import android.content.Intent
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.Executors
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Brush
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
    private var callbackUri by mutableStateOf<Uri?>(null)
    private var presenceSdkReady = false
    private val sdkHandler = Handler(Looper.getMainLooper())
    private val sdkPump = object : Runnable {
        override fun run() {
            if (presenceSdkReady) {
                try { DiscordPresenceBridge.nativeRunCallbacks() } catch (_: Throwable) {}
                sdkHandler.postDelayed(this, 25L)
            }
        }
    }
    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); callbackUri = intent.data }
    override fun onDestroy() {
        sdkHandler.removeCallbacks(sdkPump)
        super.onDestroy()
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            DiscordPresenceBridge.initialize(this)
            presenceSdkReady = true
            sdkHandler.post(sdkPump)
        } catch (e: Throwable) {
            android.util.Log.e("PresenceStudio", "Discord Social SDK initialization failed", e)
        }
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
        callbackUri = intent?.data
        setContent {
            StudioScreen(
                load(), ::save, callbackUri, presenceSdkReady,
                onPublish = { p, id ->
                    DiscordPresenceBridge.nativePublish(
                        id, p.name, p.type, p.details, p.state, p.artworkKey,
                        p.button1, p.url1, p.button2, p.url2,
                        p.startTimestamp, p.endTimestamp
                    )
                },
                onClearPresence = { DiscordPresenceBridge.nativeClear() }
            )
        }
    }
}

private fun starterPresets() = listOf(
    Preset("theo", "Theo — Development", details = "Building my personal AI assistant",
        state = "Shipping features", button1 = "Repository", url1 = "https://github.com/",
        artworkKey = "https://raw.githubusercontent.com/Kentaakakashi/Discord-widget/feature/social-sdk-rich-presence/assets/theo-presence.svg"),
    Preset("gaming", "Gaming", details = "Playing a game", state = "Custom session"),
    Preset("movie", "Movie Night", type = "Watching", details = "Watching something good", state = "Cinema mode"),
    Preset("youtube", "YouTube", type = "Watching", details = "Watching videos", state = "Just chilling")
)

private const val DISCORD_REDIRECT_URI = "presencestudio://oauth/callback"

private val accent = Color(0xFFB49AFF)
private val cyan = Color(0xFF65E6F2)
private val bg = Color(0xFF090A10)
private val panel = Color(0xFF151622)
private val muted = Color(0xFFA4A5BD)
private val glassEdge = Color(0x33C7C2FF)

@Composable
private fun StudioScreen(
    initial: List<Preset>,
    save: (List<Preset>) -> Unit,
    oauthCallback: Uri?,
    presenceSdkReady: Boolean,
    onPublish: (Preset, String) -> Unit,
    onClearPresence: () -> Unit
) {
    var presets by remember { mutableStateOf(initial) }
    var active by remember { mutableStateOf<String?>(null) }
    var editing by remember { mutableStateOf<Preset?>(null) }
    val context = androidx.compose.ui.platform.LocalContext.current
    var notice by remember { mutableStateOf(if (presenceSdkReady) "Ready to publish through Discord's Android RPC" else "Discord SDK could not initialize. Check the build logs.") }
    var publishing by remember { mutableStateOf(false) }
    var clientId by remember { mutableStateOf(context.getSharedPreferences("presence_studio", android.content.Context.MODE_PRIVATE).getString("discord_client_id", "") ?: "") }
    var bridgeUrl by remember { mutableStateOf(context.getSharedPreferences("presence_studio", android.content.Context.MODE_PRIVATE).getString("firebase_bridge_url", "") ?: "") }
    var discordUser by remember { mutableStateOf("") }
    DisposableEffect(Unit) {
        DiscordPresenceBridge.setResultListener { _, message ->
            publishing = false
            notice = message
        }
        onDispose { DiscordPresenceBridge.setResultListener(null) }
    }
    val oauthPrefs = remember { context.getSharedPreferences("presence_studio_oauth", android.content.Context.MODE_PRIVATE) }
    // Persist PKCE transaction values so Android recreation does not lose them.
    var oauthState by remember { mutableStateOf(oauthPrefs.getString("state", "") ?: "") }
    var oauthVerifier by remember { mutableStateOf(oauthPrefs.getString("verifier", "") ?: "") }
    var oauthBusy by remember { mutableStateOf(false) }
    var oauthHandled by remember { mutableStateOf("") }

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

    LaunchedEffect(oauthCallback) {
        val callback = oauthCallback ?: return@LaunchedEffect
        if (callback.toString() == oauthHandled) return@LaunchedEffect
        oauthHandled = callback.toString()
        val returnedState = callback.getQueryParameter("state")
        val code = callback.getQueryParameter("code")
        val error = callback.getQueryParameter("error")
        if (error != null) {
            oauthPrefs.edit().remove("state").remove("verifier").apply()
            oauthState = ""; oauthVerifier = ""
            notice = "Discord login cancelled or denied: $error"; return@LaunchedEffect
        }
        val savedState = oauthPrefs.getString("state", "") ?: ""
        val savedVerifier = oauthPrefs.getString("verifier", "") ?: ""
        if (returnedState.isNullOrBlank() || savedState.isBlank() || returnedState != savedState ||
            code.isNullOrBlank() || savedVerifier.isBlank()) {
            notice = "Discord callback could not be verified. Start Connect Discord again. If it repeats, check the exact redirect URL in Developer Portal."
            return@LaunchedEffect
        }
        oauthState = savedState
        oauthVerifier = savedVerifier
        oauthBusy = true
        Executors.newSingleThreadExecutor().execute {
            try {
                val body = listOf(
                    "client_id" to clientId.trim(),
                    "grant_type" to "authorization_code",
                    "code" to code,
                    "redirect_uri" to DISCORD_REDIRECT_URI,
                    "code_verifier" to oauthVerifier
                ).joinToString("&") { (k,v) -> "${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}" }
                val conn = (URL("https://discord.com/api/oauth2/token").openConnection() as HttpURLConnection)
                conn.requestMethod = "POST"; conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.connectTimeout = 15000; conn.readTimeout = 15000
                conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val responseCode = conn.responseCode
                val response = (if (responseCode in 200..299) conn.inputStream else conn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                if (responseCode !in 200..299) error("Token exchange failed (HTTP $responseCode): ${JSONObject(response).optString("error_description", JSONObject(response).optString("error"))}")
                val tokenJson = JSONObject(response)
                val token = tokenJson.optString("access_token")
                if (token.isBlank()) error("Discord did not return an access token")
                val meConn = (URL("https://discord.com/api/users/@me").openConnection() as HttpURLConnection)
                meConn.setRequestProperty("Authorization", "Bearer $token")
                meConn.connectTimeout = 15000; meConn.readTimeout = 15000
                val meCode = meConn.responseCode
                val meText = (if (meCode in 200..299) meConn.inputStream else meConn.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                if (meCode !in 200..299) error("Could not read Discord profile (HTTP $meCode)")
                val me = JSONObject(meText)
                val label = me.optString("global_name").ifBlank { me.optString("username", "Discord user") }
                val localJson = JSONArray().apply { presets.forEach { put(it.toJson()) } }.toString()
                FirebaseCloudSync.signInAndSync(context, bridgeUrl.trim(), token, localJson) { syncResult ->
                    Handler(Looper.getMainLooper()).post {
                        oauthPrefs.edit().remove("state").remove("verifier").apply()
                        oauthState = ""; oauthVerifier = ""
                        oauthBusy = false
                        syncResult.onSuccess { mergedJson ->
                            try {
                                val mergedArray = JSONArray(mergedJson)
                                val mergedPresets = (0 until mergedArray.length()).map { mergedArray.getJSONObject(it).toPreset() }
                                presets = mergedPresets
                                save(mergedPresets)
                                discordUser = label
                                notice = "Connected as $label • presets synced to cloud"
                            } catch (e: Exception) {
                                FirebaseCloudSync.signOut()
                                notice = "Cloud login succeeded but preset data could not be loaded: ${e.message}"
                            }
                        }.onFailure { error ->
                            FirebaseCloudSync.signOut()
                            notice = "Discord verified, but cloud login/sync failed: ${error.message}"
                        }
                    }
                }
            } catch (e: Exception) {
                Handler(Looper.getMainLooper()).post { oauthBusy = false; notice = "Discord login failed: ${e.message}" }
            }
        }
    }

    fun persist(list: List<Preset>) {
        presets = list
        save(list)
        if (com.google.firebase.auth.FirebaseAuth.getInstance().currentUser != null) {
            val json = JSONArray().apply { list.forEach { put(it.toJson()) } }.toString()
            FirebaseCloudSync.savePresets(json) { result ->
                Handler(Looper.getMainLooper()).post {
                    result.onFailure { error -> notice = "Saved locally; cloud sync failed: ${error.message}" }
                }
            }
        }
    }
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
                Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080910), Color(0xFF11101D), Color(0xFF090B12)))).padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("VELTRIX NOVA  /  PRESENCE LAB", color = cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(5.dp))
                            Text("Your activity library", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                            Text("Design it. Preview it. Make it yours.", color = muted, style = MaterialTheme.typography.bodySmall)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.background(Color(0x1FB49AFF), RoundedCornerShape(18.dp)).border(1.dp, glassEdge, RoundedCornerShape(18.dp)).padding(horizontal = 13.dp, vertical = 10.dp)) {
                            Icon(Icons.Default.Gamepad, null, tint = accent, modifier = Modifier.size(27.dp))
                            Text("NOVA", color = cyan, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    val selected = presets.firstOrNull { it.id == active }
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF252039), Color(0xFF171A2A), Color(0xFF11151D))), RoundedCornerShape(24.dp)).border(1.dp, glassEdge, RoundedCornerShape(24.dp)).padding(16.dp)) {
                        Text("ACTIVITY PREVIEW", color = muted, style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(selected?.name ?: "No activity selected", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Text(selected?.details ?: "Choose a preset below", color = muted)
                        Text(selected?.state ?: "Choose a preset to publish", color = Color(0xFF79D6A5))
                        if (selected != null) {
                            Spacer(Modifier.height(10.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                Button(
                                    onClick = {
                                        if (clientId.trim().isBlank() || !clientId.trim().all(Char::isDigit)) {
                                            notice = "Enter your numeric Discord Application ID first."
                                        } else if (!presenceSdkReady) {
                                            notice = "Discord SDK isn't available in this build."
                                        } else {
                                            publishing = true
                                            notice = "Sending “${selected.name}” to Discord…"
                                            onPublish(selected, clientId.trim())
                                        }
                                    },
                                    enabled = !publishing && presenceSdkReady,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    if (publishing) CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    else Text("Publish to Discord")
                                }
                                OutlinedButton(
                                    onClick = { onClearPresence(); notice = "Clear Presence requested" },
                                    enabled = presenceSdkReady
                                ) { Text("Clear") }
                            }
                        }
                        if (selected != null && selected.endTimestamp > 0L) {
                            Text("Ends: ${java.text.DateFormat.getDateTimeInstance().format(java.util.Date(selected.endTimestamp))}", color = muted)
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF1C1C2A), Color(0xFF12141D))), RoundedCornerShape(20.dp)).border(1.dp, Color(0x222FE2F2), RoundedCornerShape(20.dp)).padding(13.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("DISCORD ACCOUNT", color = accent, style = MaterialTheme.typography.labelMedium)
                        if (discordUser.isNotBlank()) {
                            Text("Connected as $discordUser", fontWeight = FontWeight.SemiBold)
                            OutlinedButton(onClick = { FirebaseCloudSync.signOut(); discordUser = ""; notice = "Disconnected from cloud on this device. Revoke app access in Discord settings if needed." }, modifier = Modifier.fillMaxWidth()) { Text("Disconnect") }
                        } else {
                            OutlinedTextField(value = clientId, onValueChange = { clientId = it; context.getSharedPreferences("presence_studio", android.content.Context.MODE_PRIVATE).edit().putString("discord_client_id", it).apply() }, label = { Text("Discord Application / Client ID") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = bridgeUrl, onValueChange = { bridgeUrl = it.trim(); context.getSharedPreferences("presence_studio", android.content.Context.MODE_PRIVATE).edit().putString("firebase_bridge_url", it.trim()).apply() }, label = { Text("Firebase cloud bridge HTTPS URL") }, placeholder = { Text("https://your-worker.workers.dev") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                            Text("Required for cloud login and syncing. Deploy the free Worker from the repository docs first.", color = muted, style = MaterialTheme.typography.bodySmall)
                            Button(onClick = {
                                val id = clientId.trim()
                                if (id.isBlank() || !id.all(Char::isDigit)) { notice = "Enter the numeric Client ID from Discord Developer Portal first." }
                                else try {
                                    val random = ByteArray(32).also { SecureRandom().nextBytes(it) }
                                    val verifier = Base64.encodeToString(random, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                                    val challenge = Base64.encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray()), Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                                    val state = Base64.encodeToString(ByteArray(24).also { SecureRandom().nextBytes(it) }, Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING)
                                    oauthVerifier = verifier; oauthState = state
                                    oauthPrefs.edit().putString("verifier", verifier).putString("state", state).apply()
                                    val auth = Uri.parse("https://discord.com/oauth2/authorize").buildUpon()
                                        .appendQueryParameter("client_id", id)
                                        .appendQueryParameter("response_type", "code")
                                        .appendQueryParameter("redirect_uri", DISCORD_REDIRECT_URI)
                                        .appendQueryParameter("scope", "identify")
                                        .appendQueryParameter("state", state)
                                        .appendQueryParameter("code_challenge", challenge)
                                        .appendQueryParameter("code_challenge_method", "S256")
                                        .build()
                                    context.startActivity(Intent(Intent.ACTION_VIEW, auth))
                                    notice = "Complete sign-in in Discord's browser page, then return here."
                                } catch (e: Exception) { notice = "Could not start Discord login: ${e.message}" }
                            }, enabled = !oauthBusy, modifier = Modifier.fillMaxWidth()) {
                                if (oauthBusy) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp) else Text("Connect Discord")
                            }
                            Text("Uses Discord OAuth in your browser. Never enter your Discord password inside Presence Studio.", color = muted, style = MaterialTheme.typography.bodySmall)
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
                                .background(Brush.linearGradient(if (active == p.id) listOf(Color(0xFF302747), Color(0xFF1B2130)) else listOf(Color(0xFF191A26), Color(0xFF11131C))), RoundedCornerShape(18.dp))
                                .border(1.dp, if (active == p.id) Color(0x887DDBF5) else Color(0x22C7C2FF), RoundedCornerShape(18.dp))
                                .clickable { active = p.id; notice = "Selected “${p.name}” • ready to publish" }
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
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF080910), Color(0xFF12111E), Color(0xFF090B12)))).padding(18.dp)) {
        Text("VELTRIX NOVA  /  EDITOR", color = cyan, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
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
            item { OutlinedTextField(art, { art = it }, label = { Text("Discord artwork key or image URL") }, modifier = Modifier.fillMaxWidth()) }
            item { OutlinedButton(onClick = { picker.launch(arrayOf("image/*")) }, modifier = Modifier.fillMaxWidth()) { Text(if (artUri.isBlank()) "Choose artwork image" else "Change selected artwork") } }
            item { Text(if (artUri.isBlank()) "No local image selected" else "Image selected on this device", color = muted) }
            item { Text("TIMESTAMPS (Unix milliseconds; optional)", color = accent) }
            item { Text("Discord converts these to Unix seconds when publishing. Theo artwork URL is prefilled in the Theo preset. Use an uploaded Discord asset key or supported public image URL; a local image URI cannot be published directly.", color = muted, style = MaterialTheme.typography.bodySmall) }
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
