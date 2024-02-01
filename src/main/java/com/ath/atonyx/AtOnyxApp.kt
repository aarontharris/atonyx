package com.ath.atonyx

import android.app.Application
import com.onyx.android.sdk.rx.RxManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

object AtOnyxApp {

    fun onCreate(application: Application) {
        RxManager.Builder.initAppContext(application)
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

}