package com.example.photoevents.ui

import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.FragmentManager
import com.bumptech.glide.Glide
import com.bumptech.glide.request.target.CustomTarget
import com.bumptech.glide.request.transition.Transition
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import com.example.photoevents.R
import com.example.photoevents.data.EventImage

class FocusAdjustBottomSheet : BottomSheetDialogFragment() {

    private var onSavedListener: ((Float, Float) -> Unit)? = null

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

        val btnPresetTop = view.findViewById<Button>(R.id.btnPresetTop)
        val btnPresetCenter = view.findViewById<Button>(R.id.btnPresetCenter)
        val btnPresetBottom = view.findViewById<Button>(R.id.btnPresetBottom)
        val btnCancel = view.findViewById<Button>(R.id.btnCancel)
        val btnSave = view.findViewById<Button>(R.id.btnSave)

        val imagePath = arguments?.getString(ARG_IMAGE_PATH)
        val initFocusX = arguments?.getFloat(ARG_FOCUS_X, 0.5f) ?: 0.5f
        val initFocusY = arguments?.getFloat(ARG_FOCUS_Y, 0.25f) ?: 0.25f
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

        fun updatePresetButtons() {
            if (focusView.hasHorizontalSlack() && !focusView.hasVerticalSlack()) {
                btnPresetTop.text = "Căn trái"
                btnPresetCenter.text = "Căn giữa"
                btnPresetBottom.text = "Căn phải"

                btnPresetTop.setOnClickListener { focusView.setFocus(0.0f, focusView.focusY) }
                btnPresetCenter.setOnClickListener { focusView.setFocus(0.5f, 0.5f) }
                btnPresetBottom.setOnClickListener { focusView.setFocus(1.0f, focusView.focusY) }
            } else {
                btnPresetTop.text = "Căn đỉnh"
                btnPresetCenter.text = "Căn giữa"
                btnPresetBottom.text = "Căn đáy"

                btnPresetTop.setOnClickListener { focusView.setFocus(focusView.focusX, 0.0f) }
                btnPresetCenter.setOnClickListener { focusView.setFocus(0.5f, 0.5f) }
                btnPresetBottom.setOnClickListener { focusView.setFocus(focusView.focusX, 1.0f) }
            }
        }

        chipGroupRatio.setOnCheckedStateChangeListener { _, checkedIds ->
            when (checkedIds.firstOrNull()) {
                R.id.chipRatioSquare -> focusView.targetAspectRatio = 1.0f
                R.id.chipRatioSub -> focusView.targetAspectRatio = 1.33f
                R.id.chipRatioWide -> focusView.targetAspectRatio = 1.6f
            }
            updatePresetButtons()
        }

        if (!imagePath.isNullOrBlank()) {
            Glide.with(this)
                .asBitmap()
                .load(imagePath)
                .into(object : CustomTarget<Bitmap>() {
                    override fun onResourceReady(resource: Bitmap, transition: Transition<in Bitmap>?) {
                        focusView.bitmap = resource
                        focusView.setFocus(initFocusX, initFocusY)
                        updatePresetButtons()
                    }

                    override fun onLoadCleared(placeholder: Drawable?) {
                        focusView.bitmap = null
                    }
                })
        }

        updatePresetButtons()

        btnCancel.setOnClickListener { dismiss() }
        btnSave.setOnClickListener {
            onSavedListener?.invoke(focusView.focusX, focusView.focusY)
            dismiss()
        }
    }

    companion object {
        private const val TAG = "FocusAdjustBottomSheet"
        private const val ARG_IMAGE_PATH = "arg_image_path"
        private const val ARG_FOCUS_X = "arg_focus_x"
        private const val ARG_FOCUS_Y = "arg_focus_y"
        private const val ARG_ASPECT_RATIO = "arg_aspect_ratio"

        fun show(
            fm: FragmentManager,
            image: EventImage,
            aspectRatio: Float = 1.0f,
            onSaved: (newFocusX: Float, newFocusY: Float) -> Unit
        ) {
            val sheet = FocusAdjustBottomSheet().apply {
                arguments = Bundle().apply {
                    putString(ARG_IMAGE_PATH, image.localImagePath ?: image.driveThumbnailLink)
                    putFloat(ARG_FOCUS_X, image.focusX)
                    putFloat(ARG_FOCUS_Y, image.focusY)
                    putFloat(ARG_ASPECT_RATIO, aspectRatio)
                }
                this.onSavedListener = onSaved
            }
            sheet.show(fm, TAG)
        }
    }
}
