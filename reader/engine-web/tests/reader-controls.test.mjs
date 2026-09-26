import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import vm from 'node:vm'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('const DoubleTapWindowMillis')
const end = bridge.indexOf('function selectWordAt', start)
assert.ok(start >= 0 && end > start, 'reader tap arbitration not found in bridge.js')

function harness({ word = false, fixed = false } = {}) {
    const posted = []
    const timers = new Map()
    let timerId = 0
    let clicks
    let lookups = 0
    let zooms = 0
    const context = vm.createContext({
        Math,
        Number,
        WeakMap,
        fixedLayout: fixed,
        window: { innerWidth: 900 },
        post: (type, payload) => posted.push({ type, payload }),
        selectWordAt: () => {
            lookups++
            return word
        },
        toggleFixedZoom: () => { zooms++ },
        setTimeout: callback => {
            const id = ++timerId
            timers.set(id, callback)
            return id
        },
        clearTimeout: id => timers.delete(id),
    })
    vm.runInContext(bridge.slice(start, end), context)
    const doc = {
        defaultView: { top: { innerWidth: 900 } },
        addEventListener: (type, handler) => { if (type === 'click') clicks = handler },
    }
    context.wireDoubleTapLookup(doc)
    const click = (time, x = 450, y = 300) => clicks({
        target: { closest: () => null },
        screenX: x,
        clientX: x,
        clientY: y,
        timeStamp: time,
    })
    const flush = () => {
        const pending = [...timers.values()]
        timers.clear()
        pending.forEach(callback => callback())
    }
    return {
        context,
        posted,
        click,
        flush,
        lookups: () => lookups,
        zooms: () => zooms,
    }
}

test('single center tap requests controls after the double-tap window', () => {
    const h = harness()
    h.context.setReaderControlsGesture(1, false, false)
    h.click(0)
    assert.equal(h.posted.length, 0)
    h.flush()
    assert.deepEqual(h.posted.map(event => event.type), ['controlsRequested'])
})

test('double-tap controls preserve dictionary lookup on a word', () => {
    const word = harness({ word: true })
    word.context.setReaderControlsGesture(2, false, false)
    word.click(0)
    word.click(100)
    assert.equal(word.lookups(), 1)
    assert.equal(word.posted.length, 0)

    const blank = harness()
    blank.context.setReaderControlsGesture(2, false, false)
    blank.click(0)
    blank.click(100)
    assert.deepEqual(blank.posted.map(event => event.type), ['controlsRequested'])
})

test('triple tap wins over the delayed double-tap action', () => {
    const h = harness({ word: true })
    h.context.setReaderControlsGesture(3, false, false)
    h.click(0)
    h.click(100)
    h.click(200)
    h.flush()
    assert.equal(h.lookups(), 0)
    assert.deepEqual(h.posted.map(event => event.type), ['controlsRequested'])
})

test('visible controls and read-aloud block new control requests', () => {
    for (const [visible, blocked] of [[true, false], [false, true]]) {
        const h = harness()
        h.context.setReaderControlsGesture(1, visible, blocked)
        h.click(0)
        h.flush()
        assert.equal(h.posted.length, 0)
    }
})
