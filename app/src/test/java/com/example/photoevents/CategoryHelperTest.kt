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
        assertEquals("📂", icon)
        assertEquals("Chưa gán", name)
    }

    @Test
    fun testFormatStandard() {
        assertEquals("✈️ Du lịch", CategoryHelper.formatStandard("Du lịch"))
        assertEquals("💖 Kỷ niệm", CategoryHelper.formatStandard("Kỷ niệm"))
        assertEquals("🏷️ Cắm trại", CategoryHelper.formatStandard("Cắm trại"))
        assertEquals("📂 Chưa gán", CategoryHelper.formatStandard(""))
        assertEquals("📂 Chưa gán", CategoryHelper.formatStandard("UNCATEGORIZED"))
    }

    @Test
    fun testMatches() {
        assertTrue(CategoryHelper.matches("✈️ Du lịch", "ALL"))
        assertTrue(CategoryHelper.matches("Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("✈️ Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("Du lịch", "✈️ Du lịch"))
        assertTrue(CategoryHelper.matches("", "UNCATEGORIZED"))
        assertTrue(CategoryHelper.matches("", "Chưa gán"))
        assertTrue(CategoryHelper.matches("📂 Chưa gán", "UNCATEGORIZED"))
        assertFalse(CategoryHelper.matches("", "Chung"))
        assertFalse(CategoryHelper.matches("", "Du lịch"))
        assertFalse(CategoryHelper.matches("Du lịch", "UNCATEGORIZED"))
        assertFalse(CategoryHelper.matches("Du lịch", "Gia đình"))
    }

    @Test
    fun testIsUncategorized() {
        assertTrue(CategoryHelper.isUncategorized(""))
        assertTrue(CategoryHelper.isUncategorized("   "))
        assertTrue(CategoryHelper.isUncategorized(null))
        assertTrue(CategoryHelper.isUncategorized("UNCATEGORIZED"))
        assertTrue(CategoryHelper.isUncategorized("Chưa gán"))
        assertTrue(CategoryHelper.isUncategorized("📂 Chưa gán"))
        assertFalse(CategoryHelper.isUncategorized("Du lịch"))
        assertFalse(CategoryHelper.isUncategorized("🌸 Chung"))
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

    @Test
    fun testExtractExplicitEmojiAndCleanName() {
        // Có emoji tường minh
        val (emoji1, name1) = CategoryHelper.extractExplicitEmojiAndCleanName("🏕️ DaNgoai")
        assertEquals("🏕️", emoji1)
        assertEquals("DaNgoai", name1)

        val (emoji2, name2) = CategoryHelper.extractExplicitEmojiAndCleanName("🏕️DaNgoai")
        assertEquals("🏕️", emoji2)
        assertEquals("DaNgoai", name2)

        val (emoji3, name3) = CategoryHelper.extractExplicitEmojiAndCleanName("DaNgoai 🏕️")
        assertEquals("🏕️", emoji3)
        assertEquals("DaNgoai", name3)

        // Tên thông thường (kể cả có trong NAME_TO_ICON_MAP) KHÔNG bị tự gắn emoji
        val (emoji4, name4) = CategoryHelper.extractExplicitEmojiAndCleanName("Du lịch")
        assertEquals(null, emoji4)
        assertEquals("Du lịch", name4)

        val (emoji5, name5) = CategoryHelper.extractExplicitEmojiAndCleanName("Kỷ niệm")
        assertEquals(null, emoji5)
        assertEquals("Kỷ niệm", name5)

        val (emoji6, name6) = CategoryHelper.extractExplicitEmojiAndCleanName("")
        assertEquals(null, emoji6)
        assertEquals("", name6)
    }

    @Test
    fun testFormatStandardPreservesPickedEmoji() {
        assertEquals("🏖️ Du lịch", CategoryHelper.formatStandard("🏖️ Du lịch"))
        assertEquals("☕ Da Ngoai", CategoryHelper.formatStandard("☕ Da Ngoai"))
    }

    @Test
    fun testExtractCleanTextName() {
        assertEquals("Du lịch", CategoryHelper.extractCleanTextName("🏷️ 🏖️ Du lịch"))
        assertEquals("Du lịch", CategoryHelper.extractCleanTextName("🏷️ 🏖️Du lịch"))
        assertEquals("Du lịch", CategoryHelper.extractCleanTextName("🏖️ Du lịch"))
        assertEquals("Du lịch", CategoryHelper.extractCleanTextName("Du lịch 🏖️"))
        assertEquals("Du lịch", CategoryHelper.extractCleanTextName("Du lịch"))
        assertEquals("Chưa gán", CategoryHelper.extractCleanTextName(""))
        assertEquals("Chưa gán", CategoryHelper.extractCleanTextName("UNCATEGORIZED"))
        assertEquals("Chung", CategoryHelper.extractCleanTextName("🌸 Chung"))
    }

    @Test
    fun testMatchesWithCorruptedOrPrependedEmoji() {
        assertTrue(CategoryHelper.matches("🏷️ 🏖️ Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("🏷️ 🏖️ Du lịch", "🏖️ Du lịch"))
        assertTrue(CategoryHelper.matches("🏷️ 🏖️Du lịch", "Du lịch"))
        assertTrue(CategoryHelper.matches("Du lịch", "🏷️ 🏖️ Du lịch"))
        assertTrue(CategoryHelper.matches("🏷️ 🏖️ Du lịch", "✈️ Du lịch"))
        assertFalse(CategoryHelper.matches("🏷️ 🏖️ Du lịch", "Cắm trại"))
    }

    @Test
    fun testExtractIconAndNameWithCorruptedOrPrependedEmoji() {
        val (icon, name) = CategoryHelper.extractIconAndName("🏷️ 🏖️ Du lịch")
        assertEquals("🏖️", icon)
        assertEquals("Du lịch", name)

        val (icon2, name2) = CategoryHelper.extractIconAndName("🏷️ 🏖️Du lịch")
        assertEquals("🏖️", icon2)
        assertEquals("Du lịch", name2)
    }

    @Test
    fun testFormatStandardRepairsCorruptedEmoji() {
        assertEquals("🏖️ Du lịch", CategoryHelper.formatStandard("🏷️ 🏖️ Du lịch"))
        assertEquals("🏖️ Du lịch", CategoryHelper.formatStandard("🏷️ 🏖️Du lịch"))
    }
}
