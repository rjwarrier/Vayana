const pdfjsPath = path => new URL(`vendor/pdfjs/${path}`, import.meta.url).toString()

import './vendor/pdfjs/pdf.mjs'
const pdfjsLib = globalThis.pdfjsLib
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsPath('pdf.worker.mjs')

// https://raw.githubusercontent.com/mozilla/pdf.js/refs/tags/v5.5.207/web/text_layer_builder.css
// https://raw.githubusercontent.com/mozilla/pdf.js/refs/tags/v5.5.207/web/annotation_layer_builder.css
// Vayana: linked from each page document rather than inlined into it; every page document is kept for the book's
// lifetime (on Android, in the app's memory), and the two stylesheets are ~16 KB of it. A frame's load event waits
// for them, so the text layer is still laid out with them in place.
const layerStylesheets = ['text_layer_builder.css', 'annotation_layer_builder.css']
    .map(name => `<link rel="stylesheet" href="${pdfjsPath(name)}">`).join('')

// Vayana: Android WebView fails to load `blob:` URLs inside a sandboxed same-origin iframe (see
// epub.js), so with the native bridge present each page document is served by the app instead.
const pageDocumentUrl = html => {
    if (!globalThis.AndroidBridge) return URL.createObjectURL(new Blob([html], { type: 'text/html' }))
    const bytes = new TextEncoder().encode(html)
    let binary = ''
    for (let i = 0; i < bytes.length; i += 0x8000)
        binary += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000))
    return globalThis.AndroidBridge.registerResource('text/html', btoa(binary))
}

// Vayana: { background, foreground } for dark and tinted reader themes, or null for the page's own colours. pdf.js
// recolours text and vector art and leaves images alone.
let pageColors = null
// Vayana: page document -> its TextLayer (a promise while it is first being built)
const textLayers = new WeakMap()
// Vayana: page document -> { generation, task } of its latest render. A zoom or a resize can start a render while an
// older one is still drawing; the older one is cancelled, and never replaces the newer canvas if it finishes first.
const pageRenders = new WeakMap()

// Vayana: pdf.js draws a long page in slices paced by animation frames, and frames stop with the screen off or the app
// in the background, which is when read aloud turns pages. Each slice here waits for a posted message instead: that
// still yields to input and painting between slices, and unlike a timer it isn't slowed down in a hidden page.
const sliceChannel = new MessageChannel()
const pendingSlices = []
sliceChannel.port1.onmessage = () => pendingSlices.shift()?.()
const nextSlice = continueRendering => {
    pendingSlices.push(continueRendering)
    sliceChannel.port2.postMessage(null)
}
const renderWithoutFrames = task => {
    // `onContinue` receives pdf.js's own scheduler, which would still wait for a frame unless this is off.
    if (task._internalRenderTask) task._internalRenderTask._useRequestAnimationFrame = false
    task.onContinue = nextSlice
    return task
}

const render = async (page, doc, zoom) => {
    const scale = zoom * devicePixelRatio
    doc.documentElement.style.transform = `scale(${1 / devicePixelRatio})`
    doc.documentElement.style.transformOrigin = 'top left'
    doc.documentElement.style.setProperty('--scale-factor', scale)
    const viewport = page.getViewport({ scale })

    // the canvas must be in the `PDFDocument`'s `ownerDocument`
    // (`globalThis.document` by default); that's where the fonts are loaded
    const canvas = document.createElement('canvas')
    canvas.height = viewport.height
    canvas.width = viewport.width
    const canvasContext = canvas.getContext('2d')
    const previous = pageRenders.get(doc)
    previous?.task.cancel()
    const generation = (previous?.generation ?? 0) + 1
    const task = page.render({ canvasContext, viewport, ...(pageColors ? { pageColors } : {}) })
    renderWithoutFrames(task)
    pageRenders.set(doc, { generation, task })
    try {
        await task.promise
    } catch (error) {
        if (error?.name === 'RenderingCancelledException') return
        throw error
    }
    if (pageRenders.get(doc)?.generation !== generation) return
    doc.querySelector('#canvas').replaceChildren(doc.adoptNode(canvas))

    // Vayana: build the text layer once per page document and rescale it on later renders (zoom, colours). Its text
    // nodes then outlive a zoom, so selections, marks and read-aloud ranges held on them stay valid.
    const container = doc.querySelector('.textLayer')
    const existing = textLayers.get(doc)
    if (existing) {
        const textLayer = await existing
        if (pageRenders.get(doc)?.generation !== generation) return
        textLayer.update({ viewport })
    } else {
        const building = (async () => {
            const textLayer = new pdfjsLib.TextLayer({
                textContentSource: await page.streamTextContent(),
                container, viewport,
            })
            await textLayer.render()
            return textLayer
        })()
        textLayers.set(doc, building)
        await building

        // hide "offscreen" canvases appended to docuemnt when rendering text layer
        // https://github.com/mozilla/pdf.js/blob/642b9a5ae67ef642b9a8808fd9efd447e8c350e2/web/pdf_viewer.css#L51-L58
        for (const canvas of document.querySelectorAll('.hiddenCanvasElement'))
            Object.assign(canvas.style, {
                position: 'absolute',
                top: '0',
                left: '0',
                width: '0',
                height: '0',
                display: 'none',
            })

        // fix text selection
        // https://github.com/mozilla/pdf.js/blob/642b9a5ae67ef642b9a8808fd9efd447e8c350e2/web/text_layer_builder.js#L105-L107
        const endOfContent = document.createElement('div')
        endOfContent.className = 'endOfContent'
        container.append(endOfContent)
        // TODO: this only works in Firefox; see https://github.com/mozilla/pdf.js/pull/17923
        container.onpointerdown = () => container.classList.add('selecting')
        container.onpointerup = () => container.classList.remove('selecting')
    }

    const div = doc.querySelector('.annotationLayer')
    div.replaceChildren()
    const linkService = {
        goToDestination: () => {},
        getDestinationHash: dest => JSON.stringify(dest),
        addLinkAttributes: (link, url) => link.href = url,
    }
    await new pdfjsLib.AnnotationLayer({ page, viewport, div, linkService })
        .render({ annotations: await page.getAnnotations() })
    // Vayana: the text layer has moved to the new scale; the reader redraws its marks on it after this
    if (pageRenders.get(doc)?.generation === generation) doc.dispatchEvent(new CustomEvent('vayana-page-rendered'))
}

const renderPage = async (page, getImageBlob) => {
    const viewport = page.getViewport({ scale: 1 })
    if (getImageBlob) {
        const canvas = document.createElement('canvas')
        canvas.height = viewport.height
        canvas.width = viewport.width
        const canvasContext = canvas.getContext('2d')
        await page.render({ canvasContext, viewport }).promise
        return new Promise(resolve => canvas.toBlob(resolve))
    }
    const src = pageDocumentUrl(`
        <!DOCTYPE html>
        <html lang="en">
        <meta charset="utf-8">
        <meta name="viewport" content="width=${viewport.width}, height=${viewport.height}">
        <style>
        html, body {
            margin: 0;
            padding: 0;
        }
        /*
        https://github.com/mozilla/pdf.js/commit/bd05b255fabfc313b194bfe9a17ccded4d90fb5a
        */
        :root {
          --user-unit: 1;
          --total-scale-factor: calc(var(--scale-factor) * var(--user-unit));
          --scale-round-x: 1px;
          --scale-round-y: 1px;
        }
        </style>
        ${layerStylesheets}
        <div id="canvas"></div>
        <div class="textLayer"></div>
        <div class="annotationLayer"></div>
    `)
    const onZoom = ({ doc, scale }) => render(page, doc, scale)
    return { src, onZoom }
}

const makeTOCItem = item => ({
    label: item.title,
    href: JSON.stringify(item.dest),
    subitems: item.items.length ? item.items.map(makeTOCItem) : null,
})

export const makePDF = async file => {
    const transport = new pdfjsLib.PDFDataRangeTransport(file.size, [])
    transport.requestDataRange = (begin, end) => {
        file.slice(begin, end).arrayBuffer().then(chunk => {
            transport.onDataRange(begin, chunk)
        })
    }
    const pdf = await pdfjsLib.getDocument({
        range: transport,
        cMapUrl: pdfjsPath('cmaps/'),
        standardFontDataUrl: pdfjsPath('standard_fonts/'),
        isEvalSupported: false,
        // Vayana: read the parts of the file the pages on screen need, not the whole file in the background. The
        // file is local, so a page's data is a quick slice away; a large scanned book no longer has to fit in memory.
        disableAutoFetch: true,
        // Each read is a round trip to the worker; 1 MB reads (not the default 64 KB) keep search across a whole
        // book, which touches every page, from turning into thousands of them.
        rangeChunkSize: 1 << 20,
    }).promise

    // Vayana: one page per screen. Paired spreads suit a desktop window, not a phone, and moving between the two pages
    // of one spread by `goTo` emits no 'relocate', which would leave the saved position behind.
    const book = { rendition: { layout: 'pre-paginated', spread: 'none' } }

    const { metadata, info } = await pdf.getMetadata() ?? {}
    // TODO: for better results, parse `metadata.getRaw()`
    book.metadata = {
        title: metadata?.get('dc:title') ?? info?.Title,
        author: metadata?.get('dc:creator') ?? info?.Author,
        contributor: metadata?.get('dc:contributor'),
        description: metadata?.get('dc:description') ?? info?.Subject,
        language: metadata?.get('dc:language'),
        publisher: metadata?.get('dc:publisher'),
        subject: metadata?.get('dc:subject'),
        identifier: metadata?.get('dc:identifier'),
        source: metadata?.get('dc:source'),
        rights: metadata?.get('dc:rights'),
    }

    const outline = await pdf.getOutline()
    book.toc = outline?.map(makeTOCItem)

    const cache = new Map()
    book.sections = Array.from({ length: pdf.numPages }).map((_, i) => ({
        id: i,
        load: async () => {
            const cached = cache.get(i)
            if (cached) return cached
            const url = await renderPage(await pdf.getPage(i + 1))
            cache.set(i, url)
            return url
        },
        size: 1000,
    }))
    book.isExternal = uri => /^\w+:/i.test(uri)
    book.resolveHref = async href => {
        const parsed = JSON.parse(href)
        const dest = typeof parsed === 'string'
            ? await pdf.getDestination(parsed) : parsed
        const index = await pdf.getPageIndex(dest[0])
        return { index }
    }
    book.splitTOCHref = async href => {
        const parsed = JSON.parse(href)
        const dest = typeof parsed === 'string'
            ? await pdf.getDestination(parsed) : parsed
        const index = await pdf.getPageIndex(dest[0])
        return [index, null]
    }
    book.getTOCFragment = doc => doc.documentElement
    book.getCover = async () => renderPage(await pdf.getPage(1), true)
    // Vayana: page text for in-book search, and the colours pages render in
    // Kept for the open book: search and the word list go through every page again each time. A page's text is a
    // few KB, so even a long book's is small next to one rendered page.
    const pageTexts = new Map()
    book.getPageText = index => {
        if (!pageTexts.has(index)) {
            const text = pdf.getPage(index + 1)
                .then(page => page.getTextContent())
                .then(({ items }) => items.map(item => (item.str ?? '') + (item.hasEOL ? ' ' : '')).join(''))
            text.catch(() => pageTexts.delete(index))
            pageTexts.set(index, text)
        }
        return pageTexts.get(index)
    }
    book.setPageColors = colors => { pageColors = colors }
    book.destroy = () => pdf.destroy()
    return book
}
