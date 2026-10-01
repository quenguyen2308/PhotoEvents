package com.example.photoevents.drive

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignIn

/** Truy cập nhanh DriveServiceHelper dựa trên tài khoản Google đã đăng nhập, dùng chung cho mọi Activity. */
object DriveSession {
    fun getHelper(context: Context): DriveServiceHelper? {
        val account = GoogleSignIn.getLastSignedInAccount(context) ?: return null
        val googleAccount = account.account ?: return null
        return DriveServiceHelper(context, googleAccount)
    }
}
