# 问题登记表 · 2026-11-09

> 全部条目来自实测命令输出、真机截图或源码行号，不含推测。状态：已修 / 未修 / 待验证。

## A. P0 用户可见错误（今日已全部修复）

| # | 问题 | 证据 | 状态 |
|---|---|---|---|
| A1 | 收起态小白条热区与主键重叠，点一下同时展开底栏并开始录音 | `ChatScreen.kt:770-798` 不消费事件 + `BottomInteractiveBlock.kt:396-487` 手势常驻 | 已修（`isCollapsed` 分支 + 手势 key） |
| A2 | 语音「取消」实际提交识别，半句话被派发给 Agent | `AsrSession.kt:10-13` requestStop=照常转写 | 已修（改 `cancel()`） |
| A3 | 审批放行 220ms 内按返回，放行被改写成拒绝 | `ChatViewModel.kt:322-332/351-355` 无决策锁 | 已修（`isApprovalCollapsing` 作锁） |

## B. 功能缺失 / 假控件

| # | 问题 | 证据（原始） | 状态与证据 |
|---|---|---|---|
| B1 | 「边说边显示」是假开关 | 原 `VoiceInputManager.kt:99` 空实现、`ChatViewModel.kt` 零调用 | **已修**：`VoiceInputManager.kt:96-100` 中间结果真上抛 → `AgentSettingGates.kt:18` `VoiceDraftGate` 闸门 → `ChatViewModel.kt:508` 写草稿；`AgentSettingConsumptionTest.kt:43` |
| B2 | 「记住此命令」从未接线 | 原 `rememberable` 无 UI 读取、allowlist 永远为空 | **已修**：`BottomInteractiveBlock.kt:603` 真给「记住并放行」入口 → `ChatViewModel.kt:377-384` 写 allowlist；判定在 `ChatUiState.kt:206-217` `ApprovalRememberPolicy` |
| B3 | 网页端 16 个字段能编辑但写不进手表 | 原 `ConsoleFields.ALL` 缺这些键，POST 静默进 `ignored` | **已修**：`ConsoleFields.kt:58-100` 已含 `sandboxLevel`(:93)、`toolApprovalPolicy`(:95)、`historyCompressThreshold` 等；`ConsoleSettingsParityGuardTest.kt:267-274` 五条已接通项从 `knownDroppedWebRows` 移除 |
| B4 | 网页端看不到 `maxContextTurns` | 原 `app.js` 零引用 | **已修**：`app.js:1061` 上下文轮数上限滑杆（0–200 轮），键表见 `:23` |
| B5 | 校验不一致 | 原 `temperature` FLOAT 无范围；输入方式二选一字段是 STRING 非 ENUM | **已修**：`ConsoleFields.kt:62` `temperature` 限 0.0–1.5，越界落 `ConsoleValue.OutOfRange`；输入方式二选一字段随本轮重构整体删除，代码里已无该标识符（全仓 grep 0 命中） |
| B6 | 默认值 3 处不一致 | 原 `app.js` 与 `AgentPreferences` 各写一份 | **已修**（三处已对齐）：`modelName`=`deepseek-chat`（`app.js:1003` / `AgentPreferences.kt:35`）、`maxContextTurns`=30（`app.js:1061` / `AgentPreferences.kt:78`）、`historyCompressThreshold`=0.8（`app.js:1063` / `AgentSettingGates.kt:208`）；`SettingsFieldRegistryGuardTest.kt:38-44` 禁止裸字面量默认值 |
| B7 | 主键边缘高光未实现 | 原设计已定未落地 | **已修**：`BottomInteractiveBlock.kt:534-544` 1dp 细光贴圆周（静息 0.22 / 录音随呼吸 / 按下 1.0 / 发送后 140ms 衰减），强度与色调纯函数在 `:918-961` |
| B8 | 引导页仍是 3 阶段动画，0 处手势教学 | 原 `OnboardingScreen.kt` | **已修**：`OnboardingScreen.kt:77-122` 五页手势文案、`:437-566` 每页一张示意图；守门 `OnboardingPagerTest.kt`（页数 / 标题 ≤8 字 / 说明 ≤20 字且 ≤148dp 单行） |

## C. 网页控制台

| # | 问题 | 证据（原始） | 状态与证据 |
|---|---|---|---|
| C1 | **控制台只在 Activity 生命周期内运行** | 原 `MainActivity.kt` 起停、Manifest 无前台服务 | **已修**：`ConsoleService.kt:24/30/72`（`startForeground` / `START_STICKY` / `startForegroundService`），`AndroidManifest.xml:9-10` 两条权限 + `:76` service 声明 |
| C2 | **密钥掩码回填风险** | 原 `/api/state` 给掩码、`app.js` 原样 POST 回写 | **已修**：`ConsoleSecrets.kt:25-40` 服务端自己认掩码（`isMaskEcho`，省略号与 `**` 两种风格都拦），`ConsoleApi.kt:154-161` 任何响应只给掩码 |
| C3 | 启动失败无日志 | 原 `ConsoleServer.kt` 两个 catch 全吞 | **已修**：`ConsoleServer.kt:57/60/65` 三条 `Log.w`（候选端口绑定失败 / 绑定异常 / 全部候选不可用） |
| C4 | 端口会静默漂移 | `ConsolePortPolicy` 8080 占用则 8081/8082 | **未修**：手表端仍没有「当前真实端口」的用户可见提示；免鉴权 `GET /healthz` 已在（`ConsoleApi.kt:125/276`），但只服务 PC 侧排障，用户看不到 |
| C5 | 一致性测试有 2 条死豁免 | 原 `ConsoleSettingsParityGuardTest.kt:254-273`（providerProfiles / longTermMemoryEnabled 其实已支持） | **已修**：`ConsoleSettingsParityGuardTest.kt:267-274` 记录五条已接通项从 `knownDroppedWebRows` 移除，基线只剩仍未接通的 13 条网页行 |

## D. 性能（实测，含结论）

| 版本 | janky | p50 | p90 | Slow UI |
|---|---|---|---|---|
| 旧 UI 基线 | 41.79% | 18ms | 25ms | 7 |
| 新 UI | 69.84% | 25ms | 65ms | 33 |
| 新 UI + 滚动冻结点阵 | 66.28% | 28ms | 57ms | 49 |

- **结论：瓶颈在 UI 线程的组合与测量，不是绘制。** 成本来自字号放大（15/18/24sp）与每消息一层 Surface 卡片。
- `ScalingLazyColumn` 1.7.1 **不支持 `contentType`**，该优化路被库版本堵死（编译期验证）。
- Markdown 解析缓存**已实现**（`FrostedGlassComponents.kt:402-405`，含增量 stripper），此路无收益。
- 剩余唯一未试：消息卡改 `drawBehind` 去掉 `clip` 图层。收益不确定。

## E. 代码卫生

| # | 问题 | 数量 / 证据 | 状态 |
|---|---|---|---|
| E1 | 零引用的 public 声明 | 15 项 | **未修**（不影响行为，按 I.3 降为低优先） |
| E2 | 未使用 import | 49 处 | **未修**（同上，低优先） |
| E3 | 死字符串资源 | 原 7 条 | **部分已修**：本轮删除输入方式的两条提示串（键盘 / 语音各一条，`values` 与 `values-zh` 双侧共 4 行，`ChatViewModelTest` 的夹具已改为不依赖它们），剩 5 条 |
| E4 | `DEFAULT_TIMEOUT_MS = 15_000L` 重复定义 | 4 处 | **未修** |
| E5 | `SessionStore.save` 空 catch 吞写盘失败 | 1 处 | **已修**：`SessionStore.kt:25` 改为返回 `Result<Unit>`，`:10-23` 明令「丢弃返回值就等于回到空 catch」 |
| E6 | 剪贴板遥测结构上永远取空（不在前台即拒绝） | 原 `AgentDispatcher.kt:113-117` | **已修**：`AgentDispatcher.kt:114-117` 整段剪贴板遥测删除（结构上取不到，就不留恒空的假遥测），现只保留时刻 / 设备形态 / 电量三项 |
| E7 | 百度 ASR 3308–3317 错误码语义未确认 | 原 `BaiduAsrProvider.kt:133` | **已修**：`BaiduAsrProvider.kt:133-149` 不再自造语义——区间码只陈述「服务端返回该码、本次识别未完成」并让上层附带服务端 `err_msg`；`:149` 对语义未核实的码故意返回 false |

## F. 待验证（不是失败）

| # | 项 | 现状 |
|---|---|---|
| F1 | **P6 模型真实读取** | **PASS（真机实测）**：`refresh-models` 返回 2 个模型。原先「子页没打开过 → `cachedRemoteModels` 为空 = 从未执行」的判定随之作废；本轮设备离线（见 K.1），未在本机复跑，状态来自真机实测回报 |
| F2 | 设置页入口 | 代码链路已确认接通（`BottomInteractiveBlock:431` → `ChatScreen:755` → `ChatApp:166`）；`adb input swipe` 合成事件打不开，需用手指复验 |

## G. 阶段进度（按 56214378 顺序）

| 序 | 阶段 | 状态 |
|---|---|---|
| 1 | P5 UI 大改 | 约 97%（B7 主键边缘高光已落地 `BottomInteractiveBlock.kt:534-544`；**剩审批首帧第 2 刀未做**） |
| 2 | P6 模型读取 | **完成**（F1 真机实测 PASS：`refresh-models` 返回 2 个模型） |
| 3 | P2 动画统一 | **完成**（统一入口 `SettingsMotion.kt:24` `motionSpec`，main 内 23 处调用；总开关 `LocalAnimationsEnabled` 由 `ChatApp.kt:99/161` 注入，三屏共用） |
| 4 | P1 滚动卡顿 | 部分（只剩消息卡 `drawBehind`，D 节结论，收益不确定） |
| 5 | P4 删输入方式二选一状态 | **已完成**（18 文件删除；主键单击恒为语音起录，文字输入由左翼一步直接打开） |
| 6 | P3 引导重写 | **完成**（`OnboardingScreen.kt:77-122` 五页手势文案 + `:437-566` 示意图；守门 `OnboardingPagerTest.kt`） |
| 7 | P7 网页端 | **主体完成**（C1 前台服务 / C2 掩码 / C3 日志 / C5 死豁免 / B3 字段补齐 / B4 轮数滑杆）；**剩 C4 端口漂移的用户可见提示未做** |
| 8 | P8 收口 | 未开始 |

## H. 已独立验证通过的部分

- 密钥完好：`agent_prefs.xml` 中 `apiKey` 长度 35、`sk-` 开头、无 U+2026（脚本 `docs/keycheck.sh`）
- 手表网络：DNS → 112.49.40.58、ping 32ms、TCP 443 通、Wi-Fi on（脚本 `docs/netcheck.sh`）
- DeepSeek 端点：`/v1/models` 与 `/models` 均返回 401（非 404）
- 766 条单测全绿；release 3.4MB 已装 TicWatch Pro 5
## I. 用户新优先级（2026-11-10 下达，覆盖之前排序）

**第一优先级：高度可自定义。第二优先级：补全 AI 功能。**

### I.1 可自定义（按落地顺序）

| # | 项 | 现状 |
|---|---|---|
| I1-1 | 手表端开放最常改的 6 个「仅网页端」字段：温度、maxTokens、上下文轮数、压缩阈值、工具审批策略、沙箱级别 | 现在这 18 个字段手表端只读 |
| I1-2 | 数值字段从三档预设改为连续可调（温度 0–1.5 步进 0.1；maxTokens 阶梯 + 自定义） | `HyperParamsSection.kt:41` 只给 0.2/0.7/1.2 三档并量化到 0.1 |
| I1-3 | 手表端可编辑自定义系统提示词与角色卡 | 仅网页端 |
| I1-4 | 手势与副键可绑定（长按动作、左右副键各自绑定） | 固定写死 |
| I1-5 | 外观自定义扩展：字号档、强调色、点阵密度、动画、紧凑模式 | 点阵密度/动画/紧凑已有，字号与强调色写死 |
| I1-6 | 配置导出 / 导入（JSON，不含密钥） | 无 |

### I.2 AI 功能补全

| # | 项 | 现状 |
|---|---|---|
| I2-1 | 边说边显示（partial results） | **已修**（见 B1）：`VoiceInputManager.kt:96-100` 中间结果经 `AgentSettingGates.kt:18` 闸门上屏 |
| I2-2 | 记住此命令 / 命令允许清单 | **已修**（见 B2）：`BottomInteractiveBlock.kt:603` 入口 + `ChatViewModel.kt:377-384` 写 allowlist |
| I2-3 | 模型清单真实读取 | 待验证（拉取只在打开子页时触发） |
| I2-4 | 子代理模式 | 后端已有 `SubAgentRunner`，设置项存在，端到端未验证 |
| I2-5 | 视觉 beta / 长期记忆 / 联网搜索 | 设置项存在，效果未验证 |

### I.3 与旧清单的关系
- 旧清单 E 类（死代码/import 清理）降为**低优先**，不再占用主进度
- 旧清单 C1（控制台前台服务）仍为**高优先**：网页端是唯一能改那 18 个字段的地方，服务不常驻就随时失联
- P1 性能按实测结论处理：只剩消息卡 `drawBehind` 一条路，收益不确定
## J. C1 实施规格：控制台迁前台服务（下一轮直接照做）

### J.1 现状（已查实）
- `MainActivity.kt:36` `private val consoleServer by lazy { ConsoleServer.create(applicationContext) }`
- `MainActivity.kt:89` `consoleServer.start()`（onCreate 末尾）
- `MainActivity.kt:93` `consoleServer.stop()`（onDestroy）
- Manifest 里只有 Shizuku 的 `.agent.privilege.UserService`，**没有前台服务**
- 唯一另一个 start 点：`RemoteConsoleSection.kt:92`（仅在用户打开设置页时救回来）
- 失败时无日志：`ConsoleServer.kt:55-64` 两个 catch 全吞；`agent/remote` 包 0 处 Log

### J.2 改动清单
1. **新增** `agent/remote/ConsoleService.kt`：
   - `class ConsoleService : Service()`
   - `onStartCommand` 里 `startForeground(NOTIF_ID, buildNotification())`，再 `ConsoleServer.create(this).start()`
   - `onDestroy` 调 `stop()`
   - `START_STICKY` 返回，避免被回收后不自启
2. **通知**：新增渠道 id `console`，重要性 `IMPORTANCE_LOW`（不响铃），标题「网页控制台运行中」，内容显示真实端口与四位码
3. **Manifest**：
   - `<service android:name=".agent.remote.ConsoleService" android:foregroundServiceType="specialUse" android:exported="false" />`
   - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />`
   - `<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />`
   - `<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />`
   - service 内加 `<property android:name="android:app_foregroundServiceType" ...>` 或用 specialUse 的子属性说明
4. **MainActivity**：`onCreate` 里去掉 `consoleServer.start()`，改为
   `ContextCompat.startForegroundService(this, Intent(this, ConsoleService::class.java))`；
   `onDestroy` 不再 `stop()`（服务自理）
5. **开关语义**：用户关闭网页控制台时 `stopService(...)`，而不是只 `server.stop()`（否则前台通知还在）
6. **日志**：`ConsoleServer.start()` 的 catch 里补 `Log.w(TAG, ...)`，把候选端口与异常一起打出来
7. **健康探针**：新增免鉴权 `GET /healthz` 返回 `{"port":..,"uptimeMs":..}`，PC 排障时用它区分「没起 / 起在别的端口 / 401」

### J.3 验收
- 关屏 10 分钟后 `adb forward tcp:18080 tcp:8080` 后请求 `/healthz` 仍返回 200
- `am force-stop` 后不自动重启（用户显式退出应停止），但系统内存回收后应能自启
- 通知栏可见「网页控制台运行中」并显示端口
- 手表端关闭开关后通知消失、端口不再监听
## K. 设备离线阻塞与测量口径（2026-11-10 20:40）

### K.1 阻塞
- `adb devices` 空（35.0.2 / E:/app/platform-tools/adb.exe，kill-server 后仍空，5 次复检）
- USB PnP 无 Android 设备，仅 CM_PROB_PHANTOM 幽灵记录
- `adb mdns services` 空；扫 192.168.10.0/24:5555 全不通
- => 实测与功耗测量全部无法执行；**需人类接线或开无线调试**

### K.2 「组件飞起来」两条已确认成因
| # | 成因 | 证据 |
|---|---|---|
| 1 | 滚动首次组合仍播入场位移（+24dp 浮起 + 淡入），touch fling 一次带入多行 | `SettingsRowStagger.kt:118-125` 用 index 作唯一 key，`claimEnter(index)` 屏幕级 mutableSet |
| 2 | **曲率缩放未关**：`SettingsShared.kt:282-311` 无 `scalingParams`，`grep ScalingParams` 在 main 零命中，而 `:298` 注释声称已「关死」 | 真机 uiautomator：同文案节点 254x45 vs 236x41 px，同屏尺寸差 7.6%/9.8% |

两条都要修，只修一条仍会看到行随位置变大变小。

### K.3 测量口径（已定，重派时按此执行）
1. **被测包锁定**：B 方案——用 `3493994` 之上重建（含修复）的 release 包验证修复；不再复现旧 bug（用户已口述确认现象）
2. **息屏 vs 保活**：A/B/C 场景用 `KEYCODE_WAKEUP` 每 15s 保活并在报告中标注该开销；另加 `-ScreenOff` 长采 15–30 分钟做续航外推
3. **C 场景归因**：补一段「应用不在前台时的空滑对照」，否则 C−B 只能算上界
4. `capacity` 整数百分比误差大，跌幅 <2pp 标「低置信」，续航以电流法 cross-check

### K.4 已就绪的采集链路（仓库外）
`D:/AndroidProjects/powerlab/`：`pwr_collect.sh`、`pwr_swipe.sh`、`run_scenarios.ps1`、`analyze.py`（合成数据自测通过），CSV 输出到 `powerlab/raw/`。
