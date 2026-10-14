package com.example.photoevents.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.target.Target
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

class FocusAdjustBottomSheet : BottomSheetDialogFragment() {

    private var onSavedListener: ((Float, Float, Float) -> Unit)? = null

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
        val chipGroupRatio = view.findViewById<ChipGroup>(R.id.chipGroupRatio)
        val chipRatioSquare = view.findViewById<Chip>(R.id.chipRatioSquare)
        val chipRatioSub = view.findViewById<Chip>(R.id.chipRatioSub)
        val chipRatioWide = view.findViewById<Chip>(R.id.chipRatioWide)

        val btnZoomOut = view.findViewById<MaterialButton>(R.id.btnZoomOut)
        val btnZoomIn = view.findViewById<MaterialButton>(R.id.btnZoomIn)
        val txtZoomLevel = view.findViewById<TextView>(R.id.txtZoomLevel)
        val btnResetZoom = view.findViewById<MaterialButton>(R.id.btnResetZoom)
        val btnFullSide = view.findViewById<MaterialButton>(R.id.btnFullSide)

        val btnPresetTop = view.findViewById<Button>(R.id.btnPresetTop)
        val btnPresetCenter = view.findViewById<Button>(R.id.btnPresetCenter)
        val btnPresetBottom = view.findViewById<Button>(R.id.btnPresetBottom)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)
        val btnSaveHeader = view.findViewById<Button>(R.id.btnSaveHeader)

        // Tự động đẩy nội dung lên trên thanh điều hướng hệ thống (Navigation bar) của Android
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

        // Đặt tỷ lệ ban đầu
        focusView.targetAspectRatio = aspectRatio

        // Chọn chip tương ứng
        when {
            aspectRatio in 0.85f..1.15f -> chipRatioSquare.isChecked = true
            aspectRatio in 1.16f..1.45f -> chipRatioSub.isChecked = true
            aspectRatio > 1.45f -> chipRatioWide.isChecked = true
            else -> chipRatioSquare.isChecked = true
        }

        fun updateZoomUi() {
            val percent = (focusView.zoom * 100).toInt()
            if (focusView.isFullSide()) {
                txtZoomLevel.text = "Full"
                btnFullSide.text = "Cắt khung"
            } else {
                txtZoomLevel.text = "$percent%"
                btnFullSide.text = "Toàn ảnh"
            }
        }

        fun updatePresetButtons() {
            val hasH = focusView.hasHorizontalSlack()
            val hasV = focusView.hasVerticalSlack()

            if (hasH && !hasV) {
                btnPresetTop.text = "Căn trái"
                btnPresetCenter.text = "Căn giữa"
                btnPresetBottom.text = "Căn phải"

                btnPresetTop.setOnClickListener { focusView.alignLeft() }
                btnPresetCenter.setOnClickListener { focusView.alignCenter() }
                btnPresetBottom.setOnClickListener { focusView.alignRight() }
            } else {
                btnPresetTop.text = "Căn đỉnh"
                btnPresetCenter.text = "Căn giữa"
                btnPresetBottom.text = "Căn đáy"

                btnPresetTop.setOnClickListener { focusView.alignTop() }
                btnPresetCenter.setOnClickListener { focusView.alignCenter() }
                btnPresetBottom.setOnClickListener { focusView.alignBottom() }
            }
        }

        focusView.onFocusChanged = { _, _, _ ->
            updatePresetButtons()
            updateZoomUi()
        }

        focusView.onStateChanged = {
            updatePresetButtons()
            updateZoomUi()
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

        chipGroupRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipRatioSquare -> focusView.targetAspectRatio = 1.0f
                R.id.chipRatioSub -> focusView.targetAspectRatio = 1.33f
                R.id.chipRatioWide -> focusView.targetAspectRatio = 1.6f
            }
            updatePresetButtons()
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
                        updatePresetButtons()
                        updateZoomUi()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        focusView.bitmap = null
                    }
                })
        }

        updatePresetButtons()
        updateZoomUi()

        val saveAction = {
            focusView.cancelZoomAnimation()
            onSavedListener?.invoke(focusView.focusX, focusView.focusY, focusView.zoom)
            dismiss()
        }

        btnCancel.setOnClickListener { dismiss() }
        btnSave.setOnClickListener { saveAction() }
        btnSaveHeader?.setOnClickListener { saveAction() }
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
