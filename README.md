<div align="center">
  <picture>
    <source media="(prefers-color-scheme: dark)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_dark_reader.webp">
    <source media="(prefers-color-scheme: light)" srcset="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp">
    <img alt="Vayana — an Android e-reader. Read, undisturbed." src="core/resources/src/main/res/drawable-nodpi/vayana_share_light.webp" width="820">
  </picture>

  <h1>Vayana</h1>

  <p><strong>Read, undisturbed.</strong></p>
  <p>A calm, local-first EPUB reader for Android, built for regular screens and E‑Ink devices.</p>

  <p>
    <a href="https://github.com/rjwarrier/Vayana/releases/latest"><strong>Download the latest release</strong></a>
    ·
    <a href="docs/releases/v0.85.md">Release notes</a>
    ·
    <a href="docs/FEATURES.md">Feature documentation</a>
  </p>

  <p>
    <img alt="Release v0.85" src="https://img.shields.io/badge/release-v0.85-00695c?style=flat-square">
    <img alt="Android 8.0 and newer" src="https://img.shields.io/badge/Android-8.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white">
    <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-2.3-7F52FF?style=flat-square&logo=kotlin&logoColor=white">
    <img alt="Material 3" src="https://img.shields.io/badge/Material-3-6750A4?style=flat-square&logo=materialdesign&logoColor=white">
  </p>
</div>

## Why Vayana?

Most reading apps stop at displaying a book. Vayana treats reading as a connected practice: organize what you want to read, stay immersed in the text, keep meaningful passages, learn unfamiliar words, revisit what mattered, and carry your progress safely between devices.

Vayana does this without requiring a Vayana account or putting ads in the reading experience. Your library is local by default. Optional online features—such as Goodreads enrichment and self-managed GitHub sync—remain under your control.

## What makes it different

### A real E‑Ink mode

E‑Ink is a first-class display profile rather than a colour filter:

- Continuous motion is removed, including a static squiggly reading-progress line.
- Page-turn animation is disabled and the reader clock updates only on page turns.
- Covers use high-contrast greyscale; highlights become black shape-coded marks that work without colour.
- Hardware page keys turn reader pages and page through long app screens.
- Library, Notes, Search, Settings, Shelves and Statistics gain screen-sized paging controls.
- Clean reader refreshes can run every chosen number of pages and at chapter/menu transitions.
- Typography controls include bolder text, alignment and hyphenation.

### Reading that becomes learning

- Double-tap a word for the bundled offline dictionary.
- Save looked-up words directly to a spaced-review deck.
- Discover unusual words from the current chapter with definitions.
- Export vocabulary to Anki-compatible CSV or readable Markdown.
- Review your own highlights with spaced intervals: **See soon**, **Got it**, or **Know it well**.
- Return after time away to a recap with a recent highlight and due vocabulary.

### Read aloud that follows the book

- Android text-to-speech reads sentence by sentence and advances through chapters.
- Spoken words or sentences are marked directly in the text.
- Start playback from a selected passage; tap anywhere in the reader to pause.
- Choose the installed TTS engine, voice, speed and pitch.
- Use a sleep timer, audio-focus handling, headset/Bluetooth controls, lock-screen controls and notification actions.

### Sync without a proprietary service

Use a GitHub repository you control to synchronize:

- Reading position, sessions and reading dates
- Books, covers and library metadata
- Highlights, notes, shelves and Read Next
- Vocabulary and portable settings
- Deletions and restores across devices

Book and cover assets are encrypted with AES-GCM using your passphrase before upload. Lightweight progress sync can run while reading, and conflict messages identify the newer position with its sync time and device name. The sync setup itself can be exported as an encrypted transfer file for another device.

## Features

| Area | Highlights |
| --- | --- |
| **Library** | EPUB import, folder scanning, Android **Open with**, duplicate detection, grid/list views, filters, sorting, Currently Reading, Read Next, shelves, series folders and Recently Deleted |
| **Book details** | Editable metadata, series and tags, ratings, reading dates, time spent, custom covers, source-file replacement, file sharing and visual reading cards |
| **Goodreads** | Preview and import series details, genres, description, cover, publication year, rating and popular quotes; browser fallback when direct fetching is blocked |
| **Reader** | Contents navigation, remembered position, per-book preferences, imported fonts, themes, margins, headers/footers, publisher styles, tap zones, volume keys, fullscreen and two-column landscape layout |
| **Typography** | Font family and size, line height, custom fonts, alignment, hyphenation, bolder text and optional bionic reading |
| **Annotations** | Highlights, underlines, bookmarks, notes, annotation tags, overlapping-highlight merge, footnote popups and direct navigation back to a passage |
| **Notes and quotes** | Global and per-book Notes views, Kindle `My Clippings.txt` import, Markdown export, Goodreads quote import and shareable quote-card images |
| **Dictionary** | Offline lookup, saved vocabulary, chapter words, spaced review, known-word tracking and Anki/Markdown export |
| **Search** | Fast full-text search across books, metadata, highlighted text, notes and chapter names, with prefix matching and recent searches |
| **Statistics** | Reading time and sessions, streaks, daily/yearly goals, finished books, date-based activity and book-level reading history |
| **Backup** | Manual portable ZIP backup/restore and automatic backups to a selected folder with daily, weekly or 30-day schedules and retention controls |
| **Appearance** | Material 3, system/light/dark modes, true black, optional Material You colour, Standard/E‑Ink profiles, motion controls and adaptive phone/tablet navigation |

Vayana can also track physical books without an EPUB file, including progress, dates, ratings, notes and statistics.

## Install

Vayana supports **Android 8.0 (API 26) and newer**.

1. Open the [latest GitHub release](https://github.com/rjwarrier/Vayana/releases/latest).
2. Download the APK (`Vayana-v0.85.apk` for the first release).
3. Allow installation from your browser or file manager if Android asks, then open the APK.

Android may warn that the app came from outside Google Play. Release assets include a `.sha256` file so the download can be checked before installation.

For v0.85:

```text
APK SHA-256
826EA831F37964F7B6EB67367B66DF5337975AA9E62206F33583A20B55D35352

Release certificate SHA-256
53:2C:F4:07:D5:F0:D1:21:58:8A:5C:F1:6E:61:12:C8:F1:BB:3B:7E:D9:CF:F1:39:80:6A:7B:63:C6:43:96:C7
```

## Current scope

- **Readable ebook format:** EPUB. Physical books can be tracked without a file.
- **Online access:** Core reading, notes, dictionary and statistics work locally. Goodreads enrichment, cover browsing and GitHub sync require internet access when used.
- **E‑Ink refresh:** Portable Android behavior is implemented. Vendor-specific refresh modes such as Onyx/Boox SDK modes are not yet integrated.
- **Cloud history:** Permanently deleted encrypted files may remain in older commits of the GitHub sync repository because Git history is immutable unless rewritten.

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

## Project structure

```text
app/                    Application shell and navigation
core/                   Database, settings, files, backup, sync and design system
feature/                Library, reader, notes, search, statistics, settings and onboarding
reader/engine-api/      Reader engine contract
reader/engine-web/      Foliate-based EPUB engine and WebView bridge
format/epub/            EPUB metadata and import support
dictionary/             Dictionary API and bundled StarDict implementation
build-logic/            Shared Android and Kotlin build conventions
```

The app uses Kotlin, Jetpack Compose, Material 3, Room, DataStore, Hilt, WorkManager and a focused vendoring of Foliate's EPUB rendering stack.

## Documentation

- [Feature behavior and invariants](docs/FEATURES.md)
- [Architecture and implementation decisions](docs/DECISIONS.md)
- [Database changelog](docs/DATABASE_CHANGELOG.md)
- [GitHub sync design](docs/GITHUB_SYNC_IMPLEMENTATION_PLAN.md)
- [v0.85 release notes](docs/releases/v0.85.md)

## Feedback

Use [GitHub Issues](https://github.com/rjwarrier/Vayana/issues) for reproducible bugs and focused feature requests. When reporting reader or sync problems, include the Android version, device model, display profile, and the diagnostic export where available—never include a GitHub token or sync passphrase.
