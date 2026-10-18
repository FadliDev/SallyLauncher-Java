package com.movtery.zalithlauncher.feature.performance

import android.content.Context
import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.Settings
import net.kdt.pojavlaunch.Architecture
import net.kdt.pojavlaunch.Tools
import net.kdt.pojavlaunch.prefs.LauncherPreferences
import kotlin.math.max
import kotlin.math.min

/**
 * One-tap performance presets. A preset only touches four existing settings:
 * render resolution, game RAM, sustained performance mode and big-core affinity.
 * The renderer and everything else stay as the player configured them.
 */
enum class PerformancePreset(@StringRes val nameRes: Int, @StringRes val descRes: Int) {
    SAVER(R.string.preset_saver, R.string.preset_desc_saver),
    BALANCED(R.string.preset_balanced, R.string.preset_desc_balanced),
    PERFORMANCE(R.string.preset_performance, R.string.preset_desc_performance)
}

data class PresetValues(
    val resolutionRatio: Int,
    val ramMb: Int,
    val sustainedPerformance: Boolean,
    val bigCoreAffinity: Boolean
)

object PerformancePresets {
    /** Same ceiling the RAM slider in game settings uses. */
    @JvmStatic
    fun maxRam(context: Context): Int {
        val deviceRam = Tools.getTotalDeviceMemory(context)
        return if (Architecture.is32BitsDevice() || deviceRam < 2048) {
            min(1024, deviceRam)
        } else {
            deviceRam - (if (deviceRam < 3064) 800 else 1024)
        }
    }

    private fun round64(value: Int) = (value / 64) * 64

    fun valuesFor(context: Context, preset: PerformancePreset): PresetValues {
        val deviceRam = Tools.getTotalDeviceMemory(context)
        val best = LauncherPreferences.findBestRAMAllocation(context)
        val ceiling = max(256, maxRam(context))
        return when (preset) {
            PerformancePreset.SAVER -> PresetValues(
                resolutionRatio = 60,
                ramMb = min(ceiling, max(384, round64((best * 0.75).toInt()))),
                sustainedPerformance = true,
                bigCoreAffinity = false
            )
            PerformancePreset.BALANCED -> PresetValues(
                resolutionRatio = 80,
                ramMb = min(ceiling, best),
                sustainedPerformance = false,
                bigCoreAffinity = false
            )
            PerformancePreset.PERFORMANCE -> PresetValues(
                resolutionRatio = 100,
                ramMb = min(ceiling, min(3072, max(best, round64(deviceRam / 2)))),
                sustainedPerformance = false,
                bigCoreAffinity = true
            )
        }
    }

    /** Applies the preset and saves. The settings page is refreshed by the SettingsChangeEvent. */
    @JvmStatic
    fun apply(context: Context, preset: PerformancePreset) {
        Settings.refreshSettings()
        val v = valuesFor(context, preset)
        AllSettings.resolutionRatio.put(v.resolutionRatio)
            .put(AllSettings.ramAllocation.value, v.ramMb)
            .put(AllSettings.sustainedPerformance, v.sustainedPerformance)
            .put(AllSettings.bigCoreAffinity, v.bigCoreAffinity)
            .save()
    }

    /** The preset whose values exactly match the current settings, or null for custom settings. */
    @JvmStatic
    fun detect(context: Context): PerformancePreset? {
        val currentRatio = AllSettings.resolutionRatio.getValue()
        val currentRam = AllSettings.ramAllocation.value.getValue()
        val currentSustained = AllSettings.sustainedPerformance.getValue()
        val currentBigCore = AllSettings.bigCoreAffinity.getValue()
        return PerformancePreset.values().firstOrNull { preset ->
            val v = valuesFor(context, preset)
            v.resolutionRatio == currentRatio && v.ramMb == currentRam &&
                    v.sustainedPerformance == currentSustained && v.bigCoreAffinity == currentBigCore
        }
    }

    /** Suggested preset from the amount of device RAM (a simple, predictable rule). */
    @JvmStatic
    fun recommended(context: Context): PerformancePreset {
        val deviceRam = Tools.getTotalDeviceMemory(context)
        return when {
            deviceRam < 3072 -> PerformancePreset.SAVER
            deviceRam < 6144 -> PerformancePreset.BALANCED
            else -> PerformancePreset.PERFORMANCE
        }
    }
}
