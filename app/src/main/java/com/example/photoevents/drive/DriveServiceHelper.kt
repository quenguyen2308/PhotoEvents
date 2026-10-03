package com.example.photoevents.drive

import android.accounts.Account
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.google.api.client.googleapis.extensions.android.gms.auth.GoogleAccountCredential
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.client.http.ByteArrayContent
import com.google.api.client.http.javanet.NetHttpTransport
import com.google.api.client.json.gson.GsonFactory
import com.google.api.services.drive.Drive
import com.google.api.services.drive.DriveScopes
import com.google.api.services.drive.model.File as DriveFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.FileOutputStream

private const val APPDATA_FOLDER = "appDataFolder"
private const val METADATA_FILE_NAME = "metadata.json"
const val THUMBNAIL_MAX_SIZE = 512 // px, cạnh dài nhất

class DriveServiceHelper(context: Context, account: Account) {

    private val drive: Drive
    private val prefs = context.getSharedPreferences("drive_appdata_cache_${account.name}", Context.MODE_PRIVATE)

    @Volatile
    private var cachedMetadataFileId: String? = prefs.getString("metadata_file_id", null)

    init {
        val credential = GoogleAccountCredential.usingOAuth2(
            context, listOf(DriveScopes.DRIVE_APPDATA)
        ).apply { selectedAccount = account }

        drive = Drive.Builder(
            NetHttpTransport(),
            GsonFactory.getDefaultInstance(),
            credential
        ).setApplicationName("PhotoEvents").build()
    }

    private fun saveMetadataFileId(id: String?) {
        cachedMetadataFileId = id
        prefs.edit().putString("metadata_file_id", id).apply()
    }

    fun invalidateCache() {
        saveMetadataFileId(null)
    }

    /**
     * Trả về alias folder appDataFolder trên Google Drive.
     * appDataFolder là thư mục ẩn riêng biệt của ứng dụng, không xuất hiện trên giao diện
     * Google Drive của người dùng, giúp bảo mật và chống hoàn toàn việc bị người dùng xóa nhầm.
     */
    fun getOrCreateAppFolderId(): String = APPDATA_FOLDER

    /**
     * Resize ảnh và upload làm thumbnail cho 1 ảnh (imageId) trực tiếp vào appDataFolder ẩn.
     * Chỉ thực hiện 1 request create kèm setFields("id, webContentLink").
     */
    suspend fun uploadThumbnail(context: Context, imageId: String, sourceUri: Uri): Pair<String, String?> =
        withContext(Dispatchers.IO) {
            val bitmap = decodeSampledBitmap(context, sourceUri, THUMBNAIL_MAX_SIZE)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 80, out)
                out.toByteArray()
            }

            val fileName = "$imageId.jpg"
            val content = ByteArrayContent("image/jpeg", bytes)
            val meta = DriveFile().apply {
                name = fileName
                parents = listOf(APPDATA_FOLDER)
            }

            val created = drive.files().create(meta, content)
                .setFields("id, webContentLink")
                .execute()
            created.id to created.webContentLink
        }

    /** Xoá hẳn 1 file (vd. thumbnail) trên Drive. Bỏ qua lỗi nếu file đã không còn tồn tại. */
    suspend fun deleteFile(fileId: String): Unit = withContext(Dispatchers.IO) {
        try {
            drive.files().delete(fileId).execute()
        } catch (e: GoogleJsonResponseException) {
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

    /**
     * Đọc metadata.json hiện có trên Drive trong appDataFolder.
     * Sử dụng cachedMetadataFileId để tải thẳng (1 request duy nhất) thay vì list query 2 lần.
     */
    suspend fun downloadMetadataJson(): String? = withContext(Dispatchers.IO) {
        val fileId = cachedMetadataFileId
        if (fileId != null) {
            try {
                val out = ByteArrayOutputStream()
                drive.files().get(fileId).executeMediaAndDownloadTo(out)
                return@withContext out.toString("UTF-8")
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 404) {
                    saveMetadataFileId(null)
                } else {
                    throw e
                }
            }
        }

        // Nếu chưa cache hoặc fileId cũ bị 404, tìm kiếm trong appDataFolder
        val existing = drive.files().list()
            .setSpaces(APPDATA_FOLDER)
            .setQ("name = '$METADATA_FILE_NAME' and trashed = false")
            .setFields("files(id)")
            .execute().files?.firstOrNull() ?: return@withContext null

        saveMetadataFileId(existing.id)
        val out = ByteArrayOutputStream()
        drive.files().get(existing.id).executeMediaAndDownloadTo(out)
        out.toString("UTF-8")
    }

    /**
     * Ghi đè metadata.json trên Drive trong appDataFolder bằng dữ liệu đã merge.
     * Sử dụng cachedMetadataFileId để update thẳng 1 request duy nhất.
     */
    suspend fun uploadMetadataJson(json: String): Unit = withContext(Dispatchers.IO) {
        val content = ByteArrayContent("application/json", json.toByteArray())
        val fileId = cachedMetadataFileId

        if (fileId != null) {
            try {
                drive.files().update(fileId, null, content).execute()
                return@withContext
            } catch (e: GoogleJsonResponseException) {
                if (e.statusCode == 404) {
                    saveMetadataFileId(null)
                } else {
                    throw e
                }
            }
        }

        val existing = drive.files().list()
            .setSpaces(APPDATA_FOLDER)
            .setQ("name = '$METADATA_FILE_NAME' and trashed = false")
            .setFields("files(id)")
            .execute().files?.firstOrNull()

        if (existing != null) {
            saveMetadataFileId(existing.id)
            drive.files().update(existing.id, null, content).execute()
        } else {
            val meta = DriveFile().apply {
                name = METADATA_FILE_NAME
                parents = listOf(APPDATA_FOLDER)
            }
            val created = drive.files().create(meta, content).setFields("id").execute()
            saveMetadataFileId(created.id)
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
