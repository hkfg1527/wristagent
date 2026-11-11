# 设置系统审计（SETTINGS-AUDIT）

> 生成方式：逐字段读 `agent/AgentPreferences.kt` 源码 + 对 `wear/src/main` 全量 grep 真实消费点。
> 所有"消费点"列都是从源码里读出来的文件:行，不是推测。审计时间点即本次改动前的 HEAD。
>
> 判定口径（来自本任务书第二步）：
> - **(i) 死**：非 UI 代码里没有任何读取点
> - **(ii) 无效**：有读取点但对行为无可观测影响（只被回显/只被写回）
> - **(iii) 重复**：与另一项语义重复
> - **真**：读取点位于真实执行路径，改值后行为可观测变化

> **时点说明**：本表是 2026-11-10 审计时点的快照（47 个属性）。此后又发生了三件事，
> 本表**未逐行回填**，以 `CHANGELOG.md` 的 1.0 条目为准：
> 1. `mainInputType` 已整体删除（输入方式改由左翼直接打开输入框决定）；
> 2. `visionBetaEnabled` 已删除（agent 侧零消费点，打开只多一个按钮且点击只提示服务未接入）；
> 3. 新增 `toolApprovalPolicy` / `sandboxLevel` / `historyCompressThreshold` / `longTermMemory`（remember_fact 写入方）四项。

## 一、全字段表（47 个属性）

"手表可改"= 手表端是否存在写入路径（开关 / 单选 / 滑杆 / 文本对话框）。

| # | 字段 | 存储键 | 默认值 | 手表可改 | 真实消费 | 消费点（文件:行） | 判定 |
|---|------|--------|--------|----------|----------|-------------------|------|
| 1 | securityMode | security_mode | STANDARD | 是（三档单选） | 是 | AgentDispatcher.kt:314,348,354,388；SecurityGuardrail.kt:30,69 | 真 |
| 2 | rightButtonAction | right_button_action | MODEL_SWITCH | 是（本无写入路径） | **否** | 仅 SettingsScreen.kt:108/139/607 与控制台回显，**无任何执行路径** | **(i) 死** |
| 3 | apiKey | api_key | "" | 是（文本输入） | 是 | AgentDispatcher.kt:832；VoiceAsrGateway.kt:62 | 真 |
| 4 | baseUrl | base_url | https://api.deepseek.com | 是（SELECT） | 是 | AgentDispatcher.kt:737-740 | 真 |
| 5 | modelName | model_name | deepseek-chat | 是（SELECT） | 是 | AgentDispatcher.kt:799,804；ChatViewModel.kt:457 | 真 |
| 6 | matrixSpacingDp | matrix_spacing_dp | 16f | 是（滑杆） | 是 | ChatApp.kt:69,78,89 | 真 |
| 7 | showThoughtChain | show_thought_chain | true | 是（开关） | 是 | AgentDispatcher.kt:869 | 真 |
| 8 | reasoningEffort | reasoning_effort | DEFAULT | 是（单选） | 是 | AgentDispatcher.kt:800 | 真 |
| 9 | ultraSaving | ultra_saving | false | 是（工具内） | 是 | AgentToolRegistry.kt:53,128,130,233,342 | 真 |
| 10 | hasCompletedOnboarding | has_completed_onboarding | false | 否（只读） | 是 | ChatApp.kt:66,154；OnboardingScreen.kt:101 | 真（只读） |
| 11 | temperature | temperature | 0.7f | 是（滑杆） | 是 | AgentDispatcher.kt:805 | 真 |
| 12 | mcpEndpoint | mcp_endpoint | http://127.0.0.1:8000/sse | 否（只读回显） | **否** | 无 MCP 传输层；仅 SettingsScreen 回显 + ConsoleBridge 读写 | **(i) 死** |
| 13 | isMcpEnabled | is_mcp_enabled | true | 是（开关） | **否** | 同上，**应用内不存在任何 MCP 客户端** | **(i) 死** |
| 14 | customSystemPrompt | custom_system_prompt | "" | 是（文本对话框） | 是 | AgentDispatcher.kt:141,1139；AgentSystemPrompt | 真 |
| 15 | maxContextTurns | max_context_turns | 30 | **是**（4/10/30 单选） | 是 | AgentDispatcher.kt:655,812 | 真（已可改） |
| 16 | 输入方式二选一设置项 | — | **已删除（输入方式改由左翼直接打开输入框决定）** | — | — | 字段与整条二选一状态已随本轮重构删除：主键单击恒为语音起录，文字输入由左翼一步打开，代码内 0 引用 | **(i) 已删除** |
| 17 | maxOutputTokens | max_output_tokens | 2048 | 是（滑杆） | 是 | AgentDispatcher.kt:806 | 真 |
| 18 | streamMode | stream_mode | true | 是（开关） | 是 | AgentDispatcher.kt:807,831,922 | 真 |
| 19 | cachedRemoteModels | cached_remote_models | deepseek-chat,deepseek-reasoner | 通过子页刷新 | 是 | ModelCatalog.kt:28；ModelCycle.kt:4 | 真 |
| 20 | modelCatalogFromRemote | model_catalog_from_remote | false | 否 | 是 | ModelConfigSection.kt:50,70 | 真（UI 真值） |
| 21-26 | toolDeviceStatus / Vibrate / MatrixDensity / PowerMode / Weather / WebSearchEnabled | tool_*_enabled | true | 是（6 个开关） | 是 | AgentToolRegistry.kt:61,75,96,116,136,157 | 真 |
| 27 | selfDefinedTools | self_defined_tools | "" | 由 AI 工具写入 | 是 | AgentToolRegistry.kt:208,459-463,634 | 真 |
| 28 | rememberedShellCommands | remembered_shell_commands | "" | **否**（设置页无入口） | 部分 | 经 `rememberedShellCommandList()` 被 AgentDispatcher.kt:356 读取；但 `rememberShellCommand()` 的**唯一写入者是零调用者的 `approvePendingAndRemember`** → 清单永远为空 | **(ii) 无效（C6，已在他人边界内另修）** |
| 29 | asrEnabled | asr_enabled | false | 是（开关） | 是 | VoiceAsrGateway；SettingsScreen.kt:338 | 真 |
| 30 | asrProvider | asr_provider | SYSTEM | 是（单选） | 是 | AsrServiceRouter；VoiceAsrSection.kt | 真 |
| 31 | baiduApiKey | asr_baidu_api_key | "" | 是（文本输入） | 是 | AsrProvider.kt:52,57；VoiceAsrGateway.kt:62 | 真 |
| 32 | baiduSecretKey | asr_baidu_secret_key | "" | 是（文本输入） | 是 | AsrProvider.kt:53,57；VoiceAsrGateway.kt:63 | 真 |
| 33 | asrDevPid | asr_dev_pid | 1537 | 是（单选） | 是 | VoiceAsrSection.kt:63 → AsrSettings.language | 真 |
| 34 | animationsEnabled | animationsEnabled | true | 是（开关） | 是 | VoxelMatrixEffect.kt:349 等 50 处 | 真 |
| 35 | ~~visionBetaEnabled~~ | ~~vision_beta_enabled~~ | — | — | — | **1.0 已删除**：agent 侧零消费点，打开只多一个按钮、点击只显示「识别服务尚未接入」；要真实现需给 ChatMessage 加图片 content part 并改两处请求体拼装，而 TicWatch Pro 5 无摄像头、手表上没有采集手段 | (i) 死 — 已删 |
| 36 | subAgentEnabled | subAgentEnabled | false | 是（开关） | 是 | SubAgentPolicy.isEffective → AgentToolRegistry.kt:214,681 | 真 |
| 37 | autoSpeakReplies | autoSpeakReplies | false | 是（开关） | 是 | AgentSettingGates.kt:12 → ChatViewModel.kt:300 | 真 |
| 38 | asrLiveTranscript | asrLiveTranscript | true | 是（开关） | 是 | AgentSettingGates.kt:12 → ChatViewModel.kt:504 | 真 |
| 39 | voiceWakeOnScreen | voiceWakeOnScreen | false | 否（已移除） | **否** | 主源码 0 处读取 | **(i) 死** |
| 40 | voiceWakeOnLockScreen | voiceWakeOnLockScreen | false | 否（已移除） | **否** | 主源码 0 处读取 | **(i) 死** |
| 41 | providerManagedSearchEnabled | providerManagedSearchEnabled | false | 是（开关） | 是 | AgentToolRegistry.kt:158 | 真 |
| 42 | defaultThinkingEnabled | defaultThinking | true | 是（开关） | 是 | AgentDispatcher.kt:795,798 | 真 |
| 43 | memoryEnabled | memoryEnabled | true | 是（开关） | 是 | AgentDispatcher.kt:138,139 | 真 |
| 44 | autoCompressContext | autoCompressContext | true | 是（开关） | 是 | AgentDispatcher.kt:647,652 | 真 |
| 45 | usePureBlack | usePureBlack | true | 否（只读） | **否** | `Wear.Palette.Bg` 是编译期常量 0xFF000000，**拨它不改变任何像素** | **(i) 死** |
| 46 | themeMode | themeMode | DARK | 否（只读回显） | **否** | WatchSettingsSections.kt:137,155 只取来显示文案；表端只有一套深色令牌集 | **(ii) 无效** |
| 47 | compactMode | compactMode | false | 是（开关） | 是 | ChatViewModel.kt:70,126,130；AgentRepository.kt:98 | 真 |

## 二、网页有、手表缺的 4 个字段（任务书第三步点名）

审计结论与任务书假设**不完全一致**，以源码为准：

| 字段 | 网页端位置 | 审计事实 |
|------|-----------|----------|
| ① 上下文轮数 `maxContextTurns` | generate.memoryRounds | **表端早已可改**：MemoryAndSecuritySections.kt:52 三档单选（4/10/30）。缺的是"任意值"，网页端可写 4..200 |
| ② 压缩阈值 `historyCompressThreshold` | generate.historyCompressThreshold | **AgentPreferences 里根本没有这个字段**（grep 全仓：只有 console/app.js:1025 与 SettingsCoverage 的 WEB_ONLY 声明） |
| ③ 工具审批策略 `toolApprovalPolicy` | tools.toolApprovalPolicy | **没有字段**（console/app.js:1078） |
| ④ 沙箱等级 `sandboxLevel` | dialog.sandboxLevel | **没有字段**（console/app.js:1040） |

即：②③④ 是"网页端有一个只写在控制台 JSON 里的键，表端连存储都没有"——这正是用户抱怨 (d) 的成因之一。

## 三、本轮删除清单（满足 (i)/(ii)）

| 项 | 依据 | 删除动作 |
|----|------|----------|
| `voiceWakeOnScreen` | (i) 主源码 0 读取；开麦唤醒路径已整体删除 | 删属性、KEY、DEFAULT、catalog 项、测试项 |
| `voiceWakeOnLockScreen` | 同上 | 同上 |
| `usePureBlack` | (i) 0 读取；Bg 是编译期常量 | 删属性、KEY、DEFAULT、catalog 项、外观页只读行、测试项 |
| `themeMode` | (ii) 只被取来渲染文案，拨它不改变任何像素 | 删属性、KEY、DEFAULT、ThemeMode 枚举、catalog 项、外观页只读行、测试项 |
| `rightButtonAction` | (i) 全仓 0 执行路径；WingShortcutContent 根本没调用 `onSelect` | 删属性与 `RightButtonAction` 绑定，原位置换成真正有消费点的"手势与侧键绑定" |

### 三之二、手势动作的语义修正（集成反馈后）

第一版把"忘掉已记住的命令"挂在 `GestureAction.NEW_SESSION` 上，属于**隐式破坏性副作用**：
用户按这个手势只是想开新会话，却顺手丢掉了自己的审批放行记录，且没有任何提示。
现已改为独立动作 `GestureAction.FORGET_REMEMBERED` —— 动作名就是动作本身，
并在工具页给出一条**显式入口**（红色按钮 + 二次确认），两条路径调的是同一个方法。

同时补上 `remembered_shell_commands` 的上限可见性：`rememberShellCommand` 在清单满
`MaxRememberedShellCommands = 20` 条时静默返回 false，用户会以为自己记住了、其实没有；
现在工具页显示 `N / 20`，满额时取值变 Warning 色并附一行说明。

未删除但记录在案（越界，需控制台侧一并处理）：`isMcpEnabled`、`mcpEndpoint` —— 应用内不存在 MCP 客户端，是假开关，但删字段必须改 `agent/remote/ConsoleBridge.kt` 与 `ConsoleFields.kt`（本任务书禁止编辑）。

## 四、本轮新增（每条都带真实消费点）

| 新增项 | 存储键 | 默认值 | 消费点 |
|--------|--------|--------|--------|
| 压缩阈值 | historyCompressThreshold | 0.8f | `CompressPolicy` → AgentDispatcher.applyAutoCompaction() |
| 工具审批策略 | toolApprovalPolicy | RISKY | `ApprovalGate.decide(risk, mode, policy)` → AgentDispatcher.kt 两处工具闸门 |
| 沙箱等级 | sandboxLevel | FULL | `SandboxGate.decide(level, danger)` → AgentDispatcher run_shell 执行前 |
| 手势/侧键绑定（4 条） | longPressAction / doubleTapAction / sideKeyShortAction / sideKeyLongAction | NONE | `GestureBindingGate` + `GestureRuntime.perform`（终止当前任务 / 切换模型 / 忘掉已记住的命令） |
| 审批放行清单：条数可见 | （无新键，读 remembered_shell_commands） | 0 / 20 | `AgentPreferences.rememberedShellCommandCount` → 工具页显示 `N / 20`，满额 Warning |
| 审批放行清单：显式撤销 | （无新键） | — | `forgetRememberedShellCommands()` ← 工具页红色按钮 + 二次确认 |
| 配置导入导出 | （无新键，序列化现有键） | — | `SettingsTransferCodec` → 导入即刻写回全部白名单键 |

## 五、用户四条抱怨 → 对应改动

| 抱怨 | 根因（审计证据） | 改哪里 |
|------|------------------|--------|
| (a) 选项之间区分不够大 | 行间只有一条满宽 1dp 发丝线（SettingsShared.kt），组内行与子项行没有区分 | 分隔线分两档：同级行满宽线、子项行内缩 20dp 短线（`SettingsSubItemDivider`） |
| (b) 页面空、没有颜色 | 组标题用 TextSecondary，全页只有一条 20×2dp Accent 线 | 组标题升为 Accent；状态取值行用语义色：Success（只读/已绑定）/ Warning（工作区/清单满）/ Danger（从不确认/全权限） |
| (c) 组与子项层级读不出 | 组标题与行标题同宽同色（都是居中 BasicText） | 子项行左右各缩进 12dp（`SettingsSubItemIndent`）+ 字号从 bodyLarge 降到 bodyMedium，组标题保持满宽 |
| (d) 假开关 / 缺功能 | 本文件第一、三节 | 删 5 项死开关，补 4 个字段 + 3 个功能，每项都有执行路径 |
| 补充：审批放行记录只能加不能减 | `rememberShellCommand` 有 20 条上限且满额静默失败；`forgetRememberedShellCommands` 此前只有根页"清空记忆"会间接清掉 | 工具页新增「审批放行清单」组：条数 `N / 20`（满额 Warning）+ 显式「忘掉全部已记住的命令」（红色 + 二次确认） |
