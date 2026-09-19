import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('function popularBadgePlacement')
const end = bridge.indexOf('const popularBadgeLayer')
assert.ok(start > 0 && end > start, 'popularBadgePlacement not found in bridge.js')

const context = vm.createContext({ Math })
vm.runInContext(bridge.slice(start, end), context)

// A 400px page whose left edge is at x=40 on a 480px screen, showing document x 1200..1600.
const page = { pageStart: 1200, pageSize: 400, iframeLeft: 40 - 1200, iframeTop: 50, badgeWidth: 30, badgeHeight: 18, viewportWidth: 480 }
const place = quote => context.popularBadgePlacement({ ...page, ...quote })

test('a quote starting in the left half puts its pill in the margin left of the page', () => {
    const at = place({ rectLeft: 1230, rectTop: 100, rectHeight: 20 })
    assert.equal(at.onLeft, true)
    assert.ok(at.x + page.badgeWidth <= 40, 'pill must end before the page edge, clear of the text')
    assert.ok(at.x >= 0)
})

test('a quote starting in the right half puts its pill in the margin right of the page', () => {
    const at = place({ rectLeft: 1500, rectTop: 100, rectHeight: 20 })
    assert.equal(at.onLeft, false)
    assert.ok(at.x >= 40 + 400, 'pill must start after the page edge')
    assert.ok(at.x + page.badgeWidth <= page.viewportWidth)
})

test('the pill is centred on the first line of the quote', () => {
    const at = place({ rectLeft: 1230, rectTop: 100, rectHeight: 20 })
    assert.equal(at.y + page.badgeHeight / 2, 50 + 100 + 20 / 2)
})

test('a quote that starts on another page gets no pill here', () => {
    assert.equal(place({ rectLeft: 1100, rectTop: 100, rectHeight: 20 }), null)
    assert.equal(place({ rectLeft: 1600, rectTop: 100, rectHeight: 20 }), null)
})

test('a pill never leaves the screen when the margin is too narrow', () => {
    const narrow = context.popularBadgePlacement({ ...page, iframeLeft: 5 - 1200, rectLeft: 1230, rectTop: 100, rectHeight: 20 })
    assert.ok(narrow.x >= 0)
})
