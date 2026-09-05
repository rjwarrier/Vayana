import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const cfiSource = await readFile(new URL('../src/main/assets/foliate/epubcfi.js', import.meta.url), 'utf8')
const CFI = await import(`data:text/javascript;base64,${Buffer.from(cfiSource).toString('base64')}`)
const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
// Exercise the actual formatting code without initializing the Android reader bridge.
const formatting = bridge.slice(bridge.indexOf('const BionicWordClass'), bridge.indexOf('function wireSelection'))

function fixture() {
    const { window } = new JSDOM('<html><head></head><body><p>Hello beautiful world <em>reader</em> again.</p></body></html>')
    globalThis.NodeFilter = window.NodeFilter
    const context = vm.createContext({
        NodeFilter: window.NodeFilter,
        documentTextIndexes: new WeakMap(),
        unmatchedInDoc: new WeakMap(),
    })
    vm.runInContext(formatting, context)
    return { doc: window.document, format: context.transformBionicWords, revert: context.revertBionicWords }
}

test('an original highlight resolves to the same text after enabling and disabling bionic reading', () => {
    const { doc, format, revert } = fixture()
    const range = doc.createRange()
    range.setStart(doc.querySelector('p').firstChild, 6)
    range.setEnd(doc.querySelector('em').firstChild, 4)
    const text = range.toString()
    const cfi = CFI.fromRange(range)
    format(doc)
    const formatted = CFI.toRange(doc, CFI.parse(cfi))
    assert.equal(formatted.toString(), text)
    assert.equal(CFI.fromRange(formatted), cfi)
    revert(doc)
    assert.equal(CFI.toRange(doc, CFI.parse(cfi)).toString(), text)
})

test('a highlight created inside bold text survives reopening the unformatted book', () => {
    const { doc, format } = fixture()
    format(doc)
    const bold = doc.querySelectorAll('b')[1].firstChild
    const range = doc.createRange()
    range.setStart(bold, 1)
    range.setEnd(bold, 4)
    const cfi = CFI.fromRange(range)
    const { doc: reopened } = fixture()
    assert.equal(CFI.toRange(reopened, CFI.parse(cfi)).toString(), range.toString())
})

test('a collapsed reading position keeps its original offset across formatting', () => {
    const { doc, format, revert } = fixture()
    const range = doc.createRange()
    range.setStart(doc.querySelector('p').firstChild, 9)
    range.collapse(true)
    const cfi = CFI.fromRange(range)
    format(doc)
    assert.equal(CFI.fromRange(CFI.toRange(doc, CFI.parse(cfi))), cfi)
    revert(doc)
    const restored = CFI.toRange(doc, CFI.parse(cfi))
    assert.equal(restored.startOffset, 9)
    assert.equal(restored.startContainer, doc.querySelector('p').firstChild)
})
