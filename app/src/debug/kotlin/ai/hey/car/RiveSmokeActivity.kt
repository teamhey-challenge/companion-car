package ai.hey.car

import android.app.Activity
import android.opengl.EGL14
import android.opengl.GLES20
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.Choreographer
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import app.rive.runtime.kotlin.RiveAnimationView
import app.rive.runtime.kotlin.controllers.RiveFileController
import app.rive.runtime.kotlin.core.PlayableInstance
import app.rive.runtime.kotlin.core.Rive
import app.rive.runtime.kotlin.core.RendererType
import java.util.concurrent.atomic.AtomicInteger

private const val TAG = "rive"

/**
 * Rive GLES smoke test (branch spike/audio-go-no-go, debug builds only, never merged).
 * Assets (res/raw/rive_<name>.riv) are unmodified copies of app/src/main/res/raw/<file>.riv from https://github.com/rive-app/rive-android
 * (MIT License, Copyright (c) 2020-2024 Rive): car=off_road_car_blog, marty=marty (state machine "SM", default).
 *   am start -n ai.hey.car/.RiveSmokeActivity [--es renderer canvas] [--es asset marty|car] [--es sm "State Machine 1"] [--es anim Idle]
 * Logs under tag `rive`: GL strings from an EGL context, renderer type actually used, artboard animation/state machine names,
 * exceptions, frame counts over 10 s.
 */
class RiveSmokeActivity : Activity() {
    private val advances = AtomicInteger()
    private var choreoFrames = 0
    private var live = true
    private lateinit var label: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val force = intent.getStringExtra("renderer")?.lowercase()
        val root = FrameLayout(this)
        label = TextView(this).apply { textSize = 22f; setTextColor(0xFF000000.toInt()); setBackgroundColor(0xCCFFFFFF.toInt()) }
        try {
            logGl()
            val requested = if (force == "canvas") RendererType.Canvas else Rive.defaultRendererType
            Log.i(TAG, "requested=$requested default=${Rive.defaultRendererType} force=$force")
            Rive.init(this, requested)
            val view = RiveAnimationView(this, null)
            root.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            view.registerListener(object : RiveFileController.Listener {
                override fun notifyPlay(playableInstance: PlayableInstance) { Log.i(TAG, "play $playableInstance") }
                override fun notifyPause(playableInstance: PlayableInstance) {}
                override fun notifyStop(playableInstance: PlayableInstance) {}
                override fun notifyLoop(playableInstance: PlayableInstance) {}
                override fun notifyStateChanged(stateMachineName: String, stateName: String) { Log.i(TAG, "state $stateMachineName -> $stateName") }
                override fun notifyAdvance(elapsed: Float) { advances.incrementAndGet() }
            })
            val asset = intent.getStringExtra("asset") ?: "marty"
            val resId = resources.getIdentifier("rive_$asset", "raw", packageName)
            val sm = intent.getStringExtra("sm") ?: if (intent.hasExtra("anim")) null else "SM"
            val anim = intent.getStringExtra("anim")
            Log.i(TAG, "asset=$asset res=$resId stateMachine=$sm animation=$anim")
            view.setRiveResource(resId, stateMachineName = sm, animationName = anim, autoplay = true)
            // artboardRenderer is null until the view is attached to a window, so it is read after layout.
            label.text = "rive requested=$requested gl=$glLine"
            val view0 = view
            val h = Handler(Looper.getMainLooper())
            val t0 = SystemClock.elapsedRealtime()
            val cb = object : Choreographer.FrameCallback {
                override fun doFrame(frameTimeNanos: Long) { choreoFrames++; if (live) Choreographer.getInstance().postFrameCallback(this) }
            }
            Choreographer.getInstance().postFrameCallback(cb)
            h.postDelayed({
                val r = view0.artboardRenderer
                Log.i(TAG, "renderer used=${r?.type} class=${r?.javaClass?.simpleName} attached=${r?.isAttached}")
                label.text = "rive renderer=${r?.type} asset=$asset gl=$glLine"
                Log.i(TAG, "artboard=${view0.controller.activeArtboard?.name} animations=${view0.controller.activeArtboard?.animationNames} stateMachines=${view0.controller.activeArtboard?.stateMachineNames}")
            }, 1_000)
            h.postDelayed({
                val dt = (SystemClock.elapsedRealtime() - t0) / 1000f
                val r = view0.artboardRenderer
                Log.i(TAG, "RESULT type=${r?.type} advances10s=${advances.get()} (${advances.get() / dt}/s) " +
                    "choreographer10s=$choreoFrames (${choreoFrames / dt}/s) rendererAvgFps=${r?.averageFps} playing=${view0.isPlaying} dt=$dt")
            }, 10_000)
        } catch (t: Throwable) {
            Log.e(TAG, "EXCEPTION ${t.javaClass.name}: ${t.message}", t)
            label.text = "EXCEPTION ${t.javaClass.simpleName}: ${t.message}"
        }
        root.addView(label, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(root)
    }

    override fun onDestroy() { live = false; super.onDestroy() }

    private var glLine = "?"

    /** Create a throwaway pbuffer EGL context (ES3, falling back to ES2) and log GL_VERSION / GL_RENDERER. */
    private fun logGl() {
        val dpy = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        val v = IntArray(2)
        EGL14.eglInitialize(dpy, v, 0, v, 1)
        Log.i(TAG, "EGL ${EGL14.eglQueryString(dpy, EGL14.EGL_VERSION)} vendor=${EGL14.eglQueryString(dpy, EGL14.EGL_VENDOR)}")
        for (es in intArrayOf(3, 2)) {
            val renderable = if (es == 3) 0x40 else EGL14.EGL_OPENGL_ES2_BIT // EGL_OPENGL_ES3_BIT_KHR
            val attrs = intArrayOf(EGL14.EGL_RENDERABLE_TYPE, renderable, EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT, EGL14.EGL_NONE)
            val cfg = arrayOfNulls<android.opengl.EGLConfig>(1)
            val n = IntArray(1)
            if (!EGL14.eglChooseConfig(dpy, attrs, 0, cfg, 0, 1, n, 0) || n[0] == 0) { Log.w(TAG, "no EGL config for ES$es"); continue }
            val ctx = EGL14.eglCreateContext(dpy, cfg[0], EGL14.EGL_NO_CONTEXT, intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, es, EGL14.EGL_NONE), 0)
            if (ctx == EGL14.EGL_NO_CONTEXT) { Log.w(TAG, "eglCreateContext ES$es failed 0x${Integer.toHexString(EGL14.eglGetError())}"); continue }
            val surf = EGL14.eglCreatePbufferSurface(dpy, cfg[0], intArrayOf(EGL14.EGL_WIDTH, 16, EGL14.EGL_HEIGHT, 16, EGL14.EGL_NONE), 0)
            EGL14.eglMakeCurrent(dpy, surf, surf, ctx)
            glLine = "ES$es ctx: GL_VERSION=${GLES20.glGetString(GLES20.GL_VERSION)} | GL_RENDERER=${GLES20.glGetString(GLES20.GL_RENDERER)} | GL_VENDOR=${GLES20.glGetString(GLES20.GL_VENDOR)}"
            Log.i(TAG, glLine)
            EGL14.eglMakeCurrent(dpy, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(dpy, surf); EGL14.eglDestroyContext(dpy, ctx)
            if (es == 3) return
        }
    }
}
