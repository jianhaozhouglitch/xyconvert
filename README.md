# XYConvert - 三星 Galaxy Tab S9+ (One UI 8.5) 触控板 X/Y 轴对调工具

专为 **Samsung Galaxy Tab S9+** 与 **One UI 8.5** 系统量身定制的触控板轴向重映射应用。基于 Android 14+ 最新的无障碍输入拦截架构设计，无需 Root 权限即可实现触控板与外接鼠标的 X/Y 轴增量对调与旋转重映射。

---

## 🛠️ 技术背景与工作原理

在 Android 系统中，重映射物理硬件输入设备（如键盘保护套触控板、蓝牙触控板）的坐标通常受到系统底层沙盒的严格限制。本项目使用 **Android 14 (API 34) 及以上系统官方新增的无障碍底层交互 API** 实现：

1. **底层事件拦截 (`setMotionEventSources`)**：
   - 传统 Android 无法在无障碍层捕获相对鼠标位移。
   - 自 Android 14+ (API 34) 起，`AccessibilityServiceInfo.setMotionEventSources(InputDevice.SOURCE_MOUSE or InputDevice.SOURCE_TOUCHPAD)` 允许无障碍服务独占监听鼠标与触控板的原始输入。
   - **特性**：一旦无障碍服务声明监听特定源，Android 系统会将原始事件完全路由给服务的 `onMotionEvent`，并主动抑制系统默认指针的移动，防止发生“双重光标冲突”。

2. **X/Y 轴增量转换算法**：
   - 在 `TrackpadService.onMotionEvent` 中提取硬件位移增量 `AXIS_RELATIVE_X` 与 `AXIS_RELATIVE_Y`（同时消费历史批处理帧，保证 120Hz 高刷屏下的平滑度）。
   - **直接对调模式**：$dx' = dy, \quad dy' = dx$
   - **顺时针 90° 旋转模式（竖屏使用横向键盘）**：$dx' = -dy, \quad dy' = dx$
   - **逆时针 90° 旋转模式**：$dx' = dy, \quad dy' = -dx$

3. **低延迟虚拟光标覆盖层 (`TYPE_ACCESSIBILITY_OVERLAY`)**：
   - 无障碍服务拥有直接挂载全屏顶层悬浮窗的权限，无需额外的“悬浮窗授权”。
   - 配合硬件加速与属性平移（`translationX` / `translationY`），零布局重绘开销，响应延迟低于 5ms。
   - 外观复刻三星 One UI 8.5 经典蓝底白环半透明圆点指针。

4. **手势与点击模拟 (`dispatchGesture`)**：
   - 触控板单指轻点 / 左键释放时：根据位移与按压时长判断为点击，调用 `dispatchGesture` 发送毫秒级点击手势。
   - 触控板长按移动时：自动转换为拖拽手势。
   - 双指滚动时：拦截 `AXIS_VSCROLL` 与 `AXIS_HSCROLL`，对调滚动方向并派发平滑滑动手势。

---

## 📱 功能特性

- **X/Y 轴对调开关**：自由切换是否对调横向与纵向移动。
- **多角度旋转预设**：
  - **默认模式**：直接交换 X 与 Y 轴。
  - **竖屏 90° / 270° 模式**：解决将 Tab S9+ 竖向摆放阅读或手写，但使用底部横向官方键盘触控板时指针方向反错的痛点。
- **独立反转设置**：支持单轴反转（Invert X / Invert Y）。
- **灵敏度与指针调节**：支持 0.5x ~ 3.5x 移动阻尼调校，以及指针尺寸自定义。
- **开箱即用**：纯无障碍权限实现，无需电脑 ADB 调试激活，无需 Root。

---

## 🚀 编译与安装

1. **导入项目**：
   - 使用最新版 **Android Studio (Ladybug / Koala 或更新版本)** 打开本项目根目录 `xyconvert`。
2. **构建 APK**：
   - 等待 Gradle 同步完成，点击顶部工具栏 **Build** -> **Build Bundle(s) / APK(s)** -> **Build APK(s)**。
3. **安装至平板**：
   - 通过 USB 数据线或无线调试将生成的 APK 安装到三星 Galaxy Tab S9+。

---

## ⚙️ 在三星 Tab S9+ 上的启用步骤

1. 打开 **XYConvert** 应用。
2. 点击顶部的 **“前往系统设置开启无障碍服务”**。
3. 系统将自动跳转至三星系统的【辅助功能 / 无障碍】设置页：
   - 点击 **已安装的应用 / 服务**（Installed Services）。
   - 找到 **XYConvert 触控板轴对调服务**。
   - 开启主开关，并在弹出的三星安全提示中选择 **“允许”**。
4. 返回 XYConvert 应用，状态指示灯将显示为 **绿色运行中**。
5. 连接您的三星键盘保护套或外接蓝牙触控板，滑动手指即可体验对调后的操控！
