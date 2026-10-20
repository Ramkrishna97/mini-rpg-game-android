#include <android/asset_manager_jni.h>
#include <android/native_window_jni.h>
#include <jni.h>

#include "VulkanRenderer.h"

using emberfall::VulkanRenderer;

namespace {
    inline VulkanRenderer* fromHandle(jlong handle) {
        return reinterpret_cast<VulkanRenderer*>(handle);
    }
}  // namespace

// Kotlin class: com.emberfall.game.render.NativeRenderer
extern "C" {

JNIEXPORT jlong JNICALL
Java_com_emberfall_game_render_NativeRenderer_nativeCreate(
        JNIEnv* env, jobject /*thiz*/, jobject surface, jobject assetManager) {
    ANativeWindow* window = ANativeWindow_fromSurface(env, surface);
    if (!window) return 0;

    AAssetManager* assets = AAssetManager_fromJava(env, assetManager);
    auto* renderer = new VulkanRenderer();
    if (!renderer->init(window, assets)) {
        renderer->shutdown();
        delete renderer;
        return 0;
    }
    return reinterpret_cast<jlong>(renderer);
}

JNIEXPORT void JNICALL
Java_com_emberfall_game_render_NativeRenderer_nativeDestroy(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    VulkanRenderer* renderer = fromHandle(handle);
    if (!renderer) return;
    renderer->shutdown();
    delete renderer;
}

JNIEXPORT void JNICALL
Java_com_emberfall_game_render_NativeRenderer_nativeRequestRecreate(
        JNIEnv* /*env*/, jobject /*thiz*/, jlong handle) {
    if (VulkanRenderer* renderer = fromHandle(handle)) renderer->requestRecreate();
}

JNIEXPORT jboolean JNICALL
Java_com_emberfall_game_render_NativeRenderer_nativeUploadAtlas(
        JNIEnv* env, jobject /*thiz*/, jlong handle, jintArray pixels, jint width, jint height) {
    VulkanRenderer* renderer = fromHandle(handle);
    if (!renderer || !pixels) return JNI_FALSE;

    jint* data = env->GetIntArrayElements(pixels, nullptr);
    const bool ok = renderer->uploadAtlas(reinterpret_cast<const uint32_t*>(data), width, height);
    env->ReleaseIntArrayElements(pixels, data, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_com_emberfall_game_render_NativeRenderer_nativeDrawFrame(
        JNIEnv* env, jobject /*thiz*/, jlong handle,
        jfloatArray sprites, jint count,
        jfloat clearR, jfloat clearG, jfloat clearB,
        jfloatArray lights, jint lightCount, jfloat ambient) {
    VulkanRenderer* renderer = fromHandle(handle);
    if (!renderer || !sprites || count < 0 || lightCount < 0) return JNI_FALSE;

    const jsize spriteLength = env->GetArrayLength(sprites);
    if (static_cast<jsize>(count) * 13 > spriteLength) return JNI_FALSE;

    const jfloat* lightData = nullptr;
    jfloat* lightBase = nullptr;
    if (lights && lightCount > 0 && ambient > 0.0f) {
        if (static_cast<jsize>(lightCount) * 4 > env->GetArrayLength(lights)) return JNI_FALSE;
        lightBase = env->GetFloatArrayElements(lights, nullptr);
        lightData = lightBase;
    }

    jfloat* data = env->GetFloatArrayElements(sprites, nullptr);
    const float clear[3] = {clearR, clearG, clearB};
    const bool ok = renderer->drawFrame(reinterpret_cast<const float*>(data),
            static_cast<uint32_t>(count), clear,
            lightData, static_cast<uint32_t>(lightCount), ambient);
    env->ReleaseFloatArrayElements(sprites, data, JNI_ABORT);
    if (lightBase) env->ReleaseFloatArrayElements(lights, lightBase, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

}  // extern "C"