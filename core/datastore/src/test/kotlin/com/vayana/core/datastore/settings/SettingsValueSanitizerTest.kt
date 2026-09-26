package com.vayana.core.datastore.settings

import com.vayana.core.resources.R
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsValueSanitizerTest {
    private val intSetting = IntSetting(
        key = "test.int",
        defaultValue = 50,
        titleRes = R.string.app_name,
        subtitleRes = null,
        group = SettingsGroup.READER_PAGE,
        range = 0..100,
        step = 5,
    )
    private val floatSetting = FloatSetting(
        key = "test.float",
        defaultValue = 1f,
        titleRes = R.string.app_name,
        subtitleRes = null,
        group = SettingsGroup.READER_TEXT,
        range = 0.5f..2f,
        step = 0.1f,
    )

    @Test
    fun numericSettingsAreClampedBeforeReachingControls() {
        assertEquals(0, intSetting.sanitizeStoredValue(-1))
        assertEquals(100, intSetting.sanitizeStoredValue(101))
        assertEquals(0.5f, floatSetting.sanitizeStoredValue(-1f))
        assertEquals(2f, floatSetting.sanitizeStoredValue(3f))
    }

    @Test
    fun nonFiniteFloatSettingsFallBackToTheDefault() {
        assertEquals(1f, floatSetting.sanitizeStoredValue(Float.NaN))
        assertEquals(1f, floatSetting.sanitizeStoredValue(Float.POSITIVE_INFINITY))
        assertEquals(1f, floatSetting.sanitizeStoredValue(Float.NEGATIVE_INFINITY))
    }
}
