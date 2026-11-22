# 设置页 V2 · 验收清单

> 每条能力都对应：**自动化判据**（能跑的就跑）+ **真机证据**（截图/像素/哈希）。
> 只写本会话实测到的；没验过的写「未验证」。
> 版本基准：0.0.0.188（versionCode 226）；设备 TicWatch Pro 5，466×466，density 2.0。

## 1. 五套风格都能渲染，且互不相同

| 风格 | 卡/行形态 | 真机哈希（0.0.0.188，本轮实测） | 目视 |
|---|---|---|---|
| M3_CARD | 176dp 大卡、卡内两行堆叠 | `98f7f51535dc` | ✅ 卡内 模型/deepseek-v4-pro、温度/1.4、回复风格/BALANCED |
| M3_LIST | 无卡面、左对齐分区头、灰细线 | `e10a0ce9dae1` | ✅ 同上三行完整 |
| ARC | 贴边卡、26dp 安全边距 | `cc9966de7cea` | ✅ 左缘 3.24~7.05px（边缘缩放下限） |
| SQUARE | 居中分区头 + 1px 亮线 + 左右竖轨 | `4baf54756c77` | ✅ 竖轨可见 |
| TERMINAL | `#`/`>` 前缀、等宽绿字 | `30a02eec3130` | ✅ 缩进右值 |

判据：`SettingsV2ScreenTest`（五套身份互异）、`SettingsV2ArchitectureGuardTest`（每套一文件、互不 import）、
`style-shots-v2.mjs` 的 `distinct 5/5`（工具内置去重）。

## 2. 每套必备的三件动效

| 动效 | 实现 | 判据 | 真机/帧级证据 |
|---|---|---|---|
| 入场逐行错峰 | `rememberEnterProgress(index)`，参数 40/26/50/0/18ms | `SettingsV2MotionTest`（延迟 = index×stagger）、`SettingsV2ArchitectureGuardTest` | `SettingsV2StaggerFrameTest`：120ms 时第 1 行位移 > 第 2 行；2s 后间距回到稳态（<1px） |
| 磁滑吸附 | `snapFlingBehavior`（TERMINAL 关闭） | 架构守卫：除 TERMINAL 外必须有真实调用 | **已量化证明**（`snap-probe.mjs`，判据 R4）：M3_CARD 12 个拖动距离（12~400px）只留 **3 个静止态**，其中 36/48/60/72/84/96/160 七个距离哈希完全相同；对照组 TERMINAL 8 个距离留 **7 个**静止态、最大共用距离数仅 2（两次空拖）。详见 §7.1 |
| 边缘淡出 + 等比缩放 | `reportEdgeDistance` + `edgeAppearance`（`scaleX=scaleY`） | 架构守卫：四套必须有接线；M3_CARD 走官方 `SurfaceTransformation` | 接线前后哈希变化可证：M3_LIST `cb38ad9a→a48bed06`、ARC `a61368d4→c96b911f`、SQUARE `f7bfa828→714e394d`、TERMINAL `bf02da38→90c4b553` |

**真机截不到的物理原因**：`adb exec-out screencap` 单次 469~555ms（均值 506ms），入场动效仅 200~300ms。

## 3. 几何与排版约束

| 约束 | 判据 | 实测 |
|---|---|---|
| 除 ARC 外同宽 176dp | `SettingsV2CardWidthTest`（4 条） | 卡宽 = 176dp；M3_CARD 一屏 1 张 + 第二张露头 |
| ARC 贴边（允许超 176dp） | `ArcStyleTest`（卡宽 > 176dp、左缘 ≤8px、安全边距 26dp） | 左缘 3.24~7.05px |
| 行高 ≥48dp（触控） | `SquareAndTerminalStyleTest` / `M3ListStyleTest` | SQUARE **70dp**（0.0.0.195 起，原 48dp）、M3_LIST 48dp、TERMINAL 48dp（0.0.0.193 起，原 40dp 容器/行距 46dp → 实测 **104px/52.0dp**） |
| M3_LIST vs SQUARE 可区分 | `squareIsLooserThanM3ListByAtLeastTwelveDp`（行高差 ≥12dp）+ `squareHairlineIsInsetWhileTheOfficialListIsFullBleed`（策略守卫） | 行距 114px/57dp vs **150px/75dp**（差 36px/18dp）；分隔线 304px×2px 通栏 vs **253px×1px** 内缩（每侧 25px） |
| 华为页边距 | `ArcStyleTest`（26/20/6/40vp 常量） | 尾留白复测（0.0.0.221，确认真到底）：末行底边 y=244，距屏底 **222px = 111dp**；其中内容区内 126px（≈63dp）、全局底部内边距 48dp。旧记录的 138px 是**未滚到底**时量的，已作废 |
| 不硬编码字号 | 架构守卫（除 TERMINAL 外禁止 `sp`） | 通过 |
| 底部按钮不压内容 | `bottomInsetMatchesTheStyleButtonAndContentIsClipped`（常量 48dp + 源码守卫 `clipToBounds()`） | 列表底 386→**370**，与按钮顶边齐平；按钮框外亮像素 **905 → 0** |

## 4. 真实数据

| 项 | 判据 | 真机 |
|---|---|---|
| 模型 / 温度 / 回复风格 / 页面风格 接实值 | `SettingsV2PreferencesWiringTest`（3 条） | 设备实值 `deepseek-v4-pro`、`1.4`、`BALANCED` 上屏 |
| 空值兜底、温度一位小数 | 同上（`未设置`、`0.75→0.8`） | — |

## 5. 手表内换风格（V2 之前完全没有）

| 项 | 判据 | 真机 |
|---|---|---|
| 打开选择器 | `SettingsV2StylePickerTest`（3 条） | 底部「风格」按钮 → 覆盖层（截图 `picker.png`） |
| 五套都在、可滚动 | 同上（逐项断言） | 滚动后可见「终端」 |
| 选中即切换 | 同上（回调断言） | 点「终端」→ 页面立刻变终端形态（`after-pick.png`），无需重启 |
| V2 → 旧枚举落库 | 同上（五套映射互不相同） | `preferences.settingsStyle` 写入 + `SettingsChangeBus.bump()` |

## 6. 旧实现清理

| 项 | 结论 |
|---|---|
| 起始 | `ui/settings/` 55 个文件 / ~500KB |
| 已删 | 32 个（旧页面根、十套渲染件、各 Section 与行控件）|
| 保留 | 23 个，**全部被活代码引用**（逐文件「删除→编译→还原」验证；其中 4 个能过主代码编译但会让测试/真机红） |
| 连带清理 | 孤立文案 1044×2 条、过期守卫 21 个 |

## 7. 明确未验证 / 已知取舍

1. **磁滑吸附：已量化证明**（`docs/tools/snap-probe.mjs`，判据 R4，0.0.0.194 轮实测）。
   录屏在 Wear 上拿不到有效帧，`screencap` 单次 469~555ms 又长于滚动动画，所以不用"看动画"，
   改用**离散性**判决：慢拖（900ms，末速度≈0.04px/ms，不产生惯性）松手后，吸附生效则内容被重新
   对齐到锚点，多个不同拖动距离会**塌陷**到同一静止态；无吸附则每个距离留下一个独立态。

   | 风格 | 拖动距离(px) | 哈希静止态 | 最大共用距离数 | 结论 |
   |---|---|---|---|---|
   | M3_CARD（吸附开） | 12/24/36/48/60/72/84/96/160/240/320/400 | **3** | **7**（36~160 全同） | 态数≤4 且最大组≥3 → PASS |
   | TERMINAL（吸附关，对照） | 12/24/36/48/60/72/84/96 | **7** | 2（12/24 是未过 slop 的空拖） | 态数≥4 → PASS |

   - 交叉校验：marker 可见的 9 次里，`uiautomator` 坐标划分与像素哈希划分**完全一致**。
   - 已排除"拖到底了"这个伪解释：96/160 与 240/320/400 属于**两个不同**的静止态
     （后者首卡已滚出视野）；若只是到底，两者必然同态。
   - 写错过的两版判据留档在工具头部：R2 假设"位移=行距"（实测 25px；卡内行距 86px 与文字高 47px
     均不变 → `SurfaceTransformation` 是**平移**非缩放，前提不成立）；R3 拿被屏幕裁切的坐标判对照组
     （marker 顶到 y=0 后 h 从 32 掉到 3）。
2. **TERMINAL 行高：已修（0.0.0.193）**。40dp 容器 → 48dp，真机行距 92px/46.0dp → **104px/52.0dp**，
   `SquareAndTerminalStyleTest` 已加 `rowMinHeight ≥ 48dp` 断言锁死，防回退。
3. **选择器遮挡**：已在 SQUARE / TERMINAL 底层各截一张（`picker-square.png` / `picker-terminal.png`），
   据此发现覆盖层 95% 不透明时底层文字会 ghost（TERMINAL 下看到「&gt; 温度」），已改为全不透明并加源码守卫
   `thePickerBackgroundIsFullyOpaque`；**修复后已在真机确认**（`picker-opaque.png`，TERMINAL 底层打开选择器，顶部不再有 ghost 文字）。
4. **ARC 尾留白**在滚动中途的表现未拍（只拍了到底）。
5. **设备状态**：TicWatch 曾于 20:35 掉线，21:0x 恢复；恢复后已补跑 `release-check` **8/8**（0.0.0.191 装机）
   与五套重拍（`distinct 5/5`，哈希与 0.0.0.188 一致 —— 选择器不透明化不影响页面本身）。

5. **物理表冠走哪条通道：未验证（但已做成一条命令）**。
   代码里三条路都已接线：`ACTION_SCROLL`（onGenericMotionEvent）、音量键映射（onKeyDown VOLUME_UP/DOWN）、
   Enter（onKeyDown ENTER/DPAD_CENTER，且按下与抬起都消费）。设备侧只有"音量键"这条被实测证明
   （注入 KEYCODE_VOLUME_DOWN -> 位移 {-36, 0}，一格正好一行）。
   `ACTION_SCROLL` 无法用 adb 注入（sendevent 的 REL_WHEEL、input roll 的 trackball 事件，两层日志全 0）。
   判定命令：`node docs/tools/crown-check.mjs` —— 它会清日志、提示你转表冠、读回 `TerminalRotary` 并判定
   （已自测两条分支：无事件 -> 退出码 2 + 排查指引；注入音量键 -> 判为 key 通道）。
