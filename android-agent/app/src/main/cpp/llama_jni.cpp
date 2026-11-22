#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "llama.h"

#define TAG "SilentExecLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint threads
) {
    if (g_model != nullptr) {
        LOGI("Model already loaded, releasing previous context");
        if (g_ctx) { llama_free(g_ctx); g_ctx = nullptr; }
        if (g_model) { llama_free_model(g_model); g_model = nullptr; }
    }

    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!path_chars) return JNI_FALSE;

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path_chars, model_params);
    env->ReleaseStringUTFChars(model_path, path_chars);

    if (!g_model) {
        LOGE("Failed to load llama model from file");
        return JNI_FALSE;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = threads > 0 ? threads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (!g_ctx) {
        LOGE("Failed to create llama context with model");
        llama_free_model(g_model);
        g_model = nullptr;
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully with %d threads", ctx_params.n_threads);
    return JNI_TRUE;
}

JNIEXPORT jboolean JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeIsLoaded(
    JNIEnv* env,
    jobject /* this */
) {
    return (g_model != nullptr && g_ctx != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeUnload(
    JNIEnv* env,
    jobject /* this */
) {
    if (g_ctx) {
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model) {
        llama_free_model(g_model);
        g_model = nullptr;
    }
    llama_backend_free();
    LOGI("Model unloaded successfully");
}

JNIEXPORT jstring JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeGenerateJson(
    JNIEnv* env,
    jobject /* this */,
    jstring prompt,
    jstring grammar_text,
    jint max_tokens
) {
    if (!g_model || !g_ctx) {
        return env->NewStringUTF("{\"error\":\"Model not loaded\"}");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) return env->NewStringUTF("{\"error\":\"Invalid prompt\"}");

    const char* grammar_str = grammar_text ? env->GetStringUTFChars(grammar_text, nullptr) : nullptr;

    const struct llama_vocab* vocab = llama_model_get_vocab(g_model);

    std::string prompt_text(prompt_str);
    int n_tokens = -llama_tokenize(vocab, prompt_text.c_str(), prompt_text.length(), NULL, 0, true, true);
    std::vector<llama_token> prompt_tokens(n_tokens);
    if (llama_tokenize(vocab, prompt_text.c_str(), prompt_text.length(), prompt_tokens.data(), prompt_tokens.size(), true, true) < 0) {
        env->ReleaseStringUTFChars(prompt, prompt_str);
        if (grammar_str) env->ReleaseStringUTFChars(grammar_text, grammar_str);
        return env->NewStringUTF("{\"error\":\"Prompt tokenization failed\"}");
    }

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);

    if (grammar_str && strlen(grammar_str) > 0) {
        llama_sampler_chain_add(smpl, llama_sampler_init_grammar(vocab, grammar_str, "root"));
    }
    llama_sampler_chain_add(smpl, llama_sampler_init_greedy());

    llama_batch batch = llama_batch_get_one(prompt_tokens.data(), prompt_tokens.size());
    if (llama_decode(g_ctx, batch) != 0) {
        llama_sampler_free(smpl);
        env->ReleaseStringUTFChars(prompt, prompt_str);
        if (grammar_str) env->ReleaseStringUTFChars(grammar_text, grammar_str);
        return env->NewStringUTF("{\"error\":\"Initial prompt decode failed\"}");
    }

    std::string output_str;
    int limit = max_tokens > 0 ? max_tokens : 160;

    for (int i = 0; i < limit; ++i) {
        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        llama_sampler_accept(smpl, new_token);

        if (llama_vocab_is_eog(vocab, new_token)) {
            break;
        }

        char piece_buf[128];
        int n_piece = llama_token_to_piece(vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
        if (n_piece > 0) {
            output_str.append(piece_buf, n_piece);
        }

        batch = llama_batch_get_one(&new_token, 1);
        if (llama_decode(g_ctx, batch) != 0) {
            break;
        }
    }

    llama_sampler_free(smpl);
    env->ReleaseStringUTFChars(prompt, prompt_str);
    if (grammar_str) env->ReleaseStringUTFChars(grammar_text, grammar_str);

    return env->NewStringUTF(output_str.c_str());
}

} // extern "C"
