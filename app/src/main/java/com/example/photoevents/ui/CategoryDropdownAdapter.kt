package com.example.photoevents.ui

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.photoevents.R
import com.example.photoevents.data.CategoryHelper

class CategoryDropdownAdapter(
    context: Context,
    private val categories: MutableList<String>
) : ArrayAdapter<String>(context, R.layout.item_dropdown_category, R.id.txtDropdownItem, categories) {

    private val noOpFilter = object : Filter() {
        override fun performFiltering(constraint: CharSequence?): FilterResults {
            return FilterResults().apply {
                values = categories
                count = categories.size
            }
        }

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            notifyDataSetChanged()
        }

        override fun convertResultToString(resultValue: Any?): CharSequence {
            return resultValue?.toString() ?: ""
        }
    }

    override fun getFilter(): Filter = noOpFilter

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        return createView(position, convertView, parent)
    }

    private fun createView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(
            R.layout.item_dropdown_category,
            parent,
            false
        )

        val txtIcon = view.findViewById<TextView>(R.id.txtDropdownIcon)
        val txtName = view.findViewById<TextView>(R.id.txtDropdownItem)
        val frameIcon = view.findViewById<View>(R.id.frameDropdownIcon)

        val item = getItem(position) ?: ""
        if (item.contains("Thêm danh mục mới") || item.startsWith("➕")) {
            txtIcon.text = "➕"
            txtIcon.textSize = 15f
            txtName.text = "Thêm danh mục mới..."
            txtName.setTextColor(ContextCompat.getColor(context, R.color.sakura_pink))
            frameIcon.setBackgroundResource(R.drawable.bg_category_icon_circle)
        } else if (item.contains("Quản lý danh mục") || item.startsWith("⚙️")) {
            txtIcon.text = "⚙️"
            txtIcon.textSize = 15f
            txtName.text = "Quản lý danh mục..."
            txtName.setTextColor(ContextCompat.getColor(context, R.color.sakura_text_secondary))
            frameIcon.setBackgroundResource(R.drawable.bg_category_icon_circle)
        } else {
            val (icon, name) = CategoryHelper.extractIconAndName(item)
            txtIcon.text = icon
            txtIcon.textSize = 16f
            txtName.text = name
            txtName.setTextColor(ContextCompat.getColor(context, R.color.sakura_text_primary))
            frameIcon.setBackgroundResource(R.drawable.bg_category_icon_circle)
        }

        return view
    }
}
