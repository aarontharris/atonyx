package com.ath.atonyx

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.ComponentCallbacks2
import android.content.res.Configuration
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.view.SurfaceView
import com.ath.atonyx.SurfaceManager.ShutdownReason
import java.lang.ref.WeakReference

/**
 * # How to use it ?
 * ## Inside an Activity ?
 *  - You will need to:
 *      - call doInit( surfaceView ) from onCreate
 *      - override onEnabled() { /* ready to use until onDisabled */ }
 *      - override onDisabled() { /* system is paused until onEnabled */ }
 *
 * ## Inside a Fragment ?
 *  Same as Activity HOWEVER, if your fragment is to be paused/resumed/detached/destroyed
 *  independently of it's activity, that is not automagically supported.
 *  Example:
 *      If you have a Fragment ViewPager and the fragment is paused, but the activity is not.
 *      In this case, the system will not know it is paused.
 *  Solution:
 *      You can forcefully call doShutdown(), create a new instance and doInit(surface)
 *
 *
 * # How does it work
 * The system subscribes to lifecycle callbacks from the activity obtained from the SurfaceView.
 * It uses these callbacks to manage its own lifecycle.
 * The system must be triggered via doInit(surfaceView)
 * The system is not enabled until after the surfaceView completes it's onLayout
 * at which time, the system does a ui.post -> onEnable() assuming everything is valid.
 * Valid requires a valid Activity and SurfaceView (they are held weakly)
 * And the SurfaceView must have a height & width > 0
 * And the Activity must be in "Resume"
 * If at any point in time the Activity or SurfaceView are lost => onShutdown( reason )
 * If at any point in time the Activity becomes paused => onDisable()
 * If at any point in time the Activity becomes re-resumed => onEnable()
 * If at any point in time the Activity becomes Destroyed => onShutdown( reason )
 */

interface SurfaceStarted {
    fun shutdown() = shutdown(ShutdownReason.MANUAL_SHUTDOWN, "")
    fun shutdown(reason: ShutdownReason, msg: String)
}

interface SurfaceEnabled : SurfaceStarted {
    val activity: Activity
    val surface: SurfaceView
    fun disable()
}

interface SurfaceShutdown {
    val shutdownReason: ShutdownReason //= ShutdownReason.UNKNOWN; private set
    val shutdownMessage: String // = ""; private set
}

interface SurfaceManager : SurfaceStarted, SurfaceShutdown {
    companion object {
        fun builder() = SurfaceManagerBuilder()
    }

    enum class ShutdownReason(val expected: Boolean) {
        ACTIVITY_DESTROY(true),
        ACTIVITY_LOST(false),
        SURFACE_LOST(false),
        MANUAL_SHUTDOWN(true),
        UNKNOWN(false)
    }

    class SurfaceManagerBuilder {
        private var workStartup: ((SurfaceManager, Bundle?) -> Unit)? = null
        private var workEnabled: ((SurfaceEnabled) -> Unit)? = null
        private var workDisabled: ((SurfaceManager) -> Unit)? = null
        private var workShutdown: ((SurfaceShutdown) -> Unit)? = null
        private var workSave: ((SurfaceManager, Bundle?) -> Unit)? = null
        fun onStartup(work: (SurfaceManager, Bundle?) -> Unit) = this.also { workStartup = work }
        fun onEnabled(work: (SurfaceEnabled) -> Unit) = this.also { workEnabled = work }
        fun onDisabled(work: (SurfaceManager) -> Unit) = this.also { workDisabled = work }
        fun onShutdown(work: (SurfaceShutdown) -> Unit) = this.also { workShutdown = work }
        fun onSave(work: (SurfaceManager, bundle: Bundle?) -> Unit) = this.also { workSave = work }
        fun build(): SurfaceManager {
            return object : __SurfaceManager() {
                override fun onStartup(bundle: Bundle?) {
                    super.onStartup(bundle)
                    workStartup?.invoke(this, bundle)
                }

                override fun onEnabled() {
                    super.onEnabled()
                    workEnabled?.invoke(this)
                }

                override fun onDisabled() {
                    super.onDisabled()
                    workDisabled?.invoke(this)
                }

                override fun onShutdown() {
                    super.onEnabled()
                    workShutdown?.invoke(this)
                }

                override fun onSave(bundle: Bundle) {
                    super.onSave(bundle)
                    workSave?.invoke(this, bundle)
                }
            }
        }
    }

    /** must be called before all else */
    fun init(surfaceView: SurfaceView, bundle: Bundle?)

    /** called once per instance when the system is initializing and not yet valid */
    fun onStartup(bundle: Bundle?) {}

    /** called many per instance when the system is ready */
    fun onEnabled() {}

    /** called many per instance when the system is paused do to temporary interruption */
    fun onDisabled() {}

    /** called once per instance when the system is shutting down and no longer valid */
    fun onShutdown() {}

    /** called many per instance when the system is paused do to save in case of death */
    fun onSave(bundle: Bundle) {}

    fun enable()
    fun disable()
}

internal open class __SurfaceManager : SurfaceManager, SurfaceEnabled {
    companion object {
        internal const val MSG_READY = 1
    }

    private var handler: Handler? = null
    private var wSurfaceView: WeakReference<SurfaceView>? = null
    private var wActivity: WeakReference<Activity>? = null
    private var enabled = false
    private var shutdown = true
    override var shutdownReason = ShutdownReason.UNKNOWN
    override var shutdownMessage = ""

    // Only available while isValid()
    override val surface: SurfaceView = wSurfaceView?.get()!!
    // Only available while isValid()
    override val activity: Activity = wActivity?.get()!!

    private fun defaults(
        handler: Handler? = null,
        wSurfaceView: WeakReference<SurfaceView>? = null,
        wActivity: WeakReference<Activity>? = null,
        enabled: Boolean = false,
        shutdown: Boolean = true,
        shutdownReason: ShutdownReason = ShutdownReason.UNKNOWN,
        shutdownMessage: String = ""
    ) {
        this.handler = handler
        this.wSurfaceView = wSurfaceView
        this.wActivity = wActivity
        this.enabled = enabled
        this.shutdown = shutdown
        this.shutdownReason = shutdownReason
        this.shutdownMessage = shutdownMessage
    }

    override fun init(surfaceView: SurfaceView, bundle: Bundle?) {
        if (!shutdown)
            shutdown(ShutdownReason.MANUAL_SHUTDOWN, "recycled without shutdown")
        defaults(
            shutdown = false,
            handler = object : Handler(Looper.getMainLooper()) {
                override fun handleMessage(msg: Message) = updateEnabled(true)
            },
        )
        initCallbacks(surfaceView)
        onStartup(bundle)
    }

    override fun shutdown(reason: ShutdownReason, msg: String) {
        Log.d("SurfaceManager.doShutdown isShutdown=$shutdown")
        if (!shutdown) {
            Log.d("Shutting Down. Reason='$reason', Msg='$msg'")
            defaults(
                shutdownReason = reason,
                shutdownMessage = msg,
            )
            onShutdown()
        }
    }

    override fun enable() {
        enabled = true
        onEnabled()
    }

    override fun disable() {
        enabled = false
        handler?.removeMessages(MSG_READY)
        onDisabled()
    }

    /** Is Valid when we have an Activity and a measured SurfaceView */
    protected fun isValid(): Boolean = !shutdown && wActivity?.get()?.let {
        wSurfaceView?.get()?.let { it.width > 0 && it.height > 0 }
    } ?: false

    /** calls [work] when there is a valid activity, else calls [doShutdown] */
    protected fun act(work: (Activity) -> Unit) =
        wActivity?.get()?.let { if (!shutdown) work.invoke(it) }
            ?: shutdown(ShutdownReason.ACTIVITY_LOST, "Activity Lost")

    /** post [work] to the uiThread when there is a valid activity, else calls [doShutdown] */
    protected fun post(work: () -> Unit) = act { it.runOnUiThread { if (!shutdown) work.invoke() } }

    private fun onCreate(bundle: Bundle?) {}
    private fun onStart() {}
    private fun onResume() = updateEnabled(true)
    private fun onPause() = updateEnabled(false)
    private fun onStop() {}
    private fun onDestroy() = shutdown(ShutdownReason.ACTIVITY_DESTROY, "Lifecycle OnDestroy")
    private fun onSaveInstanceState(bundle: Bundle) {}
    private fun onConfChange(configuration: Configuration) {}
    private fun onLowMem() {}
    private fun onTrimMem(level: Int) {}

    private fun initCallbacks(surfaceView: SurfaceView) {
        wActivity = WeakReference(AndroidUtils.requireActivity(surfaceView))
        wSurfaceView = WeakReference(surfaceView)
        Log.d("SurfaceManager.initCallbacks ${wActivity?.get()}")

        surfaceView.context.registerComponentCallbacks(object : ComponentCallbacks2 {
            override fun onConfigurationChanged(cfg: Configuration) = onConfChange(cfg)
            override fun onLowMemory() = onLowMem()
            override fun onTrimMemory(level: Int) = onTrimMem(level)
        })

        app().registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityCreated(a: Activity, b: Bundle?) = act { onCreate(b) }
            override fun onActivityStarted(a: Activity) = act { onStart() }
            override fun onActivityResumed(a: Activity) = act { onResume() }
            override fun onActivityPaused(a: Activity) = act { onPause() }
            override fun onActivityStopped(a: Activity) = act { onStop() }
            override fun onActivityDestroyed(a: Activity) {
                Log.d("SurfaceManager.onActivityDestroyed $a")
                act { onDestroy() }
            }

            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) =
                act { onSaveInstanceState(b) }
        })

        surfaceView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
            queueLayoutChanged()
        }
    }

    /**
     * Check if system can be enabled and calls [onEnabled]
     * checked from
     * - OnLayoutChange
     * - OnResume
     *
     * Typically at the time of the first onResume, the surfaceView has not yet completed
     * it's onMeasure & onLayout. This results in a width and height of 0 and 0 as the
     * layout has not yet determined the needs of its children (and thus itself).
     *
     * Typical flow is:
     *
     * onResume() // First
     *   updateEnabled() // results in No-Op -- Not Ready
     *     if ( !enabled && valid && resume )
     *       onEnable()
     *
     * ... // meanwhile onMeasure() has finished.
     *
     * onLayoutChanged()
     *   updateEnabled() // ready but we're unsure if the activity is in Resume or Pause.
     *     if ( !enabled && valid && resume )
     *       onEnable() // Probably happened, but if the activity did pause...
     *
     * onResume() // Subsequent
     *   updateEnabled()
     *     if ( !enabled && valid && resume ) // if we're not already enabled, then we are now
     *       onEnable()
     *
     */
    private fun updateEnabled(resumed: Boolean) {
        if (shutdown) return // don't bother
        val oldState = enabled
        val newState = resumed && isValid()
        if (newState == oldState) return // no change
        if (newState) enable() else disable()
    }


    /**
     * Reduce the number of messages sent upon onLayoutChanged
     * This can occur when views are co-dependent on each other for their size.
     * This results in onMeasure-Bounce.
     *
     * The approach is to delay the signal by 10ms (does not need to be perfect)
     * which will allow for multiple onLayout's to pile in and get ignored.
     * The stampede will most likely be exhausted by the time enabled = true
     * Which will block further calls.
     *
     * If another message does get through, no harm done.
     * The goal is to protect the msg queue from getting over burdened which can
     * lead to Looper being busy > main thread busy > frame loss.
     */
    private fun queueLayoutChanged() {
        handler?.let {
            if (!enabled && !it.hasMessages(MSG_READY))
                it.sendEmptyMessageDelayed(MSG_READY, 10)
        }
    }

}