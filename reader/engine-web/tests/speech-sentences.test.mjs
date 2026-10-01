import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import vm from 'node:vm'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const bridge = await readFile(new URL('../src/main/assets/bridge.js', import.meta.url), 'utf8')
const source = bridge.slice(bridge.indexOf('const speech ='), bridge.indexOf('// [fromCfi] starts reading'))

function setup(html, { fixedLayout = false, index = 0, existingContext = null } = {}) {
    const { window } = new JSDOM(html)
    const context = existingContext ?? vm.createContext({
        NodeFilter: window.NodeFilter,
        fixedLayout,
        segmenterFor: () => new Intl.Segmenter('en', { granularity: 'sentence' }),
    })
    if (!existingContext) vm.runInContext(source, context)
    const sentences = context.speechSentencesFor(window.document, index, null)
    return { context, sentences, document: window.document }
}

test('common titles stay with the following name without a spoken full stop', () => {
    for (const title of ['Mr', 'Mrs', 'Ms', 'Mx', 'Dr', 'Prof', 'Rev', 'Fr', 'Capt', 'Lt', 'Col', 'Gen', 'Sgt', 'Maj', 'Cmdr', 'Hon', 'Pres', 'Gov', 'Sen', 'Rep', 'Supt', 'Det']) {
        const { sentences } = setup(`<p>${title}. Smith arrived. They left.</p>`)
        assert.deepEqual(Array.from(sentences, s => s.text), [`${title} Smith arrived.`, 'They left.'], title)
    }
})

test('multiple and lowercase titles survive inline formatting and a line break', () => {
    const { sentences } = setup('<p>mr. <em>Smith</em> met Dr.\nJones and Mrs. Brown. Goodbye!</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['mr Smith met Dr Jones and Mrs Brown.', 'Goodbye!'])
})

test('consecutive titles stay with the name', () => {
    const { sentences } = setup('<p>Dr. Prof. Smith arrived. They left.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Dr Prof Smith arrived.', 'They left.'])
})

test('spoken name offsets still highlight the original text after removing title periods', () => {
    const { context, sentences } = setup('<p>  Mr. <em>Smith</em> met Dr.\nJones. </p>')
    const sentence = sentences[0]
    context.sentenceId = sentence.id
    const start = sentence.text.indexOf('Jones')
    const range = vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 5})`, context)
    assert.equal(range.toString(), 'Jones')
    assert.equal(vm.runInContext('speech.sentences.get(sentenceId).range.toString()', context), 'Mr. Smith met Dr.\nJones.')
})

test('ordinary full stops, decimals and words ending in title letters are unchanged', () => {
    const { sentences } = setup('<p>It cost 3.14. The hydr. dried. Done?</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['It cost 3.14.', 'The hydr. dried.', 'Done?'])
})

test('a title at a paragraph boundary or end of text keeps its period', () => {
    const { sentences } = setup('<p>They called him Dr.</p><p>Smith arrived.</p><p>Mr.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['They called him Dr.', 'Smith arrived.', 'Mr.'])
})

test('numbered references stay with their numbers', () => {
    for (const abbreviation of ['No', 'Nos', 'Vol', 'Ch', 'Chap', 'Fig', 'Figs', 'Eq', 'Eqs', 'Sec', 'p', 'pp']) {
        const { sentences } = setup(`<p>See ${abbreviation}. 12 for details. Continue.</p>`)
        assert.deepEqual(Array.from(sentences, s => s.text), [`See ${abbreviation} 12 for details.`, 'Continue.'], abbreviation)
    }
})

test('name initials stay together and an initial after a title stays with the surname', () => {
    for (const [source, expected] of [['J. R. R. Tolkien', 'J R R Tolkien'], ['Mr. J. Smith', 'Mr J Smith']]) {
        const { sentences } = setup(`<p>${source} wrote this. Continue.</p>`)
        assert.deepEqual(Array.from(sentences, s => s.text), [`${expected} wrote this.`, 'Continue.'])
    }
})

test('dotted acronyms and clock abbreviations preserve sentence endings', () => {
    const { sentences } = setup('<p>The U.S. government meets at 9 a.m. today. Return at 5 p.m. Tomorrow is fine.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), [
        'The U S government meets at 9 a m today.', 'Return at 5 p m.', 'Tomorrow is fine.',
    ])
})

test('familiar prose abbreviations are spoken as words', () => {
    const { sentences } = setup('<p>Try e.g. London, i.e. the capital. Tea vs. coffee, etc., are choices. Bring cups etc. Then leave.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), [
        'Try for example London, that is the capital.',
        'Tea versus coffee, et cetera, are choices.', 'Bring cups et cetera.', 'Then leave.',
    ])
})

test('versus and examples can precede proper names', () => {
    const { sentences } = setup('<p>India vs. Australia is next. Try e.g. Paris.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['India versus Australia is next.', 'Try for example Paris.'])
})

test('paragraphs, headings and scene breaks have pauses but ordinary sentences do not', () => {
    const { sentences } = setup('<p>One. Two.</p><p>Three.</p><h2>Chapter Two</h2><p>Four.</p><hr><p>Five.</p>')
    assert.deepEqual(Array.from(sentences, s => s.pauseBeforeMs), [0, 0, 250, 600, 600, 900])
})

test('inline whitespace between styled words is preserved', () => {
    const { sentences } = setup('<p><em>Hello</em> <strong>world.</strong></p>')
    assert.equal(sentences[0].text, 'Hello world.')
})

test('marked footnote references are skipped but mathematical superscripts remain', () => {
    const { sentences } = setup('<p>Hello<sup><a href="#n">1</a></sup> world. Read x<sup>2</sup>.</p><p>Yes<a role="doc-noteref" href="#n">2</a>.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Hello world.', 'Read x2.', 'Yes.'])
})

test('PDF wraps join words, retain compound hyphens and map the original printed text', () => {
    const { context, sentences } = setup('<div class="textLayer"><span>An inter-</span><br><span>national well-</span><br><span>known soft\u00adhyphen.</span></div>', { fixedLayout: true })
    assert.equal(sentences[0].text, 'An international well-known softhyphen.')
    context.sentenceId = sentences[0].id
    const start = sentences[0].text.indexOf('international')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 13}).toString()`, context), 'inter-national')
})

test('PDF margin headers require three distinct pages and body text is never removed', () => {
    const html = '<div class="textLayer"><span style="top:2%">Running Header.</span><br><span style="top:40%">Running Header.</span><br><span style="top:97%">12</span></div>'
    const first = setup(html, { fixedLayout: true, index: 0 })
    const read = index => setup(html, { fixedLayout: true, index, existingContext: first.context }).sentences
    assert.equal(first.sentences.length, 2)
    assert.equal(read(0).length, 2)
    assert.equal(read(1).length, 2)
    assert.equal(read(2).length, 1)
    assert.equal(read(3)[0].text, 'Running Header.')
})

test('expanded speech maps both abbreviations and subsequent words to the original text', () => {
    const { context, sentences } = setup('<p>Try e.g. <em>London</em> today.</p>')
    const sentence = sentences[0]
    context.sentenceId = sentence.id
    for (const [spoken, original] of [['for example', 'e.g.'], ['London', 'London'], ['today', 'today']]) {
        const start = sentence.text.indexOf(spoken)
        assert.ok(start >= 0)
        const range = vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + spoken.length})`, context)
        assert.equal(range.toString(), original)
    }
})

test('list labels, paragraph boundaries, ambiguous abbreviations and decimals keep their stops', () => {
    const { sentences } = setup('<p>A. First item.</p><p>See Fig.</p><p>12 is next. Acme Inc. They left. It cost 3.14.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), [
        'A.', 'First item.', 'See Fig.', '12 is next.', 'Acme Inc.', 'They left.', 'It cost 3.14.',
    ])
})
