# Vendored: foliate-js

Source: https://github.com/johnfactotum/foliate-js (MIT, LICENSE alongside this file).
Files: view.js, epub.js, epubcfi.js, progress.js, overlayer.js, text-walker.js,
paginator.js, vendor/zip.js — the exact static+first-level-dynamic import closure needed
for EPUB rendering only (comic-book.js, fb2.js, mobi.js, pdf.js, fixed-layout.js, tts.js
and their dependents are intentionally not vendored; add them if/when those
formats or features are implemented — see docs/DECISIONS.md).

Search: `search.js` (foliate-js commit 78914ae, unmodified; no imports) backs in-book EPUB search, which `view.js`
loads with `import('./search.js')`. It was missing at first, which broke EPUB search without an error on screen;
`tests/epub-search.test.mjs` now loads it.

Local patch: `epubcfi.js` defaults both CFI generation and resolution to a filter
that flattens elements marked `data-foliate-cfi-transparent`. The reader marks its
bionic-reading wrappers this way so formatting does not change saved locations.

Local patch: `paginator.js` `animate()` finishes on a timer when animation frames stop
arriving (screen off, app in background), so a page turn in flight never leaves the
paginator locked. Read-aloud relies on this to keep turning pages with the screen off.

PDF: `pdf.js` and `fixed-layout.js` (foliate-js commit 78914ae) plus `vendor/pdfjs/` from the same commit: Mozilla
pdf.js 5.5.207 (Apache-2.0, notice at the top of `pdf.mjs`), `pdf.mjs`, `pdf.worker.mjs`, the text/annotation layer
CSS, `cmaps/` and `standard_fonts/` (Foxit and Liberation licences alongside). Source maps are left out. No wasm
decoders are vendored, so JPEG 2000 images inside a PDF don't render.

Local patch: `pdf.js` serves each page document through `AndroidBridge.registerResource` instead of a `blob:` URL
(the same WebView iframe bug as `epub.js`), and sets `rendition.spread = 'none'` so every page is its own screen:
foliate's paired spreads emit no `relocate` when `goTo` moves between the two pages of one spread. It also renders with
optional `pageColors` (`book.setPageColors`), clears the text and annotation layers before each re-render (upstream
stacks a second copy on every zoom), fires `vayana-page-rendered` on the page document afterwards, and adds
`book.getPageText(index)` for search. The text layer is built once per page document and rescaled with
`TextLayer.update` on later renders, so its text nodes (and ranges held on them) survive a zoom. A page has one
render in flight (a newer one cancels the older, which never replaces the newer canvas); page drawing slices are paced
by `MessageChannel` messages instead of animation frames, which stop with the screen off; the layer stylesheets are
linked from each page document rather than inlined; the file is read on demand in 1 MB ranges (`disableAutoFetch`),
and page text for search is cached per open book.

Local patch: `fixed-layout.js` centres with `safe center` so a zoomed page's left and top stay scrollable, and tags each
frame with its section index so `getContents()` returns `{ doc, index }` like the paginator.
