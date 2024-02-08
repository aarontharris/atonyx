package com.ath.atonyx

import android.app.Application
import com.onyx.android.sdk.rx.RxManager
import org.lsposed.hiddenapibypass.HiddenApiBypass

internal fun app(): Application = AtOnyxApp.application

object AtOnyxApp {
    internal lateinit var application: Application

    fun onCreate(application: Application) {
        this.application = application
        RxManager.Builder.initAppContext(application)
        checkHiddenApiBypass()
    }

    private fun checkHiddenApiBypass() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            HiddenApiBypass.addHiddenApiExemptions("")
        }
    }

}