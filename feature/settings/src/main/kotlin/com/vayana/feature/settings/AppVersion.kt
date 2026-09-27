package com.vayana.feature.settings

import android.content.Context
import android.os.Build

internal data class AppVersion(val name: String?, val code: Long)

/** This build's version name and code, as shown on About and in diagnostics reports. */
internal fun Context.appVersion(): AppVersion {
    val packageInfo = packageManager.getPackageInfo(packageName, 0)
    val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        packageInfo.longVersionCode
    } else {
        @Suppress("DEPRECATION")
        packageInfo.versionCode.toLong()
    }
    return AppVersion(name = packageInfo.versionName, code = code)
}
