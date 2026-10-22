# Three value-for-effort improvements — October 21, 2026

Selected from the [deep scan](D:/AntiGravity/Vayana/docs/FEATURE_OPTIMIZATION_REVIEW_2026-10-21.md): the smallest confirmed optimization, a portability gap affecting user-created customizations, and the remaining OCR correctness prerequisite.

## Changes

1. **Dictionary lookup short-circuiting.** Additional-language lookups now use a lazy sequence, stopping once two successful distinct extra languages have been collected. Dictionary order, missing-hit behavior, language filtering and primary-definition fallback are unchanged. Existing dictionary tests exercise the module; no new lookup framework or runtime dependency was added. Device latency improvement has not been measured.
2. **Complete reader-customization backups.** Format-2 archives include imported font bytes, font metadata/selection and per-book pronunciation corrections. Restore validates font paths/files and parses corrections before closing the live database. Fonts participate in the existing file stash/swap; the previous customization state is captured for rollback. Rollback runs without cancellation so it can finish before restart. A required file disappearing during backup now fails the archive operation instead of being silently omitted. Archive metadata remains bounded to 1 MiB.
3. **Reject empty recognized Text views.** OCR finalization publishes text only when a chapter has readable title/body content; otherwise it removes the generated EPUB and page map. The availability check rejects earlier empty-spine artifacts and corrupt ZIPs, accepting generated chapter names with localized digits. Scan reading remains available. This addresses the previously reproduced F6 issue, including page-number-only output for newly generated artifacts.

## Backup compatibility and presentation

- Format-1 backups remain supported. Without the new optional payload they preserve the device's existing font and pronunciation customizations; a clean installation naturally retains its defaults.
- Format-2 archives require the new payload. Missing font files or customization metadata fail before the live database is closed.
- Older app versions reject new format-2 archives rather than silently omitting their customizations.
- Preview shows font and pronunciation-book counts, or explains omissions in older backups. Added text is supplied in all nine app languages; the preview body scrolls for smaller screens and larger text.
- Only the dedicated pronunciation store is included. Share-card profile/seal preferences, downloaded dictionaries and OCR models were not added to backups.

## Regression evidence

- The initial archive test failed against the existing manager because imported font bytes were absent.
- Initial OCR tests failed because blank and page-number-only recognition still published Text view.
- The locale regression reproduced a flaw in the first availability-check implementation; the final check accepts localized chapter numbers without changing generated filenames or existing anchors.
- Backup checks cover archive contents, customization metadata round-trip, pronunciation replacement, rollback snapshot restoration, legacy inspection, missing-payload/missing-font rejection before database close, invalid paths/selections, duplicate font identities and malformed correction JSON.
- OCR checks cover blank → readable → blank output, stale EPUB/page-map removal, older empty-spine EPUBs, page-number-only output and a non-Latin default numbering locale.

Full successful restore/restart on a device was not exercised. Tests create a real archive with Room/DataStore/preferences and exercise the customization restore/rollback methods, plus invalid archives through the actual manager before its restart boundary. Existing file-swap logic is reused.

## Verification

| Check | Result |
| --- | --- |
| Final affected-module results across the focused runs | 225 passed: dictionary 15, settings 30, OCR 21, reader 159; zero failures/errors/skips |
| Full repository Android debug unit run | 941 total: 940 passed, 1 failed; zero errors/skips |
| Reader JavaScript tests | 188 passed; zero failures/skips |
| Whitespace/diff check | Passed |

The full run completed before the additional Arabic-locale regression case and its correction. After that correction, the entire OCR and reader suites passed; the final settings suite also passed after making the preview scrollable. Other modules were unchanged. The sole full-run failure is the existing `HardcodedTextKonsistTest` false positive on unchanged `DataUsageScreen.kt` expressions for Wi-Fi/mobile labels, as in the preceding debug passes. It was not suppressed or bypassed. The repository-wide gate therefore remains red.

Commands used:

```powershell
.\gradlew.bat :format:ocr:testDebugUnitTest :dictionary:stardict:testDebugUnitTest :feature:settings:testDebugUnitTest --console=plain
.\gradlew.bat testDebugUnitTest --continue --console=plain
.\gradlew.bat :feature:settings:testDebugUnitTest --console=plain
.\gradlew.bat :format:ocr:testDebugUnitTest :feature:reader:testDebugUnitTest --console=plain
node --test reader/engine-web/tests/*.test.mjs
git diff --check
```

Local logs are under ignored `build/deep-scan-2026-10-21/`: `backup-red.log`, `ocr-red.log`, `ocr-locale-red.log`, `value-three-tests.log`, `value-three-full-tests.log`, `value-three-final-settings.log`, `value-three-final-ocr-reader.log`, and `value-three-reader-js.log`. Earlier in-progress OCR logs include a Windows fixture overwrite issue that was corrected by replacing the seeded page file before simulating a new recognition result; no production overwrite behavior was changed for that fixture.

All pre-existing uncommitted changes and the promotional image were preserved. No APK was installed and no commit or push was made.
