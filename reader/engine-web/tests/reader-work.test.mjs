import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')

test('warm community badge layout visits only loaded-section candidates', () => {
    const { window } = new JSDOM('<body></body>')
    const operations = { scans: 0, parses: 0, resolves: 0, frameReads: 0 }
    const frame = { getBoundingClientRect: () => { operations.frameReads++; return { left: 30, top: 0 } } }
    const doc = { defaultView: { frameElement: frame } }
    const annotations = Array.from({ length: 10_000 }, (_, id) => ({
        get popular() { operations.scans++; return true },
        value: `epubcfi(${id})`, note: '12 highlights', color: '#111111',
    }))
    const context = vm.createContext({
        fixedLayout: false,
        document: window.document,
        window,
        requestAnimationFrame: callback => callback(),
        activeAnnotationsList: annotations,
        resolvedTextAnnotations: new Map(),
        DefaultAnnotationColor: '#111111',
        markColor: color => color,
        inkMarks: false,
        highlightCount: () => { operations.parses++; return 12 },
        view: {
            renderer: { scrolled: false, size: 400, start: 400, getContents: () => [{ index: 0, doc }] },
            resolveCFI: cfi => {
                operations.resolves++
                return {
                    index: Number(cfi.match(/\d+/)[0]) % 100,
                    anchor: () => ({ getClientRects: () => [{ left: 40, top: 10, width: 100, height: 20 }] }),
                }
            },
        },
    })
    vm.runInContext(bridge.slice(bridge.indexOf('function popularBadgePlacement'), bridge.indexOf("window.addEventListener('resize', scheduleBadgeLayout)")), context)

    vm.runInContext('layoutPopularBadges()', context)
    assert.equal(operations.scans, 10_000)
    assert.equal(window.document.body.querySelectorAll('div > div').length, 100)

    for (const key of Object.keys(operations)) operations[key] = 0
    vm.runInContext('layoutPopularBadges()', context)
    assert.deepEqual(operations, { scans: 0, parses: 0, resolves: 0, frameReads: 1 })
    assert.equal(window.document.body.querySelectorAll('div > div').length, 100)
})

test('community badge CFI resolution automatically retries after a transient renderer miss', async () => {
    const { window } = new JSDOM('<body></body>')
    const doc = { defaultView: { frameElement: { getBoundingClientRect: () => ({ left: 30, top: 0 }) } } }
    let resolveAttempts = 0
    const context = vm.createContext({
        fixedLayout: false,
        document: window.document,
        window,
        requestAnimationFrame: callback => callback(),
        activeAnnotationsList: [{ popular: true, value: 'epubcfi(/6/2)', note: '12 highlights', color: '#111111' }],
        resolvedTextAnnotations: new Map(),
        DefaultAnnotationColor: '#111111',
        markColor: color => color,
        inkMarks: false,
        highlightCount: () => 12,
        view: {
            renderer: { scrolled: false, size: 400, start: 400, getContents: () => [{ index: 0, doc }] },
            resolveCFI: () => {
                resolveAttempts++
                if (resolveAttempts === 1) return null
                return {
                    index: 0,
                    anchor: () => ({ getClientRects: () => [{ left: 40, top: 10, width: 100, height: 20 }] }),
                }
            },
        },
    })
    vm.runInContext(bridge.slice(bridge.indexOf('function popularBadgePlacement'), bridge.indexOf("window.addEventListener('resize', scheduleBadgeLayout)")), context)

    vm.runInContext('layoutPopularBadges()', context)
    assert.equal(window.document.body.querySelectorAll('div > div').length, 0)

    await new Promise(resolve => window.setTimeout(resolve, 80))
    assert.equal(resolveAttempts, 2)
    assert.equal(window.document.body.querySelectorAll('div > div').length, 1)
})

test('warm page statistics reuse section totals and the contents map', () => {
    const sections = Array.from({ length: 1_000 }, () => ({ linear: 'yes', size: 10_000 }))
    let contentsVisits = 0
    const context = vm.createContext({
        fixedLayout: false,
        view: { renderer: { pages: 12, page: 2 }, book: { sections } },
        sectionByteSizes: null,
        bytesPerPage: new Map(),
        pageEstimateCache: null,
        pageEstimateRevision: 0,
        tocSectionIndexes: function* () {
            for (let index = 0; index < sections.length; index++) {
                contentsVisits++
                yield [`chapter-${index}`, index]
            }
        },
    })
    vm.runInContext(bridge.slice(bridge.indexOf('function bookPageStats'), bridge.indexOf('// TOC href -> section index')), context)
    const first = vm.runInContext('bookPageStats(500)', context)
    assert.equal(contentsVisits, 1_000)
    context.view.renderer.page = 3
    const second = vm.runInContext('bookPageStats(500)', context)
    assert.equal(contentsVisits, 1_000)
    assert.equal(second.tocPages, first.tocPages)
    assert.equal(second.tocRevision, first.tocRevision)
    assert.equal(second.currentPage, first.currentPage + 1)
    context.view.renderer.pages = 24
    const changed = vm.runInContext('bookPageStats(500)', context)
    assert.equal(contentsVisits, 2_000)
    assert.notEqual(changed.tocRevision, second.tocRevision)
})

test('cached page statistics preserve the original estimates across sampled sections', () => {
    const sizes = Array.from({ length: 50 }, (_, index) => index % 11 === 0 ? 0 : 901 + index * 79)
    const sections = sizes.map(size => ({ linear: size ? 'yes' : 'no', size }))
    const samples = new Map()
    const context = vm.createContext({
        fixedLayout: false,
        view: { renderer: { pages: 9, page: 4 }, book: { sections } },
        sectionByteSizes: null,
        bytesPerPage: new Map(),
        pageEstimateCache: null,
        pageEstimateRevision: 0,
        tocSectionIndexes: () => sizes.map((_, index) => [`chapter-${index}`, index]),
    })
    vm.runInContext(bridge.slice(bridge.indexOf('function bookPageStats'), bridge.indexOf('// TOC href -> section index')), context)
    for (const sectionIndex of [0, 1, 16, 17, 28, 49, 28]) {
        const pages = 5 + sectionIndex % 13
        context.view.renderer.pages = pages
        context.view.renderer.page = Math.min(4, pages - 1)
        const pagesInSection = Math.max(1, pages - 2)
        if (sizes[sectionIndex]) samples.set(sectionIndex, sizes[sectionIndex] / pagesInSection)
        let knownSize = 0; let knownPages = 0
        for (const [index, bpp] of samples) {
            knownSize += sizes[index]
            knownPages += sizes[index] / bpp
        }
        const average = knownPages ? knownSize / knownPages : (sizes[sectionIndex] / pagesInSection || 1600)
        const estimate = (from, to) => sizes.slice(from, to).reduce((total, size, offset) =>
            total + (size ? size / (samples.get(from + offset) ?? average) : 0), 0)
        const expectedCurrent = Math.round(estimate(0, sectionIndex)) + Math.min(pagesInSection, Math.max(1, context.view.renderer.page - 1))
        const expectedTotal = Math.round(estimate(0, sectionIndex)) + pagesInSection + Math.round(estimate(sectionIndex + 1, sizes.length))
        const actual = vm.runInContext(`bookPageStats(${sectionIndex})`, context)
        assert.equal(actual.currentPage, expectedCurrent)
        assert.equal(actual.totalPages, expectedTotal)
        for (let index = 0; index < sizes.length; index++) {
            assert.equal(actual.tocPages[`chapter-${index}`], Math.round(estimate(0, index)) + 1)
        }
    }
})

test('annotation rendering applies the newest waiting snapshot after an in-flight update', async () => {
    let releaseFirst
    const firstAdd = new Promise(resolve => { releaseFirst = resolve })
    const operations = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => {
                operations.push(`add:${annotation.value}`)
                if (annotation.value === 'a') await firstAdd
            },
            deleteAnnotation: async annotation => { operations.push(`delete:${annotation.value}`) },
            renderer: { getContents: () => [] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingQuoteAdds: new Set(),
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: () => false,
        matchTextAnnotationsForDoc: () => {},
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function matchTextAnnotationsForDoc')), context)
    const annotation = value => ({ value, type: 'highlight', color: '#111111' })
    context.renderAnnotations([annotation('a')])
    context.renderAnnotations([annotation('b')])
    context.renderAnnotations([annotation('c')])
    releaseFirst()
    for (let turn = 0; turn < 10; turn++) await Promise.resolve()
    assert.deepEqual(operations, ['add:a', 'delete:a', 'add:c'])
    assert.deepEqual(Array.from(context.renderedAnnotations), ['c'])
})

test('a late quote match cannot restore an annotation removed by a newer snapshot', async () => {
    let releaseAdd
    const pendingAdd = new Promise(resolve => { releaseAdd = resolve })
    const operations = []
    const doc = {}
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => { operations.push(`add:${annotation.value}`); await pendingAdd },
            deleteAnnotation: async annotation => { operations.push(`delete:${annotation.value}`) },
            getCFI: () => 'epubcfi(/6/2)',
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: () => true,
        findTextRangeInDoc: () => ({}),
        rangeOverlapRatio: () => 0,
        highlightCount: () => 1,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)
    context.renderAnnotations([{ value: 'quote:1', text: 'A long quote', type: 'underline' }])
    for (let turn = 0; turn < 5; turn++) await Promise.resolve()
    context.renderAnnotations([])
    for (let turn = 0; turn < 5; turn++) await Promise.resolve()
    releaseAdd()
    for (let turn = 0; turn < 50; turn++) await Promise.resolve()
    assert.deepEqual(operations, ['add:epubcfi(/6/2)', 'delete:epubcfi(/6/2)'])
    assert.equal(context.renderedAnnotations.size, 0)
    assert.equal(context.resolvedTextAnnotations.size, 0)
})

test('editing a missing quote or its matching mode retries in the loaded document', async () => {
    const doc = {}
    const added = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => { added.push(annotation.value) },
            deleteAnnotation: async () => {},
            getCFI: () => 'epubcfi(/6/4)',
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: () => true,
        findTextRangeInDoc: (_, text, options) => !options?.exactOnly && text === 'Edited passage' ? {} : null,
        rangeOverlapRatio: () => 0,
        highlightCount: () => 1,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)
    context.renderAnnotations([{ value: 'quote:1', text: 'Missing passage' }])
    for (let turn = 0; turn < 10; turn++) await Promise.resolve()
    context.renderAnnotations([{ value: 'quote:1', text: 'Edited passage' }])
    for (let turn = 0; turn < 30; turn++) await Promise.resolve()
    assert.deepEqual(added, ['epubcfi(/6/4)'])
    assert.equal(context.resolvedTextAnnotations.get('quote:1'), 'epubcfi(/6/4)')
    context.renderAnnotations([{ value: 'quote:2', text: 'Edited passage', popular: true }])
    for (let turn = 0; turn < 30; turn++) await Promise.resolve()
    assert.equal(context.resolvedTextAnnotations.has('quote:2'), false)
    context.renderAnnotations([{ value: 'quote:2', text: 'Edited passage', popular: false }])
    for (let turn = 0; turn < 30; turn++) await Promise.resolve()
    assert.equal(context.resolvedTextAnnotations.get('quote:2'), 'epubcfi(/6/4)')
})

test('CFI-backed user highlights bypass quote matching and are rendered only once', async () => {
    const doc = {}
    const added = []
    const matched = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => { added.push(annotation.value) },
            deleteAnnotation: async () => {},
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: value => value.startsWith('quote:'),
        findTextRangeInDoc: (_, text) => { matched.push(text); return null },
        rangeOverlapRatio: () => 0,
        highlightCount: () => 1,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)

    context.renderAnnotations([
        { value: 'epubcfi(/6/2)', text: 'My selected passage', type: 'highlight', color: '#111111' },
        { value: 'quote:1', text: 'Imported community passage', type: 'underline' },
    ])
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()

    assert.deepEqual(added, ['epubcfi(/6/2)'])
    assert.deepEqual(matched, ['Imported community passage'])
})

test('a community quote resolving to a personal highlight CFI does not replace it', async () => {
    const doc = {}
    const added = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => { added.push(annotation.value); return { drawn: true } },
            deleteAnnotation: async () => {},
            getCFI: () => 'epubcfi(/6/2)',
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: value => value.startsWith('quote:'),
        findTextRangeInDoc: () => ({}),
        rangeOverlapRatio: () => 0,
        highlightCount: () => 4,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)

    context.renderAnnotations([
        { value: 'epubcfi(/6/2)', text: 'My selected passage', type: 'highlight', color: '#111111' },
        { value: 'quote:1', text: 'My selected passage', type: 'underline', popular: true, note: '4 readers' },
    ])
    for (let turn = 0; turn < 30; turn++) await Promise.resolve()

    assert.deepEqual(added, ['epubcfi(/6/2)'])
    assert.equal(context.resolvedTextAnnotations.get('quote:1'), 'epubcfi(/6/2)')
    assert.equal(context.authoritativeSourceForCfi.has('epubcfi(/6/2)'), false)

    // Removing the personal mark releases that CFI so the informational community underline can return.
    context.renderAnnotations([
        { value: 'quote:1', text: 'My selected passage', type: 'underline', popular: true, note: '4 readers' },
    ])
    for (let turn = 0; turn < 30; turn++) await Promise.resolve()

    assert.deepEqual(added, ['epubcfi(/6/2)', 'epubcfi(/6/2)'])
    assert.equal(context.authoritativeSourceForCfi.get('epubcfi(/6/2)'), 'quote:1')
})

test('an editable highlight renders when a newer note has the exact same CFI', async () => {
    const added = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => { added.push(annotation) },
            deleteAnnotation: async () => {},
            renderer: { getContents: () => [] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: () => false,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)
    const sharedCfi = 'epubcfi(/6/2)'

    context.renderAnnotations([
        { id: 'newer-note', value: sharedCfi, type: 'highlight', editable: false, color: '#222222' },
        { id: 'editable-highlight', value: sharedCfi, type: 'highlight', editable: true, color: '#111111' },
    ])
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()

    assert.deepEqual(added.map(annotation => annotation.id), ['editable-highlight'])
})

test('adding a user highlight preserves unchanged community-quote miss results', async () => {
    const doc = {}
    let matchAttempts = 0
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async () => {},
            deleteAnnotation: async () => {},
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: value => value.startsWith('quote:'),
        findTextRangeInDoc: () => { matchAttempts++; return null },
        rangeOverlapRatio: () => 0,
        highlightCount: () => 1,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)
    const quote = { value: 'quote:1', text: 'A passage absent from this chapter', type: 'underline' }

    context.renderAnnotations([quote])
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()
    context.renderAnnotations([
        quote,
        { value: 'epubcfi(/6/8)', text: 'My new highlight', type: 'highlight', color: '#111111' },
    ])
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()

    assert.equal(matchAttempts, 1)
})

test('a community quote stays retryable when its overlay is not attached yet', async () => {
    const doc = {}
    let overlayAttached = false
    const addAttempts = []
    const context = vm.createContext({
        fixedLayout: false,
        view: {
            addAnnotation: async annotation => {
                addAttempts.push(annotation.value)
                return { drawn: overlayAttached }
            },
            deleteAnnotation: async () => {},
            getCFI: () => 'epubcfi(/6/8)',
            renderer: { getContents: () => [{ doc, index: 0 }] },
        },
        annotationRevision: 0,
        popularBadgeIndexDirty: false,
        resolvedTextAnnotations: new Map(),
        resolvedTextFingerprints: new Map(),
        authoritativeSourceForCfi: new Map(),
        renderedAnnotations: new Set(),
        standardAnnotationFingerprints: new Map(),
        pendingTextAnnotations: new Set(),
        pendingQuoteAdds: new Set(),
        unmatchedInDoc: new WeakMap(),
        completedMatchingRevision: new WeakMap(),
        DefaultAnnotationColor: '#111111',
        resetPopularBadgeResolutionRetries: () => {},
        scheduleBadgeLayout: () => {},
        isTextAnnotationValue: () => true,
        findTextRangeInDoc: () => ({}),
        rangeOverlapRatio: () => 0.9,
        highlightCount: () => 8,
        post: () => {},
    })
    vm.runInContext(bridge.slice(bridge.indexOf('let activeAnnotationsList'), bridge.indexOf('function highlightCount')), context)
    const quote = { value: 'quote:1', text: 'A community passage', type: 'underline', popular: true }
    const duplicate = { ...quote, value: 'quote:2' }

    context.renderAnnotations([quote, duplicate])
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()
    assert.equal(context.resolvedTextAnnotations.has('quote:1'), false)
    assert.equal(context.resolvedTextAnnotations.has('quote:2'), false, 'a duplicate must not publish a badge before its underline is drawn')

    overlayAttached = true
    context.matchTextAnnotationsForDoc(doc, 0)
    for (let turn = 0; turn < 20; turn++) await Promise.resolve()

    assert.deepEqual(addAttempts, ['epubcfi(/6/8)', 'epubcfi(/6/8)'])
    assert.equal(context.resolvedTextAnnotations.get('quote:1'), 'epubcfi(/6/8)')
    assert.equal(context.resolvedTextAnnotations.get('quote:2'), 'epubcfi(/6/8)')
    assert.equal(context.renderedAnnotations.has('epubcfi(/6/8)'), true)
})
