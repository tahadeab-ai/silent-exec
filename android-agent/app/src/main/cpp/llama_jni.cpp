#include <jni.h>
#include <string>
#include <vector>
#include <cstring>
#include <mutex>
#include <android/log.h>
#include "llama.h"

#define TAG "SilentExecLlama"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

static std::mutex g_llama_mutex;
static llama_model* g_model = nullptr;
static llama_context* g_ctx = nullptr;
static const llama_model* g_vocab = nullptr;

static void release_llama_state_locked() {
    if (g_ctx != nullptr) {
        llama_kv_cache_clear(g_ctx);
        llama_free(g_ctx);
        g_ctx = nullptr;
    }
    if (g_model != nullptr) {
        llama_free_model(g_model);
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
        return JNI_FALSE;
    }

    const char* path_chars = env->GetStringUTFChars(model_path, nullptr);
    if (!path_chars) {
        return JNI_FALSE;
    }
    std::string path_str(path_chars);
    env->ReleaseStringUTFChars(model_path, path_chars);

    if (path_str.empty()) {
        LOGE("Model path is empty");
        return JNI_FALSE;
    }

    std::lock_guard<std::mutex> lock(g_llama_mutex);

    if (g_model != nullptr || g_ctx != nullptr) {
        LOGI("Model already loaded, releasing previous context");
        release_llama_state_locked();
    }

    llama_backend_init();

    llama_model_params model_params = llama_model_default_params();
    g_model = llama_load_model_from_file(path_str.c_str(), model_params);

    if (g_model == nullptr) {
        LOGE("Failed to load llama model from file: %s", path_str.c_str());
        g_vocab = nullptr;
        return JNI_FALSE;
    }
    g_vocab = g_model;

    llama_context_params ctx_params = llama_context_default_params();
    ctx_params.n_ctx = 1024;
    ctx_params.n_threads = threads > 0 ? threads : 4;
    ctx_params.n_threads_batch = ctx_params.n_threads;

    g_ctx = llama_new_context_with_model(g_model, ctx_params);
    if (g_ctx == nullptr) {
        LOGE("Failed to create llama context with model");
        release_llama_state_locked();
        return JNI_FALSE;
    }

    LOGI("Model loaded successfully with %d threads", ctx_params.n_threads);
    return JNI_TRUE;
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
    jstring /* grammar_text */,
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

    // Clear KV cache completely before each generation to avoid stale/invalid state crashes
    llama_kv_cache_clear(g_ctx);

    const int limit = max_tokens > 0 ? max_tokens : 160;
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

    llama_batch batch = llama_batch_init(n_tokens, 0, 1);
    if (batch.token == nullptr || batch.pos == nullptr || batch.n_seq_id == nullptr ||
        batch.seq_id == nullptr || batch.logits == nullptr) {
        llama_batch_free(batch);
        return env->NewStringUTF("{\"error\":\"Failed to allocate prompt batch\"}");
    }

    for (int i = 0; i < n_tokens; ++i) {
        batch.token[i] = prompt_tokens[static_cast<size_t>(i)];
        batch.pos[i] = static_cast<llama_pos>(i);
        batch.n_seq_id[i] = 1;
        if (batch.seq_id[i] != nullptr) {
            batch.seq_id[i][0] = 0;
        }
        batch.logits[i] = (i == n_tokens - 1) ? 1 : 0;
    }
    batch.n_tokens = static_cast<int32_t>(n_tokens);

    if (llama_decode(g_ctx, batch) != 0) {
        llama_batch_free(batch);
        llama_kv_cache_clear(g_ctx);
        return env->NewStringUTF("{\"error\":\"Prompt decode failed\"}");
    }
    llama_batch_free(batch);

    auto sparams = llama_sampler_chain_default_params();
    llama_sampler* smpl = llama_sampler_chain_init(sparams);
    if (smpl == nullptr) {
        llama_kv_cache_clear(g_ctx);
        return env->NewStringUTF("{\"error\":\"Failed to initialize sampler chain\"}");
    }

    llama_sampler* penalty_smpl = llama_sampler_init_penalties(
        llama_n_vocab(g_vocab),
        llama_token_eos(g_vocab),
        llama_token_nl(g_vocab),
        64,
        1.15f,
        0.0f,
        0.0f,
        false,
        false
    );
    if (penalty_smpl != nullptr) {
        llama_sampler_chain_add(smpl, penalty_smpl);
    }

    llama_sampler* greedy_smpl = llama_sampler_init_greedy();
    if (greedy_smpl != nullptr) {
        llama_sampler_chain_add(smpl, greedy_smpl);
    } else {
        llama_sampler_free(smpl);
        llama_kv_cache_clear(g_ctx);
        return env->NewStringUTF("{\"error\":\"Failed to initialize greedy sampler\"}");
    }

    std::string output_str;
    bool saw_open_brace = false;
    bool in_string = false;
    bool escape_next = false;
    int brace_depth = 0;
    size_t scan_idx = 0;

    for (int i = 0; i < limit; ++i) {
        const llama_pos next_pos = static_cast<llama_pos>(n_tokens + i);
        if (static_cast<uint32_t>(next_pos) >= n_ctx) {
            break;
        }

        llama_token new_token = llama_sampler_sample(smpl, g_ctx, -1);
        llama_sampler_accept(smpl, new_token);

        if (llama_token_is_eog(g_vocab, new_token)) {
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

        bool json_complete = false;
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
    llama_kv_cache_clear(g_ctx);

    return env->NewStringUTF(output_str.c_str());
}

} // extern "C"
