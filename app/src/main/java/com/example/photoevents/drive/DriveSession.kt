package com.example.photoevents.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn

/**
 * Quản lý phiên làm việc với Google Drive.
 * Tái sử dụng DriveServiceHelper (và connection pool HTTP bên dưới) giữa các lần gọi sync
 * để tránh lãng phí thời gian bắt tay SSL/TLS và khởi tạo client liên tục.
 */
object DriveSession {
    private var cachedHelper: DriveServiceHelper? = null
    private var cachedAccountKey: String? = null

    @Synchronized
    fun getHelper(context: Context): DriveServiceHelper? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val googleAccount = account.account ?: return null
        val key = account.email ?: account.id ?: "default_account"

        if (cachedHelper != null && cachedAccountKey == key) {
            return cachedHelper
        }

        return DriveServiceHelper(context.applicationContext, googleAccount).also {
            cachedHelper = it
            cachedAccountKey = key
        }
    }

    @Synchronized
    fun clearSession() {
        cachedHelper = null
        cachedAccountKey = null
    }
}
