# Next three reader features

- [x] OCR search source/revision lifecycle and page text tests; focused OCR suite passes.
- [x] FTS accepts recognized PDF sources, replaces stale revisions and filters unavailable books; database/search tests pass.
- [x] PDF search hits use printed scan-page CFIs; index/controller integration and reader regression tests pass. No device navigation run.
- [x] Durable preparation worker and unique-work request/status actions; identity/local-file/network-option/retry/cancel checks pass. Live cloud transfer remains untested.
- [x] Library preparation selection/options and queue UI are wired; library compile/tests pass. No device UI run.
- [x] OCR storage ownership/removal operations prevent active-model deletion and late text recreation; focused tests pass.
- [x] Privacy storage UI lists models and individual books, confirms cleanup and reports errors; settings compile/tests pass.
- [x] Reviewed final changes, assembled debug APKs, ran full debug unit and reader JavaScript checks, documented results and limits.

Final result: 953/954 Android tests and 188/188 JavaScript tests passed. The only failing gate is the pre-existing Data Usage localization-parser false positive; it remains enabled. See `docs/NEXT_FEATURES_IMPLEMENTATION_2026-10-21.md`.
