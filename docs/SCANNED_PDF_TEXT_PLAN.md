# Text for scanned PDFs (OCR) — plan

Scanned PDFs have no text layer, so search, selection, highlights and notes, dictionary lookup, read aloud and the
word list don't work on them; only zoom and bookmarks do (docs/FEATURES.md, "PDF books"). This plan recognises the
text of a scanned book once, on the device, and hands it to the reader as the text layer pdf.js would have built, so
every existing text feature works unchanged.

## What the trial showed (2026-10-20, *Ram C/O Anandi*, 324 scanned pages, Malayalam)

Tesseract 5.5, pages rendered at 300 dpi, one core of a desktop PC:

| Model (tessdata) | Size | Time / page | Notes |
|---|---|---|---|
| `tessdata_fast/mal` | 5.3 MB | 1.2–1.5 s | ~95% of words right on a checked paragraph; mean confidence 86–91. Garbles Latin text ("5:45 AM"). |
| `tessdata_best/mal` | 12.5 MB | 2.4–3.0 s | No more accurate than fast here. |
| `tessdata_fast/script/Malayalam` | 9.0 MB | 2.4–3.0 s | Only one to read the Latin "5:45 AM" right; for books mixing English. |

- Errors are mostly one wrong letter inside a long word (ആലി for ആലപ്പി); the three models agree on 87–94% of words.
- Word boxes sit tightly on the printed words; good enough to place highlights, selections and the read-aloud mark.
- 600 dpi was slower and no better than 300 dpi.
- Phone estimate (not measured): 3–4 s / page / core with the fast model, ~10 min for 324 pages on two threads.

### Phase 1 on the phone (2026-10-20, Tesseract4Android 4.9.0, `PdfRenderer` at 300 dpi)

| Model | One page at a time | Two at a time | 324 pages (two at a time) | Native memory |
|---|---|---|---|---|
| `mal` (fast) | 1.0–1.2 s / page | 0.70 s / page | ~4 min | +10–15 MB |
| `Malayalam` (script, fast) | — | 0.84 s / page | ~4.5 min | +40–60 MB |

- Times include drawing the page. The text matches the desktop trial word for word.
- Engine start-up is 40–50 ms (`mal`), 135–190 ms (`Malayalam`).
- APK: +7.6 MB of native libraries per ABI (`libtesseract` 4.2, `libleptonica` 2.9, `libjpeg`, `libpngx`), +19 MB
  for the universal APK with three ABIs. All four are 16 KB page-aligned (`zipalign -c -P 16` passes; LOAD segments
  aligned to 0x4000).
- Trigger (debug builds): `adb shell am broadcast -n com.vayana.app/.debug.OcrSpikeReceiver --es pdf <path>
  --es pages 10,12 [--es lang mal] [--ei threads 2]`, models in `files/ocr-models/tessdata/`; logs under `OcrSpike`.

## Design

### Recognition runs on the Android side, not in the reader

- Engine: **Tesseract4Android** (Tesseract 5 + Leptonica, Apache-2.0), added to a new module `:format:ocr`.
- Page images come from Android's **`PdfRenderer`** (already used by `PdfParser` for covers), rendered at 300 dpi in
  grayscale. Recognition then needs no WebView, can run with the reader closed, and is independent of pdf.js.
- A **`CoroutineWorker`** per book (unique work name `ocr-<book id>`), constraints: battery not low, storage not low.
  It works in chunks of pages and saves each page as it finishes, so it survives being stopped and resumes where it
  left off. No foreground service: WorkManager's ~10 min per run is enough for a chunk, and the next chunk is
  enqueued until the book is done. It starts from the page being read and works outwards, so the pages in front of
  the reader are ready first.
- Two pages at a time (two `TessBaseAPI` instances) on phones with enough memory; one otherwise.

### Which pages

- A page is recognised only if pdf.js finds no text on it, so mixed PDFs keep their real text. The reader already
  learns this as it opens pages; to plan the whole book, `pdf.js` reports the text-less page indexes once
  (`getTextContent` per page, as search already does) and Kotlin stores the list with the job.
- Recognition is **opt-in per book** (it costs minutes of CPU and a model download).

### Models

- Downloaded on first use, not bundled: `mal` (fast) by default for Malayalam books, the `Malayalam` script model as
  an option for books mixing English, `eng` (fast) for English scans. Language from the book's metadata, else the
  app language, changeable before starting.
- From `raw.githubusercontent.com/tesseract-ocr/tessdata_fast` over HTTPS, checked against SHA-256 values pinned in
  the app, stored in `filesDir/ocr-models/`. Recorded in Data usage like other downloads. Wi-Fi only unless the
  person says otherwise; size shown before downloading.

### Stored result

- `filesDir/ocr/<books.fileHash>/<page>.json`, one file per page, in PDF points (pdf.js's user space), so it is
  independent of screen size and zoom:

  ```json
  { "model": "mal-fast-4.1", "lines": [ { "words": [ ["ചെന്നൈ", x0, y0, x1, y1, conf], ... ] } ] }
  ```

- Keyed by the file's hash: replacing the book's file starts over; the same file on another device recognises again
  (not synced in v1; see open questions).
- Deleted with the book (permanent deletion and orphan clean-up in `StorageMaintenance`) and from Settings → Privacy
  & data ("Recognised text: N books, X MB").

### Text normalisation (before saving)

- NFC, then old-style chillu sequences (consonant + virama + ZWJ, which Tesseract emits) to atomic chillus
  (ൻ ർ ൽ ൾ ൺ ൿ), and stray ZWJ/ZWNJ removed, so search and the dictionary match ordinary text.
- Words below a confidence threshold are kept in the layer (selection and search still find them) but flagged, and
  double-tap lookup skips them.

### Into the reader

- New path `/ocr/<file hash>/<page>.json` in `FoliateBookEngine`'s asset loader (validated names, like
  `/page-cache/`), and a bridge call telling `pdf.js` which pages have recognised text.
- `pdf.js` gains `pageTextContent(page)`: pdf.js's own text content when the page has any, else the recognised words
  turned into a pdf.js `TextContent` (`items` with `str`, `transform = [h, 0, 0, h, x0, pageHeight - y1]`, `width`,
  `height`, `hasEOL` at line ends; one `styles` entry with a system sans-serif font). It feeds both
  - `render()`'s `TextLayer` (selection, highlights and notes, double-tap lookup, read aloud's marks), and
  - `book.getPageText` (in-book search, global search's content index, the word list).
- When recognition finishes a page that is on screen, that page re-renders its text layer, and annotations resolve
  against it as they do after any render.
- CFIs point into the text layer as for text PDFs, so highlights made on recognised text survive reopening as long
  as the recognised text is the same; re-running recognition with another model can orphan them (see risks).

### Reader UI

- On a scanned page the reader shows a one-time chip: "Scanned book — make its text searchable?" → sheet with
  language, model, size, estimated time, Wi-Fi note → Start.
- Book menu: "Recognise text" / progress ("142 of 324 pages") / "Stop" / "Remove recognised text".
- A notification while it runs in the background, with progress and Stop; it goes away when done.
- Pages recognised so far work at once; others show the existing "no text on this page" behaviour.

## Phases

Each phase ends installed on the phone, verified on *Ram C/O Anandi*, and committed.

1. **On-device spike (½ day).** Add Tesseract4Android; a debug-only action recognises the current page via
   `PdfRenderer` and logs time, memory and text. *Done when:* per-page time and peak memory on the phone are known,
   the APK size increase is known, and the library's native libraries are confirmed to load (16 KB page-size
   alignment for Android 15+). Go/no-go for the rest.
2. **Engine and storage (1 day).** `:format:ocr`: render → recognise → normalise → per-page JSON; model download with
   pinned hashes; worker with chunking, resume, cancel, page order from the current page. *Tests:* normalisation
   (chillus, ZWJ), JSON round trip, coordinate conversion (pixels at 300 dpi → PDF points, y flipped), resume after a
   stop, hash mismatch refused.
3. **Reader integration (1 day).** `/ocr/` path, `pageTextContent`, text layer + `getPageText` from recognised words,
   live update of a page on screen. *Tests:* `TextContent` built from words (positions, line ends), own text wins
   over recognised text, search finds a recognised word, a highlight on recognised text survives reopening.
4. **UI (1 day).** Chip, sheet, book menu, notification, Privacy & data entry, deletion with the book, strings in all
   locales. *Verify:* full run on the 324-page book in the background with the screen off; time and battery noted.
5. **Text quality (½–1 day).** Low-confidence handling; words broken across lines (see below); read aloud over
   recognised text checked by ear.

Total: about 4–5 days of work.

## Risks and open questions

- **Words split across lines.** Malayalam books break words at line ends without a hyphen
  (കിലോമീറ്ററു / കള്‍ക്കു). Read aloud and search treat a line end as a word break, so such words are read and
  matched in halves. Options: join when the joined form is a dictionary headword or either half isn't one (the
  offline dictionaries are available), or match search across line ends. Decide in phase 5 with real examples.
- **Highlights depend on the recognised text.** Re-running recognition (another model) changes the text layer, and
  highlights made on it may no longer resolve. v1: keep the first result; offer re-running only with a warning that
  marks on that book may be lost.
- **APK size.** Native libraries for three ABIs (`arm64-v8a`, `armeabi-v7a`, `x86_64`); measure in phase 1.
- **Sync.** Recognised text is regenerated per device in v1. Syncing it (small JSON, encrypted like books) can follow
  if recognising on every device proves annoying.
- **Other scripts.** Tamil, Hindi etc. work the same way with their models; only Malayalam and English are tested.
