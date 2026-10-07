package com.example.photoevents

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.example.photoevents.data.AppDatabase
import com.example.photoevents.data.CategoryHelper
import com.example.photoevents.drive.DriveSession
import com.example.photoevents.drive.SyncManager
import com.example.photoevents.drive.SyncScope
import com.example.photoevents.ui.BentoDialogHelper
import com.example.photoevents.ui.CategoryManagementAdapter
import com.example.photoevents.ui.CategoryManagementItem
import com.example.photoevents.ui.EmojiPickerHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CategoryManagementActivity : AppCompatActivity() {

    private lateinit var adapter: CategoryManagementAdapter
    private lateinit var txtCategoryCountHeader: TextView
    private lateinit var layoutEmptyCategories: View
    private lateinit var recyclerCategories: RecyclerView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_category_management)

        findViewById<ImageButton>(R.id.btnBack).setOnClickListener { finish() }

        findViewById<MaterialButton>(R.id.btnRestoreDefaults)?.visibility = View.GONE

        findViewById<View>(R.id.cardAddNewCategory).setOnClickListener {
            showAddCategoryDialog()
        }

        txtCategoryCountHeader = findViewById(R.id.txtCategoryCountHeader)
        layoutEmptyCategories = findViewById(R.id.layoutEmptyCategories)
        recyclerCategories = findViewById(R.id.recyclerCategories)

        adapter = CategoryManagementAdapter(
            onEditClick = { item -> showEditCategoryDialog(item) },
            onDeleteClick = { item -> showDeleteConfirmDialog(item) },
            onIconClick = { item -> showChangeEmojiDialog(item) }
        )

        recyclerCategories.layoutManager = LinearLayoutManager(this)
        recyclerCategories.adapter = adapter

        loadCategories()
    }

    override fun onResume() {
        super.onResume()
        loadCategories()
    }

    private fun showChangeEmojiDialog(item: CategoryManagementItem) {
        EmojiPickerHelper.showEmojiPicker(
            context = this,
            categoryName = item.name,
            currentEmoji = item.icon
        ) { newEmoji ->
            if (newEmoji == item.icon) return@showEmojiPicker
            lifecycleScope.launch {
                val db = AppDatabase.get(this@CategoryManagementActivity)
                CategoryHelper.updateCategoryIcon(
                    this@CategoryManagementActivity,
                    item.raw,
                    newEmoji,
                    db
                )
                Toast.makeText(this@CategoryManagementActivity, "Đã đổi biểu tượng sang $newEmoji", Toast.LENGTH_SHORT).show()
                loadCategories()
                runSync()
            }
        }
    }

    private fun loadCategories() {
        lifecycleScope.launch {
            val db = AppDatabase.get(this@CategoryManagementActivity)
            val allEvents = withContext(Dispatchers.IO) {
                db.eventDao().getAllIncludingDeleted().filter { !it.deleted }
            }
            CategoryHelper.syncCategoriesFromEvents(
                this@CategoryManagementActivity,
                allEvents.map { it.category }
            )
            val available = CategoryHelper.getAvailableCategories(this@CategoryManagementActivity)

            val items = available.map { rawCat ->
                val (icon, name) = CategoryHelper.extractIconAndName(rawCat)
                val count = allEvents.count { CategoryHelper.matches(it.category, name) }
                CategoryManagementItem(
                    raw = rawCat,
                    name = name,
                    icon = icon,
                    count = count,
                    isPreset = false
                )
            }

            adapter.submitList(items)
            txtCategoryCountHeader.text = "DANH SÁCH DANH MỤC (${items.size})"
            layoutEmptyCategories.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
            recyclerCategories.visibility = if (items.isEmpty()) View.GONE else View.VISIBLE
        }
    }

    private fun showAddCategoryDialog() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val framePreview = view.findViewById<View>(R.id.frameEditCategoryPreviewIcon)
        val txtPreview = view.findViewById<TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroupSuggestions = view.findViewById<ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitCategory)

        var selectedEmoji = "🏷️"
        txtPreview?.text = selectedEmoji
        txtTitle?.text = "🌸 Thêm danh mục mới"
        txtSub?.text = "Nhập tên danh mục và chọn biểu tượng emoji gợi nhớ"
        btnSubmit?.text = "🌸 Thêm danh mục"

        val openPicker = {
            val currentName = edtName?.text?.toString()?.trim().orEmpty().ifEmpty { "Mới" }
            EmojiPickerHelper.showEmojiPicker(this, currentName, selectedEmoji) { emoji ->
                selectedEmoji = emoji
                txtPreview?.text = emoji
            }
        }

        framePreview?.setOnClickListener { openPicker() }

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroupSuggestions?.removeAllViews()
        for (emoji in emojis) {
            val chip = Chip(this).apply {
                text = emoji
                isCheckable = false
                textSize = 15f
                setChipBackgroundColorResource(R.color.badge_pink_bg)
                setOnClickListener {
                    selectedEmoji = emoji
                    txtPreview?.text = emoji
                }
            }
            chipGroupSuggestions?.addView(chip)
        }
        val moreChip = Chip(this).apply {
            text = "➕ Khác"
            isCheckable = false
            textSize = 13f
            setChipBackgroundColorResource(R.color.badge_pink_bg)
            setOnClickListener { openPicker() }
        }
        chipGroupSuggestions?.addView(moreChip)

        edtName?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val (icon, _) = CategoryHelper.extractIconAndName(s?.toString())
                if (icon != "🏷️" && icon != "🌸") {
                    selectedEmoji = icon
                    txtPreview?.text = icon
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<View>(R.id.btnCloseEditCategorySheet)?.setOnClickListener { sheet.dismiss() }

        btnSubmit?.setOnClickListener {
            val text = edtName?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "Nhập tên danh mục", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val (typedIcon, cleanName) = CategoryHelper.extractIconAndName(text)
            val finalIcon = if (typedIcon != "🏷️" && typedIcon != "🌸") typedIcon else selectedEmoji
            CategoryHelper.addCategory(this, "$finalIcon $cleanName")
            Toast.makeText(this, "Đã thêm danh mục $cleanName", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
            loadCategories()
        }

        sheet.show()
    }

    private fun showEditCategoryDialog(item: CategoryManagementItem) {
        val currentDisplay = "${item.icon} ${item.name}"
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val framePreview = view.findViewById<View>(R.id.frameEditCategoryPreviewIcon)
        val txtPreview = view.findViewById<TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroup = view.findViewById<ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<MaterialButton>(R.id.btnSubmitCategory)

        var selectedEmoji = item.icon
        txtPreview?.text = selectedEmoji
        txtTitle?.text = "✏️ Đổi tên & Biểu tượng"
        txtSub?.text = "Tất cả sự kiện trong danh mục này sẽ tự động cập nhật an toàn"
        btnSubmit?.text = "✨ Lưu thay đổi"
        edtName?.setText(item.name)
        edtName?.setSelection(item.name.length)

        val openPicker = {
            val currentName = edtName?.text?.toString()?.trim().orEmpty().ifEmpty { item.name }
            EmojiPickerHelper.showEmojiPicker(this, currentName, selectedEmoji) { emoji ->
                selectedEmoji = emoji
                txtPreview?.text = emoji
            }
        }

        framePreview?.setOnClickListener { openPicker() }

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroup?.removeAllViews()
        for (emoji in emojis) {
            val chip = Chip(this).apply {
                text = emoji
                isCheckable = false
                textSize = 15f
                setChipBackgroundColorResource(R.color.badge_pink_bg)
                setOnClickListener {
                    selectedEmoji = emoji
                    txtPreview?.text = emoji
                }
            }
            chipGroup?.addView(chip)
        }
        val moreChip = Chip(this).apply {
            text = "➕ Khác"
            isCheckable = false
            textSize = 13f
            setChipBackgroundColorResource(R.color.badge_pink_bg)
            setOnClickListener { openPicker() }
        }
        chipGroup?.addView(moreChip)

        edtName?.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val (icon, _) = CategoryHelper.extractIconAndName(s?.toString())
                if (icon != "🏷️" && icon != "🌸") {
                    selectedEmoji = icon
                    txtPreview?.text = icon
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        view.findViewById<View>(R.id.btnCloseEditCategorySheet)?.setOnClickListener { sheet.dismiss() }

        btnSubmit?.setOnClickListener {
            val text = edtName?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "Tên danh mục không được để trống", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val (typedIcon, cleanName) = CategoryHelper.extractIconAndName(text)
            val finalIcon = if (typedIcon != "🏷️" && typedIcon != "🌸") typedIcon else selectedEmoji
            val newFormatted = "$finalIcon $cleanName"

            if (newFormatted == currentDisplay) {
                sheet.dismiss()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = AppDatabase.get(this@CategoryManagementActivity)
                CategoryHelper.renameCategory(this@CategoryManagementActivity, item.raw, newFormatted, db)
                val isOnlyIconChanged = CategoryHelper.matches(item.name, cleanName)
                val toastMsg = if (isOnlyIconChanged) "Đã đổi biểu tượng sang $finalIcon" else "Đã đổi tên thành $newFormatted"
                Toast.makeText(this@CategoryManagementActivity, toastMsg, Toast.LENGTH_SHORT).show()
                sheet.dismiss()
                loadCategories()
                runSync()
            }
        }

        sheet.show()
    }

    private fun showDeleteConfirmDialog(item: CategoryManagementItem) {
        val available = CategoryHelper.getAvailableCategories(this)
        val fallback = available.firstOrNull { !CategoryHelper.matches(it, item.name) } ?: "🌸 Chung"
        val fallbackFormatted = CategoryHelper.formatStandard(fallback)

        val message = "Bạn có chắc chắn muốn xoá vĩnh viễn danh mục này không?"
        val impactText = if (item.count > 0) {
            "Có ${item.count} sự kiện sẽ được tự động chuyển sang $fallbackFormatted an toàn."
        } else {
            null
        }

        BentoDialogHelper.showConfirmDialog(
            context = this,
            title = "Xoá danh mục '${item.name}'?",
            message = message,
            impactText = impactText,
            confirmText = "Xoá danh mục",
            cancelText = "Huỷ bỏ",
            iconRes = R.drawable.ic_delete,
            isDanger = true
        ) {
            lifecycleScope.launch {
                val db = AppDatabase.get(this@CategoryManagementActivity)
                val updatedCount = CategoryHelper.deleteCategory(this@CategoryManagementActivity, item.name, db)
                val note = if (updatedCount > 0) " (đã chuyển $updatedCount sự kiện sang $fallbackFormatted)" else ""
                Toast.makeText(this@CategoryManagementActivity, "Đã xoá danh mục ${item.name}$note", Toast.LENGTH_SHORT).show()
                loadCategories()
                runSync()
            }
        }
    }

    private fun showRestoreConfirmDialog() {
        BentoDialogHelper.showConfirmDialog(
            context = this,
            title = "Khôi phục danh mục gốc?",
            message = "Tất cả các danh mục mặc định ban đầu (Kỷ niệm, Du lịch, Gia đình, ...) sẽ được khôi phục lại.",
            impactText = "Các danh mục tuỳ chỉnh bạn đã tạo vẫn được giữ nguyên an toàn.",
            confirmText = "Khôi phục",
            cancelText = "Huỷ bỏ",
            iconRes = R.drawable.ic_restore,
            isDanger = false
        ) {
            CategoryHelper.restoreDefaultPresets(this)
            Toast.makeText(this, "Đã khôi phục các danh mục mặc định", Toast.LENGTH_SHORT).show()
            loadCategories()
        }
    }

    private fun runSync() {
        DriveSession.getHelper(this)?.let { helper ->
            SyncScope.scope.launch {
                runCatching { SyncManager(applicationContext, helper).sync() }
            }
        }
    }
}
