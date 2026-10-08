# Unreleased

- Resolve saved cover paths in Stats through the shared library path resolver so reading-record covers display correctly.
- Add reading records to Stats with covers, book details, start/finish dates and accumulated recorded time, including physical books still in progress. Tap a record to open Book details.
- Record a finish date when a physical or other page-tracked offline book is added at its final page, so yearly books-read statistics include it. Explicit finish dates remain unchanged.
- Prevent invalid nonpositive physical-book page totals from crashing Wear OS catalog sync; treat them as unknown totals.
- Stop reading-interval subtraction scans when sorted coverage cannot affect the remaining source range. See the [measured experiment](../PERFORMANCE.md).

See [Vayana 0.90](v0.90.md) for the previously released changes since v0.87.
