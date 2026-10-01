import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import vm from 'node:vm'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('const TextPinchThresholdRatio')
const end = bridge.indexOf('const SelectionDragSettleMillis', start)
assert.ok(start >= 0 && end > start, 'text pinch policy not found')

function harness() {
    const listeners = new Map()
    const posts = []
    let selectionActive = false
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
    }
    const context = vm.createContext({
        hasReaderSelection: () => selectionActive,
        post: (type, payload) => posts.push([type, payload]),
        touchDistance: (a, b) => Math.hypot(a.screenX - b.screenX, a.screenY - b.screenY),
    })
    vm.runInContext(
        `${bridge.slice(start, end)}\n` +
            `globalThis.wireTextPinch = wireTextPinch; globalThis.textPinchStep = textPinchStep`,
        context,
    )
    context.wireTextPinch(doc)
    return {
        listeners,
        posts,
        setSelectionActive: value => { selectionActive = value },
    }
}

const touchesAtDistance = distance => [
    { screenX: 0, screenY: 0 },
    { screenX: distance, screenY: 0 },
]

test('pinch out requests exactly one larger font step after the fingers lift', () => {
    const { listeners, posts } = harness()
    listeners.get('touchstart')({ touches: touchesAtDistance(100) })
    let prevented = false
    listeners.get('touchmove')({
        touches: touchesAtDistance(125),
        preventDefault: () => { prevented = true },
    })
    assert.equal(prevented, true)
    assert.deepEqual(posts, [])
    listeners.get('touchend')({ touches: touchesAtDistance(0).slice(0, 1) })
    listeners.get('touchend')({ touches: [] })
    assert.equal(posts.length, 1)
    assert.equal(posts[0][0], 'fontSizeStep')
    assert.equal(posts[0][1].direction, 1)
})

test('pinch in requests one smaller font step and a small movement does nothing', () => {
    const { listeners, posts } = harness()
    listeners.get('touchstart')({ touches: touchesAtDistance(100) })
    listeners.get('touchmove')({ touches: touchesAtDistance(85), preventDefault: () => {} })
    listeners.get('touchend')({ touches: [] })
    listeners.get('touchstart')({ touches: touchesAtDistance(100) })
    listeners.get('touchmove')({ touches: touchesAtDistance(95), preventDefault: () => {} })
    listeners.get('touchend')({ touches: [] })
    assert.equal(posts.length, 1)
    assert.equal(posts[0][0], 'fontSizeStep')
    assert.equal(posts[0][1].direction, -1)
})

test('an existing or newly-created text selection cancels font resizing', () => {
    const { listeners, posts, setSelectionActive } = harness()
    setSelectionActive(true)
    listeners.get('touchstart')({ touches: touchesAtDistance(100) })
    listeners.get('touchmove')({ touches: touchesAtDistance(140), preventDefault: () => {} })
    listeners.get('touchend')({ touches: [] })

    setSelectionActive(false)
    listeners.get('touchstart')({ touches: touchesAtDistance(100) })
    setSelectionActive(true)
    listeners.get('touchmove')({ touches: touchesAtDistance(140), preventDefault: () => {} })
    listeners.get('touchend')({ touches: [] })
    assert.deepEqual(posts, [])
})
