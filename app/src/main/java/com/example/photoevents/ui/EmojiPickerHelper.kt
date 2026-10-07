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

    val POPULAR_EMOJIS = listOf(
        "💖", "🌸", "✈️", "🏖️", "🏕️", "🎂", "🎉", "☕", "🌿", "👨‍👩‍👧", "💼", "⭐", "🔥", "✨", "🌟"
    )

    val ACTIVITY_EMOJIS = listOf(
        "🍜", "🍕", "🍣", "🍰", "🍻", "🏋️", "🚴", "⚽", "🎮", "🎬", "🎤", "🎨", "📚", "🛍️"
    )

    val LIFE_EMOJIS = listOf(
        "🌿", "🐾", "🚗", "🏠", "🌅", "🎁", "🏆", "💎", "💡", "📸", "🩺", "🎓", "💰", "💍", "🌻", "🏷️"
    )

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
        val chipActivities = view.findViewById<ChipGroup>(R.id.chipGroupEmojiActivities)
        val chipLife = view.findViewById<ChipGroup>(R.id.chipGroupEmojiLife)

        txtPreview?.text = currentEmoji.ifBlank { "🏷️" }
        txtSubtitle?.text = "Chọn biểu tượng đại diện cho danh mục '$categoryName'"

        btnClose?.setOnClickListener { sheet.dismiss() }

        fun addChipsToGroup(group: ChipGroup?, emojis: List<String>) {
            group?.removeAllViews()
            for (emoji in emojis) {
                val chip = Chip(context).apply {
                    text = emoji
                    isCheckable = false
                    textSize = 16f
                    setChipBackgroundColorResource(R.color.badge_pink_bg)
                    setOnClickListener {
                        txtPreview?.text = emoji
                        onEmojiSelected(emoji)
                        sheet.dismiss()
                    }
                }
                group?.addView(chip)
            }
        }

        addChipsToGroup(chipPopular, POPULAR_EMOJIS)
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
            txtPreview?.text = selected
            onEmojiSelected(selected)
            sheet.dismiss()
        }

        sheet.show()
    }
}
