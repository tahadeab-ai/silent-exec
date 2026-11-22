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
    }

    external fun nativeLoadModel(modelPath: String, threads: Int): Boolean
    external fun nativeIsLoaded(): Boolean
    external fun nativeUnload()
    external fun nativeGenerateJson(prompt: String, grammarText: String, maxTokens: Int): String

    /**
     * Loads a quantized GGUF model into memory for fast inference.
     */
    fun loadModel(modelFile: File, threads: Int = 4): Boolean {
        if (!isNativeLoaded || !modelFile.exists()) {
            return false
        }
        return nativeLoadModel(modelFile.absolutePath, threads)
    }

    /**
     * Checks if the model is currently loaded in memory.
     */
    fun isLoaded(): Boolean {
        if (!isNativeLoaded) return false
        return nativeIsLoaded()
    }

    /**
     * Releases model and context memory.
     */
    fun unload() {
        if (isNativeLoaded) {
            nativeUnload()
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
                throw IllegalStateException("llama.cpp model is not loaded in memory")
            }
            val prompt = PromptFormatter.formatPrompt(userQuery)
            val rawOutput = nativeGenerateJson(prompt, grammarText, maxTokens)
            val cleanJson = PromptFormatter.extractActionJson(rawOutput)
            ActionJson.fromJson(cleanJson)
        }
    }
}
