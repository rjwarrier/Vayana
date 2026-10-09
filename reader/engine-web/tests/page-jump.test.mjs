import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('async function goToPage(')
const source = bridge.slice(start >= 0 ? start : bridge.indexOf('function goToPage('), bridge.indexOf('function providePdfPassword('))
function fixture(fixedLayout = false) {
    const navigated = [], anchors = []
    let currentIndex = -1
    const context = vm.createContext({ fixedLayout, pageJumpSnapshot: null,
        window: { innerWidth: 500, innerHeight: 1000 }, bytesPerPage: new Map([[0, 100]]),
        lastSentTocRevision: 0, requestAnimationFrame: callback => callback(),
        pageEstimateCache: { prefixPages: [0, 10, 30], totalPages: 30 },
        bookPageStats: () => ({ totalPages: 30 }),
        view: { book: { sections: [{}, {}] },
            goTo: async index => { navigated.push(index); currentIndex = index },
            renderer: { pages: 22, getContents: () => [{ index: currentIndex }],
                scrollToAnchor: async anchor => anchors.push(anchor) } },
    })
    vm.runInContext(source, context)
    return { context, navigated, anchors }
}
test('EPUB jumps to the section and exact local page, including the last page', async () => {
    const f = fixture()
    await f.context.goToPage(10)
    await f.context.goToPage(29)
    assert.deepEqual(f.navigated, [1])
    assert.deepEqual(f.anchors, [0, 1])
})
test('failed chapter loads do not scroll or measure the previously open chapter', async () => {
    const f = fixture()
    f.context.view.goTo = async () => {}
    f.context.bookPageStats = () => { throw Error('must not measure wrong chapter') }
    await f.context.goToPage(10)
    assert.deepEqual(f.anchors, [])
})
test('invalid EPUB page indexes do not navigate', async () => {
    const f = fixture()
    for (const index of [-1, 30, 1.5, NaN]) await f.context.goToPage(index)
    assert.deepEqual(f.navigated, [])
})
test('PDF jumps remain zero based and bounded', async () => {
    const f = fixture(true)
    for (const index of [0, 1, -1, 2]) await f.context.goToPage(index)
    assert.deepEqual(f.navigated, [0, 1])
})
test('last displayed EPUB page is reachable despite rounding of chapter estimates', async () => {
    const f = fixture()
    f.context.pageEstimateCache.prefixPages = [0, 10.4, 29.4]
    await f.context.goToPage(29)
    assert.deepEqual(f.navigated, [1])
    assert.deepEqual(f.anchors, [1])
})
test('EPUB re-resolves the chapter when measuring changes its page boundaries', async () => {
    const f = fixture()
    f.context.bookPageStats = index => {
        f.context.pageEstimateCache = { prefixPages: [0, 25, 45], totalPages: 45 }
        f.context.view.renderer.pages = index === 0 ? 27 : 22
    }
    await f.context.goToPage(20)
    assert.deepEqual(f.navigated, [1, 0])
    assert.deepEqual(f.anchors, [20 / 24])
})
test('EPUB correction is bounded if estimates keep changing', async () => {
    const f = fixture()
    f.context.bookPageStats = index => {
        f.context.pageEstimateCache = { prefixPages: index === 1 ? [0, 25, 45] : [0, 10, 30], totalPages: 45 }
    }
    await f.context.goToPage(20)
    assert.ok(f.navigated.length <= 3)
    assert.equal(f.anchors.length, 1)
})

test('page dialog restores full-height estimates before resolving a page number', async () => {
    const f = fixture()
    f.context.preparePageJump()
    f.context.window.innerHeight = 600
    f.context.pageEstimateCache = { prefixPages: [0, 20, 60], totalPages: 60 }
    f.context.bytesPerPage.set(0, 50)
    f.context.requestAnimationFrame = callback => { f.context.window.innerHeight = 1000; callback() }
    await f.context.goToPage(10)
    assert.deepEqual(f.navigated, [1])
    assert.deepEqual(f.anchors, [0])
    assert.equal(f.context.bytesPerPage.get(0), 100)
})

test('cancelling the dialog restores its original position and page estimates', async () => {
    const f = fixture()
    f.context.view.lastLocation = { cfi: 'epubcfi(saved)' }
    f.context.preparePageJump()
    f.context.pageEstimateCache = { prefixPages: [0, 20, 60], totalPages: 60 }
    await f.context.cancelPageJump()
    assert.deepEqual(f.navigated, ['epubcfi(saved)'])
    assert.equal(f.context.pageEstimateCache.totalPages, 30)
})

test('cancel keeps the original rendered page instead of rounding its passage CFI', async () => {
    const f = fixture()
    f.context.view.renderer.page = 8
    f.context.preparePageJump()
    f.context.view.renderer.page = 12
    await f.context.cancelPageJump()
    assert.deepEqual(f.navigated, [])
    assert.deepEqual(f.anchors, [7 / 19])
})
