# 设置页 V2 · 全面重构（研究定稿版）

> 2026-11-20 用户决定：旧设置页积重难返，**全部重做**；风格从 10 套砍到 **5 套**。
> 本文是"查完资料后"的定稿：先列**研究结论与出处**，再列**规格**，最后是**分轮计划与验收**。
> 纪律照旧：每轮内部版 +1、提交前跑全量单测、没验证的写「未验证」。

---

## 0. 上一版为什么失败（复盘，写死不再犯）

| 症状 | 根因 |
|---|---|
| 十套里四套是空壳（NEON/PAPER/GLASS/ARC） | 只在枚举里"登记"了风格、写了参数表，**没有渲染实现就宣布完成** |
| 改一处影响十套 | `SettingsShared.kt` 2000+ 行，十套风格共用一条渲染链，靠 `if (style == X)` 分支 |
| 贴边卡做不出来 | 行宽被页面层 `Modifier.width(176.dp)` 写死，行内 `fillMaxWidth()` 只能填满它 |
| 两次"改了没生效" | 代码插进了**执行不到的分支**（0.0.0.137 `belowContent`、0.0.0.150 HAIRLINE 分支） |
| 四次误判（裁切/空壳/误选/纸色被盖） | 只取**一个采样点**就下结论；边缘像素被圆屏暗角压暗 |

**新纪律**：①一套风格 = 一个文件 = 一次真机证据；②判据先写（可自动化的先自动化）；③任何视觉结论至少 3 个采样点。

---

## 1. 研究结论（有出处）

### 1.1 M3E 的"贴边"是官方能力，不是自研

- **edge-hugging container / edge-hugging button** 是 M3E for Wear 的招牌：
  "A new shape system with edge-hugging containers and buttons creates variety and visual balance on round screens."
  —— [m3.material.io/foundations/watches](https://m3.material.io/foundations/watches)
- 官方中文文档同样强调"**贴边容器**（采用圆形设计、最大限度利用圆形设备空间）"与"**贴边按钮**作为圆形设备上的标志性模式"
  —— [Material 3 富有表现力的设计语言（Wear）](https://developer.android.com/design/ui/wear/guides/get-started/apply?hl=zh-cn)
- 中文解读：圆屏相比方屏**损失约 22% 可用空间**，edge-hugging 正是为了把这块空间拿回来；按钮/列表边缘改成**圆弧收边**，底部按钮曲率与表盘曲率贴合
  —— [uisdc：M3E 解读](https://www.uisdc.com/wear-os-app-2)

**结论**：ARC 不要再手搓 `RoundedCornerShape(0,28,28,0)`，要走官方形状与 transform API。

### 1.2 官方真实 API（我 javap 了 `compose-material3-1.7.1.aar`，不是猜）

```kotlin
// 存在且有方法体：
androidx.wear.compose.material3.SurfaceTransformation {
    fun createContainerPainter(painter: Painter, shape: Shape, borderStroke: BorderStroke?): Painter
    fun applyContainerTransformation(scope: GraphicsLayerScope)
    fun applyContentTransformation(scope: GraphicsLayerScope)
}

// Card 的完整签名里就有这两个参数（第 7、11 位）：
Card(..., shape: Shape, colors: CardColors, border: BorderStroke, contentPadding: PaddingValues,
     interactionSource: MutableInteractionSource, transformation: SurfaceTransformation, content: ...)
```

官方示例（`github.com/android/skills` 的 `TransformingLazyColumnSample.kt.md`）用法：

```kotlin
val transformationSpec = rememberTransformationSpec()
TransformingLazyColumn(...) {
    items(count = 10) { index ->
        Card(
            onClick = {},
            modifier = Modifier.fillMaxWidth().transformedHeight(this, transformationSpec),
            transformation = SurfaceTransformation(transformationSpec),
        ) { ... }
    }
}
// 磁滑（吸附）官方写法：
rotaryScrollableBehavior = RotaryScrollableDefaults.snapBehavior(scrollableState = state)
flingBehavior = TransformingLazyColumnDefaults.snapFlingBehavior(state = state)
```

**结论**：之前我在 `compose-foundation` 里找 `SurfaceTransformation` 找不到，是因为它属于 **`compose-material3`**；
"自己的 graphicsLayer 缩放"是我写对了效果、写错了层级 —— 官方那条路同时给你**形状变形 + 高度变换 + 边框**。

### 1.3 华为（可用的硬数字）

**智能穿戴 UX 体验标准 8.1.1 圆形屏边距**（[官方](https://developer.huawei.com/consumer/cn/doc/doccenter-ux-design/ux-guidelines-wearable-0000002503475023?docScope=all)）：

| 项 | 数值 |
|---|---|
| 左/右边距 | **26 vp** |
| 顶部边距 | **20 vp** |
| 底部边距 | **6 vp** |
| 长页滑到底部边距 | **40 vp** |
| 文本对齐 | 圆屏**建议居中** |

**穿戴卡片规范**（[官方](https://developer.huawei.com/consumer/cn/doc/design-guides-V1/service-widget-devices-watch-0000001098059484-V1)）：

- 卡片结构 = **标题区（icon/应用名）+ 内容区 + 操作区（可选）**；
- 布局：卡片左右 26vp；顶部用 icon 时 6vp、用标题时 20vp；底部无按钮 40vp、有按钮 20vp；
- 字号：重要信息 **≥18fp**；Headline 5 = 30fp、Headline 6 = 24fp、Headline 7 = 19fp、Body 1 = 19fp；
- 圆屏要把主要内容**收进圆内**，避免被裁。

### 1.4 排版与字体（M3 权威 token）

| 角色 | 字号/行高(px) | 字重 | 字距 |
|---|---|---|---|
| Title Large | 22 / 28 | 500 | 0 |
| Title Medium | 16 / 24 | 500 | 0.15 |
| Title Small | 14 / 20 | 500 | 0.1 |
| Body Large | 16 / 24 | 400 | 0.5 |
| Body Medium | 14 / 20 | 400 | 0.25 |
| Body Small | 12 / 16 | 400 | 0.4 |
| Label Large | 14 / 20 | 500 | 0.1 |
| Label Medium | 12 / 16 | 500 | 0.5 |
| Label Small | 11 / 16 | 500 | 0.5 |

来源：[M3 type scale tokens 摘录](https://github.com/yhongm/material-design-skill/blob/master/references/typography.md)（与官方 `typescale-*` 一致）。
组件对应：**卡片标题 Title Medium、卡身 Body Medium、按钮 Label Large、对话框标题 Headline Small**。

### 1.5 手表 UI 的通用原则（跨平台共识）

- 一屏**一个主数据点**，层级清晰；主信息**不用滚**就能看到；
- **5 秒**交互预算；正文 ≥16pt、标题 ≥18pt（我们这里是 sp）；
- 大触控目标 + 居中排布，避免误触；
- **表冠是主滚动方式**；离散值要有**触觉刻度**；
- 文案能删就删，图标优先。
  来源：[watchOS design rules](https://github.com/dirnbauer/webconsulting-skills/blob/main/skills/watchos-design/rules/_sections.md)、[智能手表 UI/UX 指南](https://jaipurtech.in/public/blog/wearable-ux-design-guidelines-smartwatch)

### 1.6 谷歌健康（Google Health）的大卡片形态

- 首页是**可自定义的 focus tiles**：**一个大圆盘 + 3 个小 tile**，一屏 4 个；
- 点 tile 进全屏单指标页（日/周/月/年）；
- 设备设置页（Fitbit→Health 改版）首屏是**大预览卡**（设备 + 表盘 + 连接/电量/上次同步）。
  来源：[Droid-Life](https://www.droid-life.com/2026/06/01/heres-the-best-way-to-setup-the-new-google-health-app/)、[Android Central](https://www.androidcentral.com/wearables/fitbit/i-made-the-new-google-health-app-my-own)、[9to5Google 设置页改版](https://9to5google.com/2025/06/15/fitbit-device-settings-redesign/)

**结论**：`M3_CARD` 不是"每张卡塞 3 行设置"，而是**一屏一张主卡 + 2~3 条状态行 + 少量入口**。

---

## 2. 五套风格的定稿规格

| 风格 | 形态 | 关键数字 | 用到的官方能力 |
|---|---|---|---|
| `M3_CARD`（Google Health 大卡） | 一屏 1~2 张主卡；卡内 Title Medium + 2~3 条 Body Medium 状态行；底部 EdgeButton | 卡内边距 **12dp**；卡间距 **8dp**；主卡 **≥140dp** 高；圆角 **26dp** | `Card(transformation=…)` + `rememberTransformationSpec()` |
| `M3_LIST`（官方列表） | 无卡面，左标题右值/开关；行高 ≥ **48dp** | 行内边距 12/8dp；行间 1dp 分隔 | `TransformingLazyColumn` + `transformedHeight` + snapFling |
| `ARC`（华为贴边卡） | 卡片**外侧贴屏**（0 圆角）、内侧 28dp；左右 26vp 边距规则用于文本 | 页边距 **26vp**；顶部 **20vp**；底部 **6dp**；长列表底部 **40vp** | 官方贴边形状（`EdgeButtonShape`/`ShapeHelper`）+ 我们的宽度策略 `EdgeToEdge` |
| `SQUARE`（方角） | 0 圆角、1px 发丝线、无卡面 | 线宽 **1px(0.5dp)**；行距与字号同步 | 纯 Compose 绘制 |
| `TERMINAL`（终端） | 近黑底 + 等宽绿字 + `> ` 前缀 | 底 `#0B0F0C`、绿 `#3BFF9A`、线 `#123322` | 纯 Compose |

通用（五套都必须有）：
- 配色只用主题 token（surface/surfaceContainer/primary…），不硬编码随机色；
- 字体只用上表 9 档中的 **3~4 档**；
- 每套自己实现入场错峰与磁滑，参数写在 `StyleIdentity` 侧，不再全局共享。

---

## 3. 目录与边界（已建好骨架）

```
presentation/ui/settingsv2/
├── SettingsV2Style.kt     # 5 套枚举 + StyleIdentity（圆角/宽度策略/配色）
├── SettingsV2Data.kt      # 纯内容模型（Section/Row/RowTrailing），不含渲染
├── SettingsV2Screen.kt    # 唯一入口：按风格分发，无行内样式分支
└── style/
    ├── M3CardStyle.kt     # ✅ R1 已完成（大卡片）
    ├── M3ListStyle.kt     # R2
    ├── ArcStyle.kt        # R3
    ├── SquareStyle.kt     # R4
    └── TerminalStyle.kt   # R4
```

**硬规则**：`style/*` 只允许 import 官方库 + `SettingsV2Data`；**禁止互相 import**；禁止 import 旧 `ui/settings/`。

---

## 4. 分轮计划（每轮 1 个交付物 + 判据）

| 轮 | 交付 | 判据（自动化优先） |
|---|---|---|
| **R1** | `M3_CARD` 大卡片（本轮已写骨架，待按规格修） | Robolectric：存在 ≥140dp 高的卡；卡宽 ≤ 屏宽-16dp；5 套身份互不相同 |
| **R2** | `M3_LIST` 官方列表 | 行高 ≥48dp；行底色 == 页面底色（无卡面）；`TransformingLazyColumn` 渲染成功 |
| **R3** | `ARC` 贴边卡 | **宽度 > 176dp 且 left ≤ 2px**（这条上次被宽度上限卡死，这次宽度由 `RowWidthPolicy` 决定）；页边距 26vp |
| **R4** | `SQUARE` + `TERMINAL` | 方角：角半径 0、线宽 1px；终端：`> ` 前缀 + `#3BFF9A` + `#0B0F0C` |
| **R5** | 入口切换 + 删旧 | App 设置入口指向 `SettingsV2Screen`；旧 `ui/settings/` 删除；全量单测绿 |
| **R6** | 真机五视角 ×5 套 + release-check 8/8 + tag | 每套 5 张截图 + 3 个数字（宽/高/色值） |

---

## 5. 待用户拍板（会影响 R3）

**ARC 是否允许突破"卡片同宽 176dp"？**
- 官方 edge-hugging 的前提是"卡片贴到屏幕边"；
- 建议：**ARC 允许贴边满宽（外侧 0 圆角）**，其余四套仍 176dp。
- 若不同意，ARC 只能退化成"普通圆角卡 + 弧形分隔线"（即旧版做法），辨识度会明显下降。

---

## 6. 未验证项（照实）

- `ShapeHelper` 的公开 API 我还没 javap（下一步做），所以"官方贴边卡形状怎么构造"尚未定稿；
- 华为文档只读到检索摘要里的 8.1.1 / 卡片规范数字，**未读全文**（`web_fetch` 在本会话仍 404）；
- M3E 的 shape morphing 具体圆角插值曲线未查（属细节，R2 之后再说）。

---

## 7. 删除旧实现的范围评估（0.0.0.160 实测）

`ui/settings/` 现有 **55 个文件 / 约 500 KB**，其中**不全是"旧设置页"** —— 有一部分是被全 App 共用的基础设施。
直接整个目录删掉会立刻编译失败，必须先拆分。

### 7.1 必须保留（外部引用次数，实测）

| 符号 | 文件 | 外部引用 | 用途 |
|---|---|---|---|
| `LocalAnimationsEnabled` | `SettingsMotion.kt`(或同名文件) | **20** | 全 App 的动效总开关 |
| `motionSpec` | 同上 | **26** | 全 App 的动效 spec |
| `WearAppearance` | `AppearanceSettings.kt` | **20** | 字体缩放/强调色/风格的内存态（V2 与主题都读它） |
| `SettingsStylePolicy` | `SettingsStylePolicy.kt` | 5 | 控制台/导入导出按策略读写风格 |
| `PaperPalette` / `TerminalPalette` | stylekit | 6 / 4 | 主题与背景取色仍在用 |
| `SettingsContentWidth` | `SettingsShared.kt` | 4 | 控制台/几何判据在用 |
| `HapticAmplitude` | `SettingsHaptics.kt` | 3 | 偏好仓库导入导出用 |
| `SettingsScreenVeilMs` | 动画相关 | 4 | 页面转场遮罩时长 |
| `SettingsChangeBus` | 已在 `agent/` 包 | 7 | 设置变更广播 |

### 7.2 可以删（旧十套风格的页面实现与判据）

- `SettingsScreen.kt`（旧设置页根）、`SettingsBalloonGroup.kt`、`SettingsStyleCards.kt`、
  `SettingsOfficialControls.kt`、`SettingsSliderRow.kt`、`SettingsSwitchRow.kt`、`SettingsRadioRow.kt`、
  `SettingsRowStagger.kt`、`SettingsCardEdgeZoom.kt`、`SettingsShared.kt` 里与行渲染相关的部分；
- `stylekit/` 里仅为旧风格服务的：`StyleMotion.kt`、`NeonPalette.kt`、`GlassPalette.kt`、`ArcPalette.kt`、`TerminalText.kt`；
- 以及对应的 **~80 个测试文件**（`SettingsHairlineStyleTest`、`GoogleCardGranularityTest`、`StyleMotionTest` 等）。

### 7.3 执行顺序（每步都要全量单测绿）

1. **抽离**：把 7.1 的符号搬到一个中性包（例如 `presentation/ui/appearance/`），调用方改 import；
2. **删页面**：删 7.2 的渲染文件与其测试，跑全量单测；
3. **删 stylekit 残留**：确认无引用后删除，跑全量单测；
4. **跑 `release-check` 8/8**，然后打 tag。

**结论**：这是 2~3 轮的活，不是一次 `git rm -r`。本轮先把范围钉死，避免"删一半编译不过"。

### 7.4 抽离尝试失败（0.0.0.162，已全部回退）

按 7.3 第 1 步把 `SettingsMotion.kt` / `AppearanceSettings.kt` / `HapticAmplitude.kt` / `SettingsRowStaggerMath.kt`
移到 `presentation/ui/appearance/`，然后用脚本批量改写 import。结果**连续 8 次编译失败**，全部回退：

| 次数 | 失败现象 |
|---|---|
| 1 | 全限定名引用（`ui.settings.WearAppearance`）没被改写 |
| 2 | 同包隐式引用断裂（`SettingsRowStagger.kt` 里的 `SettingsRowExitTotalMs` 等） |
| 3 | 只按 `val/fun` 提取声明，漏了 `enum class`（`AccentPreset` / `FontScaleTier`） |
| 4 | KDoc 里的 `[AppearanceBootstrap]` 也要 import |
| 5 | 自动插入的 import 与原 import 重复 → `Conflicting import` |
| 6 | 去重脚本过度删除，连 `androidx.compose.ui.unit.dp` 都被删掉 |

**结论（写死）**：这种"批量移动 + 脚本改 import"的做法在这个仓库里**不可行** ——
Kotlin 的同包隐式引用、全限定名、KDoc 引用、重复 import 四种情况必须一起处理，脚本很难一次做对。

**改为不移动**：这 9 组共享符号**留在 `ui/settings/` 不动**（它们是基础设施，不是"旧设置页"）；
删除时只删**页面渲染文件**（`SettingsScreen.kt`、`SettingsBalloonGroup.kt`、`SettingsStyleCards.kt`、
`SettingsShared.kt` 等）与其测试，并用 IDE 逐个删除、每次删完立刻编译。


## 8. V2 信息架构（与 SettingsV2Data.zoneTitles 同源）

| 顺序 | 分区 | 内容 |
|---|---|---|
| 1 | 连得上 | 供应商 / 模型 / 温度 |
| 2 | 让它能做事 | 工具与 MCP / 安全策略 / 特权执行 |
| 3 | 让它听懂我 | 语音与唤醒 / 自动播报 / 唤醒词 |
| 4 | 让它答得好 | 页面风格 / 紧凑模式 / 网页控制台 |

> 守卫：`SettingsV2IaGuardTest` 断言这四个名字与顺序，并校验它们同时出现在本文件与 `docs/CONTEXT.md`。

---

## 9. 旧实现剩余文件清单（0.0.0.170 实测，按"是否被 ui/settings 之外的代码引用"分类）

### 9.1 真·基础设施（外部有引用，**保留**）

| 外部引用 | 内部引用 | 大小 | 文件 |
|---|---|---|---|
| 42 | 6 | 5KB | `AdvancedPermissionSection.kt` |
| 18 | 5 | 2KB | `SettingsHaptics.kt` |
| 17 | 28 | 97KB | `SettingsShared.kt` |
| 14 | 24 | 6KB | `SettingsCardEdgeZoom.kt` |
| 9 | 2 | 5KB | `ModelCatalogFetcher.kt` |
| 7 | 0 | 1KB | `SettingsMotion.kt` |
| 4 | 5 | 5KB | `SettingsRowText.kt` |
| 3 | 9 | 7KB | `AppearanceSettings.kt` |
| 2 | 7 | 26KB | `SettingsUiMath.kt` |
| 1 | 2 | 2KB | `SettingsRowStaggerMath.kt` |
| 1 | 1 | 1KB | `HapticAmplitude.kt` |
| 1 | 0 | 4KB | `VoiceEntryPolicy.kt` |

### 9.2 死代码（外部 0 引用，**待清理**，共 19 个 / 约 160KB）

`NetworkDiagnosticsSection` `SettingsBalloonGroup` `WebOnlyRow` `SettingsHeroRing` `SettingsCardTypography`
`ModelCatalog` `MemoryAndSecuritySections` `BalloonSegment` `SettingsCoverage` `SettingsSwitchRow`
`SettingsSliderRow` `SettingsRadioRow` `SettingsScaling` `SettingsRowStagger` `SettingsOfficialControls`
`SettingsCurve` `SettingsTextLayout` `SettingsStyleCards` `SettingsStylePolicy`

### 9.3 为什么不立即删

它们**只被本目录内的文件互相引用**，而本目录里"还在用的"文件（如 `SettingsShared`）可能引用它们。
逐个删要重新跑一遍"删除→编译→删孤立文案→删过期守卫"的链路（本目录已走 4 轮，删掉 24 个文件、
约 900 条文案、13 个守卫）。这属于**纯死代码清理**，不改变任何用户可见行为，
因此按"V2 验收优先"处理：先打 tag 收口，清理单开任务。
