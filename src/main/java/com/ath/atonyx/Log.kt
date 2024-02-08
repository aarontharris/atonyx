package com.ath.atonyx

internal object Log {
    var TAG = "AtOnyx"
    var TEST = false

    private fun logd(msg: String) {
        if (TEST) println(msg) else android.util.Log.d(TAG, msg)
    }

    private fun loge(msg: String) {
        if (TEST) System.err.println(msg) else android.util.Log.e(TAG, msg)
    }

    private fun loge(msg: String, throwable: Throwable?) {
        if (TEST) {
            System.err.println(msg)
            throwable?.printStackTrace()
        } else android.util.Log.e(TAG, msg)
    }

    fun d(msg: String) = logd(msg)

    fun e(msg: String, throwable: Throwable? = null) = loge(msg, throwable)

    fun e(throwable: Throwable?) {
        if (throwable != null) loge(throwable.message ?: "", throwable)
    }

}