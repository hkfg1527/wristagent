# Wrist Agent

<p align="center">
  <b>戴在手腕上的 AI Agent —— 不只是聊天框，它能在设备上真的做事。</b>
</p>

---

## 这是什么

Wrist Agent 是一个运行在 Wear OS 上的本地 AI 代理。它把一个能调用工具的模型放进 233dp 的圆屏里，
并把“执行”这件事做完整：**模型决定 → 工具调用 → 风险审批 → 设备执行 → 结果回填**，全链路在手表上闭环。

它能做的事：读电量与温控、查天气、调点阵密度、开省电模式、发振动、执行受控的 shell 命令，
甚至给自己**造新工具**（命令模板 + 参数占位符）。

## 界面

| 屏 | 设计意图 |
|---|---|
| **聊天** | 弧形时钟压顶，答案是一层实心面（不是气泡墙），底部一颗纯白主键。点阵背景只在思考/录音时增强，静息时是纹理不是噪声 |
| **设置** | 同心构图，内容落在屏幕圆心轴上。9 个分组、57 个字段，行内直接标注「仅网页端可改」 |
| **审批** | 全屏接管，滑块左滑放行 / 点击拒绝，风险分三档（安全 / 中度 / 危险），危险等级会改变滑块配色 |
| **引导** | 粒子开场 → 白圆欢迎 → 配置表单 |

## 设计系统

不靠感觉调参，全部钉成令牌与测试。核心思路是**让背景点阵成为布局骨架**，而不是一张壁纸——
按下主键时，被按的那一格放大到 1.35 倍、周围格点沿径向被弹性顶开，松手弹回。

- **点阵**：格距 16dp、圆心为锚点、圆形盘裁剪。它是唯一度量单位，任何新尺寸都要能写成格距的简单倍数。
  规范与六条禁忌见 [`docs/UI-DOTMATRIX.md`](docs/UI-DOTMATRIX.md)
- **颜色**：15 个语义令牌。正文灰对纯黑约 **9.3:1**、辅助灰约 **5.8:1**，都过 WCAG AA（4.5:1）；
  `PaletteAuditTest` 守门数量/重复度/对比度/死 token。
  强调色只当重音用：设置页全目录只剩 2 处（滑轨已选段、单选选中态），语义色只染 6dp 状态点
- **字阶**：`12 / 15 / 18 / 24 / 30 sp`，相邻差 ≥20%（官方默认阶梯只有 12.5%，读起来糊成一片），`WearTypographyAuthorityTest` 守门
- **圆角**：`12dp` 控件 / `16dp` 组卡 / `24dp` 独立面板 / `999dp` 胶囊
- **动效**：五档 `micro / quick / base / page / Stagger`，全仓不再出现临时手调的弹簧参数
- **圆屏几何**：按弦长公式推导。贴在圆屏最顶部的标题可用宽只有约 107dp——`High-risk action` 这种英文
  会被圆周切边，所以审批标题用的是 `High risk`。行步进 49dp（组内无间隙，靠点线分隔）

## 架构

```
wear/
├── agent/            模型调度 · 工具注册 · 审批与安全策略
│   ├── remote/       嵌入式 HTTP 控制台（前台服务常驻）
│   ├── privilege/    Root / Shizuku 三种 shell 实现
│   └── session/      会话持久化（扁平文本 + Base64，JVM 可测）
├── presentation/     ViewModel · UI 状态 · 错误文案映射
└── ui/               Compose 界面 · 设计令牌 · 动效令牌
```

## 构建

```bash
# 需要 JDK 17、Android SDK 37
export GRADLE_USER_HOME=/path/to/gradle-home
./gradlew :wear:assembleRelease --offline
```

产物：`wear/build/outputs/apk/release/wear-release.apk`（约 3.4 MB，R8 已开）

### 兼容构建

```bash
./gradlew :wear:assembleRelease -PcompatMinSdk=30 --offline
```
把 `com.google.android.wearable` 的 required 降为可选，可装到更低版本的手表。

## 测试

```bash
./gradlew :wear:testDebugUnitTest --offline
```

**1000 余条单测**，其中相当一部分是「结构守卫」——不测行为，而是把设计决策钉死：
调色板不许膨胀、字阶相邻差必须 ≥20%、正文对比度必须过 AA、行几何必须落在网格上、
点阵在振幅为 0 时必须与改造前逐像素相同、组内分隔线的点数不许骑到右端余量上。

## 已知短板

诚实清单，写在这里而不是等你踩到：

- **配套的派生色不跟随强调色**：主强调色已全站走运行时投影（选绿就整套变绿），
  但 `AccentDim` / `AccentTrack` 这两枚配套令牌没有运行时投影——用到它们的少数元素
  （例如用户消息气泡的底色）仍停在默认蓝。要不要派生、按什么比例派生属产品决定
- **主键边缘高光固定在令牌蓝**：`OrbEdgeHighlight.toneOf` 是零 Compose 依赖的纯函数，
  被既有测试钉在 `Wear.Palette.Accent` 上；要让它跟随换色得同时改签名与那个测试
- **长期记忆的写入工具只有源码级守卫**：`remember_fact` 的「注册表分派 → 落盘」这一跳在 JVM 单测里
  执行不了——它必经 `org.json`，而单测里那是 AGP 的桩（`unitTests.isReturnDefaultValues = true`）。
  规则层、存储层、注入层都有真测试，只有这一跳是读源码断言接线
- **帧耗时 p50 18ms**，只比 16.7ms 预算高 1.3ms，所以 janky 计数偏高；GPU p90 只有 9ms，
  真正的杠杆是减少每帧绘制指令数（把静止时的点阵烘成缓存位图属 2.0）
- **192dp 窄屏**上设置根页不显示取值（`settingsRowShowsValue` 的既有规则），端口状态只在子页可见
- **MCP 只实现 Streamable-HTTP 形态**；旧的「GET 长连 SSE + POST message endpoint」分离式传输未实现，
  遇到会降级成 timeout/connect 回执

## 路线图

- **0.0.x** ✅：功能收敛、界面打磨、开源可发布
- **1.0** ✅（当前）：高度可自定义 + AI 能力补全——MCP 客户端、长期记忆写入、手势绑定、
  字号/强调色可换、配置导出导入、网页控制台安全加固
- **2.0**：自研 UI 组件库 + 架构性能重构（点阵烘焙缓存、统一动效层）

## 许可

见 [LICENSE](LICENSE)。
