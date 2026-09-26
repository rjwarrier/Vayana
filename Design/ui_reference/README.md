# Vayana UI reference

## Overview
UI design for **vayana**, an Android e-reader app ("read, undisturbed"). Covers foundations (color/type), core components, onboarding, and the primary screens: Library, Reader (clean state), Book detail, Notes, Statistics dashboard, Search, and Settings. Phone form factor, light and dark mode.

## About the Design Files
The bundled HTML file (`vayana_ui_design.html`) is a **design reference built in HTML** — a visual/interaction spec, not production code. Recreate these screens natively in the target codebase's environment (native Android/Kotlin + Jetpack Compose is the natural fit given Material 3, but use whatever the codebase already has) using its existing component patterns, not by embedding this HTML.

## Fidelity
**High-fidelity.** Colors, typography, spacing, and layout are final. Treat hex values, font families/sizes, and corner radii below as exact.

## Design System Source
Built on a Material 3 Expressive design system with a warm, bookish palette. Token names below map to that system's CSS custom properties.

## Screens

### Foundations & Components (section 01–02)
Color palette (light + dark/AMOLED variants) and component sheet: buttons, nav bar, chips, cards. Reference for all screens below.

### Onboarding (03)
Sequence of intro screens introducing vayana's value props, ending in a get-started CTA. Material 3 Expressive style, teal accent on cream/navy.

### Library (04) — home screen
- Status bar (44px) → header row: "Library" title (Playfair Display / --font-display, 28px/700) + search icon (Material Symbols, 24px) → horizontal genre/filter chip row (pill-shaped, 6px/14px padding) → book grid: cover tiles at 2:3 aspect ratio, 8px radius, genre+count label overlay (bottom, rgba(0,0,0,.55) scrim, 8px white text) → bottom nav (80px height, 5 items, active item pill-highlighted in teal-100/rgba(0,212,200,.16)) → FAB (56px, 16px radius, teal, bottom-right, offset 16px/36px).
- Nav items: Library (book), Notes (edit_note), Statistics (bar_chart), + 2 more (see component sheet section 02 for full 5-icon set).
- Both light (`--bg-primary-light` #F5F0E8, `--fg-primary-light` #1A1A1A) and dark (`--bg-primary-dark` #131C27, `--fg-primary-dark` #E8E0D0) variants shown side by side.

### Reader — clean state (05)
Full-bleed reading view: no chrome, body text in `--font-serif` (Lora), generous line-height (`--leading-relaxed`). Bottom sheet chrome (hidden by default, shown on tap) with icons: contents (list), notes (edit_note), progress (donut_large), style/text settings (text_fields), TTS (graphic_eq), search. Sheet: 32px top radius, `--shadow-lg`, 16px/8px/20px padding.

### Book detail (06)
Cover art (large, top), title/author, metadata, progress indicator, primary "Continue reading" action, secondary actions (add note, share, etc.) — see file for exact layout.

### Notes (07)
Header "Notes" (28px/700 display font) → filter tabs ("By book" pill, teal-dim background, white text, 12px/600) → note list: quote card (italic Lora excerpt) + metadata (book, page, timestamp). Bottom nav present (Notes tab active).

### Statistics dashboard (08)
Reading stats: streaks, time read, books finished, animated bar chart (teal bars, grows from base on load per motion spec). Card-based layout, 12dp radius, surface color background.

### Search (09)
Search field + results list, likely filtered by title/author/tag — see file for exact composition.

### Settings (10)
Grouped settings list (account, reading preferences, appearance, TTS/voice, about), Material 3 list-item pattern with icon + label + optional trailing control (switch/chevron).

## Interactions & Behavior
- Bottom nav: tap to switch tabs; active tab gets pill highlight in teal (`--accent` #00D4C8 light / `rgba(0,212,200,.16)` dark) — matches Material 3 ripple + highlight spec.
- Reader: tap center of page to toggle chrome sheet; sheet enter/exit uses Material 3 emphasized decelerate/accelerate easing, swift and purposeful (no heavy animation).
- FAB: standard elevation shadow (`--shadow-lg`), teal fill, opens quick-add/new note flow.
- Press state: 0.7 opacity on tap for interactive items (per design system spec).
- Statistics bar chart animates bars growing from base on screen load.

## State Management
- Theme: light/dark mode toggle (system-linked or manual), affects all `--bg-*`/`--fg-*`/`--border-*` tokens.
- Library: active filter chip, book cover data (title, author, genre, cover color/art, progress).
- Reader: chrome visible/hidden, current page/position, active panel (contents/notes/progress/style/TTS/search).
- Notes: filter mode (by book / chronological, etc.), note list data (quote, book ref, page, timestamp).
- Bottom nav: active tab index (Library / Notes / Statistics / + others).

## Design Tokens

### Colors
| Token | Hex | Use |
|---|---|---|
| `--bg-primary-light` | #F5F0E8 | light bg |
| `--bg-primary-dark` | #131C27 | dark bg |
| `--bg-amoled` | #000000/#0A0A0A | AMOLED true-black variant |
| `--bg-surface-light` | #FFFFFF | light card/surface |
| `--bg-surface-dark` | #1E2A38 | dark card/surface |
| `--bg-elevated-dark` | #253447 | dark elevated layer |
| `--accent` | #00D4C8 | teal, active states, charts |
| `--brand-green` | #4A7041 | forest green, brand anchor |
| `--fg-primary-light` | #1A1A1A | light primary text |
| `--fg-primary-dark` | #E8E0D0 | dark primary text (warm white) |
| `--fg-secondary-dark` | #8A9BB0 | dark secondary text/inactive icons |

Full palette (cream/forest/gold/navy/teal ramps) in the design system's `colors_and_type.css` — see file section 01 for swatches.

### Typography
- UI font: Google Sans (`--font-ui`)
- Display/headline: Playfair Display (`--font-display`)
- Body reading: Lora (`--font-serif`)
- Monospace: JetBrains Mono (`--font-mono`)
- Material 3 scale: Display 57px, Headline 32px, Title 22px, Body 16px, Label 12px

### Spacing & Radii
- Base unit 4dp; scale 8/12/16/24/32/48dp
- Cards: 12dp radius; buttons/chips: 8dp; bottom sheets/dialogs: 16–24dp; FAB/avatar: full (50%)
- Bottom nav: fixed, full width, 80dp height
- Content max width: 600dp

### Shadows
- Light mode: `0 1px 3px`, `0 2px 8px rgba(0,0,0,.08)`, `0 4px 16px` (small/medium/large elevation)
- Dark mode: no shadows — elevation conveyed via layered surface colors

## Assets
- Icons: Material Symbols Outlined (Google Fonts CDN), 24dp, outlined weight 300–400. Teal for active, `--fg-secondary-*`/`#8A9BB0` for inactive.
- Book covers: generated placeholder tiles (flat color fills from the forest/gold/navy/teal ramps + genre label), not final artwork — swap in real cover images during implementation.
- No custom icon font, no PNG icons, no emoji in UI chrome.

## Files
- `vayana_ui_design.html` — full design reference (all screens, both color modes, component sheet). Sections are ordered and labeled in the HTML source: 01 Foundations, 02 Components, 03 Onboarding, 04 Library, 05 Reader, 06 Book detail, 07 Notes, 08 Statistics, 09 Search, 10 Settings.
