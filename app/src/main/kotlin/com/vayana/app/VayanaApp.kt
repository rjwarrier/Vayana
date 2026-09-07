package com.vayana.app

import android.app.Application
import com.vayana.core.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VayanaApp : Application() {

    @Inject lateinit var crashReporter: CrashReporter

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
    }
}
