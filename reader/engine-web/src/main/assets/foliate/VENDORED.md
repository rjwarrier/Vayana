# Vendored: foliate-js

Source: https://github.com/johnfactotum/foliate-js (MIT, LICENSE alongside this file).
Files: view.js, epub.js, epubcfi.js, progress.js, overlayer.js, text-walker.js,
paginator.js, vendor/zip.js — the exact static+first-level-dynamic import closure needed
for EPUB rendering only (comic-book.js, fb2.js, mobi.js, pdf.js, fixed-layout.js, tts.js,
search.js and their dependents are intentionally not vendored; add them if/when those
formats or features are implemented — see docs/DECISIONS.md).
