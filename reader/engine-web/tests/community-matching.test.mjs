import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
function fixture(html) {
    const { window } = new JSDOM(`<body>${html}</body>`)
    const context = vm.createContext({ NodeFilter: window.NodeFilter, documentTextIndexes: new WeakMap() })
    vm.runInContext(bridge.slice(bridge.indexOf('function normalizeForMatching('), bridge.indexOf('async function findCfiInBook(')), context)
    return { context, doc: window.document }
}
const strict = { exactOnly: true, unique: true }
test('community matching refuses similar prose while personal quote matching retains its fallback', () => {
    const f = fixture('<p>The traveller found a quiet house beside the old river at dawn.</p>')
    const quote = 'The traveller found a quiet castle beside the old river at dawn.'
    assert.ok(f.context.findTextRangeInDoc(f.doc, quote))
    assert.equal(f.context.findTextRangeInDoc(f.doc, quote, strict), null)
})
test('community matching rejects repeated passages rather than guessing a location', () => {
    const f = fixture('<p>The river ran quietly under the bridge.</p><p>The river ran quietly under the bridge.</p>')
    assert.equal(f.context.findTextRangeInDoc(f.doc, 'The river ran quietly under the bridge.', strict), null)
})
test('community matching keeps genuine quotes across formatting and accent differences', () => {
    const f = fixture('<p>Café society <em>waited quietly</em> under the bridge.</p>')
    const range = f.context.findTextRangeInDoc(f.doc, 'Cafe society waited quietly under the bridge.', strict)
    assert.equal(range?.toString(), 'Café society waited quietly under the bridge')
})

test('community underlines ignore empty rectangles instead of leaving stray dots', () => {
    const { window } = new JSDOM('<body></body>')
    let handler, shape
    const context = vm.createContext({ document: window.document, DefaultAnnotationColor: '#111111',
        markColor: value => value, popularBadgeIndexDirty: false,
        resetPopularBadgeResolutionRetries() {}, scheduleBadgeLayout() {},
        view: { addEventListener: (_, callback) => { handler = callback } } })
    vm.runInContext(bridge.slice(bridge.indexOf("view.addEventListener('draw-annotation'"), bridge.indexOf("view.addEventListener('show-annotation'")), context)
    handler({ detail: { annotation: { type: 'underline', popular: true }, draw: callback => {
        shape = callback([{ left: 200, bottom: 20, width: 0, height: 20 }, { left: 40, bottom: 120, width: 100, height: 20 }], {})
    } } })
    assert.equal(shape.querySelectorAll('line').length, 1)
    assert.equal(shape.querySelector('line').getAttribute('y1'), '119')
})
