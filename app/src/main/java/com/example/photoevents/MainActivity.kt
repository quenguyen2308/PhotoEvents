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
import com.example.photoevents.ui.EventsAdapter
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
    private lateinit var recyclerView: RecyclerView
    private lateinit var swipeRefresh: SwipeRefreshLayout
    private lateinit var btnSort: MaterialButton
    private lateinit var headerNormal: View
    private lateinit var headerSelection: View
    private lateinit var txtSelectedCount: TextView

    // Kiểu sắp xếp chỉ là tuỳ chọn hiển thị trên máy này — lưu SharedPreferences, không đồng bộ Drive.
    private val prefs by lazy { getSharedPreferences("settings", MODE_PRIVATE) }
    private val sortFlow = MutableStateFlow(SortOption.NEWEST)
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
            onSelectionChanged = { count -> updateSelectionHeader(count) }
        )

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
            startActivity(Intent(this, AddEventActivity::class.java))
        }

        // Khôi phục kiểu sắp xếp đã chọn lần trước
        sortFlow.value = SortOption.fromName(prefs.getString(PREF_SORT, null))
        btnSort = findViewById(R.id.btnSort)
        btnSort.text = sortFlow.value.label
        btnSort.setOnClickListener { showSortDialog() }

        // Kết hợp dữ liệu Room + kiểu sắp xếp: đổi 1 trong 2 đều tự cập nhật danh sách
        lifecycleScope.launch {
            combine(
                AppDatabase.get(this@MainActivity).eventDao().observeAllWithImages(),
                sortFlow
            ) { events, sort -> sort.sort(events) }
                .collect { sorted ->
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

    private fun showSortDialog() {
        val options = SortOption.values()
        val labels = options.map { it.label }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Sắp xếp theo")
            .setSingleChoiceItems(labels, options.indexOf(sortFlow.value)) { dialog, which ->
                val chosen = options[which]
                if (chosen != sortFlow.value) {
                    scrollToTopOnNextList = true
                    sortFlow.value = chosen
                    prefs.edit().putString(PREF_SORT, chosen.name).apply()
                    btnSort.text = chosen.label
                }
                dialog.dismiss()
            }
            .setNegativeButton("Đóng", null)
            .show()
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
        AlertDialog.Builder(this)
            .setTitle("Xoá sự kiện")
            .setMessage("Xoá ${ids.size} sự kiện đã chọn? Toàn bộ ảnh trong đó cũng sẽ bị xoá.")
            .setPositiveButton("Xoá") { _, _ -> deleteSelected(ids) }
            .setNegativeButton("Huỷ", null)
            .show()
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

    companion object {
        private const val PREF_SORT = "sort_option"
    }
}
