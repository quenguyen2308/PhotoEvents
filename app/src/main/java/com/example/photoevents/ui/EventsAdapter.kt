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
import com.example.photoevents.R
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
    private val onSelectionChanged: (count: Int) -> Unit
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
        val img: ImageView = view.findViewById(R.id.imgThumbnail)
        val imageCount: TextView = view.findViewById(R.id.txtImageCount)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val date: TextView = view.findViewById(R.id.txtEventDate)
        val note: TextView = view.findViewById(R.id.txtNote)
        val btnExpand: ImageButton = view.findViewById(R.id.btnExpand)
        val scrollImages: HorizontalScrollView = view.findViewById(R.id.scrollImages)
        val imagesStrip: LinearLayout = view.findViewById(R.id.imagesStrip)
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
        holder.note.text = event.note
        holder.note.visibility = if (event.note.isBlank()) View.GONE else View.VISIBLE

        holder.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        holder.checkbox.setOnCheckedChangeListener(null)
        holder.checkbox.isChecked = selected

        // Ảnh đầu tiên luôn hiển thị trước tiêu đề.
        // Chỉ tô màu (tint) cho icon placeholder; ảnh thật phải để tint = null, nếu không sẽ bị nhuộm xám.
        val cover = item.coverImage
        if (cover != null) {
            ImageViewCompat.setImageTintList(holder.img, null)
            holder.img.scaleType = ImageView.ScaleType.CENTER_CROP
            val src: Any? = cover.localImagePath ?: cover.driveThumbnailLink
            Glide.with(holder.img).load(src).centerCrop().into(holder.img)
        } else {
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
            holder.imageCount.text = "${images.size}"
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
            populateStrip(holder, images.map { it.localImagePath ?: it.driveThumbnailLink })
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

    private fun populateStrip(holder: VH, sources: List<Any?>) {
        val context = holder.itemView.context
        val size = (56 * context.resources.displayMetrics.density).toInt()
        val margin = (4 * context.resources.displayMetrics.density).toInt()
        val radius = (10 * context.resources.displayMetrics.density)

        holder.imagesStrip.removeAllViews()
        sources.forEach { src ->
            val iv = ImageView(context).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply {
                    marginEnd = margin
                }
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                outlineProvider = RoundedOutline(radius)
            }
            Glide.with(context).load(src).placeholder(R.drawable.ic_image).centerCrop().into(iv)
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
