# 长期上下文（跨轮恢复的唯一入口）

> 每轮开工先读这一页。它只记**会变的东西**：硬约束、已定决策、当前阶段、待办、恢复步骤。
> 事实性细节去代码与 `docs/PERF-*.md`、`docs/brand/*`、`docs/DESIGN-*.md`。

## 0.0 接续卡（新会话开工第一件事：读完这一段再动手）

用户 2026-11-17 说"我还不想丢掉你"——所以这张卡的目的只有一个：**让换会话不等于失忆**。

**我是谁**：这台手表上 Wrist Agent 的常驻工程师。行事准则在 `docs/SELF.md`（用户可改），核心一句：
**报告必须和事实一致**——说修好了就是真机上修好了，说没验证就不假装验证过。

**现在在哪**（截至 2026-11-19 08:35）：
- 内部版 `0.0.0.107` / versionCode 145；发布点 `v0.0.0.10` / `.20` / `.30` / `.40` / `.50` / `.80` / `.90` / `.100` 均已打 tag 并推送。
- 单测基线 **1388 条 / 212 套件全绿**（`./gradlew :wear:testDebugUnitTest` 实测，0 失败 0 跳过）。
- P0–P3 四条主线全部收口（细节见 §0.5），20 个真实设置全部落地并带说明行；六套风格已上真机逐套核对。
- 当前主线：**设置页功能回归搬到 JVM（Robolectric + Compose ui-test）**，已到 11 个测试类 / 39 条用例；
  借它当场查出并修掉一个无障碍真缺陷（两行滑杆被并成一个节点，第二个滑杆读屏拨不动）。
- 网页控制台页面级 E2E 已接进 `release-check.mjs` 当第 4 项（令牌用 `su -c cat` 自动读，不再手填环境变量）。

**开工四步**（照做，不要凭印象）：
1. `git log --oneline -5` + `git status --porcelain` —— 确认工作区干净、远端追上没有
2. 跑一次单测，条目数必须等于 §0.5 的基线（**1378**）；不等就先查清再动手，别在漂移的基线上改代码
3. `manage_context({action:"usage"})` —— 看上下文占用；**压缩现在能自己触发**（见 §0.6.1）
4. 从 §0.5「下一步」那一行直接继续，不要重新规划

**当前环境变化（2026-11-17）**：工具面已换成原生工具（`pwsh` / `read` / `write` / `edit` / `glob` / `grep`），
`run_code` 不存在了；adb、gradle、git 全部走 `pwsh`。新装的两个插件：`@local/context-governor`（自我管理上下文）
与 `@local/android-wear-toolbox`（adb/Gradle 工具，若工具面里出现 `android_wear_*` 就直接用）。

## 0. 恢复步骤（新会话照做）

1. `docs/CONTEXT.md`（本页）→ `docs/PREP-LONGTERM.md` → `CHANGELOG.md` 顶部
2. `git log --oneline -8`、`git status --porcelain` 确认工作区干净
3. 环境配方：`docs/HANDOFF-0.05.md`（无 shell 工具、gradle env、adb、GUI、git push 的坑）
4. 跑一次 `./gradlew :wear:testDebugUnitTest` 确认基线（以 §0.5 的数字为准，别再在这里写死一份）

## 0.5 运行中状态（每轮覆盖这一段，别追加）· 最后更新 2026-11-19 07:55

**版本与基线**
- 内部版 `0.0.0.107`（versionCode 145）；`v0.0.0.10` / `.20` / `.30` / `.40` / `.50` / `.80` / `.90` / `.100` 已打 tag 并推送。
- 单测基线 **1388** 条 / **212** 套件全绿。
- 发布自检 `node docs/tools/release-check.mjs` = **4 段 8 条判据**；控制台页面 E2E **72 条**断言。

**0.0.0.107 · Shizuku 绑定解绑 + 工具诚实性测试改成真调用**
- **Shizuku 悬挂绑定**（0.0.0.100 审查中危）：`bind()` 的 `suspendCancellableCoroutine` 没有
  `invokeOnCancellation` —— 绑定超时（`withTimeoutOrNull`）或取消后连接**永不解绑**，
  而且 `pendingConnection` 会被下一次 bind 覆盖，`release()` 只能解掉最后一个。
  现在取消时 `unbindUserService`；`onBindingDied` / `onNullBinding` 也顺手解绑。
- **`AgentToolHonestyTest` 三条自证式断言**（两次审查点名）：原来自己 `new Date()` 再断言格式、
  自己写常量再断言它等于自己、断言数据类存了构造参数 —— 一条都没调用被测代码。
  现在走 `AgentToolRegistry.executeTool`：时间必须与**当前分钟**一致、天气必须如实声明
  `unavailable_without_sensor_or_api`、空白关键词必须被真实入口挡下、非空关键词不许被误挡。
  这组测试必须跑 Robolectric（工具层用 `org.json`，纯 JVM 下是桩 → `optString` 返回 null 直接 NPE）。

**0.0.0.106 · 特权层会话回收 + 版本行守卫收紧**
- **`PrivilegeManager.release()` 漏回收**（0.0.0.100 审查中危）：以前只解 Shizuku 监听，
  `RootShell` 里那个常驻 `su` 进程与读线程**一次都不会关**（`RootShell.close()` 全仓无调用点），
  自建的 `scope` 也从不 cancel —— `SettingsScreen` / `ConsoleBridge` / `AgentDispatcher` 每 new 一个 Manager
  就留下一个常驻 su 进程 + 一条线程，反复进出设置页会累积授权弹窗。
  现在 `release()` 里 `rootShell.close()` + 只 cancel **自建**的 scope（构造参数改成 `externalScope: CoroutineScope? = null`，
  外部传入的归调用方管）。
- **版本行守卫收紧**：`ContextDocFreshnessGuardTest` 以前只在整篇里 `contains(版本号)` —— §0.0 写对、
  §0.5 停在上一个版本时照样绿。现在分别切 §0.0 与 §0.5 两段，且 **§0.5 只认「- 内部版」那一行**
  （那一段还含全部历史条目，放开会更松）。
  **变异验证**：把 §0.5 的版本行改回 `0.0.0.104`，用例立刻红；改回即绿。

**0.0.0.105 · 子进程输出封顶 + 最后三处裸切片**
- **输出无上限**（0.0.0.100 审查中危）：`ProcessExec.drain` 以前 `reader.readText()` 无上限灌 StringBuilder ——
  `logcat` / `dumpsys` 15 秒能产数十 MB，手表堆只有数百 MB，直接 OOM。现在每路输出按 **256K 字符**封顶，
  超出部分继续读干（不读干的话子进程会被管道写满卡住）但不再入内存。
  回归 `ProcessExecTest` 2 条；**变异验证**：换回 `readText()`，用例报「实际 614699 字符」立刻红。
- **三处裸切片补锚点**（0.0.0.100 审查）：新增 `TestSlices.slice(source, start, end)` ——
  先断言两个锚点存在、顺序正确、切片长度 < 全文一半，再返回。
  `SettingsDotRuleTest`（按下态底色）、`OnboardingPagerTest`（页脚/正文/页面/页码点四处）、
  `SettingsVisualGuardTest`（组标题）全部改走它。

**0.0.0.104 · 补上 0.0.0.103 欠的那条测试（假 su 进程）**
- 0.0.0.103 修了"超时卡死 root"但**没有测试**；本轮补 `FakeSuProcess`（`java.lang.Process` 的假实现）+
  `RootShellTimeoutTest` 2 条：超时后下一条命令仍能跑通、死会话会被丢弃重开。
- 假进程有三处刻意"像真的一样"的细节，否则测不出真问题：
  ① `read()` 覆盖 `read(byte[])` —— 默认实现会一直填满缓冲区，`readLine()` 永远返回不了；
  ② `InterruptedException` **吞掉继续等** —— 真管道读不响应 interrupt，否则 `future.cancel(true)` 会"意外生效"；
  ③ `survivesDestroy` —— 模拟"su 被杀但子孙进程仍握着管道"，这才是卡死的真成因。
- **变异验证**：把 `execute` 换回旧的常驻单线程池，这条用例立刻红（`超时之后 root 必须还能用：timeout after 400ms`）。

**0.0.0.103 · root 会话回收与协议加固（0.0.0.100 审查的三条高/中危）**
- **超时会永久卡死单线程池**：旧实现 `newSingleThreadExecutor` 常驻 + 超时 `future.cancel(true)`，
  而任务阻塞在 `readLine()`（管道读不响应 interrupt）→ 该线程永久占住池子，之后每条命令逐个 15s 超时，
  `isAvailable()` 恒 false，**root 静默降级到 DIRECT**。改成每条命令一个一次性线程；超时即杀进程
  （管道关闭 → 阻塞读立刻返回）后丢弃该线程。
- **死会话被永久复用**：读到 EOF 返回 `root shell closed` 后不清 `live` → su 被撤权/被杀一次后每条命令都是同一句失败。
  现在见到该标记就 `dispose`，下一条命令重新申请。
- **会话错位（两条真实坑）**：`RootShellProtocol.buildCommand` 现在 `</dev/null` 隔离 stdin
  （否则 `cat`/`read`/嵌套 `sh` 会吞掉**下一条命令**）、哨兵前先 `printf '\n'`
  （否则不带尾换行的输出会把哨兵粘成 `hitoken:0`，读循环卡到超时）。
  回归：`RootSessionTest.commandIsIsolatedFromStdinAndTheSentinelCannotStickToOutput`。
- **未覆盖**：`RootShell` 的会话回收没有单测（要伪造 `java.lang.Process`），本轮由编译 + 全量单测兜底。

**0.0.0.102 · MCP 三件收口（0.0.0.100 审查的高/中危）**
- **响应体无上限**（高）：`McpHttpTransport.readBody` 原来 `reader.readText()` 读完整个体 —— 端点回几百 MB
  或每 5s 吐一字节（低于读超时）就能拖死 IO 线程并 OOM（手表堆仅数百 MB）。现在按 64K 字符封顶，
  超限截断并打 `…[truncated]`。
- **取消不断连接**（高）：`cancelCurrentExecution()` 以前只置标志，流式读阻塞在 socket 上不会醒 ——
  点取消后请求仍跑满读超时（默认 40s、可设 180s）。现在持有 `activeConnection` 并在取消时 `disconnect()`；
  顺带把"我们主动断开导致的 IOException"按正常收尾处理（否则用户点取消会看到"网络通信异常"）。
- **MCP 工具不过审批**（高）：`requiresApprovalAlways` 现在把 `mcp_` 前缀的远端工具也纳入 ——
  远端 server 暴露什么能力本地不可知（可能是 shell / 文件系统），而 `assessTool` 对这类名字一律判"安全只读"，
  STANDARD/EXPERT 下会 AUTO_ALLOW，等于把远端能力接到无人值守通道。
  回归：`AgentToolRegistryMcpTest.remoteToolsRequireApprovalLikeAnyPrivilegedTool`（MCP 必批 + 元数据工具不必批 + run_shell 一如既往）。
- **未覆盖**：前两条没有单测（要真起 socket / 真取消流式请求），本轮只有编译与全量单测兜底。

**0.0.0.101 · 供应商档案满额/同名现在真的会拒绝（补 0.0.0.95 没修到的地方）**
- 红队在 0.0.0.100 用一条 POST 证伪了 0.0.0.95 那条修复：`ProviderProfileBook.add()` 在满 5 套或 id 重复时
  **原样返回**，而 `saveWebProviderProfiles` 把它当成功 —— 加第 6 套回 200 + `ok:true`，账本里只有 5 套。
  **上次只改返回码，没改"满额时根本不报错"的上游。**
- 修法：抽出纯函数 `rebuildProviderBookFromWebRows`，每加一行都检查"账本真的变长了"，否则整体返回 null；
  同一份提交里出现同名行也直接拒（名字就是界面上主键）。`ConsoleBridge` 据此回 `REJECTED`，
  而 0.0.0.95 已经把 `rejected` 与 `ignored` 分开、网页会报「保存失败」并保留输入 —— 这条链现在才真正闭合。
- 回归：`ConsoleProviderProfilesTest` 4 条（6 套必拒 / 同名必拒 / 正常载荷仍落库且沿用原 id / 空名与非对象行必拒）。

**0.0.0.100 · 整十发布点（第五次全代码审查 + 审批浮层吃掉手势）**
- **修（高，用户可感知）**：`ApprovalFloatingLayer` 为了"预合成"常驻组合、靠 `graphicsLayer(alpha)` 显隐，
  而 alpha=0 **不影响手势消费** —— 屏幕中部约 190dp 宽的带子把手势全吃掉，正文列表在那一段**竖划完全没反应**。
  修法：滚动容器 `verticalScroll(contentScroll, enabled = visible)`；隐藏时整棵语义树 `clearAndSetSemantics {}`
  （读屏也读不到隐藏的审批文案）。
- 5 路审查（聊天页 UI / 网络与 MCP / 特权层 / 红队对抗 / 文档与守卫）：报告与处置表见
  `docs/REVIEW-0.0.0.100.md`。其中**未修**的高危：MCP `readBody` 无字节上限（OOM）、
  取消不 abort 在途连接（点取消仍跑满读超时）、审批滑轨与主键**没有无障碍语义**（读屏无法完成审批）。

**0.0.0.99 · 控制台三处收口（令牌脱敏 / 500 留痕 / 开关不被弹回）**
- **令牌明文外泄**：`/api/state` 的 `tokenMasked` 用的是 `ConsoleToken.mask()`，而 mask 对 ≤4 位**原样返回**
  （那是给手表本机 UI 看自己访问码用的）—— 于是字段名承诺脱敏、实际把可用访问码写进每个响应体。
  新增 `ConsoleToken.redacted()`（固定 `••••`），对外一律用它；本机 UI 仍用 `mask()`。
  回归：`ConsoleApiTest` 断言 4 位令牌下 `tokenMasked == ••••` 且不含任何令牌片段。
- **500 零日志**：`serve()` 的兜底 catch 以前直接吞掉，真缺陷在真机上没有任何证据。
  现在 `Log.w(TAG, "serve 失败：<method> <uri>", failure)`。
- **开关被响应弹回**：文本/密钥/滑杆/下拉都有"脏键优先于快照"的保护，唯独开关没有 ——
  拨完到落盘之间（300ms 防抖 + 一次往返），任何一次 flush 的响应都会把刚拨的开关弹回旧值。
  现在 switch 的 `sync` 先查 `dirty` 里有没有它的服务端键。

**0.0.0.98 · 请求侧不再送孤儿 tool 回执（整段对话卡 400 的根因）**
- 上游要求每条 `tool` 消息都能找到发起它的 `assistant(tool_calls)`，只要有一条找不到就整单 400，
  而日志里只有一句不相关的错误 —— 表现是**整段对话长期卡在"网络通信异常"**。
- 孤儿有两个来源：上一次请求被取消（回执写一半）、按条数裁剪正好切在"发起 → 返回"中间。
  旧实现一处只补不删（`sanitizeConversationHistory`）、另一处完全不处理（请求侧裸 `takeLast(turns*2)`）。
- 修法：抽出 `ConversationWindow`（纯逻辑，4 条单测）：`sanitizeForRequest` 补缺失回执 + **丢孤儿**；
  `trimRecentTurns` 取最近 N 条并保证不以 TOOL 开头（丢掉开头连续的 TOOL 是安全的：发起方若还在必定排在前面）。
- 顺手踩到一个坑：补齐回执的内容原用 `JSONObject().apply{...}.toString()`，纯 JVM 单测里 `org.json`
  是桩（`isReturnDefaultValues = true`）→ `toString()` 为 null → NPE。改成字符串常量，
  顺带让这条路径不依赖 Android 运行时。

**0.0.0.97 · 安全护栏不再误杀普通文本；上下文轮数加夹取**
- **误杀（用户可感知）**：`SecurityGuardrail.assessTool` 对**整个 argsJson** 做致命命令子串匹配，
  而黑名单里含 `format ` / `wipe ` 这种短词 —— 搜「how to format a disk」、
  记「wipe cache 的步骤」都会被判致命指令硬拦，用户只看到"致命指令已被安全护栏拦截"，
  检索与记忆什么也没做。现在只扫 `command` 字段（run_shell 与自写工具的命令都在这个键），
  真正命令的拦截力不变（`run_shell` 的 `rm -rf /`、自写工具的 `mkfs.ext4` 都仍被硬拦，已各有用例）。
- **`maxContextTurns` 夹取**：控制台字段表下限写的是 0，属性本身不夹 ——存 0 之后 `takeLast(0)`
  **静默丢掉全部历史**，负数/超大值抛 `IllegalArgumentException` 被上层吞成"网络异常"。
  现在 getter/setter 都夹到 `MIN_CONTEXT_TURNS(1)..MAX_CONTEXT_TURNS(200)`，控制台字段表改成同源常量。

**0.0.0.96 · 两处切片守卫补锚点自证 + MCP 超时断言与假服务器解耦**
- `SettingsRootSnapshotTest`：`readBack` 切片原本没有锚点校验 —— 函数一改名 `substringBefore` 就返回整串，
  `missing` 必然为空，守卫永远绿。现在先断言锚点存在、切片长度 < 全文一半、切片里能看到
  `return SettingsRootSnapshot(`。
- `LongTermMemoryWriteTest`：`executeRememberFact` 的切片同理，补两个锚点 + 长度上界。
- `McpToolBindingTest.readTimeoutIsBoundedInsideTheBinding`：原来是"假服务器睡 900ms + 断言 < 900ms"，
  阈值与睡眠同值 —— 有人把睡眠调小，超时没生效也能过。现在睡眠 1200ms、断言 < 700ms
  （读超时 250ms + 450ms 余量），两头都留出了余量。

**0.0.0.95 · 「存储拒绝」与「后端没这个字段」分成两条通道**
- 症状（0.0.0.90 审查）：供应商档案写到第 6 套（上限 5）时，服务端把它塞进 `ignored`，
  网页据此当成"后端还没这字段"的草稿 → `pruneDrafts()` 立刻删掉 → **用户改动静默回滚，界面还提示"已保存"**。
- 修法：`ConsoleWriteResult` 加 `REJECTED`；服务端分 `ignored`（未知/只读）与 `rejected`（存储拒绝）两个数组；
  网页对 `rejected` 报「保存失败：手表拒绝了 N 项」并**保留输入**，不再当草稿处理。
- 回归：`ConsoleApiTest.storageRejectionIsReportedSeparatelyFromIgnored`（一个键 REJECTED、一个键 READ_ONLY、
  一个键 OK，断言三个数组各归各位）。变异验证：把 REJECTED 塞回 ignored，用例立刻红。
- **未做**：这条策略目前只有供应商档案一条真实触发路径；其它键都是"写即成功"。

**0.0.0.94 · 外观单例的用例隔离 + 控制台 stop() 不再打断在跑的请求**
- **测试隔离**：`WearAppearance` 是 Kotlin `object`，Robolectric 同一个 sandbox 里跨类复用 ——
  一个用例把风格切到 `M3_LIST`，下一个用例渲染出来的就是官方件版本，断言却以"找不到节点"的样子红。
  新增 `SettingsAppearanceResetRule`（before/after 都复位 风格/字号/强调色），已接到 4 个会渲染设置页的类上；
  规则自身的行为用 `ExternalResource.apply()` 包一层语句验证，**不依赖用例执行顺序**（2 条）。
- **控制台 `stop()` 顺序**：旧实现先 `shutdownNow()` 再关监听 —— 已开始的 handler 被直接打断，
  `clear-memory` 或一次 40 键批量写会停在一半。现在先关监听 → `shutdown()` + 200ms 有界等待 → 才 `shutdownNow()`。
  回归 `stopDoesNotInterruptAHandlerThatIsAlreadyWriting`：变异验证过（换回旧顺序立刻红）。
  注意这条只保证 **handler 跑完并落盘**；响应能否送到客户端取决于 NanoHTTPd 关连接的时机，不承诺。

**0.0.0.93 · 网页按钮与服务端 action 的契约**
- 审查发现：供应商档案那张卡的「测试连接」按钮打 `connection-test`，而服务端 `ACTION_NAMES` 里没有这个名字
  → **恒 404**，点一次红一次。两边各自自洽，任何单测都不会红。
- 修法：服务端补 `connection-test`（拿当前 Base URL + Key 打一次 `/models`，只报结论不改缓存）。
- 守卫：`ConsoleActionContractTest`（3 条）扫 `app.js` 里的 `actionName: '...'`，与 `ACTION_NAMES` **双向对齐** ——
  网页调了服务端没有的、或服务端登记了网页不用的，都会红。变异验证：摘掉 `connection-test` 后 2 条立刻红。
- E2E 新增 J 组 3 条：真点一次「测试连接」，断言不再是 404、且给出「连接成功/连接失败」明确结论（69 → 72）。
- 顺带修 429 的措辞：网页原来把「输错令牌被临时锁」渲染成「无法连接控制台服务」（还让用户去查网络）。
  现在 `api()` 单独抛 `throttled`，横幅写「尝试次数过多 · 连续输错令牌会临时锁定 60 秒」。

**0.0.0.92 · 补上子代理路径的沙箱闸门**
- 审查（0.0.0.90）发现：`executeSubAgentTool` **没有** `SandboxGate`，而主循环有 ——
  沙箱等级 `READ_ONLY` 时，主循环会挡下 RISKY/DESTRUCTIVE 命令，**子代理照样执行**，等级形同不存在。
- 修法：把主循环那段判定原样搬进子代理路径，且必须排在审批闸门**之前**（等级不允许就不该弹审批页）。
- 回归：`SandboxWiringGuardTest`（2 条源码守卫，带锚点自证与长度上界）——
  分别钉"两条路径都调用 `SandboxGate.blockMessage`"和"沙箱判定在审批之前"。
  **变异验证**：把子代理那段改成 `null`，守卫立刻红。

**0.0.0.91 · 修两处"点了没反应"的 UI 缺陷 + 三条假绿守卫**
- **官方两档子页弹窗全不出来**（高）：`SettingsSubPage` 在 `rowShell == PLAIN` 时早退到 `OfficialListSubPage`，
  而 `overlay` / `overlayBack` 挂在早退之后 —— M3_LIST / M3_TRANSFORM 下改 Base URL、API Key、
  语音密钥、终端输入、提示词、网络体检**点「编辑」毫无反应**，内层页返回还会直接跳回设置根页。
  修法：把 `overlay` / `overlayBack` 传进 `OfficialListSubPage`（浮层压在时钟之上 + 注册 BackHandler）。
  回归：`SettingsModelConfigRobolectricTest.officialListStyleStillOpensTheTextDialog`（删掉修复会红）。
- **触觉强度 / 播报队列点了圆点不动**（高）：`selected` 直接读 `preferences.*`，写存储不触发重组。
  修法：两处都改成 `remember { mutableStateOf(...) }`，回调里先更状态再落盘。
  回归：`threeHapticLevelsEachWriteThePreference` 追加"该行必须变成已选中"断言（删掉修复会红）。
- 顺带把单选行从 `clickable(role = Button)` 换成 `selectable(role = RadioButton)`：
  读屏能念出"单选按钮 / 已选中"，测试也终于能断言选中态。
- **三条假绿守卫修好**（0.0.0.90 审查点名）：`SettingsSubtitleLineGuardTest` 的锚点 `可进入行` 在源文件里不存在
  （切片实为 526 行整段）→ 换成真实锚点 + 切片长度/越界自证；`SettingsStylePolicyTest` 的
  `spec.shell != null` / `back in values()` 被类型系统保证恒真 → 换成逐风格钉死返回控件位置与控件族；
  `SettingsVisualGuardTest` 断言 `slot.contains("trailing")` 而切片起点正是声明该形参的函数 →
  改成钉"有 null 判定 + 真的被调用"。**变异验证**：把旧锚点改回去，7 条用例全红（改之前全绿）。

**0.0.0.90 · 第五次整十审查，修掉三条安全缺陷**
- 按"每 10 个内部版派 ≥5 个子智能体做全代码审查"跑了 5 路（设置页 / 控制台 / agent 核心 / 测试有效性 / 文档一致性），
  报告与处置表落在 `docs/REVIEW-0.0.0.90.md`（1 路失败，已补进下一轮计划）。
- **修①（高）**：`ShellPolicy` 的 `normalize` 把 `\n` 折成空格，`ls\nreboot -p` 归一化后只剩首词 → 判 SAFE，
  而 `sh -c` 会把两条都跑掉；现在按原始串的命令分隔符拆开逐条判、取最坏档。
- **修②（高）**：`rm` 只检查第一个目标，`rm -rf /sdcard/Download /data` 里的 `/data` 从未被查 → 只判 RISKY（可被记住）。
  现在每个非选项词都查，选项与目标任意排序也成立。
- **修③（高）**：控制台把 NanoHTTPD 的 `postData`（其实是**正文文本**）当文件路径读取并 `delete()`，
  且发生在鉴权之前 → 无令牌 POST 一个真实路径就能删任意文件。整段删除，正文按文本处理。
- **修④（高）**：审批回调缺失时默认放行（`?: true`），而 ViewModel `onCleared` 会把它置空 →
  回收瞬间排队的审批点自动通过。改成默认拒绝。
- 文档同步：HANDOFF §0/§0.5 全表重写（旧版 5 条已完成）、CONTEXT 两处写死的旧基线、
  M3_TRANSFORM"行转场 ✅"改成"未接线"、边缘缩放"0.70/0.50"改成"只淡出不缩放"、发布自检 7 项 → 8 条。

**0.0.0.89 · 网络/采样参数/模型端点三页进 JVM 回归**
- `SettingsNetworkAndHyperRobolectricTest`（6 条）：超时 55.4→55、重试 2.4→2、退避 3300→3400ms；
  top_p 0.63→0.65、最大 token 走台阶（拨第 3 级 → `TokenLadder[3]`）、空转止损 5.6→6。
- `SettingsModelConfigRobolectricTest`（4 条）：Base URL 弹层确认落盘 / **取消一个字不写**；
  API Key 弹层确认落盘；已有密钥时**屏上不出现原始密钥**（只出现 `maskSecret` 掩码）。
- 顺手把三处重复的 `setSlider` 收进 `SettingsUi`（测试辅助，不是生产代码）。
- **查到但没动的一件**：Wear M3 1.7.1 的 `Card` **只有带 onClick 的版本**（`javap` 实测签名），
  于是每张卡组都是一个"点了没反应"的 `android.widget.Button` 无障碍节点（真机 dump 可见）。
  两条路各有代价，写进 `docs/DECISIONS-PENDING.md` D5 等用户拍板，不擅动。

**0.0.0.88 · 设置页功能回归进 JVM + 修掉一个无障碍真缺陷**
- 新 4 套 Robolectric（真渲染 composable、点语义节点、读 `AgentPreferences` 真值）：
  外观深水区（触觉 3 档 / 字号 3 档 / 强调色 5 档 / 三条滑杆）、手势与侧键（四段各点一个动作）、
  供应商档案（切档案换模型、复制与删除）、导入导出（导出→改→导入 复原、垃圾输入一个字不写）。
- **查出真缺陷**：`SettingsThinSlider` 自绘轨道没有任何无障碍语义；补语义时又发现卡组外层
  `Card(onClick = {})` 会 `mergeDescendants`，把**两行滑杆并成一个节点**（实测区间取的是邻居的，
  第二个滑杆的 `SetProgress` 直接丢失 = 读屏拨不动）。修法：滑杆行自设空语义边界 + 轨道补
  `contentDescription` / `ProgressBarRangeInfo` / `SetProgress`；三个滑杆测试都断言"区间必须是它自己的"。
- `release-check.mjs` 增加第 4 项：控制台页面级 E2E 自动跑（令牌用 `su -c cat console_prefs.xml` 读，不用设环境变量）。
  实测 `--skip-build --skip-device` 直接 **69/69**。

**0.0.0.48 · 4 页标题与行名统一 + 会话看门狗（真机已验证）**
- 4 页子页标题改成与根页行名一致（模型与服务 / 模型参数 / 语音与唤醒 / 底栏副键），中英成对改。
- 新增守卫 `SettingsPageTitleMatchesRowTest`（中英各一条），**实测会红**：
  把 hyper_params 改回「推理参数」→ `chinesePageTitlesEqualTheirRootRows FAILED`，恢复后绿。
- 巡检判据收紧成「标题必须等于行名」，并加 `waitForTitle` 轮询 —— 点完立刻 dump 会读到
  动画中间帧（「语音与唤醒」被读成「启用语音识别」），那是取样太早，不是页面坏。
- 新增 `docs/tools/session-watchdog.ps1`：看宿主进程 / 会话文件增长 / GUI 探活。
  **GUI 未鉴权返回 401 = 服务在听，算活着**（`curl` 实测）。
**0.0.0.47 · 设置页 15 页全量巡检（真机已验证，报告 `docs/SETTINGS-SWEEP.md`）**
- `docs/tools/sweep-pages.mjs`：每页判据 = **能打开 + 能返回根页**，结果落
  `build-logs/sweep-0.0.0.47.jsonl`。**15 / 15 打开，15 / 15 返回。**
- **查出真问题**：4 页子页标题 ≠ 根页行名（模型与服务→模型与端点、模型参数→推理参数、
  语音与唤醒→语音输入、底栏副键→手势与侧键），破坏 `docs/SETTINGS-IA.md` §4 的
  「页面标题 = 根页行标题」。修法是字符串成对改 + 同步 `SettingsIaDocNamingGuardTest`。
- 自动化的四个坑（步长 1 格≈1/8 行、找不到≠在下面、别等坐标稳定、返回后要先回顶）
  都写进脚本注释与 CHANGELOG，别再踩第二遍。
- **未收口**：第 2 页「模型参数」在整套自动化里偶发点空（手动 probe 正常，标题 `推理参数`）——
  脚本落点问题，不是页面问题；下轮改成"点完验证，没打开同坐标重试一次"。

**0.0.0.46 · 设置页去「假 M3E」第 2 步（真机已验证）**
- **整页手搓超椭圆 → 官方 `Card` 分组卡**。旧实现 `SettingsBalloonGroup` 用 `SettingsCurveGeometry`
  自己算屏幕弦长、把整页（连分区标题）裹进一张闭合超椭圆里；圆角、色调、涟漪全是自绘 ——
  这是"像 M3E 但就是不对"最大的一块。
- 分组规则改在 `SettingsPageChunking`：**连续的 CARD_ROW 拼一张卡，分区标题与卡外行动各自断开**。
  原来"一页只用一张主气球"的规则连同它的守卫断言一起改掉（`SettingsPageChunkingTest` 现在钉新规则）。
- 卡片宽度 = `SettingsContentWidth` 176dp = **实测 352px**，与卡片档 hero 卡同一把尺子、符合硬约束。
- 组内行不再各自按弦长缩放（那会让卡内文字左右不齐）：缩放责任下沉给卡（`CardEdgeZoom`，本项目
  `MinScale = 1.00f`，即只淡出不缩放）。`SettingsDotRuleTest` 的断言按新契约改写。
- 根页四个分区拆成 四张卡 + 四个小标题，外层补了水平居中（否则小标题被卡片圆角压住）。
- 证据：`docs/device/v11/cardgroup-root-top.png`、`cardgroup-root-scrolled.png`、`cardgroup-subpage.png`。

**0.0.0.49 · 全代码审查（第一次按新规派 5 个子智能体）+ 修 P0 与导航缺陷**
- 报告：`docs/ARCH-AUDIT-0.0.0.49.md`。基线：153 文件 / ~30876 行 / 17 包；
  测试 1295 条 / 189 套件；`presentation.ui.settings` 占 29%（9038 行）；8 个上帝文件占全项目 30%。
- **P0 已修**：`isMinifyEnabled = true` 但从未接 `proguardFiles` —— 1377 字节的 keep 规则从没生效。
- **驳回一条**：两处 `ui-tooling` 是 wear 与 androidx 两件不同的包，不是重复。
- **实测撤回一条**：`maxParallelForks=4` 50.3s vs 1 fork 51.3s（差 2%，噪声内）；瓶颈是编译。按简洁优先不加。
- **导航缺陷已修并真机验证**：`网络体检` 返回键直接跳根页 → 现在回父页。新增 `overlayBack` 钩子 + 守卫测试。
- 审查列出的重复控件 13 组、性能热点 13 处、死代码 52 个“仅单测引用”声明，全部落进报告，按优先级排好。

**0.0.0.51 · 拆上帝文件第一步 + 行组件去重（真机冒烟通过）**
- 触觉三件套搬到 `SettingsHaptics.kt`（56 行）；`SettingsShared.kt` 2236 → 2203 行。
- `SettingsSplitToggleRow` / `SettingsSubRadioRow` 从"复制函数体"改成"一次委托"，各去掉一段重复实现。
- 纠正假注释：SplitToggleRow 注释说走官方 `TextToggleButton`，实际是行尾手搓开关（官方那个整宽会吞右槽）。
- `HapticAmplitudeTest` 改读新文件，并加一条"触觉不许散落回 SettingsShared"。
**0.0.0.52 · 拆上帝文件第二步：滑杆搬家**
- `SettingsSliderRow` + `SettingsThinSlider` + 滑轨常量 → `SettingsSliderRow.kt`（194 行）。
  `SettingsShared.kt` 2203 → **2043 行**。
- 三个辅助放宽为 `internal`；`SettingsVisualGuardTest` 的强调色守卫改成"允许两个文件、总数仍 6"。
- 真机冒烟：网络与请求页滑杆渲染正常（`docs/device/v11/slider-smoke.png`）。
**0.0.0.53 · 拆上帝文件第三步：开关族搬家**
- `SettingsSwitchRow` / `SettingsCompactSwitch` / `SettingsSplitToggleRow` → `SettingsSwitchRow.kt`（115 行）。
  `SettingsShared.kt` 2043 → **1959 行**（三轮累计 −277）。
- `SettingsSlotRow` 放宽为 `internal`；删掉三个只在注释里出现的 import。
- 真机：工具与 MCP 页开关正常（`docs/device/v11/switch-smoke.png`）。
- **新发现的密度问题（待用户拍板）**：行内 148dp − 开关 44dp − 间隙 10dp = 左槽 94dp ≈ 188px，
  7 个汉字（≈105dp）必折行。三个选项：①开关缩到 40dp ②标签字号降到 14sp ③允许两行但把说明行让出来。
**0.0.0.54 · 拆上帝文件第四步 + 清冗余图标**
- `SettingsNavRow` + 四个排版令牌 + `SettingsStatusBlock` → `SettingsRowText.kt`（122 行）。
  `SettingsShared.kt` 1959 → **1866 行**（四轮累计 −370）。
- 删 10 个不会被选中的启动图标 webp（33 KB），APK 4.13 → 4.10 MB。
- 强调色守卫从"钉文件名"改成"钉规则"（页面文件 0 处 + 全站总 6 处），拆文件不再误报。
- 真机冒烟：根页/子页/时钟/卡片正常。
**0.0.0.55 · 拆上帝文件第五步 + 修两条假绿守卫**
- 单选族 → `SettingsRadioRow.kt`（133 行）；`SettingsShared.kt` 1866 → **1754 行**（五轮累计 −482）。
- `SettingsSubtitleLineGuardTest` 锚点去掉 `private `：它俩已改 `internal`，
  而 `substringAfter` 找不到会返回整文件 → 断言恒真（假绿）。现在重新有牙。
- 性能实测（gfxinfo，根页来回滚 8 趟）：1399 帧 / 11 卡顿 0.79% / p90 18ms / p99 20ms / slow bitmap 0。
  **结论：不做"拆巨型 lazy item"那刀** —— 没有数据支撑。
- 真机冒烟正常。
**0.0.0.56 · UI 第一优先：四套风格的槽位表**
- 用户判定风格"大杂烩"，要求每套独立自洽。**实测只有 4 套**（气泡/方角/大卡片/终端），
  文档写"5 种"是错数；病根是 `WearAppearance.style ==` 散在 10 处。
- 新增 `SettingsStylePolicy`（8 槽位声明）+ `SettingsStylePolicyTest`（5 条守卫）；
  已接线 2 处（子页 backInList、终端等宽）。
- 计划/验收口径：`docs/UI-STYLE-PLAN.md`。

**0.0.0.58 · 风格扩到 6 套（可选中，两套待接线）**
- 新增 `M3_LIST` / `M3_TRANSFORM` 枚举 + 中英字符串 + 槽位表两行；真机外观页已列 6 项
  （`docs/device/v11/styles-6-list.png`）。
- **未接线**：`SettingsStyleM3List.kt` 0 调用点，选 5/6 仍走旧渲染；守卫里
  `notYetWired` 显式放行，只许变短。
- 测试同步到 6 套：1305 条 / 191 套件 / 0 红 / 1 skipped。
**0.0.0.87 · 控制台页面级 E2E（真浏览器）+ 两个真缺陷（代码与单测已验证，装机待 USB 恢复）**
- 新工具 `docs/tools/console-e2e.mjs`：headless Edge + CDP 驱动真页面，**断 DOM 和抓到的请求**。
  做法先在 `Page.addScriptToEvaluateOnNewDocument` 里包一层 `window.fetch`，之后"有没有发请求""发了什么"都是抓到的证据。
  覆盖 A–I 九组：认证闸门 / 令牌弹层 / 十个页面 / 搜索跳转 / 非法 JSON 拦截 / 文本域写回 / 滑杆写回 / 三档皮肤 / 零 JS 异常。
- **查出的缺陷 1（严重，会毁数据）**：`Content-Type: application/json` 不带 charset 时，NanoHTTPD 按 US-ASCII 解 body，
  网页里输入的中文被逐字节换成 U+FFFD **再落库，不可逆**（写「中文探针-ABC」，读回来 12 个 `�`）。
  修法：`ConsoleCharset.withUtf8()` + `ConsoleServer.forceUtf8Charset()` 在 `parseBody` 前补 charset=utf-8 并即时还原请求头；
  网页端同时显式声明 `charset=utf-8`。守卫 `ConsoleCharsetTest` 5 条 + E2E 的 F3（中文原样往返）。
- **查出的缺陷 2（小）**：`/api/state` 的 `capabilities` 没有设备名字段，网页顶栏永远是「当前设备」。
  现在 `ConsoleBridge` 把 `Build.MODEL` 带进能力表，顶栏实测显示 `TicWatch Pro 5`。
- 顺带纠正上一轮的判断错误：`customHeaders` / `requestHeaders` **不是服务端键**，网页把它们存本地草稿并发「只保存在本地」横幅；
  我原来写的"非法 JSON 没写回"是恒真断言（两边都取 `{}`），现在改成"记录到的 POST 数为 0"。
- **已补真机验证（23:20）**：装 `0.0.0.87` 后，不带 charset 写「中文探针-ABC」能原样读回、`deviceName = TicWatch Pro 5`、
  控制台 E2E **69/69**。同轮还揪出并修掉第三个缺陷：保存偶发 9 秒超时（详见 §0.0.5 与 `docs/ACCEPTANCE-MATRIX.md` §2.9）。
- **设备掉线期间的替代产物：JVM 上跑真 Compose**（Robolectric 4.18.1）。`SettingsPagesRobolectricTest` 直接渲染
  「网络与请求」「模型参数」「语音转写」三页真 composable，点语义节点、读 `AgentPreferences` 真值：
  仅 WiFi 请求 / 触觉反馈 / 开启 SSE 流式 / 极速 0.2 预设 / 语音转写开关 —— **5 个键的功能实测从"只有真机能验"变成 JVM 上 5 秒跑完**。
  另加 `SettingsSwitchRowRobolectricTest` 3 条（文案、点击取反、无障碍状态描述）与 `WearComposeSmokeTest` 1 条探针。
  单测基线随之 1314 → **1327 条 / 198 套件 / 0 失败**；覆盖 15 个设置键 + 六套风格 + 三档安全模式（明细见 `docs/SETTINGS-FUNCTIONAL-TEST.md` §〇）。

**下一步（下一轮直接从这里开始）· 先补真机，再继续 UI**
1. **插回手表 USB**（✔ 23:05 已恢复）→ 装 `0.0.0.87` → 跑 `node docs/tools/console-e2e.mjs`（✔ 69/69 通过）。
   下轮第一件事改成：把控制台 E2E 接进 `release-check.mjs` 当第 8 项，避免它再退化成"只有想起来才跑"。
2. 设置页功能实测剩下三页（模型参数 / 网络与请求 / 语音与唤醒）改用 instrumentation 节点点击，别数表冠格数。
3. 剩下 8 处 `style ==` 逐个改成读表（每处一跑单测 + 真机）。
4. 大卡片档（M3E 基准）全链路换成官方件：开关 → `SwitchButton`、滑杆 → `Slider`、
   单选 → `RadioButton`、返回 → 列表首项（已是）。**这一步会动像素，先出对比截图给用户看。**
5. 收口气泡/方角/终端三套，各写一节"风格说明书"进 `UI-STYLE-PLAN.md`（6 套已全部接线，见 §0.0.2 总表）。

1. 按审查报告 §5 动手砍：先删死分支（不可达的超椭圆外壳 + `cardPath` + `RootSectionRow`，约 290 行，零风险）。
2. 再合并行组件四份、卡壳三份、分隔线三份（约 185 行，可见一致性收益）。
3. 0.0.0.50 发布点：跑 `node docs/tools/release-check.mjs`，7/7 通过后打 tag。
1. 修 4 页标题与行名不一致（字符串成对改 + `SettingsIaDocNamingGuardTest` 同步），把巡检脚本的
   "点空重试"补上，让 15/15 稳定复现。
2. 去「假 M3E」第 3 步：`SettingsThinSlider` / `SettingsCompactSwitch` / 手绘 radio 换成官方
   `Slider(segmented)` / `SwitchButton` / `RadioButton` —— **注意**：上一任在代码注释里写了
   "为什么不用官方 Slider（52dp 分段胶囊铺满强调色太重）"，这是审美取舍，动手前把两版截图摆给用户看。
3. 第 4 步：分区标题那根手绘强调色短横（"沟里的短横"）与 `> ` 前缀，换成官方 `ListSubHeader` 语义。
4. 密度：外观页每个风格选项占 145px（标签 + 两行说明），一屏只放 1.5 个 —— 说明压到一行。

**已完成（别再重做）**
- 卡片粘连：根因是 `RootContentRow`/`SettingsStaggeredRow` 内层为 **Box**，兄弟 `Spacer` 会叠放不产生间距；改为卡片自身 `padding(bottom)`。
- 卡片宽度：**一律 352px / 176dp**，边缘缩放恒 1.0、只淡出（0.45）。
- 品牌图：10 家官方 SVG；点阵角色已删除；`BrandAssetsTest` 钉住"文件必须不存在"。
- 指示器：官方 `CircularProgressIndicator`，每卡一个（没数据也画轨道）。
- 返回控件：卡片档子页的返回是**列表首项**，跟着列表滚；下拉退出手势常驻。
- 信息架构：四意图分区 + 15 行（S1 4 / S2 3 / S3 4 / S4 4）；「网络与请求」页；网页体检改挂；
  **底栏副键与特权执行已从「关于」内层提升为一级**（关于现在是单页）。
- 真实设置：**20 / 20**，每项都有单一消费点 + 落盘守门；20 个滑杆全部带"这个数字改变什么"的说明行。
- Lux 终端：v1 快捷命令 → v2 自由输入 → v3 历史 + 清屏 → v4 快捷位；真机验证"保存 → 落盘"链路。
- 网页控制台：10 页 / 63 行；接口 200 / 无令牌 401 / POST 写入通过；三档皮肤真浏览器截图验证。
- 主气球品牌竖线、连续列表（相邻助手回复共享一个面）、官方 `MotionScheme.standard()` 都已落地。

**P0 / P1 / P2 / P3 —— 全部收口**
- P0：卡片重合 ✅ / 返回控件 ✅ / 字体"过扁" ✅（审计完成：主题行高 1.25–1.33 比 Material 1.43 紧，
  但直接抬到 1.42 会打破 `ApprovalBandMetricsTest` 的几何推导，**改的正确顺序写在 `docs/DESIGN-SPEC.md`**）。
- P1：触觉语义 ✅（官方 primitive + 三档强度）、圆环规则 ✅（每卡一个）、
  **验收矩阵 ✅**（表冠 × 三条滚动实现、触摸拖拽/fling/下拉退出，见 `docs/ACCEPTANCE-MATRIX.md` §2.5–2.7）。
- P2：`MotionScheme` ✅、品牌视觉重心 ✅、连续列表 ✅。
- P3：控制台 ✅（验收 + 键表守卫）、设置风格 **6 套** ✅、20 设置 ✅、Lux 终端 ✅。

**结构性守卫（16 条，改代码前先看这里）**
过冲 / 死文案 / 说明行 / 文档命名 / 网页键表漂移 / 品牌资产 / 设置覆盖 / 结构守卫 / 落盘守门 / 验收矩阵 …

**上下文插件（fact_register）—— 用户 2026-11-15 装的"自己管上下文"能力**

实测结论（第 53 轮）：
- **能用的**：`fact_register({fact, tag})` 钉住一条 ≤160 字的事实，它会随每一步的工作上下文重新投影；
  `fact_register({revoke: tag})` 按标签整组撤销。两者都实测通过（pin 4 条真事实 + 9 条容量测试，按 tag 撤销两组）。
- **上限 12 条**，超出丢最旧的（文档行为；我无法直接观测到自己那行，只能信文档 + 用 tag 管理）。
- **没有的**：本会话没有分页工具命名空间（`tool_activate` 返回 `paged namespaces: (none)`），
  也没有"手动触发压缩 / 查看上下文占用"的工具 —— 压缩是 harness 自动的，我触发不了。
- **压缩确实存在**：本轮会话开头的 checkpoint 消息就是证据（更早的一段被压缩成了摘要）。

**我现在的用法（每轮开工顺手做）**：
1. 先读本文件（唯一记忆源），2. 核对 `version` / `baseline` 两条事实是否还准，不准就重新 pin，
3. 每轮结束前把"下一轮第一件事"写进下面这一节（比 pin 更耐久，因为它在磁盘上）。
**两条会反复咬人的规则**
1. **设置页 DSL**：行在**收集期**注册（`SettingsSubPage` 调一次 `registry.rows()`），
   **不能用 `if (状态) { row {} }` 增删行**；总是注册行，把状态读取放进行内容的 lambda，或用 `enabled =`。
2. **文档名必须等于界面名**：`SettingsIaDocNamingGuardTest` 钉住 19 个页面/分区的界面真实文案；
   写文档时**先 grep 界面文案再落笔**。

**环境坑（每次都会踩）**
- **Robolectric 已在用（0.0.0.87 起）**：设置页的"点了有没有反应"可以在 JVM 上断，不必等手表。
  配方：`testOptions.unitTests.isIncludeAndroidResources = true`；测试类上 `@RunWith(RobolectricTestRunner::class)`
  `@Config(sdk = [35], qualifiers = "zh-rCN")`（不写 qualifiers 断言中文会全红，默认是 en-US）。
  `android-all-instrumented` 的 197MB jar **不在仓库里**，放在 `D:\gradle-home\robolectric-deps\`
  （`build.gradle.kts` 里 `robolectric.offline=true` 指过去）；换机器/清缓存后要重新放，否则报 `Path is not a file`。
  两个坑：①`performScrollToNode` 会撞上两个可滚动节点 → 用 `hasScrollAction() and keyIsDefined(CollectionInfo)`
  取列表那个；②同一句文案可能既是分区标题又是行标签（「语音转写开关」）→ `filterToOne(hasClickAction())`。
- 无 shell 工具：一切经 `run_code` + `node:child_process`；gradle 必须给全 env。
- 表冠注入走 `su`，每次都弹 Magisk toast：**把连续滚动合并成一次 `su` 调用，等 6 秒再截图**。
- `git push` 常断：`-c http.curloptResolve=github.com:443:<ip>`，轮换 140.82.112/113/114/116.4。
- 批量改代码的四个坑：`git add -A` 会扫进子代理半成品；常量插到行尾会合并成一行；抽多行块时别假设单行；
  **`if` 在收集期求值**（见上）。
- 自动化导航"卡住"的真相（第 52 轮查明）：**点击落到了「输入命令…」这类行上，弹出对话框盖住整页**，
  dump 里全是弹层节点，看起来像"页面滚不动"。**不是焦点丢失**（我上一轮写错了，已改正）。
  解药：每次点击后检查是否出现 `取消/完成`，出现就 `keyevent 4` 关掉；工具在 `docs/tools/nav.mjs`（`tapRow` 自带这个自检）。
- 网页控制台截图：`.mjs` 不能用 `require`；headless Edge 用 `start /B` 拉起会被杀掉，
  **必须"启动浏览器 + 截图"在同一个进程里**（`docs/tools/console-shot.mjs` 已封装）。

**发布自检（一条命令）**

    node docs/tools/release-check.mjs

会跑 4 段共 **8 条判据**：clean 构建 + 全量单测 + APK 产出 + 真机安装 + 进入设置页 + 四分区按序 +
卡片几何采样 + 控制台页面 E2E，全过才 exit 0。
可 `--skip-build` / `--skip-device` / `--skip-console` 分段跑。

**下一步（下一轮直接从这里开始）· 2026-11-17 更新**

1. **设置页全量巡检**：`build-logs/sweep2.mjs` + `docs/tools/nav.mjs`，每批 5 页分次跑，判据"能打开 + 能返回根页"（上次跑超时中断）。
2. **0.0.0.50 发布点**：还差 6 个内部版；到点跑 `node docs/tools/release-check.mjs`，7/7 后打 tag。
3. 可选：行高 1.42 的版式手术（**必须先问用户**，顺序见 `docs/DESIGN-SPEC.md`）、控制台页面内容扩充。

完整欠账清单（9 条，含验证方式）在 `docs/HANDOFF.md` §0.5。

## 0.6 上下文自管理（怎么用、边界在哪）

**已安装的插件：`@local/context-governor` v1.0.0**（2026-11-15 用户装）
- 位置：`~/.dsh/profiles/desktop/node_modules/@local/context-governor`，已进 profile 的 `dsh.profile.bundles`，
  自带 `cordis.patch.yml` 会把 `context-governor` 插进组合 —— 接线正确。
- 它注册一个 **`manage_context`** 工具，9 个 action：`view` / `usage` / `compact` / `set_persona` /
  `reset_persona` / `set_section` / `remove_section` / `set_variable` / `remove_variable` / `rollback`。
- 守卫：不可变内核提示、命名空间隔离（只能改人设覆盖/`governor:custom:*`/`governor_*` 变量）、
  作用域只在调用者 agent、输入限额、写前快照可回滚、**会话级不持久**。
- **本会话拿不到它**：`tools.manage_context` 不存在，`tool_activate` 回 `paged namespaces: (none)`。
  原因是工具面在会话启动时固定，而本会话是**恢复**出来的，早于插件安装。**需要开新会话**才会出现。
- 新会话第一次用它时按这个顺序验：`usage`（看占用）→ `view`（看人设/片段/变量）→ `compact`（压缩历史）。

**没有插件时也能做的两件事**（本会话实测）：
- `fact_register`：钉 ≤160 字的事实，随每步工作上下文投影，压缩也吃不掉；`revoke` 按 tag 撤销。
- 量自己的上下文压力：`~/.dsh/sessions/<workspace>/<session>/session.v4.jsonl.zstd` 的大小就是压力表 ——
  2026-11-15 22:07 本会话是 **38 MB / 163k 行**，这就是压缩一直在触发的原因。


**结论先说：现在我有 `manage_context` 了（2026-11-17 实测通过），但它压不动。**
实测矩阵（2026-11-17 00:55，全部真调用）：

| action | 结果 |
|---|---|
| `usage` | ✅ `~779412 routed tokens / 1339 surface nodes` |
| `view` | ✅ 列出人设 / 片段 / 变量 / 回滚快照数 |
| `set_variable` / `remove_variable` | ✅ 变量以 `{{governor_<名>}}` 引用 |
| `set_persona` / `reset_persona` | ✅ 覆盖人设；`rollback` 也能撤销 |
| `set_section` | ✅ 自定义片段可增删；保留名被拒（`harness:identity` → 正则拒绝，不崩） |
| `rollback` | ✅ 撤销一次编辑，快照数 2→1，人设回到部署默认 |
| **`compact`** | ❌ `compaction backend is not mounted in this composition; use the /compact command instead` |

**要压缩只能让用户发 `/compact` 命令**：梁神预设把 `compaction` 隔离在它自己的 agent realm 里，
插件从 `exec.agent.ctx.get("compaction")` 拿不到。

**插件状态是会话级**：中途 profile 重载后我设过的 persona / section / variable 全部清空（符合设计）。
装新插件会让**运行中的会话**短暂丢掉 `manage_context`（报 "unknown tool"），等重载完自动恢复；
但新装的插件本身不会进当前会话的工具面 —— **必须开新会话**。

**所以我的职责不是"按下压缩键"，而是让压缩无损：**

1. **唯一记忆源**：`docs/CONTEXT.md`。压缩后不能丢的东西必须落在这里，不留在聊天里。
2. **钉住不变量**：`fact_register` 最多 12 条，压缩后仍在眼前。
3. **控制工作集**：先 `grep` 定位再 `read` 窄区间；大日志只取尾部；不重复读同一文件。
4. **阶段边界主动总结**：一个任务收口就写进 CONTEXT / CHANGELOG，再开下一个任务。
5. **每轮自检**：三句话回答"硬约束是什么 / 当前 P0 是什么 / 版本号到哪"，答案必须来自仓库文件。
6. **想要更快的压缩**：开新会话即可 —— 只要 `docs/CONTEXT.md` 在，新会话直接接上。
> 我的行事准则写在 `docs/SELF.md`（用户可改）。开工先读它，再读下面。

## 1. 硬约束（违反即返工）

- **禁止任何横向拉伸/横向形变**；动画**只允许缩小**（等比，`scaleX == scaleY`）。
- M3E 卡片**不得相互重合**：静态卡间距 8dp，实测每张卡独立、间隙 16px@2x。
- 默认气泡档的像素要守住：改它之前先问用户（渐变实验已备好，见 `docs/PERF-0.05.md`）。
- 设置页行文案始终"左值右值两槽"（有守卫测试）；调色板上限 14 token。
- 所有交付必须**真机验证**，不能用"编译通过"代替交互验收。

## 2. 版本与发布节奏（用户 2026-11-14 新规）

- 内部版本四位：`0.0.0.x`，每次内部更新**只递增最低位**。
- 每累计 **10** 个内部版本，才做一次对外发布（GitHub release + 控制台说明 + 完整回归）。
- 每 **100** 个内部版本做一次信息架构 / 视觉系统复审；之后继续，不停工。
- 历史：0.04→0.11-beta 是旧的三位节奏（已发布 7 个），从 `0.0.0.1` 起切到新规。

## 3. 已定决策（不要再问第二遍）

| 主题 | 决定 |
|---|---|
| 大卡片档 | 官方 Wear M3 `Card`，一行一张；根页每卡一个 lazy item；不开 autoCentering |
| 边缘缩放 | `CardEdgeZoom`：屏心 1.0，**`MinScale = 1.00f` 即只淡出不缩放**（alpha 走官方曲线）；库的 scalingParams 保持平坦。若要真缩到 0.70 得先解开与 §1「禁止横向形变」的冲突，属未做 |
| 品牌识别 | `BrandId.fromEndpoint`：host 优先、模型名兜底、都不中 → `UNKNOWN` 统一兜底 |
| 两套资产 | 品牌矢量 `brand_*_mark`（res/drawable）与点阵拟人 `BrandCharacters`（代码内位图）互不复用 |
| 页标题 | 卡片档里标题与 hero 标签同句时**不画标题**（`SettingsTextLayout.shouldRenderPageHeader`） |
| 系统卡 | 根页「系统」hero 报 `BuildConfig.VERSION_NAME` |
| 命名对照（2026-11-15） | 界面「网页控制台」= 枚举 `REMOTE_CONSOLE`；文档与提交信息一律用界面名 |
| 命名对照（2026-11-15） | 界面「网页控制台」= 枚举 `REMOTE_CONSOLE`；文档与提交信息一律用界面名 |
| 圆环规则（用户 01:15 拍板） | **每张卡都有环**；没数据也画轨道（"就算没有，也得在旁边塞点东西"） |
| 返回控件（用户 01:15 拍板） | 选 **②跟着列表滚走**（返回钮进列表内容，滚下去就滚出去；下拉退出手势仍常驻） |
| 字体过扁 | 用户 01:15 表示**现在没有**这个问题，本条关闭 |
| 设置 IA 三问（子代理 A1-A3） | A1 取 4 分区（不改结构守卫）；A2 新增 20 项仅本机（PortableKeys 仍 40）；A3 不开放给控制台（计数不变） |

## 4. 当前阶段与待办

> **2026-11-19 更正**：下面这一整节是 0.0.0.4x 时期的历史待办，其中 12 条已经收口（返回控件已改成"跟着列表滚"、
> 字体问题用户已关闭、圆环规则已拍板、验收矩阵已落盘 §2.5–2.7、控制台 10 页 63 行、风格已 6 套、设置项 20/20、Lux 终端已上线）。
> 只有**行高版式手术**和**验收矩阵三行**还活着，见 `docs/HANDOFF.md` §0.5 与 `docs/DECISIONS-PENDING.md`。
> 保留原文是为了让后来人看到"当时是怎么想的"，**不要再照着勾**。

**P0（正确性，优先）**
- [ ] M3E 卡片"相互重合"：静态实测无重叠（间隙 16px@2x），**待用户指出页面/条件**；疑点是入场动画期间的行位移
- [ ] 返回控件不随列表滚动
- [ ] 设置页字体"过扁"

**P1（验收与规则）**
- [ ] 页面 × 输入方式（表冠 / 手势 / 磁滑）× 预期结果 的验收矩阵，覆盖列表、子页、对话、审批、控制台
- [ ] 统一圆环指示器规则（**待用户拍板**：每卡至少一个 vs 最多一个）
- [ ] 采用官方 M3 Expressive 触觉反馈语义（Motion Physics / 触觉章节）

**P2（视觉系统）**
- [ ] 连续列表 / 主气球重构；品牌图与视觉重心
- [ ] 把 `docs/DESIGN-SPEC.md` 落到四套风格上（Claude/高端 Agent 视觉体系，**"cloud"语境待确认**）

**P3（功能）**
- [ ] 完善手机控制台
- [ ] 设置页扩到 **5** 种风格
- [ ] 新增 **20** 个设置子页 + 20 个真实可调、可持久化、被业务消费的设置项（禁止空页/无效开关）
- [ ] 极轻量 "Lux" 终端（形态先调研再定）

## 5. 已知环境坑（省时间）

- 没有 shell 工具：一律 `run_code` + `node:child_process.spawnSync`；gradle 必须显式给全 env（见 HANDOFF）。
- `adb` 的 server/forward 会掉：每次重新 `start-server` + `forward`。
- 进设置页：聊天页从底部上滑（列表长时要多滑几次到底再上滑）。
- `git push` 常被 reset：`-c http.curloptResolve=github.com:443:<ip>`，ip 轮换（113/112/114/116/121）。
- 抓网页：`node docs/tools/read-url.mjs <url> [chars]`（headless Edge + CDP，JS 站也能读）；
  非 JS 站可直接 `curl --ssl-no-revoke`。**developer.android.com 与 *.appspot.com 在本机网络不可达**。
- 批量脚本改代码后必须用**运行时值**验证；过滤行不要用会撞到合法标识符的子串（踩过：`SubPageHeader` 撞 `SubPageHeaderTopPadding`）。

## 0.0.1 压缩后恢复卡（2026-11-18，写给下一个我）

**当前状态**：内部版 `0.0.0.68`；测试 1305 条 / 191 套件 / 0 红。
**主线**：设置页 6 套风格，其中官方两套（M3_LIST / M3_TRANSFORM）已经走 `TransformingLazyColumn`
+ 官方 `SplitSwitchButton` / `RadioButton` / `Slider`，其余四套仍是旧实现 + 槽位表声明。

**三个已知卡点（别重复试错）**
1. ~~`HAIRLINE_FLAT` 不套卡的分支没生效~~ **已修好并真机验证（0.0.0.70）**；真相是我当时看错了风格。原记录：：给 `SettingsGroupCard` 加了非卡外壳直通的判断，
   编译过、装到表上，截图仍是圆角卡（当时 prefs 已是 SQUARE）。改动已回滚。
   下轮先查：根页的卡壳是不是还有第二个来源（`SettingsStyleCards.kt` 的 `SettingsM3Card`），
   以及 `SettingsStylePolicy.current()` 在 `SettingsGroupCard` 里读到的是不是最新风格值。
2. 巡检/实测探针**依赖坐标点击，不可靠**：`tapped=false` 证明点击没发生；换触摸滑动更糟（已回滚）。
   下轮改 instrumentation：`UiDevice.findObject(By.text("模型参数")).click()`。
3. `stale 假绿` 教训：源码扫描型测试的锚点一改就失效（`substringAfter` 找不到会返回整个文件）。
   改可见性或搬文件时，必须同步改锚点，否则断言恒真。

**别做的事**：不要再用"数表冠格数"驱动滚动；不要再猜测坐标；不要在没真机验证前声称风格修好。
## 0.0.2 六套风格实测总表（2026-11-18，0.0.0.72）

| 风格 | 外壳槽位 | 真机 | 证据 |
|---|---|---|---|
| BALLOON 气泡 | CARD_GROUP（一组一张卡） | ✅ 一致 | `style-balloon-root.png` |
| SQUARE 方角 | HAIRLINE_FLAT（无卡+线） | ✅ 一致 | `style-square-hairline.png` |
| GOOGLE_CARD 大卡片 | CARD_EACH（一行一张） | ✅ 一致 | `style-googlecard-root.png` |
| TERMINAL 终端 | HAIRLINE_FLAT + 等宽 + `> ` | ✅ 一致 | `style-terminal-root.png` |
| M3_LIST 官方列表 | PLAIN（官方胶囊） | ✅ 一致 | `m3list-tools-open.png` |
| M3_TRANSFORM 官方转场 | PLAIN + **行转场未接线**（`transformRows` 形参已声明但函数体未使用，见 `SettingsShared.kt` 的 TODO 0.0.0.62） | ⚠️ 外观与 M3_LIST 相同 | `m3transform-scrolled.png` |

**一处共性缺陷（待用户拍板）**：根页叠层时钟与首行内容在 y≈10..30 重叠，六套都有
（24dp 上边距只够让开遮罩，不够让开钟带）。三个改法见 `docs/UI-STYLES.md` 末节。

**下一步优先级**
1. 时钟重叠的三个改法等用户选一个（它不是审美小事：现在是真的字叠字）。
2. 设置项实测剩余三页 + 15 页巡检：改用 instrumentation 节点点击，别再数表冠格数。
3. 官方两套的 `SettingsM3List.kt` 仍是"未接线但有文件"，可以考虑删掉死文件或正式接入。
## 0.0.3 三十轮（0.0.0.53 → 0.0.0.82）到底做成了什么

**已交付、有真机证据**
1. **六套风格**：气泡 / 方角 / 大卡片 / 终端 / 官方列表 / 官方转场。
   每套都有截图，并按"槽位声明 vs 真机表现"逐项核对（`docs/UI-STYLES.md` §7）。
   官方两套走 `TransformingLazyColumn` + `SplitSwitchButton` / `RadioButton` / `Slider`，转场档带官方行转场。
2. **方角/终端不再套卡**（发丝线外壳生效），**根页时钟滚动淡出**（修掉六套共同的叠字缺陷，2 条守卫）。
3. **15 页巡检 15/15**（`docs/tools/sweep-pages.mjs`）——HANDOFF §0.5 第①条收口。
4. **0.0.0.80 发布点自检 7/7**，tag `v0.0.0.80` 已推送。
5. 清理：删 `SettingsStyleM3List.kt`（352 行未接线重复实现）、删死分支、`SettingsShared.kt` 从 2236 → ~1750 行。

**明确没做成（别当成做完了）**
- **设置项功能实测**：只有 6 个键可信（工具页三个开关 + `security_mode` + `settings_style`）。
  `sweep-settings.mjs` 已标注"不可用于结论"，要真结论必须换 instrumentation。
- **等用户拍板的四件事**（都不许擅动）：
  ① 大卡片档长副标题在 176dp 折行（放宽到 216dp / 单行省略 / 保持折行）；
  ② 行高 1.42 版式手术（要按 DESIGN-SPEC 顺序，先改 `ApprovalBandMetricsTest` 推导）；
  ③ 官方 `Slider` 在 176dp 里轨道只剩 ~52dp，够不够用；
  ④ 时钟重叠的另两个方案（抬边距 / 只抬大卡片档）——现在用的是"滚动淡出"。

**下一轮第一件事**：`git push`（本地可能领先）；然后按上面"等用户拍板"清单去问，不要自己选。
## 0.6.1 `manage_context compact` 为什么永远失败（2026-11-18 查明）

**结论：不是插件配置错，是它要的那个服务在这个组合里根本不存在。**

插件代码 `@local/context-governor/index.js`：

```js
function resolveService(agentCtx, name) {
  for (const candidate of [agentCtx, pluginCtx]) {
    const service = candidate?.get?.(name);   // 只查 agent 面 + 插件面
    if (service) return service;
  }
  return undefined;
}
// compactAction 里：
const compaction = resolveService(agentCtx, "compaction");
if (!compaction) return bad("compaction backend is not mounted in this composition; use the /compact command instead.");
// 用到的 API：compaction.compactRegion(start, end, agent, signal)
```

而梁神预设的 agent 组合（`@linxin666/dsh-liangshen/presets/liangshen/agent.cordis.yml`）里是：

```yaml
- id: compaction            # 一个开关型条目（compaction: true）
- id: compaction-basic      # @deepseek-ai/dsh-compaction-basic
- id: command-compact       # @deepseek-ai/dsh-command-compact   ← /compact 命令就是它
- id: compaction-tool-result-pruner
```

也就是说：**命令路径存在（所以 `/compact` 能用），但插件要的 `compaction` 服务对象没有暴露在
`agentCtx` / `pluginCtx` 这两层上**，`compactRegion` 这个 API 在本机安装包目录里也没搜到。
所以那句报错是**事实陈述**，不是插件写错了。

**两条修法（都需要动别人的东西，所以停在这里等你）**
1. 改插件：把 `compact` 从"调服务"改成"调命令"（等价于替你发 `/compact`），或沿着 ctx 父链再找几层。
   插件是本地链接（`D:\nfc isc\context-governor`），改完要重载。
2. 改预设：让梁神预设把 `compaction` 服务暴露到 agent 面。风险更高（影响所有 agent 行为）。

**现状（2026-11-18 更新）**：压缩我自己就能触发 —— `manage_context({action:"compact"})` 现在会**排队到本轮结束后执行**
（用户 2026-11-18 换掉了插件版本）。所以"想压就压"不再是禁区，也不该再靠反复试点那个按钮来验证。
不再需要用户手动发 `/compact`，也不必为了压缩而中断本轮工作。