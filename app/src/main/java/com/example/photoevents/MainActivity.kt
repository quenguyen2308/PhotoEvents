package com.example.photoevents

import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.api.services.drive.DriveScopes
import com.example.photoevents.data.AppDatabase
import com.example.photoevents.drive.DriveSession
import com.example.photoevents.drive.SyncManager
import com.example.photoevents.ui.BentoDialogHelper
import com.example.photoevents.ui.EventsAdapter
import com.example.photoevents.ui.FocusAdjustBottomSheet
import com.example.photoevents.ui.SortOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Đăng nhập Google bắt buộc ở bản RELEASE (ép đăng nhập ngay khi mở app, như hành vi gốc).
 * Ở bản DEBUG (BuildConfig.DEBUG = true), việc đăng nhập là tuỳ chọn — app chạy bình thường
 * (thêm/xem/xoá sự kiện + ảnh cục bộ) trên máy ảo không có Google Play Services hoặc chưa cấu
 * hình OAuth Client ID; chỉ riêng tính năng đồng bộ Drive sẽ không hoạt động cho tới khi đăng
 * nhập (có thể đăng nhập bất cứ lúc nào bằng cách kéo-để-làm-mới danh sách).
 *
 * Không có nút Sync riêng: mỗi lần lưu/xoá sự kiện hoặc ảnh sẽ tự bắn sync ngầm (xem
 * [com.example.photoevents.drive.SyncScope]); kéo-để-làm-mới ở màn hình này chỉ dùng khi cần
 * chủ động lấy dữ liệu mới từ máy khác ngay lập tức.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var googleSignInClient: GoogleSignInClient
    private lateinit var adapter: EventsAdapter
    private lateinit var categoryAdapter: com.example.photoevents.ui.CategoriesAdapter
    private lateinit var recyclerCategories: RecyclerView
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnSort: MaterialButton
    private lateinit var headerNormal: View
    private lateinit var headerSelection: View
    private lateinit var txtSelectedCount: TextView

    // Kiểu sắp xếp chỉ là tuỳ chọn hiển thị trên máy này — lưu SharedPreferences, không đồng bộ Drive.
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val sortFlow = MutableStateFlow(SortOption.NEWEST)
    private val selectedCategoryFlow = MutableStateFlow(com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID)
    private val customCategoriesFlow = MutableStateFlow<Set<String>>(emptySet())
    private val categoryRevisionFlow = MutableStateFlow(0)
    private var scrollToTopOnNextList = false
    private lateinit var backPressedCallback: OnBackPressedCallback

    private val signInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        try {
            val account = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                .getResult(ApiException::class.java)
            if (account?.account != null) {
                DriveSession.clearSession()
                runSync()
            } else {
                swipeRefresh.isRefreshing = false
            }
        } catch (e: ApiException) {
            swipeRefresh.isRefreshing = false
            Toast.makeText(
                this,
                "Không đăng nhập được Google (mã ${e.statusCode}). Vẫn dùng app bình thường, chỉ không đồng bộ được.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestScopes(Scope(DriveScopes.DRIVE_APPDATA))
            .build()
        googleSignInClient = GoogleSignIn.getClient(this, gso)

        adapter = EventsAdapter(
            onClick = { item ->
                startActivity(
                    Intent(this, EventDetailActivity::class.java)
                        .putExtra(EXTRA_EVENT_ID, item.event.id)
                )
            },
            onSelectionChanged = { count -> updateSelectionHeader(count) },
            onAdjustFocus = { image, aspectRatio ->
                FocusAdjustBottomSheet.show(
                    supportFragmentManager,
                    image,
                    aspectRatio
                ) { newFocusX, newFocusY ->
                    lifecycleScope.launch {
                        val db = AppDatabase.get(this@MainActivity)
                        db.eventImageDao().updateFocus(image.id, newFocusX, newFocusY)
                        runSync()
                    }
                }
            }
        )

        // Thiết lập Category Card View ghim ở đầu trang
        categoryAdapter = com.example.photoevents.ui.CategoriesAdapter(
            onCategoryClick = { categoryItem ->
                if (selectedCategoryFlow.value != categoryItem.id) {
                    selectedCategoryFlow.value = categoryItem.id
                    categoryAdapter.selectedCategoryId = categoryItem.id
                    scrollToTopOnNextList = true
                }
            },
            onAddCategoryClick = {
                showAddCategoryDialog()
            },
            onCategoryLongClick = { categoryItem ->
                showCategoryOptionsDialog(categoryItem)
            },
            onEditCategoryClick = { categoryItem ->
                showEditCategoryDialog(categoryItem)
            }
        )

        recyclerCategories = findViewById(R.id.recyclerCategories)
        recyclerCategories.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        recyclerCategories.adapter = categoryAdapter

        // Khôi phục custom categories từ SharedPreferences
        val savedCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
        customCategoriesFlow.value = savedCustom

        recyclerView = findViewById(R.id.recyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter

        swipeRefresh = findViewById(R.id.swipeRefresh)
        swipeRefresh.setOnRefreshListener { ensureSignedInThenSync() }

        headerNormal = findViewById(R.id.headerNormal)
        headerSelection = findViewById(R.id.headerSelection)
        txtSelectedCount = findViewById(R.id.txtSelectedCount)
        findViewById<android.widget.ImageButton>(R.id.btnCancelSelection).setOnClickListener {
            adapter.clearSelection()
        }
        findViewById<android.widget.ImageButton>(R.id.btnDeleteSelected).setOnClickListener {
            confirmDeleteSelected()
        }

        // Đang chọn nhiều mà bấm back thì thoát chế độ chọn trước, không thoát app luôn
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                adapter.clearSelection()
            }
        }.also { callback -> backPressedCallback = callback })

        findViewById<View>(R.id.fabAdd).setOnClickListener {
            val intent = Intent(this, AddEventActivity::class.java)
            val currentCat = selectedCategoryFlow.value
            if (currentCat != com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID) {
                intent.putExtra(EXTRA_CATEGORY, currentCat)
            }
            startActivity(intent)
        }

        // Nút Quản lý danh mục
        findViewById<View>(R.id.btnManageCategories).setOnClickListener {
            startActivity(Intent(this, CategoryManagementActivity::class.java))
        }

        // Khôi phục kiểu sắp xếp đã chọn lần trước
        sortFlow.value = SortOption.fromName(prefs.getString(PREF_SORT, null))
        btnSort = findViewById(R.id.btnSort)
        btnSort.text = sortFlow.value.shortLabel
        btnSort.setOnClickListener { showSortDialog() }

        // Kết hợp dữ liệu Room + kiểu sắp xếp + danh mục lọc
        lifecycleScope.launch {
            combine(
                AppDatabase.get(this@MainActivity).eventDao().observeAllWithImages(),
                sortFlow,
                selectedCategoryFlow,
                customCategoriesFlow,
                categoryRevisionFlow
            ) { events, sort, selectedCat, customCats, _ ->
                val categoryItems = buildCategoryItems(events, customCats)
                val filtered = if (selectedCat == com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID) {
                    events
                } else {
                    events.filter { com.example.photoevents.data.CategoryHelper.matches(it.event.category, selectedCat) }
                }
                val sorted = sort.sort(filtered)
                Triple(categoryItems, sorted, selectedCat)
            }.collect { (catItems, sorted, selectedCat) ->
                categoryAdapter.selectedCategoryId = selectedCat
                categoryAdapter.submitList(catItems)

                adapter.submitList(sorted) {
                    if (scrollToTopOnNextList) {
                        recyclerView.scrollToPosition(0)
                        scrollToTopOnNextList = false
                    }
                }
                findViewById<android.view.View>(R.id.txtEmpty).visibility =
                    if (sorted.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            }
        }

        // Release: bắt buộc đăng nhập Google ngay khi mở app (như hành vi gốc).
        // Debug: bỏ qua — chỉ tự sync ngầm nếu máy đã từng đăng nhập sẵn, không ép đăng nhập,
        // để chạy/test thoải mái trên máy ảo chưa cấu hình OAuth hoặc không có Play Services.
        if (BuildConfig.DEBUG) {
            val account = GoogleSignIn.getLastSignedInAccount(this)
            if (account?.account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
                runSync()
            }
        } else {
            ensureSignedInThenSync()
        }
    }

    override fun onResume() {
        super.onResume()
        val savedCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
        customCategoriesFlow.value = savedCustom
        val deletedSet = com.example.photoevents.data.CategoryHelper.getDeletedCategories(this)
        val (_, currentClean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(selectedCategoryFlow.value)
        if (deletedSet.contains(currentClean.lowercase())) {
            selectedCategoryFlow.value = com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID
        }
        categoryRevisionFlow.value++
    }

    private fun showSortDialog() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val sheetView = layoutInflater.inflate(R.layout.sheet_sort_events, null)
        sheet.setContentView(sheetView)

        val container = sheetView.findViewById<android.widget.LinearLayout>(R.id.containerSortOptions)
        sheetView.findViewById<android.view.View>(R.id.btnCloseSortSheet)?.setOnClickListener { sheet.dismiss() }

        val sortIcons = mapOf(
            SortOption.EVENT_DATE_NEWEST to "📅",
            SortOption.EVENT_DATE_OLDEST to "🗓️",
            SortOption.NEWEST to "⏱️",
            SortOption.OLDEST to "⏳",
            SortOption.RECENTLY_UPDATED to "🔄",
            SortOption.TITLE_ASC to "🔤",
            SortOption.TITLE_DESC to "🔠",
            SortOption.MOST_IMAGES to "🖼️"
        )

        val currentSort = sortFlow.value
        for (option in SortOption.values()) {
            val itemView = layoutInflater.inflate(R.layout.item_sort_option, container, false)
            val card = itemView.findViewById<com.google.android.material.card.MaterialCardView>(R.id.cardSortOption)
            val txtIcon = itemView.findViewById<android.widget.TextView>(R.id.txtSortIcon)
            val txtLabel = itemView.findViewById<android.widget.TextView>(R.id.txtSortLabel)
            val imgChecked = itemView.findViewById<android.widget.ImageView>(R.id.imgSortChecked)

            txtIcon.text = sortIcons[option] ?: "🌸"
            txtLabel.text = option.label

            val isSelected = option == currentSort
            if (isSelected) {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.badge_pink_bg))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary)
                card.strokeWidth = (1.5f * resources.displayMetrics.density).toInt()
                txtLabel.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.colorPrimary))
                txtLabel.typeface = android.graphics.Typeface.DEFAULT_BOLD
                imgChecked.visibility = android.view.View.VISIBLE
            } else {
                card.setCardBackgroundColor(androidx.core.content.ContextCompat.getColor(this, R.color.surface))
                card.strokeColor = androidx.core.content.ContextCompat.getColor(this, R.color.sakura_border_soft)
                card.strokeWidth = (1f * resources.displayMetrics.density).toInt()
                txtLabel.setTextColor(androidx.core.content.ContextCompat.getColor(this, R.color.sakura_text_primary))
                txtLabel.typeface = android.graphics.Typeface.DEFAULT
                imgChecked.visibility = android.view.View.GONE
            }

            card.setOnClickListener {
                if (option != sortFlow.value) {
                    scrollToTopOnNextList = true
                    sortFlow.value = option
                    prefs.edit().putString(PREF_SORT, option.name).apply()
                    btnSort.text = option.shortLabel
                }
                sheet.dismiss()
            }
            container.addView(itemView)
        }

        sheet.show()
    }

    private fun updateSelectionHeader(count: Int) {
        val inSelection = adapter.selectionMode
        headerNormal.visibility = if (inSelection) View.GONE else View.VISIBLE
        headerSelection.visibility = if (inSelection) View.VISIBLE else View.GONE
        txtSelectedCount.text = "$count đã chọn"
        backPressedCallback.isEnabled = inSelection
    }

    private fun confirmDeleteSelected() {
        val ids = adapter.selectedEventIds()
        if (ids.isEmpty()) return

        val count = ids.size
        BentoDialogHelper.showConfirmDialog(
            context = this,
            title = "Xoá $count sự kiện đã chọn?",
            message = "Bạn có chắc chắn muốn xoá các sự kiện đã chọn không?",
            impactText = "Toàn bộ $count sự kiện và các ảnh bên trong sẽ bị xoá khỏi máy và đồng bộ lên Google Drive.",
            confirmText = "Xoá sự kiện",
            cancelText = "Huỷ bỏ",
            iconRes = R.drawable.ic_delete,
            isDanger = true
        ) {
            deleteSelected(ids)
        }
    }

    private fun deleteSelected(ids: Set<String>) {
        lifecycleScope.launch {
            val db = AppDatabase.get(this@MainActivity)
            val now = System.currentTimeMillis()
            // Xoá sự kiện + toàn bộ ảnh của chúng, để sync dọn file thật trên Drive
            db.eventDao().softDeleteAll(ids, now)
            db.eventImageDao().softDeleteForEvents(ids, now)
            adapter.clearSelection()
            runSync()
        }
    }

    /** Gọi khi người dùng kéo-để-làm-mới (hoặc tự động ở bản release lúc mở app). */
    private fun ensureSignedInThenSync() {
        val account = GoogleSignIn.getLastSignedInAccount(this)
        if (account?.account != null && GoogleSignIn.hasPermissions(account, Scope(DriveScopes.DRIVE_APPDATA))) {
            runSync()
            return
        }
        try {
            signInLauncher.launch(googleSignInClient.signInIntent)
        } catch (e: ActivityNotFoundException) {
            swipeRefresh.isRefreshing = false
            Toast.makeText(
                this,
                "Máy ảo này không có Google Play Services nên không đăng nhập được. Vẫn dùng app bình thường, chỉ không đồng bộ được.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun runSync() {
        val helper = DriveSession.getHelper(this)
        if (helper == null) {
            swipeRefresh.isRefreshing = false
            return
        }
        lifecycleScope.launch {
            try {
                val result = SyncManager(this@MainActivity, helper).sync()
                if (result.downloadFailed > 0) {
                    Toast.makeText(
                        this@MainActivity,
                        "Đã đồng bộ, nhưng ${result.downloadFailed} ảnh chưa tải về được: ${result.firstError}",
                        Toast.LENGTH_LONG
                    ).show()
                }
                // Đồng bộ thành công thì âm thầm, không hiện Toast — chỉ báo khi có lỗi.
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Lỗi đồng bộ: ${e.message}", Toast.LENGTH_LONG).show()
            } finally {
                swipeRefresh.isRefreshing = false
            }
        }
    }

    private fun getRenamedPresetsMap(): Map<String, String> {
        val set = prefs.getStringSet(PREF_RENAMED_PRESETS, emptySet()) ?: emptySet()
        val map = mutableMapOf<String, String>()
        for (entry in set) {
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                map[parts[0].lowercase()] = parts[1]
            }
        }
        return map
    }

    private fun buildCategoryItems(
        events: List<com.example.photoevents.data.EventWithImages>,
        customCategories: Set<String>
    ): List<com.example.photoevents.data.CategoryItem> {
        val deletedSet = com.example.photoevents.data.CategoryHelper.getDeletedCategories(this)
        val categoriesSet = linkedSetOf<String>()

        // 1. Thêm custom categories do người dùng tạo (trừ khi đã bị xoá)
        for (custom in customCategories) {
            val (_, clean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(custom)
            if (deletedSet.contains(clean.lowercase())) continue
            categoriesSet.add(com.example.photoevents.data.CategoryHelper.formatStandard(custom))
        }

        // 2. Thêm các categories thực tế đang có trong events (trừ khi đã bị xoá)
        events.forEach { item ->
            val cat = item.event.category.trim()
            if (cat.isNotEmpty()) {
                val (_, clean) = com.example.photoevents.data.CategoryHelper.extractIconAndName(cat)
                if (!deletedSet.contains(clean.lowercase())) {
                    categoriesSet.add(com.example.photoevents.data.CategoryHelper.formatStandard(cat))
                }
            }
        }

        // Đồng bộ danh mục từ sự kiện vào SharedPreferences
        com.example.photoevents.data.CategoryHelper.syncCategoriesFromEvents(
            this,
            events.map { it.event.category }
        )

        val items = mutableListOf<com.example.photoevents.data.CategoryItem>()
        // "Tất cả" luôn đứng đầu
        items.add(
            com.example.photoevents.data.CategoryItem(
                id = com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID,
                name = "Tất cả",
                icon = "🌸",
                count = events.size,
                isAll = true
            )
        )

        for (rawCat in categoriesSet) {
            val (icon, name) = com.example.photoevents.data.CategoryHelper.extractIconAndName(rawCat)
            val count = events.count { item ->
                com.example.photoevents.data.CategoryHelper.matches(item.event.category, name)
            }
            items.add(
                com.example.photoevents.data.CategoryItem(
                    id = name,
                    name = name,
                    icon = icon,
                    count = count,
                    isPreset = com.example.photoevents.data.CategoryHelper.isPreset(name)
                )
            )
        }

        // Nút "+" Thêm mục ở cuối danh sách
        items.add(
            com.example.photoevents.data.CategoryItem(
                id = "ACTION_ADD",
                name = "Thêm mục",
                icon = "➕",
                isAddAction = true
            )
        )

        return items
    }

    private fun showAddCategoryDialog() {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val txtPreview = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitCategory)

        txtTitle?.text = "🌸 Thêm danh mục mới"
        txtSub?.text = "Nhập tên danh mục và chọn biểu tượng emoji gợi nhớ"
        btnSubmit?.text = "🌸 Thêm danh mục"

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroup?.removeAllViews()
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
            chipGroup?.addView(chip)
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
            val text = edtName?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "Nhập tên danh mục", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val formatted = com.example.photoevents.data.CategoryHelper.addCategory(this, text)
            val (_, cleanName) = com.example.photoevents.data.CategoryHelper.extractIconAndName(formatted)
            val current = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
            customCategoriesFlow.value = current
            selectedCategoryFlow.value = cleanName
            Toast.makeText(this, "Đã thêm danh mục $cleanName", Toast.LENGTH_SHORT).show()
            sheet.dismiss()
        }

        sheet.show()
    }

    private fun showCategoryOptionsDialog(categoryItem: com.example.photoevents.data.CategoryItem) {
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_category_options, null)
        sheet.setContentView(view)

        view.findViewById<android.widget.TextView>(R.id.txtCategorySheetIcon)?.text = categoryItem.icon
        view.findViewById<android.widget.TextView>(R.id.txtCategorySheetTitle)?.text = categoryItem.name
        view.findViewById<android.widget.TextView>(R.id.txtCategorySheetCount)?.text =
            if (categoryItem.count == 1) "1 sự kiện trong danh mục này" else "${categoryItem.count} sự kiện trong danh mục này"

        view.findViewById<android.view.View>(R.id.btnCloseCategorySheet)?.setOnClickListener { sheet.dismiss() }

        view.findViewById<android.view.View>(R.id.cardRenameCategory)?.setOnClickListener {
            sheet.dismiss()
            showEditCategoryDialog(categoryItem)
        }

        view.findViewById<android.view.View>(R.id.cardCreateEventForCategory)?.setOnClickListener {
            sheet.dismiss()
            val intent = Intent(this, AddEventActivity::class.java).apply {
                putExtra(EXTRA_CATEGORY, categoryItem.name)
            }
            startActivity(intent)
        }

        view.findViewById<android.view.View>(R.id.cardManageCategories)?.setOnClickListener {
            sheet.dismiss()
            startActivity(Intent(this, CategoryManagementActivity::class.java))
        }

        val cardDelete = view.findViewById<android.view.View>(R.id.cardDeleteCategory)
        cardDelete?.setOnClickListener {
            sheet.dismiss()
            val available = com.example.photoevents.data.CategoryHelper.getAvailableCategories(this)
            val fallback = available.firstOrNull { !com.example.photoevents.data.CategoryHelper.matches(it, categoryItem.name) } ?: "🌸 Chung"
            val fallbackFormatted = com.example.photoevents.data.CategoryHelper.formatStandard(fallback)

            val message = "Bạn có chắc chắn muốn xoá vĩnh viễn danh mục này không?"
            val impactText = if (categoryItem.count > 0) {
                "Có ${categoryItem.count} sự kiện sẽ được tự động chuyển sang $fallbackFormatted an toàn."
            } else {
                null
            }

            BentoDialogHelper.showConfirmDialog(
                context = this,
                title = "Xoá danh mục '${categoryItem.name}'?",
                message = message,
                impactText = impactText,
                confirmText = "Xoá danh mục",
                cancelText = "Huỷ bỏ",
                iconRes = R.drawable.ic_delete,
                isDanger = true
            ) {
                lifecycleScope.launch {
                    val db = AppDatabase.get(this@MainActivity)
                    val updatedCount = com.example.photoevents.data.CategoryHelper.deleteCategory(
                        this@MainActivity,
                        categoryItem.name,
                        db
                    )
                    val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
                    customCategoriesFlow.value = currentCustom
                    if (com.example.photoevents.data.CategoryHelper.matches(selectedCategoryFlow.value, categoryItem.name)) {
                        selectedCategoryFlow.value = com.example.photoevents.data.CategoryHelper.ALL_CATEGORY_ID
                    }
                    val note = if (updatedCount > 0) " (đã chuyển $updatedCount sự kiện sang $fallbackFormatted)" else ""
                    Toast.makeText(this@MainActivity, "Đã xoá danh mục ${categoryItem.name}$note", Toast.LENGTH_SHORT).show()
                    runSync()
                }
            }
        }

        sheet.show()
    }

    private fun showEditCategoryDialog(categoryItem: com.example.photoevents.data.CategoryItem) {
        val currentDisplay = "${categoryItem.icon} ${categoryItem.name}"
        val sheet = com.google.android.material.bottomsheet.BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_edit_category, null)
        sheet.setContentView(view)

        val txtPreview = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryPreviewIcon)
        val txtTitle = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderTitle)
        val txtSub = view.findViewById<android.widget.TextView>(R.id.txtEditCategoryHeaderSubtitle)
        val edtName = view.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditCategoryName)
        val chipGroup = view.findViewById<com.google.android.material.chip.ChipGroup>(R.id.chipGroupEmojiSuggestions)
        val btnSubmit = view.findViewById<com.google.android.material.button.MaterialButton>(R.id.btnSubmitCategory)

        txtPreview?.text = categoryItem.icon
        txtTitle?.text = "✏️ Đổi tên danh mục"
        txtSub?.text = "Tất cả sự kiện trong danh mục này sẽ tự động cập nhật sang tên mới"
        btnSubmit?.text = "✨ Lưu thay đổi"
        edtName?.setText(currentDisplay)
        edtName?.setSelection(currentDisplay.length)

        val emojis = listOf("💖", "✈️", "👨‍👩‍👧", "🎉", "🎂", "☕", "🌿", "💼", "🏕️", "🎬", "🍜", "🏋️", "🛍️", "🐾", "🎨", "🎵")
        chipGroup?.removeAllViews()
        for (emoji in emojis) {
            val chip = com.google.android.material.chip.Chip(this).apply {
                text = emoji
                isCheckable = false
                textSize = 15f
                setChipBackgroundColorResource(R.color.badge_pink_bg)
                setOnClickListener {
                    val currentText = edtName?.text?.toString()?.trim().orEmpty()
                    val (_, cleanName) = com.example.photoevents.data.CategoryHelper.extractIconAndName(currentText)
                    val newName = "$emoji $cleanName"
                    edtName?.setText(newName)
                    edtName?.setSelection(newName.length)
                    txtPreview?.text = emoji
                }
            }
            chipGroup?.addView(chip)
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
            val text = edtName?.text?.toString()?.trim().orEmpty()
            if (text.isBlank()) {
                Toast.makeText(this, "Tên danh mục không được để trống", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val newFormatted = com.example.photoevents.data.CategoryHelper.formatStandard(text)
            val (_, newCleanName) = com.example.photoevents.data.CategoryHelper.extractIconAndName(newFormatted)

            if (newFormatted == currentDisplay) {
                sheet.dismiss()
                return@setOnClickListener
            }

            lifecycleScope.launch {
                val db = AppDatabase.get(this@MainActivity)
                com.example.photoevents.data.CategoryHelper.renameCategory(
                    this@MainActivity,
                    categoryItem.name,
                    text,
                    db
                )
                val currentCustom = prefs.getStringSet(PREF_CUSTOM_CATEGORIES, emptySet()) ?: emptySet()
                customCategoriesFlow.value = currentCustom
                selectedCategoryFlow.value = newCleanName
                categoryAdapter.selectedCategoryId = newCleanName

                Toast.makeText(this@MainActivity, "Đã đổi tên thành $newFormatted", Toast.LENGTH_SHORT).show()
                runSync()
            }
            sheet.dismiss()
        }

        sheet.show()
    }
    companion object {
        private const val PREF_SORT = "sort_option"
        private const val PREF_CUSTOM_CATEGORIES = "custom_categories"
        private const val PREF_RENAMED_PRESETS = "renamed_presets"
    }
}
