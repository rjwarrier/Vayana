import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import vm from 'node:vm'
import { JSDOM } from 'jsdom'

const source = (await readFile(new URL('../src/main/assets/foliate/overlayer.js', import.meta.url), 'utf8'))
    .replace('export class Overlayer', 'class Overlayer')

function setup() {
    const { window } = new JSDOM('<!doctype html><body></body>')
    const context = vm.createContext({ document: window.document })
    vm.runInContext(source, context)
    const overlayer = vm.runInContext('new Overlayer()', context)
    const add = (key, rect, options) => {
        const range = { getClientRects: () => [rect] }
        overlayer.add(key, range, () => window.document.createElementNS('http://www.w3.org/2000/svg', 'g'), options)
        return range
    }
    return { overlayer, add }
}

const forgivingOptions = {
    padding: 12,
    minimumTargetSize: 56,
    filter: key => !key.startsWith('foliate-search:'),
}

function assertHit(actual, key, range) {
    assert.equal(actual[0], key)
    assert.equal(actual[1], range)
}

test('highlight hit testing accepts a natural near miss but rejects a distant tap', () => {
    const { overlayer, add } = setup()
    const range = add('highlight', { left: 50, right: 150, top: 100, bottom: 120 })

    assertHit(overlayer.hitTest({ x: 80, y: 136 }, forgivingOptions), 'highlight', range)
    assert.equal(overlayer.hitTest({ x: 80, y: 150 }, forgivingOptions).length, 0)
})

test('an exact highlight hit wins over an overlapping expanded target', () => {
    const { overlayer, add } = setup()
    const exactRange = add('exact', { left: 50, right: 150, top: 100, bottom: 120 })
    add('nearer-in-stack', { left: 50, right: 150, top: 125, bottom: 145 })

    assertHit(overlayer.hitTest({ x: 80, y: 110 }, forgivingOptions), 'exact', exactRange)
})

test('an editable highlight wins an exact overlap with a later community mark', () => {
    const { overlayer, add } = setup()
    const editableRange = add(
        'personal-highlight',
        { left: 50, right: 150, top: 100, bottom: 120 },
        { tapPriority: 1 },
    )
    add(
        'community-highlight',
        { left: 40, right: 170, top: 100, bottom: 120 },
        { tapPriority: 0 },
    )

    assertHit(overlayer.hitTest({ x: 80, y: 110 }, forgivingOptions), 'personal-highlight', editableRange)
})

test('expanded targets choose the nearest editable highlight and ignore search marks', () => {
    const { overlayer, add } = setup()
    const nearestRange = add('nearest', { left: 50, right: 150, top: 100, bottom: 118 })
    add('farther', { left: 50, right: 150, top: 135, bottom: 153 })
    add('foliate-search:result', { left: 50, right: 150, top: 119, bottom: 121 })

    assertHit(overlayer.hitTest({ x: 80, y: 124 }, forgivingOptions), 'nearest', nearestRange)
})
