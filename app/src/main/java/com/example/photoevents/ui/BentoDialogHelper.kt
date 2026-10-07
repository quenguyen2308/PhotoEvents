package com.example.photoevents.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.example.photoevents.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Helper hiển thị Dialog xác nhận phong cách Sakura Bento Floating Card.
 * Bo góc 24dp, icon Gradient kép nổi bật, Impact Card giải thích chuyển sự kiện an toàn,
 * và cặp nút bấm pill bo tròn (Tonal Huỷ + Gradient Đỏ/Hồng Xác nhận).
 */
object BentoDialogHelper {

    fun showConfirmDialog(
        context: Context,
        title: String,
        message: String,
        impactText: CharSequence? = null,
        confirmText: String = "Xoá danh mục",
        cancelText: String = "Huỷ bỏ",
        @DrawableRes iconRes: Int = R.drawable.ic_delete,
        isDanger: Boolean = true,
        onConfirm: () -> Unit
    ): AlertDialog {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_confirm_bento, null)

        val imgIcon = view.findViewById<ImageView>(R.id.imgBentoIcon)
        val layoutIconOuter = view.findViewById<View>(R.id.layoutBentoIconOuter)
        val layoutIcon = view.findViewById<View>(R.id.layoutBentoIcon)
        val txtTitle = view.findViewById<TextView>(R.id.txtBentoTitle)
        val txtMessage = view.findViewById<TextView>(R.id.txtBentoMessage)
        val layoutImpact = view.findViewById<View>(R.id.layoutBentoImpact)
        val txtImpact = view.findViewById<TextView>(R.id.txtBentoImpact)
        val imgImpactIcon = view.findViewById<ImageView>(R.id.imgBentoImpactIcon)
        val btnCancel = view.findViewById<MaterialButton>(android.R.id.button2)
        val btnConfirm = view.findViewById<MaterialButton>(android.R.id.button1)

        imgIcon.setImageResource(iconRes)
        txtTitle.text = title
        txtMessage.text = message

        if (!impactText.isNullOrBlank()) {
            layoutImpact.visibility = View.VISIBLE
            layoutImpact.setLayerType(View.LAYER_TYPE_SOFTWARE, null)
            txtImpact.text = impactText
        } else {
            layoutImpact.visibility = View.GONE
        }

        if (!isDanger) {
            layoutIconOuter.setBackgroundResource(R.drawable.bg_category_icon_circle)
            layoutIcon.setBackgroundResource(R.drawable.bg_circle_button)
            imgIcon.setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary))
            btnConfirm.setBackgroundResource(R.drawable.bg_btn_bento_primary)
            imgImpactIcon.setColorFilter(ContextCompat.getColor(context, R.color.colorPrimary))
            txtImpact.setTextColor(ContextCompat.getColor(context, R.color.sakura_text_primary))
        }

        btnCancel.text = cancelText
        btnConfirm.text = confirmText

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(view)
            .setCancelable(true)
            .create()

        btnCancel.setOnClickListener {
            dialog.dismiss()
        }

        btnConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm()
        }

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()

        // Định dạng kích thước hộp thoại cân đối giữa màn hình
        dialog.window?.let { window ->
            val displayMetrics = context.resources.displayMetrics
            val width = (displayMetrics.widthPixels * 0.88).toInt()
            window.setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        return dialog
    }
}
