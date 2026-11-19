# UI 证据台账（每轮 UI 改动的真机数字）

> 用途：S/P 阶段每轮都要有"截图 + 像素/几何数字"。这里按轮次记，避免证据散在对话里。
> 截图统一放 `docs/device/vNN/`。设备：TicWatch Pro 5，466×466，density 2.0（1dp = 2px）。

## 0.0.0.123 · S2 方角发丝线（缩进 + 半像素）

- 截图：`docs/device/v23/square-from-release-check.png`（SQUARE 档根页底部：无卡片、只有发丝线）
- 量法：逐行扫描 longest 连续非黑像素段（阈值 RGB 和 > 45）

| 量 | 实测 | 对应代码 |
|---|---|---|
| 发丝线宽度 | **296px**（=148dp），起点 x=85 | 卡宽 352px 两侧各缩进 **28px = 14dp**（`SettingsRowSidePadding`） |
| 发丝线高度 | **1 物理像素**（每条线只出现在一个 y：31 / 128 / 225） | `0.5.dp`（2.0 密度 → 1px） |
| 线间距（行高） | 128 − 31 = **97px**，225 − 128 = **97px** | 行距由 `RowVerticalPadding` + 文本高度决定 |

结论：方角档的"缩进 + 半像素"在真机上成立，且与文字左缘对齐（x=85 与文字左缘同列）。

## 0.0.0.122 · S1 气泡留白（组内上下 8dp）

- 截图：`docs/device/v22/balloon-121.png`
- 卡片宽度：**352px = 176dp × 2.0**，81 行采样 min = median = max = 352（同宽约束成立，无更宽的行）

## 0.0.0.119 · 磁滑（吸附到"某一项正好居中"）

- JVM 手势测试：`snapOffset=72dp → centerOffset=-4px`；默认 `0dp → centerOffset=0`
- 真机 5 次快甩落点：`28 / 62 / 62 / 62 / 62` px（稳定收敛；精确居中由 JVM 测试证明）

## 已知：进设置页的可靠路径（写进台账免得每轮重踩）

- `ChatScreen.kt:854` 审批浮层上的**阻尼上滑**（`onSwipeUpSettings`）；
- `ChatScreen.kt:838` 底部右侧翼按钮打开**会话抽屉**，抽屉里有设置入口（`onOpenSettings`）。
- 从**空聊天页直接上滑不会进设置**（会被列表滚动吃掉）—— 所以纯 adb 手势截图不稳定，
  本轮改用 `node docs/tools/release-check.mjs` 的第 2 段（它自己会上机进设置页并截图），一次成功。
