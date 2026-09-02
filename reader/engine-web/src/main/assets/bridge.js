// Kotlin <-> foliate-js glue. Exposes window.VayanaReader for Android to call via
// evaluateJavascript, and forwards foliate-js events to Android via the injected
// `AndroidBridge` @JavascriptInterface (see FoliateBookEngine.kt).
import './foliate/view.js'

let view = null

function post(type, payload) {
    if (window.AndroidBridge) window.AndroidBridge.onEvent(type, JSON.stringify(payload ?? {}))
}

window.addEventListener('error', e => {
    post('error', { message: `${e.message} (${e.filename}:${e.lineno})` })
})
window.addEventListener('unhandledrejection', e => {
    post('error', { message: `Unhandled rejection: ${e.reason && e.reason.stack || e.reason}` })
})

function tocToPlain(items) {
    if (!items) return []
    return items.map(item => ({
        label: item.label?.trim?.() ?? item.label ?? '',
        href: item.href ?? '',
        children: tocToPlain(item.subitems),
    }))
}

async function open(bookUrl, lastLocatorCfi) {
    try {
        post('log', { step: 'fetching', bookUrl })
        const res = await fetch(bookUrl)
        post('log', { step: 'fetched', ok: res.ok, status: res.status, contentType: res.headers.get('content-type') })

        view = document.createElement('foliate-view')
        document.body.append(view)
        view.addEventListener('relocate', e => {
            const { cfi, fraction, tocItem } = e.detail
            post('relocate', { cfi, fraction, tocLabel: tocItem?.label?.trim?.() ?? null })
        })
        view.addEventListener('load', () => post('pageLoaded', {}))

        post('log', { step: 'view.open' })
        await view.open(bookUrl)
        post('log', { step: 'view.init' })
        await view.init({ lastLocation: lastLocatorCfi || undefined, showTextStart: !lastLocatorCfi })
        post('log', { step: 'view.init done' })

        post('opened', { toc: tocToPlain(view.book.toc), title: view.book.metadata?.title ?? '' })
    } catch (err) {
        post('error', { message: String(err && err.stack || err) })
    }
}

function next() { view?.next() }
function prev() { view?.prev() }
function goLeft() { view?.goLeft() }
function goRight() { view?.goRight() }
function goToFraction(fraction) { view?.goToFraction(fraction) }
function goToHref(href) { view?.goTo(href) }

function applyStyle(css) {
    if (view?.renderer?.setStyles) view.renderer.setStyles(css)
}

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle }
post('ready', {})
