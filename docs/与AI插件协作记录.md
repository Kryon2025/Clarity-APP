# 与桌面插件 / 主题工作区的协作记录

> 记录手机端（Lead）与插件侧 AI（aichat-plugin）以及桌面 0.18 主题/商店工作的对接结论。

## 1. 方向修正

- 第一版交付是 TypeScript PWA；用户明确要求“不要网页，要真正能深度锁机的手机端”。
- 已删除网页实现，改为 Kotlin + Jetpack Compose Android 原生；PWA 不能访问无障碍/悬浮层/Device Owner/桌面组件。

## 2. 与插件侧的兼容

- 契约 v1 保留：/ping、/manifest、/theme、/spaces、/stats、/chat、/conversations、/plugins、CORS、Bearer 认证。
- 新增：
  - GET /shenqing/v1/themes 与 /themes/{id}：返回 .sqtheme 与桌面 custom_themes；
  - plugin.json 增加 mobile 摘要/指标；桥接从 plugin_data 读取 metrics（例如番茄同步今日次数）；
  - 手机端原生插件页展示这些指标。
- 插件侧测试：23/23 通过（20 个原有测试 + 3 个 /themes / 桌面自定义主题 / mobile metrics 测试）。
- 主题令牌：79 色 × 4 模式 + 2 列表，插件 theme_tokens.json 与手机端 theme/tokens.json 逐字节一致。

## 3. 与桌面 0.18 主题工作的兼容

桌面已实现 v0.18 自定义主题：

- AppTheme.qml 支持 mode = custom:<id> 与 customPalette；
- settings.custom_themes / theme_mode = custom:<id>；
- 主题页和商店可安装/删除自定义主题。

手机端对接方式：

1. .sqtheme 使用桌面 6 键 palette（bgTop/bgMid/bgBottom/accent/cardBg/textPrimary），与桌面 custom theme 完全一致；
2. tools/install_theme_pack.py 把 .sqtheme 写入桌面 custom_themes，重启即生效；
3. 桥接 /themes 同时列出桌面 custom_themes 与 %APPDATA%\ShenQing\themes\*.sqtheme；
4. 手机端导入/下载同一文件，叠加 modes.solid.colors 到内置主题。

## 4. 验证

- Android assembleDebug 成功，APK 16 MB；
- 插件 Python 23 tests OK；
- 主题令牌 --check 通过；
- 主题包校验通过。
- 未在真机/模拟器启动：本机沙箱清理后台模拟器进程；需真机补验。

## 5. Lead 裁决

- 深度锁机以 Android 原生为唯一目标平台；iOS 不做深度锁机。
- v1 手机端不执行桌面 Python/QML 插件；只读 mobile 元数据/指标。
- 桌面主题动态加载沿用 0.18 的 custom:<id>，不自建第二套主题系统。
- 远程控制桌面专注仍未开放；需要宿主 API 扩展。
