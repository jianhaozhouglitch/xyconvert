package com.galaxy.xyconvert

import android.content.Context
import android.content.SharedPreferences

class ConfigManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    interface OnConfigChangeListener {
        fun onConfigChanged(config: ConfigManager)
    }

    private val listeners = mutableListOf<OnConfigChangeListener>()

    fun registerListener(listener: OnConfigChangeListener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
        }
    }

    fun unregisterListener(listener: OnConfigChangeListener) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        for (listener in listeners) {
            listener.onConfigChanged(this)
        }
    }

    var isSwapXY: Boolean
        get() = prefs.getBoolean(KEY_SWAP_XY, true)
        set(value) {
            prefs.edit().putBoolean(KEY_SWAP_XY, value).apply()
            notifyChanged()
        }

    var isInvertX: Boolean
        get() = prefs.getBoolean(KEY_INVERT_X, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INVERT_X, value).apply()
            notifyChanged()
        }

    var isInvertY: Boolean
        get() = prefs.getBoolean(KEY_INVERT_Y, false)
        set(value) {
            prefs.edit().putBoolean(KEY_INVERT_Y, value).apply()
            notifyChanged()
        }

    var presetMode: Int
        get() = prefs.getInt(KEY_PRESET_MODE, PRESET_NORMAL)
        set(value) {
            prefs.edit().putInt(KEY_PRESET_MODE, value).apply()
            notifyChanged()
        }

    var sensitivity: Float
        get() = prefs.getFloat(KEY_SENSITIVITY, 1.0f)
        set(value) {
            prefs.edit().putFloat(KEY_SENSITIVITY, value).apply()
            notifyChanged()
        }

    var pointerSizeDp: Int
        get() = prefs.getInt(KEY_POINTER_SIZE, 18)
        set(value) {
            prefs.edit().putInt(KEY_POINTER_SIZE, value).apply()
            notifyChanged()
        }

    companion object {
        private const val PREF_NAME = "xyconvert_prefs"
        private const val KEY_SWAP_XY = "key_swap_xy"
        private const val KEY_INVERT_X = "key_invert_x"
        private const val KEY_INVERT_Y = "key_invert_y"
        private const val KEY_PRESET_MODE = "key_preset_mode"
        private const val KEY_SENSITIVITY = "key_sensitivity"
        private const val KEY_POINTER_SIZE = "key_pointer_size"

        const val PRESET_NORMAL = 0   // 直接对调 X/Y
        const val PRESET_CW90 = 1     // 顺时针旋转90° (竖屏模式 + 横置键盘)
        const val PRESET_CCW90 = 2    // 逆时针旋转90°

        @Volatile
        private var instance: ConfigManager? = null

        fun getInstance(context: Context): ConfigManager {
            return instance ?: synchronized(this) {
                instance ?: ConfigManager(context).also { instance = it }
            }
        }
    }
}
