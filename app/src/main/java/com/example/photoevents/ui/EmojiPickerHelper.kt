package com.example.photoevents.ui

import android.content.Context
import android.view.LayoutInflater
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.photoevents.R
import com.example.photoevents.data.CategoryHelper

object EmojiPickerHelper {

    /**
     * Danh sách biểu tượng emoji đề xuất hiển thị nhanh trên các thanh chip gợi ý (sheet tạo/sửa danh mục).
     * Bao gồm hoa anh đào 🌸, icon chính thức của simple-notes-app, kỷ niệm, showbiz/giải trí.
     */
    val SUGGESTED_EMOJIS = listOf(
        "💖", "🌸", CategoryHelper.ICON_SIMPLE_NOTES, "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️",
        "🎬", "🎤", "🎵", "🎸", "💃", "🎭", "🌟", "📸", "🎟️", "👑", "🏆", "✨", "🍿",
        "🍜", "🏋️", "🛍️", "🐾", "🎨", "☘️", "🍀", "📝", "📓"
    )

    val POPULAR_EMOJIS = listOf(
        "💖", "🌸", CategoryHelper.ICON_SIMPLE_NOTES, "✈️", "🏖️", "🏕️", "🎂", "🎉", "☕", "🌿", "👨‍👩‍👧", "💼", "⭐", "🔥", "✨", "🌟", "👑", "🏆"
    )

    val SHOWBIZ_EMOJIS = listOf(
        "🎤", "🎬", "🎵", "🎸", "🎹", "💃", "🕺", "🎭", "🌟", "📸", "🎟️", "👑", "🏆", "✨", "🍿", "🎪", "📻", "🎙️", "🪄", "🪩"
    )

    val ACTIVITY_EMOJIS = listOf(
        "🍜", "🍕", "🍣", "🍰", "🍻", "🏋️", "🚴", "⚽", "🎮", "🎨", "📚", "🛍️", "🎯", "🎳"
    )

    val LIFE_EMOJIS = listOf(
        CategoryHelper.ICON_SIMPLE_NOTES, "🌿", "☘️", "🍀", "🪷", "🦋", "🐾", "🚗", "🏠", "🌅", "🎁", "💎", "💡", "🩺", "🎓", "💰", "💍", "🌻", "📝", "📓", "✍️", "🏷️"
    )

    fun createChip(context: Context, emoji: String, onClick: () -> Unit): Chip {
        return Chip(context).apply {
            if (CategoryHelper.isCustomIcon(emoji)) {
                text = ""
                chipIcon = androidx.core.content.ContextCompat.getDrawable(context, R.drawable.ic_launcher_simple_notes)
                isChipIconVisible = true
                val size = 24f * resources.displayMetrics.density
                chipIconSize = size
            } else {
                text = emoji
                isChipIconVisible = false
            }
            isCheckable = false
            textSize = 15f
            setChipBackgroundColorResource(R.color.badge_pink_bg)
            setOnClickListener { onClick() }
        }
    }

    /**
     * Hiển thị Bottom Sheet chọn biểu tượng Emoji cho một danh mục.
     */
    fun showEmojiPicker(
        context: Context,
        categoryName: String,
        currentEmoji: String,
        onEmojiSelected: (String) -> Unit
    ) {
        val sheet = BottomSheetDialog(context)
        val view = LayoutInflater.from(context).inflate(R.layout.sheet_change_emoji, null)
        sheet.setContentView(view)

        val txtPreview = view.findViewById<TextView>(R.id.txtEmojiPickerPreview)
        val txtSubtitle = view.findViewById<TextView>(R.id.txtEmojiPickerSubtitle)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseEmojiSheet)
        val edtCustom = view.findViewById<EditText>(R.id.edtCustomEmojiInput)
        val btnApply = view.findViewById<MaterialButton>(R.id.btnApplyCustomEmoji)

        val chipPopular = view.findViewById<ChipGroup>(R.id.chipGroupEmojiPopular)
        val chipShowbiz = view.findViewById<ChipGroup>(R.id.chipGroupEmojiShowbiz)
        val chipActivities = view.findViewById<ChipGroup>(R.id.chipGroupEmojiActivities)
        val chipLife = view.findViewById<ChipGroup>(R.id.chipGroupEmojiLife)

        CategoryHelper.renderIcon(txtPreview, currentEmoji.ifBlank { "🏷️" })
        txtSubtitle?.text = "Chọn biểu tượng đại diện cho danh mục '$categoryName'"

        btnClose?.setOnClickListener { sheet.dismiss() }

        fun addChipsToGroup(group: ChipGroup?, emojis: List<String>) {
            group?.removeAllViews()
            for (emoji in emojis) {
                val chip = createChip(context, emoji) {
                    CategoryHelper.renderIcon(txtPreview, emoji)
                    onEmojiSelected(emoji)
                    sheet.dismiss()
                }
                group?.addView(chip)
            }
        }

        addChipsToGroup(chipPopular, POPULAR_EMOJIS)
        addChipsToGroup(chipShowbiz, SHOWBIZ_EMOJIS)
        addChipsToGroup(chipActivities, ACTIVITY_EMOJIS)
        addChipsToGroup(chipLife, LIFE_EMOJIS)

        btnApply?.setOnClickListener {
            val input = edtCustom?.text?.toString()?.trim().orEmpty()
            if (input.isBlank()) {
                Toast.makeText(context, "Vui lòng nhập biểu tượng emoji", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (extractedIcon, _) = CategoryHelper.extractIconAndName(input)
            val selected = if (extractedIcon.isNotBlank() && extractedIcon != "🌸" && extractedIcon != "🏷️") {
                extractedIcon
            } else {
                input.take(4).trim()
            }
            CategoryHelper.renderIcon(txtPreview, selected)
            onEmojiSelected(selected)
            sheet.dismiss()
        }

        sheet.show()
    }
}
