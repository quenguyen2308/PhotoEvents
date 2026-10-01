package com.example.photoevents.drive

import android.accounts.Account
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

private const val APP_FOLDER_NAME = "PhotoEventsApp"
private const val METADATA_FILE_NAME = "metadata.json"
const val THUMBNAIL_MAX_SIZE = 512 // px, cạnh dài nhất

class DriveServiceHelper(context: Context, account: Account) {

    private val drive: Drive

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_FILE)
        ).apply { selectedAccount = account }

        drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("PhotoEvents").build()
    }

    /** Tìm hoặc tạo folder riêng của app trên Drive (chỉ app này thấy/đọc/ghi vì dùng scope drive.file). */
    private suspend fun getOrCreateAppFolderId(): String = withContext(Dispatchers.IO) {
        val query = "mimeType = 'application/vnd.google-apps.folder' and name = '$APP_FOLDER_NAME' and trashed = false"
        val result = drive.files().list().setQ(query).setSpaces("drive").execute()
        result.files?.firstOrNull()?.id ?: run {
            val meta = DriveFile().apply {
                name = APP_FOLDER_NAME
                mimeType = "application/vnd.google-apps.folder"
            }
            drive.files().create(meta).setFields("id").execute().id
        }
    }

    /** Resize ảnh và upload làm thumbnail cho 1 event. Trả về (fileId, webContentLink). */
    suspend fun uploadThumbnail(context: Context, eventId: String, sourceUri: Uri): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val folderId = getOrCreateAppFolderId()
            val bitmap = decodeSampledBitmap(context, sourceUri, THUMBNAIL_MAX_SIZE)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                out.toByteArray()
            }

            val fileName = "$eventId.jpg"
            // Nếu đã có thumbnail cho event này thì update thay vì tạo file mới
            val existing = drive.files().list()
                .setQ("name = '$fileName' and '$folderId' in parents and trashed = false")
                .setFields("files(id)")
                .execute().files?.firstOrNull()

            val content = com.google.api.client.http.ByteArrayContent("image/jpeg", bytes)

            val fileId = if (existing != null) {
                drive.files().update(existing.id, null, content).execute()
                existing.id
            } else {
                val meta = DriveFile().apply {
                    name = fileName
                    parents = listOf(folderId)
                }
                drive.files().create(meta, content).setFields("id").execute().id
            }

            val webContentLink = drive.files().get(fileId)
                .setFields("webContentLink").execute().webContentLink
            fileId to webContentLink
        }

    /** Xoá hẳn 1 file (vd. thumbnail) trên Drive. Bỏ qua lỗi nếu file đã không còn tồn tại. */
    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        try {
            drive.files().delete(fileId).execute()
        } catch (e: com.google.api.client.googleapis.json.GoogleJsonResponseException) {
            if (e.statusCode != 404) throw e
        }
    }

    /** Tải nội dung 1 file thumbnail về local, trả về đường dẫn file đã lưu. */
    suspend fun downloadFileTo(fileId: String, destPath: String): String = withContext(Dispatchers.IO) {
        FileOutputStream(destPath).use { out ->
            drive.files().get(fileId).executeMediaAndDownloadTo(out)
        }
        destPath
    }

    /** Đọc metadata.json hiện có trên Drive (danh sách event của mọi thiết bị). Null nếu chưa có. */
    suspend fun downloadMetadataJson(): String? = withContext(Dispatchers.IO) {
        val folderId = getOrCreateAppFolderId()
        val existing = drive.files().list()
            .setQ("name = '$METADATA_FILE_NAME' and '$folderId' in parents and trashed = false")
            .setFields("files(id)")
            .execute().files?.firstOrNull() ?: return@withContext null

        val out = ByteArrayOutputStream()
        drive.files().get(existing.id).executeMediaAndDownloadTo(out)
        out.toString("UTF-8")
    }

    /** Ghi đè metadata.json trên Drive bằng dữ liệu đã merge. */
    suspend fun uploadMetadataJson(json: String): Unit = withContext(Dispatchers.IO) {
        val folderId = getOrCreateAppFolderId()
        val existing = drive.files().list()
            .setQ("name = '$METADATA_FILE_NAME' and '$folderId' in parents and trashed = false")
            .setFields("files(id)")
            .execute().files?.firstOrNull()

        val content = com.google.api.client.http.ByteArrayContent("application/json", json.toByteArray())
        if (existing != null) {
            drive.files().update(existing.id, null, content).execute()
        } else {
            val meta = DriveFile().apply {
                name = METADATA_FILE_NAME
                parents = listOf(folderId)
            }
            drive.files().create(meta, content).execute()
        }
    }

    private fun decodeSampledBitmap(context: Context, uri: Uri, maxSize: Int): Bitmap {
        val input = context.contentResolver.openInputStream(uri)
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeStream(input, null, options)
        input?.close()

        var sample = 1
        while (options.outWidth / sample > maxSize * 2 || options.outHeight / sample > maxSize * 2) {
            sample *= 2
        }

        val input2 = context.contentResolver.openInputStream(uri)
        val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sample }
        val bitmap = BitmapFactory.decodeStream(input2, null, decodeOptions)!!
        input2?.close()

        val scale = maxSize.toFloat() / maxOf(bitmap.width, bitmap.height)
        return if (scale < 1f) {
            Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
        } else bitmap
    }
}
