import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'
import vm from 'node:vm'

const paginator = await readFile(new URL('../src/main/assets/foliate/paginator.js', import.meta.url), 'utf8')
const turnStart = paginator.indexOf('const selectionIsBackward')
const turnEnd = paginator.indexOf('const setSelectionTo', turnStart)
assert.ok(turnStart >= 0 && turnEnd > turnStart, 'selection page-turn policy not found')

const turnContext = vm.createContext({})
vm.runInContext(
    `${paginator.slice(turnStart, turnEnd)}\n` +
        `globalThis.selectionPageTurnDirection = selectionPageTurnDirection;\n` +
        `globalThis.selectionPageTurnAllowed = selectionPageTurnAllowed;\n` +
        `globalThis.selectionPageTurnRetreated = selectionPageTurnRetreated;\n` +
        `globalThis.selectionPageTurnIntent = selectionPageTurnIntent;\n` +
        `globalThis.touchEventHasSelection = touchEventHasSelection;\n` +
        `globalThis.restoreTouchStartPosition = restoreTouchStartPosition;\n` +
        `globalThis.paginatedSelectionScrollCorrection = paginatedSelectionScrollCorrection`,
    turnContext,
)

const direction = overrides => turnContext.selectionPageTurnDirection({
    backward: false,
    startsBeforePage: false,
    endsAfterPage: false,
    atSectionStart: false,
    atSectionEnd: false,
    ...overrides,
})

test('selection handles request page turns in either direction at the visible edge', () => {
    assert.equal(direction({ endsAfterPage: true }), 1)
    assert.equal(direction({ backward: true, startsBeforePage: true }), -1)
})

test('selection never auto-turns across an EPUB document boundary', () => {
    assert.equal(direction({ endsAfterPage: true, atSectionEnd: true }), 0)
    assert.equal(direction({ backward: true, startsBeforePage: true, atSectionStart: true }), 0)
})

test('native text selection owns touch movement instead of the paginator', () => {
    const activeDocument = {
        nodeType: 9,
        getSelection: () => ({ type: 'Range', isCollapsed: false }),
    }
    const collapsedDocument = {
        nodeType: 9,
        getSelection: () => ({ type: 'Caret', isCollapsed: true }),
    }

    assert.equal(turnContext.touchEventHasSelection({ currentTarget: activeDocument }), true)
    assert.equal(turnContext.touchEventHasSelection({ currentTarget: collapsedDocument }), false)
})

test('a canceled page pan restores its exact starting offset', () => {
    const element = { scrollLeft: 237 }
    turnContext.restoreTouchStartPosition(element, 'scrollLeft', { offset: 0 })
    assert.equal(element.scrollLeft, 0)
    assert.match(paginator, /addEventListener\('touchcancel', this\.#onTouchCancel\.bind\(this\)\)/)
})

test('native selection auto-scroll is returned to the last settled page', () => {
    const activeSelection = { type: 'Range', isCollapsed: false }
    const correction = overrides => turnContext.paginatedSelectionScrollCorrection({
        scrolled: false,
        locked: false,
        selection: activeSelection,
        currentOffset: 2_015.69,
        settledOffset: 2_214.91,
        ...overrides,
    })

    assert.equal(correction({}), 2_214.91)
    assert.equal(correction({ locked: true }), null, 'programmatic page turns retain ownership')
    assert.equal(correction({ scrolled: true }), null, 'continuous reading may scroll freely')
    assert.equal(correction({ selection: { type: 'Caret', isCollapsed: true } }), null)
    assert.equal(correction({ currentOffset: 2_214.5 }), null, 'subpixel layout drift is harmless')
})

test('reaching an edge only arms a turn until the endpoint moves farther', () => {
    const focusA = {}
    const focusB = {}
    const first = turnContext.selectionPageTurnIntent({
        pendingEdge: null,
        direction: 1,
        focusNode: focusA,
        focusOffset: 4,
        focusChanged: false,
        focusMovedFurther: false,
        now: 1_000,
    })
    assert.equal(first.ready, false)
    assert.equal(first.pendingEdge.focusNode, focusA)

    const stationary = turnContext.selectionPageTurnIntent({
        pendingEdge: first.pendingEdge,
        direction: 1,
        focusNode: focusA,
        focusOffset: 4,
        focusChanged: false,
        focusMovedFurther: false,
        now: 1_500,
    })
    assert.equal(stationary.ready, false, 'holding at the edge must not turn the page')

    const earlyMovement = turnContext.selectionPageTurnIntent({
        pendingEdge: first.pendingEdge,
        direction: 1,
        focusNode: focusB,
        focusOffset: 1,
        focusChanged: true,
        focusMovedFurther: true,
        now: 1_500,
    })
    assert.equal(earlyMovement.ready, false, 'the edge hold gives time for accurate correction')

    const continuedMovement = turnContext.selectionPageTurnIntent({
        pendingEdge: first.pendingEdge,
        direction: 1,
        focusNode: focusB,
        focusOffset: 2,
        focusChanged: true,
        focusMovedFurther: true,
        now: 1_700,
    })
    assert.equal(continuedMovement.ready, true)
})

test('moving inward at the edge re-arms paging from the corrected endpoint', () => {
    const original = { direction: -1, focusNode: {}, focusOffset: 8, time: 1_000 }
    const correctedNode = {}
    const corrected = turnContext.selectionPageTurnIntent({
        pendingEdge: original,
        direction: -1,
        focusNode: correctedNode,
        focusOffset: 2,
        focusChanged: true,
        focusMovedFurther: false,
        now: 1_400,
    })
    assert.equal(corrected.ready, false)
    assert.equal(corrected.pendingEdge.focusNode, correctedNode)
    assert.equal(corrected.pendingEdge.time, 1_400)
})

test('a long selection can keep turning pages only as its endpoint advances', () => {
    const focusA = {}
    const focusB = {}
    const lastTurn = { direction: 1, focusNode: focusA, focusOffset: 4, time: 1_000 }
    const allowed = overrides => turnContext.selectionPageTurnAllowed({
        lastTurn,
        direction: 1,
        focusNode: focusA,
        focusOffset: 4,
        now: 1_500,
        ...overrides,
    })

    assert.equal(allowed({}), false, 'page relocation with the same endpoint must not turn again')
    assert.equal(allowed({ focusNode: focusB, now: 1_200 }), false, 'rapid range churn must be throttled')
    assert.equal(allowed({ focusNode: focusB, now: 2_199 }), false, 'repeat turns wait for deliberate movement')
    assert.equal(allowed({ focusNode: focusB, now: 2_200 }), true, 'an advanced endpoint may turn another page')
    assert.equal(allowed({ direction: -1, focusNode: focusB, now: 2_200 }), true, 'the drag may reverse direction')
})

test('each additional page requires a deliberate retreat and renewed edge push', () => {
    const entryNode = {}
    const entryFocus = { node: entryNode, offset: 6 }
    const retreated = overrides => turnContext.selectionPageTurnRetreated({
        direction: 1,
        entryFocus,
        focusNode: {},
        focusOffset: 9,
        ...overrides,
    })

    assert.equal(retreated({}), false, 'holding or advancing at the edge cannot turn another page')
    assert.equal(retreated({ direction: 0, focusNode: entryNode, focusOffset: 6 }), false,
        'the focus the turn left behind is not fresh user intent')
    assert.equal(retreated({ direction: 0, focusNode: entryNode, focusOffset: 8 }), true,
        'moving back inside the new page rearms the next deliberate edge push')
})

test('a later selection gesture starts a fresh page-turn session', () => {
    assert.equal(turnContext.selectionPageTurnAllowed({
        lastTurn: { direction: 1, focusNode: {}, focusOffset: 4, time: 1_000 },
        direction: 1,
        focusNode: {},
        focusOffset: 4,
        now: 2_500,
    }), true)
})

// Drives the paginator's drag-turn controller the way Android's native handle
// does: every move rewrites the selection focus from outside, and the page turn
// resolves either after the slide animation or at once (E-Ink, no animation).
function nativeSelectionDrag({ turnDelayMillis }) {
    const dom = new JSDOM('<body>' + Array.from({ length: 8 }, (_, i) => `<p>word${i} alpha beta gamma</p>`).join('') + '</body>')
    const doc = dom.window.document
    const text = [...doc.querySelectorAll('p')].map(p => p.firstChild)
    const pageRange = (first, last) => {
        const range = doc.createRange()
        range.setStart(text[first], 0)
        range.setEnd(text[last], text[last].length)
        return range
    }
    const writes = []
    const state = { anchorNode: text[1], anchorOffset: 2, focusNode: text[2], focusOffset: 3 }
    const sel = {
        rangeCount: 1,
        type: 'Range',
        isCollapsed: false,
        get anchorNode() { return state.anchorNode },
        get anchorOffset() { return state.anchorOffset },
        get focusNode() { return state.focusNode },
        get focusOffset() { return state.focusOffset },
        getRangeAt: () => {
            const range = doc.createRange()
            range.setStart(state.anchorNode, state.anchorOffset)
            range.setEnd(state.focusNode, state.focusOffset)
            return range
        },
    }
    for (const name of ['setBaseAndExtent', 'addRange', 'removeAllRanges', 'collapse', 'extend', 'modify'])
        sel[name] = (...args) => writes.push([name, ...args])

    let clock = 0
    let visible = pageRange(1, 2)
    let page = 2
    const turns = []
    const context = vm.createContext({ document: doc, Range: dom.window.Range })
    vm.runInContext(`${paginator.slice(turnStart, turnEnd)}
globalThis.selectionPageTurner = selectionPageTurner`, context)
    const turner = context.selectionPageTurner({
        doc,
        isLocked: () => false,
        page: () => page,
        pages: () => 12,
        now: () => clock,
        turn: direction => {
            turns.push(direction)
            page += direction
            visible = direction > 0 ? pageRange(3, 4) : pageRange(1, 2)
            return new Promise(resolve => setTimeout(resolve, turnDelayMillis))
        },
    })
    // One native handle move: the focus lands where the finger is, then Blink fires selectionchange.
    const move = (at, node, offset) => {
        clock = at
        state.focusNode = text[node]
        state.focusOffset = offset
        turner.check(visible, sel)
    }
    const settle = () => new Promise(resolve => setTimeout(resolve, turnDelayMillis + 10))
    return { move, settle, turns, writes, state, text }
}

for (const [mode, turnDelayMillis] of [['animated', 30], ['E-Ink (no animation)', 0]]) {
    test(`a handle dragged past the edge turns one page and keeps the native drag alive (${mode})`, async () => {
        const drag = nativeSelectionDrag({ turnDelayMillis })
        drag.move(0, 2, 5)
        drag.move(100, 3, 2) // past the right edge: arms the turn
        drag.move(900, 3, 6) // still pushing after the hold: turns
        assert.deepEqual(drag.turns, [1])

        drag.move(910, 4, 1) // the finger keeps moving while the page changes
        await drag.settle()

        // Holding the handle against the new page's edge must not page again.
        drag.move(1_000, 5, 2)
        drag.move(2_000, 5, 8)
        drag.move(3_500, 5, 12)
        assert.deepEqual(drag.turns, [1], 'one page per edge push')

        // The focus is still wherever the finger put it, and the anchor never moved.
        assert.equal(drag.state.focusNode, drag.text[5])
        assert.equal(drag.state.focusOffset, 12)
        assert.equal(drag.state.anchorNode, drag.text[1])
        assert.equal(drag.state.anchorOffset, 2)
        assert.deepEqual(drag.writes, [], 'a scripted selection change hides Android handles and ends the drag')
    })

    test(`after retreating into the new page a renewed edge push turns once more (${mode})`, async () => {
        const drag = nativeSelectionDrag({ turnDelayMillis })
        drag.move(0, 2, 5)
        drag.move(100, 3, 2)
        drag.move(900, 3, 6)
        await drag.settle()
        drag.move(1_000, 5, 4) // still past the edge
        drag.move(1_100, 4, 7) // back inside the new page
        drag.move(1_200, 5, 2) // edge again: arms
        drag.move(2_200, 5, 9) // pushes on after the hold and the turn throttle
        assert.deepEqual(drag.turns, [1, 1])
        assert.deepEqual(drag.writes, [])
    })
}

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const wireStart = bridge.indexOf('const SelectionDragSettleMillis')
const wireEnd = bridge.indexOf('function postSelection', wireStart)
assert.ok(wireStart >= 0 && wireEnd > wireStart, 'selection event wiring not found')

test('the selection card waits for touchend after Android cancels the pointer', () => {
    const listeners = new Map()
    const viewListeners = new Map()
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
        defaultView: { addEventListener: (name, listener) => viewListeners.set(name, listener) },
    }
    const posts = []
    const scheduled = []
    const context = vm.createContext({
        clearTimeout: () => {},
        hideAnnotationCard: () => {},
        post: (...args) => posts.push(args),
        postSelection: (...args) => posts.push(['postSelection', ...args]),
        publishedSelectionDocument: null,
        selectionTimers: new Map(),
        setTimeout: callback => {
            scheduled.push(callback)
            return scheduled.length
        },
    })
    vm.runInContext(bridge.slice(wireStart, wireEnd), context)
    context.wireSelection(doc, 3)

    listeners.get('pointerdown')()
    listeners.get('touchstart')()
    listeners.get('pointercancel')()
    listeners.get('selectionchange')()
    assert.equal(scheduled.length, 0, 'pointer cancellation must not publish while touch remains active')

    listeners.get('touchend')()
    assert.equal(scheduled.length, 1)
    scheduled[0]()
    assert.deepEqual(posts, [['postSelection', doc, 3]])
})

test('touching selection handles hides the old card until the range is released', () => {
    const listeners = new Map()
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
        defaultView: { addEventListener: () => {} },
    }
    const posts = []
    const scheduled = []
    const context = vm.createContext({
        clearTimeout: () => {},
        hideAnnotationCard: () => {},
        post: (...args) => posts.push(args),
        postSelection: (...args) => posts.push(['postSelection', ...args]),
        publishedSelectionDocument: doc,
        selectionTimers: new Map(),
        setTimeout: callback => {
            scheduled.push(callback)
            return scheduled.length
        },
    })
    vm.runInContext(bridge.slice(wireStart, wireEnd), context)
    context.wireSelection(doc, 4)

    listeners.get('pointerdown')()
    listeners.get('touchstart')()
    assert.deepEqual(posts, [['selection', null]])
    listeners.get('pointerup')()
    assert.equal(scheduled.length, 0)
    listeners.get('touchend')()
    scheduled[0]()
    assert.deepEqual(posts, [
        ['selection', null],
        ['postSelection', doc, 4],
    ])
})

test('native handle range changes hide the card and wait for selection stability', () => {
    const listeners = new Map()
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
        defaultView: { addEventListener: () => {} },
    }
    const posts = []
    const scheduled = []
    const context = vm.createContext({
        clearTimeout: () => {},
        hideAnnotationCard: () => {},
        post: (...args) => posts.push(args),
        postSelection: (...args) => posts.push(['postSelection', ...args]),
        publishedSelectionDocument: doc,
        selectionTimers: new Map(),
        setTimeout: (callback, delay) => {
            scheduled.push({ callback, delay })
            return scheduled.length
        },
    })
    vm.runInContext(bridge.slice(wireStart, wireEnd), context)
    context.wireSelection(doc, 5)

    listeners.get('selectionchange')()
    assert.deepEqual(posts, [['selection', null]])
    assert.equal(scheduled.length, 1)
    assert.equal(scheduled[0].delay, 600)

    scheduled[0].callback()
    assert.deepEqual(posts, [
        ['selection', null],
        ['postSelection', doc, 5],
    ])
})

test('duplicate native selectionchange events do not hide a stable card or restart its delay', () => {
    const listeners = new Map()
    const node = {}
    const selection = {
        rangeCount: 1,
        anchorNode: node,
        anchorOffset: 2,
        focusNode: node,
        focusOffset: 9,
        isCollapsed: false,
    }
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
        getSelection: () => selection,
        defaultView: { addEventListener: () => {} },
    }
    const posts = []
    const scheduled = []
    const context = vm.createContext({
        selectionTimers: new Map(),
        publishedSelectionDocument: doc,
        hideAnnotationCard: () => {},
        post: (...args) => posts.push(args),
        postSelection: () => {},
        clearTimeout: () => {},
        setTimeout: (callback, delay) => { scheduled.push({ callback, delay }); return scheduled.length },
    })
    vm.runInContext(bridge.slice(wireStart, wireEnd), context)
    context.wireSelection(doc, 0)

    listeners.get('touchstart')()
    listeners.get('touchend')()
    const scheduledAfterRelease = scheduled.length
    listeners.get('selectionchange')()
    listeners.get('selectionchange')()

    assert.equal(scheduled.length, scheduledAfterRelease)
    assert.deepEqual(posts, [['selection', null]])
})

test('a collapsed caret move after a forgiving highlight tap keeps its annotation card open', () => {
    const listeners = new Map()
    const node = {}
    const selection = {
        rangeCount: 1,
        anchorNode: node,
        anchorOffset: 4,
        focusNode: node,
        focusOffset: 4,
        isCollapsed: true,
    }
    const doc = {
        addEventListener: (name, listener) => listeners.set(name, listener),
        getSelection: () => selection,
        defaultView: { addEventListener: () => {} },
    }
    let annotationHides = 0
    const scheduled = []
    const context = vm.createContext({
        selectionTimers: new Map(),
        publishedSelectionDocument: null,
        hideAnnotationCard: () => { annotationHides++ },
        post: () => {},
        postSelection: () => {},
        clearTimeout: () => {},
        setTimeout: (callback, delay) => { scheduled.push({ callback, delay }); return scheduled.length },
    })
    vm.runInContext(bridge.slice(wireStart, wireEnd), context)
    context.wireSelection(doc, 0)

    listeners.get('selectionchange')()

    assert.equal(annotationHides, 0)
    assert.equal(scheduled.length, 1)
    assert.equal(scheduled[0].delay, 600)
})

const restoreStart = bridge.indexOf('async function restoreAnnotationsForOverlay')
const restoreEnd = bridge.indexOf("// 'relocate' can fire", restoreStart)
assert.ok(restoreStart >= 0 && restoreEnd > restoreStart, 'overlay annotation restore not found')

test('a replacement overlay redraws standard and authoritative community marks', async () => {
    const doc = {}
    const added = []
    let badgeLayouts = 0
    const activeAnnotationsList = [
        { value: 'epubcfi(/6/2!/4/2)', type: 'highlight' },
        { value: 'goodreads-quote:one', type: 'underline', popular: true },
        { value: 'goodreads-quote:duplicate', type: 'underline', popular: true },
        { value: 'epubcfi(/6/4!/4/2)', type: 'highlight' },
    ]
    const resolvedTextAnnotations = new Map([
        ['goodreads-quote:one', 'epubcfi(/6/2!/4/4)'],
        ['goodreads-quote:duplicate', 'epubcfi(/6/2!/4/4)'],
    ])
    const authoritativeSourceForCfi = new Map([
        ['epubcfi(/6/2!/4/4)', 'goodreads-quote:one'],
    ])
    const view = {
        renderer: { getContents: () => [{ doc, index: 0, overlayer: {} }] },
        resolveCFI: cfi => ({ index: cfi.includes('/6/4!') ? 1 : 0 }),
        addAnnotation: annotation => {
            added.push(annotation)
            return { index: 0, drawn: true }
        },
    }
    const context = vm.createContext({
        activeAnnotationsList,
        annotationSectionCache: new WeakMap(),
        authoritativeSourceForCfi,
        isTextAnnotationValue: value => value.startsWith('goodreads-quote:'),
        popularBadgeIndexDirty: false,
        Promise,
        resolvedTextAnnotations,
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => { badgeLayouts++ },
        Set,
        view,
    })
    vm.runInContext(bridge.slice(restoreStart, restoreEnd), context)
    await context.restoreAnnotationsForOverlay(doc, 0)

    assert.deepEqual(added.map(annotation => annotation.value), [
        'epubcfi(/6/2!/4/2)',
        'epubcfi(/6/2!/4/4)',
    ])
    assert.equal(badgeLayouts, 1)
})

test('chapter changes resolve annotation sections once per snapshot rather than once per overlay', async () => {
    let doc = {}, index = 0, resolves = 0, draws = 0
    const annotations = Array.from({ length: 1000 }, (_, id) => ({ value: `mark:${id}`, editable: id % 2 === 0 }))
    const view = {
        renderer: { getContents: () => [{ doc, index, overlayer: {} }] },
        resolveCFI: cfi => { resolves++; return { index: Number(cfi.split(':')[1]) % 20 } },
        addAnnotation: () => { draws++ },
    }
    const context = vm.createContext({ view, activeAnnotationsList: annotations,
        annotationSectionCache: new WeakMap(), isTextAnnotationValue: () => false,
        resolvedTextAnnotations: new Map(), authoritativeSourceForCfi: new Map(),
        resetPopularBadgeResolutionRetries() {}, scheduleBadgeLayout() {},
    })
    vm.runInContext(bridge.slice(restoreStart, restoreEnd), context)
    for (index = 0; index < 20; index++) {
        doc = {}
        await context.restoreAnnotationsForOverlay(doc, index)
    }
    assert.equal(draws, 1000, 'all marks still draw in their own chapters')
    assert.equal(resolves, 1000, '1000 annotations across 20 chapters need 1000 resolutions, rather than 20000')

    context.activeAnnotationsList = annotations.map(annotation => ({ ...annotation }))
    doc = {}
    index = 0
    await context.restoreAnnotationsForOverlay(doc, index)
    assert.equal(resolves, 1000, 'a fresh snapshot of the same marks reuses resolved sections')

    context.view = { ...view }
    index = 0
    await context.restoreAnnotationsForOverlay(doc, index)
    assert.equal(resolves, 2000, 'another book view cannot inherit cached section indexes')
})

test('overlay section caching retries resolution misses and follows changed quote CFIs', async () => {
    const doc = {}, quote = { value: 'quote:1' }
    let ready = false, index = 0, resolves = 0
    const drawn = []
    const context = vm.createContext({
        activeAnnotationsList: [quote], annotationSectionCache: new WeakMap(),
        resolvedTextAnnotations: new Map([['quote:1', 'mark:0']]), authoritativeSourceForCfi: new Map(),
        isTextAnnotationValue: value => value.startsWith('quote:'),
        resetPopularBadgeResolutionRetries() {}, scheduleBadgeLayout() {},
        view: {
            renderer: { getContents: () => [{ doc, index, overlayer: {} }] },
            resolveCFI: cfi => { resolves++; return ready ? { index: Number(cfi.split(':')[1]) } : null },
            addAnnotation: annotation => { drawn.push(annotation.value) },
        },
    })
    vm.runInContext(bridge.slice(restoreStart, restoreEnd), context)
    await context.restoreAnnotationsForOverlay(doc, 0)
    assert.deepEqual(drawn, [])
    ready = true
    await context.restoreAnnotationsForOverlay(doc, 0)
    assert.equal(resolves, 2, 'a missing resolution is not cached')
    context.resolvedTextAnnotations.set('quote:1', 'mark:1')
    index = 1
    await context.restoreAnnotationsForOverlay(doc, 1)
    assert.deepEqual(drawn, ['mark:0', 'mark:1'])
    assert.equal(resolves, 3)
})
