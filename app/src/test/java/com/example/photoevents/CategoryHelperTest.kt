package com.example.photoevents

import com.example.photoevents.data.CategoryHelper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryHelperTest {

    @Test
    fun testExtractIconAndNameWithEmoji() {
        val (icon, name) = CategoryHelper.extractIconAndName("✈️ Du lịch")
        assertEquals("✈️", icon)
        assertEquals("Du lịch", name)
    }

    @Test
    fun testExtractIconAndNameWithoutEmoji() {
        val (icon, name) = CategoryHelper.extractIconAndName("Du lịch")
        assertEquals("✈️", icon)
        assertEquals("Du lịch", name)
    }

    @Test
    fun testExtractIconAndNameCustom() {
        val (icon, name) = CategoryHelper.extractIconAndName("Cắm trại")
        assertEquals("🏷️", icon)
        assertEquals("Cắm trại", name)
    }

    @Test
    fun testExtractIconAndNameEmpty() {
        val (icon, name) = CategoryHelper.extractIconAndName("")
        assertEquals("🌸", icon)
        assertEquals("Chung", name)
    }

    @Test
    fun testFormatStandard() {
        assertEquals("✈️ Du lịch", CategoryHelper.formatStandard("Du lịch"))
        assertEquals("💖 Kỷ niệm", CategoryHelper.formatStandard("Kỷ niệm"))
        assertEquals("🏷️ Cắm trại", CategoryHelper.formatStandard("Cắm trại"))
    }

    @Test
    fun testMatches() {
        assertTrue(CategoryHelper.matches("✈️ Du lịch", "ALL"))
        assertTrue(CategoryHelper.matches("Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("✈️ Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("Du lịch", "✈️ Du lịch"))
        assertTrue(CategoryHelper.matches("", "Chung"))
        assertFalse(CategoryHelper.matches("Du lịch", "Gia đình"))
    }
}
