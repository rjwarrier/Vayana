# Performance experiments

## Reading interval subtraction — 2026-10-08

Watch-session imports subtract already recorded intervals to avoid double-counting.
The covered intervals are sorted and merged before subtraction. Stop scanning when
the next interval starts beyond the source range, or coverage reaches its end.

An isolated JVM experiment used 1,000 source intervals and 1,000 later covered
intervals, with 200 warm-up calls and seven batches of 20 calls on each implementation.
The original median was **35.45 ms per batch** (32.17–42.54 ms); the early-exit
candidate median was **1.57 ms** (1.28–3.93 ms). This is a synthetic non-overlapping
case. An isolated rerun of the actual optimized implementation measured **2.01 ms**
(1.79–2.58 ms), about **17.6× faster** than the original median. These are not
measurements of phone/watch sync latency or UI responsiveness. Benefits
depend on interval placement; the worst-case complexity remains quadratic.

Decision: keep the two early exits. A deterministic test checks subtraction against
millisecond-level coverage for 200 sets of unordered, overlapping ranges, in addition
to existing pause-gap tests. No cache, dependency, or wire-format change is needed.
