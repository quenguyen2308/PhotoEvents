package com.example.photoevents.ui

import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.view.View
import android.widget.ImageView
import com.example.photoevents.R
import com.example.photoevents.util.AppTheme
import com.example.photoevents.util.ThemeHelper
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView

object ThemeSwitcherBottomSheet {

    fun show(activity: Activity) {
        val dialog = BottomSheetDialog(activity)
        val view = activity.layoutInflater.inflate(R.layout.sheet_switch_theme, null)
        dialog.setContentView(view)

        val cardSakura = view.findViewById<MaterialCardView>(R.id.cardThemeSakura)
        val cardChest = view.findViewById<MaterialCardView>(R.id.cardThemeChest)
        val cardJournal = view.findViewById<MaterialCardView>(R.id.cardThemeJournal)

        val radioSakura = view.findViewById<ImageView>(R.id.imgRadioSakura)
        val radioChest = view.findViewById<ImageView>(R.id.imgRadioChest)
        val radioJournal = view.findViewById<ImageView>(R.id.imgRadioJournal)

        val btnApply = view.findViewById<MaterialButton>(R.id.btnApplyTheme)
        val btnClose = view.findViewById<View>(R.id.btnCloseThemeSheet)

        var selectedTheme = ThemeHelper.getSavedTheme(activity)

        fun updateUI(theme: AppTheme) {
            selectedTheme = theme
            val density = activity.resources.displayMetrics.density
            val outlineColor = ThemeHelper.getColorAttr(activity, com.google.android.material.R.attr.colorOutline)

            fun bindCard(card: MaterialCardView?, radio: ImageView?, isSelected: Boolean, colorHex: String) {
                if (card == null || radio == null) return
                if (isSelected) {
                    val color = Color.parseColor(colorHex)
                    card.strokeColor = color
                    card.strokeWidth = (2.2f * density).toInt()
                    radio.visibility = View.VISIBLE
                    radio.setColorFilter(color)
                } else {
                    card.strokeColor = outlineColor
                    card.strokeWidth = (1.2f * density).toInt()
                    radio.visibility = View.INVISIBLE
                }
            }

            bindCard(cardSakura, radioSakura, theme == AppTheme.SAKURA, AppTheme.SAKURA.primaryColorHex)
            bindCard(cardChest, radioChest, theme == AppTheme.MEMORY_CHEST, AppTheme.MEMORY_CHEST.primaryColorHex)
            bindCard(cardJournal, radioJournal, theme == AppTheme.MEMORY_JOURNAL, AppTheme.MEMORY_JOURNAL.primaryColorHex)

            btnApply?.backgroundTintList = ColorStateList.valueOf(Color.parseColor(theme.primaryColorHex))
        }

        updateUI(selectedTheme)

        cardSakura?.setOnClickListener { updateUI(AppTheme.SAKURA) }
        cardChest?.setOnClickListener { updateUI(AppTheme.MEMORY_CHEST) }
        cardJournal?.setOnClickListener { updateUI(AppTheme.MEMORY_JOURNAL) }

        btnApply?.setOnClickListener {
            dialog.dismiss()
            ThemeHelper.switchThemeAndIcon(activity, selectedTheme)
        }

        btnClose?.setOnClickListener { dialog.dismiss() }

        dialog.show()
    }
}
