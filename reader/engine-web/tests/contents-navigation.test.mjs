import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const source = bridge.slice(bridge.indexOf('async function goToHref('), bridge.indexOf('function applyReaderMargin('))
const locator = (chapter, text) => `text:search:${encodeURIComponent(chapter)}:${encodeURIComponent(text)}`
function fixture(found = true) {
    const navigated = [], loaded = [], matched = []
    const range = { collapse(value) { assert.equal(value, true) } }
    const view = {
        book: { resolveHref: href => href === 'OPS/chapter.xhtml' ? { index: 1 } : null,
            sections: [{ createDocument: () => { throw Error('wrong chapter') } },
                { createDocument: async () => { loaded.push(1); return {} } }] },
        getCFI: (index, value) => { assert.equal(index, 1); assert.equal(value, range); return 'epubcfi(target)' },
        goTo: async value => navigated.push(value),
    }
    const context = vm.createContext({ view, Number, decodeURIComponent,
        findTextRangeInDoc: (_, text) => { matched.push(text); return found ? range : null }, post() {} })
    vm.runInContext(source, context)
    return { context, navigated, loaded, matched }
}
test('opens the matching passage in its indexed chapter', async () => {
    const f = fixture()
    await f.context.goToHref(locator('OPS/chapter.xhtml', 'Café: quiet river'))
    assert.deepEqual(f.loaded, [1])
    assert.deepEqual(f.matched, ['Café: quiet river'])
    assert.deepEqual(f.navigated, ['epubcfi(target)'])
})
test('falls back to the chapter when text changed', async () => {
    const f = fixture(false)
    await f.context.goToHref(locator('OPS/chapter.xhtml', 'lost phrase'))
    assert.deepEqual(f.navigated, ['OPS/chapter.xhtml'])
})
test('invalid locators do not navigate or scan unrelated chapters', async () => {
    const f = fixture()
    for (const value of ['text:search:broken', 'text:search:%ZZ:quote', locator('missing.xhtml', 'quote'), locator('OPS/chapter.xhtml', 'x'.repeat(1801))]) await f.context.goToHref(value)
    assert.deepEqual(f.navigated, [])
    assert.deepEqual(f.loaded, [])
})
