package com.presencestudio

import android.app.Activity

/** JNI bridge to Discord Social SDK's Android RPC Rich Presence API. */
object DiscordPresenceBridge {
    @Volatile private var listener: ((Boolean, String) -> Unit)? = null
    @Volatile private var loaded = false

    @Synchronized
    fun initialize(activity: Activity) {
        if (!loaded) {
            System.loadLibrary("presence_bridge")
            loaded = true
        }
        com.discord.socialsdk.DiscordSocialSdkInit.setEngineActivity(activity)
        nativeInitialize()
    }

    fun setResultListener(callback: ((Boolean, String) -> Unit)?) { listener = callback }

    @JvmStatic
    fun onNativePresenceResult(success: Boolean, message: String) { listener?.invoke(success, message) }

    external fun nativeInitialize()
    external fun nativePublish(
        applicationId: String, name: String, type: String, details: String, state: String,
        artworkKey: String, button1: String, url1: String, button2: String, url2: String,
        startTimestampMillis: Long, endTimestampMillis: Long
    )
    external fun nativeClear()
    external fun nativeRunCallbacks()
}
