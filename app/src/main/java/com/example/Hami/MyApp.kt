package com.example.Hami

import android.app.Application

class MyApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // ONNX Runtime doesn't need special initialization
        // Keep this file for app functionality
    }
}