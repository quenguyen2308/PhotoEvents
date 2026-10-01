package com.example.photoevents.ui

import com.example.photoevents.data.EventWithImages
import java.text.Collator
import java.util.Locale

/** Các kiểu sắp xếp danh sách sự kiện trên màn hình chính. */
enum class SortOption(val label: String) {
    EVENT_DATE_NEWEST("Ngày diễn ra (mới nhất)"),
    EVENT_DATE_OLDEST("Ngày diễn ra (cũ nhất)"),
    NEWEST("Mới nhất"),
    OLDEST("Cũ nhất"),
    RECENTLY_UPDATED("Cập nhật gần đây"),
    TITLE_ASC("Tên A → Z"),
    TITLE_DESC("Tên Z → A"),
    MOST_IMAGES("Nhiều ảnh nhất");

    fun sort(list: List<EventWithImages>): List<EventWithImages> {
        // Collator tiếng Việt để xếp đúng thứ tự có dấu (Ă, Â, Đ, Ê, Ô, Ơ, Ư...)
        val collator = Collator.getInstance(Locale("vi", "VN"))
        return when (this) {
            EVENT_DATE_NEWEST -> list.sortedByDescending { it.event.eventDate }
            EVENT_DATE_OLDEST -> list.sortedBy { it.event.eventDate }
            NEWEST -> list.sortedByDescending { it.event.createdAt }
            OLDEST -> list.sortedBy { it.event.createdAt }
            RECENTLY_UPDATED -> list.sortedByDescending { it.event.updatedAt }
            TITLE_ASC -> list.sortedWith { a, b -> collator.compare(a.event.title, b.event.title) }
            TITLE_DESC -> list.sortedWith { a, b -> collator.compare(b.event.title, a.event.title) }
            MOST_IMAGES -> list.sortedByDescending { it.visibleImages.size }
        }
    }

    companion object {
        fun fromName(name: String?): SortOption =
            values().firstOrNull { it.name == name } ?: NEWEST
    }
}
