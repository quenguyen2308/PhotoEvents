package com.example.photoevents.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.nio.ByteBuffer
import java.security.MessageDigest

/**
 * Glide Transformation cho phép hiển thị ảnh với tiêu điểm (focus area) tùy chỉnh.
 *
 * @param focusX Toạ độ tiêu điểm ngang trong khoảng [0.0f, 1.0f]:
 *               0.0f: sát lề trái, 0.5f: chính giữa, 1.0f: sát lề phải.
 * @param focusY Toạ độ tiêu điểm dọc trong khoảng [0.0f, 1.0f]:
 *               0.0f: sát mép trên (TopCrop), 0.5f: chính giữa, 1.0f: sát mép dưới.
 */
class FocusCropTransformation(
    val focusX: Float = 0.5f,
    val focusY: Float = 0.25f
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (toTransform.width == outWidth && toTransform.height == outHeight) {
            return toTransform
        }

        val scaleX = outWidth.toFloat() / toTransform.width
        val scaleY = outHeight.toFloat() / toTransform.height
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = scale * toTransform.width
        val scaledHeight = scale * toTransform.height

        val clampedFocusX = focusX.coerceIn(0f, 1f)
        val clampedFocusY = focusY.coerceIn(0f, 1f)

        // Dịch chuyển ngang dựa theo focusX khi ảnh rộng hơn khung nhìn
        val dx = if (scaledWidth > outWidth) {
            (outWidth - scaledWidth) * clampedFocusX
        } else {
            (outWidth - scaledWidth) * 0.5f
        }

        // Dịch chuyển dọc dựa theo focusY khi ảnh cao hơn khung nhìn
        val dy = if (scaledHeight > outHeight) {
            (outHeight - scaledHeight) * clampedFocusY
        } else {
            (outHeight - scaledHeight) * 0.5f
        }

        val config = toTransform.config
        val result = pool.get(outWidth, outHeight, config)

        if (toTransform.hasAlpha()) {
            result.setHasAlpha(true)
            val clearCanvas = Canvas(result)
            clearCanvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)
        }

        val canvas = Canvas(result)
        val matrix = Matrix().apply {
            setScale(scale, scale)
            postTranslate(dx, dy)
        }
        val paint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
        canvas.drawBitmap(toTransform, matrix, paint)

        return result
    }

    override fun updateDiskCacheKey(messageDigest: MessageDigest) {
        messageDigest.update(ID_BYTES)
        val byteBuffer = ByteBuffer.allocate(8).putFloat(focusX).putFloat(focusY)
        messageDigest.update(byteBuffer.array())
    }

    override fun equals(other: Any?): Boolean {
        if (other is FocusCropTransformation) {
            return focusX == other.focusX && focusY == other.focusY
        }
        return false
    }

    override fun hashCode(): Int {
        var result = ID.hashCode()
        result = 31 * result + focusX.hashCode()
        result = 31 * result + focusY.hashCode()
        return result
    }

    companion object {
        private const val ID = "com.example.photoevents.ui.FocusCropTransformation"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
