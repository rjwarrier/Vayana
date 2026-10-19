# Vayana user guide

**Languages:** English · [Español](USER_GUIDE.es.md) · [Português (Brasil)](USER_GUIDE.pt.md) · [Français](USER_GUIDE.fr.md) · [Deutsch](USER_GUIDE.de.md)

This guide describes the features available in Vayana 0.93 and explains the most common reading, library, backup, sync, and support workflows. Vayana is a local-first Android reader for EPUB and PDF books. It also tracks physical, borrowed, audiobook, and other offline books without requiring a digital file.

> **Release 0.93:** Download both signed APKs from the [release page](https://github.com/rjwarrier/Vayana/releases/tag/v0.93) for the [Wear OS companion](WEAR_OS.md). See the [release notes](releases/v0.93.md) for changes since v0.92.

> Screenshots in this guide were captured from a development device using public-domain Project Gutenberg books. Screen layout can vary with display size, Android version, theme, language, and E-Ink settings.

## Contents

- [Quick start](#quick-start)
- [Navigation](#navigation)
- [Adding books](#adding-books)
- [Managing the library](#managing-the-library)
- [Book details and reading status](#book-details-and-reading-status)
- [Reading EPUB books](#reading-epub-books)
- [Reading PDF books](#reading-pdf-books)
- [Highlights, notes, bookmarks, and quotes](#highlights-notes-bookmarks-and-quotes)
- [Dictionary, translation, and vocabulary](#dictionary-translation-and-vocabulary)
- [Read aloud](#read-aloud)
- [Search](#search)
- [Statistics and goals](#statistics-and-goals)
- [Wear OS companion](#wear-os-companion)
- [Widgets and app shortcuts](#widgets-and-app-shortcuts)
- [Backup and restore](#backup-and-restore)
- [GitHub sync](#github-sync)
- [E-Ink and accessibility](#e-ink-and-accessibility)
- [Settings and languages](#settings-and-languages)
- [Diagnostics and support](#diagnostics-and-support)
- [Privacy and online services](#privacy-and-online-services)
- [Troubleshooting](#troubleshooting)
- [Current limitations](#current-limitations)

## Quick start

1. Install the APK from the [latest GitHub release](https://github.com/rjwarrier/Vayana/releases/latest).
2. Complete onboarding and choose the display profile that matches the device: **Standard** or **E-Ink**.
3. Open **Books** and tap **Add books**.
4. Import an EPUB/PDF, scan a folder, or choose **Free books** to browse Project Gutenberg.
5. Tap a cover to open **Book details**, then choose **Continue reading**.
6. Tap the middle third of an EPUB page to briefly show reading information. Double-tap blank space there to open reader tools; the controls gesture can be changed in Settings.

Vayana stores the library locally by default. An account is not required. Backup, GitHub sync, Goodreads, Project Gutenberg, OPDS, dictionary downloads, Wikimedia lookup, and translation are optional.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/01-library.png" alt="Vayana library with Today, Continue reading, suggestions, and a grid of public-domain books" width="360"><br>
      <sub><strong>Library</strong> — reading status, Continue reading, suggestions, filters, and book covers.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/02-free-books.png" alt="Project Gutenberg browser with search, language, topic, and popularity filters" width="360"><br>
      <sub><strong>Free books</strong> — browse public-domain titles by popularity, topic, or language.</sub>
    </td>
  </tr>
</table>

## Navigation

The compact phone layout has three main destinations:

- **Books** opens the reading dashboard and library.
- **Notes** groups highlights, underlines, and notes by book.
- **Stats** shows reading time by week, month, year or all time, streaks, goals, finished books, words you looked up, and storage use.

The **Add books** button appears beside the main navigation. The Library toolbar also provides:

- **Sync now** for configured GitHub sync.
- **Search** across the library and reading data.
- **More library actions** for views such as Read Next, shelves, offline books, and Recently Deleted.
- **Settings** for appearance, reader behavior, backup, sync, goals, Help, About, and Diagnostics.

On larger screens, Vayana uses adaptive layouts, side navigation, and list/detail views where space allows. A two-column reader/notes layout can be enabled for wide landscape screens.

## Adding books

### Import EPUB or PDF files

From **Books → Add books**:

- **Import books** opens Android's file picker for one or more EPUB/PDF files.
- **Import folder** scans a selected folder and imports supported books it finds.
- Android **Open with** and **Share to Vayana** can import a supported file from another app.
- **Replace source** on Book details replaces a missing or updated book file while retaining Vayana's library record and reading data where possible.

Vayana detects likely duplicate imports. Keep the original source files available until an import finishes. Password-protected PDFs are reported as unsupported.

### Download free books from Project Gutenberg

Open **Books → Add books → Free books**. You can:

- Search by title or author.
- Switch between **Popular**, **Latest**, and **Random** lists.
- Filter by language and topics such as adventure, mystery, science fiction, fantasy, poetry, or children.
- Open a title, compare available editions, and choose versions with or without images.
- See which books are already in the Vayana library.
- Continue browsing a cached catalog when Project Gutenberg is temporarily unavailable.

Project Gutenberg titles may be free of copyright in the United States but not necessarily everywhere. Check the copyright rules where you live.

### Connect an OPDS catalog

Choose **Online catalogs** from the Free books screen. Add the OPDS address supplied by a service such as:

- Calibre content server
- Calibre-Web
- Standard Ebooks
- Another compatible OPDS catalog

Open a catalog to browse its navigation feeds, search when the server supports it, and download compatible EPUB or PDF acquisitions. Authentication and availability depend on the catalog server.

<p align="center">
  <img src="assets/user-guide/03-online-catalogs.png" alt="Empty Online catalogs screen explaining how to add an OPDS catalog" width="360"><br>
  <sub>Add Calibre, Calibre-Web, Standard Ebooks, or another OPDS catalog.</sub>
</p>

## Managing the library

### Dashboard and filters

The Library combines the catalog with a reading dashboard:

- **Today** shows progress toward the daily reading goal and due review items.
- **Continue reading** returns to the most recently active locally available book.
- **Suggestions to read** uses current library context to surface related books.
- Status filters include **All**, **Reading**, **Finished**, **Not started**, **Paused**, and **Did not finish**.
- Search matches title, author, and description.
- Grid/list display, sorting, shelves, series, Read Next, and other filters help organize larger libraries.

### Read Next, shelves, and series

- Add books to **Read Next** and reorder the queue.
- Create shelves for personal collections and filters.
- Use series metadata to keep related books together and preserve series order.
- A book can retain tags, rating, dates, progress, notes, and reading statistics alongside its file metadata.

### Offline, physical, borrowed, and other books

Offline book records do not require an EPUB or PDF. Use them for paper books, audiobooks, or formats read in another app. You can record:

- Title, author, cover, series, tags, and page count
- Reading status and dates
- Pages read or percentage progress
- Ratings, notes, and reading sessions
- **Owned** or **Borrowed** status
- A return date for borrowed books

Borrow reminders can notify three days before, one day before, and on the due date when enabled.

### Home Library mirror

If the developer's Home Library app is installed on the same device and sharing is enabled, Vayana can mirror that catalog into Offline books:

- Enable **Sync with Home Library** in Settings.
- Mirrored catalog fields are read-only; Home Library remains the owner of that data.
- Vayana keeps its own reading progress, dates, Read Next state, notes, and statistics.
- With [GitHub sync](#github-sync), these books and their reading progress and time also reach your other devices,
  even ones without Home Library. There they stay read-only, and a book removed from Home Library stays on them.
- Shelf location and shared metadata appear in search and Book details.
- **View in Home Library** returns to the source record.

## Book details and reading status

Book details brings metadata and reading activity together. Depending on the book, it can show:

- Cover, title, author, series, format, and progress
- Description, publisher details, tags, subjects, and custom metadata
- Personal rating and reading status
- Started, finished, and last-read dates
- Time spent reading and current progress
- Notes, highlights, quotes, and chapter counts
- File actions, source replacement, sharing, and cover editing
- Optional Goodreads metadata, rating, genres, series data, and community quotes

Metadata and cover changes affect Vayana's local record unless the book is a read-only Home Library mirror.

<p align="center">
  <img src="assets/user-guide/04-book-details.png" alt="Book details for The Hound of the Baskervilles" width="360"><br>
  <sub>Book details combines metadata, status, progress, dates, notes, and actions.</sub>
</p>

## Reading EPUB books

Vayana's EPUB reader remembers the position for each book and supports reflowable text, fixed-layout EPUBs, and per-book presentation choices.

### Reader navigation

- Tap or swipe through pages according to the configured tap zones and page-turn behavior.
- By default, one tap anywhere in the middle third shows reading indicators for seven seconds. Accessibility timeout preferences extend this interval, and touching the indicators pauses the countdown.
- Double-tap a word for dictionary lookup. Double-tap blank space in the middle third to open tools. If you select single- or triple-tap controls in Settings, that preference applies throughout the middle third.
- The tools row keeps **Contents**, **Search**, **Bookmarks**, and **Style** within one tap. **More tools** contains Notes, Progress, Words, and Read aloud when enabled.
- Opening tools preserves the text viewport. Page-jump dialogs remain open when reading indicators disappear.
- Use hardware volume/page keys when enabled.
- Open **Contents** to jump to a chapter.
- Add and revisit bookmarks.
- Search the current book.
- View progress by page/location and percentage.
- Use fullscreen, header/footer information, and keep-screen-awake options as preferred.
- The back arrow at the top of the reader tools closes the book, which helps on E-Ink devices where back gestures are unreliable.
- Landscape can use two columns on wide screens.

When returning after time away, Vayana can show a short **Welcome back** recap with the last chapter and recent reading context.

### Typography and page appearance

Open **Style** from the reader tools to adjust the options below. The page stays visible behind the panel, so each change previews live.

- Page theme: System, Light, Paper, Sepia, Mint, or other available themes
- Font family, imported custom font, font size, and bolder text
- Line height, alignment, hyphenation, and side margins
- Publisher styles and whether Vayana overrides book typography
- Header/footer content and page progress style
- Optional bionic reading
- Per-book custom styles

Theme options can apply broadly while a custom style switch preserves book-specific typography choices.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/06-reader.png" alt="EPUB reader showing a public-domain Sherlock Holmes chapter in a dark theme" width="360"><br>
      <sub><strong>Reader</strong> — distraction-free text with progress and a return recap.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/07-reader-controls.png" alt="Reader controls and typography settings" width="360"><br>
      <sub><strong>Reader controls</strong> — contents, notes, progress, style, read aloud, and search.</sub>
    </td>
  </tr>
</table>

## Reading PDF books

PDF files can be imported through the file picker, folder scan, Open with, Share, or Replace source workflows.

The PDF reader supports:

- One page per screen with page taps and hardware-key navigation
- PDF outline/contents navigation when the document provides it
- Page-number navigation and remembered position
- Fit-page, fit-width, zoom, and panning
- Brightness/warm-light edge controls where configured
- Bookmarks on all PDFs
- Text selection, copy, dictionary lookup, highlights, underlines, notes, and quote cards on PDFs with a text layer
- First-page cover generation and title/author extraction from PDF metadata when present

Scanned-image PDFs without a text layer can be viewed, zoomed, panned, and bookmarked, but their text cannot be selected or searched. PDF fonts and spacing are printed into the page, so EPUB typography controls do not apply.

## Highlights, notes, bookmarks, and quotes

### While reading

Select text to access actions such as:

- Highlight or underline
- Add/edit a note
- Copy or share selected text
- Create a visual quote card
- Define, search, or translate the selection
- Add annotation tags

Overlapping EPUB highlights can be merged. Footnotes can open as popups when supported by the book.

### Notes library

The global **Notes** destination groups annotations by book and shows counts and update dates. It supports:

- Search across notes, books, and highlighted text
- Filters and direct navigation back to the original passage
- Editing and sharing annotations
- Markdown export
- Automatic Markdown notebooks when configured
- Kindle `My Clippings.txt` import
- Goodreads community quote import, kept distinct from personal annotations
- Spaced highlight review using **See soon**, **Got it**, and **Know it well**

<p align="center">
  <img src="assets/user-guide/08-notes.png" alt="Notes library grouped by public-domain books" width="360"><br>
  <sub>Notes are grouped by book with annotation and chapter counts.</sub>
</p>

## Dictionary, translation, and vocabulary

Double-tap a word to look it up offline. Vayana tries the dictionaries for the book's language first, and below the main definition it adds the word's meaning in your other installed languages, such as **In Malayalam** from Olam. Lookups also find possessives, contractions, older verb endings (*canst*, *lendeth*) and words written with or without accents.

### Offline dictionaries

Manage dictionaries in **Settings → Reader text → Dictionaries**. Lookups try them in the order shown: use **Try … first** to move one up, and the trash button to remove one.

| Dictionary | Words and meanings | How to install |
| --- | --- | --- |
| Open English WordNet | Modern English definitions with examples | **Get dictionary** downloads the free ZIP in your browser; then choose **Install ZIP** |
| GCIDE (Webster's 1913) | English, including older and literary words | **Download** fetches it from GNU and builds it on the phone, which takes a few minutes |
| Olam | English → Malayalam | **Download** |
| FreeDict | 300+ bilingual dictionaries | **Get more dictionaries** opens the FreeDict catalog, with your languages listed first |
| Your own dictionary | Any StarDict dictionary | **Import StarDict file**, then choose the language of its words and of its meanings |

Downloads are checked before they are installed, and a damaged download is rejected. Each device keeps its own dictionaries; they are not synced.

### Online lookup and translation

- Search a selected word or phrase on Wiktionary or Wikipedia when an internet connection is available.
- Send a selection to an installed translation app.

Online lookup sends only the selected query to the chosen provider when you request it. Translation requires a compatible translation app.

### Learn and vocabulary

- **Stats → Words you looked up → See all** opens **Learn**: each word you looked up, how often, when, and the sentence it came from. Tap a word to show its meaning, or remove it. Lists and removals sync across devices.
- Only deliberate lookups are kept: a definition you leave open for a moment, read online, or save. Selecting a word to start a highlight does not count, and words no installed dictionary knows, such as names and typos, are not saved.
- Hide words you already know, or save a word to **vocabulary review**.
- Discover unusual words from the current chapter, and mark familiar terms as known.
- Review saved vocabulary with spaced intervals.
- Export vocabulary as Anki-compatible CSV or readable Markdown.

## Read aloud

Android text-to-speech can read an EPUB sentence by sentence and continue across chapters.

- Start from the current position or a selected passage.
- Follow the spoken word/sentence marking in the text.
- Choose an installed TTS engine, language, voice, speed, and pitch.
- Teach it names: **Read aloud → Pronunciations for this book** lets you write how a word or phrase should sound. Corrections are saved on the device.
- Pauses follow paragraphs, headings and scene breaks. Roman numerals in labels such as *Chapter IV* are read as numbers, and English voices also get hints for dates, times, money and measurements.
- Listening keeps going with the screen off and counts as reading time; Stats shows how much you listened.
- Use a sleep timer.
- Pause by tapping the reader.
- Control playback from notifications, lock screen, headset, or Bluetooth controls.
- Audio focus pauses or ducks playback when another app needs audio.
- The Continue Reading widget can start or pause read-aloud for its displayed book.

Available voices and languages depend on the TTS engines installed on the device. Read aloud is primarily designed for reflowable text, not scanned PDFs.

## Search

Global search can find:

- Titles, authors, descriptions, series, tags, and other metadata
- EPUB chapter names and indexed book text
- Personal highlights and notes
- Recent queries and prefix matches

Put a phrase in double quotes, such as `"river boat"`, to match those words together and in that order. The **book** and **shelf** chips under the search box narrow results to one book or shelf, and the result chips switch between All, Books, Notes, and Contents.

Locally available EPUB content is indexed for fast search. PDF page text and physical-book contents are not indexed, although personal PDF/physical-book annotations can still appear in annotation search.

## Statistics and goals

Choose **Week**, **Month**, **Year**, or **All time** at the top of Stats. The main card shows the time read in that period and how it compares with the same point of the previous period, along with sessions, days read, and books finished. Its chart can show bars or a line; the small chip in the card's corner switches between them, and Vayana remembers the choice. Tap a bar to see its time. **All time** shows the last 12 months, including months without reading.

The cards below are grouped:

- **Reading:** streak and daily goal, **When you read** (time of day, weekday, and average and longest session), **Listened vs read**, **Most read** books, **Devices**, reading activity, and pace toward finishing the current book
- **Books:** Year in books with **See your year**, a shareable summary of the year; reading records; reading habits; authors and series; and tags ranked by reading time
- **Notes & words:** a highlight to revisit, notes and highlights, **Words you looked up**, vocabulary growth, and vocabulary review
- **Library:** library totals, average progress, and storage used by books, covers, notes/quotes, dictionaries, fonts, and cache

**Devices** shows reading time per synced device and the most used one. Each session records the sync device name of the device that read it. With [GitHub sync](#github-sync), older sessions are credited once from the sync repository's history the next time the device is on Wi-Fi; any time that cannot be matched is shown as not included.

**Reading records** lists books with saved reading activity, including physical books that are still in progress. Each entry shows its cover, title, author, recorded start and finish dates, and accumulated reading time. Tap an entry to open Book details; use **View all reading records** when more than three books are available. Missing dates appear as **Not recorded**. Recorded time comes from saved sessions; entering a finish date does not estimate time spent reading.

Statistics are based on activity recorded by Vayana. Sessions synced from another device can contribute after synchronization. Set the daily minute and yearly book goals in **Settings → Reading goals**.

<p align="center">
  <img src="assets/user-guide/05-statistics.png" alt="Statistics screen with reading activity, goals, and storage" width="360"><br>
  <sub>Reading activity, goals, library totals, and storage use.</sub>
</p>

## Wear OS companion

The companion tracks physical-book reading on Wear OS 3+ with Google Play services and a paired Android phone. Set it up with matching current phone and watch builds using the [Wear OS guide](WEAR_OS.md#install-and-build).

1. Add a physical book on the phone and mark it as currently reading.
2. Open Vayana on the watch and choose **Sync with phone** to cache your books.
3. Select a book, check the starting page, and tap **Start timer**. Watch-owned timers, pauses and page edits work offline.
4. Tap **Stop**, enter your ending page, then **Save**. Cancel leaves a watch-owned timer paused.
5. Reconnect to send saved sessions to the phone's history and statistics. Pending sessions stay on the watch until acknowledged.

A timer running on the phone can also appear on the watch, where you can pause, resume, stop and update its page. Remote actions wait for the owning device to acknowledge them; an offline timer display is not confirmation that a command was applied. The phone also supports controls for a watch-owned timer.

Use **Reading goals** for a daily target or session reminder, and **Vibration** for haptics. The companion includes an ambient display, a reading tile and a watch-face complication. For page conflicts, overlap review, reboot recovery and connection indicators, see the [complete companion guide](WEAR_OS.md).

The watch does not open ebook files. Its companion connection uses Google's Data Layer, independently of optional GitHub sync.

## Widgets and app shortcuts

Vayana provides two configurable home-screen widgets:

- **Continue Reading** shows the current book, cover, progress, and a one-tap return to reading. When read aloud is enabled, it can show play/pause.
- **Reading Time** shows today's minutes and a responsive seven-day chart with an average.

The widget configuration screen previews changes and applies shared corner-radius and progress-style choices to Vayana widgets. Layout adapts to the size selected in the launcher.

Long-press the Vayana launcher icon for shortcuts to:

- Free books
- Search
- Statistics

## Backup and restore

### Manual backup

Open **Settings → Backup & restore** to create a portable ZIP containing the library database, settings, book files managed by Vayana, covers, and related reading data.

### Automatic backup

Choose a folder, frequency, and retention count. Available schedules include daily, every seven days, and every 30 days. Android may delay background work depending on battery and device restrictions.

### Restore safely

Vayana shows a backup summary before restoring, including creation date, app version, book count, annotation count, and size. Restore replaces the current on-device library and settings, then restarts the app. Create a fresh backup first if the current library may still be needed.

Backup is separate from GitHub sync: a backup is a point-in-time portable archive; sync merges supported state across configured devices.

## GitHub sync

GitHub sync uses a repository controlled by you. It can synchronize:

- Books, covers, and library metadata
- Reading position, sessions, and dates
- Highlights, notes, shelves, and Read Next
- Vocabulary, looked-up words, and portable settings
- Home Library books and their reading data
- Deletions and restores

Book and cover assets are encrypted with AES-GCM using the sync passphrase before upload. The GitHub token is stored on the device. Lightweight progress sync can run during reading, while full sync reconciles library state and assets. Only changed data is uploaded, so a sync with nothing new takes about a second, and **Settings → Help and about → Data usage** shows what each sync sent and received.

Every device that shares a repository needs Vayana 0.93 or newer: older versions cannot read its compact sync format.

Important setup rules:

- Use a private repository dedicated to Vayana sync.
- Give the token only the repository access it needs.
- Use the same repository, branch, and passphrase on every device.
- Give each device a recognizable name for conflict and history messages and the **Devices** card in Stats.
- Keep the passphrase somewhere safe; encrypted assets cannot be recovered without it.
- Use **Test GitHub connection** before the first full sync.

See [Set up GitHub sync](GITHUB_SYNC_SETUP.md) for the complete walkthrough and troubleshooting checklist.

## E-Ink and accessibility

### E-Ink display profile

E-Ink is a dedicated profile rather than a simple grayscale theme. It can:

- Remove or reduce motion and page-turn animation
- Use static progress rendering
- Support **Monochrome** and high-contrast **Color** palettes
- Keep reader updates tied to page turns where possible
- Use hardware page keys
- Add screen-sized paging controls to long app screens
- Request clean reader refreshes at configured intervals and transitions
- Provide bolder text, alignment, and hyphenation controls

Vendor-specific refresh APIs are not currently integrated, so behavior depends on the Android device and its own refresh mode.

### Accessibility and adaptive layout

- Material 3 components expose content descriptions and scalable text.
- Motion can be Full, Reduced, or Off.
- Standard, softer-dark, and true-black appearances are available.
- Phone and tablet layouts adapt navigation and content width.
- Reader text, margins, contrast, and line spacing can be adjusted independently.

## Settings and languages

Settings are grouped and searchable:

- **Appearance:** theme, colors, E-Ink, motion, navigation, dates, and app language
- **Library:** start screen, covers, finishing behavior, offline books, Home Library, reminders, and Recently Deleted
- **Reader text:** font, size, spacing, book styles, custom fonts, and offline dictionaries
- **Reader page:** page colors, margins, header, footer, and progress presentation
- **Reader controls:** taps, keys, highlighting, wake behavior, read aloud, and related controls
- **Reading goals:** daily minutes, yearly books, and week start
- **Sync:** GitHub repository, credentials, passphrase, device name, health, testing, and transfer
- **Backup & restore:** manual and scheduled backups
- **Help and about:** built-in help, Diagnostics, Privacy & data, Data usage, version, source code, release link, sharing, and developer links

Vayana's interface can be selected independently of book language. Version 0.93 includes English, Spanish, Portuguese, Russian, German, French, Italian, Malayalam, and Tamil. Search Settings for **Language** to change the app language; books retain their own language metadata.

<p align="center">
  <img src="assets/user-guide/11-settings.png" alt="Searchable Settings categories" width="360"><br>
  <sub>Searchable setting groups keep global and reader-specific options separate.</sub>
</p>

## Diagnostics and support

Open **Settings → Help and about → Diagnostics** when the app crashes or sync behaves unexpectedly.

Diagnostics records recent unhandled crashes and sync issues locally. A sync skipped only because the device is offline is not recorded as an issue. The screen shows counts, newest-first events, and expandable technical details. It also offers:

- **Share with developer** to open Android's share sheet with a plain-text report
- **Copy report** to place the report on the clipboard
- **Clear all logs** to remove stored diagnostic events from the device

The generated report includes app version, Android version, device model, event times, sources, messages, and technical traces. It does not include private book content, notes, or settings. Common authorization values, secret URL parameters, GitHub token patterns, user paths, external-file paths, content URIs, and email addresses are redacted. Always review the report before sharing it.

When reporting a problem, include:

- What you were doing
- What you expected
- What happened instead
- Whether it happens every time
- Book format and whether the issue affects one or all books
- The diagnostic report, if relevant

Never post a GitHub token or sync passphrase in an issue.

<table>
  <tr>
    <td width="50%" align="center">
      <img src="assets/user-guide/10-diagnostics.png" alt="Diagnostics screen with share and copy actions" width="360"><br>
      <sub><strong>Diagnostics</strong> — review and share redacted crash or sync information.</sub>
    </td>
    <td width="50%" align="center">
      <img src="assets/user-guide/09-about.png" alt="Vayana About screen showing version, source, releases, support, and other apps" width="360"><br>
      <sub><strong>About</strong> — version, source code, releases, support, sharing, and developer links.</sub>
    </td>
  </tr>
</table>

Report reproducible problems through [GitHub Issues](https://github.com/rjwarrier/Vayana/issues).

## Privacy and online services

Core reading, local search, notes, installed offline dictionaries, statistics, and local backup work without a Vayana account.

**Settings → Help and about → Privacy & data** shows where your data is stored on the device, what leaves it and to whom, and the live status of device encryption, screen lock, and the sync repository's visibility. **Data usage**, beside it, shows Vayana's Wi-Fi and mobile data by day and what each sync sent and received; these records stay on the device.

Network access occurs when you choose or configure an online feature:

| Feature | Data involved |
| --- | --- |
| Project Gutenberg | Search/filter requests and book downloads |
| OPDS | Catalog address, navigation/search requests, optional server authentication, and downloads |
| Goodreads | Book metadata/cover lookup and browser fallback |
| Wiktionary/Wikipedia | The selected word or phrase when you request lookup |
| Dictionary downloads | Downloads from Open English WordNet, GNU (GCIDE), Olam, or FreeDict when you install a dictionary; offline lookups stay on the device |
| Translation | Selected text passed to the chosen translation app/provider |
| GitHub sync | Sync metadata plus AES-GCM-encrypted book/cover assets; repository identifiers and token remain in app settings on the device |
| Cover browsing | Book search metadata needed by the selected source |

Diagnostic logs stay on the device until you explicitly copy/share or clear them. Android's chosen receiving app controls what happens after sharing.

## Troubleshooting

### A book will not import

- Confirm it is a valid EPUB or PDF and can be opened by another reader.
- Password-protected PDFs are not supported.
- If Android reports a generic file type, keep the `.epub` or `.pdf` extension.
- Try the Vayana file picker instead of Share/Open with.
- Check available storage and grant access to the selected file/folder.

### A book is missing after moving files

- Open Book details and use **Replace source** to reconnect the record.
- If it was permanently deleted from Vayana, re-import the file.
- Check **Recently Deleted** if it was removed recently and restore it there.

### Project Gutenberg or OPDS does not load

- Check the internet connection and retry.
- Project Gutenberg may respond slowly; Vayana can show the saved catalog while offline.
- For OPDS, verify the exact catalog URL and server reachability.
- Confirm server credentials outside Vayana if authentication fails.

### Dictionary or translation is unavailable

- Install a dictionary for the book's language in **Settings → Reader text → Dictionaries**.
- Confirm the book language is correct in metadata.
- If the wrong dictionary answers first, change the order there.
- Wikimedia fallback requires internet access.
- Translation requires a compatible translation app.

### Read aloud has no voice

- Install or enable an Android TTS engine and the required language voice.
- Check media volume and Bluetooth output.
- Review the engine, voice, language, speed, and pitch under reader/audio settings.
- On E-Ink devices, confirm **Audio features** is enabled.

### GitHub sync fails

- Open **Settings → Sync** and run **Test GitHub connection**.
- Confirm owner, repository, branch, token, and passphrase.
- Ensure the token has access to the configured repository.
- Use the same passphrase on every device.
- Open Diagnostics and share the redacted sync event if the error remains unclear.

### Statistics look incomplete

- Only sessions recorded by Vayana are counted.
- Confirm the book is being opened through Vayana and the device clock is correct.
- Run sync if activity was recorded on another configured device.
- If the **Devices** card is missing a device, give each device its own name in **Settings → Sync** and sync again.

### The app crashed

1. Reopen Vayana.
2. Go to **Settings → Help and about → Diagnostics**.
3. Expand the newest crash and confirm the time matches.
4. Use **Share with developer** or **Copy report**.
5. Describe the action immediately before the crash.

## Current limitations

- Supported readable digital formats are EPUB and PDF.
- Scanned PDFs without a text layer cannot provide selection, lookup, search, or text annotations.
- PDF typography is fixed by the document; EPUB font and spacing controls do not apply.
- PDF support does not currently include every EPUB-only reading feature, such as bionic reading or community quotes.
- Password-protected PDFs are unsupported.
- Physical-book contents and PDF page text are not part of full-book indexing.
- Vendor-specific E-Ink refresh SDKs are not integrated.
- Git history may retain encrypted files in older sync commits after a permanent cloud deletion unless repository history is rewritten.
- Online catalog, enrichment, lookup, translation, and sync features depend on third-party service availability.

## Related documentation

- [Feature behavior and implementation details](FEATURES.md)
- [GitHub sync setup](GITHUB_SYNC_SETUP.md)
- [Architecture and implementation decisions](DECISIONS.md)
- [Database changelog](DATABASE_CHANGELOG.md)
- [Vayana 0.93 release](https://github.com/rjwarrier/Vayana/releases/tag/v0.93)
