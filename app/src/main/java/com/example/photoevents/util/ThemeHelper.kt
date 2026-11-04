package com.example.photoevents.util

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.TypedValue
import androidx.annotation.AttrRes
import androidx.annotation.ColorInt
import com.example.photoevents.R

enum class AppTheme(
    val key: String,
    val styleResId: Int,
    val aliasName: String,
    val iconResId: Int,
    val title: String,
    val description: String,
    val badgeText: String,
    val primaryColorHex: String
) {
    SAKURA(
        key = "sakura",
        styleResId = R.style.Theme_PhotoEvents_Sakura,
        aliasName = "com.example.photoevents.MainActivitySakura",
        iconResId = R.drawable.ic_launcher_sakura,
        title = "Sakura Blossom",
        description = "Thanh lịch, hoa anh đào, camera phẳng hiện đại.",
        badgeText = "Mặc định",
        primaryColorHex = "#FF6584"
    ),
    MEMORY_CHEST(
        key = "memory_chest",
        styleResId = R.style.Theme_PhotoEvents_MemoryChest,
        aliasName = "com.example.photoevents.MainActivityMemoryChest",
        iconResId = R.drawable.ic_launcher_memory_chest,
        title = "Memory Chest 3D",
        description = "Rương kỷ vật hồng viền đồng thau, khung bento may chỉ nổi (Stitched Scrapbook).",
        badgeText = "Mới",
        primaryColorHex = "#F0728F"
    ),
    MEMORY_JOURNAL(
        key = "memory_journal",
        styleResId = R.style.Theme_PhotoEvents_MemoryJournal,
        aliasName = "com.example.photoevents.MainActivityMemoryJournal",
        iconResId = R.drawable.ic_launcher_memory_journal,
        title = "Memory Journal 3D",
        description = "Sổ tay xanh mint aqua, khung bento dải thước phim 35mm hoài niệm.",
        badgeText = "Mới",
        primaryColorHex = "#2A9D8F"
    );

    companion object {
        fun fromKey(key: String?): AppTheme {
            return entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: SAKURA
        }
    }
}

object ThemeHelper {
    private const val PREF_NAME = "theme_prefs"
    private const val KEY_THEME = "selected_theme"

    private var isLifecycleRegistered = false
    private var startedActivitiesCount = 0
    private var pendingCleanupNeeded = false

    fun getSavedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_THEME, AppTheme.SAKURA.key)
        return AppTheme.fromKey(key)
    }

    /**
     * Bắt buộc gọi trước super.onCreate() trong tất cả các Activity
     */
    fun applyTheme(activity: Activity) {
        registerLifecycleIfNeeded(activity.application)
        val theme = getSavedTheme(activity)
        activity.setTheme(theme.styleResId)
    }

    private fun registerLifecycleIfNeeded(application: Application) {
        if (isLifecycleRegistered) return
        isLifecycleRegistered = true

        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {
                startedActivitiesCount++
            }
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {
                startedActivitiesCount = maxOf(0, startedActivitiesCount - 1)
                // Khi người dùng thực sự rời khỏi app (không phải do recreate đổi theme):
                if (!activity.isChangingConfigurations && startedActivitiesCount == 0 && pendingCleanupNeeded) {
                    cleanUpOldAliases(activity.applicationContext)
                }
            }
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    private fun cleanUpOldAliases(context: Context) {
        val currentTheme = getSavedTheme(context)
        val pm = context.packageManager
        val packageName = context.packageName

        for (other in AppTheme.entries) {
            if (other != currentTheme) {
                val disableComponent = ComponentName(packageName, other.aliasName)
                try {
                    pm.setComponentEnabledSetting(
                        disableComponent,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                } catch (e: Exception) {
                    // Bỏ qua nếu đã tắt hoặc lỗi không nghiêm trọng
                }
            }
        }
        pendingCleanupNeeded = false
    }

    /**
     * Lấy màu sắc động tương ứng với attribute của Theme hiện tại (ví dụ: ?attr/colorPrimary)
     */
    @ColorInt
    fun getColorAttr(context: Context, @AttrRes attrResId: Int): Int {
        val typedValue = TypedValue()
        context.theme.resolveAttribute(attrResId, typedValue, true)
        return typedValue.data
    }

    /**
     * Chuyển đổi Theme và Icon Launcher đồng bộ:
     * - Lưu theme mới và kích hoạt alias mới ngay lập tức.
     * - KHÔNG tắt alias cũ trong lúc app đang chạy để tránh Android OS kill task làm app bị văng ra Home Screen.
     * - Recreate Activity mượt mà để giao diện đổi theme ngay lập tức mà người dùng vẫn ở nguyên trong ứng dụng.
     * - Việc tắt alias cũ được trì hoãn an toàn khi người dùng thoát app ra màn hình chính (onActivityStopped).
     */
    fun switchThemeAndIcon(activity: Activity, targetTheme: AppTheme) {
        val currentTheme = getSavedTheme(activity)
        if (currentTheme == targetTheme) return

        registerLifecycleIfNeeded(activity.application)

        // 1. Lưu theme được chọn vào SharedPreferences
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, targetTheme.key)
            .apply()

        // 2. Kích hoạt activity-alias của Launcher Icon mới
        val pm = activity.packageManager
        val packageName = activity.packageName

        val enableComponent = ComponentName(packageName, targetTheme.aliasName)
        pm.setComponentEnabledSetting(
            enableComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Đánh dấu cần dọn dẹp các alias cũ khi người dùng rời app
        pendingCleanupNeeded = true

        // 3. Tải lại Activity hiện tại để áp dụng theme mới ngay lập tức, giữ người dùng ở nguyên trong ứng dụng
        activity.recreate()
    }
}
