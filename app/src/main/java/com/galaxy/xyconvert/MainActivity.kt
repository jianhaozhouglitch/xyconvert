package com.galaxy.xyconvert

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.widget.SeekBar
import androidx.appcompat.app.AppCompatActivity
import com.galaxy.xyconvert.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var config: ConfigManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        config = ConfigManager.getInstance(this)

        setupListeners()
        loadConfigToUI()
    }

    override fun onResume() {
        super.onResume()
        updateServiceStatus()
    }

    private fun setupListeners() {
        // 无障碍设置跳转按钮
        binding.btnOpenAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        // 预设模式单选框
        binding.radioGroupPreset.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbPresetNormal -> {
                    config.presetMode = ConfigManager.PRESET_NORMAL
                    binding.switchSwapXY.isEnabled = true
                    binding.switchInvertX.isEnabled = true
                    binding.switchInvertY.isEnabled = true
                }
                R.id.rbPresetCW90 -> {
                    config.presetMode = ConfigManager.PRESET_CW90
                    binding.switchSwapXY.isEnabled = false
                    binding.switchInvertX.isEnabled = false
                    binding.switchInvertY.isEnabled = false
                }
                R.id.rbPresetCCW90 -> {
                    config.presetMode = ConfigManager.PRESET_CCW90
                    binding.switchSwapXY.isEnabled = false
                    binding.switchInvertX.isEnabled = false
                    binding.switchInvertY.isEnabled = false
                }
            }
        }

        // 轴向单独开关
        binding.switchSwapXY.setOnCheckedChangeListener { _, isChecked ->
            config.isSwapXY = isChecked
        }
        binding.switchInvertX.setOnCheckedChangeListener { _, isChecked ->
            config.isInvertX = isChecked
        }
        binding.switchInvertY.setOnCheckedChangeListener { _, isChecked ->
            config.isInvertY = isChecked
        }

        // 灵敏度滑动条 (0.5x ~ 3.5x)
        binding.seekSensitivity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress / 10f)
                binding.tvSensitivity.text = String.format("灵敏度调节: %.1fx", value)
                if (fromUser) {
                    config.sensitivity = value
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // 指针大小滑动条 (12dp ~ 48dp)
        binding.seekPointerSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val sizeDp = 12 + progress
                binding.tvPointerSize.text = String.format("指针大小: %d dp", sizeDp)
                if (fromUser) {
                    config.pointerSizeDp = sizeDp
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun loadConfigToUI() {
        // 加载预设模式
        when (config.presetMode) {
            ConfigManager.PRESET_CW90 -> binding.rbPresetCW90.isChecked = true
            ConfigManager.PRESET_CCW90 -> binding.rbPresetCCW90.isChecked = true
            else -> binding.rbPresetNormal.isChecked = true
        }

        // 加载开关
        binding.switchSwapXY.isChecked = config.isSwapXY
        binding.switchInvertX.isChecked = config.isInvertX
        binding.switchInvertY.isChecked = config.isInvertY

        // 加载灵敏度
        val sensProgress = ((config.sensitivity - 0.5f) * 10).toInt().coerceIn(0, 30)
        binding.seekSensitivity.progress = sensProgress
        binding.tvSensitivity.text = String.format("灵敏度调节: %.1fx", config.sensitivity)

        // 加载指针大小
        val sizeProgress = (config.pointerSizeDp - 12).coerceIn(0, 36)
        binding.seekPointerSize.progress = sizeProgress
        binding.tvPointerSize.text = String.format("指针大小: %d dp", config.pointerSizeDp)
    }

    private fun updateServiceStatus() {
        val isEnabled = isAccessibilityServiceEnabled(this, TrackpadService::class.java)

        val indicatorDrawable = binding.statusIndicator.background as? GradientDrawable
            ?: GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                binding.statusIndicator.background = this
            }

        if (isEnabled) {
            indicatorDrawable.setColor(Color.parseColor("#2E7D32")) // 绿色
            binding.tvStatus.text = getString(R.string.status_enabled)
            binding.tvStatus.setTextColor(Color.parseColor("#2E7D32"))
            binding.btnOpenAccessibility.text = "无障碍服务已生效（点击可重新配置）"
            binding.btnOpenAccessibility.setBackgroundColor(Color.parseColor("#455A64"))
        } else {
            indicatorDrawable.setColor(Color.parseColor("#C62828")) // 红色
            binding.tvStatus.text = getString(R.string.status_disabled)
            binding.tvStatus.setTextColor(Color.parseColor("#C62828"))
            binding.btnOpenAccessibility.text = getString(R.string.btn_enable_service)
            binding.btnOpenAccessibility.setBackgroundColor(getColor(R.color.primary))
        }
    }

    /**
     * 检测指定的无障碍服务是否已开启
     */
    private fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<*>): Boolean {
        val expectedComponentName = ComponentName(context, serviceClass)
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)

        while (colonSplitter.hasNext()) {
            val componentNameString = colonSplitter.next()
            val enabledComponent = ComponentName.unflattenFromString(componentNameString)
            if (enabledComponent != null && enabledComponent == expectedComponentName) {
                return true
            }
        }
        return false
    }
}
