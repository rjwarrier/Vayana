package com.vayana.feature.statistics

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import com.vayana.core.common.shareText
import com.vayana.core.designsystem.sharecard.ShareCardDialog
import com.vayana.core.designsystem.sharecard.ShareCardOptionChip
import com.vayana.core.designsystem.tokens.Sizes
import com.vayana.core.designsystem.tokens.Spacing
import com.vayana.core.resources.R
import java.time.DayOfWeek
import java.time.Month
import java.time.format.TextStyle
import java.util.Locale

/** One screen of the review; each is a card that can be shared as an image. */
internal enum class YearReviewSlide(val labelRes: Int) {
    OVERVIEW(R.string.statistics_year_review_slide_overview),
    HABITS(R.string.statistics_year_review_slide_habits),
    TOP_BOOK(R.string.statistics_year_review_slide_top_book),
    AUTHOR(R.string.statistics_year_review_slide_author),
    MARKS(R.string.statistics_year_review_slide_marks),
}

/** The slides there is something to say on: a reader with no highlights gets no "Notes" slide. */
internal fun YearReview.slides(): List<YearReviewSlide> = buildList {
    add(YearReviewSlide.OVERVIEW)
    if (longestStreakDays > 0 || readerKind != null) add(YearReviewSlide.HABITS)
    if (topBook != null) add(YearReviewSlide.TOP_BOOK)
    if (topAuthor != null) add(YearReviewSlide.AUTHOR)
    if (highlights + notes + savedWords > 0) add(YearReviewSlide.MARKS)
}

@Composable
internal fun YearReviewDialog(review: YearReview, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val slides = remember(review) { review.slides() }
    var slide by remember { mutableStateOf(YearReviewSlide.OVERVIEW) }
    val shareTitle = stringResource(R.string.statistics_year_review_share_title)
    val shareText = yearReviewShareText(context, review)
    ShareCardDialog(
        onDismiss = onDismiss,
        onShareText = {
            context.shareText(shareText, shareTitle)
            onDismiss()
        },
        chooserTitle = stringResource(R.string.share_card_image_chooser_title),
        shareTextLabel = stringResource(R.string.share_card_share_text),
        shareImageLabel = stringResource(R.string.share_card_share_image),
        shareImageFileName = "vayana-year-${review.year}-${slide.name.lowercase(Locale.ROOT)}",
        title = shareTitle,
        options = {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.xs),
            ) {
                slides.forEach { option ->
                    ShareCardOptionChip(selected = option == slide, label = stringResource(option.labelRes), onClick = { slide = option })
                }
            }
        },
    ) {
        YearReviewCard(review, slide)
    }
}

/** A square card in the app's own colours, so it follows the reader's theme and still reads well as a picture. */
@Composable
internal fun YearReviewCard(review: YearReview, slide: YearReviewSlide, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val locale = LocalLocale.current.platformLocale
    Box(
        modifier = modifier
            .width(Sizes.shareCardWidth)
            .aspectRatio(1f)
            .background(Brush.linearGradient(listOf(colors.primaryContainer, colors.tertiaryContainer)))
            .padding(Spacing.xl),
    ) {
        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Label(stringResource(R.string.statistics_year_review_label, review.year))
                when (slide) {
                    YearReviewSlide.OVERVIEW -> {
                        Hero(review.finishedBooks.toString())
                        Line(stringResource(R.string.statistics_year_review_books_finished, review.year))
                        Spacer(Modifier.height(Spacing.md))
                        Line(stringResource(R.string.statistics_year_review_time_read, durationText(review.readingSeconds, LocalContext.current)))
                        Line(stringResource(R.string.statistics_year_review_days, review.activeDays))
                        review.longestFinishedTitle?.let { title ->
                            Line(stringResource(R.string.statistics_year_review_longest, title, review.longestFinishedPages ?: 0))
                        }
                    }
                    YearReviewSlide.HABITS -> {
                        review.readerKind?.let { Hero(stringResource(it.labelRes())) }
                        Spacer(Modifier.height(Spacing.md))
                        if (review.longestStreakDays > 0) Line(stringResource(R.string.statistics_year_review_streak, review.longestStreakDays))
                        review.busiestWeekday?.let { Line(stringResource(R.string.statistics_year_review_busiest_day, it.display(locale))) }
                        review.busiestMonth?.let { Line(stringResource(R.string.statistics_year_review_busiest_month, it.display(locale))) }
                    }
                    YearReviewSlide.TOP_BOOK -> review.topBook?.let { book ->
                        Line(stringResource(R.string.statistics_year_review_top_book))
                        Hero(book.title, small = true)
                        book.author?.let { Line(it) }
                        Spacer(Modifier.height(Spacing.md))
                        Line(stringResource(R.string.statistics_year_review_time_read, durationText(book.seconds, LocalContext.current)))
                    }
                    YearReviewSlide.AUTHOR -> review.topAuthor?.let { author ->
                        Line(stringResource(R.string.statistics_year_review_top_author))
                        Hero(author, small = true)
                        Spacer(Modifier.height(Spacing.md))
                        Line(stringResource(R.string.statistics_year_review_author_books, review.topAuthorBooks))
                    }
                    YearReviewSlide.MARKS -> {
                        Line(stringResource(R.string.statistics_year_review_marks))
                        Spacer(Modifier.height(Spacing.md))
                        if (review.highlights > 0) Line(stringResource(R.string.statistics_year_review_highlights, review.highlights))
                        if (review.notes > 0) Line(stringResource(R.string.statistics_year_review_notes, review.notes))
                        if (review.savedWords > 0) Line(stringResource(R.string.statistics_year_review_words, review.savedWords))
                    }
                }
            }
            Text(
                stringResource(R.string.statistics_year_review_footer),
                style = MaterialTheme.typography.labelMedium,
                color = colors.onPrimaryContainer.copy(alpha = FooterAlpha),
            )
        }
    }
}

@Composable
private fun Label(text: String) = Text(
    text,
    style = MaterialTheme.typography.labelLarge,
    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = FooterAlpha),
)

@Composable
private fun Hero(text: String, small: Boolean = false) = Text(
    text,
    style = if (small) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.displayLarge,
    color = MaterialTheme.colorScheme.onPrimaryContainer,
    maxLines = if (small) 3 else 2,
    overflow = TextOverflow.Ellipsis,
)

@Composable
private fun Line(text: String) = Text(
    text,
    style = MaterialTheme.typography.titleMedium,
    color = MaterialTheme.colorScheme.onPrimaryContainer,
    maxLines = 2,
    overflow = TextOverflow.Ellipsis,
)

private fun ReaderKind.labelRes(): Int = when (this) {
    ReaderKind.EARLY_BIRD -> R.string.statistics_year_review_kind_early_bird
    ReaderKind.AFTERNOON -> R.string.statistics_year_review_kind_afternoon
    ReaderKind.EVENING -> R.string.statistics_year_review_kind_evening
    ReaderKind.NIGHT_OWL -> R.string.statistics_year_review_kind_night_owl
}

private fun DayOfWeek.display(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)

private fun Month.display(locale: Locale): String = getDisplayName(TextStyle.FULL, locale)

/** "42 h 15 min" in the reader's language, or "35 min" under an hour. */
internal fun durationText(seconds: Long, context: Context): String {
    val minutes = (seconds / 60L).toInt()
    return if (minutes >= 60) {
        context.getString(R.string.statistics_year_review_duration_hours, minutes / 60, minutes % 60)
    } else {
        context.getString(R.string.statistics_year_review_duration_minutes, minutes)
    }
}

private fun yearReviewShareText(context: Context, review: YearReview): String = context.getString(
    R.string.statistics_year_review_share_text,
    review.year,
    review.finishedBooks,
    durationText(review.readingSeconds, context),
    review.longestStreakDays,
)

private const val FooterAlpha = 0.7f
