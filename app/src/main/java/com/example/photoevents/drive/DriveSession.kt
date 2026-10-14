package com.example.photoevents.drive

import android.content.Context
import android.util.Log
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import com.google.android.gms.tasks.Tasks
import com.google.api.services.drive.DriveScopes
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
     * Lấy DriveServiceHelper hiện tại.
     * Thử lấy token/ủy quyền âm thầm (silent authorization) qua AuthorizationClient mà không làm phiền người dùng.
     */
    @Synchronized
    fun getHelper(context: Context): DriveServiceHelper? {
        if (cachedHelper != null) {
            return cachedHelper
        }

        return try {
            val authClient = Identity.getAuthorizationClient(context.applicationContext)
            val authRequest = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DriveScopes.DRIVE_APPDATA)))
                .build()

            // Chờ kết quả ủy quyền âm thầm tối đa 5 giây
            val result = Tasks.await(authClient.authorize(authRequest), 5, TimeUnit.SECONDS)
            if (result.hasResolution()) {
                // Cần tương tác người dùng (UI) -> không thể tự động lấy token âm thầm lúc này
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
}
