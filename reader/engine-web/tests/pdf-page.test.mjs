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
    assert.deepEqual(Array.from(context.result(4, 10)), [5, 3])
    assert.deepEqual(Array.from(context.result(0, 10)), [1])
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
