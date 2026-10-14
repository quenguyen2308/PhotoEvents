package com.example.photoevents.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * Custom View cho phép người dùng:
 * 1. Chạm và kéo (drag / pan) ảnh mượt mà đến BẤT KỲ vị trí nào mà KHÔNG BỊ GIỚI HẠN (360 độ tự do).
 * 2. Thay đổi kích thước khung focus (resize frame) trực quan bằng cách kéo 4 tay cầm ở 4 góc của khung.
 * 3. Phóng to / thu nhỏ (zoom-in / zoom-out) bằng cử chỉ 2 ngón tay (pinch) hoặc nút bấm.
 * 4. Chạm đúp (double-tap) để phóng to nhanh hoặc quay về mức vừa vặn.
 * 5. Tự động đồng bộ vị trí tiêu điểm (focusX, focusY, focusZoom) chuẩn xác 100% với FocusCropTransformation.
 */
class FocusAdjustView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private enum class DragMode {
        NONE,
        PAN_IMAGE,
        CORNER_TL,
        CORNER_TR,
        CORNER_BL,
        CORNER_BR
    }

    var bitmap: Bitmap? = null
        set(value) {
            field = value
            recalculateFrame(width, height)
            invalidate()
            onStateChanged?.invoke()
        }

    /** Toạ độ tiêu điểm ngang trong ảnh: 0.0 (trái), 0.5 (giữa), 1.0 (phải), tự do ngoài khoảng */
    var focusX: Float = 0.5f
        private set

    /** Toạ độ tiêu điểm dọc trong ảnh: 0.0 (đỉnh), 0.25 (mặt/gần đỉnh), 0.5 (giữa), 1.0 (đáy), tự do ngoài khoảng */
    var focusY: Float = 0.25f
        private set

    /** Mức phóng to/thu nhỏ (1.0f = fill khung, <1.0f = zoom out xem toàn cảnh, >1.0f = zoom in) */
    var zoom: Float = 1.0f
        set(value) {
            val clamped = value.coerceIn(getMinZoom(), maxZoom)
            if (field != clamped) {
                field = clamped
                updateFrameSizeForZoom()
                invalidate()
            }
        }

    val maxZoom: Float = 5.0f

    /** Tỷ lệ khung nhìn mục tiêu (width / height) - Mặc định 1.0f (Vuông theo Bento chính) */
    var targetAspectRatio: Float = 1.0f
        set(value) {
            field = if (value > 0f) value else 1.0f
            recalculateFrame(width, height)
            invalidate()
            onStateChanged?.invoke()
        }

    var onFocusChanged: ((focusX: Float, focusY: Float, zoom: Float) -> Unit)? = null
    var onStateChanged: (() -> Unit)? = null

    // Khung focus hiển thị hiện tại (có thể kéo resize)
    val frameRect = RectF()
    // Khung kích thước tối đa cho aspect ratio hiện tại
    private val defaultFrameRect = RectF()

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
        color = Color.argb(225, 12, 12, 16) // Scrim ngoài khung nhìn tối rõ ràng (88%) để người dùng phân biệt tuyệt đối vùng trong khung
    }
    private val frameStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
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
        color = Color.argb(85, 255, 255, 255)
    }
    private val focalTargetPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.8f * resources.displayMetrics.density
        color = Color.parseColor("#E57373")
    }
    private val focalLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.2f * resources.displayMetrics.density
        color = Color.argb(180, 255, 255, 255)
    }

    // Paint cho 4 tay cầm resize ở 4 góc của khung focus
    private val cornerHandleShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = Color.argb(120, 0, 0, 0)
    }
    private val cornerHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
        color = Color.WHITE
    }
    private val cornerGripPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.parseColor("#E57373")
    }
    private val cornerGripBorderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
        color = Color.WHITE
    }

    private val cornerRadius = 20f * resources.displayMetrics.density

    private var lastTouchX = 0f
    private var lastTouchY = 0f
    private var dragMode = DragMode.NONE
    private var zoomAnimator: ValueAnimator? = null

    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newZoom = (zoom * scaleFactor).coerceIn(getMinZoom(), maxZoom)
            if (newZoom != zoom) {
                zoom = newZoom
                invalidate()
                onFocusChanged?.invoke(focusX, focusY, zoom)
            }
            return true
        }
    })

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            val fit = getFitZoom()
            val target = when {
                zoom < 0.95f -> 1.0f
                zoom in 0.95f..1.4f -> 2.0f.coerceAtMost(maxZoom)
                else -> fit
            }
            animateZoomTo(target)
            return true
        }
    })

    fun setFocus(x: Float, y: Float, newZoom: Float = zoom) {
        focusX = x.coerceIn(0.0f, 1.0f)
        focusY = y.coerceIn(0.0f, 1.0f)
        val clampedZoom = newZoom.coerceIn(getMinZoom(), maxZoom)
        zoom = clampedZoom
        updateFrameSizeForZoom()
        invalidate()
        onFocusChanged?.invoke(focusX, focusY, zoom)
    }

    private fun getBaseScale(): Float {
        val bmp = bitmap ?: return 1.0f
        if (defaultFrameRect.width() <= 0f || defaultFrameRect.height() <= 0f) return 1.0f
        val scaleX = defaultFrameRect.width() / bmp.width
        val scaleY = defaultFrameRect.height() / bmp.height
        return maxOf(scaleX, scaleY)
    }

    /** Mức zoom để nhìn thấy trọn vẹn toàn bộ bức ảnh (full side) lọt trong khung nhìn */
    fun getFitZoom(): Float {
        val bmp = bitmap ?: return 1.0f
        if (defaultFrameRect.width() <= 0f || defaultFrameRect.height() <= 0f) return 1.0f
        val scaleX = defaultFrameRect.width() / bmp.width
        val scaleY = defaultFrameRect.height() / bmp.height
        val base = maxOf(scaleX, scaleY)
        val fit = minOf(scaleX, scaleY)
        return if (base > 0f) (fit / base).coerceIn(0.1f, 1.0f) else 1.0f
    }

    fun getMinZoom(): Float = minOf(0.35f, getFitZoom() * 0.9f)

    fun getEffectiveScale(): Float {
        val base = getBaseScale()
        return if (zoom < 1.0f) base * zoom else base
    }

    fun hasVerticalSlack(): Boolean {
        val bmp = bitmap ?: return false
        val photoAR = bmp.width.toFloat() / bmp.height
        return photoAR < targetAspectRatio
    }

    fun hasHorizontalSlack(): Boolean {
        val bmp = bitmap ?: return false
        val photoAR = bmp.width.toFloat() / bmp.height
        return photoAR > targetAspectRatio
    }

    fun alignCenter() {
        setFocus(0.5f, 0.5f, zoom)
    }

    fun alignTop() {
        setFocus(focusX, 0.0f, zoom)
    }

    fun alignBottom() {
        setFocus(focusX, 1.0f, zoom)
    }

    fun alignLeft() {
        setFocus(0.0f, focusY, zoom)
    }

    fun alignRight() {
        setFocus(1.0f, focusY, zoom)
    }

    fun zoomIn() {
        val target = (zoom + 0.25f).coerceAtMost(maxZoom)
        animateZoomTo(target)
    }

    fun zoomOut() {
        val target = (zoom - 0.25f).coerceAtLeast(getMinZoom())
        animateZoomTo(target)
    }

    fun resetZoom() {
        animateZoomTo(1.0f)
    }

    fun toggleFullSide() {
        val fit = getFitZoom()
        val target = if (abs(zoom - fit) < 0.05f) 1.0f else fit
        animateZoomTo(target)
    }

    fun isFullSide(): Boolean {
        val fit = getFitZoom()
        return abs(zoom - fit) < 0.05f || zoom < fit
    }

    fun animateZoomTo(targetZoom: Float) {
        zoomAnimator?.cancel()
        val startZoom = zoom
        zoomAnimator = ValueAnimator.ofFloat(startZoom, targetZoom.coerceIn(getMinZoom(), maxZoom)).apply {
            duration = 240
            interpolator = DecelerateInterpolator()
            addUpdateListener { anim ->
                zoom = anim.animatedValue as Float
                invalidate()
                onFocusChanged?.invoke(focusX, focusY, zoom)
            }
            start()
        }
    }

    fun cancelZoomAnimation() {
        zoomAnimator?.let {
            if (it.isRunning) {
                it.end()
            }
        }
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
        defaultFrameRect.set(left, top, left + frameWidth, top + frameHeight)

        updateFrameSizeForZoom()

        viewClipPath.reset()
        viewClipPath.addRoundRect(0f, 0f, w.toFloat(), h.toFloat(), viewRadius, viewRadius, Path.Direction.CW)
    }

    private fun updateFrameSizeForZoom() {
        val maxW = defaultFrameRect.width()
        val maxH = defaultFrameRect.height()
        if (maxW <= 0f || maxH <= 0f) return

        val curW: Float
        val curH: Float
        if (zoom >= 1.0f) {
            curW = maxW / zoom
            curH = curW / targetAspectRatio
        } else {
            curW = maxW
            curH = maxH
        }

        val cx = defaultFrameRect.centerX()
        val cy = defaultFrameRect.centerY()
        frameRect.set(cx - curW * 0.5f, cy - curH * 0.5f, cx + curW * 0.5f, cy + curH * 0.5f)

        clipPath.reset()
        clipPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        recalculateFrame(w, h)
        onStateChanged?.invoke()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val bmp = bitmap ?: return super.onTouchEvent(event)

        gestureDetector.onTouchEvent(event)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val density = resources.displayMetrics.density
                val touchTarget = 36f * density // 36dp vùng chạm quanh mỗi góc khung

                val dTL = hypot(event.x - frameRect.left, event.y - frameRect.top)
                val dTR = hypot(event.x - frameRect.right, event.y - frameRect.top)
                val dBL = hypot(event.x - frameRect.left, event.y - frameRect.bottom)
                val dBR = hypot(event.x - frameRect.right, event.y - frameRect.bottom)

                val minD = minOf(dTL, dTR, dBL, dBR)
                dragMode = if (minD <= touchTarget) {
                    when (minD) {
                        dTL -> DragMode.CORNER_TL
                        dTR -> DragMode.CORNER_TR
                        dBL -> DragMode.CORNER_BL
                        else -> DragMode.CORNER_BR
                    }
                } else {
                    DragMode.PAN_IMAGE
                }

                lastTouchX = event.x
                lastTouchY = event.y
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                dragMode = DragMode.NONE
                lastTouchX = event.x
                lastTouchY = event.y
            }

            MotionEvent.ACTION_MOVE -> {
                if (scaleDetector.isInProgress) {
                    lastTouchX = event.x
                    lastTouchY = event.y
                    return true
                }

                when (dragMode) {
                    DragMode.CORNER_TL, DragMode.CORNER_TR, DragMode.CORNER_BL, DragMode.CORNER_BR -> {
                        // Kéo góc để thay đổi kích thước khung focus (resize frame)
                        val cx = defaultFrameRect.centerX()
                        val cy = defaultFrameRect.centerY()
                        val distX = abs(event.x - cx)
                        val distY = abs(event.y - cy)

                        val candidateHalfW = max(distX, distY * targetAspectRatio)
                        val maxHalfW = defaultFrameRect.width() * 0.5f
                        val minHalfW = maxHalfW / maxZoom

                        val newZoom = if (candidateHalfW <= maxHalfW) {
                            maxHalfW / candidateHalfW.coerceAtLeast(minHalfW)
                        } else {
                            maxHalfW / candidateHalfW
                        }

                        zoom = newZoom.coerceIn(getMinZoom(), maxZoom)
                        invalidate()
                        onFocusChanged?.invoke(focusX, focusY, zoom)
                        return true
                    }

                    DragMode.PAN_IMAGE -> {
                        // Kéo ảnh tự do 360 độ hoàn toàn KHÔNG BỊ GIỚI HẠN
                        val deltaX = event.x - lastTouchX
                        val deltaY = event.y - lastTouchY
                        lastTouchX = event.x
                        lastTouchY = event.y

                        val scale = getEffectiveScale()
                        val scaledWidth = bmp.width * scale
                        val scaledHeight = bmp.height * scale

                        val slackX = scaledWidth - frameRect.width()
                        val slackY = scaledHeight - frameRect.height()

                        var changed = false
                        if (slackX > 0f) {
                            focusX -= deltaX / slackX
                            focusX = focusX.coerceIn(0.0f, 1.0f)
                            changed = true
                        }
                        if (slackY > 0f) {
                            focusY -= deltaY / slackY
                            focusY = focusY.coerceIn(0.0f, 1.0f)
                            changed = true
                        }

                        if (changed) {
                            invalidate()
                            onFocusChanged?.invoke(focusX, focusY, zoom)
                        }
                        return true
                    }

                    DragMode.NONE -> {
                        lastTouchX = event.x
                        lastTouchY = event.y
                    }
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragMode = DragMode.NONE
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bmp = bitmap ?: return
        if (frameRect.width() <= 0 || frameRect.height() <= 0) return

        val scale = getEffectiveScale()
        val scaledWidth = bmp.width * scale
        val scaledHeight = bmp.height * scale

        val slackX = scaledWidth - frameRect.width()
        val slackY = scaledHeight - frameRect.height()

        // Tính toạ độ vẽ ảnh đồng bộ 100% với FocusCropTransformation
        val imageLeft = if (slackX > 0f) {
            frameRect.left - focusX * slackX
        } else {
            frameRect.left + (frameRect.width() - scaledWidth) * 0.5f
        }
        val imageTop = if (slackY > 0f) {
            frameRect.top - focusY * slackY
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

        // 2. Phủ lớp đen mờ (scrim) bên ngoài khung nhìn để làm nổi bật vùng focus
        dimPath.reset()
        dimPath.addRect(0f, 0f, width.toFloat(), height.toFloat(), Path.Direction.CW)
        dimPath.addRoundRect(frameRect, cornerRadius, cornerRadius, Path.Direction.CW)
        dimPath.fillType = Path.FillType.EVEN_ODD
        canvas.drawPath(dimPath, dimPaint)

        // 3. Vẽ đường lưới bố cục 1/3 (Rule of thirds) và tâm ngắm tiêu điểm
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

        // Tâm ngắm tiêu điểm tinh tế chính giữa khung nhìn
        val centerX = frameRect.centerX()
        val centerY = frameRect.centerY()
        val crossSize = 7f * resources.displayMetrics.density
        canvas.drawCircle(centerX, centerY, 3.5f * resources.displayMetrics.density, focalTargetPaint)
        canvas.drawLine(centerX - crossSize, centerY, centerX + crossSize, centerY, focalLinePaint)
        canvas.drawLine(centerX, centerY - crossSize, centerX, centerY + crossSize, focalLinePaint)

        canvas.restore()

        // 4. Vẽ viền khung nhìn nổi bật (Sakura accent)
        canvas.drawRoundRect(frameRect, cornerRadius, cornerRadius, frameStrokePaint)

        // 5. Vẽ 4 tay cầm góc (Corner resize handles) chuyên nghiệp
        drawCornerHandles(canvas)

        // 6. Viền ngoài bo cong tinh tế cho cả view
        canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), viewRadius, viewRadius, outerBorderPaint)

        canvas.restore()
    }

    private fun drawCornerHandles(canvas: Canvas) {
        val handleLen = 18f * resources.displayMetrics.density
        val gripRadius = 3.5f * resources.displayMetrics.density

        val corners = arrayOf(
            // TL: góc (left, top), mở về phía phải và dưới
            floatArrayOf(frameRect.left, frameRect.top, 1f, 1f),
            // TR: góc (right, top), mở về phía trái và dưới
            floatArrayOf(frameRect.right, frameRect.top, -1f, 1f),
            // BL: góc (left, bottom), mở về phía phải và trên
            floatArrayOf(frameRect.left, frameRect.bottom, 1f, -1f),
            // BR: góc (right, bottom), mở về phía trái và trên
            floatArrayOf(frameRect.right, frameRect.bottom, -1f, -1f)
        )

        for (c in corners) {
            val cx = c[0]
            val cy = c[1]
            val dirX = c[2]
            val dirY = c[3]

            // Bóng mờ đổ dưới tay cầm để nổi bật trên mọi tông nền ảnh
            canvas.drawLine(cx, cy, cx + dirX * handleLen, cy, cornerHandleShadowPaint)
            canvas.drawLine(cx, cy, cx, cy + dirY * handleLen, cornerHandleShadowPaint)

            // Thanh góc màu trắng dày nổi bật
            canvas.drawLine(cx, cy, cx + dirX * handleLen, cy, cornerHandlePaint)
            canvas.drawLine(cx, cy, cx, cy + dirY * handleLen, cornerHandlePaint)

            // Điểm chạm tròn (grip dot) tại đỉnh góc
            canvas.drawCircle(cx, cy, gripRadius, cornerGripPaint)
            canvas.drawCircle(cx, cy, gripRadius, cornerGripBorderPaint)
        }
    }
}
