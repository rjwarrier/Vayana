package com.vayana.app.navigation

import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavHostController
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.toRoute
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.designsystem.theme.LocalDynamicColor
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.LocalDisplayProfile
import com.vayana.core.designsystem.theme.LocalMotionSetting
import com.vayana.core.designsystem.theme.MotionSetting
import com.vayana.feature.library.LibraryAddAction
import kotlin.math.abs
import kotlin.math.roundToInt

internal const val LIBRARY_ADD_ACTION_KEY = "libraryAddAction"
internal const val BOOK_DETAIL_READABLE_KEY = "bookDetailReadable"

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun VayanaFloatingBar(
    navController: NavHostController,
    onBooksLongPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentBackStackEntry = navController.currentBackStackEntryAsState().value
    val currentDestination = currentBackStackEntry?.destination
    val readableStateFlow = remember(currentBackStackEntry) {
        currentBackStackEntry?.savedStateHandle?.getStateFlow(BOOK_DETAIL_READABLE_KEY, false)
    }
    val bookDetailReadable = if (readableStateFlow != null) {
        val readable by readableStateFlow.collectAsStateWithLifecycle()
        readable
    } else {
        false
    }
    val colors = MaterialTheme.colorScheme
    val displayProfile = LocalDisplayProfile.current
    val motionSetting = LocalMotionSetting.current
    val isEInk = displayProfile == DisplayProfile.E_INK
    val motionEnabled = !isEInk && motionSetting != MotionSetting.OFF
    val isWallpaperColor = LocalDynamicColor.current && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val destinations = TopLevelDestination.entries
    val selectedIndex = destinations
        .indexOfFirst { destination -> currentDestination?.isSelectedFor(destination) == true }
        .coerceAtLeast(0)
    val selectedFlags = destinations.indices.map { index -> index == selectedIndex }
    val barScale = remember { Animatable(1f) }

    LaunchedEffect(selectedIndex, motionEnabled) {
        if (motionEnabled) {
            barScale.snapTo(0.975f)
            barScale.animateTo(
                targetValue = 1f,
                animationSpec = motionScheme.defaultSpatialSpec(),
            )
        } else {
            barScale.snapTo(1f)
        }
    }

    // Animated values are kept as State and read in layout/draw lambdas to avoid recomposition.
    val effectsProgress: List<State<Float>> = selectedFlags.map { selected ->
        animateFloatAsState(
            targetValue = if (selected) 1f else 0f,
            animationSpec = motionScheme.defaultEffectsSpec(),
            label = "floatingNavEffectsProgress",
        )
    }
    val itemHeight = Sizes.floatingNavItem - (Spacing.xs * 2)
    val indicatorColor = if (isEInk) colors.primary else colors.primary.copy(alpha = 0.16f)
    val barColor = when {
        isEInk -> colors.surface
        isWallpaperColor -> colors.surfaceContainerHigh
        else -> colors.primaryContainer
    }
    val companionColor = if (isEInk) colors.primary else indicatorColor.compositeOver(barColor)
    val barBorder = if (isEInk) BorderStroke(1.dp, colors.outline) else null
    val barElevation = if (isEInk) 0.dp else Elevations.shadowLarge
    val showBookDetailBackAttachment = currentDestination?.hasRoute(BookDetailRoute::class) == true
    val showSettingsBackAttachment = currentDestination?.hasRoute(SettingsRoute::class) == true
    val showBackAttachment = showBookDetailBackAttachment || showSettingsBackAttachment
    val showAddBookAttachment = currentDestination?.hasRoute(TopLevelRoute.Library::class) == true
    val bookDetailRoute = if (showBookDetailBackAttachment) currentBackStackEntry.toRoute<BookDetailRoute>() else null
    val showReadBookAttachment = bookDetailRoute != null && bookDetailReadable
    val showRightAttachment = showAddBookAttachment || showReadBookAttachment
    val attachmentShape = MaterialShapes.Ghostish.toShape()
    var addBookMenuExpanded by remember { mutableStateOf(false) }
    LaunchedEffect(showAddBookAttachment) {
        if (!showAddBookAttachment) addBookMenuExpanded = false
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = Spacing.lg, vertical = Spacing.lg),
        contentAlignment = Alignment.Center,
    ) {
        val attachmentSize = Sizes.floatingNavItem
        val attachmentGap = 0.5.dp
        val visibleAttachmentCount =
            (if (showBackAttachment) 1 else 0) + (if (showRightAttachment) 1 else 0)
        val expandedContentWidth =
            Sizes.floatingNavSelectedItem + (Sizes.floatingNavUnselectedItem * (destinations.size - 1))
        val expandedBarWidth = expandedContentWidth + (Spacing.xs * 2)
        val expandedTotalWidth =
            expandedBarWidth + ((attachmentSize + attachmentGap) * visibleAttachmentCount)
        val showLabels = expandedTotalWidth <= maxWidth
        val selectedItemWidth = if (showLabels) {
            Sizes.floatingNavSelectedItem
        } else {
            Sizes.floatingNavUnselectedItem
        }
        val totalContentWidth = selectedItemWidth + (Sizes.floatingNavUnselectedItem * (destinations.size - 1))
        val totalBarWidth = totalContentWidth + (Spacing.xs * 2)
        val spatialProgress: List<State<Float>> = selectedFlags.map { selected ->
            animateFloatAsState(
                targetValue = if (selected && showLabels) 1f else 0f,
                animationSpec = motionScheme.defaultSpatialSpec(),
                label = "floatingNavSpatialProgress",
            )
        }
        val unselectedPx = with(density) {
            Sizes.floatingNavUnselectedItem.toPx()
        }
        val selectedPx = with(density) {
            selectedItemWidth.toPx()
        }

        // The front of the pill follows the tab while its trailing edge catches up.
        val indicatorLeadingOffset = animateFloatAsState(
            targetValue = selectedIndex * unselectedPx,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "floatingNavIndicatorLeadingOffset",
        )
        val indicatorTrailingOffset = animateFloatAsState(
            targetValue = selectedIndex * unselectedPx,
            animationSpec = motionScheme.slowSpatialSpec(),
            label = "floatingNavIndicatorTrailingOffset",
        )
        val maxStretchPx = with(density) { Spacing.xl.toPx() }

        // Match the navbar shell height while leaving a hairline separation so the
        // companion actions remain visually related without merging into the bar.
        val attachmentOffset = totalBarWidth / 2 + attachmentSize / 2 + attachmentGap
        val attachmentContentColor = if (isWallpaperColor) colors.primary else colors.onPrimaryContainer
        FloatingBarCompanionButton(
            visible = showBackAttachment,
            side = FloatingBarCompanionSide.Left,
            offset = -attachmentOffset,
            shape = attachmentShape,
            containerColor = companionColor,
            contentColor = if (isEInk) colors.onPrimary else attachmentContentColor,
            animateBlob = motionEnabled,
            useSlowReveal = motionEnabled && motionSetting == MotionSetting.FULL,
            shadowElevation = barElevation,
            icon = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(
                if (showSettingsBackAttachment) {
                    com.vayana.core.resources.R.string.settings_back_content_description
                } else {
                    com.vayana.core.resources.R.string.library_back_to_library_content_description
                },
            ),
            pressedIconOffsetX = (-2).dp,
            onClick = {
                val returnedToLibrary = if (showSettingsBackAttachment) {
                    navController.popBackStack()
                } else {
                    navController.popBackStack(
                        route = TopLevelRoute.Library,
                        inclusive = false,
                    )
                }
                if (!returnedToLibrary) navController.navigateToTopLevel(TopLevelRoute.Library)
            },
        )
        FloatingBarCompanionButton(
            visible = showRightAttachment,
            side = FloatingBarCompanionSide.Right,
            offset = attachmentOffset,
            shape = attachmentShape,
            containerColor = companionColor,
            contentColor = if (isEInk) colors.onPrimary else attachmentContentColor,
            animateBlob = motionEnabled,
            useSlowReveal = motionEnabled && motionSetting == MotionSetting.FULL,
            shadowElevation = barElevation,
            icon = if (showReadBookAttachment) Icons.Outlined.AutoStories else Icons.Outlined.Add,
            contentDescription = stringResource(
                if (showReadBookAttachment) {
                    com.vayana.core.resources.R.string.library_continue_reading
                } else {
                    com.vayana.core.resources.R.string.library_add_content_description
                },
            ),
            iconRotationDegrees = if (showAddBookAttachment && addBookMenuExpanded) 45f else 0f,
            pressedIconRotation = if (showReadBookAttachment) 0f else 45f,
            onClick = {
                if (showReadBookAttachment) {
                    navController.navigate(ReaderRoute(bookId = requireNotNull(bookDetailRoute).bookId))
                } else {
                    addBookMenuExpanded = !addBookMenuExpanded
                }
            },
        ) {
            if (showAddBookAttachment) {
                AddBookDropdownMenu(
                    expanded = addBookMenuExpanded,
                    onDismissRequest = { addBookMenuExpanded = false },
                    onAction = { action ->
                        addBookMenuExpanded = false
                        navController.requestLibraryAddAction(action)
                    },
                )
            }
        }

        Surface(
            modifier = Modifier
                .width(totalBarWidth)
                .graphicsLayer {
                    scaleY = barScale.value
                    scaleX = 1f + ((1f - barScale.value) * 0.35f)
                    transformOrigin = TransformOrigin.Center
                },
            shape = CircleShape,
            color = barColor,
            border = barBorder,
            shadowElevation = barElevation,
        ) {
            Box(
                modifier = Modifier
                    .height(Sizes.floatingNavItem)
                    .padding(Spacing.xs)
                    .drawBehind {
                        val maxOffset = (size.width - selectedPx).coerceAtLeast(0f)
                        val leading = indicatorLeadingOffset.value.coerceIn(0f, maxOffset)
                        val trailing = indicatorTrailingOffset.value.coerceIn(0f, maxOffset)
                        val stretch = (leading - trailing).coerceIn(-maxStretchPx, maxStretchPx)
                        val logicalLeft = leading - stretch.coerceAtLeast(0f)
                        val logicalRight = leading + selectedPx - stretch.coerceAtMost(0f)
                        val pillWidth = logicalRight - logicalLeft
                        val left = if (layoutDirection == LayoutDirection.Rtl) {
                            size.width - logicalRight
                        } else {
                            logicalLeft
                        }
                        val verticalInset = if (isEInk) 0f else Spacing.xs.toPx() * abs(stretch) / maxStretchPx / 2f
                        val pillHeight = size.height - verticalInset * 2f
                        val stretchFraction = (abs(stretch) / maxStretchPx).coerceIn(0f, 1f)
                        drawRoundRect(
                            color = if (isEInk) {
                                indicatorColor
                            } else {
                                indicatorColor.copy(alpha = 0.16f + (0.06f * stretchFraction))
                            },
                            topLeft = Offset(left, verticalInset),
                            size = Size(pillWidth, pillHeight),
                            cornerRadius = CornerRadius(pillHeight / 2f),
                        )
                    },
            ) {
                Row(
                    modifier = Modifier.selectableGroup(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    destinations.forEachIndexed { index, destination ->
                        val selected = selectedFlags[index]
                        val contentColor = if (isEInk) {
                            if (selected) colors.onPrimary else colors.onSurface
                        } else if (isWallpaperColor) {
                            if (selected) colors.primary else colors.onSurfaceVariant
                        } else {
                            colors.onPrimaryContainer
                        }
                        val spatial = spatialProgress[index]
                        val effects = effectsProgress[index]
                        val label = stringResource(destination.labelRes)
                        val interactionSource = remember { MutableInteractionSource() }
                        val pressed by interactionSource.collectIsPressedAsState()
                        if (destination == TopLevelDestination.LIBRARY) {
                            NavigationLongPressEffect(interactionSource, onBooksLongPress)
                        }

                        // Tactile press squish with expressive spring pop on release.
                        val itemScale = animateFloatAsState(
                            targetValue = if (pressed) 0.93f else 1f,
                            animationSpec = motionScheme.fastSpatialSpec(),
                            label = "floatingNavItemScale",
                        )

                        Box(
                            modifier = Modifier
                                .height(itemHeight)
                                .layout { measurable, constraints ->
                                    val deltaPx = selectedPx - unselectedPx
                                    val width = (unselectedPx + deltaPx * spatial.value.coerceIn(0f, 1f))
                                        .roundToInt()
                                        .coerceAtLeast(0)
                                    val placeable = measurable.measure(Constraints.fixed(width, constraints.maxHeight))
                                    layout(width, placeable.height) { placeable.placeRelative(0, 0) }
                                }
                                .clip(CircleShape)
                                .graphicsLayer {
                                    scaleX = itemScale.value
                                    scaleY = itemScale.value
                                }
                                .selectable(
                                    selected = selected,
                                    interactionSource = interactionSource,
                                    indication = null,
                                    role = Role.Tab,
                                    onClick = { navController.navigateToTopLevel(destination.route) },
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center,
                                modifier = Modifier.wrapContentSize(Alignment.Center),
                            ) {
                                Box(
                                    modifier = Modifier.size(Sizes.icon),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        imageVector = destination.unselectedIcon,
                                        contentDescription = if (selected && showLabels) null else label,
                                        tint = contentColor,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            val progress = effects.value.coerceIn(0f, 1f)
                                            alpha = 1f - progress
                                            scaleX = 1f - (0.12f * progress)
                                            scaleY = scaleX
                                        },
                                    )
                                    Icon(
                                        imageVector = destination.selectedIcon,
                                        contentDescription = null,
                                        tint = contentColor,
                                        modifier = Modifier.fillMaxSize().graphicsLayer {
                                            val progress = effects.value.coerceIn(0f, 1f)
                                            alpha = progress
                                            scaleX = 0.82f + (0.18f * progress)
                                            scaleY = scaleX
                                        },
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .layout { measurable, constraints ->
                                            val placeable = measurable.measure(constraints.copy(minWidth = 0))
                                            val animatedWidth = (placeable.width * spatial.value.coerceIn(0f, 1f)).roundToInt()
                                            layout(animatedWidth, placeable.height) {
                                                placeable.placeRelative(0, 0)
                                            }
                                        }
                                        .clipToBounds()
                                        .graphicsLayer {
                                            alpha = effects.value.coerceIn(0f, 1f)
                                        },
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(start = Spacing.sm),
                                    ) {
                                        Text(
                                            text = label,
                                            color = contentColor,
                                            style = MaterialTheme.typography.labelLarge,
                                            maxLines = 1,
                                            overflow = TextOverflow.Clip,
                                            softWrap = false,
                                            modifier = Modifier.then(
                                                if (selected && showLabels) Modifier else Modifier.clearAndSetSemantics { },
                                            ),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun NavHostController.requestLibraryAddAction(action: LibraryAddAction) {
    if (currentDestination?.hasRoute(TopLevelRoute.Library::class) != true) {
        val returnedToLibrary = popBackStack(route = TopLevelRoute.Library, inclusive = false)
        if (!returnedToLibrary) navigateToTopLevel(TopLevelRoute.Library)
    }
    currentBackStackEntry?.savedStateHandle?.set(LIBRARY_ADD_ACTION_KEY, action.name)
}

private enum class FloatingBarCompanionSide {
    Left,
    Right,
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FloatingBarCompanionButton(
    visible: Boolean,
    side: FloatingBarCompanionSide,
    offset: Dp,
    shape: Shape,
    containerColor: Color,
    contentColor: Color,
    animateBlob: Boolean,
    useSlowReveal: Boolean,
    shadowElevation: Dp,
    icon: ImageVector,
    contentDescription: String,
    iconRotationDegrees: Float = 0f,
    pressedIconRotation: Float = 0f,
    pressedIconOffsetX: Dp = 0.dp,
    onClick: () -> Unit,
    menuContent: @Composable () -> Unit = {},
) {
    val motionScheme = MaterialTheme.motionScheme
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale = animateFloatAsState(
        targetValue = if (animateBlob && pressed) 0.93f else 1f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "floatingNav${side.name}CompanionScale",
    )
    val iconRotation = animateFloatAsState(
        targetValue = when {
            iconRotationDegrees != 0f -> iconRotationDegrees
            animateBlob && pressed -> pressedIconRotation
            else -> 0f
        },
        animationSpec = if (animateBlob) motionScheme.fastSpatialSpec() else snap(),
        label = "floatingNav${side.name}CompanionIconRotation",
    )
    val pressedIconOffsetXPx = with(LocalDensity.current) {
        pressedIconOffsetX.toPx()
    }
    val iconOffsetX = animateFloatAsState(
        targetValue = if (animateBlob && pressed) pressedIconOffsetXPx else 0f,
        animationSpec = motionScheme.fastSpatialSpec(),
        label = "floatingNav${side.name}CompanionIconOffset",
    )
    val transformOrigin = when (side) {
        FloatingBarCompanionSide.Left -> TransformOrigin(1f, 0.5f)
        FloatingBarCompanionSide.Right -> TransformOrigin(0f, 0.5f)
    }
    val rotation = when (side) {
        FloatingBarCompanionSide.Left -> -90f
        FloatingBarCompanionSide.Right -> 90f
    }
    val hiddenOffset: (Int) -> Int = when (side) {
        FloatingBarCompanionSide.Left -> { fullWidth -> fullWidth }
        FloatingBarCompanionSide.Right -> { fullWidth -> -fullWidth }
    }

    val enterTransition = if (animateBlob) {
        slideInHorizontally(
            animationSpec = if (useSlowReveal) {
                motionScheme.slowSpatialSpec()
            } else {
                motionScheme.defaultSpatialSpec()
            },
            initialOffsetX = hiddenOffset,
        ) + fadeIn(
            animationSpec = if (useSlowReveal) {
                motionScheme.slowEffectsSpec()
            } else {
                motionScheme.defaultEffectsSpec()
            },
            initialAlpha = 0.55f,
        )
    } else {
        EnterTransition.None
    }
    val exitTransition = if (animateBlob) {
        slideOutHorizontally(
            animationSpec = if (useSlowReveal) {
                motionScheme.slowSpatialSpec()
            } else {
                motionScheme.defaultSpatialSpec()
            },
            targetOffsetX = hiddenOffset,
        ) + fadeOut(
            animationSpec = if (useSlowReveal) {
                motionScheme.slowEffectsSpec()
            } else {
                motionScheme.defaultEffectsSpec()
            },
        )
    } else {
        ExitTransition.None
    }

    AnimatedVisibility(
        visible = visible,
        modifier = Modifier
            .offset(x = offset)
            .size(Sizes.floatingNavItem),
        enter = enterTransition,
        exit = exitTransition,
    ) {
        val blobProgress = transition.animateFloat(
            transitionSpec = {
                if (useSlowReveal) motionScheme.slowSpatialSpec() else motionScheme.defaultSpatialSpec()
            },
            label = "floatingNav${side.name}CompanionBlobProgress",
        ) { state ->
            if (state == EnterExitState.Visible) 1f else 0f
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .drawBehind {
                    val progress = blobProgress.value.coerceIn(0f, 1f)
                    val bondProgress = (1f - abs((progress * 2f) - 1f)).coerceIn(0f, 1f)
                    if (animateBlob && bondProgress > 0f) {
                        val neckLength = 18.dp.toPx() * bondProgress
                        val neckHeight = size.height * (0.28f + (0.22f * bondProgress))
                        val neckTop = (size.height - neckHeight) / 2f
                        val neckLeft = when (side) {
                            FloatingBarCompanionSide.Left -> size.width - (neckLength * 0.45f)
                            FloatingBarCompanionSide.Right -> -(neckLength * 0.55f)
                        }
                        drawRoundRect(
                            color = containerColor.copy(alpha = 0.92f * bondProgress),
                            topLeft = Offset(neckLeft, neckTop),
                            size = Size(neckLength, neckHeight),
                            cornerRadius = CornerRadius(neckHeight / 2f),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val progress = blobProgress.value.coerceIn(0f, 1f)
                        scaleX = scale.value * (0.18f + (0.82f * progress))
                        scaleY = scale.value * (0.46f + (0.54f * progress))
                        this.transformOrigin = transformOrigin
                    },
                contentAlignment = Alignment.Center,
            ) {
                Surface(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationZ = rotation },
                    shape = shape,
                    color = containerColor,
                    shadowElevation = shadowElevation,
                ) {}
                Box(
                    modifier = Modifier
                        .size(Sizes.floatingNavUnselectedItem)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null,
                            role = Role.Button,
                            onClick = onClick,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = contentDescription,
                        tint = contentColor,
                        modifier = Modifier
                            .size(Sizes.icon)
                            .graphicsLayer {
                                rotationZ = iconRotation.value
                                translationX = iconOffsetX.value
                            },
                    )
                }
            }
            menuContent()
        }
    }
}

@Composable
private fun AddBookDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    onAction: (LibraryAddAction) -> Unit,
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = Modifier.widthIn(min = Sizes.menuMinWidth),
        shape = RoundedCornerShape(Radii.largeIncreased),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = Elevations.shadowLarge,
        shadowElevation = Elevations.shadowMedium,
    ) {
        DropdownMenuItem(
            text = { Text(stringResource(com.vayana.core.resources.R.string.library_import_files)) },
            leadingIcon = { AddBookMenuIcon(Icons.Outlined.AutoStories) },
            modifier = Modifier.heightIn(min = Sizes.menuItemLargeHeight),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            onClick = { onAction(LibraryAddAction.IMPORT_FILES) },
        )
        DropdownMenuItem(
            text = { Text(stringResource(com.vayana.core.resources.R.string.library_import_folder)) },
            leadingIcon = { AddBookMenuIcon(Icons.Outlined.CreateNewFolder) },
            modifier = Modifier.heightIn(min = Sizes.menuItemLargeHeight),
            contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.sm),
            onClick = { onAction(LibraryAddAction.IMPORT_FOLDER) },
        )
    }
}

@Composable
private fun AddBookMenuIcon(icon: ImageVector) {
    Surface(
        modifier = Modifier.size(Sizes.touchTarget),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(Sizes.icon))
        }
    }
}
