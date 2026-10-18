package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Built-in FeatherGL (slim NG-GL4ES / Krypton Wrapper fork). Library: libfeathergl.so in jniLibs.
 * Env values mirror what NGG uses for Minecraft 1.16 .. 1.21+ (GL 3.1 emulated on GLES 3).
 */
class FeatherGLRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3"

    override fun getUniqueIdentifier(): String = "6f1c2b7e-3a4d-4c59-9e0b-7d2a5f8c1e44"

    override fun getRendererName(): String = "FeatherGL (1.16 - 26.x)"

    override fun getRendererEnv(): Lazy<Map<String, String>> = lazy {
        mapOf(
            "LIBGL_USE_MC_COLOR" to "1",
            "LIBGL_GL" to "31",
            "LIBGL_ES" to "3",
            "LIBGL_NORMALIZE" to "1",
            "LIBGL_NOERROR" to "1",
            "LIBGL_NOBANNER" to "1"
        )
    }

    override fun getDlopenLibrary(): Lazy<List<String>> = lazy { emptyList() }

    override fun getRendererLibrary(): String = "libfeathergl.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
