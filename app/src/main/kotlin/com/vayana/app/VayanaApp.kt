package com.vayana.app

import android.app.Application
import android.content.Context
import com.vayana.core.common.AppLanguage
import com.vayana.core.diagnostics.CrashReporter
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VayanaApp : Application() {

    @Inject lateinit var crashReporter: CrashReporter

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(AppLanguage.wrap(base))
    }

    override fun onCreate() {
        super.onCreate()
        crashReporter.install()
    }
}
