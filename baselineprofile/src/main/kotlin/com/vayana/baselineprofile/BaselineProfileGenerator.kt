package com.vayana.baselineprofile

import android.content.Intent
import android.net.Uri
import androidx.benchmark.macro.MacrobenchmarkScope
import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiObject2
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Records the code Vayana runs on the paths people take most: launch, the library, opening and paging a book, notes,
 * statistics. Run with `./gradlew :app:generateBaselineProfile` on an emulator (a fresh app: it skips onboarding and
 * imports a bundled sample book); the result lands in app/src/release/generated/baselineProfiles.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(packageName = TargetPackage, includeInStartupProfile = true) {
        pressHome()
        startActivityAndWait()
        device.findObject(By.text("Skip"))?.let { skip ->
            skip.click()
            device.wait(Until.hasObject(By.text("Books")), UiTimeoutMillis)
        }
        importSampleBook()
        readSampleBook()
        visitTab("Notes")
        visitTab("Stats")
        device.findObject(By.scrollable(true))?.fling(Direction.DOWN)
        visitTab("Books")
    }

    /** Hands the bundled EPUB to Vayana as "open with" from another app would; the library imports and shows it. */
    private fun MacrobenchmarkScope.importSampleBook() {
        if (findSampleBook() != null) return
        val context = InstrumentationRegistry.getInstrumentation().context
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse("content://com.vayana.baselineprofile.books/profile-sample.epub"), "application/epub+zip")
            .setClassName(TargetPackage, "$TargetPackage.MainActivity")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
        device.wait(Until.hasObject(By.text("Done")), ImportTimeoutMillis)
        device.findObject(By.text("Done"))?.click()
        waitUntil(UiTimeoutMillis) { findSampleBook() != null }
    }

    /** Opens the book (straight into the reader, or through its details), turns pages, then comes back. */
    private fun MacrobenchmarkScope.readSampleBook() {
        val book = findSampleBook() ?: return
        book.click()
        device.waitForIdle()
        // Book details' read button carries its label as a description; a button may show it as text instead.
        listOf("Start reading", "Continue reading", "Read")
            .firstNotNullOfOrNull { device.findObject(By.desc(it)) ?: device.findObject(By.text(it)) }
            ?.click()
        device.wait(Until.hasObject(By.clazz("android.webkit.WebView")), UiTimeoutMillis)
        Thread.sleep(ReaderSettleMillis)
        val x = (device.displayWidth * PageTurnTapFraction).toInt()
        val y = device.displayHeight / 2
        repeat(PageTurns) {
            device.click(x, y)
            Thread.sleep(PageTurnPauseMillis)
        }
        device.pressBack()
        device.waitForIdle()
        if (!device.hasObject(By.text("Books"))) device.pressBack()
        device.wait(Until.hasObject(By.text("Books")), UiTimeoutMillis)
    }

    /** The book as the library shows it: its title as text, or as the description of its cover. */
    private fun MacrobenchmarkScope.findSampleBook(): UiObject2? =
        device.findObject(By.textContains(SampleTitle)) ?: device.findObject(By.descContains(SampleTitle))

    private fun waitUntil(timeoutMillis: Long, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMillis
        while (!condition() && System.currentTimeMillis() < deadline) Thread.sleep(PollMillis)
    }

    /** A tab by its label: the selected one shows it as text, the others as their icon's description. */
    private fun MacrobenchmarkScope.visitTab(label: String) {
        (device.findObject(By.desc(label)) ?: device.findObject(By.text(label)))?.click() ?: return
        device.waitForIdle()
        Thread.sleep(TabSettleMillis)
    }

    private companion object {
        const val TargetPackage = "com.vayana.app"
        const val SampleTitle = "Profile Sample"
        const val UiTimeoutMillis = 10_000L
        const val ImportTimeoutMillis = 30_000L
        const val ReaderSettleMillis = 3_000L
        const val PageTurns = 8
        const val PageTurnPauseMillis = 600L
        const val PageTurnTapFraction = 0.9
        const val TabSettleMillis = 1_500L
        const val PollMillis = 250L
    }
}
