package com.presencestudio

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.net.HttpURLConnection
import java.net.URL
import java.io.OutputStreamWriter
import java.util.concurrent.Executors

/**
 * Exchanges a Discord OAuth token for a Firebase custom token and syncs presets.
 * The bridge URL is configured in app preferences; no privileged credential is in the APK.
 */
object FirebaseCloudSync {
    private val io = Executors.newSingleThreadExecutor()
    private val auth by lazy { FirebaseAuth.getInstance() }
    private val db by lazy { FirebaseFirestore.getInstance() }

    fun signInAndSync(
        context: Context,
        bridgeUrl: String,
        discordAccessToken: String,
        localPresetsJson: String,
        callback: (Result<String>) -> Unit
    ) {
        io.execute {
            try {
                require(bridgeUrl.startsWith("https://") && !bridgeUrl.contains("<") && !bridgeUrl.contains(">")) {
                    "Set the deployed HTTPS Worker URL in the Discord cloud bridge field first."
                }
                val connection = (URL(bridgeUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    connectTimeout = 15000
                    readTimeout = 15000
                    doOutput = true
                    setRequestProperty("Authorization", "Bearer $discordAccessToken")
                    setRequestProperty("Content-Type", "application/json")
                    setRequestProperty("Accept", "application/json")
                }
                connection.outputStream.use { it.write("{}".toByteArray(Charsets.UTF_8)) }
                val status = connection.responseCode
                val raw = (if (status in 200..299) connection.inputStream else connection.errorStream)
                    ?.bufferedReader()?.use { it.readText() } ?: "{}"
                if (status !in 200..299) {
                    val error = JSONObject(raw).optString("error", "HTTP $status")
                    error("Cloud login failed: $error")
                }
                val response = JSONObject(raw)
                val customToken = response.optString("firebaseCustomToken")
                require(customToken.isNotBlank()) { "Bridge did not return a Firebase custom token." }

                // Wait for Firebase Auth to establish the verified account before touching Firestore.
                val signInLatch = java.util.concurrent.CountDownLatch(1)
                var signInError: Exception? = null
                auth.signInWithCustomToken(customToken)
                    .addOnSuccessListener { signInLatch.countDown() }
                    .addOnFailureListener { signInError = it; signInLatch.countDown() }
                if (!signInLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) error("Firebase sign-in timed out.")
                signInError?.let { throw it }
                val user = auth.currentUser ?: error("Firebase did not establish a signed-in account.")
                require(user.uid.startsWith("discord_")) { "Unexpected Firebase account identity." }

                val doc = db.collection("users").document(user.uid)
                    .collection("appData").document("main")
                val readLatch = java.util.concurrent.CountDownLatch(1)
                var cloudJson = "[]"
                var readError: Exception? = null
                doc.get().addOnSuccessListener { snapshot ->
                    cloudJson = snapshot.getString("presetsJson") ?: "[]"
                    readLatch.countDown()
                }.addOnFailureListener { readError = it; readLatch.countDown() }
                if (!readLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) error("Cloud data read timed out.")
                readError?.let { throw it }

                // Merge by stable preset ID: preserve local-only and cloud-only presets; local edits win conflicts.
                val merged = LinkedHashMap<String, JSONObject>()
                fun add(rawJson: String) {
                    val array = JSONArray(rawJson)
                    for (i in 0 until array.length()) {
                        val item = array.optJSONObject(i) ?: continue
                        val id = item.optString("id")
                        if (id.isNotBlank()) merged[id] = item
                    }
                }
                add(cloudJson)
                add(localPresetsJson)
                val mergedJson = JSONArray().apply { merged.values.forEach { put(it) } }.toString()

                val writeLatch = java.util.concurrent.CountDownLatch(1)
                var writeError: Exception? = null
                doc.set(mapOf(
                    "presetsJson" to mergedJson,
                    "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "schemaVersion" to 1
                )).addOnSuccessListener { writeLatch.countDown() }
                    .addOnFailureListener { writeError = it; writeLatch.countDown() }
                if (!writeLatch.await(30, java.util.concurrent.TimeUnit.SECONDS)) error("Cloud data upload timed out.")
                writeError?.let { throw it }
                callback(Result.success(mergedJson))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun savePresets(presetsJson: String, callback: (Result<Unit>) -> Unit = {}) {
        io.execute {
            try {
                val user = auth.currentUser ?: error("Sign in to sync presets.")
                require(user.uid.startsWith("discord_")) { "Unexpected Firebase account identity." }
                val latch = java.util.concurrent.CountDownLatch(1)
                var failure: Exception? = null
                db.collection("users").document(user.uid).collection("appData").document("main")
                    .set(mapOf(
                        "presetsJson" to presetsJson,
                        "updatedAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                        "schemaVersion" to 1
                    ))
                    .addOnSuccessListener { latch.countDown() }
                    .addOnFailureListener { failure = it; latch.countDown() }
                if (!latch.await(30, java.util.concurrent.TimeUnit.SECONDS)) error("Cloud data upload timed out.")
                failure?.let { throw it }
                callback(Result.success(Unit))
            } catch (error: Exception) {
                callback(Result.failure(error))
            }
        }
    }

    fun signOut() {
        auth.signOut()
    }
}
