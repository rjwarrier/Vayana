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
const pendingQuoteAdds = new Set()
const authoritativeSourceForCfi = new Map()
const standardAnnotationFingerprints = new Map()
let annotationRevision = 0
const documentTextIndexes = new WeakMap()
const unmatchedInDoc = new WeakMap()
const selectionTimers = new WeakMap()
let publishedSelectionDocument = null
let publishedAnnotationId = null
let readerSideMarginPercent = 10
let hasOpened = false
// Pre-paginated books (PDF) render each page as an image with a text layer on top. Typography cannot reflow, but the
// text layer still supports selection, annotations, search, lookup, read aloud and the chapter word list.
let fixedLayout = false
let pendingPdfPassword = null

addEventListener('vayana-pdf-password', event => {
    pendingPdfPassword = event.detail?.updatePassword ?? null
    post('pdfPasswordRequired', { incorrect: Boolean(event.detail?.incorrect) })
})

// Dynamic page-count estimate: foliate's paginator lays out each chapter into CSS columns
// sized by the live font-size/line-height/margin, so `renderer.pages` for the chapter you're
// on is already a real, current measurement. We sample it per chapter into `bytesPerPage` and
// extrapolate a whole-book estimate from each section's known byte size — no extra rendering,
// just arithmetic over numbers the renderer already computed for the current page turn.
let sectionByteSizes = null
const bytesPerPage = new Map()
let pageEstimateCache = null
let pageEstimateRevision = 0
let pageJumpSnapshot = null
let lastSentTocRevision = -1

function resetPageEstimate() {
    bytesPerPage.clear()
    pageEstimateCache = null
}

function bookPageStats(sectionIndex) {
    if (fixedLayout) return fixedLayoutPageStats(sectionIndex)
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
    const pageInSection = Math.min(pagesInSection, Math.max(1, page))

    const sectionSize = sectionByteSizes[sectionIndex] || 0
    if (sectionSize > 0) {
        const density = sectionSize / pagesInSection
        if (bytesPerPage.get(sectionIndex) !== density) {
            bytesPerPage.set(sectionIndex, density)
            pageEstimateCache = null
        }
    }

    if (!pageEstimateCache) {
        let knownSize = 0
        let knownPages = 0
        for (const [i, bpp] of bytesPerPage) {
            knownSize += sectionByteSizes[i]
            knownPages += sectionByteSizes[i] / bpp
        }
        const avgBytesPerPage = knownPages > 0 ? knownSize / knownPages : (sectionSize / pagesInSection || 1600)
        const prefixPages = [0]
        for (const [index, size] of sectionByteSizes.entries()) {
            prefixPages.push(prefixPages[index] + (size ? size / (bytesPerPage.get(index) ?? avgBytesPerPage) : 0))
        }
        // Entries sharing a section use the same start page, as before.
        const tocPages = {}
        for (const [href, index] of tocSectionIndexes()) {
            if (index < sectionByteSizes.length) tocPages[href] = Math.round(prefixPages[index]) + 1
        }
        pageEstimateCache = { prefixPages, tocPages, revision: ++pageEstimateRevision }
    }
    const { prefixPages, tocPages, revision } = pageEstimateCache
    const pagesBefore = Math.round(prefixPages[sectionIndex])
    const pagesAfter = Math.round(prefixPages[sectionByteSizes.length] - prefixPages[sectionIndex + 1])
    const totalPages = pagesBefore + pagesInSection + pagesAfter
    pageEstimateCache.totalPages = totalPages

    return {
        currentPage: pagesBefore + pageInSection,
        totalPages,
        tocPages,
        tocRevision: revision,
    }
}

// A fixed-layout section is one page. Its contents entries resolve asynchronously (PDF destinations), so their page
// numbers join the next relocate once resolved.
let fixedLayoutTocPages = null
let fixedLayoutTocRevision = 0

function fixedLayoutPageStats(sectionIndex) {
    const totalPages = view?.book?.sections?.length ?? 0
    if (!Number.isInteger(sectionIndex) || totalPages <= 0) return null
    return {
        currentPage: Math.min(totalPages, sectionIndex + 1),
        totalPages,
        tocPages: fixedLayoutTocPages ?? {},
        tocRevision: fixedLayoutTocRevision,
    }
}

async function resolveFixedLayoutTocPages(book) {
    const pages = {}
    const walk = async items => {
        for (const item of items ?? []) {
            if (item.href && !(item.href in pages)) {
                try {
                    const index = (await book.resolveHref(item.href))?.index
                    if (Number.isInteger(index) && index >= 0) pages[item.href] = index + 1
                } catch (_) {}
            }
            await walk(item.subitems)
        }
    }
    await walk(book.toc)
    if (view?.book !== book) return
    fixedLayoutTocPages = pages
    fixedLayoutTocRevision++
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

// ---- End of the story ------------------------------------------------------------------------------------------
// Many books carry pages after the last chapter or epilogue: other books by the author, acknowledgements, an excerpt
// of the next book. The reader asks whether the book is finished when the story itself ends, so this finds the last
// section of the story: the book's own back-matter landmarks when it declares them, else the trailing run of contents
// entries whose titles read as back matter.

// Titles that are back matter wherever they appear in the trailing run of contents entries.
const BackMatterTitle = /acknowledge?ments?|about the (author|authors|translator|illustrator|publisher)|also (by|available)|other (books|titles|works)|by the same author|more (books|from|by)|books by|bibliograph|excerpt|preview|sneak peek|read on|coming soon|reading group|discussion|book club|reader'?s guide|endnotes|glossary|copyright|praise for|newsletter|sign up|a conversation with|interview|q ?(&|and) ?a\b|bonus|teaser|credits|colophon|permissions|further reading|afterword|author'?s note|historical note|a note (on|from)|a preview/i
// Short generic titles that only count when they are the whole title (a chapter may well be called "Field Notes").
const BackMatterWholeTitle = /^(notes|index|extras?|references|appendix( [a-z0-9]+)?|appendices|sources|glossary)$/i
// EPUB 3 landmark types (and EPUB 2 guide types) that open the back matter.
const BackMatterLandmarkTypes = new Set([
    'backmatter', 'afterword', 'appendix', 'bibliography', 'colophon', 'endnotes', 'glossary', 'index',
    'acknowledgments', 'acknowledgements', 'notes', 'rearnotes',
])
const BodyMatterLandmarkTypes = new Set(['bodymatter', 'text'])

let storyEndIndexCache = undefined
let storyEndPosted = false
let previousRelocateSection = null

function isBackMatterTitle(label) {
    const title = String(label ?? '').trim()
    return title.length > 0 && (BackMatterTitle.test(title) || BackMatterWholeTitle.test(title))
}

function sectionIndexOfHref(href) {
    if (!href) return null
    try {
        const index = view.book.resolveHref(href)?.index
        return Number.isInteger(index) && index >= 0 ? index : null
    } catch (_) {
        return null
    }
}

// First section of the back matter per the book's landmarks (EPUB 3 nav or EPUB 2 guide), or null.
function landmarkBackMatterStart() {
    let bodyStart = null
    let backStart = null
    for (const item of view.book.landmarks ?? []) {
        const types = [].concat(item.type ?? item.getAttribute?.('type') ?? [])
            .flatMap(type => String(type).toLowerCase().split(/\s+/))
        const index = sectionIndexOfHref(item.href ?? item.getAttribute?.('href'))
        if (index == null) continue
        if (types.some(type => BodyMatterLandmarkTypes.has(type))) bodyStart = Math.min(bodyStart ?? index, index)
        if (types.some(type => BackMatterLandmarkTypes.has(type))) {
            // Notes or an index before the story starts are front matter, not the end of the book.
            if (bodyStart == null || index > bodyStart) backStart = Math.min(backStart ?? index, index)
        }
    }
    return backStart
}

// First section of the trailing run of back-matter contents entries, or null when the contents end on the story.
function tocBackMatterStart() {
    const entries = []
    const walk = items => {
        for (const item of items ?? []) {
            const index = sectionIndexOfHref(item.href)
            if (index != null) entries.push({ label: item.label, index })
            walk(item.subitems)
        }
    }
    walk(view.book.toc)
    let backStart = null
    for (let i = entries.length - 1; i >= 0; i--) {
        if (!isBackMatterTitle(entries[i].label)) {
            // Back matter sharing the story's last file can't be told apart from it by section.
            return backStart != null && backStart > entries[i].index ? backStart : null
        }
        backStart = entries[i].index
    }
    return null
}

// Index of the last section of the story itself.
function storyEndIndex() {
    if (storyEndIndexCache !== undefined) return storyEndIndexCache
    const sections = view.book.sections ?? []
    const lastLinearBefore = index => {
        let i = index
        while (i >= 0 && sections[i]?.linear === 'no') i--
        return i
    }
    const lastLinear = lastLinearBefore(sections.length - 1)
    let end = lastLinear
    let backStart = null
    try {
        backStart = landmarkBackMatterStart() ?? tocBackMatterStart()
    } catch (error) {
        post('log', { step: 'storyEnd', message: String(error) })
    }
    if (backStart != null && backStart > 0) {
        const candidate = lastLinearBefore(backStart - 1)
        // "Back matter" starting in the first half of the book is a misreading of the contents; keep the whole book.
        if (candidate >= lastLinear / 2) end = candidate
    }
    storyEndIndexCache = end
    return end
}

// Tells the app once per opened book when the reader reaches the story's last page, or pages on past it. A jump
// straight into the back matter (from the contents, say) is not reading to the end, so it doesn't count.
function checkStoryEnd(index) {
    if (storyEndPosted || !Number.isInteger(index)) return
    try {
        const end = storyEndIndex()
        const renderer = view.renderer
        const onLastPage = fixedLayout || (renderer.scrolled
            ? renderer.viewSize - renderer.end <= 2
            : Number.isFinite(renderer.page) && renderer.page >= renderer.pages - 2)
        const reached = (index === end && onLastPage) || (index > end && previousRelocateSection === end)
        previousRelocateSection = index
        if (reached) {
            storyEndPosted = true
            post('storyEnd', { sectionIndex: index })
        }
    } catch (error) {
        post('log', { step: 'checkStoryEnd', message: String(error) })
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

// EPUB titles may be language maps; PDF titles may be missing.
function bookTitle(metadata) {
    const title = metadata?.title
    if (typeof title === 'string') return title
    if (title && typeof title === 'object') return Object.values(title).find(value => typeof value === 'string') ?? ''
    return ''
}

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

function nativePdfFile(bookUrl) {
    if (!globalThis.AndroidBridge?.bookInfo) return null
    let info
    try {
        info = JSON.parse(globalThis.AndroidBridge.bookInfo())
    } catch (_) {
        return null
    }
    if (info?.type !== 'application/pdf' || !Number.isSafeInteger(info.size) || info.size <= 0) return null

    const read = async (begin, end) => {
        if (end <= begin) return new ArrayBuffer(0)
        const response = await fetch(bookUrl, {
            headers: { Range: `bytes=${begin}-${end - 1}` },
            cache: 'no-store',
        })
        if (!response.ok) throw new Error(`PDF range fetch failed: ${response.status} ${response.statusText}`)
        const data = await response.arrayBuffer()
        if (data.byteLength !== end - begin)
            throw new Error(`PDF range fetch returned ${data.byteLength} bytes; expected ${end - begin}`)
        return data
    }
    const index = value => Math.max(0, Math.min(info.size, Math.trunc(Number(value) || 0)))
    return {
        name: info.name || 'current.pdf',
        type: info.type,
        size: info.size,
        slice: (begin = 0, end = info.size) => {
            const start = index(begin)
            const finish = Math.max(start, index(end))
            return { size: finish - start, arrayBuffer: () => read(start, finish) }
        },
    }
}

async function open(bookUrl, lastLocatorCfi) {
    speech.pdfMarginPages.clear()
    let phase = 'starting'
    const watchdog = setInterval(() => {
        post('log', { step: 'openWatchdog', phase })
    }, 5000)
    try {
        renderedAnnotations.clear()
        resolvedTextAnnotations.clear()
        resolvedTextFingerprints.clear()
        pendingTextAnnotations.clear()
        pendingQuoteAdds.clear()
        authoritativeSourceForCfi.clear()
        standardAnnotationFingerprints.clear()
        annotationApplyGeneration++
        pendingAnnotations = undefined
        annotationApplyRunning = false
        resolvedBadgeCfis.clear()
        popularBadgesBySection.clear()
        popularBadgeIndexDirty = true
        resetPopularBadgeResolutionRetries()
        annotationRevision++
        publishedSelectionDocument = null
        publishedAnnotationId = null
        hasOpened = false
        phase = 'opening book source'
        let bookFile = nativePdfFile(bookUrl)
        if (bookFile) {
            post('log', { step: 'rangeBackedPdf', size: bookFile.size })
        } else {
            post('log', { step: 'fetching', bookUrl })
            const res = await fetch(bookUrl)
            post('log', { step: 'fetched', ok: res.ok, status: res.status, contentType: res.headers.get('content-type') })
            if (!res.ok) throw new Error(`Book fetch failed: ${res.status} ${res.statusText}`)
            const bookBlob = await res.blob()
            bookFile = new File([bookBlob], 'current')
        }

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
        storyEndIndexCache = undefined
        storyEndPosted = false
        previousRelocateSection = null
        lastSentTocRevision = -1
        fixedLayout = false
        fixedLayoutTocPages = null
        resetFixedLayoutState()
        resetPageEstimate()
        pageJumpSnapshot = null
        view.addEventListener('relocate', e => {
            if (publishedSelectionDocument && !view.renderer.getContents()
                .some(content => content?.doc === publishedSelectionDocument)) {
                clearTimeout(selectionTimers.get(publishedSelectionDocument))
                selectionTimers.delete(publishedSelectionDocument)
                publishedSelectionDocument = null
                post('selection', null)
            }
            scheduleBadgeLayout()
            const { cfi, fraction, tocItem, section, time } = e.detail
            const pageStats = bookPageStats(section?.current ?? 0)
            const tocChanged = pageStats && pageStats.tocRevision !== lastSentTocRevision
            post('relocate', {
                cfi,
                fraction: fixedLayout ? fixedLayoutFraction(section) ?? fraction : fraction,
                tocLabel: tocItem?.label?.trim?.() ?? null,
                tocHref: tocItem?.href ?? null,
                currentPage: pageStats?.currentPage ?? null,
                totalPages: pageStats?.totalPages ?? null,
                ...(tocChanged ? { tocPages: pageStats.tocPages } : {}),
                // Minutes remaining at foliate's fixed reading-speed assumption (chars/min) —
                // text remaining to read, so unlike page count this is independent of font size.
                chapterMinutesLeft: Number.isFinite(time?.section) ? time.section : null,
                bookMinutesLeft: Number.isFinite(time?.total) ? time.total : null,
            })
            if (tocChanged) lastSentTocRevision = pageStats.tocRevision
            markFirstRender('relocate')
            checkStoryEnd(section?.current)
            if (fixedLayout) {
                onFixedLayoutRelocate(section?.current)
                view.book.prefetchAround?.(section?.current)
            }
            if (hasOpened && !fixedLayout) {
                for (const { doc, index } of view.renderer.getContents()) {
                    if (doc) queueDocumentEnhancements(doc, index)
                }
            }
        })
        view.addEventListener('load', e => {
            const { doc, index } = e.detail
            if (fixedLayout) {
                wireFixedLayoutPage(doc, index)
                post('pageLoaded', {})
                markFirstRender('load')
                return
            }
            wireSelection(doc, index)
            wireTextPinch(doc)
            wireDoubleTapLookup(doc)
            post('pageLoaded', {})
            markFirstRender('load')
            if (hasOpened) queueDocumentEnhancements(doc, index)
        })
        view.addEventListener('create-overlay', e => {
            const { doc, index } = e.detail
            if (doc) {
                // The event is emitted just before Foliate attaches the overlay. Queueing onto
                // the next task makes failed early annotation draws retry against the real overlay.
                resetPopularBadgeResolutionRetries()
                popularBadgeIndexDirty = true
                queueDocumentEnhancements(doc, index, true)
                scheduleBadgeLayout()
            }
        })
        view.addEventListener('draw-annotation', e => {
            const { draw, annotation } = e.detail
            const color = markColor(annotation.color ?? DefaultAnnotationColor)
            // Community marks are informational. When their range overlaps a personal mark, tapping must edit the
            // personal mark instead of publishing the intentionally non-editable community annotation id.
            const tapOptions = { tapPriority: annotation.editable ? 2 : annotation.popular ? 0 : 1 }
            if (annotation.type === 'underline') {
                draw((rects, options) => {
                    const g = document.createElementNS('http://www.w3.org/2000/svg', 'g')
                    g.classList.add('vayana-dotted-underline')
                    const strokeWidth = 2
                    for (const { left, bottom, width, height } of rects) {
                        if (!(width > 0 && height > 0)) continue
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
                }, tapOptions)
                // The overlay redraws whenever the page is re-paginated; the pills beside it must follow.
                if (annotation.popular) {
                    resetPopularBadgeResolutionRetries()
                    popularBadgeIndexDirty = true
                }
                scheduleBadgeLayout()
            } else {
                const inkStyle = inkStyleFor(annotation.color ?? DefaultAnnotationColor)
                if (inkStyle && inkStyle !== 'highlight') {
                    draw(Overlayer[inkStyle], { color, width: 2.5, ...tapOptions })
                } else {
                    draw((rects, options) => {
                        const g = Overlayer.highlight(rects, options)
                        g.style.fill = color
                        return g
                    }, tapOptions)
                }
            }
        })
        view.addEventListener('show-annotation', e => postAnnotationTap(e.detail))
        view.addEventListener('hide-annotation', hideAnnotationCard)
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
        fixedLayout = Boolean(view.isFixedLayout)
        if (fixedLayout) {
            view.book.setPageColors?.(fixedPageColors)
            void resolveFixedLayoutTocPages(view.book)
        }
        post('log', { step: 'view.init', fixedLayout })
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

        if (!fixedLayout) await assertChaptersReadable(view.book)
        hasOpened = true
        pendingPdfPassword = null
        post('opened', {
            toc: tocToPlain(view.book.toc),
            title: bookTitle(view.book.metadata),
            fixedLayout,
            pageLabels: fixedLayout ? view.book.pageLabels ?? [] : [],
            // The book's own language (its primary subtag, "ml" for ml-IN), for looking words up in that language.
            language: view.language?.locale?.language ?? null,
        })
        if (!fixedLayout) {
            for (const { doc, index } of view.renderer.getContents()) {
                if (doc) queueDocumentEnhancements(doc, index)
            }
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
 * A damaged EPUB (a truncated download, a zip whose chapters were lost) still has a package file listing its chapters,
 * so it opens, and then shows a blank page with no hint why. If none of the first chapters can be read, say so.
 */
async function assertChaptersReadable(book) {
    const sections = (book?.sections ?? []).filter(section => section.linear !== 'no').slice(0, 3)
    if (!sections.length) return
    let readable = false
    for (const section of sections) {
        try {
            if (await section.load()) {
                readable = true
                break
            }
        } catch (_) {}
    }
    if (!readable) throw new Error('The book file is damaged: its chapters are missing. Try getting or exporting it again.')
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

function visiblePopularBadgeRect(rects, { pageStart, pageSize, iframeLeft, iframeTop, viewportWidth, viewportHeight }) {
    for (const rect of rects ?? []) {
        if (!(rect.width > 0 && rect.height > 0) ||
            rect.left < pageStart || rect.left >= pageStart + pageSize) continue
        const left = iframeLeft + rect.left
        const top = iframeTop + rect.top
        if (left + rect.width <= 0 || left >= viewportWidth || top + rect.height <= 0 || top >= viewportHeight) continue
        return rect
    }
    return null
}

const popularBadgeLayer = document.createElement('div')
Object.assign(popularBadgeLayer.style, { position: 'fixed', inset: '0', pointerEvents: 'none', zIndex: '20' })
document.body.append(popularBadgeLayer)
const resolvedBadgeCfis = new Map()
const popularBadgesBySection = new Map()
let popularBadgeIndexDirty = true
let badgeLayoutQueued = false
let popularBadgeResolutionRetryTimer = null
let popularBadgeResolutionRetryAttempts = 0
const MaxPopularBadgeResolutionRetries = 3

function resetPopularBadgeResolutionRetries() {
    popularBadgeResolutionRetryAttempts = 0
    if (popularBadgeResolutionRetryTimer !== null) {
        window.clearTimeout(popularBadgeResolutionRetryTimer)
        popularBadgeResolutionRetryTimer = null
    }
}

/** A renderer transition can make CFI resolution fail for a frame; retry briefly without polling forever. */
function schedulePopularBadgeResolutionRetry() {
    if (popularBadgeResolutionRetryTimer !== null ||
        popularBadgeResolutionRetryAttempts >= MaxPopularBadgeResolutionRetries) return
    const delay = 32 * (2 ** popularBadgeResolutionRetryAttempts++)
    popularBadgeResolutionRetryTimer = window.setTimeout(() => {
        popularBadgeResolutionRetryTimer = null
        popularBadgeIndexDirty = true
        scheduleBadgeLayout()
    }, delay)
}

/** Resolve and parse annotations when they change, not on every page turn. */
function rebuildPopularBadgeIndex() {
    popularBadgesBySection.clear()
    let unresolved = false
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
    for (const cachedCfi of resolvedBadgeCfis.keys()) {
        if (!byCfi.has(cachedCfi)) resolvedBadgeCfis.delete(cachedCfi)
    }
    for (const [cfi, metadata] of byCfi) {
        let resolved = resolvedBadgeCfis.get(cfi)
        if (resolved == null) {
            try {
                resolved = view.resolveCFI(cfi)
            } catch (_) {
                resolved = null
            }
            // A null here is normally a transient renderer/overlay timing miss. Do not make it
            // permanent: retry after the renderer has had another chance to settle.
            if (resolved) resolvedBadgeCfis.set(cfi, resolved)
            else {
                resolvedBadgeCfis.delete(cfi)
                unresolved = true
            }
        }
        if (resolved) {
            const group = popularBadgesBySection.get(resolved.index) ?? []
            group.push({ ...metadata, resolved })
            popularBadgesBySection.set(resolved.index, group)
        }
    }
    popularBadgeIndexDirty = unresolved
    if (unresolved) schedulePopularBadgeResolutionRetry()
    else resetPopularBadgeResolutionRetries()
}

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
    const renderer = view?.renderer
    // Scrolled layouts have no pages, and so no page margin to put a pill in.
    if (!renderer || renderer.scrolled || !activeAnnotationsList.length) {
        popularBadgeLayer.replaceChildren()
        return
    }
    if (popularBadgeIndexDirty) rebuildPopularBadgeIndex()
    const contents = renderer.getContents()
    const pageSize = renderer.size
    // The paginator scrolls a spare page-width of padding ahead of the chapter, so the visible page begins at
    // `start - size` in the chapter document's own coordinates (the ones quote rects use).
    const pageStart = Math.max(0, renderer.start - pageSize)
    const badgeHeight = 18
    const badges = []
    for (const content of contents) {
        const frame = content?.doc?.defaultView?.frameElement
        if (!frame) continue
        const frameRect = frame.getBoundingClientRect()
        for (const { count, color, resolved } of popularBadgesBySection.get(content.index) ?? []) {
            let range = null
            try {
                range = resolved.anchor(content.doc)
            } catch (_) {}
            const rect = visiblePopularBadgeRect(range?.getClientRects?.(), {
                pageStart, pageSize, iframeLeft: frameRect.left, iframeTop: frameRect.top,
                viewportWidth: window.innerWidth, viewportHeight: window.innerHeight,
            })
            if (!rect) continue
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
            if (place) badges.push({ text, badgeWidth, badgeHeight, place, color })
        }
    }
    const fragment = document.createDocumentFragment()
    for (const { text, badgeWidth, badgeHeight, place, color } of badges) {
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
            // E-Ink: a coloured chip dithers to grey, so the count is plain black digits with no chip behind it.
            background: inkMarks ? 'none' : markColor(color),
            color: inkMarks ? '#000000' : '#ffffff',
            font: inkMarks ? '700 12px/1 sans-serif' : '700 11px/1 sans-serif',
            opacity: inkMarks ? '1' : '0.95',
        })
        fragment.append(pill)
    }
    popularBadgeLayer.replaceChildren(fragment)
}

window.addEventListener('resize', scheduleBadgeLayout)
window.addEventListener('vayana-ink-marks', scheduleBadgeLayout)

/**
 * A Foliate overlayer belongs to one loaded document and is discarded with it.
 * The annotation caches below are book-wide, so a cached CFI is not proof that
 * its underline still exists in a newly-created overlay. Repaint the marks for
 * this document before matching any community quotes that are not resolved yet.
 */
async function restoreAnnotationsForOverlay(doc, index) {
    const current = view?.renderer?.getContents?.()
        ?.some(content => content?.doc === doc && content.index === index && content.overlayer)
    if (!current || !activeAnnotationsList.length) return

    const additions = []
    const restoredCfis = new Set()
    // A highlight must claim a shared CFI before a note, underline, or community mark can consume it.
    const annotationsByTapPriority = [...activeAnnotationsList].sort((a, b) =>
        Number(Boolean(b.editable)) - Number(Boolean(a.editable)))
    for (const annotation of annotationsByTapPriority) {
        let cfi = annotation.value
        if (isTextAnnotationValue(cfi)) {
            cfi = resolvedTextAnnotations.get(cfi)
            if (!cfi) continue
            // Near-identical community quotes can share a range. Restore only the
            // authoritative one so a duplicate cannot overwrite its style/count.
            const source = authoritativeSourceForCfi.get(cfi)
            if (source && source !== annotation.value) continue
        }
        if (!cfi || restoredCfis.has(cfi)) continue
        let resolved = null
        try {
            resolved = view.resolveCFI(cfi)
        } catch (_) {}
        if (resolved?.index !== index) continue
        restoredCfis.add(cfi)
        additions.push(Promise.resolve(view.addAnnotation({ ...annotation, value: cfi })))
    }
    if (additions.length) await Promise.allSettled(additions)
    resetPopularBadgeResolutionRetries()
    popularBadgeIndexDirty = true
    scheduleBadgeLayout()
}

// 'relocate' can fire many times in a row for the same page turn (each a no-op full document
// walk once bionic reading/annotations are already applied) - collapse repeats scheduled
// before the first one runs into a single pass instead of stacking up redundant timeouts.
const enhancementsPending = new WeakMap()
const completedMatchingRevision = new WeakMap()
function queueDocumentEnhancements(doc, index, restoreOverlay = false) {
    const pending = enhancementsPending.get(doc)
    if (pending) {
        // A relocate/load callback can reach this document just before create-overlay.
        // Preserve the later, stronger request instead of letting the first timer swallow it.
        pending.restoreOverlay ||= restoreOverlay
        return
    }
    const request = { restoreOverlay }
    enhancementsPending.set(doc, request)
    setTimeout(async () => {
        enhancementsPending.delete(doc)
        applyBionicReadingToDoc(doc)
        if (request.restoreOverlay) await restoreAnnotationsForOverlay(doc, index)
        matchTextAnnotationsForDoc(doc, index)
    }, 0)
}

function next() {
    if (fixedLayout && scrollFixedPage(1)) return
    view?.next()
}
function prev() {
    if (fixedLayout && scrollFixedPage(-1)) return
    view?.prev()
}
function goLeft() { view?.goLeft() }
function goRight() { view?.goRight() }
function goToFraction(fraction) {
    if (fixedLayout) {
        const index = fixedLayoutIndexOfFraction(fraction, view?.book?.sections?.length ?? 0)
        if (index != null) view.goTo(index)
        return
    }
    view?.goToFraction(fraction)
}

async function goToPage(pageIndex) {
    if (!Number.isInteger(pageIndex) || pageIndex < 0 || !view) return
    await restorePageJumpLayout()
    if (fixedLayout) {
        const total = view.book?.sections?.length ?? 0
        if (pageIndex < total) await view.goTo(pageIndex)
        return
    }
    if (!pageEstimateCache || pageIndex >= pageEstimateCache.totalPages) return
    let index = pageSectionIndex(pageIndex)
    // Measurements can move the requested page into another chapter. Correct the
    // estimate at most twice, keeping navigation bounded on inconsistent layouts.
    for (let attempt = 0; attempt < 3; attempt++) {
        if (index < 0) return
        if (!view.renderer.getContents().some(content => content.index === index)) {
            await view.goTo(index)
        }
        // A damaged chapter or a busy renderer may leave the old chapter open.
        // Never sample it as though the requested chapter had loaded.
        if (!view.renderer.getContents().some(content => content.index === index)) return
        bookPageStats(index)
        const correctedIndex = pageSectionIndex(pageIndex)
        if (correctedIndex === index || attempt === 2) break
        index = correctedIndex
    }
    const pages = Math.max(1, view.renderer.pages - 2)
    const localPage = Math.min(pages - 1, Math.max(0,
        pageIndex - Math.round(pageEstimateCache.prefixPages[index])))
    await view.renderer.scrollToAnchor(pages > 1 ? localPage / (pages - 1) : 0)
}

function preparePageJump() {
    pageJumpSnapshot = {
        width: window.innerWidth, height: window.innerHeight,
        densities: new Map(bytesPerPage), estimate: pageEstimateCache,
        cfi: view?.lastLocation?.cfi,
        section: view?.renderer?.getContents?.()[0]?.index, page: view?.renderer?.page,
    }
}

async function restorePageJumpLayout() {
    const snapshot = pageJumpSnapshot
    if (!snapshot) return null
    pageJumpSnapshot = null
    const matches = () => window.innerWidth === snapshot.width && window.innerHeight === snapshot.height
    const deadline = Date.now() + 2000
    // IME dismissal can briefly reach the original size, then resize again. Only
    // accept it after ResizeObserver has had two frames at the original size.
    while (Date.now() < deadline) {
        if (matches()) {
            await new Promise(resolve => requestAnimationFrame(() => requestAnimationFrame(resolve)))
            if (matches()) break
        } else await new Promise(requestAnimationFrame)
    }
    if (matches()) {
        bytesPerPage.clear()
        for (const [index, density] of snapshot.densities) bytesPerPage.set(index, density)
        pageEstimateCache = snapshot.estimate
        lastSentTocRevision = -1
    } else {
        resetPageEstimate()
        const index = view?.renderer?.getContents?.()[0]?.index
        if (Number.isInteger(index)) bookPageStats(index)
    }
    return snapshot
}

async function cancelPageJump() {
    const snapshot = await restorePageJumpLayout()
    if (!snapshot) return
    if (!fixedLayout && window.innerWidth === snapshot.width && window.innerHeight === snapshot.height
        && Number.isFinite(snapshot.page)
        && view?.renderer?.getContents?.()[0]?.index === snapshot.section) {
        const pages = Math.max(1, view.renderer.pages - 2)
        const localPage = Math.min(pages - 1, Math.max(0, snapshot.page - 1))
        await view.renderer.scrollToAnchor(pages > 1 ? localPage / (pages - 1) : 0)
    } else if (snapshot.cfi) await view?.goTo(snapshot.cfi)
}

function pageSectionIndex(pageIndex) {
    const prefix = pageEstimateCache?.prefixPages
    if (!prefix) return -1
    // Prefixes are ordered even when non-linear sections repeat a boundary.
    let low = 1, high = prefix.length
    while (low < high) {
        const middle = Math.floor((low + high) / 2)
        if (Math.round(prefix[middle]) > pageIndex) high = middle
        else low = middle + 1
    }
    let index = low < prefix.length ? low - 1 : -1
    // Independently rounded chapter prefixes can differ by one from the displayed
    // total. Its final page still belongs to the last non-empty chapter.
    if (index < 0) {
        for (let i = prefix.length - 2; i >= 0; i--) {
            if (prefix[i + 1] > prefix[i]) { index = i; break }
        }
    }
    return index
}

function providePdfPassword(password) {
    const updatePassword = pendingPdfPassword
    pendingPdfPassword = null
    if (!updatePassword) return
    updatePassword(typeof password === 'string' ? password : new Error('PDF password entry cancelled'))
}

async function pageThumbnail(requestId, pageIndex, maxWidth) {
    let dataUrl = null
    try {
        dataUrl = fixedLayout ? await view?.book?.getPageThumbnail?.(pageIndex, maxWidth) : null
    } catch (error) {
        post('log', { step: 'pageThumbnail', pageIndex, message: String(error) })
    }
    post('reply', { requestId, dataUrl })
}

// A fixed-layout page counts as read once it is on screen, so the last page is 100%. Navigating by fraction maps back
// to the same page: fixedLayoutIndexOfFraction(fixedLayoutFraction(i)) === i.
function fixedLayoutFraction(section) {
    const { current, total } = section ?? {}
    return Number.isInteger(current) && total > 0 ? Math.min(1, (current + 1) / total) : null
}

function fixedLayoutIndexOfFraction(fraction, total) {
    if (!(total > 0) || !Number.isFinite(fraction)) return null
    // The epsilon absorbs float error: (i + 1) / n * n can come out a hair above i + 1.
    return Math.max(0, Math.min(total - 1, Math.ceil(fraction * total - 1e-9) - 1))
}

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

    if (href.startsWith('text:search:')) {
        try {
            const payload = href.slice('text:search:'.length)
            const delimiter = payload.indexOf(':')
            if (delimiter < 0) return
            const chapter = decodeURIComponent(payload.slice(0, delimiter))
            const text = decodeURIComponent(payload.slice(delimiter + 1))
            if (!chapter || !text || text.length > 1800) return
            const resolved = view.book.resolveHref(chapter)
            const index = resolved?.index
            const section = Number.isInteger(index) ? view.book.sections[index] : null
            const doc = await section?.createDocument?.()
            const range = doc ? findTextRangeInDoc(doc, text) : null
            if (range) { range.collapse(true); await view.goTo(view.getCFI(index, range)) }
            else if (resolved) await view.goTo(chapter)
        } catch (error) { post('log', { step: 'contentsNavigation', message: String(error) }) }
        return
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
    const contentWidth = Math.max(240, Math.round(viewportWidth - marginPx * 2))
    const viewportHeight = window.visualViewport?.height || innerHeight
    let maxInlineSizePx = contentWidth
    if (viewportWidth >= 700) {
        // Tablet: a landscape page becomes a two-page spread (each column just under half the width so the
        // paginator picks two columns); a portrait page keeps one column capped at a readable line length.
        maxInlineSizePx = viewportWidth > viewportHeight && viewportWidth >= 900
            ? Math.round(contentWidth * 0.45)
            : Math.min(contentWidth, 760)
    }
    if (view?.renderer) {
        const value = `${maxInlineSizePx}px`
        // The paginator's attribute callback renders. A height-only IME resize
        // is already handled by its ResizeObserver and needs no margin update.
        if (view.renderer.getAttribute('max-inline-size') !== value)
            view.renderer.setAttribute('max-inline-size', value)
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
    completedMatchingRevision.delete(doc)
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

const TextPinchThresholdRatio = 0.12

function textPinchStep(startDistance, endDistance, selectionActive, threshold = TextPinchThresholdRatio) {
    if (selectionActive || !(startDistance > 0) || !Number.isFinite(endDistance) || endDistance < 0) return 0
    const ratio = endDistance / startDistance
    if (ratio >= 1 + threshold) return 1
    if (ratio <= 1 - threshold) return -1
    return 0
}

function wireTextPinch(doc) {
    let pinch = null
    doc.addEventListener('touchstart', event => {
        if (event.touches.length !== 2 || hasReaderSelection(doc)) {
            pinch = null
            return
        }
        const distance = touchDistance(event.touches[0], event.touches[1])
        pinch = distance > 0 ? { startDistance: distance, endDistance: distance } : null
    }, { passive: true })
    doc.addEventListener('touchmove', event => {
        if (!pinch || event.touches.length !== 2) return
        if (hasReaderSelection(doc)) {
            pinch = null
            return
        }
        event.preventDefault()
        pinch.endDistance = touchDistance(event.touches[0], event.touches[1])
    }, { passive: false })
    doc.addEventListener('touchend', event => {
        if (!pinch || event.touches.length >= 2) return
        const completed = pinch
        pinch = null
        const direction = textPinchStep(
            completed.startDistance,
            completed.endDistance,
            hasReaderSelection(doc),
        )
        if (direction) post('fontSizeStep', { direction })
    }, { passive: true })
    doc.addEventListener('touchcancel', () => { pinch = null }, { passive: true })
}

const SelectionDragSettleMillis = 600

function wireSelection(doc, index) {
    let pointerSelecting = false
    let touchSelecting = false
    let observedBoundary = null
    const selectionBoundary = () => {
        const selection = doc.getSelection?.()
        if (!selection?.rangeCount) return null
        return {
            anchorNode: selection.anchorNode,
            anchorOffset: selection.anchorOffset,
            focusNode: selection.focusNode,
            focusOffset: selection.focusOffset,
            collapsed: selection.isCollapsed,
        }
    }
    const sameBoundary = (a, b) => Boolean(a && b &&
        a.anchorNode === b.anchorNode && a.anchorOffset === b.anchorOffset &&
        a.focusNode === b.focusNode && a.focusOffset === b.focusOffset &&
        a.collapsed === b.collapsed)
    const rememberBoundary = () => { observedBoundary = selectionBoundary() }
    const schedulePost = delay => {
        clearTimeout(selectionTimers.get(doc))
        selectionTimers.set(doc, setTimeout(() => {
            selectionTimers.delete(doc)
            if (!pointerSelecting && !touchSelecting) postSelection(doc, index)
        }, delay))
    }
    const hideSelectionCard = () => {
        clearTimeout(selectionTimers.get(doc))
        selectionTimers.delete(doc)
        hideAnnotationCard()
        if (publishedSelectionDocument) {
            publishedSelectionDocument = null
            post('selection', null)
        }
    }
    const publishWhenReleased = () => {
        if (!pointerSelecting && !touchSelecting) schedulePost(0)
    }
    doc.addEventListener('pointerdown', () => {
        pointerSelecting = true
        hideSelectionCard()
    })
    doc.addEventListener('touchstart', () => {
        touchSelecting = true
        hideSelectionCard()
    }, { passive: true })
    doc.addEventListener('pointerup', () => {
        pointerSelecting = false
        if (!touchSelecting) rememberBoundary()
        publishWhenReleased()
    })
    doc.addEventListener('pointercancel', () => {
        // Android cancels the pointer when native text selection takes over,
        // while the finger is still down. Wait for touchend before showing UI.
        pointerSelecting = false
        if (!touchSelecting) rememberBoundary()
        publishWhenReleased()
    })
    const finishTouchSelection = () => {
        touchSelecting = false
        rememberBoundary()
        publishWhenReleased()
    }
    doc.addEventListener('touchend', finishTouchSelection, { passive: true })
    doc.addEventListener('touchcancel', finishTouchSelection, { passive: true })
    doc.defaultView?.addEventListener('blur', () => {
        pointerSelecting = false
        touchSelecting = false
        rememberBoundary()
        publishWhenReleased()
    })
    doc.addEventListener('selectionchange', () => {
        if (!pointerSelecting && !touchSelecting) {
            const boundary = selectionBoundary()
            // Android WebView can emit duplicate selectionchange notifications for
            // the same native range while layout/handles settle. They must not hide
            // a stable card or restart its settle delay and dictionary lookup.
            if (sameBoundary(observedBoundary, boundary)) return
            observedBoundary = boundary
            // A forgiving annotation tap can land just outside the glyph rect.
            // Android then moves its collapsed caret after the click; that is not
            // a text selection and must not close the annotation card just opened.
            if (boundary?.collapsed) {
                schedulePost(SelectionDragSettleMillis)
                return
            }
            // Android's native selection handles can move the range without
            // forwarding their touch stream into the document. Treat every
            // range change as an active drag and wait for the range to settle.
            hideSelectionCard()
            schedulePost(SelectionDragSettleMillis)
        }
    })
}

function postSelection(doc, index) {
    const active = view?.renderer?.getContents?.()
        ?.some(content => content?.doc === doc && content.index === index)
    // A delayed selectionchange from an unloaded chapter must not resurrect its
    // stale range/card after a page turn.
    if (!active) {
        if (publishedSelectionDocument === doc) {
            publishedSelectionDocument = null
            post('selection', null)
        }
        return
    }
    const selection = doc.getSelection()
    if (!selection || !selection.rangeCount || selection.isCollapsed) {
        if (publishedSelectionDocument === doc) publishedSelectionDocument = null
        post('selection', null)
        return
    }
    const selectedText = selection.toString().trim()
    if (!selectedText) {
        if (publishedSelectionDocument === doc) publishedSelectionDocument = null
        post('selection', null)
        return
    }
    const range = selection.getRangeAt(0).cloneRange()
    const focusRange = doc.createRange()
    let rect = null
    try {
        focusRange.setStart(selection.focusNode, selection.focusOffset)
        focusRange.collapse(true)
        rect = focusRange.getClientRects()[0] ?? focusRange.getBoundingClientRect()
    } catch (_) {}
    if (!rect || (!rect.width && !rect.height)) {
        const rects = range.getClientRects()
        rect = rects[rects.length - 1] ?? range.getBoundingClientRect()
    }
    // The section sits in an iframe scrolled inside the reader view; edges are reported against the view (the WebView).
    const frameTop = doc.defaultView?.frameElement?.getBoundingClientRect().top ?? 0
    const viewHeight = window.innerHeight
    const fractionOfView = y => viewHeight > 0 ? Math.max(0, Math.min(1, (frameTop + y) / viewHeight)) : null
    const wordLookup = wordLookupSelection
    wordLookupSelection = false
    publishedSelectionDocument = doc
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
const readerTapState = new WeakMap()
let wordLookupSelection = false
let readerControlsTapCount = 2
let readerControlsVisible = false
let readerControlsBlocked = false

function setReaderControlsGesture(tapCount, controlsVisible, blocked) {
    const count = Math.trunc(Number(tapCount))
    readerControlsTapCount = count >= 1 && count <= 3 ? count : 2
    readerControlsVisible = Boolean(controlsVisible)
    readerControlsBlocked = Boolean(blocked)
}

function clearReaderTapState(doc) {
    const state = readerTapState.get(doc)
    if (state?.timer) clearTimeout(state.timer)
    readerTapState.delete(doc)
}

function hasReaderSelection(doc) {
    const selection = doc.getSelection?.()
    return selection?.type === 'Range' && !selection.isCollapsed
}

function requestReaderControls(doc) {
    clearReaderTapState(doc)
    if (!readerControlsVisible && !readerControlsBlocked && !hasReaderSelection(doc))
        post('controlsRequested', {})
}

function lookupOrZoomAt(doc, x, y) {
    clearReaderTapState(doc)
    if (readerControlsVisible || readerControlsBlocked) return
    // On a PDF page, a double tap away from any word (or on a scanned page with no text) zooms instead.
    if (!selectWordAt(doc, x, y) && fixedLayout) toggleFixedZoom(doc, x, y)
}

// Reader-control taps and word lookup share the middle of the page. A double tap on a word always keeps its dictionary
// meaning; otherwise the configured count opens controls. Triple-tap mode delays double-tap lookup by one tap window
// so a third tap can win without briefly opening the dictionary first.
function wireDoubleTapLookup(doc) {
    doc.addEventListener('click', e => {
        if (e.target?.closest?.('a[href]')) return
        if (readerControlsVisible || readerControlsBlocked) return
        const width = (doc.defaultView?.top ?? window).innerWidth || 1
        const horizontal = e.screenX / width
        if (horizontal < 1 / 3 || horizontal > 2 / 3) return
        const last = readerTapState.get(doc)
        const continues = last &&
            e.timeStamp - last.time <= DoubleTapWindowMillis &&
            Math.hypot(e.clientX - last.x, e.clientY - last.y) <= DoubleTapSlopPx
        if (last?.timer) clearTimeout(last.timer)
        const state = {
            time: e.timeStamp,
            x: e.clientX,
            y: e.clientY,
            count: continues ? last.count + 1 : 1,
            timer: null,
        }
        readerTapState.set(doc, state)

        if (readerControlsTapCount === 1) {
            if (state.count >= 2) lookupOrZoomAt(doc, e.clientX, e.clientY)
            else state.timer = setTimeout(() => requestReaderControls(doc), DoubleTapWindowMillis)
            return
        }
        if (readerControlsTapCount === 2) {
            if (state.count >= 2) {
                clearReaderTapState(doc)
                if (!selectWordAt(doc, e.clientX, e.clientY)) requestReaderControls(doc)
            } else {
                state.timer = setTimeout(() => clearReaderTapState(doc), DoubleTapWindowMillis)
            }
            return
        }
        if (state.count >= 3) {
            requestReaderControls(doc)
        } else if (state.count === 2) {
            state.timer = setTimeout(() => lookupOrZoomAt(doc, e.clientX, e.clientY), DoubleTapWindowMillis)
        } else {
            state.timer = setTimeout(() => clearReaderTapState(doc), DoubleTapWindowMillis)
        }
    })
}

function selectWordAt(doc, x, y) {
    const caret = doc.caretRangeFromPoint?.(x, y)
    const node = caret?.startContainer
    if (!node || node.nodeType !== 3) return false
    const range = doc.createRange()
    const bionicWord = node.parentElement?.closest(`.${BionicWordClass}`)
    if (bionicWord) {
        range.selectNodeContents(bionicWord)
    } else {
        const word = [...segmenterFor(doc, 'word').segment(node.data)]
            .find(({ segment, index }) => caret.startOffset >= index && caret.startOffset < index + segment.length)
        if (!word?.isWordLike) return false
        range.setStart(node, word.index)
        range.setEnd(node, word.index + word.segment.length)
    }
    wordLookupSelection = true
    const selection = doc.getSelection()
    selection.removeAllRanges()
    selection.addRange(range)
    return true
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
    // The community count pills are drawn in the ink colour too: have them redrawn.
    globalThis.dispatchEvent?.(new Event('vayana-ink-marks'))
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
const speech = { index: -1, sentences: new Map(), markedOverlayer: null, turning: false, pdfMarginPages: new Map() }

// Only classify PDF running text at the outer 5% of a page, and only after it repeats on three distinct pages.
// This avoids removing a chapter title or repeated prose from the reading area. The first two occurrences stay audible.
function repeatedPdfSpeechMargins(root, pageIndex) {
    const groups = new Map()
    for (const span of root.querySelectorAll('span')) {
        if (!/^\d+(?:\.\d+)?%$/.test(span.style.top)) continue
        const top = parseFloat(span.style.top)
        if (top > 5 && top < 95) continue
        const key = `${top <= 5 ? 'top' : 'bottom'}:${Math.round(top * 2) / 2}`
        if (!groups.has(key)) groups.set(key, [])
        groups.get(key).push(span)
    }
    const ignored = new Set()
    for (const [position, spans] of groups) {
        const label = spans.map(span => span.textContent).join(' ').replace(/\s+/gu, ' ').trim()
        if (/^\d{1,5}$/.test(label)) { spans.forEach(span => ignored.add(span)); continue }
        if ((label.match(/\p{L}/gu)?.length ?? 0) < 6) continue
        const key = `${position}:${label.toLocaleLowerCase()}`
        let pages = speech.pdfMarginPages.get(key)
        if (!pages) {
            if (speech.pdfMarginPages.size >= 128) speech.pdfMarginPages.delete(speech.pdfMarginPages.keys().next().value)
            pages = new Set()
            speech.pdfMarginPages.set(key, pages)
        }
        if (pages.size < 3) pages.add(pageIndex)
        if (pages.size >= 3) spans.forEach(span => ignored.add(span))
    }
    return ignored
}

// Mask abbreviation stops before ICU sentence segmentation. Every replacement here has the same UTF-16
// length as the source so timed highlighting can still address the original DOM. Ambiguous abbreviations
// keep their final period before a capitalized sentence or paragraph break.
function speechParagraphBreak(gap) {
    return /\n\s*\n/u.test(gap.replace(/\r\n?/g, '\n'))
}

function speechTextWithoutAbbreviationStops(text) {
    const withoutStop = (match, gap) => speechParagraphBreak(gap)
        ? match : match.slice(0, -gap.length - 1) + ' ' + gap.replace(/[\r\n]/g, ' ')
    let prepared = text.replace(
        /(?<![\p{L}\p{N}_])(?:Mr|Mrs|Ms|Mx|Dr|Prof|Rev|Fr|Capt|Lt|Col|Gen|Sgt|Maj|Cmdr|Hon|Pres|Gov|Sen|Rep|Supt|Det)\.(\s+)(?=\p{L})/giu,
        withoutStop,
    )
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:No|Nos|Vol|Ch|Chap|Fig|Figs|Eq|Eqs|Sec|pp|p)\.(\s+)(?=\d)/giu,
        withoutStop,
    )
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:Mr|Mrs|Ms|Mx|Dr|Prof|Rev|Fr)[ \t]+[A-Z]\.(\s+)(?=[A-Z]\p{L})/gu,
        withoutStop,
    )
    // Abbreviated place names in prose, e.g. "in S. Place" and "towards K. bridge".
    // Require a location preposition and place word so list labels such as "A. First item" stay intact.
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:in|at|from|to|towards?|near|on|of|by|into|past|through)[ \t]+[A-Z]\.(\s+)(?=(?:place|street|road|avenue|lane|square|bridge|district|town|city)\b)/giu,
        withoutStop,
    )
    // Two or more spaced name initials, as in J. R. R. Tolkien. A single "A." may be a list label.
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:[A-Z]\.\s+){2,}(?=[A-Z]\p{L})/gu,
        match => speechParagraphBreak(match) ? match : match.replace(/[.\r\n]/g, ' '),
    )
    // Dotted acronyms and clock suffixes: internal dots never end a sentence. The final dot can.
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:(?:[A-Z]\.){2,}|[ap]\.m\.)/gu,
        (match, offset) => {
            const following = prepared.slice(offset + match.length)
            const gap = following.match(/^\s+/u)?.[0] ?? ''
            const continues = !speechParagraphBreak(gap) && (/^\s+[a-z]/u.test(following) || /^[,;:]/u.test(following))
            return match.slice(0, -1).replace(/\./g, ' ') + (continues ? ' ' : '.')
        },
    )
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:e\.g|i\.e|vs)\.(\s+)(?=\p{L})/giu,
        (match, gap) => speechParagraphBreak(gap) ? match : match.replace(/[.\r\n]/g, ' '),
    )
    prepared = prepared.replace(
        /(?<![\p{L}\p{N}_])(?:e\.g\.|i\.e\.)(?=[,;:])/giu,
        match => match.replace(/\./g, ' '),
    )
    return prepared.replace(
        /(?<![\p{L}\p{N}_])(?:etc|vs)\.(?=\s+[a-z]|[,;:])/gu,
        (match, offset) => {
            const gap = prepared.slice(offset + match.length).match(/^\s+/u)?.[0] ?? ''
            return speechParagraphBreak(gap) ? match : match.slice(0, -1) + ' '
        },
    )
}

// Android TTS reports UTF-16 offsets into the whitespace-normalized text it receives. Keep a boundary map back to
// the EPUB's original text so every timed range can become an exact DOM Range even across collapsed whitespace.
function normalizeSpeechSegment(segment, prepared = segment, omitted = new Set(), sourceOffset = 0) {
    let text = ''
    const sourceStarts = []
    const sourceEnds = []
    // Speak familiar prose abbreviations as words. Each expanded character maps to the original abbreviation,
    // while all later words keep their own exact source offsets.
    const expansions = new Map()
    const words = { 'e.g.': 'for example', 'i.e.': 'that is', 'etc.': 'et cetera', 'vs.': 'versus' }
    for (const match of segment.matchAll(/(?<![\p{L}\p{N}_])(?:e\.g\.|i\.e\.|etc\.|vs\.)(?![\p{L}\p{N}_])/giu)) {
        const end = match.index + match[0].length
        const punctuation = prepared[end - 1] === '.' ? '.' : ''
        expansions.set(match.index, { end, text: words[match[0].toLowerCase()] + punctuation })
    }
    for (let index = 0; index < segment.length;) {
        if (segment[index] === '\u00ad' || omitted.has(index + sourceOffset)) { index++; continue }
        const expansion = expansions.get(index)
        if (expansion) {
            text += expansion.text
            for (let i = 0; i < expansion.text.length; i++) {
                sourceStarts.push(index)
                sourceEnds.push(expansion.end)
            }
            index = expansion.end
        } else if (/\s/u.test(prepared[index])) {
            let end = index + 1
            while (end < segment.length && /\s/u.test(prepared[end])) end++
            if (text && end < segment.length) {
                text += ' '
                sourceStarts.push(index)
                sourceEnds.push(end)
            }
            index = end
        } else {
            text += prepared[index]
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
    // On a PDF page only the text layer holds the words (the link layer can carry form text).
    const root = fixedLayout ? doc.querySelector('.textLayer') ?? doc.body : doc.body
    const ignoredMargins = fixedLayout ? repeatedPdfSpeechMargins(root, index) : new Set()
    const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT | NodeFilter.SHOW_ELEMENT)
    const pieces = []
    const boundaries = []
    let text = ''
    let lastBlock = null
    let pendingPause = 0
    for (let node = walker.nextNode(); node; node = walker.nextNode()) {
        if (node.nodeType === 1) {
            if (node.localName === 'hr') {
                pendingPause = 900
                if (text) {
                    text += '\n\n'
                    boundaries.push({ start: text.length, pause: 900 })
                }
            }
            // Keep printed line breaks until speech cleanup can distinguish word wraps from compound hyphens.
            if (node.localName === 'br' && text) text += '\n'
            continue
        }
        const parent = node.parentElement
        if (!node.data || parent?.closest('script, style, rt, [hidden], [aria-hidden="true"], [role="doc-noteref"], [epub\\:type~="noteref"]')) continue
        if (parent?.closest('a') && isSpeechNoteReference(parent.closest('a'))) continue
        if (parent?.closest('[role="doc-pageheader"], [role="doc-pagefooter"]')) continue
        if (fixedLayout && Array.from(ignoredMargins).some(span => span.contains(node))) continue
        // Separate blocks so a paragraph without closing punctuation doesn't run into the next one.
        const block = parent?.closest('p, li, h1, h2, h3, h4, h5, h6, blockquote, dd, dt, td, figcaption, div, section')
        if (!node.data.trim() && (!block || block !== lastBlock)) continue
        if (lastBlock && block !== lastBlock) {
            text += '\n\n'
            const heading = /^h[1-6]$/.test(block?.localName ?? '') || /^h[1-6]$/.test(lastBlock.localName)
            const sceneBreak = /^[*#•\-—\s]{3,}$/u.test(lastBlock.textContent ?? '')
            boundaries.push({ start: text.length, pause: Math.max(pendingPause, sceneBreak ? 900 : heading ? 600 : 250) })
            pendingPause = 0
        }
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
    let previousStart = -1
    let boundaryIndex = 0
    const omitted = new Set()
    let prepared = speechTextWithoutAbbreviationStops(text)
    // ICU treats a line separator as a sentence ending, even inside unpunctuated prose. EPUB source
    // wrapping and single <br> elements must not create extra TTS utterances. Preserve blank lines and
    // injected block boundaries, and keep the same UTF-16 length for DOM offset mapping.
    prepared = prepared.replace(/[ \t\r\n\u2028]+/gu, gap => speechParagraphBreak(gap)
        ? gap : gap.replace(/[\r\n\u2028]/gu, ' '))
    if (fixedLayout) {
        // Printed line breaks are word boundaries. Join clear word-wrap hyphens, but retain common compound prefixes.
        for (const match of text.matchAll(/(\p{L}+)[-\u00ad][ \t]*\n[ \t]*(?=\p{Ll})/gu)) {
            const compound = text[match.index + match[1].length] === '-' && /^(?:well|self|ex|non|co|anti|pro|pre|re|high|low|long|short)$/iu.test(match[1])
            for (let offset = match.index + match[1].length + (compound ? 1 : 0); offset < match.index + match[0].length; offset++) omitted.add(offset)
        }
        prepared = prepared.replace(/[^]/g, (character, offset) => omitted.has(offset) ? ' ' : character)
        prepared = prepared.replace(/(?<!\n)\n(?!\n)/g, ' ')
    }
    for (const { segment, index: segmentStart } of segmenterFor(doc, 'sentence').segment(prepared)) {
        if (!/[\p{L}\p{N}]/u.test(segment)) continue
        // The sentence spans its first to last non-space character. Skip those that end before the page on screen
        // before doing any per-character work or building a Range for them.
        const start = segmentStart + segment.length - segment.trimStart().length
        const endPoint = pointAt(segmentStart + segment.trimEnd().length)
        if (fromRange && fromRange.comparePoint(...endPoint) < 0) continue
        const normalized = normalizeSpeechSegment(text.slice(segmentStart, segmentStart + segment.length), segment, omitted, segmentStart)
        const sentenceText = normalized.text
        const range = doc.createRange()
        range.setStart(...pointAt(start))
        range.setEnd(...endPoint)
        const id = `${index}:${sentences.length}`
        let pauseBeforeMs = 0
        while (boundaryIndex < boundaries.length && boundaries[boundaryIndex].start <= start) {
            const boundary = boundaries[boundaryIndex++]
            if (boundary.start > previousStart) pauseBeforeMs = Math.max(pauseBeforeMs, boundary.pause)
        }
        if (!sentences.length) pauseBeforeMs = 0
        previousStart = start
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
        sentences.push({ id, text: sentenceText, pauseBeforeMs })
    }
    return sentences
}

function isSpeechNoteReference(link) {
    const types = `${link.getAttributeNS('http://www.idpf.org/2007/ops', 'type') ?? ''} ${link.getAttribute('epub:type') ?? ''}`
    // Numeric superscript links are common in EPUBs lacking semantic markup. Ordinary superscripts (x²) remain spoken.
    return /\bnoteref\b/.test(types) || (link.closest('sup') && /^[\[(]?\d{1,3}[\])]?$/u.test(link.textContent.trim()))
}

// [fromCfi] starts reading at the sentence holding that position (a selection) instead of at the top of the page.
async function startSpeech(requestId, fromCfi) {
    const content = currentContent()
    if (!content?.doc) {
        post('reply', { requestId, sentences: [], endOfBook: true })
        return
    }
    if (fixedLayout) await whenFixedPageRendered(content.doc)
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
        if (fixedLayout) await whenFixedPageRendered(content?.doc)
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
    if (fixedLayout) {
        markFixedLayoutSpeech(range)
        return
    }
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
    if (fixedSpeechMark) clearFixedLayoutSpeech()
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
async function chapterWordCounts(requestId, minLength) {
    const counts = Object.create(null)
    const doc = currentContent()?.doc
    let text = null
    if (fixedLayout) {
        const current = currentFixedIndex() ?? 0
        const total = view.book.sections?.length ?? 0
        const { start, end } = chapterPageRange(current, total, Object.values(fixedLayoutTocPages ?? {}))
        const pages = []
        for (let index = start; index <= end; index++) {
            try {
                pages.push(await view.book.getPageText(index))
            } catch (error) {
                post('log', { step: 'chapterWords', index, message: String(error) })
            }
        }
        text = pages.join('\n')
    } else if (doc?.body) {
        text = doc.body.textContent ?? ''
    }
    if (doc && text) {
        let kinds = 0
        for (const { segment, isWordLike } of segmenterFor(doc, 'word').segment(text)) {
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
    for (const { doc } of view.renderer.getContents()) {
        clearTimeout(selectionTimers.get(doc))
        selectionTimers.delete(doc)
        doc.getSelection()?.removeAllRanges()
    }
    publishedSelectionDocument = null
    post('selection', null)
}

let searchToken = 0

async function search(query) {
    if (!view) return
    if (fixedLayout) {
        const token = ++searchToken
        try {
            await searchFixedLayout(query, token)
        } catch (e) {
            post('error', { message: `Search failed: ${e.message}` })
        }
        return
    }
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
    if (fixedLayout) {
        fixedSearchQuery = ''
        redrawFixedLayoutMarks()
        return
    }
    if (view?.clearSearch) view.clearSearch()
}

function annotationForRenderedValue(value) {
    // Several saved marks can legitimately share one CFI. Prefer the one for which the reader actually exposes an
    // edit menu, irrespective of database recency or renderer insertion order.
    const direct = activeAnnotationsList.find(annotation => annotation?.value === value && annotation.editable)
        ?? activeAnnotationsList.find(annotation => annotation?.value === value)
    if (direct) return direct
    const sourceValue = authoritativeSourceForCfi.get(value) ?? value
    const candidates = activeAnnotationsList.filter(annotation =>
        annotation?.value === sourceValue || resolvedTextAnnotations.get(annotation?.value) === value)
    return candidates.find(annotation => annotation.editable) ?? candidates[0]
}

const AnnotationTapPadding = 12
const AnnotationTapTargetSize = 56

function rectDistanceSquared(rect, clientX, clientY) {
    const dx = clientX < rect.left ? rect.left - clientX : clientX >= rect.right ? clientX - rect.right : 0
    const dy = clientY < rect.top ? rect.top - clientY : clientY >= rect.bottom ? clientY - rect.bottom : 0
    return dx * dx + dy * dy
}

function rectContainsForgivingPoint(rect, clientX, clientY) {
    const horizontalExpansion = Math.max(AnnotationTapPadding, (AnnotationTapTargetSize - (rect.right - rect.left)) / 2)
    const verticalExpansion = Math.max(AnnotationTapPadding, (AnnotationTapTargetSize - (rect.bottom - rect.top)) / 2)
    return rect.left - horizontalExpansion <= clientX && clientX < rect.right + horizontalExpansion &&
        rect.top - verticalExpansion <= clientY && clientY < rect.bottom + verticalExpansion
}

function rangeHitDistance(range, clientX, clientY) {
    let nearest = Infinity
    for (const rect of Array.from(range?.getClientRects?.() ?? [])) {
        if (!rectContainsForgivingPoint(rect, clientX, clientY)) continue
        nearest = Math.min(nearest, rectDistanceSquared(rect, clientX, clientY))
    }
    return nearest
}

function rangeRectAtPoint(range, clientX, clientY) {
    const rects = Array.from(range?.getClientRects?.() ?? [])
    return rects.reduce((nearest, rect) => {
        const distance = rectDistanceSquared(rect, clientX, clientY)
        return !nearest || distance < nearest.distance ? { rect, distance } : nearest
    }, null)?.rect
        ?? range?.getBoundingClientRect?.()
        ?? null
}

function hideAnnotationCard() {
    if (publishedAnnotationId == null) return
    publishedAnnotationId = null
    post('annotationTapped', {})
}

function postAnnotationTap({ value, range, clientX, clientY } = {}) {
    const doc = range?.startContainer?.ownerDocument
    const selection = doc?.getSelection?.()
    // Releasing a drag over an existing mark must keep the text-selection card path in control.
    if (selection?.rangeCount && !selection.isCollapsed) return
    const annotation = annotationForRenderedValue(value)
    const rect = rangeRectAtPoint(range, clientX, clientY)
    if (!annotation?.id || !doc || !rect) {
        hideAnnotationCard()
        return
    }
    const frameTop = doc.defaultView?.frameElement?.getBoundingClientRect().top ?? 0
    const viewHeight = window.innerHeight
    const fractionOfView = y => viewHeight > 0 ? Math.max(0, Math.min(1, (frameTop + y) / viewHeight)) : null
    publishedAnnotationId = annotation.id
    post('annotationTapped', {
        annotationId: annotation.id,
        top: fractionOfView(rect.top),
        bottom: fractionOfView(rect.bottom),
    })
}

let activeAnnotationsList = []
let pendingAnnotations = undefined
let annotationApplyRunning = false
let annotationApplyGeneration = 0

/** Keep one annotation application in flight and retain only the newest waiting snapshot. */
function renderAnnotations(annotations) {
    if (!view) return
    if (fixedLayout) {
        activeAnnotationsList = Array.isArray(annotations) ? annotations.filter(Boolean) : []
        redrawFixedLayoutMarks()
        return
    }
    pendingAnnotations = Array.isArray(annotations) ? annotations.filter(Boolean) : []
    if (!annotationApplyRunning) void drainAnnotationUpdates()
}

async function drainAnnotationUpdates() {
    const generation = annotationApplyGeneration
    annotationApplyRunning = true
    try {
        while (pendingAnnotations !== undefined && generation === annotationApplyGeneration) {
            const next = pendingAnnotations
            pendingAnnotations = undefined
            try {
                await applyAnnotations(next, generation)
            } catch (error) {
                post('log', { step: 'renderAnnotations', message: String(error) })
            }
        }
    } finally {
        if (generation === annotationApplyGeneration) {
            annotationApplyRunning = false
            if (pendingAnnotations !== undefined) void drainAnnotationUpdates()
        }
    }
}

async function applyAnnotations(annotations, generation) {
    if (generation !== annotationApplyGeneration) return
    // Quote matches started by a document load also use view.addAnnotation. Finish those
    // writes before diffing a newer snapshot, so a late add cannot restore a deleted quote.
    if (pendingQuoteAdds.size) await Promise.allSettled(Array.from(pendingQuoteAdds))
    if (generation !== annotationApplyGeneration) return
    activeAnnotationsList = annotations
    annotationRevision++
    resetPopularBadgeResolutionRetries()
    popularBadgeIndexDirty = true
    scheduleBadgeLayout()
    // The repository is newest-first. Keep that stable order except when an editable highlight shares a range with a
    // non-editable note/underline: only one overlay can own a CFI, and the tappable one must win.
    const nextByValue = new Map()
    for (const annotation of activeAnnotationsList) {
        const current = nextByValue.get(annotation.value)
        if (!current || (!current.editable && annotation.editable)) nextByValue.set(annotation.value, annotation)
    }
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
            if (generation !== annotationApplyGeneration) return
            renderedAnnotations.delete(cfi)
        }
    }
    for (const [value, fingerprint] of standardAnnotationFingerprints) {
        const next = nextByValue.get(value)
        if (!next || annotationFingerprint(next) !== fingerprint) {
            await view.deleteAnnotation({ value })
            if (generation !== annotationApplyGeneration) return
            renderedAnnotations.delete(value)
            standardAnnotationFingerprints.delete(value)
            // A community quote may have been suppressed because it resolved to this exact personal-highlight CFI.
            // Make it match again now that the editable mark no longer owns the overlay key.
            for (const [sourceValue, cfi] of Array.from(resolvedTextAnnotations)) {
                if (cfi !== value) continue
                resolvedTextAnnotations.delete(sourceValue)
                resolvedTextFingerprints.delete(sourceValue)
            }
            authoritativeSourceForCfi.delete(value)
        }
    }
    for (const annotation of nextByValue.values()) {
        if (annotation.value &&
            !isTextAnnotationValue(annotation.value) &&
            !standardAnnotationFingerprints.has(annotation.value)) {
            // If an already-resolved community quote shares this exact CFI, the personal mark now owns taps and
            // appearance. Its resolved mapping remains available for the community-count badge.
            authoritativeSourceForCfi.delete(annotation.value)
            await view.addAnnotation(annotation)
            if (generation !== annotationApplyGeneration) return
            renderedAnnotations.add(annotation.value)
            standardAnnotationFingerprints.set(annotation.value, annotationFingerprint(annotation))
        }
    }
    // Also match any active documents in view
    for (const { doc, index } of view.renderer.getContents()) {
        if (doc) {
            matchTextAnnotationsForDoc(doc, index)
        }
    }
}

function annotationFingerprint(annotation) {
    return JSON.stringify([
        annotation?.type || '',
        annotation?.editable || false,
        annotation?.color || '',
        annotation?.popular || false,
        annotation?.note || '',
        annotation?.text || '',
    ])
}

function matchTextAnnotationsForDoc(doc, index) {
    if (!activeAnnotationsList || !activeAnnotationsList.length || !view) return
    if (completedMatchingRevision.get(doc) === annotationRevision) return
    let missing = unmatchedInDoc.get(doc)
    if (!missing) {
        // Remember the exact failed passage, not just its value. A later
        // standard-CFI highlight update can then reuse the negative result,
        // while editing an imported quote's text still retries it.
        missing = new Map()
        unmatchedInDoc.set(doc, missing)
    }
    const matches = []
    const generation = annotationApplyGeneration
    for (const ann of activeAnnotationsList) {
        // CFI-backed user highlights already render directly above. Running the
        // quote-text fallback for them scans the whole chapter and may draw a
        // second equivalent mark.
        if (!ann.value || !isTextAnnotationValue(ann.value) ||
            resolvedTextAnnotations.has(ann.value) || pendingTextAnnotations.has(ann.value)) continue
        const textToFind = ann.text
        if (!textToFind || textToFind.length < 5) continue
        const matchFingerprint = `${Boolean(ann.popular)}:${textToFind}`
        // A quote already proven absent from this document won't suddenly appear in it -
        // skip re-running the expensive alignment fallback for it on every page turn.
        if (missing.get(ann.value) === matchFingerprint) continue
        // Community marks need a unique passage in this edition; approximate matches
        // are useful for personal imports but can put public counts beside unrelated prose.
        const range = findTextRangeInDoc(doc, textToFind, { exactOnly: Boolean(ann.popular), unique: Boolean(ann.popular) })
        if (range) {
            missing.delete(ann.value)
            try {
                const cfi = view.getCFI(index, range)
                matches.push({ ann, range, cfi })
            } catch (_) {}
        } else {
            missing.set(ann.value, matchFingerprint)
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
            duplicate.aliases.push(match.ann)
            continue
        }
        match.aliases = []
        accepted.push(match)
    }
    const rememberResolution = match => {
        for (const annotation of [match.ann, ...match.aliases]) {
            resolvedTextAnnotations.set(annotation.value, match.cfi)
            resolvedTextFingerprints.set(annotation.value, annotationFingerprint(annotation))
        }
        popularBadgeIndexDirty = true
        scheduleBadgeLayout()
    }
    for (const match of accepted) {
        if (standardAnnotationFingerprints.has(match.cfi)) {
            // Overlayer keys are CFIs. Do not replace an editable personal highlight with a community underline when
            // both resolve to the identical range; retain the resolution so its community-count badge still renders.
            rememberResolution(match)
            continue
        }
        for (const annotation of [match.ann, ...match.aliases]) pendingTextAnnotations.add(annotation.value)
        const add = Promise.resolve(view.addAnnotation({
            value: match.cfi,
            type: match.ann.type || 'underline',
            color: match.ann.color || DefaultAnnotationColor,
            note: match.ann.note,
            popular: match.ann.popular,
        })).then(result => {
            if (generation !== annotationApplyGeneration) return
            if (result?.drawn === false) {
                // Foliate can resolve the CFI before its overlay is attached. Keep the quote
                // unresolved so create-overlay/relocate retries it instead of caching a no-op.
                completedMatchingRevision.delete(doc)
                return
            }
            renderedAnnotations.add(match.cfi)
            authoritativeSourceForCfi.set(match.cfi, match.ann.value)
            rememberResolution(match)
        }).catch(error => {
            if (generation !== annotationApplyGeneration) return
            // Leave it unresolved (not marked pending, not cached as a rendered cfi) so a
            // later render pass retries it instead of silently never showing this quote again.
            post('log', { step: 'addQuoteAnnotation', message: String(error) })
            completedMatchingRevision.delete(doc)
        }).finally(() => {
            if (generation === annotationApplyGeneration) {
                for (const annotation of [match.ann, ...match.aliases]) pendingTextAnnotations.delete(annotation.value)
            }
        })
        pendingQuoteAdds.add(add)
        void add.finally(() => pendingQuoteAdds.delete(add))
    }
    completedMatchingRevision.set(doc, annotationRevision)
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

function findTextRangeInDoc(doc, text, { exactOnly = false, unique = false } = {}) {
    if (!doc || !doc.body || !text) return null
    try {
        const cleanTarget = normalizeForMatching(text)
        if (cleanTarget.length < 5) return null

        const index = buildDocumentTextIndex(doc)
        const { cleanDoc, charMap } = index

        if (cleanDoc.length < 5) return null

        // Strategy 1: an exact normalized substring is both fastest and safest.
        const exactIndex = cleanDoc.indexOf(cleanTarget)
        if (unique && exactIndex >= 0 && cleanDoc.indexOf(cleanTarget, exactIndex + 1) >= 0) return null
        if (exactOnly && exactIndex < 0) return null
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

// ---- Fixed-layout (PDF) pages: zoom, page colours, marks and search -------------------------------------------------
// A PDF page is a canvas with pdf.js's invisible text layer on top. Selection, lookup and CFIs work on that text layer
// like on an EPUB document; marks are drawn in a layer of our own between the canvas and the text, because foliate's
// overlayer only exists for reflowable sections. pdf.js rebuilds the text layer on every render (zoom, colours) and
// fires 'vayana-page-rendered' afterwards, when the marks are drawn again.

const MinFixedZoom = 1
const MaxFixedZoom = 5
const DoubleTapFixedZoom = 2.5
const FixedMarkLayerClass = 'vayana-marks'
const SearchMarkColor = '#F6C453'
const MaxFixedSearchResults = 500
const FixedSearchExcerptChars = 40
const FixedSearchBatch = 8

// Zoom relative to the page (or its printed area, with margins cropped) fitting the screen.
let fixedZoom = 1
let fixedPinch = null
let fixedPageColors = null
let fixedSearchQuery = ''
let lastFixedLayoutIndex = null
// The reader's page settings for PDFs: crop the blank margins, fit the width instead of the whole page, darken print.
let pdfLayout = { cropMargins: false, fitWidth: false, darken: false, rotationDegrees: 0 }
// Page index -> its printed area as fractions of the page ({ x, y, w, h }), or null for a page with no margins to crop.
const pageContentBoxes = new Map()
// The union of the printed areas measured so far: one crop for the whole book, so the text keeps its size from page
// to page (a chapter's short last page would otherwise zoom in) and sits in the same place on screen.
let cropBox = null
// Set once the reader touches the page after a page turn; the view then stops re-aligning itself to late renders.
let fixedViewTouched = false
// The read-aloud mark on a PDF page: { doc, range }.
let fixedSpeechMark = null
const renderedPageDocs = new WeakSet()
const pageRenderWaiters = new WeakMap()
const FixedScaleTolerance = 0.03
const ContentBoxSampleWidth = 160
const ContentBoxThreshold = 40
const ContentBoxPadding = 0.015
const MaxChapterWordPages = 100
const ChapterWordPagesWithoutContents = 10

function resetFixedLayoutState() {
    fixedZoom = 1
    fixedPinch = null
    fixedSearchQuery = ''
    lastFixedLayoutIndex = null
    fixedViewTouched = false
    fixedSpeechMark = null
    pageContentBoxes.clear()
    cropBox = null
    clearTimeout(fixedRefitTimer)
    fixedScrollableReported = null
}

function fixedLayoutContents() {
    return (view?.renderer?.getContents?.() ?? []).filter(content => content.doc && Number.isInteger(content.index))
}

function wireFixedLayoutPage(doc, index) {
    wireSelection(doc, index)
    wireDoubleTapLookup(doc)
    wirePinchZoom(doc)
    doc.addEventListener('click', event => {
        const selection = doc.getSelection?.()
        if (selection?.rangeCount && !selection.isCollapsed) return
        let nearest = null
        for (let i = activeAnnotationsList.length - 1; i >= 0; i--) {
            const annotation = activeAnnotationsList[i]
            const range = fixedLayoutRangeOf(annotation.value, doc, index)
            if (!range) continue
            const distance = rangeHitDistance(range, event.clientX, event.clientY)
            const priority = annotation.editable ? 2 : annotation.popular ? 0 : 1
            if (distance < (nearest?.distance ?? Infinity) ||
                (distance === nearest?.distance && priority > nearest.priority)) {
                nearest = { annotation, range, distance, priority }
            }
        }
        if (nearest) {
            postAnnotationTap({
                value: nearest.annotation.value,
                range: nearest.range,
                clientX: event.clientX,
                clientY: event.clientY,
            })
        }
    })
    doc.addEventListener('touchstart', () => { fixedViewTouched = true }, { passive: true })
    doc.addEventListener('vayana-page-rendered', () => onFixedPageRendered(doc, index))
}

function onFixedPageRendered(doc, index) {
    renderedPageDocs.add(doc)
    for (const resolve of pageRenderWaiters.get(doc) ?? []) resolve()
    pageRenderWaiters.delete(doc)
    applyPdfDarken(doc)
    let boxChanged = false
    if (pdfLayout.cropMargins && !pageContentBoxes.has(index)) {
        const before = cropBox
        measureContentBox(doc, index)
        boxChanged = cropBox !== before
    }
    drawFixedLayoutMarks(doc, index)
    // A page's printed area is only known once it has rendered; line the view up with it unless the reader has
    // already moved the page themselves.
    if (boxChanged && index === currentFixedIndex() && !fixedViewTouched) applyFixedView()
}

// Resolves once the page's text layer exists (or after a few seconds, so a failed render never hangs read aloud).
function whenFixedPageRendered(doc) {
    if (!doc || renderedPageDocs.has(doc)) return Promise.resolve()
    return new Promise(resolve => {
        const waiters = pageRenderWaiters.get(doc) ?? []
        waiters.push(resolve)
        pageRenderWaiters.set(doc, waiters)
        setTimeout(resolve, 4000)
    })
}

function currentFixedIndex() {
    const current = view?.lastLocation?.section?.current
    return Number.isInteger(current) ? current : lastFixedLayoutIndex
}

// A new page opens at its top, or at its bottom when paging backwards, at the same zoom.
function onFixedLayoutRelocate(index) {
    if (index === lastFixedLayoutIndex) return
    const backwards = Number.isInteger(lastFixedLayoutIndex) && index < lastFixedLayoutIndex
    lastFixedLayoutIndex = index
    fixedViewTouched = false
    applyFixedView(backwards ? 'bottom' : 'top')
}

function fixedPageSize() {
    const doc = fixedLayoutContents()[0]?.doc
    const content = doc?.querySelector('meta[name="viewport"]')?.getAttribute('content') ?? ''
    const size = Object.fromEntries(content.split(',').map(part => part.split('=').map(value => value.trim())))
    const width = parseFloat(size.width)
    const height = parseFloat(size.height)
    return width > 0 && height > 0 ? { width, height } : null
}

/**
 * Where the renderer must scroll so the page point under [focus] (renderer coordinates) stays under it when the page
 * scale changes from [oldScale] to [newScale]. A page smaller than the view is centred, which shifts it by an offset.
 */
function zoomScrollFor({ scroll, focus, viewSize, pageSize, oldScale, newScale }) {
    const oldOffset = Math.max(0, (viewSize - pageSize * oldScale) / 2)
    const newOffset = Math.max(0, (viewSize - pageSize * newScale) / 2)
    const pagePoint = (scroll + focus - oldOffset) / oldScale
    return Math.max(0, pagePoint * newScale + newOffset - focus)
}

function clampFixedZoom(zoom) {
    return Math.max(MinFixedZoom, Math.min(MaxFixedZoom, Number.isFinite(zoom) ? zoom : 1))
}

const FullPageBox = Object.freeze({ x: 0, y: 0, w: 1, h: 1 })
let fixedRefitTimer = null
let fixedViewSize = null

function scheduleFixedRefit() {
    if (!fixedLayout) return
    clearTimeout(fixedRefitTimer)
    fixedRefitTimer = setTimeout(() => {
        const renderer = view?.renderer
        if (!renderer) return
        const size = `${renderer.clientWidth}x${renderer.clientHeight}`
        if (size === fixedViewSize) return
        fixedViewSize = size
        applyFixedView()
    }, 150)
}

function fixedFitBox() {
    return (pdfLayout.cropMargins && cropBox) || FullPageBox
}

// Pages with nothing to crop (a full-bleed cover or photo) leave the book's crop as it is.
function measureContentBox(doc, index) {
    const box = contentBoxOfPage(doc)
    pageContentBoxes.set(index, box)
    const union = unionBox(cropBox, box)
    if (JSON.stringify(union) !== JSON.stringify(cropBox)) cropBox = union
    return box
}

function unionBox(a, b) {
    if (!a || !b) return a ?? b ?? null
    const x = Math.min(a.x, b.x)
    const y = Math.min(a.y, b.y)
    return { x, y, w: Math.max(a.x + a.w, b.x + b.w) - x, h: Math.max(a.y + a.h, b.y + b.h) - y }
}

/** The page scale at which [box] (fractions of the page) fills the view: its width, or all of it. */
function fitScaleFor(box, page, viewWidth, viewHeight, fitWidth) {
    const width = viewWidth / (box.w * page.width)
    return fitWidth ? width : Math.min(width, viewHeight / (box.h * page.height))
}

/**
 * Scroll offsets that show [box] at [scale]: centred when it is smaller than the view, else from its start, or from
 * its end with [align] 'bottom' (paging backwards lands at the foot of the previous page).
 */
function boxScrollFor({ box, page, scale, viewWidth, viewHeight, align }) {
    const x = box.x * page.width * scale
    const w = box.w * page.width * scale
    const y = box.y * page.height * scale
    const h = box.h * page.height * scale
    return {
        left: Math.max(0, x - Math.max(0, (viewWidth - w) / 2)),
        top: align === 'bottom' ? Math.max(0, y + h - viewHeight) : Math.max(0, y - Math.max(0, (viewHeight - h) / 2)),
    }
}

function rendererScale() {
    const value = parseFloat(view?.renderer?.getAttribute('zoom'))
    return Number.isFinite(value) && value > 0 ? value : null
}

// Setting the scale re-renders the page; a change too small to see keeps the current render.
function setRendererScale(scale) {
    const current = rendererScale()
    if (current && Math.abs(scale - current) / current < FixedScaleTolerance) return
    view.renderer.setAttribute('zoom', String(scale))
}

function fixedViewGeometry() {
    const renderer = view?.renderer
    const page = fixedPageSize()
    if (!renderer || !page) return null
    const viewWidth = renderer.clientWidth
    const viewHeight = renderer.clientHeight
    const box = fixedFitBox()
    const fit = fitScaleFor(box, page, viewWidth, viewHeight, pdfLayout.fitWidth)
    return fit > 0 ? { renderer, page, viewWidth, viewHeight, box, fit } : null
}

// Scales the page for the current zoom and page settings and scrolls to its printed area.
function applyFixedView(align = 'top') {
    const geometry = fixedViewGeometry()
    if (!geometry) return
    const { renderer, page, viewWidth, viewHeight, box, fit } = geometry
    fixedViewSize = `${viewWidth}x${viewHeight}`
    setRendererScale(fit * fixedZoom)
    const scale = rendererScale() ?? fit * fixedZoom
    const { left, top } = boxScrollFor({ box, page, scale, viewWidth, viewHeight, align })
    renderer.scrollLeft = left
    renderer.scrollTop = top
    reportFixedScrollable()
}

// Tells the app whether a drag on the page pans it, so the reader's own edge swipes keep out of the way.
let fixedScrollableReported = null
function reportFixedScrollable() {
    const renderer = view?.renderer
    if (!renderer) return
    const scrollable = renderer.scrollHeight > renderer.clientHeight + 2 || renderer.scrollWidth > renderer.clientWidth + 2
    if (scrollable === fixedScrollableReported) return
    fixedScrollableReported = scrollable
    post('pageScrollable', { scrollable })
}

function setFixedZoom(zoom, focusX, focusY) {
    const geometry = fixedViewGeometry()
    if (!geometry) return
    const { renderer, page, viewWidth, viewHeight, fit } = geometry
    const next = clampFixedZoom(zoom)
    if (next <= 1.001) {
        fixedZoom = 1
        applyFixedView()
        return
    }
    const oldScale = rendererScale() ?? fit * fixedZoom
    const newScale = fit * next
    const fx = Number.isFinite(focusX) ? focusX : viewWidth / 2
    const fy = Number.isFinite(focusY) ? focusY : viewHeight / 2
    const left = zoomScrollFor({ scroll: renderer.scrollLeft, focus: fx, viewSize: viewWidth, pageSize: page.width, oldScale, newScale })
    const top = zoomScrollFor({ scroll: renderer.scrollTop, focus: fy, viewSize: viewHeight, pageSize: page.height, oldScale, newScale })
    fixedZoom = next
    setRendererScale(newScale)
    renderer.scrollLeft = left
    renderer.scrollTop = top
    reportFixedScrollable()
}

/**
 * Next/previous on a page taller than the screen (fit width, zoomed in) first moves down/up through it, keeping a
 * little of the old view for context; only at the end of the page does it turn. True when it scrolled.
 */
function scrollFixedPage(direction) {
    const geometry = fixedViewGeometry()
    if (!geometry) return false
    const { renderer, page, viewHeight, box } = geometry
    const scale = rendererScale() ?? geometry.fit * fixedZoom
    const top = box.y * page.height * scale
    const bottom = (box.y + box.h) * page.height * scale
    const step = viewHeight * 0.9
    if (direction > 0 && renderer.scrollTop + viewHeight < bottom - 4) {
        renderer.scrollTop = Math.min(bottom - viewHeight, renderer.scrollTop + step)
        return true
    }
    if (direction < 0 && renderer.scrollTop > top + 4) {
        renderer.scrollTop = Math.max(top, renderer.scrollTop - step)
        return true
    }
    return false
}

function setPdfLayout(options) {
    const rotationDegrees = [0, 90, 180, 270].includes(Number(options?.rotationDegrees))
        ? Number(options.rotationDegrees) : 0
    const next = {
        cropMargins: Boolean(options?.cropMargins),
        fitWidth: Boolean(options?.fitWidth),
        darken: Boolean(options?.darken),
        rotationDegrees,
    }
    if (JSON.stringify(next) === JSON.stringify(pdfLayout)) return
    const cropTurnedOn = next.cropMargins && !pdfLayout.cropMargins
    const rotationChanged = next.rotationDegrees !== pdfLayout.rotationDegrees
    pdfLayout = next
    if (!fixedLayout) return
    view?.book?.setPageRotation?.(next.rotationDegrees)
    if (rotationChanged) {
        pageContentBoxes.clear()
        cropBox = null
        const renderer = view?.renderer
        const scale = rendererScale() ?? 1
        renderer?.setAttribute('zoom', String(scale * 1.0001))
        requestAnimationFrame(() => renderer?.setAttribute('zoom', String(scale)))
    }
    for (const { doc, index } of fixedLayoutContents()) {
        applyPdfDarken(doc)
        if (cropTurnedOn && renderedPageDocs.has(doc) && !pageContentBoxes.has(index)) measureContentBox(doc, index)
    }
    applyFixedView()
}

// A gamma curve on the page image: faint print gets darker on a light page (lighter on a dark one) while the paper
// stays as it is. A plain contrast filter pivots on mid-grey and leaves grey text grey.
function applyPdfDarken(doc) {
    const holder = doc?.querySelector('#canvas')
    if (!holder) return
    if (!pdfLayout.darken) {
        holder.style.filter = ''
        return
    }
    const dark = fixedPageColors ? (relativeLuminance(fixedPageColors.background) ?? 1) < 0.2 : false
    const id = dark ? 'vayana-lighten-print' : 'vayana-darken-print'
    if (!doc.getElementById(id)) {
        const exponent = dark ? 0.55 : 2.2
        const svg = doc.createElementNS('http://www.w3.org/2000/svg', 'svg')
        svg.setAttribute('width', '0')
        svg.setAttribute('height', '0')
        svg.style.position = 'absolute'
        svg.innerHTML = `<filter id="${id}" color-interpolation-filters="sRGB"><feComponentTransfer>` +
            ['R', 'G', 'B'].map(c => `<feFunc${c} type="gamma" amplitude="1" exponent="${exponent}" offset="0"/>`).join('') +
            '</feComponentTransfer></filter>'
        doc.body.append(svg)
    }
    holder.style.filter = `url(#${id})`
}

/**
 * The printed area of an RGBA image of [width] x [height] as fractions of it, padded a little, or null when the print
 * already reaches (nearly) every edge. Background is the top-left pixel's colour; a pixel counts as print when any
 * channel differs from it by more than [threshold].
 */
function contentBoxFromPixels(data, width, height, threshold = ContentBoxThreshold, padding = ContentBoxPadding) {
    if (!(width > 0 && height > 0) || data.length < width * height * 4) return null
    const [r0, g0, b0] = [data[0], data[1], data[2]]
    let minX = width
    let minY = height
    let maxX = -1
    let maxY = -1
    for (let y = 0; y < height; y++) {
        for (let x = 0; x < width; x++) {
            const i = (y * width + x) * 4
            if (Math.abs(data[i] - r0) > threshold || Math.abs(data[i + 1] - g0) > threshold ||
                Math.abs(data[i + 2] - b0) > threshold) {
                if (x < minX) minX = x
                if (x > maxX) maxX = x
                if (y < minY) minY = y
                if (y > maxY) maxY = y
            }
        }
    }
    if (maxX < 0) return null
    const x = Math.max(0, minX / width - padding)
    const y = Math.max(0, minY / height - padding)
    const w = Math.min(1, (maxX + 1) / width + padding) - x
    const h = Math.min(1, (maxY + 1) / height + padding) - y
    // Nothing worth cropping: the view would barely change and just re-render.
    if (w * h > 0.92) return null
    return { x, y, w, h }
}

function contentBoxOfPage(doc) {
    const canvas = doc?.querySelector('#canvas canvas')
    if (!canvas?.width || !canvas.height) return null
    try {
        const width = ContentBoxSampleWidth
        const height = Math.max(1, Math.round(canvas.height / canvas.width * width))
        const sample = document.createElement('canvas')
        sample.width = width
        sample.height = height
        const context = sample.getContext('2d', { willReadFrequently: true })
        context.drawImage(canvas, 0, 0, width, height)
        return contentBoxFromPixels(context.getImageData(0, 0, width, height).data, width, height)
    } catch (error) {
        post('log', { step: 'contentBox', message: String(error) })
        return null
    }
}

/**
 * The pages whose words make up the "chapter" of PDF page [current] for the word list: from the contents entry it
 * falls under to the page before the next entry, capped at [maxPages]. Without contents, this page and the
 * [pagesWithoutContents] - 1 after it.
 * [tocStartPages] are 1-based contents page numbers.
 */
function chapterPageRange(current, total, tocStartPages, maxPages = MaxChapterWordPages, pagesWithoutContents = ChapterWordPagesWithoutContents) {
    const starts = [...new Set(tocStartPages)].map(page => page - 1).filter(i => i >= 0 && i < total).sort((a, b) => a - b)
    if (!starts.length) return { start: current, end: Math.max(current, Math.min(total - 1, current + pagesWithoutContents - 1)) }
    const start = starts.filter(i => i <= current).at(-1) ?? 0
    const next = starts.find(i => i > current)
    const end = Math.min(next == null ? total - 1 : next - 1, start + maxPages - 1)
    return { start, end: Math.max(start, end) }
}

// A point in a page document, in the renderer's own coordinates.
function rendererPointOf(doc, clientX, clientY) {
    const frame = doc.defaultView?.frameElement?.getBoundingClientRect()
    const renderer = view?.renderer?.getBoundingClientRect()
    if (!frame || !renderer) return null
    return { x: frame.left + clientX - renderer.left, y: frame.top + clientY - renderer.top }
}

function toggleFixedZoom(doc, clientX, clientY) {
    const point = rendererPointOf(doc, clientX, clientY)
    setFixedZoom(fixedZoom > 1.05 ? 1 : DoubleTapFixedZoom, point?.x, point?.y)
}

function touchDistance(a, b) {
    // Screen coordinates: the page frame is scaled while pinching, which would skew its own client coordinates.
    return Math.hypot(a.screenX - b.screenX, a.screenY - b.screenY)
}

function wirePinchZoom(doc) {
    // Two fingers are ours; one finger still pans the zoomed page.
    doc.documentElement.style.touchAction = 'pan-x pan-y'
    doc.addEventListener('touchstart', e => {
        if (e.touches.length !== 2) return
        const [a, b] = e.touches
        const focus = rendererPointOf(doc, (a.clientX + b.clientX) / 2, (a.clientY + b.clientY) / 2)
        const distance = touchDistance(a, b)
        if (!focus || !(distance > 0)) return
        // A second finger ends any selection the first one started.
        doc.getSelection()?.removeAllRanges()
        fixedPinch = { distance, focus, ratio: 1 }
    }, { passive: true })
    doc.addEventListener('touchmove', e => {
        if (!fixedPinch || e.touches.length !== 2) return
        e.preventDefault()
        const [a, b] = e.touches
        const ratio = clampFixedZoom(fixedZoom * touchDistance(a, b) / fixedPinch.distance) / fixedZoom
        fixedPinch.ratio = ratio
        // Cheap while the fingers move; the page renders sharp at the new scale when they lift.
        const renderer = view.renderer
        renderer.style.transformOrigin = `${fixedPinch.focus.x}px ${fixedPinch.focus.y}px`
        renderer.style.transform = `scale(${ratio})`
    }, { passive: false })
    const end = e => {
        if (!fixedPinch || e.touches.length >= 2) return
        const { focus, ratio } = fixedPinch
        fixedPinch = null
        view.renderer.style.transform = ''
        view.renderer.style.transformOrigin = ''
        if (Math.abs(ratio - 1) > 0.02) setFixedZoom(fixedZoom * ratio, focus.x, focus.y)
    }
    doc.addEventListener('touchend', end, { passive: true })
    doc.addEventListener('touchcancel', end, { passive: true })
}

function relativeLuminance(hex) {
    const match = /^#?([0-9a-f]{6})$/i.exec(String(hex ?? '').trim())
    if (!match) return null
    const value = parseInt(match[1], 16)
    const channel = shift => {
        const c = ((value >> shift) & 0xff) / 255
        return c <= 0.03928 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

/**
 * The colours pdf.js should draw pages in for a reader theme, or null for the page's own colours. White paper with
 * dark text (light and E-Ink themes) keeps the original colours, including coloured text and links; any other theme
 * recolours text and drawings into it, leaving images as they are.
 */
function pageColorsForTheme(background, foreground) {
    const bg = relativeLuminance(background)
    const fg = relativeLuminance(foreground)
    if (bg == null || fg == null) return null
    if (bg > 0.9 && fg < 0.2) return null
    return { background, foreground }
}

function setPageColors(background, foreground) {
    const next = pageColorsForTheme(background, foreground)
    if (JSON.stringify(next) === JSON.stringify(fixedPageColors)) return
    fixedPageColors = next
    if (!fixedLayout) return
    view?.book?.setPageColors?.(next)
    // Setting the zoom again re-renders the pages on screen in the new colours.
    const renderer = view?.renderer
    if (renderer) renderer.setAttribute('zoom', renderer.getAttribute('zoom') ?? 'fit-page')
    // The darken curve runs the other way on a dark page.
    for (const { doc } of fixedLayoutContents()) applyPdfDarken(doc)
}

function fixedMarkLayer(doc) {
    const textLayer = doc.querySelector('.textLayer')
    if (!textLayer) return null
    let layer = doc.querySelector(`.${FixedMarkLayerClass}`)
    if (!layer) {
        layer = doc.createElement('div')
        layer.className = FixedMarkLayerClass
        textLayer.before(layer)
    }
    const dark = fixedPageColors ? (relativeLuminance(fixedPageColors.background) ?? 1) < 0.2 : false
    Object.assign(layer.style, {
        position: 'absolute',
        left: `${textLayer.offsetLeft}px`,
        top: `${textLayer.offsetTop}px`,
        width: `${textLayer.offsetWidth}px`,
        height: `${textLayer.offsetHeight}px`,
        pointerEvents: 'none',
        // Multiply keeps print legible through a light mark; on a dark page it would vanish, so lighten there instead.
        mixBlendMode: dark ? 'screen' : 'multiply',
        opacity: dark ? '0.55' : '0.45',
    })
    return layer
}

function drawFixedLayoutMarks(doc, index) {
    const layer = fixedMarkLayer(doc)
    if (!layer) return
    layer.replaceChildren()
    const box = layer.getBoundingClientRect()
    if (!box.width || !layer.offsetWidth) return
    // Client rects are measured after the page document's own scaling; the layer is laid out before it.
    const scale = layer.offsetWidth / box.width
    const drawRange = (range, style) => {
        for (const rect of range.getClientRects()) {
            if (!rect.width || !rect.height) continue
            const mark = doc.createElement('div')
            Object.assign(mark.style, {
                position: 'absolute',
                left: `${(rect.left - box.left) * scale}px`,
                top: `${(rect.top - box.top) * scale}px`,
                width: `${rect.width * scale}px`,
                height: `${rect.height * scale}px`,
            }, style)
            layer.append(mark)
        }
    }
    // Paint editable highlights last when several marks share pixels, matching their tap precedence.
    const annotationsByTapPriority = [...activeAnnotationsList].sort((a, b) =>
        Number(Boolean(a.editable)) - Number(Boolean(b.editable)))
    for (const annotation of annotationsByTapPriority) {
        const range = fixedLayoutRangeOf(annotation.value, doc, index)
        if (!range) continue
        const color = markColor(annotation.color ?? DefaultAnnotationColor)
        drawRange(range, annotation.type === 'underline'
            ? { borderBottom: `2px dashed ${color}` }
            : { background: color })
    }
    if (fixedSearchQuery) {
        for (const range of findTextRanges(doc, fixedSearchQuery)) drawRange(range, { background: markColor(SearchMarkColor) })
    }
    if (fixedSpeechMark?.doc === doc) drawRange(fixedSpeechMark.range, { background: markColor(SpeechHighlightColor) })
}

function markFixedLayoutSpeech(range) {
    const doc = range.startContainer.ownerDocument
    const previous = fixedSpeechMark?.doc
    fixedSpeechMark = { doc, range }
    if (previous && previous !== doc) drawFixedLayoutMarks(previous, fixedLayoutContents().find(c => c.doc === previous)?.index)
    drawFixedLayoutMarks(doc, speech.index)
    scrollFixedRangeIntoView(doc, range)
}

function clearFixedLayoutSpeech() {
    const doc = fixedSpeechMark?.doc
    fixedSpeechMark = null
    if (doc) drawFixedLayoutMarks(doc, fixedLayoutContents().find(c => c.doc === doc)?.index)
}

// Keeps the word being read on screen when the page is larger than the view.
function scrollFixedRangeIntoView(doc, range) {
    const renderer = view?.renderer
    const rect = range.getBoundingClientRect()
    const top = rendererPointOf(doc, rect.left, rect.top)
    const bottom = rendererPointOf(doc, rect.right, rect.bottom)
    if (!renderer || !top || !bottom) return
    const height = renderer.clientHeight
    const width = renderer.clientWidth
    if (top.y < 0 || bottom.y > height) renderer.scrollTop += top.y - height / 3
    if (top.x < 0 || bottom.x > width) renderer.scrollLeft += top.x - width / 8
}

// A range on this page, only for CFIs pointing into a page's text: a bare page CFI is a bookmark.
function fixedLayoutRangeOf(cfi, doc, index) {
    if (typeof cfi !== 'string' || !cfi.startsWith('epubcfi(') || !cfi.includes('!')) return null
    try {
        const resolved = view.resolveCFI(cfi)
        if (resolved?.index !== index) return null
        const range = resolved.anchor(doc)
        return range?.collapsed === false ? range : null
    } catch (_) {
        return null
    }
}

function redrawFixedLayoutMarks() {
    for (const { doc, index } of fixedLayoutContents()) drawFixedLayoutMarks(doc, index)
}

// Lower-cased text with each whitespace run as one space, and for each character the offset it came from.
function normalizeWithMap(text) {
    let normalized = ''
    const map = []
    let lastSpace = false
    for (let i = 0; i < text.length; i++) {
        const isSpace = /\s/.test(text[i])
        if (isSpace && lastSpace) continue
        normalized += isSpace ? ' ' : text[i].toLowerCase()
        map.push(i)
        lastSpace = isSpace
    }
    return { normalized, map }
}

function normalizeSearchText(text) {
    return normalizeWithMap(String(text ?? '').trim()).normalized
}

// Offsets in [text] where [query] matches, case-insensitively and ignoring runs of whitespace: [start, end) pairs.
function findTextMatches(text, query, limit) {
    const needle = normalizeSearchText(query)
    if (!needle) return []
    const { normalized, map } = normalizeWithMap(text)
    const matches = []
    for (let at = normalized.indexOf(needle); at >= 0 && matches.length < limit; at = normalized.indexOf(needle, at + needle.length)) {
        matches.push({ start: map[at], end: map[at + needle.length - 1] + 1 })
    }
    return matches
}

// Ranges of [query] in the page's text layer, across span boundaries.
function findTextRanges(doc, query) {
    const root = doc.querySelector('.textLayer')
    if (!root) return []
    const nodes = []
    let text = ''
    const walker = doc.createTreeWalker(root, NodeFilter.SHOW_TEXT)
    let node
    while ((node = walker.nextNode())) {
        nodes.push({ node, start: text.length })
        text += node.data
    }
    if (!nodes.length) return []
    const pointAt = offset => {
        let i = nodes.length - 1
        while (i > 0 && nodes[i].start > offset) i--
        return { node: nodes[i].node, offset: Math.min(nodes[i].node.data.length, offset - nodes[i].start) }
    }
    return findTextMatches(text, query, 200).map(({ start, end }) => {
        const range = doc.createRange()
        const from = pointAt(start)
        const to = pointAt(end - 1)
        range.setStart(from.node, from.offset)
        range.setEnd(to.node, to.offset + 1)
        return range
    })
}

// The match with up to [FixedSearchExcerptChars] of context either side, trimmed back to whole words.
function searchExcerpt(text, start, end, context = FixedSearchExcerptChars) {
    let from = Math.max(0, start - context)
    let to = Math.min(text.length, end + context)
    if (from > 0) {
        const space = text.slice(from, start).search(/\s/)
        if (space >= 0) from += space + 1
    }
    if (to < text.length) {
        const tail = text.slice(end, to)
        const space = tail.search(/\s\S*$/)
        if (space >= 0) to = end + space
    }
    return `${from > 0 ? '… ' : ''}${text.slice(from, to).replace(/\s+/g, ' ').trim()}${to < text.length ? ' …' : ''}`
}

// Every match in the book with a little context either side, pointing at its page.
async function searchFixedLayout(query, token) {
    fixedSearchQuery = query
    redrawFixedLayoutMarks()
    const results = []
    const sections = view.book.sections ?? []
    const pageText = index => view.book.getPageText(index).catch(error => {
        post('log', { step: 'searchPage', index, message: String(error) })
        return ''
    })
    // Pages are read a few at a time: each is a round trip to pdf.js's worker, and waiting on them one by one is
    // most of a whole-book search.
    let batch = []
    for (let index = 0; index < sections.length && results.length < MaxFixedSearchResults; index++) {
        if (index % FixedSearchBatch === 0) {
            batch = Array.from({ length: Math.min(FixedSearchBatch, sections.length - index) }, (_, i) => pageText(index + i))
        }
        const text = await batch[index % FixedSearchBatch]
        if (token !== searchToken) return
        for (const { start, end } of findTextMatches(text, query, MaxFixedSearchResults - results.length)) {
            results.push({
                cfi: view.getCFI(index),
                excerpt: searchExcerpt(text, start, end),
                tocLabel: `Page ${index + 1}`,
            })
        }
    }
    if (token === searchToken) post('searchResults', { query, results })
}

window.VayanaReader = { open, setPageColors, setPdfLayout, next, prev, goLeft, goRight, goToFraction, preparePageJump, cancelPageJump, goToPage, goToHref, providePdfPassword, pageThumbnail, setReaderControlsGesture, applyStyle, setBionicReading, setPageTurnAnimation, setInkMarks, renderAnnotations, clearSelection, search, clearSearch, startSpeech, nextSpeechChunk, markSpeech, stopSpeech, chapterWordCounts, mergeRanges }
addEventListener('resize', () => {
    applyReaderMargin(readerSideMarginPercent)
    scheduleFixedRefit()
})
post('ready', {})
