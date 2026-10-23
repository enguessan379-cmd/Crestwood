package com.rockstargames.oswrapper;

import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * Descobre, sem abrir o jogo, quais formatos de textura do texdb (dxt / pvr / etc) o motor do
 * GTA:SA pode escolher neste celular.
 *
 * O motor decide pelos mesmos sinais que lemos aqui (extensões OpenGL ES):
 *   - GL_EXT_texture_compression_s3tc / dxt1  -> texdb/*.dxt.*
 *   - GL_IMG_texture_compression_pvrtc        -> texdb/*.pvr.*
 *   - nenhuma das duas (Mali sem S3TC, etc.)  -> texdb/*.etc.*
 *
 * Se o celular anuncia mais de uma, devolvemos todas (é o caso seguro: o pacote precisa ter
 * qualquer uma que o motor venha a escolher).
 */
public final class TextureFormatProbe {
    private static final String TAG = "TextureFormatProbe";

    public static final class Result {
        /** GL_RENDERER (ex.: "Mali-G52 MC2"), só para diagnóstico. */
        public final String renderer;
        /** Subconjunto não vazio de "dxt", "pvr", "etc". */
        public final String[] formats;

        Result(String renderer, String[] formats) {
            this.renderer = renderer == null ? "" : renderer;
            this.formats = formats;
        }
    }

    private static Result cached;
    private static boolean attempted;

    private TextureFormatProbe() { }

    /** Resultado em cache; null se não foi possível consultar a GPU (nesse caso não se deve bloquear nada). */
    public static synchronized Result get() {
        if (attempted) return cached;
        attempted = true;
        try {
            cached = probe();
        } catch (Throwable error) {
            Log.w(TAG, "Não foi possível consultar a GPU: " + error);
            cached = null;
        }
        if (cached != null) {
            Log.i(TAG, "GPU=" + cached.renderer + " formatos texdb=" + java.util.Arrays.toString(cached.formats));
        }
        return cached;
    }

    private static Result probe() {
        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (display == null || display == EGL14.EGL_NO_DISPLAY) return null;
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) return null;

        EGLContext context = EGL14.EGL_NO_CONTEXT;
        EGLSurface surface = EGL14.EGL_NO_SURFACE;
        try {
            int[] configAttribs = {
                    EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                    EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                    EGL14.EGL_RED_SIZE, 8,
                    EGL14.EGL_GREEN_SIZE, 8,
                    EGL14.EGL_BLUE_SIZE, 8,
                    EGL14.EGL_NONE
            };
            EGLConfig[] configs = new EGLConfig[1];
            int[] count = new int[1];
            if (!EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, count, 0) || count[0] < 1) return null;

            int[] contextAttribs = { EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE };
            context = EGL14.eglCreateContext(display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) return null;

            int[] surfaceAttribs = { EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE };
            surface = EGL14.eglCreatePbufferSurface(display, configs[0], surfaceAttribs, 0);
            if (surface == null || surface == EGL14.EGL_NO_SURFACE) return null;

            if (!EGL14.eglMakeCurrent(display, surface, surface, context)) return null;

            String extensions = GLES20.glGetString(GLES20.GL_EXTENSIONS);
            String renderer = GLES20.glGetString(GLES20.GL_RENDERER);
            if (extensions == null || extensions.isEmpty()) return null;

            boolean dxt = extensions.contains("GL_EXT_texture_compression_s3tc")
                    || extensions.contains("GL_EXT_texture_compression_dxt1");
            boolean pvr = extensions.contains("GL_IMG_texture_compression_pvrtc");

            List<String> formats = new ArrayList<>();
            if (dxt) formats.add("dxt");
            if (pvr) formats.add("pvr");
            if (formats.isEmpty()) formats.add("etc");
            return new Result(renderer, formats.toArray(new String[0]));
        } finally {
            // Não chamamos eglTerminate: o display é compartilhado com o resto do processo.
            EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
            if (surface != null && surface != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, surface);
            if (context != null && context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
        }
    }
}
