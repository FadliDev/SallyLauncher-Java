package com.movtery.zalithlauncher.feature.crash

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.feature.performance.PerformancePreset
import com.movtery.zalithlauncher.feature.performance.PerformancePresets
import com.movtery.zalithlauncher.renderer.Renderers
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.setting.Settings
import kotlin.math.max
import kotlin.math.min

/** Applies the safe, reversible settings changes suggested by [CrashAnalyzer]. */
object CrashFixer {
    private const val GL4ES_ID = "8b52d82d-8f6d-4d3a-a767-dc93f8b72fc7"

    /** @return one human-readable line per fix that was attempted */
    @JvmStatic
    fun apply(context: Context, fixes: Set<CrashFix>): List<String> {
        // The crash screen may be the first thing this process shows: make sure the settings map is
        // loaded before saving, otherwise a save would overwrite the file with an incomplete map.
        Settings.refreshSettings()
        val messages = mutableListOf<String>()
        // The saver preset already lowers resolution and RAM, so skip the narrower fixes then.
        val saver = CrashFix.APPLY_SAVER_PRESET in fixes
        if (saver) {
            PerformancePresets.apply(context, PerformancePreset.SAVER)
            messages += context.getString(R.string.crash_fix_saver)
        }
        for (fix in fixes) {
            when (fix) {
                CrashFix.RAISE_RAM -> messages += raiseRam(context)
                CrashFix.SWITCH_RENDERER -> messages += switchRenderer(context)
                CrashFix.LOWER_RESOLUTION -> if (!saver) messages += lowerResolution(context)
                CrashFix.APPLY_SAVER_PRESET -> Unit
            }
        }
        return messages
    }

    private fun raiseRam(context: Context): String {
        val current = AllSettings.ramAllocation.value.getValue()
        val ceiling = PerformancePresets.maxRam(context)
        val target = min(ceiling, (max(current + 512, (current * 1.25).toInt()) / 64) * 64)
        if (target <= current) return context.getString(R.string.crash_fix_ram_max)
        AllSettings.ramAllocation.value.put(target).save()
        return context.getString(R.string.crash_fix_ram_raised, target)
    }

    private fun switchRenderer(context: Context): String {
        Renderers.init()
        val list = Renderers.getCompatibleRenderers(context).first
        val current = AllSettings.renderer.getValue()
        // Prefer the most compatible renderer (GL4ES); if it is already in use, try the next one.
        val target = if (current != GL4ES_ID && list.rendererIdentifier.contains(GL4ES_ID)) GL4ES_ID
        else list.rendererIdentifier.firstOrNull { it != current && it != GL4ES_ID }
        val index = target?.let { list.rendererIdentifier.indexOf(it) } ?: -1
        if (index < 0) return context.getString(R.string.crash_fix_renderer_none)
        AllSettings.renderer.put(target!!).save()
        return context.getString(R.string.crash_fix_renderer, list.rendererNames[index])
    }

    private fun lowerResolution(context: Context): String {
        val current = AllSettings.resolutionRatio.getValue()
        if (current <= 40) return context.getString(R.string.crash_fix_nothing)
        val target = max(40, current - 20)
        AllSettings.resolutionRatio.put(target).save()
        return context.getString(R.string.crash_fix_resolution, target)
    }
}
