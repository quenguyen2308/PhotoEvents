package com.example.photoevents.data

import androidx.room.Embedded
import androidx.room.Relation

data class EventWithImages(
    @Embedded val event: Event,
    @Relation(parentColumn = "id", entityColumn = "eventId")
    val images: List<EventImage>
) {
    /** Ảnh chưa bị xoá, theo thứ tự thêm vào. */
    val visibleImages: List<EventImage>
        get() = images.filter { !it.deleted }.sortedBy { it.position }

    /**
     * Ảnh đại diện: ảnh người dùng đã chọn ([Event.coverImageId]); nếu chưa chọn hoặc ảnh đó đã bị
     * xoá thì tự dùng ảnh đầu tiên còn lại.
     */
    val coverImage: EventImage?
        get() = visibleImages.firstOrNull { it.id == event.coverImageId } ?: visibleImages.firstOrNull()
}
