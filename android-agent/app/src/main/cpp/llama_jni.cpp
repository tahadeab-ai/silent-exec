#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <cerrno>
#include <cstdio>
#include <algorithm>
#include <thread>
#include <mutex>
#include <android/log.h>
#include "llama.h"

#define TAG "SilentExecLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_llama_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_vocab* g_vocab = nullptr;
static std::vector<llama_token> g_cached_tokens;
static std::string g_last_error;

static void clear_kv_cache_locked() {
    g_cached_tokens.clear();
    if (g_ctx != nullptr) {
        llama_memory_t mem = llama_get_memory(g_ctx);
        if (mem != nullptr) {
            llama_memory_clear(mem, true);
        }
    }
}

static void release_llama_state_locked() {
    if (g_ctx != nullptr) {
        clear_kv_cache_locked();
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_model_free(g_model);
        g_model = nullptr;
    }
    g_vocab = nullptr;
}

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeLoadModel(
    JNIEnv* env,
    jobject /* this */,
    jstring model_path,
    jint threads
) {
    if (env == nullptr || model_path == nullptr) {
        std::lock_guard<std::mutex> lock(g_llama_mutex);
        g_last_error = "Invalid JNI environment or null model path";
        return JNI_FALSE;
    }

    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!path_chars) {
        std::lock_guard<std::mutex> lock(g_llama_mutex);
        g_last_error = "Failed to decode UTF-8 model path";
        return JNI_FALSE;
    }
    std::string path_str(path_chars);
    env->ReleaseStringUTFChars(model_path, path_chars);

    if (path_str.empty()) {
        std::lock_guard<std::mutex> lock(g_llama_mutex);
        g_last_error = "Model path is empty";
        LOGE("Model path is empty");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_llama_mutex);
    g_last_error.clear();

    // Cleanly free any stale context/model before loading
    if (g_model != nullptr || g_ctx != nullptr) {
        LOGI("Releasing stale llama context/model before loading new model");
        release_llama_state_locked();
    }

    FILE* fp = std::fopen(path_str.c_str(), "rb");
    if (fp == nullptr) {
        g_last_error = "Cannot open model file '" + path_str + "': " + std::strerror(errno);
        LOGE("%s", g_last_error.c_str());
        return JNI_FALSE;
    }
    std::fclose(fp);

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_model_load_from_file(path_str.c_str(), model_params);

    if (g_model == nullptr) {
        g_last_error = "llama_model_load_from_file failed for '" + path_str + "' (corrupt GGUF or unsupported quant/architecture)";
        LOGE("%s", g_last_error.c_str());
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    g_vocab = llama_model_get_vocab(g_model);
    if (g_vocab == nullptr) {
        g_last_error = "llama_model_get_vocab returned null for '" + path_str + "'";
        LOGE("%s", g_last_error.c_str());
        release_llama_state_locked();
        return JNI_FALSE;
    }

    int hw_threads = static_cast<int>(std::thread::hardware_concurrency());
    if (hw_threads <= 0) {
        hw_threads = 4;
    }
    int req_threads = threads > 0 ? threads : 4;
    int safe_threads = std::min(req_threads, std::min(4, hw_threads));
    if (safe_threads < 1) {
        safe_threads = 1;
    }

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 4096;
    ctx_params.n_threads = safe_threads;
    ctx_params.n_threads_batch = safe_threads;
    ctx_params.type_k = GGML_TYPE_Q8_0;

    g_ctx = llama_init_from_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        g_last_error = "llama_init_from_model failed (n_ctx=4096, n_threads=" + std::to_string(safe_threads) + ", insufficient RAM)";
        LOGE("%s", g_last_error.c_str());
        release_llama_state_locked();
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully (n_ctx=4096, n_threads=%d)", safe_threads);
    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeGetLastError(
    JNIEnv* env,
    jobject /* this */
) {
    if (env == nullptr) return nullptr;
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    return env->NewStringUTF(g_last_error.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeIsLoaded(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    return (g_model != nullptr && g_ctx != nullptr && g_vocab != nullptr) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_silentexec_android_nlp_OnDeviceLlamaEngine_nativeUnload(
    JNIEnv* /* env */,
    jobject /* this */
) {
    std::lock_guard<std::mutex> lock(g_llama_mutex);
    release_llama_state_locked();
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
    if (env == nullptr) {
        return nullptr;
    }
    if (prompt == nullptr) {
        return env->NewStringUTF("{\"error\":\"Invalid prompt\"}");
    }

    const char* prompt_str = env->GetStringUTFChars(prompt, nullptr);
    if (!prompt_str) {
        return env->NewStringUTF("{\"error\":\"Invalid prompt\"}");
    }
    std::string prompt_text(prompt_str);
    env->ReleaseStringUTFChars(prompt, prompt_str);

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (g_model == nullptr || g_ctx == nullptr || g_vocab == nullptr) {
        return env->NewStringUTF("{\"error\":\"Model not loaded\"}");
    }

    if (prompt_text.empty()) {
        return env->NewStringUTF("{\"error\":\"Empty prompt\"}");
    }

    std::string grammar_str;
    if (grammar_text != nullptr) {
        const char* g_chars = env->GetStringUTFChars(grammar_text, nullptr);
        if (g_chars != nullptr) {
            grammar_str.assign(g_chars);
            env->ReleaseStringUTFChars(grammar_text, g_chars);
        }
    }

    const int limit = max_tokens > 0 ? max_tokens : 384;
    const uint32_t n_ctx = llama_n_ctx(g_ctx);
    if (n_ctx == 0 || static_cast<uint32_t>(limit) >= n_ctx) {
        return env->NewStringUTF("{\"error\":\"Invalid context window size\"}");
    }

    int n_tokens = -llama_tokenize(
        g_vocab,
        prompt_text.c_str(),
        static_cast<int32_t>(prompt_text.length()),
        nullptr,
        0,
        true,
        true
    );
    if (n_tokens <= 0) {
        return env->NewStringUTF("{\"error\":\"Failed to calculate prompt token count\"}");
    }

    if (static_cast<uint32_t>(n_tokens) >= (n_ctx - static_cast<uint32_t>(limit))) {
        LOGE("Prompt token count %d exceeds safe context budget (n_ctx=%u, max_tokens=%d)", n_tokens, n_ctx, limit);
        return env->NewStringUTF("{\"error\":\"Prompt exceeds context window\"}");
    }

    std::vector<llama_token> prompt_tokens(static_cast<size_t>(n_tokens));
    if (llama_tokenize(
            g_vocab,
            prompt_text.c_str(),
            static_cast<int32_t>(prompt_text.length()),
            prompt_tokens.data(),
            static_cast<int32_t>(prompt_tokens.size()),
            true,
            true) < 0) {
        return env->NewStringUTF("{\"error\":\"Prompt tokenization failed\"}");
    }

    // Reuse KV cache for matching prefix (pins static system prompt 0..n_sys across steps)
    int n_common = 0;
    const int max_common = std::min(static_cast<int>(g_cached_tokens.size()), n_tokens - 1);
    while (n_common < max_common &&
           g_cached_tokens[static_cast<size_t>(n_common)] == prompt_tokens[static_cast<size_t>(n_common)]) {
        ++n_common;
    }

    if (n_common > 0) {
        llama_memory_t mem = llama_get_memory(g_ctx);
        if (mem != nullptr) {
            llama_memory_seq_rm(mem, 0, static_cast<llama_pos>(n_common), -1);
        } else {
            n_common = 0;
            clear_kv_cache_locked();
        }
    } else {
        clear_kv_cache_locked();
    }

    const int n_eval = n_tokens - n_common;
    llama_batch batch = llama_batch_init(n_eval, 0, 1);
    if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr ||
        batch.seq_id == nullptr || batch.logits == nullptr) {
        llama_batch_free(batch);
        return env->NewStringUTF("{\"error\":\"Failed to allocate prompt batch\"}");
    }

    for (int i = 0; i < n_eval; ++i) {
        const int tok_idx = n_common + i;
        batch.token[i] = prompt_tokens[static_cast<size_t>(tok_idx)];
        batch.pos[i] = static_cast<llama_pos>(tok_idx);
        batch.n_seq_id[i] = 1;
        if (batch.seq_id[i] != nullptr) {
            batch.seq_id[i][0] = 0;
        }
        batch.logits[i] = (tok_idx == n_tokens - 1) ? 1 : 0;
    }
    batch.n_tokens = static_cast<int32_t>(n_eval);

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        clear_kv_cache_locked();
        return env->NewStringUTF("{\"error\":\"Prompt decode failed\"}");
    }
    llama_batch_free(batch);
    g_cached_tokens = prompt_tokens;

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (smpl == nullptr) {
        clear_kv_cache_locked();
        return env->NewStringUTF("{\"error\":\"Failed to initialize sampler chain\"}");
    }

    llama_sampler* penalty_smpl = llama_sampler_init_penalties(
        llama_vocab_n_tokens(g_vocab),
        64,
        1.12f,
        0.0f,
        0.0f
    );
    if (penalty_smpl != nullptr) {
        llama_sampler_chain_add(smpl, penalty_smpl);
    }

    llama_sampler* top_k_smpl = llama_sampler_init_top_k(40);
    if (top_k_smpl != nullptr) {
        llama_sampler_chain_add(smpl, top_k_smpl);
    }

    llama_sampler* top_p_smpl = llama_sampler_init_top_p(0.90f, 1);
    if (top_p_smpl != nullptr) {
        llama_sampler_chain_add(smpl, top_p_smpl);
    }

    llama_sampler* temp_smpl = llama_sampler_init_temp(0.20f);
    if (temp_smpl != nullptr) {
        llama_sampler_chain_add(smpl, temp_smpl);
    }

    if (!grammar_str.empty() && grammar_str != "NO_THINK") {
        llama_sampler* grammar_smpl = llama_sampler_init_grammar(g_vocab, grammar_str.c_str(), "root");
        if (grammar_smpl != nullptr) {
            llama_sampler_chain_add(smpl, grammar_smpl);
        }
    }

    llama_sampler* dist_smpl = llama_sampler_init_dist(LLAMA_DEFAULT_SEED);
    if (dist_smpl != nullptr) {
        llama_sampler_chain_add(smpl, dist_smpl);
    } else {
        llama_sampler_free(smpl);
        clear_kv_cache_locked();
        return env->NewStringUTF("{\"error\":\"Failed to initialize dist sampler\"}");
    }

    std::string output_str;
    bool saw_open_brace = false;
    bool in_string = false;
    bool escape_next = false;
    int brace_depth = 0;
    size_t scan_idx = 0;
    bool passed_think = false;

    for (int i = 0; i < limit; ++i) {
        const llama_pos next_pos = static_cast<llama_pos>(n_tokens + i);
        if (static_cast<uint32_t>(next_pos) >= n_ctx) {
            break;
        }

        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        llama_sampler_accept(smpl, new_token);

        if (llama_vocab_is_eog(g_vocab, new_token)) {
            break;
        }

        char piece_buf[256];
        int n_piece = llama_token_to_piece(g_vocab, new_token, piece_buf, sizeof(piece_buf), 0, true);
        if (n_piece > 0 && n_piece <= static_cast<int>(sizeof(piece_buf))) {
            output_str.append(piece_buf, static_cast<size_t>(n_piece));
        }

        const size_t im_end_pos = output_str.find("<|im_end|>");
        if (im_end_pos != std::string::npos) {
            output_str.resize(im_end_pos);
            break;
        }
        const size_t eot_pos = output_str.find("<|endoftext|>");
        if (eot_pos != std::string::npos) {
            output_str.resize(eot_pos);
            break;
        }

        const size_t first_non_ws = output_str.find_first_not_of(" \t\r\n");
        const bool starts_with_think =
            (first_non_ws != std::string::npos && output_str.compare(first_non_ws, 7, "<think>") == 0);
        const size_t think_end_pos =
            starts_with_think ? output_str.find("</think>", first_non_ws + 7) : std::string::npos;

        // Hard <think> token budget guard: break cleanly if unclosed after 96 tokens
        if (starts_with_think && think_end_pos == std::string::npos && (i + 1) > 96) {
            LOGI("Hard <think> budget guard triggered at token %d", i + 1);
            break;
        }

        if (starts_with_think && think_end_pos != std::string::npos && !passed_think) {
            passed_think = true;
            scan_idx = think_end_pos + 8;
            saw_open_brace = false;
            in_string = false;
            escape_next = false;
            brace_depth = 0;
        }

        bool json_complete = false;
        if (!starts_with_think || passed_think) {
            while (scan_idx < output_str.size()) {
                const char ch = output_str[scan_idx];
                if (escape_next) {
                    escape_next = false;
                } else if (in_string) {
                    if (ch == '\\') {
                        escape_next = true;
                    } else if (ch == '"') {
                        in_string = false;
                    }
                } else {
                    if (ch == '"') {
                        in_string = true;
                    } else if (ch == '{') {
                        saw_open_brace = true;
                        ++brace_depth;
                    } else if (ch == '}') {
                        if (saw_open_brace) {
                            --brace_depth;
                            if (brace_depth <= 0) {
                                output_str.resize(scan_idx + 1);
                                json_complete = true;
                                break;
                            }
                        }
                    }
                }
                ++scan_idx;
            }
        }
        if (json_complete) {
            break;
        }

        llama_batch gen_batch = llama_batch_init(1, 0, 1);
        if (gen_batch.token == nullptr || gen_batch.pos == nullptr || gen_batch.n_seq_id == nullptr ||
            gen_batch.seq_id == nullptr || gen_batch.seq_id[0] == nullptr || gen_batch.logits == nullptr) {
            llama_batch_free(gen_batch);
            break;
        }
        gen_batch.token[0] = new_token;
        gen_batch.pos[0] = next_pos;
        gen_batch.n_seq_id[0] = 1;
        gen_batch.seq_id[0][0] = 0;
        gen_batch.logits[0] = 1;
        gen_batch.n_tokens = 1;

        int decode_res = llama_decode(g_ctx, gen_batch);
        llama_batch_free(gen_batch);
        if (decode_res != 0) {
            break;
        }
    }

    llama_sampler_free(smpl);

    return env->NewStringUTF(output_str.c_str());
}

} // extern "C"
