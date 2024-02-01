package com.ath.atonyx

import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.data.TouchPointList

interface PenStroke {
    val attr: StrokeAttr
    fun toTouchPointList(): List<TouchPoint>
    fun add(touchPointList: TouchPointList)
    fun add(points: List<TouchPoint>)
    fun add(point: TouchPoint)
}

/**
 * We wrap the TouchPointsList because it is unclear if a TouchPointsList is pooled or cached.
 * By keeping a ref, we could be causing leaks or other data errors.
 */
internal class PenStrokeImpl(
    override val attr: StrokeAttr,
    points: List<TouchPoint>? = null,
) : PenStroke {
    private val touchPointList = TouchPointList()

    init {
        if (points != null) add(points)
    }

    override fun add(touchPointList: TouchPointList) {
        this.touchPointList.addAll(touchPointList)
        Log.d("AtOnyx.PenStroke.add -- points=${touchPointList.size()}, total=${this.touchPointList.size()}")
    }

    override fun add(points: List<TouchPoint>) {
        for (point in points) {
            add(point)
        }
    }

    override fun add(point: TouchPoint) {
        touchPointList.add(point)
    }

    override fun toTouchPointList(): List<TouchPoint> {
        val points = touchPointList.points ?: emptyList()
        return points
    }
}