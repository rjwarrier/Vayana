// Kotlin <-> foliate-js glue. Exposes window.VayanaReader for Android to call via
// evaluateJavascript, and forwards foliate-js events to Android via the injected
// `AndroidBridge` @JavascriptInterface (see FoliateBookEngine.kt).
import './foliate/view.js'
import { Overlayer } from './foliate/overlayer.js'

let view = null
const renderedAnnotations = new Set()
const resolvedTextAnnotations = new Map()
const resolvedTextFingerprints = new Map()
const pendingTextAnnotations = new Set()
const authoritativeSourceForCfi = new Map()
const standardAnnotationFingerprints = new Map()
const documentTextIndexes = new WeakMap()
const unmatchedInDoc = new WeakMap()
const selectionTimers = new WeakMap()
let readerSideMarginPercent = 10
let hasOpened = false

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

    // Start page of every TOC entry, numbered the same way as currentPage. Entries that share a
    // section (anchors within one file) share its start page - placing them finer needs layout.
    const sectionStartPages = []
    let runningPages = 0
    for (let i = 0; i < sectionByteSizes.length; i++) {
        sectionStartPages.push(Math.round(runningPages) + 1)
        runningPages += estimatePages(i, i + 1)
    }
    const tocPages = {}
    for (const [href, index] of tocSectionIndexes()) {
        if (index < sectionStartPages.length) tocPages[href] = sectionStartPages[index]
    }

    return {
        currentPage: pagesBefore + pageInSection,
        totalPages: pagesBefore + pagesInSection + pagesAfter,
        tocPages,
    }
}

// TOC href -> section index, resolved once per book for the contents panel's page numbers.
let tocSectionIndexCache = null

function tocSectionIndexes() {
    if (tocSectionIndexCache) return tocSectionIndexCache
    const indexes = new Map()
    const walk = items => {
        for (const item of items ?? []) {
            if (item.href && !indexes.has(item.href)) {
                try {
                    const index = view.book.resolveHref(item.href)?.index
                    if (Number.isInteger(index) && index >= 0) indexes.set(item.href, index)
                } catch (_) {}
            }
            walk(item.subitems)
        }
    }
    walk(view.book.toc)
    tocSectionIndexCache = indexes
    return indexes
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

// view.init() has no fallback of its own - if resuming to a saved locator fails outright
// (not just a load stall, which paginator.js's own watchdog already recovers from - e.g. a
// stale/invalid CFI), retry from the start of the book, and if even that fails, jump straight
// to the first linear section. Without this ladder a single bad resume CFI fails the whole
// book open with no way to recover.
async function initWithFallback(initialLocation) {
    if (initialLocation) {
        try {
            return await view.init({ lastLocation: initialLocation, showTextStart: false })
        } catch (error) {
            post('log', { step: 'resumeFallback', message: String(error && error.message || error) })
        }
    }
    try {
        return await view.init({ lastLocation: null, showTextStart: true })
    } catch (error) {
        post('log', { step: 'directSectionFallback', message: String(error && error.message || error) })
        const firstLinearSection = view.book.sections.findIndex(section => section.linear !== 'no')
        if (firstLinearSection < 0) throw error
        return view.goTo(firstLinearSection)
    }
}

async function open(bookUrl, lastLocatorCfi) {
    let phase = 'starting'
    const watchdog = setInterval(() => {
        post('log', { step: 'openWatchdog', phase })
    }, 5000)
    try {
        renderedAnnotations.clear()
        resolvedTextAnnotations.clear()
        resolvedTextFingerprints.clear()
        pendingTextAnnotations.clear()
        authoritativeSourceForCfi.clear()
        standardAnnotationFingerprints.clear()
        hasOpened = false
        phase = 'fetching book'
        post('log', { step: 'fetching', bookUrl })
        const res = await fetch(bookUrl)
        post('log', { step: 'fetched', ok: res.ok, status: res.status, contentType: res.headers.get('content-type') })
        if (!res.ok) throw new Error(`Book fetch failed: ${res.status} ${res.statusText}`)
        const bookBlob = await res.blob()
        const bookFile = new File([bookBlob], 'current')

        phase = 'creating view'
        view = document.createElement('foliate-view')
        document.body.append(view)
        let firstRenderResolve = null
        const firstRender = new Promise(resolve => {
            firstRenderResolve = resolve
        })
        const markFirstRender = source => {
            if (!firstRenderResolve) return
            post('log', { step: 'firstRender', source })
            firstRenderResolve(source)
            firstRenderResolve = null
        }
        sectionByteSizes = null
        tocSectionIndexCache = null
        resetPageEstimate()
        view.addEventListener('relocate', e => {
            const { cfi, fraction, tocItem, section, time } = e.detail
            const pageStats = bookPageStats(section?.current ?? 0)
            post('relocate', {
                cfi,
                fraction,
                tocLabel: tocItem?.label?.trim?.() ?? null,
                tocHref: tocItem?.href ?? null,
                currentPage: pageStats?.currentPage ?? null,
                totalPages: pageStats?.totalPages ?? null,
                tocPages: pageStats?.tocPages ?? null,
                // Minutes remaining at foliate's fixed reading-speed assumption (chars/min) —
                // text remaining to read, so unlike page count this is independent of font size.
                chapterMinutesLeft: Number.isFinite(time?.section) ? time.section : null,
                bookMinutesLeft: Number.isFinite(time?.total) ? time.total : null,
            })
            markFirstRender('relocate')
            if (hasOpened) {
                for (const { doc, index } of view.renderer.getContents()) {
                    if (doc) queueDocumentEnhancements(doc, index)
                }
            }
        })
        view.addEventListener('load', e => {
            const { doc, index } = e.detail
            wireSelection(doc, index)
            post('pageLoaded', {})
            markFirstRender('load')
            if (hasOpened) queueDocumentEnhancements(doc, index)
        })
        view.addEventListener('create-overlay', e => {
            const { index } = e.detail
            const obj = view.renderer.getContents().find(x => x.index === index)
            if (obj?.doc && hasOpened) queueDocumentEnhancements(obj.doc, index)
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
        phase = 'opening book package'
        await view.open(bookFile)
        post('log', { step: 'view.init' })
        phase = 'initializing book view'
        const isStandardCfi = lastLocatorCfi && (lastLocatorCfi.startsWith('epubcfi(') || lastLocatorCfi.includes('.xhtml') || lastLocatorCfi.includes('.html'))
        const initialLocation = isStandardCfi ? lastLocatorCfi : undefined
        const initPromise = initWithFallback(initialLocation)
            .then(() => post('log', { step: 'view.init done' }))
            .catch(error => {
                post('log', { step: 'view.init failed', message: String(error && error.message || error) })
                throw error
            })
        const openSource = await Promise.race([initPromise.then(() => 'init'), firstRender])
        if (openSource !== 'init') {
            initPromise.catch(() => {})
            post('log', { step: 'view.init pending', openedBy: openSource })
        }

        hasOpened = true
        post('opened', { toc: tocToPlain(view.book.toc), title: view.book.metadata?.title ?? '' })
        for (const { doc, index } of view.renderer.getContents()) {
            if (doc) queueDocumentEnhancements(doc, index)
        }

        if (lastLocatorCfi && !isStandardCfi) {
            setTimeout(async () => {
                await goToHref(lastLocatorCfi)
            }, 300)
        }
    } catch (err) {
        post('error', { message: String(err && err.stack || err) })
    } finally {
        clearInterval(watchdog)
    }
}

// 'relocate' can fire many times in a row for the same page turn (each a no-op full document
// walk once bionic reading/annotations are already applied) - collapse repeats scheduled
// before the first one runs into a single pass instead of stacking up redundant timeouts.
const enhancementsPending = new WeakSet()
function queueDocumentEnhancements(doc, index) {
    if (enhancementsPending.has(doc)) return
    enhancementsPending.add(doc)
    setTimeout(() => {
        enhancementsPending.delete(doc)
        applyBionicReadingToDoc(doc)
        matchTextAnnotationsForDoc(doc, index)
    }, 0)
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
            } catch (error) {
                // Foliate can reject after its renderer has already accepted the location.
                // A virtual quote locator is never a valid fallback navigation target.
                post('log', { step: 'quoteNavigation', message: String(error) })
            } finally {
                for (const { doc, index } of view.renderer.getContents()) {
                    if (doc) matchTextAnnotationsForDoc(doc, index)
                }
            }
            return
        }
    }

    if (href.startsWith('quote:') || href.startsWith('text:')) {
        post('log', { step: 'quoteNotFound', value: href })
        return
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

const BionicWordClass = 'vayana-bionic-word'
let bionicReadingEnabled = false

function setBionicReading(enabled) {
    bionicReadingEnabled = Boolean(enabled)
    if (!view) return
    for (const { doc } of view.renderer.getContents()) {
        if (doc) applyBionicReadingToDoc(doc)
    }
}

function applyBionicReadingToDoc(doc) {
    if (!doc || !doc.body) return
    if (bionicReadingEnabled) {
        transformBionicWords(doc)
    } else {
        revertBionicWords(doc)
    }
    // Restructuring text nodes invalidates any cached (text -> DOM node) mapping for this doc.
    documentTextIndexes.delete(doc)
    unmatchedInDoc.delete(doc)
}

function transformBionicWords(doc) {
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT, {
        acceptNode(node) {
            const parent = node.parentElement
            if (!parent || !node.textContent || !/\S/.test(node.textContent)) return NodeFilter.FILTER_REJECT
            if (parent.closest(`.${BionicWordClass},style,script,noscript,template`)) return NodeFilter.FILTER_REJECT
            return NodeFilter.FILTER_ACCEPT
        },
    })
    const nodes = []
    let node
    while ((node = walker.nextNode())) nodes.push(node)
    for (const textNode of nodes) {
        const span = doc.createElement('span')
        span.className = BionicWordClass
        span.setAttribute('data-foliate-cfi-transparent', '')
        span.dataset.original = textNode.textContent
        span.innerHTML = bionicMarkup(textNode.textContent)
        textNode.replaceWith(span)
    }
}

function revertBionicWords(doc) {
    const spans = doc.body.querySelectorAll(`.${BionicWordClass}`)
    for (const span of spans) {
        span.replaceWith(doc.createTextNode(span.dataset.original ?? ''))
    }
    doc.body.normalize()
}

// Bolds roughly the first half of each word (a fixation point meant to guide the eye) while
// leaving the rest at normal weight. Escapes non-word characters itself since it builds HTML.
function bionicMarkup(text) {
    return text.replace(/[\p{L}\p{N}]+|[\s\S]/gu, chunk => {
        if (/^[\p{L}\p{N}]+$/u.test(chunk)) {
            const boldLength = Math.max(1, Math.ceil(chunk.length * 0.5))
            return `<b data-foliate-cfi-transparent>${chunk.slice(0, boldLength)}</b>${chunk.slice(boldLength)}`
        }
        return chunk === '&' ? '&amp;' : chunk === '<' ? '&lt;' : chunk === '>' ? '&gt;' : chunk
    })
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
    const rect = range.getBoundingClientRect()
    const viewportHeight = Math.max(doc.documentElement?.clientHeight ?? 0, doc.defaultView?.innerHeight ?? 0)
    post('selection', {
        cfi: view.getCFI(index, range),
        selectedText,
        tocLabel: view.getProgressOf(index, range)?.tocItem?.label?.trim?.() ?? null,
        verticalPosition: viewportHeight > 0
            ? Math.max(0, Math.min(1, (rect.top + rect.bottom) / 2 / viewportHeight))
            : null,
    })
}

function clearSelection() {
    if (!view) return
    for (const { doc } of view.renderer.getContents()) doc.getSelection()?.removeAllRanges()
    post('selection', null)
}

let searchToken = 0

async function search(query) {
    if (!view) return
    const myToken = ++searchToken
    const results = []
    try {
        for await (const result of view.search({ query })) {
            if (myToken !== searchToken) return // superseded by a newer search
            if (result === 'done' || !result.subitems) continue
            for (const { cfi, excerpt } of result.subitems) {
                results.push({ cfi, excerpt, tocLabel: result.label ?? null })
            }
        }
    } catch (e) {
        post('error', { message: `Search failed: ${e.message}` })
        return
    }
    if (myToken === searchToken) post('searchResults', { query, results })
}

function clearSearch() {
    searchToken++ // invalidate any in-flight search so its stale results never post
    if (view?.clearSearch) view.clearSearch()
}

let activeAnnotationsList = []

async function renderAnnotations(annotations) {
    if (!view) return
    activeAnnotationsList = Array.isArray(annotations) ? annotations.filter(Boolean) : []
    const nextByValue = new Map(activeAnnotationsList.map(annotation => [annotation.value, annotation]))
    const orphanedCfis = new Set()
    for (const [sourceValue, cfi] of resolvedTextAnnotations) {
        const next = nextByValue.get(sourceValue)
        if (!next || resolvedTextFingerprints.get(sourceValue) !== annotationFingerprint(next)) {
            orphanedCfis.add(cfi)
            resolvedTextAnnotations.delete(sourceValue)
            resolvedTextFingerprints.delete(sourceValue)
            if (authoritativeSourceForCfi.get(cfi) === sourceValue) {
                // Other quotes deduplicated onto this cfi only inherited ITS color/note/type.
                // Release them too so they re-match and render with their own data instead of
                // permanently keeping a now-deleted (or changed) quote's stale metadata.
                authoritativeSourceForCfi.delete(cfi)
                for (const [otherValue, otherCfi] of Array.from(resolvedTextAnnotations)) {
                    if (otherCfi === cfi) {
                        resolvedTextAnnotations.delete(otherValue)
                        resolvedTextFingerprints.delete(otherValue)
                    }
                }
            }
        }
    }
    const retainedCfis = new Set(resolvedTextAnnotations.values())
    for (const cfi of orphanedCfis) {
        if (!retainedCfis.has(cfi)) {
            await view.deleteAnnotation({ value: cfi })
            renderedAnnotations.delete(cfi)
        }
    }
    for (const [value, fingerprint] of standardAnnotationFingerprints) {
        const next = nextByValue.get(value)
        if (!next || annotationFingerprint(next) !== fingerprint) {
            await view.deleteAnnotation({ value })
            renderedAnnotations.delete(value)
            standardAnnotationFingerprints.delete(value)
        }
    }
    for (const annotation of activeAnnotationsList) {
        if (annotation.value &&
            !annotation.value.startsWith('text:') &&
            !annotation.value.startsWith('quote:') &&
            !standardAnnotationFingerprints.has(annotation.value)) {
            await view.addAnnotation(annotation)
            renderedAnnotations.add(annotation.value)
            standardAnnotationFingerprints.set(annotation.value, annotationFingerprint(annotation))
        }
    }
    // Also match any active documents in view
    for (const { doc, index } of view.renderer.getContents()) {
        if (doc) matchTextAnnotationsForDoc(doc, index)
    }
}

function annotationFingerprint(annotation) {
    return JSON.stringify([
        annotation?.type || '',
        annotation?.color || '',
        annotation?.note || '',
        annotation?.text || '',
    ])
}

function matchTextAnnotationsForDoc(doc, index) {
    if (!activeAnnotationsList || !activeAnnotationsList.length || !view) return
    let missing = unmatchedInDoc.get(doc)
    if (!missing) {
        missing = new Set()
        unmatchedInDoc.set(doc, missing)
    }
    const matches = []
    for (const ann of activeAnnotationsList) {
        if (!ann.value || resolvedTextAnnotations.has(ann.value) || pendingTextAnnotations.has(ann.value)) continue
        const textToFind = ann.text
        if (!textToFind || textToFind.length < 5) continue
        // A quote already proven absent from this document won't suddenly appear in it -
        // skip re-running the expensive alignment fallback for it on every page turn.
        if (missing.has(ann.value)) continue
        const range = findTextRangeInDoc(doc, textToFind)
        if (range) {
            try {
                const cfi = view.getCFI(index, range)
                matches.push({ ann, range, cfi })
            } catch (_) {}
        } else {
            missing.add(ann.value)
        }
    }

    // Goodreads may list near-identical variants as separate quotes. Kindle-style popular
    // highlights show one underline, so consolidate ranges that cover substantially the same
    // passage and keep the largest popularity count.
    matches.sort((a, b) => highlightCount(b.ann.note) - highlightCount(a.ann.note))
    const accepted = []
    for (const match of matches) {
        const duplicate = accepted.find(existing => rangeOverlapRatio(existing.range, match.range) >= 0.80)
        if (duplicate) {
            resolvedTextAnnotations.set(match.ann.value, duplicate.cfi)
            resolvedTextFingerprints.set(match.ann.value, annotationFingerprint(match.ann))
            continue
        }
        accepted.push(match)
        pendingTextAnnotations.add(match.ann.value)
        Promise.resolve(view.addAnnotation({
            value: match.cfi,
            type: match.ann.type || 'underline',
            color: match.ann.color || '#6366f1',
            note: match.ann.note,
        })).then(() => {
            renderedAnnotations.add(match.cfi)
            resolvedTextAnnotations.set(match.ann.value, match.cfi)
            resolvedTextFingerprints.set(match.ann.value, annotationFingerprint(match.ann))
            authoritativeSourceForCfi.set(match.cfi, match.ann.value)
        }).catch(error => {
            // Leave it unresolved (not marked pending, not cached as a rendered cfi) so a
            // later render pass retries it instead of silently never showing this quote again.
            post('log', { step: 'addQuoteAnnotation', message: String(error) })
        }).finally(() => {
            pendingTextAnnotations.delete(match.ann.value)
        })
    }
}

function highlightCount(note) {
    const match = String(note || '').match(/\d+/)
    return match ? Number(match[0]) : 0
}

function rangeOverlapRatio(a, b) {
    const aStart = a.__vayanaStart
    const aEnd = a.__vayanaEnd
    const bStart = b.__vayanaStart
    const bEnd = b.__vayanaEnd
    if (![aStart, aEnd, bStart, bEnd].every(Number.isFinite)) return 0
    const overlap = Math.max(0, Math.min(aEnd, bEnd) - Math.max(aStart, bStart))
    return overlap / Math.max(1, Math.min(aEnd - aStart, bEnd - bStart))
}

function normalizeForMatching(str) {
    if (!str) return ''
    return str
        .replace(/&[a-z0-9#]+;/gi, ' ')
        .replace(/<[^>]+>/g, ' ')
        .normalize('NFKD')
        .replace(/\p{M}/gu, '') // Decomposed accents and combining marks
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

// buildDocumentTextIndex calls this once per character of the whole chapter body, so the
// common case (plain ASCII) is fast-pathed to avoid running normalizeForMatching's full
// regex/Unicode-normalize chain per character. Every branch here returns exactly what
// normalizeForMatching(char) would for that single character - verified for the full ASCII
// range, where every non-alnum input (space, punctuation, control chars) always collapses to
// '' and every letter/digit passes through unchanged but lowercased.
function normalizeCharForIndex(char) {
    const code = char.charCodeAt(0)
    if (code >= 97 && code <= 122) return char // a-z
    if (code >= 48 && code <= 57) return char // 0-9
    if (code >= 65 && code <= 90) return String.fromCharCode(code + 32) // A-Z -> a-z
    if (code < 128) return '' // Other ASCII (space, punctuation, control) always normalizes to empty.
    return normalizeForMatching(char)
}

function buildDocumentTextIndex(doc) {
    const cached = documentTextIndexes.get(doc)
    if (cached) return cached
    const visibilityCache = new WeakMap()
    const walker = doc.createTreeWalker(
        doc.body,
        NodeFilter.SHOW_TEXT,
        {
            acceptNode(node) {
                const parent = node.parentElement
                if (!parent || !node.textContent) return NodeFilter.FILTER_REJECT
                if (!isMatchableTextParent(parent, doc, visibilityCache)) {
                    return NodeFilter.FILTER_REJECT
                }
                return NodeFilter.FILTER_ACCEPT
            },
        },
    )
    let node
    let cleanDoc = ''
    const charMap = []
    const tokens = []
    const tokenPositions = new Map()
    let token = null
    const flushToken = () => {
        if (token?.value) {
            const position = tokens.length
            tokens.push(token)
            const positions = tokenPositions.get(token.value)
            if (positions) positions.push(position)
            else tokenPositions.set(token.value, [position])
        }
        token = null
    }
    while ((node = walker.nextNode())) {
        const str = node.textContent
        for (let offset = 0; offset < str.length; offset++) {
            const normalized = normalizeCharForIndex(str[offset])
            if (normalized) {
                if (!token) token = { value: '', start: cleanDoc.length, end: cleanDoc.length }
                token.value += normalized
                for (const char of normalized) {
                    cleanDoc += char
                    token.end = cleanDoc.length
                    charMap.push({ node, offset })
                }
            } else if (!/[\u2018\u2019\u201A\u201B\u2032'`]/u.test(str[offset])) {
                flushToken()
            }
        }
    }
    flushToken()
    const index = { cleanDoc, charMap, tokens, tokenPositions }
    documentTextIndexes.set(doc, index)
    return index
}

function isMatchableTextParent(element, doc, cache) {
    if (!element) return true
    if (cache.has(element)) return cache.get(element)
    if (element.matches('style,script,noscript,template,[hidden],[aria-hidden="true"]')) {
        cache.set(element, false)
        return false
    }
    const getStyle = doc.defaultView?.getComputedStyle?.bind(doc.defaultView)
    if (getStyle) {
        const style = getStyle(element)
        if (style.display === 'none' || style.visibility === 'hidden' || style.contentVisibility === 'hidden') {
            cache.set(element, false)
            return false
        }
    }
    const result = element === doc.documentElement || isMatchableTextParent(element.parentElement, doc, cache)
    cache.set(element, result)
    return result
}

function tokenizeForMatching(text) {
    return String(text || '')
        .split(/[^\p{L}\p{N}\p{M}\u2018\u2019\u201A\u201B\u2032'`]+/u)
        .map(normalizeForMatching)
        .filter(Boolean)
}

// Align target words to a small document window. Unlike the former character-probe
// fallback, this backtracks the edit path, so inserted or omitted words cannot shift
// the returned DOM range into adjacent prose.
function findAlignedTokenMatch(index, rawText) {
    const target = tokenizeForMatching(rawText)
    const source = index.tokens
    if (target.length < 4 || target.length > 600 || source.length < 4) return null

    let anchorIndex = -1
    let anchorFrequency = Infinity
    for (let i = 0; i < target.length; i++) {
        const frequency = index.tokenPositions.get(target[i])?.length || 0
        if (target[i].length >= 4 && frequency > 0 && frequency < anchorFrequency) {
            anchorIndex = i
            anchorFrequency = frequency
        }
    }
    if (anchorIndex < 0 || anchorFrequency > 40) return null

    let best = null
    for (const anchorAt of index.tokenPositions.get(target[anchorIndex]) || []) {
        const padding = Math.max(6, Math.ceil(target.length * 0.25))
        const from = Math.max(0, anchorAt - anchorIndex - padding)
        const to = Math.min(source.length, anchorAt + (target.length - anchorIndex) + padding)
        const window = source.slice(from, to)
        const rows = Array.from({ length: target.length + 1 }, () => new Uint16Array(window.length + 1))
        for (let i = 1; i <= target.length; i++) {
            rows[i][0] = i
            for (let j = 1; j <= window.length; j++) {
                const substitution = rows[i - 1][j - 1] + (target[i - 1] === window[j - 1].value ? 0 : 1)
                rows[i][j] = Math.min(substitution, rows[i - 1][j] + 1, rows[i][j - 1] + 1)
            }
        }

        let end = 1
        for (let j = 2; j <= window.length; j++) {
            if (rows[target.length][j] < rows[target.length][end]) end = j
        }
        const distance = rows[target.length][end]
        let i = target.length
        let j = end
        let exactWords = 0
        while (i > 0 && j > 0) {
            if (target[i - 1] === window[j - 1].value && rows[i][j] === rows[i - 1][j - 1]) {
                exactWords++
                i--
                j--
            } else if (rows[i][j] === rows[i - 1][j] + 1) {
                i--
            } else if (rows[i][j] === rows[i][j - 1] + 1) {
                j--
            } else {
                i--
                j--
            }
        }
        const start = j
        const spanLength = end - start
        const score = 1 - distance / Math.max(target.length, spanLength)
        const evidence = exactWords / target.length
        const minimumScore = target.length < 8 ? 0.85 : 0.72
        const minimumEvidence = target.length < 8 ? 0.75 : 0.60
        if (score >= minimumScore && evidence >= minimumEvidence && (!best || score > best.score)) {
            const firstToken = window[start]
            const lastToken = window[end - 1]
            if (firstToken && lastToken) {
                best = {
                    matchIdx: firstToken.start,
                    matchLen: lastToken.end - firstToken.start,
                    score,
                }
                // A near-exact alignment won't be beaten by another anchor occurrence - stop
                // paying for more O(target*window) DP grids once we already have one this good.
                if (score >= 0.97) break
            }
        }
    }
    return best
}

function findMultiChunkMatch(cleanDoc, rawText) {
    if (!cleanDoc || !rawText) return null
    const cleanTargetLength = normalizeForMatching(rawText).length
    // Split raw text into natural clauses/phrases by punctuation or ellipses
    const clauses = rawText
        .split(/[.,;:!?…\n"]|\.{2,}/)
        .map(c => normalizeForMatching(c))
        .filter(c => c.length >= 10)

    if (clauses.length < 2) return null

    let firstMatch = null
    let lastMatch = null
    let matchedCount = 0
    let matchedLength = 0
    let firstMatchedClauseIndex = -1
    let lastEndIdx = 0

    for (let clauseIndex = 0; clauseIndex < clauses.length; clauseIndex++) {
        const clause = clauses[clauseIndex]
        const idx = cleanDoc.indexOf(clause, lastEndIdx)
        if (idx !== -1 && (lastEndIdx === 0 || idx - lastEndIdx < 800)) {
            if (!firstMatch) {
                firstMatch = { matchIdx: idx, matchLen: clause.length }
                firstMatchedClauseIndex = clauseIndex
            }
            lastMatch = { matchIdx: idx, matchLen: clause.length }
            lastEndIdx = idx + clause.length
            matchedCount++
            matchedLength += clause.length
        }
    }

    const hasEnoughEvidence = matchedLength >= Math.max(24, Math.floor(cleanTargetLength * 0.55))
    if (matchedCount >= 2 && firstMatchedClauseIndex <= 1 && hasEnoughEvidence && firstMatch && lastMatch) {
        const startIdx = firstMatch.matchIdx
        const endIdx = lastMatch.matchIdx + lastMatch.matchLen
        const totalSpan = endIdx - startIdx
        const maximumSpan = Math.min(2500, Math.max(cleanTargetLength * 3, 300))
        if (totalSpan > 0 && totalSpan <= maximumSpan) {
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

        const index = buildDocumentTextIndex(doc)
        const { cleanDoc, charMap } = index

        if (cleanDoc.length < 5) return null

        // Strategy 1: an exact normalized substring is both fastest and safest.
        const exactIndex = cleanDoc.indexOf(cleanTarget)
        let match = exactIndex >= 0
            ? { matchIdx: exactIndex, matchLen: cleanTarget.length, score: 1 }
            : null

        // Strategy 2: explicit clause gaps, such as dialogue elided by Goodreads.
        if (!match) {
            const chunkMatch = findMultiChunkMatch(cleanDoc, text)
            if (chunkMatch) match = chunkMatch
        }

        // Strategy 3: bounded word alignment for small wording differences. This returns
        // endpoints from the EPUB itself rather than estimating them from quote offsets.
        if (!match) match = findAlignedTokenMatch(index, text)

        if (!match || match.matchIdx === -1 || match.matchIdx >= charMap.length) return null

        const { matchIdx, matchLen } = match
        const start = charMap[matchIdx]
        const endCharIdx = Math.min(matchIdx + matchLen - 1, charMap.length - 1)
        const end = charMap[endCharIdx]

        if (start && end) {
            const range = doc.createRange()
            range.setStart(start.node, start.offset)
            range.setEnd(end.node, end.offset + 1)
            range.__vayanaStart = matchIdx
            range.__vayanaEnd = endCharIdx + 1
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
                const start = range.cloneRange()
                start.collapse(true)
                const cfi = view.getCFI(i, start)
                return cfi // Found exact valid section
            }
        } catch (_) {}
    }
    return null
}

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle, setBionicReading, renderAnnotations, clearSelection, search, clearSearch }
addEventListener('resize', () => applyReaderMargin(readerSideMarginPercent))
post('ready', {})
