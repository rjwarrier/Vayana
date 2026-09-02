# PROMPT 1 — UI Design (paste into Claude Design / any UI design tool)

---

Design the complete UI for **an offline-first Android e-reader app** called *(pick a name — placeholder: "Codex")*. Target: Jetpack Compose + **Material 3 Expressive**. Two hard constraints shape everything: the app is **100% local** (no accounts, no cloud, no network chrome anywhere — never draw a sync icon, login screen, or "upgrade to pro" surface), and every screen must have a **legible E-Ink variant** for devices like Boox / Kobo / reMarkable-class panels.

Produce artboards at **phone (412×915)**, **tablet/foldable-open (840×1160)**, and **e-ink 7.8" (1404×1872 @ 1x rendered at 702×936)**. Light and dark for the color build; monochrome for the e-ink build.

## 1. Design language

**Material 3 Expressive**, used properly — not baseline M3 with rounder corners:

- **Shape scale with contrast, not uniform roundness.** Tokens: 0 / 4 / 8 / 12 / 16 / 20 / 28 / 32 / 48 / full. Small controls stay tight; containers get markedly rounder. Sheets and dialogs use the 32dp increased extra-large corner; sheets flatten their bottom edge. Buttons are stadium/pill. Show at least one **shape-morph** moment (e.g., FAB → menu, selected nav item pill expanding).
- **Elevation is expressed by surface tiers, never by shadow.** Use `surfaceContainerLowest → Low → Container → High → Highest`. If two things need visual separation, move one up a tier. Draw no drop shadows anywhere.
- **Motion:** spring-based, emphasized. Fast-spatial for nav transitions, slow-spatial for containers expanding, effects springs for color/opacity. Show motion intent in the artboards via before/after frames and annotations (durations + easing names).
- **Type emphasis:** M3 Expressive's emphasized type roles. Big, confident display/headline weights on the stats and book-detail hero areas; a genuinely readable body scale everywhere else.
- **Density is a user setting** (compact / standard / comfortable). Show the list surfaces in at least two densities.

**Palette:** warm, paper-adjacent, bookish — not a generic blue. Provide a full `ColorScheme` (all M3 roles, light + dark) built from a seed. Also support: dynamic color (Material You), a "softer dark" variant (raised black point, less pure-black), a "true black" OLED variant, and a color-intensity slider (muted → vivid) that scales chroma. Show the palette as a swatch board with role names and hex.

**Illustration/empty states:** line-art, single-weight, tintable with `onSurfaceVariant`, must survive 1-bit rendering.

## 2. The E-Ink build (design it as a first-class variant, not an afterthought)

Rules for every e-ink artboard:

- Pure `#FFFFFF` background, `#000000` text. No greys below ~30% for text; greys only as hairline dividers.
- **Zero animation.** No ripples, no crossfades, no shimmer skeletons — replace skeleton loaders with a static "Loading…" label.
- **Borders replace elevation.** Every card/sheet/menu gets a 1px black outline instead of a tier change.
- Larger touch targets (56dp min), thicker hairlines (1.5dp), higher stroke weight on icons.
- Charts become **outline + hatch/pattern fills**, never gradient or alpha. Heatmap becomes a 5-step pattern ramp (empty → dotted → light hatch → cross hatch → solid).
- Bottom nav becomes a fixed bordered bar with visible labels; no floating/translucent bar.
- Show a **hardware-key affordance** overlay: volume-key page turn, and a settings diagram for tap zones.

## 3. Screens to design

### 3.1 Library (home tab 1)
- Grid of book covers, **user-adjustable cover width** (slider result: 2–6 columns), optional original-aspect-ratio covers vs uniform.
- **Generated cover** for books with no artwork: title + author typeset on a color derived by hashing the title (show 4 examples).
- Per-cover **progress indicator** (thin arc or bottom bar) and an unread/finished marker.
- **Folders/groups**: drag one cover onto another to create a folder; two folder styles — *overlapping stack* and *mini-grid*. Show an opened-folder overlay.
- **Reorderable staggered grid** with drag state (lifted card, drop target, "drop to group" hint).
- **Tags**: colored chips, multi-select filter row; filter presets: All / Reading / Finished / Not started; sort by title, author, last-read, progress, import date, ascending/descending.
- **Import**: FAB → file picker / share-sheet receive / drag-and-drop (tablet). Show import progress sheet with per-file rows: parsing → converting → done / duplicate-skipped / unsupported.
- Long-press book → **bottom sheet**: read, details, edit metadata, replace file, share file, add to group, tags, delete (with confirm).
- Empty state; search entry point.

### 3.2 Reader (the most important screen)
Design the **clean state first**: full-bleed text, no chrome at all, optional header/footer info strip. The strip is configurable and each corner slot independently picks from: none / chapter title / chapter progress / book progress / battery / clock / battery+clock. Show 3 configurations.

Then design the **revealed chrome** (tap center zone):
- Top bar: back, copy chapter, bookmark toggle, overflow.
- Bottom action rail (M3 Expressive **button group**, with optional labels under icons): **Contents · Notes · Progress · Style · Read-aloud · Search**.
- Each opens a **bottom sheet / side panel** (side panel on tablet & landscape):
  - **Contents**: TOC tree with nesting, current chapter highlighted, per-chapter progress, searchable, plus a **Bookmarks** tab.
  - **Notes**: highlights + underlines for this book, grouped by chapter, with the reader's own note text, tap to jump.
  - **Progress**: chapter pager, book slider with percentage + "N pages left in chapter", back/forward history stack buttons, jump-to-percentage.
  - **Style**: font family picker (system fonts + user-imported .ttf/.otf), font size, font weight, heading size, line height, letter/word spacing, paragraph spacing, first-line indent, text alignment (auto/left/center/right/justify), side/top/bottom margins, column count (auto/1/2) + column-width threshold, writing mode (horizontal / vertical-RTL), background themes (swatch row + custom color pickers + background image with fit/blur/opacity and separate day/night image), "use publisher styles" toggle, custom CSS editor with validation.
  - **Read-aloud (TTS)**: play/pause, prev/next sentence, speed, pitch, volume, voice picker, sleep timer, sentence-highlight in text. Mini floating control when panel is dismissed.
- **Text selection menu** (the pop-over): Highlight · Underline · Color swatches (5) · Write note · Copy · **Look up word (dictionary)** · Translate · Search in book · Share as excerpt · Read from here · Delete.
- **Note editor sheet**: quoted text, color/type picker, multiline note field, chapter breadcrumb, timestamp.
- **Excerpt share card composer**: 4 templates (classic / minimal / elegant / vertical), font choice, background color/image, author + title footer, save-to-gallery and share buttons. Show all 4 templates rendered.
- **Dictionary lookup popup**: headword, phonetics, part of speech, senses, examples, "more" link — must work offline; show an empty/not-found state and a "no dictionary installed → import one" state.
- **Search in book**: query field, result list with chapter + snippet + match count, jump-to-result.
- **Footnote/annotation popup** and **image viewer** (pinch-zoom, save image).
- Page-turn interaction design: a **9-zone tap map editor** (each zone → previous / next / menu / none), a simple mode (left/right/center), swap-sides toggle, page-turn animation picker (none / slide / scroll / curl), volume-key and keyboard-shortcut toggles.
- **Reading modes**: full-screen toggle, keep-screen-awake timer, bionic-reading toggle, auto theme switch by system dark mode, minute-clock overlay for e-ink.

### 3.3 Notes (home tab 2)
- Global list of all highlights/underlines/notes across all books, grouped by book (sticky headers) or by date.
- Filters: book, color, type, has-note, tag; sort by time or chapter position; ascending/descending.
- Note card: colored left rail, quoted text, reader's note, book + chapter, relative timestamp; swipe actions (edit / delete / jump).
- Per-book note detail view with export bar: **Markdown / CSV / TXT**, "merge by chapter" option, copy-to-clipboard, share.
- Empty state.

### 3.4 Statistics (home tab 3)
A **customizable dashboard of tiles** — the user adds, removes, and reorders them. Design each tile at 1×1 and 2×1:
1. Total reading time · 2. Library totals (books/pages/words) · 3. Period summary (week/month/year/all) · 4. Books read · 5. Reading days · 6. Notes count · 7. **Reading streak** (current + best, with encouragement line) · 8. **Random highlight** (resurfaces an old note) · 9. Reading duration trend — last 7 days · 10. Last 30 days · 11. Completion progress across the library · 12. Top book · 13. Continue reading.
Plus:
- **Year heatmap calendar** (GitHub-style, 5 intensity steps + e-ink pattern variant).
- **Bar/line chart** of reading time with week / month / year / all-time segmented control, and a per-book breakdown chart.
- Tap a tile → **detail view** with the expanded chart and a table.
- "Add card" sheet listing available tiles with name + one-line description.
- Swipe-to-delete a day's record; deleted-records notice.

### 3.5 Book detail
- Hero: cover, title, author, rating (5-star, editable), tags, description (expandable), format badge, file size, import date, last-read date, "Nth book in library".
- Progress ring + "continue reading" primary button.
- Reading-time chart for this book, notes count, per-book heatmap.
- Edit mode: inline editable title/author/description/cover (pick from file or extract from book), replace source file.

### 3.6 Search (global)
- Single field searching **books and notes** simultaneously; segmented results (Books | Notes | In current book); empty "start typing" state; recent queries; result cards with highlighted match spans.

### 3.7 Settings
Grouped list, each row = icon + title + optional subtitle + control. Sections and their contents:
- **Appearance**: theme mode (system/light/dark), seed color picker + brand palette toggle, softer-dark, true-black, color intensity slider, motion (full/reduced/off), list density, language, bottom-nav visible tabs, open-book animation, **E-Ink mode master switch**, show labels under icons.
- **Library**: cover width, cover aspect ratio, folder style, default-cover shows title/author, default sort.
- **Reading**: all reader defaults, page-turn config, tap-zone editor, volume/keyboard keys, auto-hide bars, show menu on hover (desktop/tablet), haptics intensity.
- **Read aloud**: engine (system TTS), voice, rate, pitch, volume, sleep timer, mix-with-other-audio.
- **Dictionary**: installed dictionaries list, import dictionary file, default dictionary, lookup behavior (popup / full screen).
- **Content processing**: TXT/MOBI/AZW3 → EPUB conversion settings, **chapter-split rules editor** (named regex rules, built-in presets, sample tester with pass/fail results), Chinese conversion (off/simplified/traditional), code-highlight theme.
- **Storage**: storage location picker + migration progress, size breakdown (database / books / covers / fonts / cache / logs), clear cache, file-hash (dedupe) management with "calculate missing" progress.
- **Backup**: export/import full library archive (books + database + settings) to a local file; restore-from-backup list with confirm dialog.
- **Advanced**: log viewer with level filter and export, developer options, haptics test page, reset hints.
- **About**: version, changelog viewer, licenses, help.

### 3.8 Onboarding
3–4 pages: welcome · appearance (theme + **e-ink toggle right here**) · import your first book · done. Skip/back/next, page dots.

### 3.9 Systemic pieces
- **Hint banners**: dismissible inline tips (e.g., "drag one cover onto another to make a folder"), with a "show all hints again" reset.
- **Snackbars / toasts** in tonal style, undo affordance.
- **Confirm dialogs** for destructive actions.
- **Skeleton loaders** for async lists (and their static e-ink replacement).
- **Empty states** for: library, notes, stats, search, bookmarks, dictionary.
- **Tablet/foldable layouts**: navigation rail instead of bottom bar; library + detail two-pane; reader + side panel two-pane.

## 4. Deliverables

1. Palette board (all roles, light/dark/e-ink) + type scale + shape scale + spacing scale, each token **named** — the names get used verbatim in code, so name them as design tokens, not as descriptions.
2. Component sheet: buttons (all M3E variants incl. button group and split button), chips, list rows at 3 densities, cards, sheets, dialogs, sliders, segmented controls, nav bar/rail, FAB + FAB menu, snackbar, tile.
3. Every screen above, phone + tablet, light + dark.
4. The full e-ink screen set for: library, reader (clean + chrome), notes, statistics, settings.
5. Interaction annotations: what animates, what the spring is, what the e-ink fallback is.

**Do not** invent network features, accounts, paywalls, or AI chat surfaces. Everything in this app runs offline on the device.
