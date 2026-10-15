package com.example.photoevents.ui

import android.app.Dialog
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.google.android.material.slider.Slider
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

class FocusAdjustBottomSheet : BottomSheetDialogFragment() {

    private var onSavedListener: ((Float, Float, Float) -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            if (bottomSheet != null) {
                val behavior = BottomSheetBehavior.from(bottomSheet)
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_focus_adjust, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val focusView = view.findViewById<FocusAdjustView>(R.id.focusAdjustView)
        val btnClose = view.findViewById<MaterialButton>(R.id.btnClose)

        val chipGroupRatio = view.findViewById<ChipGroup>(R.id.chipGroupRatio)
        val chipRatioSquare = view.findViewById<Chip>(R.id.chipRatioSquare)
        val chipRatioSub = view.findViewById<Chip>(R.id.chipRatioSub)
        val chipRatioPortrait = view.findViewById<Chip>(R.id.chipRatioPortrait)
        val chipRatioWide = view.findViewById<Chip>(R.id.chipRatioWide)

        val btnZoomOut = view.findViewById<MaterialButton>(R.id.btnZoomOut)
        val sliderZoom = view.findViewById<Slider>(R.id.sliderZoom)
        val btnZoomIn = view.findViewById<MaterialButton>(R.id.btnZoomIn)
        val txtZoomLevel = view.findViewById<TextView>(R.id.txtZoomLevel)

        val btnResetZoom = view.findViewById<MaterialButton>(R.id.btnResetZoom)
        val btnFullSide = view.findViewById<MaterialButton>(R.id.btnFullSide)
        val btnTogglePreview = view.findViewById<MaterialButton>(R.id.btnTogglePreview)

        val btnPresetFace = view.findViewById<MaterialButton>(R.id.btnPresetFace)
        val btnPresetCenter = view.findViewById<MaterialButton>(R.id.btnPresetCenter)
        val btnPresetLeft = view.findViewById<MaterialButton>(R.id.btnPresetLeft)
        val btnPresetRight = view.findViewById<MaterialButton>(R.id.btnPresetRight)
        val btnPresetBottom = view.findViewById<MaterialButton>(R.id.btnPresetBottom)

        val btnResetAll = view.findViewById<MaterialButton>(R.id.btnResetAll)
        val btnSave = view.findViewById<MaterialButton>(R.id.btnSave)

        // Đẩy nội dung lên trên thanh điều hướng hệ thống (Navigation bar) của Android
        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val basePadding = (16 * resources.displayMetrics.density).toInt()
            v.updatePadding(bottom = basePadding + navBars.bottom)
            insets
        }

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        val initFocusX = (arguments?.getFloat(ARG_FOCUS_X, 0.5f) ?: 0.5f).coerceIn(0.0f, 1.0f)
        val initFocusY = (arguments?.getFloat(ARG_FOCUS_Y, 0.25f) ?: 0.25f).coerceIn(0.0f, 1.0f)
        val initZoom = arguments?.getFloat(ARG_ZOOM, 1.0f) ?: 1.0f
        val aspectRatio = arguments?.getFloat(ARG_ASPECT_RATIO, 1.0f) ?: 1.0f

        // Đặt tỷ lệ ban đầu chuẩn xác theo layout Bento
        focusView.targetAspectRatio = aspectRatio

        // Chọn chip tương ứng với layout của ảnh trên Bento
        when {
            aspectRatio in 0.85f..1.15f -> chipRatioSquare.isChecked = true
            aspectRatio in 1.16f..1.45f -> chipRatioSub.isChecked = true
            aspectRatio < 0.85f -> chipRatioPortrait.isChecked = true
            aspectRatio > 1.45f -> chipRatioWide.isChecked = true
            else -> chipRatioSquare.isChecked = true
        }

        fun updatePreviewButtonUi() {
            if (focusView.isPreviewMode) {
                btnTogglePreview.text = "Chỉnh sửa"
                btnTogglePreview.setIconResource(R.drawable.ic_crop)
            } else {
                btnTogglePreview.text = "Xem trước"
                btnTogglePreview.setIconResource(R.drawable.ic_visibility)
            }
        }

        fun updateZoomUi(skipSlider: Boolean = false) {
            val percent = (focusView.zoom * 100).toInt()
            if (focusView.isFullSide()) {
                txtZoomLevel.text = "Toàn ảnh"
                btnFullSide.text = "Cắt khung"
            } else {
                txtZoomLevel.text = "$percent%"
                btnFullSide.text = "Toàn ảnh"
            }

            if (!skipSlider && !sliderZoom.isPressed) {
                val minZ = focusView.getMinZoom()
                val maxZ = focusView.maxZoom
                val fromVal = minOf(0.4f, minZ)
                sliderZoom.valueFrom = fromVal
                sliderZoom.valueTo = maxZ
                sliderZoom.value = focusView.zoom.coerceIn(fromVal, maxZ)
            }
        }

        focusView.onFocusChanged = { _, _, _ ->
            updateZoomUi()
        }

        focusView.onStateChanged = {
            updateZoomUi()
            updatePreviewButtonUi()
        }

        sliderZoom.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                focusView.zoom = value
                updateZoomUi(skipSlider = true)
            }
        }

        btnZoomIn.setOnClickListener {
            focusView.zoomIn()
            updateZoomUi()
        }

        btnZoomOut.setOnClickListener {
            focusView.zoomOut()
            updateZoomUi()
        }

        btnResetZoom.setOnClickListener {
            focusView.resetZoom()
            updateZoomUi()
        }

        btnFullSide.setOnClickListener {
            focusView.toggleFullSide()
            updateZoomUi()
        }

        btnTogglePreview.setOnClickListener {
            focusView.isPreviewMode = !focusView.isPreviewMode
            updatePreviewButtonUi()
        }

        // Căn nhanh thông minh
        btnPresetFace.setOnClickListener { focusView.alignFace() }
        btnPresetCenter.setOnClickListener { focusView.alignCenter() }
        btnPresetLeft.setOnClickListener { focusView.alignLeft() }
        btnPresetRight.setOnClickListener { focusView.alignRight() }
        btnPresetBottom.setOnClickListener { focusView.alignBottom() }

        // Đặt lại về toạ độ ban đầu
        btnResetAll.setOnClickListener {
            focusView.animateFocusTo(initFocusX, initFocusY, initZoom)
        }

        chipGroupRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipRatioSquare -> focusView.targetAspectRatio = 1.0f
                R.id.chipRatioSub -> focusView.targetAspectRatio = 1.33f
                R.id.chipRatioPortrait -> focusView.targetAspectRatio = 0.67f
                R.id.chipRatioWide -> focusView.targetAspectRatio = 1.6f
            }
            updateZoomUi()
        }

        if (!imagePath.isNullOrBlank()) {
            Glide.with(this)
                .asBitmap()
                .load(imagePath)
                .override(Target.SIZE_ORIGINAL, Target.SIZE_ORIGINAL)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        focusView.bitmap = resource
                        focusView.setFocus(initFocusX, initFocusY, initZoom)
                        updateZoomUi()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        focusView.bitmap = null
                    }
                })
        }

        updateZoomUi()
        updatePreviewButtonUi()

        val saveAction = {
            focusView.cancelAllAnimations()
            onSavedListener?.invoke(focusView.focusX, focusView.focusY, focusView.zoom)
            dismiss()
        }

        btnClose.setOnClickListener { dismiss() }
        btnSave.setOnClickListener { saveAction() }
    }

    companion object {
        private const val TAG = "FocusAdjustBottomSheet"
        private const val ARG_IMAGE_PATH = "arg_image_path"
        private const val ARG_FOCUS_X = "arg_focus_x"
        private const val ARG_FOCUS_Y = "arg_focus_y"
        private const val ARG_ZOOM = "arg_zoom"
        private const val ARG_ASPECT_RATIO = "arg_aspect_ratio"

        fun show(
            fm: FragmentManager,
            image: EventImage,
            aspectRatio: Float = 1.0f,
            onSaved: (newFocusX: Float, newFocusY: Float, newZoom: Float) -> Unit
        ) {
            val sheet = FocusAdjustBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, image.localImagePath ?: image.driveThumbnailLink)
                    putFloat(ARG_FOCUS_X, image.focusX)
                    putFloat(ARG_FOCUS_Y, image.focusY)
                    putFloat(ARG_ZOOM, image.focusZoom)
                    putFloat(ARG_ASPECT_RATIO, aspectRatio)
                }
                this.onSavedListener = onSaved
            }
            sheet.show(fm, TAG)
        }
    }
}
