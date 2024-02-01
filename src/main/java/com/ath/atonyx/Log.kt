package com.ath.atonyx

internal object Log {
    var TAG = "AtOnyx"

    fun d(msg: String) {
        android.util.Log.d(TAG, msg)
    }
}