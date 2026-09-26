import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('function fixedLayoutFraction')
const end = bridge.indexOf('function isTextAnnotationValue')
assert.ok(start > 0 && end > start, 'fixed-layout fraction helpers not found in bridge.js')

const context = vm.createContext({ Math, Number })
vm.runInContext(bridge.slice(start, end), context)
const { fixedLayoutFraction, fixedLayoutIndexOfFraction } = context

test('the last page of a fixed-layout book reports the whole book as read', () => {
    assert.equal(fixedLayoutFraction({ current: 9, total: 10 }), 1)
    assert.equal(fixedLayoutFraction({ current: 0, total: 10 }), 0.1)
})

test('navigating to a reported fraction returns to the same page', () => {
    for (const total of [1, 2, 7, 10, 333]) {
        for (let index = 0; index < total; index++) {
            const fraction = fixedLayoutFraction({ current: index, total })
            assert.equal(fixedLayoutIndexOfFraction(fraction, total), index, `page ${index} of ${total}`)
        }
    }
})

test('slider extremes and the resume clamp land on real pages', () => {
    assert.equal(fixedLayoutIndexOfFraction(0, 10), 0)
    assert.equal(fixedLayoutIndexOfFraction(1, 10), 9)
    assert.equal(fixedLayoutIndexOfFraction(0.999, 10), 9)
    assert.equal(fixedLayoutIndexOfFraction(0.5, 10), 4)
})

test('unknown positions stay unknown', () => {
    assert.equal(fixedLayoutFraction(null), null)
    assert.equal(fixedLayoutFraction({ current: 0, total: 0 }), null)
    assert.equal(fixedLayoutIndexOfFraction(Number.NaN, 10), null)
    assert.equal(fixedLayoutIndexOfFraction(0.5, 0), null)
})
