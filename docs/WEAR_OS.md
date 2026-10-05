# Wear OS companion

The `:wear` app records physical-book reading sessions on Wear OS 3+ (API 30+).
Install it alongside the updated Android phone app. Both packages must use
`com.vayana.app` and the same signing certificate. Debug builds use the usual
shared Android debug key. Release builds use the phone's
`VAYANA_KEYSTORE_PROPERTIES` / root `keystore.properties` configuration.

## Use

1. Open Vayana on the paired Android phone and add a physical book.
2. Open Vayana on the watch and tap **Sync**. The watch caches the 50 most recently
   read, currently-reading physical books (started date, progress, reading time, or a reading occasion; finished and inactive books are excluded). Initial setup requires the phone.
3. Choose a book, confirm the starting page, then start reading. Pause/resume and
   **Current page** work without the phone or internet.
4. **Stop & save** pauses the timer and asks for the final page. Cancel leaves the
   timer paused so it can be resumed. Save commits the session to the watch.
5. Sessions show **Pending** until the phone acknowledges a database commit.
   Reconnecting automatically transfers queued Data Layer items. Workers retry
   failures, with a periodic fallback; Android may defer background execution.
   **Sync** also requests a refresh and retry.

Phone/watch capability changes now wake synchronization when the companion becomes
reachable. Fresh events replace backed-off work and every watch pass requests the
latest catalog, so an earlier failed request cannot hold new sessions behind it.
The watch distinguishes **Phone connected**, **Offline · saved on watch**, and a
retrying sync failure. Connectivity means the companion app is reachable through
the Data Layer (including remote routes); it does not guarantee Bluetooth proximity.

The watch owns its timer. This version does not remotely operate or transfer an
already-running phone timer. Stop a phone timer before starting on the watch.
Sessions subsequently appear in the existing physical-book history and statistics.

## Durability and conflicts

- One committed local snapshot holds the active timer, page, book cache, outbox,
  and receipts. Stopping atomically replaces the active timer with a queued session.
- Phone and watch share the existing monotonic timer implementation. Screen-off
  and process death do not stop it. A reboot pauses at the last saved checkpoint
  and explains this on the watch; uncheckpointed time before a reboot is not guessed.
- Each session uses a UUID and a separate persistent Data Layer item. The phone
  imports in chronological order in a Room transaction, with unique-ID deduplication.
  A crash between database commit and acknowledgement cannot double-count time.
- Acknowledgements are durable Data Layer items. The watch commits the receipt
  before deleting its outgoing item. The phone then removes the acknowledgement.
- Cleanup also uses receipts already committed on the watch, so restarting after
  an interrupted deletion does not leave outgoing items stranded. A delayed
  rejection never downgrades a successful receipt. Event listeners ignore their
  own protocol writes to avoid sync feedback loops.
- A changed phone page/page count or newer phone reading keeps phone progress;
  the watch's duration and page checkpoints remain in phone history for review.
- Overlapping sessions (including a running phone timer) are retained on the watch
  instead of double-counted. Review/correct the conflicting phone history, then Sync.
  The overlap check is conservative: session spans include pauses.
- Deleted/non-physical books and reset history are never silently recreated.
  Rejected records remain visible and stored on the watch. Restore a deleted book
  to retry it. A history-reset rejection requires manually recording any desired
  session on the phone; it is not imported automatically.
- Completed receipts remain in local watch history; the UI shows ten recent
  completed sessions and all pending sessions. Clearing app data/uninstalling
  deletes locally held records, including anything not yet delivered.

Data Layer requires Google Play services and a paired Android phone. No account
or custom sync server is added. Local timer data does not depend on connectivity.
Protocol paths are versioned under `/vayana/wear/v1/`; book IDs are stable sync IDs,
never database row IDs. Catalog titles are bounded and catalog size is limited to
stay below the Data Layer item size limit.

## Build and verification

```powershell
.\gradlew.bat :wear:assembleDebug :app:assembleDebug
.\gradlew.bat :core:wear:testDebugUnitTest :core:database:testDebugUnitTest :feature:library:testDebugUnitTest
```

Install `wear/build/outputs/apk/debug/wear-debug.apk` on the watch and
`app/build/outputs/apk/debug/app-debug.apk` on the phone (do not install the watch
APK over the phone app). Release artifacts need a separate Wear OS Play track.

Before distribution, verify on a paired watch/phone: cache books; disconnect;
record two sessions with pauses and page edits; reopen the watch app; reconnect;
confirm both phone logs and totals; repeat Sync and confirm no duplicates. Also
exercise restart/reboot, a changed phone page, a deleted book, overlapping sessions,
and a phone process killed before acknowledgement. Test round and square screens,
rotary input, and large fonts. Unit tests cannot verify Bluetooth delivery or real
watch layout.

A paired-device instrumentation test in `wear/src/androidTest` publishes a
temporary unknown-book record and verifies the phone's acknowledgement and local
retention. It removes its test record/items afterward without touching real books.
Run `PairedSyncTest` on the watch with the updated phone installed. The optional
`offline=true` runner argument requires disconnecting the phone; it writes
`files/sync-test-reconnect` in the target app after confirming durable offline
queueing, then waits up to 60 seconds for connectivity and acknowledgement.

### Session conflicts and timestamps
New timers record active intervals using monotonic time mapped to the start clock. Pauses do not
create overlap. Same-book overlap imports only uncovered time as a new row; existing session rows
are never rewritten. Fully covered uploads retain a zero-time identity marker to prevent retries
from counting again. Old logs with uncertain pause placement, different-book overlaps, and changed
watch clocks wait for review. Out-of-order uploads preserve newer phone pages and last-read dates.
Review on watch allows retaining the phone page, explicitly applying the watch page, ignoring an
upload, correcting the local start date, or explicitly counting a separate reading occasion.
Acknowledgements contain a payload fingerprint, so an old receipt cannot accept revised timestamps.
No historical reconciliation or live-record repair is performed automatically.

### Connection indicators
The phone shows a Watch connected strip above app navigation screens. The watch shows
Phone connected in green or Phone offline in muted text above its sync status. Both observe
reachable companion capabilities, refresh on opening, and recheck every ten seconds while
visible. These checks do not import or edit reading records. Data Layer reachability can
include Wi-Fi/cloud routes and can take a short time to reflect a disconnection.
