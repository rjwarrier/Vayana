# UI design audit — 4 October 2026

## Scope and basis

Source review of the shared theme, navigation, Library/book details, Notes, Search, Statistics/review screens, Settings/About, Reader controls, Onboarding, Help, Gutenberg and OPDS. Visually inspected the four screenshots in `docs/assets/screenshots`. These are saved reference images, not captures of the current build. No live device walkthrough, font-scale test or pixel-level contrast audit was performed. Findings below distinguish implementation defects from visual recommendations.

The current `MaterialExpressiveTheme`, semantic color roles, tokenized spacing and intentional JetBrains Mono UI are the baseline. Older design documents contain superseded product requirements and conflicting visual specifications; they should not be treated as blanket acceptance criteria. Reader page palettes, generated covers and exported share artwork can legitimately differ from app chrome. Custom floating navigation and monospace typography are not inherently M3 violations.

Reference: [Material 3 in Compose](https://developer.android.com/develop/ui/compose/designsystems/material3) explains semantic colors, typography and components. [Compose accessibility defaults](https://developer.android.com/develop/ui/compose/accessibility/api-defaults) recommends 48dp interactive targets and explains automatic touch-bound expansion; small visual elements alone do not prove small effective targets.

## Findings

### 1. High: dense heatmap makes individual days difficult to select

`feature/statistics/src/main/kotlin/com/vayana/feature/statistics/StatisticsScreen.kt:1028` attaches a separate click handler to every 11dp cell (`Sizes.kt:74`). Automatic touch expansion cannot give densely packed cells independent, non-overlapping 48dp regions. Cells also lack date/minutes descriptions and selected-state semantics. This is an interaction/accessibility problem, not a request to enlarge every decorative chart mark.

Keep the compact chart as an overview and open a larger day picker or accessible list for inspection. Alternatively implement chart-level selection with an accessible equivalent. Verify neighboring-day selection and TalkBack on a device.

### 2. Medium: Softer dark is actually darker

`core/designsystem/src/main/kotlin/com/vayana/core/designsystem/theme/ColorSchemes.kt:89` maps softer-dark background/surface to `M3SurfaceContainerLowestDark` (#090F0E), below the normal dark background (#0E1513). It also shifts the container tiers downward. The comment promises a raised black point, but the mapping does the reverse. This affects the built-in palette; dynamic colors follow a separate branch.

Define a genuinely raised background and ordered surface tiers. Compare normal, softer and true-black themes side by side before adopting values.

### 3. Medium: motion preference is not applied consistently

`core/designsystem/src/main/kotlin/com/vayana/core/designsystem/theme/EinkProgressIndicator.kt:89` stops the wavy progress animation only for E-Ink. Color displays always receive a nonzero wave speed, even with Motion Off. The shared loading indicator in `component/ExpressiveControls.kt:60` similarly branches only on display profile before using the animated M3 indicator.

Make persistent animation follow both display profile and motion preference. Check Library, book-detail progress and loading states with Off/Reduced settings.

### 4. Medium: search metadata looks actionable but does nothing

`feature/search/src/main/kotlin/com/vayana/feature/search/SearchScreen.kt:386` builds match-field badges as `AssistChip(onClick = {})`. These advertise an action, create focus stops and can consume taps within an otherwise clickable result without doing anything.

Render noninteractive tonal labels, consistent with metadata badges elsewhere, or implement a useful chip action.

### 5. Medium: E-Ink treatment stops short of cards and charts

Notes cards (`NotesScreen.kt:990`), Search result surfaces (`SearchScreen.kt:367`) and the shared dialog surface (`ExpressiveDialogs.kt:42`) use tonal fills without the explicit E-Ink outline already present on reader selection cards (`ReaderSelectionCard.kt:168`). The Statistics heatmap (`StatisticsScreen.kt:1033`, `:1058`) relies on alpha levels for both cells and legend, with no patterned variant.

Introduce a shared profile-aware container treatment and use discrete patterns for chart levels. E-Ink refresh behavior must be independent of color capability: enforce monochrome only for a Monochrome palette, and preserve useful color for Color E-Ink while retaining outlines and non-color cues. Exact on-panel legibility still needs hardware validation.

### 6. Medium: half-star rating splits one target into two

`feature/library/src/main/kotlin/com/vayana/feature/library/BookDetailScreen.kt:1644` creates a 48dp star container, then divides it into two independently clickable halves. Each half is only 24dp wide before touch expansion; neighboring expanded targets compete. The control does not expose rating selection as a coherent adjustable value.

Use one star target with an explicit half-step adjustment, or a rating dialog/slider with 0.5 increments and value semantics. Validate at increased font/display scale and with TalkBack.

### 7. Medium: filter controls have three different shape/state treatments

Library uses default `FilterChip` styling (`LibraryScreen.kt:1035`); Notes forces full pills (`NotesScreen.kt:339`); Settings morphs from small corners to full pills and uses a primary-filled selection (`SettingControls.kt:299`). Gutenberg also uses default chips. The Library-versus-Notes difference is visible in the saved screenshots and remains supported by source.

Create a shared filter-chip style for equivalent filtering actions. Keep Settings choices distinct only where their behavior warrants it; small mutually exclusive choices can use a connected segmented group. M3 permits shape variation, so the issue is inconsistent treatment of equivalent controls rather than rounded rectangles being invalid.

### 8. Low: destructive dialogs do not share one visual convention

OPDS catalog removal (`feature/opds/src/main/kotlin/com/vayana/feature/opds/OpdsScreens.kt:180`) uses a stock AlertDialog and ordinary primary Button. Book deletion uses `ConfirmActionDialog` with destructive error roles and the app's expressive header (`BookDeletionDialogs.kt:90`). Both are valid components, but destructive actions have inconsistent emphasis and composition across features.

Reuse the shared confirmation pattern, with severity appropriate to removing a catalog connection rather than deleting books.

### 9. Low: About has an isolated hardcoded yellow CTA

`feature/settings/src/main/kotlin/com/vayana/feature/settings/SettingsAbout.kt:322` renders Support with #FFDD00, black content and a black border, independent of dynamic color, dark mode and E-Ink. This is a visible exception to the otherwise semantic palette.

If the donation brand color is intentional, retain it as a documented brand exception and provide an E-Ink treatment. Otherwise use a tonal app button and retain the coffee icon/wording for identity. This is a visual recommendation, not a contrast failure.

### 10. Low: Notes cards have more visual layers than their content needs

`feature/notes/src/main/kotlin/com/vayana/feature/notes/NotesScreen.kt:990` nests a rounded quote surface and rounded note surface inside another rounded card, plus a pill and a three-action footer. The saved annotation screenshot shows the resulting density clearly. Quoted content competes with repeated metadata and container boundaries.

Consider one main card surface, a modest quote marker or rule, and lighter metadata. Preserve clear distinction between the original passage and the reader's own note. This is an editorial recommendation, not an M3 prohibition on nested surfaces.

## Suggested order

1. Fix misleading/no-op interactions, rating and heatmap selection, softer-dark mapping and Motion Off behavior.
2. Consolidate chip styles, E-Ink containers/chart patterns and confirmation composition.
3. Review Notes density and the branded support CTA with current screenshots.

## Validation still needed

Run the current app in light, dark, softer-dark, true-black, dynamic-color and E-Ink profiles; check compact/expanded layouts, increased font scale, TalkBack and motion settings. Saved screenshots show an older book-detail composition, so its bright statistics card and floating-bar overlap should not be reported as confirmed current defects without a fresh capture. No application code was changed for this audit.
