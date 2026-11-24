package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import com.silentexec.android.core.ActionJson
import java.io.File

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
        private const val MIN_MODEL_SIZE_BYTES = 1_000_000L
    }

    external fun nativeLoadModel(modelPath: String, threads: Int): Boolean
    external fun nativeIsLoaded(): Boolean
    external fun nativeUnload()
    external fun nativeGenerateJson(prompt: String, grammarText: String, maxTokens: Int): String

    /**
     * Loads a quantized GGUF model into memory for fast inference.
     * Guarded with file checks and JNI exception safety.
     */
    @Synchronized
    fun loadModel(modelFile: File, threads: Int = 4): Boolean {
        if (!isNativeLoaded) {
            return false
        }
        if (!modelFile.exists() || modelFile.length() < MIN_MODEL_SIZE_BYTES) {
            return false
        }
        return try {
            nativeLoadModel(modelFile.absolutePath, threads)
        } catch (_: Throwable) {
            false
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
     */
    fun inferActionCommand(
        userQuery: String,
        grammarText: String = "",
        maxTokens: Int = 160
    ): Result<ActionCommand> {
        return runCatching {
            if (!isLoaded()) {
                throw IllegalStateException("يرجى تحميل نموذج من تبويب النماذج أو اختيار Cloud API من الإعدادات")
            }
            val prompt = PromptFormatter.formatPrompt(userQuery)
            val rawOutput = try {
                nativeGenerateJson(prompt, grammarText, maxTokens)
            } catch (t: Throwable) {
                throw IllegalStateException("فشل تشغيل النموذج المحلي: ${t.message ?: "Unknown error"}", t)
            }
            val cleanJson = PromptFormatter.extractActionJson(rawOutput)
            ActionJson.fromJson(cleanJson)
        }
    }
}
