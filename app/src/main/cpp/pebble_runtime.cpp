#include <jni.h>

#include <algorithm>
#include <cstdint>
#include <cstring>
#include <string>

namespace {
constexpr jint kFrameWidth = 144;
constexpr jint kFrameHeight = 168;
constexpr jint kBytesPerPixel = 4;

std::string abi_name() {
#if defined(__aarch64__)
    return "arm64-v8a";
#elif defined(__arm__)
    return "armeabi-v7a";
#elif defined(__x86_64__)
    return "x86_64";
#elif defined(__i386__)
    return "x86";
#else
    return "unknown";
#endif
}
}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_manufacttest_pebblereardisplay_runtime_PebbleNativeRuntime_nativeBuildInfo(
        JNIEnv* env,
        jclass /* clazz */) {
    const std::string result = "Pebble native runtime bridge · ABI " + abi_name()
            + " · framebuffer 144x168 RGBA8888";
    return env->NewStringUTF(result.c_str());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_manufacttest_pebblereardisplay_runtime_PebbleNativeRuntime_nativeSelfTest(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    std::uint32_t value = 0x50454242U;
    value ^= 0x0000000EU;
    return value == 0x5045424CU ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_manufacttest_pebblereardisplay_runtime_PebbleNativeRuntime_nativeFrameWidth(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return kFrameWidth;
}

extern "C" JNIEXPORT jint JNICALL
Java_com_manufacttest_pebblereardisplay_runtime_PebbleNativeRuntime_nativeFrameHeight(
        JNIEnv* /* env */,
        jclass /* clazz */) {
    return kFrameHeight;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_com_manufacttest_pebblereardisplay_runtime_PebbleNativeRuntime_nativeFillTestFrame(
        JNIEnv* env,
        jclass /* clazz */,
        jobject buffer,
        jlong frameNumber) {
    if (buffer == nullptr) {
        return JNI_FALSE;
    }

    auto* pixels = static_cast<std::uint8_t*>(env->GetDirectBufferAddress(buffer));
    const jlong capacity = env->GetDirectBufferCapacity(buffer);
    const jlong required = static_cast<jlong>(kFrameWidth) * kFrameHeight * kBytesPerPixel;
    if (pixels == nullptr || capacity < required) {
        return JNI_FALSE;
    }

    const std::uint8_t phase = static_cast<std::uint8_t>(frameNumber % 256);
    for (jint y = 0; y < kFrameHeight; ++y) {
        for (jint x = 0; x < kFrameWidth; ++x) {
            const jlong offset = (static_cast<jlong>(y) * kFrameWidth + x) * kBytesPerPixel;
            const bool checker = ((x / 12) + (y / 12)) % 2 == 0;
            pixels[offset] = checker ? static_cast<std::uint8_t>((x + phase) & 0xFF) : 20;
            pixels[offset + 1] = checker ? static_cast<std::uint8_t>((y * 2 + phase) & 0xFF) : 180;
            pixels[offset + 2] = checker ? 210 : static_cast<std::uint8_t>((x + y + phase) & 0xFF);
            pixels[offset + 3] = 255;
        }
    }

    return JNI_TRUE;
}
