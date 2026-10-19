# Unreleased

- Sync Home Library books between devices: a device without Home Library now gets them, read-only, with their reading progress, dates, sessions and time taken, so Stats and the Today card match. Home Library's catalog on the mirroring device is never overwritten by an older copy. Books removed from Home Library stay on other devices. (Database v38 and v39.)
- Write sync snapshots in a compact format: books move into their own zipped slice and every slice is zipped, so each background sync uploads about a third as much (240 KB of book data becomes about 64 KB). Every syncing device needs this version, since 0.92 and older can't read the format.
- Keep the screen on during a Goodreads import, in the in-app browser and in the Fetch from Goodreads dialog.
- Keep measured page counts when switching between light and dark themes, instead of measuring the whole book again.
- Add Settings → Help & About → Data usage: Wi-Fi and mobile data the app used today, in the last 7 and 30 days (as Android counts it), plus what each sync sent and received, a 14-day chart and the last 50 syncs.
- Data usage keeps the app's daily Wi-Fi and mobile use on the device (never synced), with a 30-day chart and monthly totals that outlast Android's own history.
- Redesign Statistics around a Week / Month / Year / All time switcher: time read with the change against the same point of the previous period, a bar or line chart (remembered), when you read (time of day and weekday), most-read books, devices and a Listened vs read card, in Reading, Books, Notes & words and Library sections. All time shows at least the last 12 months.
- Show reading time per synced device. Sessions now record the device that read them (database v40), and older sessions are credited once from the GitHub sync history.
- Count read-aloud listening as reading time, also with the screen off, and keep how much of each session was listened to (database v41).
- Fix selections dropping or stopping mid-drag when a highlight runs onto the next page, notably on E-Ink.
- Learn words: only deliberate lookups of words the offline dictionaries know are saved; tap a word to see its meaning, or remove it (removals sync). Compact layout without the double top gap.
- Add offline dictionaries in Settings → Reader text → Dictionaries: GCIDE (Webster's 1913, downloaded from GNU and built on the phone), Olam English→Malayalam, any of FreeDict's 300+ bilingual dictionaries from an in-app catalog, or your own StarDict file. Lookups follow the book's language and add meanings in your other installed languages.
- Look up more spellings: possessives, contractions, archaic endings (canst, lendeth) and accents (abbé) now find their dictionary entries.

See [Vayana 0.92](v0.92.md) for the previously released changes since v0.90.
