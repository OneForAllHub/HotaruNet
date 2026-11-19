#include <jni.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <stdio.h>

#include "hotaru_vault_gen.h"

namespace {

inline uint8_t rotr8(uint8_t val, unsigned shift) {
    shift &= 7U;
    return shift == 0U ? val : static_cast<uint8_t>((val >> shift) | (val << (8U - shift)));
}

void wipe_buffer(void* ptr, size_t len) {
    volatile uint8_t* p = reinterpret_cast<volatile uint8_t*>(ptr);
    while (len--) {
        *p++ = 0U;
    }
}

bool ascii_contains_icase(const char* haystack, const char* needle) {
    if (!haystack || !needle || !*needle) return false;
    size_t nlen = strlen(needle);
    for (const char* h = haystack; *h; ++h) {
        size_t i = 0;
        while (i < nlen && h[i]) {
            char a = h[i];
            char b = needle[i];
            if (a >= 'A' && a <= 'Z') a = static_cast<char>(a - 'A' + 'a');
            if (b >= 'A' && b <= 'Z') b = static_cast<char>(b - 'A' + 'a');
            if (a != b) break;
            ++i;
        }
        if (i == nlen) return true;
    }
    return false;
}

bool is_ptrace_attached() {
    FILE* f = fopen("/proc/self/status", "r");
    if (!f) return false;
    char line[256];
    bool traced = false;
    while (fgets(line, sizeof(line), f)) {
        if (strncmp(line, "TracerPid:", 10) == 0) {
            long pid = strtol(line + 10, nullptr, 10);
            if (pid > 0) traced = true;
            break;
        }
    }
    fclose(f);
    return traced;
}

bool has_injected_framework() {
    FILE* f = fopen("/proc/self/maps", "r");
    if (!f) return false;
    char line[512];
    bool detected = false;
    while (fgets(line, sizeof(line), f)) {
        if (ascii_contains_icase(line, "frida") ||
            ascii_contains_icase(line, "xposed") ||
            ascii_contains_icase(line, "lsposed") ||
            ascii_contains_icase(line, "edxposed") ||
            ascii_contains_icase(line, "substrate")) {
            detected = true;
            break;
        }
    }
    fclose(f);
    return detected;
}

bool environment_compromised() {
    return is_ptrace_attached() || has_injected_framework();
}

jbyteArray JNICALL resolve_vault_entry(JNIEnv* env, jclass /*clazz*/, jint id) {
    if (id < 0 || id >= HOTARU_VAULT_COUNT || environment_compromised()) {
        return nullptr;
    }
    const HotaruVaultEntry& entry = kHotaruVault[id];
    if (entry.len == 0 || entry.len > 512) {
        return nullptr;
    }
    uint8_t plain[512];
    for (uint16_t i = 0; i < entry.len; ++i) {
        uint64_t s = entry.seed + static_cast<uint64_t>(i) * 0x9E3779B97F4A7C15ULL;
        uint8_t k = static_cast<uint8_t>(((s ^ (s >> 27)) * static_cast<uint64_t>(entry.mult)) + (static_cast<uint64_t>(i) * 17ULL));
        unsigned r = (static_cast<unsigned>(entry.rot) + static_cast<unsigned>(i)) % 7U + 1U;
        plain[i] = rotr8(static_cast<uint8_t>(entry.data[i] ^ k), r);
    }
    jbyteArray out = env->NewByteArray(static_cast<jsize>(entry.len));
    if (out) {
        env->SetByteArrayRegion(out, 0, static_cast<jsize>(entry.len), reinterpret_cast<const jbyte*>(plain));
    }
    wipe_buffer(plain, entry.len);
    return out;
}

}  // namespace

__attribute__((visibility("default"))) JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* /*reserved*/) {
    JNIEnv* env = nullptr;
    if (vm->GetEnv(reinterpret_cast<void**>(&env), JNI_VERSION_1_6) != JNI_OK || !env) {
        return JNI_ERR;
    }
    jclass cls = env->FindClass("com/v2ray/ang/util/HotaruCrypto");
    if (!cls) {
        return JNI_ERR;
    }
    static const JNINativeMethod methods[] = {
        {"n0", "(I)[B", reinterpret_cast<void*>(&resolve_vault_entry)}
    };
    if (env->RegisterNatives(cls, methods, 1) != JNI_OK) {
        return JNI_ERR;
    }
    return JNI_VERSION_1_6;
}
