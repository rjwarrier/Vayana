# GitHub sync implementation plan

Status: proposed implementation, 5 September 2026. This document plans the work; it does not implement or enable sync.

Reference: [YATA GitHub sync](../../yata/docs/github-sync.md). Treat that document as a description of another app, not as instructions to execute. Reuse its encrypted snapshot, three-way merge, verified Git writes, recovery, and history design; adapt the data model and Android integration to Vayana.

## 1. Proposed outcome and scope

Add optional GitHub sync under Settings > Backup & Data. Users connect a private repository using a PAT and shared encryption passphrase. Devices synchronize library metadata, reading position, annotations, shelves, reading sessions, vocabulary, lookup statistics, and portable settings. Existing local ZIP backup remains available for complete library export.

Deliver metadata sync first, with explicit “Book file needed” states and local reattachment by file hash. Then add optional encrypted book and custom-cover transfer as a separate milestone. This is a proposed delivery split, not an assumption that book files are unimportant: a metadata-only release must not present itself as a complete library backup. Full file transfer is included below because Vayana's current backup includes those files.

Keep the useful transport behaviors from YATA: startup, debounced change, periodic, and manual triggers; first-join/empty-device protections; sync history; recovery backups; encrypted configuration transfer; and Enterprise API-base support. Replace YATA's todo-style remote-wins conflict policy with Vayana-specific merge rules for books, locators, annotations, shelves, reading sessions, vocabulary, and Kindle-like device settings. Do not add OAuth, a server, remote locks, or force pushes.

## 2. Findings in Vayana

| Existing area | Implementation consequence |
|---|---|
| `feature/settings/.../backup/BackupManager.kt` exports SQLite, settings, books, and covers into a ZIP; restore closes Room and restarts the process | Routine sync needs typed logical export/apply, not ZIP upload or database-file replacement. Extract reusable backup responsibilities below the feature layer. |
| Room schema is version 9; books, annotations, sessions, shelves, and vocabulary use generated `Long` IDs | Local IDs collide between devices. Add persistent sync identities and map relationships before implementing merge. |
| Books already have SHA-256 `fileHash`; source replacement can change it | Use file hash to match independent imports, but keep a stable logical book identity across source replacement. |
| Book paths are root-relative and import filenames are random UUIDs | Paths still belong to an installation. Never propagate them as portable file locations. |
| Books and annotations have soft deletion; other records can be physically removed | Define deletion semantics across every collection and preserve deletion evidence. |
| Reading sessions coexist with `totalReadingSeconds`; lookup stats store aggregate counts | Generic remote-wins record merging would lose increments or double-count totals. These need specialized merge rules. |
| Settings export currently includes every registry setting | Introduce an explicit portable-setting allowlist. Treat Kindle-style identity/settings carefully: device name/label, device class, storage policy, credentials, sync state, hardware controls, and other installation capabilities stay local; reading preferences that users expect to follow them may sync. |
| WorkManager is in the version catalog; `VayanaApp` currently has no worker initialization | Add scheduler, worker injection, and startup orchestration. |
| Manifest explicitly prohibits Internet and sets `allowBackup=false` | The requested optional network feature changes the former architectural requirement. Add Internet permission and update the design documentation; retain offline functionality and disabled Android backup. |
| `FoliateBookEngine` delegates interception to the asset loader, which can return null | Before granting Internet permission, explicitly block unhandled external reader requests and navigation so imported books cannot gain network access. |

Primary integration files: `core/database` entities/DAOs/repositories/migrations; `core/datastore` settings; `core/filesystem/BookFileImporter.kt`; `feature/library/LibraryViewModel.kt`; `feature/reader/ReaderViewModel.kt`; `feature/settings/SettingsScreen.kt` and `SettingsViewModel.kt`; `app/VayanaApp.kt`, navigation, and manifest. Paths above abbreviate the existing `src/main/kotlin/com/vayana/...` hierarchy.

## 3. Architecture

Add three modules, keeping UI out of the sync implementation:

| Module | Responsibilities and proposed types |
|---|---|
| `:core:backup` | Extract existing local backup service; add `PortableSnapshot`, `SnapshotExporter`, `SnapshotValidator`, `SnapshotApplier`, `RecoveryBackupStore`. Depends on database, settings, filesystem, and common. |
| `:core:sync` | `SnapshotNormalizer`, `SnapshotMerger`, `SyncStateStore`, `SyncCoordinator`, `SyncWorker`, `GitHubApi`, `GitHubSnapshotPublisher`, `GitHubSyncManager`, `SnapshotCipher`, `SyncSecretsStore`, `GitHubConfigTransfer`. Depends on backup and existing core modules. Keep merge logic platform-independent within the module. |
| `:feature:sync` | Setup/status, first-join and recovery dialogs, history inspection, config transfer, view models. Depends on core sync and existing UI conventions. |

Wire the modules through `settings.gradle.kts`, app dependencies, Hilt, and navigation. Settings links to the sync feature through navigation callbacks. Move backup ownership without changing the existing ZIP format unnecessarily; retain its validator tests.

One coordinator serializes sync, recovery, local ZIP restore, and configuration changes. A separate short write gate covers snapshot capture and final local apply; network operations never hold that gate. All relevant data writes, including direct DAO callers, must participate in the gate/revision mechanism.

## 4. Portable schema and identity

Define a separately versioned sync schema, independent of the Room version. Include format version and compatibility metadata outside the canonical user-data hash. Validate supported versions before merging; never let an older client silently drop unknown required fields and republish.

Add unique `syncId` values for books, annotations, shelves, sessions, and vocabulary while retaining existing local primary keys. Migrate existing rows once; retain these identities in local backups. Snapshot references use sync IDs, never local `Long` IDs. Shelf memberships use `(bookSyncId, shelfSyncId)`.

For independently imported identical files, reconcile book identities by `fileHash`, select a deterministic canonical identity, and persist aliases; rewrite all child references. Do not match books just by title/author. Keep identity stable when replacing an EPUB; pair locators and annotation anchors with the content hash they reference. If another device still has an older file revision, mark the locator unavailable instead of navigating an incompatible CFI. Audit the legacy `groupId` field and migrate supported relationships explicitly rather than publishing a local number.

Normalize collection order, field defaults, null handling, membership order, and numeric serialization. Exclude file paths, generated cover paths, secrets, baseline/checkpoints, device identifiers used only for configuration, and backup timestamps. SHA-256 of canonical plaintext determines whether data changed; fresh encryption nonces must not create redundant commits.

Maintain tombstones for hard deletions, including memberships. Distinguish soft-deleted books in Recently Deleted from permanent purges. Do not garbage-collect tombstones merely because they are old: offline devices may still depend on them. A future compaction protocol must explicitly reset/rejoin stale clients.

### Vayana merge and conflict rules

| Data | Proposed rule |
|---|---|
| Book identity and metadata | Match by `syncId` first and verified `fileHash` aliases second. Merge title/author/publisher/description fields with three-way rules. Keep user-edited metadata over machine-extracted metadata when both changed. If two devices edit the same user-owned metadata field differently, keep the newest field value by its edit timestamp and write the older value into a conflict artifact. Never match only by title/author. |
| Book file revisions | A book record can exist without the file on a device. Match file-backed revisions by content hash, not path. If a locator or annotation refers to a different content hash than the attached local file, mark it unavailable/reattach-needed instead of navigating to a potentially wrong position. Source replacement creates a new file revision under the stable book identity. |
| Reading position | Treat content hash, locator, percent, and position timestamp as one unit. Apply the position with the newest explicit reading timestamp when the content hash is compatible. Do not take maximum percent because rereading is legitimate. If timestamps are equal or incomparable and locators differ, keep the local active-reader position, record the remote alternative, and surface a resumable conflict choice when the book opens. |
| Annotations and highlights | Use stable annotation IDs and content-hash-aware anchors. Independent annotations are unioned. Edits to color/note text/selection range merge by field when possible. If two devices edit the same note text differently, keep both copies: retain the newest as the main note and store the other as a conflict alternative so user text is not silently discarded. Delete-versus-edit keeps the edit visible and marks the delete as a pending conflict unless the user confirms deletion. |
| Vocabulary and lookup history | Merge vocabulary entries by normalized word plus language/source book where available. User-authored notes/favorites use newest-field merge with conflict alternatives. Lookup counts use per-device or per-installation monotonic counters keyed by normalized word; merge each origin by maximum, then sum. Restored installations get fresh writer origins. |
| Shelves, collections, and read-next state | Merge membership/tombstones independently. Preserve explicit read-next queue timestamps and use `(timestamp, deviceName, syncId)` as a deterministic tie-breaker only when the user order cannot otherwise be reconstructed. |
| Reading sessions | Union by stable session ID; repeat downloads never add time again. Derive totals from merged sessions plus a documented legacy remainder for time that predates session tracking. |
| Portable reader settings | Sync only settings that are genuinely user preferences across devices: theme intent, typography preferences, margin/line-height defaults, dictionary/language preferences, and selected portable behavior toggles. Use newest-field merge with conflict alternatives for simultaneous changes. |
| Kindle-like device settings | Keep device name/label, device capability profile, storage/download policy, Wi-Fi-only preference, local file availability, hardware key mappings, display cutout/window metrics, credentials, sync baseline, pending journals, and install/device identifiers local. Include device name in commit labels and conflict reports, but exclude it from the canonical portable user-data hash unless it is part of explicit sync configuration metadata. |
| Missing book references | Retain legitimate metadata-only books. Preserve vocabulary text when a source is purged. Validate/remap annotation and session references before any cascading deletion. |

Legacy copies made before sync IDs exist cannot always be distinguished from independent records. First-join inspection must disclose possible duplicate annotations/sessions/statistics and allow choosing one side; do not silently deduplicate by matching text or timestamps.

## 5. GitHub transport, setup, and encryption

Use an HTTPS-only API client with typed failures, bounded response sizes, cancellable requests, timeouts, token redaction, and no cross-origin credential forwarding. Normalize owner/repo and branch safely, support Enterprise API prefixes, and pin an API version supported by the target server.

Use `vayana/snapshot.json` for the encrypted snapshot and add README only if absent. Preserve unrelated tree entries. Read metadata, exact branch ref, commit, tree, and blob; verify Git blob SHA over `blob <length>\0<bytes>`. Publish blob, tree based on the observed tree, commit parented to observed head, and ref update with `force:false`. This matches GitHub's documented fast-forward behavior. [Git references API](https://docs.github.com/en/rest/git/refs)

Correct two setup assumptions in the reference:

1. GitHub documents that refs cannot be created in an empty repository even when a commit object exists. Support initialized repositories first. For an app-created repository use `auto_init:true`; for an existing empty repository show an initialization action/instructions. If preserving the “no Contents API writes” design, require initialization before sync rather than promising parentless ref creation. [Git references API](https://docs.github.com/en/rest/git/refs)
2. A repository-scoped Contents-write PAT is the normal sync credential. Repository creation requires different privileges; expose creation as optional and provide a manual creation path without requesting broader rights for routine sync. Do not interpret an ambiguous 404 as proof the repository does not exist. [Repository creation API](https://docs.github.com/en/rest/repos/repos#create-a-repository-for-the-authenticated-user)

Recheck private visibility and write access on every run. Use repository ID plus API origin, branch, path, and schema family to scope state; changing credentials for the same scope should not discard the baseline. Changing the destination starts a checked first join. Default-branch discovery must not silently overwrite an explicitly selected existing branch.

Store the PAT and passphrase encrypted with an Android Keystore-held key. Do not introduce deprecated `EncryptedSharedPreferences` solely to copy YATA's implementation; Android lists its security-crypto API as deprecated. [Android API reference](https://developer.android.com/reference/androidx/security/crypto/EncryptedSharedPreferencesKt)

Use a versioned AES-256-GCM envelope with fresh salt/nonce, authenticated format metadata, and a password KDF with benchmarked parameters and strict decoding bounds. Review and test any reused YATA crypto code before adopting it. Keep Git object hashing separate from authenticated encryption and canonical hashing. Start with YATA's 60 MiB encrypted snapshot cap as an application limit, not a claim about GitHub's maximum. Bound plaintext and decoding allocations too.

Config export uses a separate transfer password, includes credentials only inside authenticated ciphertext, and excludes device IDs and baselines. Keep YATA's 128 KiB input/64 KiB ciphertext bounds. Import previews destination, validates schema/API base, and saves secrets without displaying or logging them. Passphrase rotation needs a versioned transition/re-encryption policy; the first release should clearly require reconnecting other devices and retaining old passphrases for old history.

## 6. Sync state machine and crash safety

Persist baseline, verified remote head/hash, dirty revision, and pending operation journal under app-private storage excluded from backup. Encrypt baselines, recovery copies, and conflict artifacts containing user data. Scope approvals to the inspected local revision and remote head; changes invalidate the approval.

1. Acquire operation coordination; verify configuration/enabled state and repository policy.
2. Capture coherent local portable data and revision. Use the unchanged path only when local hash and verified remote head match the checkpoint and no journal recovery is pending.
3. Verify that the last accepted head is an ancestor. Compare API first; bounded parent traversal second. A traversal cap means “cannot establish ancestry,” not proof of a rewrite. Stop for user-directed recovery if ancestry cannot be trusted.
4. Download, verify, decrypt, and validate remote; load baseline. Apply first-join, unexpectedly empty local, missing snapshot, and history-rewrite guards before publication.
5. Merge and validate. Persist any required recovery/conflict data before publishing destructive outcomes; backup failure aborts. Store a durable pending operation recording original local revision and intended canonical result.
6. Skip publication if canonical remote already matches. Otherwise upload verified objects and advance the ref. Retry a ref race by reading and merging afresh, up to six attempts with backoff. HTTP transient retries have a separate three-attempt bound and overall run deadline; re-read after ambiguous write timeouts.
7. Re-read the head and snapshot. If a competing writer advanced it, fetch and merge the new state; never checkpoint an uninspected head.
8. Under the write gate, re-export local data and merge edits made during network work into the confirmed remote result. Apply records transactionally in Room while preserving local IDs/file attachments. Those extra local edits remain dirty for a subsequent publish.
9. Room and DataStore cannot share one transaction. Journal the intended portable-setting application, apply it idempotently, verify a fresh export, then finalize checkpoint/baseline and clear the journal. Recover unfinished phases at startup before scheduling new work; protect only the short commit phase against coroutine cancellation.
10. Save the baseline as the confirmed remote state, not the locally re-merged state containing unpublished edits. Report remote success/local recovery pending separately when local commit fails.

Do not hold the reader open on stale entity references after local apply. Refresh annotations/library through Room flows and defer/reconcile reader position changes while the user is actively reading.

## 7. Scheduling and user experience

Add disabled-by-default GitHub configuration, token/passphrase fields, repo/branch/API base, device label, connection validation, frequency, last successful sync, pending changes, and error actions. Show token expiry only when reported by GitHub.

Manual sync and foreground startup call the coordinator. Data changes mark a durable dirty revision and debounce foreground sync for 15 seconds. Reading-position churn needs an upper bound on delay (proposed 2 minutes) plus reader-pause flush. Unique WorkManager jobs provide process-death recovery and network-constrained periodic work. Offer after-change with a 15-minute periodic safety net, then 30/60/120 minutes. WorkManager timing is inexact; 15 minutes is its minimum periodic interval. [Android scheduling guidance](https://developer.android.com/develop/background-work/services/alarms)

Prevent sync's own writes from creating an endless scheduling loop; schedule again only for a remaining dirty revision. Turning sync off cancels routine jobs and prevents a not-yet-published run from publishing. If publication already succeeded, finish local recovery bookkeeping safely. Saved configuration and explicit restore remain accessible.

Show first-join counts for books, annotations, shelves, sessions, and vocabulary. An unexpectedly empty device offers download remote or explicitly propagate intentional deletion. Never let a background worker choose. Retry transient network/server failures and bounded throttles; stop retries for auth, permissions, public repositories, incompatible schema, history changes, and required user choices.

## 8. History, recovery, and restore semantics

List commits affecting the snapshot in pages of 100, with lazy verified/decrypted inspection. Commit titles use a best-effort device label and book/annotation counts; arbitrary historical commits remain visible. Restore stays disabled until validation succeeds. Keep up to 25 local recovery points, removing old ones only after the new point is durably verified.

For damaged head data, inspect at most 20 previous snapshot commits. Never classify auth, rate limits, incompatible app versions, or a wrong passphrase as evidence that remote history should be rewritten. Show recoverable candidates; only a confirmed fresh-device recovery may promote an older valid snapshot automatically. Established devices require explicit selection.

Distinguish two actions:

- **Use latest GitHub snapshot on this device:** save recovery, replace portable local state, preserve local file attachments, and set baseline to the verified latest state. Missing files stay visibly unavailable.
- **Restore this historical version across devices:** after confirmation, publish the selected old snapshot as a new child of the current head using the same race checks, then apply locally. Never move the branch backward.

Do not copy the reference's historical-checkpoint behavior blindly: merely saving an old SHA as baseline can cause the next merge to undo a restore. If a local-only historical preview/restore is added later, pause sync and require an explicit resume decision.

## 9. Optional book and cover transfer

After metadata correctness is proven, add immutable encrypted assets under `vayana/assets/` with a manifest mapping content revisions to opaque asset IDs. Upload new assets before committing the snapshot that references them; unchanged files are not uploaded on reading-position changes. Custom covers transfer as assets; generated covers can be regenerated.

Stream encryption/downloads, enforce per-file and aggregate quotas before publishing, and define chunked assets for files over the chosen blob budget. Download to staging, authenticate and verify plaintext hashes, then atomically attach. Interrupted downloads resume or restart safely. Keep file availability local, support Wi-Fi-only/manual download, and never purge a local book file merely because sync removes its metadata.

Old commits reference old encrypted assets, so Git storage grows even after logical deletion. Show storage implications and avoid automatic pruning. Asset garbage collection/history rewriting is outside this plan. Benchmark realistic large libraries before enabling automatic asset upload by default.

## 10. Ordered implementation milestones and acceptance gates

| Milestone | Deliverables | Required evidence |
|---|---|---|
| 1. Foundation | Module extraction; sync identities/aliases/tombstones; next Room migration and changelog; portable schema/validation; file availability model; offline-policy update and reader network blocking | Existing ZIP import/export still works; migration preserves data/relationships; two independent ID=1 records do not collide; external EPUB requests remain blocked with Internet permission present. |
| 2. Merge and local commit | Deterministic normalizer; baseline store; field/specialized merges; write revisions; recovery store and journal | Repeated merge converges; no duplicated sessions/counts; deletes and file replacements behave as specified; concurrent reader edit survives; process death at each apply phase recovers. |
| 3. GitHub transport | Secrets/cipher; setup; API client; verified Git publication; ancestry and retries | Fake-server race/error tests; private test-repo integration for initialized/default/custom branches; no lost remote commit; preservation of unrelated files; wrong key/corrupt blob/public repo cannot mutate local state. |
| 4. Usable metadata sync | Setup/status and confirmation UI; manual/startup/change/periodic work; missing-file reattach | Two-device offline-edit/reconnect exercise; no network when disabled; app remains usable offline; no write-trigger loop; no accidental empty-device wipe. |
| 5. Recovery parity | History, latest download, historical promotion, encrypted config transfer, token-expiry UI | Restore survives next sync and converges on a second device; interrupted restore recoverable; tampered config rejected; secret material absent from logs/plaintext snapshots. |
| 6. Asset transfer | Encrypted asset transport, quotas, download UI, custom covers | Fresh device can obtain/open synced books; interruptions and oversized files handled; file hashes verified; reading updates do not re-upload unchanged assets. |

Implement in this order: identity/export before merge; merge and recovery before publication; manual sync before automatic triggers; recovery parity before general release; assets after metadata convergence. These are dependency milestones, not time estimates.

Use focused JVM tests for normalization/merge/crypto/HTTP state transitions; Room migration and Android integration tests for transactions, Keystore, DataStore journal recovery, WorkManager, and WebView isolation. Exercise real GitHub behavior only with a dedicated test repository and test credentials. Do not use production libraries for destructive recovery tests.

## 11. Decisions to retain in the implementation record

Proposed defaults are metadata-first delivery, optional later file transfer, latest-compatible reading-position merge, local active-reader protection for ambiguous locator conflicts, initialized repositories, explicit historical promotion, and per-origin lookup counters. These are Vayana-specific choices, not requirements imposed by YATA. Revisit them before their dependent milestone if product preferences differ.

No implementation changes, repository creation, credential setup, or network sync are authorized by this planning artifact itself. The next development slice should be milestone 1 with its migration and portability acceptance tests.
