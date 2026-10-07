package com.example.photoevents.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.photoevents.R

data class CategoryManagementItem(
    val raw: String,
    val name: String,
    val icon: String,
    val count: Int,
    val isPreset: Boolean
)

class CategoryManagementAdapter(
    private val onEditClick: (CategoryManagementItem) -> Unit,
    private val onDeleteClick: (CategoryManagementItem) -> Unit,
    private val onIconClick: (CategoryManagementItem) -> Unit = {}
) : ListAdapter<CategoryManagementItem, CategoryManagementAdapter.VH>(DIFF) {

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val frameIcon: View = view.findViewById(R.id.frameManageIcon)
        val txtIcon: TextView = view.findViewById(R.id.txtManageCategoryIcon)
        val txtName: TextView = view.findViewById(R.id.txtManageCategoryName)
        val txtBadge: TextView = view.findViewById(R.id.txtManageCategoryBadge)
        val txtCount: TextView = view.findViewById(R.id.txtManageEventCount)
        val btnEdit: ImageButton = view.findViewById(R.id.btnManageEdit)
        val btnDelete: ImageButton = view.findViewById(R.id.btnManageDelete)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(
            R.layout.item_category_management,
            parent,
            false
        )
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.txtIcon.text = item.icon
        holder.txtName.text = item.name
        holder.txtCount.text = if (item.count == 1) "1 sự kiện" else "${item.count} sự kiện"

        if (item.isPreset) {
            holder.txtBadge.text = "Mặc định"
            holder.txtBadge.setTextColor(ContextCompat.getColor(context, R.color.sakura_pink))
            holder.txtBadge.setBackgroundResource(R.drawable.bg_count_badge)
        } else {
            holder.txtBadge.text = "Tuỳ chỉnh"
            holder.txtBadge.setTextColor(ContextCompat.getColor(context, R.color.pastel_lavender_text))
            holder.txtBadge.setBackgroundResource(R.drawable.bg_count_badge)
        }

        holder.frameIcon.setOnClickListener { onIconClick(item) }
        holder.txtIcon.setOnClickListener { onIconClick(item) }
        holder.btnEdit.setOnClickListener { onEditClick(item) }
        holder.btnDelete.setOnClickListener { onDeleteClick(item) }
        holder.itemView.setOnClickListener { onEditClick(item) }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryManagementItem>() {
            override fun areItemsTheSame(a: CategoryManagementItem, b: CategoryManagementItem): Boolean =
                a.name.equals(b.name, ignoreCase = true)

            override fun areContentsTheSame(a: CategoryManagementItem, b: CategoryManagementItem): Boolean =
                a == b
        }
    }
}
