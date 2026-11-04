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
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

object PhotoLightboxDialog {

    fun show(
        context: Context,
        images: List<EventImage>,
        initialPosition: Int = 0,
        getIsCover: ((EventImage) -> Boolean)? = null,
        onToggleCover: ((EventImage) -> Unit)? = null,
        onDelete: ((EventImage) -> Unit)? = null,
        onAdjustFocus: ((EventImage) -> Unit)? = null
    ): Dialog {
        val dialog = Dialog(context, android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_photo_lightbox, null)
        dialog.setContentView(view)

        val viewPager = view.findViewById<ViewPager2>(R.id.viewPagerLightbox)
        val txtIndex = view.findViewById<TextView>(R.id.txtLightboxIndex)
        val btnClose = view.findViewById<ImageButton>(R.id.btnLightboxClose)
        val btnCover = view.findViewById<ImageButton>(R.id.btnLightboxCover)
        val btnFocus = view.findViewById<ImageButton>(R.id.btnLightboxFocus)
        val btnDelete = view.findViewById<ImageButton>(R.id.btnLightboxDelete)
        val topBar = view.findViewById<View>(R.id.layoutLightboxTopBar)

        val imageList = images.ifEmpty { emptyList() }
        val startPos = initialPosition.coerceIn(0, (imageList.size - 1).coerceAtLeast(0))
        var currentCoverImageId: String? = imageList.firstOrNull { getIsCover?.invoke(it) == true }?.id
            ?: imageList.firstOrNull()?.id

        val toggleBarVisibility = {
            topBar.visibility = if (topBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        viewPager.adapter = LightboxPagerAdapter(imageList, toggleBarVisibility)

        fun updateUIForPosition(pos: Int) {
            val currentImage = imageList.getOrNull(pos) ?: return
            val total = imageList.size
            txtIndex.text = "Ảnh ${pos + 1} / $total"

            val isCover = if (currentCoverImageId != null) {
                currentImage.id == currentCoverImageId
            } else {
                getIsCover?.invoke(currentImage) == true
            }

            if (isCover) {
                btnCover.setImageResource(R.drawable.ic_star)
                btnCover.setColorFilter(ContextCompat.getColor(context, R.color.amber_primary))
                btnCover.contentDescription = "Đang là ảnh đại diện"
            } else {
                btnCover.setImageResource(R.drawable.ic_star_outline)
                btnCover.setColorFilter(Color.WHITE)
                btnCover.contentDescription = "Đặt làm ảnh đại diện"
            }
        }

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                updateUIForPosition(position)
            }
        })

        if (imageList.isNotEmpty()) {
            viewPager.setCurrentItem(startPos, false)
            updateUIForPosition(startPos)
        }

        btnClose.setOnClickListener { dialog.dismiss() }

        if (onToggleCover != null) {
            btnCover.visibility = View.VISIBLE
            btnCover.setOnClickListener {
                val currentPos = viewPager.currentItem
                val currentImg = imageList.getOrNull(currentPos)
                if (currentImg != null) {
                    currentCoverImageId = if (currentCoverImageId == currentImg.id) {
                        // Nếu bỏ cover thì fallback về ảnh đầu tiên
                        imageList.firstOrNull()?.id
                    } else {
                        currentImg.id
                    }
                    onToggleCover.invoke(currentImg)
                    updateUIForPosition(currentPos)
                }
            }
        } else {
            btnCover.visibility = View.GONE
        }

        if (onAdjustFocus != null) {
            btnFocus.visibility = View.VISIBLE
            btnFocus.setOnClickListener {
                val currentPos = viewPager.currentItem
                val currentImg = imageList.getOrNull(currentPos)
                if (currentImg != null) {
                    dialog.dismiss()
                    onAdjustFocus.invoke(currentImg)
                }
            }
        } else {
            btnFocus.visibility = View.GONE
        }

        if (onDelete != null) {
            btnDelete.visibility = View.VISIBLE
            btnDelete.setOnClickListener {
                val currentPos = viewPager.currentItem
                val currentImg = imageList.getOrNull(currentPos)
                if (currentImg != null) {
                    dialog.dismiss()
                    onDelete.invoke(currentImg)
                }
            }
        } else {
            btnDelete.visibility = View.GONE
        }

        dialog.window?.let { window ->
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            window.setBackgroundDrawableResource(android.R.color.transparent)
        }

        dialog.show()
        return dialog
    }

    /**
     * Phương thức backward compatibility nếu gọi với 1 ảnh đơn
     */
    fun show(
        context: Context,
        image: EventImage,
        isCover: Boolean,
        onToggleCover: ((EventImage) -> Unit)? = null,
        onDelete: ((EventImage) -> Unit)? = null,
        onAdjustFocus: ((EventImage) -> Unit)? = null
    ): Dialog {
        return show(
            context = context,
            images = listOf(image),
            initialPosition = 0,
            getIsCover = { isCover },
            onToggleCover = onToggleCover,
            onDelete = onDelete,
            onAdjustFocus = onAdjustFocus
        )
    }

    private class LightboxPagerAdapter(
        private val images: List<EventImage>,
        private val onItemClick: () -> Unit
    ) : RecyclerView.Adapter<LightboxPagerAdapter.LightboxVH>() {

        class LightboxVH(view: View) : RecyclerView.ViewHolder(view) {
            val imgView: ImageView = view.findViewById(R.id.imgLightboxItem)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LightboxVH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_lightbox_photo, parent, false)
            return LightboxVH(v)
        }

        override fun onBindViewHolder(holder: LightboxVH, position: Int) {
            val image = images[position]
            val src: Any? = image.localImagePath ?: image.driveThumbnailLink
            Glide.with(holder.imgView.context)
                .load(src)
                .fitCenter()
                .into(holder.imgView)

            holder.itemView.setOnClickListener {
                onItemClick()
            }
            holder.imgView.setOnClickListener {
                onItemClick()
            }
        }

        override fun getItemCount(): Int = images.size
    }
}
