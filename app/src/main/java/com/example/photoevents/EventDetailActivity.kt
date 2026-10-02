package com.example.photoevents

import android.app.DatePickerDialog
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

    private val pickImages = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(10)
    ) { uris -> if (uris.isNotEmpty()) addImages(uris) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_event_detail)

        eventId = intent.getStringExtra(EXTRA_EVENT_ID) ?: run { finish(); return }

        adapter = ImagesAdapter(
            onDelete = { image -> confirmDeleteImage(image) },
            onToggleCover = { image -> toggleCover(image) }
        )
        findViewById<RecyclerView>(R.id.recyclerImages).apply {
            layoutManager = GridLayoutManager(this@EventDetailActivity, 3)
            adapter = this@EventDetailActivity.adapter
        }
        ItemTouchHelper(ImageDragCallback(adapter) { persistImageOrder() })
            .attachToRecyclerView(findViewById(R.id.recyclerImages))

        findViewById<android.view.View>(R.id.btnBack)?.setOnClickListener { finish() }

        findViewById<TextView>(R.id.txtEventDate).setOnClickListener { showDatePicker() }

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
                    findViewById<TextView>(R.id.txtTitle).text = currentTitle
                    findViewById<TextView>(R.id.txtNote).text = currentNote
                    findViewById<TextView>(R.id.txtNote).visibility =
                        if (currentNote.isBlank()) android.view.View.GONE else android.view.View.VISIBLE
                    currentEventDate = eventWithImages.event.eventDate
                    findViewById<TextView>(R.id.txtEventDate).text =
                        "${formatDate(currentEventDate)} · Đổi ngày"
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
        val dialogView = layoutInflater.inflate(R.layout.dialog_edit_event, null)
        val edtTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditTitle)
        val edtNote = dialogView.findViewById<com.google.android.material.textfield.TextInputEditText>(R.id.edtEditNote)
        val layoutTitle = dialogView.findViewById<com.google.android.material.textfield.TextInputLayout>(R.id.layoutEditTitle)

        edtTitle.setText(currentTitle)
        edtNote.setText(currentNote)
        edtTitle.setSelection(edtTitle.text?.length ?: 0)

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle("Sửa thông tin sự kiện")
            .setView(dialogView)
            .setPositiveButton("Lưu", null)
            .setNegativeButton("Huỷ", null)
            .create()

        dialog.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newTitle = edtTitle.text?.toString()?.trim().orEmpty()
            val newNote = edtNote.text?.toString()?.trim().orEmpty()
            if (newTitle.isEmpty()) {
                layoutTitle.error = "Tên sự kiện không được để trống"
                return@setOnClickListener
            }
            layoutTitle.error = null

            lifecycleScope.launch {
                AppDatabase.get(this@EventDetailActivity).eventDao()
                    .updateEventInfo(eventId, newTitle, newNote)
                triggerSync()
            }
            dialog.dismiss()
        }
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
        AlertDialog.Builder(this)
            .setTitle("Xoá ảnh")
            .setMessage("Xoá ảnh này khỏi sự kiện?")
            .setPositiveButton("Xoá") { _, _ -> deleteImage(image) }
            .setNegativeButton("Huỷ", null)
            .show()
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
}
