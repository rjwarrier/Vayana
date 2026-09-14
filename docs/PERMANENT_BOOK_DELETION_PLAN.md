# Plan: delete a book permanently, everywhere

**Goal:** one action that removes a book for good — from this device, from every synced device, and from the
GitHub sync repository — including its file, covers, highlights/notes and reading history.

Status: proposal, 2026-09-13. Nothing here is implemented yet.

---

## 1. What exists today (and why it isn't enough)

| Piece | Today | Gap |
|---|---|---|
| **Delete** (Book Detail ⋮) | `BookRepositoryImpl.softDelete`: sets `books.isDeleted = 1`, writes a `book` tombstone. | Fine as "move to Recently deleted". |
| **Delete forever** (Recently deleted) | `purge`: writes the *same* `book` tombstone, then `DELETE FROM books`. Annotations, reading sessions and shelf links cascade (FKs). | Other devices treat the `book` tombstone as a **soft** delete (`applyCloudTombstone` → `softDeleteBySyncId`), so the book sits in *their* Recently deleted. |
| Local files | Neither path deletes `books/<uuid>.epub`, `covers/…`, `customCoverPath`, `goodreadsCoverPath`. The string even says the file "is kept for maintenance cleanup later". | Files leak on the device. |
| Cloud files | `CloudAssetStore` has only `put`/`get`. `GitHubContentsAssetStore` can delete, but `validateSyncDocumentPath` only allows snapshot JSON. Asset ids are random per upload (`CloudBookAssetTransfer`: `UUID…`), stored at `vayana/assets/xx/yy/<id>.bin`. | Encrypted book/cover blobs are never removed. |
| Tombstone vs. edit | `TombstoneDao.appliesOver`: a local row edited after `deletedAt` **cancels** the tombstone. | Right for soft delete; wrong for "delete everywhere" — a stale device could keep (and re-publish) a book whose cloud file is gone. |
| Related data | Vocabulary cards keep a dangling `bookId` (not an FK, by design). `book_aliases` rows for the book stay. | Dangling references; an alias could tie a future re-import to the purged identity. |
| Git history | Contents API deletes create new commits; earlier commits still contain the encrypted blobs (see `GITHUB_SYNC_IMPLEMENTATION_PLAN.md` §9). | Must be stated honestly in the UI; true erasure needs history rewriting (out of scope). |

## 2. Behaviour to build

### 2.1 User-facing

- **Book Detail ⋮ → "Delete…"** opens one dialog with two choices:
  - **Move to Recently deleted** — today's soft delete (restorable, synced as today).
  - **Delete permanently everywhere** — the new action.
- **Recently deleted → "Delete forever"** becomes the same *everywhere* action (today's local-only-ish purge is
  inconsistent with sync and goes away).
- **Confirmation dialog** (destructive styling, no undo) lists exactly what goes, with counts:
  - the book file and covers on this device;
  - the cloud copy in your GitHub sync repository (shown only when the book has uploaded assets);
  - N highlights & notes, reading history (M hours — removed from Statistics), shelf memberships, Read Next entry;
  - on every device that syncs this library.
  - Kept: vocabulary words (their book link is cleared; the saved book title stays).
  - Footnote: "Older encrypted versions remain in your GitHub repository's history."
  - Confirm button: **Delete everywhere**. Recommended extra guard: a checkbox "I understand this can't be undone".
- **After confirming:** the book disappears immediately; snackbar "“Title” deleted everywhere." If sync isn't
  ready/online: "Deleted from this device. The cloud copy will be removed on the next sync."
- **Settings → sync health card:** a row "Cloud files waiting to be deleted: N" while the queue is non-empty, with the
  last error if deletions keep failing.
- **Another device** that syncs a purge: the book is removed without a prompt; the next sync summary says
  "1 book deleted on another device".
- Physical books (no file) and never-synced books use the same action; the cloud part is simply skipped.

### 2.2 Rules

1. **Purge beats edits.** A purge is explicit and permanent: it applies even if the target device edited or read the
   book after `deletedAt`. (Soft-delete tombstones keep today's "newer edit wins" rule.)
2. **Purge blocks children.** Any synced annotation, reading session, shelf membership, reading-progress patch or
   book record whose book `syncId` has a purge tombstone is ignored on merge and never re-exported. This covers
   devices that were offline with unsynced highlights for that book — one rule instead of per-child tombstones.
3. **A purge doesn't block a fresh re-import.** Importing the same file later creates a new `syncId`; purge
   tombstones are keyed by the old `syncId`, and the purged book's `book_aliases` rows are removed.
4. **Publish first, delete blobs second.** Cloud files are deleted only after the tombstone has been published, so
   no device is ever told to download a book that the repository no longer has. A crash between the two just
   leaves queued deletions for the next sync.
5. **Every device that purges queues the asset ids it knows.** If device B uploaded a different revision, B queues
   that one when it applies the purge. Deleting an already-deleted asset (404) counts as success.
6. **Tombstones are never garbage-collected** (existing rule in the sync plan) — offline devices depend on them.

## 3. Design

### 3.1 Data (DB v20)

- **New tombstone type** `TombstoneEntityType.BOOK_PURGE("book_purge")`, syncId `purge:<bookSyncId>` (same pattern as
  `readingProgressResetTombstoneId`). `tombstones.syncId` is the primary key, so a separate id is required.
  The existing `book` tombstone is **also** written, so older app versions (which ignore unknown types) still at
  least soft-delete the book.
- **New table** `pending_cloud_deletions`:
  `assetId TEXT PRIMARY KEY, kind TEXT ('book_file'|'cover'), queuedAt INTEGER, attempts INTEGER, lastError TEXT?`.
  Local only (not in the portable snapshot). Migration `MIGRATION_19_20` + `schemas/20.json` + changelog entry.
- **DAO additions:** `BookDao.purgeAny(id)` (no `isDeleted = 1` precondition), `VocabularyCardDao.detachBook(bookId)`,
  `BookAliasDao.deleteBySyncId(syncId)`, `PendingCloudDeletionDao` (insert-ignore, list, delete, record failure).

### 3.2 Repository

`BookRepository.purgeEverywhere(id: Long): PurgedBook?` — one transaction:

1. Load the book (live or soft-deleted). Collect local paths (`filePath`, `coverPath`, `customCoverPath`,
   `goodreadsCoverPath`, de-duplicated) and asset ids (`fileAssetId`, `coverAssetId`).
2. Upsert tombstones: `book` (for old clients) and `purge:<syncId>` (`BOOK_PURGE`).
3. Queue asset ids into `pending_cloud_deletions`.
4. `VocabularyCardDao.detachBook`, `BookAliasDao.deleteBySyncId`, `BookDao.purgeAny` (cascades annotations,
   sessions, shelf links; FTS triggers remove the search rows).
5. Return `PurgedBook(title, localFiles, queuedAssetCount)`.

`applyPurgeTombstone(bookSyncId, deletedAt): PurgedBook?` — same steps for a synced purge (no new tombstones
written; the incoming one is stored).

**Local file deletion** happens *after* the transaction commits, via a small `BookFileCleaner` in
`core/filesystem` that only deletes paths that resolve under `StorageRoots.rootDir` (reuse the check in
`LibraryViewModel.removeLocalFileFromLibrary`). A failed file delete is logged, not fatal.

### 3.3 Sync

- **Export:** `SnapshotExporter` and `ReadingProgressOnlySyncer` already export all tombstones; add a guard that
  skips any child row (annotation, session, membership, vocabulary link) whose book has a `BOOK_PURGE` tombstone.
- **Merge** (`LibraryViewModel.mergeCloudLibrary` and the repositories it calls):
  - `applyCloudTombstone`: new `BOOK_PURGE` branch → `applyPurgeTombstone` + `BookFileCleaner`; no `appliesOver`
    check (rule 1). Count it for the "deleted on another device" summary.
  - `mergeCloudBook`, `AnnotationRepository` merge, `ReadingSessionRepository` merge, shelf membership merge and
    `applySyncedReadingProgress`: skip when `tombstoneDao.findBySyncId("purge:$bookSyncId") != null`.
  - Tombstones must be applied **before** books/annotations in the merge so rule 2 holds within one sync.
- **Cloud deletion step** (new `CloudAssetDeletionProcessor` in `core/sync/asset`):
  - Runs at the end of `syncNow` after `putPortableSnapshotDocuments` succeeds, and after a successful
    `pushPortableReadingProgress` in progress-only sync (rule 4).
  - For each queued id: `store.delete(CloudAssetLayout.pathFor(id))`; on success or 404 remove the row; on other
    errors increment `attempts`, store `lastError`, keep it. Stop early on 401/403/rate-limit responses.
  - Batch size cap per sync (e.g. 20) to stay within GitHub rate limits; the rest waits for the next sync.
- **Asset store API:** add `suspend fun delete(path: String)` to `CloudAssetStore`. In `GitHubContentsAssetStore`,
  validate with `validateAssetPath`, look up the blob SHA, `DELETE` with commit message
  "Delete Vayana asset <path>", treat 404 as already gone. (Later optimisation: one Git Data API commit removing a
  whole batch instead of one commit per file.)

### 3.4 In-flight work

- **Open reader / downloading / uploading the same book:** `purgeEverywhere` first cancels an active cloud download
  for that book (`cloudBookDownloadProgress`), and asset upload loops re-check that the book still exists before
  `markFileAssetUploaded` (and delete a just-uploaded orphan blob if it doesn't). If the reader has the book open,
  it navigates back when the book row disappears.
- **Pending upload (`UPLOAD_PENDING`)**: nothing to delete in the cloud; the queue stays empty for that book.

### 3.5 UI

- `BookDetailScreen`: replace the delete `ConfirmActionDialog` with `DeleteBookDialog` (two options + the
  permanent confirmation step). `onDeleteBook` splits into `onMoveToRecentlyDeleted` and `onDeletePermanently`.
- `RecentlyDeletedScreen`: "Delete forever" opens the same permanent confirmation.
- `LibraryViewModel.deletePermanently(bookId)` → repository → `BookFileCleaner` → emits a message
  (`DELETED_EVERYWHERE` / `DELETED_CLOUD_PENDING`), then pops the detail screen.
- `SettingsSyncCards.GitHubSyncHealthCard`: pending cloud deletions row.
- Strings in `core/resources`; E-Ink: destructive colours must keep contrast (use `error`/`onError` roles).

## 4. Implementation order

| Phase | Work | Rough size |
|---|---|---|
| 1. Data | `BOOK_PURGE` tombstone type + id helpers; DB v20 (`pending_cloud_deletions`), DAOs; `purgeEverywhere`; `BookFileCleaner`; vocabulary detach, alias cleanup. | M |
| 2. Cloud store | `CloudAssetStore.delete`, GitHub implementation (404-tolerant), `CloudAssetDeletionProcessor` wired after publish in both sync paths. | M |
| 3. Sync rules | `BOOK_PURGE` in `applyCloudTombstone`; purge-blocks-children guards in merges and exporters; tombstones applied first. | M–L |
| 4. UI | Delete dialog with two choices; permanent confirmation with counts; Recently deleted change; snackbar messages; sync health row. | M |
| 5. Tests & docs | See §5; update `FEATURES.md`, `DATABASE_CHANGELOG.md`, `GITHUB_SYNC_IMPLEMENTATION_PLAN.md` (§4 tombstones, §9 asset deletion), `HANDOFF.md`. | S–M |

Ship phases 1–3 behind the existing UI first (no entry point), then phase 4, so sync behaviour can be verified with
two devices before the button appears.

## 5. Tests

- **Unit (JVM):** tombstone id helpers; purge-beats-edit and purge-blocks-children decision functions (extract them
  as pure functions next to `TombstoneResolution.kt`); `PortableTombstone` JSON round-trip for `book_purge`;
  `CloudAssetDeletionProcessor` with a fake `CloudAssetStore` (success, 404, 403 stop, retry counting, batch cap);
  `GitHubContentsAssetStore.delete` with the existing fake HTTP client in `GitHubContentsAssetStoreTest`.
- **Robolectric + in-memory Room** (Robolectric 4.13 is already used in `feature/settings`; add it to
  `core/database` tests): `purgeEverywhere` removes the row, cascades children, detaches vocabulary, removes aliases,
  writes both tombstones and queues asset ids; `applyPurgeTombstone` on a live, soft-deleted and edited-after book;
  `MIGRATION_19_20` against `schemas/19.json`.
- **Manual, two devices:**
  1. Device A purges a synced book; sync A; sync B → gone on B, blobs gone from the repo, no errors.
  2. B offline adds a highlight to the book, A purges, B syncs → highlight not republished, book gone on B.
  3. A purges while offline → snackbar says pending; later sync deletes the blobs; health row clears.
  4. Re-import the same EPUB on A after the purge → imports as a new book and syncs normally.
  5. Old app build on B (without `book_purge`) → book goes to B's Recently deleted, nothing crashes.

## 6. Decisions to confirm

1. **Reading history:** remove the book's sessions from Statistics (recommended; matches "permanently") or keep the
   time in totals?
2. **Purge beats newer edits on other devices** (recommended), or ask on the other device?
3. **Recently deleted "Delete forever" = everywhere** (recommended), or keep a separate device-only purge?
4. **Undo:** none, with the explicit confirmation (recommended), or a short delayed-commit undo snackbar?
5. **Git history:** accept that older encrypted blobs stay in repository history (recommended; documented in the
   dialog), or later add an optional "compact sync repository" tool that rewrites history?

## 7. Later, not in this plan

- Sweeping **orphaned assets** left by earlier uploads (e.g. after replacing a book's source file): list
  `vayana/assets/`, subtract ids referenced by the latest snapshot and the pending queue, delete only blobs older
  than a grace period so another device's not-yet-published upload is never removed.
- Bulk selection and deletion in the Library.
- Auto-purging Recently deleted after N days.
