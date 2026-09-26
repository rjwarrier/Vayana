const pdfjsPath = path => new URL(`vendor/pdfjs/${path}`, import.meta.url).toString()

import './vendor/pdfjs/pdf.mjs'
const pdfjsLib = globalThis.pdfjsLib
pdfjsLib.GlobalWorkerOptions.workerSrc = pdfjsPath('pdf.worker.mjs')

// Every PDF page uses the same lightweight document shell. Only its viewport differs, so carrying it in the URL
// avoids creating and retaining a native bridge resource for every page visited.
const pageDocumentUrl = ({ width, height }) => {
    const url = new URL('../pdf-page.html', import.meta.url)
    url.searchParams.set('width', width)
    url.searchParams.set('height', height)
    return url.toString()
}

// Vayana: { background, foreground } for dark and tinted reader themes, or null for the page's own colours. pdf.js
// recolours text and vector art and leaves images alone.
let pageColors = null
let pageRotationDegrees = 0
// Vayana: page document -> its TextLayer (a promise while it is first being built)
const textLayers = new WeakMap()
// Vayana: page document -> { generation, task } of its latest render. A zoom or a resize can start a render while an
// older one is still drawing; the older one is cancelled, and never replaces the newer canvas if it finishes first.
const pageRenders = new WeakMap()
const MaxPdfCanvasPixels = 12_000_000
const MaxPdfCanvasDimension = 8_192

/** Physical pixels per CSS pixel, bounded so a high zoom or unusually large page cannot kill the WebView renderer. */
function pdfRenderPixelRatio({
    width,
    height,
    zoom,
    pixelRatio = devicePixelRatio,
    maxPixels = MaxPdfCanvasPixels,
    maxDimension = MaxPdfCanvasDimension,
}) {
    const cssWidth = Math.max(1, width * zoom)
    const cssHeight = Math.max(1, height * zoom)
    const areaLimit = Math.sqrt(maxPixels / (cssWidth * cssHeight))
    const dimensionLimit = Math.min(maxDimension / cssWidth, maxDimension / cssHeight)
    return Math.max(0.1, Math.min(pixelRatio, areaLimit, dimensionLimit))
}

const adjacentPageIndexes = (index, total) => [index + 1, index - 1]
    .filter(candidate => candidate >= 0 && candidate < total)

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
    const pageSize = page.getViewport({ scale: 1, rotation: page.rotate + pageRotationDegrees })
    const pixelRatio = pdfRenderPixelRatio({ width: pageSize.width, height: pageSize.height, zoom })
    const scale = zoom * pixelRatio
    doc.documentElement.style.transform = `scale(${1 / pixelRatio})`
    doc.documentElement.style.transformOrigin = 'top left'
    doc.documentElement.style.setProperty('--scale-factor', scale)
    const viewport = page.getViewport({ scale, rotation: page.rotate + pageRotationDegrees })
    const viewportMeta = doc.querySelector('meta[name="viewport"]')
    if (viewportMeta) viewportMeta.content = `width=${pageSize.width}, height=${pageSize.height}`

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
    const viewport = page.getViewport({ scale: 1, rotation: page.rotate + pageRotationDegrees })
    if (getImageBlob) {
        const canvas = document.createElement('canvas')
        canvas.height = viewport.height
        canvas.width = viewport.width
        const canvasContext = canvas.getContext('2d')
        await page.render({ canvasContext, viewport }).promise
        return new Promise(resolve => canvas.toBlob(resolve))
    }
    const src = pageDocumentUrl(viewport)
    const onZoom = ({ doc, scale }) => render(page, doc, scale)
    return { src, onZoom }
}

const makeTOCItem = item => ({
    label: item.title,
    href: JSON.stringify(item.dest),
    subitems: item.items.length ? item.items.map(makeTOCItem) : null,
})

export const makePDF = async file => {
    pageRotationDegrees = 0
    const transport = new pdfjsLib.PDFDataRangeTransport(file.size, [])
    transport.requestDataRange = (begin, end) => {
        file.slice(begin, end).arrayBuffer().then(chunk => {
            transport.onDataRange(begin, chunk)
        })
    }
    const loadingTask = pdfjsLib.getDocument({
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
    })
    loadingTask.onPassword = (updatePassword, reason) => {
        dispatchEvent(new CustomEvent('vayana-pdf-password', {
            detail: {
                updatePassword,
                incorrect: reason === pdfjsLib.PasswordResponses.INCORRECT_PASSWORD,
            },
        }))
    }
    const pdf = await loadingTask.promise

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
    book.pageLabels = await pdf.getPageLabels() ?? Array.from({ length: pdf.numPages }, (_, index) => String(index + 1))

    // Cache the in-flight promise too: a fast page turn and the idle warm-up must never decode the same page twice.
    const cache = new Map()
    const loadPage = index => {
        const cached = cache.get(index)
        if (cached) return cached
        const loading = pdf.getPage(index + 1).then(renderPage)
        loading.catch(() => cache.delete(index))
        cache.set(index, loading)
        return loading
    }
    book.sections = Array.from({ length: pdf.numPages }).map((_, i) => ({
        id: i,
        load: () => loadPage(i),
        size: 1000,
    }))
    let prefetchRevision = 0
    let prefetchTimer = null
    book.prefetchAround = index => {
        if (!Number.isInteger(index)) return
        const revision = ++prefetchRevision
        clearTimeout(prefetchTimer)
        prefetchTimer = setTimeout(async () => {
            for (const candidate of adjacentPageIndexes(index, pdf.numPages)) {
                if (revision !== prefetchRevision) return
                try { await loadPage(candidate) } catch (_) {}
            }
        }, 200)
    }
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
    const thumbnailCache = new Map()
    book.getPageThumbnail = (index, maxWidth = 240) => {
        if (!Number.isInteger(index) || index < 0 || index >= pdf.numPages) return Promise.resolve(null)
        const width = Math.max(80, Math.min(480, Number(maxWidth) || 240))
        const cacheKey = `${index}:${Math.round(width)}:${pageRotationDegrees}`
        if (thumbnailCache.has(cacheKey)) return thumbnailCache.get(cacheKey)
        const thumbnail = pdf.getPage(index + 1).then(async page => {
            const natural = page.getViewport({ scale: 1, rotation: page.rotate + pageRotationDegrees })
            const viewport = page.getViewport({
                scale: Math.min(1, width / natural.width),
                rotation: page.rotate + pageRotationDegrees,
            })
            const canvas = document.createElement('canvas')
            canvas.width = Math.max(1, Math.round(viewport.width))
            canvas.height = Math.max(1, Math.round(viewport.height))
            await page.render({ canvasContext: canvas.getContext('2d'), viewport }).promise
            return canvas.toDataURL('image/jpeg', 0.78)
        })
        thumbnail.catch(() => thumbnailCache.delete(cacheKey))
        thumbnailCache.set(cacheKey, thumbnail)
        if (thumbnailCache.size > 72) thumbnailCache.delete(thumbnailCache.keys().next().value)
        return thumbnail
    }
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
    book.setPageRotation = degrees => {
        const normalized = ((Number(degrees) || 0) % 360 + 360) % 360
        pageRotationDegrees = [0, 90, 180, 270].includes(normalized) ? normalized : 0
    }
    book.destroy = () => {
        prefetchRevision++
        clearTimeout(prefetchTimer)
        cache.clear()
        thumbnailCache.clear()
        return pdf.destroy()
    }
    return book
}
