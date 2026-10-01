import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import vm from 'node:vm'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('function annotationForRenderedValue')
const end = bridge.indexOf('let pendingAnnotations', start)
assert.ok(start >= 0 && end > start, 'annotation tap helpers not found')

function harness({ selectionCollapsed = true } = {}) {
    const posts = []
    const context = vm.createContext({
        authoritativeSourceForCfi: new Map(),
        resolvedTextAnnotations: new Map(),
        publishedAnnotationId: null,
        post: (type, payload) => posts.push([type, payload]),
        window: { innerHeight: 1000 },
    })
    vm.runInContext(
        `${bridge.slice(start, end)}\n` +
            `globalThis.setAnnotations = value => { activeAnnotationsList = value };\n` +
            `globalThis.postAnnotationTap = postAnnotationTap`,
        context,
    )
    context.setAnnotations([{ id: '42', value: 'epubcfi(/6/4!/4/2)', type: 'highlight', editable: true }])
    const doc = {
        getSelection: () => ({ rangeCount: 1, isCollapsed: selectionCollapsed }),
        defaultView: { frameElement: { getBoundingClientRect: () => ({ top: 100 }) } },
    }
    const rects = [
        { left: 10, right: 110, top: 20, bottom: 40 },
        { left: 10, right: 110, top: 200, bottom: 225 },
    ]
    const range = {
        startContainer: { ownerDocument: doc },
        getClientRects: () => rects,
        getBoundingClientRect: () => rects[0],
    }
    return { context, posts, range }
}

test('tapping a rendered highlight posts its id and the tapped line anchor', () => {
    const { context, posts, range } = harness()
    context.postAnnotationTap({
        value: 'epubcfi(/6/4!/4/2)',
        range,
        clientX: 30,
        clientY: 210,
    })
    assert.equal(posts.length, 1)
    assert.equal(posts[0][0], 'annotationTapped')
    assert.equal(posts[0][1].annotationId, '42')
    assert.equal(posts[0][1].top, 0.3)
    assert.equal(posts[0][1].bottom, 0.325)
})

test('releasing an active text selection over a highlight does not open its edit card', () => {
    const { context, posts, range } = harness({ selectionCollapsed: false })
    context.postAnnotationTap({ value: 'epubcfi(/6/4!/4/2)', range, clientX: 30, clientY: 30 })
    assert.deepEqual(posts, [])
})

test('a forgiving tap beside a later highlight line anchors the card to that line', () => {
    const { context, posts, range } = harness()
    context.postAnnotationTap({
        value: 'epubcfi(/6/4!/4/2)',
        range,
        clientX: 30,
        clientY: 232,
    })
    assert.equal(posts.length, 1)
    assert.equal(posts[0][1].top, 0.3)
    assert.equal(posts[0][1].bottom, 0.325)
})

test('an editable highlight owns a CFI shared with a newer note', () => {
    const { context, posts, range } = harness()
    context.setAnnotations([
        { id: '99', value: 'epubcfi(/6/4!/4/2)', type: 'highlight', editable: false },
        { id: '42', value: 'epubcfi(/6/4!/4/2)', type: 'highlight', editable: true },
    ])

    context.postAnnotationTap({ value: 'epubcfi(/6/4!/4/2)', range, clientX: 30, clientY: 30 })

    assert.equal(posts[0][1].annotationId, '42')
})
