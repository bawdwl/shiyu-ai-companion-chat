package com.shiyu.chat.data

import android.app.ActivityManager
import android.content.Context
import android.os.Build

data class DeviceModelCompatibility(
    val deviceName: String,
    val socModel: String,
    val socLabel: String,
    val htpArchitecture: String?,
    val arm64Supported: Boolean,
    val totalRamGb: Double,
    val mnnTextModelSupported: Boolean,
    val dreamNpuSupported: Boolean,
    val dreamPackageSuffix: String?,
    val dreamDownloadUrl: String?,
    val dreamPackageLabel: String,
    val note: String,
)

object DeviceCompatibility {
    private const val HUGGING_FACE_ROOT =
        "https://huggingface.co/xororz/sd-qnn/resolve/main"

    fun inspect(context: Context): DeviceModelCompatibility {
        val soc = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MODEL }.getOrNull()
        } else {
            null
        }
            ?.takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) }
            ?: runCatching { Build.HARDWARE }.getOrDefault("unknown")
        val manufacturer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { Build.SOC_MANUFACTURER }.getOrNull().orEmpty()
        } else {
            ""
        }
        val hardware = runCatching { Build.HARDWARE }.getOrDefault("").orEmpty()
        val displaySoc = soc.uppercase()
        val qualcomm = listOf(manufacturer, hardware, displaySoc).any {
            it.contains("qualcomm", ignoreCase = true) ||
                it.contains("qcom", ignoreCase = true) ||
                it.startsWith("SM", ignoreCase = true) ||
                it.startsWith("QCS", ignoreCase = true) ||
                it.startsWith("QCM", ignoreCase = true)
        }
        val arm64 = Build.SUPPORTED_64_BIT_ABIS.any {
            it.equals("arm64-v8a", ignoreCase = true)
        }
        val totalRamGb = totalRamGb(context)
        val htp = htpForQualcommSoc(displaySoc)
        val suffix = meinaPackageSuffixForSoc(displaySoc, qualcomm, htp)
        val socLabel = when (displaySoc) {
            "SM8350", "SM8350P" -> "骁龙 888"
            "SM8450", "SM8475" -> "骁龙 8 Gen 1"
            "SM8550", "SM8550P", "QCS8550", "QCM8550" -> "骁龙 8 Gen 2"
            "SM8650", "SM8650P" -> "骁龙 8 Gen 3"
            "SM8750", "SM8750P" -> "骁龙 8 Elite"
            "SM8850", "SM8850P" -> "新一代骁龙 8 旗舰"
            else -> if (qualcomm) "骁龙兼容平台" else "非骁龙或未知平台"
        }
        val dreamSupported = qualcomm && suffix != null && htp != null
        val packageLabel = when (suffix) {
            "8gen1" -> "MeinaMix V12 · 8 Gen 1 包"
            "8gen2" -> "MeinaMix V12 · 8 Gen 2 优化包（新平台复用）"
            "min" -> "MeinaMix V12 · Hexagon V68+ 兼容包"
            else -> "本地 Dream NPU 不可用"
        }
        val note = when {
            !arm64 -> "当前设备不是 ARM64，无法使用本应用内置的端侧模型运行库。"
            dreamSupported ->
                "已识别 ${htp ?: "Hexagon V68+"}。MeinaMix V12 没有单独的 8 Gen 3/Elite 包；" +
                    "当前应用会自动选择 ${suffix}，并在安装前校验大小和 SHA-256。"
            qualcomm ->
                "检测到高通平台，但无法确认 Hexagon 架构是否符合 V68+。为避免安装后崩溃，本地 Dream 保持禁用。"
            else ->
                "当前端侧 Dream 后端只使用 Qualcomm Hexagon NPU；此设备请使用千问生图 API。"
        }
        return DeviceModelCompatibility(
            deviceName = listOf(Build.BRAND, Build.MODEL)
                .filter(String::isNotBlank)
                .joinToString(" "),
            socModel = displaySoc,
            socLabel = socLabel,
            htpArchitecture = htp,
            arm64Supported = arm64,
            totalRamGb = totalRamGb,
            mnnTextModelSupported = arm64 && totalRamGb >= 6.0,
            dreamNpuSupported = dreamSupported,
            dreamPackageSuffix = suffix.takeIf { dreamSupported },
            dreamDownloadUrl = suffix?.takeIf { dreamSupported }?.let {
                "$HUGGING_FACE_ROOT/MeinaMixV12_qnn2.28_$it.zip"
            },
            dreamPackageLabel = packageLabel,
            note = note,
        )
    }

    private fun totalRamGb(context: Context): Double {
        val manager = context.getSystemService(ActivityManager::class.java)
        val info = ActivityManager.MemoryInfo()
        manager.getMemoryInfo(info)
        return info.totalMem.toDouble() / (1024.0 * 1024.0 * 1024.0)
    }
}

internal fun htpForQualcommSoc(soc: String): String? = when (soc.uppercase()) {
    "SM8350", "SM8350P" -> "V68"
    "SM8450", "SM8475" -> "V69"
    "SM8550", "SM8550P", "QCS8550", "QCM8550" -> "V73"
    "SM8650", "SM8650P" -> "V75"
    "SM8750", "SM8750P" -> "V79"
    "SM8850", "SM8850P" -> "V81"
    else -> null
}

internal fun meinaPackageSuffixForSoc(
    soc: String,
    qualcomm: Boolean,
    htp: String?,
): String? = when (soc.uppercase()) {
    "SM8450", "SM8475" -> "8gen1"
    "SM8550", "SM8550P", "QCS8550", "QCM8550",
    "SM8650", "SM8650P", "SM8750", "SM8750P",
    "SM8850", "SM8850P" -> "8gen2"
    else -> "min".takeIf { qualcomm && htp != null }
}

data class MnnDownloadOffer(
    val id: String,
    val displayName: String,
    val repoId: String,
)

object MnnDownloadCatalog {
    val songZhiyi = MnnDownloadOffer(
        id = "song-zhiyi",
        displayName = "宋知意 · MNN Q8",
        repoId = "Alixs666/shiyu-qwen3-1.7b-song-zhiyi-mnn-q8",
    )
    val peiJiming = MnnDownloadOffer(
        id = "pei-jiming",
        displayName = "裴既明 · MNN Q8",
        repoId = "Alixs666/shiyu-qwen3-1.7b-pei-jiming-mnn-q8",
    )
    val all = listOf(songZhiyi, peiJiming)

    fun byId(id: String): MnnDownloadOffer? = all.firstOrNull { it.id == id }
}
