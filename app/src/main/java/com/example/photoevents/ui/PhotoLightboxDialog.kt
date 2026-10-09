package com.example.photoevents.ui

import android.app.Dialog
import android.content.Context
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.bumptech.glide.Glide
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

object PhotoLightboxDialog {

    fun show(
        context: Context,
        image: EventImage,
        isCover: Boolean,
        indexText: String = "",
        onToggleCover: ((EventImage) -> Unit)? = null,
        onDelete: ((EventImage) -> Unit)? = null,
        onAdjustFocus: ((EventImage) -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_photo_lightbox, null)
        dialog.setContentView(view)

        val imgPhoto = view.findViewById<ImageView>(R.id.imgLightboxPhoto)
        val txtIndex = view.findViewById<TextView>(R.id.txtLightboxIndex)
        val btnClose = view.findViewById<ImageButton>(R.id.btnLightboxClose)
        val btnCover = view.findViewById<ImageButton>(R.id.btnLightboxCover)
        val btnFocus = view.findViewById<ImageButton>(R.id.btnLightboxFocus)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnLightboxDelete)
        val topBar = view.findViewById<View>(R.id.layoutLightboxTopBar)

        txtIndex.text = indexText

        val src: Any? = image.localImagePath ?: image.driveThumbnailLink
        Glide.with(context)
            .load(src)
            .fitCenter()
            .into(imgPhoto)

        var currentIsCover = isCover
        fun updateCoverIcon(cover: Boolean) {
            currentIsCover = cover
            if (cover) {
                btnCover.setImageResource(R.drawable.ic_star)
                btnCover.setColorFilter(ContextCompat.getColor(context, R.color.amber_primary))
                btnCover.contentDescription = "Đang là ảnh đại diện"
            } else {
                btnCover.setImageResource(R.drawable.ic_star_outline)
                btnCover.setColorFilter(Color.WHITE)
                btnCover.contentDescription = "Đặt làm ảnh đại diện"
            }
        }
        updateCoverIcon(isCover)

        btnClose.setOnClickListener { dialog.dismiss() }

        if (onToggleCover != null) {
            btnCover.visibility = View.VISIBLE
            btnCover.setOnClickListener {
                updateCoverIcon(!currentIsCover)
                onToggleCover.invoke(image)
            }
        } else {
            btnCover.visibility = View.GONE
        }

        if (onAdjustFocus != null) {
            btnFocus.visibility = View.VISIBLE
            btnFocus.setOnClickListener {
                dialog.dismiss()
                onAdjustFocus.invoke(image)
            }
        } else {
            btnFocus.visibility = View.GONE
        }

        if (onDelete != null) {
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener {
                dialog.dismiss()
                onDelete.invoke(image)
            }
        } else {
            btnDelete.visibility = View.GONE
        }

        // Chạm vào ảnh để ẩn/hiện thanh công cụ
        imgPhoto.setOnClickListener {
            topBar.visibility = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialog.show()
        return dialog
    }
}
