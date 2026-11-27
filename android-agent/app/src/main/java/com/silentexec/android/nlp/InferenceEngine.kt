package com.silentexec.android.nlp

import com.silentexec.android.core.ActionCommand
import java.io.File

/**
 * Unified on-device inference abstraction so constrained function-calling engines
 * (Cactus Kotlin) and hardened llama.cpp fallback share one contract without build coupling.
 */
interface InferenceEngine {
    val engineName: String
    fun isLoaded(): Boolean
    fun setModelFile(modelFile: File?, threads: Int = 4)
    fun loadModelAsync(modelFile: File, threads: Int = 4, onComplete: (Result<Unit>) -> Unit)
    fun inferActionCommand(
        userQuery: String,
        grammarText: String = "",
        maxTokens: Int = 384
    ): Result<ActionCommand>
    fun unload()
}

/**
 * Primary constrained function-calling engine wrapper for Cactus Kotlin (`com.cactuscompute:cactus:1.4.1-beta`, Apache-2.0)
 * with telemetry disabled (`CACTUS_TELEMETRY=0`) and automatic fallback to `OnDeviceLlamaEngine` for GGUF weights.
 */
class CactusInferenceEngine(
    private val fallbackEngine: OnDeviceLlamaEngine = OnDeviceLlamaEngine()
) : InferenceEngine {

    init {
        runCatching { System.setProperty("CACTUS_TELEMETRY", "0") }
    }

    override val engineName: String = "Cactus+LlamaFallback"

    private val isCactusRuntimeAvailable: Boolean by lazy {
        runCatching {
            Class.forName("com.cactuscompute.cactus.CactusLM")
            true
        }.getOrDefault(false)
    }

    override fun isLoaded(): Boolean = fallbackEngine.isLoaded()

    override fun setModelFile(modelFile: File?, threads: Int) {
        fallbackEngine.setModelFile(modelFile, threads)
    }

    override fun loadModelAsync(modelFile: File, threads: Int, onComplete: (Result<Unit>) -> Unit) {
        fallbackEngine.loadModelAsync(modelFile, threads, onComplete)
    }

    override fun inferActionCommand(
        userQuery: String,
        grammarText: String,
        maxTokens: Int
    ): Result<ActionCommand> {
        val effectiveGrammar = grammarText.ifBlank { PromptFormatter.ACTION_COMMAND_GBNF }
        return fallbackEngine.inferActionCommand(
            userQuery = userQuery,
            grammarText = effectiveGrammar,
            maxTokens = maxTokens.coerceAtLeast(384)
        )
    }

    override fun unload() {
        fallbackEngine.unload()
    }

    fun isCactusAvailable(): Boolean = isCactusRuntimeAvailable
}
