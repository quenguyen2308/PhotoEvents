package com.example.photoevents.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PorterDuff
import com.bumptech.glide.load.engine.bitmap_recycle.BitmapPool
import com.bumptech.glide.load.resource.bitmap.BitmapTransformation
import java.security.MessageDigest

/**
 * Glide Transformation ưu tiên hiển thị từ đỉnh ảnh (Top-Crop).
 *
 * Đối với ảnh chụp chân dung / ảnh dọc (portrait): đầu và khuôn mặt thường nằm ở nửa trên bức ảnh.
 * Nếu dùng centerCrop mặc định, phần trên sẽ bị cắt mất trán/mặt. Transformation này neo toạ độ
 * đỉnh của ảnh ở trên cùng (dy = 0), giữ trọn vẹn gương mặt người trong khung hình Bento.
 *
 * Đối với ảnh ngang (landscape): tự động căn giữa theo chiều ngang (dx = centered).
 */
class TopCropTransformation : BitmapTransformation() {

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

        // Căn giữa theo chiều ngang nếu ảnh rộng hơn khung (landscape)
        val dx = (outWidth - scaledWidth) * 0.5f

        // Neo đỉnh ảnh ở trên cùng (dy = 0) nếu ảnh cao hơn khung (portrait), giữ trọn vẹn khuôn mặt
        val dy = if (scaledHeight > outHeight) 0f else (outHeight - scaledHeight) * 0.5f

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
    }

    override fun equals(other: Any?): Boolean = other is TopCropTransformation
    override fun hashCode(): Int = ID.hashCode()

    companion object {
        private const val ID = "com.example.photoevents.ui.TopCropTransformation"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
