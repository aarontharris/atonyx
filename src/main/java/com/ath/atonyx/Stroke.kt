package com.ath.atonyx

import com.onyx.android.sdk.pen.TouchHelper

/**
 * NOTE: [TouchHelper].STROKE_STYLE_XXX is redundant of
 * [com.onyx.android.sdk.pen.style.StrokeStyle].XXX
 * Hard to say which is the source of truth but for now the two agree.
 *
 * We abstract the two here into an enum for:
 * - typesafety (vs Int)
 * - futureproofing against onyx api changes
 */
enum class Stroke(val style: Int) {
    PENCIL(TouchHelper.STROKE_STYLE_PENCIL),
    FOUNTAIN(TouchHelper.STROKE_STYLE_FOUNTAIN),
    MARKER(TouchHelper.STROKE_STYLE_MARKER),
    NEO_BRUSH(TouchHelper.STROKE_STYLE_NEO_BRUSH),
    CHARCOAL(TouchHelper.STROKE_STYLE_CHARCOAL),
    DASH(TouchHelper.STROKE_STYLE_DASH),
    CHARCOAL2(TouchHelper.STROKE_STYLE_CHARCOAL_V2),
}