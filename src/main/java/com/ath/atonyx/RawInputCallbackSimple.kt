package com.ath.atonyx

import android.graphics.RectF
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.data.TouchPointList

/**
 * Reference: https://github.com/onyx-intl/OnyxAndroidDemo/blob/master/doc/Scribble-TouchHelper-API.md
 * ^ Seems out of date, but we can infer the use-case
 */
open class RawInputCallbackSimple : RawInputCallback() {
    /** tbd */
    override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) {
    }

    /** tbd */
    override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) {
    }

    /** tbd */
    override fun onRawDrawingTouchPointMoveReceived(point: TouchPoint) {
    }

    /** tbd */
    override fun onRawDrawingTouchPointListReceived(points: TouchPointList) {
    }

    /** tbd */
    override fun onBeginRawErasing(shortcutErasing: Boolean, point: TouchPoint) {
    }

    /** tbd */
    override fun onEndRawErasing(outLimitRegion: Boolean, point: TouchPoint) {
    }

    /** tbd */
    override fun onRawErasingTouchPointMoveReceived(point: TouchPoint) {
    }

    /** tbd */
    override fun onRawErasingTouchPointListReceived(points: TouchPointList) {
    }

    /** tbd */
    override fun onPenActive(point: TouchPoint) {
    }

    /** tbd */
    override fun onPenUpRefresh(refreshRect: RectF) {
    }
}