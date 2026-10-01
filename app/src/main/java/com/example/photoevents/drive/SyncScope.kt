package com.example.photoevents.drive

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Scope sống theo cả ứng dụng (không gắn với 1 Activity cụ thể), dùng để bắn sync chạy ngầm
 * ngay sau khi lưu/xoá — kể cả khi Activity gọi finish() ngay sau đó (ví dụ AddEventActivity).
 * Dùng lifecycleScope cho việc này rất rủi ro: nếu Activity bị huỷ trước khi coroutine kịp
 * chạy tới đoạn sync, việc sync có thể không bao giờ xảy ra.
 */
object SyncScope {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
}
