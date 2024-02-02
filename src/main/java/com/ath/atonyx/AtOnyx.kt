package com.ath.atonyx

import android.app.Activity
import android.app.Application.ActivityLifecycleCallbacks
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.annotation.ColorInt
import androidx.annotation.RequiresApi
import com.ath.atonyx.LC.*
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.rx.RxManager
import com.onyx.android.sdk.utils.NumberUtils

interface AtOnyx {
    companion object {
        /** Attain an instance of AtOnyx */
        @JvmStatic
        fun attain(): AtOnyx {
            return AtOnyxImpl()
        }
    }

    /** Style Interface -- Change the pen style & mode */
    fun style(): AtOnyxStyle

    /** Initialization Interface -- Lifecycle callbacks */
    fun init(): AtOnyxInit

    /** Areas that will not receive pen drawing input (but still receive clicks) */
    fun addExclusion(rect: Rect)

    /** Applies to Drawing Surface Only */
    fun inputMode(pen: Boolean, touch: Boolean) {
        if (pen) enablePen() else disablePen()
        if (touch) enableFinger() else disableFinger()
    }

    /** Applies to Drawing Surface Only */
    fun enablePen()

    /** Applies to Drawing Surface Only */
    fun disablePen()

    /** Applies to Drawing Surface Only */
    fun enableFinger()

    /** Applies to Drawing Surface Only */
    fun disableFinger()

    fun draw(touchPointList: TouchPointList)

    /** Save in-memory history to storage */
    fun save(name: String)

    /** Restore history to memory from storage */
    fun restore(name: String)

    /** Clear the surface and redraw recorded history */
    fun refreshAll()

    /** Defaults to [setBackgroundColor] */
    fun eraseEverything()

    /** Erase everything using the provided color instead of the [setBackgroundColor] */
    fun eraseEverything(@ColorInt color: Int = Color.WHITE)

    /** Defaults to [Color.WHITE] -- used when clearing the surface */
    fun setBackgroundColor(@ColorInt color: Int)
}

interface AtOnyxInit {
    fun doCreate(surfaceView: SurfaceView)
    fun doSaveInstanceState(bundle: Bundle)
    fun doStart()
    fun doResume()
    fun doPause()
    fun doStop()
    fun doDestroy()

    /**
     * NOT YET IMPLEMENTED
     * Optional Convenience for SDK29+ users.
     * This allows you to omit lifecycle calls to: [doCreate], [doResume], etc
     */
    @RequiresApi(Build.VERSION_CODES.Q)
    private fun doCreate29(context: Activity, surface: SurfaceView) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
                override fun onActivityCreated(p0: Activity, p1: Bundle?) {}
                override fun onActivityStarted(p0: Activity) {}
                override fun onActivityResumed(p0: Activity) {}
                override fun onActivityPaused(p0: Activity) {}
                override fun onActivityStopped(p0: Activity) {}
                override fun onActivitySaveInstanceState(p0: Activity, p1: Bundle) {}
                override fun onActivityDestroyed(p0: Activity) {}
            })
        }
    }
}

interface AtOnyxStyle {
    fun setMode(mode: PenMode)
    fun setStyle(style: StrokeStyle)
    fun setWidth(width: Float)
    fun setColor(@ColorInt color: Int)
    fun setColor(color: Color) = setColor(color.toArgb())
}

/**
 * # Onyx Boox Note 3
 * Screen Resolution: 1872 x 1404, PPI 227
 */
open class AtOnyxImpl : AtOnyx, AtOnyxStyle, AtOnyxInit {

    private val attr = StrokeAttr()
    @ColorInt private var backColor: Int = Color.WHITE
    private var lifecycle: LC = ON_DESTROY

    private val isSurfaceValid: Boolean
        get() = _surfaceview?.let { it.width > 0 && it.height > 0 } ?: false

    private var _surfaceview: SurfaceView? = null
    protected val surfaceview: SurfaceView get() = _surfaceview!! // non-null or fail as it is required in onCreate()

    // bitmap & canvas are tied.
    // canvas is backed by bitmap
    private var _bitmap: Bitmap? = null
    private val bitmap get() = this._bitmap ?: newBitmap().also { this._bitmap = it }
    private var _canvas: Canvas? = null
    private val canvas get() = (this._canvas ?: newCanvas(bitmap)).also { it.setBitmap(bitmap) }

    private var _touchHelper: TouchHelper? = null
    private val touchHelper: TouchHelper
        get() = _touchHelper ?: TouchHelper.create(surfaceview, inputListener())
            .also { _touchHelper = it }

    private var _rxManager: RxManager? = null

    private val exclusionList = mutableListOf<Rect>()
    private val paint = Paint()

    private var deviceReceiver: GlobalDeviceReceiver? = GlobalDeviceReceiver()
    private var currentPenStroke: PenStroke? = PenStrokeImpl(attr)

    private fun inputListener(): RawInputCallback = object : RawInputCallbackSimple() {
        override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) {
            enableFinger()
            currentPenStroke = null
        }

        override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) {
            currentPenStroke =
                PenStrokeImpl(StrokeAttr(attr)).also { penStrokesOrdered.add(it) }
            disableFinger()
        }

        override fun onRawDrawingTouchPointListReceived(points: TouchPointList) {
            currentPenStroke?.add(points)
            draw(points)
        }
    }

    private fun initStyle() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = this.attr.color
        paint.strokeWidth = this.attr.width

        touchHelper.setStrokeStyle(attr.style.style)
        touchHelper.setStrokeWidth(attr.width)
        touchHelper.setStrokeColor(attr.color)

        when (attr.mode) {
            PenMode.PEN -> {
                touchHelper.setBrushRawDrawingEnabled(true)
                touchHelper.setEraserRawDrawingEnabled(false)
            }

            PenMode.ERASE_STROKE -> {
                touchHelper.setBrushRawDrawingEnabled(false)
                touchHelper.setEraserRawDrawingEnabled(true)
            }
        }
    }

    private fun initReceiver() {
        deviceReceiver?.setSystemNotificationPanelChangeListener { open ->
            Log.d("AtOnyx.SystemNotificationPanelChangeListener")
            setRawDrawing(!open)
            queueRenderBitmapToScreen(surfaceview, bitmap)
        }?.setSystemScreenOnListener {
            Log.d("AtOnyx.SystemScreenOnListener")
            queueRenderBitmapToScreen(surfaceview, bitmap)
        }
        deviceReceiver?.enable(getContext(), true)
    }

    private fun startup() {
        @Suppress("UNUSED_ANONYMOUS_PARAMETER")
        surfaceview.addOnLayoutChangeListener { v, l, t, r, b, oldL, oldT, oldR, oldB ->
            Log.d("AtOnyx.onLayoutChange -- surfaceview.wh=${surfaceview.width}/${surfaceview.height}")
            cleanSurfaceView()
            val limit = Rect()
            surfaceview.getLocalVisibleRect(limit)
            touchHelper.setLimitRect(limit, exclusionList)
            if (!touchHelper.isRawDrawingCreated)
                openRawDrawing()
            notifyLayoutChange()
        }

        // surfaceview.setOnTouchListener { _, event ->
        //    Log.d("surfaceView.setOnTouchListener - onTouch::action - " + event.action)
        //    true
        // }

        surfaceview.holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cleanSurfaceView()
            }

            override fun surfaceChanged(holder: SurfaceHolder, fmt: Int, w: Int, h: Int) {}
            override fun surfaceDestroyed(holder: SurfaceHolder) = holder.removeCallback(this)
        })
    }

    override fun style(): AtOnyxStyle = this
    override fun init(): AtOnyxInit = this

    override fun setMode(mode: PenMode) {
        Log.d("AtOnyx.setMode $mode")
        this.attr.mode = mode
        initStyle()
    }

    override fun setStyle(style: StrokeStyle) {
        Log.d("AtOnyx.setStyle $style")
        this.attr.style = style
        initStyle()
    }

    override fun setWidth(width: Float) {
        this.attr.width = width
        initStyle()
    }

    override fun setColor(color: Int) {
        this.attr.color = color
        initStyle()
    }

    override fun addExclusion(rect: Rect) {
        exclusionList.add(rect)
    }

    private fun getRxManager(): RxManager =
        _rxManager ?: RxManager.Builder.sharedSingleThreadManager().also { _rxManager = it }

    private fun queueRenderBitmapToScreen(surfaceView: SurfaceView, bitmap: Bitmap?) {
        getRxManager().enqueue(RendererToScreenRequest(surfaceView, bitmap), null)
    }

    protected open fun newBitmap(): Bitmap = Bitmap.createBitmap(
        surfaceview.width,
        surfaceview.height,
        Bitmap.Config.ARGB_8888
    )

    protected open fun newCanvas(bitmap: Bitmap): Canvas = Canvas(bitmap)

    fun cleanSurfaceView(@ColorInt color: Int = Color.WHITE): Boolean {
        if (surfaceview.holder == null) return false
        val canvas: Canvas = surfaceview.holder.lockCanvas() ?: return false
        canvas.drawColor(color)
        surfaceview.holder.unlockCanvasAndPost(canvas)
        return true
    }

    private fun recycleBitmap() {
        Log.d("AtOnyx.recycleBitmap")
        _canvas = null
        _bitmap?.recycle()
        _bitmap = null
    }


    override fun refreshAll() {
        Log.d("AtOnyx.refreshAll")
        setRawDrawing(false)

        // clear it immediately
        recycleBitmap()

        // copy penStrokes to bitmap
        val copy = ArrayList(penStrokesOrdered)
        for (stroke in copy) {
            draw(canvas, stroke) // to bitmap // canvas is backed by bitmap
        }

        // queue bitmap to screen
        queueRenderBitmapToScreen(surfaceview, bitmap)
        setRawDrawing(true)
    }

    override fun eraseEverything() {
        eraseEverything(backColor)
    }

    override fun eraseEverything(@ColorInt color: Int) {
        Log.d("AtOnyx.eraseEverything")
        setRawDrawing(false)
        recycleBitmap()
        cleanSurfaceView(color)
    }

    override fun setBackgroundColor(@ColorInt color: Int) {
        this.backColor = color
    }

    override fun enablePen() {
        drawingEnabled(true)
        // setRawDrawing(true)
    }

    override fun disablePen() {
        drawingEnabled(false)
        // setRawDrawing(false)
    }

    // TODO untested
    override fun enableFinger() {
        TouchUtils.enableFingerTouch(getContext())
        touchHelper.enableFingerTouch(true)
    }

    // TODO untested
    override fun disableFinger() {
        TouchUtils.disableFingerTouch(getContext())
        touchHelper.enableFingerTouch(false)
    }

    /**
     * Paused due to interruption such as
     */
    private fun systemPaused(paused: Boolean) {

    }

    private fun systemResume() {

    }

    // TODO untested
    private fun setRawDrawing(enabled: Boolean) {
        Log.d("AtOnyx.setRawDrawing( $enabled )")
        touchHelper.setRawDrawingEnabled(enabled)
    }

    private fun openRawDrawing() {
        touchHelper.openRawDrawing()
    }

    private fun closeRawDrawing() {
        touchHelper.closeRawDrawing()
    }

    private fun renderDuringScribble(enabled: Boolean) {
        touchHelper.setRawDrawingRenderEnabled(enabled)
    }

    private fun drawingEnabled(enabled: Boolean) {
        touchHelper.setRawDrawingEnabled(enabled)
    }

    private fun uiRefresh() {
        setRawDrawing(false)
        setRawDrawing(true)
    }


    override fun doSaveInstanceState(bundle: Bundle) {
        onSaveInstanceState(bundle)
    }

    protected open fun onSaveInstanceState(bundle: Bundle) {}

    /**
     * Must be called before use.
     * @see [doDestroy]
     */
    override fun doCreate(surfaceView: SurfaceView) {
        this._surfaceview = surfaceView
        notifyLifecycleChange(ON_CREATE)
    }

    override fun doStart() = notifyLifecycleChange(ON_START)
    override fun doResume() = notifyLifecycleChange(ON_RESUME)
    override fun doPause() = notifyLifecycleChange(ON_PAUSE)
    override fun doStop() = notifyLifecycleChange(ON_STOP)
    override fun doDestroy() = notifyLifecycleChange(ON_DESTROY)

    private fun notifyLifecycleChange(event: LC) {
        Log.d("AtOnyx.Lifecycle: $event")
        lifecycle = event
        when (event) {
            ON_CREATE -> startup()
            ON_START -> {}
            ON_RESUME -> checkValidSurface()
            ON_PAUSE -> {}
            ON_STOP -> {}
            ON_DESTROY -> shutdown()
        }
    }

    private fun notifyLayoutChange() {
        checkValidSurface()
    }

    private fun checkValidSurface() {
        if (lifecycle == ON_RESUME && isSurfaceValid) {
            onValidSurface()
        }
    }

    private fun onValidSurface() {
        initStyle()
        initReceiver()
        enablePen()
    }

    fun shutdown() {
        closeRawDrawing()
        _surfaceview = null
        recycleBitmap()
        _canvas = null
        _touchHelper = null
        deviceReceiver?.enable(getContext(), false)
        deviceReceiver = null
        _rxManager?.shutdown()
        _rxManager = null
    }

    protected open fun getContext(): Context {
        return surfaceview.context
    }

    override fun draw(touchPointList: TouchPointList) {
        draw(canvas, this.attr, touchPointList.points)
    }

    fun draw(surfaceCanvas: Canvas, penStroke: PenStroke) =
        draw(surfaceCanvas, penStroke.attr, penStroke.toTouchPointList())

    fun draw(canvas: Canvas, attr: StrokeAttr, points: List<TouchPoint>) {
        if (points.isEmpty()) return
        when (attr.style) {
            StrokeStyle.PENCIL -> drawPencil(canvas, points)
            StrokeStyle.FOUNTAIN -> drawFountain(canvas, points)
            else -> drawFountain(canvas, points)
        }
    }


    // NOTE: It is unclear if touchpoints are pooled. So we should not keep refs to them.
    //       TouchPointList.detachPointList() could be a hint that pooling considered
    //       However, TouchPoint looks ok? Still unsure. My testing suggests its ok?
    //       In the name of performance we will risk it and fix if it becomes a problem later.
    private val penStrokesOrdered = ArrayList<PenStroke>()

    override fun save(name: String) {
        TODO("Not yet implemented")
    }

    override fun restore(name: String) {
        Log.d("AtOnyx.restore $name")
        val copy = ArrayList(penStrokesOrdered)
        penStrokesOrdered.clear()
        for (stroke in copy) {
            Log.d("AtOnyx.restore $name -- points=${stroke.toTouchPointList().size}")
            // drawPencil(stroke.toTouchPointList())
            penStrokesOrdered.add(stroke)
        }
    }

    /**
     * Q: Which Canvas do I use?
     * The Onyx renderer wants us to provide a bitmap. So when drawing directly to the screen
     * we must use a canvas that is backed by the same bitmap we pass to the Onyx Renderer.
     * However, when you are writing to the surfaceview directly (such as restoring history)
     * you would want to obtain the surfaceview.canvas
     * @param canvas - You can draw to a bitmap-backed canvas, or the surface.canvas
     */
    protected open fun drawPencil(canvas: Canvas, points: List<TouchPoint>) {
        // Log.d("AtOnyx.drawPencil -- points=${points.size}")
        val path = Path()
        val prePoint = PointF(points[0].x, points[0].y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in points) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        canvas.drawPath(path, paint)
    }

    /**
     * @param canvas - You can draw to a bitmap-backed canvas, or the surface.canvas
     */
    protected open fun drawFountain(canvas: Canvas, points: List<TouchPoint>) {
        // Log.d("AtOnyx.drawFountain -- points=${points.size}")
        val maxPressure = EpdController.getMaxTouchPressure()
        val eraser = false // attr.mode == PenMode.ERASE_STROKE
        NeoFountainPen.drawStroke(
            canvas,
            paint,
            points,
            NumberUtils.FLOAT_ONE,
            attr.width,
            maxPressure,
            eraser
        )
    }

    private fun drawBitmapToSurface(bitmap: Bitmap) {
        Log.d("AtOnyx.drawBitmap")
        val lockCanvas = surfaceview.holder.lockCanvas().also {
            if (it == null) Log.d("AtOnyx.drawBitmap -- could not obtain lockCanvas")
        } ?: return

        // lockCanvas.drawColor(Color.BLACK); // This overwrites the whole canvas with this color
        Log.d("AtOnyx.drawBitmap to canvas")
        lockCanvas.drawBitmap(bitmap, 0f, 0f, paint);
        surfaceview.holder.unlockCanvasAndPost(lockCanvas);

        uiRefresh()
//        if (!binding.cbRender.isChecked()) {
//            touchHelper.setRawDrawingRenderEnabled(false);
//        }
    }

}