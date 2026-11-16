# Vayana floating navigation bar implementation

Vayana uses a custom Jetpack Compose capsule with an elastic selection pill, expanding tab labels, and optional companion buttons. It is built from `Surface`, `Row`, custom layout and drawing, and spring animations.

This document describes the implementation at commit `536759aa` and provides a framework-independent specification for reproducing it.

## Source map

| Responsibility | Source |
|---|---|
| Capsule, tab animations, indicator, companion buttons | [VayanaFloatingBar.kt](../app/src/main/kotlin/com/vayana/app/navigation/VayanaFloatingBar.kt) |
| Spring definitions and motion preferences | [MotionSchemes.kt](../core/designsystem/src/main/kotlin/com/vayana/core/designsystem/theme/MotionSchemes.kt) |
| Overlay placement and adaptive layout | [VayanaAppRoot.kt](../app/src/main/kotlin/com/vayana/app/VayanaAppRoot.kt) |
| Destinations and owning-tab selection | [TopLevelDestination.kt](../app/src/main/kotlin/com/vayana/app/navigation/TopLevelDestination.kt) |
| Navigation state restoration | [VayanaBottomBar.kt](../app/src/main/kotlin/com/vayana/app/navigation/VayanaBottomBar.kt) |
| Navigation presentation selection | [NavigationPresentation.kt](../app/src/main/kotlin/com/vayana/app/navigation/NavigationPresentation.kt) |

## 1. Visual design and dimensions

The bar floats over the screen, centered at the bottom. It contains Library, Notes, and Statistics. Only the selected tab displays its label when space permits.

| Element | Value |
|---|---:|
| Capsule height | 72 dp |
| Internal padding | 4 dp |
| Tab height | 64 dp |
| Unselected tab width | 64 dp |
| Selected tab width with label | 132 dp |
| Three-tab capsule width with label | 268 dp |
| Three-tab capsule width without labels | 200 dp |
| Icon size | 24 dp |
| Icon-to-label spacing | 8 dp |
| Outer horizontal and vertical padding | 16 dp |
| Shadow elevation | 4 dp |
| Companion button size | 72 × 72 dp |
| Companion-to-capsule gap | 0.5 dp |

Use fully rounded ends (`CircleShape`) and `MaterialTheme.typography.labelLarge`. The capsule is opaque and tonal, with no blur or glass effect.

### Theme colors

- Normal theme: capsule uses `primaryContainer`; contents use `onPrimaryContainer`.
- Wallpaper-derived colors on supported Android versions: capsule uses `surfaceContainerHigh`; selected content uses `primary`, and inactive content uses `onSurfaceVariant`.
- Selection indicator: primary color at 16% opacity at rest on regular displays.
- Companion background: indicator color composited over the capsule background.
- Monochrome E-Ink: capsule uses `surface`; indicator and companion use solid `primary`; selected content uses `onPrimary`.
- E-Ink adds a 1 dp outline and removes the shadow. Color E-Ink flattens the translucent indicator onto the capsule color.

## 2. Responsive layout

Compute whether the expanded capsule and visible companion buttons fit inside the available width after horizontal padding:

```text
expandedContentWidth = 132 + 64 × (tabCount − 1)
expandedBarWidth = expandedContentWidth + 2 × 4
expandedTotalWidth = expandedBarWidth + companionCount × (72 + 0.5)
showLabels = expandedTotalWidth <= availableWidth
```

If they do not fit, all tabs use 64 dp widths and labels collapse. The implementation has this label fallback; it does not add a separate overflow mode for exceptionally narrow windows.

The capsule remains centered. Each companion button is positioned relative to its center:

```text
companionOffset = barWidth / 2 + 72 / 2 + 0.5
leftButtonX = −companionOffset
rightButtonX = companionOffset
```

## 3. Expanding selected tabs

Each tab has selection geometry progress `p`, animated between 0 and 1:

```text
tabWidth = 64 + (132 − 64) × p
```

The old selection contracts while the new selection expands. Vayana implements the width in a custom `Modifier.layout`, reading the animated state during measurement.

The label is measured at its natural width, but its allocated width is multiplied by geometry progress. It is clipped during expansion and remains on one line. A separate effects progress animates opacity.

```text
labelAllocatedWidth = measuredLabelWidth × geometryProgress
labelOpacity = effectsProgress
```

This separates physical movement from visual effects and avoids an abrupt label appearance.

## 4. Elastic moving selection indicator

Draw a single rounded rectangle behind the tab row. Do not draw a separate selected background inside each tab.

The indicator's resting horizontal position is:

```text
targetX = selectedIndex × 64 dp
```

Animate two positions toward this target:

- `normalX`: normal spatial spring.
- `slowX`: slower spatial spring.

Their difference temporarily stretches the pill:

```text
stretch = clamp(normalX − slowX, −24 dp, +24 dp)
left = normalX − max(stretch, 0)
right = normalX + selectedWidth − min(stretch, 0)
pillWidth = right − left
```

Clamp the positions to the valid content range before drawing. When the two animations settle, stretch becomes zero and the pill returns to its resting size.

Additional deformation:

```text
stretchFraction = clamp(abs(stretch) / 24 dp, 0, 1)
verticalInset = 2 dp × stretchFraction
pillHeight = contentHeight − 2 × verticalInset
cornerRadius = pillHeight / 2
indicatorOpacity = 0.16 + 0.06 × stretchFraction
```

The moving pill becomes slightly thinner and darker as it stretches. On E-Ink, vertical deformation is disabled and colors use the solid/composited variants described above.

For right-to-left layout, mirror the logical drawing coordinates:

```text
physicalLeft = contentWidth − logicalRight
```

This is custom rounded-rectangle geometry, not a fluid simulation.

## 5. Icon transition

Stack outlined and filled icons in the same 24 dp box. Animate their opacity and scale using effects progress `p`:

```text
outlinedOpacity = 1 − p
outlinedScale = 1 − 0.12 × p

filledOpacity = p
filledScale = 0.82 + 0.18 × p
```

The outlined icon fades and shrinks while the filled icon fades in and grows. The paths do not morph. Content colors change according to selection and theme.

## 6. Press feedback and capsule squash

Tabs use an interaction source to observe press state. Their contents scale to 93% while pressed, then spring back to 100%. Default ripple indication is disabled.

On a selected-index change, the capsule performs a small squash:

```text
scaleY snaps to 0.975, then springs to 1
scaleX = 1 + (1 − scaleY) × 0.35
transformOrigin = center
```

The capsule briefly becomes shorter and slightly wider. This effect also runs on initial composition through the selection effect.

## 7. Motion specifications

Vayana routes animation specifications through `MaterialTheme.motionScheme`.

| Animation | Damping ratio | Stiffness |
|---|---:|---|
| Default spatial movement | 0.9 | `Spring.StiffnessMedium` |
| Fast spatial / press feedback | 0.9 | `Spring.StiffnessMedium` |
| Slow spatial / trailing indicator | 0.95 | `Spring.StiffnessMediumLow` |
| Default effects / opacity | `Spring.DampingRatioNoBouncy` | `Spring.StiffnessMedium` |

Other effects specifications delegate to Material 3's expressive motion scheme. These spring animations do not have a fixed millisecond duration.

Motion preferences:

- Full: custom expressive spring scheme and slower companion reveal.
- Reduced: Material 3 standard motion scheme, with ordinary companion reveal rather than the full-motion slow reveal.
- Off or E-Ink: specifications snap immediately; companion entry/exit transitions and the capsule squash are disabled.

The tab press target remains 0.93 in the code even with snap motion: it changes immediately rather than animating.

## 8. Companion buttons

| Screen | Left companion | Right companion |
|---|---|---|
| Library | None | Add menu |
| Book details | Back | Read, when readable |
| Settings | Back | None |
| Offline books | None | Add offline book |

### Shape

Use Material 3 Expressive `MaterialShapes.Ghostish.toShape()` for the background, rotated −90° on the left and +90° on the right. Keep the icon outside the rotated surface so it stays upright.

### Reveal and exit

Combine horizontal slide, fade, and nonuniform scale. The left companion enters from its right; the right companion enters from its left, so both appear to emerge from the capsule.

```text
entryOpacity: 0.55 → 1
horizontalScale = pressScale × (0.18 + 0.82 × progress)
verticalScale = pressScale × (0.46 + 0.54 × progress)
```

Pivot on the edge closest to the capsule:

- Left companion: right-center pivot `(1, 0.5)`.
- Right companion: left-center pivot `(0, 0.5)`.

Use the corresponding reversed slide/fade on exit.

### Temporary connecting neck

Draw a rounded connection near the capsule-facing edge. Its strength peaks halfway through the transition:

```text
bond = clamp(1 − abs(2 × progress − 1), 0, 1)
neckLength = 18 dp × bond
neckHeight = buttonHeight × (0.28 + 0.22 × bond)
neckOpacity = 0.92 × bond
neckCornerRadius = neckHeight / 2
```

Position the neck across the inner edge:

```text
leftCompanionNeckX = buttonWidth − neckLength × 0.45
rightCompanionNeckX = −neckLength × 0.55
```

The capsule is drawn after the companions, helping conceal the inward part of the reveal. The temporary neck creates the impression that the button buds off the capsule and separates.

### Interaction

- Companion press scale: 93% when motion is enabled.
- Add icon rotates 45° while its menu is open; add-type buttons also have a 45° press rotation.
- Back icon shifts 2 dp left while pressed.
- Read icon has no press rotation.
- The Add menu uses a Material dropdown with rounded corners, elevated tonal background, and icon-leading rows.

## 9. Screen integration

Render the floating bar as an overlay, not as the full-width `Scaffold.bottomBar`:

```kotlin
Box(Modifier.fillMaxSize()) {
    ScreenContent()

    FloatingNavigationBar(
        modifier = Modifier.align(Alignment.BottomCenter)
    )
}
```

Within the bar, apply system navigation padding followed by the 16 dp outer margins:

```kotlin
Modifier
    .fillMaxWidth()
    .navigationBarsPadding()
    .padding(horizontal = 16.dp, vertical = 16.dp)
```

Vayana provides screens with a floating-navigation inset of 104 dp (`72 + 32`). In a new project, account for system insets as appropriate and give scrolling content enough bottom padding that its last items can scroll above the overlay.

The reader hides navigation. Medium-width and larger layouts use a navigation rail. Phone layouts can choose conventional bottom navigation or the floating capsule.

## 10. Navigation and accessibility

Derive selected state from the current route. Map secondary screens to their owning top-level tab, so opening a detail screen does not clear selection.

Preserve each tab's navigation state:

```kotlin
navController.navigate(route) {
    popUpTo(navController.graph.findStartDestination().id) {
        saveState = true
    }
    launchSingleTop = true
    restoreState = true
}
```

Use `selectableGroup()` for the tab row and `selectable(role = Role.Tab, selected = selected)` for each tab. Supply an accessible label when the visible label is absent; avoid duplicate descriptions when the label is visible. Remove hidden label semantics during collapse.

Vayana also listens to Library press interactions for a long-press action that toggles navigation mode, using the platform long-press timeout.

## 11. Reproduction structure

Separate reusable visuals from application routing:

```text
FloatingNavBar
  inputs: items, selectedIndex, onSelect, optional actions, motion mode
  owns: capsule layout, tab expansion, indicator geometry, icon transitions

CompanionAction
  inputs: visible, side, icon, description, onClick
  owns: reveal, neck drawing, press feedback

NavigationHost
  owns: routes, selected-index mapping, back-stack preservation, overlay insets
```

For Compose, use `Surface`, `BoxWithConstraints`, `Row`, `animateFloatAsState`, `Animatable`, `Modifier.layout`, `drawBehind`, `graphicsLayer`, and `AnimatedVisibility`.

Read animated values in layout, drawing, or graphics-layer callbacks where possible. This allows width changes to trigger layout and visual-only effects to update drawing without unnecessarily recomposing the whole bar.

To port to another UI framework, retain the dimensions, dual-spring indicator model, width/opacity separation, icon crossfade, and companion pivot/neck formulas. Replace Compose-specific drawing and navigation APIs with the target framework's equivalents.
