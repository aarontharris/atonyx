package com.ath.atonyx

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnLayoutChangeListener
import androidx.annotation.ColorInt
import com.ath.atonyx.TouchUtils.disableFingerTouch
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

    fun style(): AtOnyxStyle

    fun addExclusion(rect: Rect)
    fun eraseEverything()

    fun enablePen()
    fun enableErase()

    fun doCreate(surfaceView: SurfaceView)
    fun doResume()
    fun doPause()
    fun doStop()
    fun doDestroy()

    fun draw(touchPointList: TouchPointList)

    fun save(name: String)
    fun restore(name: String)
    fun refreshAll()
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
open class AtOnyxImpl : AtOnyx, AtOnyxStyle {

    private val attr = StrokeAttr()

    private var inResume = false
    private var isLayoutValid = false

    private var _surfaceview: SurfaceView? = null
    protected val surfaceview: SurfaceView get() = _surfaceview!! // non-null or fail as it is required in onCreate()

    private var _bitmap: Bitmap? = null
    private var _canvas: Canvas? = null

    private var _touchHelper: TouchHelper? = null
    private val touchHelper: TouchHelper
        get() = _touchHelper ?: TouchHelper.create(surfaceview, callback).also { _touchHelper = it }

    private var startPoint: TouchPoint? = null
    private var rxManager: RxManager? = null

    private val exclusion = mutableListOf<Rect>()
    private val paint = Paint()

    private var deviceReceiver: GlobalDeviceReceiver? = GlobalDeviceReceiver()

    override fun setMode(mode: PenMode) {
        this.attr.mode = mode
        initStyle()
    }

    override fun setStyle(stroke: StrokeStyle) {
        this.attr.style = stroke
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

    fun initStyle() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = this.attr.color
        paint.strokeWidth = this.attr.width

        touchHelper.setStrokeStyle(attr.style.style)
        touchHelper.setStrokeWidth(attr.width)
        touchHelper.setStrokeColor(attr.color)
    }

    override fun style(): AtOnyxStyle = this

    override fun addExclusion(rect: Rect) {
        exclusion.add(rect)
    }

    private fun getRxManager(): RxManager? {
        if (rxManager == null) {
            rxManager = RxManager.Builder.sharedSingleThreadManager()
        }
        return rxManager
    }

    private fun renderToScreen(surfaceView: SurfaceView?, bitmap: Bitmap?) {
        getRxManager()!!.enqueue(RendererToScreenRequest(surfaceView, bitmap), null)
    }

    private fun attainBitmap(): Bitmap = this._bitmap ?: newBitmap().also { this._bitmap = it }
    private fun attainCanvas(): Canvas =
        (this._canvas ?: newCanvas(attainBitmap())).also { it.setBitmap(attainBitmap()) }

    protected open fun newBitmap(): Bitmap = Bitmap.createBitmap(
        surfaceview.width,
        surfaceview.height,
        Bitmap.Config.ARGB_8888
    )

    protected open fun newCanvas(bitmap: Bitmap): Canvas = Canvas(bitmap)

    private fun initReceiver() {
        deviceReceiver?.setSystemNotificationPanelChangeListener { open ->
            setRawDrawing(!open)
            renderToScreen(surfaceview, attainBitmap())
        }?.setSystemScreenOnListener { renderToScreen(surfaceview, attainBitmap()) }
        deviceReceiver?.enable(getContext(), true)
    }

    fun cleanSurfaceView(): Boolean {
        if (surfaceview.getHolder() == null) {
            return false
        }
        val canvas: Canvas = surfaceview.holder.lockCanvas() ?: return false
        canvas.drawColor(Color.WHITE)
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
        if (surfaceview.holder == null) {
            Log.e("AtOnyx.refreshAll -- Unable to obtain surfaceview.holder")
            return
        }

        setRawDrawing(false)

        val surfaceCanvas: Canvas = surfaceview.holder.lockCanvas().also {
            if (it == null) Log.e("AtOnyx.refreshAll -- Unable to obtain surfaceview.lockCanvas")
        } ?: return

        surfaceCanvas.drawColor(Color.WHITE) // overwrite the whole surfaceCanvas with WHITE

        val copy = ArrayList(penStrokesOrdered)
        penStrokesOrdered.clear()

        for (stroke in copy) {
            draw(surfaceCanvas, stroke)
            penStrokesOrdered.add(stroke)
        }

        surfaceview.holder.unlockCanvasAndPost(surfaceCanvas)
        setRawDrawing(true)
    }

    override fun eraseEverything() {
        Log.d("AtOnyx.eraseEverything")
        setRawDrawing(false)
        recycleBitmap()
        cleanSurfaceView()
    }

    override fun enablePen() {
        setMode(PenMode.PEN)
        Log.d("AtOnyx.enablePen")
        setRawDrawing(true)
        // recycleBitmap()
    }

    override fun enableErase() {
        setMode(PenMode.ERASE_STROKE)
    }

    private fun setRawDrawing(enabled: Boolean) {
        Log.d("AtOnyx.setRawDrawing( $enabled )")
        touchHelper.setRawDrawingEnabled(enabled)
    }

    override fun doResume() {
        Log.d("AtOnyx.doResume")
        setRawDrawing(true)
        inResume = true
        checkResumeAndLayoutComplete()
    }

    fun checkResumeAndLayoutComplete() {
        if (inResume && isLayoutValid) onResumeAndLayoutComplete()
    }

    fun onResumeAndLayoutComplete() {
    }

    override fun doPause() {
        Log.d("AtOnyx.doPause")
        setRawDrawing(false)
        inResume = false
    }

    override fun doStop() {
        Log.d("AtOnyx.doStop")
        setRawDrawing(false)
    }

    /**
     * Must be called before use.
     * @see [doDestroy]
     */
    override fun doCreate(surfaceView: SurfaceView) {
        Log.d("AtOnyx.doCreate() - Start")
        this._surfaceview = surfaceView
        // touchHelper = TouchHelper.create(surfaceView, callback)
        initReceiver()
        initStyle()
        initSurface()
        setRawDrawing(true)
        onCreate(surfaceView)
        Log.d("AtOnyx.doCreate() - Finish")
    }

    private fun initSurface() {
        surfaceview.addOnLayoutChangeListener(object : OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int,
            ) {
                Log.d("AtOnyx.onLayoutChange -- surfaceview.wh=${surfaceview.width}/${surfaceview.height}")
                if (cleanSurfaceView()) {
                    surfaceview.removeOnLayoutChangeListener(this)
                }
                val limit = Rect()
                surfaceview.getLocalVisibleRect(limit)
                touchHelper
                    .setLimitRect(limit, exclusion)
                    .openRawDrawing()
                initStyle()
                isLayoutValid = true
                checkResumeAndLayoutComplete()
            }
        })

        surfaceview.setOnTouchListener { _, event ->
            Log.d("surfaceView.setOnTouchListener - onTouch::action - " + event.action)
            true
        }

        val surfaceCallback: SurfaceHolder.Callback = object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                cleanSurfaceView()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int,
            ) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                holder.removeCallback(this)
            }
        }
        surfaceview.holder.addCallback(surfaceCallback)
    }

    protected open fun getContext(): Context {
        return surfaceview.context
    }

    /**
     * Called when this [AtOnyx] is being created.
     * Guaranteed before use.
     * Perform initialization here.
     */
    protected open fun onCreate(surfaceView: SurfaceView) {}

    /**
     * Call when no longer in use.
     * @see [doCreate]
     */
    override fun doDestroy() = onDestroy()

    /**
     * Called when this [AtOnyx] is no longer needed.
     * Not guaranteed such as when the app is force-closed or crashes.
     * Perform cleanup here.
     */
    protected open fun onDestroy() {
        touchHelper.closeRawDrawing()
        _surfaceview = null
        recycleBitmap()
        _canvas = null
        _touchHelper = null
        deviceReceiver?.enable(getContext(), false)
        deviceReceiver = null
    }


    private val callback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onBeginRawDrawing")
            currentPenStroke = PenStrokeImpl(StrokeAttr(attr)).also { penStrokesOrdered.add(it) }
            startPoint = touchPoint
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
            disableFingerTouch(getContext())
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onEndRawDrawing###")
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
            TouchUtils.enableFingerTouch(surfaceview.context)
            currentPenStroke = null

        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
            Log.d("onRawDrawingTouchPointMoveReceived")
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
            Log.d("AtOnyx.onTouchPoints -- points=${touchPointList.size()}")
            currentPenStroke?.add(touchPointList)
            draw(touchPointList)
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onBeginRawErasing")
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onEndRawErasing")
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint) {
            Log.d("onRawErasingTouchPointMoveReceived")
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList) {
            Log.d("onRawErasingTouchPointListReceived")
        }
    }

    override fun draw(touchPointList: TouchPointList) {
        draw(attainCanvas(), this.attr, touchPointList.points)
    }

    fun draw(surfaceCanvas: Canvas, penStroke: PenStroke) =
        draw(surfaceCanvas, penStroke.attr, penStroke.toTouchPointList())

    private var currentPenStroke: PenStroke? = PenStrokeImpl(attr)

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
    //       However, TouchPoint looks ok? Still unsure. TODO: Test to find out?
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

        // refresh ui
        touchHelper.setRawDrawingEnabled(false)
        touchHelper.setRawDrawingEnabled(true)

//        if (!binding.cbRender.isChecked()) {
//            touchHelper.setRawDrawingRenderEnabled(false);
//        }
    }

    /*
    private void drawBitmapToSurface() {
        if (!binding.cbRender.isChecked()) {
            return;
        }
        if (bitmap == null) {
            return;
        }
        Canvas lockCanvas = binding.surfaceview.getHolder().lockCanvas();
        if (lockCanvas == null) {
            return;
        }
        lockCanvas.drawColor(Color.WHITE);
        lockCanvas.drawBitmap(bitmap, 0f, 0f, paint);
        binding.surfaceview.getHolder().unlockCanvasAndPost(lockCanvas);
        // refresh ui
        touchHelper.setRawDrawingEnabled(false);
        touchHelper.setRawDrawingEnabled(true);
        if (!binding.cbRender.isChecked()) {
            touchHelper.setRawDrawingRenderEnabled(false);
        }
    }
     */
}