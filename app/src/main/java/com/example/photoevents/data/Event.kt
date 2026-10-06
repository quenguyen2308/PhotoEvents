package com.example.photoevents.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.google.gson.annotations.Expose
import java.util.UUID

/**
 * Một sự kiện — có thể chứa NHIỀU ảnh (xem [EventImage], bảng riêng, quan hệ 1-nhiều qua eventId).
 *
 * Việc "xổ ra / thu lại" dải ảnh trên màn hình chính chỉ là trạng thái hiển thị cục bộ (giữ
 * trong EventsAdapter khi cuộn danh sách), không lưu DB / không đồng bộ — vì đó là thao tác xem
 * tạm thời, không phải dữ liệu của sự kiện.
 *
 * - [updatedAt]: dùng để merge khi đồng bộ (bản mới hơn thắng).
 * - [deleted]: soft-delete để việc xoá cả sự kiện cũng lan truyền khi sync.
 */
@Entity(tableName = "events")
data class Event(
    @Expose @PrimaryKey val id: String = UUID.randomUUID().toString(),
    @Expose var title: String = "",
    @Expose var note: String = "",
    @Expose var createdAt: Long = System.currentTimeMillis(),
    @Expose var updatedAt: Long = System.currentTimeMillis(),
    @Expose var coverImageId: String? = null, // ảnh đại diện do người dùng chọn; null = dùng ảnh đầu tiên
    @Expose var eventDate: Long = normalizeToMidnight(System.currentTimeMillis()), // ngày diễn ra sự kiện (không giờ/phút/giây) — mặc định hôm nay lúc tạo, có thể đổi lại
    @Expose var deleted: Boolean = false,
    @Expose var category: String = "" // danh mục phân loại sự kiện (ví dụ: Du lịch, Kỷ niệm, Gia đình, ...)
)

/** Đưa 1 mốc thời gian về 00:00:00.000 cùng ngày (giờ hệ thống của máy), để "ngày diễn ra" không lẫn giờ/phút/giây khi lưu/so sánh. */
fun normalizeToMidnight(millis: Long): Long {
    val cal = java.util.Calendar.getInstance()
    cal.timeInMillis = millis
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}
