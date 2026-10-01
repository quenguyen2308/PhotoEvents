package com.example.photoevents.drive

import android.content.Context
import android.net.Uri
import com.google.gson.GsonBuilder
import com.google.gson.annotations.Expose
import com.example.photoevents.data.AppDatabase
import com.example.photoevents.data.Event
import com.example.photoevents.data.EventImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Toàn bộ nội dung ghi vào metadata.json trên Drive: danh sách sự kiện + danh sách ảnh. */
private data class SyncPayload(
    @Expose val events: List<Event>? = null,
    @Expose val images: List<EventImage>? = null
)

/** Kết quả 1 lần sync: số ảnh chưa tải về được (sẽ tự thử lại ở lần sync sau) và lỗi đầu tiên gặp phải. */
data class SyncResult(val downloadFailed: Int = 0, val firstError: String? = null)

/**
 * Đồng bộ 2 chiều kiểu "last-write-wins" theo updatedAt, áp dụng cho cả bảng events và
 * bảng event_images (1 sự kiện có nhiều ảnh):
 * 1. Tải metadata.json hiện có trên Drive.
 * 2. Merge events theo id, merge images theo id — bản nào updatedAt lớn hơn thắng.
 * 3. Ảnh đã bị đánh dấu deleted mà vẫn còn driveFileId -> xoá file thật trên Drive.
 * 4. Ảnh local mới (chưa có driveFileId) -> upload lên Drive.
 * 5. Ảnh đã có driveFileId nhưng máy này chưa có cache -> tải về (lỗi được báo lại, không nuốt).
 * 6. Ghi kết quả merge vào Room + ghi đè metadata.json trên Drive.
 *
 * - Chạy trong NonCancellable: rời màn hình giữa chừng không làm sync dừng nửa vời (đã upload ảnh
 *   nhưng chưa kịp ghi metadata.json).
 * - Mutex toàn app: các lần sync gọi chồng nhau (mở app + thêm ảnh ở màn chi tiết...) được xếp
 *   hàng, tránh đọc-ghi metadata.json đè lên nhau.
 */
class SyncManager(private val context: Context, private val drive: DriveServiceHelper) {

    // excludeFieldsWithoutExposeAnnotation(): chỉ field có @Expose mới vào metadata.json —
    // localImagePath là đường dẫn riêng từng máy, không được sync.
    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    private val eventDao = AppDatabase.get(context).eventDao()
    private val imageDao = AppDatabase.get(context).eventImageDao()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO + NonCancellable) {
        syncMutex.withLock { doSync() }
    }

    private suspend fun doSync(): SyncResult {
        val remoteJson = drive.downloadMetadataJson()
        val remotePayload = if (remoteJson != null) {
            gson.fromJson(remoteJson, SyncPayload::class.java)
        } else SyncPayload()

        val mergedEvents = mergeById(eventDao.getAllIncludingDeleted(), remotePayload.events ?: emptyList()) { it.id }
        val mergedImages = mergeById(imageDao.getAllIncludingDeleted(), remotePayload.images ?: emptyList()) { it.id }

        // 1) Xoá file Drive cho ảnh đã bị đánh dấu deleted nhưng vẫn còn driveFileId
        val afterRemoteDelete = mergedImages.map { image ->
            if (image.deleted && image.driveFileId != null) {
                runCatching { drive.deleteFile(image.driveFileId!!) }
                image.copy(driveFileId = null, driveThumbnailLink = null)
            } else image
        }

        // 2) Upload ảnh local mới (chưa từng lên Drive)
        val afterUpload = afterRemoteDelete.map { image ->
            if (!image.deleted && image.driveFileId == null && image.localImagePath != null) {
                val (fileId, link) = drive.uploadThumbnail(
                    context, image.id, Uri.fromFile(File(image.localImagePath!!))
                )
                image.copy(driveFileId = fileId, driveThumbnailLink = link)
            } else image
        }

        // 3) Tải ảnh về cache cho máy chưa có bản local; ghi nhận lỗi thay vì nuốt im lặng
        var downloadFailed = 0
        var firstError: String? = null
        val cacheDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val afterDownload = afterUpload.map { image ->
            if (!image.deleted && image.driveFileId != null && image.localImagePath == null) {
                val dest = File(cacheDir, "${image.id}.jpg")
                try {
                    drive.downloadFileTo(image.driveFileId!!, dest.absolutePath)
                    image.copy(localImagePath = dest.absolutePath)
                } catch (e: Exception) {
                    dest.delete() // tránh để lại file tải dở
                    downloadFailed++
                    if (firstError == null) firstError = e.message ?: e.javaClass.simpleName
                    image
                }
            } else image
        }

        eventDao.upsertAll(mergedEvents)
        imageDao.upsertAll(afterDownload)

        drive.uploadMetadataJson(gson.toJson(SyncPayload(mergedEvents, afterDownload)))
        return SyncResult(downloadFailed, firstError)
    }

    private fun <T> mergeById(local: List<T>, remote: List<T>, idOf: (T) -> String): List<T>
            where T : Any {
        val updatedAtOf: (T) -> Long = { item ->
            when (item) {
                is Event -> item.updatedAt
                is EventImage -> item.updatedAt
                else -> 0L
            }
        }
        val byId = LinkedHashMap<String, T>()
        for (item in remote) byId[idOf(item)] = item
        for (item in local) {
            val existing = byId[idOf(item)]
            if (existing == null || updatedAtOf(item) >= updatedAtOf(existing)) {
                byId[idOf(item)] = item
            }
        }
        return byId.values.toList()
    }

    companion object {
        private val syncMutex = Mutex()
    }
}
