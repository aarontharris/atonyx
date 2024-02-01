package com.ath.atonyx

import android.graphics.Color
import androidx.annotation.ColorInt

class StrokeAttr(
    var mode: PenMode = PenMode.PEN,
    var style: StrokeStyle = DEFAULT_STROKE_STYLE,
    var width: Float = DEFAULT_STROKE_WIDTH,
    @get:ColorInt var color: Int = DEFAULT_STROKE_COLOR,
) {
    companion object {
        const val DEFAULT_STROKE_WIDTH = 4.0f
        val DEFAULT_STROKE_STYLE = StrokeStyle.FOUNTAIN
        val DEFAULT_STROKE_COLOR = Color.BLACK
    }

    constructor(attr: StrokeAttr) : this(attr.mode, attr.style, attr.width, attr.color)
}