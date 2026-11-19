import java.security.MessageDigest
import java.security.SecureRandom

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.jaredsburrows.license")
}

android {
    namespace = "com.v2ray.ang"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.hotarunet.client"
        minSdk = 24
        targetSdk = 35
        versionCode = 786
        versionName = "2.3.43"

        val abiFilterList = (properties["ABI_FILTERS"] as? String)?.split(';')
        splits {
            abi {
                isEnable = true
                reset()
                if (!abiFilterList.isNullOrEmpty()) {
                    include(*abiFilterList.toTypedArray())
                } else {
                    include(
                        "arm64-v8a",
                        "armeabi-v7a",
                        "x86_64",
                        "x86"
                    )
                }
                isUniversalApk = abiFilterList.isNullOrEmpty()
            }
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Read-only feed token is verified at build time and scrambled strictly into
        // libhotaruguard.so (never stored in BuildConfig or DEX bytecode).
        // Showcase build allows placeholder vault credentials when private env vars are absent
    }

    signingConfigs {
        create("release") {
            // HotaruNet delivery standard: V1 + V2 + V3 signing. minSdk 24 lets AGP
            // skip V1 (JAR) signing, so it is forced on explicitly.
            isV1SigningEnabled = true
            enableV1Signing = true
            enableV2Signing = true
            enableV3Signing = true

            // CI overrides these through -Pandroid.injected.signing.*.
            val injectedPath = (project.findProperty("android.injected.signing.store.file") as? String)
            val injectedPassword = (project.findProperty("android.injected.signing.store.password") as? String)
            val injectedAlias = (project.findProperty("android.injected.signing.key.alias") as? String)
            val injectedKeyPassword = (project.findProperty("android.injected.signing.key.password") as? String)

            if (injectedPath != null) {
                storeFile = file(injectedPath)
                storePassword = injectedPassword
                keyAlias = injectedAlias
                keyPassword = injectedKeyPassword
            } else {
                val localStore = System.getenv("HOTARU_KEYSTORE_PATH")
                if (!localStore.isNullOrBlank()) {
                    storeFile = file(localStore)
                    storePassword = System.getenv("HOTARU_KEYSTORE_PASSWORD")
                    keyAlias = System.getenv("HOTARU_KEY_ALIAS")
                    keyPassword = System.getenv("HOTARU_KEY_PASSWORD")
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            signingConfig = signingConfigs.getByName("release")
        }
    }

    flavorDimensions.add("distribution")
    productFlavors {
        create("fdroid") {
            dimension = "distribution"
            applicationIdSuffix = ".fdroid"
            buildConfigField("String", "DISTRIBUTION", "\"F-Droid\"")
        }
        create("playstore") {
            dimension = "distribution"
            buildConfigField("String", "DISTRIBUTION", "\"Play Store\"")
        }
    }

    sourceSets {
        getByName("main") {
            jniLibs.directories.add("libs")
            jniLibs.directories.add(layout.buildDirectory.dir("hotaru_native/jniLibs").get().asFile.absolutePath)
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
    }

    applicationVariants.all {
        val variant = this
        val isFdroid = variant.productFlavors.any { it.name == "fdroid" }
        if (isFdroid) {
            val versionCodes =
                mapOf(
                    "armeabi-v7a" to 2, "arm64-v8a" to 1, "x86" to 4, "x86_64" to 3, "universal" to 0
                )

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = output.getFilter("ABI") ?: "universal"
                    output.outputFileName = "HotaruNet_${variant.versionName}-fdroid_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (100 * variant.versionCode + versionCodes[abi]!!).plus(5000000)
                    } else {
                        return@forEach
                    }
                }
        } else {
            val versionCodes =
                mapOf("armeabi-v7a" to 4, "arm64-v8a" to 4, "x86" to 4, "x86_64" to 4, "universal" to 4)

            variant.outputs
                .map { it as com.android.build.gradle.internal.api.ApkVariantOutputImpl }
                .forEach { output ->
                    val abi = if (output.getFilter("ABI") != null)
                        output.getFilter("ABI")
                    else
                        "universal"

                    output.outputFileName = "HotaruNet_${variant.versionName}_${abi}.apk"
                    if (versionCodes.containsKey(abi)) {
                        output.versionCodeOverride =
                            (1000000 * versionCodes[abi]!!).plus(variant.versionCode)
                    } else {
                        return@forEach
                    }
                }
        }
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    androidResources {
        generateLocaleConfig = true
        localeFilters += listOf(
            "en",
            "zh-rCN",
            "zh-rTW",
            "vi",
            "ru",
            "fa",
            "ar",
            "az",
            "bn",
            "bqi-rIR"
        )
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all {
            it.useJUnitPlatform()
            // Treat Jupiter discovery warnings as failures: JUnit 4 failed on an invalid @Test
            // method, such as one that returns a value, while Jupiter only warns and skips it.
            // The key is @API(status = EXPERIMENTAL) in JUnit 6; after a JUnit upgrade, check
            // that a temporary `@Test fun probe() = 1` still fails the unit test task.
            it.systemProperty("junit.platform.discovery.issue.severity.critical", "WARNING")
        }
    }

}

dependencies {
    // Core Libraries
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.aar", "*.jar"))))

    // AndroidX Core Libraries
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)

    // Compose Libraries
    implementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.lifecycle.runtime.compose)
    implementation(libs.coil.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)

    // Data and Storage Libraries
    implementation(libs.mmkv.static)
    implementation(libs.gson)
    implementation(libs.okhttp)

    // Reactive and Utility Libraries
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.coroutines.core)

    // QR Code: CameraX + ZXing
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.compose)
    implementation(libs.core) // zxing core

    // AndroidX Lifecycle and Architecture Components
    implementation(libs.lifecycle.viewmodel.ktx)
    implementation(libs.lifecycle.runtime.ktx)

    // Background Task Libraries
    implementation(libs.work.runtime.ktx)
    implementation(libs.work.multiprocess)

    // Reorderable list
    implementation(libs.reorderable)

    // Testing Libraries
    testImplementation(platform(libs.junit.bom))
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    coreLibraryDesugaring(libs.desugar.jdk.libs)
}

fun findNdkPrebuiltBinDir(sdkDir: File): File {
    val osName = System.getProperty("os.name").lowercase()
    val hostTag = when {
        osName.contains("win") -> "windows-x86_64"
        osName.contains("mac") -> "darwin-x86_64"
        else -> "linux-x86_64"
    }
    val ndkRoot = System.getenv("ANDROID_NDK_HOME")?.let(::File)?.takeIf { it.exists() }
        ?: File(sdkDir, "ndk").listFiles()?.filter { it.isDirectory }?.maxByOrNull { it.name }
        ?: error("Android NDK is required to compile libhotaruguard.so")
    val prebuiltDir = File(ndkRoot, "toolchains/llvm/prebuilt/$hostTag/bin")
    if (!prebuiltDir.exists()) {
        // Apple Silicon NDK falls back to darwin-x86_64; try first available toolchain dir if needed
        val fallback = File(ndkRoot, "toolchains/llvm/prebuilt").listFiles()?.firstOrNull()?.let { File(it, "bin") }
        if (fallback != null && fallback.exists()) return fallback
        error("NDK LLVM toolchain bin directory not found under $ndkRoot")
    }
    return prebuiltDir
}

val buildHotaruGuardNative = tasks.register("buildHotaruGuardNative") {
    val cppSrc = file("src/main/cpp/hotaru_guard.cpp")
    val nativeOutRoot = layout.buildDirectory.dir("hotaru_native")
    val feedToken = (project.findProperty("HOTARU_FEED_TOKEN") as? String)?.takeIf { it.isNotBlank() } ?: System.getenv("HOTARU_FEED_TOKEN")?.takeIf { it.isNotBlank() } ?: "SHOWCASE_PLACEHOLDER_TOKEN"")
    val telemetrySecret = (project.findProperty("HOTARU_TELEMETRY_SECRET") as? String)?.takeIf { it.isNotBlank() } ?: System.getenv("HOTARU_TELEMETRY_SECRET")?.takeIf { it.isNotBlank() } ?: "SHOWCASE_PLACEHOLDER_HMAC_SECRET"")
    val tokenSha = MessageDigest.getInstance("SHA-256")
        .digest(feedToken.toByteArray(Charsets.UTF_8))
        .joinToString("") { b -> "%02x".format(b) }
    val telemetrySecretSha = MessageDigest.getInstance("SHA-256")
        .digest(telemetrySecret.toByteArray(Charsets.UTF_8))
        .joinToString("") { b -> "%02x".format(b) }

    inputs.file(cppSrc)
    inputs.property("feedTokenSha", tokenSha)
    inputs.property("telemetrySecretSha", telemetrySecretSha)
    inputs.property("vaultUrlsVersion", "OneForAllHub/HotaruNet/v2-telemetry")
    outputs.dir(nativeOutRoot.map { it.dir("jniLibs") })

    doLast {
        val outBase = nativeOutRoot.get().asFile
        val incDir = File(outBase, "include").apply { mkdirs() }
        val jniRoot = File(outBase, "jniLibs").apply { mkdirs() }

        fun unmaskBytes(masked: IntArray, mask: IntArray): ByteArray = ByteArray(masked.size) { i ->
            (masked[i] xor mask[i % mask.size]).toByte()
        }

        val keyMask = intArrayOf(0x5E, 0x1A, 0x8D, 0xC4, 0x37, 0x92, 0xF0, 0x6B, 0xA1, 0x48, 0x2C, 0x75, 0xE9, 0x13, 0x86, 0x5F)
        val maskedKey = intArrayOf(12, 95, 221, 136, 118, 209, 181, 52, 246, 1, 120, 61, 182, 32, 180, 0, 28, 67, 217, 129, 104, 211, 181, 56, 254, 15, 111, 56, 182, 88, 195, 6)
        val urlMask = intArrayOf(0x4B, 0x93, 0x1E, 0x82, 0xC5, 0x67, 0x3A, 0xF0)
        val maskedUrl = intArrayOf(35, 231, 106, 242, 182, 93, 21, 223, 46, 235, 127, 239, 181, 11, 95, 222, 34, 253, 104, 227, 169, 14, 94, 223, 45, 246, 123, 230, 235, 3, 91, 132)
        val repoMask = intArrayOf(0x62, 0xD4, 0x19, 0x8F, 0x51, 0xAE, 0x3C, 0x77)
        val maskedAppRepo = intArrayOf(10, 160, 109, 255, 34, 148, 19, 88, 5, 189, 109, 231, 36, 204, 18, 20, 13, 185, 54, 192, 63, 203, 122, 24, 16, 149, 117, 227, 25, 219, 94, 88, 42, 187, 109, 238, 35, 219, 114, 18, 22)
        val maskedAppApi = intArrayOf(10, 160, 109, 255, 34, 148, 19, 88, 3, 164, 112, 161, 54, 199, 72, 31, 23, 182, 55, 236, 62, 195, 19, 5, 7, 164, 118, 252, 126, 225, 82, 18, 36, 187, 107, 206, 61, 194, 116, 2, 0, 251, 81, 224, 37, 207, 78, 2, 44, 177, 109, 160, 35, 203, 80, 18, 3, 167, 124, 252)
        val maskedAppIssues = intArrayOf(10, 160, 109, 255, 34, 148, 19, 88, 5, 189, 109, 231, 36, 204, 18, 20, 13, 185, 54, 192, 63, 203, 122, 24, 16, 149, 117, 227, 25, 219, 94, 88, 42, 187, 109, 238, 35, 219, 114, 18, 22, 251, 112, 252, 34, 219, 89, 4)
        val maskedAppPrivacy = intArrayOf(10, 160, 109, 255, 34, 148, 19, 88, 16, 181, 110, 161, 54, 199, 72, 31, 23, 182, 108, 252, 52, 220, 95, 24, 12, 160, 124, 225, 37, 128, 95, 24, 15, 251, 86, 225, 52, 232, 83, 5, 35, 184, 117, 199, 36, 204, 19, 63, 13, 160, 120, 253, 36, 224, 89, 3, 77, 185, 120, 230, 63, 129, 108, 37, 43, 130, 88, 204, 8, 128, 81, 19)
        val mirrorMask = intArrayOf(0x6A, 0x3D, 0x91, 0xE5, 0x24, 0xB8, 0x73, 0xCF)
        val maskedMirrorRaw = intArrayOf(2, 73, 229, 149, 87, 130, 92, 224, 15, 69, 240, 136, 84, 212, 22, 225, 3, 83, 231, 132, 72, 209, 23, 224, 7, 84, 227, 151, 75, 202, 94, 189, 11, 74, 191, 129, 69, 204)
        val maskedMirrorCdn = intArrayOf(2, 73, 229, 149, 87, 130, 92, 224, 15, 69, 240, 136, 84, 212, 22, 225, 3, 83, 231, 132, 72, 209, 23, 224, 7, 84, 227, 151, 75, 202, 94, 172, 14, 83, 191, 129, 69, 204)
        val maskedMirrorCdnMain = intArrayOf(2, 73, 229, 149, 87, 130, 92, 224, 15, 69, 240, 136, 84, 212, 22, 225, 3, 83, 231, 132, 72, 209, 23, 224, 7, 84, 227, 151, 75, 202, 94, 174, 6, 73, 191, 129, 69, 204)
        val telemetryUrlMask = intArrayOf(0x39, 0xA7, 0x5C, 0xE2, 0x71, 0x1B, 0x8E, 0x45)
        val maskedTelemetryUrl = intArrayOf(81, 211, 40, 146, 2, 33, 161, 106, 92, 223, 61, 143, 1, 119, 235, 107, 80, 201, 42, 131, 29, 114, 234, 106, 77, 194, 48, 135, 28, 126, 250, 55, 64)

        val entries = listOf(
            unmaskBytes(maskedKey, keyMask),                 // 0: AES_KEY
            unmaskBytes(maskedUrl, urlMask),                 // 1: SUB_URL
            feedToken.toByteArray(Charsets.UTF_8),           // 2: FEED_TOKEN
            unmaskBytes(maskedMirrorRaw, mirrorMask),        // 3: MIRROR_RAW
            unmaskBytes(maskedMirrorCdn, mirrorMask),        // 4: MIRROR_CDN
            unmaskBytes(maskedMirrorCdnMain, mirrorMask),    // 5: MIRROR_CDN_MAIN
            unmaskBytes(maskedAppRepo, repoMask),            // 6: APP_REPO
            unmaskBytes(maskedAppApi, repoMask),             // 7: APP_API
            unmaskBytes(maskedAppIssues, repoMask),          // 8: APP_ISSUES
            unmaskBytes(maskedAppPrivacy, repoMask),         // 9: APP_PRIVACY
            unmaskBytes(maskedTelemetryUrl, telemetryUrlMask), // 10: TELEMETRY_URL
            telemetrySecret.toByteArray(Charsets.UTF_8),     // 11: TELEMETRY_SECRET
        )

        val rng = SecureRandom()
        val hdr = StringBuilder()
        hdr.append("#pragma once\n#include <stdint.h>\n\n")
        hdr.append("struct HotaruVaultEntry {\n")
        hdr.append("    uint16_t len;\n    uint8_t rot;\n    uint8_t mult;\n    uint64_t seed;\n    const uint8_t* data;\n};\n\n")

        val metaList = mutableListOf<String>()
        entries.forEachIndexed { id, plain ->
            val seed = rng.nextLong().toULong()
            val rot = rng.nextInt(256) and 0xFF
            val mult = ((rng.nextInt(127) * 2) + 1) and 0xFF
            val enc = IntArray(plain.size) { i ->
                val r = (rot + i) % 7 + 1
                val p = plain[i].toInt() and 0xFF
                val rotByte = ((p shl r) or (p ushr (8 - r))) and 0xFF
                val s = seed + i.toULong() * 0x9E3779B97F4A7C15UL
                val k = (((s xor (s shr 27)) * mult.toULong()) + (i.toULong() * 17UL)).toInt() and 0xFF
                rotByte xor k
            }
            hdr.append("static const uint8_t kVaultData$id[${plain.size}] = {")
            hdr.append(enc.joinToString(",") { "0x%02x".format(it) })
            hdr.append("};\n")
            metaList.add("{${plain.size}, ${rot}U, ${mult}U, ${seed}ULL, kVaultData$id}")
        }
        hdr.append("\nstatic const int HOTARU_VAULT_COUNT = ${entries.size};\n")
        hdr.append("static const HotaruVaultEntry kHotaruVault[${entries.size}] = {\n    ")
        hdr.append(metaList.joinToString(",\n    "))
        hdr.append("\n};\n")
        File(incDir, "hotaru_vault_gen.h").writeText(hdr.toString(), Charsets.UTF_8)

        val ndkBin = findNdkPrebuiltBinDir(android.sdkDirectory)
        val isWin = System.getProperty("os.name").lowercase().contains("win")
        val cmdExt = if (isWin) ".cmd" else ""
        val exeExt = if (isWin) ".exe" else ""
        val stripBin = File(ndkBin, "llvm-strip$exeExt").absolutePath

        val abiMap = linkedMapOf(
            "arm64-v8a" to "aarch64-linux-android24-clang++$cmdExt",
            "armeabi-v7a" to "armv7a-linux-androideabi24-clang++$cmdExt",
            "x86_64" to "x86_64-linux-android24-clang++$cmdExt",
            "x86" to "i686-linux-android24-clang++$cmdExt",
        )
        val filterAbis = (project.findProperty("ABI_FILTERS") as? String)?.split(';')?.filter { it.isNotBlank() }?.toSet()
        for ((abi, compilerName) in abiMap) {
            if (filterAbis != null && abi !in filterAbis) continue
            val compiler = File(ndkBin, compilerName)
            require(compiler.exists()) { "Missing NDK compiler: ${compiler.absolutePath}" }
            val abiDir = File(jniRoot, abi).apply { mkdirs() }
            val outSo = File(abiDir, "libhotaruguard.so")
            val compileRes = ProcessBuilder(
                compiler.absolutePath,
                "-shared", "-fPIC", "-std=c++17", "-O3",
                "-nostdlib++", "-fvisibility=hidden", "-fno-rtti", "-fno-exceptions",
                "-ffunction-sections", "-fdata-sections", "-fstack-protector-strong",
                "-Wl,--gc-sections", "-Wl,-z,relro,-z,now",
                "-I${incDir.absolutePath}",
                cppSrc.absolutePath,
                "-o", outSo.absolutePath,
            ).redirectErrorStream(true).start()
            val compileLog = compileRes.inputStream.bufferedReader().readText()
            check(compileRes.waitFor() == 0) { "hotaru_guard compile failed for $abi:\n$compileLog" }

            val stripRes = ProcessBuilder(stripBin, "--strip-all", outSo.absolutePath)
                .redirectErrorStream(true).start()
            val stripLog = stripRes.inputStream.bufferedReader().readText()
            check(stripRes.waitFor() == 0) { "llvm-strip failed for $abi:\n$stripLog" }
        }
    }
}

tasks.named("preBuild").configure {
    dependsOn(buildHotaruGuardNative)
}

