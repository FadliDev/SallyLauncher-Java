cmake_minimum_required(VERSION 3.18)
project(feathergl C CXX)

# ---------------------------------------------------------------
# FeatherGL: slimmed Android-only fork of NG-GL4ES (Krypton Wrapper)
# Goal: small .so, low call overhead, GL 2.1 compat (MC 1.16) up to
# GL 3.2 core (MC 1.17 .. 26.x) via GLES 3.x.
# ---------------------------------------------------------------

set(CMAKE_C_STANDARD 11)
set(CMAKE_C_STANDARD_REQUIRED ON)
set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)
set(CMAKE_POSITION_INDEPENDENT_CODE ON)

if(NOT CMAKE_BUILD_TYPE)
    set(CMAKE_BUILD_TYPE Release)
endif()

# ---- third-party: glslang + SPIRV-Cross (fetched by feathergl/setup_deps.sh)
if(NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty/glslang/CMakeLists.txt" OR
   NOT EXISTS "${CMAKE_CURRENT_SOURCE_DIR}/3rdparty/SPIRV-Cross/CMakeLists.txt")
    message(FATAL_ERROR "3rdparty/glslang or 3rdparty/SPIRV-Cross missing. Run feathergl/setup_deps.sh first.")
endif()

set(ENABLE_RTTI                ON  CACHE BOOL "" FORCE)
set(ENABLE_HLSL                OFF CACHE BOOL "" FORCE)
set(BUILD_EXTERNAL             OFF CACHE BOOL "" FORCE)
set(ENABLE_GLSLANG_BINARIES    OFF CACHE BOOL "" FORCE)
set(ENABLE_OPT                 OFF CACHE BOOL "" FORCE)
set(ENABLE_SPVREMAPPER         OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_C_API   ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_GLSL    ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_REFLECT OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_HLSL    OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_MSL     OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_CPP     OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_TESTS   OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_CLI            OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_STATIC         ON  CACHE BOOL "" FORCE)
set(SPIRV_CROSS_SHARED         OFF CACHE BOOL "" FORCE)
set(SPIRV_CROSS_ENABLE_UTIL    OFF CACHE BOOL "" FORCE)

# Size-oriented flags for the (big) third-party code; -O2 keeps speed.
set(CMAKE_C_FLAGS_RELEASE   "-O2 -DNDEBUG -g0")
set(CMAKE_CXX_FLAGS_RELEASE "-O2 -DNDEBUG -g0")

add_subdirectory(3rdparty/glslang EXCLUDE_FROM_ALL)
add_subdirectory(3rdparty/SPIRV-Cross EXCLUDE_FROM_ALL)

# ---- our sources
set(C_SRC_FILES
    src/gl/arbconverter.c
    src/gl/arbgenerator.c
    src/gl/arbhelper.c
    src/gl/arbparser.c
    src/gl/array.c
    src/gl/blend.c
    src/gl/blit.c
    src/gl/buffers.c
    src/gl/build_info.c
    src/gl/cJSON.c
    src/gl/config.c
    src/gl/debug.c
    src/gl/decompress.c
    src/gl/depth.c
    src/gl/directstate.c
    src/gl/drawing.c
    src/gl/enable.c
    src/gl/envvars.c
    src/gl/eval.c
    src/gl/face.c
    src/gl/fog.c
    src/gl/fpe.c
    src/gl/fpe_cache.c
    src/gl/fpe_shader.c
    src/gl/framebuffers.c
    src/gl/gl4es.c
    src/gl/gl_lookup.c
    src/gl/glstate.c
    src/gl/hint.c
    src/gl/init.c
    src/gl/light.c
    src/gl/line.c
    src/gl/list.c
    src/gl/listdraw.c
    src/gl/listrl.c
    src/gl/loader.c
    src/gl/logs.c
    src/gl/math/matheval.c
    src/gl/matrix.c
    src/gl/matvec.c
    src/gl/oldprogram.c
    src/gl/pixel.c
    src/gl/planes.c
    src/gl/pointsprite.c
    src/gl/preproc.c
    src/gl/program.c
    src/gl/queries.c
    src/gl/raster.c
    src/gl/render.c
    src/gl/samplers.c
    src/gl/shader.c
    src/gl/shader_hacks.c
    src/gl/shaderconv.c
    src/gl/stack.c
    src/gl/stencil.c
    src/gl/string_utils.c
    src/gl/stubs.c
    src/gl/texenv.c
    src/gl/texgen.c
    src/gl/texture.c
    src/gl/texture_3d.c
    src/gl/texture_compressed.c
    src/gl/texture_params.c
    src/gl/texture_read.c
    src/gl/uniform.c
    src/gl/vertexattrib.c
    src/gl/vgpu/shaderconv.c
    src/gl/wrap/gl4eswraps.c
    src/gl/wrap/gles.c
    src/gl/wrap/glstub.c
    src/glx/gbm.c
    src/glx/hardext.c
    src/glx/lookup.c
    src/glx/streaming.c
)
set(CPP_SRC_FILES
    src/gl/glsl/glsl_for_es.cpp
    src/gl/getter.cpp
    src/gl/wrap/glesnative.cpp
)

add_library(feathergl SHARED ${C_SRC_FILES} ${CPP_SRC_FILES})

target_include_directories(feathergl PRIVATE
    ${CMAKE_CURRENT_SOURCE_DIR}/include
    ${CMAKE_CURRENT_SOURCE_DIR}/src
    ${CMAKE_CURRENT_SOURCE_DIR}/src/gl
    ${CMAKE_CURRENT_SOURCE_DIR}/src/glx
)

target_compile_definitions(feathergl PRIVATE NO_GBM DEFAULT_ES=3 NOX11 ANDROID)

# Per-target only: do NOT put -fno-exceptions in global flags (SPIRV-Cross C API needs try/catch).
target_compile_options(feathergl PRIVATE
    -fvisibility=hidden
    -ffunction-sections -fdata-sections
    -fno-semantic-interposition
    -fno-stack-protector
    -fomit-frame-pointer
    $<$<COMPILE_LANGUAGE:CXX>:-fno-exceptions>
)

# Thin LTO: most of the win of full LTO, much lower link RAM (matters on-device/Termux).
target_compile_options(feathergl PRIVATE -flto=thin)
target_link_options(feathergl PRIVATE
    -flto=thin
    -Wl,--gc-sections
    -Wl,--icf=safe
    -Wl,--exclude-libs,ALL
    -Wl,-z,max-page-size=16384
    -Wl,--strip-all
)

target_link_libraries(feathergl PRIVATE glslang SPIRV spirv-cross-c android log)
