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
     * Trích xuất phần text thuần tuý của tên danh mục (loại bỏ toàn bộ emoji/icon ở đầu, cuối và khoảng trắng dư thừa).
     * Đóng vai trò là định danh (identity) cốt lõi cho việc so khớp, tìm kiếm, xoá, và đồng bộ danh mục.
     * Ví dụ:
     *   "🏷️ 🏖️ Du lịch" -> "Du lịch"
     *   "🏷️ 🏖️Du lịch"  -> "Du lịch"
     *   "🏖️ Du lịch"    -> "Du lịch"
     *   "Du lịch 🏖️"    -> "Du lịch"
     *   "Du lịch"        -> "Du lịch"
     *   ""               -> "Chung"
     */
    fun extractCleanTextName(category: String?): String {
        val trimmed = category?.trim().orEmpty()
        if (trimmed.isEmpty()) return "Chung"
        val firstLetter = trimmed.indexOfFirst { it.isLetterOrDigit() }
        val lastLetter = trimmed.indexOfLast { it.isLetterOrDigit() }
        if (firstLetter != -1 && lastLetter >= firstLetter) {
            val extracted = trimmed.substring(firstLetter, lastLetter + 1).trim()
            if (extracted.isNotEmpty()) return extracted
        }
        return trimmed
    }

    /**
     * Tách icon (emoji) và tên danh mục hiển thị từ chuỗi thô.
     * Tự động làm sạch các trường hợp tên bị dính nhiều emoji hoặc placeholder rác (như "🏷️ 🏖️ Du lịch").
     * Ví dụ:
     *   "🏷️ 🏖️ Du lịch" -> Pair("🏖️", "Du lịch")
     *   "✈️ Du lịch"     -> Pair("✈️", "Du lịch")
     *   "Du lịch"        -> Pair("✈️", "Du lịch")
     *   ""               -> Pair("🌸", "Chung")
     */
    fun extractIconAndName(rawCategory: String?): Pair<String, String> {
        val trimmed = rawCategory?.trim().orEmpty()
        if (trimmed.isEmpty()) return Pair("🌸", "Chung")

        val (explicitEmoji, cleanName) = extractExplicitEmojiAndCleanName(trimmed)
        if (cleanName.isEmpty()) {
            return Pair(explicitEmoji ?: "🌸", "Chung")
        }

        // 1. Nếu có emoji người dùng chọn/nhập (khác generic placeholder "🏷️" và "🌸")
        if (explicitEmoji != null && explicitEmoji != "🏷️" && explicitEmoji != "🌸") {
            return Pair(explicitEmoji, cleanName)
        }

        // 2. Tra cứu trong bảng danh mục mặc định
        for (preset in PRESET_CATEGORIES) {
            val (pIcon, pName) = extractExplicitEmojiAndCleanName(preset)
            if (pName.equals(cleanName, ignoreCase = true)) {
                return Pair(pIcon ?: "🏷️", pName)
            }
        }

        // 3. Tra cứu trong bảng tên thông dụng
        val mappedIcon = NAME_TO_ICON_MAP[cleanName.lowercase()]
        if (mappedIcon != null) {
            val capitalized = cleanName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
            return Pair(mappedIcon, capitalized)
        }

        return Pair(explicitEmoji ?: "🏷️", cleanName)
    }

    /**
     * Định dạng danh mục chuẩn có emoji kèm theo, ví dụ "✈️ Du lịch".
     * Tự động dọn dẹp các emoji thừa / placeholder rác để chỉ giữ 1 emoji và tên sạch.
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
        val name = extractCleanTextName(category)
        return PRESET_CATEGORIES.any {
            val pName = extractCleanTextName(it)
            pName.equals(name, ignoreCase = true)
        }
    }

    /**
     * Tách emoji rõ ràng (nếu người dùng thực sự nhập emoji vào chuỗi) và tên danh mục sạch (chỉ gồm text).
     * Khác với extractIconAndName, hàm này KHÔNG tự động gán icon mặc định hay tra cứu NAME_TO_ICON_MAP.
     * Tự động dọn dẹp các placeholder rác (như "🏷️ ") bị lưu chung với emoji người dùng nhập.
     * Trả về Pair(explicitEmoji?, cleanName).
     */
    fun extractExplicitEmojiAndCleanName(rawCategory: String?): Pair<String?, String> {
        val trimmed = rawCategory?.trim().orEmpty()
        if (trimmed.isEmpty()) return Pair(null, "")

        val firstLetter = trimmed.indexOfFirst { it.isLetterOrDigit() }
        val lastLetter = trimmed.indexOfLast { it.isLetterOrDigit() }

        // Trường hợp chuỗi không chứa ký tự chữ/số nào (chỉ toàn emoji, vd "🏖️")
        if (firstLetter == -1) {
            return Pair(trimmed, "")
        }

        val cleanName = trimmed.substring(firstLetter, lastLetter + 1).trim()
        val leading = trimmed.substring(0, firstLetter).trim()
        val trailing = trimmed.substring(lastLetter + 1).trim()

        val realLeading = leading.replace("🏷️", "").replace("🌸", "").trim()
        val realTrailing = trailing.replace("🏷️", "").replace("🌸", "").trim()

        val explicitEmoji = when {
            realLeading.isNotEmpty() -> {
                if (realLeading.contains(" ")) realLeading.split("\\s+".toRegex()).last() else realLeading
            }
            realTrailing.isNotEmpty() -> {
                if (realTrailing.contains(" ")) realTrailing.split("\\s+".toRegex()).first() else realTrailing
            }
            leading.isNotEmpty() -> leading
            trailing.isNotEmpty() -> trailing
            else -> null
        }

        return Pair(explicitEmoji, cleanName)
    }

    /**
     * Khử trùng lặp danh sách danh mục theo tên chuẩn (cleanTextName lowercase).
     * Mục xuất hiện trước giữ lại icon của nó, không sinh ra bản sao trùng tên mang icon khác.
     */
    fun deduplicateCategories(categories: Collection<String>): List<String> {
        val map = linkedMapOf<String, String>()
        for (cat in categories) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty()) continue
            val clean = extractCleanTextName(trimmed)
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
            val clean = extractCleanTextName(trimmed)
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
     * Tự động khử trùng lặp theo cleanTextName để tránh lưu nhiều icon cùng lúc cho 1 tên danh mục.
     */
    fun saveCustomCategories(context: Context, categories: Collection<String>) {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val deletedSet = getDeletedCategories(context)
        val map = linkedMapOf<String, String>()
        for (cat in categories) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty()) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || deletedSet.contains(clean.lowercase())) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
            }
        }
        prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, map.values.toSet()).commit()
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
            val clean = extractCleanTextName(c)
            if (clean.isNotBlank() && !deletedSet.contains(clean.lowercase())) {
                map[clean.lowercase()] = formatStandard(c)
            }
        }

        var changed = map.size != currentCustom.size
        for (raw in categories) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || deletedSet.contains(clean.lowercase())) continue
            if (clean.equals("Chung", ignoreCase = true) && trimmed.isEmpty()) continue
            val key = clean.lowercase()
            if (!map.containsKey(key)) {
                map[key] = formatStandard(trimmed)
                changed = true
            }
        }
        if (changed) {
            prefs.edit().putStringSet(PREF_CUSTOM_CATEGORIES, map.values.toSet()).commit()
        }
    }

    /**
     * Thêm một danh mục mới.
     * Nếu trước đó từng bị xoá, sẽ khôi phục lại khỏi danh sách đã xoá.
     */
    fun addCategory(context: Context, rawCategory: String): String {
        val (explicitIcon, cleanName) = extractExplicitEmojiAndCleanName(rawCategory)
        val finalIcon = explicitIcon ?: extractIconAndName(rawCategory).first
        val formatted = "$finalIcon $cleanName"
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

        // 2. Thêm vào custom_categories (xoá bất kỳ bản cũ trùng tên cleanName)
        val custom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        custom.removeAll { matches(it, cleanName) }
        custom.add(formatted)

        prefs.edit()
            .putStringSet(PREF_DELETED_CATEGORIES, deleted)
            .putStringSet(PREF_CUSTOM_CATEGORIES, custom)
            .commit()

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
        val oldClean = extractCleanTextName(oldCategory)
        val newClean = extractCleanTextName(newFormatted)
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        // Bỏ newClean khỏi danh sách đã xoá (nếu có)
        deleted.removeAll { it.equals(newClean, ignoreCase = true) }

        // NẾU ĐỔI TÊN (oldClean != newClean):
        // BẮT BUỘC thêm oldClean vào deleted_categories để Drive Sync và events cũ không tái sinh danh mục cũ!
        val isNameChanged = !oldClean.equals(newClean, ignoreCase = true)
        if (isNameChanged && oldClean.isNotBlank()) {
            deleted.add(oldClean.lowercase())
        }

        // 1. Cập nhật custom_categories: Xoá TẤT CẢ các mục khớp với oldClean hoặc newClean
        val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentCustom.removeAll { matches(it, oldClean) || matches(it, newClean) }
        currentCustom.add(newFormatted)

        // 2. Cập nhật renamed_presets
        val renamedPresets = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet())?.toMutableSet() ?: mutableSetOf()
        var matchedPresetClean: String? = null
        for (preset in PRESET_CATEGORIES) {
            val pClean = extractCleanTextName(preset)
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
        }

        // Lưu đồng bộ tất cả vào SharedPreferences
        prefs.edit()
            .putStringSet(PREF_DELETED_CATEGORIES, deleted)
            .putStringSet(PREF_CUSTOM_CATEGORIES, currentCustom)
            .putStringSet(PREF_RENAMED_PRESETS, renamedPresets)
            .commit()

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
        val cleanName = extractCleanTextName(category)
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
        val clean = extractCleanTextName(categoryToDelete)
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)

        // 1. Thêm vào deleted_categories (lưu tên text thuần tuý viết thường)
        val deleted = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        if (clean.isNotBlank() && !clean.equals("Chung", ignoreCase = true)) {
            deleted.add(clean.lowercase())
        }
        val (_, rawName) = extractIconAndName(categoryToDelete)
        if (rawName.isNotBlank() && !rawName.equals("Chung", ignoreCase = true)) {
            deleted.add(rawName.lowercase())
        }
        val trimmedRaw = categoryToDelete.trim().lowercase()
        if (trimmedRaw.isNotBlank()) {
            deleted.add(trimmedRaw)
        }
        for (preset in PRESET_CATEGORIES) {
            val pClean = extractCleanTextName(preset)
            if (matches(preset, clean)) {
                deleted.add(pClean.lowercase())
            }
        }

        // 2. Xoá khỏi custom_categories (xoá mọi biến thể khớp theo text thuần tuý)
        val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet())?.toMutableSet() ?: mutableSetOf()
        currentCustom.removeAll { matches(it, clean) || matches(it, categoryToDelete) }

        // 3. Xoá khỏi renamed_presets
        val renamedPresets = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet())?.toMutableSet() ?: mutableSetOf()
        renamedPresets.removeAll { 
            matches(it.substringBefore("|"), clean) || matches(it.substringAfter("|"), clean) 
        }
        for (preset in PRESET_CATEGORIES) {
            val pClean = extractCleanTextName(preset)
            if (matches(preset, clean)) {
                renamedPresets.removeAll { it.startsWith("${pClean.lowercase()}|") }
            }
        }

        // Ghi đồng bộ tất cả vào SharedPreferences cùng lúc
        prefs.edit()
            .putStringSet(PREF_DELETED_CATEGORIES, deleted)
            .putStringSet(PREF_CUSTOM_CATEGORIES, currentCustom)
            .putStringSet(PREF_RENAMED_PRESETS, renamedPresets)
            .commit()

        // 4. Tìm danh mục thay thế cho các sự kiện bị mồ côi
        val available = getAvailableCategories(context)
        val fallback = available.firstOrNull { !matches(it, clean) } ?: ""

        // 5. Cập nhật các sự kiện trong Room DB (so khớp theo text thuần tuý)
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
            val pClean = extractCleanTextName(preset)
            deleted.remove(pClean.lowercase())
        }
        prefs.edit()
            .putStringSet(PREF_DELETED_CATEGORIES, deleted)
            .remove(PREF_RENAMED_PRESETS)
            .commit()
    }

    /**
     * Kiểm tra xem sự kiện có thuộc danh mục mục tiêu không (so khớp thuần tuý theo text trong tên, bỏ qua mọi emoji).
     */
    fun matches(eventCategory: String?, targetCategoryId: String): Boolean {
        if (targetCategoryId == ALL_CATEGORY_ID) return true
        val targetText = extractCleanTextName(targetCategoryId)
        val eventText = extractCleanTextName(eventCategory)
        return targetText.equals(eventText, ignoreCase = true)
    }
}
