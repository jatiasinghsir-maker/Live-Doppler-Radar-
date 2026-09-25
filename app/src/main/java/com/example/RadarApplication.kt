package com.example

import android.app.Application
import android.content.Context
import android.system.Os
import android.util.Log

class RadarApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        try {
            // Configure graphics driver environment before native EGL initialization
            Os.setenv("LIBGL_ALWAYS_SOFTWARE", "1", true)
            Os.setenv("LIBGL_DRI3_DISABLE", "1", true)
            Os.setenv("MESA_LOADER_DRIVER_OVERRIDE", "swrast", true)
            Os.setenv("MESA_NO_ERROR", "1", true)
        } catch (e: Throwable) {
            // Ignore environment setting issues
        }
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("RadarApplication", "Application process initialized successfully.")
    }
}
