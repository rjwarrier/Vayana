import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const pdf = await readFile(new URL('../src/main/assets/foliate/pdf.js', import.meta.url), 'utf8')
const pdfPage = await readFile(new URL('../src/main/assets/pdf-page.html', import.meta.url), 'utf8')
const slice = (from, to) => {
    const start = bridge.indexOf(from)
    const end = bridge.indexOf(to, start)
    assert.ok(start > 0 && end > start, `${from} not found in bridge.js`)
    return bridge.slice(start, end)
}

const context = vm.createContext({ Math, Number, String, JSON, Object, parseInt, parseFloat })
vm.runInContext([
    slice('function zoomScrollFor', 'function clampFixedZoom'),
    slice('function relativeLuminance', 'function setPageColors'),
    slice('function normalizeWithMap', '// Ranges of [query]'),
].join('\n'), context)
const { zoomScrollFor, pageColorsForTheme, findTextMatches } = context

test('zooming keeps the page point under the fingers in place', () => {
    // A 600px-wide page fitted into a 400px view (scale 2/3), zoomed to twice that around x = 100.
    const oldScale = 400 / 600
    const newScale = oldScale * 2
    const scroll = zoomScrollFor({ scroll: 0, focus: 100, viewSize: 400, pageSize: 600, oldScale, newScale })
    const pagePointBefore = (0 + 100) / oldScale
    const pagePointAfter = (scroll + 100) / newScale
    assert.ok(Math.abs(pagePointBefore - pagePointAfter) < 1e-9)
})

test('zooming accounts for a page centred in a wider view', () => {
    // A 300px page centred in a 400px view sits 50px in; the point under x = 250 is page x 200.
    const scroll = zoomScrollFor({ scroll: 0, focus: 250, viewSize: 400, pageSize: 300, oldScale: 1, newScale: 2 })
    assert.equal((scroll + 250) / 2, 200)
})

test('zooming back out never scrolls before the page start', () => {
    // Page point 150 under x = 300; at the smaller scale keeping it there would need a negative scroll.
    assert.equal(zoomScrollFor({ scroll: 0, focus: 300, viewSize: 400, pageSize: 600, oldScale: 2, newScale: 1 }), 0)
})

test('white-paper themes keep the PDF\'s own colours; others recolour pages', () => {
    assert.equal(pageColorsForTheme('#FFFFFF', '#111111'), null)
    assert.equal(pageColorsForTheme('#FFFFFF', '#000000'), null)
    assert.deepEqual({ ...pageColorsForTheme('#121212', '#E0E0E0') }, { background: '#121212', foreground: '#E0E0E0' })
    assert.deepEqual({ ...pageColorsForTheme('#F4ECD8', '#5B4636') }, { background: '#F4ECD8', foreground: '#5B4636' })
    assert.equal(pageColorsForTheme('not a colour', '#000000'), null)
})

test('search matches ignore case and whitespace runs and map back to the original text', () => {
    const text = 'The Lazy\n  dog and the lazy dog.'
    const matches = findTextMatches(text, 'LAZY dog', 10)
    assert.equal(matches.length, 2)
    assert.equal(text.slice(matches[0].start, matches[0].end), 'Lazy\n  dog')
    assert.equal(text.slice(matches[1].start, matches[1].end), 'lazy dog')
})

test('search respects the result limit and ignores a blank query', () => {
    assert.equal(findTextMatches('a a a a', 'a', 2).length, 2)
    assert.equal(findTextMatches('text', '   ', 10).length, 0)
})

const layout = vm.createContext({ Math, Number, Set, Object, ContentBoxThreshold: 40, ContentBoxPadding: 0.015, MaxChapterWordPages: 100, ChapterWordPagesWithoutContents: 10 })
vm.runInContext([
    slice('const FullPageBox', 'function rendererScale'),
    slice('function contentBoxFromPixels', 'function contentBoxOfPage'),
    slice('function chapterPageRange', '// A point in a page document'),
].join('\n'), layout)

// A white 100 x 200 image with dark print from x 20..79, y 40..159.
function page(print = { x0: 20, x1: 79, y0: 40, y1: 159 }, width = 100, height = 200) {
    const data = new Uint8ClampedArray(width * height * 4).fill(255)
    for (let y = print.y0; y <= print.y1; y++) {
        for (let x = print.x0; x <= print.x1; x++) data.set([20, 20, 20, 255], (y * width + x) * 4)
    }
    return { data, width, height }
}

test('the printed area is found and padded a little', () => {
    const { data, width, height } = page()
    const box = layout.contentBoxFromPixels(data, width, height)
    assert.ok(Math.abs(box.x - (0.2 - 0.015)) < 1e-9)
    assert.ok(Math.abs(box.y - (0.2 - 0.015)) < 1e-9)
    assert.ok(Math.abs(box.w - (0.6 + 0.03)) < 1e-9)
    assert.ok(Math.abs(box.h - (0.6 + 0.03)) < 1e-9)
})

test('blank pages and pages printed to the edges are left uncropped', () => {
    const blank = page({ x0: 1, x1: 0, y0: 1, y1: 0 })
    assert.equal(layout.contentBoxFromPixels(blank.data, blank.width, blank.height), null)
    const full = page({ x0: 1, x1: 99, y0: 1, y1: 199 })
    assert.equal(layout.contentBoxFromPixels(full.data, full.width, full.height), null)
})

test('a dark page background counts as paper, not print', () => {
    const { data, width, height } = page()
    for (let i = 0; i < data.length; i += 4) if (data[i] === 255) data.set([18, 18, 18], i)
    for (let i = 0; i < data.length; i += 4) if (data[i] === 20) data.set([230, 230, 230], i)
    assert.ok(layout.contentBoxFromPixels(data, width, height))
})

test('fit width fills the view across; fit page fits both ways', () => {
    const pageSize = { width: 600, height: 800 }
    const box = { x: 0.1, y: 0.1, w: 0.8, h: 0.8 }
    assert.equal(layout.fitScaleFor(box, pageSize, 480, 700, true), 1)
    assert.equal(layout.fitScaleFor(vm.runInContext("FullPageBox", layout), pageSize, 480, 700, false), Math.min(480 / 600, 700 / 800))
})

test('the view opens on the printed area: its top going forwards, its foot going back', () => {
    const args = { box: { x: 0.1, y: 0.1, w: 0.8, h: 0.8 }, page: { width: 600, height: 800 }, scale: 1, viewWidth: 480, viewHeight: 400 }
    assert.deepEqual({ ...layout.boxScrollFor({ ...args, align: 'top' }) }, { left: 60, top: 80 })
    assert.deepEqual({ ...layout.boxScrollFor({ ...args, align: 'bottom' }) }, { left: 60, top: 80 + 640 - 400 })
})

test('the word list covers the contents entry a PDF page falls under', () => {
    // Entries start on pages 1, 5 and 12 of 20.
    assert.deepEqual({ ...layout.chapterPageRange(6, 20, [1, 5, 12]) }, { start: 4, end: 10 })
    assert.deepEqual({ ...layout.chapterPageRange(15, 20, [1, 5, 12]) }, { start: 11, end: 19 })
    assert.deepEqual({ ...layout.chapterPageRange(3, 20, []) }, { start: 3, end: 12 })
    assert.deepEqual({ ...layout.chapterPageRange(15, 20, []) }, { start: 15, end: 19 })
    assert.deepEqual({ ...layout.chapterPageRange(0, 500, [1], 100) }, { start: 0, end: 99 })
})

const excerpts = vm.createContext({ Math, FixedSearchExcerptChars: 40 })
vm.runInContext(slice('function searchExcerpt', '// Every match in the book'), excerpts)

test('search excerpts are cut back to whole words and marked where they were cut', () => {
    const text = 'Several words come before the match, which is Wildfire, and several words come after it too.'
    const start = text.indexOf('Wildfire')
    const excerpt = excerpts.searchExcerpt(text, start, start + 'Wildfire'.length, 20)
    assert.equal(excerpt, '… match, which is Wildfire, and several words …')
    assert.equal(excerpts.searchExcerpt('Wildfire', 0, 8, 20), 'Wildfire')
})

test('the book crop grows to cover every page measured, and ignores pages with nothing to crop', () => {
    const a = { x: 0.1, y: 0.1, w: 0.7, h: 0.8 }
    const b = { x: 0.15, y: 0.05, w: 0.7, h: 0.6 }
    const union = layout.unionBox(a, b)
    assert.ok(Math.abs(union.x - 0.1) < 1e-9 && Math.abs(union.y - 0.05) < 1e-9)
    assert.ok(Math.abs(union.x + union.w - 0.85) < 1e-9 && Math.abs(union.y + union.h - 0.9) < 1e-9)
    assert.equal(layout.unionBox(a, null), a)
    assert.equal(layout.unionBox(null, null), null)
})

test('native PDFs request only the selected byte range', async () => {
    const requests = []
    const rangeContext = vm.createContext({
        ArrayBuffer, JSON, Math, Number,
        globalThis: { AndroidBridge: { bookInfo: () => JSON.stringify({ name: 'book.pdf', type: 'application/pdf', size: 100 }) } },
        fetch: async (url, options) => {
            requests.push({ url, options })
            return { ok: true, status: 206, statusText: 'Partial Content', arrayBuffer: async () => new ArrayBuffer(10) }
        },
    })
    vm.runInContext(slice('function nativePdfFile', 'async function open'), rangeContext)
    const file = rangeContext.nativePdfFile('https://appassets.androidplatform.net/book/current')
    const data = await file.slice(20, 30).arrayBuffer()
    assert.equal(data.byteLength, 10)
    assert.equal(requests.length, 1)
    assert.equal(requests[0].options.headers.Range, 'bytes=20-29')
    assert.equal(requests[0].options.cache, 'no-store')
})

test('PDF warm-up prefers the next page and stays inside the book', () => {
    const start = pdf.indexOf('const adjacentPageIndexes')
    const end = pdf.indexOf('// Vayana: pdf.js draws', start)
    assert.ok(start >= 0 && end > start)
    const context = vm.createContext({})
    vm.runInContext(`${pdf.slice(start, end)}; globalThis.result = adjacentPageIndexes`, context)
    assert.deepEqual(Array.from(context.result(4, 10)), [5, 6, 3])
    assert.deepEqual(Array.from(context.result(0, 10)), [1, 2])
    assert.deepEqual(Array.from(context.result(9, 10)), [8])
})

test('PDF raster resolution keeps normal pages sharp and caps high-zoom canvas memory', () => {
    const start = pdf.indexOf('function pdfRenderPixelRatio')
    const end = pdf.indexOf('const adjacentPageIndexes', start)
    assert.ok(start >= 0 && end > start)
    const context = vm.createContext({ Math })
    vm.runInContext(`${pdf.slice(start, end)}; globalThis.result = pdfRenderPixelRatio`, context)
    const ratio = context.result
    assert.equal(ratio({ width: 600, height: 800, zoom: 0.5, pixelRatio: 3, maxPixels: 12_000_000, maxDimension: 8192 }), 3)

    const capped = ratio({ width: 600, height: 800, zoom: 5, pixelRatio: 3, maxPixels: 12_000_000, maxDimension: 8192 })
    assert.ok(600 * 5 * capped * 800 * 5 * capped <= 12_000_000 + 1)
    assert.ok(600 * 5 * capped <= 8192 && 800 * 5 * capped <= 8192)
    assert.ok(capped < 3)
})

test('every PDF page reuses the packaged document shell', () => {
    assert.match(pdf, /new URL\('\.\.\/pdf-page\.html', import\.meta\.url\)/)
    assert.match(pdfPage, /id="canvas"/)
    assert.match(pdfPage, /class="textLayer"/)
    assert.match(pdfPage, /class="annotationLayer"/)
})

test('PDF navigation metadata, previews, rotation and password callbacks are exposed', () => {
    assert.match(pdf, /book\.pageLabels = await pdf\.getPageLabels\(\)/)
    assert.match(pdf, /book\.getPageThumbnail =/)
    assert.match(pdf, /loadingTask\.onPassword =/)
    assert.match(bridge, /function goToPage\(pageIndex\)/)
    assert.match(bridge, /function providePdfPassword\(password\)/)
    assert.match(bridge, /rotationDegrees/)
})

// The page drawing code of foliate/pdf.js in a context with stand-ins for the DOM, pdf.js and the Android bridge.
const pageDrawing = (globals = {}) => {
    const start = pdf.indexOf('let pageColors')
    const end = pdf.indexOf('const renderPage', start)
    assert.ok(start >= 0 && end > start)
    const drawnImages = []
    const savedImages = []
    const canvas = () => ({
        getContext: () => ({ drawImage: image => drawnImages.push(image) }),
        toBlob: callback => callback('webp bytes'),
    })
    const context = vm.createContext({
        Math, Map, WeakMap, Promise, String, Object, Error, devicePixelRatio: 2,
        MessageChannel: class { port1 = {}; port2 = { postMessage() {} } },
        document: { createElement: canvas, querySelectorAll: () => [] },
        CustomEvent: class {},
        FileReader: class { readAsDataURL(blob) { this.result = `data:image/webp;base64,${blob}`; this.onload() } },
        AndroidBridge: { savePageImage: (book, name, data) => savedImages.push({ book, name, data }) },
        fetch: async () => ({ ok: false }),
        pdfjsLib: {
            TextLayer: class { textDivs = []; async render() {} update() {} },
            AnnotationLayer: class { async render() {} },
        },
        ...globals,
    })
    context.globalThis = context
    vm.runInContext(`${pdf.slice(start, end)}; globalThis.api = {
        render, prerender, prerendered, shownPages,
        setColors: c => { pageColors = c },
        setBook: key => { pageImageBook = key },
    }`, context)
    const fakePage = pageNumber => ({
        pageNumber, rotate: 0, renders: 0, cleanups: 0,
        getViewport: ({ scale }) => ({ width: 100 * scale, height: 150 * scale }),
        render() { this.renders++; return { promise: Promise.resolve(), cancel() {} } },
        cleanup() { this.cleanups++ },
        getTextContent: async () => ({ items: [] }),
        getAnnotations: async () => [],
    })
    const fakeDoc = () => {
        const doc = { shown: null, documentElement: { style: { setProperty() {} } }, adoptNode: node => node, dispatchEvent() {} }
        doc.querySelector = selector => selector === '#canvas' ? { replaceChildren: node => { doc.shown = node } }
            : selector === '.textLayer' ? { append() {} }
            : selector === '.annotationLayer' ? { replaceChildren() {} } : null
        return doc
    }
    return { ...context.api, drawnImages, savedImages, fakePage, fakeDoc }
}

test('a page drawn ahead is copied onto the screen, not rendered again', async () => {
    const { render, prerender, setColors, drawnImages, fakePage, fakeDoc } = pageDrawing()
    await render(fakePage(1), fakeDoc(), 1)
    const next = fakePage(2)
    const ahead = await prerender(next)
    assert.equal(next.renders, 1)
    // drawn off screen: its decoded images go once the canvas holds the page
    assert.equal(next.cleanups, 1)
    const doc = fakeDoc()
    await render(next, doc, 1)
    assert.equal(next.renders, 1)
    assert.equal(drawnImages.at(-1), ahead)
    assert.ok(doc.shown)

    // drawn in other colours: rendered afresh
    setColors({ background: '#000000', foreground: '#FFFFFF' })
    await render(next, fakeDoc(), 1)
    assert.equal(next.renders, 2)
})

test('pages are drawn from their saved image, and saved after pdf.js draws them', async () => {
    const book = 'b'.repeat(64)
    const requested = []
    const savedBitmap = { close() { this.closed = true } }
    const { render, setBook, drawnImages, savedImages, fakePage, fakeDoc } = pageDrawing({
        fetch: async url => {
            requested.push(url)
            return url.includes('/7-') ? { ok: true, blob: async () => 'saved' } : { ok: false }
        },
        createImageBitmap: async () => savedBitmap,
    })
    setBook(book)

    const saved = fakePage(7)
    await render(saved, fakeDoc(), 1)
    assert.equal(saved.renders, 0)
    assert.equal(drawnImages.at(-1), savedBitmap)
    assert.ok(savedBitmap.closed)
    assert.equal(requested[0], `/page-cache/${book}/7-200x300-0-own.webp`)

    const fresh = fakePage(8)
    await render(fresh, fakeDoc(), 1)
    assert.equal(fresh.renders, 1)
    assert.deepEqual({ ...savedImages[0] }, { book, name: '8-200x300-0-own.webp', data: 'webp bytes' })

    // zoomed past a screenful: drawn on demand, never saved
    const zoomed = fakePage(9)
    await render(zoomed, fakeDoc(), 30)
    assert.equal(zoomed.renders, 1)
    assert.equal(savedImages.length, 1)
})

test('words recognised on a scanned page become pdf.js text items over their boxes, used only when the page has no text', async () => {
    const start = pdf.indexOf('let ocrKey = null')
    const end = pdf.indexOf('const finishTextLayer', start)
    assert.ok(start >= 0 && end > start)
    const requested = []
    const recognized = { lines: [[['ചെന്നൈ', 35.5, 145.5, 73.5, 151.5, 94], ['എന്ന', 80, 145, 100, 152, 90]], [['൨൦൧൮', 35, 160, 60, 168, 41]], [['കിലോമീറ്ററു', 35, 175, 90, 183, 90]], [['കൾക്കു', 35, 190, 70, 198, 90]]], joins: [2] }
    const context = vm.createContext({
        Map, Math, String, Boolean,
        fetch: async url => {
            requested.push(url)
            return { ok: url.endsWith('/3.json'), json: async () => recognized }
        },
    })
    vm.runInContext(`${pdf.slice(start, end)}; globalThis.api = { pageTextContent, setKey: key => { ocrKey = key } }`, context)
    const { pageTextContent, setKey } = context.api
    const page = (pageNumber, items) => ({ pageNumber, view: [0, 0, 356, 576], getTextContent: async () => ({ items, styles: {} }) })

    // no key: the page's own (empty) text, nothing fetched
    assert.equal((await pageTextContent(page(4, []))).items.length, 0)
    assert.equal(requested.length, 0)

    setKey('a'.repeat(64))
    const own = await pageTextContent(page(4, [{ str: 'printed', hasEOL: false }]))
    assert.equal(own.items[0].str, 'printed')
    assert.equal(requested.length, 0)

    const content = await pageTextContent(page(4, []))
    assert.equal(requested[0], `/ocr/${'a'.repeat(64)}/3.json`)
    assert.deepEqual(Array.from(content.items, item => item.str), ['ചെന്നൈ', ' ', 'എന്ന', '2018', 'കിലോമീറ്ററു', 'കൾക്കു'])
    // a joined line (2) ends without a break, so the word reads whole across it, and its first half is marked
    assert.deepEqual(Array.from(content.items, item => item.hasEOL), [false, false, true, true, false, true])
    assert.deepEqual(Array.from(content.items, item => Boolean(item.joinsNext)), [false, false, false, false, true, false])
    // a word Tesseract was unsure of (41) is marked; lookup skips it
    assert.deepEqual(Array.from(content.items, item => Boolean(item.lowConfidence)), [false, false, false, true, false, false])
    const [first, space, second] = content.items
    // the item spans the word across, and its line down: the line's band (145-152) reaches halfway to the next line
    // (160), so a press anywhere on the line finds a word; the baseline sits a fifth of that height above its foot
    assert.equal(first.transform[4], 35.5)
    assert.equal(first.width, 38)
    assert.equal(first.height, 11)
    assert.equal(second.height, 11)
    assert.ok(Math.abs(first.transform[5] - (576 - 156 + 11 * 0.2)) < 1e-9)
    assert.equal(space.transform[4], 73.5)
    assert.equal(space.width, 6.5)
    assert.equal(content.styles[first.fontName].ascent, 0.8)

    // a page not recognised yet keeps its own empty text
    assert.equal((await pageTextContent(page(5, []))).items.length, 0)
})
