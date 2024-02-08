package com.ath.atonyx

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View

object AndroidUtils {

    @JvmStatic
    fun getActivityFrom(context: Context): Activity? {
        Log.d("AtOnyx.getActivityFrom context=$context")
        return getActivityFrom(context as ContextWrapper)
    }

    @JvmStatic
    fun getActivityFrom(context: ContextWrapper): Activity? {
        Log.d("AtOnyx.getActivityFrom contextWrapper=$context")
        return when (context) {
            is Activity -> getActivityFrom(context as Activity)
            else ->
                if (context.baseContext != null) getActivityFrom(context.baseContext)
                else null
        }
    }

    @JvmStatic
    fun getActivityFrom(context: Activity): Activity {
        Log.d("AtOnyx.getActivityFrom activity=$context")
        return context
    }

    @JvmStatic
    fun getActivityFrom(view: View): Activity? = getActivityFrom(view.context)

    @JvmStatic
    fun requireActivity(context: Context): Activity {
        return getActivityFrom(context)
            ?: throw IllegalStateException("Context was not an Activity")
    }

    @JvmStatic
    fun requireActivity(view: View): Activity {
        return getActivityFrom(view)
            ?: throw IllegalStateException("Context was not an Activity")
    }

}