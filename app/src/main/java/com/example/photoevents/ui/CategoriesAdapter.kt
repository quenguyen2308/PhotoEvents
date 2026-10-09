package com.example.photoevents.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.example.photoevents.R
import com.example.photoevents.data.CategoryItem

class CategoriesAdapter(
    private val onCategoryClick: (CategoryItem) -> Unit,
    private val onAddCategoryClick: () -> Unit,
    private val onCategoryLongClick: ((CategoryItem) -> Unit)? = null,
    private val onEditCategoryClick: ((CategoryItem) -> Unit)? = null
) : ListAdapter<CategoryItem, CategoriesAdapter.VH>(DIFF) {

    var selectedCategoryId: String = "ALL"
        set(value) {
            if (field != value) {
                field = value
                notifyDataSetChanged()
            }
        }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val card: MaterialCardView = view.findViewById(R.id.cardCategory)
        val frameIcon: FrameLayout = view.findViewById(R.id.frameIcon)
        val txtIcon: TextView = view.findViewById(R.id.txtCategoryIcon)
        val txtName: TextView = view.findViewById(R.id.txtCategoryName)
        val txtCount: TextView = view.findViewById(R.id.txtCategoryCount)
        val btnEdit: android.widget.ImageButton = view.findViewById(R.id.btnEditCategory)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_category_card, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val context = holder.itemView.context

        holder.txtIcon.text = item.icon
        holder.txtName.text = item.name

        if (item.isAddAction) {
            holder.txtCount.text = "Tạo mới"
            holder.card.setCardBackgroundColor(ContextCompat.getColor(context, R.color.surface))
            holder.card.strokeColor = ContextCompat.getColor(context, R.color.colorPrimary)
            holder.card.cardElevation = 1f * context.resources.displayMetrics.density
            holder.txtName.setTextColor(ContextCompat.getColor(context, R.color.colorPrimary))
            holder.txtCount.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))

            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(ContextCompat.getColor(context, R.color.badge_pink_bg))
            }
            holder.frameIcon.background = iconBg

            holder.card.setOnClickListener { onAddCategoryClick() }
            holder.card.setOnLongClickListener(null)
            return
        }

        // Đếm số sự kiện
        holder.txtCount.text = if (item.count == 1) "1 sự kiện" else "${item.count} sự kiện"

        val isSelected = item.id == selectedCategoryId

        if (isSelected) {
            val primaryColor = ContextCompat.getColor(context, R.color.colorPrimary)
            holder.card.setCardBackgroundColor(primaryColor)
            holder.card.strokeColor = primaryColor
            holder.card.cardElevation = 3.5f * context.resources.displayMetrics.density

            // Vòng tròn icon mờ trắng sang trọng
            val selectedIconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.argb(45, 255, 255, 255))
            }
            holder.frameIcon.background = selectedIconBg

            holder.txtName.setTextColor(Color.WHITE)
            holder.txtCount.setTextColor(Color.parseColor("#FFE4EC"))

            if (!item.isAll && !item.isUncategorized && !item.isAddAction && onEditCategoryClick != null) {
                holder.btnEdit.visibility = View.VISIBLE
                holder.btnEdit.setOnClickListener { onEditCategoryClick.invoke(item) }
            } else {
                holder.btnEdit.visibility = View.GONE
            }
        } else {
            val surfaceColor = ContextCompat.getColor(context, R.color.surface)
            val outlineColor = ContextCompat.getColor(context, R.color.sakura_border_soft)
            holder.card.setCardBackgroundColor(surfaceColor)
            holder.card.strokeColor = outlineColor
            holder.card.cardElevation = 1f * context.resources.displayMetrics.density

            val defaultIconBg = ContextCompat.getDrawable(context, R.drawable.bg_category_icon_circle)
            holder.frameIcon.background = defaultIconBg

            holder.txtName.setTextColor(ContextCompat.getColor(context, R.color.on_surface))
            holder.txtCount.setTextColor(ContextCompat.getColor(context, R.color.on_surface_variant))
            holder.btnEdit.visibility = View.GONE
        }

        holder.card.setOnClickListener {
            onCategoryClick(item)
        }

        holder.card.setOnLongClickListener {
            if (!item.isAll && !item.isUncategorized && !item.isAddAction && onCategoryLongClick != null) {
                holder.card.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                onCategoryLongClick.invoke(item)
                true
            } else {
                false
            }
        }
    }

    companion object {
        val DIFF = object : DiffUtil.ItemCallback<CategoryItem>() {
            override fun areItemsTheSame(a: CategoryItem, b: CategoryItem) = a.id == b.id
            override fun areContentsTheSame(a: CategoryItem, b: CategoryItem) =
                a.name == b.name && a.icon == b.icon && a.count == b.count && a.isAll == b.isAll && a.isUncategorized == b.isUncategorized
        }
    }
}
