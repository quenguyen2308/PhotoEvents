package com.example.photoevents.ui

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView

/**
 * Cho phép GIỮ (long-press) rồi kéo 1 ô ảnh trong [ImagesAdapter] để đổi vị trí — dùng với
 * GridLayoutManager nên cho kéo cả 4 hướng (lên/xuống/trái/phải), không dùng vuốt-để-xoá
 * (đã có nút xoá riêng trên mỗi ảnh).
 *
 * [onDragFinished] chỉ gọi 1 lần khi THẢ tay (không gọi liên tục trong lúc kéo qua từng ô) —
 * đúng lúc cần lưu thứ tự mới xuống Room + trigger sync.
 */
class ImageDragCallback(
    private val adapter: ImagesAdapter,
    private val onDragFinished: () -> Unit
) : ItemTouchHelper.SimpleCallback(
    ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
    0 // không cho vuốt-để-xoá
) {

    private var didMove = false

    override fun isLongPressDragEnabled() = true

    override fun onMove(
        recyclerView: RecyclerView,
        viewHolder: RecyclerView.ViewHolder,
        target: RecyclerView.ViewHolder
    ): Boolean {
        val from = viewHolder.bindingAdapterPosition
        val to = target.bindingAdapterPosition
        if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) return false
        adapter.moveItem(from, to)
        didMove = true
        return true
    }

    override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
        // không dùng swipe ở đây (swipeFlags = 0 nên hàm này thực chất không bao giờ được gọi)
    }

    override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
        super.clearView(recyclerView, viewHolder)
        if (didMove) {
            didMove = false
            onDragFinished()
        }
    }
}
