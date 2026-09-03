// Kotlin <-> foliate-js glue. Exposes window.VayanaReader for Android to call via
// evaluateJavascript, and forwards foliate-js events to Android via the injected
// `AndroidBridge` @JavascriptInterface (see FoliateBookEngine.kt).
import './foliate/view.js'
import { Overlayer } from './foliate/overlayer.js'

let view = null
const renderedAnnotations = new Set()
const selectionTimers = new WeakMap()
let readerSideMarginPercent = 10

// Dynamic page-count estimate: foliate's paginator lays out each chapter into CSS columns
// sized by the live font-size/line-height/margin, so `renderer.pages` for the chapter you're
// on is already a real, current measurement. We sample it per chapter into `bytesPerPage` and
// extrapolate a whole-book estimate from each section's known byte size — no extra rendering,
// just arithmetic over numbers the renderer already computed for the current page turn.
let sectionByteSizes = null
const bytesPerPage = new Map()

function resetPageEstimate() {
    bytesPerPage.clear()
}

function bookPageStats(sectionIndex) {
    const renderer = view?.renderer
    if (!renderer || typeof renderer.pages !== 'number') return null
    const pages = renderer.pages
    const page = renderer.page
    if (!Number.isFinite(pages) || pages <= 0 || !Number.isFinite(page)) return null

    if (!sectionByteSizes) {
        sectionByteSizes = view.book.sections.map(s => s.linear !== 'no' && s.size > 0 ? s.size : 0)
    }
    // paginator reserves one blank column at each end for the header/footer margins.
    const pagesInSection = Math.max(1, pages - 2)
    const pageInSection = Math.min(pagesInSection, Math.max(1, page - 1))

    const sectionSize = sectionByteSizes[sectionIndex] || 0
    if (sectionSize > 0) bytesPerPage.set(sectionIndex, sectionSize / pagesInSection)

    let knownSize = 0
    let knownPages = 0
    for (const [i, bpp] of bytesPerPage) {
        knownSize += sectionByteSizes[i]
        knownPages += sectionByteSizes[i] / bpp
    }
    const avgBytesPerPage = knownPages > 0 ? knownSize / knownPages : (sectionSize / pagesInSection || 1600)

    const estimatePages = (fromIndex, toIndex) => {
        let total = 0
        for (let i = fromIndex; i < toIndex; i++) {
            const size = sectionByteSizes[i]
            if (!size) continue
            total += size / (bytesPerPage.get(i) ?? avgBytesPerPage)
        }
        return total
    }

    const pagesBefore = Math.round(estimatePages(0, sectionIndex))
    const pagesAfter = Math.round(estimatePages(sectionIndex + 1, sectionByteSizes.length))
    return {
        currentPage: pagesBefore + pageInSection,
        totalPages: pagesBefore + pagesInSection + pagesAfter,
    }
}

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
        sectionByteSizes = null
        resetPageEstimate()
        view.addEventListener('relocate', e => {
            const { cfi, fraction, tocItem, section, time } = e.detail
            const pageStats = bookPageStats(section?.current ?? 0)
            post('relocate', {
                cfi,
                fraction,
                tocLabel: tocItem?.label?.trim?.() ?? null,
                currentPage: pageStats?.currentPage ?? null,
                totalPages: pageStats?.totalPages ?? null,
                // Minutes remaining at foliate's fixed reading-speed assumption (chars/min) —
                // text remaining to read, so unlike page count this is independent of font size.
                chapterMinutesLeft: Number.isFinite(time?.section) ? time.section : null,
                bookMinutesLeft: Number.isFinite(time?.total) ? time.total : null,
            })
        })
        view.addEventListener('load', e => {
            const { doc, index } = e.detail
            wireSelection(doc, index)
            matchTextAnnotationsForDoc(doc, index)
            post('pageLoaded', {})
        })
        view.addEventListener('draw-annotation', e => {
            const { draw, annotation } = e.detail
            const color = annotation.color ?? '#888888'
            if (annotation.type === 'underline') {
                draw((range, options) => {
                    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
                    g.classList.add('vayana-dotted-underline')
                    const rects = range.getClientRects()
                    const strokeWidth = 1.5
                    for (const { left, bottom, width } of rects) {
                        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line')
                        line.setAttribute('x1', left)
                        line.setAttribute('y1', bottom - strokeWidth)
                        line.setAttribute('x2', left + width)
                        line.setAttribute('y2', bottom - strokeWidth)
                        line.setAttribute('stroke', color)
                        line.setAttribute('stroke-width', strokeWidth)
                        line.setAttribute('stroke-dasharray', '3,3')
                        g.append(line)
                    }
                    if (annotation.note && rects.length > 0) {
                        const lastRect = rects[rects.length - 1]
                        const badge = document.createElementNS('http://www.w3.org/2000/svg', 'text')
                        badge.setAttribute('x', lastRect.right + 4)
                        badge.setAttribute('y', lastRect.bottom - 1)
                        badge.setAttribute('fill', color)
                        badge.setAttribute('font-size', '9px')
                        badge.setAttribute('font-family', 'sans-serif')
                        badge.setAttribute('opacity', '0.75')
                        badge.textContent = `· ${annotation.note}`
                        g.append(badge)
                    }
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
    const viewportWidth = window.visualViewport?.width || innerWidth
    const marginPx = Math.round(viewportWidth * readerSideMarginPercent / 100)
    const maxInlineSizePx = Math.max(240, Math.round(viewportWidth - marginPx * 2))
    if (view?.renderer) {
        view.renderer.setAttribute('max-inline-size', `${maxInlineSizePx}px`)
        view.renderer.render?.()
    }
}

function applyStyle(css, sideMarginPercent) {
    // Font/line-height changes invalidate every sampled bytes-per-page density: a chapter
    // measured under the old layout no longer reflects how many pages it takes now.
    resetPageEstimate()
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

let activeAnnotationsList = []

async function renderAnnotations(annotations) {
    if (!view) return
    activeAnnotationsList = annotations || []
    const nextValues = new Set(annotations.map(annotation => annotation.value))
    for (const value of renderedAnnotations) {
        if (!nextValues.has(value)) await view.deleteAnnotation({ value })
    }
    for (const annotation of annotations) {
        if (annotation.value && !annotation.value.startsWith('text:')) {
            await view.addAnnotation(annotation)
            renderedAnnotations.add(annotation.value)
        }
    }
    for (const value of Array.from(renderedAnnotations)) {
        if (!nextValues.has(value)) renderedAnnotations.delete(value)
    }
    // Also match any active documents
    for (const { doc, index } of view.renderer.getContents()) {
        if (doc) matchTextAnnotationsForDoc(doc, index)
    }
}

function matchTextAnnotationsForDoc(doc, index) {
    if (!activeAnnotationsList || !activeAnnotationsList.length || !view) return
    for (const ann of activeAnnotationsList) {
        const textToFind = ann.text || (ann.value.startsWith('text:') ? null : null)
        if (!textToFind || textToFind.length < 5) continue
        const range = findTextRangeInDoc(doc, textToFind)
        if (range) {
            try {
                const cfi = view.getCFI(index, range)
                view.addAnnotation({
                    value: cfi,
                    type: ann.type || 'underline',
                    color: ann.color || '#888888',
                    note: ann.note,
                })
                renderedAnnotations.add(cfi)
            } catch (_) {}
        }
    }
}

function findTextRangeInDoc(doc, text) {
    try {
        const target = text.replace(/\s+/g, ' ').trim().toLowerCase()
        const bodyText = (doc.body?.innerText || doc.body?.textContent || '').replace(/\s+/g, ' ').toLowerCase()
        const searchSample = target.substring(0, Math.min(30, target.length))
        if (!bodyText.includes(searchSample)) return null

        const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, null, false)
        const textNodes = []
        let fullDocText = ''
        let node
        while ((node = walker.nextNode())) {
            const str = node.textContent
            const start = fullDocText.length
            fullDocText += str
            textNodes.push({ node, start, end: fullDocText.length })
        }

        const normDoc = fullDocText.replace(/\s+/g, ' ').toLowerCase()
        let matchIdx = normDoc.indexOf(target)
        let matchLen = target.length
        if (matchIdx === -1) {
            const sample = target.substring(0, Math.min(50, target.length))
            matchIdx = normDoc.indexOf(sample)
            matchLen = sample.length
        }
        if (matchIdx === -1) return null

        let startNode = null, startOffset = 0, endNode = null, endOffset = 0
        for (const { node, start, end } of textNodes) {
            if (!startNode && matchIdx >= start && matchIdx < end) {
                startNode = node
                startOffset = Math.min(matchIdx - start, node.textContent.length)
            }
            if (startNode && matchIdx + matchLen <= end) {
                endNode = node
                endOffset = Math.min((matchIdx + matchLen) - start, node.textContent.length)
                break
            }
        }
        if (startNode && endNode) {
            const range = doc.createRange()
            range.setStart(startNode, startOffset)
            range.setEnd(endNode, endOffset)
            return range
        }
    } catch (_) {}
    return null
}

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle, renderAnnotations, clearSelection }
addEventListener('resize', () => applyReaderMargin(readerSideMarginPercent))
post('ready', {})
