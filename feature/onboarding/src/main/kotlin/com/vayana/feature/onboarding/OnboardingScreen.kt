package com.vayana.feature.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.CloudSync
import androidx.compose.material.icons.outlined.Contrast
import androidx.compose.material.icons.outlined.FileOpen
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.hilt.navigation.compose.hiltViewModel
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.vayanaContentTransform
import com.vayana.core.designsystem.tokens.Elevations
import com.vayana.core.designsystem.tokens.Paddings
import com.vayana.core.designsystem.tokens.Radii
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R

@Composable
fun OnboardingRoute(
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    OnboardingScreen(
        modifier = modifier,
        onDisplayProfileSelected = viewModel::chooseDisplayProfile,
        onComplete = viewModel::complete,
    )
}

@Composable
private fun OnboardingScreen(
    onDisplayProfileSelected: (DisplayProfile) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pageIndex by remember { mutableIntStateOf(0) }
    val page = OnboardingPage.entries[pageIndex]
    val isLastPage = pageIndex == OnboardingPage.entries.lastIndex

    Surface(color = MaterialTheme.colorScheme.background, modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(Paddings.screenHorizontal),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    targetState = page,
                    transitionSpec = vayanaContentTransform(),
                    label = "OnboardingPage",
                ) { targetPage ->
                    OnboardingPageContent(
                        page = targetPage,
                        onDisplayProfileSelected = onDisplayProfileSelected,
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.xl),
                verticalArrangement = Arrangement.spacedBy(Spacing.md),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                PageDots(pageIndex = pageIndex)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FilledTonalButton(
                        onClick = onComplete,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(R.string.onboarding_skip))
                    }
                    Button(
                        onClick = {
                            if (isLastPage) {
                                onComplete()
                            } else {
                                pageIndex += 1
                            }
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(Radii.full),
                    ) {
                        Text(stringResource(if (isLastPage) R.string.onboarding_finish else R.string.onboarding_next))
                    }
                }
            }
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    onDisplayProfileSelected: (DisplayProfile) -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Spacing.lg),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Surface(
            shape = RoundedCornerShape(Radii.extraLarge),
            color = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            tonalElevation = Elevations.none,
        ) {
            Icon(
                imageVector = page.icon,
                contentDescription = null,
                modifier = Modifier
                    .padding(Spacing.xl)
                    .size(Sizes.iconLarge),
            )
        }
        Text(
            text = page.title(),
            style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
            textAlign = TextAlign.Center,
        )
        Text(
            text = page.body(),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (page == OnboardingPage.APPEARANCE) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { onDisplayProfileSelected(DisplayProfile.STANDARD) },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.onboarding_display_standard))
                }
                OutlinedButton(
                    onClick = { onDisplayProfileSelected(DisplayProfile.E_INK) },
                    shape = RoundedCornerShape(Radii.full),
                ) {
                    Text(stringResource(R.string.onboarding_display_eink))
                }
            }
        }
    }
}

@Composable
private fun PageDots(pageIndex: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        OnboardingPage.entries.forEachIndexed { index, _ ->
            Surface(
                modifier = Modifier
                    .size(width = if (index == pageIndex) Sizes.iconSmall else Spacing.sm, height = Spacing.sm),
                shape = RoundedCornerShape(Radii.full),
                color = if (index == pageIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                content = {},
            )
        }
    }
}

private enum class OnboardingPage(val icon: ImageVector) {
    WELCOME(Icons.Outlined.AutoStories),
    APPEARANCE(Icons.Outlined.Contrast),
    IMPORT(Icons.Outlined.FileOpen),
    SYNC(Icons.Outlined.CloudSync),
    DONE(Icons.Outlined.Check),
}

@Composable
private fun OnboardingPage.title(): String = when (this) {
    OnboardingPage.WELCOME -> stringResource(R.string.onboarding_welcome_title)
    OnboardingPage.APPEARANCE -> stringResource(R.string.onboarding_appearance_title)
    OnboardingPage.IMPORT -> stringResource(R.string.onboarding_import_title)
    OnboardingPage.SYNC -> stringResource(R.string.onboarding_sync_title)
    OnboardingPage.DONE -> stringResource(R.string.onboarding_done_title)
}

@Composable
private fun OnboardingPage.body(): String = when (this) {
    OnboardingPage.WELCOME -> stringResource(R.string.onboarding_welcome_body)
    OnboardingPage.APPEARANCE -> stringResource(R.string.onboarding_appearance_body)
    OnboardingPage.IMPORT -> stringResource(R.string.onboarding_import_body)
    OnboardingPage.SYNC -> stringResource(R.string.onboarding_sync_body)
    OnboardingPage.DONE -> stringResource(R.string.onboarding_done_body)
}
