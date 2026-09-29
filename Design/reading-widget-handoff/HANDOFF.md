# Handoff: Vayana reading-time home-screen widget (redesign)

**For:** Claude Code, working in the Vayana Android project.
**Scope:** the home-screen widget that shows reading time. Change only this widget: its layout, the chart drawing and the numbers it shows. Leave the rest of the app alone.
**Reference image:** `reading-widget-reference.png` (drawn at 3×) and `reading-widget-reference.svg` (1× in dp: 1 SVG unit = 1 dp). Both use sample data.

---

## 1. What changes, and why

The current widget has a smoothed (curvy) line, a dashed average line that also counts today, and loose gridlines with no frame. The new version:

1. Shows **today's reading time** in large type.
2. Shows the **average of the 7 days before today (today excluded)**.
3. Draws a **straight-segment line chart** (no smoothing or Bézier curves) with **8 points**: the 7 averaged days, then today.
4. Draws the average as a **solid horizontal line** across the whole plot.
5. Puts the plot inside a **bordered grid box**: horizontal gridlines at each y-axis step, a vertical gridline at each day, and a 1 dp frame around the plot.
6. Adds a small **"+23m vs avg"** chip comparing today with the average.

## 2. Before you start

- Find the existing widget (search for `AppWidgetProvider`, `GlanceAppWidget`, `appwidget-provider` XML and the current chart code). Keep its framework, whether Glance or RemoteViews, and its update wiring. Replace only the layout and the chart rendering.
- Neither Glance nor RemoteViews can draw paths. Render the **chart area to a `Bitmap` with `android.graphics.Canvas`** (reference code in §7) and show it in an `Image` / `ImageView`. Render the header text as ordinary widget text, not in the bitmap, so it stays sharp and accessible.
- Find where reading sessions are stored (Room). You need total reading **minutes per calendar day** in the device's local time zone.

## 3. Data rules

| Item | Rule |
|---|---|
| Days shown | 8 days: D-7 … D-1, then today (D0). |
| Per-day value | Sum of session durations whose time falls on that local calendar day, in whole minutes. A session that crosses midnight is split at midnight. |
| Today | D0's total so far, including the session in progress if the reader is open. |
| 7-day average | `(D-7 + … + D-1) / 7`, **counting zero days**, rounded to the nearest minute. Today is **never** included. |
| Delta chip | `today − average` in minutes. Positive shows `+23m vs avg` in accent blue. Negative shows `−12m vs avg` in muted text colour (use a real minus sign, U+2212). Zero shows `= avg`. |
| New user (fewer than 7 days of history) | Days before the first recorded session still count as 0 and appear on the chart. When there is no history at all, hide the average line and the chip and show `7-day avg —`. |
| All zeros | Draw the line flat along the bottom, keep the y-axis at 0–30m, and hide the chip. |

**Duration format** (use it in every place the widget shows a time):
`0m`, `7m`, `43m`, `1h`, `1h 6m`, `2h 15m`. Use no leading zeros and no seconds.

**Y-axis scale:** always 3 equal intervals, starting at 0.
Pick the smallest step from `[10, 20, 30, 40, 60, 90, 120, 180]` minutes where `3 × step ≥ max(all 8 values, average)`.
Tick labels: `0`, then minutes below 60 as `30m`, whole hours as `1h` / `2h`, and other values as `1h30`. Keep axis labels compact: no space, no trailing `m` after hours. Example: max 80 → step 30 → ticks `0, 30m, 1h, 1h30`.

**Day labels:** narrow weekday name from the device locale (`DateTimeFormatter.ofPattern("EEEEE", locale)`). Today's label is bold and in the primary text colour.

## 4. Layout (dp at the 4×2 default size of 440 × 220 dp)

```
┌──────────────────────────────────────────────── radius 22 ─┐
│ Reading today                        ▬ 7-day avg      43m  │  header, top padding 14
│ 1h 6m                                      (+23m vs avg)   │
│      ┌───────┬──────┬──────┬──────┬──────┬──────┬───────┐  │
│ 1h30 │       │      │      │      │      │      │       │  │
│   1h ├───────┼──────┼──────┼──────┼──────┼──────┼───────┤  │  plot box, framed
│      │═══════════════════ avg line ═════════════════════ │  │
│  30m ├───────┼──────┼──────┼──────┼──────┼──────┼───────┤  │
│    0 └───────┴──────┴──────┴──────┴──────┴──────┴───────┘  │
│      T       W      T      F      S      S      M      T   │  day labels
└────────────────────────────────────────────────────────────┘
```

- Widget: background `#041A24`, corner radius 22 dp. Use `android:clipToOutline` / Glance `cornerRadius`, with a background drawable fallback below API 31. Padding 16 dp left and right, 14 dp top, 12 dp bottom.
- **Header row**, about 44 dp tall:
  - Left column: "Reading today" (10 sp, `#9CC6D8`), then today's value (27 sp, bold, `#F2FAFD`, 2 dp gap).
  - Right column, right-aligned:
    - Row 1: a legend swatch (13 × 2 dp, `#F2B84B`), 5 dp gap, "7-day avg" (10 sp, `#9CC6D8`), 6 dp gap, average value (13 sp, bold, `#F2B84B`).
    - Row 2, 8 dp below: the chip. Text 9 sp semibold, padding 7 dp horizontal and 3 dp vertical, fully rounded, background `#0C3344`, text colour per §3.
- 10 dp gap, then the **chart area** fills the rest of the widget's width and height. Everything inside it is drawn in the bitmap:
  - Y-label gutter: 31 dp on the left. Labels right-aligned, 6 dp from the box, 8.5 sp, `#7FA8BA`, vertically centred on their gridline.
  - Plot box: from the gutter to the right edge. Its height is the chart-area height minus 22 dp, which is reserved for day labels below.
  - Day labels: centred under each x position, baseline 16 dp below the box, 9 sp, `#7FA8BA`. Today's label uses `#F2FAFD` and semibold.
- X positions: 8 points spread evenly. The first sits on the box's left edge and the last (today) on its right edge, 7 equal gaps between.

**Resizing:** support resizing (`resizeMode="horizontal|vertical"`, minimum about 3×2 cells). Recompute the bitmap from the actual widget size in px, taken from `AppWidgetManager.getAppWidgetOptions` or Glance `LocalSize`. On widths below 280 dp, hide the chip and the "7-day avg" words, keeping the swatch and value.

## 5. Colours and strokes (dark theme; the app is dark-first)

| Token | Hex | Used for |
|---|---|---|
| `bg` | `#041A24` | widget background; ring fill of past points |
| `plotBg` | `#06222F` | fill inside the plot box |
| `grid` | `#1A4254` | inner gridlines, 0.75 dp |
| `frame` | `#2C6378` | plot box border, 1 dp |
| `line` | `#5CC8F0` | data line (2 dp, **miter** joins, no smoothing), point strokes, today's dot, positive chip text |
| `area` | `#22B8E8` at 12% alpha | fill under the data line, down to the box bottom |
| `avg` | `#F2B84B` | average line (solid, 1.5 dp, full plot width), legend swatch, average value |
| `textPrimary` | `#F2FAFD` | today's value, today's day label |
| `textMuted` | `#9CC6D8` | header labels, negative chip text |
| `axisText` | `#7FA8BA` | tick and day labels |
| `chipBg` | `#0C3344` | chip background |

**Draw order** (back to front): plot background → inner gridlines → frame → area fill → average line → data line → past points → today point.
- Past points: circles of radius 3 dp, filled `bg`, 1.5 dp `line` stroke.
- Today: a circle of radius 5 dp, filled `line`, with a 1.5 dp `bg` stroke so it stands off the frame.
- Do not clip points to the plot box; today's dot sits on the right border.

Font: the app's existing type, which appears to be JetBrains Mono. For the numbers in the bitmap, load the same font with `ResourcesCompat.getFont`. If the widget can't reach it, fall back to `Typeface.MONOSPACE`.

## 6. Behaviour

- Tapping the widget opens the app's reading stats screen (the bar-chart tab). If that route doesn't exist, open the app.
- **Updates:**
  - When a reading session ends, and every 5 minutes while the reader is open, if cheap.
  - At local midnight: schedule a `WorkManager` one-time job for the next midnight, then re-arm it.
  - On `ACTION_TIME_CHANGED` / `ACTION_TIMEZONE_CHANGED`.
  - Otherwise use the `updatePeriodMillis` fallback of 30 minutes.
- Accessibility: give the chart image a contentDescription such as *"Reading time, last 7 days: Tue 1h 20m, Wed 1h 12m, … Today 1h 6m. 7-day average 43 minutes."*
- Bitmap size: render at the widget's real pixel size, and keep it under the RemoteViews bitmap memory limit. For large widgets, cap the scale at 2×.

## 7. Reference chart renderer (Kotlin, adapt to the project)

```kotlin
data class ReadingWeek(
    val minutes: List<Int>,          // size 8: D-7 .. D-1, today last
    val dayLabels: List<String>,     // size 8, narrow weekday names
    val averageMinutes: Int?,        // null = no history → hide avg line
)

fun renderReadingChart(ctx: Context, widthPx: Int, heightPx: Int, d: ReadingWeek): Bitmap {
    require(d.minutes.size == 8 && d.dayLabels.size == 8)
    val dp = ctx.resources.displayMetrics.density
    val sp = ctx.resources.displayMetrics.scaledDensity
    val bmp = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
    val c = Canvas(bmp)
    val mono = runCatching { ResourcesCompat.getFont(ctx, R.font.jetbrains_mono) }.getOrNull()
        ?: Typeface.MONOSPACE

    // Geometry
    val gutter = 31 * dp
    val labelBand = 22 * dp
    val left = gutter; val right = widthPx - 5 * dp   // leave room for today's dot
    val top = 5 * dp;  val bottom = heightPx - labelBand

    // Scale (see HANDOFF §3)
    val maxVal = maxOf(d.minutes.max(), d.averageMinutes ?: 0)
    val step = listOf(10, 20, 30, 40, 60, 90, 120, 180).firstOrNull { it * 3 >= maxVal } ?: ((maxVal + 2) / 3)
    val yMax = step * 3f
    fun y(v: Number) = bottom - (bottom - top) * (v.toFloat() / yMax)
    fun x(i: Int) = left + (right - left) * i / 7f

    val p = Paint(Paint.ANTI_ALIAS_FLAG)

    // Plot background
    p.style = Paint.Style.FILL; p.color = 0xFF06222F.toInt()
    c.drawRect(left, top, right, bottom, p)

    // Inner gridlines
    p.style = Paint.Style.STROKE; p.strokeWidth = 0.75f * dp; p.color = 0xFF1A4254.toInt()
    for (k in 1..2) c.drawLine(left, y(step * k), right, y(step * k), p)
    for (i in 1..6) c.drawLine(x(i), top, x(i), bottom, p)

    // Frame
    p.strokeWidth = 1f * dp; p.color = 0xFF2C6378.toInt()
    c.drawRect(left, top, right, bottom, p)

    // Y labels
    val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 8.5f * sp; color = 0xFF7FA8BA.toInt(); textAlign = Paint.Align.RIGHT }
    for (k in 0..3) {
        val v = step * k
        c.drawText(axisLabel(v), left - 6 * dp, y(v) + tp.textSize / 3, tp)
    }

    // Area + line (straight segments)
    val path = Path().apply { d.minutes.forEachIndexed { i, m -> if (i == 0) moveTo(x(i), y(m)) else lineTo(x(i), y(m)) } }
    val area = Path(path).apply { lineTo(x(7), bottom); lineTo(x(0), bottom); close() }
    p.style = Paint.Style.FILL; p.color = 0x1F22B8E8   // 12% alpha
    c.drawPath(area, p)

    // Average line
    d.averageMinutes?.let {
        p.style = Paint.Style.STROKE; p.strokeWidth = 1.5f * dp; p.color = 0xFFF2B84B.toInt()
        c.drawLine(left, y(it), right, y(it), p)
    }

    p.style = Paint.Style.STROKE; p.strokeWidth = 2f * dp; p.color = 0xFF5CC8F0.toInt()
    p.strokeJoin = Paint.Join.MITER; p.strokeCap = Paint.Cap.SQUARE
    c.drawPath(path, p)

    // Points
    val ring = Paint(Paint.ANTI_ALIAS_FLAG)
    for (i in 0..6) {
        ring.style = Paint.Style.FILL; ring.color = 0xFF041A24.toInt(); c.drawCircle(x(i), y(d.minutes[i]), 3 * dp, ring)
        ring.style = Paint.Style.STROKE; ring.strokeWidth = 1.5f * dp; ring.color = 0xFF5CC8F0.toInt(); c.drawCircle(x(i), y(d.minutes[i]), 3 * dp, ring)
    }
    ring.style = Paint.Style.FILL; ring.color = 0xFF5CC8F0.toInt(); c.drawCircle(x(7), y(d.minutes[7]), 5 * dp, ring)
    ring.style = Paint.Style.STROKE; ring.strokeWidth = 1.5f * dp; ring.color = 0xFF041A24.toInt(); c.drawCircle(x(7), y(d.minutes[7]), 5 * dp, ring)

    // Day labels
    val dl = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = mono; textSize = 9 * sp; textAlign = Paint.Align.CENTER }
    d.dayLabels.forEachIndexed { i, s ->
        val today = i == 7
        dl.color = if (today) 0xFFF2FAFD.toInt() else 0xFF7FA8BA.toInt()
        dl.isFakeBoldText = today
        c.drawText(s, x(i), bottom + 16 * dp, dl)
    }
    return bmp
}

fun axisLabel(min: Int): String = when {
    min == 0 -> "0"
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h${min % 60}"
}

fun formatDuration(min: Int): String = when {
    min < 60 -> "${min}m"
    min % 60 == 0 -> "${min / 60}h"
    else -> "${min / 60}h ${min % 60}m"
}
```

Unit-test these helpers:
- `axisLabel`
- `formatDuration`
- the scale step picker
- the average: excludes today, counts zero days, and gives 303 / 7 → 43
- the chip text: `+23m vs avg`, `−12m vs avg`, `= avg`

## 8. Acceptance checklist

- [ ] With the sample data `[80, 72, 68, 55, 8, 3, 17, 66]`, the widget matches `reading-widget-reference.png`: today `1h 6m`, avg `43m`, chip `+23m vs avg`, ticks `0 / 30m / 1h / 1h30`.
- [ ] The line has no curves: straight segments with sharp joins.
- [ ] The average line is solid, horizontal and spans the full plot width. It is drawn in amber, not blue.
- [ ] The plot sits inside a visible framed box with 2 inner horizontal gridlines and 6 inner vertical gridlines.
- [ ] The average never includes today. Check this by adding reading time today: the average value must not change.
- [ ] At midnight the chart shifts one day and today resets to `0m`.
- [ ] The widget renders correctly at 3×2, 4×2 and 5×3, and in both light and dark launcher wallpapers (it has its own background).
- [ ] TalkBack reads the content description.
