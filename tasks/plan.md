# Next three reader features — October 21, 2026

Implement the remaining feature recommendations from `docs/FEATURE_OPTIMIZATION_REVIEW_2026-10-21.md`, preserving the current uncommitted work. User authorized implementation; no extra approval checkpoint is needed.

1. Recognized-PDF library search: reuse the disposable FTS passage tables. Index finalized page text with a revision-qualified source identity and printed-page locators. Observe OCR finalization/removal as well as library changes. Verify replacement, deletion, restart identity, Unicode and page navigation.
2. Durable offline preparation: per-book unique WorkManager work, existing authenticated cloud transfer and optional existing recognition work. Persist only book/model/options identifiers in job inputs, never credentials. Expose status/cancel/retry in the library. Verify deduplication, local/cloud cases, retries and cancellation.
3. OCR storage: model sizes and removal plus per-book recognized-text sizes/removal in Privacy & data. Prevent model removal while queued/active recognition uses it. Coordinate book cleanup with workers; invalidate derived search/read state and preserve original books/annotations/progress.

Work through focused regression tests for each slice, then compile the app and run the full debug unit and JavaScript suites. The known Data Usage localization-guard failure must be reported unless independently fixed within scope. No new server, scheduler framework, semantic search, byte-range downloader, or automatic OCR without explicit selection.

Existing task files were absent before this plan was created. Progress and acceptance checks are tracked in `tasks/todo.md`.
