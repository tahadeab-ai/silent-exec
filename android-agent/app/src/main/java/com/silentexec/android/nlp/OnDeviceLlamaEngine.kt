package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * Real On-Device llama.cpp Inference Engine for SilentExec Android.
 * Communicates with `libllama_jni.so` compiled via NDK for fast arm64-v8a offline execution.
 */
class OnDeviceLlamaEngine {

    companion object {
        val isNativeLoaded: Boolean = try {
            System.loadLibrary("llama_jni")
            true
        } catch (_: Throwable) {
            false
        }
        const val MIN_MODEL_SIZE_BYTES = 5_000_000L // 5 MB

        /**
         * Resolves the canonical absolute model path under filesDir/models/<fileName>.
         */
        fun resolveCanonicalModelFile(filesDir: File, fileName: String): File {
            val baseName = File(fileName.trim()).name
            return File(File(filesDir, "models"), baseName).absoluteFile
        }
    }

    @Volatile
    private var configuredModelFile: File? = null

    @Volatile
    private var configuredThreads: Int = 4

    external fun nativeLoadModel(modelPath: String, threads: Int): Boolean
    external fun nativeGetLastError(): String
    external fun nativeIsLoaded(): Boolean
    external fun nativeUnload()
    external fun nativeGenerateJson(prompt: String, grammarText: String, maxTokens: Int): String

    /**
     * Configures the active GGUF model file for lazy background loading.
     */
    fun setModelFile(modelFile: File?, threads: Int = 4) {
        configuredModelFile = modelFile
        configuredThreads = threads
    }

    /**
     * Validates that the model file exists and is at least 5 MB.
     * Returns null if valid, or a clean user-facing error message if invalid.
     */
    fun validateModelFile(modelFile: File?): String? {
        if (modelFile == null || !modelFile.exists()) {
            return "ملف النموذج غير موجود. يرجى تحميل نموذج من تبويب النماذج أو اختيار Cloud API من الإعدادات"
        }
        if (!modelFile.canRead()) {
            return "تعذر قراءة ملف النموذج (${modelFile.absolutePath}). يرجى إعادة تحميله من تبويب النماذج"
        }
        if (modelFile.length() <= MIN_MODEL_SIZE_BYTES) {
            return "ملف النموذج غير صالح أو أصغر من 5 ميجابايت (${modelFile.name}). يرجى إعادة تحميله من تبويب النماذج"
        }
        return null
    }

    /**
     * Loads a quantized GGUF model into memory for fast inference.
     * Guarded with file checks (>= 5 MB) and JNI exception safety.
     */
    @Synchronized
    fun loadModel(modelFile: File, threads: Int = 4): Boolean {
        if (!isNativeLoaded) {
            return false
        }
        if (validateModelFile(modelFile) != null) {
            return false
        }
        configuredModelFile = modelFile
        configuredThreads = threads
        return try {
            nativeLoadModel(modelFile.absolutePath, threads)
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Lazily loads the active GGUF file on a background thread if not yet loaded.
     */
    fun loadModelAsync(modelFile: File, threads: Int = 4, onComplete: (Result<Unit>) -> Unit) {
        val validationError = validateModelFile(modelFile)
        if (validationError != null) {
            onComplete(Result.failure(IllegalStateException(validationError)))
            return
        }
        if (isLoaded()) {
            onComplete(Result.success(Unit))
            return
        }
        thread(name = "llama-model-loader") {
            val ok = loadModel(modelFile, threads)
            if (ok) {
                onComplete(Result.success(Unit))
            } else {
                val nativeErr = runCatching {
                    if (isNativeLoaded) nativeGetLastError() else "Native library not loaded"
                }.getOrDefault("")
                val detail = if (nativeErr.isNotBlank()) " ($nativeErr)" else ""
                onComplete(Result.failure(IllegalStateException("فشل تحميل النموذج المحلي: ${modelFile.name}$detail")))
            }
        }
    }

    /**
     * Checks if the model is currently loaded in memory.
     */
    fun isLoaded(): Boolean {
        if (!isNativeLoaded) return false
        return try {
            nativeIsLoaded()
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Releases model and context memory safely.
     */
    @Synchronized
    fun unload() {
        if (isNativeLoaded) {
            try {
                nativeUnload()
            } catch (_: Throwable) {}
        }
    }

    /**
     * Formats user query with zero-chatter ChatML prompt, runs constrained GBNF inference,
     * extracts clean JSON, and parses into typed ActionCommand.
     * Lazily loads the configured GGUF model on a background thread if not yet loaded.
     */
    fun inferActionCommand(
        userQuery: String,
        grammarText: String = "",
        maxTokens: Int = 160
    ): Result<ActionCommand> {
        return runCatching {
            if (!isLoaded()) {
                val activeFile = configuredModelFile
                if (activeFile != null) {
                    val validationError = validateModelFile(activeFile)
                    if (validationError != null) {
                        throw IllegalStateException(validationError)
                    }
                    if (!isNativeLoaded) {
                        throw IllegalStateException("المكتبة الأصلية للنموذج غير محملة")
                    }
                    val latch = CountDownLatch(1)
                    var loadResult: Result<Unit> = Result.failure(IllegalStateException("انتهت مهلة تحميل النموذج"))
                    loadModelAsync(activeFile, configuredThreads) { res ->
                        loadResult = res
                        latch.countDown()
                    }
                    latch.await(60, TimeUnit.SECONDS)
                    loadResult.getOrThrow()
                } else {
                    throw IllegalStateException("يرجى تحميل نموذج من تبويب النماذج أو اختيار Cloud API من الإعدادات")
                }
            }
            val prompt = PromptFormatter.formatPrompt(userQuery)
            val rawOutput = try {
                nativeGenerateJson(prompt, grammarText, maxTokens)
            } catch (t: Throwable) {
                throw IllegalStateException("فشل تشغيل النموذج المحلي: ${t.message ?: "Unknown error"}", t)
            }
            val cleanJson = PromptFormatter.extractActionJson(rawOutput)
            PromptFormatter.normalizeCommand(ActionJson.fromLlmJson(cleanJson))
        }
    }
}
