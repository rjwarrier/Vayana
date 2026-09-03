// Kotlin <-> foliate-js glue. Exposes window.VayanaReader for Android to call via
// evaluateJavascript, and forwards foliate-js events to Android via the injected
// `AndroidBridge` @JavascriptInterface (see FoliateBookEngine.kt).
import './foliate/view.js'
import { Overlayer } from './foliate/overlayer.js'

let view = null
const renderedAnnotations = new Set()
const selectionTimers = new WeakMap()
let readerSideMarginPercent = 10

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
        view.addEventListener('load', e => {
            const { doc, index } = e.detail
            wireSelection(doc, index)
            post('pageLoaded', {})
        })
        view.addEventListener('draw-annotation', e => {
            const { draw, annotation } = e.detail
            const color = annotation.color ?? '#f6c453'
            if (annotation.type === 'underline') {
                draw((range, options) => {
                    const g = Overlayer.underline(range, options)
                    g.setAttribute('fill', color)
                    return g
                })
            } else {
                draw((range, options) => {
                    const g = Overlayer.highlight(range, options)
                    g.style.fill = color
                    return g
                })
            }
        })

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

function applyReaderMargin(percent) {
    readerSideMarginPercent = Math.max(0, Math.min(24, Number(percent) || 0))
    const marginPx = Math.round(innerWidth * readerSideMarginPercent / 100)
    if (view?.renderer) {
        view.renderer.setAttribute('margin', `${marginPx}px`)
        view.renderer.render?.()
    }
}

function applyStyle(css, sideMarginPercent) {
    if (view?.renderer?.setStyles) view.renderer.setStyles(css)
    applyReaderMargin(sideMarginPercent)
}

function wireSelection(doc, index) {
    doc.addEventListener('selectionchange', () => {
        clearTimeout(selectionTimers.get(doc))
        selectionTimers.set(doc, setTimeout(() => postSelection(doc, index), 150))
    })
}

function postSelection(doc, index) {
    const selection = doc.getSelection()
    if (!selection || !selection.rangeCount || selection.isCollapsed) {
        post('selection', null)
        return
    }
    const selectedText = selection.toString().trim()
    if (!selectedText) {
        post('selection', null)
        return
    }
    const range = selection.getRangeAt(0).cloneRange()
    post('selection', {
        cfi: view.getCFI(index, range),
        selectedText,
        tocLabel: view.getProgressOf(index, range)?.tocItem?.label?.trim?.() ?? null,
    })
}

function clearSelection() {
    if (!view) return
    for (const { doc } of view.renderer.getContents()) doc.getSelection()?.removeAllRanges()
    post('selection', null)
}

async function renderAnnotations(annotations) {
    if (!view) return
    const nextValues = new Set(annotations.map(annotation => annotation.value))
    for (const value of renderedAnnotations) {
        if (!nextValues.has(value)) await view.deleteAnnotation({ value })
    }
    for (const annotation of annotations) {
        await view.addAnnotation(annotation)
        renderedAnnotations.add(annotation.value)
    }
    for (const value of Array.from(renderedAnnotations)) {
        if (!nextValues.has(value)) renderedAnnotations.delete(value)
    }
}

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle, renderAnnotations, clearSelection }
addEventListener('resize', () => applyReaderMargin(readerSideMarginPercent))
post('ready', {})
