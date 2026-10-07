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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/** Toàn bộ nội dung ghi vào metadata.json trên Drive: danh sách sự kiện + danh sách ảnh + danh sách danh mục. */
private data class SyncPayload(
    @Expose val events: List<Event>? = null,
    @Expose val images: List<EventImage>? = null,
    @Expose val categories: List<String>? = null
)

/** Kết quả 1 lần sync: số ảnh chưa tải về được (sẽ tự thử lại ở lần sync sau) và lỗi đầu tiên gặp phải. */
data class SyncResult(val downloadFailed: Int = 0, val firstError: String? = null)

/**
 * Đồng bộ 2 chiều kiểu "last-write-wins" theo updatedAt:
 * 1. Tải metadata.json hiện có trên Drive (sử dụng cache file ID trực tiếp, 1 request).
 * 2. Merge events và images. CẬP NHẬT ROOM NGAY LẬP TỨC để UI nhận dữ liệu tức thì.
 * 3. Xoá file Drive cho ảnh đã bị soft-delete (chạy song song).
 * 4. Upload ảnh local mới lên Drive (chạy song song).
 * 5. Tải ảnh thumbnail về local cache cho ảnh chưa có file cục bộ (chạy song song).
 * 6. Chỉ tải lên metadata.json mới nếu thực sự CÓ THAY ĐỔI (bỏ qua nếu không đổi, giúp pull-to-refresh cực nhanh).
 */
class SyncManager(private val context: Context, private val drive: DriveServiceHelper) {

    private val gson = GsonBuilder().excludeFieldsWithoutExposeAnnotation().create()
    private val eventDao = AppDatabase.get(context).eventDao()
    private val imageDao = AppDatabase.get(context).eventImageDao()

    suspend fun sync(): SyncResult = withContext(Dispatchers.IO + NonCancellable) {
        syncMutex.withLock { doSync() }
    }

    private suspend fun doSync(): SyncResult {
        val prefs = context.getSharedPreferences("sync_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PREF_MIGRATED_TO_APPDATA, false)) {
            // Khi chuyển sang appDataFolder an toàn, các fileId cũ trên My Drive không còn nằm trong appDataFolder.
            // Reset driveFileId cho các ảnh local có sẵn file trên máy để được tự động upload vào appDataFolder ẩn mới.
            val allImages = imageDao.getAllIncludingDeleted()
            val resetImages = allImages.map { img ->
                val hasLocalFile = img.localImagePath?.let { path -> File(path).exists() } == true
                if (hasLocalFile && img.driveFileId != null) {
                    img.copy(driveFileId = null, driveThumbnailLink = null)
                } else img
            }
            imageDao.upsertAll(resetImages)
            prefs.edit().putBoolean(PREF_MIGRATED_TO_APPDATA, true).apply()
        }

        val remoteJson = drive.downloadMetadataJson()
        val remotePayload = if (remoteJson != null) {
            gson.fromJson(remoteJson, SyncPayload::class.java)
        } else SyncPayload()

        val localEvents = eventDao.getAllIncludingDeleted()
        val localImages = imageDao.getAllIncludingDeleted()
        val remoteEvents = remotePayload.events ?: emptyList()
        val remoteImages = remotePayload.images ?: emptyList()

        val mergedEvents = mergeById(localEvents, remoteEvents) { it.id }
        val mergedImages = mergeById(localImages, remoteImages) { it.id }

        // Cập nhật Room sớm: UI lập tức nhận được các sự kiện và metadata ảnh mới mà không phải đợi tải xong toàn bộ file
        eventDao.upsertAll(mergedEvents)
        imageDao.upsertAll(mergedImages)

        // Đồng bộ danh mục 2 chiều giữa local, remote Drive và danh mục trên sự kiện
        val localCategories = com.example.photoevents.data.CategoryHelper.getAvailableCategories(context)
        val remoteCategories = remotePayload.categories ?: emptyList()
        val eventCategories = mergedEvents.map { it.category.trim() }.filter { it.isNotEmpty() }
        val deletedCategories = com.example.photoevents.data.CategoryHelper.getDeletedCategories(context)

        val mergedMap = linkedMapOf<String, String>()
        // 1. Ưu tiên localCategories trước (giữ icon mới nhất mà người dùng vừa chọn ở máy này)
        for (cat in localCategories) {
            val (_, clean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(cat)
            if (clean.isNotBlank() && !deletedCategories.contains(clean.lowercase())) {
                mergedMap[clean.lowercase()] = com.example.photoevents.data.CategoryHelper.formatStandard(cat)
            }
        }
        // 2. Bổ sung từ remote Drive nếu local chưa có danh mục này
        for (cat in remoteCategories) {
            val (_, clean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(cat)
            val key = clean.lowercase()
            if (clean.isNotBlank() && !deletedCategories.contains(key) && !mergedMap.containsKey(key)) {
                mergedMap[key] = com.example.photoevents.data.CategoryHelper.formatStandard(cat)
            }
        }
        // 3. Bổ sung từ các sự kiện nếu chưa có danh mục này
        for (cat in eventCategories) {
            val (_, clean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(cat)
            val key = clean.lowercase()
            if (clean.isNotBlank() && !deletedCategories.contains(key) && !mergedMap.containsKey(key)) {
                mergedMap[key] = com.example.photoevents.data.CategoryHelper.formatStandard(cat)
            }
        }
        com.example.photoevents.data.CategoryHelper.saveCustomCategories(context, mergedMap.values)

        // 1) Xoá file Drive cho ảnh đã bị đánh dấu deleted nhưng vẫn còn driveFileId (chạy song song)
        val imagesToDeleteOnDrive = mergedImages.filter { it.deleted && it.driveFileId != null }
        if (imagesToDeleteOnDrive.isNotEmpty()) {
            coroutineScope {
                imagesToDeleteOnDrive.map { image ->
                    async(Dispatchers.IO) {
                        runCatching { drive.deleteFile(image.driveFileId!!) }
                    }
                }.awaitAll()
            }
        }
        val afterRemoteDelete = mergedImages.map { image ->
            if (image.deleted && image.driveFileId != null) {
                image.copy(driveFileId = null, driveThumbnailLink = null)
            } else image
        }

        // 2) Upload ảnh local mới (chưa từng lên Drive) - chạy song song
        val imagesToUpload = afterRemoteDelete.filter { !it.deleted && it.driveFileId == null && it.localImagePath != null }
        val uploadedResults = if (imagesToUpload.isNotEmpty()) {
            coroutineScope {
                imagesToUpload.map { image ->
                    async(Dispatchers.IO) {
                        try {
                            val (fileId, link) = drive.uploadThumbnail(
                                context, image.id, Uri.fromFile(File(image.localImagePath!!))
                            )
                            image.id to (fileId to link)
                        } catch (e: Exception) {
                            image.id to null
                        }
                    }
                }.awaitAll().toMap()
            }
        } else emptyMap()

        val afterUpload = afterRemoteDelete.map { image ->
            val uploadInfo = uploadedResults[image.id]
            if (uploadInfo != null) {
                image.copy(driveFileId = uploadInfo.first, driveThumbnailLink = uploadInfo.second)
            } else image
        }

        // 3) Tải ảnh về cache cho máy chưa có bản local (chạy song song)
        var downloadFailed = 0
        var firstError: String? = null
        val cacheDir = File(context.filesDir, "thumbnails").apply { mkdirs() }
        val imagesToDownload = afterUpload.filter { !it.deleted && it.driveFileId != null && it.localImagePath == null }

        val downloadResults = if (imagesToDownload.isNotEmpty()) {
            coroutineScope {
                imagesToDownload.map { image ->
                    async(Dispatchers.IO) {
                        val dest = File(cacheDir, "${image.id}.jpg")
                        try {
                            drive.downloadFileTo(image.driveFileId!!, dest.absolutePath)
                            image.id to dest.absolutePath
                        } catch (e: Exception) {
                            dest.delete()
                            image.id to null
                        }
                    }
                }.awaitAll().toMap()
            }
        } else emptyMap()

        imagesToDownload.forEach { image ->
            if (downloadResults[image.id] == null) {
                downloadFailed++
                if (firstError == null) firstError = "Lỗi tải ảnh ${image.id}"
            }
        }

        val afterDownload = afterUpload.map { image ->
            val downloadedPath = downloadResults[image.id]
            if (downloadedPath != null) {
                image.copy(localImagePath = downloadedPath)
            } else image
        }

        // Ghi lại Room nếu có ảnh vừa upload hoặc download thành công
        if (imagesToUpload.isNotEmpty() || downloadResults.isNotEmpty() || imagesToDeleteOnDrive.isNotEmpty()) {
            imageDao.upsertAll(afterDownload)
        }

        // 4) Chỉ tải lên metadata.json nếu thực sự có thay đổi so với remote
        val newPayload = SyncPayload(mergedEvents, afterDownload, mergedMap.values.toList())
        val newJson = gson.toJson(newPayload)
        val hasChanges = remoteJson == null ||
                imagesToDeleteOnDrive.isNotEmpty() ||
                imagesToUpload.isNotEmpty() ||
                newJson != remoteJson

        if (hasChanges) {
            drive.uploadMetadataJson(newJson)
        }

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
                if (item is EventImage && existing is EventImage) {
                    if (item.driveFileId == null && existing.driveFileId != null) {
                        item.driveFileId = existing.driveFileId
                        item.driveThumbnailLink = existing.driveThumbnailLink
                    }
                }
                byId[idOf(item)] = item
            }
        }
        return byId.values.toList()
    }

    companion object {
        private val syncMutex = Mutex()
        private const val PREF_MIGRATED_TO_APPDATA = "migrated_to_appdata_v1"
    }
}
