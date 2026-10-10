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

test('wrapped EPUB lines stay in one sentence and map back to the original text', () => {
    const { context, sentences } = setup('<p>She walked\ninto <em>the</em>\r\nroom<br>without stopping.</p><p>Then she left.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['She walked into the room without stopping.', 'Then she left.'])
    assert.deepEqual(Array.from(sentences, s => s.pauseBeforeMs), [0, 250])
    context.sentenceId = sentences[0].id
    const start = sentences[0].text.indexOf('room')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 4}).toString()`, context), 'room')
})

test('Crime and Punishment Chapter I opening has no stops at printed wraps or abbreviated places', () => {
    const { context, sentences } = setup(`<p>
      On an exceptionally hot evening early in July a young man came out of the
      garret in which he lodged in S. Place and walked slowly, as though in
      hesitation, towards K. bridge.
    </p>`)
    assert.deepEqual(Array.from(sentences, s => s.text), [
        'On an exceptionally hot evening early in July a young man came out of the garret in which he lodged in S Place and walked slowly, as though in hesitation, towards K bridge.',
    ])
    context.sentenceId = sentences[0].id
    for (const word of ['garret', 'Place', 'hesitation', 'bridge']) {
        const start = sentences[0].text.indexOf(word)
        assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + word.length}).toString()`, context), word)
    }
})

test('soft line separators join prose while blank lines and explicit paragraphs keep their boundaries', () => {
    const { sentences } = setup('<p>She walked\u2028into the room.\n  \nHe stayed.<br><br>She returned.</p><p>Next paragraph.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['She walked into the room.', 'He stayed.', 'She returned.', 'Next paragraph.'])
})

test('marked footnote references are skipped but mathematical superscripts remain', () => {
    const { sentences } = setup('<p>Hello<sup><a href="#n">1</a></sup> world. Read x<sup>2</sup>.</p><p>Yes<a role="doc-noteref" href="#n">2</a>.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Hello world.', 'Read x squared.', 'Yes.'])
})

test('HTML and Unicode numeric exponents preserve their mathematical meaning', () => {
    const { sentences } = setup('<p>Read x<sup>2</sup>, y<sup>3</sup>, z<sup>4</sup> and a<sup>12</sup>.</p><p>Read x², y³, z⁴ and a⁻².</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), [
        'Read x squared, y cubed, z to the power of four and a to the power of 12.',
        'Read x squared, y cubed, z to the power of four and a to the power of minus two.',
    ])
})

test('expanded exponents and following words highlight their original source without changing the book', () => {
    for (const exponent of ['<sup><em>2</em></sup>', '²', '<sup>12</sup>', '⁻²']) {
        const before = `<p>Read x${exponent} then continue.</p>`
        const { context, sentences, document } = setup(before)
        const sentence = sentences[0]
        context.sentenceId = sentence.id
        const start = sentence.text.indexOf(exponent.includes('12') || exponent === '⁻²' ? 'to the power' : 'squared')
        const end = sentence.text.indexOf(' then')
        assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${end}).toString()`, context),
            exponent === '²' ? '²' : exponent === '⁻²' ? '⁻²' : exponent.includes('12') ? '12' : '2')
        const word = sentence.text.indexOf('continue')
        assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${word}, ${word + 8}).toString()`, context), 'continue')
        assert.equal(document.body.innerHTML, before)
    }
})

test('ordinary digits and standalone superscripts are preserved while symbolic powers are spoken', () => {
    const { sentences } = setup('<p>Read x2, x<sup>n</sup> and H<sub>2</sub>O.</p><p><sup>2</sup> is a label.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Read x2, x to the power of n and H2O.', '2 is a label.'])
})

test('adjacent factors, symbolic powers and fractional powers retain their boundaries', () => {
    const { sentences } = setup('<p>Read x²y, x<sup>n</sup>, xⁿ and x<sup>1/2</sup>.</p>')
    assert.equal(sentences[0].text, 'Read x squared y, x to the power of n, x to the power of n and x to the power of one over two.')
})

test('MathML fractions and powers retain their structure instead of concatenating digits', () => {
    const { context, sentences } = setup('<p>Read <math><mfrac><mn>1</mn><mn>2</mn></mfrac></math> then <math><msup><mi>x</mi><mi>n</mi></msup></math> next.</p>')
    assert.equal(sentences[0].text, 'Read one over two then x to the power of n next.')
    context.sentenceId = sentences[0].id
    const start = sentences[0].text.indexOf('one')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 12}).toString()`, context), '12')
    const next = sentences[0].text.indexOf('next')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${next}, ${next + 4}).toString()`, context), 'next')
})

test('fractional MathML exponents and nested fractions preserve numerator and denominator boundaries', () => {
    const { sentences } = setup('<p>Read <math><msup><mi>x</mi><mrow><mfrac><mn>1</mn><mn>2</mn></mfrac></mrow></msup></math>.</p><p>Read <math><mfrac><mfrac><mn>1</mn><mn>2</mn></mfrac><mn>3</mn></mfrac></math>.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Read x to the power of one over two.', 'Read fraction one over two end fraction over three.'])
})

test('squared units and trigonometric functions are mathematical bases, not prose footnotes', () => {
    const { sentences } = setup('<p>Read 5 cm² and sin<sup>2</sup>x.</p>')
    assert.equal(sentences[0].text, 'Read 5 cm squared and sin squared x.')
})

test('mixed Unicode, HTML and MathML powers keep their order and later highlight offsets', () => {
    const { context, sentences } = setup('<p>Read x². Then y<sup>3</sup>. Finally <math><msup><mi>z</mi><mn>4</mn></msup></math> ends.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Read x squared.', 'Then y cubed.', 'Finally z to the power of four ends.'])
    context.sentenceId = sentences[2].id
    const start = sentences[2].text.indexOf('four')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 4}).toString()`, context), '4')
    const ends = sentences[2].text.indexOf('ends')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${ends}, ${ends + 4}).toString()`, context), 'ends')
})

test('linked powers survive while semantic and prose footnotes remain silent', () => {
    const { sentences } = setup('<p>Read x<sup><a href="#equation">2</a></sup>. Sentence<sup>2</sup> continues. Sentence² continues. Read x<sup><a role="doc-noteref" href="#note">3</a></sup>.</p>')
    assert.deepEqual(Array.from(sentences, s => s.text), ['Read x squared.', 'Sentence continues.', 'Sentence continues.', 'Read x.'])
})

test('links to marked footnote targets override apparent mathematical bases', () => {
    const { sentences } = setup('<p>Read x<sup><a href="#note">2</a></sup>.</p><aside id="note" role="doc-footnote" hidden>Footnote</aside>')
    assert.equal(sentences[0].text, 'Read x.')
})

test('explicitly raised PDF digits become powers without rewriting ordinary digits', () => {
    const { context, sentences } = setup('<div class="textLayer"><span>Read x</span><span style="vertical-align:super">2</span><span> then x2.</span></div>', { fixedLayout: true })
    assert.equal(sentences[0].text, 'Read x squared then x2.')
    context.sentenceId = sentences[0].id
    const start = sentences[0].text.indexOf('squared')
    assert.equal(vm.runInContext(`speech.sentences.get(sentenceId).rangeForOffsets(${start}, ${start + 7}).toString()`, context), '2')
})

test('PDF powers require a smaller raised adjacent span, not merely a higher position', () => {
    const { window } = new JSDOM('<div class="textLayer"><span>Read x</span><span>2</span><span> then y</span><span>3</span><span> ends.</span></div>')
    const spans = window.document.querySelectorAll('span')
    const boxes = [[0, 20, 60, 40], [60, 15, 68, 29], [68, 20, 128, 40], [128, 20, 138, 40], [138, 20, 200, 40]]
    spans.forEach((span, i) => {
        const [left, top, right, bottom] = boxes[i]
        span.getBoundingClientRect = () => ({ left, top, right, bottom, width: right - left, height: bottom - top })
        span.style.fontSize = i === 1 ? '14px' : '20px'
    })
    const context = vm.createContext({ NodeFilter: window.NodeFilter, fixedLayout: true, segmenterFor: () => new Intl.Segmenter('en', { granularity: 'sentence' }) })
    vm.runInContext(source, context)
    assert.equal(context.speechSentencesFor(window.document, 0, null)[0].text, 'Read x squared then y3 ends.')
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
