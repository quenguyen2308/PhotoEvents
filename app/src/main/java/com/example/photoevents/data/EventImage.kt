package com.example.photoevents.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import java.util.UUID

/**
 * 1 ảnh thuộc về 1 [Event] (quan hệ 1-nhiều qua [eventId]).
 *
 * - [localImagePath]: đường dẫn cache trên máy — KHÔNG @Expose nên không bị ghi vào metadata.json
 *   khi đồng bộ (chỉ có ý nghĩa trên chính thiết bị đó). Máy khác tự tải ảnh về cache riêng dựa
 *   theo [driveFileId]. Vẫn được Room lưu bình thường (không dùng `transient`, Room sẽ bỏ qua
 *   field `transient`).
 * - [deleted]: soft-delete — khi xoá 1 ảnh, chỉ đánh dấu deleted=true (kèm updatedAt mới) để lần
 *   sync kế tiếp xoá file thật trên Drive và báo cho các thiết bị khác cũng gỡ ảnh này.
 */
@Entity(
    tableName = "event_images",
    foreignKeys = [
        ForeignKey(
            entity = Event::class,
            parentColumns = ["id"],
            childColumns = ["eventId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("eventId")]
)
data class EventImage(
    @Expose @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @Expose var eventId: String = "",
    @Expose var driveFileId: String? = null,
    @Expose var driveThumbnailLink: String? = null,
    var localImagePath: String? = null,
    @Expose var position: Int = 0,
    @Expose var focusX: Float = 0.5f,
    @Expose var focusY: Float = 0.25f,
    @Expose var createdAt: Long = System.currentTimeMillis(),
    @Expose var updatedAt: Long = System.currentTimeMillis(),
    @Expose var deleted: Boolean = false
)
