# Plan: read aloud (TTS)

**Goal:** the reader can read a book aloud with the phone's offline voices, following along page by page, so a book
can be listened to while commuting, doing chores or resting the eyes.

Status: **proposed 2026-09-14, not started.** Spec: `PROMPT2appbuild.md` §4.7 (and `textForTts` in §3). Chosen as the
next value-for-money feature: every item in `FEATURE_VALUE_RECOMMENDATIONS.md` except tablet side-by-side notes is
built, and that doc names read-aloud as the next milestone.

---

## 1. What exists today

| Piece | Today | Gap |
|---|---|---|
| Engine contract | `reader/engine-api/BookEngine.kt`: open, location, goTo, style, annotations, search, events. | No `textForTts` (the spec's §3 method was never added). |
| Renderer | `reader/engine-web`: vendored foliate-js (`view.js`, `paginator.js`, `text-walker.js`, …) driven through `bridge.js` + `evaluateJavascript`. | `tts.js` deliberately **not vendored** (`assets/foliate/VENDORED.md`). Upstream `view.js` loads it from `initTTS()`. |
| Position | `EngineEvent.Relocated` → `ReaderViewModel` saves the locator; resume + "back to where I was" work. | Nothing moves the page while audio plays. |
| Reading time | `ReadingTimeTracker` advances only after reader input and stops when idle. | A listener doesn't touch the screen, so listening wouldn't count. |
| Settings | Typed `SettingsRegistry` (`core/datastore/.../SettingsModel.kt`) with groups such as `READER_CONTROLS`. | No TTS settings. |
| Manifest / deps | Only `INTERNET`; no services; no media3. | Background playback needs a media session + foreground service. |

## 2. Behaviour to build

### 2.1 User-facing

- **Reader top bar → "Read aloud"** (headphones icon) starts reading from the **first sentence on the current page**.
- **Player bar** (bottom of the reader, replaces the page controls while active): play/pause, previous/next
  sentence, speed (0.5×–2.0×), sleep timer, close.
- **The page follows the voice:** the sentence being read is highlighted, and the reader turns the page (or moves to
  the next chapter) when speech moves past it.
- **Chapter end:** continues into the next chapter automatically; stops at the end of the book.
- **Sleep timer:** off / 15 / 30 / 45 / 60 min / end of chapter. When it fires, fade isn't possible with system
  TTS, so it finishes the current sentence and pauses.
- **Tapping text or turning a page by hand while playing** pauses and offers "Read from here".
- **Stop / close reader:** the reading position is where speech stopped (normal locator save), so resume and sync
  just work.
- **No voice for the book's language** (e.g. no Malayalam voice installed): a dialog explains it and opens Android's
  TTS settings to install one.
- **Settings → Read aloud** (new group): voice/engine, default speed, pitch, highlight sentence on/off, sleep-timer
  default, "pause other audio" vs "mix with other audio".

### 2.2 Rules

1. **Offline system TTS only.** `android.speech.tts.TextToSpeech`; no network voices, no bundled voices.
2. **Sentences come from the rendered book, not a second parser.** One source of truth for text and positions means
   highlights and page-follow line up with what the user sees (CFI ranges), including bionic-reading wrappers
   (already CFI-transparent).
3. **Queue small batches.** Speak one sentence per utterance, keep ~5 queued ahead; flushing on pause/seek stays cheap.
4. **Language per book:** use the book's EPUB `dc:language` for the voice, fall back to the user's chosen voice.
5. **Listening counts as reading time** while audio is actually playing (see decision D3).
6. **No database change** for stages 1–2. Position, stats and sync reuse existing paths.

## 3. Design

### 3.1 Engine API (`:reader:engine-api`)

```kotlin
data class TtsSentence(val id: String, val text: String)   // id = foliate SSML mark name

interface BookEngine {
    …
    /** Starts a speech session at the first sentence visible on the current page. */
    suspend fun startSpeech(): Result<Unit>
    /** Next batch of sentences after [afterId] (null = from the start); empty = end of book. Crosses chapters. */
    suspend fun speechSentences(afterId: String?, limit: Int): List<TtsSentence>
    /** Highlights [id] and moves the page to it when it's off screen. */
    suspend fun markSpeech(id: String)
    /** Starts from the sentence at the current selection / page instead (the "Read from here" action). */
    suspend fun restartSpeechHere(): Result<Unit>
    fun stopSpeech()
}
```

Pure Kotlin, nothing WebView-specific, so a native engine could implement it later.

### 3.2 Web engine (`:reader:engine-web`)

- **Vendor upstream `tts.js`** (+ its import closure) at the same foliate-js commit as the other files; update
  `VENDORED.md` and follow `docs/DECISIONS.md` for vendored code.
- `bridge.js`: `VayanaReader.tts.start/next(batch)/setMark/stop` wrapping `view.initTTS()` and `view.tts`
  (`start()`, `next()`, `setMark()`); strip SSML to plain text + mark ids in JS and return JSON through the existing
  bridge callback.
- Chapter boundary: when `view.tts.next()` returns nothing, `view.next()` section, re-init TTS, continue.
- `FoliateBookEngine` exposes the §3.1 methods; results come back as request/response pairs over the existing event
  channel (same pattern as search).
- **Spike first (stage 0):** confirm `tts.js` works with our paginator setup and that `Intl.Segmenter` sentence
  splitting behaves for English and Malayalam in Android System WebView.

### 3.3 Player (`:feature:reader`)

- `SpeechEngine` interface (speak(id, text), stop, setRate/Pitch/Voice, callbacks onStart/onDone/onError) with
  `AndroidSpeechEngine` wrapping `TextToSpeech` + `UtteranceProgressListener`. Tests use a fake.
- `ReadAloudController` (plain class, injected dispatchers): state machine
  `Idle → Preparing → Playing ⇄ Paused → Idle`, sentence queue, sleep timer, audio focus.
  - `onStart(id)` → `engine.markSpeech(id)`.
  - Queue below 2 → `engine.speechSentences(lastId, 5)`.
  - Empty batch → end of book → `Idle`.
- `ReaderViewModel` owns one controller, exposes `readAloudState: StateFlow<ReadAloudUiState>`, and marks the
  `ReadingTimeTracker` active while `Playing`.
- Compose: `ReadAloudBar` in its own file (keep `ReaderScreen.kt` from growing); sizes via design tokens (Konsist).
- Strings in `core/resources`.

### 3.4 Settings (`:core:datastore`)

New `SettingsGroup.READ_ALOUD` with registry entries: `tts.voice` (string, empty = system default), `tts.rate`
(float 1.0), `tts.pitch` (float 1.0), `tts.highlight_sentence` (bool true), `tts.sleep_timer_default` (choice, off),
`tts.mix_with_other_audio` (bool false). Being registry entries, they get defaults, reset and backup for free.

### 3.5 Background playback (stage 3)

- media3 `MediaSessionService` with `foregroundServiceType="mediaPlayback"`, `FOREGROUND_SERVICE`,
  `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `POST_NOTIFICATIONS` (Android 13+ runtime prompt).
- Notification + lock-screen controls: play/pause, previous/next sentence, stop.
- **Hard part:** sentences come from the WebView, which Android may throttle or destroy in the background. Choose in
  decision D1 before starting this stage.

## 4. Stages

| Stage | Scope | Effort | Done when |
|---|---|---|---|
| **0. Spike** | Vendor `tts.js`, log sentences + marks from a real EPUB (English + Malayalam) via `bridge.js`. | 0.5–1 day | Sentence batches with working highlight across a chapter boundary. |
| **1. MVP (foreground)** | §3.1–3.4: play/pause, prev/next sentence, speed, auto next chapter, highlight + page follow, sleep timer, missing-voice dialog, settings group, reading time. Stops when the app leaves the foreground. | 3–4 days | Listen to a full chapter hands-free with the screen on; position resumes where speech stopped. |
| **2. Polish** | "Read from here" on selection, voice/engine picker with preview, pitch, audio focus (pause on calls / other media), keep-screen-on option while playing. | 1–2 days | Interruptions (call, other app playing) pause and resume correctly. |
| **3. Background** | §3.5: media session, notification, lock screen, screen-off playback. | 3–5 days | 30 min screen-off listening without stopping; controls work from lock screen. |

Ship after stage 1 or 2; stage 3 is a separate release.

## 5. Tests

- `ReadAloudController` unit tests with fake `SpeechEngine` + fake engine: queue refill, chapter crossing, end of
  book, pause/resume, seek flushes queue, sleep timer (virtual time), error → paused.
- SSML → `TtsSentence` stripping: JS unit tests in `reader/engine-web/tests` (`npm test`).
- Settings registry entries: existing registry/backup tests pick them up; add a round-trip case.
- Manual device checks (add to `HANDOFF.md` "Unverified"): English + Malayalam voice, E-Ink highlight contrast,
  bionic reading on, a book with footnotes/tables, battery over 30 min (stage 3).

## 6. Decisions to take

| # | Question | Options | Recommendation |
|---|---|---|---|
| D1 | Text source for background playback (stage 3) | (a) keep the WebView alive in a headless/retained host; (b) extract chapter text natively in Kotlin (`format/epub` + HTML text walk) and map back to CFIs when the reader reopens | Decide after stage 1 spike data; (a) is less code, (b) is more robust. Stages 1–2 don't depend on it. |
| D2 | Where the entry point lives | Top bar icon / overflow menu / selection menu | Top bar icon + "Read from here" in the selection menu (stage 2). |
| D3 | Does listening count toward reading time and stats? | Yes / No / Separate "listening" stat | Yes, same bucket (no schema change); revisit if users ask for a split. |
| D4 | Word-level highlight | Sentence only / word (needs `onRangeStart`, engine-dependent) | Sentence only; word highlight is inconsistent across TTS engines. |

## 7. Risks

- **Voice availability:** many devices lack Malayalam (or any non-English) voices offline; the missing-voice dialog
  is part of the MVP, not polish.
- **`tts.js` fit:** it's written for foliate's own view; if the spike shows it fights our paginator/bridge, fall
  back to a small in-house sentence walker over `text-walker.js` + `Intl.Segmenter` (+1–2 days).
- **Big files:** `ReaderScreen.kt` (~2.4k lines) and `ReaderViewModel.kt` (~1.1k) are already large; new code goes
  in new files (`ReadAloudBar.kt`, `ReadAloudController.kt`, `AndroidSpeechEngine.kt`).
- **Background limits (stage 3):** OEM battery savers kill foreground services; test on the user's phone early.
