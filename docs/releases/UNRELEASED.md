# Changes since v0.87

These notes describe changes on `main`, not a new published APK release. The
[v0.87 release](https://github.com/rjwarrier/Vayana/releases/tag/v0.87) currently
contains the phone APK only. Build current phone and watch apps together to use
the companion. See [Wear OS setup](../WEAR_OS.md#install-and-build).

## Added: Wear OS physical-reading companion

- Cache currently-reading physical books from the paired phone, including covers.
- Start, pause and save watch-owned reading timers and page updates offline.
- Mirror a companion timer and send shared controls while retaining ownership on
  the device that started it; queued actions require acknowledgement.
- Track a daily reading goal, choose a session reminder and enable watch haptics.
- Use an ambient display, rotary page input, reading tile and watch-face complication.
- Retry queued sessions after reconnection, preserve undelivered records and
  review page conflicts or ambiguous reading-time overlaps explicitly.

## Fixed: reading-time synchronization

- Carry session checkpoints through reading-progress sync so reading time follows
  the reader between devices without repeatedly adding earlier checkpoint time.
- Preserve newer reading progress while merging remote session data.
- Prevent backward wall-clock changes from producing a crashing checkpoint;
  ignore invalid or conflicting checkpoints safely.
- Avoid full watch sync work for unrelated ebook checkpoint updates.

## Compatibility and validation

The companion needs Wear OS 3+, Google Play services, a paired Android phone and
matching app signatures. It is a physical-reading tracker, not an EPUB/PDF reader.
Data Layer companion sync is separate from optional GitHub library sync.

Automated unit tests and debug builds do not establish real-device delivery,
notification timing or watch layout. Follow the [paired-device checklist](../WEAR_OS.md#verification)
before distributing a watch release.
