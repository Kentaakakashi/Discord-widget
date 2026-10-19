#include <jni.h>
#include <android/log.h>
#include <discordpp.h>
#include <memory>
#include <mutex>
#include <string>
#include <cstdint>

#define LOG_TAG "PresenceStudioRPC"

namespace {
JavaVM* g_vm = nullptr;
std::shared_ptr<discordpp::Client> g_client;
std::mutex g_mutex;

std::string fromJ(JNIEnv* env, jstring value) {
    if (!value) return {};
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (!chars) return {};
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

void report(bool success, const std::string& message) {
    if (!g_vm) return;
    JNIEnv* env = nullptr;
    bool attached = false;
    jint status = g_vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6);
    if (status == JNI_EDETACHED) {
        if (g_vm->AttachCurrentThread(&env, nullptr) != JNI_OK) return;
        attached = true;
    } else if (status != JNI_OK) return;
    jclass cls = env->FindClass("com/presencestudio/DiscordPresenceBridge");
    if (cls) {
        jmethodID method = env->GetStaticMethodID(cls, "onNativePresenceResult", "(ZLjava/lang/String;)V");
        if (method) {
            jstring text = env->NewStringUTF(message.c_str());
            env->CallStaticVoidMethod(cls, method, success ? JNI_TRUE : JNI_FALSE, text);
            env->DeleteLocalRef(text);
        }
        env->DeleteLocalRef(cls);
    }
    if (env->ExceptionCheck()) env->ExceptionClear();
    if (attached) g_vm->DetachCurrentThread();
}
}

extern "C" JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void*) {
    g_vm = vm;
    return JNI_VERSION_1_6;
}

extern "C" JNIEXPORT void JNICALL
Java_com_presencestudio_DiscordPresenceBridge_nativeInitialize(JNIEnv*, jobject) {
    std::lock_guard<std::mutex> lock(g_mutex);
    if (!g_client) g_client = std::make_shared<discordpp::Client>();
}

extern "C" JNIEXPORT void JNICALL
Java_com_presencestudio_DiscordPresenceBridge_nativePublish(
    JNIEnv* env, jobject, jstring appId, jstring name, jstring type,
    jstring details, jstring state, jstring artworkKey,
    jstring button1, jstring url1, jstring button2, jstring url2,
    jlong startMs, jlong endMs) {
    const std::string appIdStr = fromJ(env, appId);
    if (appIdStr.empty()) { report(false, "Enter your numeric Discord Application ID first."); return; }
    uint64_t appIdValue = 0;
    try { appIdValue = std::stoull(appIdStr); }
    catch (...) { report(false, "Discord Application ID must contain digits only."); return; }
    if (appIdValue == 0) { report(false, "Discord Application ID is invalid."); return; }

    std::shared_ptr<discordpp::Client> client;
    {
        std::lock_guard<std::mutex> lock(g_mutex);
        if (!g_client) g_client = std::make_shared<discordpp::Client>();
        client = g_client;
    }
    client->SetApplicationId(appIdValue);

    discordpp::Activity activity;
    const std::string nameStr = fromJ(env, name);
    const std::string typeStr = fromJ(env, type);
    if (!nameStr.empty()) activity.SetName(nameStr);
    if (typeStr == "Watching") activity.SetType(discordpp::ActivityTypes::Watching);
    else if (typeStr == "Listening") activity.SetType(discordpp::ActivityTypes::Listening);
    else activity.SetType(discordpp::ActivityTypes::Playing);
    const std::string detailsStr = fromJ(env, details);
    const std::string stateStr = fromJ(env, state);
    if (!detailsStr.empty()) activity.SetDetails(detailsStr);
    if (!stateStr.empty()) activity.SetState(stateStr);

    const std::string key = fromJ(env, artworkKey);
    if (!key.empty()) {
        discordpp::ActivityAssets assets;
        assets.SetLargeImage(key);
        activity.SetAssets(assets);
    }
    auto addButton = [&env, &activity](jstring labelJ, jstring urlJ) {
        const std::string label = fromJ(env, labelJ);
        const std::string url = fromJ(env, urlJ);
        if (label.empty() || url.empty()) return;
        if (url.rfind("https://", 0) != 0 && url.rfind("http://", 0) != 0) return;
        discordpp::ActivityButton button;
        button.SetLabel(label.substr(0, 32));
        button.SetUrl(url);
        activity.AddButton(button);
    };
    addButton(button1, url1);
    addButton(button2, url2);

    if (startMs > 0 || endMs > 0) {
        discordpp::ActivityTimestamps timestamps;
        if (startMs > 0) timestamps.SetStart(static_cast<int64_t>(startMs / 1000));
        if (endMs > 0) timestamps.SetEnd(static_cast<int64_t>(endMs / 1000));
        activity.SetTimestamps(timestamps);
    }
    client->UpdateRichPresence(activity, [](const discordpp::ClientResult& result) {
        if (result.Successful()) report(true, "Discord confirmed the Rich Presence update.");
        else report(false, "Discord rejected the update. Ensure Discord is installed, signed in, and the Application ID is valid.");
    });
}

extern "C" JNIEXPORT void JNICALL
Java_com_presencestudio_DiscordPresenceBridge_nativeClear(JNIEnv*, jobject) {
    std::shared_ptr<discordpp::Client> client;
    { std::lock_guard<std::mutex> lock(g_mutex); client = g_client; }
    if (client) client->ClearRichPresence();
    report(true, "Clear Rich Presence request sent.");
}

extern "C" JNIEXPORT void JNICALL
Java_com_presencestudio_DiscordPresenceBridge_nativeRunCallbacks(JNIEnv*, jobject) {
    discordpp::RunCallbacks();
}
