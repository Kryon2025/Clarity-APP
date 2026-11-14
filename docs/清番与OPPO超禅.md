# 清番、灵动岛与 OPPO 超禅

## 1. 清番（番茄钟）

手机端新增「清番」底部页签 + 主页清番卡片，与桌面 v0.16 的清番对齐：

- 主页可直接「开始」并跳转清番页；
- 开始前可选专注分钟数（15/25/45/60）和**本轮番茄数**（1/2/3/4/6/8）；
- 完成设定的番茄数后自动结束本轮；

- 专注 / 短休息 / 长休息三阶段；
- 默认 25 分钟专注、5 分钟短休息、每 4 轮 15 分钟长休息；
- 可暂停、继续、跳过当前阶段、退出；
- 完成一个专注阶段会写入「清番」专注记录，计入今日次数 / 近 7 天次数；
- 独立于深清锁机：清番运行时不会自动开启锁机，锁机仍按空间规则。

前台服务 PomodoroService 负责跨应用计时与通知；退出应用后计时继续，通知栏可暂停/跳过/退出。

## 2. 灵动岛 / ColorOS 流体云

手机系统没有统一的第三方「灵动岛」API。当前实现分两层：

1. **Android 16 Live Updates（首选）**
   - 使用 Notification.ProgressStyle（反射构建，避免 compileSdk 必须升到 36）；
   - setRequestPromotedOngoing(true)、setShortCriticalText；
   - ColorOS 16 的流体云/胶囊可识别 promoted ongoing notification；
   - 清单声明 POST_PROMOTED_NOTIFICATIONS。
2. **旧系统回退**
   - 普通 ongoing 进度通知（倒计时 + 进度 + 暂停/跳过/退出）；
   - 不会伪装成系统灵动岛。

注意：OPPO / ColorOS 的完整「流体云卡片」还涉及 Pantanal/Seedling 平台准入与 UPK 发布，本地 APK 不能保证上岛；Android 16 Live Updates 是当前可用的标准通道。系统是否展示由 ColorOS 决定。

## 3. OPPO / 一加 / realme 超禅

检测到 OPPO 家族（制造商标识含 oppo/oplus/oneplus/realme）且安装了禅定空间（com.oneplus.brickmode 等候选）时：

- 设置页出现「OPPO 超禅锁机」开关，默认开启；
- 启动覆清/深清前，优先调用禅定空间：
  1. 用 launch intent 打开禅定空间；
  2. 尽力携带 minutes/duration/deep_zen/super_zen/mode=deep extras；
  3. 无障碍服务按文本尝试点「超禅」→ 对应时长 → 「开始」；
  4. 深清仍启动 FocusService 记录计时，但不再叠加自己的全屏遮罩；
- 时长映射到超禅支持值：1/20/30/40/60/90/120 分钟，取不小于请求值的最小项；
  - 25 → 30，45 → 60，90 → 90，超过 120 → 120；
  - 实际会话时长也改为映射后的值，避免深清提前结束而超禅还在锁。
- 如果禅定空间未安装/无法启动/自动化失败：回退深清自己的无障碍覆盖层；未开无障碍则提示开启。

### 限制

- 禅定空间没有公开的本地 API；extras 与文本自动化属于尽力而为，ColorOS 版本/语言/UI 变化可能导致失败；
- 需要用户安装并同意禅定空间；部分系统会限制第三方启动系统应用；
- 未在 OPPO 真机上验收，需真机确认包名、文案和超禅流程；
- 超禅是系统级锁机，深清无法在超禅期间暂停或提前结束；这是预期行为。

## 4. 设置

- 清番页：15/25/45/60 分钟快捷选择；
- 设置 → 锁机权限 → OPPO 超禅锁机开关（仅 OPPO 家族显示）；
- 通知权限 + Android 16 的实时更新授权由系统控制。

## 5. 验证

- Android assembleDebug 成功；
- 无 OPPO 真机，禅定空间调用/自动化/流体云展示未做真机验收；
- 旧系统通知回退可构建；
- 相关代码：PomodoroScreen.kt、PomodoroService.kt、LiveNotification.kt、OppoZen.kt、FocusAccessibilityService.kt。
