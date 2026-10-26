package com.galaxy.xyconvert

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.accessibility.AccessibilityEvent
import androidx.annotation.RequiresApi

/**
 * 专为 Samsung Galaxy Tab S9+ (One UI 8.5 / Android 14+) 定制的触控板轴向重映射无障碍服务
 * 利用 Android 14+ 提供的 setMotionEventSources 和 onMotionEvent 拦截底层触控板，
 * 实施 X/Y 轴坐标增量对调，并配合极低延迟虚拟光标与手势派发器完成系统级交互。
 */
class TrackpadService : AccessibilityService(), ConfigManager.OnConfigChangeListener {

    private val TAG = "TrackpadService"
    private lateinit var config: ConfigManager
    private lateinit var pointerOverlay: PointerOverlay

    private var downX = 0f
    private var downY = 0f
    private var downTime = 0L
    private var isPointerDown = false

    private var lastRawX = 0f
    private var lastRawY = 0f
    private var hasLastRaw = false

    override fun onCreate() {
        super.onCreate()
        config = ConfigManager.getInstance(this)
        pointerOverlay = PointerOverlay(this)
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        Log.i(TAG, "XYConvert Accessibility Service Connected")

        // 配置监听的输入设备：鼠标指针 (SOURCE_MOUSE) 与 触控板 (SOURCE_TOUCHPAD)
        val info = serviceInfo ?: AccessibilityServiceInfo()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val sources = InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD
            info.setMotionEventSources(sources)
        }
        serviceInfo = info

        config.registerListener(this)
        pointerOverlay.show()
        pointerOverlay.updatePointerSize(config.pointerSizeDp)
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    override fun onMotionEvent(event: MotionEvent) {
        val isPointerDevice = (event.source and InputDevice.SOURCE_CLASS_POINTER) != 0 ||
                (event.source and InputDevice.SOURCE_MOUSE) != 0 ||
                (event.source and InputDevice.SOURCE_TOUCHPAD) != 0

        if (!isPointerDevice) {
            super.onMotionEvent(event)
            return
        }

        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_MOVE,
            MotionEvent.ACTION_MOVE -> {
                handlePointerMovement(event)
            }

            MotionEvent.ACTION_DOWN,
            MotionEvent.ACTION_BUTTON_PRESS -> {
                isPointerDown = true
                downTime = SystemClock.uptimeMillis()
                downX = pointerOverlay.currentX
                downY = pointerOverlay.currentY
            }

            MotionEvent.ACTION_UP,
            MotionEvent.ACTION_BUTTON_RELEASE -> {
                if (isPointerDown) {
                    val currentUpTime = SystemClock.uptimeMillis()
                    val duration = currentUpTime - downTime
                    val upX = pointerOverlay.currentX
                    val upY = pointerOverlay.currentY
                    val distanceMoved = Math.hypot((upX - downX).toDouble(), (upY - downY).toDouble()).toFloat()

                    if (duration < 350 && distanceMoved < 20f) {
                        // 单击轻点识别
                        pointerOverlay.triggerClickAnimation()
                        dispatchTap(upX, upY)
                    } else if (distanceMoved >= 20f) {
                        // 拖拽手势识别
                        dispatchSwipe(downX, downY, upX, upY, duration.coerceAtLeast(100))
                    }
                    isPointerDown = false
                }
            }

            MotionEvent.ACTION_SCROLL -> {
                handleScrollEvent(event)
            }
        }
    }

    /**
     * 处理指针移动增量并实施 X/Y 轴对调
     */
    private fun handlePointerMovement(event: MotionEvent) {
        val historySize = event.historySize

        // 1. 处理历史批处理点（避免高刷新率触控板丢帧或抖动）
        for (h in 0 until historySize) {
            val hdx = event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_X, 0, h)
            val hdy = event.getHistoricalAxisValue(MotionEvent.AXIS_RELATIVE_Y, 0, h)
            applyRelativeMovement(hdx, hdy)
        }

        // 2. 处理当前最新的移动点
        val dx = event.getAxisValue(MotionEvent.AXIS_RELATIVE_X)
        val dy = event.getAxisValue(MotionEvent.AXIS_RELATIVE_Y)

        if (dx != 0f || dy != 0f) {
            applyRelativeMovement(dx, dy)
            hasLastRaw = false
        } else {
            // 如果硬件未上报 AXIS_RELATIVE，则回退为连续绝对坐标差值
            val currentRawX = event.x
            val currentRawY = event.y
            if (hasLastRaw) {
                val fallbackDx = currentRawX - lastRawX
                val fallbackDy = currentRawY - lastRawY
                applyRelativeMovement(fallbackDx, fallbackDy)
            }
            lastRawX = currentRawX
            lastRawY = currentRawY
            hasLastRaw = true
        }
    }

    /**
     * 核心算法：依据用户设置与屏幕旋转预设转换 X/Y 轴位移
     */
    private fun applyRelativeMovement(rawDx: Float, rawDy: Float) {
        var finalDx = rawDx
        var finalDy = rawDy

        when (config.presetMode) {
            ConfigManager.PRESET_CW90 -> {
                // 顺时针旋转90°（平板竖向放置，底部外接横向键盘）
                // 触控板向右 -> 屏幕向下；触控板向上 -> 屏幕向右
                finalDx = -rawDy
                finalDy = rawDx
            }
            ConfigManager.PRESET_CCW90 -> {
                // 逆时针旋转90°
                finalDx = rawDy
                finalDy = -rawDx
            }
            else -> {
                // 标准自定义对调
                if (config.isSwapXY) {
                    finalDx = rawDy
                    finalDy = rawDx
                }
                if (config.isInvertX) finalDx = -finalDx
                if (config.isInvertY) finalDy = -finalDy
            }
        }

        finalDx *= config.sensitivity
        finalDy *= config.sensitivity

        pointerOverlay.moveBy(finalDx, finalDy)
    }

    /**
     * 处理滚轮与双指滚动：根据轴向对调滚动方向
     */
    private fun handleScrollEvent(event: MotionEvent) {
        val rawVScroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL)
        val rawHScroll = event.getAxisValue(MotionEvent.AXIS_HSCROLL)

        var vScroll = rawVScroll
        var hScroll = rawHScroll

        if (config.isSwapXY || config.presetMode != ConfigManager.PRESET_NORMAL) {
            // 滚动方向同步对调
            val temp = vScroll
            vScroll = hScroll
            hScroll = temp
        }

        val curX = pointerOverlay.currentX
        val curY = pointerOverlay.currentY

        // 模拟滚动滑动手势
        val scrollDistance = 180f
        val deltaY = if (vScroll > 0) scrollDistance else if (vScroll < 0) -scrollDistance else 0f
        val deltaX = if (hScroll > 0) scrollDistance else if (hScroll < 0) -scrollDistance else 0f

        if (deltaX != 0f || deltaY != 0f) {
            dispatchSwipe(curX, curY, curX + deltaX, curY + deltaY, 150)
        }
    }

    /**
     * 使用 AccessibilityService 原生能力派发点击手势
     */
    private fun dispatchTap(x: Float, y: Float) {
        val clickPath = Path().apply { moveTo(x, y) }
        val stroke = GestureDescription.StrokeDescription(clickPath, 0, 50)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    /**
     * 使用 AccessibilityService 原生能力派发滑动手势
     */
    private fun dispatchSwipe(startX: Float, startY: Float, endX: Float, endY: Float, durationMs: Long) {
        val swipePath = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val stroke = GestureDescription.StrokeDescription(swipePath, 0, durationMs)
        val gesture = GestureDescription.Builder().addStroke(stroke).build()
        dispatchGesture(gesture, null, null)
    }

    override fun onConfigChanged(config: ConfigManager) {
        pointerOverlay.updatePointerSize(config.pointerSizeDp)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 监控常规无障碍事件（如需要可扩展为辅助定位可点击视图节点）
    }

    override fun onInterrupt() {
        Log.w(TAG, "XYConvert Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        config.unregisterListener(this)
        pointerOverlay.hide()
        Log.i(TAG, "XYConvert Service Destroyed")
    }
}
