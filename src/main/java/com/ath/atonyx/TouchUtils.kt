package com.ath.atonyx

import android.content.Context
import android.graphics.Rect
import android.view.View
import com.onyx.android.sdk.api.device.epd.EpdController

object TouchUtils {
    @JvmStatic
    fun disableFingerTouch(context: Context) {
        val width = context.resources.displayMetrics.widthPixels
        val height = context.resources.displayMetrics.heightPixels
        val rect = Rect(0, 0, width, height)
        val arrayRect = arrayOf(rect)
        EpdController.setAppCTPDisableRegion(context, arrayRect)
    }

    @JvmStatic
    fun enableFingerTouch(context: Context?) {
        EpdController.appResetCTPDisableRegion(context)
    }

    @JvmStatic
    fun getRelativeRect(parentView: View, childView: View): Rect {
        val parent = IntArray(2)
        val child = IntArray(2)
        parentView.getLocationOnScreen(parent)
        childView.getLocationOnScreen(child)
        val rect = Rect()
        childView.getLocalVisibleRect(rect)
        rect.offset(child[0] - parent[0], child[1] - parent[1])
        return rect
    }
}