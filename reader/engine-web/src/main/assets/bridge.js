// Kotlin <-> foliate-js glue. Exposes window.VayanaReader for Android to call via
// evaluateJavascript, and forwards foliate-js events to Android via the injected
// `AndroidBridge` @JavascriptInterface (see FoliateBookEngine.kt).
import './foliate/view.js'
import { Overlayer } from './foliate/overlayer.js'

const DefaultAnnotationColor = '#6366f1'

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
        resolvedBadgeCfis.clear()
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
            scheduleBadgeLayout()
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
            wireDoubleTapLookup(doc)
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
            const color = markColor(annotation.color ?? DefaultAnnotationColor)
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
                    return g
                })
                // The overlay redraws whenever the page is re-paginated; the pills beside it must follow.
                scheduleBadgeLayout()
            } else {
                const inkStyle = inkStyleFor(annotation.color ?? DefaultAnnotationColor)
                if (inkStyle && inkStyle !== 'highlight') {
                    draw(Overlayer[inkStyle], { color, width: 2.5 })
                } else {
                    draw((rects, options) => {
                        const g = Overlayer.highlight(rects, options)
                        g.style.fill = color
                        return g
                    })
                }
            }
        })
        // Note references open as a popup instead of jumping away from the page being read.
        view.addEventListener('link', e => {
            const { a, href } = e.detail
            if (!isFootnoteLink(a)) return
            e.preventDefault()
            showFootnote(href)
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

/**
 * Where a popularity pill goes: in the blank margin beside the page, level with the first line of its quote. The page
 * overlay is clipped at the text edge and that edge sits only a half-gap from the text, so a pill drawn there covers
 * letters; the margin outside the page has the room. Null when the quote does not start on the visible page.
 */
function popularBadgePlacement({ rectLeft, rectTop, rectHeight, pageStart, pageSize, iframeLeft, iframeTop, badgeWidth, badgeHeight, viewportWidth }) {
    if (!(rectLeft >= pageStart && rectLeft < pageStart + pageSize)) return null
    const pageLeft = iframeLeft + pageStart
    const onLeft = rectLeft - pageStart < pageSize / 2
    const gap = 3
    const wanted = onLeft ? pageLeft - badgeWidth - gap : pageLeft + pageSize + gap
    return {
        x: Math.max(0, Math.min(viewportWidth - badgeWidth, wanted)),
        y: iframeTop + rectTop + rectHeight / 2 - badgeHeight / 2,
        onLeft,
    }
}

const popularBadgeLayer = document.createElement('div')
Object.assign(popularBadgeLayer.style, { position: 'fixed', inset: '0', pointerEvents: 'none', zIndex: '20' })
document.body.append(popularBadgeLayer)
const resolvedBadgeCfis = new Map()
let badgeLayoutQueued = false

function scheduleBadgeLayout() {
    if (badgeLayoutQueued) return
    badgeLayoutQueued = true
    requestAnimationFrame(() => {
        badgeLayoutQueued = false
        try {
            layoutPopularBadges()
        } catch (error) {
            post('log', { step: 'layoutPopularBadges', message: String(error) })
        }
    })
}

/** Rebuilds the count pills of the community quotes on the visible page, from the annotation list. */
function layoutPopularBadges() {
    popularBadgeLayer.replaceChildren()
    const renderer = view?.renderer
    // Scrolled layouts have no pages, and so no page margin to put a pill in.
    if (!renderer || renderer.scrolled || !activeAnnotationsList.length) return
    // Quotes that resolve to the same passage share one underline; its pill shows the largest count.
    const byCfi = new Map()
    for (const ann of activeAnnotationsList) {
        if (!ann.popular) continue
        const cfi = resolvedTextAnnotations.get(ann.value) ?? (ann.value?.startsWith?.('epubcfi(') ? ann.value : null)
        if (!cfi) continue
        const count = highlightCount(ann.note)
        if (count <= 0) continue
        const best = byCfi.get(cfi)
        if (!best || count > best.count) byCfi.set(cfi, { count, color: ann.color ?? DefaultAnnotationColor })
    }
    if (!byCfi.size) return
    const contents = renderer.getContents()
    const pageSize = renderer.size
    // The paginator scrolls a spare page-width of padding ahead of the chapter, so the visible page begins at
    // `start - size` in the chapter document's own coordinates (the ones quote rects use).
    const pageStart = Math.max(0, renderer.start - pageSize)
    const badgeHeight = 18
    for (const [cfi, { count, color }] of byCfi) {
        let resolved = resolvedBadgeCfis.get(cfi)
        if (resolved === undefined) {
            try {
                resolved = view.resolveCFI(cfi)
            } catch (_) {
                resolved = null
            }
            resolvedBadgeCfis.set(cfi, resolved)
        }
        if (!resolved) continue
        const content = contents.find(c => c.index === resolved.index)
        const frame = content?.doc?.defaultView?.frameElement
        if (!frame) continue
        let range = null
        try {
            range = resolved.anchor(content.doc)
        } catch (_) {}
        const rect = range?.getClientRects?.()[0]
        if (!rect) continue
        const frameRect = frame.getBoundingClientRect()
        const text = String(count)
        const badgeWidth = Math.max(22, text.length * 7 + 12)
        const place = popularBadgePlacement({
            rectLeft: rect.left,
            rectTop: rect.top,
            rectHeight: rect.height,
            pageStart,
            pageSize,
            iframeLeft: frameRect.left,
            iframeTop: frameRect.top,
            badgeWidth,
            badgeHeight,
            viewportWidth: window.innerWidth,
        })
        if (!place) continue
        const pill = document.createElement('div')
        pill.textContent = text
        Object.assign(pill.style, {
            position: 'absolute',
            left: `${place.x}px`,
            top: `${place.y}px`,
            width: `${badgeWidth}px`,
            height: `${badgeHeight}px`,
            boxSizing: 'border-box',
            // Flex centring puts the digits in the middle both ways; a line-height would be reset by the font shorthand.
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            padding: '0',
            borderRadius: `${badgeHeight / 2}px`,
            background: markColor(color),
            color: '#ffffff',
            font: '700 11px/1 sans-serif',
            opacity: '0.95',
        })
        popularBadgeLayer.append(pill)
    }
}

window.addEventListener('resize', scheduleBadgeLayout)

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

function isTextAnnotationValue(value) {
    return value?.startsWith('text:') ||
        value?.startsWith('quote:') ||
        value?.startsWith('goodreads-quote:')
}

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
    const textToFind = ann?.text || (href.length > 8 && !isTextAnnotationValue(href) ? href : null)

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

    if (isTextAnnotationValue(href)) {
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

// paginator.js slides between pages only while its renderer carries the `animated` attribute.
function setPageTurnAnimation(enabled) {
    view?.renderer?.toggleAttribute('animated', Boolean(enabled))
}

// Documents currently carrying bionic markup. Applied on every page turn, so a document already in the wanted
// state must be left alone: reverting walks it, and the invalidation below would throw away its text index and
// its record of quotes known to be absent, which both exist to make page turns cheap.
const bionicAppliedDocs = new WeakSet()

function applyBionicReadingToDoc(doc) {
    if (!doc || !doc.body) return
    if (bionicReadingEnabled === bionicAppliedDocs.has(doc)) return
    if (bionicReadingEnabled) {
        bionicAppliedDocs.add(doc)
        transformBionicWords(doc)
    } else {
        bionicAppliedDocs.delete(doc)
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
    // The section sits in an iframe scrolled inside the reader view; edges are reported against the view (the WebView).
    const frameTop = doc.defaultView?.frameElement?.getBoundingClientRect().top ?? 0
    const viewHeight = window.innerHeight
    const fractionOfView = y => viewHeight > 0 ? Math.max(0, Math.min(1, (frameTop + y) / viewHeight)) : null
    const wordLookup = wordLookupSelection
    wordLookupSelection = false
    post('selection', {
        cfi: view.getCFI(index, range),
        selectedText,
        wordLookup,
        tocLabel: view.getProgressOf(index, range)?.tocItem?.label?.trim?.() ?? null,
        top: fractionOfView(rect.top),
        bottom: fractionOfView(rect.bottom),
    })
}

// The loaded section on screen and its document.
function currentContent() {
    const contents = view?.renderer?.getContents() ?? []
    return contents.find(c => c.index === view.lastLocation?.section?.current) ?? contents[0]
}

const segmenters = new Map()

// One Intl.Segmenter per book language and granularity ('word' or 'sentence').
function segmenterFor(doc, granularity) {
    const lang = doc.documentElement.lang || ''
    const key = `${lang}|${granularity}`
    let segmenter = segmenters.get(key)
    if (!segmenter) {
        segmenter = new Intl.Segmenter(lang || undefined, { granularity })
        segmenters.set(key, segmenter)
    }
    return segmenter
}

const EpubOpsNamespace = 'http://www.idpf.org/2007/ops'
const MaxFootnoteChars = 2000

// A link to a note: marked as one by the book, or a short superscript/bracketed marker such as "12", "[3]" or "*".
function isFootnoteLink(a) {
    const types = `${a.getAttributeNS(EpubOpsNamespace, 'type') ?? ''} ${a.getAttribute('epub:type') ?? ''} ${a.getAttribute('role') ?? ''}`
    if (/\bnoteref\b|doc-noteref/.test(types)) return true
    const label = a.textContent.trim()
    if (!/^[\[(]?(\d{1,3}|[*†‡§])[\])]?$/.test(label)) return false
    return a.closest('sup') != null || a.querySelector('sup') != null || /^[\[(]/.test(label)
}

async function showFootnote(href) {
    try {
        const resolved = view.resolveNavigation(href)
        if (!resolved) throw new Error(`Unresolved note ${href}`)
        const doc = await view.book.sections[resolved.index].createDocument()
        const target = resolved.anchor?.(doc)
        const node = target instanceof Range ? target.startContainer : target
        const element = node?.nodeType === 1 ? node : node?.parentElement
        const block = element?.closest('aside, li, dd, p, div') ?? element
        const text = (block?.textContent ?? '').replace(/\s+/g, ' ').trim()
        if (!text) throw new Error(`Empty note ${href}`)
        post('footnote', { text: text.slice(0, MaxFootnoteChars), href })
    } catch (error) {
        // Not a note we can show in place: follow the link as the book intended.
        post('log', { step: 'footnote', message: String(error) })
        view.goTo(href)
    }
}

const DoubleTapWindowMillis = 350
const DoubleTapSlopPx = 24
const doubleTapState = new WeakMap()
let wordLookupSelection = false

// Two quick taps on a word in the middle of the page select it, which opens the dictionary like a long-press.
// The sides of the page turn pages on a single tap, so double taps there are left alone.
function wireDoubleTapLookup(doc) {
    doc.addEventListener('click', e => {
        if (e.target?.closest?.('a[href]')) return
        const width = (doc.defaultView?.top ?? window).innerWidth || 1
        const horizontal = e.screenX / width
        if (horizontal < 1 / 3 || horizontal > 2 / 3) return
        const last = doubleTapState.get(doc)
        doubleTapState.set(doc, { time: e.timeStamp, x: e.clientX, y: e.clientY })
        if (!last || e.timeStamp - last.time > DoubleTapWindowMillis) return
        if (Math.hypot(e.clientX - last.x, e.clientY - last.y) > DoubleTapSlopPx) return
        doubleTapState.delete(doc)
        selectWordAt(doc, e.clientX, e.clientY)
    })
}

function selectWordAt(doc, x, y) {
    const caret = doc.caretRangeFromPoint?.(x, y)
    const node = caret?.startContainer
    if (!node || node.nodeType !== 3) return
    const range = doc.createRange()
    const bionicWord = node.parentElement?.closest(`.${BionicWordClass}`)
    if (bionicWord) {
        range.selectNodeContents(bionicWord)
    } else {
        const word = [...segmenterFor(doc, 'word').segment(node.data)]
            .find(({ segment, index }) => caret.startOffset >= index && caret.startOffset < index + segment.length)
        if (!word?.isWordLike) return
        range.setStart(node, word.index)
        range.setEnd(node, word.index + word.segment.length)
    }
    wordLookupSelection = true
    const selection = doc.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
}

const SpeechHighlightColor = '#5B8DEF'
const SpeechMarkKey = 'vayana-speech'

// A tinted mark on an E-Ink page comes out as a pale grey (yellow all but disappears), so every mark - highlights,
// underlines, the read-aloud sentence, the community pills - is drawn in black there: dark grey at the overlay's
// default opacity, still a clear shape on 16 grey levels. The book document's own CSS cannot restyle these, the
// overlay sits outside its iframe.
let inkMarks = false

function setInkMarks(enabled) {
    inkMarks = Boolean(enabled)
}

function markColor(color) {
    return inkMarks ? '#000000' : color
}

// With every mark black the highlight colours can no longer tell themselves apart, so on E-Ink each colour gets its
// own shape instead: shaded, underlined, wavy, boxed. Anything else (the default colour) is shaded.
const InkStyleByColor = { '#f6c453': 'highlight', '#7bae7f': 'underline', '#5b8def': 'squiggly', '#d77fa1': 'outline' }

function inkStyleFor(color) {
    if (!inkMarks) return null
    return InkStyleByColor[String(color).toLowerCase()] ?? 'highlight'
}
const speech = { index: -1, sentences: new Map(), markedOverlayer: null, turning: false }

// Android TTS reports UTF-16 offsets into the whitespace-normalized text it receives. Keep a boundary map back to
// the EPUB's original text so every timed range can become an exact DOM Range even across collapsed whitespace.
function normalizeSpeechSegment(segment) {
    let text = ''
    const sourceStarts = []
    const sourceEnds = []
    for (let index = 0; index < segment.length;) {
        if (/\s/u.test(segment[index])) {
            let end = index + 1
            while (end < segment.length && /\s/u.test(segment[end])) end++
            if (text && end < segment.length) {
                text += ' '
                sourceStarts.push(index)
                sourceEnds.push(end)
            }
            index = end
        } else {
            text += segment[index]
            sourceStarts.push(index)
            sourceEnds.push(index + 1)
            index++
        }
    }
    return {
        text,
        sourceRange(start, end) {
            const safeStart = Math.max(0, Math.min(sourceStarts.length, start))
            const safeEnd = Math.max(safeStart, Math.min(sourceEnds.length, end))
            if (safeStart === safeEnd) return null
            return [sourceStarts[safeStart], sourceEnds[safeEnd - 1]]
        },
    }
}

// The chapter's sentences in reading order, from the first one not before [fromRange] (the page on screen).
function speechSentencesFor(doc, index, fromRange) {
    speech.index = index
    speech.sentences.clear()
    if (!doc.body) return []
    const walker = doc.createTreeWalker(doc.body, NodeFilter.SHOW_TEXT)
    const pieces = []
    let text = ''
    let lastBlock = null
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        const parent = node.parentElement
        if (!node.data.trim() || parent?.closest('script, style, rt')) continue
        // Separate blocks so a paragraph without closing punctuation doesn't run into the next one.
        const block = parent?.closest('p, li, h1, h2, h3, h4, h5, h6, blockquote, dd, dt, td, figcaption, div, section')
        if (lastBlock && block !== lastBlock) text += '\n\n'
        lastBlock = block
        pieces.push({ node, start: text.length })
        text += node.data
    }
    const pointAt = offset => {
        let low = 0
        let high = pieces.length - 1
        while (low < high) {
            const mid = (low + high + 1) >> 1
            if (pieces[mid].start <= offset) low = mid
            else high = mid - 1
        }
        const { node, start } = pieces[low]
        return [node, Math.max(0, Math.min(node.data.length, offset - start))]
    }
    const sentences = []
    for (const { segment, index: segmentStart } of segmenterFor(doc, 'sentence').segment(text)) {
        if (!/[\p{L}\p{N}]/u.test(segment)) continue
        // The sentence spans its first to last non-space character. Skip those that end before the page on screen
        // before doing any per-character work or building a Range for them.
        const start = segmentStart + segment.length - segment.trimStart().length
        const endPoint = pointAt(segmentStart + segment.trimEnd().length)
        if (fromRange && fromRange.comparePoint(...endPoint) < 0) continue
        const normalized = normalizeSpeechSegment(segment)
        const sentenceText = normalized.text
        const range = doc.createRange()
        range.setStart(...pointAt(start))
        range.setEnd(...endPoint)
        const id = `${index}:${sentences.length}`
        speech.sentences.set(id, {
            range,
            rangeForOffsets(startOffset, endOffset) {
                const sourceRange = normalized.sourceRange(startOffset, endOffset)
                if (!sourceRange) return null
                const wordRange = doc.createRange()
                wordRange.setStart(...pointAt(segmentStart + sourceRange[0]))
                wordRange.setEnd(...pointAt(segmentStart + sourceRange[1]))
                return wordRange
            },
        })
        sentences.push({ id, text: sentenceText })
    }
    return sentences
}

// [fromCfi] starts reading at the sentence holding that position (a selection) instead of at the top of the page.
function startSpeech(requestId, fromCfi) {
    const content = currentContent()
    if (!content?.doc) {
        post('reply', { requestId, sentences: [], endOfBook: true })
        return
    }
    const visible = view.lastLocation?.range
    let fromRange = visible?.startContainer?.ownerDocument === content.doc ? visible : null
    if (fromCfi) {
        try {
            const { index, anchor } = view.resolveCFI(fromCfi)
            const selected = index === content.index ? anchor(content.doc) : null
            if (selected?.startContainer) fromRange = selected
        } catch (error) {
            post('log', { step: 'startSpeech', message: String(error) })
        }
    }
    post('reply', { requestId, sentences: speechSentencesFor(content.doc, content.index, fromRange), endOfBook: false })
}

async function nextSpeechChunk(requestId) {
    clearSpeechMark()
    const sections = view?.book?.sections ?? []
    let next = speech.index + 1
    while (next < sections.length && sections[next].linear === 'no') next++
    if (next >= sections.length) {
        post('reply', { requestId, sentences: [], endOfBook: true })
        return
    }
    try {
        await view.goTo(next)
        const content = view.renderer.getContents().find(c => c.index === next)
        const sentences = content?.doc ? speechSentencesFor(content.doc, next, null) : []
        speech.index = next
        post('reply', { requestId, sentences, endOfBook: false })
    } catch (error) {
        post('log', { step: 'nextSpeechChunk', message: String(error) })
        post('reply', { requestId, sentences: [], endOfBook: true })
    }
}

async function markSpeech(id, start, end) {
    const sentence = speech.sentences.get(id)
    const range = sentence?.rangeForOffsets(start, end) ?? sentence?.range
    if (!range || !view) return
    // Drawn straight onto the section's overlay from the Range already in hand. Going through view.addAnnotation would
    // build a CFI for it and resolve that back to a Range, twice per word (again to remove it).
    const overlayer = view.renderer.getContents().find(c => c.index === speech.index)?.overlayer
    if (!overlayer) return
    clearSpeechMark()
    overlayer.add(SpeechMarkKey, range, rects => {
        const g = Overlayer.highlight(rects)
        g.style.fill = markColor(SpeechHighlightColor)
        return g
    })
    speech.markedOverlayer = overlayer
    // Turn the page once speech reaches text past the end of the page on screen.
    // Words arrive every few hundred ms, and the paginator queues a page turn requested mid-turn (skipping a page),
    // so only one turn is in flight at a time; the next word re-checks against the new page.
    if (speech.turning) return
    const visible = view.lastLocation?.range
    if (visible?.startContainer?.ownerDocument === range.startContainer.ownerDocument &&
        visible.comparePoint(range.startContainer, range.startOffset) > 0) {
        speech.turning = true
        try {
            await view.next()
        } finally {
            speech.turning = false
        }
    }
}

function clearSpeechMark() {
    speech.markedOverlayer?.remove(SpeechMarkKey)
    speech.markedOverlayer = null
}

function stopSpeech() {
    clearSpeechMark()
    speech.sentences.clear()
    speech.index = -1
    speech.turning = false
}

const MaxChapterWordKinds = 20000

// How often each all-letter word of at least [minLength] letters appears (as written) in the chapter on screen, for
// the reader's chapter word list. Shorter words are dropped here so they never cross the bridge.
function chapterWordCounts(requestId, minLength) {
    const counts = Object.create(null)
    const doc = currentContent()?.doc
    if (doc?.body) {
        let kinds = 0
        for (const { segment, isWordLike } of segmenterFor(doc, 'word').segment(doc.body.textContent ?? '')) {
            if (!isWordLike || segment.length < minLength || !/^\p{L}+$/u.test(segment)) continue
            if (counts[segment] === undefined) {
                if (kinds >= MaxChapterWordKinds) continue
                kinds++
                counts[segment] = 0
            }
            counts[segment]++
        }
    }
    post('reply', { requestId, counts })
}

// Grows the range of `cfi` over every range in `others` that overlaps it, directly or through another one
// already merged, and replies with the union's CFI and text. Only ranges in the loaded section count.
function mergeRanges(requestId, cfi, others) {
    let reply = { requestId }
    try {
        const contents = view.renderer.getContents()
        const resolve = value => {
            const { index, anchor } = view.resolveCFI(value)
            const doc = contents.find(c => c.index === index)?.doc
            const range = doc ? anchor(doc) : null
            return range ? { index, range } : null
        }
        const base = resolve(cfi)
        if (base) {
            const union = base.range.cloneRange()
            const pending = others.map(value => ({ value, resolved: resolve(value) }))
                .filter(item => item.resolved?.index === base.index)
            const merged = []
            let grew = true
            while (grew) {
                grew = false
                for (let i = pending.length - 1; i >= 0; i--) {
                    const { range } = pending[i].resolved
                    const overlaps = union.compareBoundaryPoints(Range.START_TO_END, range) <= 0 &&
                        union.compareBoundaryPoints(Range.END_TO_START, range) >= 0
                    if (!overlaps) continue
                    if (union.compareBoundaryPoints(Range.START_TO_START, range) > 0) union.setStart(range.startContainer, range.startOffset)
                    if (union.compareBoundaryPoints(Range.END_TO_END, range) < 0) union.setEnd(range.endContainer, range.endOffset)
                    merged.push(pending[i].value)
                    pending.splice(i, 1)
                    grew = true
                }
            }
            if (merged.length) {
                reply = { requestId, cfi: view.getCFI(base.index, union), text: union.toString().trim(), merged }
            }
        }
    } catch (error) {
        post('log', { step: 'mergeRanges', message: String(error) })
    }
    post('reply', reply)
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
    scheduleBadgeLayout()
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
            !isTextAnnotationValue(annotation.value) &&
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
        annotation?.popular || false,
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
            scheduleBadgeLayout()
            continue
        }
        accepted.push(match)
        pendingTextAnnotations.add(match.ann.value)
        Promise.resolve(view.addAnnotation({
            value: match.cfi,
            type: match.ann.type || 'underline',
            color: match.ann.color || DefaultAnnotationColor,
            note: match.ann.note,
        })).then(() => {
            renderedAnnotations.add(match.cfi)
            resolvedTextAnnotations.set(match.ann.value, match.cfi)
            resolvedTextFingerprints.set(match.ann.value, annotationFingerprint(match.ann))
            authoritativeSourceForCfi.set(match.cfi, match.ann.value)
            scheduleBadgeLayout()
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

window.VayanaReader = { open, next, prev, goLeft, goRight, goToFraction, goToHref, applyStyle, setBionicReading, setPageTurnAnimation, setInkMarks, renderAnnotations, clearSelection, search, clearSearch, startSpeech, nextSpeechChunk, markSpeech, stopSpeech, chapterWordCounts, mergeRanges }
addEventListener('resize', () => applyReaderMargin(readerSideMarginPercent))
post('ready', {})
