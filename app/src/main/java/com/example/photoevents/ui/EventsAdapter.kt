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
    private val onAdjustFocus: ((event: com.example.photoevents.data.Event, image: EventImage, aspectRatio: Float, isCover: Boolean) -> Unit)? = null
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

    fun selectAll(eventIds: Collection<String>) {
        if (eventIds.isEmpty()) return
        selectionMode = true
        selectedIds.addAll(eventIds)
        notifyDataSetChanged()
        onSelectionChanged(selectedIds.size)
    }

    fun isAllSelected(eventIds: Collection<String>): Boolean {
        if (eventIds.isEmpty()) return false
        return selectedIds.containsAll(eventIds)
    }

    fun toggleSelectAll(eventIds: Collection<String>) {
        if (isAllSelected(eventIds)) {
            clearSelection()
        } else {
            selectAll(eventIds)
        }
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
        val layoutMosaic: LinearLayout = view.findViewById(R.id.layoutMosaic)
        val bentoContainer: View = view.findViewById(R.id.bentoContainer)
        val frameMainImage: View = view.findViewById(R.id.frameMainImage)
        val layoutSubImages: View = view.findViewById(R.id.layoutSubImages)
        val imgSub1: ShapeableImageView = view.findViewById(R.id.imgSub1)
        val frameSub2: View = view.findViewById(R.id.frameSub2)
        val imgSub2: ShapeableImageView = view.findViewById(R.id.imgSub2)
        val btnAdjustFocus: ImageButton = view.findViewById(R.id.btnAdjustFocus)

        // Theme-specific decorative views:
        val layoutSpiralHeader: View? = view.findViewById(R.id.layoutSpiralHeader)
        val layoutSpiralRings: View? = view.findViewById(R.id.layoutSpiralRings)
        val imgThumbtackPin: View? = view.findViewById(R.id.imgThumbtackPin)
        val imgWashiTape: View? = view.findViewById(R.id.imgWashiTape)
        val imgPaperclip: View? = view.findViewById(R.id.imgPaperclip)
        val layoutFilmHeader: View? = view.findViewById(R.id.layoutFilmHeader)
        val imgFilmCornerMainTL: View? = view.findViewById(R.id.imgFilmCornerMainTL)
        val imgFilmCornerMainBR: View? = view.findViewById(R.id.imgFilmCornerMainBR)
        val imgFilmCornerSub1TL: View? = view.findViewById(R.id.imgFilmCornerSub1TL)
        val imgFilmCornerSub2BR: View? = view.findViewById(R.id.imgFilmCornerSub2BR)
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
        if (cat.isNotEmpty() && !com.example.photoevents.data.CategoryHelper.isUncategorized(cat)) {
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
        val displayMetrics = holder.itemView.context.resources.displayMetrics
        val density = displayMetrics.density

        val currentTheme = com.example.photoevents.util.ThemeHelper.getSavedTheme(holder.itemView.context)
        val isFilmTheme = (currentTheme == com.example.photoevents.util.AppTheme.MEMORY_JOURNAL)

        // Khung Thước Phim 35mm (Memory Journal):
        holder.layoutFilmHeader?.visibility = if (isFilmTheme) View.VISIBLE else View.GONE
        holder.imgFilmCornerMainTL?.visibility = if (isFilmTheme) View.VISIBLE else View.GONE
        holder.imgFilmCornerMainBR?.visibility = if (isFilmTheme) View.VISIBLE else View.GONE
        holder.imgFilmCornerSub1TL?.visibility = if (isFilmTheme && otherImages.isNotEmpty()) View.VISIBLE else View.GONE
        holder.imgFilmCornerSub2BR?.visibility = if (isFilmTheme && otherImages.size >= 2) View.VISIBLE else View.GONE

        // Mẫu 4: Stitched Scrapbook (Sổ Da May Chỉ) cho Memory Chest
        holder.imgThumbtackPin?.visibility = View.GONE

        // Ẩn các view cũ:
        holder.layoutSpiralHeader?.visibility = View.GONE
        holder.layoutSpiralRings?.visibility = View.GONE
        holder.imgWashiTape?.visibility = View.GONE
        holder.imgPaperclip?.visibility = View.GONE

        // Căn chỉnh khung Bento bên trong thẻ (Khung Sổ Da May Chỉ được bo viền đặt lồng vào trong):
        val (bentoMarginH, bentoMarginTop) = when (currentTheme) {
            com.example.photoevents.util.AppTheme.SAKURA -> 0 to 0
            com.example.photoevents.util.AppTheme.MEMORY_CHEST -> (8 * density).toInt() to (8 * density).toInt()
            com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> 0 to 0
        }
        (holder.bentoContainer.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.leftMargin != bentoMarginH || lp.rightMargin != bentoMarginH || lp.topMargin != bentoMarginTop) {
                lp.setMargins(bentoMarginH, bentoMarginTop, bentoMarginH, 0)
                holder.bentoContainer.layoutParams = lp
            }
        }

        val (mosaicMarginH, mosaicMarginV) = when (currentTheme) {
            com.example.photoevents.util.AppTheme.SAKURA -> (4 * density).toInt() to (4 * density).toInt()
            com.example.photoevents.util.AppTheme.MEMORY_CHEST -> (7 * density).toInt() to (7 * density).toInt()
            com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> (6 * density).toInt() to (6 * density).toInt()
        }

        val gap = when (currentTheme) {
            com.example.photoevents.util.AppTheme.SAKURA -> (4 * density).toInt()
            com.example.photoevents.util.AppTheme.MEMORY_CHEST -> (6 * density).toInt()
            com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> (5 * density).toInt()
        }

        (holder.layoutMosaic.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.leftMargin != mosaicMarginH || lp.topMargin != mosaicMarginV ||
                lp.rightMargin != mosaicMarginH || lp.bottomMargin != mosaicMarginV) {
                lp.setMargins(mosaicMarginH, mosaicMarginV, mosaicMarginH, mosaicMarginV)
                holder.layoutMosaic.layoutParams = lp
            }
        }

        (holder.layoutSubImages.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.marginStart != gap) {
                lp.marginStart = gap
                holder.layoutSubImages.layoutParams = lp
            }
        }
        (holder.frameSub2.layoutParams as? ViewGroup.MarginLayoutParams)?.let { lp ->
            if (lp.topMargin != gap) {
                lp.topMargin = gap
                holder.frameSub2.layoutParams = lp
            }
        }

        val cardWidth = if (holder.itemView.width > 0) {
            holder.itemView.width
        } else {
            displayMetrics.widthPixels - (32 * density).toInt()
        }
        val containerHeight = (215 * density).toInt()
        val usableWidth = (cardWidth - 2 * mosaicMarginH).coerceAtLeast(10)
        val usableHeight = (containerHeight - 2 * mosaicMarginV).coerceAtLeast(10)

        val (mainTargetWidth, mainTargetHeight) = if (otherImages.isNotEmpty()) {
            val availWidth = (usableWidth - gap).coerceAtLeast(10)
            val w = (availWidth * 0.6f).toInt()
            w to usableHeight
        } else {
            usableWidth to usableHeight
        }

        if (cover != null) {
            if (otherImages.isNotEmpty()) {
                // Có từ 2 ảnh trở lên: Kích hoạt Bento Mosaic
                when (currentTheme) {
                    com.example.photoevents.util.AppTheme.SAKURA -> {
                        val outerRadius = 16 * density
                        val innerRadius = 6 * density
                        val subBottomRadius = if (otherImages.size >= 2) innerRadius else outerRadius

                        holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                            .setTopLeftCornerSize(outerRadius)
                            .setBottomLeftCornerSize(outerRadius)
                            .setTopRightCornerSize(innerRadius)
                            .setBottomRightCornerSize(innerRadius)
                            .build()

                        holder.imgSub1.shapeAppearanceModel = holder.imgSub1.shapeAppearanceModel.toBuilder()
                            .setTopRightCornerSize(outerRadius)
                            .setTopLeftCornerSize(innerRadius)
                            .setBottomRightCornerSize(subBottomRadius)
                            .setBottomLeftCornerSize(innerRadius)
                            .build()

                        if (otherImages.size >= 2) {
                            holder.imgSub2.shapeAppearanceModel = holder.imgSub2.shapeAppearanceModel.toBuilder()
                                .setBottomRightCornerSize(outerRadius)
                                .setTopRightCornerSize(innerRadius)
                                .setTopLeftCornerSize(innerRadius)
                                .setBottomLeftCornerSize(innerRadius)
                                .build()
                        }
                    }
                    com.example.photoevents.util.AppTheme.MEMORY_CHEST -> {
                        val scrapbookRadius = 9 * density
                        holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(scrapbookRadius)
                            .build()
                        holder.imgSub1.shapeAppearanceModel = holder.imgSub1.shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(scrapbookRadius)
                            .build()
                        if (otherImages.size >= 2) {
                            holder.imgSub2.shapeAppearanceModel = holder.imgSub2.shapeAppearanceModel.toBuilder()
                                .setAllCornerSizes(scrapbookRadius)
                                .build()
                        }
                    }
                    com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> {
                        val filmRadius = 8 * density
                        holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(filmRadius)
                            .build()
                        holder.imgSub1.shapeAppearanceModel = holder.imgSub1.shapeAppearanceModel.toBuilder()
                            .setAllCornerSizes(filmRadius)
                            .build()
                        if (otherImages.size >= 2) {
                            holder.imgSub2.shapeAppearanceModel = holder.imgSub2.shapeAppearanceModel.toBuilder()
                                .setAllCornerSizes(filmRadius)
                                .build()
                        }
                    }
                }

                val mainParams = holder.frameMainImage.layoutParams as LinearLayout.LayoutParams
                mainParams.weight = 60f
                holder.frameMainImage.layoutParams = mainParams

                holder.layoutSubImages.visibility = View.VISIBLE

                if (otherImages.size >= 2) {
                    holder.frameSub2.visibility = View.VISIBLE
                } else {
                    holder.frameSub2.visibility = View.GONE
                    Glide.with(holder.imgSub2).clear(holder.imgSub2)
                }
            } else {
                // Chỉ có 1 ảnh duy nhất: Ảnh chính phủ toàn bộ chiều ngang
                val singleRadius = when (currentTheme) {
                    com.example.photoevents.util.AppTheme.SAKURA -> 16 * density
                    com.example.photoevents.util.AppTheme.MEMORY_CHEST -> 9 * density
                    com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> 8 * density
                }
                holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                    .setAllCornerSizes(singleRadius)
                    .build()

                val mainParams = holder.frameMainImage.layoutParams as LinearLayout.LayoutParams
                mainParams.weight = 100f
                holder.frameMainImage.layoutParams = mainParams

                holder.layoutSubImages.visibility = View.GONE
                Glide.with(holder.imgSub1).clear(holder.imgSub1)
                Glide.with(holder.imgSub2).clear(holder.imgSub2)
            }

            ImageViewCompat.setImageTintList(holder.img, null)
            holder.img.scaleType = ImageView.ScaleType.CENTER_CROP
            val src: Any? = cover.localImagePath ?: cover.driveThumbnailLink
            Glide.with(holder.img)
                .load(src)
                .override(mainTargetWidth, mainTargetHeight)
                .transform(FocusCropTransformation(cover.focusX, cover.focusY, cover.focusZoom))
                .into(holder.img)

            if (otherImages.isNotEmpty()) {
                val availWidth = (usableWidth - gap).coerceAtLeast(10)
                val subWidth = availWidth - mainTargetWidth
                val hasTwoSubs = otherImages.size >= 2
                val sub1Height = if (hasTwoSubs) (usableHeight - gap) / 2 else usableHeight
                val sub2Height = if (hasTwoSubs) usableHeight - gap - sub1Height else 0

                val sub1 = otherImages[0]
                val srcSub1: Any? = sub1.localImagePath ?: sub1.driveThumbnailLink
                holder.imgSub1.scaleType = ImageView.ScaleType.CENTER_CROP
                Glide.with(holder.imgSub1)
                    .load(srcSub1)
                    .override(subWidth, sub1Height)
                    .transform(FocusCropTransformation(sub1.focusX, sub1.focusY, sub1.focusZoom))
                    .into(holder.imgSub1)

                if (otherImages.size >= 2) {
                    val sub2 = otherImages[1]
                    val srcSub2: Any? = sub2.localImagePath ?: sub2.driveThumbnailLink
                    holder.imgSub2.scaleType = ImageView.ScaleType.CENTER_CROP
                    Glide.with(holder.imgSub2)
                        .load(srcSub2)
                        .override(subWidth, sub2Height)
                        .transform(FocusCropTransformation(sub2.focusX, sub2.focusY, sub2.focusZoom))
                        .into(holder.imgSub2)
                }
            }

            // Cho phép chỉnh tiêu điểm nhanh trực tiếp bằng cách nhấn giữ vào ảnh trên Bento
            holder.img.setOnLongClickListener {
                if (!selectionMode) {
                    onAdjustFocus?.invoke(event, cover, if (otherImages.isNotEmpty()) 1.0f else 1.6f, true)
                    true
                } else false
            }
            holder.imgSub1.setOnLongClickListener {
                if (!selectionMode && otherImages.isNotEmpty()) {
                    onAdjustFocus?.invoke(event, otherImages[0], if (otherImages.size >= 2) 1.33f else 0.67f, false)
                    true
                } else false
            }
            holder.imgSub2.setOnLongClickListener {
                if (!selectionMode && otherImages.size >= 2) {
                    onAdjustFocus?.invoke(event, otherImages[1], 1.33f, false)
                    true
                } else false
            }

            // Nút icon chỉnh tiêu điểm Bento ở góc dưới bên phải
            if (!selectionMode) {
                holder.btnAdjustFocus.visibility = View.VISIBLE
                holder.btnAdjustFocus.setOnClickListener {
                    if (otherImages.isEmpty()) {
                        onAdjustFocus?.invoke(event, cover, 1.6f, true)
                    } else {
                        val popup = androidx.appcompat.widget.PopupMenu(holder.itemView.context, holder.btnAdjustFocus)
                        popup.menu.add(0, 0, 0, "🌸 Chỉnh ảnh đại diện (khung vuông)")
                        popup.menu.add(0, 1, 1, "🌸 Chỉnh ảnh phụ 1 (trên)")
                        if (otherImages.size >= 2) {
                            popup.menu.add(0, 2, 2, "🌸 Chỉnh ảnh phụ 2 (dưới)")
                        }
                        popup.setOnMenuItemClickListener { menuItem ->
                            when (menuItem.itemId) {
                                0 -> onAdjustFocus?.invoke(event, cover, 1.0f, true)
                                1 -> onAdjustFocus?.invoke(event, otherImages[0], if (otherImages.size >= 2) 1.33f else 0.67f, false)
                                2 -> if (otherImages.size >= 2) onAdjustFocus?.invoke(event, otherImages[1], 1.33f, false)
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
            val placeholderRadius = when (currentTheme) {
                com.example.photoevents.util.AppTheme.SAKURA -> 16 * density
                com.example.photoevents.util.AppTheme.MEMORY_CHEST -> 9 * density
                com.example.photoevents.util.AppTheme.MEMORY_JOURNAL -> 8 * density
            }
            holder.img.shapeAppearanceModel = holder.img.shapeAppearanceModel.toBuilder()
                .setAllCornerSizes(placeholderRadius)
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
            if (!selectionMode) {
                holder.itemView.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                enterSelectionMode(event.id)
            }
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
        images.forEachIndexed { index, image ->
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
                .transform(FocusCropTransformation(image.focusX, image.focusY, image.focusZoom))
                .into(iv)
            iv.setOnClickListener {
                val item = getItem(holder.bindingAdapterPosition)
                com.example.photoevents.ui.PhotoLightboxDialog.show(
                    context = context,
                    images = images,
                    initialPosition = index,
                    getIsCover = { img -> img.id == item?.coverImage?.id },
                    onAdjustFocus = { img ->
                        item?.let { onAdjustFocus?.invoke(it.event, img, 1.0f, img.id == it.coverImage?.id) }
                    }
                )
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
