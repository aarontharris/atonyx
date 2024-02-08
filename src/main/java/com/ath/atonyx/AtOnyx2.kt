package com.ath.atonyx

import android.os.Bundle
import android.view.SurfaceView
import com.onyx.android.sdk.data.note.TouchPoint
import com.onyx.android.sdk.pen.RawInputCallback
import com.onyx.android.sdk.pen.TouchHelper
import com.onyx.android.sdk.pen.data.TouchPointList

class AtOnyx2 {


    private val props = ManagedProps()
    private val penEnabled = Prop(props, true) { touchHelper?.setRawDrawingEnabled(it.cur) }

    private var touchHelper: TouchHelper? = null

    private val surfaceManager = SurfaceManager.builder()
        .onEnabled { onEnabled(it) }
        .onDisabled { onDisabled() }
        .build()

    fun doInit(surfaceView: SurfaceView, bundle: Bundle?) {
        surfaceManager.init(surfaceView, bundle)
    }

    fun enable() = surfaceManager.enable()
    fun disable() = surfaceManager.disable()
    fun shutdown() = surfaceManager.shutdown()

    private fun onEnabled(surfaceEnabled: SurfaceEnabled) {
        // NOTE: SurfaceView is weakly-held by TouchHelper & inputListener is stronly-held
        touchHelper = TouchHelper.create(surfaceEnabled.surface, inputListener()).also {
            it.openRawDrawing()
        }

        penEnabled.usr = true
        penEnabled.sys = true

    }

    private fun onDisabled() {
        touchHelper?.let {
            it.closeRawDrawing()
        }
        touchHelper = null
    }

    private fun inputListener(): RawInputCallback = object : RawInputCallbackSimple() {
        override fun onEndRawDrawing(outLimitRegion: Boolean, point: TouchPoint) {
            // enableFinger()
            // currentPenStroke = null
        }

        override fun onBeginRawDrawing(shortcutDrawing: Boolean, point: TouchPoint) {
//            currentPenStroke =
//                PenStrokeImpl(StrokeAttr(attr)).also { penStrokesOrdered.add(it) }
//            disableFinger()
        }

        override fun onRawDrawingTouchPointListReceived(points: TouchPointList) {
//            currentPenStroke?.add(points)
//            draw(points)
        }
    }

}