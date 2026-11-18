# 0.0.0.100 整十代码审查（5 路子智能体）

> 第五次整十审查（上次是 0.0.0.90）。全部只读、不改文件、不跑构建；下面每条都由我复核代码后决定处置。
> 5 路：聊天页与共享 UI / 网络与 MCP / 特权执行层 / 红队对抗 / 文档与守卫一致性。

## 一、本轮就修（3 条）

| # | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| 1 | **审批浮层吃手势**：`ApprovalFloatingLayer` 为预合成常驻组合、靠 `graphicsLayer(alpha)` 显隐，而 alpha=0 **不影响手势消费** —— 屏幕中部约 190dp 宽的带子把手势全吃掉，正文列表在那一段竖划完全没反应 | `ApprovalOverlay.kt` 的滚动容器 | `verticalScroll(contentScroll, enabled = visible)`；隐藏时整棵语义树 `clearAndSetSemantics {}`（读屏也读不到隐藏文案） |
| 2 | **引号能绕过破坏性判定**：`rm -rf "/data"`、`rm -rf '/system'`、`sh -c 'rm -rf /data'`、`dd of="/dev/block/mmcblk0"`、`pm uninstall "com.android.systemui"` 全部落到 RISKY —— 而 RISKY 可被「记住此命令」永久免审批，WORKSPACE 沙箱也只挡 DESTRUCTIVE | `ShellPolicy.dangerOf` 直接对原文做正则 | 判档前加**词法归一**（去引号与反斜杠），「记住此命令」的键仍是原文 |
| 3 | **路径写法漏判**：`rm -rf /data/`、`/./data`、`/system/bin/rm -rf /data`、`rm --recursive --force /data` | `rmInvocation` 前缀类只有空白与 `;&\|(`；目标用字符串全等 | 前缀类补 `` `/` `"` `'` `` 等；目标先 `canonicalPath`（去重复斜杠、去 `/./`、去尾斜杠）再比 |

回归：`ShellPolicyTest.quotedOrWrappedCommandsCannotHideTheirRealTarget`（10 个绕过样本 + 1 个反方向"安全目标不许被误升级"）。

## 二、记录在案、未修（按严重度）

### 网络与 MCP（12 条，2 高）
- **高**｜`McpHttpTransport.readBody` 用 `readText()` 读完整个响应体，**无字节上限**；12s 的 `withTimeoutOrNull` 套的是阻塞 IO，超时后也不 `abort()` → 端点回百 MB 体或每 5s 吐一字节即可拖死 IO 线程 / OOM（手表堆只有数百 MB）。
- **高**｜取消只置 `isCancelled` 标志，**不持有也不断开在途连接**：点取消后请求仍跑满读超时（默认 40s、可设 180s），表持续耗电、请求继续计费。
- 中｜错误体 `errorStream.readText()` 无上限且整段进异常消息、`state.errorMessage` 与日志。
- 中｜`McpJson` 解析无深度上限，`[[[[…]]]]` 抛 `StackOverflowError`，绕过所有 `catch (Exception)`。
- 中｜`fetchRemoteModels` 全程无 `disconnect()`、非 2xx 分支不关 `errorStream`。
- 中｜只判 `endsWith("/v1/models")`：baseUrl 填 `https://api.openai.com/v1` 会拼成 `/v1/v1/models` → 模型清单恒空且静默。
- 中｜MCP 端点的 query（可能带 token）被拼进异常 detail，再进给模型看的 ToolResult。
- 中｜5xx 重试不解析 `Retry-After`；429 反而直接失败不重试；退避前不看 `isCancelled`。
- 中｜`McpHttpTransport.active` 单槽位，并发 post 时会 abort 错连接 / 清空仍在途的槽位。
- 中｜任一 `tools/call` 失败就把整份远端工具清单清空 15s 并 `dropSession()`，兄弟调用一起被打断。
- 中｜`streamMode` 只认 `data:` 行，服务端忽略 `Accept` 直接回整段 JSON 时"模型回了空消息"且无提示。
- 中｜MTU 检查向用户真实 baseUrl 发两条 1400B/1200B 的 NUL 填充 POST（消耗计费配额）。

### 特权执行层（11 条，3 高）
- **高**｜`RootShell` 命令超时后只 `future.cancel(true)`，而 worker 阻塞在 `readLine()`（管道读不响应 interrupt），子孙进程仍持管道 → 该任务永不结束，之后每条 root 命令逐个 15s 超时，root 静默降级。
- **高**｜读到 EOF 返回 "root shell closed" 后不清 `live`，死会话被永久复用；Shizuku 侧同理 → su 被撤权一次后永久失效且不重新申请。
- **高**｜`RootSession` 命令/哨兵/下一条命令共用同一条 stdin/stdout：命令读 stdin 会吞掉下一条命令；输出不带尾换行时哨兵粘成 `hitoken:0` → 会话永久错位。
- 中｜`ip link set … down` / `date -s …` / `logcat -c` 被判 SAFE（只按首词），READ_ONLY 沙箱放行且可记住。
- 中｜超时口径四处不一致：接口 `run(command)` 没有超时参数，工具层 `withTimeoutOrNull` 对阻塞调用无效，三后端各自硬编码 15s，`ShizukuShell.timeoutMs` 是死参数。
- 中｜Shizuku 绑定超时/取消时不 `unbind`，`pendingConnection` 被下次 bind 覆盖 → 悬挂绑定。
- 中｜`RootShell.close()` 与 `worker.shutdownNow()` **无调用点**，`PrivilegeManager.release()` 不 cancel scope → 每进一次设置页留一个常驻 `su` 进程。
- 中｜`ProcessExec` 用无上限 StringBuilder 累加输出（截断只在工具层）→ `logcat` 15s 可产出数十 MB。
- 中｜读线程只 `join(500ms)` 就返回已读部分、`exitCode` 仍 0 → 大输出被静默截断还报 success。
- 中｜`shellCommand != null` 分支排在 `forcedApproval` 之前 → 自写工具的"必弹审批"永远走不到；`ToolApprovalPolicy.ALWAYS` 也被记住清单覆盖。
- 低｜自写工具的参数 `$n` 原样插进模板，无引号转义。

### 红队对抗（6 项修复的复核）
- **挡住**：换行/分隔符拆分、`tokenMasked` 恒 `••••`、孤儿回执的绝大多数分支。
- **没挡住的第 6 项（重要）**：`providerProfiles` 写到第 6 套时，`ProviderProfileBook.add` 满额时**原样返回**，`saveWebProviderProfiles` 走完仍返回 true → 响应是 `ok:true` 且没有 `rejected`，账本里只剩 5 套。**0.0.0.95 那条修复的正是这个场景，但它没生效** —— 我上次只改了返回码，没改"满额时根本不报错"的上游。
- 没挡住：MCP 工具**不经过 `requiresApprovalAlways`**，`assessTool` 判它"安全只读" → STANDARD/EXPERT 下 AUTO_ALLOW，远端 server 带 shell 工具时可无人值守执行。
- 没挡住：`{"cmd": …}`、`{"commands":[…]}`、双转义嵌套等把命令藏在别的字段名里（`SecurityGuardrail` 只认裸 `"command"`）。
- 缝：`ConversationWindow` 的 j 循环无条件吞掉紧跟 assistant 的**任意** TOOL 消息，不校验 id 归属。

### 文档与守卫（6 + 6 条）
- 已修：`CONTEXT` 开工四步里写死的 1360、`HANDOFF` 的版本/基线/"7 项判据"、`CHANGELOG` 的 versionCode。
- 未修（下一轮）：`SettingsDotRuleTest` / `OnboardingPagerTest` / `SettingsVisualGuardTest` 三处切片仍无锚点自证；`LongTermMemoryWriteTest` 另三处裸切片；`ContextDocFreshnessGuardTest` 只在整篇里找版本号（§0.5 写着旧版本时仍绿）；`AgentToolHonestyTest` 依然是自证式断言。

## 三、教训

- **"改了返回码"不等于"改了行为"**：第 6 项修复落在一个"上游根本不报错"的路径上，红队一条 POST 就验出它没生效。
- **安全判档必须在 shell 语义上做**：引号、路径规范化、外层解释器都是 free bypass。
- **alpha=0 不是 invisible**：Compose 里隐藏只能靠条件组合或 `clearAndSetSemantics`/禁用交互修饰符，`graphicsLayer` 只管画。
- 文档数字别再手抄三处：同一事实只允许有**一个**权威来源（§0.5），其它地方一律引用它。
