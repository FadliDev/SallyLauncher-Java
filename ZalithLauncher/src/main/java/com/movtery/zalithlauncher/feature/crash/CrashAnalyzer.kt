package com.movtery.zalithlauncher.feature.crash

import androidx.annotation.StringRes
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.utils.path.PathManager
import java.io.File
import java.io.RandomAccessFile

/** Automatic fixes the crash screen can apply with one tap. */
enum class CrashFix { RAISE_RAM, SWITCH_RENDERER, LOWER_RESOLUTION, APPLY_SAVER_PRESET }

enum class CrashCause(
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    @StringRes val tipRes: Int,
    val fixes: List<CrashFix> = emptyList()
) {
    DISK_FULL(R.string.crash_cause_disk_title, R.string.crash_cause_disk_desc, R.string.crash_cause_disk_tip),
    JAVA_VERSION(R.string.crash_cause_java_title, R.string.crash_cause_java_desc, R.string.crash_cause_java_tip),
    OUT_OF_MEMORY(R.string.crash_cause_oom_title, R.string.crash_cause_oom_desc, R.string.crash_cause_oom_tip, listOf(CrashFix.RAISE_RAM)),
    KILLED_BY_SYSTEM(R.string.crash_cause_killed_title, R.string.crash_cause_killed_desc, R.string.crash_cause_killed_tip, listOf(CrashFix.APPLY_SAVER_PRESET)),
    MOD_MISSING_DEPENDENCY(R.string.crash_cause_mod_dep_title, R.string.crash_cause_mod_dep_desc, R.string.crash_cause_mod_dep_tip),
    MOD_CONFLICT(R.string.crash_cause_mod_conflict_title, R.string.crash_cause_mod_conflict_desc, R.string.crash_cause_mod_conflict_tip),
    MOD_FAILED(R.string.crash_cause_mod_failed_title, R.string.crash_cause_mod_failed_desc, R.string.crash_cause_mod_failed_tip),
    GRAPHICS(R.string.crash_cause_graphics_title, R.string.crash_cause_graphics_desc, R.string.crash_cause_graphics_tip, listOf(CrashFix.SWITCH_RENDERER, CrashFix.LOWER_RESOLUTION)),
    NATIVE_CRASH(R.string.crash_cause_native_title, R.string.crash_cause_native_desc, R.string.crash_cause_native_tip, listOf(CrashFix.SWITCH_RENDERER, CrashFix.LOWER_RESOLUTION)),
    LIBRARY_LOAD(R.string.crash_cause_library_title, R.string.crash_cause_library_desc, R.string.crash_cause_library_tip),
    MISSING_FILES(R.string.crash_cause_files_title, R.string.crash_cause_files_desc, R.string.crash_cause_files_tip)
}

data class CrashDiagnosis(
    val cause: CrashCause,
    /** The most relevant log line (or a derived value such as the required Java version). */
    val detail: String?
)

data class CrashReport(
    val diagnoses: List<CrashDiagnosis>,
    /** Best guess for the root error line, e.g. the last "Caused by:" line. */
    val rootError: String?
) {
    val fixes: Set<CrashFix> get() = diagnoses.flatMap { it.cause.fixes }.toSet()
}

/**
 * Reads the end of latestlog.txt and matches it against well-known failure signatures.
 * Pure text matching: it never modifies anything and works offline.
 */
object CrashAnalyzer {
    private const val TAIL_BYTES = 600 * 1024L
    private const val MAX_DIAGNOSES = 3

    private class Rule(val cause: CrashCause, val regex: Regex)

    private val RULES: List<Rule> = listOf(
        Rule(CrashCause.DISK_FULL, Regex("No space left on device", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.JAVA_VERSION, Regex("UnsupportedClassVersionError|compiled by a more recent version of the Java Runtime|class file version \\d+", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.OUT_OF_MEMORY, Regex("java\\.lang\\.OutOfMemoryError|Java heap space|GC overhead limit exceeded|Could not reserve enough space|insufficient memory for the Java Runtime|Cannot allocate memory", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.MOD_MISSING_DEPENDENCY, Regex("Missing or unsupported mandatory dependencies|Mod ID: '[^']+', Requested by|Mod '[^']+' \\([^)]*\\)[^\\n]*requires|Unmet dependency|ModResolutionException|Could not find required mod", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.MOD_CONFLICT, Regex("Duplicate mod|Found duplicate mods|Incompatible mod set|is incompatible with|Mixin apply failed|InvalidInjectionException|MixinTransformerError|Critical injection failure|MixinApplyError", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.MOD_FAILED, Regex("Failed to load mod|Exception caught from [^\\n]+|Caught exception from [^\\n]+|ModLoadingException|Failed to create mod instance|Error loading class", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.LIBRARY_LOAD, Regex("UnsatisfiedLinkError|Failed to locate library|Failed to load library|dlopen failed|cannot locate symbol", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.GRAPHICS, Regex("GLFW error|Unable to initialize GLFW|Failed to create (the )?(GLFW )?(window|context)|EGL_BAD_|eglCreateContext|No OpenGL context|OpenGL error|GL_INVALID_|Failed to compile shader|Shader compile|Could not initialize class [\\w.$]*(GL|Window)|VK_ERROR_", RegexOption.IGNORE_CASE)),
        Rule(CrashCause.NATIVE_CRASH, Regex("A fatal error has been detected by the Java Runtime Environment|SIGSEGV|SIGABRT|SIGBUS|Fatal signal \\d+|hs_err_pid")),
        Rule(CrashCause.MISSING_FILES, Regex("ClassNotFoundException|NoClassDefFoundError|NoSuchMethodError|NoSuchFieldError|Could not find or load main class|Invalid or corrupt jarfile|invalid (LOC|CEN) header|java\\.util\\.zip\\.ZipException", RegexOption.IGNORE_CASE))
    )

    private val ROOT_ERROR_LINE = Regex("^\\s*(Caused by:\\s*)?[\\w.$]+(Exception|Error)\\b.*$")

    @JvmStatic
    fun analyze(exitCode: Int, isSignal: Boolean): CrashReport {
        val text = readLogTail()
        return analyzeText(text, exitCode, isSignal)
    }

    @JvmStatic
    fun analyzeText(text: String, exitCode: Int, isSignal: Boolean): CrashReport {
        val found = LinkedHashMap<CrashCause, CrashDiagnosis>()

        // The OS (low-memory killer) ends the process without leaving anything in the log.
        if (exitCode == 137 || (isSignal && exitCode == 9)) {
            found[CrashCause.KILLED_BY_SYSTEM] = CrashDiagnosis(CrashCause.KILLED_BY_SYSTEM, "exit code $exitCode")
        }

        for (rule in RULES) {
            if (found.containsKey(rule.cause)) continue
            val match = rule.regex.find(text) ?: continue
            val detail = when (rule.cause) {
                CrashCause.JAVA_VERSION -> requiredJava(text) ?: lineAround(text, match.range.first)
                CrashCause.LIBRARY_LOAD -> Regex("lib[\\w+.-]+\\.so").find(lineAround(text, match.range.first))?.value
                    ?: lineAround(text, match.range.first)
                CrashCause.OUT_OF_MEMORY -> null
                else -> lineAround(text, match.range.first)
            }
            found[rule.cause] = CrashDiagnosis(rule.cause, detail)
        }

        if (isSignal && !found.containsKey(CrashCause.NATIVE_CRASH) && !found.containsKey(CrashCause.KILLED_BY_SYSTEM)) {
            found[CrashCause.NATIVE_CRASH] = CrashDiagnosis(CrashCause.NATIVE_CRASH, "signal $exitCode")
        }

        return CrashReport(found.values.take(MAX_DIAGNOSES), findRootError(text))
    }

    /** "class file version 65.0" -> Java 21 */
    private fun requiredJava(text: String): String? {
        val major = Regex("class file version (\\d+)\\.\\d+").findAll(text)
            .mapNotNull { it.groupValues[1].toIntOrNull() }
            .maxOrNull() ?: return null
        val java = major - 44
        return if (java >= 8) "Java $java" else null
    }

    private fun findRootError(text: String): String? {
        val lines = text.lineSequence().map { it.trim() }.toList()
        lines.lastOrNull { it.startsWith("Caused by:") }?.let { return clip(it) }
        lines.firstOrNull { it.contains("Exception in thread") }?.let { return clip(it) }
        lines.lastOrNull { ROOT_ERROR_LINE.matches(it) }?.let { return clip(it) }
        return null
    }

    private fun lineAround(text: String, index: Int): String {
        val start = text.lastIndexOf('\n', index).let { if (it < 0) 0 else it + 1 }
        val end = text.indexOf('\n', index).let { if (it < 0) text.length else it }
        return clip(text.substring(start, end).trim())
    }

    private fun clip(s: String): String = if (s.length > 160) s.take(157) + "..." else s

    private fun readLogTail(): String {
        val file = File(PathManager.DIR_GAME_HOME, "latestlog.txt")
        if (!file.exists() || file.length() == 0L) return ""
        return runCatching {
            RandomAccessFile(file, "r").use { raf ->
                val length = raf.length()
                val start = if (length > TAIL_BYTES) length - TAIL_BYTES else 0L
                raf.seek(start)
                val bytes = ByteArray((length - start).toInt())
                raf.readFully(bytes)
                String(bytes, Charsets.UTF_8)
            }
        }.getOrDefault("")
    }
}
