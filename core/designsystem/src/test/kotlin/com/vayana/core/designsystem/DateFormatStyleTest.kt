package com.vayana.core.designsystem

import com.vayana.core.designsystem.theme.DateFormatStyle
import com.vayana.core.designsystem.theme.format
import java.time.LocalDate
import java.util.Locale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DateFormatStyleTest {
    private val date = LocalDate.of(2026, 7, 4)

    @Test
    fun `system dates respect regional ordering within the same language`() {
        assertEquals("Jul 4, 2026", DateFormatStyle.SYSTEM.format(date, Locale.US))
        assertEquals("4 Jul 2026", DateFormatStyle.SYSTEM.format(date, Locale.UK))
    }

    @Test
    fun `global date styles override regional ordering`() {
        assertEquals("4 Jul 2026", DateFormatStyle.DAY_FIRST.format(date, Locale.US))
        assertEquals("Jul 4, 2026", DateFormatStyle.MONTH_FIRST.format(date, Locale.UK))
        assertEquals("2026-07-04", DateFormatStyle.ISO.format(date, Locale.FRANCE))
    }

    @Test
    fun `locale changes do not reuse a formatter from another region`() {
        assertEquals("4 Jul 2026", DateFormatStyle.SYSTEM.format(date, Locale.UK))
        assertEquals("Jul 4, 2026", DateFormatStyle.SYSTEM.format(date, Locale.US))
        assertEquals("4 Jul 2026", DateFormatStyle.SYSTEM.format(date, Locale.UK))
    }
}
