package com.ath.atonyx

import androidx.lifecycle.Lifecycle

// Q: Why wrap Lifecycle.Event ??
// A: Because Google corrupted it with "ON_ANY"
enum class LC(event: Lifecycle.Event) {
    ON_CREATE(Lifecycle.Event.ON_CREATE),
    ON_START(Lifecycle.Event.ON_START),
    ON_RESUME(Lifecycle.Event.ON_RESUME),
    ON_PAUSE(Lifecycle.Event.ON_PAUSE),
    ON_STOP(Lifecycle.Event.ON_STOP),
    ON_DESTROY(Lifecycle.Event.ON_DESTROY),
}