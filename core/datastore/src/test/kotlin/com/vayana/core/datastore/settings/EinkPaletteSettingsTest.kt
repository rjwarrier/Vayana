package com.vayana.core.datastore.settings

import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.vayana.core.designsystem.theme.DisplayProfile
import com.vayana.core.designsystem.theme.EinkPalette
import kotlin.test.Test
import kotlin.test.assertEquals

class EinkPaletteSettingsTest {
    @Test
    fun `existing eink settings remain monochrome without migration`() {
        val preferences = mutablePreferencesOf(
            stringPreferencesKey(SettingsRegistry.DisplayProfile.key) to DisplayProfile.E_INK.name,
        )
        assertEquals(DisplayProfile.E_INK, preferences.toSnapshot().displayProfile)
        assertEquals(EinkPalette.MONOCHROME, preferences.toSnapshot().einkPalette)
    }

    @Test
    fun `color preference survives leaving and returning to eink`() {
        val profileKey = stringPreferencesKey(SettingsRegistry.DisplayProfile.key)
        val preferences = mutablePreferencesOf(
            profileKey to DisplayProfile.E_INK.name,
            stringPreferencesKey(SettingsRegistry.EinkPalette.key) to EinkPalette.COLOR.name,
        )
        assertEquals(EinkPalette.COLOR, preferences.toSnapshot().einkPalette)
        preferences[profileKey] = DisplayProfile.STANDARD.name
        assertEquals(EinkPalette.COLOR, preferences.toSnapshot().einkPalette)
        preferences[profileKey] = DisplayProfile.E_INK.name
        assertEquals(EinkPalette.COLOR, preferences.toSnapshot().einkPalette)
    }

    @Test
    fun `unknown palette safely falls back to monochrome`() {
        val preferences = mutablePreferencesOf(stringPreferencesKey(SettingsRegistry.EinkPalette.key) to "UNKNOWN")
        assertEquals(EinkPalette.MONOCHROME, preferences.toSnapshot().einkPalette)
    }
}
