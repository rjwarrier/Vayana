# OCR sync debug and optimization pass

Verified on 8 November 2026 with the connected Pixel 9 Pro XL and **Ram C/O Anandi**.

## Result

The phone contained all 324 recognized pages, joined-line data, a generated text EPUB, and a printed-page map. The existing sync implementation transferred the original PDF, metadata and annotations, but never transferred these OCR files.

Completed OCR now participates in **Full Sync**. The updated build was installed over the existing app with the matching signing key. The book's OCR was uploaded to the configured GitHub sync repository with explicit user approval, then downloaded into a separate empty store and validated successfully. No recognition model or OCR rerun was needed for that restoration.

## Changes and checks

| Area | Result |
|---|---|
| Complete-page detection | Checks saved pages against the actual recorded PDF page count. Legacy OCR gets a one-time page-count check against the original PDF. A contiguous partial run cannot publish as complete. |
| Transfer contents | Includes recognized words and boxes, line joins, the generated text EPUB, and printed-page mapping. Models and unfinished recognition jobs stay local. |
| Encryption and integrity | Uses the existing passphrase and authenticated asset encryption, bound to the PDF hash. Downloads must match their expected byte count, archive hash, content revision, and source book. |
| Repeated sync | Stable content revisions skip unchanged transfers. A verified local archive cache avoids repeated JSON parsing and compression. |
| Cloud requests | One shared conditional directory listing supplies OCR references for all PDFs in a sync. Metadata and archive downloads have explicit size limits. |
| Upload memory | Encrypted payloads use the existing streaming file-upload path, avoiding a large Base64 payload copy in memory. Temporary upload files are removed after completion or failure. |
| Concurrent changes | References publish only after the asset exists, with compare-and-set protection and at most three conflict attempts. Completed versions converge using modification time and a deterministic revision tie-break. |
| Active recognition | Busy OCR writers are skipped until a later sync, so Full Sync does not wait for an entire recognition job. Local content changes during a download prevent an unsafe replacement. |
| Download installation | Validates page JSON, expected pages, text EPUB structure, page-map destinations, ZIP entry names, duplicates, and expansion limits before installation. |
| Interrupted installation | A recovery journal restores a previous folder or retains an already-installed new folder after a process interruption. Staging stays outside the OCR orphan-cleanup scan. |
| Permanent deletion | Queues the cloud OCR descriptor and its referenced encrypted archive alongside the book's other cloud deletions. Processing happens after the existing tombstone publication step. |
| Older clients | OCR references live separately from the main snapshots, so an older client publishing a snapshot cannot discard the new OCR reference. |

## Validation

| Suite | Tests | Failures |
|---|---:|---:|
| `:format:ocr:testDebugUnitTest` | 37 | 0 |
| `:core:sync:testDebugUnitTest` | 96 | 0 |
| Library OCR transfer and permanent-deletion tests | 18 | 0 |
| **Total** | **151** | **0** |

Regression cases cover encrypted round trips, search text and text-view preservation, archive caching and invalidation, corrupt downloads, wrong passphrases, partial recognition, malformed page maps, unsafe ZIP paths, decompression limits, cancellation, interrupted directory replacement, concurrent publishers, equal-timestamp convergence, shared directory requests, streamed payload correctness, temporary-file cleanup, and permanent-deletion queuing. Permanent deletion was tested with isolated databases and mock cloud stores; the live book was retained.

The Android app build completed successfully. The installed app launches normally.

## Live measurements

- **324 pages** restored from the encrypted cloud copy into an empty isolated store.
- Restored data includes searchable text and a usable generated text EPUB and page map.
- Compressed OCR bundle: **1,539,274 bytes** (about 1.54 MB).
- Immediate unchanged sync: **0 OCR uploads and 0 OCR downloads**.
- Unchanged-sync metadata traffic: **1 request**, **424 bytes sent**, **1,384 bytes received**, **1,028 ms** on this connection. These are observed wire counters, including request/response overhead.
- Original OCR input files, database, reader settings, and all **118 book files** retained their original checksums. Page-count bookkeeping and the disposable archive cache were added separately.

## Using the fix elsewhere

Other devices need a build containing this feature, the same GitHub sync configuration and passphrase, and **Full Sync**. Reading-progress-only sync continues to transfer reading progress. OCR restoration was verified using an isolated empty store on the Pixel; a second physical device was not connected during this check.

Removing recognized text locally leaves its cloud copy available for restoration by a later Full Sync. Permanent book deletion follows the cloud-deletion path described above.

## Final optimization pass

The follow-up pass removes redundant ZIP recompression during OCR restoration: file revisions are checked with a streaming digest, and the already-verified received archive becomes the local cache. The regression test verifies the received archive bytes are retained. Cleanup position recovery now uses the same offline XHTML entity-preserving parser as cleanup itself, with regression coverage for named entities in the restored passage.

Final validation: **432 unit tests passed** (37 OCR, 96 sync, 299 library), plus the isolated Android cleanup test on the connected Pixel. The signed debug APK was installed in place. Database, reader settings, OCR files/cache and all 118 book files matched their pre-install checksums.
