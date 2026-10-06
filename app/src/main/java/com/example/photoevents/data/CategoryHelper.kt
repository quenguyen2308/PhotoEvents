package com.example.photoevents.data

data class CategoryItem(
    val id: String, // "ALL", hoặc tên danh mục chuẩn
    val name: String,
    val icon: String,
    val count: Int = 0,
    val isAll: Boolean = false,
    val isAddAction: Boolean = false
)

object CategoryHelper {
    const val ALL_CATEGORY_ID = "ALL"
    const val DEFAULT_CATEGORY = "💖 Kỷ niệm"

    val PRESET_CATEGORIES = listOf(
        "💖 Kỷ niệm",
        "✈️ Du lịch",
        "👨‍👩‍👧 Gia đình",
        "🎉 Bạn bè",
        "🎂 Sinh nhật",
        "☕ Hẹn hò",
        "🌿 Đời sống",
        "💼 Công việc"
    )

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

        // Kiểm tra xem chuỗi có định dạng "[Icon] [Tên]" không (phần icon không chứa chữ cái hoặc số)
        val spaceIndex = trimmed.indexOf(' ')
        if (spaceIndex > 0) {
            val potentialIcon = trimmed.substring(0, spaceIndex).trim()
            val potentialName = trimmed.substring(spaceIndex + 1).trim()
            if (potentialIcon.isNotEmpty() && potentialIcon.none { it.isLetterOrDigit() } && potentialName.isNotEmpty()) {
                return Pair(potentialIcon, potentialName)
            }
        }

        // Nếu toàn bộ chuỗi chỉ gồm ký tự emoji / biểu tượng
        if (trimmed.none { it.isLetterOrDigit() }) {
            return Pair(trimmed, "Chung")
        }

        // Nếu không có emoji phía trước, tìm trong bảng tra cứu
        val mappedIcon = NAME_TO_ICON_MAP[trimmed.lowercase()]
        return if (mappedIcon != null) {
            Pair(mappedIcon, trimmed)
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
     * Lấy danh sách các danh mục khả dụng gồm preset (đã cập nhật tên nếu được đổi) và custom.
     */
    fun getAvailableCategories(context: android.content.Context): MutableList<String> {
        val prefs = context.getSharedPreferences("settings", android.content.Context.MODE_PRIVATE)
        val renamedSet = prefs.getStringSet("renamed_presets", emptySet()) ?: emptySet()
        val renamedMap = mutableMapOf<String, String>()
        for (entry in renamedSet) {
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                renamedMap[parts[0].lowercase()] = parts[1]
            }
        }

        val result = linkedSetOf<String>()
        for (preset in PRESET_CATEGORIES) {
            val (_, clean) = extractIconAndName(preset)
            val actual = renamedMap[clean.lowercase()] ?: preset
            result.add(actual)
        }

        val customCats = prefs.getStringSet("custom_categories", emptySet()) ?: emptySet()
        result.addAll(customCats)

        return result.toMutableList()
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
