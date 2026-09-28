package com.example.data.downloader.extractor

import android.content.Context
import android.os.Build
import android.util.Log
import com.example.BuildConfig
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File

enum class YtDlpFailureCategory {
    EXTRACTOR_ERROR,
    UNSUPPORTED_URL,
    NETWORK_ERROR,
    NETWORK_TIMEOUT,
    HTTP_403,
    HTTP_429,
    LOGIN_REQUIRED,
    BOT_CHALLENGE,
    PLAYER_CHALLENGE,
    MISSING_RUNTIME_COMPONENT,
    PYTHON_RUNTIME_ERROR,
    NATIVE_LIBRARY_ERROR,
    NO_FORMATS,
    PARSE_ERROR,
    UNKNOWN
}

enum class InitializationFailureCategory {
    RUNTIME_INIT_FAILED,
    MISSING_RUNTIME_COMPONENT,
    UNSUPPORTED_ABI,
    NATIVE_LIBRARY_LOAD_FAILED,
    PYTHON_INIT_FAILED,
    RESOURCE_EXTRACTION_FAILED,
    DEPENDENCY_MISMATCH,
    FILESYSTEM_INIT_FAILED,
    UNKNOWN_INIT_FAILURE
}

object YtDlpDiagnostics {
    const val TAG = "PixelRoxYtDlp"

    fun log(message: String) {
        if (BuildConfig.DEBUG) {
            try {
                Log.d(TAG, message)
            } catch (_: Throwable) {
                // Fallback for non-Robolectric test environments where android.util.Log is unmocked
                println("[$TAG] $message")
            }
        }
    }

    fun logRuntimeStatus(context: Context?) {
        if (!BuildConfig.DEBUG) return
        val isReady = YtDlpInitializer.isReady()
        val libVersion = "0.18.1"
        val pkgVersion = try {
            if (context != null && isReady) {
                YoutubeDL.getInstance().version(context.applicationContext) ?: "unknown"
            } else {
                "uninitialized"
            }
        } catch (_: Exception) {
            "unavailable"
        }
        log("YTDLP_RUNTIME_STATUS: LibVersion=$libVersion, PkgVersion=$pkgVersion, PythonRuntimeAvailable=$isReady, NativeRuntimeAvailable=$isReady")
    }

    fun logDeviceAbiDiagnostics(context: Context?) {
        if (!BuildConfig.DEBUG) return
        val primaryAbi = Build.SUPPORTED_ABIS.firstOrNull() ?: "unknown"
        val supportedAbis = Build.SUPPORTED_ABIS.joinToString(",")
        val nativeLibDirPresent = if (context != null) {
            try {
                val dir = File(context.applicationInfo.nativeLibraryDir)
                dir.exists() && dir.isDirectory
            } catch (_: Throwable) {
                false
            }
        } else {
            false
        }
        log("DEVICE_PRIMARY_ABI=$primaryAbi")
        log("SUPPORTED_ABIS=$supportedAbis")
        log("APP_NATIVE_LIBRARY_DIR_PRESENT=${if (nativeLibDirPresent) "YES" else "NO"}")
    }

    fun inspectExceptionChain(e: Throwable) {
        if (!BuildConfig.DEBUG) return
        val topClass = e.javaClass.name
        val topMsg = sanitizeMsg(e.message)
        log("TopExceptionClass=$topClass")
        log("TopExceptionMessageSafe=$topMsg")

        var cause1: Throwable? = e.cause
        if (cause1 != null) {
            log("Cause1Class=${cause1.javaClass.name}")
            log("Cause1MessageSafe=${sanitizeMsg(cause1.message)}")
        }

        var cause2: Throwable? = cause1?.cause
        if (cause2 != null) {
            log("Cause2Class=${cause2.javaClass.name}")
            log("Cause2MessageSafe=${sanitizeMsg(cause2.message)}")
        }

        var root = e
        while (root.cause != null && root.cause !== root) {
            root = root.cause!!
        }
        log("RootCauseClass=${root.javaClass.name}")
        log("RootCauseMessageSafe=${sanitizeMsg(root.message)}")
    }

    private fun sanitizeMsg(msg: String?): String {
        if (msg.isNullOrBlank()) return "none"
        // Remove file paths, tokens, cookies
        return msg.replace(Regex("/[a-zA-Z0-9_/.-]+"), "[path]")
            .replace(Regex("(?i)(token|key|cookie|auth)=[^\\s]+"), "[masked]")
    }

    fun classifyInitError(e: Throwable): InitializationFailureCategory {
        val msg = (e.message ?: "").lowercase()
        val causeMsg = (e.cause?.message ?: "").lowercase()
        val combined = "$msg $causeMsg"
        val className = e.javaClass.simpleName.lowercase()

        return when {
            combined.contains("abi") || combined.contains("32-bit") || combined.contains("64-bit") || combined.contains("cpu") ->
                InitializationFailureCategory.UNSUPPORTED_ABI
            combined.contains("unsatisfiedlinkerror") || combined.contains("dlopen") || combined.contains(".so") || className.contains("linkage") ->
                InitializationFailureCategory.NATIVE_LIBRARY_LOAD_FAILED
            combined.contains("python") || combined.contains("libpython") || combined.contains("interpreter") ->
                InitializationFailureCategory.PYTHON_INIT_FAILED
            combined.contains("extract") || combined.contains("unzip") || combined.contains("zip") || combined.contains("asset") ->
                InitializationFailureCategory.RESOURCE_EXTRACTION_FAILED
            combined.contains("file") || combined.contains("dir") || combined.contains("permission") || combined.contains("ioexception") ->
                InitializationFailureCategory.FILESYSTEM_INIT_FAILED
            combined.contains("missing") || combined.contains("component") || combined.contains("not found") ->
                InitializationFailureCategory.MISSING_RUNTIME_COMPONENT
            combined.contains("version") || combined.contains("dependency") || combined.contains("incompatible") ->
                InitializationFailureCategory.DEPENDENCY_MISMATCH
            combined.contains("init") || combined.contains("youtubedlexception") ->
                InitializationFailureCategory.RUNTIME_INIT_FAILED
            else ->
                InitializationFailureCategory.UNKNOWN_INIT_FAILURE
        }
    }

    fun classifyError(e: Throwable): YtDlpFailureCategory {
        val msg = (e.message ?: "").lowercase()
        val causeMsg = (e.cause?.message ?: "").lowercase()
        val combined = "$msg $causeMsg"
        val className = e.javaClass.simpleName.lowercase()
        return when {
            msg.contains("connect timed out") || msg.contains("connection timed out") || (className.contains("socket") && !combined.contains("transporterror")) -> YtDlpFailureCategory.NETWORK_ERROR
            combined.contains("transporterror") || combined.contains("timed out") || combined.contains("timeout") -> YtDlpFailureCategory.NETWORK_TIMEOUT
            msg.contains("captcha") || msg.contains("bot") || msg.contains("robot") ||
                    msg.contains("recaptcha") || msg.contains("verify") || msg.contains("verification") ||
                    msg.contains("confirm you're not a robot") -> YtDlpFailureCategory.BOT_CHALLENGE
            msg.contains("403") || msg.contains("forbidden") -> YtDlpFailureCategory.HTTP_403
            msg.contains("429") || msg.contains("too many requests") || msg.contains("rate limit") -> YtDlpFailureCategory.HTTP_429
            msg.contains("login required") || msg.contains("sign in") || msg.contains("authentication required") ||
                    msg.contains("account is private") || msg.contains("private video") || msg.contains("followers-only") -> YtDlpFailureCategory.LOGIN_REQUIRED
            msg.contains("player") || msg.contains("signature") || msg.contains("nsig") || msg.contains("challenge") -> YtDlpFailureCategory.PLAYER_CHALLENGE
            msg.contains("sockettimeout") || msg.contains("unknownhost") || msg.contains("connect timed out") ||
                    msg.contains("network") || msg.contains("connection refused") || className.contains("socket") -> YtDlpFailureCategory.NETWORK_ERROR
            msg.contains("unsupported url") || msg.contains("no suitable extractor") -> YtDlpFailureCategory.UNSUPPORTED_URL
            msg.contains("no formats") || msg.contains("no downloadable formats") -> YtDlpFailureCategory.NO_FORMATS
            msg.contains("json") || msg.contains("parse") || msg.contains("unexpected end") -> YtDlpFailureCategory.PARSE_ERROR
            msg.contains("libpython") || msg.contains(".so") || msg.contains("dlopen") ||
                    msg.contains("unsatisfiedlinkerror") || className.contains("linkage") -> YtDlpFailureCategory.NATIVE_LIBRARY_ERROR
            msg.contains("syntaxerror") || msg.contains("attributeerror") || msg.contains("traceback") ||
                    msg.contains("python") || msg.contains("typeerror") || msg.contains("keyerror") -> YtDlpFailureCategory.PYTHON_RUNTIME_ERROR
            msg.contains("not initialized") || msg.contains("missing") || msg.contains("component") -> YtDlpFailureCategory.MISSING_RUNTIME_COMPONENT
            msg.contains("extractorerror") || msg.contains("youtubedlexception") || className.contains("youtubedl") -> YtDlpFailureCategory.EXTRACTOR_ERROR
            else -> YtDlpFailureCategory.UNKNOWN
        }
    }
}

