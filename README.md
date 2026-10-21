<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_dark_reader.webp">
    <source media="(prefers-color-scheme: light)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp">
    <img alt="Vayana — an Android e-reader. Read, undisturbed." src="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp" width="820">
  </picture>

  <h1>Vayana</h1>

  <p><strong>Read, undisturbed.</strong></p>
  <p>A calm, local-first <a href="docs/USER_GUIDE.md#reading-epub-books">EPUB</a> and <a href="docs/USER_GUIDE.md#reading-pdf-books">PDF</a> reader for Android, built for regular screens and <a href="docs/USER_GUIDE.md#e-ink-and-accessibility">E‑Ink devices</a>.</p>

  <p>
    <a href="https://github.com/rjwarrier/Vayana/releases/latest"><strong>Download the latest release</strong></a>
    ·
    <a href="https://ranjithj.in/vayana/"><strong>Vayana website</strong></a>
    ·
    <a href="https://github.com/rjwarrier/Vayana/releases/tag/v0.93">Release notes</a>
    ·
    <a href="docs/USER_GUIDE.md">User guide</a>
    ·
    <a href="docs/FEATURES.md">Feature documentation</a>
    ·
    <a href="docs/GITHUB_SYNC_SETUP.md">GitHub sync setup</a>
  </p>

  <p>
    <img alt="Release v0.93" src="https://img.shields.io/badge/release-v0.93-00695c?style=flat-square">
    <img alt="Android 8.0 and newer" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
    <img alt="Material 3" src="https://img.shields.io/badge/Material-3-6750A4?style=flat-square&logo=materialdesign&logoColor=white">
  </p>

  <p>
    <a href="https://www.buymeacoffee.com/ranjithj"><img alt="Buy me a coffee" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a>
  </p>
</div>

**Languages:** English · [Español](README.es.md) · [Português (Brasil)](README.pt.md) · [Français](README.fr.md) · [Deutsch](README.de.md)

**User guides:** [English](docs/USER_GUIDE.md) · [Español](docs/USER_GUIDE.es.md) · [Português (Brasil)](docs/USER_GUIDE.pt.md) · [Français](docs/USER_GUIDE.fr.md) · [Deutsch](docs/USER_GUIDE.de.md)

## Why Vayana?

Most reading apps stop at displaying a book. Vayana treats reading as a connected practice: organize what you want to read, stay immersed in the text, keep meaningful passages, learn unfamiliar words, revisit what mattered, and carry your progress safely between devices.

Vayana does this without requiring a Vayana account or putting ads in the reading experience. Your library is local by default. Optional online features—such as [Goodreads enrichment](docs/USER_GUIDE.md#book-details-and-reading-status) and [self-managed GitHub sync](docs/USER_GUIDE.md#github-sync)—remain under your control.

## What's new in 0.93

Version 0.93 brings a redesigned **Statistics** screen (week, month, year and all-time views compared with the previous period, bar or line chart, when you read, most-read books, reading time per synced device and listened-versus-read time), **several offline dictionaries** chosen by the book's language (Open English WordNet, GCIDE, Olam English→Malayalam, 300+ FreeDict bilingual dictionaries or your own StarDict files), and a **Learn** screen that keeps only deliberate lookups of real words and shows their meanings. See the [changes since v0.92](docs/releases/v0.93.md).

Release **v0.93** includes signed phone and Wear OS APKs. Install `Vayana-v0.93.apk` on the phone and `Vayana-WearOS-v0.93.apk` on the watch; see [Wear OS setup](docs/WEAR_OS.md#install-and-build). The watch app tracks physical reading sessions; it does not render EPUB or PDF books.

## Highlights

- **[A real E‑Ink mode](docs/USER_GUIDE.md#e-ink-and-accessibility):** no animation, hardware page keys, paged app screens, timed clean refreshes and a monochrome or colour palette made for E‑Ink panels.
- **[Reading that becomes learning](docs/USER_GUIDE.md#dictionary-translation-and-vocabulary):** double-tap a word for offline definitions from dictionaries chosen by the book's language, with its meaning in your language too; keep the words you meant to look up, review them with spaced repetition and export them to Anki.
- **[Sync on your own terms](docs/USER_GUIDE.md#github-sync):** no Vayana account. Positions, sessions, notes, books and covers sync through a GitHub repository you own, with books and covers encrypted by your passphrase.
- **[One library for every book](docs/USER_GUIDE.md#offline-physical-borrowed-and-other-books):** EPUB and PDF alongside physical, borrowed and audiobooks, with page tracking, return reminders and a [Wear OS companion](docs/WEAR_OS.md) that times paper reading from your wrist.
- **[Statistics that tell you something](docs/USER_GUIDE.md#statistics-and-goals):** how this week, month or year compares with the last, when you read, which books took your time, how much you listened, and on which device.
- **[Read aloud that follows the text](docs/USER_GUIDE.md#read-aloud):** sentence-by-sentence speech with the spoken words marked, natural pauses, per-book pronunciations, and listening that keeps counting with the screen off.
- **[Free books built in](docs/USER_GUIDE.md#adding-books):** download from Project Gutenberg or your OPDS catalogs (Calibre, Calibre-Web, Standard Ebooks), and enrich books with Goodreads details and quotes.

## Screenshots

<table>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/01-notes-and-highlights.png" alt="Book notes and highlights with filters, search, and community annotations" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.md#highlights-notes-bookmarks-and-quotes"><strong>Highlights and notes</strong></a> — search, filter, edit, share, and distinguish personal annotations from community quotes.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/02-notes-library.png" alt="Notes library grouped by book" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.md#notes-library"><strong>Notes library</strong></a> — browse annotations by book with chapter and note counts at a glance.</sub>
    </td>
  </tr>
  <tr>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/03-book-library.png" alt="Vayana book library with Currently Reading card and cover grid" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.md#managing-the-library"><strong>Library</strong></a> — continue reading, search and filter a cover-first collection.</sub>
    </td>
    <td width="50%" align="center">
      <img src="docs/assets/screenshots/04-book-details.png" alt="Book details with Goodreads metadata, reading progress, and statistics" width="100%"><br>
      <sub><a href="docs/USER_GUIDE.md#book-details-and-reading-status"><strong>Book details</strong></a> — metadata, community quotes, personal rating, progress, and reading statistics in one place.</sub>
    </td>
  </tr>
</table>

## What makes it different

### [A real E‑Ink mode](docs/USER_GUIDE.md#e-ink-and-accessibility)

E‑Ink is a first-class display profile rather than a colour filter:

- Continuous motion is removed, including a static squiggly reading-progress line.
- Page-turn animation is disabled and the reader clock updates only on page turns.
- Choose **Monochrome** or **Color** under **Settings → Appearance → E-ink palette**, or during onboarding. Monochrome remains the default: covers use high-contrast greyscale and highlights become black shape-coded marks. Color preserves covers, illustrations, highlights and theme accents for color E-Ink panels.
- Both palettes retain E-Ink motion, paging and refresh behavior; color capability does not re-enable animation.
- Color E-Ink uses a dedicated high-contrast palette: neutral surfaces, black/white text, solid outlines and teal/blue/berry accents. Light and dark are supported; wallpaper colors and OLED surface variants are bypassed in this profile. Covers and reader page-theme choices remain intact. Digital contrast checks do not replace testing on the panel with its front light and refresh settings.
- Hardware page keys turn reader pages and page through long app screens.
- Library, Notes, Search, Settings, Shelves and Statistics gain screen-sized paging controls.
- Clean reader refreshes can run every chosen number of pages and at chapter/menu transitions.
- Typography controls include bolder text, alignment and hyphenation.

### [Reading that becomes learning](docs/USER_GUIDE.md#dictionary-translation-and-vocabulary)

- Double-tap a word for an offline definition. Vayana picks dictionaries by the book's language and can add the meaning in your language: install Open English WordNet, GCIDE (Webster's 1913), Olam English→Malayalam, any of FreeDict's 300+ bilingual dictionaries or your own StarDict file. Words still missing can be looked up on Wiktionary or Wikipedia with a tap.
- Only deliberate lookups of real dictionary words go to **Learn**; tap a word there to see its meaning, or remove it.
- Save looked-up words directly to a spaced-review deck.
- Discover unusual words from the current chapter with definitions.
- Export vocabulary to Anki-compatible CSV or readable Markdown.
- Review your own highlights with spaced intervals: **See soon**, **Got it**, or **Know it well**.
- Return after time away to a recap with a recent highlight and due vocabulary.

### [Read aloud that follows the book](docs/USER_GUIDE.md#read-aloud)

- Android text-to-speech reads sentence by sentence and advances through chapters.
- Spoken words or sentences are marked directly in the text.
- Start playback from a selected passage; tap anywhere in the reader to pause.
- Choose the installed TTS engine, voice, speed and pitch, and teach it how to say names and words in each book.
- Natural pauses between sentences, paragraphs and scenes; numbers, dates and Roman numerals are read the way they're meant.
- Listening keeps playing, and counts as reading time, with the screen off.
- Use a sleep timer, audio-focus handling, headset/Bluetooth controls, lock-screen controls and notification actions.

### [Sync without a proprietary service](docs/USER_GUIDE.md#github-sync)

Use a GitHub repository you control to synchronize:

- Reading position, sessions and reading dates
- Books, covers and library metadata
- Highlights, notes, shelves and Read Next
- Vocabulary, looked-up words and portable settings
- Home Library books and their reading data
- Deletions and restores across devices

Book and cover assets are encrypted with AES-GCM using your passphrase before upload. Lightweight progress sync can run while reading, and conflict messages identify the newer position with its sync time and device name. The sync setup itself can be exported as an encrypted transfer file for another device. Only changed data is uploaded, so a sync with nothing new takes about a second, and **Settings → Help & About → Data usage** shows what each sync sent and received.

Follow the [step-by-step GitHub sync setup guide](docs/GITHUB_SYNC_SETUP.md) to create a private repository, configure a least-privilege token, connect the first device, and add more devices safely.

## Features

| Area | Highlights |
| --- | --- |
| [**Library**](docs/USER_GUIDE.md#managing-the-library) | EPUB/PDF import, folder scanning, Android **Open with**, duplicate detection, grid/list views, filters, sorting, Currently Reading, Read Next, shelves, series folders and Recently Deleted |
| [**Offline and physical books**](docs/USER_GUIDE.md#offline-physical-borrowed-and-other-books) | Track books without a local file, reading progress and timers, owned/borrowed status, return dates and reminders; optionally mirror the catalog shared by Home Library |
| [**Book details**](docs/USER_GUIDE.md#book-details-and-reading-status) | Editable metadata, series and tags, ratings, reading dates, time spent, custom covers, source-file replacement, file sharing and visual reading cards |
| [**Goodreads**](docs/USER_GUIDE.md#book-details-and-reading-status) | Preview and import series details, genres, description, cover, publication year, rating and popular quotes; browser fallback when direct fetching is blocked |
| [**Book discovery**](docs/USER_GUIDE.md#adding-books) | Browse and download from Project Gutenberg by topic or language, and connect OPDS catalogs including Calibre, Calibre-Web and Standard Ebooks |
| [**EPUB reader**](docs/USER_GUIDE.md#reading-epub-books) | Contents navigation, remembered position, per-book preferences, imported fonts, themes, margins, headers/footers, publisher styles, tap zones, volume keys, fullscreen and two-column landscape layout |
| [**PDF reader**](docs/USER_GUIDE.md#reading-pdf-books) | Page and outline navigation, fit/zoom controls, text selection, dictionary lookup, highlights, underlines, notes, bookmarks, copy and quote cards for text PDFs |
| [**Typography**](docs/USER_GUIDE.md#typography-and-page-appearance) | Font family and size, line height, custom fonts, alignment, hyphenation, bolder text and optional bionic reading |
| [**Annotations**](docs/USER_GUIDE.md#highlights-notes-bookmarks-and-quotes) | Highlights, underlines, bookmarks, notes, annotation tags, overlapping-highlight merge, footnote popups and direct navigation back to a passage |
| [**Notes and quotes**](docs/USER_GUIDE.md#notes-library) | Global and per-book Notes views, Kindle `My Clippings.txt` import, Markdown export, Goodreads quote import and shareable quote-card images |
| [**Dictionary and translation**](docs/USER_GUIDE.md#dictionary-translation-and-vocabulary) | Several offline dictionaries (WordNet, GCIDE, Olam English→Malayalam, FreeDict's 300+ bilingual dictionaries, StarDict import) chosen by the book's language, with meanings in your language; phrase search, Wiktionary/Wikipedia fallback, translation, saved vocabulary, Learn words with meanings, chapter words, spaced review, known-word tracking and Anki/Markdown export |
| [**Read aloud**](docs/USER_GUIDE.md#read-aloud) | Sentence-following Android TTS with chapter advance, selection start, natural pauses, per-book pronunciations, sleep timer, voice/speed/pitch controls, audio focus, headset/Bluetooth and notification controls; listening counts as reading time, even with the screen off |
| [**Search**](docs/USER_GUIDE.md#search) | Full-text search across book text, metadata, highlights, notes and chapter names; exact phrases in quotes, results scoped by book or shelf, prefix matching and recent searches |
| [**Statistics**](docs/USER_GUIDE.md#statistics-and-goals) | Week, month, year and all-time views compared with the previous period, bar or line charts, when you read (time of day, weekday, session length), most-read books, reading time per synced device, listened-versus-read time, streaks, daily/yearly goals, finished books, date-based activity, book-level reading history and storage used on the device |
| [**Wear OS companion**](docs/USER_GUIDE.md#wear-os-companion) | Offline timers and page tracking for physical books on the watch, shared phone/watch timer controls, reading goals, a tile and a watch-face complication |
| [**Widgets and shortcuts**](docs/USER_GUIDE.md#widgets-and-app-shortcuts) | Continue Reading with read-aloud play/pause, a responsive seven-day reading-time chart and shortcuts into key library destinations |
| [**Backup**](docs/USER_GUIDE.md#backup-and-restore) | Manual portable ZIP backup/restore and automatic backups to a selected folder with daily, weekly or 30-day schedules and retention controls |
| [**Diagnostics**](docs/USER_GUIDE.md#diagnostics-and-support) | Local crash and sync history, environment details, sensitive-data sanitization, copy/share actions and a one-tap developer support path |
| [**Privacy and data**](docs/USER_GUIDE.md#privacy-and-online-services) | A Privacy & data screen showing where your data lives, what leaves the device and the device's security status; Data usage with app Wi-Fi/mobile traffic and what each sync sent |
| [**Languages**](docs/USER_GUIDE.md#settings-and-languages) | In-app language selection for English, Spanish, Portuguese, Russian, German, French, Italian, Malayalam and Tamil |
| [**Appearance**](docs/USER_GUIDE.md#settings-and-languages) | Material 3, system/light/dark modes, true black, optional Material You colour, Standard/E‑Ink profiles, motion controls and adaptive phone/tablet navigation |

Vayana can also [track physical and borrowed books](docs/USER_GUIDE.md#offline-physical-borrowed-and-other-books) without a digital file, including progress, dates, ratings, notes and statistics.

## Install

Vayana supports **Android 8.0 (API 26) and newer**.

For a first-run walkthrough, see the [Quick start guide](docs/USER_GUIDE.md#quick-start).

1. Open the [latest GitHub release](https://github.com/rjwarrier/Vayana/releases/latest).
2. Download `Vayana-v0.93.apk`.
3. Allow installation from your browser or file manager if Android asks, then open the APK.

Android may warn that the app came from outside Google Play. Release assets include a `.sha256` file so the download can be checked before installation.

For v0.93:

```text
APK SHA-256
6C1F1F8517303367CCCACBD23909312718092268769AFAA731832FBB5B815197

Release certificate SHA-256
53:2C:F4:07:D5:F0:D1:21:58:8A:5C:F1:6E:61:12:C8:F1:BB:3B:7E:D9:CF:F1:39:80:6A:7B:63:C6:43:96:C7
```

## Current scope

- **Readable ebook formats:** [EPUB](docs/USER_GUIDE.md#reading-epub-books) and [PDF](docs/USER_GUIDE.md#reading-pdf-books). Scanned PDFs without a text layer support viewing, zoom and bookmarks, but not text selection. [Physical books](docs/USER_GUIDE.md#offline-physical-borrowed-and-other-books) can be tracked without a file.
- **Online access:** Core reading, notes, dictionary and statistics work locally. [Project Gutenberg](docs/USER_GUIDE.md#download-free-books-from-project-gutenberg), [OPDS](docs/USER_GUIDE.md#connect-an-opds-catalog), Goodreads enrichment, cover browsing, translation, Wiktionary/Wikipedia lookups, dictionary downloads and [GitHub sync](docs/USER_GUIDE.md#github-sync) require internet access when used; selected text is sent to an online provider only when you choose that action. See the [privacy and online-services guide](docs/USER_GUIDE.md#privacy-and-online-services).
- **E‑Ink refresh:** [Portable E‑Ink behavior](docs/USER_GUIDE.md#e-ink-display-profile) is implemented. Vendor-specific refresh modes such as Onyx/Boox SDK modes are not yet integrated.
- **Cloud history:** Permanently deleted encrypted files may remain in older commits of the GitHub sync repository because Git history is immutable unless rewritten. See [current limitations](docs/USER_GUIDE.md#current-limitations).

## Build from source

### Requirements

- JDK 17 or newer
- Android SDK 37
- Git

Clone the repository and build a debug APK with the included Gradle wrapper:

```bash
git clone https://github.com/rjwarrier/Vayana.git
cd Vayana
./gradlew :app:assembleDebug
```

On Windows:

```powershell
.\gradlew.bat :app:assembleDebug
```

The debug APK is written under `app/build/outputs/apk/debug/`.

To build the companion too, run `./gradlew :app:assembleDebug :wear:assembleDebug`
(or `.\gradlew.bat :app:assembleDebug :wear:assembleDebug` on Windows).
Install `wear/build/outputs/apk/debug/wear-debug.apk` on the watch. Both apps must
use the same signing certificate; see [installation and verification](docs/WEAR_OS.md#install-and-build).

### Release signing

Release credentials stay outside Git. Point the build at a local Java-properties file using `VAYANA_KEYSTORE_PROPERTIES` as a Gradle property or environment variable:

```properties
storeFile=/absolute/path/to/release-keystore
storePassword=...
keyAlias=...
keyPassword=...
storeType=PKCS12
```

```bash
./gradlew :app:assembleRelease \
  -PVAYANA_KEYSTORE_PROPERTIES=/absolute/path/to/keystore.properties
```

Without an external path, the build looks for an ignored `keystore.properties` file in the repository root.

The build writes one APK per CPU type to `app/build/outputs/apk/release/`: `app-arm64-v8a-release.apk` (nearly every
phone and tablet), `app-armeabi-v7a-release.apk` (older 32-bit phones), `app-x86_64-release.apk` (emulators,
Chromebooks) and `app-universal-release.apk` (all of them, larger).

## Project structure

```text
app/                    Phone application shell and navigation
wear/                   Wear OS physical-reading companion
core/wear/              Shared timer, session and Data Layer protocol
core/                   Database, settings, files, backup, sync, diagnostics, Home Library integration and design system
feature/                Library, reader, discovery, notes, reminders, search, statistics, settings and onboarding
reader/engine-api/      Reader engine contract
reader/engine-web/      Foliate-based EPUB engine and WebView bridge
format/epub/            EPUB metadata and import support
format/pdf/             PDF metadata, rendering and text support
format/convert/         Shared document conversion support
dictionary/             Dictionary API, offline dictionaries (WordNet, GCIDE, Olam, FreeDict/StarDict) and online lookup
build-logic/            Shared Android and Kotlin build conventions
```

The app uses Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, WorkManager and a focused vendoring of Foliate's EPUB rendering stack.

## Documentation

- [Vayana website](https://ranjithj.in/vayana/)
- [User guide and detailed feature help](docs/USER_GUIDE.md)
- [Documentation index](docs/README.md)
- [Feature behavior and invariants](docs/FEATURES.md)
- [Wear OS companion setup and use](docs/WEAR_OS.md)
- [Changes since v0.92](docs/releases/v0.93.md)
- [GitHub sync setup](docs/GITHUB_SYNC_SETUP.md)
- [Architecture and implementation decisions](docs/DECISIONS.md)
- [Database changelog](docs/DATABASE_CHANGELOG.md)
- [GitHub sync design](docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md)
- [v0.93 release notes](https://github.com/rjwarrier/Vayana/releases/tag/v0.93)
- [v0.85 release notes](docs/releases/v0.85.md)

## Feedback

Use [GitHub Issues](https://github.com/rjwarrier/Vayana/issues) for reproducible bugs and focused feature requests. When reporting reader or sync problems, include the Android version, device model, display profile, and the [diagnostic export](docs/USER_GUIDE.md#diagnostics-and-support) where available—never include a GitHub token or sync passphrase.

## Support

If Vayana is useful to you, you can support development:

<a href="https://www.buymeacoffee.com/ranjithj"><img alt="Buy me a coffee" src="https://img.buymeacoffee.com/button-api/?text=Buy%20me%20a%20coffee&emoji=&slug=ranjithj&button_colour=FFDD00&font_colour=000000&font_family=Bree&outline_colour=000000&coffee_colour=ffffff" height="44"></a>

## License

Copyright (C) 2026 Ranjith Jayadevan

Vayana is free software: you can redistribute it and/or modify it under the terms of the [GNU General Public License](LICENSE) as published by the Free Software Foundation, either version 3 of the License, or (at your option) any later version.

Vayana is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the [LICENSE](LICENSE) file for details.

In short: you're free to use, study, modify and share Vayana. If you distribute a modified version, it must also be released under the GPL with its source code available.
