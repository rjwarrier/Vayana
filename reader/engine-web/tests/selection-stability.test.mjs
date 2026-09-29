import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import vm from 'node:vm'

const paginator = await readFile(new URL('../src/main/assets/foliate/paginator.js', import.meta.url), 'utf8')
const turnStart = paginator.indexOf('const selectionPageTurnDirection')
const turnEnd = paginator.indexOf('const setSelectionTo', turnStart)
assert.ok(turnStart >= 0 && turnEnd > turnStart, 'selection page-turn policy not found')

const turnContext = vm.createContext({})
vm.runInContext(
    `${paginator.slice(turnStart, turnEnd)}\nglobalThis.selectionPageTurnDirection = selectionPageTurnDirection`,
    turnContext,
)

const direction = overrides => turnContext.selectionPageTurnDirection({
    backward: false,
    startsBeforePage: false,
    endsAfterPage: false,
    turnedBackward: false,
    turnedForward: false,
    atSectionStart: false,
    atSectionEnd: false,
    ...overrides,
})

test('a forward handle can cross one page but cannot run away across more pages', () => {
    assert.equal(direction({ endsAfterPage: true }), 1)
    assert.equal(direction({ endsAfterPage: true, turnedForward: true }), 0)
})

test('the same drag may reverse one page so the user can correct the range', () => {
    assert.equal(direction({ backward: true, startsBeforePage: true, turnedForward: true }), -1)
    assert.equal(direction({ backward: true, startsBeforePage: true, turnedBackward: true }), 0)
})

test('selection never auto-turns across an EPUB document boundary', () => {
    assert.equal(direction({ endsAfterPage: true, atSectionEnd: true }), 0)
    assert.equal(direction({ backward: true, startsBeforePage: true, atSectionStart: true }), 0)
})

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
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
