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

    @Test
    fun testIsPreset() {
        // Mọi danh mục đều là tuỳ chỉnh do người dùng tạo, không ép preset mặc định
        assertFalse(CategoryHelper.isPreset("💖 Kỷ niệm"))
        assertFalse(CategoryHelper.isPreset("Du lịch"))
        assertFalse(CategoryHelper.isPreset("Cắm trại"))
    }

    @Test
    fun testCompositeEmojiHandling() {
        val (icon, name) = CategoryHelper.extractIconAndName("👨‍👩‍👧 Gia đình")
        assertEquals("👨‍👩‍👧", icon)
        assertEquals("Gia đình", name)
        assertTrue(CategoryHelper.matches("👨‍👩‍👧 Gia đình", "gia đình"))
        assertTrue(CategoryHelper.matches("gia đình", "👨‍👩‍👧 GIA ĐÌNH"))
    }

    @Test
    fun testCategoryMapping() {
        assertEquals("💼 Công việc", CategoryHelper.formatStandard("công việc"))
        assertEquals("☕ Hẹn hò", CategoryHelper.formatStandard("hẹn hò"))
        assertEquals("🌸 Chung", CategoryHelper.formatStandard("chung"))
    }

    @Test
    fun testDeduplicateCategories() {
        val listWithDuplicates = listOf("🏷️ DaNgoai", "🏕️ DaNgoai", "✈️ Du lịch", "🌲 DaNgoai")
        val deduplicated = CategoryHelper.deduplicateCategories(listWithDuplicates)
        assertEquals(2, deduplicated.size)
        assertEquals("🏷️ DaNgoai", deduplicated[0])
        assertEquals("✈️ Du lịch", deduplicated[1])
    }

    @Test
    fun testChangeIconOnlyMatching() {
        val oldCat = "🏷️ DaNgoai"
        val newCat = "🏕️ DaNgoai"
        assertTrue(CategoryHelper.matches(oldCat, newCat))
        assertTrue(CategoryHelper.matches(newCat, oldCat))
        assertTrue(CategoryHelper.matches(newCat, "DaNgoai"))
        val (oldIcon, oldName) = CategoryHelper.extractIconAndName(oldCat)
        val (newIcon, newName) = CategoryHelper.extractIconAndName(newCat)
        assertEquals("🏷️", oldIcon)
        assertEquals("🏕️", newIcon)
        assertEquals(oldName, newName)
    }

    @Test
    fun testExtractIconWithoutSpace() {
        val (icon, name) = CategoryHelper.extractIconAndName("🏕️DaNgoai")
        assertEquals("🏕️", icon)
        assertEquals("DaNgoai", name)
    }

    @Test
    fun testExtractIconTrailing() {
        val (icon, name) = CategoryHelper.extractIconAndName("DaNgoai 🏕️")
        assertEquals("🏕️", icon)
        assertEquals("DaNgoai", name)

        val (icon2, name2) = CategoryHelper.extractIconAndName("DaNgoai🏕️")
        assertEquals("🏕️", icon2)
        assertEquals("DaNgoai", name2)
    }

    @Test
    fun testFormatStandardWithVariousEmojiFormats() {
        assertEquals("🏕️ DaNgoai", CategoryHelper.formatStandard("🏕️DaNgoai"))
        assertEquals("🏕️ DaNgoai", CategoryHelper.formatStandard("DaNgoai 🏕️"))
        assertEquals("🏕️ DaNgoai", CategoryHelper.formatStandard("DaNgoai🏕️"))
    }
}
