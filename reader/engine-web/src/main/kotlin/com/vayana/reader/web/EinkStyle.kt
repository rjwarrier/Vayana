package com.vayana.reader.web

import com.vayana.reader.api.ReadTheme

/** Keep slow-refresh rendering independent of whether the panel can display color. */
internal fun einkStyleCss(theme: ReadTheme): String = buildString {
    if (!theme.eink) return@buildString
    append("*,*::before,*::after{")
    append("animation:none !important;transition:none !important;")
    append("text-shadow:none !important;box-shadow:none !important;filter:none !important;}")
    append("a{text-decoration:underline !important;")
    if (theme.monochrome) append("color:${theme.textColorArgb.toCssColor()} !important;")
    append("}")
    if (theme.monochrome) {
        append("img,svg,video,canvas{filter:grayscale(1) contrast(1.15) !important;}")
    }
}
