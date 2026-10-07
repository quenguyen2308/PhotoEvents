package com.example.photoevents.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.annotations.Expose
import com.google.gson.reflect.TypeToken

data class CategoryRecord(
    @Expose val id: String, // cleanTextName lowercase
    @Expose val name: String, // formatStandard, ví dụ "✈️ Du lịch"
    @Expose val updatedAt: Long = System.currentTimeMillis(),
    @Expose val deleted: Boolean = false
)

data class CategoryItem(
    val id: String, // "ALL", "UNCATEGORIZED", hoặc tên danh mục chuẩn
    val name: String,
    val icon: String,
    val count: Int = 0,
    val isAll: Boolean = false,
    val isUncategorized: Boolean = false,
    val isAddAction: Boolean = false,
    val isPreset: Boolean = false
)

object CategoryHelper {
    const val ALL_CATEGORY_ID = "ALL"
    const val UNCATEGORIZED_CATEGORY_ID = "UNCATEGORIZED"
    const val UNCATEGORIZED_NAME = "Chưa gán"
    const val UNCATEGORIZED_ICON = "📂"
    const val UNCATEGORIZED_DISPLAY = "📂 Chưa gán"
    const val DEFAULT_CATEGORY = ""

    const val PREF_SETTINGS = "settings"
    const val PREF_CUSTOM_CATEGORIES = "custom_categories"
    const val PREF_RENAMED_PRESETS = "renamed_presets"
    const val PREF_DELETED_CATEGORIES = "deleted_categories"
    const val PREF_CATEGORY_RECORDS = "category_records_json"

    private val recordsGson by lazy { Gson() }

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
     * Kiểm tra xem một danh mục có phải là "Chưa gán" (không có danh mục) hay không.
     */
    fun isUncategorized(category: String?): Boolean {
        val trimmed = category?.trim().orEmpty()
        if (trimmed.isEmpty()) return true
        if (trimmed.equals(UNCATEGORIZED_CATEGORY_ID, ignoreCase = true)) return true
        val clean = extractCleanTextName(trimmed)
        return clean.equals(UNCATEGORIZED_NAME, ignoreCase = true) ||
               clean.equals("uncategorized", ignoreCase = true) ||
               clean.equals("chua gan", ignoreCase = true)
    }

    /**
     * Trích xuất phần text thuần tuý của tên danh mục (loại bỏ toàn bộ emoji/icon ở đầu, cuối và khoảng trắng dư thừa).
     * Đóng vai trò là định danh (identity) cốt lõi cho việc so khớp, tìm kiếm, xoá, và đồng bộ danh mục.
     * Ví dụ:
     *   "🏷️ 🏖️ Du lịch" -> "Du lịch"
     *   "🏷️ 🏖️Du lịch"  -> "Du lịch"
     *   "🏖️ Du lịch"    -> "Du lịch"
     *   "Du lịch 🏖️"    -> "Du lịch"
     *   "Du lịch"        -> "Du lịch"
     *   ""               -> "Chưa gán"
     */
    fun extractCleanTextName(category: String?): String {
        val trimmed = category?.trim().orEmpty()
        if (trimmed.isEmpty() || trimmed.equals(UNCATEGORIZED_CATEGORY_ID, ignoreCase = true)) {
            return UNCATEGORIZED_NAME
        }
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
     *   ""               -> Pair("📂", "Chưa gán")
     */
    fun extractIconAndName(rawCategory: String?): Pair<String, String> {
        val trimmed = rawCategory?.trim().orEmpty()
        if (trimmed.isEmpty() || isUncategorized(trimmed)) {
            return Pair(UNCATEGORIZED_ICON, UNCATEGORIZED_NAME)
        }

        val (explicitEmoji, cleanName) = extractExplicitEmojiAndCleanName(trimmed)
        if (cleanName.isEmpty() || isUncategorized(cleanName)) {
            return Pair(explicitEmoji ?: UNCATEGORIZED_ICON, UNCATEGORIZED_NAME)
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
        if (isUncategorized(rawCategory)) return UNCATEGORIZED_DISPLAY
        val (icon, name) = extractIconAndName(rawCategory)
        return "$icon $name"
    }

    /**
     * Lấy danh sách đầy đủ CategoryRecord (gồm cả mục đang hoạt động và các mục đã bị xoá tombstone).
     * Tự động migrate từ cấu trúc lưu cũ (custom_categories, deleted_categories) nếu chưa có JSON.
     */
    fun getCategoryRecords(context: Context): List<CategoryRecord> {
        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        val json = prefs.getString(PREF_CATEGORY_RECORDS, null)
        if (!json.isNullOrBlank()) {
            try {
                val type = object : TypeToken<List<CategoryRecord>>() {}.type
                val list: List<CategoryRecord>? = recordsGson.fromJson(json, type)
                if (!list.isNullOrEmpty()) {
                    val map = LinkedHashMap<String, CategoryRecord>()
                    for (rec in list) {
                        val key = rec.id.trim().lowercase()
                        if (key.isBlank() || isUncategorized(key)) continue
                        val existing = map[key]
                        if (existing == null || rec.updatedAt >= existing.updatedAt) {
                            map[key] = rec.copy(id = key)
                        }
                    }
                    return map.values.toList()
                }
            } catch (e: Exception) {
                // Lỗi phân tích JSON thì chuyển sang migrate dữ liệu cũ
            }
        }

        // Migrate từ preferences cũ: PREF_CUSTOM_CATEGORIES và PREF_DELETED_CATEGORIES
        val map = LinkedHashMap<String, CategoryRecord>()
        val customCats = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
        val deletedCats = prefs.getStringSet(PREF_DELETED_CATEGORIES, emptySet()) ?: emptySet()

        for (custom in customCats) {
            val trimmed = custom.trim()
            if (trimmed.isEmpty() || isUncategorized(trimmed)) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || isUncategorized(clean)) continue
            val key = clean.lowercase()
            map[key] = CategoryRecord(
                id = key,
                name = formatStandard(trimmed),
                updatedAt = 1L,
                deleted = false
            )
        }

        for (del in deletedCats) {
            val trimmed = del.trim()
            if (trimmed.isEmpty() || isUncategorized(trimmed)) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || isUncategorized(clean)) continue
            val key = clean.lowercase()
            val existing = map[key]
            val formatted = existing?.name ?: formatStandard(trimmed)
            map[key] = CategoryRecord(
                id = key,
                name = formatted,
                updatedAt = 2L,
                deleted = true
            )
        }

        val result = map.values.toList()
        if (result.isNotEmpty()) {
            saveCategoryRecords(context, result)
        }
        return result
    }

    /**
     * Lưu danh sách CategoryRecord và đồng bộ với SharedPreferences cũ để tương thích ngược.
     */
    fun saveCategoryRecords(context: Context, records: Collection<CategoryRecord>) {
        val map = LinkedHashMap<String, CategoryRecord>()
        for (rec in records) {
            val key = rec.id.trim().lowercase()
            if (key.isBlank() || isUncategorized(key)) continue
            val existing = map[key]
            if (existing == null || rec.updatedAt >= existing.updatedAt) {
                map[key] = rec.copy(id = key, name = formatStandard(rec.name))
            }
        }
        val deduplicated = map.values.toList()
        val json = recordsGson.toJson(deduplicated)

        val activeNames = deduplicated
            .filter { !it.deleted && !isUncategorized(it.name) }
            .map { it.name }
            .toSet()
        val deletedIds = deduplicated
            .filter { it.deleted }
            .map { it.id.lowercase() }
            .toSet()

        val prefs = context.getSharedPreferences(PREF_SETTINGS, Context.MODE_PRIVATE)
        prefs.edit()
            .putString(PREF_CATEGORY_RECORDS, json)
            .putStringSet(PREF_CUSTOM_CATEGORIES, activeNames)
            .putStringSet(PREF_DELETED_CATEGORIES, deletedIds)
            .commit()
    }

    /**
     * Lấy tập hợp các danh mục đã bị xoá (tên chuẩn viết thường).
     */
    fun getDeletedCategories(context: Context): Set<String> {
        return getCategoryRecords(context)
            .filter { it.deleted }
            .map { it.id.lowercase() }
            .toSet()
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
            if (trimmed.isEmpty() || isUncategorized(trimmed)) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || isUncategorized(clean)) continue
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
        val records = getCategoryRecords(context)
        val map = linkedMapOf<String, String>()
        for (rec in records) {
            val key = rec.id.trim().lowercase()
            if (!rec.deleted && key.isNotBlank() && !isUncategorized(key)) {
                map[key] = formatStandard(rec.name)
            }
        }
        return map.values.toMutableList()
    }

    /**
     * Lưu danh sách danh mục tuỳ chỉnh vào SharedPreferences.
     * Tự động khử trùng lặp theo cleanTextName để tránh lưu nhiều icon cùng lúc cho 1 tên danh mục.
     */
    fun saveCustomCategories(context: Context, categories: Collection<String>) {
        val now = System.currentTimeMillis()
        val currentRecords = getCategoryRecords(context).toMutableList()
        val deletedSet = getDeletedCategories(context)
        for (cat in categories) {
            val trimmed = cat.trim()
            if (trimmed.isEmpty() || isUncategorized(trimmed)) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || isUncategorized(clean) || deletedSet.contains(clean.lowercase())) continue
            val key = clean.lowercase()
            val existingIndex = currentRecords.indexOfFirst { it.id.equals(key, ignoreCase = true) }
            val formatted = formatStandard(trimmed)
            if (existingIndex >= 0) {
                val existing = currentRecords[existingIndex]
                if (!existing.deleted) {
                    currentRecords[existingIndex] = existing.copy(name = formatted)
                }
            } else {
                currentRecords.add(CategoryRecord(id = key, name = formatted, updatedAt = now, deleted = false))
            }
        }
        saveCategoryRecords(context, currentRecords)
    }

    /**
     * Đồng bộ thêm các danh mục từ sự kiện hiện có vào SharedPreferences nếu chưa tồn tại.
     */
    fun syncCategoriesFromEvents(context: Context, categories: Collection<String>) {
        val currentRecords = getCategoryRecords(context).toMutableList()
        val recordsMap = currentRecords.associateBy { it.id.lowercase() }.toMutableMap()
        var changed = false
        val now = System.currentTimeMillis()

        for (raw in categories) {
            val trimmed = raw.trim()
            if (trimmed.isEmpty() || isUncategorized(raw)) continue
            val clean = extractCleanTextName(trimmed)
            if (clean.isBlank() || isUncategorized(clean)) continue
            val key = clean.lowercase()
            if (!recordsMap.containsKey(key)) {
                val rec = CategoryRecord(
                    id = key,
                    name = formatStandard(trimmed),
                    updatedAt = now,
                    deleted = false
                )
                recordsMap[key] = rec
                changed = true
            }
        }

        if (changed) {
            saveCategoryRecords(context, recordsMap.values)
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
        val clean = extractCleanTextName(cleanName)
        val cleanId = clean.lowercase()
        val now = System.currentTimeMillis()

        val currentRecords = getCategoryRecords(context).toMutableList()
        currentRecords.removeAll { it.id.equals(cleanId, ignoreCase = true) }
        currentRecords.add(CategoryRecord(id = cleanId, name = formatted, updatedAt = now, deleted = false))
        saveCategoryRecords(context, currentRecords)

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
        val oldId = oldClean.lowercase()
        val newId = newClean.lowercase()
        val now = System.currentTimeMillis()

        val isNameChanged = !oldId.equals(newId, ignoreCase = true)
        val currentRecords = getCategoryRecords(context).toMutableList()

        if (isNameChanged && oldId.isNotBlank() && !isUncategorized(oldClean)) {
            // Đánh dấu tombstone đã xoá cho danh mục cũ với timestamp now
            currentRecords.removeAll { it.id.equals(oldId, ignoreCase = true) }
            currentRecords.add(CategoryRecord(id = oldId, name = oldCategory, updatedAt = now, deleted = true))
        }

        // Cập nhật/thêm danh mục mới
        currentRecords.removeAll { it.id.equals(newId, ignoreCase = true) }
        currentRecords.add(CategoryRecord(id = newId, name = newFormatted, updatedAt = now, deleted = false))

        saveCategoryRecords(context, currentRecords)

        // Cập nhật tất cả sự kiện trong DB
        if (db != null) {
            val allEvents = db.eventDao().getAllIncludingDeleted()
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
     * - Lưu tombstone đã xoá với timestamp hiện tại
     * - Cập nhật toàn bộ sự kiện đang dùng danh mục này về Chưa gán ("")
     */
    suspend fun deleteCategory(
        context: Context,
        categoryToDelete: String,
        db: AppDatabase? = null
    ): Int {
        val clean = extractCleanTextName(categoryToDelete)
        val cleanId = clean.lowercase()
        val now = System.currentTimeMillis()

        val currentRecords = getCategoryRecords(context).toMutableList()
        currentRecords.removeAll { it.id.equals(cleanId, ignoreCase = true) }
        currentRecords.add(
            CategoryRecord(
                id = cleanId,
                name = formatStandard(categoryToDelete),
                updatedAt = now,
                deleted = true
            )
        )
        saveCategoryRecords(context, currentRecords)

        // Các sự kiện mồ côi sẽ được chuyển về Chưa gán ("")
        val fallback = ""
        var countUpdated = 0
        if (db != null) {
            val allEvents = db.eventDao().getAllIncludingDeleted()
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
        prefs.edit().remove(PREF_RENAMED_PRESETS).commit()
    }

    /**
     * Kiểm tra xem sự kiện có thuộc danh mục mục tiêu không (so khớp thuần tuý theo text trong tên, bỏ qua mọi emoji).
     */
    fun matches(eventCategory: String?, targetCategoryId: String): Boolean {
        if (targetCategoryId == ALL_CATEGORY_ID) return true
        if (targetCategoryId == UNCATEGORIZED_CATEGORY_ID || isUncategorized(targetCategoryId)) {
            return isUncategorized(eventCategory)
        }
        if (isUncategorized(eventCategory)) return false
        val targetText = extractCleanTextName(targetCategoryId)
        val eventText = extractCleanTextName(eventCategory)
        return targetText.equals(eventText, ignoreCase = true)
    }
}
