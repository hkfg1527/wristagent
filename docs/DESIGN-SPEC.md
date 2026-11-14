# 动效与触觉规范（M3 Expressive，一手资料版）

> 目的：不再"凭感觉堆样式"。这份文件只写**能落到代码和测试里的规则**，每条都标注来源。
> 抓取方式：`node docs/tools/read-url.mjs <url> [chars]`（headless Edge + CDP，JS 站也能读）。

## 1. 一手来源

| 主题 | 来源 | 状态 |
|---|---|---|
| M3 Expressive 动效物理系统 | https://m3.material.io/blog/m3-expressive-motion-theming | ✅ 已读全文（2026-11-14） |
| Wear 质量分级 | `developer.android.com/design/ui/wear/guides/foundations/quality-tiers` | ❌ 本机网络不可达（连 appspot 镜像也超时） |
| Wear 字阶 token | `developer.android.com/design/ui/wear/guides/styles/typography/type-scale-tokens` | ❌ 同上（搜索能返回链接，正文取不到） |
| 触觉反馈语义 | 待补（下一步优先找 m3.material.io 或 Wear 文档的可达镜像） | ⬜ |

## 2. 官方语义（原文结论，不是我的解释）

1. **两套 motion scheme**：`Expressive`（推荐、会过冲回弹）与 `Standard`（缓入、更"功能性"）。
2. **两类 spec**：
   - **Spatial**：位置、朝向、尺寸、形状 —— 弹簧**会过冲**再落位；
   - **Effects**：颜色、透明度 —— **不得有过冲**。
3. **三档速度**：default / fast / slow。小元件（开关、按钮）用 fast，半屏动效用 default，全屏用 slow。
   同一 token 在 wearable / phone / tablet 上的**具体数值不同**（"在设备的语境里要显得快"）。

## 3. 我们自己依赖里的官方 API（已用 javap 核实，不是猜）

`androidx.wear.compose:compose-material3:1.7.1`（本工程实际依赖）里已经有：

- `interface MotionScheme`：`defaultSpatialSpec / fastSpatialSpec / slowSpatialSpec`、
  `defaultEffectsSpec / fastEffectsSpec / slowEffectsSpec`
- `MotionScheme.Companion.expressive` 与 `.standard` 两套预设
- `LocalMotionScheme`（CompositionLocal）
- 公开常量：`StandardDefaultStiffness`、`ExpressiveDefaultDamping`、`EffectsDefaultStiffness` 等

**结论：官方动效体系不用升级依赖就能用，只是我们一直没接**（现在用的是自建 `Wear.Motion` 弹簧）。

## 4. 与本产品硬约束的冲突与裁决

| 冲突 | 裁决 |
|---|---|
| Spatial 弹簧**会过冲**，而过冲意味着某一帧**放大**（scale > 1） | 与"只允许缩小、禁止横向形变"冲突 → **边缘缩放只能用不过冲的 spec**：`MotionScheme.standard` 的 spatial，或自建 `spring(dampingRatio = 1f)`。`expressive` 的 spatial 只能用在**不改变尺寸**的位移/淡入上 |
| Effects 不过冲 | 与透明度/颜色相关的动效可以直接吃官方 effects spec |
| 三档速度与元件大小绑定 | 列表边缘缩放（大块）走 default；开关/按键的按下态走 fast；整页切换走 slow |

**一句话规则：凡是改变 scale 的动画都必须 critically damped（`dampingRatio = 1f`）；颜色与透明度走 effects spec。**

## 5. 落到本工程的映射

| 界面 | 应使用的 spec | 现状 |
|---|---|---|
| 列表边缘缩放（`CardEdgeZoom`） | standard spatial（不过冲） | ✅ 用的是 cubic-bezier 缓动 + 线性映射，等价"不过冲"，但**不是官方 token** |
| 卡片按下态 `pressScale` 0.985 | fast spatial | ⚠️ 自建 `Wear.Motion.micro()` |
| 行入场位移 24dp→0 | default spatial | ⚠️ 自建 tween，且**用户报的"卡片重合"疑点就在这里**（位移期间两张卡视觉叠压） |
| 行入场淡入 | default effects | ⚠️ 同上，与位移绑在一个 tween 里 |
| 子页/风格切换 | slow spatial（不改变尺寸） | ⚠️ 自建 `Wear.Motion.page` |
| 触觉 | 官方触觉语义（待补来源） | ⚠️ 只有 `vibrateTick/vibrateConfirm` 两种 |

## 6. 可以直接写成测试的规则

1. 任何改变 `scaleX/scaleY` 的动画，spring 的 `dampingRatio` 必须 == 1f（不许过冲）。
2. `scaleX == scaleY` 恒成立（禁止横向形变）。
3. 缩放上限 1.0（只缩不放）。
4. 颜色/透明度动画只能用 effects 类 spec，位移/尺寸只能用 spatial 类。
5. 新增动效必须从**单一 motion 源**取 spec（不许再散落 duration/easing 字面量）。

## 7. 待办（按优先级）

1. **补触觉来源**：找 Google 官方 haptics 语义（Wear 优先），写成"事件 → 触觉类型"的表。
2. 把 `Wear.Motion` 收敛到 `MotionScheme`（先做映射表，再逐处替换，每处配真机验收）。
3. 用标准 spec 换掉行入场的 tween —— 顺带验证"卡片重合"是不是位移造成的（可先做一版**纯淡入无位移**的对照）。
