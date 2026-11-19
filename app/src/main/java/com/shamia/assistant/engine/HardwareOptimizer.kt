package com.shamia.assistant.engine

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.shamia.assistant.model.DeviceHardwareSpec
import java.io.File

data class InferenceLoadAttempt(
    val backend: LlmInference.Backend?,
    val maxTokens: Int,
    val label: String
)

data class HardwareExecutionPlan(
    val isArm64: Boolean,
    val isEmulator: Boolean,
    val socLabel: String,
    val thermalLabel: String,
    val isThermallyHot: Boolean,
    val availRamMb: Int,
    val totalRamGb: Int,
    val isLowMemory: Boolean,
    val attempts: List<InferenceLoadAttempt>
)

/**
 * Profiles real-time Android hardware (RAM, ABI, SoC/GPU vendor, CPU cores, and Thermal status)
 * and builds an optimal MediaPipe LiteRT execution plan so on-device models load fast,
 * stay cool, and never trigger Out-Of-Memory (LMKD) crashes.
 */
object HardwareOptimizer {

    fun isArm64Device(): Boolean {
        return Build.SUPPORTED_ABIS.any { it.equals("arm64-v8a", ignoreCase = true) }
    }

    fun isLikelyEmulator(): Boolean {
        val fp = Build.FINGERPRINT.lowercase()
        val model = Build.MODEL.lowercase()
        val hw = Build.HARDWARE.lowercase()
        val prod = Build.PRODUCT.lowercase()
        return fp.contains("generic") ||
            fp.contains("emulator") ||
            model.contains("emulator") ||
            model.contains("android sdk built for") ||
            hw.contains("goldfish") ||
            hw.contains("ranchu") ||
            prod.contains("sdk_gphone") ||
            prod.contains("emulator")
    }

    fun detectSocLabel(): String {
        val rawSoc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            "${Build.SOC_MANUFACTURER} ${Build.SOC_MODEL}".trim()
        } else {
            "${Build.HARDWARE} ${Build.BOARD}".trim()
        }
        val lower = rawSoc.lowercase()
        return when {
            isLikelyEmulator() -> "Emulator CPU"
            lower.contains("qcom") || lower.contains("qualcomm") || lower.contains("sm8") || lower.contains("sm7") || lower.contains("sm6") || lower.contains("snapdragon") ->
                "Snapdragon / Adreno"
            lower.contains("mediatek") || lower.contains("mt6") || lower.contains("dimensity") || lower.contains("helio") ->
                "MediaTek / Mali"
            lower.contains("tensor") || lower.contains("gs101") || lower.contains("gs201") || lower.contains("zuma") ->
                "Google Tensor"
            lower.contains("exynos") || lower.contains("s5e") || lower.contains("samsung") ->
                "Samsung Exynos"
            rawSoc.isNotBlank() -> rawSoc.take(22)
            else -> "ARM64 Mobile SoC"
        }
    }

    fun readThermalStatus(context: Context): Pair<String, Boolean> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Cool" to false
        return runCatching {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            when (pm?.currentThermalStatus ?: PowerManager.THERMAL_STATUS_NONE) {
                PowerManager.THERMAL_STATUS_NONE -> "Cool" to false
                PowerManager.THERMAL_STATUS_LIGHT -> "Optimal" to false
                PowerManager.THERMAL_STATUS_MODERATE -> "Warm (Throttled)" to true
                PowerManager.THERMAL_STATUS_SEVERE,
                PowerManager.THERMAL_STATUS_CRITICAL,
                PowerManager.THERMAL_STATUS_EMERGENCY,
                PowerManager.THERMAL_STATUS_SHUTDOWN -> "Hot (Safe Mode)" to true
                else -> "Optimal" to false
            }
        }.getOrDefault("Optimal" to false)
    }

    fun detectLiveHardware(context: Context): DeviceHardwareSpec {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val ramGb = maxOf(2, Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt())
        val availRamMb = (info.availMem / (1024L * 1024L)).toInt().coerceAtLeast(256)
        val isLowMem = info.lowMemory || availRamMb < 420
        val cores = Runtime.getRuntime().availableProcessors().coerceAtLeast(4)
        val soc = detectSocLabel()
        val (thermal, _) = readThermalStatus(context)
        val label = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL}"
        val tier = if (isLowMem) {
            "Low-RAM Safe Mode ($availRamMb MB Free · $thermal)"
        } else {
            "$soc · $thermal · ${if (isArm64Device()) "ARM64" else "x86"}"
        }
        return DeviceHardwareSpec(
            ramGb = ramGb,
            availRamMb = availRamMb,
            isLowMemory = isLowMem,
            cpuCores = cores,
            deviceLabel = label,
            recommendedTier = tier
        )
    }

    fun computeInferencePlan(
        context: Context,
        weightsFile: File,
        gpuKnownUnsupported: Boolean
    ): HardwareExecutionPlan {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val totalRamGb = maxOf(2, Math.round(info.totalMem / (1024.0 * 1024.0 * 1024.0)).toInt())
        val availRamMb = (info.availMem / (1024L * 1024L)).toInt().coerceAtLeast(256)
        val isLowMemory = info.lowMemory || availRamMb < 450
        val isArm64 = isArm64Device()
        val isEmulator = isLikelyEmulator()
        val soc = detectSocLabel()
        val (thermalLabel, isHot) = readThermalStatus(context)

        val fileMb = weightsFile.length() / (1024L * 1024L)
        val lowerName = weightsFile.name.lowercase()
        val isQ8OrDynamicCpu = lowerName.contains("q8") ||
            lowerName.contains("int8") ||
            lowerName.contains("multi-prefill") ||
            lowerName.contains("cpu")
        val isAdrenoOrTensor = soc.contains("Snapdragon", true) || soc.contains("Tensor", true)

        // Choose safe maxTokens dynamically based on live available RAM, model size, and thermal headroom
        val primaryMaxTokens = when {
            isLowMemory || isHot || availRamMb < 950 -> 512
            fileMb >= 1350L && availRamMb < 2000 -> 768
            else -> 1024
        }
        val compactMaxTokens = 512

        val preferGpuFirst = !gpuKnownUnsupported &&
            !isEmulator &&
            isArm64 &&
            !isQ8OrDynamicCpu &&
            !isHot &&
            !isLowMemory &&
            availRamMb >= 1200 &&
            (isAdrenoOrTensor || lowerName.contains("gpu") || lowerName.contains("q4") || lowerName.contains("int4"))

        val attempts = buildList {
            if (preferGpuFirst) {
                add(InferenceLoadAttempt(LlmInference.Backend.GPU, primaryMaxTokens, "GPU"))
                add(InferenceLoadAttempt(LlmInference.Backend.CPU, primaryMaxTokens, "CPU"))
                if (primaryMaxTokens > compactMaxTokens) {
                    add(InferenceLoadAttempt(LlmInference.Backend.CPU, compactMaxTokens, "CPU"))
                }
                add(InferenceLoadAttempt(null, compactMaxTokens, "Auto"))
            } else {
                add(InferenceLoadAttempt(LlmInference.Backend.CPU, primaryMaxTokens, "CPU"))
                if (primaryMaxTokens > compactMaxTokens) {
                    add(InferenceLoadAttempt(LlmInference.Backend.CPU, compactMaxTokens, "CPU"))
                }
                add(InferenceLoadAttempt(null, compactMaxTokens, "Auto"))
                if (!gpuKnownUnsupported && !isEmulator && isArm64 && !isLowMemory && !isHot) {
                    add(InferenceLoadAttempt(LlmInference.Backend.GPU, compactMaxTokens, "GPU"))
                }
            }
        }

        return HardwareExecutionPlan(
            isArm64 = isArm64,
            isEmulator = isEmulator,
            socLabel = soc,
            thermalLabel = thermalLabel,
            isThermallyHot = isHot,
            availRamMb = availRamMb,
            totalRamGb = totalRamGb,
            isLowMemory = isLowMemory,
            attempts = attempts
        )
    }

    /**
     * Reclaims unused JVM buffers right before MediaPipe mmaps large native C++ weights when RAM is tight.
     */
    fun reclaimHeapIfTight(availRamMb: Int) {
        if (availRamMb < 1600) {
            runCatching { System.gc() }
        }
    }
}
