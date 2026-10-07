package com.example.photoevents

import android.app.DatePickerDialog
import android.content.Intent
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.example.photoevents.data.AppDatabase
import com.example.photoevents.data.EventImage
import com.example.photoevents.data.normalizeToMidnight
import com.example.photoevents.drive.DriveSession
import com.example.photoevents.drive.SyncManager
import com.example.photoevents.drive.SyncScope
import com.example.photoevents.ui.BentoDialogHelper
import com.example.photoevents.ui.FocusAdjustBottomSheet
import com.example.photoevents.ui.ImageDragCallback
import com.example.photoevents.ui.ImagesAdapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

const val EXTRA_EVENT_ID = "extra_event_id"

class EventDetailActivity : AppCompatActivity() {

    private lateinit var eventId: String
    private lateinit var adapter: ImagesAdapter
    private var explicitCoverId: String? = null
    private var currentEventDate: Long = normalizeToMidnight(System.currentTimeMillis())
    private var currentTitle: String = ""
    private var currentNote: String = ""
    private var currentCategory: String = ""

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) addImages(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: run { finish(); return }

        adapter = ImagesAdapter(
            onDelete = { image -> confirmDeleteImage(image) },
            onToggleCover = { image -> toggleCover(image) },
            onAdjustFocus = { image -> showFocusAdjuster(image) }
        )
        findViewById<RecyclerView>(R.id.recyclerImages).apply {
            layoutManager = GridLayoutManager(this@EventDetailActivity, 3)
            adapter = this@EventDetailActivity.adapter
        }
        ItemTouchHelper(ImageDragCallback(adapter) { persistImageOrder() })
            .attachToRecyclerView(findViewById(R.id.recyclerImages))

        findViewById<android.view.View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtEventDate).setOnClickListener { showDatePicker() }
        findViewById<TextView>(R.id.txtCategory).setOnClickListener { showEditTitleDialog() }

        findViewById<android.view.View>(R.id.btnEditTitle)?.setOnClickListener { showEditTitleDialog() }
        findViewById<TextView>(R.id.txtTitle).setOnClickListener { showEditTitleDialog() }

        findViewById<android.view.View>(R.id.btnAddImages).setOnClickListener {
            pickImages.launch(androidx.activity.result.PickVisualMediaRequest(
                ActivityResultContracts.PickVisualMedia.ImageOnly
            ))
        }

        lifecycleScope.launch {
            AppDatabase.get(this@EventDetailActivity).eventDao()
                .observeOneWithImages(eventId).collect { eventWithImages ->
                    if (eventWithImages == null) { finish(); return@collect }
                    currentTitle = eventWithImages.event.title
                    currentNote = eventWithImages.event.note
                    currentCategory = eventWithImages.event.category
                    findViewById<TextView>(R.id.txtTitle).text = currentTitle
                    findViewById<TextView>(R.id.txtNote).text = currentNote
                    findViewById<TextView>(R.id.txtNote).visibility =
                        if (currentNote.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
                    currentEventDate = eventWithImages.event.eventDate
                    findViewById<TextView>(R.id.txtEventDate).text =
                        "${formatDate(currentEventDate)} · Đổi ngày"
                    findViewById<TextView>(R.id.txtCategory).text =
                        com.example.photoevents.data.CategoryHelper.formatStandard(currentCategory)
                    explicitCoverId = eventWithImages.event.coverImageId
                    adapter.submitList(eventWithImages.visibleImages)
                    adapter.setCover(eventWithImages.coverImage?.id)
                }
        }

        // Mở chi tiết là lúc hay cần thấy dữ liệu mới nhất từ máy khác (ảnh vừa thêm/xoá nơi
        // khác), nên chủ động sync ngay khi mở màn hình này. Chạy hoàn toàn ngầm — lifecycleScope
        // tự huỷ coroutine này khi Activity đóng, không cần tự giới hạn thời gian chờ riêng.
        triggerSync()
    }

    private fun showEditTitleDialog() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_event, null)
        sheet.setContentView(dialogView)

        val edtTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditTitle)
        val edtNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditNote)
        val edtCategory = dialogView.findViewById<com.google.android.material.textfield.MaterialAutoCompleteTextView>(R.id.edtEditCategory)
        val chipGroupCategory = dialogView.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEditCategory)
        val layoutTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEditTitle)
        val btnSave = dialogView.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSaveEditEvent)
        dialogView.findViewById<android.view.View>(R.id.btnCloseEditSheet)?.setOnClickListener { sheet.dismiss() }

        edtTitle.setText(currentTitle)
        edtNote.setText(currentNote)
        val available = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        val initialCat = currentCategory.ifBlank { available.firstOrNull() ?: "" }
        val formattedInitial = if (initialCat.isNotBlank()) com.example.photoevents.data.CategoryHelper.formatStandard(initialCat) else ""
        edtTitle.setSelection(edtTitle.text?.length ?: 0)

        // Dropdown menu cho Category
        val categoryList = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
        val matchingInitial = categoryList.firstOrNull { com.example.photoevents.data.CategoryHelper.matches(it, formattedInitial) }
        val effectiveInitial = matchingInitial ?: formattedInitial
        if (effectiveInitial.isNotBlank() && !categoryList.contains(effectiveInitial) && categoryList.isNotEmpty()) {
            categoryList.add(effectiveInitial)
        }
        categoryList.add("➕ Thêm danh mục mới...")
        categoryList.add("⚙️ Quản lý danh mục...")
        val dropdownAdapter = com.example.photoevents.ui.CategoryDropdownAdapter(
            this,
            categoryList
        )
        edtCategory.setAdapter(dropdownAdapter)
        edtCategory.setText(effectiveInitial, false)

        edtCategory.setOnItemClickListener { parent, _, position, _ ->
            val selected = parent.getItemAtPosition(position).toString()
            when (selected) {
                "➕ Thêm danh mục mới..." -> {
                    edtCategory.setText(formattedInitial, false)
                    showAddNewCategoryInDetailDialog(edtCategory, categoryList, dropdownAdapter, chipGroupCategory)
                }
                "⚙️ Quản lý danh mục..." -> {
                    edtCategory.setText(formattedInitial, false)
                    sheet.dismiss()
                    startActivity(Intent(this, CategoryManagementActivity::class.java))
                }
                else -> {
                    for (i in 0 until chipGroupCategory.childCount) {
                        (chipGroupCategory.getChildAt(i) as? com.google.android.material.chip.Chip)?.let { c ->
                            c.isChecked = com.example.photoevents.data.CategoryHelper.matches(c.text.toString(), selected)
                            c.setChipBackgroundColorResource(
                                if (c.isChecked) R.color.badge_pink_bg else R.color.surface
                            )
                        }
                    }
                }
            }
        }

        chipGroupCategory.removeAllViews()
        for (cat in available) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = cat
                isCheckable = true
                isChecked = com.example.photoevents.data.CategoryHelper.matches(cat, initialCat)
                setChipBackgroundColorResource(
                    if (isChecked) R.color.badge_pink_bg else R.color.surface
                )
                setOnClickListener {
                    edtCategory.setText(cat, false)
                    for (i in 0 until chipGroupCategory.childCount) {
                        (chipGroupCategory.getChildAt(i) as? com.google.android.material.chip.Chip)?.let { c ->
                            c.isChecked = (c.text == cat)
                            c.setChipBackgroundColorResource(
                                if (c.isChecked) R.color.badge_pink_bg else R.color.surface
                            )
                        }
                    }
                }
            }
            chipGroupCategory.addView(chip)
        }

        btnSave.setOnClickListener {
            val newTitle = edtTitle.text?.toString()?.trim().orEmpty()
            val newNote = edtNote.text?.toString()?.trim().orEmpty()
            val newCategory = edtCategory.text?.toString()?.trim().orEmpty()
            if (newTitle.isEmpty()) {
                layoutTitle.error = "Tên sự kiện không được để trống"
                return@setOnClickListener
            }
            layoutTitle.error = null

            lifecycleScope.launch {
                AppDatabase.get(this@EventDetailActivity).eventDao()
                    .updateEventInfo(eventId, newTitle, newNote, newCategory)
                triggerSync()
            }
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showAddNewCategoryInDetailDialog(
        edtCategory: com.google.android.material.textfield.MaterialAutoCompleteTextView,
        categoryList: MutableList<String>,
        adapter: android.widget.ArrayAdapter<String>,
        chipGroup: com.google.android.material.chip.ChipGroup
    ) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val framePreview = view.findViewById<android.view.View>(R.id.frameEditCategoryPreviewIcon)
        val txtPreview = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroupSuggestions = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitCategory)

        var selectedEmoji = "🏷️"
        txtPreview?.text = selectedEmoji
        txtTitle?.text = "🌸 Thêm danh mục mới"
        txtSub?.text = "Nhập tên danh mục và chọn biểu tượng emoji gợi nhớ"
        btnSubmit?.text = "🌸 Thêm danh mục"

        val openPicker = {
            val currentName = edtName?.text?.toString()?.trim().orEmpty().ifEmpty { "Mới" }
            com.example.photoevents.ui.EmojiPickerHelper.showEmojiPicker(this, currentName, selectedEmoji) { emoji ->
                selectedEmoji = emoji
                txtPreview?.text = emoji
            }
        }

        framePreview?.setOnClickListener { openPicker() }

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroupSuggestions?.removeAllViews()
        for (emoji in emojis) {
            val chip = com.google.android.material.chip.Chip(this).apply {
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
        val moreChip = com.google.android.material.chip.Chip(this).apply {
            text = "➕ Khác"
            isCheckable = false
            textSize = 13f
            setChipBackgroundColorResource(R.color.badge_pink_bg)
            setOnClickListener { openPicker() }
        }
        chipGroupSuggestions?.addView(moreChip)

        edtName?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val (icon, _) = com.example.photoevents.data.CategoryHelper.extractIconAndName(s?.toString())
                if (icon != "🏷️" && icon != "🌸") {
                    selectedEmoji = icon
                    txtPreview?.text = icon
                }
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        view.findViewById<android.view.View>(R.id.btnCloseEditCategorySheet)?.setOnClickListener { sheet.dismiss() }

        btnSubmit?.setOnClickListener {
            val nameText = edtName?.text?.toString()?.trim().orEmpty()
            if (nameText.isNotBlank()) {
                val (typedIcon, cleanName) = com.example.photoevents.data.CategoryHelper.extractIconAndName(nameText)
                val finalIcon = if (typedIcon != "🏷️" && typedIcon != "🌸") typedIcon else selectedEmoji
                val formatted = com.example.photoevents.data.CategoryHelper.addCategory(this, "$finalIcon $cleanName")
                val index = (categoryList.size - 2).coerceAtLeast(0)
                categoryList.add(index, formatted)
                adapter.notifyDataSetChanged()

                edtCategory.setText(formatted, false)
                val chip = com.google.android.material.chip.Chip(this).apply {
                    this.text = formatted
                    isCheckable = true
                    isChecked = true
                    setChipBackgroundColorResource(R.color.badge_pink_bg)
                    setOnClickListener { edtCategory.setText(formatted, false) }
                }
                chipGroup.addView(chip)
                sheet.dismiss()
            } else {
                Toast.makeText(this, "Nhập tên danh mục", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show()
    }

    private fun showDatePicker() {
        val cal = Calendar.getInstance().apply { timeInMillis = currentEventDate }
        DatePickerDialog(
            this,
            { _, year, month, day ->
                val picked = Calendar.getInstance().apply { set(year, month, day, 0, 0, 0); set(Calendar.MILLISECOND, 0) }
                lifecycleScope.launch {
                    AppDatabase.get(this@EventDetailActivity).eventDao()
                        .setEventDate(eventId, picked.timeInMillis)
                    triggerSync()
                }
            },
            cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun formatDate(millis: Long): String =
        SimpleDateFormat("dd/MM/yyyy", Locale("vi", "VN")).format(millis)

    /** Gọi khi thả tay sau khi kéo-thả đổi vị trí ảnh — ghi lại thứ tự mới (field `position`) và sync. */
    private fun persistImageOrder() {
        lifecycleScope.launch {
            val now = System.currentTimeMillis()
            val reordered = adapter.currentList().mapIndexed { index, image ->
                image.copy(position = index, updatedAt = now)
            }
            AppDatabase.get(this@EventDetailActivity).eventImageDao().upsertAll(reordered)
            triggerSync()
        }
    }

    /** Bấm sao ở ảnh khác = đặt làm ảnh đại diện; bấm lại sao của ảnh đại diện đã chọn = bỏ chọn (tự dùng ảnh đầu tiên). */
    private fun toggleCover(image: EventImage) {
        lifecycleScope.launch {
            val newCover = if (explicitCoverId == image.id) null else image.id
            AppDatabase.get(this@EventDetailActivity).eventDao().setCover(eventId, newCover)
            triggerSync()
        }
    }

    private fun confirmDeleteImage(image: EventImage) {
        BentoDialogHelper.showConfirmDialog(
            context = this,
            title = "Xoá ảnh này?",
            message = "Bạn có chắc chắn muốn xoá ảnh khỏi sự kiện này không?",
            impactText = "Ảnh này sẽ bị xoá khỏi sự kiện và đồng bộ lên Google Drive.",
            confirmText = "Xoá ảnh",
            cancelText = "Huỷ bỏ",
            iconRes = R.drawable.ic_delete,
            isDanger = true
        ) {
            deleteImage(image)
        }
    }

    private fun deleteImage(image: EventImage) {
        lifecycleScope.launch {
            // Soft-delete trước để UI cập nhật ngay; việc xoá file thật trên Drive xảy ra lúc sync.
            AppDatabase.get(this@EventDetailActivity).eventImageDao().softDelete(image.id)
            triggerSync()
        }
    }

    private fun addImages(uris: List<Uri>) {
        lifecycleScope.launch {
            val dao = AppDatabase.get(this@EventDetailActivity).eventImageDao()
            val startPosition = dao.countForEvent(eventId)
            val dir = File(filesDir, "picked").apply { mkdirs() }

            uris.forEachIndexed { index, uri ->
                val destPath = withContext(Dispatchers.IO) {
                    val dest = File(dir, "img_${System.currentTimeMillis()}_$index.jpg")
                    contentResolver.openInputStream(uri)?.use { input ->
                        dest.outputStream().use { output -> input.copyTo(output) }
                    }
                    dest.absolutePath
                }
                dao.upsert(
                    EventImage(
                        eventId = eventId,
                        localImagePath = destPath,
                        position = startPosition + index
                    )
                )
            }
            triggerSync()
        }
    }

    private fun triggerSync() {
        // SyncScope (sống theo cả app) chứ không phải lifecycleScope — người dùng có thể bấm
        // back rời màn hình này ngay sau khi xoá/thêm ảnh hoặc đổi ảnh đại diện.
        val helper = DriveSession.getHelper(this) ?: return
        SyncScope.scope.launch {
            runCatching { SyncManager(applicationContext, helper).sync() }
        }
    }

    private fun showFocusAdjuster(image: EventImage) {
        val isCover = image.id == explicitCoverId || (explicitCoverId == null && adapter.currentList().firstOrNull()?.id == image.id)
        val aspectRatio = if (isCover) 1.0f else 1.33f
        FocusAdjustBottomSheet.show(
            supportFragmentManager,
            image,
            aspectRatio
        ) { newFocusX, newFocusY ->
            lifecycleScope.launch {
                val db = AppDatabase.get(this@EventDetailActivity)
                db.eventImageDao().updateFocus(image.id, newFocusX, newFocusY)
                triggerSync()
            }
        }
    }
}
