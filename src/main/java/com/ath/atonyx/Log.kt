package com.ath.atonyx

internal object Log {
    var TAG = "AtOnyx"

    fun d(msg: String) {
        android.util.Log.d(TAG, msg)
    }

    fun e(msg: String, throwable: Throwable? = null) {
        android.util.Log.e(TAG, msg)
        if (throwable != null) android.util.Log.e(TAG, throwable.message, throwable)
    }

    fun e(throwable: Throwable?) {
        if (throwable != null) android.util.Log.e(TAG, throwable.message, throwable)
    }

}