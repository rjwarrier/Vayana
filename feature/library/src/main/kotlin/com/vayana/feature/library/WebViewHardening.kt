package com.vayana.feature.library

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView

/** Settings shared by the in-app browsers that show third-party sites (Goodreads, cover image search). */
internal fun WebView.hardenForBrowsing() {
    settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
    settings.safeBrowsingEnabled = true
    CookieManager.getInstance().setAcceptThirdPartyCookies(this, false)
}
