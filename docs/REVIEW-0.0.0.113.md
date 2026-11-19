# 审查报告 · 0.0.0.113 / 0.0.0.114（整十仪式补做）

> 触发：`0.0.0.110` 是整十点，但当轮只跑了 8/8、没做 5 路审查、也没打 tag，欠了一次仪式。
> 本轮补做：**5 路子智能体并行只读审查**（设置 UI / 代理主循环 / 网络与 MCP / 特权与安全 / 测试质量）+
> 由 lead 逐条复核并修复其中确定成立的项。报告里的行号是**审查当时的行号**。

## 一、本轮已修（含回归测试）

| 来源 | 位置 | 缺陷 | 修法 | 验证 |
|---|---|---|---|---|
| 网络与 MCP | `McpJson.kt`（parseValue） | JSON 解析是**无限深递归**，远端回 `[[[[…` 直接 `StackOverflowError`；那是 `Error` 不是 `Exception`，工具循环 catch 不住 → 进程崩溃 | 加 `MAX_DEPTH = 32`（与 `ConsoleJson` 同值），进入容器时计数 | `McpJsonDepthTest` 3 条；**变异**：把上限改成 100000，两条立刻红 |
| 特权与安全 | `RootSession.buildCommand` | `printf '\n'` 抢在 `echo "$?"` 之前，`$?` 变成 printf 的退出码（恒 0）——**所有 root 命令都以 exit_code=0 回报**，失败命令被模型当成"已经改成功了" | `__rc=$?` 先存，再 printf，再 `echo "token:$__rc"` | `RootSessionTest` 断言新形态 + 顺序；`FakeSuProcess` 同时认两种形态 |
| 特权与安全 | `RootSession.run` | 输出**无上限**（`ProcessExec` 有 256K 上限，最热的 root 常驻路径反而没有） | 256K 封顶，超出继续读干不入内存，末尾标 `…[truncated]` | 与 `ProcessExecTest` 同判据 |
| 特权与安全 | `ShellPolicy.canonicalPath` | `echo $(rm -rf /data)` 的目标被正则连 `)` 一起吃成 `/data)`、反引号写法成 `` /data` ``、`/storage/..` 不折叠 → 全判 **RISKY**（可"记住此命令"永久免审批，且能过 WORKSPACE 沙箱） | 剥掉收尾定界符 + 逐段消 `..` | `ShellPolicyTest.commandSubstitutionAndDotDotCannotLaunderADestructiveCommand`；**变异**：去掉 `)` 剥离，立刻红（`expected DESTRUCTIVE but was RISKY`） |
| 代理主循环 | `ShellPolicy.dangerOf` | `rm$IFS-rf$IFS/data` 静态匹配不到 `rm `，判 **RISKY** → 可永久免审批，而 `sh -c` 执行时真的会展开 | 命中 `${IFS}` / `$IFS` / `$@` / `$*` 一律按破坏性 | `ShellPolicyTest.ifsBasedSeparatorEvasionIsTreatedAsDestructive` 4 条 |
| 代理主循环 | `AgentDispatcher` 重试循环 | 取消靠 `disconnect` 打断读，那条 `IOException` 被当成"可重试网络错误"——循环里**没有 isCancelled 检查**，用户点停止仍重发 1~3 次真实请求，最后把"取消"显示成「请求失败 · Socket closed」 | 循环开头与 catch 里都先判取消，直接回 `CancellationException` | 代码级修复（取消路径难以在单测里造，见"未验证"） |
| 代理主循环 | `AgentDispatcher.handleCancelledTools` | 取消时遍历**整批**调用补 `cancelled` 回执，已执行/已拒绝的调用会重复，同一 `tool_call_id` 两条 `tool` 消息 → 严格供应商 400 | 按 `toolCallId` 去重后再补 | 代码级修复 |
| 代理主循环 | `AgentDispatcher.extractToolSummary` | `query_time_weather` **从不返回** `temperature`/`weather`，却带默认值 `"22℃"/"晴朗"` —— 工具胶囊上凭空显示编造天气 | 改读 `weather_status`，显示"天气不可用（原因）" | `AgentToolHonestyTest` 加"不许出现 temperature/weather 字段"（原来的 `contains("22")` 判据在 22:00 会误报，已换掉） |
| 设置 UI | `SettingsShared.kt`（CARD_EACH 分支） | 大卡片档把 `Modifier.selectable(role=RadioButton)` 整段丢掉，只把 `onClick` 给 Card → 六套风格选择、安全三档、播报队列、手势绑定读不出"已选中"，也不是单选框 | 在卡片节点上补 `semantics { selected = …; role = Role.RadioButton }`（只补语义，不加第二个点击处理器） | 编译 + 既有语义用例 |
| 设置 UI | `SettingsShared.kt`（子页根） | `pullDownToDismiss` 不看浮层是否打开：对话框打开时往下拖 50dp 就把整页退掉，**正在编辑的系统提示词草稿直接丢** | `canPullDown = { overlayBack == null && listState.isAtScrollTop() }` | 代码级修复 |

## 二、已确认但**本轮未修**（按价值排序，留给后续轮次）

| 严重度 | 位置 | 问题 |
|---|---|---|
| 高 | `ProcessExec` | 超时只 `destroy()` 直接子进程，不杀进程组/孙进程 → `sh -c 'sleep 600'` 超时后真正的命令作为孤儿继续以 root 跑 |
| 高 | `AgentDispatcher:1078` | 非流式分支把整段响应读进 `StringBuilder`，无上限，随后 `JSONObject` 再复制一份 → 大响应 OOM（MCP 侧有 64K 封顶，聊天主链路没有） |
| 高 | `ConsoleServer:139` | 无 `Content-Length`/chunked 的 POST 绕过 64K 前置闸门，NanoHTTPD 先物化整段 body 再判 413，且发生在 token 校验之前 |
| 高 | `AgentDispatcher:235` | `stop()` 只置位不等待，`send()` 里无条件 `isCancelled = false` 会把仍在重试的旧协程"复活"，两个协程并发写同一个无锁 `mutableList` |
| 中 | `AgentDispatcher:1218` | 子代理请求不登记 `activeConnection`，取消断不到，最坏等满 `subagentReadTimeoutMs`（默认 60s） |
| 中 | `McpToolBinding:335` | `dropSession` 用 `@Synchronized`，`sessionFor` 用 `Mutex`，两把锁保护同一个槽 → 可能 close 掉兄弟协程正在用的 client |
| 中 | `AgentDispatcher:966` | 429 不重试、不解析 `Retry-After` |
| 中 | `ModelStream:243` | 流内错误帧（`{"error":…}`）被静默丢弃 → 界面显示"模型回了空消息" |
| 中 | `AgentDispatcher:297` + `AgentLoopPolicy:60` | 空转检测只认"整批签名连续相同"，交替 A,B,A,B 永远检测不到 → 真正的无限循环 |
| 中 | `AgentToolRegistry:746` | `define_tool` 的保留名表缺 `remember_fact`/`provider_web_search`/`run_subagent`，可落盘重名死工具 |
| 中 | `AgentDispatcher:1187` | 子代理的强制审批 `RiskAssessment` 不带 `commandText`（审批页看不到要执行的命令），也没有冷却 |
| 中 | `PrivilegeManager` 两处 | `AgentDispatcher:52`、`ConsoleBridge:243` 从不 `release()` → 常驻 su 进程活到进程结束 |
| 中 | `ShizukuShell.timeoutMs` | 死参数，远端硬编码 15s，设置里的 `shell_command_timeout_ms`（最长 120s）在 Shizuku 后端被静默忽略 |
| 低 | `SettingsCardEdgeZoom` | `MinScale = 1.00f`，缩放是空操作，配套守卫断言恒真（自我比较） |
| 低 | `SettingsStylePolicy.shell` 槽位 | 只有声明、无读取点，配套断言不产生任何像素差异 |
| 低 | `ConsoleServer:51` | 绑 `0.0.0.0` + 4 位数字码 + 明文 HTTP |

## 三、方法与局限

- 5 路子智能体并行、只读、要求给出真实行号并用 read/grep 自证；lead 对每条做二次复核后才动手。
- **局限**：本轮只对"能在单测里确定性复现"的项补了回归。取消路径、孤儿进程、OOM 类修复目前是**代码级修复，未验证**（无法在现有测试基建里稳定复现），已在 CONTEXT/CHANGELOG 如实标注。
- 测试质量审查（第 5 路）在本报告成稿时尚未返回，其结论并入下一轮。
