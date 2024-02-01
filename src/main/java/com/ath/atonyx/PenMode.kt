package com.ath.atonyx

enum class PenMode {
    /** Adds strokes */
    PEN,

    /** Removes whole strokes that contact the pen */
    ERASE_STROKE,

    /** NOT-IMPLEMENTED - Cuts a stroke at the pen-width intersects and removes the intersection */
    // ERASE_INTERSECT
}