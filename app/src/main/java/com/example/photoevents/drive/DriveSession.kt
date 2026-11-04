package com.example.photoevents.drive

import android.content.Context
import android.os.Looper
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.client.googleapis.json.GoogleJsonResponseException
import com.google.api.services.drive.DriveScopes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Quản lý phiên làm việc với Google Drive sử dụng Google Identity Services (Authorization API).
 * Tái sử dụng DriveServiceHelper (và connection pool HTTP bên dưới) giữa các lần gọi sync
 * để tránh lãng phí thời gian bắt tay SSL/TLS và khởi tạo client liên tục.
 */
object DriveSession {
    private const val TAG = "DriveSession"
    private var cachedHelper: DriveServiceHelper? = null
    private var cachedAccountKey: String? = null

    /**
     * Lấy DriveServiceHelper dạng suspend an toàn, luôn chạy trên Dispatchers.IO để không
     * vi phạm quy định của Google Tasks.await (Must not be called on the main thread).
     */
    suspend fun getHelperSuspend(context: Context): DriveServiceHelper? = withContext(Dispatchers.IO) {
        getHelperInternal(context.applicationContext)
    }

    /**
     * Lấy DriveServiceHelper hiện tại.
     * CẢNH BÁO: Nếu gọi trên Main Thread khi cachedHelper == null, Tasks.await sẽ ném IllegalStateException.
     * Do đó phương thức này chỉ trả về cache ngay lập tức nếu có, hoặc bắt buộc dùng getHelperSuspend / triggerSilentSync.
     */
    @Synchronized
    fun getHelper(context: Context): DriveServiceHelper? {
        if (cachedHelper != null) {
            return cachedHelper
        }
        if (Looper.myLooper() == Looper.getMainLooper()) {
            Log.w(TAG, "getHelper called on main thread with null cache. Use getHelperSuspend instead.")
            return null
        }
        return getHelperInternal(context.applicationContext)
    }

    @Synchronized
    private fun getHelperInternal(context: Context): DriveServiceHelper? {
        if (cachedHelper != null) {
            return cachedHelper
        }

        return try {
            val authClient = Identity.getAuthorizationClient(context.applicationContext)
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
                .build()

            // Chờ kết quả ủy quyền âm thầm tối đa 8 giây
            val result = Tasks.await(authClient.authorize(authRequest), 8, TimeUnit.SECONDS)
            if (result.hasResolution()) {
                null
            } else {
                val token = result.accessToken
                val account = result.toGoogleSignInAccount()
                val key = account?.email ?: account?.id ?: "authorized_account"
                if (!token.isNullOrEmpty()) {
                    setAccessToken(context, token, key)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            Log.d(TAG, "getHelper silent auth not available: ${e.message}")
            null
        }
    }

    @Synchronized
    fun setAccessToken(context: Context, accessToken: String, accountKey: String = "token_account"): DriveServiceHelper {
        return DriveServiceHelper(context.applicationContext, accessToken, accountKey).also {
            cachedHelper = it
            cachedAccountKey = accountKey
        }
    }

    @Synchronized
    fun clearSession() {
        cachedHelper = null
        cachedAccountKey = null
    }

    /**
     * Bắn sync ngầm an toàn từ bất kỳ Activity nào:
     * - Luôn chạy trên Dispatchers.IO qua SyncScope.
     * - Tự động lấy token âm thầm hoặc refresh token nếu cần.
     * - Tự động xóa session cache nếu gặp lỗi 401 Unauthorized để lần sync kế tiếp lấy token mới.
     */
    fun triggerSilentSync(context: Context, onComplete: ((SyncResult) -> Unit)? = null) {
        val appContext = context.applicationContext
        SyncScope.scope.launch(Dispatchers.IO) {
            val helper = getHelperSuspend(appContext) ?: return@launch
            try {
                val result = SyncManager(appContext, helper).sync()
                onComplete?.invoke(result)
            } catch (e: Exception) {
                Log.w(TAG, "triggerSilentSync failed: ${e.message}")
                if (e is GoogleJsonResponseException && e.statusCode == 401) {
                    clearSession()
                }
            }
        }
    }
}
