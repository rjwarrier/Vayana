import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'
import vm from 'node:vm'

const paginator = await readFile(new URL('../src/main/assets/foliate/paginator.js', import.meta.url), 'utf8')
const turnStart = paginator.indexOf('const touchEventHasSelection')
const turnEnd = paginator.indexOf('const setSelectionTo', turnStart)
assert.ok(turnStart >= 0 && turnEnd > turnStart, 'selection page-turn policy not found')

const turnContext = vm.createContext({})
vm.runInContext(
    `${paginator.slice(turnStart, turnEnd)}\n` +
        `globalThis.selectionPageTurnDirection = selectionPageTurnDirection;\n` +
        `globalThis.selectionPageTurnAllowed = selectionPageTurnAllowed;\n` +
        `globalThis.selectionPageTurnRetreated = selectionPageTurnRetreated;\n` +
        `globalThis.selectionPageTurnIntent = selectionPageTurnIntent;\n` +
        `globalThis.selectionPageEntryFocus = selectionPageEntryFocus;\n` +
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
        'the automatic entry clamp is not fresh user intent')
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

test('a forward page turn selects only the first few words of the new page', () => {
    const dom = new JSDOM('<p><span>one two</span> <em>three four</em> five six</p>')
    const doc = dom.window.document
    const range = doc.createRange()
    range.selectNodeContents(doc.querySelector('p'))

    const focus = turnContext.selectionPageEntryFocus(range, 1)

    assert.equal(focus.node.data, 'three four')
    assert.equal(focus.offset, 5)
})

test('a backward page turn selects only the last few words of the new page', () => {
    const dom = new JSDOM('<p><span>one two</span> <em>three four</em> five six</p>')
    const doc = dom.window.document
    const range = doc.createRange()
    range.selectNodeContents(doc.querySelector('p'))

    const focus = turnContext.selectionPageEntryFocus(range, -1)

    assert.equal(focus.node.data, 'three four')
    assert.equal(focus.offset, 6)
})

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
