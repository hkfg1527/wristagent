# 手表设置信息架构规范（SETTINGS-IA）

> **命名对照（2026-11-15 统一）**：界面上这个入口叫「**网页控制台**」，代码枚举是 `SettingsSubDialog.REMOTE_CONSOLE`，
> 文档与提交信息统一用界面名。此前文档写「远程控制台」、界面写「网页控制台」，找入口时白花了 15 分钟。

> 适用对象：Wrist Agent，Wear OS，包名 dev.ghr.wristagent。
> 本文只描述"信息架构 + 落位 + 迁移影响"，不含实现代码；实现顺序见第 6 节。
> 文中每条结构断言都可在仓库源文件里复核，出处写在句末括号内。

## 0. 事实基线（已核实，非推测）

| 事实 | 取值 | 出处 |
| --- | --- | --- |
| 偏好属性总数 | 58 个可持久化属性 | `agent/AgentPreferences.kt` L19–L375 |
| `KEY_*` / `DEFAULT_*` 常量 | 56 条（含 2 条内部键） | `AgentPreferences.kt` L454–L531 |
| 可移植快照键 | 恰好 40 条，被测试钉死 | `SettingsTransferCodec.PortableKeys`；`SettingsTransferCodecTest` 断言 `40` |
| 关闭页风格 4 套 | BALLOON / SQUARE / GOOGLE_CARD / TERMINAL | `KEY_SETTINGS_STYLE="settings_style"`，`appearance/SettingsStyle.kt` |
| 二级页总数 | 17 = 11 个一级 `SettingsSubDialog` + 6 个内层页 | `settings/SettingsShared.kt` L227–L249 + `SettingsScreen.kt` L722–L886 |
| 根页组标题 | 恰好 4 个，顺序被测试钉死 | `SettingsScreenStructureGuardTest.expectedSections` |
| 行步进 | 49dp（行高下限 48 + 发丝线 1 + 卡内行距 0） | `SettingsUiMath.SettingsSpacingMath.RowStepDp` |
| 组间留白 | 24dp；卡内行距 0 | `SectionGapDp=24f`、`CardRowGapDp=0f` |
| 列表项间距 | 4dp（4dp 网格） | `ItemGapDp=4f`、`GridDp` |
| 网页覆盖 | 10 页 / 63 行 / 55 带键行 | `SettingsCoverage.entries` + `ConsoleSettingsParityGuardTest` |
| 双语 | 414 条 / 414 条 | `values/strings_settings.xml`、`values-zh/strings_settings.xml` |

### 0.1 为什么顶层最多 5 个分区（并且本文只取 4）

按 `233dp` 屏、行步进 `49dp`、组间 `24dp`、组标题占一行高算：

- 一个分区的固定开销 = 组标题行(≈48dp) + 组间留白(24dp) = **72dp**；
- 4 个分区 = 288dp 固定开销，5 个 = 360dp，6 个 = 432dp；
- 一屏可见内容 ≈ 233dp − 顶部 24dp 遮罩 − 底部 44dp 内缩 = **165dp ≈ 3.4 行**。

结论：**分区数每 +1，首屏就少 1.4 个可读行**。5 个分区在 233dp 屏上意味着"第 5 组标题必然落在第 3 屏之外"，分组带来的检索收益被滚动成本吃掉；6 个及以上则首屏只能看见分区标题、看不见任何内容行。

因此本文取 **4 个分区**（也正好与 `SettingsScreenStructureGuardTest` 的 4 组标题断言一致，迁移时唯一需要改的是组标题**取值**，不是数量）。第 5 个分区被明确否决，理由即上述算术，不是审美偏好。

### 0.2 两条不可协商的屏面硬约束（影响所有落位）

1. **不许横向拉伸/形变**：卡片宽度固定（内容基准 176dp，外壳上限 216dp），任何新增行只能纵向增长或收一行，不允许靠"变宽"塞下更多文案（`SettingsShared.kt` L319、`SettingsScaling`）。
2. **动画只允许缩小**：入场/切换动效只允许 `scale ∈ (0,1]`，不允许放大越过 1.0，也不允许位移拉伸（`settings/SettingsMotion.kt`、`SettingsRowStaggerMath`）。
3. **大卡片档（GOOGLE_CARD）每张卡 8dp 间距、不得粘连**：当前 `CardRowGapDp=0f` 是给气球/方角档"组内拼一整张卡 + 发丝线"用的；大卡片档每行是独立 M3E Card，必须取 `CardStyleCardGapDp = 8f`（8 是 4dp 网格整数倍，满足 `isOnGrid`）。**这条是本规范新增的常量，实施时必须落在 `SettingsSpacingMath` 里，而不是散落的 `.padding(8.dp)`。**

---

## 1. 顶层结构

### 1.1 分区命名原则

分区名用**用户意图**（"我要它干什么"），不用代码模块名（"voice / tools / network"）。四个分区各对应一句用户心里的话：

| 分区 | 用户心里那句话 | 判据 |
| --- | --- | --- |
| **S1 让它答得好** | "回答质量与模型行为" | 改完影响**下一次回答内容** |
| **S2 让它听懂我** | "输入方式与手感" | 改完影响**我怎么操作它** |
| **S3 让它能做事** | "能力边界与放权" | 改完影响**它能碰什么、要不要问我** |
| **S4 连得上** | "连接、诊断、搬运、版本" | 改完影响**连不连得上/能不能迁移** |

一条设置项只能进一个分区，判定顺序固定：**S1 内容 → S2 输入 → S3 权限 → S4 连接**，先命中先落位，禁止"两边都放"。

### 1.2 根页构成（4 hero + 4 分区 + 14 行）

根页元素顺序（自上而下，即表冠向下滚动的顺序）：

```
[H1] 模型 hero        ← 品牌身份 + 当前模型名 + 配置状态徽标("TXT/MOD/SYS/APP")
[H2] 语音 hero        ← ASR 开关真实状态(关/系统/百度) + 缺密钥告警
[H3] 工具 hero        ← 已启用工具数 / 待确认数
[H4] 系统 hero        ← 控制台端口 或 体检结论
─────────────────────────────────────────
S1 让它答得好
   · 模型与端点          (值: 当前模型名)
   · 供应商              (值: 当前档案名 / 未启用)
   · 生成参数            (值: 温度)
   · 提示词与记忆   ▸    (中转页, 值: 默认/自定义; 副值: 上下文轮数)
S2 让它听懂我
   · 语音与唤醒          (值: 语音引擎状态)
   · 手势与侧键          (值: 已绑定动作数)
   · 外观与动效          (值: 风格名)
S3 让它能做事
   · 工具与 MCP          (值: 已启用数 / 全开)
   · 安全策略            (值: 严格/标准/自动)
   · 权限                (值: root / shizuku 后端)
S4 连得上
   · 网络与请求          (值: 超时秒数)
   · 网页控制台          (值: 端口 / 未运行)
   · 导入导出            (值: —)
   · 关于                (值: 体检结论)
```

**根页行数 14**，分布在 4 个分区；加上 4 张 hero，首屏（165dp）恰好容纳 1 张 hero + 1 个组标题 + 2 行，这是"滚动 1 次即可覆盖一个分区"的临界值。超过 16 行即视为回归（见 4.4 的行数预算规则）。

### 1.3 四个 hero 的职责（这是"常用项不许被埋"的执行机构）

hero 不是装饰，是**高频项的直通车道**：每张 hero 同时承担"状态显示"与"1 次点击直达"两个职责，点击目标固定如下：

| hero | 显示 | 点击直达 | 为什么它值一张 hero |
| --- | --- | --- | --- |
| H1 模型 | `modelName`、是否已填 Key | 模型与端点 | 每次换模型/换端点都从这里进；不填 Key 时是首屏唯一红灯 |
| H2 语音 | `asrEnabled` 的真实三态 | 语音与唤醒 | 语音是最常用输入；密钥缺失必须首屏可见 |
| H3 工具 | 已启用工具数 | 工具与 MCP | 工具开关是"要它做新事情"时的第一站 |
| H4 系统 | 控制台端口 / 体检结论 | 网络与请求 | 连不上时第一个要看的地方 |

hero 只允许**只读状态 + 跳转**，禁止在 hero 上直接放开关：开关需要二次确认与失败回滚面，放在 hero 上会把"看一眼"变成"误触改配置"。

---

## 2. 完整地图（分区 → 二级页 → 设置项）

### 2.1 阅读约定

- **键名列**给两样东西：`KEY_*` 常量名（源码里真实存在）与存储键字符串（`AgentPreferences` 里真实写入 prefs 的名字）。
- 「形态」列取值：开关 / 滑杆 / 枚举 / 文本 / 动作 / 只读。
- 「现值」列写当前默认值来源常量。
- 未标 `KEY_*` 的项是**运行时状态**（不是设置），它们出现在页面上但不可拨，属于"看得见但不该调"的一类，必须在页面上以只读行呈现（`WatchSettingForm.READ_ONLY` 已有约束：只读项不给 `preferenceKey`）。

### 2.2 S1 · 让它答得好

| 二级页 | 设置项（KEY_* / 存储键） | 形态 | 现值 |
| --- | --- | --- | --- |
| **模型与端点** `MODEL_CONFIG` | `baseUrl` / `"baseUrl"` | 文本 | 默认常量 |
| | `apiKey` / `"apiKey"` | 文本(密钥) | 空 |
| | `modelName` / `"modelName"` | 枚举(远端清单) | 默认常量 |
| | `cachedRemoteModels` | 只读(清单缓存) | — |
| | `modelCatalogFromRemote` / `"modelCatalogFromRemote"` | 开关 | false |
| | `maxContextTurns` / `"maxContextTurns"` | 滑杆 4..200 | 默认常量 |
| **供应商** `PROVIDER_PROFILES` | `providerProfiles` / `"provider_profiles"` | 动作(增删改/套用) | 空 |
| | 当前档案的 baseUrl/apiKey/modelName（复用上页三件套） | 文本 | — |
| **生成参数** `HYPER_PARAMS` | `temperature` / `"temperature"` | 滑杆 0..2 | 默认常量 |
| | `maxOutputTokens` / `"maxOutputTokens"` | 滑杆 | `DEFAULT_MAX_OUTPUT_TOKENS=2048` |
| | `streamMode` / `"streamMode"` | 开关 | true |
| | `reasoningEffort` / `"reasoningEffort"` | 枚举 | `ReasoningEffort.DEFAULT_STORAGE` |
| | `defaultThinkingEnabled` / `"defaultThinking"` | 开关 | `DEFAULT_DEFAULT_THINKING=true` |
| | `showThoughtChain` / `"showThoughtChain"` | 开关 | 默认常量 |
| | `replyStyle` / `"replyStyle"` | 枚举 | `ReplyStyle` 默认 |
| **提示词与记忆** `PROMPT_CONTEXT`（中转页，2 内层） | 值行：`customSystemPrompt` / `"customSystemPrompt"` | 只读摘要 | 默认/自定义 |
| | 副值行：`maxContextTurns` | 只读摘要 | — |
| ↳ **提示词** `PromptViewerContent` | `customSystemPrompt` | 文本 | 空(用默认) |
| ↳ **记忆与上下文** `MemoryManagerContent` | `memoryEnabled` / `"memoryEnabled"` | 开关 | `DEFAULT_MEMORY_ENABLED=true` |
| | `maxContextTurns` / `"maxContextTurns"` | 滑杆 4..200 | 默认常量 |
| | `historyCompressThreshold` / `"historyCompressThreshold"` | 滑杆 | `CompressPolicy.DefaultThreshold` |
| | `autoCompressContext` / `"autoCompressContext"` | 开关 | `DEFAULT_AUTO_COMPRESS_CONTEXT=true` |
| | 清空记忆 | 动作 | — |
| | 备注条数 / 事实数 | 只读 | — |

### 2.3 S2 · 让它听懂我

| 二级页 | 设置项（KEY_* / 存储键） | 形态 | 现值 |
| --- | --- | --- | --- |
| **语音与唤醒** `VOICE_ENTRY` | `asrEnabled` / `"asr_enabled"` | 开关 | `DEFAULT_ASR_ENABLED=false` |
| | `autoSpeakReplies` / `"autoSpeakReplies"` | 开关 | `DEFAULT_AUTO_SPEAK_REPLIES=false` |
| | 电源档位（只读回显） | 只读 | — |
| ↳ **语音引擎** `VoiceAsrContent`（原 `showVoiceAsr` 全局浮层，本轮收为此页内层） | `asrProvider` / `"asr_provider"` | 枚举 | `DEFAULT_ASR_PROVIDER="SYSTEM"` |
| | `baiduApiKey` / `"asr_baidu_api_key"` | 文本(密钥) | `DEFAULT_ASR_KEY=""` |
| | `baiduSecretKey` / `"asr_baidu_secret_key"` | 文本(密钥) | `DEFAULT_ASR_KEY=""` |
| | `asrDevPid` / `"asr_dev_pid"` | 枚举/数字 | `1537` |
| | `asrLiveTranscript` / `"asrLiveTranscript"` | 开关 | `DEFAULT_ASR_LIVE_TRANSCRIPT=true` |
| **手势与侧键** `GESTURES`（本轮从「关于」中转页提升为一级） | `longPressAction` / `"longPressAction"` | 枚举 | `GestureAction.NONE` |
| | `doubleTapAction` / `"doubleTapAction"` | 枚举 | `GestureAction.NONE` |
| | `sideKeyShortAction` / `"sideKeyShortAction"` | 枚举 | `GestureAction.NONE` |
| | `sideKeyLongAction` / `"sideKeyLongAction"` | 枚举 | `GestureAction.NONE` |
| **外观与动效** `APPEARANCE` | `settingsStyle` / `"settings_style"` | 枚举(4 风格) | `"BALLOON"` |
| | `matrixSpacingDp` / `"matrixSpacingDp"` | 滑杆 4..32 | 默认常量 |
| | `compactMode` / `"compactMode"` | 开关 | `DEFAULT_COMPACT_MODE=false` |
| | `animationsEnabled` / `"animationsEnabled"` | 开关 | `DEFAULT_ANIMATIONS_ENABLED=true` |
| | `hapticsEnabled` / `"hapticsEnabled"` | 开关 | `DEFAULT_HAPTICS=true` |
| | `font_scale`（`AppearanceSettings.KEY_FONT_SCALE`） | 枚举档 | `FontScaleTier.STANDARD` |
| | `accent_argb`（`KEY_ACCENT`） | 枚举色 | 无强调色 |

> 注：`ultraSaving`、`hasCompletedOnboarding` 不是用户设置，不进设置页（前者是系统省电联动状态，后者是首次引导状态）；`hasCompletedOnboarding` 的唯一用户可见面是「关于 → 重看引导」。

### 2.4 S3 · 让它能做事

| 二级页 | 设置项（KEY_* / 存储键） | 形态 | 现值 |
| --- | --- | --- | --- |
| **工具与 MCP** `TOOLS_MCP` | `toolDeviceStatusEnabled` / `"toolDeviceStatusEnabled"` | 开关 | 默认常量 |
| | `toolVibrateEnabled` | 开关 | 默认常量 |
| | `toolMatrixDensityEnabled` | 开关 | 默认常量 |
| | `toolPowerModeEnabled` | 开关 | 默认常量 |
| | `toolWeatherEnabled` | 开关 | 默认常量 |
| | `toolWebSearchEnabled` | 开关 | 默认常量 |
| | `providerManagedSearchEnabled` / `"providerManagedSearchEnabled"` | 开关 | `false` |
| | `subAgentEnabled` / `"subAgentEnabled"` | 开关 | `false` |
| | `isMcpEnabled` / `"isMcpEnabled"` | 开关 | 默认常量 |
| | `mcpEndpoint` / `"mcpEndpoint"` | 只读(网页可改) | 默认常量 |
| | `selfDefinedTools` / `"self_defined_tools"` | 只读/动作 | 空 |
| | `rememberedShellCommands` / `"remembered_shell_commands"` | 只读 + 清空动作 | 空 |
| **安全策略** `SECURITY_GUARDRAIL` | `securityMode` / `"securityMode"` | 枚举(严格/标准/自动) | `SecurityMode.STANDARD` |
| | `toolApprovalPolicy` / `"toolApprovalPolicy"` | 枚举 | `ToolApprovalPolicy.RISKY` |
| | `sandboxLevel` / `"sandboxLevel"` | 枚举 | `SandboxLevel.FULL` |
| **权限** `PRIVILEGE`（本轮从「关于」中转页提升为一级） | root 状态、shizuku 状态、shizuku 授权 | 只读 + 动作 | 无持久化键（读系统） |

> 结构与"假开关"防线一致：**权限页整页没有 `preferenceKey`**，因为它的值来自系统而不来自偏好（`WatchSettingCatalog` 的头注释已把这条写成结构性约束）。

### 2.5 S4 · 连得上

| 二级页 | 设置项（KEY_* / 存储键） | 形态 | 现值 |
| --- | --- | --- | --- |
| **网络与请求** `NETWORK_REQUEST`（新页：承接原先散在根页的 5 行内联） | `requestTimeoutSeconds` / `"requestTimeoutSeconds"` | 滑杆 | `DEFAULT_REQUEST_TIMEOUT=40` |
| | `requestRetries` / `"requestRetries"` | 滑杆 | `DEFAULT_REQUEST_RETRIES=1` |
| | `retryBackoffMs` / `"retryBackoffMs"` | 滑杆 | `DEFAULT_RETRY_BACKOFF=800` |
| | `wifiOnlyRequests` / `"wifiOnlyRequests"` | 开关 | `DEFAULT_WIFI_ONLY=false` |
| | 网络体检入口（行） | 导航 | — |
| ↳ **网络体检** `NETWORK_DIAG`（原「关于」内层，本轮挂到本页下） | 结论/问题数/耗时 | 只读 | `NetworkDiagnosisStore` |
| | 重新体检 | 动作 | — |
| **网页控制台** `REMOTE_CONSOLE` | 运行状态、端口 | 只读 | `ConsolePortStatus` |
| | `console_token`（`ConsoleBridge.KEY_TOKEN`） | 文本(密钥) | 生成 |
| **导入导出** `TRANSFER` | 导出的 40 个可移植键（整份快照） | 文本(复制/粘贴) | `PortableKeys` |
| **关于** `ABOUT` | 版本、重看引导、体检结论摘要 | 只读 + 动作 | `hasCompletedOnboarding` |

### 2.6 归属自检（40+ 项一个不漏）

按 `AgentPreferences.kt` L19–L375 的属性清单逐条落位后的闭环检查（数字均为实数，可复核）：

- **属性表 58 条 = 51 条用户设置 + 7 条非设置**。
- **7 条非设置**分两类，都不进设置页：
  - 派生存量（3）：`rememberedShellCommandCount`、`rememberedShellCommandsFull`、`cachedRemoteModels`（前者由 `rememberedShellCommands` 解析得来，后者是远端清单缓存）；
  - 运行时状态/标志（4）：`calledToolNames`、`registeredToolCount`、`ultraSaving`（系统省电联动）、`hasCompletedOnboarding`（首次引导，唯一用户可见面是「关于 → 重看引导」）。
- **另有 4 条设置在属性表之外**，但必须计入设置面：`providerProfiles`（分组键 `"provider_profiles"`，由方法而非属性读写）、`font_scale`、`accent_argb`（`AppearanceSettings` 的 `KEY_FONT_SCALE`/`KEY_ACCENT`）、`console_token`（`ConsoleBridge.KEY_TOKEN`）。
- **设置面合计 = 51 + 4 = 55 条**，落位分布与每页条数一一对应：

| 分区 | 设置条数 | 构成 | 其中可移植键 |
| --- | --- | --- | --- |
| S1 让它答得好 | **17** | 模型与端点 5 + 供应商 1 + 生成参数 7 + 提示词 1 + 记忆与上下文 3（`maxContextTurns` 只在模型页计一次） | 11 |
| S2 让它听懂我 | **18** | 语音与唤醒 2 + 语音引擎 5 + 手势与侧键 4 + 外观与动效 7（含 `font_scale`/`accent_argb`） | 14 |
| S3 让它能做事 | **15** | 工具与 MCP 12 + 安全策略 3 + 权限 0（权限页无持久化键） | 11 |
| S4 连得上 | **5** | 网络与请求 4 + 网页控制台 1（`console_token`）+ 导入导出 0（快照非设置）+ 关于 0 | 4 |
| **合计** | **55** | — | **40** |

- **40 个可移植键的分布校验**：S1 = 11、S2 = 14、S3 = 11、S4 = 4，合计 40，与 `SettingsTransferCodec.PortableKeys` 的 `40` 断言吻合。S4 的 4 条可移植键是 `requestTimeoutSeconds`、`requestRetries`、`retryBackoffMs`、`wifiOnlyRequests`——连接参数可移植，而 `console_token` 这类密钥不可移植（密钥永不进快照，见该文件 L28–L29 的注释）。

> 结论：58 条属性 = 51 可调 + 7 非设置；补上属性表外的 4 条，设置面 55 条全部有唯一归属，无悬空项、无两处重复计数。这张表就是"40+ 项全部有归属"的验收凭据。

---
## 3. 20 个新增设置项提案

### 3.0 反"20 个孤立入口"的落位规则（先立规矩，再列项）

1. **不为任何新项新开二级页**。20 项全部落进既有 17 页的**分组**里。
2. **每页最多 2 个新组，每组不少于 2 项**；单页新增超过 4 项时必须再分组（分组标题占用 8dp 上边距，不占行高）。
3. 组内按"影响面从大到小"排序，与既有行混排时新组一律置于该页**下半区**（不抢占首屏的既有高频行）。
4. 每项必须能写出**消费点文件名 + 常量名**；写不出的一律删除（本轮已按此淘汰 4 个候选：\`wakeOnScreen\`/\`wakeOnLockScreen\`/\`usePureBlack\`/\`themeMode\` 已在旧审计中被删，不再复活）。
5. 新项一律**只改默认常量的取值来源**，不新增分支逻辑：把 `const val DEFAULT_X` 改成 `AgentPreferences.x`，消费方签名不变。

### 3.1 六组提案（共 20 项）

**组 A · 流式与循环节奏**（落 S1 生成参数页下半区，5 项）
**组 B · 记忆预算**（落 S1 记忆与上下文页，1 项）
**组 C · 播报输出**（落 S2 语音与唤醒页，2 项）
**组 D · 手感与体感**（落 S2 外观与动效页，4 项）
**组 E · 审批与执行**（落 S3 工具与 MCP / 安全策略页，4 项）
**组 F · 连接与守护**（落 S4 网络与请求 / 网页控制台页，4 项）

| # | 键名（snake_case） | 类型 / 范围 | 默认值 | 被谁消费（真实路径） | 所属子页 | 频率 |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `stream_emit_interval_ms` | 滑杆 16..120 ms，步进 8 | 32 | `StreamingThrottle.DEFAULT_INTERVAL_MS`（=32L）被流式节流器读取，决定逐字渲染的合帧间隔；`ChatViewModel` 每帧调用 `shouldEmit` | 生成参数 | 高 |
| 2 | `stream_emit_chars` | 滑杆 4..40 字 | 12 | `StreamingThrottle.DEFAULT_CHARS_PER_EMIT_THRESHOLD`（=12），决定攒够几个字才提交一帧 | 生成参数 | 中 |
| 3 | `idle_spin_stop_after` | 滑杆 2..8 轮 | 5 | `AgentLoopPolicy.IDLE_SPIN_STOP_AFTER`（=5），空转重复工具调用到第几轮强制停 | 生成参数 | 低 |
| 4 | `energy_hint_after_turns` | 滑杆 10..100 轮 | 40 | `AgentLoopPolicy.ENERGY_HINT_AFTER_TURNS`（=40），超过该轮次开始提示电量与上下文成本 | 生成参数 | 低 |
| 5 | `narration_timeout_ms` | 滑杆 1000..10000 ms | 4000 | `AgentDispatcher.NARRATION_TIMEOUT_MS`（=4000L），危险操作前的风险叙述超时；超时即放弃叙述继续执行 | 生成参数 | 低 |
| 6 | `memory_max_fact_length` | 滑杆 40..240 字 | 120 | `AgentMemory.MAX_FACT_LENGTH`（=120），`remember_fact` 工具写入前截断长度 | 记忆与上下文 | 低 |
| 7 | `tts_speech_rate` | 滑杆 0.5..2.0，步进 0.1 | 1.0 | `ReplySpeaker.speak` → 合成前 `TextToSpeech.setSpeechRate(rate)`（当前 `speak` 只有 QUEUE_FLUSH，未设语速） | 语音与唤醒 | 中 |
| 8 | `tts_queue_mode` | 枚举：打断 / 排队 | 打断 | `ReplySpeaker.speak` 的队列参数：打断=`QUEUE_FLUSH`，排队=`QUEUE_ADD` | 语音与唤醒 | 中 |
| 9 | `rotary_scroll_gain` | 滑杆 0.5..2.5，步进 0.1 | 1.0 | 两处设置页表冠处理器 `SettingsScreen.kt:309` 与 `SettingsShared.kt:402`（所有子页共用），都是 `dispatchRawDelta(it.verticalScrollPixels)`，乘 gain 即可；聊天页 `ChatScreen.kt:546` 有同源的原始位移入口（自带亚像素累积，介入点在 `wantPx`），可选一并覆盖；注意 `ConversationList.kt:166` 走的是库自带 `rotaryScrollable`，**不经过** `dispatchRawDelta`，增益对它无效（需单独接线，本轮不做） | 外观与动效 | 高 |
| 10 | `haptic_amplitude` | 枚举：轻 / 标准 / 强 | 标准 | `SettingsShared.vibrateTick`（15L 刻度震动）与 `vibrateConfirm`（20L 确认震动），两处都已被 `hapticsEnabled` 挡住，本项只改幅度 | 外观与动效 | 中 |
| 11 | `bottom_bar_auto_hide_ms` | 滑杆 1000..8000 ms，步进 500 | 3000 | `BottomBarVisibility.AUTO_COLLAPSE_DELAY_MS`（=3000L），底栏自动收起延时 | 外观与动效 | 中 |
| 12 | `dot_effect_peak_gain` | 滑杆 0.5..1.5，步进 0.1 | 1.0 | `DotLattice.PEAK_DISPLACEMENT_GRID_STEPS`（=0.9f）与 `PEAK_RADIUS_GAIN`（=0.35f），决定点阵被按压时的峰值位移 | 外观与动效 | 低 |
| 13 | `approval_allow_threshold` | 滑杆 0.50..0.95，步进 0.05 | 0.85 | `ApprovalSliderState.ALLOW_THRESHOLD`（=0.85f），审批滑杆拖到多少算放行 | 安全策略 | 中 |
| 14 | `mcp_max_tool_pages` | 滑杆 1..10 页 | 5 | `McpClient.MAX_TOOL_PAGES`（=5），MCP `tools/list` 翻页上限 | 工具与 MCP | 低 |
| 15 | `shell_command_timeout_ms` | 滑杆 1000..120000 ms，步进 1000 | 15000 | `ShellPolicy.DEFAULT_TIMEOUT_MS`（=15_000L），`run_shell` 工具执行上限；该文件已有 `MIN_TIMEOUT_MS=1_000` / `MAX_TIMEOUT_MS=120_000` 可直接夹紧 | 工具与 MCP | 中 |
| 16 | `subagent_max_depth` | 枚举：关闭 / 1 层 | 1 层 | `SubAgentPolicy.MAX_DEPTH`（=1），`depthAllowed(depth)` 的唯一判据 | 工具与 MCP | 低 |
| 17 | `subagent_read_timeout_ms` | 滑杆 15000..180000 ms | 60000 | `AgentDispatcher.SUB_AGENT_READ_TIMEOUT_MS`（=60000），子代理读取超时 | 工具与 MCP | 低 |
| 18 | `net_diag_timeout_ms` | 滑杆 1000..10000 ms，步进 500 | 3000 | `NetworkDiagnostics.TCP_TIMEOUT_MS`（=3_000）与 `STACK_TIMEOUT_MS`（=2_500），体检的 TCP/双栈探测超时 | 网络与请求 | 低 |
| 19 | `console_max_failures` | 滑杆 3..10 次 | 5 | `ConsoleAuthThrottle.MAX_FAILURES`（=5），控制台令牌连续错误上限 | 网页控制台 | 低 |
| 20 | `console_lockout_minutes` | 滑杆 1..60 分钟 | 1 | `ConsoleAuthThrottle.LOCK_DURATION_MS`（=60_000L），达到上限后的锁定窗口 | 网页控制台 | 低 |

### 3.2 三条必须同时满足的落位约束

1. **每项都必须进 `AgentPreferences` 的属性 + `KEY_*` + `DEFAULT_*` 三件套**，否则 `SettingsFieldRegistryGuardTest` 立即红（详见 5.3）。
2. **可移植性：本轮 20 项全部判为"仅本机"**，理由统一且可复核——它们全是**节奏/硬件/连接**参数（渲染合帧、TTS 语速、震动幅度、表冠增益、各类超时、控制台锁定），换一台设备后最优值由那颗 SoC、那只马达、那条网络决定，跨设备同步只会把 A 机的调优搬到 B 机上制造故障。因此 `PortableKeys` **保持 40 不变**，`SettingsTransferCodecTest` 的 `40` 断言本轮不动。
   - 逃生口：若产品后续要求跨设备同步调优值，新增第二张白名单 `PortableTuningKeys` 并同时修改 export/parse 与断言（`40 → 40+N`），不要把它塞进现有 `PortableKeys`——那会破坏"可移植=语义级偏好"的既有语义。
3. **不开放给网页控制台**：20 项中 0 项进 `ConsoleFields`。手表手感参数对远程客户端没有意义，且一旦进 `ConsoleFields` 就必须同步网页 PAGES 文档，触发 `ConsoleSettingsParityGuardTest` 的 10/63/55 三条计数重排（见 5.2）。

### 3.3 使用频率判据（供第 4 节与本文频率列共用）

一个设置项满足下列任意 **2 条**即为「常用」（必须 1 次点击可达），满足 0–1 条为「偶用」（可 2–3 次点击）：

- **F1 触发频率**：日活用户平均每日 ≥1 次调整，或每次改变使用场景都要动（换模型、换输入方式）。
- **F2 状态可见性**：值会自己变，用户需要"看一眼"确认当前处于哪一档（语音开没开、连没连上、工具开几个）。
- **F3 即时可感**：调完对**下一次交互**立即可感（语速、动效、表冠手感、审批阈值）。
- **F4 故障入口**：出问题时它是第一个要进的地方（网络/控制台/模型端点）。

本文 20 项中，判为「高」的只有 #1 `stream_emit_interval_ms` 与 #9 `rotary_scroll_gain`（都命中 F1+F3）；判为「中」的命中 F3 或 F4 之一；其余为「低」（调完很久才感受到差异，属"装一次就不再碰"）。

---

## 4. 导航与层级规则

### 4.1 层级

- **最大 3 层**：`根页 → 一级子页 → 内层子页`。第 4 层禁止（233dp 屏上第 4 层连面包屑都放不下）。
- 当前深 3 的只有三条：`提示词与记忆 → 提示词 / 记忆与上下文`、`语音与唤醒 → 语音引擎`、`网络与请求 → 网络体检`。
- 内层子页一律用 `overlay` 叠在父页上（`SettingsHubSections.kt` 的 `inner` 哨兵模式），**不允许**把内层页做成平级 `SettingsSubDialog` 值——平级化会让"返回"跳级。

### 4.2 命名规则

| 规则 | 说明 | 反例 |
| --- | --- | --- |
| 用名词短语，4–8 个汉字 | 一行放得下，不换行 | 「设置一些和模型有关的选项」 |
| 不用代码模块名 | 用户不认识 MODULE/MCP 之外的缩写 | 「VOICE_ENTRY」 |
| 并列关系用「与」 | 与既有 `settings_tools_and_mcp` 一致 | 「工具、MCP」 |
| 页面标题 = 根页行标题 | 进出不换词，避免"我点的是哪个" | 根页「外观」→ 页标题「界面」 |
| 技术专名保留原文 | MCP / API Key / TTS 不翻译 | 「模型上下文协议」 |
| 中英双语必须同批提交 | `values/` 与 `values-zh/` 各一条，缺一即视为未完成 | 只加中文 |

本文新增/改名的标题建议（键名均落 `strings_settings.xml`）：

| 用途 | 建议 key | EN | ZH |
| --- | --- | --- | --- |
| S1 组标题 | `settings_section_answers` | Answers & Model | 让它答得好 |
| S2 组标题 | `settings_section_voice` | Voice & Feel | 让它听懂我 |
| S3 组标题 | `settings_section_abilities` | Abilities & Safety | 让它能做事 |
| S4 组标题 | `settings_section_connect` | Network & System | 连得上 |
| 新页 | `settings_sub_network_request` | Network & Requests | 网络与请求 |
| 新页 | `settings_sub_voice_engine` | Voice Engine | 语音引擎 |
| 改名 | `settings_sub_gesture`（原「手势」） | Gestures & Side Key | 手势与侧键 |
| 改名 | `settings_sub_appearance`（原「外观与点阵」） | Look & Feel | 外观与动效 |

### 4.3 返回语义

- **唯一返回键**：子页底部的「完成返回」行（`settings_done_back`）。根页没有返回行（根页即退出点）。
- **右滑/系统返回必须与底部行等价**：内层页存在时先关内层，再退父页（`SettingsHubSections.kt` 的 `BackHandler` 已实现该语义，新页必须照抄）。
- **返回不丢编辑**：内层页返回父页时父页必须重新读取偏好（`syncFromPreferences()`），否则父页会把旧值写回（该坑在 `MemoryManagerContent` 的回调注释里已有记录）。
- **下拉返回**在根页等价于"退出设置"；在子页等价于"退一层"，不允许"一路退到底"。

### 4.4 表冠与滚动的预期

- 每格刻度 **1:1 吃掉位移**：`dispatchRawDelta(it.verticalScrollPixels * gain)`，返回值恒 `true`（全消费，不留给系统做吸附）。
- **惯性必须连续衰减**：显式 `flingBehavior = ScrollableDefaults.flingBehavior()`，不吃库默认（库默认可能改成吸附）。
- **越界不拉伸**：`overscrollEffect = null`、`rotaryScrollableBehavior = null`——这条同时是"不许横向拉伸/形变"在滚动轴上的落点。
- **每格滚动可视位移 ≈ 1/3 行**（16dp 上下），保证"转一格不跳行、转三格过一行"。
- 灵敏度由 #9 `rotary_scroll_gain` 统一放大，**禁止**在单个页面里再写第二套经验系数。

### 4.5 行数与"常用项不许被埋"的硬指标

- 根页 ≤ **16 行**（本文 14 行），每分区 ≤ **4 行**，超出即拆内层或改内联为页。
- 任一分区内**首行必须是该分区最高频的项**。
- 「常用」判据见 3.3；本文判为高的 2 项必须满足：根页 0 次点击可见（hero 上直接显示）**或** ≤2 次点击可改。
- 只读状态行不计入"行数预算"，但同一页只读行 ≤ 3 行（否则页面变成仪表盘）。

---

## 5. 迁移影响

### 5.1 现有 17 个子页的处置（17 → 17：净增 1、降级 1、提升 3、改名 3、层级统一 1）

| 现状页 | 现状挂载 | 处置 | 目标 | 理由 |
| --- | --- | --- | --- | --- |
| 模型与端点 | 一级 | **保留** | S1 第 1 行 | 最高频故障入口，位置不动 |
| 供应商 | 一级 | **保留** | S1 第 2 行 | 和模型同属"换后端"，必须相邻 |
| 生成参数 | 一级 | **保留 + 扩组** | S1 第 3 行，新增组 A(5 项) | 内容域唯一合法落点 |
| 提示词上下文（中转） | 一级 | **改名** | S1 第 4 行「提示词与记忆」 | 名实一致：它下面就是提示词 + 记忆 |
| 提示词（内层） | 中转内层 | **保留** | 同上内层 | 文本编辑属低频，必须留在第 3 层 |
| 记忆与上下文（内层） | 中转内层 | **保留 + 扩组** | 同上内层，新增 #6 | — |
| 语音入口 | 一级 | **改名 + 扩组** | S2 第 1 行「语音与唤醒」，新增组 C(2 项) | "唤醒"已无实现（旧审计删除唤醒路径），但用户心智仍按"语音与唤醒"找 |
| 语音引擎（ASR 详设） | **全局浮层**（`showVoiceAsr` 独立布尔） | **层级统一** | 收为「语音与唤醒」的内层 | 现状是绕过 `SettingsSubDialog` 的第二个全局浮层，返回语义与其余 16 页不一致，是新页最容易复制的错误样板 |
| 手势（侧键绑定） | 「关于」内层 | **提升** | S2 第 2 行「手势与侧键」 | 命中 F3（调完立刻可感）；埋在"关于"里等于藏起来 |
| 外观与点阵 | 一级 | **改名 + 扩组** | S2 第 3 行「外观与动效」，新增组 D(4 项) | 它现在实际承载风格/点阵/简洁，加入动效、触感、表冠后名字必须改 |
| 工具与 MCP | 一级 | **保留 + 扩组** | S3 第 1 行，新增组 E 的 4 项 | 工具能力单一首页 |
| 安全策略 | 一级 | **保留 + 扩组** | S3 第 2 行，新增 #13 | 审批阈值属于"放权"，不是"工具开关" |
| 权限 | 「关于」内层 | **提升** | S3 第 3 行 | 命中 F4：拒绝 root 是高频首查项；且与安全同分区 |
| **网络与请求（新）** | 根页 4 行内联 | **新增页** | S4 第 1 行 | 现状把 超时/重试/退避/WiFi 四行内联在根页，占 4×49dp；上收成页给新增组 F 腾出位置 |
| 网络体检 | 「关于」内层 | **改挂** | 「网络与请求」内层 | 体检是"连不上时"的诊断，必须紧贴连接参数 |
| 网页控制台 | 一级 | **保留 + 扩组** | S4 第 2 行，新增 #19/#20 | — |
| 导入导出 | 一级 | **保留** | S4 第 3 行 | 40 键语义不变 |
| 关于（中转） | 一级 | **拆解降级** | S4 第 4 行「关于」（单页） | 4 个子项各有更好去处后，中转页只剩"版本 + 重看引导"，中转形态不再成立 |

**配套删除**：`showVoiceAsr` 这个绕过枚举的全局浮层布尔必须删除，改为 `SettingsSubDialog.VOICE_ASR` 的内层（否则 `SettingsScreenStructureGuardTest` 的"枚举 ↔ 分支 1:1"约束对它是盲区）。

### 5.2 对守卫测试的影响（逐条，含"必须改什么"）

| 守卫测试 | 现有约束 | 本规范的触发点 | 必须同步改动 |
| --- | --- | --- | --- |
| **SettingsScreenStructureGuardTest** | ①根页组标题**恰好 4 个**且按 模型/语音与工具/网络与交互/系统 排序；②每个组标题双语非空；③`SettingsSubDialog` 每个值都有 `when` 分支；④分支与枚举值一一对应 | 4 个组标题**全部改名**（顺序也变），`SettingsSubDialog` 增 `GESTURES` / `PRIVILEGE` / `NETWORK_REQUEST` / `VOICE_ASR` | 改 `expectedSections` 的 4 个字符串为新组标题；`SettingsSubDialog` 每个新值在 `SettingsScreen` 补 `when` 分支；删掉 `showVoiceAsr` 分支路径。**数量仍为 4**，所以"≤5 分区"的结论不会让本测试的算术发生变化 |
| **SettingsCoverageTest** | 网页 10 页逐行覆盖，`missingInWatch` 必须空表；表端形态是网页形态的真子集 | 本轮 20 项**不进网页**，`entries` 不变 | **无需改动**。它的断言是"网页行在表端有落位"，与手表页面**组织方式**解耦；只有"新增网页字段"才会让它红 |
| **ConsoleSettingsParityGuardTest** | ①网页 PAGES 必须 10 页/63 行/55 带键行；②每个桥写分支必须在 `ConsoleFields` 声明；③每个声明字段必须在 `snapshot()` 回显 | 20 项**不开放给控制台** | **无需改动**。若日后开放任意一项，必须三处同改（网页 PAGES + `ConsoleFields` + `ConsoleBridge.write/snapshot`），且 63/55 两个计数要重算——这是本规范刻意回避成本 |
| **SettingsFieldRegistryGuardTest** | ①每个 `KEY_*` 必须有 getter 读；②同键不得被两个字段共用；③默认值必须来自 `DEFAULT_*` 常量（禁裸字面量）；④`DEFAULT_*` 必须被用；⑤数值默认落在合理区间；⑥布尔必须 setter/getter 成对且可往返；⑦10 个新开关必须在覆盖清单里 | 新增 **20 个 `KEY_*` + 20 个 `DEFAULT_*` + 20 个属性** | 每项都要三件套齐全；**禁止**复用同一个存储键（如 `requestTimeoutSeconds` 与 `net_diag_timeout_ms` 必须是两个键）；建议按现有风格为新的数值项补区间断言（例如 `shell_command_timeout_ms` 落在 `ShellPolicy.MIN_TIMEOUT_MS..MAX_TIMEOUT_MS`、`approval_allow_threshold` 落在 0.5..0.95、`rotary_scroll_gain` 落在 0.5..2.5）；`newFlags` 覆盖清单只有在新增**布尔**项时才需要扩展（本轮多数是滑杆/枚举，仅 `tts_queue_mode` 等枚举不触发） |
| **SettingsTransferCodecTest** | `PortableKeys.size == 40` 且导出顺序稳定；密钥字段绝不进快照 | 本轮 20 项全部"仅本机" | **无需改动**（见 3.2）。若改判为可移植，则需同时改 `PortableKeys`、`export` 的 `to` 表、`apply` 的写回分支与 `40` 断言 |
| **SettingsStyleTest / SettingsVisualGuardTest / SettingsContrastTest / AppearanceSettingsTest** | 4 种风格都要渲染、对比度达标、外观档位跟随快照 | 每种风格都要新渲染组 A–F 的新行；大卡片档新增 8dp 卡间距 | 4 风格各补一轮快照/对比度用例；**建议新增** `SettingsSpacingMathTest`：断言 `CardStyleCardGapDp == 8f` 且 `isOnGrid(8f)` 为真、气球档 `CardRowGapDp == 0f` 不变 |
| **WatchSettingsCatalogTest** | 每条 item 的 `domain` / `form` / `labelRes` 有效；只读项**不得**给 `preferenceKey` | 若把 20 项纳入该清单 | 只读项（权限、体检结论）保持无 `preferenceKey`；可写项必须给真实 `preferenceKey`（这是"假开关"结构性防线） |

### 5.3 一次性风险清单（实施时容易踩的 4 个坑）

1. **`mainContextTurns` 类重复键**：`maxContextTurns` 同时出现在「模型与端点」和「记忆与上下文」两页，必须**同一个键、两处写同一属性**，不得再造第二个键（`SettingsFieldRegistryGuardTest` 的"一键一字段"会直接抓）。
2. **父页回写旧值**：内层页返回时必须重读偏好（`syncFromPreferences()`），否则父页保存路径用旧快照覆盖刚改的值——`MemoryManagerContent` 的注释已记录过该 bug。
3. **8dp 卡间距 vs 0dp 组内距**：两条并存不冲突，但必须分别落在 `CardStyleCardGapDp`（大卡片档）与 `CardRowGapDp`（气球/方角档），**不允许**用一个常量两头兼顾。
4. **hero 不能放开关**：hero 只做"状态 + 跳转"。把手感类开关搬上 hero 会让误触改配置，且 hero 没有回滚面。

---

## 6. 实施顺序

### 6.1 第一批（先做这 5 个，按此顺序）

| 顺序 | 项 | 为什么先做 |
| --- | --- | --- |
| 1 | #9 `rotary_scroll_gain` | 消费点只有 2 处设置页 `dispatchRawDelta`（根页 + 全部子页共用同一个 `SettingsSubPage` 外壳），纯乘法，改动面最小；命中 F1+F3，用户一上手就能感到；且它是后续所有页面滚动的公共系数，越早落地后续页面调优越省事 |
| 2 | #1 `stream_emit_interval_ms` | 弱设备"文字抖成一片"的**唯一**解；`StreamingThrottle` 已有 `DEFAULT_INTERVAL_MS` 命名常量，替换即生效；命中 F1+F3 |
| 3 | #15 `shell_command_timeout_ms` | 安全面收益最直接（长命令挂死 → 可调上限）；`ShellPolicy` 已自带 `MIN/MAX` 夹紧，无需新增校验；命中 F4 |
| 4 | #13 `approval_allow_threshold` | 误触率可量化改善；`ApprovalSliderState` 是纯状态机常量，风险可控；命中 F3 |
| 5 | #7 `tts_speech_rate` | 播报可用性（默认语速对中文偏快）；`ReplySpeaker` 是单点注入，且失败路径已全程 try/catch |

**共同理由**：这 5 项都满足"**消费点单一 + 已有命名常量 + 无需新页面 + 不改结构守卫**"，每一项都能在一个改动单元内完成并独立验证。它们也覆盖了 4 个分区（S1/S2/S2/S3/S4 全部命中），第一批就能把新 IA 的四条路径各点亮一条，便于尽早发现组标题/间距问题。

### 6.2 分批路线

1. **P0 结构**：改 4 个组标题 + 新增 `NETWORK_REQUEST` 页 + 上收根页 4 行 + `SettingsSubDialog` 补值 + 删 `showVoiceAsr` → 跑 `SettingsScreenStructureGuardTest`。
2. **P1 手感五项**（6.1 的 1–5）→ 跑 `SettingsFieldRegistryGuardTest` + 4 风格快照。
3. **P2 提升三页**：手势 / 权限 / 网络体检 迁位 → 跑 4 风格 + 返回语义用例。
4. **P3 剩余新项**（组 A 其余 3 项、组 B、组 D 其余 3 项、组 E 其余 3 项、组 F 其余 3 项）→ 汇总跑全量设置测试。
5. **P4 收口**：14 行根页行数断言、`CardStyleCardGapDp=8f` 断言、双语 414 → 414+N 条完整性检查。

---

## 7. 本文的假设与待确认（不超过 3 条）

1. **A1｜顶层取 4 个分区而非 5**：依据 0.1 的屏高算术，且与既有 4 组标题守卫一致。若产品坚持 5 分区，需要重算首屏行数并把组标题开销写进验收。
2. **A2｜20 项全部"仅本机"，`PortableKeys` 保持 40**：依据"节奏/硬件/连接参数不该跨设备搬"。若要求跨设备同步，需新增第二张白名单并改 `40 → 40+N` 断言。
3. **A3｜20 项全部不开放给网页控制台**：避免 `ConsoleFields` / 网页 PAGES / 10-63-55 三条计数连带改动。若要求控制台可改，需一次补齐三处并重算计数。

> 未决项一律按上述默认执行，改动点已写明"改哪里、改完哪个守卫会红"，不阻塞 P0 开工。

