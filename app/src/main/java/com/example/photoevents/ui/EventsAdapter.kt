package com.example.photoevents.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.color.MaterialColors
import com.google.android.material.imageview.ShapeableImageView
import com.example.photoevents.R
import com.example.photoevents.data.EventImage
import com.example.photoevents.data.EventWithImages
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Danh sách sự kiện. Ảnh đầu tiên (nếu có) luôn hiện trước tiêu đề. Mũi tên bên phải chỉ
 * xổ ra / thu lại dải xem nhanh TẤT CẢ ảnh của sự kiện đó ngay trong item — đây là trạng thái
 * mở/đóng cục bộ (không lưu DB, không đồng bộ), reset khi rời màn hình là bình thường.
 * Bấm vào phần tiêu đề/ảnh đại diện mở màn chi tiết để thêm/xoá ảnh.
 *
 * Giữ (long-press) 1 item để vào chế độ chọn nhiều — sau đó chỉ cần chạm để chọn thêm/bớt,
 * dùng cho việc xoá hàng loạt. [onSelectionChanged] báo lại số lượng đang chọn mỗi khi đổi,
 * để Activity cập nhật thanh tiêu đề riêng cho chế độ này.
 */
class EventsAdapter(
    private val onClick: (EventWithImages) -> Unit,
    private val onSelectionChanged: (count: Int) -> Unit,
    private val onAdjustFocus: ((EventImage, Float) -> Unit)? = null
) : ListAdapter<EventWithImages, EventsAdapter.VH>(DIFF) {

    private val expandedIds = mutableSetOf<String>()
    private val selectedIds = mutableSetOf<String>()
    var selectionMode: Boolean = false
        private set

    fun selectedEventIds(): Set<String> = selectedIds.toSet()

    fun clearSelection() {
        if (!selectionMode && selectedIds.isEmpty()) return
        selectionMode = false
        selectedIds.clear()
        notifyDataSetChanged() // đổi trạng thái checkbox trên toàn bộ item đang hiển thị
        onSelectionChanged(0)
    }

    private fun enterSelectionMode(id: String) {
        selectionMode = true
        selectedIds.add(id)
        notifyDataSetChanged() // mọi item cần hiện checkbox, không chỉ item vừa long-press
        onSelectionChanged(selectedIds.size)
    }

    private fun toggleSelection(id: String, position: Int) {
        if (selectedIds.contains(id)) selectedIds.remove(id) else selectedIds.add(id)
        if (selectedIds.isEmpty()) {
            selectionMode = false
            notifyDataSetChanged() // hết chọn -> ẩn checkbox toàn bộ, thoát chế độ chọn
        } else {
            notifyItemChanged(position)
        }
        onSelectionChanged(selectedIds.size)
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val checkbox: CheckBox = view.findViewById(R.id.checkboxSelect)
        val img: ShapeableImageView = view.findViewById(R.id.imgThumbnail)
        val imageCount: TextView = view.findViewById(R.id.txtImageCount)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val date: TextView = view.findViewById(R.id.txtEventDate)
        val category: TextView = view.findViewById(R.id.txtCategory)
        val note: TextView = view.findViewById(R.id.txtNote)
        val btnExpand: ImageButton = view.findViewById(R.id.btnExpand)
        val scrollImages: HorizontalScrollView = view.findViewById(R.id.scrollImages)
        val imagesStrip: LinearLayout = view.findViewById(R.id.imagesStrip)

        // Bento Mosaic views:
        val frameMainImage: View = view.findViewById(R.id.frameMainImage)
        val layoutSubImages: View = view.findViewById(R.id.layoutSubImages)
        val imgSub1: ShapeableImageView = view.findViewById(R.id.imgSub1)
        val frameSub2: View = view.findViewById(R.id.frameSub2)
        val imgSub2: ShapeableImageView = view.findViewById(R.id.imgSub2)
        val txtMoreOverlay: TextView = view.findViewById(R.id.txtMoreImagesOverlay)
        val btnAdjustFocus: ImageButton = view.findViewById(R.id.btnAdjustFocus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_event, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val event = item.event
        val images = item.visibleImages
        val selected = event.id in selectedIds

        holder.title.text = event.title
        holder.date.text = dateFormat.format(event.eventDate)

        val cat = event.category.trim()
        if (cat.isNotEmpty()) {
            holder.category.visibility = View.VISIBLE
            holder.category.text = com.example.photoevents.data.CategoryHelper.formatStandard(cat)
        } else {
            holder.category.visibility = View.GONE
        }

        holder.note.text = event.note
        holder.note.visibility = if (event.note.isBlank()) View.GONE else View.VISIBLE

        holder.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selected

        // Lưới Bento Mosaic: ảnh đại diện cover bên trái, các ảnh phụ bên phải
        val cover = item.coverImage
        val otherImages = images.filter { it.id != cover?.id }
        val radius = 20 * holder.itemView.resources.displayMetrics.density

        if (cover != null) {
            ImageViewCompat.setImageTintList(holder.img, null)
            holder.img.scaleType = ImageView.ScaleType.CENTER_CROP
            val src: Any? = cover.localImagePath ?: cover.driveThumbnailLink
            Glide.with(holder.img)
                .load(src)
                .transform(FocusCropTransformation(cover.focusX, cover.focusY))
                .into(holder.img)

            if (otherImages.isNotEmpty()) {
                // Có từ 2 ảnh trở lên: Kích hoạt Bento Mosaic
                holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(radius)
                    .setTopRightCornerSize(0f)
                    .build()

                val mainParams = holder.frameMainImage.layoutParams as LinearLayout.LayoutParams
                mainParams.weight = 60f
                holder.frameMainImage.layoutParams = mainParams

                holder.layoutSubImages.visibility = View.VISIBLE

                val sub1 = otherImages[0]
                val srcSub1: Any? = sub1.localImagePath ?: sub1.driveThumbnailLink
                Glide.with(holder.imgSub1)
                    .load(srcSub1)
                    .transform(FocusCropTransformation(sub1.focusX, sub1.focusY))
                    .into(holder.imgSub1)

                if (otherImages.size >= 2) {
                    holder.frameSub2.visibility = View.VISIBLE
                    val sub2 = otherImages[1]
                    val srcSub2: Any? = sub2.localImagePath ?: sub2.driveThumbnailLink
                    Glide.with(holder.imgSub2)
                        .load(srcSub2)
                        .transform(FocusCropTransformation(sub2.focusX, sub2.focusY))
                        .into(holder.imgSub2)

                    val moreCount = images.size - 3
                    if (moreCount > 0) {
                        holder.txtMoreOverlay.visibility = View.VISIBLE
                        holder.txtMoreOverlay.text = "+$moreCount"
                    } else {
                        holder.txtMoreOverlay.visibility = View.GONE
                    }
                } else {
                    holder.frameSub2.visibility = View.GONE
                    Glide.with(holder.imgSub2).clear(holder.imgSub2)
                    holder.txtMoreOverlay.visibility = View.GONE
                }
            } else {
                // Chỉ có 1 ảnh duy nhất: Ảnh chính phủ toàn bộ chiều ngang
                holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                    .setTopLeftCornerSize(radius)
                    .setTopRightCornerSize(radius)
                    .build()

                val mainParams = holder.frameMainImage.layoutParams as LinearLayout.LayoutParams
                mainParams.weight = 100f
                holder.frameMainImage.layoutParams = mainParams

                holder.layoutSubImages.visibility = View.GONE
                Glide.with(holder.imgSub1).clear(holder.imgSub1)
                Glide.with(holder.imgSub2).clear(holder.imgSub2)
            }

            // Cho phép chỉnh tiêu điểm nhanh trực tiếp bằng cách nhấn giữ vào ảnh trên Bento
            holder.img.setOnLongClickListener {
                if (!selectionMode) {
                    onAdjustFocus?.invoke(cover, if (otherImages.isNotEmpty()) 1.0f else 1.6f)
                    true
                } else false
            }
            holder.imgSub1.setOnLongClickListener {
                if (!selectionMode && otherImages.isNotEmpty()) {
                    onAdjustFocus?.invoke(otherImages[0], if (otherImages.size >= 2) 1.33f else 0.67f)
                    true
                } else false
            }
            holder.imgSub2.setOnLongClickListener {
                if (!selectionMode && otherImages.size >= 2) {
                    onAdjustFocus?.invoke(otherImages[1], 1.33f)
                    true
                } else false
            }

            // Nút icon chỉnh tiêu điểm Bento ở góc dưới bên phải
            if (!selectionMode) {
                holder.btnAdjustFocus.visibility = View.VISIBLE
                holder.btnAdjustFocus.setOnClickListener {
                    if (otherImages.isEmpty()) {
                        onAdjustFocus?.invoke(cover, 1.6f)
                    } else {
                        val popup = androidx.appcompat.widget.PopupMenu(holder.itemView.context, holder.btnAdjustFocus)
                        popup.menu.add(0, 0, 0, "🌸 Chỉnh ảnh đại diện (khung vuông)")
                        popup.menu.add(0, 1, 1, "🌸 Chỉnh ảnh phụ 1 (trên)")
                        if (otherImages.size >= 2) {
                            popup.menu.add(0, 2, 2, "🌸 Chỉnh ảnh phụ 2 (dưới)")
                        }
                        popup.setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                0 -> onAdjustFocus?.invoke(cover, 1.0f)
                                1 -> onAdjustFocus?.invoke(otherImages[0], if (otherImages.size >= 2) 1.33f else 0.67f)
                                2 -> if (otherImages.size >= 2) onAdjustFocus?.invoke(otherImages[1], 1.33f)
                            }
                            true
                        }
                        popup.show()
                    }
                }
            } else {
                holder.btnAdjustFocus.visibility = View.GONE
            }
        } else {
            holder.btnAdjustFocus.visibility = View.GONE
            // Không có ảnh
            holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                .setTopLeftCornerSize(radius)
                .setTopRightCornerSize(radius)
                .build()

            val mainParams = holder.frameMainImage.layoutParams as LinearLayout.LayoutParams
            mainParams.weight = 100f
            holder.frameMainImage.layoutParams = mainParams

            holder.layoutSubImages.visibility = View.GONE
            Glide.with(holder.imgSub1).clear(holder.imgSub1)
            Glide.with(holder.imgSub2).clear(holder.imgSub2)
            Glide.with(holder.img).clear(holder.img)
            holder.img.scaleType = ImageView.ScaleType.CENTER
            ImageViewCompat.setImageTintList(
                holder.img,
                ColorStateList.valueOf(
                    MaterialColors.getColor(holder.img, com.google.android.material.R.attr.colorOnSurfaceVariant)
                )
            )
            holder.img.setImageResource(R.drawable.ic_image)
        }

        if (images.size > 1) {
            holder.imageCount.visibility = View.VISIBLE
            holder.imageCount.text = "${images.size} ảnh"
        } else {
            holder.imageCount.visibility = View.GONE
        }

        // Mũi tên xổ/thu chỉ hiện khi có từ 2 ảnh trở lên (1 ảnh thì cover đã đủ xem), và ẩn
        // trong lúc đang chọn nhiều để tránh rối thao tác.
        val expanded = event.id in expandedIds
        if (images.size > 1 && !selectionMode) {
            holder.btnExpand.visibility = View.VISIBLE
            holder.btnExpand.rotation = if (expanded) 180f else 0f
            holder.btnExpand.setOnClickListener {
                if (expanded) expandedIds.remove(event.id) else expandedIds.add(event.id)
                notifyItemChanged(holder.bindingAdapterPosition)
            }
        } else {
            holder.btnExpand.visibility = View.GONE
        }

        holder.scrollImages.visibility = if (expanded && images.size > 1 && !selectionMode) View.VISIBLE else View.GONE
        if (expanded && images.size > 1 && !selectionMode) {
            populateStrip(holder, images)
        } else {
            holder.imagesStrip.removeAllViews()
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos == RecyclerView.NO_POSITION) return@setOnClickListener
            if (selectionMode) {
                toggleSelection(event.id, pos)
            } else {
                onClick(item)
            }
        }
        holder.itemView.setOnLongClickListener {
            if (!selectionMode) enterSelectionMode(event.id)
            true
        }
    }

    private val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN"))

    private fun populateStrip(holder: VH, images: List<EventImage>) {
        val context = holder.itemView.context
        val size = (68 * context.resources.displayMetrics.density).toInt()
        val margin = (6 * context.resources.displayMetrics.density).toInt()
        val radius = (14 * context.resources.displayMetrics.density)

        holder.imagesStrip.removeAllViews()
        images.forEach { image ->
            val src: Any? = image.localImagePath ?: image.driveThumbnailLink
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = RoundedOutline(radius)
            }
            Glide.with(context)
                .load(src)
                .placeholder(R.drawable.ic_image)
                .transform(FocusCropTransformation(image.focusX, image.focusY))
                .into(iv)
            iv.setOnClickListener {
                onAdjustFocus?.invoke(image, 1.0f)
            }
            holder.imagesStrip.addView(iv)
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<EventWithImages>() {
            override fun areItemsTheSame(a: EventWithImages, b: EventWithImages) = a.event.id == b.event.id
            override fun areContentsTheSame(a: EventWithImages, b: EventWithImages) = a == b
        }
    }
}

private class RoundedOutline(private val radiusPx: Float) : android.view.ViewOutlineProvider() {
    override fun getOutline(view: View, outline: android.graphics.Outline) {
        outline.setRoundRect(0, 0, view.width, view.height, radiusPx)
    }
}
