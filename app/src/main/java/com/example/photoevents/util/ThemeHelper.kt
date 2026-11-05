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

    private var isInitialAliasVerified = false

    fun getSavedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_THEME, AppTheme.SAKURA.key)
        return AppTheme.fromKey(key)
    }

    /**
     * Bắt buộc gọi trước super.onCreate() trong tất cả các Activity
     */
    fun applyTheme(activity: Activity) {
        ensureOnlyActiveAliasEnabled(activity.applicationContext)
        val theme = getSavedTheme(activity)
        activity.setTheme(theme.styleResId)
    }

    /**
     * Đảm bảo trên hệ điều hành chỉ có duy nhất 1 activity-alias của Theme hiện tại được bật (ENABLED).
     * Tất cả các alias khác đều phải DISABLED.
     * Tự động dọn dẹp các alias thừa/rác phát sinh từ các phiên bản trước đó trên launcher Samsung.
     */
    private fun ensureOnlyActiveAliasEnabled(context: Context) {
        if (isInitialAliasVerified) return
        isInitialAliasVerified = true

        val currentTheme = getSavedTheme(context)
        val pm = context.packageManager
        val packageName = context.packageName

        try {
            for (other in AppTheme.entries) {
                if (other != currentTheme) {
                    val comp = ComponentName(packageName, other.aliasName)
                    if (pm.getComponentEnabledSetting(comp) != PackageManager.COMPONENT_ENABLED_STATE_DISABLED) {
                        pm.setComponentEnabledSetting(
                            comp,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                            PackageManager.DONT_KILL_APP
                        )
                    }
                }
            }
            val targetComp = ComponentName(packageName, currentTheme.aliasName)
            if (pm.getComponentEnabledSetting(targetComp) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                pm.setComponentEnabledSetting(
                    targetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        } catch (e: Exception) {
            // Bỏ qua lỗi PackageManager
        }
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
     * Chuyển đổi Theme và Icon Launcher đồng bộ theo Hướng 2:
     * - Tắt (DISABLE) toàn bộ activity-alias của các theme khác TRƯỚC.
     * - Bật (ENABLE) activity-alias của targetTheme SAU.
     * - Tuyệt đối không để xảy ra thời điểm có 2 alias cùng ENABLED để Samsung One UI không chèn thêm icon mới vào App Drawer.
     * - Recreate Activity hiện tại để giao diện đổi theme ngay lập tức.
     */
    fun switchThemeAndIcon(activity: Activity, targetTheme: AppTheme) {
        val currentTheme = getSavedTheme(activity)
        if (currentTheme == targetTheme) return

        // 1. Lưu theme được chọn vào SharedPreferences
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, targetTheme.key)
            .apply()

        // 2. Chuyển đổi Launcher Icon: Tắt tất cả alias cũ TRƯỚC, rồi mới BẬT alias mới SAU
        val pm = activity.packageManager
        val packageName = activity.packageName

        for (other in AppTheme.entries) {
            if (other != targetTheme) {
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

        val enableComponent = ComponentName(packageName, targetTheme.aliasName)
        try {
            pm.setComponentEnabledSetting(
                enableComponent,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
        } catch (e: Exception) {
            // Bỏ qua nếu lỗi không nghiêm trọng
        }

        // 3. Tải lại Activity hiện tại để áp dụng theme mới ngay lập tức
        activity.recreate()
    }
}
