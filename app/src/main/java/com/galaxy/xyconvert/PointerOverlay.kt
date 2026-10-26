package com.galaxy.xyconvert

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Point
import android.os.Build
import android.util.DisplayMetrics
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * 悬浮虚拟光标层
 * 基于 TYPE_ACCESSIBILITY_OVERLAY 实现，全屏透明无阻碍，在系统顶层以超低延迟渲染指针
 */
class PointerOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    private var rootContainer: FrameLayout? = null
    private var pointerView: PointerView? = null

    var currentX: Float = 500f
        private set
    var currentY: Float = 500f
        private set

    private var screenWidth = 1920
    private var screenHeight = 1200
    private var pointerSizePx = 36f

    init {
        updateScreenDimensions()
    }

    private fun updateScreenDimensions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
        } else {
            val display = windowManager.defaultDisplay
            val size = Point()
            display.getRealSize(size)
            screenWidth = size.x
            screenHeight = size.y
        }
    }

    fun show() {
        if (rootContainer != null) return

        updateScreenDimensions()
        currentX = screenWidth / 2f
        currentY = screenHeight / 2f

        val params = WindowManager.LayoutParams().apply {
            type = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
            format = PixelFormat.TRANSLUCENT
            flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS or
                    WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            gravity = Gravity.TOP or Gravity.START
            width = WindowManager.LayoutParams.MATCH_PARENT
            height = WindowManager.LayoutParams.MATCH_PARENT
        }

        rootContainer = FrameLayout(context).apply {
            clipChildren = false
            clipToPadding = false
        }

        pointerView = PointerView(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                (pointerSizePx * 2).toInt(),
                (pointerSizePx * 2).toInt()
            )
            translationX = currentX - pointerSizePx
            translationY = currentY - pointerSizePx
        }

        rootContainer?.addView(pointerView)

        try {
            windowManager.addView(rootContainer, params)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun hide() {
        rootContainer?.let {
            try {
                windowManager.removeView(it)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        rootContainer = null
        pointerView = null
    }

    /**
     * 根据增量平移指针（保证在屏幕范围内）
     */
    fun moveBy(dx: Float, dy: Float) {
        updateScreenDimensions()
        currentX = (currentX + dx).coerceIn(0f, screenWidth.toFloat())
        currentY = (currentY + dy).coerceIn(0f, screenHeight.toFloat())

        pointerView?.let { view ->
            view.translationX = currentX - pointerSizePx
            view.translationY = currentY - pointerSizePx
        }
    }

    /**
     * 设置光标位置
     */
    fun setPosition(x: Float, y: Float) {
        updateScreenDimensions()
        currentX = x.coerceIn(0f, screenWidth.toFloat())
        currentY = y.coerceIn(0f, screenHeight.toFloat())

        pointerView?.let { view ->
            view.translationX = currentX - pointerSizePx
            view.translationY = currentY - pointerSizePx
        }
    }

    /**
     * 触发点击时的微动画反馈
     */
    fun triggerClickAnimation() {
        pointerView?.animate()
            ?.scaleX(0.75f)
            ?.scaleY(0.75f)
            ?.setDuration(60)
            ?.withEndAction {
                pointerView?.animate()
                    ?.scaleX(1.0f)
                    ?.scaleY(1.0f)
                    ?.setDuration(80)
                    ?.start()
            }
            ?.start()
    }

    fun updatePointerSize(sizeDp: Int) {
        val density = context.resources.displayMetrics.density
        pointerSizePx = sizeDp * density
        pointerView?.let { view ->
            view.layoutParams = FrameLayout.LayoutParams(
                (pointerSizePx * 2).toInt(),
                (pointerSizePx * 2).toInt()
            )
            view.requestLayout()
            view.translationX = currentX - pointerSizePx
            view.translationY = currentY - pointerSizePx
        }
    }

    /**
     * 自定义绘制的三星 One UI 风格光标：半透明精致圆形带白边与中心点
     */
    private inner class PointerView(context: Context) : View(context) {

        private val outerPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#40000000") // 柔和半透明外圈
            style = Paint.Style.STROKE
            strokeWidth = 3f
        }

        private val ringPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 4f
        }

        private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#3B82F6") // 现代三星蓝
            style = Paint.Style.FILL
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val cx = width / 2f
            val cy = height / 2f
            val radius = (width / 2f) - 6f

            // 绘制三星风格圆点指针
            canvas.drawCircle(cx, cy, radius, fillPaint)
            canvas.drawCircle(cx, cy, radius, ringPaint)
            canvas.drawCircle(cx, cy, radius + 2f, outerPaint)
        }
    }
}
