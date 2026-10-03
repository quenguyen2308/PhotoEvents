package com.example.photoevents.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/**
 * Custom View cho phép người dùng chạm và kéo (drag) bức ảnh trực tiếp
 * để chuyển vùng nhìn / tiêu điểm (focus area).
 *
 * Mặc định khung nhìn là hình vuông (1:1) tương thích hoàn hảo với ô Bento chính của Event.
 * Vùng ảnh ngoài khung nhìn được phủ một lớp mờ để người dùng dễ dàng căn chỉnh.
 */
class FocusAdjustView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            recalculateFrame(width, height)
            invalidate()
        }

    var focusX: Float = 0.5f
        private set

    var focusY: Float = 0.25f
        private set

    /** Tỷ lệ khung nhìn mục tiêu (width / height) - Mặc định 1.0f (Vuông theo Bento chính) */
    var targetAspectRatio: Float = 1.0f
        set(value) {
            field = if (value > 0f) value else 1.0f
            recalculateFrame(width, height)
            invalidate()
        }

    var onFocusChanged: ((Float, Float) -> Unit)? = null

    private val frameRect = RectF()
    private val clipPath = Path()
    private val dimPath = Path()
    private val viewClipPath = Path()
    private val drawMatrix = Matrix()

    private val backgroundPaint = Paint().apply {
        color = Color.parseColor("#18181B")
    }
    private val viewRadius = 16f * resources.displayMetrics.density

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG or Paint.DITHER_FLAG)
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.argb(165, 0, 0, 0) // ~65% black scrim ngoài khung nhìn
    }
    private val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f * resources.displayMetrics.density
        color = Color.parseColor("#E57373") // Sakura primary accent
    }
    private val outerBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.parseColor("#33FFFFFF")
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1f * resources.displayMetrics.density
        color = Color.argb(90, 255, 255, 255)
    }
    private val cornerRadius = 18f * resources.displayMetrics.density

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var isDragging = false

    fun setFocus(x: Float, y: Float) {
        focusX = x.coerceIn(0f, 1f)
        focusY = y.coerceIn(0f, 1f)
        invalidate()
        onFocusChanged?.invoke(focusX, focusY)
    }

    fun hasVerticalSlack(): Boolean {
        val bmp = bitmap ?: return false
        if (frameRect.width() <= 0 || frameRect.height() <= 0) return false
        val scaleX = frameRect.width() / bmp.width
        val scaleY = frameRect.height() / bmp.height
        val scale = maxOf(scaleX, scaleY)
        val scaledHeight = bmp.height * scale
        return (scaledHeight - frameRect.height()) > 4f
    }

    fun hasHorizontalSlack(): Boolean {
        val bmp = bitmap ?: return false
        if (frameRect.width() <= 0 || frameRect.height() <= 0) return false
        val scaleX = frameRect.width() / bmp.width
        val scaleY = frameRect.height() / bmp.height
        val scale = maxOf(scaleX, scaleY)
        val scaledWidth = bmp.width * scale
        return (scaledWidth - frameRect.width()) > 4f
    }

    private fun recalculateFrame(w: Int, h: Int) {
        if (w <= 0 || h <= 0) return
        val padding = 16f * resources.displayMetrics.density
        val availWidth = (w - 2 * padding).coerceAtLeast(10f)
        val availHeight = (h - 2 * padding).coerceAtLeast(10f)

        val frameWidth: Float
        val frameHeight: Float

        if (availWidth / availHeight > targetAspectRatio) {
            frameHeight = availHeight
            frameWidth = frameHeight * targetAspectRatio
        } else {
            frameWidth = availWidth
            frameHeight = frameWidth / targetAspectRatio
        }

        val left = (w - frameWidth) * 0.5f
        val top = (h - frameHeight) * 0.5f
        frameRect.set(left, top, left + frameWidth, top + frameHeight)

        clipPath.reset()
        clipPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)

        viewClipPath.reset()
        viewClipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), viewRadius, viewRadius, Path.Direction.CW)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateFrame(w, h)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return super.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                lastTouchY = event.y
                isDragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - lastTouchX
                    val deltaY = event.y - lastTouchY
                    lastTouchX = event.x
                    lastTouchY = event.y

                    val scaleX = frameRect.width() / bmp.width
                    val scaleY = frameRect.height() / bmp.height
                    val scale = maxOf(scaleX, scaleY)

                    val scaledWidth = bmp.width * scale
                    val scaledHeight = bmp.height * scale
                    val slackX = maxOf(0f, scaledWidth - frameRect.width())
                    val slackY = maxOf(0f, scaledHeight - frameRect.height())

                    if (slackX > 0f) {
                        focusX = (focusX - deltaX / slackX).coerceIn(0f, 1f)
                    }
                    if (slackY > 0f) {
                        focusY = (focusY - deltaY / slackY).coerceIn(0f, 1f)
                    }

                    invalidate()
                    onFocusChanged?.invoke(focusX, focusY)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    parent?.requestDisallowInterceptTouchEvent(false)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (frameRect.width() <= 0 || frameRect.height() <= 0) return

        val scaleX = frameRect.width() / bmp.width
        val scaleY = frameRect.height() / bmp.height
        val scale = maxOf(scaleX, scaleY)

        val scaledWidth = bmp.width * scale
        val scaledHeight = bmp.height * scale
        val slackX = maxOf(0f, scaledWidth - frameRect.width())
        val slackY = maxOf(0f, scaledHeight - frameRect.height())

        val imageLeft = if (slackX > 0f) {
            frameRect.left - slackX * focusX
        } else {
            frameRect.left + (frameRect.width() - scaledWidth) * 0.5f
        }

        val imageTop = if (slackY > 0f) {
            frameRect.top - slackY * focusY
        } else {
            frameRect.top + (frameRect.height() - scaledHeight) * 0.5f
        }

        drawMatrix.reset()
        drawMatrix.setScale(scale, scale)
        drawMatrix.postTranslate(imageLeft, imageTop)

        canvas.save()
        canvas.clipPath(viewClipPath)

        // Nền tối thanh lịch
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), backgroundPaint)

        // 1. Vẽ toàn bộ ảnh đã scale (bao gồm cả phần lọt ra ngoài khung nhìn)
        canvas.drawBitmap(bmp, drawMatrix, bitmapPaint)

        // 2. Phủ lớp đen mờ (scrim) bên ngoài khung nhìn để làm nổi bật ô Bento
        dimPath.reset()
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)
        dimPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(dimPath, dimPaint)

        // 3. Vẽ đường lưới bố cục 1/3 (Rule of thirds) mờ trong khung nhìn
        canvas.save()
        canvas.clipPath(clipPath)
        val col1 = frameRect.left + frameRect.width() / 3f
        val col2 = frameRect.left + 2f * frameRect.width() / 3f
        val row1 = frameRect.top + frameRect.height() / 3f
        val row2 = frameRect.top + 2f * frameRect.height() / 3f

        canvas.drawLine(col1, frameRect.top, col1, frameRect.bottom, gridPaint)
        canvas.drawLine(col2, frameRect.top, col2, frameRect.bottom, gridPaint)
        canvas.drawLine(frameRect.left, row1, frameRect.right, row1, gridPaint)
        canvas.drawLine(frameRect.left, row2, frameRect.right, row2, gridPaint)
        canvas.restore()

        // 4. Vẽ viền khung nhìn nổi bật (Sakura accent)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, frameStrokePaint)

        // 5. Viền ngoài bo cong tinh tế cho cả view
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), viewRadius, viewRadius, outerBorderPaint)

        canvas.restore()
    }
}
