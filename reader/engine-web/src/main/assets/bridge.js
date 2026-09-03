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
            for (const { doc, index } of view.renderer.getContents()) {
                if (doc) matchTextAnnotationsForDoc(doc, index)
            }
        })
        view.addEventListener('load', e => {
            const { doc, index } = e.detail
            wireSelection(doc, index)
            matchTextAnnotationsForDoc(doc, index)
            post('pageLoaded', {})
        })
        view.addEventListener('create-overlay', e => {
            const { index } = e.detail
            const obj = view.renderer.getContents().find(x => x.index === index)
            if (obj?.doc) matchTextAnnotationsForDoc(obj.doc, index)
        })
        view.addEventListener('draw-annotation', e => {
            const { draw, annotation } = e.detail
            const color = annotation.color ?? '#6366f1'
            if (annotation.type === 'underline') {
                draw((rects, options) => {
                    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
                    g.classList.add('vayana-dotted-underline')
                    const strokeWidth = 2
                    for (const { left, bottom, width } of rects) {
                        const line = document.createElementNS('http://www.w3.org/2000/svg', 'line')
                        line.setAttribute('x1', left)
                        line.setAttribute('y1', bottom - 1)
                        line.setAttribute('x2', left + width)
                        line.setAttribute('y2', bottom - 1)
                        line.setAttribute('stroke', color)
                        line.setAttribute('stroke-width', strokeWidth)
                        line.setAttribute('stroke-dasharray', '4,3')
                        line.setAttribute('stroke-linecap', 'round')
                        g.append(line)
                    }
                    const countMatch = annotation.note ? String(annotation.note).match(/\d+/) : null
                    const countText = countMatch ? countMatch[0] : (annotation.note || '')
                    if (countText && rects.length > 0) {
                        const firstRect = rects[0]
                        const badge = document.createElementNS('http://www.w3.org/2000/svg', 'text')
                        if (firstRect.left >= 14) {
                            badge.setAttribute('text-anchor', 'end')
                            badge.setAttribute('x', firstRect.left - 6)
                            badge.setAttribute('y', firstRect.bottom - 2)
                        } else {
                            const lastRect = rects[rects.length - 1]
                            badge.setAttribute('text-anchor', 'start')
                            badge.setAttribute('x', lastRect.right + 6)
                            badge.setAttribute('y', lastRect.bottom - 2)
                        }
                        badge.setAttribute('fill', color)
                        badge.setAttribute('font-size', '11px')
                        badge.setAttribute('font-weight', '600')
                        badge.setAttribute('font-family', 'sans-serif')
                        badge.setAttribute('opacity', '0.75')
                        badge.textContent = countText
                        g.append(badge)
                    }
                    return g
                })
            } else {
                draw((rects, options) => {
                    const g = Overlayer.highlight(rects, options)
                    g.style.fill = color
                    return g
                })
            }
        })

        post('log', { step: 'view.open' })
        await view.open(bookUrl)
        post('log', { step: 'view.init' })
        const isStandardCfi = lastLocatorCfi && (lastLocatorCfi.startsWith('epubcfi(') || lastLocatorCfi.includes('.xhtml') || lastLocatorCfi.includes('.html'))
        const initialLocation = isStandardCfi ? lastLocatorCfi : undefined
        await view.init({ lastLocation: initialLocation, showTextStart: !initialLocation })
        post('log', { step: 'view.init done' })

        post('opened', { toc: tocToPlain(view.book.toc), title: view.book.metadata?.title ?? '' })

        if (lastLocatorCfi && !isStandardCfi) {
            setTimeout(async () => {
                await goToHref(lastLocatorCfi)
            }, 300)
        }
    } catch (err) {
        post('error', { message: String(err && err.stack || err) })
    }
}

function next() { view?.next() }
function prev() { view?.prev() }
function goLeft() { view?.goLeft() }
function goRight() { view?.goRight() }
function goToFraction(fraction) { view?.goToFraction(fraction) }

async function goToHref(href) {
    if (!view || !href) return
    if (href.startsWith('epubcfi(') || href.includes('#') || href.endsWith('.xhtml') || href.endsWith('.html') || href.endsWith('.htm')) {
        try {
            await view.goTo(href)
            return
        } catch (_) {}
    }

    // Try finding matching annotation from activeAnnotationsList
    const ann = activeAnnotationsList.find(a =>
        a.value === href ||
        a.id === href ||
        (a.value && href.includes(a.value)) ||
        (a.id && href.endsWith(a.id))
    )
    const textToFind = ann?.text || (href.length > 8 && !href.startsWith('quote:') && !href.startsWith('text:') ? href : null)

    if (textToFind) {
        const cfi = await findCfiInBook(textToFind)
        if (cfi) {
            try {
                await view.goTo(cfi)
                for (const { doc, index } of view.renderer.getContents()) {
                    if (doc) matchTextAnnotationsForDoc(doc, index)
                }
                return
            } catch (_) {}
        }
    }

    try {
        await view.goTo(href)
    } catch (_) {}
}

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
        if (annotation.value && !annotation.value.startsWith('text:') && !annotation.value.startsWith('quote:')) {
            await view.addAnnotation(annotation)
            renderedAnnotations.add(annotation.value)
        }
    }
    for (const value of Array.from(renderedAnnotations)) {
        if (!nextValues.has(value)) renderedAnnotations.delete(value)
    }
    // Also match any active documents in view
    for (const { doc, index } of view.renderer.getContents()) {
        if (doc) matchTextAnnotationsForDoc(doc, index)
    }
}

function matchTextAnnotationsForDoc(doc, index) {
    if (!activeAnnotationsList || !activeAnnotationsList.length || !view) return
    for (const ann of activeAnnotationsList) {
        const textToFind = ann.text
        if (!textToFind || textToFind.length < 5) continue
        const range = findTextRangeInDoc(doc, textToFind)
        if (range) {
            try {
                const cfi = view.getCFI(index, range)
                view.addAnnotation({
                    value: cfi,
                    type: ann.type || 'underline',
                    color: ann.color || '#6366f1',
                    note: ann.note,
                })
                renderedAnnotations.add(cfi)
            } catch (_) {}
        }
    }
}

function normalizeForMatching(str) {
    if (!str) return ''
    return str
        .replace(/&[a-z0-9#]+;/gi, ' ')
        .replace(/<[^>]+>/g, ' ')
        .normalize('NFD')
        .replace(/[\u0300-\u036f]/g, '') // Decomposed accents
        .replace(/\uFB00/g, 'ff')        // Ligatures
        .replace(/\uFB01/g, 'fi')
        .replace(/\uFB02/g, 'fl')
        .replace(/\uFB03/g, 'ffi')
        .replace(/\uFB04/g, 'ffl')
        .replace(/[\u2018\u2019\u201A\u201B\u2032`]/g, '') // Apostrophes/single quotes
        .replace(/[\u201C\u201D\u201E\u201F\u2033"]/g, '') // Double quotes
        .replace(/[\u2013\u2014\u2015-]/g, ' ')             // Hyphens and dashes
        .toLowerCase()
        .replace(/[^\p{L}\p{N}]/gu, '')  // Letters & digits only
}

function findBestMatch(cleanDoc, cleanTarget) {
    if (!cleanDoc || !cleanTarget) return null
    const targetLen = cleanTarget.length
    if (cleanDoc.length < 5 || targetLen < 5) return null

    // High confidence thresholds to completely eliminate false positives
    const minRequiredLen = targetLen >= 40
        ? Math.max(28, Math.floor(targetLen * 0.65))
        : targetLen >= 20
            ? Math.max(16, Math.floor(targetLen * 0.70))
            : Math.max(8, Math.floor(targetLen * 0.80))

    // 1. Direct full match (optimal case)
    const fullIdx = cleanDoc.indexOf(cleanTarget)
    if (fullIdx !== -1) return { matchIdx: fullIdx, matchLen: targetLen, score: 1.0 }

    let bestCandidate = null

    function evaluateCandidate(idx, probeLen, offsetInTarget) {
        if (idx === -1) return
        const startIdx = Math.max(0, idx - offsetInTarget)

        // Expand match forward
        let forwardLen = offsetInTarget + probeLen
        while (forwardLen < targetLen &&
               startIdx + forwardLen < cleanDoc.length &&
               cleanDoc[startIdx + forwardLen] === cleanTarget[forwardLen]) {
            forwardLen++
        }

        // Expand match backward if started with offset
        let backwardIdx = startIdx
        let targetBackIdx = 0
        while (backwardIdx > 0 && targetBackIdx < offsetInTarget &&
               cleanDoc[backwardIdx - 1] === cleanTarget[offsetInTarget - targetBackIdx - 1]) {
            backwardIdx--
            targetBackIdx++
        }

        const totalMatched = forwardLen + targetBackIdx
        const score = totalMatched / targetLen

        if (totalMatched >= minRequiredLen) {
            if (!bestCandidate || totalMatched > bestCandidate.matchLen) {
                bestCandidate = { matchIdx: backwardIdx, matchLen: totalMatched, score }
            }
        }
    }

    // 2. Distinctive prefix probing (searching all occurrences across chapter)
    const prefixProbes = [70, 50, 36, 26, 20, 16]
    for (const size of prefixProbes) {
        if (targetLen >= size) {
            const probe = cleanTarget.substring(0, size)
            let searchFrom = 0
            let idx
            while ((idx = cleanDoc.indexOf(probe, searchFrom)) !== -1) {
                evaluateCandidate(idx, size, 0)
                if (bestCandidate && bestCandidate.score >= 0.95) return bestCandidate
                searchFrom = idx + 1
            }
        }
    }

    // 3. Sliding probe search (for quotes with skipped words or introductory variations)
    for (let offset = 8; offset < Math.min(targetLen - 20, 60); offset += 8) {
        const probeLen = Math.min(24, targetLen - offset)
        if (probeLen >= 16) {
            const probe = cleanTarget.substring(offset, offset + probeLen)
            let searchFrom = 0
            let idx
            while ((idx = cleanDoc.indexOf(probe, searchFrom)) !== -1) {
                evaluateCandidate(idx, probeLen, offset)
                if (bestCandidate && bestCandidate.score >= 0.95) return bestCandidate
                searchFrom = idx + 1
            }
        }
    }
    return bestCandidate
}

function findMultiChunkMatch(cleanDoc, rawText) {
    if (!cleanDoc || !rawText) return null
    // Split raw text into natural clauses/phrases by punctuation or ellipses
    const clauses = rawText
        .split(/[.,;:!?…\n"]|\.{2,}/)
        .map(c => normalizeForMatching(c))
        .filter(c => c.length >= 10)

    if (clauses.length < 2) return null

    let firstMatch = null
    let lastMatch = null
    let matchedCount = 0
    let lastEndIdx = 0

    for (const clause of clauses) {
        const idx = cleanDoc.indexOf(clause, lastEndIdx)
        if (idx !== -1 && (lastEndIdx === 0 || idx - lastEndIdx < 800)) {
            if (!firstMatch) {
                firstMatch = { matchIdx: idx, matchLen: clause.length }
            }
            lastMatch = { matchIdx: idx, matchLen: clause.length }
            lastEndIdx = idx + clause.length
            matchedCount++
        }
    }

    if (matchedCount >= 2 && firstMatch && lastMatch) {
        const startIdx = firstMatch.matchIdx
        const endIdx = lastMatch.matchIdx + lastMatch.matchLen
        const totalSpan = endIdx - startIdx
        if (totalSpan > 0 && totalSpan < 2500) {
            return { matchIdx: startIdx, matchLen: totalSpan, score: 0.92 }
        }
    }
    return null
}

function findTextRangeInDoc(doc, text) {
    if (!doc || !doc.body || !text) return null
    try {
        const cleanTarget = normalizeForMatching(text)
        if (cleanTarget.length < 5) return null

        const walker = doc.createTreeWalker(
            doc.body,
            NodeFilter.SHOW_TEXT,
            {
                acceptNode(n) {
                    const tag = n.parentElement?.tagName?.toUpperCase()
                    if (tag === 'STYLE' || tag === 'SCRIPT' || tag === 'NOSCRIPT' || tag === 'TEMPLATE') {
                        return NodeFilter.FILTER_REJECT
                    }
                    return NodeFilter.FILTER_ACCEPT
                }
            },
            false
        )
        let node
        let cleanDoc = ''
        const charMap = [] // charMap[i] = { node, offset }

        while ((node = walker.nextNode())) {
            const str = node.textContent
            for (let offset = 0; offset < str.length; offset++) {
                const char = str[offset]
                if (/[\p{L}\p{N}]/u.test(char)) {
                    // Normalize accents / ligatures identically
                    const normChar = char.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
                    for (const c of normChar) {
                        if (/[\p{L}\p{N}]/u.test(c)) {
                            cleanDoc += c
                            charMap.push({ node, offset })
                        }
                    }
                }
            }
        }

        if (cleanDoc.length < 5) return null

        // Strategy 1: Contiguous best match
        let match = findBestMatch(cleanDoc, cleanTarget)

        // Strategy 2: Multi-chunk sequence match (for quotes with ellipses or omissions)
        if (!match || (match.score && match.score < 0.85)) {
            const chunkMatch = findMultiChunkMatch(cleanDoc, text)
            if (chunkMatch) match = chunkMatch
        }

        if (!match || match.matchIdx === -1 || match.matchIdx >= charMap.length) return null

        const { matchIdx, matchLen } = match
        const start = charMap[matchIdx]
        const endCharIdx = Math.min(matchIdx + matchLen - 1, charMap.length - 1)
        const end = charMap[endCharIdx]

        if (start && end) {
            const range = doc.createRange()
            range.setStart(start.node, start.offset)
            range.setEnd(end.node, end.offset + 1)
            return range
        }
    } catch (_) {}
    return null
}

async function findCfiInBook(text) {
    if (!text || !view?.book?.sections) return null
    let bestSectionResult = null

    for (let i = 0; i < view.book.sections.length; i++) {
        const section = view.book.sections[i]
        if (!section.createDocument) continue
        try {
            const doc = await section.createDocument()
            const range = findTextRangeInDoc(doc, text)
            if (range) {
                const cfi = view.getCFI(i, range)
                return cfi // Found exact valid section
            }
        } catch (_) {}
    }
    return null
}

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle, renderAnnotations, clearSelection }
addEventListener('resize', () => applyReaderMargin(readerSideMarginPercent))
post('ready', {})
