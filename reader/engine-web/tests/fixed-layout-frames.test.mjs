import assert from 'node:assert/strict'
import { readFile } from 'node:fs/promises'
import { test } from 'node:test'
import { JSDOM } from 'jsdom'

const { window } = new JSDOM('<!doctype html><body></body>')
Object.assign(globalThis, {
    document: window.document,
    HTMLElement: window.HTMLElement,
    customElements: window.customElements,
    CustomEvent: window.CustomEvent,
    ResizeObserver: class { observe() {} unobserve() {} },
    CSSStyleSheet: class { replaceSync() {} },
})
// jsdom loads no frames; a frame here is "loaded" as soon as it is given a source
Object.defineProperty(window.HTMLIFrameElement.prototype, 'src', {
    set() { setTimeout(() => this.dispatchEvent(new window.Event('load'))) },
    get() { return '' },
})
const frameDocs = new WeakMap()
Object.defineProperty(window.HTMLIFrameElement.prototype, 'contentDocument', {
    get() {
        if (!frameDocs.has(this)) frameDocs.set(this, window.document.implementation.createHTMLDocument())
        return frameDocs.get(this)
    },
})
const source = await readFile(new URL('../src/main/assets/foliate/fixed-layout.js', import.meta.url), 'utf8')
new Function(source.replace('export class FixedLayout', 'class FixedLayout'))()

const settle = () => new Promise(resolve => setTimeout(resolve, 50))

test('one-page PDF turns reuse frames built ahead and announce each page once', async () => {
    const loadsBySection = [0, 0, 0, 0, 0]
    const book = {
        rendition: { spread: 'none', viewport: { width: 100, height: 150 } },
        sections: loadsBySection.map((_, index) => ({ load: async () => { loadsBySection[index]++; return 'about:blank' } })),
    }
    const renderer = document.createElement('foliate-fxl')
    document.body.append(renderer)
    renderer.open(book)
    const announced = []
    renderer.addEventListener('load', event => announced.push(event.detail.index))

    await renderer.goTo({ index: 1 })
    await settle()
    const shown = () => renderer.getContents()
    assert.deepEqual(shown().map(content => content.index), [1])
    const firstDoc = shown()[0].doc

    await renderer.next()
    await settle()
    assert.deepEqual(shown().map(content => content.index), [2])
    assert.deepEqual(announced, [1, 2])

    // back to the page just left: the same frame, not announced again
    await renderer.prev()
    await settle()
    assert.deepEqual(shown().map(content => content.index), [1])
    assert.equal(shown()[0].doc, firstDoc)
    assert.deepEqual(announced, [1, 2])

    // a far jump drops the frames kept around the old page
    await renderer.goTo({ index: 4 })
    await settle()
    assert.deepEqual(shown().map(content => content.index), [4])
    assert.deepEqual(announced, [1, 2, 4])
    window.close()
})
