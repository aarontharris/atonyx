package com.ath.atonyx

import android.graphics.RectF
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList

open class RawInputCallbackSimple : RawInputCallback() {
    override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) {
    }

    override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) {
    }

    override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) {
    }

    override fun onRawDrawingTouchPointListReceived(points: TouchPointList) {
    }

    override fun onBeginRawErasing(shortcutErasing: Boolean, point: TouchPoint) {
    }

    override fun onEndRawErasing(outLimitRegion: Boolean, point: TouchPoint) {
    }

    override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) {
    }

    override fun onRawErasingTouchPointListReceived(points: TouchPointList) {
    }

    override fun onPenActive(point: TouchPoint) {
    }

    override fun onPenUpRefresh(refreshRect: RectF) {
    }
}