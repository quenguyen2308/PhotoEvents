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
    val focusY: Float = 0.25f,
    val focusZoom: Float = 1.0f
) : BitmapTransformation() {

    override fun transform(
        pool: BitmapPool,
        toTransform: Bitmap,
        outWidth: Int,
        outHeight: Int
    ): Bitmap {
        if (outWidth <= 0 || outHeight <= 0) {
            return toTransform
        }

        if (toTransform.width == outWidth && toTransform.height == outHeight && focusZoom == 1.0f) {
            return toTransform
        }

        val baseScaleX = outWidth.toFloat() / toTransform.width
        val baseScaleY = outHeight.toFloat() / toTransform.height
        val baseScale = maxOf(baseScaleX, baseScaleY)
        val zoom = focusZoom.coerceIn(0.05f, 10.0f)
        val scale = baseScale * zoom

        val scaledWidth = scale * toTransform.width
        val scaledHeight = scale * toTransform.height

        val slackX = scaledWidth - outWidth
        val slackY = scaledHeight - outHeight

        // Luôn giới hạn focusX và focusY trong [0.0f, 1.0f] để đảm bảo vùng crop luôn nằm trọn vẹn trong ảnh,
        // tuyệt đối không bị trượt ra ngoài làm mất đầu hoặc lộ khoảng trống.
        val clampedFocusX = focusX.coerceIn(0.0f, 1.0f)
        val clampedFocusY = focusY.coerceIn(0.0f, 1.0f)

        // Dịch chuyển ngang: khi ảnh rộng hơn khung (slackX > 0), dịch theo focusX;
        // khi ảnh vừa khít hoặc hẹp hơn (slackX <= 0), căn giữa tuyệt đối không để lộ khoảng trống.
        val dx = if (slackX > 0f) {
            -clampedFocusX * slackX
        } else {
            (outWidth - scaledWidth) * 0.5f
        }

        // Dịch chuyển dọc: khi ảnh cao hơn khung (slackY > 0), dịch theo focusY;
        // khi ảnh vừa khít hoặc thấp hơn (slackY <= 0), căn giữa tuyệt đối không để lộ khoảng trống.
        val dy = if (slackY > 0f) {
            -clampedFocusY * slackY
        } else {
            (outHeight - scaledHeight) * 0.5f
        }

        val config: Bitmap.Config? = toTransform.config
        val safeConfig = if (config == null || config == Bitmap.Config.HARDWARE) Bitmap.Config.ARGB_8888 else config
        val result = pool.get(outWidth, outHeight, safeConfig)

        result.setHasAlpha(true)
        val canvas = Canvas(result)
        canvas.drawColor(android.graphics.Color.TRANSPARENT, PorterDuff.Mode.CLEAR)

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
        val byteBuffer = ByteBuffer.allocate(12).putFloat(focusX.coerceIn(0.0f, 1.0f)).putFloat(focusY.coerceIn(0.0f, 1.0f)).putFloat(focusZoom)
        messageDigest.update(byteBuffer.array())
    }

    override fun equals(other: Any?): Boolean {
        if (other is FocusCropTransformation) {
            return focusX == other.focusX && focusY == other.focusY && focusZoom == other.focusZoom
        }
        return false
    }

    override fun hashCode(): Int {
        var result = ID.hashCode()
        result = 31 * result + focusX.hashCode()
        result = 31 * result + focusY.hashCode()
        result = 31 * result + focusZoom.hashCode()
        return result
    }

    companion object {
        private const val ID = "com.example.photoevents.ui.FocusCropTransformation.v7"
        private val ID_BYTES = ID.toByteArray(Charsets.UTF_8)
    }
}
