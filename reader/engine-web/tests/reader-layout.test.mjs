import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const source = bridge.slice(bridge.indexOf('function applyReaderMargin('), bridge.indexOf('function applyStyle('))

test('margin changes render once and height-only keyboard resizes do not repeat the margin render', () => {
    let attribute = null, renders = 0
    const renderer = {
        getAttribute: () => attribute,
        setAttribute: (_, value) => { attribute = value; renders++ },
        render: () => { renders++ },
    }
    const context = vm.createContext({ view: { renderer }, readerSideMarginPercent: 10,
        window: {}, innerWidth: 500, innerHeight: 1000 })
    vm.runInContext(source, context)
    context.applyReaderMargin(10)
    assert.equal(attribute, '400px')
    assert.equal(renders, 1, 'paginator attribute callback already renders')
    context.innerHeight = 600
    context.applyReaderMargin(10)
    assert.equal(renders, 1, 'ResizeObserver handles the height change')
    context.applyReaderMargin(20)
    assert.equal(attribute, '300px')
    assert.equal(renders, 2)
    context.innerWidth = 1000
    context.innerHeight = 700
    context.applyReaderMargin(10)
    assert.equal(attribute, '360px')
    assert.equal(renders, 3)
})
