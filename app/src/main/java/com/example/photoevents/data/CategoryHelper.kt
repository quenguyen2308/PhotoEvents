package com.example.photoevents.data

import android.content.Context

data class CategoryItem(
    val id: String, // "ALL", hoặc tên danh mục chuẩn
    val name: String,
    val icon: String,
    val count: Int = 0,
    val isAll: Boolean = false,
    val isAddAction: Boolean = false,
    val isPreset: Boolean = false
)

object CategoryHelper {
    const val ALL_CATEGORY_ID = "ALL"
    const val DEFAULT_CATEGORY = ""

    const val PREF_SETTINGS = "settings"
    const val PREF_CUSTOM_CATEGORIES = "custom_categories"
    const val PREF_RENAMED_PRESETS = "renamed_presets"
    const val PREF_DELETED_CATEGORIES = "deleted_categories"

    val PRESET_CATEGORIES: List<String> = emptyList()

    private val NAME_TO_ICON_MAP = mapOf(
        "kỷ niệm" to "💖",
        "ky niem" to "💖",
        "du lịch" to "✈️",
        "du lich" to "✈️",
        "gia đình" to "👨‍👩‍👧",
        "gia dinh" to "👨‍👩‍👧",
        "bạn bè" to "🎉",
        "ban be" to "🎉",
        "sinh nhật" to "🎂",
        "sinh nhat" to "🎂",
        "hẹn hò" to "☕",
        "hen ho" to "☕",
        "đời sống" to "🌿",
        "doi song" to "🌿",
        "hằng ngày" to "🌿",
        "công việc" to "💼",
        "cong viec" to "💼",
        "chung" to "🌸",
        "chưa phân loại" to "🌸"
    )

    /**
     * Tách icon (emoji) và tên danh mục hiển thị từ chuỗi thô.
     * Ví dụ: "✈️ Du lịch" -> Pair("✈️", "Du lịch")
     *        "Du lịch" -> Pair("✈️", "Du lịch")
     *        "" -> Pair("🌸", "Chung")
     */
    fun extractIconAndName(rawCategory: String?): Pair<String, String> {
        val trimmed = rawCategory?.trim().orEmpty()
        if (trimmed.isEmpty()) return Pair("🌸", "Chung")

        // 1. Kiểm tra xem chuỗi có định dạng "[Icon] [Tên]" không (phần icon không chứa chữ cái hoặc số)
        val spaceIndex = trimmed.indexOf(' ')
        if (spaceIndex > 0) {
            val potentialIcon = trimmed.substring(0, spaceIndex).trim()
            val potentialName = trimmed.substring(spaceIndex + 1).trim()
            if (potentialIcon.isNotEmpty() && potentialIcon.none { it.isLetterOrDigit() } && potentialName.isNotEmpty()) {
                val cleanName = potentialName.trimEnd { !it.isLetterOrDigit() && !it.isWhitespace() }
                return Pair(potentialIcon, if (cleanName.isNotEmpty()) cleanName else potentialName)
            }
        }

        // 2. Kiểm tra tiền tố Emoji ở đầu không có dấu cách (ví dụ "🏕️DaNgoai")
        val firstLetterIndex = trimmed.indexOfFirst { it.isLetterOrDigit() }
        if (firstLetterIndex > 0) {
            val potentialIcon = trimmed.substring(0, firstLetterIndex).trim()
            val potentialName = trimmed.substring(firstLetterIndex).trim().trimEnd { !it.isLetterOrDigit() && !it.isWhitespace() }
            if (potentialIcon.isNotEmpty() && potentialName.isNotEmpty()) {
                return Pair(potentialIcon, potentialName)
            }
        }

        // 3. Kiểm tra hậu tố Emoji ở cuối (ví dụ "DaNgoai 🏕️" hoặc "DaNgoai🏕️")
        val lastLetterIndex = trimmed.indexOfLast { it.isLetterOrDigit() }
        if (lastLetterIndex in 0 until trimmed.length - 1) {
            val potentialName = trimmed.substring(0, lastLetterIndex + 1).trim()
            val potentialIcon = trimmed.substring(lastLetterIndex + 1).trim()
            if (potentialIcon.isNotEmpty() && potentialName.isNotEmpty()) {
                return Pair(potentialIcon, potentialName)
            }
        }

        // 4. Nếu toàn bộ chuỗi chỉ gồm ký tự emoji / biểu tượng
        if (trimmed.none { it.isLetterOrDigit() }) {
            return Pair(trimmed, "Chung")
        }

        // 5. Nếu không có emoji phía trước, kiểm tra xem có khớp danh mục mặc định không
        for (preset in PRESET_CATEGORIES) {
            val spaceIdx = preset.indexOf(' ')
            if (spaceIdx > 0) {
                val pIcon = preset.substring(0, spaceIdx).trim()
                val pName = preset.substring(spaceIdx + 1).trim()
                if (pName.equals(trimmed, ignoreCase = true)) {
                    return Pair(pIcon, pName)
                }
            }
        }

        // 6. Tìm trong bảng tra cứu tên thông dụng
        val mappedIcon = NAME_TO_ICON_MAP[trimmed.lowercase()]
        return if (mappedIcon != null) {
            val capitalized = trimmed.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            Pair(mappedIcon, capitalized)
        } else {
            Pair("🏷️", trimmed)
        }
    }

    /**
     * Định dạng danh mục chuẩn có emoji kèm theo, ví dụ "✈️ Du lịch"
     */
    fun formatStandard(rawCategory: String?): String {
        val (icon, name) = extractIconAndName(rawCategory)
        return "$icon $name"
    }

    /**
     * Lấy tập hợp các danh mục đã bị xoá (tên chuẩn viết thường).
     */
    fun getDeletedCategories(context: Context): Set<String> {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        return prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())
            ?.map { it.trim().lowercase() }
            ?.toSet() ?: emptySet()
    }

    /**
     * Lấy bản đồ tên preset đã đổi: [presetCleanNameLowercase] -> [newNameFormatted]
     */
    fun getRenamedPresetsMap(context: Context): Map<String, String> {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val set = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet()) ?: emptySet()
        val map = mutableMapOf<String, String>()
        for (entry in set) {
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                map[parts[0].lowercase()] = parts[1]
            }
        }
        return map
    }

    /**
     * Kiểm tra xem danh mục có phải danh mục mặc định gốc không.
     */
    fun isPreset(category: String): Boolean {
        val (_, name) = extractIconAndName(category)
        return PRESET_CATEGORIES.any {
            val (_, pName) = extractIconAndName(it)
            pName.equals(name, ignoreCase = true)
        }
    }

    /**
     * Khử trùng lặp danh sách danh mục theo tên chuẩn (cleanName lowercase).
     * Mục xuất hiện trước giữ lại icon của nó, không sinh ra bản sao trùng tên mang icon khác.
     */
    fun deduplicateCategories(categories: Collection<String>): List<String> {
        val map = linkedMapOf<String, String>()
        for (cat in categories) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty()) continue
            val (_, clean) = extractIconAndName(trimmed)
            if (clean.isBlank()) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
            }
        }
        return map.values.toList()
    }

    /**
     * Lấy danh sách các danh mục khả dụng do người dùng tạo (loại bỏ các mục đã bị xoá).
     * Đảm bảo mỗi tên danh mục là duy nhất (không bị lặp lại khi đổi icon).
     */
    fun getAvailableCategories(context: Context): MutableList<String> {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val deletedSet = getDeletedCategories(context)

        val map = linkedMapOf<String, String>()
        val customCats = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
        for (custom in customCats) {
            val trimmed = custom.trim()
            if (trimmed.isEmpty()) continue
            val (_, clean) = extractIconAndName(trimmed)
            if (clean.isBlank() || deletedSet.contains(clean.lowercase())) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
            }
        }

        return map.values.toMutableList()
    }

    /**
     * Lưu danh sách danh mục tuỳ chỉnh vào SharedPreferences.
     * Tự động khử trùng lặp theo cleanName để tránh lưu nhiều icon cùng lúc cho 1 tên danh mục.
     */
    fun saveCustomCategories(context: Context, categories: Collection<String>) {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val deletedSet = getDeletedCategories(context)
        val map = linkedMapOf<String, String>()
        for (cat in categories) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty()) continue
            val (_, clean) = extractIconAndName(trimmed)
            if (clean.isBlank() || deletedSet.contains(clean.lowercase())) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
            }
        }
        prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, map.values.toSet()).apply()
    }

    /**
     * Đồng bộ thêm các danh mục từ sự kiện hiện có vào SharedPreferences nếu chưa tồn tại.
     */
    fun syncCategoriesFromEvents(context: Context, categories: Collection<String>) {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val deletedSet = getDeletedCategories(context)
        val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        
        val map = linkedMapOf<String, String>()
        for (c in currentCustom) {
            val (_, clean) = extractIconAndName(c)
            if (clean.isNotBlank() && !deletedSet.contains(clean.lowercase())) {
                map[clean.lowercase()] = formatStandard(c)
            }
        }

        var changed = map.size != currentCustom.size
        for (raw in categories) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val (_, clean) = extractIconAndName(trimmed)
            if (clean.isBlank() || deletedSet.contains(clean.lowercase())) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, map.values.toSet()).apply()
        }
    }

    /**
     * Thêm một danh mục mới.
     * Nếu trước đó từng bị xoá, sẽ khôi phục lại khỏi danh sách đã xoá.
     */
    fun addCategory(context: Context, rawCategory: String): String {
        val formatted = formatStandard(rawCategory)
        val (_, cleanName) = extractIconAndName(formatted)
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

        // 1. Xoá khỏi danh sách đã xoá nếu có
        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        deleted.removeAll { it.equals(cleanName, ignoreCase = true) }
        for (preset in PRESET_CATEGORIES) {
            val (_, pClean) = extractIconAndName(preset)
            if (pClean.equals(cleanName, ignoreCase = true)) {
                deleted.remove(pClean.lowercase())
            }
        }
        prefs.edit().putStringSet(PREF_DELETED_CATEGORIES, deleted).apply()

        // 2. Thêm vào custom_categories (xoá bất kỳ bản cũ trùng tên cleanName)
        val custom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        custom.removeAll { matches(it, cleanName) }
        custom.add(formatted)
        saveCustomCategories(context, custom)

        return formatted
    }

    /**
     * Đổi tên hoặc đổi icon của một danh mục và đồng bộ cập nhật vào Room Database.
     */
    suspend fun renameCategory(
        context: Context,
        oldCategory: String,
        newRaw: String,
        db: AppDatabase? = null
    ): String {
        val newFormatted = formatStandard(newRaw)
        val (_, oldClean) = extractIconAndName(oldCategory)
        val (_, newClean) = extractIconAndName(newFormatted)
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

        // Bỏ newClean khỏi danh sách đã xoá (nếu có)
        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        deleted.removeAll { it.equals(newClean, ignoreCase = true) }
        prefs.edit().putStringSet(PREF_DELETED_CATEGORIES, deleted).apply()

        // 1. Cập nhật custom_categories: Xoá TẤT CẢ các mục khớp với oldClean hoặc newClean (tránh còn sót icon cũ)
        val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentCustom.removeAll { matches(it, oldClean) || matches(it, newClean) }
        currentCustom.add(newFormatted)
        saveCustomCategories(context, currentCustom)

        // 2. Cập nhật renamed_presets
        val renamedPresets = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var matchedPresetClean: String? = null
        for (preset in PRESET_CATEGORIES) {
            val (_, pClean) = extractIconAndName(preset)
            if (pClean.equals(oldClean, ignoreCase = true)) {
                matchedPresetClean = pClean.lowercase()
                break
            }
        }
        if (matchedPresetClean == null) {
            val existingEntry = renamedPresets.firstOrNull { matches(it.substringAfter("|"), oldClean) }
            if (existingEntry != null) {
                matchedPresetClean = existingEntry.substringBefore("|").lowercase()
            }
        }
        if (matchedPresetClean != null) {
            renamedPresets.removeAll { it.startsWith("$matchedPresetClean|") }
            renamedPresets.add("$matchedPresetClean|$newFormatted")
            prefs.edit().putStringSet(PREF_RENAMED_PRESETS, renamedPresets).apply()
        }

        // 3. Cập nhật tất cả sự kiện trong DB
        if (db != null) {
            val allEvents = db.eventDao().getAllIncludingDeleted()
            val now = System.currentTimeMillis()
            val matchedEvents = allEvents.filter { matches(it.category, oldClean) }
            if (matchedEvents.isNotEmpty()) {
                val updated = matchedEvents.map { it.copy(category = newFormatted, updatedAt = now) }
                db.eventDao().upsertAll(updated)
            }
        }

        return newFormatted
    }

    /**
     * Đổi biểu tượng Emoji của một danh mục mà giữ nguyên tên danh mục.
     * Cập nhật SharedPreferences và Room DB.
     */
    suspend fun updateCategoryIcon(
        context: Context,
        category: String,
        newIcon: String,
        db: AppDatabase? = null
    ): String {
        val (_, cleanName) = extractIconAndName(category)
        val newFormatted = "$newIcon $cleanName"
        return renameCategory(context, category, newFormatted, db)
    }

    /**
     * Xoá hoàn toàn một danh mục:
     * - Lưu vào danh sách đã xoá (để không bao giờ bị hiện lại từ presets hoặc sync cũ)
     * - Xoá khỏi custom_categories và renamed_presets
     * - Cập nhật toàn bộ sự kiện đang dùng danh mục này về danh mục thay thế
     */
    suspend fun deleteCategory(
        context: Context,
        categoryToDelete: String,
        db: AppDatabase? = null
    ): Int {
        val (_, clean) = extractIconAndName(categoryToDelete)
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

        // 1. Thêm vào deleted_categories
        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        deleted.add(clean.lowercase())
        for (preset in PRESET_CATEGORIES) {
            val (_, pClean) = extractIconAndName(preset)
            if (matches(preset, clean)) {
                deleted.add(pClean.lowercase())
            }
        }
        prefs.edit().putStringSet(PREF_DELETED_CATEGORIES, deleted).apply()

        // 2. Xoá khỏi custom_categories
        val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentCustom.removeAll { matches(it, clean) }
        prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, currentCustom).apply()

        // 3. Xoá khỏi renamed_presets
        val renamedPresets = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet())?.toMutableSet() ?: mutableSetOf()
        renamedPresets.removeAll { it.startsWith("${clean.lowercase()}|") || matches(it.substringAfter("|"), clean) }
        for (preset in PRESET_CATEGORIES) {
            val (_, pClean) = extractIconAndName(preset)
            if (matches(preset, clean)) {
                renamedPresets.removeAll { it.startsWith("${pClean.lowercase()}|") }
            }
        }
        prefs.edit().putStringSet(PREF_RENAMED_PRESETS, renamedPresets).apply()

        // 4. Tìm danh mục thay thế cho các sự kiện bị mồ côi
        val available = getAvailableCategories(context)
        val fallback = available.firstOrNull { !matches(it, clean) } ?: ""

        // 5. Cập nhật các sự kiện trong Room DB
        var countUpdated = 0
        if (db != null) {
            val allEvents = db.eventDao().getAllIncludingDeleted()
            val now = System.currentTimeMillis()
            val matchedEvents = allEvents.filter { matches(it.category, clean) }
            if (matchedEvents.isNotEmpty()) {
                countUpdated = matchedEvents.size
                val updated = matchedEvents.map { it.copy(category = fallback, updatedAt = now) }
                db.eventDao().upsertAll(updated)
            }
        }

        return countUpdated
    }

    /**
     * Khôi phục tất cả danh mục mặc định ban đầu.
     */
    fun restoreDefaultPresets(context: Context) {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        for (preset in PRESET_CATEGORIES) {
            val (_, pClean) = extractIconAndName(preset)
            deleted.remove(pClean.lowercase())
        }
        prefs.edit().putStringSet(PREF_DELETED_CATEGORIES, deleted).apply()
        prefs.edit().remove(PREF_RENAMED_PRESETS).apply()
    }

    /**
     * Kiểm tra xem sự kiện có thuộc danh mục mục tiêu không (không phân biệt hoa/thường, bỏ qua emoji).
     */
    fun matches(eventCategory: String?, targetCategoryId: String): Boolean {
        if (targetCategoryId == ALL_CATEGORY_ID) return true
        val (_, targetName) = extractIconAndName(targetCategoryId)
        val (_, eventName) = extractIconAndName(eventCategory)
        return targetName.equals(eventName, ignoreCase = true)
    }
}
