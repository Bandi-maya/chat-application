/*
 * Applies the selected color matrix and the face-lens overlay to camera frames
 * for BOTH preview and the recorded video (CameraX CameraEffect pipeline).
 *
 * GL threading/surface lifecycle adapted from the Apache-2.0
 * ToneMappingSurfaceProcessor sample in androidx camera
 * integration-tests/viewtestapp:
 *   https://github.com/androidx/androidx/blob/androidx-main/camera/integration-tests/viewtestapp/src/main/java/androidx/camera/integration/view/ToneMappingSurfaceProcessor.kt
 * (Licensed under the Apache License, Version 2.0.)
 *
 * Unlike OverlayEffect (overlay-only canvas), the color matrix is applied to
 * the camera frame itself in the fragment shader, so the recorded file gets
 * the same treatment as the preview instead of a raw feed behind a filtered
 * preview. The lens overlay keeps using the same LensAnchorPainter draw as the
 * photo bake path, composited here as a second texture.
 */
package com.example.chat.ui.camera

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.graphics.SurfaceTexture.OnFrameAvailableListener
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import androidx.camera.core.DynamicRange
import androidx.camera.core.SurfaceOutput
import androidx.camera.core.SurfaceProcessor
import androidx.camera.core.SurfaceRequest
import androidx.camera.core.impl.utils.executor.CameraXExecutors.newHandlerExecutor
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.Executor

class ColorLensSurfaceProcessor(
    private val lensProvider: () -> LensDefinition?,
    private val posesProvider: () -> List<FacePose>,
    private val viewportProvider: () -> android.util.Size,
    private val matrixProvider: () -> FloatArray?,
    private val lensTargetMask: Int,
    private val colorTargetMask: Int,
) : SurfaceProcessor, OnFrameAvailableListener {

    companion object {
        private const val TAG = "ColorLensSurfaceProcessor"
        private const val GL_THREAD_NAME = "ColorLensSurfaceProcessor"

        private const val VERTEX_SHADER = """
uniform mat4 uTexMatrix;
attribute vec4 aPosition;
attribute vec4 aTexCoord;
varying vec2 vTexCoord;
void main() {
  gl_Position = aPosition;
  vTexCoord = (uTexMatrix * aTexCoord).xy;
}
"""

        private const val FRAGMENT_SHADER = """
#extension GL_OES_EGL_image_external : require
precision mediump float;
uniform samplerExternalOES uFrameSampler;
uniform sampler2D uOverlaySampler;
uniform mat4 uColorMatrix;
uniform vec4 uColorOffset;
uniform float uOverlayEnabled;
varying vec2 vTexCoord;
void main() {
  vec4 c = texture2D(uFrameSampler, vTexCoord);
  vec4 tinted = uColorMatrix * c + uColorOffset;
  tinted = vec4(clamp(tinted.rgb, 0.0, 1.0), c.a);
  vec4 ov = texture2D(uOverlaySampler, vTexCoord);
  gl_FragColor = mix(tinted, vec4(ov.rgb, c.a), ov.a * uOverlayEnabled);
}
"""

        private val QUAD_COORDS = floatArrayOf(
            -1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f,
        )
        private val QUAD_TEX = floatArrayOf(
            0f, 0f, 1f, 0f, 0f, 1f, 1f, 1f,
        )
    }

    private val glThread = HandlerThread(GL_THREAD_NAME)
    private val glHandler: Handler
    private val glExecutor: Executor

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglConfig: EGLConfig? = null
    private var program = 0
    private var frameTextureId = 0
    private var overlayTextureId = 0
    private var overlayBitmap: Bitmap? = null
    private var overlayCanvasSize: android.util.Size? = null

    private val outputSurfaces: MutableMap<SurfaceOutput, EGLSurface> = mutableMapOf()
    private val outputTargets: MutableMap<SurfaceOutput, Int> = mutableMapOf()
    private val textureTransform = FloatArray(16)
    private var isReleased = false
    private val releasedSurfaceTextures = mutableListOf<SurfaceTexture>()

    init {
        glThread.start()
        glHandler = Handler(glThread.looper)
        glExecutor = newHandlerExecutor(glHandler)
        glExecutor.execute { initGl() }
    }

    fun getGlExecutor(): Executor = glExecutor

    private fun checkGlThread() {
        check(GL_THREAD_NAME == Thread.currentThread().name) { "Not on GL thread" }
    }

    private fun initGl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "eglGetDisplay failed" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "eglInitialize failed" }
        val attribs = intArrayOf(
            EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT or EGL14.EGL_PBUFFER_BIT,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val num = IntArray(1)
        check(EGL14.eglChooseConfig(eglDisplay, attribs, 0, configs, 0, 1, num, 0)) { "eglChooseConfig failed" }
        eglConfig = configs[0]
        eglContext = EGL14.eglCreateContext(
            eglDisplay, eglConfig, EGL14.EGL_NO_CONTEXT,
            intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE), 0,
        )
        check(eglContext != EGL14.EGL_NO_CONTEXT) { "eglCreateContext failed" }

        val tex = IntArray(2)
        GLES20.glGenTextures(2, tex, 0)
        frameTextureId = tex[0]
        overlayTextureId = tex[1]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTextureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
    }

    private fun createProgram(vs: String, fs: String): Int {
        fun compile(type: Int, src: String): Int {
            val id = GLES20.glCreateShader(type)
            GLES20.glShaderSource(id, src)
            GLES20.glCompileShader(id)
            val ok = IntArray(1)
            GLES20.glGetShaderiv(id, GLES20.GL_COMPILE_STATUS, ok, 0)
            check(ok[0] != 0) { "Shader compile failed: " + GLES20.glGetShaderInfoLog(id) }
            return id
        }
        val v = compile(GLES20.GL_VERTEX_SHADER, vs)
        val f = compile(GLES20.GL_FRAGMENT_SHADER, fs)
        val p = GLES20.glCreateProgram()
        GLES20.glAttachShader(p, v)
        GLES20.glAttachShader(p, f)
        GLES20.glLinkProgram(p)
        val ok = IntArray(1)
        GLES20.glGetProgramiv(p, GLES20.GL_LINK_STATUS, ok, 0)
        check(ok[0] != 0) { "Program link failed: " + GLES20.glGetProgramInfoLog(p) }
        return p
    }

    override fun onInputSurface(surfaceRequest: SurfaceRequest) {
        checkGlThread()
        if (isReleased) {
            surfaceRequest.willNotProvideSurface()
            return
        }
        val surfaceTexture = SurfaceTexture(frameTextureId)
        surfaceTexture.setDefaultBufferSize(
            surfaceRequest.resolution.width,
            surfaceRequest.resolution.height,
        )
        val surface = Surface(surfaceTexture)
        surfaceRequest.provideSurface(surface, glExecutor) { result ->
            Log.d(TAG, "onInputSurface#resultListener: $result")
            surfaceTexture.setOnFrameAvailableListener(null)
            surfaceTexture.release()
            releasedSurfaceTextures.add(surfaceTexture)
            surface.release()
        }
        surfaceTexture.setOnFrameAvailableListener(this, glHandler)
    }

    override fun onOutputSurface(surfaceOutput: SurfaceOutput) {
        checkGlThread()
        if (isReleased) {
            surfaceOutput.close()
            return
        }
        val surface = surfaceOutput.getSurface(glExecutor) {
            surfaceOutput.close()
            outputSurfaces.remove(surfaceOutput)?.let { removed ->
                EGL14.eglDestroySurface(eglDisplay, removed)
            }
            outputTargets.remove(surfaceOutput)
        }
        val eglSurface = EGL14.eglCreateWindowSurface(
            eglDisplay, eglConfig, surface,
            intArrayOf(EGL14.EGL_NONE), 0,
        )
        if (eglSurface != null && eglSurface != EGL14.EGL_NO_SURFACE) {
            outputSurfaces[surfaceOutput] = eglSurface
            outputTargets[surfaceOutput] = surfaceOutput.targets
        }
    }

    override fun onFrameAvailable(surfaceTexture: SurfaceTexture) {
        checkGlThread()
        if (isReleased) return
        if (releasedSurfaceTextures.contains(surfaceTexture)) return
        surfaceTexture.updateTexImage()
        surfaceTexture.getTransformMatrix(textureTransform)
        val lens = lensProvider()
        val poses = posesProvider()
        val matrix = matrixProvider()
        for ((output, eglSurface) in outputSurfaces) {
            val targets = outputTargets[output] ?: continue
            EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)
            val size = output.size
            GLES20.glViewport(0, 0, size.width, size.height)
            GLES20.glUseProgram(program)

            // Lens overlay, same draw + cover-crop mapping as the photo bake path.
            var overlayEnabled = 0f
            if (lens != null && !lens.isNoLens && poses.isNotEmpty() && (targets and lensTargetMask) != 0) {
                val pv = viewportProvider()
                if (pv.width > 0 && pv.height > 0 && size.width > 0 && size.height > 0) {
                    val bmp = overlayBitmapFor(size)
                    val canvas = android.graphics.Canvas(bmp)
                    canvas.drawColor(android.graphics.Color.TRANSPARENT, android.graphics.PorterDuff.Mode.CLEAR)
                    val w = size.width.toFloat(); val h = size.height.toFloat()
                    val scale = maxOf(pv.width / w, pv.height / h)
                    val cropLeft = (pv.width - w * scale) / 2f
                    val cropTop = (pv.height - h * scale) / 2f
                    fun toFrame(p0: LensOffset) = LensOffset((p0.dx - cropLeft) / scale, (p0.dy - cropTop) / scale)
                    val mapped = poses.map { pose ->
                        FacePose(
                            pose.trackingId,
                            LensRect.fromPoints(toFrame(LensOffset(pose.headBounds.left, pose.headBounds.top)), toFrame(LensOffset(pose.headBounds.right, pose.headBounds.bottom))),
                            toFrame(pose.leftEye), toFrame(pose.rightEye), toFrame(pose.noseBase), toFrame(pose.mouthCenter),
                            pose.rollRadians, pose.yawRadians, pose.pitchRadians, pose.confidence,
                        )
                    }
                    LensAnchorPainter.paint(androidx.compose.ui.graphics.Canvas(canvas), lens, mapped, 1.0f)
                    GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
                    GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bmp, 0)
                    overlayEnabled = 1f
                }
            }

            val locPos = GLES20.glGetAttribLocation(program, "aPosition")
            val locTex = GLES20.glGetAttribLocation(program, "aTexCoord")
            val locMatrix = GLES20.glGetUniformLocation(program, "uTexMatrix")
            val locFrame = GLES20.glGetUniformLocation(program, "uFrameSampler")
            val locOverlay = GLES20.glGetUniformLocation(program, "uOverlaySampler")
            val locColor = GLES20.glGetUniformLocation(program, "uColorMatrix")
            val locOffset = GLES20.glGetUniformLocation(program, "uColorOffset")
            val locEnabled = GLES20.glGetUniformLocation(program, "uOverlayEnabled")

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, frameTextureId)
            GLES20.glUniform1i(locFrame, 0)
            GLES20.glActiveTexture(GLES20.GL_TEXTURE1)
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, overlayTextureId)
            GLES20.glUniform1i(locOverlay, 1)

            // Per-output transform (rotation/crop/mirroring per SurfaceOutput contract).
            val updated = FloatArray(16)
            output.updateTransformMatrix(updated, textureTransform)
            GLES20.glUniformMatrix4fv(locMatrix, 1, false, updated, 0)

            val m = matrix
            val colorOn = m != null && m.size >= 20 && (targets and colorTargetMask) != 0
            val colMajor = FloatArray(16)
            val offset = FloatArray(4)
            if (colorOn && m != null) {
                for (r in 0..3) for (c in 0..3) colMajor[c * 4 + r] = m[r * 5 + c]
                for (r in 0..3) offset[r] = m[r * 5 + 4]
            } else {
                for (i in 0..15) colMajor[i] = if (i % 5 == 0) 1f else 0f
            }
            GLES20.glUniformMatrix4fv(locColor, 1, false, colMajor, 0)
            GLES20.glUniform4fv(locOffset, 1, offset, 0)
            GLES20.glUniform1f(locEnabled, overlayEnabled)

            val posBuf = floatBuffer(QUAD_COORDS)
            val texBuf = floatBuffer(QUAD_TEX)
            GLES20.glEnableVertexAttribArray(locPos)
            GLES20.glVertexAttribPointer(locPos, 2, GLES20.GL_FLOAT, false, 0, posBuf)
            GLES20.glEnableVertexAttribArray(locTex)
            GLES20.glVertexAttribPointer(locTex, 2, GLES20.GL_FLOAT, false, 0, texBuf)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
            GLES20.glDisableVertexAttribArray(locPos)
            GLES20.glDisableVertexAttribArray(locTex)

            EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, surfaceTexture.timestamp)
            if (!EGL14.eglSwapBuffers(eglDisplay, eglSurface)) {
                Log.w(TAG, "Swap failed: 0x" + Integer.toHexString(EGL14.eglGetError()))
                outputSurfaces.remove(output)?.let { EGL14.eglDestroySurface(eglDisplay, it) }
                outputTargets.remove(output)
            }
        }
    }

    private fun overlayBitmapFor(size: android.util.Size): Bitmap {
        val cur = overlayBitmap
        if (cur != null && overlayCanvasSize == size && cur.width == size.width && cur.height == size.height) return cur
        cur?.recycle()
        val bmp = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        overlayBitmap = bmp
        overlayCanvasSize = size
        return bmp
    }

    private fun floatBuffer(values: FloatArray): FloatBuffer {
        val b = ByteBuffer.allocateDirect(values.size * 4).order(ByteOrder.nativeOrder()).asFloatBuffer()
        b.put(values).position(0)
        return b
    }

    fun release() {
        glExecutor.execute {
            checkGlThread()
            if (!isReleased) {
                for ((output, surface) in outputSurfaces) {
                    output.close()
                    EGL14.eglDestroySurface(eglDisplay, surface)
                }
                outputSurfaces.clear()
                outputTargets.clear()
                overlayBitmap?.recycle()
                overlayBitmap = null
                if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
                    EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
                    if (eglContext != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(eglDisplay, eglContext)
                    EGL14.eglTerminate(eglDisplay)
                }
                eglDisplay = EGL14.EGL_NO_DISPLAY
                eglContext = EGL14.EGL_NO_CONTEXT
                glThread.quitSafely()
                releasedSurfaceTextures.clear()
                isReleased = true
            }
        }
    }
}
