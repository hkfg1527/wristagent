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

不靠感觉调参，全部钉成令牌与测试：

- **颜色**：15 个语义令牌，正文灰对纯黑 **5.76:1**（WCAG AA 4.5:1），`PaletteAuditTest` 守门数量/重复度/对比度/死 token
- **字阶**：`12 / 15 / 18 / 24 / 30 sp`，相邻差 ≥20%（官方默认阶梯只有 12.5%，读起来糊成一片），`WearTypographyAuthorityTest` 守门
- **动效**：五档 `micro / quick / base / page / Stagger`，全仓不再出现临时手调的弹簧参数
- **圆屏几何**：内容宽度按弦长公式推导，行步进 49–53dp；单左轴对齐已被真机否掉，改回同心

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

**766 条单测**，其中相当一部分是“结构守卫”——不测行为，而是把设计决策钉死：
调色板不许膨胀、字阶相邻差必须 ≥20%、正文对比度必须过 AA、行几何必须落在网格上。

## 已知短板

- 18 个高级字段目前只能在网页控制台改（正在搬回手表端）
- 「边说边显示」「记住此命令」两个开关尚未接通
- 滚动 janky 偏高（66–70%），瓶颈在 UI 线程的组合与测量

## 路线图

- **0.0.x**：功能收敛、界面打磨、开源可发布
- **1.0**：高度可自定义 + AI 能力补全 + 上架
- **2.0**：自研 UI 组件库 + 架构性能重构

## 许可

见 [LICENSE](LICENSE)。
