# 深清手机端（ShenQing Mobile · Android）

深清（Clarity）的 **Android 原生伴随端**：能真正锁手机的专注空间、桌面组件、数据、主题包，以及与桌面插件工作区的局域网互通。

> **为什么不是网页**：深度锁机需要 Android 的无障碍服务、前台服务、悬浮层与（可选的）Device Owner kiosk；浏览器/PWA 做不到。手机端采用 **Kotlin + Jetpack Compose**，minSdk 26 / targetSdk 35。

## 已实现

- **清番番茄钟**：主页卡片 + 底部「清番」页；可自定本轮番茄数（1/2/3/4/6/8）和专注分钟，完成设定数量即结束；可暂停/跳过/退出；完成计入今日次数；Live Updates 通知尝试进入 ColorOS 流体云/灵动岛。
- **四档专注**：浅清 / 覆清 / 深清 / 还清；浅清可暂停/结束，覆清/深清开始后不可暂停/结束，连点倒计时 10 次才出现出口。
- **OPPO 超禅**：检测到 OPPO/一加/realme 且安装禅定空间时，覆清/深清优先打开「超禅」并按 1/20/30/40/60/90/120 分钟映射时长；失败回退深清覆盖层。
- **深度锁机**：
  - 覆清/深清启动后开启全屏专注 Activity + 无障碍前台监听；切到其他应用、桌面、设置时自动盖回专注界面；
  - 无障碍服务用 TYPE_ACCESSIBILITY_OVERLAY 全屏覆盖（不依赖 SYSTEM_ALERT_WINDOW 权限），拦截触摸；
  - 可选 **Device Owner + Lock Task（真 kiosk）**：设置后 home/recents/状态栏由系统锁定，退出只能等计时结束或 10 连点；
  - 前台服务 + 精确闹钟 + 开机恢复 + 通知栏倒计时，进程被回收后尽量恢复。
- **桌面组件（App Widget）**：4×2 小部件，显示 **今日专注次数 / 近 7 天次数**，按钮一键快速专注（默认 25 分钟，可长按进入应用调整）。
- **主题互通**：
  - 内置 solid / light / glass / ocean，颜色从桌面 AppTheme.qml 生成（79 个颜色 × 4 模式 + 2 个列表）；
  - 支持 .sqtheme 主题包：手机端导入即用；桌面端用 tools/install_theme_pack.py 写入 custom_themes / theme_mode=custom:<id>，桌面 0.18+ 会即时应用；
  - 桥接插件 /themes 同时列出桌面已安装的 custom themes 与 %APPDATA%\ShenQing\themes\*.sqtheme，手机可直接下载应用。
- **插件互通**：
  - 手机端通过 shenqing.bridge/v1 读取桌面插件清单与 plugin.json 的 mobile 摘要/指标（例如番茄同步的今日次数）；
  - 不加载桌面 Python/QML，原生渲染插件卡片；
  - 主题包也通过插件工作区/桥接分发。

## 测试签名与安装冲突

- 所有预发布 APK 使用仓库内固定的测试签名 `keystore/shenqing-test.jks`（androiddebugkey / android），本地和 GitHub Actions 一致；
- 不要把它当正式签名；正式发版应换成独立的生产 keystore；
- 如果手机上已有旧 CI 构建（runner 临时 debug key 签名），首次升级会报「签名冲突」，需要卸载旧 App 一次；之后同一测试签名的更新可直接覆盖安装；
- 1.1.1 起版本号递增，不再出现同版本反复安装的问题。

## 自动发布（测试版）

`.github/workflows/release.yml` 在 **每次 push 到 main** 时：

1. Ubuntu + JDK 17 + Android SDK 35 构建 debug APK；
2. 生成唯一 tag：v<versionName>-build.<GitHub run number>；
3. 在 GitHub Releases 发布 **pre-release** 并附带 APK；
4. 明确 `make_latest: false`，**目前不发布正式版 / 不占用 latest**。

手机端「检查更新」读取 releases 列表（包含预发布），因此每次自动构建都能被检测到。正式发版前保持 pre-release 即可。

## 构建 / 安装

需要 Android SDK（compileSdk 35、build-tools 35.0.0）与 JDK 17：

    cd 深清手机端
    ./gradlew :app:assembleDebug
    adb install -r app/build/outputs/apk/debug/app-debug.apk

本仓库已生成 app/build/outputs/apk/debug/app-debug.apk（约 16 MB，debug 签名）。Windows 下也可用 Android Studio 打开本目录。

## 首次使用

1. 打开「深清 → 设置 → 锁机权限」，开启 **无障碍服务**（覆清/深清必需）；
2. Android 13+ 允许通知；建议忽略电池优化；
3. 想用真 kiosk：电脑执行一次

       adb shell dpm set-device-owner com.shenqing.mobile/.service.ShenQingDeviceAdminReceiver

   然后重启应用；未设置时使用无障碍 + 全屏覆盖层，仍属于 Android 允许的深度锁机范围。
4. 桌面长按空白 → 小组件 → 深清，添加 4×2 组件；
5. 需要和电脑插件互通：安装 深清插件开发/com.shenqing.aichat，在手机「设置 → 桌面连接」粘 shenqing://pair?...。

## 主题包（手机 / 电脑共用）

- 规范与示例：[主题包规范](<docs/主题包规范.md>)，样例 themes/aurora.sqtheme；
- 手机：插件页 / 设置页「导入 .sqtheme」；
- 桌面：先退出深清，执行

      python tools/install_theme_pack.py themes/aurora.sqtheme

  会备份 settings.json 并写入 custom_themes，重启深清即生效；
- 桌面已安装的自定义主题会由桥接 /themes 列出来，手机可以一键下载应用。

## 设置 / 商店

设置页顶部是账户卡：未登录显示「登录」，登录后显示昵称/邮箱并可进入「个人资料」设置本机昵称。下面是一组可点击进入新页面的入口：

- **跟随系统**：外观开关，系统深色用纯色、浅色用浅色主题；手动选主题会关闭跟随；
- **液态玻璃视觉**：卡片改为半透明玻璃 + 双色流光描边 + 高光；OPPO/ColorOS 自动使用「流光」彩边，其他厂商用中性玻璃。系统私有控件没有公开 API，采用自适应仿真；
- **外观商店**：拉取桌面在线主题目录（palette），应用后写入本地 `.sqtheme`；同时列出内置/本地主题；
- **插件商店**：拉取桌面商店插件目录，显示桌面已安装插件的 `mobile` 摘要/指标；手机端不执行桌面插件，安装说明在页面内；
- **账户与同步**：商店账号登录、从电脑读取配置、立即同步/拉取/上传；
- **检查更新**：GitHub Release 检查当前版本；
- **关于**：版本、GitHub 仓库与说明。

原设置页的主题、字号、锁机权限、桌面连接、组件、数据清理仍在下方。

## 账户同步（桌面 v0.20 / 服务端 /api/sync）

手机端设置页新增「账户同步」，直接使用桌面同一个商店服务端和账号：

- 服务器地址、邮箱、密码登录；密码只用于登录，**不保存**；
- 「立即同步」「从云端拉取」「上传手机设置」；
- 只合并快照里的 `settings`（theme_mode、ui_font_scale、custom_themes），
  **保留桌面的 spaces / stats 不动**；拉取时安装桌面自定义主题并应用；
- 上传前先拉最新 revision，409 冲突自动重拉再合并；
- 「从电脑读取配置」通过桥接 `GET /shenqing/v1/settings` 读取桌面主题/字号/同步地址/邮箱，
  不需要先登录（令牌不会暴露；登录仍需密码）。

服务端接口与桌面 v0.20 完全一致：

    POST /api/auth/login {email,password} -> {token}
    GET  /api/sync -> {revision, updated_at, payload, device_name}
    PUT  /api/sync {payload, revision, device} -> {revision} / 409 {revision}

## 目录

    app/src/main/java/com/shenqing/mobile/
      MainActivity / FocusActivity
      data/           模型、AppRepository、ThemeRepository、BridgeClient
      service/        FocusService、无障碍锁机、悬浮层、DeviceAdmin、开机/闹钟
      ui/             Compose 主题、页面（空间/数据/插件/设置/专注）
      widget/         桌面组件
    app/src/main/res/  layout / drawable / xml / values / mipmap
    theme/tokens.json  桌面 AppTheme 生成的主题令牌（79 色 × 4 模式）
    themes/            示例 .sqtheme 主题包
    contract/          桥接协议/主题 Schema 与 fixtures
    tools/
      build_theme_tokens.py  生成令牌 + Android asset，--check 校验漂移
      install_theme_pack.py  把 .sqtheme 安装到桌面
      mock_bridge.mjs        模拟桌面桥接（协议联调）
      conformance.mjs        协议一致性检查
    docs/              语言选型 / 兼容契约 / 主题包规范 / 协作记录 / 验收报告

## 验证

    python tools/build_theme_tokens.py --check        # 主题与 AppTheme.qml 同步
    python tools/install_theme_pack.py themes/aurora.sqtheme --check
    ./gradlew :app:assembleDebug                     # 0 error
    cd ../深清插件开发/com.shenqing.aichat && python -m unittest discover -s tests -v

当前版本：1.1.1（versionCode 3）

已验证：Android assembleDebug 成功；插件侧 24 个 Python 测试通过；主题令牌与桌面 QML 一致（79 色 × 4 + 2 列表）。源码与 APK 已推送 GitHub：Kryon2025/Clarity-APP（main）。
未在模拟器/真机上启动验收：本机沙箱会清理后台模拟器进程；APK 构建本身通过。详见 [验收报告](<docs/验收报告.md>)。

## 深度锁机的边界（必须知道）

- Android 没有 root / Device Owner 时，**任何第三方应用都无法做到绝对不可退出**：系统设置、强制停止、关机、恢复出厂仍可绕过；无障碍 + 全屏覆盖层是普通应用能做到的最强级别。
- 覆清/深清未开启无障碍时，应用会阻止开始并引导开启；浅清不需要。
- 部分 OEM（MIUI/ColorOS 等）会杀后台，需在系统里允许自启动/后台运行；应用内提供电池优化入口。
- iOS 不允许第三方深度锁机；本项目只做 Android。
