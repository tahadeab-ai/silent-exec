cmake_minimum_required(VERSION 3.22.1)
project(silent_exec_llama C CXX)

set(CMAKE_CXX_STANDARD 17)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

# Configure llama.cpp build options for fast, lightweight arm64-v8a CPU inference
set(GGML_OPENMP OFF CACHE BOOL "Disable OpenMP" FORCE)
set(GGML_CUDA OFF CACHE BOOL "Disable CUDA" FORCE)
set(GGML_VULKAN OFF CACHE BOOL "Disable Vulkan" FORCE)
set(GGML_FATAL_WARNINGS OFF CACHE BOOL "Disable fatal warnings" FORCE)
set(LLAMA_FATAL_WARNINGS OFF CACHE BOOL "Disable fatal warnings" FORCE)
set(LLAMA_BUILD_TESTS OFF CACHE BOOL "Disable tests" FORCE)
set(LLAMA_BUILD_EXAMPLES OFF CACHE BOOL "Disable examples" FORCE)
set(LLAMA_BUILD_SERVER OFF CACHE BOOL "Disable server" FORCE)

include(FetchContent)
FetchContent_Declare(
    llama_cpp
    GIT_REPOSITORY https://github.com/ggml-org/llama.cpp.git
    GIT_TAG        b4120
    GIT_SHALLOW    TRUE
)
FetchContent_MakeAvailable(llama_cpp)

add_library(llama_jni SHARED
    llama_jni.cpp
)

target_compile_options(llama_jni PRIVATE -Wno-deprecated-declarations)

target_include_directories(llama_jni PRIVATE
    ${llama_cpp_SOURCE_DIR}/include
    ${llama_cpp_SOURCE_DIR}/ggml/include
)

target_link_libraries(llama_jni PRIVATE
    llama
    ggml
    log
)
