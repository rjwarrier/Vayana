import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

// Runs the real markSpeech / clearSpeechMark from bridge.js against foliate's own Overlayer and a stubbed view.
const overlayerSource = (await readFile(new URL('../src/main/assets/foliate/overlayer.js', import.meta.url), 'utf8'))
    .replace('export class Overlayer', 'class Overlayer')
const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const start = bridge.indexOf('const SpeechHighlightColor')
const end = bridge.indexOf('const MaxChapterWordKinds')
assert.ok(start > 0 && end > start, 'speech code not found in bridge.js')

function setup({ lastLocation = null } = {}) {
    const { window } = new JSDOM('<!doctype html><body></body>')
    const context = vm.createContext({ document: window.document, NodeFilter: window.NodeFilter, Intl })
    vm.runInContext(overlayerSource + '\nvar view = null\nvar segmenterFor = () => null', context)
    vm.runInContext(bridge.slice(start, end), context)
    const overlayer = vm.runInContext('new Overlayer()', context)
    context.overlayer = overlayer
    context.turns = 0
    context.lastLocation = lastLocation
    vm.runInContext(`
        view = {
            renderer: { getContents: () => [{ index: 3, overlayer }] },
            get lastLocation() { return lastLocation },
            next: async () => { turns++ },
        }
        speech.index = 3
    `, context)
    return { context, overlayer }
}

const rangeAt = left => ({
    getClientRects: () => [{ left, top: 20, width: 50, height: 16 }],
    startContainer: { ownerDocument: 'doc' },
    startOffset: 0,
})

function registerSentence(context, range) {
    context.range = range
    vm.runInContext("speech.sentences.set('3:0', { range, rangeForOffsets: () => range })", context)
}

test('marking a word draws one highlight in the speech colour on the section overlay', async () => {
    const { context, overlayer } = setup()
    registerSentence(context, rangeAt(10))

    await vm.runInContext("markSpeech('3:0', 0, 3)", context)

    assert.equal(overlayer.element.querySelectorAll('rect').length, 1)
    assert.match(overlayer.element.querySelector('g').style.fill, /#5b8def|rgb\(91, 141, 239\)/i)
})

test('marking the next word replaces the previous mark instead of stacking', async () => {
    const { context, overlayer } = setup()
    for (const left of [10, 70, 130]) {
        registerSentence(context, rangeAt(left))
        await vm.runInContext("markSpeech('3:0', 0, 3)", context)
    }
    assert.equal(overlayer.element.querySelectorAll('rect').length, 1)
    assert.equal(overlayer.element.querySelector('rect').getAttribute('x'), '130')
})

test('clearing removes the mark, and clearing again is harmless', async () => {
    const { context, overlayer } = setup()
    registerSentence(context, rangeAt(10))
    await vm.runInContext("markSpeech('3:0', 0, 3)", context)

    vm.runInContext('clearSpeechMark(); clearSpeechMark()', context)

    assert.equal(overlayer.element.querySelectorAll('rect').length, 0)
    assert.equal(vm.runInContext('speech.markedOverlayer', context), null)
})

test('a sentence of an unloaded section is ignored without touching any overlay', async () => {
    const { context, overlayer } = setup()
    registerSentence(context, rangeAt(10))
    vm.runInContext('speech.index = 9', context)

    await vm.runInContext("markSpeech('3:0', 0, 3)", context)

    assert.equal(overlayer.element.querySelectorAll('rect').length, 0)
})

test('on E-Ink the speech mark is drawn in black, and back in the speech colour otherwise', async () => {
    const { context, overlayer } = setup()
    registerSentence(context, rangeAt(10))
    const fill = () => overlayer.element.querySelector('g').style.fill

    vm.runInContext('setInkMarks(true)', context)
    await vm.runInContext("markSpeech('3:0', 0, 3)", context)
    assert.match(fill(), /#000000|rgb\(0, 0, 0\)/i)

    vm.runInContext('setInkMarks(false)', context)
    await vm.runInContext("markSpeech('3:0', 0, 3)", context)
    assert.match(fill(), /#5b8def|rgb\(91, 141, 239\)/i)
})

test('on E-Ink each highlight colour gets its own shape, and other screens keep plain colours', () => {
    const { context } = setup()
    const styleOf = color => vm.runInContext(`inkStyleFor(${JSON.stringify(color)})`, context)

    assert.equal(styleOf('#F6C453'), null, 'colour screens draw the colour itself')

    vm.runInContext('setInkMarks(true)', context)
    assert.equal(styleOf('#F6C453'), 'highlight')
    assert.equal(styleOf('#7BAE7F'), 'underline')
    assert.equal(styleOf('#5B8DEF'), 'squiggly')
    assert.equal(styleOf('#D77FA1'), 'outline')
    assert.equal(styleOf('#6366f1'), 'highlight', 'the default colour is shaded')
})
