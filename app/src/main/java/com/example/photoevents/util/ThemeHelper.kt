package com.example.photoevents.util

import android.app.Activity
import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
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
        description = "Ấm cúng, rương gỗ hồng viền đồng thau cổ, polaroid & icon rương 3D.",
        badgeText = "Mới",
        primaryColorHex = "#F0728F"
    ),
    MEMORY_JOURNAL(
        key = "memory_journal",
        styleResId = R.style.Theme_PhotoEvents_MemoryJournal,
        aliasName = "com.example.photoevents.MainActivityMemoryJournal",
        iconResId = R.drawable.ic_launcher_memory_journal,
        title = "Memory Journal 3D",
        description = "Sổ tay xanh mint aqua, gáy lò xo kim loại, kẹp giấy & nhãn Memories.",
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

    fun getSavedTheme(context: Context): AppTheme {
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = prefs.getString(KEY_THEME, AppTheme.SAKURA.key)
        return AppTheme.fromKey(key)
    }

    /**
     * Bắt buộc gọi trước super.onCreate() trong tất cả các Activity
     */
    fun applyTheme(activity: Activity) {
        val theme = getSavedTheme(activity)
        activity.setTheme(theme.styleResId)
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
     * Chuyển đổi Theme và Icon Launcher đồng bộ qua activity-alias
     */
    fun switchThemeAndIcon(activity: Activity, targetTheme: AppTheme) {
        val currentTheme = getSavedTheme(activity)
        if (currentTheme == targetTheme) return

        // 1. Lưu theme được chọn vào SharedPreferences
        activity.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_THEME, targetTheme.key)
            .apply()

        // 2. Chuyển đổi activity-alias của Launcher Icon
        val pm = activity.packageManager
        val packageName = activity.packageName

        // Nguyên tắc an toàn: Bật alias mới trước để tránh mất icon trên màn hình chính
        val enableComponent = ComponentName(packageName, targetTheme.aliasName)
        pm.setComponentEnabledSetting(
            enableComponent,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )

        // Tắt các alias còn lại
        for (other in AppTheme.entries) {
            if (other != targetTheme) {
                val disableComponent = ComponentName(packageName, other.aliasName)
                pm.setComponentEnabledSetting(
                    disableComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
        }

        // 3. Tải lại Activity để áp dụng theme mới mượt mà
        activity.recreate()
    }
}
