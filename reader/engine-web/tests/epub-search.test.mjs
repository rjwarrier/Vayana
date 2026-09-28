import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

// view.js imports ./search.js for in-book EPUB search; it once wasn't vendored, which broke search silently.
// text-walker.js reads the browser's NodeFilter when it loads and `document` when it walks.
globalThis.NodeFilter = new JSDOM('').window.NodeFilter
const load = async name => {
    const source = await readFile(new URL(`../src/main/assets/foliate/${name}`, import.meta.url), 'utf8')
    return import(`data:text/javascript;base64,${Buffer.from(source).toString('base64')}`)
}
const { searchMatcher } = await load('search.js')
const { textWalker } = await load('text-walker.js')

function documentOf(html) {
    const { window } = new JSDOM(`<html><body>${html}</body></html>`)
    globalThis.document = window.document
    return window.document
}

test('finds every match across element boundaries, with an excerpt', () => {
    const doc = documentOf('<p>The river ran <em>quiet</em> under the bridge.</p><p>A quiet town slept.</p>')
    const matcher = searchMatcher(textWalker, { defaultLocale: 'en' })

    const results = Array.from(matcher(doc, 'quiet'))

    assert.equal(results.length, 2)
    assert.equal(results[0].range.toString(), 'quiet')
    // The excerpt's context comes from the matched text node: none for a word alone in <em>.
    assert.deepEqual(results[1].excerpt, { pre: 'A ', match: 'quiet', post: ' town slept.' })
})

test('matches case- and accent-insensitively by default', () => {
    const doc = documentOf('<p>Café society, CAFE culture.</p>')
    const matcher = searchMatcher(textWalker, { defaultLocale: 'en' })

    assert.equal(Array.from(matcher(doc, 'cafe')).length, 2)
})

test('a phrase that is not there finds nothing', () => {
    const doc = documentOf('<p>Nothing to see here.</p>')
    const matcher = searchMatcher(textWalker, { defaultLocale: 'en' })

    assert.equal(Array.from(matcher(doc, 'dragon')).length, 0)
})
