package com.robotkinematicslab.mobile.logging

import android.util.Log

object AppLog {

    const val ENABLED = false
    // You can temporarily force:
    // val ENABLED = true
    // val ENABLED = false

    inline fun d(tag: String, message: () -> String) {
        if (ENABLED) Log.d(tag, message())
    }

    inline fun i(tag: String, message: () -> String) {
        if (ENABLED) Log.i(tag, message())
    }

    inline fun w(tag: String, message: () -> String) {
        if (ENABLED) Log.w(tag, message())
    }

    inline fun e(tag: String, message: () -> String) {
        if (ENABLED) Log.e(tag, message())
    }
}
