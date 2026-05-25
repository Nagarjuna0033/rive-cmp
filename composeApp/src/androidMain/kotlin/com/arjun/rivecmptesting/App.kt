package com.arjun.rivecmptesting

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.logger.Level

class AndroidApp : Application() {
    override fun onCreate() {
        super.onCreate()
        initKoin {
            androidContext(this@AndroidApp) // Provides the Android app context
            androidLogger(Level.DEBUG) // Enables Koin's logging for debugging
        }
    }
}