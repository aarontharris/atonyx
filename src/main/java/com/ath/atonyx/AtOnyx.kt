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

    fun setStroke(stroke: StrokeStyle)
    fun addExclusion(rect: Rect)

    fun eraseEverything()

    fun enablePen()

    fun doCreate(surfaceView: SurfaceView)
    fun doResume()
    fun doPause()
    fun doStop()
    fun doDestroy()

    fun draw(stroke: StrokeStyle, touchPointList: TouchPointList)

}

open class AtOnyxImpl : AtOnyx {

    private var strokeStyle = StrokeStyle.FOUNTAIN
    private var strokeWidth = 4.0f
    private var countRec = 0

    private var _surfaceview: SurfaceView? = null
    protected val surfaceview: SurfaceView get() = _surfaceview!! // non-null or fail as it is required in onCreate()

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    private var _touchHelper: TouchHelper? = null
    private val touchHelper: TouchHelper
        get() = _touchHelper ?: TouchHelper.create(surfaceview, callback).also { _touchHelper = it }

    private var startPoint: TouchPoint? = null
    private var rxManager: RxManager? = null

    private val exclusion = mutableListOf<Rect>()
    private val paint = Paint()

    private var deviceReceiver: GlobalDeviceReceiver? = GlobalDeviceReceiver()

    override fun setStroke(stroke: StrokeStyle) {
        this.strokeStyle = stroke
        touchHelper.setStrokeStyle(stroke.style)
    }

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

    private fun attainBitmap(): Bitmap = this.bitmap ?: newBitmap().also { this.bitmap = it }
    private fun attainCanvas(): Canvas =
        (this.canvas ?: newCanvas(attainBitmap())).also { it.setBitmap(attainBitmap()) }

    protected open fun newBitmap(): Bitmap = Bitmap.createBitmap(
        surfaceview.width,
        surfaceview.height,
        Bitmap.Config.ARGB_8888
    )

    protected open fun newCanvas(bitmap: Bitmap): Canvas = Canvas(bitmap)

    protected open fun initPaint() {
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = Color.BLACK
        paint.strokeWidth = strokeWidth
    }

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

    public fun clearSurface() {
        recycleBitmap()
    }

    private fun recycleBitmap() {
        Log.d("AtOnyx.recycleBitmap")
        bitmap?.recycle()
        bitmap = null
    }

    override fun eraseEverything() {
        Log.d("AtOnyx.eraseEverything")
        setRawDrawing(false)
        recycleBitmap()
        cleanSurfaceView()
    }

    override fun enablePen() {
        Log.d("AtOnyx.enablePen")
        setRawDrawing(true)
        recycleBitmap()
    }

    private fun setRawDrawing(enabled: Boolean) {
        Log.d("AtOnyx.setRawDrawing( $enabled )")
        touchHelper.setRawDrawingEnabled(enabled)
    }

    override fun doResume() {
        Log.d("AtOnyx.doResume")
        setRawDrawing(true)
    }

    override fun doPause() {
        Log.d("AtOnyx.doPause")
        setRawDrawing(false)
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
        initPaint()
        initSurface()
        initReceiver()
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
                if (cleanSurfaceView()) {
                    surfaceview.removeOnLayoutChangeListener(this)
                }
                val limit = Rect()
                surfaceview.getLocalVisibleRect(limit)
                touchHelper.setStrokeWidth(strokeWidth)
                    ?.setLimitRect(limit, exclusion)
                    ?.openRawDrawing()
                touchHelper.setStrokeStyle(strokeStyle.style)
                surfaceview.addOnLayoutChangeListener(this)
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
        canvas = null
        _touchHelper = null
        deviceReceiver?.enable(getContext(), false)
        deviceReceiver = null
    }


    private val callback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onBeginRawDrawing")
            startPoint = touchPoint
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
            countRec = 0
            disableFingerTouch(getContext())
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d("onEndRawDrawing###")
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
            TouchUtils.enableFingerTouch(surfaceview.context)
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
            Log.d("onRawDrawingTouchPointMoveReceived")
            Log.d(touchPoint.getX().toString() + ", " + touchPoint.getY())
            countRec++
            Log.d("countRec = $countRec")
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
            Log.d("onRawDrawingTouchPointListReceived")
            draw(strokeStyle, touchPointList)
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


    override fun draw(stroke: StrokeStyle, touchPointList: TouchPointList) {
        when (stroke) {
            StrokeStyle.PENCIL -> drawPencil(touchPointList)
            StrokeStyle.FOUNTAIN -> drawFountain(touchPointList)
            else -> drawFountain(touchPointList)
        }
    }

    protected open fun drawPencil(touchPointList: TouchPointList) {
        val canvas = attainCanvas()
        val list = touchPointList.points
        val path = Path()
        val prePoint = PointF(list.get(0).x, list.get(0).y)
        path.moveTo(prePoint.x, prePoint.y)
        for (point in list) {
            path.quadTo(prePoint.x, prePoint.y, point.x, point.y)
            prePoint.x = point.x
            prePoint.y = point.y
        }
        canvas.drawPath(path, paint)
    }

    protected open fun drawFountain(touchPointList: TouchPointList) {
        val canvas = attainCanvas()
        val maxPressure = EpdController.getMaxTouchPressure()
        NeoFountainPen.drawStroke(
            canvas,
            paint,
            touchPointList.points,
            NumberUtils.FLOAT_ONE,
            strokeWidth,
            maxPressure,
            false
        )
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