package com.movtery.zalithlauncher.renderer.renderers

import com.movtery.zalithlauncher.renderer.RendererInterface

/**
 * Built-in SallyGL (slim NG-GL4ES / Krypton Wrapper fork). Library: libsallygl.so in jniLibs.
 * Env values mirror what NGG uses for Minecraft 1.16 .. 1.21+ (GL 3.1 emulated on GLES 3).
 */
class SallyGLRenderer : RendererInterface {
    override fun getRendererId(): String = "opengles3"

    override fun getUniqueIdentifier(): String = "6f1c2b7e-3a4d-4c59-9e0b-7d2a5f8c1e44"

    override fun getRendererName(): String = "SallyGL (1.16 - 26.x)"

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

    override fun getRendererLibrary(): String = "libsallygl.so"

    override fun getRendererEGL(): String = "libEGL.so"
}
