package com.example.photoevents

import android.app.DatePickerDialog
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_add_event)

        val edtTitle = findViewById<EditText>(R.id.edtTitle)
        val edtNote = findViewById<EditText>(R.id.edtNote)
        previewContainer = findViewById(R.id.previewContainer)
        btnSave = findViewById(R.id.btnSave)
        txtEventDate = findViewById(R.id.txtEventDate)
        txtPickedCount = findViewById(R.id.txtPickedCount)
        txtEventDate.text = formatDate(selectedDate)
        updatePickedCount()

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

            isSaving = true
            btnSave.isEnabled = false

            val event = Event(title = title, note = edtNote.text.toString().trim(), eventDate = selectedDate)
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
