# Decisions

Notes for anything a reader of the code would otherwise wonder about. One paragraph per decision, newest last.

## Milestone 4 slice — reader engine (built ahead of the nominal step order, on request)

**foliate-js vendored as 8 hand-picked files, not the whole repo.** Traced the actual static+dynamic `import` graph for EPUB-only rendering (`view.js` → `epubcfi.js`/`progress.js`/`overlayer.js`/`text-walker.js` statically, → `epub.js`/`paginator.js`/`vendor/zip.js` dynamically at open-time) and vendored exactly that closure into `reader/engine-web/src/main/assets/foliate/` (MIT-licensed, `LICENSE` + `VENDORED.md` alongside). `comic-book.js`, `fb2.js`, `mobi.js`, `pdf.js`, `fixed-layout.js`, `tts.js`, `search.js` and their dependents are not vendored — add them when those formats/features are actually implemented.

**No bytes cross the JS bridge for the book file itself.** `foliate-js`'s `makeBook()` accepts a URL string and does its own `fetch()`. `FoliateBookEngine` registers a `WebViewAssetLoader` `PathHandler` on `/book/` that streams the real local `File` directly as the response body, so the WebView-side `fetch('https://appassets.androidplatform.net/book/current')` transparently gets the EPUB's bytes with zero extra copying/base64/bridge-marshalling. `/assets/` is a second registered prefix serving the vendored JS/HTML via `WebViewAssetLoader.AssetsPathHandler`. Both prefixes live under the same reserved `appassets.androidplatform.net` origin, which `shouldInterceptRequest` fully intercepts before any real network attempt — no `INTERNET` permission needed or requested (docs/PRODUCT_SPEC.md §3 and §9), and same-origin means no CORS issues between the two path prefixes.

**`BookEngine.open()` takes an optional `resumeLocator` parameter instead of a separate post-open `goTo` call.** foliate-js's `<foliate-view>.init({ lastLocation, showTextStart })` is explicitly a one-shot call that does the first render and can't safely be re-invoked or followed by a second navigation without double-init side effects (it calls `history.pushState` etc.). So resuming at a saved CFI has to happen atomically with `open()`, not as a follow-up step — `engine-api`'s `BookEngine.open(source, resumeLocator)` signature reflects that constraint rather than pretending `goTo` could do it.

**`BookEngine.open()` returns `kotlin.Result`, not `:core:common`'s `AppResult`.** `:reader:engine-api` is pure `kotlin.jvm` (§0.5: "domain has zero Android imports") and must not depend on `:core:common`, which is a `com.android.library` module (it needs Android's `@StringRes`). Plain `kotlin.Result` needs no such dependency. Likewise `BookEngine.open()` takes a `BookSource(absoluteFilePath: String)`, never the `:core:database` domain `Book` — resolving `Book.filePath` (root-relative) to an absolute path via `StorageRoots` is the feature layer's job (`ReaderViewModel`), keeping the engine module dependency-free of both Android and the data layer.

**A `RowScope.weight(1f)` call in `ReaderScreen.kt` hit the identical "internal in file" Kotlin-vs-compose-metadata symptom as the material3 issue above** (`Cannot access 'val RowColumnParentData?.weight: Float': it is internal in file` — even though the resolved `androidx.compose.foundation:foundation-android` version is the same `1.12.0` release used everywhere else without issue). Rather than re-run the whole version-archaeology process for a single non-essential layout call, routed around it: the reader's title `Text` uses `maxLines`/`TextOverflow.Ellipsis` with ordinary padding instead of `weight`-based flexible sizing. Worth investigating properly if `weight()` turns out to be needed elsewhere.

**Tap zones are a plain three-way horizontal split (prev / toggle chrome / next), not the full 3×3 grid docs/PRODUCT_SPEC.md §4.2 asks for.** Ships the "simple mode" only; the 9-zone custom editor and per-zone action mapping (§4.2, §5 settings) is later Reader-polish work, not part of getting a book open and paginating.

**Reader chrome is a single top bar with just a back button** — no bottom action rail (Contents/Notes/Progress/Style/Read-aloud/Search), no TOC panel wired to `openTocEntry` yet even though `ReaderViewModel` already exposes it, no style/theme panel even though `BookEngine.applyStyle` is implemented and callable. All of §4.2's richer chrome is later work built on this now-working open/paginate/persist-locator foundation.

## Milestone 2/3 slice — data layer + EPUB import (built ahead of the nominal step order, on request)

**Minimal `Book` Room schema now, not the full docs/PRODUCT_SPEC.md §2 model.** `BookEntity`/`Book` cover the fields needed to import and list a book (title/author/description/cover/file/format/hash/rating/progress); `BookGroup`, `Tag`, `Annotation`, reading sessions, etc. are deliberately absent — they land with the features that use them (Notes, Statistics, groups/tags in a later Library pass) rather than as unused columns now. `DATABASE_VERSION = 1`; see `docs/DATABASE_CHANGELOG.md`, which every future bump must also update per the product specification's "real migrations from day one."

**No hand-rolled EPUB library dependency — `EpubParser` reads `container.xml` + the OPF directly with `android.util.Xml`.** The app is 100% offline and EPUB's relevant subset (locate the OPF via `META-INF/container.xml`, read `dc:title`/`dc:creator`/`dc:description` and the cover from `<manifest>`, either EPUB3's `properties="cover-image"` or EPUB2's `<meta name="cover">`) is small and stable enough to hand-parse without pulling in a third-party parsing library this early — revisit if TOC/spine/CFI handling (needed for the actual reader engine, milestone 4) turns out to want a real library instead.

**Import picker filters `*/*`, not `application/epub+zip`, deliberately.** The user asked for MOBI to be selectable too. SAF mime-type filtering can't distinguish by extension the way a real file browser does, and there's no MOBI parser yet (`:format:convert` is still an empty stub) — so the picker shows everything, and `LibraryViewModel.importOne` classifies by extension after the fact, reporting non-EPUB picks as "unsupported" in the one-line import summary rather than hiding them from the picker. Revisit once `:format:convert` has a real MOBI→EPUB path.

**Import feedback is a single Snackbar summary line, not docs/PRODUCT_SPEC.md §3's fuller "import progress sheet with per-file rows: parsing → converting → done / duplicate-skipped / unsupported."** Built as the minimum needed to make the FAB → picker → persisted-book loop real and correct (`insertIfNew` dedupes by SHA-256, computed once per file by `BookFileImporter`) without stalling on UI polish that doesn't change correctness. Upgrade to the real per-file sheet (and move the copy/hash/parse work into a WorkManager job per the product specification §4.1) when Library gets its full pass.

**Coil 3.6.1 added for cover images.** Not previously in the version catalog. Local-file-only usage (`AsyncImage(model = File(...))`) — no network image loading anywhere, consistent with §9's zero-network-calls rule. `coil-compose-android` needed compileSdk 37 (`minCompileSdk=37` in its AAR metadata), already satisfied by the AGP 9 stack above.

## Milestone 1 — scaffold

**Design source of truth.** `Design/ui_reference/` (high-fidelity per its README) is authoritative for color hex values, type scale, and spacing/radii — it supersedes the broader guidance in `Design/DESIGN_SPEC.md`. Where the two agree, no conflict; where they don't (e.g. exact nav tab count), the UI reference wins. `docs/PRODUCT_SPEC.md` governs engineering/architecture; the UI reference governs the visual system.

**Bottom nav is 3 tabs, not 4.** The product specification §8 step 1 says "navigation shell with 4 tabs," but every bottom-nav mock in the UI reference shows exactly 3: Library, Notes, Statistics (`auto_stories` / `edit_note` / `bar_chart`). Search is a header icon on Library, not a tab. Settings has no bottom-nav slot in the UI reference either. Went with the design's 3 tabs since visual fidelity is called out as final; Search and Settings are reached via icons/menus, wired in a later milestone.

**Versions.** No version numbers were pinned by either specification, so current (as of build date) stable releases were pulled live from Google's and Maven Central's `maven-metadata.xml` rather than guessed from training data — training-time "latest" would already be stale, with one deliberate exception: **AGP is pinned to 8.13.2, not the newer 9.4.0.** AGP 9 was tried first and hit a chain of breaking changes still settling across the toolchain — `CommonExtension` lost its type parameters and its `defaultConfig{}`/`compileOptions{}` lambda sugar; AGP 9's default built-in-Kotlin mode rejects the `org.jetbrains.kotlin.android` plugin outright ("no longer required... remove it", https://issuetracker.google.com/438678642); and once that plugin was removed, KSP (which Hilt and Room both depend on) turned out not to support built-in Kotlin at all (`KSP is not compatible with Android Gradle Plugin's built-in Kotlin`). Opting back out via `android.builtInKotlin=false` then hit a third wall: Kotlin Gradle Plugin 2.2.20 casts AGP's extension to a legacy `BaseExtension` internally, and AGP 9.4.0's restructured extension classes no longer support that cast (`ApplicationExtensionImpl$AgpDecorated_Decorated cannot be cast to ... BaseExtension`). Rather than chase a three-way compatibility gap between AGP 9, KSP, and KGP that clearly hasn't stabilized yet, the whole build stays on the mature AGP 8.x line (8.13.2), which is unaffected by any of this. Revisit once KSP2 and KGP catch up to AGP 9. Everything else: Kotlin 2.2.20, Compose BOM 2026.08.00, Material3 1.4.0 (first stable release with the Expressive APIs promoted out of alpha), Room 2.8.4, Hilt 2.60.1, Gradle 8.14 (see the wrapper note below for why that specific version).

**Konsist over a custom Android Lint rule for §0's "no bare .dp/.sp/hex color" rule.** The product specification explicitly offers either "a custom lint rule (or a Konsist/detekt test)." A real custom `Lint` `Detector`/`Issue` needs its own published lint-rule artifact wired into every consuming module's `lintChecks` config — much more moving parts than a JVM architecture test. Konsist test lives alongside `:core:designsystem` (the one package literals are allowed in) and runs as part of `test`, which is already in the `./gradlew build test lint` gate the product specification asks for at every milestone. Wired via a `vayana.konsist` convention plugin so any module can opt in later.

**Gradle wrapper pins 8.14.** AGP has a hard Gradle ceiling: AGP 9.4.0 needs newer Gradle internals than 9.3.1 has (`NoClassDefFoundError: org/gradle/features/binding/ProjectTypeBinding`), while AGP 8.x (the line actually used — see the AGP 8.13.2 note above) explicitly *rejects* Gradle 9.6+ (`Plugin 'com.android.internal.application' relies on ... 'InternalProblems', a Gradle internal API that was removed in Gradle 9.6.0 ... use Gradle 9.5`, https://docs.gradle.org/9.7.1/userguide/upgrading_version_9.html#agp_8x_incompatible). 8.14 is the newest version confirmed to work with AGP 8.13.2 without hitting either wall — it was already cached locally (`~/.gradle/wrapper/dists/gradle-8.14-bin`), unlike the two newer per-AGP-attempt versions this session touched (9.3.1, 9.7.1) which are documented here only because the trial-and-error is worth knowing about, not because either ended up wired into the project. `gradle wrapper --gradle-version <x> --no-validate-url` was needed throughout because the wrapper task's own URL-reachability check fails in this sandbox (plain Java `HttpURLConnection` can't complete the TLS handshake to GitHub's release-asset redirect target that `services.gradle.org` forwards to, even though `curl` on the same host has no trouble).

**Hilt is pinned to 2.58, not current-stable 2.60.1.** Dagger/Hilt release notes: "This release adds AGP 9 support for the Hilt Gradle plugin... AGP 9 is now a requirement" (2.59) — confirmed at runtime too (`The Hilt Android Gradle plugin is only compatible with Android Gradle plugin (AGP) version 9.0.0 or higher (found ... 8.13.2)`). 2.58 is the last release before that cutoff. This is the same AGP-8-vs-9 fault line as the Gradle/AGP version note above — Hilt's own ecosystem has moved to assuming AGP 9, one more reason 9.x will be worth revisiting once KSP and KGP catch up to it.

**`extensions.getByType<CommonExtension<*, *, *, *, *, *>>()` doesn't find the registered extension at runtime; the raw `Class` overload does.** `AndroidComposeConventionPlugin` needs the shared `CommonExtension` interface (not `ApplicationExtension`/`LibraryExtension` specifically) because it runs on both app and library modules. The reified Kotlin `getByType<T>()` resolves via a `TypeOf` token that captures the full generic signature, and a star-projected `CommonExtension<*,*,*,*,*,*>` apparently isn't considered the same `TypeOf` as the concrete `BaseAppModuleExtension` AGP actually registers (`Extension of type 'CommonExtension<?, ?, ?, ?, ?, ?>' does not exist`, even though `BaseAppModuleExtension` was right there in the list of registered types). `extensions.getByType(CommonExtension::class.java)` — the raw-`Class` overload — does a plain `isAssignableFrom` check instead and finds it immediately.

**Final call: AGP 9.4.0 + Gradle 9.7.1 + Kotlin/KSP 2.3.21/2.3.11 + Hilt 2.60.1 + material3 1.5.0-alpha27 + compileSdk/targetSdk 37 — the whole app builds and packages end-to-end (`./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`, real APK produced).** This reverses the AGP-8.13.2 pin recorded earlier in this file: with material3 1.4.0 stable keeping every Expressive entry point `@PublishedApi internal` (see the next entry), staying on AGP 8 would have meant giving up `MaterialExpressiveTheme`, `MotionScheme`, and everything else docs/PRODUCT_SPEC.md §1 explicitly names — asked the user which way to go; chose the AGP 9 path. Two more AGP-9-specific fixes were needed beyond what's recorded below: (1) `CommonExtension` needs the non-generic form (`CommonExtension`, not `CommonExtension<*,*,*,*,*,*>`) and property-access instead of `defaultConfig{}`/`compileOptions{}` lambdas, matching the AGP-9-only attempt earlier in this doc; (2) a **second** flag beyond `android.builtInKotlin=false` is required — `android.newDsl=false` — because AGP 9's new DSL doesn't support applying the classic `org.jetbrains.kotlin.android` plugin at all (`ApplicationExtensionImpl$AgpDecorated_Decorated cannot be cast to ... BaseExtension`, with AGP's own error message naming `android.newDsl=false` as the workaround). Both flags are deprecated-but-functional escape hatches AGP prints warnings about on every run; revisit removing them once KSP/Hilt support AGP's built-in-Kotlin path. compileSdk/targetSdk moved from 36 to 37 throughout (`AndroidCommon.kt`, `AndroidApplicationConventionPlugin.kt`) because material3 1.5.0-alpha27 pulls its own compose-ui/-foundation/-animation generation (`1.12.0-beta01`) that requires it — AGP auto-downloaded SDK Platform 37 on first build.

**`material3` is pinned to `1.5.0-alpha27`, not the stable `1.4.0` the Compose BOM suggests — the Expressive theme entry points are genuinely `internal` in the stable release, not a tooling bug.** `core:designsystem` failed compiling `VayanaTheme.kt`/`MotionSchemes.kt` with `Cannot access 'fun MaterialExpressiveTheme(...)': it is internal in file`. First guess was a Kotlin-compiler-vs-artifact metadata mismatch (bumping the Kotlin/KSP pairing to a newer compiler generation), but that path dead-ended: build-logic itself is compiled by Gradle's *own embedded* Kotlin compiler (not the project's declared `kotlin` version), and every Gradle release available under the AGP-8.13.2-vs-9.5 ceiling (checked 8.14, 9.1.0, 9.2.1, 9.3.1 by reading each distribution's bundled `kotlin-stdlib-*.jar` filename) tops out around Kotlin 2.2.x — nowhere near new enough to load a 2.3-metadata `ksp-gradle-plugin` jar placed on build-logic's classpath (`Module was compiled with an incompatible version of Kotlin. The binary version of its metadata is 2.3.0, expected version is 2.0.0`). Reverting to Kotlin 2.2.20 and re-testing in isolation confirmed the real cause: `javap` on `material3-android-1.4.0.aar` shows `MaterialExpressiveTheme`/`MotionScheme`/the 8-arg `Shapes` constructor as `public static final` bytecode with **no name-mangling suffix** — which rules out ordinary Kotlin `internal` (that mangles top-level names) and points at `@PublishedApi internal`: public bytecode so androidx's own inline functions can reach it across module boundaries, but the Kotlin *compiler* still enforces `internal`-only access for external callers. In other words, 1.4.0 stable deliberately doesn't expose these yet, despite `docs/PRODUCT_SPEC.md` §1's "material3 1.4+ for Expressive APIs" assumption — confirmed empirically by pointing `compose-material3` at `1.5.0-alpha27` (overriding the BOM's suggested 1.4.0 via an explicit `version.ref`) and getting a clean `compileDebugKotlin`. Revisit once a stable material3 release promotes these out of `@PublishedApi internal`.

**`material3-adaptive:1.3.0` dropped for now.** Added preemptively for later tablet/foldable work (not yet used by any code); it requires AGP 9.1+/compileSdk 37 same as several other bleeding-edge androidx releases below. Removed from `core:designsystem` until a milestone actually needs adaptive panes, at which point compatible versions need re-checking.

**A cluster of "latest stable" androidx versions all require AGP 9.1+/compileSdk 37, incompatible with the AGP 8.13.2 pin above — rolled back individually.** `./gradlew :app:checkDebugAarMetadata` was the tool that surfaced this (42 flagged dependencies at once): `androidx.core:core-ktx:1.19.0`, `androidx.hilt:hilt-navigation-compose:1.4.0`, `androidx.navigation:navigation-compose:2.10.0`, `androidx.lifecycle:lifecycle-runtime-compose:2.11.0` / `lifecycle-viewmodel-compose:2.11.0`, and the whole Compose BOM `2026.08.00` graph (`compose-ui`, `-foundation`, `-animation`, `-material-ripple` at `1.12.0`) all declare `minAndroidGradlePluginVersion=9.1.0` / `minCompileSdk=37` in their AAR metadata. Each was walked back to the newest version whose `aar-metadata.properties` still declares `minCompileSdk<=36`/`minAndroidGradlePluginVersion<=8.x` (checked by downloading each candidate `.aar` and reading `META-INF/.../aar-metadata.properties` directly rather than guessing): `coreKtx = "1.18.0"`, `hiltNavigationCompose = "1.3.0"`, `navigationCompose = "2.9.5"`, `lifecycle = "2.9.4"`, `composeBom = "2025.10.01"` (which still pins `material3 = 1.4.0`, identically to `2026.08.00` — material3 hasn't had a newer stable release, so this downgrade cost nothing on the Expressive-API front). This is the AGP-8-vs-9 fault line yet again, now hitting the wider androidx ecosystem, not just AGP/Hilt themselves — expect it to keep coming up in every future milestone that adds a new androidx dependency until this project moves to AGP 9.

**Milestone 1 verified end-to-end.** `./gradlew :app:assembleDebug` → `BUILD SUCCESSFUL`, real `app-debug.apk` produced; `./gradlew :core:designsystem:testDebugUnitTest` (the Konsist architecture test) → `BUILD SUCCESSFUL`. Installed and launched on a local AVD (`Test_Phone`, API level matching compileSdk 37): dark theme renders with the correct navy background / warm-white text / Playfair Display headline / Lora body per the UI reference, 3-tab bottom nav shows Library selected with correct icons and labels. One visual gap fixed in the same pass: `NavigationBarItem`'s default selected-tab color is M3's `secondaryContainer` (renders green with our scheme, since `secondary`/`secondaryContainer` map to the forest-green ramp), but the UI reference's active-tab pill is teal (`--accent`, i.e. `primary`/`primaryContainer`) — `VayanaBottomBar.kt` now passes explicit `NavigationBarItemDefaults.colors(...)` for that. Screen content beyond the nav shell (actual book grid, notes list, stat tiles) is still just the milestone-1 empty state per docs/PRODUCT_SPEC.md §8 step 1 — everything else in §4 is later milestones.

**`ProjectExtensions.kt` in `build-logic/convention` must declare a package.** A `Project.libs` helper was first written with no `package` line (default package). Because `build-logic` is an included build supplying plugins, any top-level declaration in the default package leaks into the implicit-import scope of *every* Kotlin DSL build script in the whole multi-project build — it silently shadowed Gradle's own generated `libs` version-catalog accessor (`LibrariesForLibs`) with the plain `VersionCatalog` from this helper, so every `libs.androidx.foo.bar` in module build files failed to resolve. Fixed by giving the file a real package (`com.vayana.buildlogic.convention`) and importing `libs` explicitly wherever a convention plugin needs it.

**Module shape mostly mirrors the product specification §1 verbatim** (`:core:*`, `:feature:*`, `:reader:engine-api`/`:reader:engine-web`, `:format:*`, `:dictionary:*`). `:reader:engine-api` and `:dictionary:api` are pure `kotlin.jvm` modules (no Android dependency) since the product specification §0.5 requires domain interfaces to carry zero Android imports. Everything else that touches Room/DataStore/WebView/Compose is `com.android.library`.

## Review fixes — September 2026

Restore now integrity-checks and migrates the staged database with the same Room
migrations used by the app, then checks foreign keys and active book/cover files.
The staged Room identity table is removed first so even a copied identity hash
cannot bypass schema validation; live data is untouched until validation passes.
The validation connection uses TRUNCATE journaling so its closed database can be
copied without a WAL. Regression tests use the checked-in schemas (versions 1–8)
with Robolectric 4.13 at API 28, without changing the app's toolchain pins.

Bionic formatting marks inserted elements as CFI-transparent. A small documented
patch in vendored `epubcfi.js` applies the same flattening filter when generating
and resolving locators. DOM regression tests run with `npm ci` and `npm test` in
`reader/engine-web/tests`; these dependencies are only for tests, not app assets.

Reading time and completed sessions now share the five-minute idle deadline.
The tracker caps late ticks at that deadline, excludes the gap before the next
interaction, and makes repeated pause/resume callbacks idempotent. Tablet
non-reader destinations consume safe drawing insets at the navigation host.

## Product-depth scan — quiet local utility before new infrastructure

**Shipped small, ethos-aligned features that use existing local data before adding migration-heavy systems.** The product specification's strongest product through-line is calm, offline reading: comfort controls, local ownership of notes, and reflective statistics without accounts or telemetry. So this pass makes already-declared reader behavior settings real (`keep screen awake`, volume-key page turns), adds local Notes search plus plain-text sharing, and turns the empty Statistics tab into a local dashboard from the existing `Book` and `Annotation` repositories. Full reading-session heatmaps, FTS search, dictionaries, TTS, tags, and backup remain good next milestones, but they need either new schema or engine/API work; these additions avoid placeholder UI while keeping the app useful and offline.

**Annotation management stays local and direct.** Notes now expose edit/delete in the global Notes tab, reader selections can be copied through the system clipboard, and the reader chrome can save a bookmark at the current CFI. These all use the existing `AnnotationRepository` contract instead of adding a second notes service or export format. Bookmark creation dedupes exact current-CFI matches so a repeated tap does not quietly multiply identical bookmarks; broader bookmark organization can wait until tags/groups land.

## PDF support — foliate's pdf.js path, pages only

**PDFs open in the existing WebView engine through foliate-js's `pdf.js` adapter and Mozilla pdf.js, not a native
`PdfRenderer` engine and not a PDF→EPUB conversion.** Conversion (the original spec plan) wrecks layout, tables and
scanned books; `PdfRenderer` is bitmap-only below API 35 and would need a second `BookEngine`. The foliate path reuses
the bridge, locators, TOC, progress, sync and resume logic, and pdf.js's text layer leaves room for selection,
dictionary and read aloud later. Cost: ~5 MB of assets.

**`OpenBook.fixedLayout` tells the reader a book is pre-paginated.** The reader then hides the typography controls,
read aloud and the chapter word list. A page's CFI is foliate's section CFI (`epubcfi(/6/N)`); a highlight's CFI points
into pdf.js's text layer (`epubcfi(/6/N!/4/6/...)`), which is deterministic for a given pdf.js version, so selections,
highlights, notes and lookup reuse the EPUB paths unchanged.

**PDF marks get their own layer, not foliate's overlayer.** foliate's fixed-layout renderer has no overlayer, and pdf.js
rebuilds the text layer on every render (zoom, theme). bridge.js draws highlights, underlines and search hits as
absolutely placed boxes in a `.vayana-marks` layer between the canvas and the text layer, redrawn on the
`vayana-page-rendered` event our pdf.js patch fires. Multiply blend on light pages, screen on dark ones, so print stays
legible through the mark.

**Zoom lives in bridge.js, not in the WebView or Kotlin.** Pinch touches reach the page document (the top page is
`user-scalable=no`), so bridge.js tracks two-finger distance in screen coordinates, previews with a CSS transform, and
on release sets foliate's `zoom` attribute to an absolute scale so pdf.js re-renders sharp; scroll is adjusted to keep
the focal point fixed (`zoomScrollFor`, tested). Kotlin only ignores multi-touch gestures as taps. Double-tap on a word
still looks it up; elsewhere it toggles zoom.

**The text layer is built once per page and rescaled after.** pdf.js's `TextLayer.update({ viewport })` repositions the
existing spans; rebuilding on every zoom would detach the text nodes that selections, marks and read-aloud ranges hold.

**PDF pages must render with the screen off.** Read aloud turns pages in the background, and pdf.js continues a long
page's drawing on animation frames, which stop then; its `onContinue` hook is handed the same frame-paced scheduler,
so our pdf.js patch switches frame pacing off for page renders and yields between slices through a `MessageChannel`
(timers are throttled in a hidden page; messages aren't).

**Large PDFs are read on demand.** `disableAutoFetch` keeps pdf.js from reading the whole file into the renderer in the
background; with 1 MB ranges a 6.7 MB picture book reads 1.5 MB to open. Search caches each page's text for the open
book, so only the first search pays for reading every page.

**PDF "typography" means fitting the page, not restyling it.** Fonts and spacing are printed into a PDF, so the style
panel offers Crop margins, Fit width and Darken text instead. The printed area comes from sampling the rendered canvas
(160px wide, differences from the top-left pixel's colour), and the crop is the union of the pages measured so far:
one box for the book, so the text keeps its size and place from page to page instead of zooming in on a chapter's
short last page. Pages with nothing to crop (full-bleed covers) leave it alone. Zoom is
relative to the box. Scales within 3% of the current one keep the current render, so turning between pages with
similar margins doesn't render twice. Darken text reuses Bolder text rather than adding a setting: same intent, one
switch across formats. A reflow (text-only) view was considered and left out: it drops images and layout and splits
highlights between two views.

**Dark themes recolour through pdf.js `pageColors`, not a CSS invert.** pdf.js paints text and vector art in the given
foreground/background and leaves images untouched, so photos don't turn negative. White-paper themes (light, E-Ink)
pass no colours and keep the document's own.

**Progress counts a page as read once it's on screen.** foliate reports a fixed-layout page's fraction as its start
(last page of 10 = 90%), which would never reach the finished threshold. bridge.js reports `(index + 1) / total` and
maps fractions back with `ceil(f * total) - 1`, so the slider, resume-by-fraction and the reported value round-trip
to the same page (tests/fixed-layout.test.mjs).

**Import metadata comes from the PDF itself, without a PDF library.** `:format:pdf` reads `/Title` and `/Author` from
the trailer's Info dictionary by scanning the file's head and tail (`PdfInfoReader`), falling back to the file name
when Info sits in a compressed object stream or the file is encrypted; the cover is page 1 rendered by Android's
`PdfRenderer`. A PDF that needs a password is reported as unsupported.
