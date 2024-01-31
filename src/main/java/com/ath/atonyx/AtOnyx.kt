package com.ath.atonyx

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.view.SurfaceView
import com.onyx.android.sdk.api.device.epd.EpdController
import com.onyx.android.sdk.pen.NeoFountainPen
import com.onyx.android.sdk.pen.data.TouchPointList
import com.onyx.android.sdk.utils.NumberUtils

open class AtOnyx() {

    @JvmField
    var STROKE_WIDTH = 3.0f

    private var _surfaceview: SurfaceView? = null
    protected val surfaceview: SurfaceView get() = _surfaceview!! // non-null or fail as it is required in onCreate()

    private var bitmap: Bitmap? = null
    private var canvas: Canvas? = null

    val paint = Paint()

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

    fun clearSurface() {
        clearBitmap()
    }

    fun clearBitmap() {
        bitmap?.recycle()
        bitmap = null
    }

    /**
     * Must be called before use.
     * @see [doDestroy]
     */
    fun doCreate(surfaceView: SurfaceView) {
        this._surfaceview = surfaceView
        initPaint()
        onCreate(surfaceView)
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
        _surfaceview = null
        clearBitmap()
        canvas = null
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