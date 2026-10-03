package com.example.photoevents.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.google.android.material.card.MaterialCardView
import com.google.android.material.color.MaterialColors
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

/**
 * Lưới ảnh trong màn chi tiết sự kiện — có thể GIỮ rồi KÉO 1 ảnh để đổi vị trí hiển thị
 * (xem [ImageDragCallback], gắn qua ItemTouchHelper ở EventDetailActivity).
 *
 * Không dùng ListAdapter/DiffUtil ở đây: trong lúc kéo cần đổi thứ tự danh sách trực tiếp
 * ([moveItem]) và gọi notifyItemMoved ngay lập tức để UI mượt, DiffUtil bất đồng bộ của
 * ListAdapter không phù hợp cho thao tác này.
 */
class ImagesAdapter(
    private val onDelete: (EventImage) -> Unit,
    private val onToggleCover: (EventImage) -> Unit,
    private val onAdjustFocus: (EventImage) -> Unit
) : RecyclerView.Adapter<ImagesAdapter.VH>() {

    private val items = mutableListOf<EventImage>()

    /** Id ảnh đại diện hiện tại (đã tính cả fallback về ảnh đầu tiên). */
    private var coverImageId: String? = null

    /** Danh sách hiện tại đang hiển thị, gồm cả thứ tự sau khi kéo — dùng để lưu lại vào DB. */
    fun currentList(): List<EventImage> = items.toList()

    fun submitList(newList: List<EventImage>) {
        if (newList == items) return // tránh vẽ lại/giật hình khi nội dung không đổi (vd. tự ghi lại từ kéo-thả)
        items.clear()
        items.addAll(newList)
        notifyDataSetChanged()
    }

    fun setCover(id: String?) {
        if (id == coverImageId) return
        coverImageId = id
        notifyItemRangeChanged(0, itemCount)
    }

    /** Gọi bởi [ImageDragCallback] khi đang kéo qua vị trí khác — chỉ đổi hiển thị tạm thời, chưa lưu DB. */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    override fun getItemCount() = items.size

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardContainer)
        val img: ImageView = view.findViewById(R.id.imgThumb)
        val btnCover: ImageButton = view.findViewById(R.id.btnCover)
        val btnDelete: ImageButton = view.findViewById(R.id.btnDelete)
        val btnFocus: ImageButton = view.findViewById(R.id.btnFocus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_image, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val image = items[position]
        val src: Any? = image.localImagePath ?: image.driveThumbnailLink
        Glide.with(holder.img)
            .load(src)
            .transform(FocusCropTransformation(image.focusX, image.focusY))
            .into(holder.img)

        val isCover = image.id == coverImageId
        val context = holder.itemView.context
        val density = context.resources.displayMetrics.density

        if (isCover) {
            holder.card.strokeWidth = (2.5f * density).toInt()
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.amber_primary)
            holder.btnCover.setImageResource(R.drawable.ic_star)
            ImageViewCompat.setImageTintList(
                holder.btnCover,
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.amber_primary))
            )
        } else {
            holder.card.strokeWidth = (1f * density).toInt()
            holder.card.strokeColor = MaterialColors.getColor(
                holder.card,
                com.google.android.material.R.attr.colorOutlineVariant
            )
            holder.btnCover.setImageResource(R.drawable.ic_star_outline)
            ImageViewCompat.setImageTintList(
                holder.btnCover,
                ColorStateList.valueOf(Color.WHITE)
            )
        }

        holder.btnCover.contentDescription = if (isCover) "Đang là ảnh đại diện" else "Đặt làm ảnh đại diện"
        holder.btnCover.setOnClickListener { onToggleCover(image) }
        holder.btnDelete.setOnClickListener { onDelete(image) }
        holder.btnFocus.setOnClickListener { onAdjustFocus(image) }
        holder.card.setOnClickListener { onAdjustFocus(image) }
    }
}
