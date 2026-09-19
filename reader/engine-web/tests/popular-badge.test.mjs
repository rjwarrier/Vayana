import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('function appendPopularCountBadge')
const end = bridge.indexOf("// 'relocate' can fire many times")
assert.ok(start > 0 && end > start, 'appendPopularCountBadge not found in bridge.js')

const { window } = new JSDOM('<!doctype html><body></body>')
const context = vm.createContext({ document: window.document })
vm.runInContext(bridge.slice(start, end), context)

const SVG = 'http://www.w3.org/2000/svg'
const pageWidth = 400

function badgeFor(rect) {
    const group = window.document.createElementNS(SVG, 'g')
    const doc = { documentElement: { getBoundingClientRect: () => ({ width: pageWidth }) } }
    context.appendPopularCountBadge(group, [rect], '128', '#6366F1', doc)
    const pill = group.querySelector('rect')
    return {
        text: group.querySelector('text').textContent,
        left: Number(pill.getAttribute('x')),
        right: Number(pill.getAttribute('x')) + Number(pill.getAttribute('width')),
    }
}

test('the popularity count is drawn as a pill with its number', () => {
    assert.equal(badgeFor({ left: 40, top: 10, width: 100, height: 16 }).text, '128')
})

test('a quote on the left half of a page puts its badge in the left margin', () => {
    const badge = badgeFor({ left: 40, top: 10, width: 100, height: 16 })
    assert.ok(badge.left >= 0 && badge.right < 40)
})

test('a quote on the right half puts its badge in the right margin', () => {
    const badge = badgeFor({ left: 250, top: 10, width: 100, height: 16 })
    assert.ok(badge.right <= pageWidth && badge.left > 350 - 30)
})

test('a quote on a later page keeps its badge on that page, not on page one', () => {
    const badge = badgeFor({ left: pageWidth + 40, top: 10, width: 100, height: 16 })
    assert.ok(badge.left >= pageWidth && badge.right < pageWidth + 40)
})
