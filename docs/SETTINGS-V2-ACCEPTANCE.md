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
| 磁滑吸附 | `snapFlingBehavior`（TERMINAL 关闭） | 架构守卫：除 TERMINAL 外必须有真实调用 | 未拍帧证据（滚动惯性无法静态截图证明）——**未验证** |
| 边缘淡出 + 等比缩放 | `reportEdgeDistance` + `edgeAppearance`（`scaleX=scaleY`） | 架构守卫：四套必须有接线；M3_CARD 走官方 `SurfaceTransformation` | 接线前后哈希变化可证：M3_LIST `cb38ad9a→a48bed06`、ARC `a61368d4→c96b911f`、SQUARE `f7bfa828→714e394d`、TERMINAL `bf02da38→90c4b553` |

**真机截不到的物理原因**：`adb exec-out screencap` 单次 469~555ms（均值 506ms），入场动效仅 200~300ms。

## 3. 几何与排版约束

| 约束 | 判据 | 实测 |
|---|---|---|
| 除 ARC 外同宽 176dp | `SettingsV2CardWidthTest`（4 条） | 卡宽 = 176dp；M3_CARD 一屏 1 张 + 第二张露头 |
| ARC 贴边（允许超 176dp） | `ArcStyleTest`（卡宽 > 176dp、左缘 ≤8px、安全边距 26dp） | 左缘 3.24~7.05px |
| 行高 ≥48dp（触控） | `SquareAndTerminalStyleTest` / `M3ListStyleTest` | SQUARE 48dp、M3_LIST 57dp、TERMINAL 46dp（等宽行，标题 40dp 容器 + 上下 4dp） |
| 华为页边距 | `ArcStyleTest`（26/20/6/40vp 常量） | 尾留白实测：末行底部距屏底 **138px = 69dp**（无尾留白时约 29dp） |
| 不硬编码字号 | 架构守卫（除 TERMINAL 外禁止 `sp`） | 通过 |

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

1. **磁滑吸附的视觉证据**：无法用静态截图证明滚动惯性，只有代码级守卫。
2. **TERMINAL 行高 46dp**：低于 48dp 触控下限（等宽行 + 4dp 上下留白），属风格取舍；若你要求统一 ≥48dp，我把行内 padding 调到 6dp。
3. **选择器遮挡**：已在 SQUARE / TERMINAL 底层各截一张（`picker-square.png` / `picker-terminal.png`），
   据此发现覆盖层 95% 不透明时底层文字会 ghost（TERMINAL 下看到「&gt; 温度」），已改为全不透明并加源码守卫
   `thePickerBackgroundIsFullyOpaque`；**但修复后的真机截图未拍**（手表 20:35 掉线）。
4. **ARC 尾留白**在滚动中途的表现未拍（只拍了到底）。
5. **设备状态**：20:35 起 TicWatch（C101X35122973）从 adb 掉线，当前只有 9D90900007505378；
   因此 0.0.0.190 的真机验证（release-check 8/8、五套重拍、选择器不透明化）全部**未验证**，
   恢复连接后需补跑。
