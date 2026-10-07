package com.example.photoevents

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.example.photoevents.data.AppDatabase
import com.example.photoevents.data.Event
import com.example.photoevents.data.normalizeToMidnight
import com.example.photoevents.data.EventImage
import com.example.photoevents.drive.DriveSession
import com.example.photoevents.drive.SyncManager
import com.example.photoevents.drive.SyncScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val EXTRA_CATEGORY = "extra_category"
private const val ACTION_ADD_NEW = "➕ Thêm danh mục mới..."
private const val ACTION_MANAGE = "⚙️ Quản lý danh mục..."

/**
 * Cho phép chọn NHIỀU ảnh cùng lúc (tối đa 10) bằng Photo Picker hệ thống.
 * Ảnh được copy vào bộ nhớ riêng của app ngay khi chọn (Uri từ picker chỉ có quyền đọc tạm thời).
 */
class AddEventActivity : AppCompatActivity() {

    private val pickedLocalPaths = mutableListOf<String>()
    private lateinit var previewContainer: LinearLayout
    private lateinit var btnSave: Button
    private lateinit var txtEventDate: TextView
    private lateinit var txtPickedCount: TextView
    private var isSaving = false
    // Mặc định hôm nay — được đưa về 00:00 để chỉ mang ý nghĩa "ngày", không lẫn giờ/phút/giây
    private var selectedDate: Long = normalizeToMidnight(System.currentTimeMillis())

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) copyAndPreview(uris) }

    private var lastSelectedCategory: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        val edtTitle = findViewById<EditText>(R.id.edtTitle)
        val edtNote = findViewById<EditText>(R.id.edtNote)
        val edtCategory = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.edtCategory)
        val chipGroupCategory = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupCategory)
        previewContainer = findViewById(R.id.previewContainer)
        btnSave = findViewById(R.id.btnSave)
        txtEventDate = findViewById(R.id.txtEventDate)
        txtPickedCount = findViewById(R.id.txtPickedCount)
        txtEventDate.text = formatDate(selectedDate)
        updatePickedCount()

        // Thiết lập danh mục ban đầu
        val available = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        val initialCategory = intent.getStringExtra(EXTRA_CATEGORY)?.takeIf { it.isNotBlank() }
            ?: available.firstOrNull() ?: ""
        lastSelectedCategory = if (initialCategory.isNotBlank()) {
            com.example.photoevents.data.CategoryHelper.formatStandard(initialCategory)
        } else ""

        // Cấu hình Dropdown Menu cho Category
        val categoryList = getCategoriesForDropdown()
        if (lastSelectedCategory.isNotBlank() && !categoryList.contains(lastSelectedCategory) && categoryList.isNotEmpty()) {
            val insertIdx = (categoryList.size - 2).coerceAtLeast(0)
            categoryList.add(insertIdx, lastSelectedCategory)
        }
        val dropdownAdapter = com.example.photoevents.ui.CategoryDropdownAdapter(
            this,
            categoryList
        )
        edtCategory.setAdapter(dropdownAdapter)
        edtCategory.setText(lastSelectedCategory, false)

        edtCategory.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            when (selected) {
                ACTION_ADD_NEW -> {
                    edtCategory.setText(lastSelectedCategory, false)
                    showAddNewCategoryDialog(edtCategory) { newCat ->
                        lastSelectedCategory = newCat
                    }
                }
                ACTION_MANAGE -> {
                    edtCategory.setText(lastSelectedCategory, false)
                    startActivity(Intent(this, CategoryManagementActivity::class.java))
                }
                else -> {
                    lastSelectedCategory = selected
                    updateChipSelection(chipGroupCategory, selected)
                }
            }
        }

        // Tạo danh sách chip danh mục gợi ý
        setupCategoryChips(chipGroupCategory, edtCategory, lastSelectedCategory)

        findViewById<android.view.View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<android.view.View>(R.id.btnPickDate).setOnClickListener { showDatePicker() }

        findViewById<android.view.View>(R.id.btnPickImage).setOnClickListener {
            pickImages.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }

        btnSave.setOnClickListener {
            // Chặn double-tap: bấm nhanh 2 lần trước khi màn hình kịp đóng sẽ chèn ảnh trùng
            // vào Room, dẫn tới bị upload trùng lên Drive ở lần sync sau.
            if (isSaving) return@setOnClickListener

            val title = edtTitle.text.toString().trim()
            if (title.isEmpty()) {
                Toast.makeText(this, "Nhập tên sự kiện", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val category = edtCategory.text.toString().trim()

            isSaving = true
            btnSave.isEnabled = false

            val event = Event(
                title = title,
                note = edtNote.text.toString().trim(),
                eventDate = selectedDate,
                category = category
            )
            lifecycleScope.launch {
                val db = AppDatabase.get(this@AddEventActivity)
                db.eventDao().upsert(event)
                pickedLocalPaths.forEachIndexed { index, path ->
                    db.eventImageDao().upsert(
                        EventImage(eventId = event.id, localImagePath = path, position = index)
                    )
                }
                // Bắn sync ngầm ngay — dùng SyncScope (sống theo cả app) chứ không phải
                // lifecycleScope, vì Activity này finish() ngay sau đây.
                DriveSession.getHelper(this@AddEventActivity)?.let { helper ->
                    SyncScope.scope.launch {
                        runCatching { SyncManager(applicationContext, helper).sync() }
                    }
                }
                finish()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshCategoryData()
    }

    private fun refreshCategoryData() {
        val edtCategory = findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.edtCategory) ?: return
        val chipGroupCategory = findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupCategory) ?: return

        val deletedSet = com.example.photoevents.data.CategoryHelper.getDeletedCategories(this)
        val (_, cleanSelected) = com.example.photoevents.data.CategoryHelper.extractIconAndName(lastSelectedCategory)
        val available = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        if (deletedSet.contains(cleanSelected.lowercase())) {
            lastSelectedCategory = available.firstOrNull() ?: ""
            edtCategory.setText(lastSelectedCategory, false)
        }

        val categoryList = getCategoriesForDropdown()
        if (lastSelectedCategory.isNotBlank() && !categoryList.contains(lastSelectedCategory) && categoryList.isNotEmpty()) {
            val insertIdx = (categoryList.size - 2).coerceAtLeast(0)
            categoryList.add(insertIdx, lastSelectedCategory)
        }
        val dropdownAdapter = com.example.photoevents.ui.CategoryDropdownAdapter(
            this,
            categoryList
        )
        edtCategory.setAdapter(dropdownAdapter)
        setupCategoryChips(chipGroupCategory, edtCategory, lastSelectedCategory)
    }

    private fun setupCategoryChips(
        chipGroup: com.google.android.material.chip.ChipGroup,
        edtCategory: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        selectedCategory: String
    ) {
        val available = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        chipGroup.removeAllViews()
        for (cat in available) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = com.example.photoevents.data.CategoryHelper.matches(cat, selectedCategory)
                setChipBackgroundColorResource(
                    if (isChecked) R.color.badge_pink_bg else R.color.surface
                )
                setOnClickListener {
                    edtCategory.setText(cat, false)
                    lastSelectedCategory = cat
                    updateChipSelection(chipGroup, cat)
                }
            }
            chipGroup.addView(chip)
        }
    }

    private fun updateChipSelection(
        chipGroup: com.google.android.material.chip.ChipGroup,
        selectedCategory: String
    ) {
        for (i in 0 until chipGroup.childCount) {
            (chipGroup.getChildAt(i) as? com.google.android.material.chip.Chip)?.let { c ->
                c.isChecked = com.example.photoevents.data.CategoryHelper.matches(c.text.toString(), selectedCategory)
                c.setChipBackgroundColorResource(
                    if (c.isChecked) R.color.badge_pink_bg else R.color.surface
                )
            }
        }
    }

    private fun getCategoriesForDropdown(): MutableList<String> {
        val list = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        list.add(ACTION_ADD_NEW)
        list.add(ACTION_MANAGE)
        return list
    }

    private fun showAddNewCategoryDialog(
        edtCategory: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        onCreated: (String) -> Unit
    ) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val txtPreview = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroupSuggestions = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitCategory)

        txtTitle?.text = "🌸 Thêm danh mục mới"
        txtSub?.text = "Nhập tên danh mục và chọn biểu tượng emoji gợi nhớ"
        btnSubmit?.text = "🌸 Thêm danh mục"

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroupSuggestions?.removeAllViews()
        for (emoji in emojis) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = emoji
                isCheckable = false
                textSize = 15f
                setChipBackgroundColorResource(R.color.badge_pink_bg)
                setOnClickListener {
                    val currentText = edtName?.text?.toString()?.trim().orEmpty()
                    val (_, cleanName) = com.example.photoevents.data.CategoryHelper.extractIconAndName(currentText)
                    val newName = if (cleanName == "Chung" || cleanName.isEmpty()) emoji else "$emoji $cleanName"
                    edtName?.setText(newName)
                    edtName?.setSelection(newName.length)
                    txtPreview?.text = emoji
                }
            }
            chipGroupSuggestions?.addView(chip)
        }

        edtName?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val (icon, _) = com.example.photoevents.data.CategoryHelper.extractIconAndName(s?.toString())
                txtPreview?.text = icon
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        view.findViewById<android.view.View>(R.id.btnCloseEditCategorySheet)?.setOnClickListener { sheet.dismiss() }

        btnSubmit?.setOnClickListener {
            val nameText = edtName?.text?.toString()?.trim().orEmpty()
            if (nameText.isNotBlank()) {
                val formatted = com.example.photoevents.data.CategoryHelper.addCategory(this, nameText)
                lastSelectedCategory = formatted
                refreshCategoryData()
                edtCategory.setText(formatted, false)
                onCreated(formatted)
                Toast.makeText(this, "Đã chọn danh mục $formatted", Toast.LENGTH_SHORT).show()
                sheet.dismiss()
            } else {
                Toast.makeText(this, "Nhập tên danh mục", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = selectedDate }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                selectedDate = picked.timeInMillis
                txtEventDate.text = formatDate(selectedDate)
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(millis)

    private fun updatePickedCount() {
        txtPickedCount.text = if (pickedLocalPaths.isEmpty()) {
            "Tối đa 10 ảnh"
        } else {
            "Đã chọn ${pickedLocalPaths.size} ảnh"
        }
    }

    private fun copyAndPreview(uris: List<Uri>) {
        lifecycleScope.launch {
            uris.forEach { uri ->
                val destPath = withContext(Dispatchers.IO) {
                    val dir = File(filesDir, "picked").apply { mkdirs() }
                    val dest = File(dir, "img_${System.currentTimeMillis()}_${(0..9999).random()}.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest.absolutePath
                }
                pickedLocalPaths.add(destPath)
                addPreviewThumbnail(destPath)
            }
        }
    }

    private fun addPreviewThumbnail(path: String) {
        val size = (88 * resources.displayMetrics.density).toInt()
        val margin = (4 * resources.displayMetrics.density).toInt()
        val radius = 14 * resources.displayMetrics.density

        val card = com.google.android.material.card.MaterialCardView(this).apply {
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                setMargins(margin, margin, margin, margin)
            }
            this.radius = radius
            cardElevation = 0f
            strokeWidth = (1 * resources.displayMetrics.density).toInt()
            setStrokeColor(com.google.android.material.color.MaterialColors.getColor(this, com.google.android.material.R.attr.colorOutlineVariant))
        }

        val frame = android.widget.FrameLayout(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
        }

        val imageView = ImageView(this).apply {
            layoutParams = android.widget.FrameLayout.LayoutParams(
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT,
                android.widget.FrameLayout.LayoutParams.MATCH_PARENT
            )
            scaleType = ImageView.ScaleType.CENTER_CROP
        }
        Glide.with(this).load(path).centerCrop().into(imageView)
        frame.addView(imageView)

        val btnDelete = android.widget.ImageButton(this).apply {
            val btnSize = (26 * resources.displayMetrics.density).toInt()
            val btnMargin = (4 * resources.displayMetrics.density).toInt()
            layoutParams = android.widget.FrameLayout.LayoutParams(btnSize, btnSize).apply {
                gravity = android.view.Gravity.TOP or android.view.Gravity.END
                setMargins(btnMargin, btnMargin, btnMargin, btnMargin)
            }
            setBackgroundResource(R.drawable.bg_circle_button)
            setImageResource(R.drawable.ic_close)
            val pad = (4 * resources.displayMetrics.density).toInt()
            setPadding(pad, pad, pad, pad)
            scaleType = ImageView.ScaleType.FIT_CENTER
            setColorFilter(android.graphics.Color.WHITE)
            contentDescription = "Xoá ảnh này"
            setOnClickListener {
                pickedLocalPaths.remove(path)
                previewContainer.removeView(card)
                updatePickedCount()
            }
        }
        frame.addView(btnDelete)
        card.addView(frame)
        previewContainer.addView(card)
        updatePickedCount()
    }
}
