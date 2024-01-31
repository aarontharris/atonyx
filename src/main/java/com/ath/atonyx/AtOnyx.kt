package com.ath.atonyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.View
import android.view.View.OnLayoutChangeListener
import android.view.View.OnTouchListener
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.utils.NumberUtils

open class AtOnyx() {

    companion object {
        const val TAG = "AtOnyx"
    }

    @JvmField
    var STROKE_WIDTH = 3.0f

    @JvmField
    var INTERVAL = 10

    var countRec = 0; private set

    private var _surfaceview: SurfaceView? = null
    protected val surfaceview: SurfaceView get() = _surfaceview!! // non-null or fail as it is required in onCreate()

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    private var touchHelper: TouchHelper? = null
    private var startPoint: TouchPoint? = null


    val paint = Paint()

    private var stroke = Stroke.FOUNTAIN

    val exclusion = mutableListOf<Rect>()

    fun setStroke(stroke: Stroke) {
        this.stroke = stroke
        touchHelper?.setStrokeStyle(stroke.style)
    }

    fun addExclusion(rect: Rect) {
        exclusion.add(rect)
    }

    fun attainBitmap(): Bitmap = this.bitmap ?: newBitmap().also { this.bitmap = it }
    fun attainCanvas(): Canvas =
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
        paint.strokeWidth = STROKE_WIDTH
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

    fun clearSurface() {
        clearBitmap()
    }

    fun clearBitmap() {
        bitmap?.recycle()
        bitmap = null
    }

    fun setRawDrawing(enabled: Boolean) {
        touchHelper?.setRawDrawingEnabled(enabled)
    }

    /**
     * Must be called before use.
     * @see [doDestroy]
     */
    fun doCreate(surfaceView: SurfaceView) {
        this._surfaceview = surfaceView
        touchHelper = TouchHelper.create(surfaceView, callback)

        initPaint()

        setRawDrawing(true)

        onCreate(surfaceView)

        initSurface()
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
    fun doDestroy() = onDestroy()

    /**
     * Called when this [AtOnyx] is no longer needed.
     * Not guaranteed such as when the app is force-closed or crashes.
     * Perform cleanup here.
     */
    protected open fun onDestroy() {
        touchHelper?.closeRawDrawing()
        _surfaceview = null
        clearBitmap()
        canvas = null
        touchHelper = null
    }


    private val callback: RawInputCallback = object : RawInputCallback() {
        override fun onBeginRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onBeginRawDrawing")
            startPoint = touchPoint
            Log.d(
                TAG,
                touchPoint.getX().toString() + ", " + touchPoint.getY()
            )
            countRec = 0
            // com.ath.onyx.TouchUtils.disableFingerTouch(getApplicationContext())
        }

        override fun onEndRawDrawing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onEndRawDrawing###")
//            if (!binding.cbRender.isChecked()) {
//                drawRect(touchPoint)
//            }
            Log.d(
                TAG,
                touchPoint.getX().toString() + ", " + touchPoint.getY()
            )
            TouchUtils.enableFingerTouch(surfaceview.context)
        }

        override fun onRawDrawingTouchPointMoveReceived(touchPoint: TouchPoint) {
            Log.d(TAG, "onRawDrawingTouchPointMoveReceived")
            Log.d(
                TAG,
                touchPoint.getX().toString() + ", " + touchPoint.getY()
            )
            countRec++
            countRec %= INTERVAL
            Log.d(TAG, "countRec = $countRec")
        }

        override fun onRawDrawingTouchPointListReceived(touchPointList: TouchPointList) {
            Log.d(TAG, "onRawDrawingTouchPointListReceived")
            // drawScribbleToBitmap(touchPointList)
            draw(stroke, touchPointList)
        }

        override fun onBeginRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onBeginRawErasing")
        }

        override fun onEndRawErasing(b: Boolean, touchPoint: TouchPoint) {
            Log.d(TAG, "onEndRawErasing")
        }

        override fun onRawErasingTouchPointMoveReceived(touchPoint: TouchPoint) {
            Log.d(TAG, "onRawErasingTouchPointMoveReceived")
        }

        override fun onRawErasingTouchPointListReceived(touchPointList: TouchPointList) {
            Log.d(TAG, "onRawErasingTouchPointListReceived")
        }
    }

    fun initSurface() {

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
                touchHelper?.setStrokeWidth(STROKE_WIDTH)
                    ?.setLimitRect(limit, exclusion)
                    ?.openRawDrawing()
                touchHelper?.setStrokeStyle(stroke.style)
                surfaceview.addOnLayoutChangeListener(this)
            }
        })

        surfaceview.setOnTouchListener(OnTouchListener { v, event ->
            Log.d(
                TAG,
                "surfaceView.setOnTouchListener - onTouch::action - " + event.action
            )
            true
        })

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

    fun draw(stroke: Stroke, touchPointList: TouchPointList) {
        when (stroke) {
            Stroke.PENCIL -> drawPencil(touchPointList)
            Stroke.FOUNTAIN -> drawFountain(touchPointList)
            else -> TODO()
        }
    }

    fun drawPencil(touchPointList: TouchPointList) {
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

    fun drawFountain(touchPointList: TouchPointList) {
        val canvas = attainCanvas()
        val maxPressure = EpdController.getMaxTouchPressure()
        NeoFountainPen.drawStroke(
            canvas,
            paint,
            touchPointList.points,
            NumberUtils.FLOAT_ONE,
            STROKE_WIDTH,
            maxPressure,
            false
        )
    }

}