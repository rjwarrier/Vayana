package com.vayana.core.datastore.settings

import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ReadingToolsRepositoryTest {
    @Test fun rulesRoundTripIncludingUnicodeAndCombinedCriteria() {
        val shelf = SmartShelf(id = "one", name = "Next 📚", query = "fiction", author = "Le Guin",
            tag = "Favorites", status = SmartShelfStatus.READING, dormantDays = 7, withNotes = true)
        assertEquals(listOf(shelf), decodeSmartShelves(encodeSmartShelves(listOf(shelf))))
    }

    @Test fun presetsRoundTripEffectiveSettingsIncludingCustomFont() {
        val preset = ReadingPreset.capture("Evening", SettingsSnapshot().copy(
            readerFontSizePercent = 140, readerLineHeight = 1.8f, readerCustomFontId = "imported"))
        assertEquals(listOf(preset), decodeReadingPresets(encodeReadingPresets(listOf(preset))))
    }

    @Test fun invalidDataDoesNotDiscardValidNeighborsOrCrash() {
        val valid = encodeSmartShelves(listOf(SmartShelf(id = "one", name = "Shelf"))).removeSuffix("]")
        assertEquals(1, decodeSmartShelves("$valid,{},null,{\"id\":\"bad\",\"name\":\"X\",\"status\":\"INVALID\"}]").size)
        assertTrue(decodeSmartShelves("broken").isEmpty())
        assertTrue(decodeReadingPresets("{}").isEmpty())
        assertEquals(mapOf("stable-id" to LocalDate.of(2026, 12, 31)),
            decodeReadingPlans("{\"stable-id\":\"2026-12-31\",\"bad\":\"invalid\"}"))
    }

    @Test fun toolsAreRegisteredForPortableBackupAndSync() {
        val keys = SettingsRegistry.persisted.map { it.key }.toSet()
        assertTrue(SettingsRegistry.SmartShelves.key in keys)
        assertTrue(SettingsRegistry.ReadingPresets.key in keys)
        assertTrue(SettingsRegistry.ReadingPlans.key in keys)
    }
}
