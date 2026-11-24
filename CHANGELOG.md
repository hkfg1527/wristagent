# 更新日志

所有重要的系统变更与特性更新均记录于此文件。

## 版本与发布节奏（2026-11-14 起）

- 内部版本号四位 `0.0.0.x`，每次内部更新**只递增最低位**。
- **每累计 10 个内部版本**才做一次对外发布（GitHub release + 完整回归）；`0.0.0.10` 是第一次对外发布点。
- 每 100 个内部版本做一次信息架构 / 视觉系统复审，之后继续，不停工。
- `versionCode` 仍然单调递增（与内部版本号解耦），当前 **139**。

---

## [未发布]

### 修复：设置页 V2 没有曲面时钟（顶部是一条空黑带）
V2 是整页重写，重写时把旧设置页的 `TimeText` 这一层**整层漏掉**了 —— 真机 466×466 实测首屏
y=0..48 没有任何语义节点，而官方 Wear 每一页都有这条钟带。

- 补「顶部渐隐 + 官方 `TimeText`」显式叠层（**不走 `ScreenScaffold` 的 `timeText` 槽**：那个槽
  只把内容登记进 `LocalScaffoldState`，本项目没有 `AppScaffold`，传了等于没传）。
- 四套列表/卡片风格（大卡片 / 官方列表 / 贴边卡 / 方角）生效；**终端档明确不放钟** ——
  它顶部是 `wristagent` banner + 状态栏，两者在同一条 y 轴上，距离不足 1dp。
- 判据 `SettingsV2ClockChromeTest` 5 条。**变异验证**：把钟带条件改成 `false` → 4 条红。
- **判据边界**：Robolectric 下 `TimeText` 产出空文本（平台时间格式化器在影子环境给不出字符串），
  所以 JVM 只钉「存在 + 贴顶 + 水平居中」，时间真的显示由真机截图负责。

### 修复：选项子页的「‹ 返回」行还挂着右向箭头
`‹ 返回 ›` 左箭头说后退、右箭头说前进，同一行自相矛盾（0.0.0.260 真机截图可见）。

- 返回行 `trailing` 改为 `RowTrailing.None`；选项子列表的构造抽成纯函数
  `choiceSubSections(row)`，让「首行是返回行 / 只有当前值带 ✓ / 选项数 + 1」变成可断言的数据判据。
- 判据加进 `SettingsV2OptionInteractionTest`（新 1 条）。

### 修复：官方滑杆读不出「这是哪一项」（并补上 M3_LIST / M3_TRANSFORM 的无障碍缺口）
官方 `Slider` 自带区间，但行标签不在那个节点上 —— 读屏只念百分比，不念项目名。

- 修法：语义挂在 **`Slider` 自己的 modifier** 上（`Modifier.semantics { contentDescription = label }`）。
  先试的"外层 Box + `mergeDescendants`"无效：合并只搬属性，`SetProgress` 是**动作**，仍留在子节点。
- 新增 `OfficialSliderAccessibilityTest` 2 条：同一节点同时具备标签、`SetProgress`、`ProgressBarRangeInfo`；
  `SetProgress` 能真正把值传出（0.8f）。
- BALLOON 起把开关/滑杆/单选切到官方件（`SwitchButton` / `Slider(segmented)` / `RadioButton`），
  这条修复同时消除了 M3_LIST / M3_TRANSFORM 之前未被测试覆盖的同类缺口。

### 修复：磁滑（snap）参数用错 —— `snapOffset` 是屏心锚点偏移，不是行距
官方 `snapFlingBehavior` 吸附的是"某一项正好居中"，`snapOffset` 表示吸附锚点相对屏心的偏移量。
之前把行距（72dp）当成节距传进去，等于给吸附网格整体偏移 72dp，看起来就像"没有吸附"。

- 新增 `SnapFlingReachTest`：Robolectric 里用同一段 `swipeUp()` 跑吸附组与对照组，读 `centerItemScrollOffset`。
  **实测 `snapOffset=72dp` → centerOffset=-4px；默认 `0dp` → centerOffset=0**（精确居中）。
- 两条列表改回 `snapFlingBehavior(state)`；`StyleMotion` 删掉概念错误的 `snapPitchDp`，只留 `snapToRows` 开关。
- 真机 5 次快甩落点 `28/62/62/62/62` px（稳定收敛）；精确居中由 JVM 测试证明，真机只证明落点稳定。

### UI S1：逐行动效按风格读表 + 磁滑接线（**未生效，待查**）
- 入场/退场位移、时长、错峰、入场缩放全部改读 `StyleMotions.current`，不再是写死的 4dp/40ms/140ms；
  入场缩放 `scaleX == scaleY`（不产生横向形变）。
- 磁滑：两条列表（`ScalingLazyColumn` / `TransformingLazyColumn`）都接官方 `snapFlingBehavior`，
  节距按风格给（`StyleMotion.snapPitchDp`，36..120 校验），终端档保留连续滚动。
- **未验证/疑似未生效**：BALLOON（snap 开）与 TERMINAL（snap 关）同样慢拖 100px，锚点位移
  -70px / -69px，没有区别。慢拖末速接近 0 可能根本没进 `performFling`，也可能是自定义表冠处理器
  与外层手势包装吃掉了这条路径。下一步先定位，不把"磁滑"当已完成。

### UI 重构 S0：设置页从 6 套风格扩到 10 套（互相独立）
用户要求「全部都按各自风格重新做」「做完十套风格，相互独立」。本轮先落地架构，视觉逐套实现。

- 新增四套自研风格：`NEON` 霓虹、`PAPER` 手帐、`GLASS` 磨砂玻璃、`ARC` 弧列（沿圆弧排布）。
- `SettingsStyle` 枚举 6 → 10，`specOf()` 十套全给值，中英文字符串各补 8 条。
- 新增 `stylekit/StyleMotion.kt`：入场/退场位移、时长、错峰、磁滑开关、贴边缩放与透明度按风格分别给值，
  带区间校验；`StyleMotionTest` 4 条钉住「十套互不相同 / 终端不吸附 / 官方两套不加位移 /
  贴边缩放只有 ARC·TERMINAL 允许为 1」。
- 计划与说明书：`docs/UI-REBUILD-PLAN.md`、`docs/UI-STYLES.md`（十套速览）。

### 安全：控制台不再"先收完 body 再鉴权"
没有 `Content-Length` 或用了 `Transfer-Encoding: chunked` 的请求，NanoHTTPD 会先把整段 body 物化成字符串
再交给 `handle` —— 64K 闸门来不及生效，而且这一步发生在 **token 校验之前**：局域网里任何未鉴权主机
都能让手表把任意大的 body 读进内存（OOM/卡死）。

- 修法：这类请求直接回 `411 length_required`，不做"先收再判"。
- 回归：`ConsoleServerTest.aChunkedBodyWithoutContentLengthIsRejectedBeforeItIsRead`（裸 socket 只发头不发 body）。
  **变异验证**：关掉闸门后服务端读完空 body 才回 **401** —— 正是"先收后判"。

### 修复：非流式响应体无上限（大响应 OOM）
非流式分支把整段响应读进 `StringBuilder`，随后 `JSONObject` 再复制一份，没有任何上限。

- 修法：按 4M 字符封顶，超限中止并如实报错（不把半截 JSON 交给解析器）；读取放进 `finally` 关闭，循环内检查取消。

### 修复：JSON 解析无深度上限导致进程崩溃
`McpJson.parseValue` 是无限深递归，`McpClient`/`McpSse` 只 catch `McpJsonException` —— 远端回一段
`[[[[…` 就是 `StackOverflowError`（`Error` 不是 `Exception`），工具循环拦不住，App 直接崩。

- 修法：加 `MAX_DEPTH = 32`（与 `ConsoleJson` 同值），进容器时计数、出容器时回退。
- 回归 `McpJsonDepthTest` 3 条；**变异验证**：上限改 100000，两条立刻红。

### 修复：root 命令的退出码恒为 0
`RootSession.buildCommand` 里 `printf '\n'` 排在 `echo "$?"` 之前，`$?` 取到的是 printf 自己的退出码（恒 0）——
**所有 root 命令都以 `exit_code=0` 回报**，失败的命令会被模型当成"已经改成功了"继续下一步。

- 修法：`__rc=$?` 先存，再 `printf`，再 `echo "token:$__rc"`。
- 顺带给 root 常驻会话的输出加 256K 封顶（`ProcessExec` 早有，这条最热的路径反而漏了）。

### 修复：命令替换与 `$IFS` 可以给破坏性命令洗白
`ShellPolicy` 的两处漏判都能把 `rm -rf /data` 降级成「有风险」——RISKY 可以被"记住此命令"永久免审批，
也能过 WORKSPACE 沙箱（只挡 DESTRUCTIVE）：

- `echo $(rm -rf /data)` / `` `rm -rf /` `` / `/storage/..`：`canonicalPath` 把收尾定界符一起吃成路径、且不折叠 `..`；
  已改成剥定界符 + 逐段消 `..`。
- `rm$IFS-rf$IFS/data`：静态匹配不到 `rm `；现在命中 `${IFS}`/`$IFS`/`$@`/`$*` 一律按破坏性。
- 回归各 1 条（含 4 个 `$IFS` 变体）；**变异验证**：去掉 `)` 剥离，立刻红（`expected DESTRUCTIVE but was RISKY`）。

### 修复：取消之后仍会重发请求 / 取消回执重复
- 重试循环里没有取消检查：取消靠 `disconnect` 打断读，那条 `IOException` 被当成"可重试"——
  用户点停止仍会再发 1~3 次真实请求，最后把"取消"显示成「请求失败 · Socket closed」。现在循环开头与 catch 都先判取消。
- `handleCancelledTools` 遍历整批补回执，已执行/已拒绝的调用会重复，同一 `tool_call_id` 两条 `tool` 消息 → 严格供应商 400。
  现在按 `toolCallId` 去重。

### 修复：工具胶囊编造天气
`query_time_weather` 从不返回 `temperature`/`weather` 两个键，`extractToolSummary` 却带默认值 `"22℃"/"晴朗"`——
工具卡上凭空显示"22℃ · 晴朗"。现在读 `weather_status`，显示"天气不可用（原因）"。

### 修复：大卡片档丢了单选语义 / 子页浮层打开时仍能下滑退页
- `CARD_EACH` 分支只把 `onClick` 交给 Card，`Modifier.selectable(role=RadioButton)` 整段被丢 →
  六套风格选择、安全三档、播报队列、手势绑定读不出"已选中"、也不是单选框。现在在卡片节点上补 `selected` + `Role.RadioButton`。
- 子页的 `pullDownToDismiss` 不看浮层：对话框打开时往下拖 50dp 就把整页退掉，正在编辑的系统提示词草稿直接丢。
  现在 `canPullDown = { overlayBack == null && listState.isAtScrollTop() }`。

### 修复：MCP `abort()` 只能断掉一条在途请求（单槽位）
`McpHttpTransport.active` 原来是**单个槽位**，而同一个 client 会并发发请求（`ensureFresh` 后台刷新 + `callTool`）：
后开的连接覆盖前者 → `abort()` 断错连接；先结束者的 `finally` 又会把仍在途的连接从槽里清空 →
此后 `abort()` 变成空操作，取消失效只能等读超时。

- 修法：改成并发集合（`newSetFromMap(ConcurrentHashMap)`），`abort()` 断开并清空全部，
  各自的 `finally` 只摘自己那一条。
- 回归：`McpTransportAbortTest` —— 假服务器把 `tools/call` 拖 10 秒，两条请求都进在途后 `abort()`，
  断言两条都失败且 5 秒内返回。**变异验证**：把 `abort()` 改回"只断最后一条"，
  用例立刻红（`expected:<2> but was:<1>`）。

### 测试：给上一轮那条「改了没测」的 MCP 修复补上回归
`McpToolBinding` 新增 `callToolOverride` 接缝（默认 `null` = 真打远端，生产行为不变）。
上一轮试了三种办法都造不出"传输层抛异常"（停服务 / 读超时 / `failCallWithRpcError`），
因为它们都会被假服务器映射成"工具自报失败"—— 没有接缝就一条用例也写不出来。

- 新回归 `aFailedCallDoesNotHideTheWholeRemoteCatalog`：注入 `McpTransportException("timeout")` 后断言
  回执是 `status=error` / `error_type=mcp_timeout`、**清单仍是 3 条**、`lastError` 有值、工具定义仍是 3 条。
- **变异验证**：把异常分支改回清空 `tools`，用例立刻红（`expected:<3> but was:<0>`）；换回即绿。
- 顺带确认约定：MCP 回执用 `output` 里的 `status/error_type` 表达失败，不设 `ToolResult.isError`。

### 修复：一次 MCP 工具调用失败会清空整份远端工具清单（15 秒）
`McpToolBinding.toolResult` 的异常分支以前连 `snapshot.tools` 一起清空并把 `retryAtMs` 推后 15 秒 ——
某条工具超时就会**把整份远端清单从模型视野里拿走 15 秒**，而兄弟调用可能还在跑，
用户看到的是"刚才还能用的远端工具突然全没了"。

- 修法：只记 `lastError` 并安排一次重试，清单保持可用；会话仍丢弃（传输可能已坏）。
- **未验证**：试了三种制造传输失败的办法（停服务 / 读超时 / 假服务器的 `failCallWithRpcError`），
  现有假服务器都会把失败映射成"工具级 `isError`"，走不到这个异常分支 —— 这条改动**没有测试覆盖**。

### 测试：把「卡片不重叠」也搬进 JVM 判据
`SettingsCardWidthRobolectricTest` 加第二条：六套风格各渲染一次，取所有可点节点，对**同宽且区间相交**的成对比较，
失败条件是「相交但谁都不包含对方」——父卡包含子行是正常结构，不算重叠。

- 活性证明：断言"至少发生过一次同宽且相交的比较"，否则一个可点节点都没有时这条会假绿。
- 实测六套全部通过。与上一条宽度判据合起来，用户定的「卡片同宽不重叠」从此每次跑单测都会验。

### 修复：子页 hero 卡 216dp，比同页所有卡宽 40dp（破「所有卡片同宽」）
子页 hero 卡躺在一个 `fillMaxWidth()` 的 Box 里 —— 在 233dp 屏上就是 **216dp**，而组卡写死 176dp。
同一页里一张卡比别家宽 40dp，正是用户点名的「规格不统一」。

- 修法：hero 外层再套一层 `Modifier.width(SettingsContentWidth)`。
- 这是**新加的 JVM 判据抓到的**：`SettingsCardWidthRobolectricTest` 六套风格各渲染一次，
  量每个可点节点的宽度，断言「没有节点比 176dp 基准更宽」且「至少有一个正好等于基准」。
  它在加上当天就报出 `GOOGLE_CARD：有行比卡片基准宽度还宽（最宽 216.0px，基准 176.0px）`。

### 修复：Base URL 带 `/v1` 时模型清单恒空（拼成 `/v1/v1/models`）
旧实现一律 `$base/v1/models` —— 用户把 Base URL 填成 OpenAI 风格 `https://api.openai.com/v1` 时，
实际请求 `/v1/v1/models` 得 404，模型清单恒空，而且被 `-1L to emptyList()` 静默吞掉，
界面上只表现为「实拉模型永远失败」。

- 抽出 `modelsUrl()`：已带 `/v1` 就不再叠、已经是 `/models` 就原样、空 base 直接返回空串（不发请求）。
- 顺带补 `disconnect()`（原来全程没有）与非 2xx 时 errorStream 的关闭。
- 回归：`ModelsUrlTest` 4 条（普通 / 已带 v1 / 已是 models / 空串）。

### 修复：服务端回整段 JSON 时被当 SSE 读，界面只说「模型回了空消息」
网关忽略 `Accept: text/event-stream`、直接回 200 + 一整段 JSON 时，每一行都不以 `data:` 开头，
SSE 累加器一个字都收不到 —— 界面表现是**空消息**且没有任何错误提示，比报错更难查。

- 修法：流式分支顺手留一份原始文本（上限 512K），若一个事件都没解析出，就按普通 chat completion
  再解析一次（`parseWholeBodyEvents`）。
- 回归：`SseWholeBodyFallbackTest` 3 条（正文 / 工具调用 / 垃圾输入不抛异常）。

### 修复：Shizuku 绑定超时/取消后不解绑（悬挂连接）
`bind()` 的 `suspendCancellableCoroutine` 没有 `invokeOnCancellation` —— 绑定超时（`withTimeoutOrNull`）
或调用方取消后连接**永不解绑**，Shizuku 进程里留一个悬挂绑定；而 `pendingConnection` 会被下一次 bind
覆盖，`release()` 只能解掉最后一个。

- 修法：取消时 `Shizuku.unbindUserService(...)`；`onBindingDied` / `onNullBinding` 也顺手解绑并清 `pendingConnection`。

### 测试：`AgentToolHonestyTest` 从「自证」改成「真调用」
原来三条一条都没碰被测代码：自己 `new Date()` 再断言格式含冒号、自己写一个常量再断言它等于自己、
断言数据类存了构造参数 —— 永远绿。两次审查都点名过。

- 现在走 `AgentToolRegistry.executeTool`：时间必须与**当前分钟**一致、天气必须如实声明
  `unavailable_without_sensor_or_api`、空白关键词必须被真实入口挡下、非空关键词不许被误挡。
- 这组测试改成 Robolectric：工具层用 `org.json`，纯 JVM 下是桩（`optString` 返回 null）→ 直接 NPE。

### 修复：`PrivilegeManager.release()` 不回收 root 会话（每进一次设置页留一个常驻 `su`）
以前 `release()` 只解绑 Shizuku 监听：`RootShell` 里那个常驻 `su` 进程与它的读线程**一次都不会关**
（`RootShell.close()` 全仓没有调用点），自建的 `scope` 也从不 cancel。`SettingsScreen` / `ConsoleBridge` /
`AgentDispatcher` 每次 new 一个 Manager 就留下一个常驻 su 进程 + 一条线程，反复进出设置页会累积授权弹窗。

- 修法：`release()` 里补 `rootShell.close()`，并 cancel **自建**的 scope；
  构造参数改成 `externalScope: CoroutineScope? = null`，外部传进来的 scope 归调用方管，不由这里 cancel。

### 测试：版本行守卫收紧到「§0.0 + §0.5 的版本行」
`ContextDocFreshnessGuardTest` 以前只在整篇 `docs/CONTEXT.md` 里 `contains(版本号)` ——
§0.0 写对了、§0.5 还停在上一个版本时守卫照样绿（0.0.0.100 审查点名）。

- 现在分别切 §0.0 与 §0.5 两段；§0.5 **只认「- 内部版」那一行**（那一段还含全部历史条目，放开会更松）。
- **变异验证**：把 §0.5 的版本行改回 `0.0.0.104`，用例立刻红；改回即绿。

### 修复：子进程输出无上限（`logcat` 一次就能把进程 OOM 掉）
`ProcessExec.drain()` 以前用 `reader.readText()` 无上限灌进 `StringBuilder` ——
`logcat`、`dumpsys` 这类命令 15 秒能产出数十 MB，手表堆只有数百 MB。工具层虽然还会 `take(4000)`，
但那是**读完之后**才截，救不了已经堆起来的内存。

- 现在每路输出（stdout / stderr）按 **256K 字符**封顶；超出部分继续读干（不读干的话子进程会被写满的管道卡住），
  但不再往内存里塞。
- 回归：`ProcessExecTest` 2 条（大输出被截、小输出原样）。**变异验证**：换回 `readText()`，
  用例报「实际 614699 字符」立刻红。

### 测试：最后三处裸切片补锚点自证
新增 `TestSlices.slice(source, start, end)`：先断言两个锚点存在、顺序正确、切片长度 < 全文一半，再切片。
`SettingsDotRuleTest`（按下态底色助手）、`OnboardingPagerTest`（页脚 / 正文 / 页面 / 页码点四处）、
`SettingsVisualGuardTest`（组标题）全部改走它 —— 这几处以前只要函数改名，切片就会静默变成「从锚点到文件尾」，
负断言（`assertFalse`）反而更绿。

### 测试：给 root 会话补上"超时后还能继续用"的回归
0.0.0.103 修了"一条超时命令把 root 线程池占死"这个高危险故障，但**没有测试** —— 要多线程 + 假进程才能复现。
现在补上 `FakeSuProcess`（`java.lang.Process` 的假实现）与 `RootShellTimeoutTest` 2 条。

假进程有三处刻意"像真的一样"的细节，缺一条测试就失真：
- `read()` 必须覆盖 `read(byte[], int, int)`：默认实现会一直调 `read()` 填满缓冲区，`readLine()` 永远返回不了；
- `InterruptedException` 要吞掉继续等：真管道读不响应 `Thread.interrupt`，否则 `future.cancel(true)` 会"意外生效"；
- `survivesDestroy`：模拟"su 被杀、但子孙进程仍握着管道"，这才是真实卡死的成因。

**变异验证**：把 `execute` 换回旧的常驻单线程池，用例立刻红
（`超时之后 root 必须还能用：timeout after 400ms`）；换回修复即绿。

### 修复：root 会话三条（超时卡死线程池 / 死会话复用 / 会话错位）
- **超时永久卡死**：旧实现用常驻单线程池 + `future.cancel(true)`，而任务阻塞在 `readLine()`
  （管道读不响应 interrupt）→ 该线程永久占住池子，之后**每条** root 命令都排在死任务后面逐个 15s 超时，
  `isAvailable()` 恒 false，root 静默降级到 DIRECT。改成每条命令一个一次性线程；超时即杀进程
  （管道关闭 → 阻塞读立刻返回）后丢弃。
- **死会话被永久复用**：读到 EOF 返回 `root shell closed` 后不清缓存 → su 被撤权/被杀一次后，
  之后每条命令都返回同一句失败且不会重新申请。现在见到该标记就丢弃会话。
- **会话错位**：`RootShellProtocol.buildCommand` 现在 `</dev/null` 隔离 stdin
  （否则 `cat`/`read`/嵌套 `sh` 会吞掉**下一条命令**）、哨兵前先 `printf '\n'`
  （否则不带尾换行的输出会把哨兵粘成 `hitoken:0`，读循环卡到超时）。
- 回归：`RootSessionTest.commandIsIsolatedFromStdinAndTheSentinelCannotStickToOutput`。
  `RootShell` 的会话回收本身没有单测（要伪造 `java.lang.Process`），本轮由编译 + 全量单测兜底。

### 修复：MCP 三处（响应体无上限 / 取消不断连接 / 远端工具不过审批）
- **响应体无上限**：`McpHttpTransport.readBody` 原来 `reader.readText()` 读完整个体 —— 端点回几百 MB、
  或每 5 秒吐一个字节（低于读超时，连接永不超时），就能把 IO 线程无限拖住并 OOM（手表堆仅数百 MB）。
  现在按 64K 字符封顶，超限截断并标 `…[truncated]`。
- **取消不断连接**：`cancelCurrentExecution()` 只置标志，而流式读阻塞在 socket 上不会醒 ——
  点取消后请求仍跑满读超时（默认 40s、可设 180s），表继续耗电、请求继续计费。
  现在持有 `activeConnection` 并在取消时 `disconnect()`；我们主动断开导致的 IOException 按正常收尾处理，
  不会让用户看到"网络通信异常"。
- **MCP 工具不过审批（高，安全）**：`requiresApprovalAlways` 现在把 `mcp_` 前缀的远端工具也纳入。
  远端 server 暴露什么能力本地不可知（可能是 shell / 文件系统），而 `assessTool` 对这类名字一律判
  "安全只读"，STANDARD/EXPERT 下会 AUTO_ALLOW —— 等于把远端能力接到无人值守通道上。
- 回归：`AgentToolRegistryMcpTest.remoteToolsRequireApprovalLikeAnyPrivilegedTool`。
  前两条没有单测（要真起 socket / 真取消流式请求），本轮由编译 + 全量单测兜底。

### 修复：供应商档案加到第 6 套仍"成功"，第 6 套静默消失
红队在 0.0.0.100 用一条 POST 证伪了 0.0.0.95 那条修复：`ProviderProfileBook.add()` 在满 5 套或 id 重复时
**原样返回**，而 `saveWebProviderProfiles` 把它当成功 —— 响应是 200 + `ok:true`，账本里却只有 5 套。
**上次只改了返回码，没改"满额时根本不报错"的上游。**

- 修法：抽出纯函数 `rebuildProviderBookFromWebRows`，每加一行都检查"账本真的变长了"，否则整体拒绝；
  同一份提交里出现同名行也直接拒（名字在界面上就是主键）。
- 这条链现在才闭合：`ConsoleBridge` 回 `REJECTED` → 响应带 `rejected` → 网页报「保存失败：手表拒绝了 N 项」
  并保留输入（0.0.0.95 做的两段在此之前从未被真正触发过）。
- 回归：`ConsoleProviderProfilesTest` 4 条（6 套必拒 / 同名必拒 / 正常载荷仍落库且沿用原 id / 空名与非对象行必拒）。

---

## [0.0.0.90] - 2026-11-19（整十发布点 · 历史条目，下方 0.0.0.100 段是其后十个内部版）

### 安全：修掉三条高危缺陷（第五次整十审查发现）

- **换行可以藏第二条命令**：`ShellPolicy.normalize()` 把 `\n` 折成空格，`ls\nreboot -p` 归一化后只剩首词 `ls`，
  命中只读白名单判成 `SAFE`（只读沙箱直接放行），而 `sh -c` 会把两条都跑掉。现在按原始串的 `\n\r;|&`
  拆成子命令逐条判档并取最坏一档。
- **`rm` 只查第一个目标**：`rm -rf /sdcard/Download /data` 里的 `/data` 从未被检查，整条只判 `RISKY`
  （可被"记住此命令"永久放行），实际执行会清掉 `/data`。现在每个非选项词都判定，选项与目标任意排序都成立。
- **控制台未鉴权任意文件删除**：`ConsoleServer` 把 NanoHTTPD 的 `postData`（其实是正文文本）当文件路径，
  `File(raw).isFile → readBytes() → delete()`，且发生在鉴权之前 —— 任何无令牌客户端 POST 一个真实路径即可
  删除该文件。整段删除，正文一律按文本处理。
- **审批闸门默认放行**：`onRequireConfirmation` 为 null 时 `?: true` 直接通过；`ChatViewModel.onCleared`
  会把回调置空，于是 ViewModel 被回收的瞬间，排队中的审批点（含破坏性命令）自动通过。改为默认拒绝。
- 回归：`ShellPolicyTest` +4 条（换行 / 分号链 / 多目标 rm / 不过度升档）、`ConsoleServerTest` +1 条
  （POST 一个真实存在的路径，断言 401 且文件仍在）。基线 1355 → **1360 条 / 205 套件**。

### 文档：把"会撒谎的记忆源"改回事实

- `docs/HANDOFF.md` §0 与 §0.5 整表重写：旧版写 `0.0.0.44` / 1289 条 / 188 套件 / 发布点还差 6 个，
  其中 5 条欠账其实早已完成（15 页巡检 15/15、控制台交互路径、压缩、工具插件）。
- `docs/CONTEXT.md`：两处写死的旧基线（1338 / 1189）改成"以 §0.5 为准"；风格"5 种"改 6 套；
  发布自检"7 项"改 8 条判据；M3_TRANSFORM 的"行转场 ✅"改成"未接线（TODO 0.0.0.62）"；
  边缘缩放"贴边 0.70/0.50"改成事实（`MinScale = 1.00f` 只淡出不缩放）；§4 那 14 条历史待办加更正抬头。
- 新增 `docs/REVIEW-0.0.0.90.md`：5 路审查的结论与处置表（含 1 路失败的重派计划）。

---

## [0.0.0.100] - 2026-11-19（整十发布点）

> 本段覆盖 0.0.0.91 → 0.0.0.100 的全部改动（第五次整十审查在同一天完成，报告见 `docs/REVIEW-0.0.0.100.md`）。

### 修复：审批浮层把正文列表的手指手势吃掉了（用户可感知）
`ApprovalFloatingLayer` 为了"预合成"常驻组合、靠 `graphicsLayer(alpha)` 显隐 —— 而 **alpha=0 不影响手势消费**。
后果：屏幕中部约 190dp 宽的一条带子把手势全吃掉，正文列表在那一段**竖划完全没反应**。

- 修法：滚动容器改成 `verticalScroll(contentScroll, enabled = visible)`；隐藏时整棵语义树
  `clearAndSetSemantics {}`（读屏也读不到隐藏的审批文案）。

### 安全：引号与路径写法能绕过破坏性判定
`rm -rf "/data"`、`rm -rf '/system'`、`sh -c 'rm -rf /data'`、`dd of="/dev/block/mmcblk0"`、
`pm uninstall "com.android.systemui"` 全部落到 RISKY —— 而 RISKY 可被"记住此命令"**永久免审批**，
`WORKSPACE` 沙箱也只挡 DESTRUCTIVE。路径写法 `rm -rf /data/`、`/./data`、`/system/bin/rm -rf /data` 同样漏判。

- 修法：判档前做**词法归一**（去引号与反斜杠）；`rm` 前缀类补 `/`、`"`、`'` 等字符；
  目标先 `canonicalPath()`（去重复斜杠、去 `/./`、去尾斜杠）再比。"记住此命令"的键仍用原文。
- 回归：`ShellPolicyTest.quotedOrWrappedCommandsCannotHideTheirRealTarget`（10 个绕过样本 + 1 个反方向）。

### 安全：`/api/state` 的 `tokenMasked` 其实回的是明文访问码
`tokenMasked` 用的是 `ConsoleToken.mask()`，而 `mask` 对 **≤4 位直接原样返回**
（那是给手表本机 UI 显示自己访问码用的）。默认令牌正是 4 位 —— 于是字段名承诺脱敏、
实际把**可用的访问码**写进了每一个 `/api/state` 响应体，截图、DevTools、抓包、日志里都会带着它。

- 新增 `ConsoleToken.redacted()`：固定回 `••••`，不透露长度、不留片段；对外响应一律用它。
- 手表本机 UI（`RemoteConsoleSection`）仍用 `mask()` —— 主人看自己的访问码是功能，不是泄漏。
- 回归：`ConsoleApiTest` 断言 4 位令牌下 `tokenMasked == ••••`，且不含令牌的任何片段。

### 修复：控制台 500 一律零日志
`serve()` 的兜底 catch 以前直接吞掉异常并回 `{"error":"internal"}`：真缺陷在真机上没有任何证据。
现在记 `Log.w(TAG, "serve 失败：<method> <uri>", failure)`。

### 修复：网页开关会被同一次 flush 的响应弹回去
文本域 / 密钥 / 滑杆 / 下拉都有"脏键优先于快照"的保护，唯独开关直接 `input.checked = readValue(row)`。
拨完开关到真正落盘之间（300ms 防抖 + 一次往返），任何一次 flush 的响应都会把刚拨的开关弹回旧值 ——
用户看到"跳两下"，弱网下会被当成没生效。现在 switch 的 `sync` 先查 `dirty` 里有没有它的服务端键。

### 修复：请求里带着孤儿 tool 回执，整段对话会卡在 400
上游要求每条 `tool` 消息都能找到发起它的 `assistant(tool_calls)`，只要有一条找不到就**整单 400**，
而日志里只有一句不相关的错误 —— 用户看到的是整段对话长期卡在"网络通信异常"，越聊越糟。

孤儿有两个来源：上一次请求被取消（回执只写了一半）、上下文窗口按条数裁剪时正好切在"发起 → 返回"中间。
旧实现一处只补不删（`sanitizeConversationHistory` 只会补缺失回执），另一处完全不处理
（请求侧裸 `takeLast(maxContextTurns * 2)`）。

- 抽出 `ConversationWindow`（纯逻辑）：`sanitizeForRequest` 补缺失回执 + **丢孤儿**；
  `trimRecentTurns` 取最近 N 条并保证**不以 TOOL 开头**（丢掉开头连续的 TOOL 是安全的：
  发起方若还在，一定排在它们前面）。
- 回归：`ConversationWindowTest` 4 条（丢孤儿 / 仍补缺失 / 裁剪不落在 TOOL 上 / 放得下时原样返回）。
- 顺带修一个测试环境坑：补齐回执的内容原来用 `JSONObject().apply{...}.toString()`，
  纯 JVM 单测里 `org.json` 是桩（`isReturnDefaultValues = true`），`toString()` 返回 null → NPE。
  改成字符串常量后，这条路径也不再依赖 Android 运行时。

### 修复：正常文本参数被当成致命指令（搜索与记忆被硬拦）
`SecurityGuardrail.assessTool` 对**整个 argsJson** 做致命命令子串匹配，而黑名单里含 `format `、`wipe `
这种短词。于是：

- `web_search` 搜「how to format a disk on windows」→ 判致命 → 用户只看到"致命指令已被安全护栏拦截"，
  检索什么都没做；
- `remember_fact` 记「wipe cache 的步骤」→ 同样被硬拦。

- 修法：只扫参数里的 `command` 字段（`run_shell` 与自写工具的命令都放这个键），
  并按 JSON 转义还原后再判定。
- 拦截力不变：`run_shell` 的 `rm -rf /`、自写工具的 `mkfs.ext4 /dev/block/sda`、
  `dd ... of=/dev/block/...` 都仍被硬拦（各有用例钉住）。
- 回归：`SecurityGuardrailTest` +2 条（"非 shell 工具的文本参数不被硬拦"先红后绿；
  "shell 工具的命令字段仍被硬拦"守住反方向）。

### 修复：上下文轮数预算可以存成 0，静默丢光历史
控制台字段表把 `maxContextTurns` 的下限写成 0，而属性本身不夹取：存 0 之后 `takeLast(0)` 会
**静默丢掉全部历史**；负数/超大值则抛 `IllegalArgumentException`，被上层吞成"网络异常"。

- 修法：getter/setter 都夹到 `MIN_CONTEXT_TURNS(1)..MAX_CONTEXT_TURNS(200)`；
  控制台字段表改成引用同一组常量（下限从 0 改成 1）。
- 回归：`AgentPreferencesWatchSettingsTest.contextTurnsAreClampedOnBothWriteAndRead`（0 / −7 / 9999 / 42）。

### 测试：两处源码切片守卫补锚点自证
`SettingsRootSnapshotTest` 与 `LongTermMemoryWriteTest` 都用 `substringAfter(...).substringBefore(...)` 取函数体，
但没校验锚点：函数一改名，`substringBefore` 找不到分隔符就**原样返回整串** —— 后面的断言退化成
"整个文件里出现过某串"，守卫永远绿。两处各补：锚点存在、切片长度 < 全文一半、切片里能看到该函数的特征行。

### 测试：MCP 读超时断言不再与假服务器睡眠同值
`readTimeoutIsBoundedInsideTheBinding` 原来是"假服务器睡 900ms + 断言 elapsed < 900ms"，
阈值与睡眠重合：有人把睡眠调小，超时没生效也能过。现在睡眠 1200ms、断言 < 700ms
（读超时 250ms + 450ms 余量），两个方向都留出了余量。

### 修复：网页改不动的键会「静默回滚，还提示已保存」
供应商档案写到第 6 套（上限 5）时，服务端把这次写入塞进 `ignored` —— 而 `ignored` 的语义是
"后端还没有这个字段"。网页据此把值存成草稿，紧接着 `pruneDrafts()` 又把草稿删掉（因为服务端快照里
本来就有这个键），于是：**用户的改动消失、界面回到旧值、toast 仍然写「已保存」**。

- 服务端：`ConsoleWriteResult` 加 `REJECTED`；响应把"未知/只读"放 `ignored`、"存储拒绝"放 `rejected`。
- 网页：对 `rejected` 报「保存失败：手表拒绝了 N 项」并**保留输入**（不写进草稿集合），给"重试保存"按钮。
- 回归：`ConsoleApiTest.storageRejectionIsReportedSeparatelyFromIgnored` —— 一个键 REJECTED、
  一个键 READ_ONLY、一个键 OK，断言三个数组各归各位；把 REJECTED 塞回 ignored 会立刻红（已实测）。

### 修复：控制台 stop() 会打断正在执行的 handler
旧顺序是先 `shutdownNow()`（打断所有 worker）再关监听 —— 已经开始的 `clear-memory` 或一次 40 键批量写
会被直接打断，prefs 停在一半已写、一半没写，而调用方看到的是断连而不是 500。

- 修法：先关监听（不再收新请求）→ `shutdown()` + 200ms 有界等待 → 才 `shutdownNow()`。
- **保证的边界说清楚**：这条只保证 handler 跑完并落盘；响应能否送到客户端取决于 NanoHTTPD 关连接的时机，不承诺。
- 回归：`stopDoesNotInterruptAHandlerThatIsAlreadyWriting`（用一个 120ms 的慢 handler + 中途 stop()，
  断言 handler 跑完了）。换回旧顺序会立刻红（已实测）。

### 测试：外观单例的用例隔离（防止"顺序变了就红/绿"）
`WearAppearance` 是 Kotlin `object`，Robolectric 在同一个 sandbox 里跨测试类复用：
一个用例把风格切到 `M3_LIST`，下一个用例渲染出来的就是官方件版本（行外壳、开关、滑杆全换一套），
断言的失败表现是"找不到节点"，看起来像控件坏了。

- 新增 `SettingsAppearanceResetRule`：before/after 都复位风格、字号、强调色；已接到 4 个会渲染设置页的测试类。
- 规则自身的行为用 `ExternalResource.apply()` 包一层语句验证（2 条，**不依赖用例执行顺序**）：
  断言"执行前已复位"与"执行后被复位"。

### 修复：网页「测试连接」按钮恒 404（网页与服务端 action 清单没对齐）
供应商档案卡上的「测试连接」打的是 `connection-test`，而服务端 `ACTION_NAMES` 里没有这个名字，
于是点一次红一次（`not_found`）。这类缺陷不会让任何单测变红 —— 网页以为自己发了个合法 action，
服务端以为自己收到个不认识的 action，各自都"自洽"。

- 修法：服务端补 `connection-test`，拿当前 Base URL + API Key 打一次 `/models`，只报结论、不改缓存。
- 守卫：`ConsoleActionContractTest`（3 条）扫 `app.js` 的 `actionName: '...'` 与 `ACTION_NAMES` 双向对齐；
  摘掉 `connection-test` 后 2 条立刻红（已实测）。
- E2E 新增 J 组 3 条：真点一次按钮，断言不再是 404 且给出「连接成功 / 连接失败」明确结论（69 → 72）。

### 修复：429 被渲染成「无法连接控制台服务」
连续输错令牌会被临时锁 60 秒并返回 429，而网页只特判 401，429 落到通用错误分支 ——
横幅写「无法连接控制台服务」，还提示用户去确认手表端服务在跑、检查网络。服务其实好好的。

- 修法：`api()` 单独抛 `throttled`，横幅改成「尝试次数过多 · 连续输错令牌会临时锁定 60 秒」，
  并同时把「令牌状态」标成无效；`errorText()` 补 `429` 与 `too_many_attempts` 两条措辞。

### 修复：子代理路径漏掉沙箱闸门（沙箱等级对子代理形同不存在）
`AgentDispatcher.executeSubAgentTool` 只做了黑名单 + 审批，**没有** `SandboxGate` ——
沙箱等级 `READ_ONLY` 时主循环会挡下 RISKY/DESTRUCTIVE 命令，子代理却照样执行。
沙箱语义是"不问人直接拒"，与审批放宽是两条正交的轴，所以这条缺口不会被审批兜住。

- 修法：把主循环那段判定原样搬进子代理路径，并排在审批闸门之前（等级不允许就不该弹审批页）。
- 回归：`SandboxWiringGuardTest` 两条源码守卫（锚点自证 + 长度上界），分别钉
  "两条路径都调用 `SandboxGate.blockMessage`"与"沙箱判定在审批之前"；把子代理那段改成 `null` 会立刻红（已实测）。

### 修复：官方两档（M3_LIST / M3_TRANSFORM）下所有子页弹窗都不出现
`SettingsSubPage` 在 `rowShell == PLAIN` 时早退到 `OfficialListSubPage`，而 `overlay` 与 `overlayBack`
挂在早退**之后** —— 于是这两套风格里改 Base URL、改 API Key、语音密钥、Lux 终端输入、提示词编辑、
网络体检内层页全都"点编辑毫无反应"，内层页的系统返回还会直接跳回设置根页。

- 修法：`overlay` / `overlayBack` 传进 `OfficialListSubPage`，浮层压在时钟与列表之上，并注册 `BackHandler`。
- 回归：`SettingsModelConfigRobolectricTest.officialListStyleStillOpensTheTextDialog`（把传参去掉就会红，已实测）。

### 修复：触觉强度 / 播报队列点了圆点不动
这两行的 `selected` 直接读 `preferences.*`，而 `AgentPreferences` 是普通 getter/setter，
写存储不会触发重组 —— 表现是"点完只有震动，圆点停在原处"。

- 修法：两处各加 `remember { mutableStateOf(...) }`，回调里先更状态再落盘。
- 顺带把单选行从 `clickable(role = Button)` 换成 `selectable(role = RadioButton)`：
  读屏能念出"单选按钮 / 已选中"，测试也终于能断言选中态。
- 回归：`threeHapticLevelsEachWriteThePreference` 追加"该行必须变成已选中"（去掉修复就会红，已实测）。

### 修复：三条"假绿"守卫测试
0.0.0.90 审查点名，逐条修好并做了变异验证（把旧写法改回去必须变红）：

- `SettingsSubtitleLineGuardTest`：锚点 `可进入行` 在 `SettingsShared.kt` 里**不存在**，
  `substringBefore` 于是返回 526 行整段文件。换成真实锚点，并自证切片长度与越界函数名。
- `SettingsStylePolicyTest`：`spec.shell != null`、`back in SettingsBackControl.values()` 被类型系统保证恒真，
  整个用例是空的。改成逐风格钉死返回控件位置、开关/滑杆/单选控件族、字族。
- `SettingsVisualGuardTest`：断言 `slot.contains("trailing")`，而切片起点正是声明 `trailing` 形参的函数。
  改成钉"有 `if (trailing != null)` 判定 + 真的 `trailing()` 调用"。
- `ChatMessageSurfaceVisualGuardTest`：`substringAfter("fun AgentSquareBlurBox(")` 没有终点，
  描边规则只看一种字面量。改成截到下个函数 + 钉"全函数只允许一处描边，且必须挂在审批态分支里"。

### 新增：网络/采样参数/模型端点三页进 JVM 功能回归
- `SettingsNetworkAndHyperRobolectricTest`（6 条）：请求超时 55.4→55 秒、重试 2.4→2 次、
  退避 3300→3400ms（200ms 一档）；top_p 0.63→0.65、最大 token 拨到第 3 级落在 `TokenLadder[3]`、
  空转止损 5.6→6 轮。每条都断言"可调节区间必须是这一行自己的"。
- `SettingsModelConfigRobolectricTest`（4 条）：Base URL 弹层确认落盘、**取消一个字都不写**；
  API Key 弹层确认落盘；已有密钥时屏上**不出现原始密钥**（只出现掩码）。
- 测试辅助收敛：三处重复的 `setSlider` 合进 `SettingsUi`。
- 一处**只记录未改动**的发现写进 `docs/DECISIONS-PENDING.md` D5：Wear M3 1.7.1 的 `Card` 只有带
  `onClick` 的重载（`javap` 实测），所以每张卡组在无障碍树里都是一个点了没反应的按钮。

### 修复：设置页两行滑杆被并成一个无障碍节点，第二个滑杆读屏拨不动
卡组外层是 `Card(onClick = {})`（可点 → `mergeDescendants`），而自绘滑杆行本身不可点，
于是被整张卡吸进同一个无障碍节点。Robolectric 打印出来的那个节点：

- `contentDescription` = **【底栏自动收起延时, 表冠滚动增益】**（两行并成一个）；
- `ProgressBarRangeInfo` = `3000.0 / 1000.0..8000.0`（只保留前一行）；
- 第二个滑杆的 `SetProgress` 被丢弃 → 读屏用户**拨不动**表冠增益。

另外自绘轨道 `SettingsThinSlider` 原本没有挂任何语义（读不到、也拨不动）。

- 修法一：滑杆行自设空语义边界 `Modifier.semantics(mergeDescendants = true) {}`，每行独立成节点。
- 修法二：轨道补 `contentDescription`（取行标签）、`ProgressBarRangeInfo`、`SetProgress`，与官方 Slider 的 rangeSemantics 同义。
- 视觉零改动（只加语义，不动几何与配色）。
- 回归：三条滑杆测试都断言"匹配到的节点区间必须是它自己的"，串了立刻红。

### 新增：设置页功能回归扩到 21 个键 + 四条手势 + 两套跨对象搬运
- `SettingsAppearanceDeepRobolectricTest`（6 条）：触觉三档、字号三档（连落盘一起验）、强调色五档（连落盘）、
  点阵间距 / 底栏延时 / 表冠增益三条滑杆用**语义动作**拨动并验吸附（9.4→9dp、3.3s→3.5s、2.27→2.3）。
- `SettingsGestureRobolectricTest`（1 条）：四段手势各点一个动作，断言四条绑定逐个落盘且其余三条不动。
  定位不靠索引（同一句动作文案在页面上出现四次、懒加载下离屏项不存在），而是"两段标题之间那条带"。
- `SettingsProviderAndTransferRobolectricTest`（4 条）：切档案必须换 `modelName` / `baseUrl` / `activeProfileId`；
  复制与删除改档案数；导出 → 改温度 → 导入复原；剪贴板塞废话再导入**一个字都不写**。
- `SettingsSliderSweepRobolectricTest`（6 条）：记忆三根（轮数 / 压缩阈值 / 单条上限）、语音两根（语速 / 音量）
  逐个验吸附；外加**官方档**（M3_LIST）的点阵间距 —— 那一档的轨道不可点，只有两端 ±按钮可点，
  用例按"连点三次加号 → 4dp 变 7dp"验它确实一段一步。
- 踩坑记档：`WearAppearance.style` 是全局单例，用例之间会污染（切到 M3_LIST 后滑杆换成官方件、没有
  `contentDescription`，断言会以"找不到节点"的样子红），所以每个滑杆用例前显式 `applySettingsStyle(BALLOON)`。
- `SettingsLuxTerminalRobolectricTest`（1 条）：Lux 终端的自由输入 → 弹层 → 确认 → 闸门 → 回显。
  输入 2001 字符（上限 2000）必须**在进 shell 之前**被挡下并在转录里留下「被拒」，
  否则被截断的尾巴会被当成命令的一部分跑掉。

### 新增：控制台页面级 E2E 接进发布自检
`release-check.mjs` 第 4 项自动跑 `console-e2e.mjs`（69 条断言）。令牌不再手填：
设备已 root，脚本直接 `su -c cat /data/data/dev.ghr.wristagent/shared_prefs/console_prefs.xml` 取。

### 修复：网页控制台保存偶发 9 秒超时（写其实已经落盘）
NanoHTTPD 把一条 TCP 连接当成一个 worker 占着，而浏览器会保留空闲 keep-alive 连接；服务端读超时关掉连接、
客户端还没收到 FIN 时发出的请求会石沉大海（POST 不重试）。用户体验就是点了保存、转圈 9 秒、"未保存"，
但刷新一看值其实已经写进去了。

- 实测：12 笔并发 POST 被拉成 5.3s / 10.3s / 15.4s / 20.4s / 25.4s（每笔都卡在 5s 读超时上），紧接的串行 GET 要 5.06s。
- 修法：`MAX_WORKERS` 8 → 32；空闲读超时 5s → 2s；响应一律 `closeConnection(true)`（一问一答就关连接）。
- 修后同一压测：12 笔并发全部 ≤166ms、串行 GET 39~45ms；控制台端到端 E2E 62/69 → **69/69**。
- 守卫：`ConsoleRequestPoolTest` 新增一条"池子至少要能容下 24 条连接"。

### 新增：设置页的 JVM 端功能测试（Robolectric 渲染真 Compose）
手表只有一块，USB 一掉线所有 UI 验证就停摆。现在用 Robolectric 4.18.1 在 JVM 上渲染真实页面，
点语义节点、读 `AgentPreferences` 真值，把"控件点下去有没有落到偏好里"变成 5 秒能跑完的断言。

- `SettingsPagesRobolectricTest`（3 条）：**仅 WiFi 请求**、**触觉反馈**、**开启 SSE 流式**、
  **极速 0.2 快捷预设**、**语音转写开关** —— 五个键从"只能真机手点"变成可回归。
- `SettingsSwitchRowRobolectricTest`（3 条）：开关行渲染文案与副标题、点击回调拿到取反值、
  `stateDescription` 跟随选中态（`已开启`）。
- `WearComposeSmokeTest`（1 条）：Wear M3 主题 + Compose 在 Robolectric 下可渲染的落地探针。
- `SettingsAppearanceAndToolsRobolectricTest`（3 条）：外观六套风格逐个点（断言偏好与运行时镜像同步）、紧凑模式、六个工具开关各点一次只翻转自己的键。
- `SettingsSecurityAndMemoryRobolectricTest`（2 条）：安全三档逐个点断言回调收到的枚举、启用记忆落盘。
- 工具链：`testOptions.unitTests.isIncludeAndroidResources = true`，`@Config(sdk=[35], qualifiers="zh-rCN")`；
  `android-all-instrumented`（197MB）不入库，放 `D:\gradle-home\robolectric-deps`，构建脚本里 `robolectric.offline=true` 指过去。

### 修复：网页控制台保存中文会损坏（数据不可逆）
NanoHTTPD 解请求体时只看 `Content-Type` 里的 charset，缺省按 US-ASCII 处理。网页端发的是
`application/json`（不带 charset），于是**任何中文在被保存前就被逐字节替换成 U+FFFD 并落库**
——写「中文探针-ABC」，读回来是 12 个 `�`，且无法还原。

- 服务端：新增 `ConsoleCharset.withUtf8()`，`ConsoleServer` 在 `parseBody` 前把缺省 charset 补成 `utf-8`，
  解析完立刻还原请求头（不影响下游读到的头）；已显式声明 charset 或 `multipart/*` 一律原样不动。
- 网页端：`api()` 显式发送 `application/json; charset=utf-8`。
- 守卫：`ConsoleCharsetTest`（5 条，钉住"补/不补"的边界），`console-e2e.mjs` 的 F3 钉住端到端中文往返。

### 新增：网页控制台页面级 E2E（`docs/tools/console-e2e.mjs`）
headless Edge + CDP 驱动真实页面，断言 DOM 与抓到的请求，覆盖九组：认证闸门、令牌弹层、
十个页面切换、搜索跳转与高亮、非法 JSON 拦截、文本域写回、滑杆写回、三档皮肤（watch/phone/desktop）、零 JS 异常。
做法是在页面加载前包一层 `window.fetch`，"有没有发请求""发了什么"都变成证据，而不是靠推断。

### 修复：控制台顶栏显示不出设备名
`/api/state` 的 `capabilities` 缺少设备名字段，顶栏只能回退成「当前设备」。
现在 `ConsoleBridge` 把 `Build.MODEL` 带进能力表，`ConsoleApi` 统一 trim 后输出空串兜底；
真机实测顶栏显示 `TicWatch Pro 5`。守卫：`ConsoleApiTest` 2 条（空串兜底、trim）。

---

## [0.0.0.56] - 2026-11-18（内部版，不对外发布）

### UI 第一优先：给四套风格建一张"槽位表"
用户判定现在的风格是"大杂烩，该有的没有，一会儿 M3E 一会儿基础"，要求每套风格独立、内部自洽。

**先说清事实**：设置页只有 **4 套**风格（气泡 / 方角 / 大卡片 / 终端），文档里"5 种"是旧的错数；
真正的病是 WearAppearance.style == 散在 **10 处**、style == 共 **12 处**，各走各的分支。

- 新增 `SettingsStylePolicy`：每套风格用 **8 个槽位**声明自己（骨架 / 组标题 / 行外壳 / 开关 / 滑杆 /
  单选 / 返回 / 字体），渲染代码只读表，不再自己写 `if (style == ...)`。
- 新增守卫 `SettingsStylePolicyTest`（5 条）：四套声明不许重复、只有大卡片档走官方 `ScreenScaffold`、
  返回控件每套只能声明一种位置、只有终端档用等宽。
- 已接线 2 处：子页 `backInList` 与终端等宽字体 —— 从"各写一个 `==`"改成"读表"。
- 计划与验收口径落 `docs/UI-STYLE-PLAN.md`（含第 5 套风格的提议：官方胶囊列表档）。

### 验证
- 单测 **1300 条 / 190 套件 / 0 失败**（新增 5 条槽位守卫）。
- 真机（`0.0.0.56` / versionCode 94）安装成功，设置页渲染与上一版一致（本轮只搬判断，不动像素）。

## [0.0.0.55] - 2026-11-18（内部版，不对外发布）

### 拆上帝文件第五步：单选族搬出去
`SettingsRadioRow` / `SettingsRadioDot` / `SettingsSubRadioRow` → `SettingsRadioRow.kt`（133 行）。
`SettingsShared.kt` **1866 → 1754 行**，五轮累计 **2236 → 1754（−482 行）**。

### 修了两条"已经变成摆设"的守卫
`SettingsSubtitleLineGuardTest` 里的锚点还写着 `substringAfter("private fun SettingsSlotRow(")` ——
但这两轮为了跨文件复用，`SettingsSlotRow` / `SettingsSubtitleLine` 已经改成 `internal`。
`substringAfter` 找不到就返回**整个文件**，于是那两条断言变成了恒真（假绿）。
现在锚点去掉 `private `，守卫重新有牙。

### 顺手量了一下性能（结论：先不动）
`dumpsys gfxinfo` 实测设置根页来回滚 8 趟：**1399 帧 / 11 卡顿（0.79%）/ p50 13ms / p90 18ms / p99 20ms**，
slow bitmap 0。审查报告担心的"整页塞进一个 lazy item 导致每帧栅格化"在这个滚动强度下**没有出现**，
所以这轮不做结构拆分 —— 没有数据支撑的改法不做。

### 验证
- 单测 **1295 条 / 189 套件 / 0 失败**。
- 真机（`0.0.0.55` / versionCode 93）：根页、外观页单选行、时钟均正常。

## [0.0.0.54] - 2026-11-18（内部版，不对外发布）

### 拆上帝文件第四步 + 清冗余图标
- `SettingsNavRow` + 四个排版令牌（组标题 / 行标签 / 行取值 / 焦点）+ `SettingsStatusBlock`
  → `SettingsRowText.kt`（122 行）。`SettingsShared.kt` **1959 → 1866 行**，
  四轮累计 **2236 → 1866（−370 行）**。
- 删掉 10 个**永远不会被选中**的启动图标 webp（`mipmap-*/ic_launcher(.round).webp`，共 33 KB）：
  `minSdk 33` 保证自适应图标可用，`mipmap-anydpi` 的 XML 优先级高于任何密度。
  **APK 4.13 → 4.10 MB**。

### 把一条"爱记仇"的守卫改成钉规则
`SettingsVisualGuardTest` 的强调色守卫原来钉的是"哪几个文件里有 `Wear.Accent`"——
拆一次文件就红一次（0.0.0.52 / .53 / .54 连红三次）。现在改成钉两件真规则：
①**页面文件**（`*Section.kt` / `SettingsScreen.kt` / `SettingsStyleCards.kt`）里一处都不许有；
②全站**总数固定 6 处**。以后随便怎么拆文件都不会误报，多涂一处照样红。

### 验证
- 单测 **1295 条 / 189 套件 / 0 失败**。
- 真机（`0.0.0.54` / versionCode 92）：根页导航行、子页、时钟、卡片均正常
  （`docs/device/v11/cardgroup-*.png`）。

## [0.0.0.53] - 2026-11-18（内部版，不对外发布）

### 拆上帝文件第三步：开关族搬出去
`SettingsSwitchRow` / `SettingsCompactSwitch` / `SettingsSplitToggleRow` → 新文件 `SettingsSwitchRow.kt`（115 行）。
`SettingsShared.kt` 从 **2043 → 1959 行**（三轮拆分累计 **2236 → 1959，−277 行**）。

- `SettingsSlotRow` 从 `private` 放宽到 `internal`（行组件跨文件复用同一份两槽布局）。
- 顺手删掉三个**只在注释里出现过**的 import：`SwitchButton`、`SwitchButtonDefaults`、`TextToggleButton`。
- 守卫继续搬家不删：`SettingsVisualGuardTest` 的强调色守卫改成"允许三个文件、**总数仍是 6 处**"，
  两槽布局与开关轨道的断言改读新文件；`substringAfter` 的锚点去掉了 `private` 前缀。

### 真机冒烟（0.0.0.53 / versionCode 91）
「工具与 MCP」页开关渲染正常（`docs/device/v11/switch-smoke.png`，时钟 1:12）。
**顺带量出一个密度问题**（未修，留给用户拍板）：
行内可写宽度 = 176dp − 14dp×2 = **148dp**，减去开关 44dp 与 10dp 间隙后左槽只剩 **94dp ≈ 188px**，
7 个汉字（约 105dp）就会折行 —— 截图里「电量与温度感知」正是被拆成两行。

## [0.0.0.52] - 2026-11-18（内部版，不对外发布）

### 拆上帝文件第二步：滑杆整块搬出去
`SettingsSliderRow` + `SettingsThinSlider` + 三条滑轨几何常量 → 新文件 `SettingsSliderRow.kt`（194 行）。
`SettingsShared.kt` 从 **2203 → 2043 行**（两轮拆分累计 −193 行）。

- 拆分时把三个被滑杆用到的辅助从 `private` 放宽到 `internal`：`SettingsLabelValueGap`、
  `SettingsValueMaxWidth`、`SettingsSubtitleLine`。
- **守卫跟着搬家，不是删掉**：`SettingsVisualGuardTest` 里"强调色只许出现在设计系统"从
  "只许 SettingsShared.kt"改成"只许这两个文件、**总数仍是 6 处**"；"滑轨选中段必须跟随运行时强调色"
  改读 `SettingsSliderRow.kt`。

### 验证
- 单测 **1295 条 / 189 套件 / 0 失败**。
- 真机（`0.0.0.52` / versionCode 90）打开「网络与请求」：滑杆轨道、强调色选中段、白色拇指、
  说明行全部正常（`docs/device/v11/slider-smoke.png`，时钟 12:56）。

## [0.0.0.51] - 2026-11-18（内部版，不对外发布）

### 拆上帝文件第一步 + 把两份复制粘贴的行组件收成一份
用户新规「简洁优先：同一个控件被交接写成多份，就融成一份只留一份」。这一轮动的是审查报告 §2 的头两条。

- **触觉三件套搬出 SettingsShared.kt** → 新建 `SettingsHaptics.kt`（56 行）：
  `vibrateTick` / `vibrateConfirm` / `vibratePrimitive`。它们只依赖 `AgentPreferences` 与系统 Vibrator，
  和排版、卡片、行组件毫无关系。`SettingsShared.kt` 因此从 2236 行降到 **2203 行**。
- **`SettingsSplitToggleRow` 不再复制 `SettingsSwitchRow` 的函数体**：改成一次委托，
  差别只剩副标题行数（2 行 vs 铺满）。顺手纠正了一处**假注释** —— 它原先写着"官方 TextToggleButton"，
  实际用的是行尾手搓开关（官方那个是整宽胶囊，会吞掉右槽）。
- **`SettingsSubRadioRow` 同理**：`SettingsRadioRow` 新增 `subItem` 参数，子项行改委托。

### 验证
- 单测 **1295 条 / 189 套件 / 0 失败**；其中 `HapticAmplitudeTest` 跟着改读新文件（规则一个字没变，
  并加了一条"触觉实现不许再散落回 SettingsShared.kt"）。
- 真机（`0.0.0.51` / versionCode 89）冒烟：设置根页与子页文本、卡片、时钟与改前一致
  （`docs/device/v11/cardgroup-*.png`）。

## [0.0.0.50] - 2026-11-18（**第 5 个发布点**）

内部版走到第 50 个，按节奏做一次对外发布前的完整回归。发布前自检 7 项全过才打 tag。

### 这一周期（0.0.0.41 → 0.0.0.50）的主要变化
- **设置页去「假 M3E」**：补上曲面 TimeText 与右缘 ScrollIndicator（根页 + 全部子页）；
  整页手搓超椭圆换成官方 Card 分组卡（卡宽 176dp = 实测 352px）；4 页子页标题与根页行名统一。
- **导航**：内层浮层（网络体检）现在会先吃系统返回键，不再跳过父页。
- **构建**：release 终于接上 proguardFiles（此前 keep 规则完全没生效）。
- **工具**：docs/tools/watch.mjs（进设置页/截图/安全带点击）、sweep-pages.mjs（15 页巡检）。
- **审查**：第一次按新规派 5 个子智能体做全代码审查，报告 docs/ARCH-AUDIT-0.0.0.49.md。

## [0.0.0.49] - 2026-11-17（内部版，不对外发布）

### 全代码审查（用户新规的第一次）：5 个子智能体并行，报告 `docs/ARCH-AUDIT-0.0.0.49.md`
基线：**153 文件 / ~30876 行 / 17 包**；测试 **1295 条 / 189 套件 / 17420 行**；
`presentation.ui.settings` 一包 **9038 行（29%）**；**8 个上帝文件占了全项目 30%**。

### 修掉一个 P0 真炸弹：R8 的 keep 规则从来没生效
`release { isMinifyEnabled = true }` 但**从未调用 `proguardFiles(...)`** ——
仓库里那份 1377 字节的 `proguard-rules.pro`（coroutines ServiceLoader / Shizuku / AIDL keep）
完全没用上，release 只吃 AGP 默认规则。已接上默认规则 + 项目规则。

### 驳回一条审查结论 + 实测撤回一条“优化”
- 审查说 `libs.compose.ui.tooling` 与 `libs.androidx.compose.ui.tooling` 重复 —— 查版本目录后确认是
  `androidx.wear.compose` 与 `androidx.compose.ui` 两件东西，**不动**。
- `maxParallelForks = 4` 实测 **50.3s**，1 fork **51.3s**，差 2%（噪声内）：1295 条真跑只占约 10s，
  瓶颈是编译（约 40s）。按“简洁优先”不引入这条配置，源码里留了注释记这个数。

### 砍掉审查确认的死代码（净 −198 行）
`git diff --stat`：**108 插入 / 306 删除**。删掉的是：
- `SettingsCurveGeometry.cardPath`（81 行）——它唯一那条调用在一个**不可达分支**里；
- `SettingsCardRow` 里那段 43 行的"按屏幕弦长自绘超椭圆"分支；
- `groupPath` 转发函数、`RootSectionRow`、`SettingsPrimaryLabel` / `SettingsSecondaryLabel`、四个只用于那条分支的局部量。
两条守卫测试同步改写（不是删掉）：曲线契约改钉 `buildGroupPath`，按压反馈改钉"行不自绘填充、卡面归官方 Card"。
真机冒烟：设置根页与子页渲染与改前一致（`docs/device/v11/cardgroup-*.png`，时钟 11:48）。

### 修掉一条导航缺陷（真机验证）
`网络与请求 → 网络体检` 按系统返回会**直接跳回设置根页**，父页被跳过；而同层级的
`系统提示词` 是正常回父页的。根因：内层页是 `overlay` + 页内 `inner` 状态，返回键走的是外层
`BackHandler`（`activeDialog = null`），没人先关浮层。
修法：`SettingsSubPage` 新增 `overlayBack`，浮层打开时注册更内层的 `BackHandler`。
真机八次轮询全部 `page=网络与请求 / zones=false`。新增守卫 `SettingsInnerOverlayBackGuardTest`。

## [0.0.0.48] - 2026-11-17（内部版，不对外发布）

### 修 4 页「点进去看到的标题和点的那一行不一样」
0.0.0.47 的 15 页巡检抓出来的问题，`docs/SETTINGS-IA.md` §4 早就写了规矩
「页面标题 = 根页行标题」，但**从来没有测试钉它**，所以破了 4 页没人知道。

| 根页行名 | 改前标题 | 改后标题 |
|---|---|---|
| 模型与服务 | 模型与端点 | 模型与服务 |
| 模型参数 | 推理参数 | 模型参数 |
| 语音与唤醒 | 语音输入 | 语音与唤醒 |
| 底栏副键 | 手势与侧键 | 底栏副键 |

- 中英两份字符串成对改（`values/` + `values-zh/`）。
- 新增守卫 `SettingsPageTitleMatchesRowTest`：子页标题键与根页行键**取值必须相等**，
  中英各一条。**实测会红**——把 `settings_sub_hyper_params` 改回「推理参数」后
  `chinesePageTitlesEqualTheirRootRows FAILED`，恢复后绿。
- 真机逐页确认标题：模型与服务 / 模型参数 / 语音与唤醒 / 底栏副键 全部等于行名
  （截图 `docs/device/v11/rename-check-*.png`、`title-check-*.png`）。

### 巡检脚本收口
- 判据收紧成「**标题必须等于行名**」：点空、点在隔壁行、标题不对，三种情况都抓得住。
- 加 `waitForTitle` 轮询：点完立刻 dump 会读到动画中间帧，把「语音与唤醒」读成
  「启用语音识别」、「特权执行」读成「Root」——**判据没错，是取样太早**。
- 已知遗留：整页连跑时偶发点空（后端 dump 与落指之间有位移），逐页 probe 稳定；
  下轮把"点完验证失败就同屏重试一次"补成默认路径。

### 新增工具：会话看门狗
- `docs/tools/session-watchdog.ps1`：看三件事 —— 宿主进程在不在、会话文件在不在长、GUI 通不通。
  - 实测输出：`proc=True(8)  gui=True  bytes=1745766  mtime=21:43:41`
  - 关键事实：**GUI 未鉴权访问返回 401**（`curl` 实测），401 表示"服务在听"，算活着；
    PS 5.1 的 `Invoke-WebRequest` 遇到 401 会抛异常，所以判活要看异常里有没有 `Response`。

## [0.0.0.47] - 2026-11-17（内部版，不对外发布）

### 设置页 15 页全量巡检做完了（HANDOFF §0.5 第 1 条欠账）
- 新增 `docs/tools/sweep-pages.mjs`：每页两条判据 —— **能打开**、**能返回根页**。
  结果落 `build-logs/sweep-0.0.0.47.jsonl`，报告落 `docs/SETTINGS-SWEEP.md`。
- **结果：15 / 15 打开，15 / 15 返回**（真机 TicWatch Pro 5 / 466×466）。
- 顺带查出一个真问题：**4 页的子页标题和根页行名不一致**
  （模型与服务→模型与端点、模型参数→推理参数、语音与唤醒→语音输入、底栏副键→手势与侧键），
  破坏 `docs/SETTINGS-IA.md` §4「页面标题 = 根页行标题」。留给 0.0.0.48 成对改字符串。

### 自动化踩坑记录（都已写进脚本注释，别再踩）
| 坑 | 事实 |
|---|---|
| 步长 | 一格表冠 ≈ 1/8 行；`crown(-5, 8)` 才走一行，按"每步 2 格"写等于原地踏步 |
| 方向 | 访问过后面几页后列表停在下方，目标行在**上方**；"找不到就往下滚"永远看不到（Lux 终端连红三版） |
| 落点 | 不要要求"坐标连续两次一致"再点：边缘缩放动画让坐标一直在微动，门槛会让点击永远不发生 |
| 返回判据 | 从「关于」返回时列表停在底部、分区标题不在屏上；只认标题会误判并再按一次返回，把设置页关掉 |

## [0.0.0.46] - 2026-11-17（内部版，不对外发布）

### 设置页去「假 M3E」第 2 步：整页手搓超椭圆 → 官方 Card 分组卡
旧实现把整页（连分区标题一起）裹进一张自绘的闭合超椭圆：圆角、色调、涟漪全是手搓的，
`SettingsCurveGeometry` 自己算屏幕圆周弦长。这是"像 M3E 但就是不对"最大的一块。

- `SettingsBalloonGroup` → **`SettingsGroupCard`**：外壳交给官方 `Card`
  （`CardDefaults.cardColors` + 官方形状），只做"一组行一张卡"。
- 分组规则改在 `SettingsPageChunking`：连续的 `CARD_ROW` 拼一张卡，分区标题与卡外行动各自断开。
  旧的"一页只用一张主气球"连同它的守卫断言一起改掉。
- 卡片宽度 = `SettingsContentWidth` **176dp = 实测 352px**：与卡片档 hero 卡同一把尺子，
  也符合"所有卡片同一宽度"的硬约束。
- 组内行不再各自按弦长缩放（那会让卡内文字左右不齐），缩放责任下沉给卡
  （`CardEdgeZoom`，本项目 `MinScale = 1.00f`，即只淡出不缩放）。
- 根页四个分区拆成四张卡 + 四个小标题；外层补水平居中，否则小标题被卡片圆角压住。

### 验证
- 真机（TicWatch Pro 5 / 466×466 / `0.0.0.46` versionCode 84）：
  `docs/device/v11/cardgroup-root-top.png`（时钟 3:27 + 小标题 + 352px 圆角卡 + 右缘指示器）、
  `cardgroup-root-scrolled.png`（卡尾与「完成返回」按钮）、`cardgroup-subpage.png`（外观页单选分组卡）。
- 单测 **1291 条 / 187 套件 / 0 失败**（新增 2 条分组规则用例；改写 1 条缩放守卫）。

## [0.0.0.45] - 2026-11-17（内部版，不对外发布）

### 设置页去「假 M3E」第 1 步：补上 Wear 的两个强约定
用户判定「之前的那个假 M3E 设置页面就很丑」。先把"假"拆成可验证的条目，这一轮修最硬的两条。

- **曲面时钟 `TimeText` + 右缘 `ScrollIndicator`**：根页与全部子页都加上了。此前两者都没有 ——
  一块没有时钟、也没有滚动指示的表，圆角做得再像也不会像官方应用。
- **查清一个真坑**：官方 `ScreenScaffold` 的 `timeText` 槽**只登记不绘制**。
  material3 1.7.1 源码 `ScreenScaffold.kt` L705–L769 把它写进 `LocalScaffoldState`，
  真正画它的是 `AppScaffold`；本项目没有 `AppScaffold`（聊天页是自绘壳），所以传进去等于没传。
  改成显式叠层，槽位留注释说明原因 —— 这个坑下次谁再传一次都会踩。
- **顶部内容边距对齐官方**：`ScreenScaffoldDefaults.contentPadding` 垂直方向 = 屏高 10%，
  233dp 屏即 24dp，正好等于既有 `SettingsLayoutMath.TopScrimDp`。子页原来给 4dp，标题挤在钟带里。

### 验证
- 真机（TicWatch Pro 5 / 466×466 / density 320 / `0.0.0.45` versionCode 83）：
  `docs/device/v11/after-clock-root-balloon.png` 有曲面时钟 `2:31` 与右缘指示器；
  `after-clock-subpage.png` 有 `2:48` 与指示器。
- 改造前四套风格基线一起留档：`style-{balloon,square,google_card,terminal}.png`。
- 单测 **1289 条 / 187 套件 / 0 失败**（旧文档写的 188 套件是错的，实测 XML 只有 187 个）。

### 新增工具
- `docs/tools/watch.mjs`：`enterSettings()`（处理"应用会恢复上次停留页面"这个坑）、
  `shot()`、`tapText()`（安全带内点击 + 自动滚动重试）、`dumpNodes()`。

## [未发布] - 2026-11-15 · 上下文插件实测

用户装了"自己管上下文"的插件，本轮实测：

| 能力 | 结果 |
|---|---|
| `fact_register` 钉事实 | ✅ 可用（4 条真事实 + 9 条容量测试） |
| `fact_register({revoke: tag})` 按标签撤销 | ✅ 可用（撤销两组） |
| 分页工具命名空间 | ❌ 本会话没有（`paged namespaces: (none)`） |
| 手动触发压缩 / 查上下文占用 | ❌ 工具面里没有，压缩由 harness 自动执行 |
| 压缩是否真的在跑 | ✅ 有证据：本会话开头的 checkpoint 就是把更早一段压成了摘要 |

已把 4 条项目不变量钉进工作上下文（硬约束 / 版本 / 单测基线 / 设置页 DSL 规则），
用法写进 `docs/CONTEXT.md`，以后每轮开工顺手核对版本与基线两条是否还准。

## [未发布] - 2026-11-15 · context-governor 插件排查

用户装了 `@local/context-governor`，要求实测。结论：

| 检查 | 结果 |
|---|---|
| 插件包 | ✅ `~/.dsh/profiles/desktop/node_modules/@local/context-governor`（index.js 20KB，README 说明齐全） |
| bundle 注册 | ✅ 在 `profiles/desktop/package.json` 的 `dsh.profile.bundles` 里 |
| 组合接线 | ✅ 插件自带 `cordis.patch.yml` 插入 `{ id: context-governor }`，用户 patch 未禁用 |
| 它提供什么 | `manage_context` 工具，9 个 action（view/usage/compact/人设/片段/变量/rollback） |
| **本会话可用性** | ❌ `tools.manage_context is not a function`；分页命名空间为空 |

原因：工具面在会话启动时固定，本会话是**恢复**创建的，早于插件安装。**开新会话才会加载**。

替代手段（已实测）：`fact_register` 钉事实（4 条已钉）；量上下文压力直接看
`~/.dsh/sessions/<ws>/<sid>/session.v4.jsonl.zstd` —— 本会话 **38 MB / 163k 行**。

## [0.0.0.44] - 2026-11-15（内部版，不对外发布）

### 上一轮"验证失败"的真相：不是焦点丢失，是我点错了行
连续两轮我把"验证失败"记成"列表丢焦点、表冠失效"。这轮查明：**我的点击落在了「输入命令…」这一行上，
弹出了文本对话框盖住整页** —— dump 里全是对话框节点（命令 / echo lux / 取消 / 完成），
看起来就像"页面卡在一屏不动"。表冠一直在正常工作，它滚的是弹层背后那一页。

**错误诊断已从 `docs/CONTEXT.md` 改正**（那份文件是唯一记忆源，留着错的比没有更糟）。

### 补上的验证（真机，一次点中）
跑 `echo lux` → 点「存为快捷位：echo lux」→ **同一屏立刻出现**：
`我的快捷位 | 存为快捷位：echo lux | 保存 | echo lux | 删除 | 快捷命令`
→ **保存后不重新进页就立刻更新，成立**。

### 新增 `docs/tools/nav.mjs`
把"点行 / 滚表冠 / 关弹层"做成可复用动作，并**把这次的教训写成代码**：
- `tapRow(label)`：点完检查是否弹出弹层，弹了就关掉并返回 false（**点了不算成功，要验证效果**）；
- `overlayOpen()` / `closeOverlay()`：以"取消 + 完成"同时出现作为弹层判据；
- `crownTo(label, …)`：朝一个方向滚到目标出现为止（一次 su 批量注入，避免 toast）。

单测 **1289 全绿**。

## [0.0.0.43] - 2026-11-15（内部版，不对外发布）

### 修根因：动态列表不再受"收集期"约束
把「我的快捷位」从 `saved.forEach { row { } }`（收集期求值）改成 **`standaloneRow` 内部渲染整个列表**：
`standaloneRow` 的内容在渲染时求值，因此保存/删除后列表能立刻反映，不必重新进页。

过程中又踩到一个更底层的事实并修正：
**SharedPreferences 的写入不会触发 Compose 重组** —— 我第一版在渲染路径里直接读 `preferences.luxSavedCommands`，
结果删除了偏好（`<string name="lux_saved_commands"></string>`）但界面上那行还在。
现在渲染路径读的是 Compose 状态 `saved`（进入页面时由偏好初始化，每次增删同步更新）。

### 验证到哪一步
- **已验（真机）**：保存 → 偏好出现 `lux_saved_commands`；删除 → 偏好清空；
  已保存的快捷位进入页面即渲染（`我的快捷位 · 先跑一条命令 · echo lux · 删除`）。
- **未验**：「保存/删除后不重新进页就立刻更新」——本轮自动化导航在弹层交互后丢了列表焦点，
  表冠注入失效，滚不到目标行。已把这个坑写进 CONTEXT.md，并记下待验证的恢复办法（先 tap 列表区域再注入）。

单测 **1289 全绿**。

## [0.0.0.42] - 2026-11-15（内部版，不对外发布）

### Lux v4 补验 + 一个已知限制的取舍
**上一轮欠的那一跳补上了**（真机实测）：
1. 点 `echo lux`（坐标 142,344）→ 命令执行；
2. 「我的快捷位」区的行变成 `存为快捷位：echo lux`；
3. 点它 → `shared_prefs` 出现 **`<string name="lux_saved_commands">echo lux</string>`**。

### 试过但退回的写法（记录理由）
为解决"刚保存的那条不会立刻出现在列表里"，我试了"固定登记 6 个位置 + 渲染时读状态"。
结果每个空位仍占一张卡的高度（≈48dp × 5 = **240dp 空白**），比"晚一次刷新"更糟，已退回。

**当前已知限制（明写在代码注释里）**：`saved.forEach` 在收集期求值，
所以刚保存的那条要**下次进入本页**才出现在列表里；偏好本身已经落盘，数据不会丢。

### 这一轮的方法论收获
设置页 DSL 的"收集期"约束有**两种坏解法**：
- 用 `if` 把行整个拿掉 → 行永远不出现（第 41 轮踩的）；
- 固定登记 N 个位置 + 空位渲染空内容 → 空位照样占满一张卡的高度（这一轮踩的）。
真正的解法要么是"一行内容内部自己渲染列表"，要么是给 DSL 加一个"可变行数"的能力 —— 留给后面。

单测 **1289 全绿**。

## [0.0.0.41] - 2026-11-15（内部版，不对外发布）

### Lux v4：我的快捷位（用户自己攒的常用命令）
- 新增偏好 `lux_saved_commands`（换行分隔：可读、可手改、天然去重）。
- 纯逻辑 `LuxTerminalPolicy.savedOf / withSaved / withoutSaved`：
  **重复保存是 no-op**（同一个命令不占两个位置）、上限 **6**（再多这页就成第二个设置页）、空白忽略、顺序即添加顺序。
- 页面：跑过命令后，「我的快捷位」区出现 `存为快捷位：<上一条命令>`；点保存即落盘；
  已保存的命令各占一行，点行即删除。

### 验证到哪一步（如实写）
- **已验**：4 条策略测试全绿；真机上一次看到 `存为快捷位：echo lux` 行渲染正确。
- **未验**：点保存 → 偏好落盘这一跳**没在真机上点中**（后续几次导航里「我的快捷位」区都滚不到，
  列表焦点在对话框交互后丢了）。下一轮开局第一件事：冷启动 → Lux → 跑一条 → 直接点保存行 → 读偏好。

合计 **1289 全绿**。

## [0.0.0.40] - 2026-11-15 · **第 4 个对外发布点**（内部版 0.0.0.31–0.0.0.40 汇总）

### 这十个内部版做了什么
- **设置页从"能用"做到"会解释自己"**：20 个滑杆全部带第二行说明（这个数字改变什么）；
  清除 3 条死文案；`SettingsSliderRow` 新增 `secondary`。
- **信息架构收口到底**：底栏副键与特权执行从「关于」内层提升为一级，「关于」变成单页；
  根页 15 行（4/3/4/4）。`docs/SETTINGS-IA.md` 全部改用界面真实文案。
- **Lux 终端 v1→v3**：快捷命令 → 自由输入 → 历史 + 清屏，真机跑通 `echo lux`。
- **网页控制台首次真正验收**：接口 200/401/POST 写入、10 页 63 行、三档皮肤真浏览器截图。
- **验收矩阵补齐两条**：表冠 × 三条滚动实现、触摸拖拽/fling/下拉退出。
- **发布流程固化**：`docs/tools/release-check.mjs` 一条命令跑 7 项判据。

### 新增的四条结构性守卫（累计 17 条）
1. `ScaleAnimationOvershootGuardTest` —— scale 动画不许过冲（硬约束变成 CI）
2. `ConsoleKeyDriftGuardTest` —— 网页键表不许与服务端漂移
3. `SettingsStringUsageGuardTest` + `SettingsRowExplanationGuardTest` —— 死文案 / 说明行
4. `SettingsIaDocNamingGuardTest` + `ContextDocFreshnessGuardTest` —— 文档名=界面名、记忆源不许过期

### 发布点回归（**第一次用一条命令完成**，2026-11-15 09:08–09:14）
`node docs/tools/release-check.mjs` → **7/7 通过，337 秒**
| 判据 | 结果 |
|---|---|
| clean 构建 | BUILD SUCCESSFUL in 2m46s |
| 全量单测 | **1285 条 / 187 套件 / 0 失败** |
| APK | 3.58 MB |
| 真机安装 | versionCode 78 / versionName **0.0.0.40** |
| 四分区按序 | 让它答得好 → 让它听懂我 → 让它能做事 → 连得上 |
| 卡片几何 | `14..341` / `358..430` —— 不重叠，间隙 17px ≈ 8dp |

**未覆盖**：完整对话流（需外网模型）、语音识别端到端（需麦克风）、网页三档皮肤之外的交互路径。

## [0.0.0.39] - 2026-11-15（内部版，不对外发布）

### 发布前自检固化成一条命令：`docs/tools/release-check.mjs`
前三个发布点（10 / 20 / 30）都是临场手打：现编 adb 坐标、现写像素扫描，还漏过"没验四分区名字"。
现在一条命令跑完 7 项判据：

| # | 判据 | 本轮实测 |
|---|---|---|
| 1 | clean 构建成功 | BUILD SUCCESSFUL in 2m48s |
| 2 | 全量单测全绿 | **1285 条 / 187 套件 / 0 失败** |
| 3 | APK 产出 | 3.58 MB |
| 4 | 真机安装 | versionName=0.0.0.38 |
| 5 | 进入设置页 | ok |
| 6 | 四分区按序 | 让它答得好 → 让它听懂我 → 让它能做事 → 连得上 |
| 7 | 卡片几何采样 | `14..341, 358..430`（两段分离，间隙 17px ≈ 8dp） |

- 全量跑一次 **339 秒**（构建 168s + 走查约 170s）。
- 支持 `--skip-build` / `--skip-device` 分段跑；任何一项失败 exit 1。
- 修掉两个自己写脚本时犯的错：把 Kotlin 的 `substringAfter` 写进了 JS；
  `screencap` 用文本编码读会把 PNG 写坏（必须 `encoding: 'buffer'`）。

单测 **1285 全绿**（本轮无新增测试，脚本本身靠实跑验证）。

## [0.0.0.38] - 2026-11-15（内部版，不对外发布）

### `docs/CONTEXT.md` 全量重写 + 新鲜度守卫
翻这一页时发现它**落后 15 个版本**：写着 `0.0.0.22` / 1266 单测，
而 P1 验收矩阵、MotionScheme、Lux 终端三项它都标着"未做"——**实际全做完了**。

- §0.5 重写为当前真相：`0.0.0.38` / 1285 单测 / 三个发布点 tag / P0–P3 全部收口（每项写明"收口"指什么）/
  16 条结构性守卫 / 两条会咬人的规则（DSL 收集期、文档名=界面名）/ 五个环境坑。
- 新增 `ContextDocFreshnessGuardTest` 两条：
  1. **CONTEXT.md 必须提到当前 `versionName`** —— 升版本不更新这页，CI 红；
  2. CONTEXT.md 里不许再留着"MotionScheme 未接入 / Lux 终端未做 / 验收矩阵还没做完"这类旧说法。
- 这条守卫立刻给自己上了一课：本轮的版本号提升到 `0.0.0.38` 时，必须**同一次提交**里把 CONTEXT.md 一起改，
  否则构建就红。**让流程约束流程**，而不是靠记性。

单测 **1285 全绿**。

## [0.0.0.37] - 2026-11-15（内部版，不对外发布）

### 根治"文档名 ≠ 界面名"：IA 文档改用界面真实文案 + 守卫
我连续三轮按文档里的名字去真机上找入口、连续三轮误判"没出现"：
`远程控制台`→`网页控制台`、`外观与点阵`→`外观与动效`、`手势与侧键`→`底栏副键`、`权限`→`特权执行`。

这一轮把 `docs/SETTINGS-IA.md` 里所有页面/行名**换成界面真实文案**（含 markdown 粗体形式）：
`模型与端点`→`模型与服务`（6 处）、`生成参数`→`模型参数`（10 处）、`导入导出`→`配置导入导出`（4 处）等。

### 新增 `SettingsIaDocNamingGuardTest`
- 判据：**IA 文档必须提到 19 个页面/分区的界面真实文案**（键 → `values-zh` 取值 → 文档是否包含）。
- 它立刻抓出一处真空白：**IA 文档里根本没有「Lux 终端」** —— 文档写于 Lux 之前，一直没补。
  已补进 §2.4 的 S3 表（含"动作、无持久化键、转录只活在内存"的形态说明）。
- 以后改名：要么同步改文档，要么这条红。

单测 **1283 全绿**。

## [0.0.0.36] - 2026-11-15（内部版，不对外发布）

### IA 最后一条：侧键与权限从「关于」内层提升到一级（IA §5.1 收口）
- 根页 S2「让它听懂我」新增 **底栏副键**（`settings_side_key`）行；S3「让它能做事」新增 **特权执行**
  （`settings_privilege_title`）行。两者原来都藏在「关于 → 内层页」里，属于"高频首查项被埋"。
- 「关于」从此是**单页**：只留版本号与重看引导，内层机制整个删掉（`NoInnerPage` 那两个分支也已移除）。
- 根页行数 12 → **15**（S1 4 / S2 3 / S3 4 / S4 4），四个分区标题与顺序不变。
- 真机核对：S2 = `语音与唤醒 · 底栏副键 · 外观与动效`；S3 = `工具与 MCP · 安全策略 · 特权执行 · Lux 终端`；
  关于页只剩 `APP 关于 0.0.0.35`。

### 又一次命名教训（同一个坑第三次）
- 文档里叫「手势与侧键」「权限」，界面实际叫 **「底栏副键」「特权执行」**。
  我按文档名字去 dump 里找，两次都判定"没出现"，实际两行都在。
- 这已经是第三个（前两个：网页控制台/远程控制台、外观与点阵/外观与动效）。
  下次核对 IA 落位时，**先 grep 界面真实文案再断言**。

单测 **1282 全绿**。

## [0.0.0.35] - 2026-11-15（内部版，不对外发布）

### 设置页复审（续）：16 个滑杆补齐"这个数字改变什么"
上一轮只补回了三条丢失的说明。这轮把**全部 20 个滑杆**过了一遍，给剩下 16 个补上第二行：

| 滑杆 | 说明 |
|---|---|
| 采样温度 | 越高越发散，越低越稳 |
| 最大输出 Token | 单次回复的字数上限 |
| 核采样 top_p | 候选词范围，越低越保守 |
| 流式合帧间隔 | 越长越平滑，也越滞后 |
| 空转收口轮次 | 同一份调用重复几次就停手 |
| 上下文轮数 | 每次请求带多少轮上下文 |
| 压缩阈值 | 历史超过多少轮开始压缩 |
| 体检探测超时 | 每次探测最多等多久 |
| 令牌错误上限 | 令牌错几次就锁住这个来源 |
| 子代理层数 | 0 表示彻底关闭子代理 |
| MCP 工具清单页数 | 工具清单最多翻几页 |
| 工具结果上限 | 单次工具结果回灌给模型的上限 |
| 审批放行阈值 | 滑到多远才算放行 |
| 播报音量 | 播报的音量 |
| 表冠滚动增益 | 表冠转一格走多远 |
| 点阵间距 | 点阵的间距 |

- 真机核对（模型参数页）：`采样温度 0.8 / 越高越发散，越低越稳`、
  `最大输出 Token 2048 / 单次回复的字数上限`、`核采样 top_p 1.00 / 候选词范围，越低越保守` 三行全部可见。

### 新增 `SettingsRowExplanationGuardTest`
- 钉住这 16 个滑杆**必须保留自己的 `_secondary` 文案**。判据：搬家时把 secondary 一起带走，
  否则 CI 红。上一轮的丢失事故正是这么发生的（文案还在、界面没带）。

文案总数 462 → **478**（两份一致）。

## [0.0.0.34] - 2026-11-15（内部版，不对外发布）

### 设置页复审：滑杆终于能"解释自己"，并清掉三条死文案
- `SettingsSliderRow` 新增可选 `secondary`（走与开关行/导航行同一条 `SettingsSubtitleLine` 渲染路径）。
  此前滑杆只有"标签 + 数值"——**最需要解释的恰恰是"这个数字改变什么"**。
- 补回三条在第 14 轮搬家时丢掉说明：请求超时（单次请求超过这个时长就放弃）、
  自动重试（掉线或服务端报错时再试一次）、重试退避（重试之前先等这么久）。
- 真机核对三行全部可见：`请求超时 40s / 自动重试 1× / 重试退避 800ms` 各带一行说明。

### 死文案清理 + 新增守卫
- 一次扫描发现 6 条 `settings_*` 文案没有任何 Kotlin 引用；删掉三条真死的
  （`settings_prompt_custom_value`、`settings_card_console_port`、`settings_section_voice_tools`），
  另三条就是上面补回去的说明。
- 新增 `SettingsStringUsageGuardTest`：**每个 `settings_*` key 都必须被代码引用**，否则 CI 红。
  这类漏网不会报错、不影响构建，只会让下一个人在"这名字是干什么的"里迷路。

文案总数 465 → 462（两份一致）。

## [0.0.0.33] - 2026-11-15（内部版，不对外发布）

### Lux 终端 v3：命令历史 + 清屏，以及一条踩出来的 DSL 规则
- `LuxTerminalPolicy.appendHistory`：**与最后一条相同则不重复记**（连点两次同一条命令是常见操作），
  上限 20 条；空白命令不入历史。
- 页面新增「历史」行（显示最近一条命令，点了再跑一次）与「清空转录」按钮（空转录时 disabled，不是隐藏）。

### 一条架构约束（写进 CONTEXT.md，因为它会反复咬人）
设置页的行在**收集期**注册（`SettingsSubPage` 调一次 `registry.rows()`）。所以：
- **不能用 `if (状态) { row { } }` 增删行** —— 首帧读到的状态会被永久固化，
  我第一版就是这么写的，真机上「历史」与「清空转录」两行**根本没出现**；
- 正确做法：**总是注册行**，把状态读取放进行内容的 lambda（`title = history.lastOrNull() ?: …`），
  或用 `enabled =` 表达可用性。
- 验证：真机上跑一次命令后，历史行从空态变成 `echo lux → 再跑一次`。

合计 **1280 全绿**（appendHistory 两条新测试）。

## [0.0.0.32] - 2026-11-15（内部版，不对外发布）

### P0 第三项"字体过扁"：审计完成，**结论是先不改**
- 量了排版真值：主题五档行高倍数是 1.33 / 1.33 / 1.33 / 1.25 —— 比 Material 自家的
  `bodyMedium` 14/20 = **1.43** 更紧。用户最初说的"扁"，最可能就是这个。
- 于是**试了一次**：把行高统一拉到 1.42。结果 `ApprovalBandMetricsTest` 立刻红 ——
  它按 `titleMedium.lineHeight` 推导审批带头部高度，行高一变整套几何失配。
- 这说明它不是"改个数字"，而是**需要重新逐屏验收的版式手术**。已回退，1278 全绿保持。
- 审计结论写进 `docs/DESIGN-SPEC.md`（含三档对照表与"要改的正确顺序"）。

### 为什么这算完成而不是拖延
- 用户 01:15 已明确"现在没有"这个问题；本轮把它从"没有依据的直觉"升级为**有数值、有取舍、有回滚依据的结论**。
- 真要改，正确顺序已写在文档里：先改几何测试的推导 → 再上机重量审批带/设置行/卡片间隙 → 最后动 typography。

## [0.0.0.31] - 2026-11-15（内部版，不对外发布）

### 网页控制台三档皮肤：第一次用真浏览器渲染验证
工具链：headless Edge 157 + CDP（`setDeviceMetricsOverride` + `captureScreenshot`），
页面经 `adb forward` 指向手表，令牌注入 `localStorage`。

| 档位 | 视口 | 骨架 | body | 横向溢出 |
|---|---|---|---|---|
| 手表 | 400×780 | 底部标签栏 | 400×1935 | 无 |
| 手机 | 700×900 | 顶部标签 | 685×1576 | 无 |
| 电脑 | 1280×900 | 左侧栏 + 10 项导航 | 1265×1542 | 无 |

- **三档都带真实设备状态**：顶栏 `当前设备 ROOT · 端口 8080 · 令牌 有效`，表单里是手表上真实的
  `https://api.deepseek.com` / `deepseek-v4-pro` —— 网页 → 手表的读取链是通的。
- 截图存 `docs/device/v11/console-{watch,phone,desktop}.png`，结论写入 `docs/ACCEPTANCE-MATRIX.md` §2.7。

### 一个工具链坑（两次踩同一处）
- `.mjs` 里不能用 `require`（Node 24 严格 ESM）；headless Edge 用 `start /B` 拉起的进程
  会在本次 `run_code` 结束后被杀 —— **必须"启动浏览器 + 截图"在同一个进程里做完**。
  这两条都写进本文，下一轮直接用脚本 `build-logs/console-shot.mjs`。

## [0.0.0.30] - 2026-11-15 · **第 3 个对外发布点**（内部版 0.0.0.21–0.0.0.30 汇总）

### 这十个内部版做了什么
- **设置补到 20 / 20**：空转收口轮次、记忆单条上限、控制台锁定窗口、子代理层数、体检探测超时、控制台防爆破上限
  （此前已有 14 项）。每项都有单一消费点、落盘守门与"改偏好行为就变"的断言。
- **P1 验收矩阵补完两条**：表冠 × 页面（三条滚动实现实测）、触摸 × 页面（拖拽 / fling / 下拉退出）。
- **P2 三项全齐**：官方 `MotionScheme.standard()` 接入；主气球的品牌竖线（注释承诺过、实现里没有的那条）；
  连续列表（相邻助手回复共享一个面）。
- **P3 补完**：Lux 终端 v1 + v2（快捷命令 + 自由输入，真机跑通 `echo lux`）；
  网页控制台首次被真正跑起来验收（200/401/POST 写入 + 10 页 63 行）。
- **两条硬约束升级成 CI**：scale 不许过冲（`ScaleAnimationOvershootGuardTest`）、
  网页键表不许漂移（`ConsoleKeyDriftGuardTest`）。

### 发布点回归（2026-11-15 07:40–08:00 本机真实执行）
| 项 | 结果 |
|---|---|
| 干净构建（clean + assembleRelease） | **BUILD SUCCESSFUL**，APK **3.58 MB** |
| 单测 | **1278 / 183 套件 / 0 失败** |
| 真机安装 | versionCode **68** / versionName **0.0.0.30** |
| 卡片几何 | 竖向连通段 `48..392` 与 `409..428` —— 卡片**不重叠**，间隙 17px ≈ 8dp |
| 触摸 | 根页与子页：拖拽响应、fling 继续滚、回顶后下拉（根页退到聊天、子页退一层） |
| 网页控制台 | / 200、css 200、js 200、无令牌 401、带令牌 POST 写入成功 |

**未覆盖**：聊天页的完整对话流（需要外网模型）、语音识别端到端（需要麦克风）、
iOS/桌面的网页三档皮肤只看了 CSS 存在性没做真浏览器截图。

## [0.0.0.29] - 2026-11-15（内部版，不对外发布）

### P3：网页控制台第一次被真正跑起来验收（此前从未验证过）
用 `adb forward tcp:8080` + `curl` 直接在真机上打接口，全部为实测输出：

| 检查 | 结果 |
|---|---|
| `GET /` | **200**，5560B，含 `<title>` |
| `GET /app.css` | **200**，43181B |
| `GET /app.js` | **200**，75807B |
| `GET /api/settings` 无令牌 | **401**（鉴权闸门有效） |
| `POST /api/settings`（带真实令牌，`{"temperature":0.8}`） | **`{"ok":true, "settings":{…"temperature":0.8…}}`** —— 写入并回传快照 |
| 页面结构 | **10 页 / 63 行**，与 `ConsoleSettingsParityGuardTest` 的期望一致 |

- 一个**设计事实**（不是 bug）：settings 接口只支持 **POST**，网页端读取也走 POST `{}`。
  我第一次用 GET 拿到 `method_not_allowed` 曾以为坏了 —— 已记在这里，省下下次的误会。

### 新增 `ConsoleKeyDriftGuardTest`
- 钉住「网页 `SERVER_KEYS` == 服务端可写键 + 6 个契约别名」。
  两份清单一旦漂移，用户点保存、服务端什么都没收到、界面还显示"已保存" —— 最典型的静默失效。
- **本次核对结果：两边完全一致**（48 = 42 可写 + 6 别名），说明没有历史漂移；现在这条不一致会让 CI 红。

### 一处过期注释
- `app.js` 头部写着"结构：9 页"，实际 10 页。已改。这类注释会让人（和我）误判覆盖面。

合计 **1278 全绿**。

## [0.0.0.28] - 2026-11-15（内部版，不对外发布）

### Lux 终端 v2：自由输入
- 新增「运行命令」区：点输入行打开 `SettingsTextDialog`（复用设置页那套草稿式对话框，确认才执行、取消一个字符都不跑）。
- 命令清洗抽成 `LuxTerminalPolicy.parseCommand`：只 trim，**不替用户改写引号与管道**
  （改写比不执行更糟）；空串与纯空白一律返回 null，不会把空命令发给 shell。
- 真机端到端：打开对话框 → 输入 → 收起输入法 → 点「完成」→ hero 计数 **0 → 1 / 12**、
  输入行回显上一条命令、转录里出现执行记录。

### 一条测试脚手架的坑（记下来省下一次）
- 对话框在**输入法弹出后布局会上移**，按弹出前的坐标点「完成」会点在输入框上
  （我因此把 `y` 打成了 `yn`）。可靠顺序是：**先 `keyevent 4` 收起输入法 → 重新 dump → 再点按钮**。
  这条已写进本文，下次不再试错。

合计 **1277 全绿**（新增 `parseCommand` 两条测试）。

## [0.0.0.27] - 2026-11-15（内部版，不对外发布）

### P3：极轻量 Lux 终端（v1，第一次落地）
**形态先按最保守的解释做出来，并写明边界**（"极轻量"的具体含义）：
- **没有 PTY、没有 ANSI 解析、没有交互式 stdin** —— 发一条命令、收一次输出。
- 输出按用户**已经设过的** `tool_result_max_chars` 截断（复用同一个预算，不新增旋钮）；
  转录只留最近 12 条（`LuxTerminalPolicy.MAX_ENTRIES`）。
- 命令仍过 `ShellPolicy.validate` 的同一道闸门：那道闸门是挡**模型**的，不是挡主人的，
  但共用一份规则能避免"终端里能干、工具里不能干"的割裂。
- 执行走 `DirectShell`（本进程权限兜底后端），超时用设置项 `shell_command_timeout_ms`。

**入口**：根页「让它能做事」末行 → 「Lux 终端 → 命令面板」；页内是四个无副作用快捷命令
（`echo lux` / `getprop ro.build.version.release` / `uptime` / `dumpsys battery | head -4`）+ 转录面板。

**真机端到端**：点 `echo lux` → hero 计数 0 → **1 / 12**，转录里出现 `❯ echo lux / lux`。

- 新增 `LuxTerminalPolicyTest` 5 个（含"成功但无输出"与"失败只报退出码"两种容易误读的形状）；
  合计 **1275 全绿**。

## [0.0.0.26] - 2026-11-15（内部版，不对外发布）

### P2：连续列表 —— 同一位说话者的相邻两条共享一个面
- `AgentSquareBlurBox` 新增 `groupedWithPrevious` / `groupedWithNext`：相接的那一侧圆角收成 **4dp**，
  外侧保持完整卡片圆角。
- 项间距从统一的 12dp 改成 **组内 2dp + 组间补足 10dp**（合计仍是 12dp，跨组观感不变）。
- 判定只看"上下一条是不是也是 `AssistantTurn`"，逻辑集中在 `ConversationList` 一处。
- **真机量化验证**：x=233 竖向扫描，两条助手回复现在连成一段 `66..359`（293px 连续，阈值 45），
  改造前它们是两张各自收口的卡。

### 一个刻意的取舍
- 组内缝取 **2dp 而不是 0**：完全贴合会看起来像"一张卡被切成两半"，
  2dp 保留"这是两条消息"的暗示，又不至于断开成一个新块。

单测 **1270** 全绿（纯视觉改动，AGENTS.md 的 TDD 豁免范围）。

## [0.0.0.25] - 2026-11-15（内部版，不对外发布）

### P2：主气球的品牌重心 —— 左边那条竖线，终于画出来了
- `AgentSquareBlurBox`（助手回复卡）的左缘新增 **2dp 强调色竖线**（`drawBehind` 绘制，不占布局）。
  它是整页唯一属于品牌的视觉锚点，也是"这是回答、不是你说的"最便宜的标记：比整卡染色省、比加图标不抢字。
- **发现方式**：读代码时看到那段注释早就写着"左侧一条 2dp 竖线就够标记这是回答"，
  但实现里根本没有这条线 —— **注释描述了一个不存在的功能**。已按注释把它补上。
- 真机量化验证：y=233 横切，x=32–34 的像素是 `rgb(171,133,219)`（强调色），x=36 起才是卡片底色。
- 文本起始内边距同步让出这条线（`Wear.Space.Md + 2dp + 4dp`），文字不贴线。
- 纯视觉改动（AGENTS.md 的 TDD 豁免范围），单测 **1270** 仍全绿。

## [0.0.0.24] - 2026-11-15（内部版，不对外发布）

### P2：接入官方 `MotionScheme`（Wear M3 1.7.1 自带）
- `WearMaterialTheme` 的 `MaterialTheme(...)` 现在显式传 `motionScheme = MotionScheme.standard()`。
  SwitchButton / Slider / Picker 等官方组件内部的时间曲线从此由这套令牌统一决定。
- **为什么选 standard 不选 expressive**：expressive 的 spatial 档带过冲，而"改 scale 不许过冲"是硬约束；
  组件内部谁在用 scale 我控制不了，所以整棵树默认必须是不过冲的那套。

### 顺手抓到两处硬约束违规（都是真机看不出来的那种）
| 位置 | 原来 | 问题 | 现在 |
|---|---|---|---|
| `Wear.Motion.micro()` | 阻尼 **0.85** | 被 `AnimatedMessageCard` 的**入场收缩 scale** 复用 → 卡片放大一下再缩回 | 阻尼 **1.0** |
| `BottomInteractiveBlock` 主键按压 | 阻尼 **0.62** | 直接动画 **scale**，按下时鼓过头再回弹 | 阻尼 **1.0** |

### 把硬约束变成 CI：`ScaleAnimationOvershootGuardTest`（4 个测试）
1. 动画热点文件里**不允许**出现 `dampingRatio = 0.x`，除非命中白名单且理由与 scale 无关
   （目前白名单只有两条：箭头旋转 ×2、点阵位移幅度 ×1）；
2. `Wear.Motion.micro()` 必须是 1.0；
3. 消息卡入场必须走不过冲令牌；
4. 主题必须用 `MotionScheme.standard()`。

合计 **1270 全绿**；真机冒烟：聊天页与设置页均正常，无 FATAL。

## [0.0.0.23] - 2026-11-15（内部版，不对外发布）

### P1 表冠 × 页面：三条滚动路径全部实测
全站只有三条不同的滚动实现，逐条真机测过（注入表冠 → dump 文本签名比对）：

| 页面 | 顶部继续向上 | 向下滚 | 回顶 | 回顶后与起始签名一致 | 进程存活 |
|---|---|---|---|---|---|
| 设置根页 | 不动 ✅ | 响应 ✅ | 响应 ✅ | 是 ✅ | ✅ |
| 设置子页（`SettingsSubPage` 共用，代表页=网络与请求） | 不动 ✅ | 响应 ✅ | 响应 ✅ | 是 ✅ | ✅ |
| 聊天页（库自带 `rotaryScrollable`） | 到底/到顶不动 ✅ | 到底后不动 ✅ | 回看历史 ✅ | — | ✅ |

其余 16 个子页与第 2 行同一条代码路径，因此行为一致。矩阵写入 `docs/ACCEPTANCE-MATRIX.md` §2.5。

### 命名统一：网页控制台 = `REMOTE_CONSOLE`
- 界面「网页控制台」与文档/枚举「远程控制台」不一致，导致我为一个入口白找了三轮（约 15 分钟）。
  现在文档统一用界面名，并在 `docs/SETTINGS-IA.md` 顶部加命名对照表。

### `docs/CONTEXT.md` 全量刷新（它已经落后 20 个版本）
- 原文停在 `0.0.0.2` / 1189 单测；现在记录 `0.0.0.22` / 1266 单测、P0–P3 真实状态、
  20 个设置的完成度、以及四条每次都会踩的环境坑（含"表冠 su → Magisk toast"的应对）。
- 这一页是跨轮恢复的唯一入口，**它过期等于我失忆**。

## [0.0.0.22] - 2026-11-15（内部版，不对外发布）

### 第 18–20 个真实设置 —— **20 个设置目标达成**
| 键 | 范围 | 默认 | 消费点 |
|---|---|---|---|
| `memory_max_fact_length` | 40..240 字 | 120 | `MemoryFactBook.add(...)` 的超长拒绝（写入方 remember_fact 传偏好） |
| `console_lockout_minutes` | 1..60 分钟 | 1 | `ConsoleBridge` 构造 `ConsoleAuthThrottle(lockDurationMs = ...)` |
| `subagent_max_depth` | 0..1 层 | 1 | `SubAgentPolicy.depthAllowed(depth, maxDepth)`（0 = 关闭，工具不出现） |

- 记忆上限在**两处**夹取（偏好与写入判定），因为 remember_fact 也会被工具层直接调用；
  子代理层数的 0 档等于彻底关闭，不是"灰显"。
- 真机核对：网页控制台页「锁定窗口 → 1 分钟」。
- `SettingsPersistenceTest` 覆盖 **20 / 20** 项；新增 `LimiterSettingsTest` 7 个；合计 **1266 全绿**。

### 一处命名不一致（记下来，下轮统一）
- 根页/子页里这个入口叫「网页控制台」，而 IA 文档与代码枚举叫「远程控制台」。
  我这轮按界面文字找了两轮才找到，说明名字不一致会真实地浪费人的时间。

## [0.0.0.21] - 2026-11-15（内部版，不对外发布）

### 新增第 17 个真实设置：空转收口轮次（`idle_spin_stop_after`）
- 范围 2..8，默认 **5**；同一份调用连续重复到第几轮停手。
- `AgentLoopPolicy.idleSpinDecision` 增两个参数（`stopAfter` / `nudgeAfter`）并在内部夹取；
  `IdleSpinDetector` 与 `SubAgentRunner` 都收同一份阈值，**主循环与子代理不会各按一套判据跑**。
- **提示轮次跟着停手轮次走**：停手调到 3 时，提示自动落到第 2 次（"永远在停手前 2 轮"）。
  这条是写测试时发现的 —— 我最初只移动了停手，提示还钉在 3，结果调早之后"提示与收口同一轮发生"，等于没有提示。
- UI：生成参数页「空转收口轮次」滑杆；真机实测一屏内可见
  `流式合帧间隔 32 毫秒 · 突发判定字数 12 · 空转收口轮次 5`。
- `SettingsPersistenceTest` 覆盖扩到 **17** 项；新增 `IdleSpinStopTest` 6 个；合计 **1259 全绿**。

## [0.0.0.20] - 2026-11-15 · **第 2 个对外发布点**（内部版 0.0.0.11–0.0.0.20 的汇总）

### 这十个内部版做了什么
- **真实设置从 4 个做到 16 个**（每个都有单一消费点 + 落盘守门 + 守卫测试）：
  `stream_emit_interval_ms` · `stream_emit_chars` · `shell_command_timeout_ms` · `approval_allow_threshold` ·
  `tts_speech_rate` · `tts_volume` · `tts_queue_mode` · `rotary_scroll_gain` · `haptic_amplitude` ·
  `tool_result_max_chars` · `bottom_bar_auto_hide_ms` · `sampling_top_p` · `mcp_max_tool_pages` ·
  `subagent_read_timeout_ms` · `net_diag_timeout_ms` · `console_max_failures`
- **信息架构收口**：根页按意图分四区（让它答得好 / 让它听懂我 / 让它能做事 / 连得上）；
  新增「网络与请求」页；提示词与记忆、外观与动效改名；网络体检从「关于」改挂到连接参数旁。
- **视觉**：官方品牌图替代自绘点阵角色（10 家）；环形指示器换成官方 M3 `CircularProgressIndicator`；
  所有卡片统一 352px 宽（缩放恒 1.0，边缘只淡出）；hero 主数值环感知退档，长值不再折行。

### 发布点回归（本机真实执行，2026-11-15 05:45–06:00）
| 项 | 结果 |
|---|---|
| 干净构建（clean + assembleRelease） | **BUILD SUCCESSFUL**，APK **3.55 MB** |
| 单测 | **1253 / 178 套件 / 0 失败** |
| 真机安装 | versionCode **58** / versionName **0.0.0.20** |
| 根页四分区 | 四个意图名按序出现（让它答得好 → 让它听懂我 → 让它能做事 → 连得上） |
| 卡片几何 | 同屏宽度 **348–352px**（差异仅来自圆角），x=233 竖向连通段 `48..392` 与 `409..431` —— 卡片**不重叠**，间隙约 17px ≈ 8dp |
| 子页 | 「网络与请求」打开正常：首项「‹ 返回」+ hero 报 40s |

**未覆盖**：聊天主页面与语音识别的端到端（需麦克风与外网）、控制台网页未在本机构建。

## [0.0.0.19] - 2026-11-15（内部版，不对外发布）

### 新增第 15、16 个真实设置（「连得上」分区）
| 键 | 范围 | 默认 | 消费点 |
|---|---|---|---|
| `net_diag_timeout_ms` | 1..10 秒 | 3.0s | `DiagnosticTimeouts.of(...)` → 体检的 TCP 探测（=旋钮）与双栈探测（=旋钮−500ms，下限 500） |
| `console_max_failures` | 3..10 次 | 5 | `ConsoleBridge` 构造 `ConsoleAuthThrottle(maxFailures = preferences.consoleMaxFailures)` |

- 体检超时抽成 `DiagnosticTimeouts` 数据类，一个旋钮摊成两档探测，夹取只有一处；
  `NetworkDiagnostics` 的三处 `connectTcp` 全部改用参数，不再读常量。
- 防爆破上限直接喂给控制台的节流器：改完下一次错误就按新上限计数。
- UI：网络与请求页「体检探测超时」（真机 3.0s）、远程控制台页「令牌错误几次后锁定」。
- `SettingsPersistenceTest` 覆盖扩到 **16** 项；新增 `ConnectivityLimitsTest` 5 个；合计 **1253 全绿**。

### 工程记录：一个反复出现的编辑坑
- 本轮又踩到"常量插到已有行尾"（`const val A = 1        const val B = 2`）。这次写了个通用修复：
  正则 `^(\s*const val [^=]+=\s*\S+)\s{4,}(const val .*)# 更新日志

所有重要的系统变更与特性更新均记录于此文件。

## 版本与发布节奏（2026-11-14 起）

- 内部版本号四位 `0.0.0.x`，每次内部更新**只递增最低位**。
- **每累计 10 个内部版本**才做一次对外发布（GitHub release + 完整回归）；`0.0.0.10` 是第一次对外发布点。
- 每 100 个内部版本做一次信息架构 / 视觉系统复审，之后继续，不停工。
- `versionCode` 仍然单调递增（与内部版本号解耦），当前 39。

---

 自动拆行，一次修掉并复查"剩余合并行 = 0"。
  以后每次批量插常量都跑一遍。

## [0.0.0.18] - 2026-11-15（内部版，不对外发布）

### 新增第 13、14 个真实设置（都在「工具与 MCP」页）
| 键 | 范围 | 默认 | 消费点 |
|---|---|---|---|
| `mcp_max_tool_pages` | 1..10 页 | 5 | `McpClient` 的 tools/list 分页循环（构造时夹取，循环用 `toolPageLimit`） |
| `subagent_read_timeout_ms` | 15s..180s | 60s | `AgentDispatcher` 子代理请求的 `readTimeout` |

- 分页上限一路透传：偏好 → `AgentToolRegistry` → `McpToolBinding` → `McpClient` 构造参数，每层都有守卫断言。
- 子代理超时直接把写死的常量换成 `preferences.subagentReadTimeoutMs.toInt()`。
- UI：工具与 MCP 页新增「MCP 分页与子代理超时」组两行；真机看到 `MCP 工具清单页数 → 5`。
- `SettingsPersistenceTest` 覆盖数从 12 扩到 **14**。
- 新增 `McpAndSubAgentLimitsTest` 5 个；合计 **1248 全绿**。

## [0.0.0.17] - 2026-11-15（内部版，不对外发布）

### 新增第 12 个真实设置：突发判定字数（`stream_emit_chars`）
- 范围 4–40 字（步进 4），默认 **12**；与合帧间隔配成一对：间隔管"多久刷一次"，阈值管"涨多少算吐了一大段"。
- `StreamingThrottleState` 新增 `updateCharsPerEmitThreshold` 与 `currentCharsPerEmitThreshold`，
  内部夹取；`ChatViewModel` 在同一处把两个偏好都灌进节流器。
- UI：生成参数页「突发判定字数」滑杆。
- 新增 `StreamEmitCharsTest` 4 个（阈值语义、状态机真的用新阈值、夹取、ViewModel 接线）。

### 新增落盘守门：`SettingsPersistenceTest`（3 个测试）
- 一条测试**一次覆盖十二个设置**：写入后逐个回读，值必须原样读得出来；
- 另一条钉死十一个键名（跨版本契约：控制台、导入导出、用户既有数据都按这些名字找值）；
- 第三条钉"未知枚举名回退默认档"而不是崩。
- 这样"UI 点得动、键名写错、冷启动才发现"这类 bug 在 CI 就被拦住。

合计 **1243 全绿**。

## [0.0.0.16] - 2026-11-15（内部版，不对外发布）

### 新增第 11 个真实设置：底栏自动收起延时（`bottom_bar_auto_hide_ms`）
- 范围 1.0–8.0 秒（步进 0.5），默认 **3.0s**；状态机 `shouldAutoCollapseAfterTurn` 新增
  `delayMs` 参数（默认沿用原常量，既有测试零改动），越界在状态机内部夹取。
- 消费点两处：`ChatScreen` 的 `delay(...)` 与传给状态机的 `delayMs` —— 两处必须同源，
  否则会出现"等 3 秒但按 8 秒判定"这种自相矛盾。
- UI：外观与动效页新增「底栏自动收起延时」滑杆，真机显示 `3.0s`。
- **端到端证据**：真机把滑杆拖到右端 → `shared_prefs` 出现
  `<long name="bottom_bar_auto_hide_ms" value="8000" />`。
- 新增 `BottomBarAutoHideTest` 4 个；合计 **1236 全绿**。

## [0.0.0.15] - 2026-11-15（内部版，不对外发布）

### 信息架构收尾：网络体检从「关于」改挂到「网络与请求」
- IA 文档 §5.1 的最后一条层级调整：体检是"连不上时"的诊断，必须紧贴连接参数，不该埋在「关于」里。
- `NetworkRequestContent` 新增「网络体检」行（值=最近一次结论，如 `DNS 正常 · TLS 正常`），
  点进去是原来的 `NetworkDiagnosticsContent`，作为该页的内层页，返回语义与其它页一致。
- `AboutContent` 从此是**单页**：只剩侧键、重看引导、权限三项，hero 说明行改报版本号。
- 真机核对：网络页能看到「网络体检 → DNS 正常 · TLS 正常」；「关于」页已无该行。
- 期间修掉一处我自己引入的重复：插入体检行时把 section 标题写了两遍，已在真机走查前删掉。
- 单测 **1232 全绿**。

## [0.0.0.14] - 2026-11-15（内部版，不对外发布）

### 新增第 10 个真实设置：播报队列（`tts_queue_mode`）
- 两档：**打断上一句**（`QUEUE_FLUSH`，默认）/ **排在上一句之后**（`QUEUE_ADD`）。
- 消费点：`ReplySpeaker.speak` 的队列参数，经 `ChatViewModel` 从显示偏好传入；
  至此播报链路三个旋钮齐全 —— 语速、音量、队列。
- 新增 `TtsQueueModeTest` 5 个，其中一条钉死"两个档位必须映射到不同的引擎常量"
  （否则"排队"会变成静默失效的假开关），另一条断言 `speak` 里不许再出现硬写的 `QUEUE_FLUSH`。
- **端到端证据**：真机点「排在上一句之后」→ `shared_prefs` 出现
  `<string name="tts_queue_mode">QUEUE</string>`。
- 合计 **1232 全绿**。

## [0.0.0.13] - 2026-11-15（内部版，不对外发布）

### 新增第 9 个真实设置：触觉强度（`haptic_amplitude`）
- 三档：轻 / 标准 / 强，对应官方 **primitive 缩放** scale 0.4 / 0.7 / 1.0。
  用 `VibrationEffect.startComposition().addPrimitive(primitive, scale)`，而不是
  `createPredefined` —— 后者不接受强度参数，想改强度只能换波形，那就把系统"滴"的语义丢了。
- 消费点两处：`vibrateTick`（滚动刻度）与 `vibrateConfirm`（提交确认），都读同一档位。
- UI：外观与动效页新增「触觉强度」三选一，选中即震一下作为试听。
- 新增 `HapticAmplitudeTest` 4 个（未知值回退标准档、名称往返、三档严格递增且落在 (0,1]、两个震动入口都读档位）。
- **端到端证据**：真机点「轻」后，`/data/data/dev.ghr.wristagent/shared_prefs/*.xml` 里出现
  `<string name="haptic_amplitude">LIGHT</string>` —— UI → 偏好落盘这条链是通的。
- 合计 **1227 全绿**。

## [0.0.0.12] - 2026-11-15（内部版，不对外发布）

### 新增第 8 个真实设置：工具结果上限（`tool_result_max_chars`）
- 偏好默认 **8000** 字符，夹取 500..50000；夹取规则抽到 `ToolOutputPolicy.clampMaxChars`。
- 消费点在**请求体构造**这一处（`buildMainRequestBody` 与 `buildSubAgentRequestBody` 都过一遍
  `ChatMessage.forPayload(...)`）—— 不在工具里裁，因为历史必须留原文：用户把上限调大时，
  上一轮的完整输出还得取回来。
- 只裁 `role == TOOL` 的消息；用户说的话、助手的话、系统提示词一律不动（有测试钉住）。
- 截断处给出代价：`……[truncated N chars]`，让模型知道后面还有内容。
- UI：工具与 MCP 页新增「工具结果上限」滑杆，真机显示 `8000`。
- 新增 `ToolOutputLimitTest` 6 个；合计 **1223 全绿**。

## [0.0.0.11] - 2026-11-15（内部版，不对外发布）

### 新增第 7 个真实设置：核采样 top_p（`sampling_top_p`）
- 偏好默认 **1.00**（不裁剪候选集），夹取 0.05..1.0，NaN 退回出厂值。
- 夹取规则抽成 `SamplingPolicy.clampTopP`，偏好 getter/setter 与测试共用同一份真值。
- 消费点**两处**：`buildMainRequestBody` 与 `buildSubAgentRequestBody` 都写 `top_p`。
- UI：生成参数页在「最大输出 Token」下方新增滑杆，真机显示 `1.00`，步进 0.05。
- 新增 `SamplingTopPTest` 4 个（出厂值、越界夹取、NaN 兜底、两个请求体都真的带上 top_p）；合计 **1217 全绿**。
- 双语文案 key 对拍 0 差异（431/431）。

## [0.0.0.10] - 2026-11-15 · **第 1 个对外发布点**（内部版 0.0.0.1–0.0.0.10 的汇总）

### 本轮新增：播报音量（`tts_volume`，第 6 个真实设置）
- 偏好默认 0.8、夹取 0.1..1.0、NaN 退回出厂值；每次合成前经**官方 per-utterance 参数**
  `TextToSpeech.Engine.KEY_PARAM_VOLUME` 传入（不改引擎全局状态，被打断后下一条不继承旧音量）。
- UI：语音页「播报音量」滑杆，真机显示 `80%`，就在「播报语速」下面。
- 新增 `TtsVolumeSettingTest` 4 个；`AdjustableSettingsConsumptionTest` 的结构守护从 1 条扩到 3 条
  （语速+音量必须同点传给播报器、音量必须走官方参数）。
- 过程中确认：compileSdk 37 下 `TextToSpeech.setVolume` 不可用（编译期 Unresolved），
  官方推荐路径就是 `KEY_PARAM_VOLUME`。

### 页面改名（IA §5.1 三条里的两条）
- 「提示词」→「提示词与记忆」；「外观与点阵」→「外观与动效」（中英各一份，key 与双语对拍 0 差异）。

### 发布点回归（本机真实执行）
| 项 | 结果 |
|---|---|
| 单测 | **1213 / 0 失败** |
| release 构建 | BUILD SUCCESSFUL |
| 真机安装 | versionCode 48，versionName 0.0.0.10 |
| 设置根页 | 4 个意图分区 + 12 行，逐屏走过 |
| 子页 | 网络与请求 / 外观与动效 / 语音与唤醒 / 供应商 均打开过 |
| 返回控件 | 「‹ 返回」随列表滚（子页首项） |
| 卡片 | 同屏所有卡同宽 352px，官方品牌图 + 官方环形指示器 |

**未覆盖**：聊天主页面与语音识别的端到端（需要麦克风与网络），控制台网页未在本机构建。

## [0.0.0.9] - 2026-11-15（内部版，不对外发布）

### 新增第 5 个真实设置：表冠滚动增益（`rotary_scroll_gain`）
- 偏好：`AgentPreferences.rotaryScrollGain`，默认 1.0×，夹取 0.5..3.0，常量全部命名（KEY/DEFAULT/MIN/MAX）。
- 消费点**两处**，覆盖全站：根页 `SettingsScreen` 与**所有子页共用的外壳** `SettingsShared` 的 `onRotaryScrollEvent`。
- 换算抽成纯函数 `rotaryDeltaFor(rawPixels: Float, gain: Float): Float`：全程 Float、**不取整**
  ——取整会让慢速转动变成一顿一顿（这条写进了测试）。
- UI：外观页新增「表冠滚动增益」滑杆（0.5..3.0，步进 0.1，右侧显示 `1.0×`）。
- 测试：新增 `RotaryScrollGainTest` 4 个（默认 1:1、线性缩放、越界夹取、小数位移保留）；总计 **1209 全绿**。
- 真机核对：外观页滚到该行显示 `表冠滚动增益  1.0×`。

### 另两条现场记录
- 上一版 `81ebac4`（信息架构归位）本轮补推成功：远端 `159def5..81ebac4`。
- 「连得上」分区核对：最末一行是「关于」，「完成返回」在它下面 —— 与 IA 文档一致。

## [0.0.0.8] - 2026-11-15（内部版，不对外发布）

### 信息架构：根页按"用户意图"重新分组
按 `docs/SETTINGS-IA.md` 把根页 12 行重新归位，组标题改成**意图名**（新增 `settings_zone_*` 四个键，
	extbf{不动}原有的 `settings_section_*` —— 那几个键还被 hero 卡与子页当标签用）：

| 分区 | 行（按顺序） |
|---|---|
| 让它答得好 | 模型与服务 · 供应商 · 模型参数 · 提示词 |
| 让它听懂我 | 语音与唤醒 · 外观与动效 |
| 让它能做事 | 工具与 MCP · 安全策略 |
| 连得上 | 网络与请求 · 远程控制台 · 导入导出 · 关于 |

- 每组的 `SettingsRowSlot`（首/中/尾）随行数重算：4 行 = FIRST/MIDDLE/MIDDLE/LAST，2 行 = FIRST/LAST。
- 结构守卫测试跟着改：从"钉 4 个 `settings_section_*`"改成"钉 4 个 `settings_zone_*` 且顺序不变"。
- 真机逐屏核对：四个组标题都出现，且每组第一行分别是 供应商 / 语音与唤醒 / 工具与 MCP / 网络与请求。
- 单测 1205 全绿。

## [0.0.0.7] - 2026-11-15（内部版，不对外发布）

### hero 卡主数值不再折行（环占位算进宽度）
- 真机复现：「语音与唤醒」的值 `密钥未填写` 折成「密钥未填 / 写」两行 —— 因为右侧新加的 28dp 环形指示器
  吃掉了文字宽度，而退档判据仍按整卡宽度算。
- 修法：新增 `HeroValueMaxEm = 4.0`（hero 卡专用阈值，比品牌卡的 7.2 小，因为要让出环的位置），
  `heroValueIsCompact` 用它；品牌卡仍用 7.2。
- 实测：同一张卡的值现在是**单行**（节点高度 53px ≈ 26dp = 一行 16sp）。

### 截图纪律（写进文档，省下每轮的重拍）
- 表冠注入走 `su`，每次调用都会弹 Magisk 授权 toast 糊在截图中间。
  做法：**把连续滚动合并成一次 `su` 调用**，然后等 6 秒再截图。

## [0.0.0.6] - 2026-11-15（内部版，不对外发布）

### 信息架构第一步：新增「网络与请求」子页
- 原来堆在根页的 5 个密集控件（超时/自动重试/重试退避/仅 WiFi/触觉反馈）搬进独立子页
  `NetworkRequestContent`；根页只留一行入口，值直接报超时秒数（40s）。
- 依据 `docs/SETTINGS-IA.md` §1.2：根页只放"状态 + 一击直达"，要反复调的滑杆与开关属于子页。
- 子页 hero：值=超时秒数、环=超时占 10..180s 的比例（实测 40s → 约 18% 弧）、说明=重试次数与退避。
- 实测：根页行「网络与请求 40s」→ 点进去看到「‹ 返回 / 网络与交互 40s / 重试 1 次 · 退避 800 ms」+ 5 行控件。
- 单测 1205 全绿（`SettingsCoverageTest` 与 `ConsoleSettingsParityGuardTest` 均未受影响）。

## [0.0.0.5] - 2026-11-15（内部版，不对外发布）

### 卡片宽度彻底统一（只淡出，不缩放）
- 用户 02:20：`所有的卡片都限制在统一宽度里面`。等比缩放在圆屏上必然改宽度，所以唯一自洽解是
  **缩放恒为 1.0、边缘只淡出**（透明度 0.45）。
- 实机实测：同一屏里所有卡片的宽度都是 **352px（176dp）**，改之前最靠边只有 246px。
- 守卫测试改成钉这个解：`MinScale == 1.0`（任何等比缩放都会改宽度）+ 边缘必须淡出。

### 点阵角色资产彻底删除
- `BrandCharacterArt.kt` 删除（含 `BrandCharacters` / `BrandCharacter` / `BrandCharacterSize`）。
- `BrandAssetsTest` 重写成钉官方标识：10 家各有官方图且互不共用、矢量合法、**自带品牌色**
  （OpenAI 例外：官方单色标识在深色底上就是白色，已写进测试注释）；并钉住"点阵角色文件必须不存在"。

## [0.0.0.4] - 2026-11-15（内部版，不对外发布）

### 官方图替代自绘角色
- 品牌卡里的点阵角色全部撤掉，换成**官方品牌标识**（10 家）：DeepSeek / OpenAI / Anthropic / Gemini /
  Kimi / Qwen / Ollama / Mistral / Grok / Meta Llama。
  来源：simple-icons CDN 与 Wikimedia Commons 的公开 SVG，转成 Android VectorDrawable（保留官方品牌色，不 tint）。
  没有官方图的那一家（自建端点）继续用自绘兜底标识。
- 用户原话：`我让你放官方图片，你就别让那个自己画那个点真图了，丑死了`。

### 指示器换成官方组件
- hero 卡的环改用 `androidx.wear.compose.material3.CircularProgressIndicator`（Wear M3 1.7.1 自带），
  轨道色 `#48556A`（Pixel Watch 2 上 Google Health 实测），指示色取该卡强调色；没数据也画轨道。

### 卡片宽度
- 所有卡的布局宽度本来就是同一个常量（`SettingsContentWidth` = 176dp）；唯一的宽度差来自边缘缩放。
  按`所有卡片限制在统一宽度里`把最靠边从 0.70 收到 **0.94**：实测量到 322…352px（差 ≤8%），
  边缘感主要交给透明度（0.45）。守着"只缩不放"不变。

## [0.0.0.3] - 2026-11-15（内部版，不对外发布）

### P0：返回控件跟着列表滚（用户拍板选 ②）
- 卡片档的返回钮从 `ScreenScaffold` 的固定槽位挪进列表内容，成为**第一个 item**：
  往下滚它就滚出去；下拉退出手势仍然常驻，所以不会出现"滚下去没法返回"。
- 实机验证：子页顶部的「‹ 返回」卡可见；表冠往下拨一格后「返回」消失（`backrow-visible.png` / `backrow-scrolled.png`）。
- 其余三套风格仍用原来的固定槽位（气球/方角/终端各有自己的返回语言）。

## [0.0.0.2] - 2026-11-15（内部版，不对外发布）

### P0：卡片档入场不再"相互重合"
- 实机连拍定位：入场时每行先下移 24dp 再上浮，位移期间相邻两张卡贴在一起 ——
  x=233 扫描行程连成一整段 h≈293（两张卡），静止帧才是分开的两段（间隙 16px = 8dp）。
- 修法：**卡片档只淡入、不位移**（`slideOnEnter = style != GOOGLE_CARD`），其余三套保持原来的浮入。
  修后同一位置扫描是分开的三段（58 / 190 / 97），不再粘连。
- 新增 `SettingsCardEntryMotionTest` 三条守卫，防止回归。

## [0.0.0.1] - 2026-11-14（内部版，不对外发布）

- 版本节奏切换：从三位 `0.11-beta` 切到四位内部号 `0.0.0.1`。
- 设置页着色器预热：首帧画两个 1dp、与背景同色、落在圆屏切角外的圆，
  实测首次滑动 `shader_compile` 3 次/55ms → 0、`cache_miss` 3 次/53ms → 0、>64ms 的帧 6 → 1
  （p50 两次采样方向不一致，待 3 次采样取中位数再决定是否保留）。
- 文档：`docs/CONTEXT.md`（跨轮记忆）、`docs/PREP-LONGTERM.md`（五项准备）、
  `docs/DESIGN-SPEC.md`（M3 Expressive 动效一手规范）、`docs/tools/read-url.mjs`（读任意网页正文）。

---

## [0.11-beta] - 2026-11-14

### 二级页标题不再与 hero 标签重复
- 卡片档里，页首标题与 hero 卡那行强调色小字如果说的是同一句话，**标题不再画**：
  14 个二级页都传 `heroLabel`，判据抽成 `SettingsTextLayout.shouldRenderPageHeader` 并加了 3 条单测。
- 实测（冷启动后进「提示词」）：以前两行都是"提示词"，现在页首直接是卡片。

### 系统卡报版本号
- 根页「系统」hero 卡的主数值是当前版本号，说明行仍是 `网页控制台 :8080`。

## [0.10-beta] - 2026-11-14

### 系统卡报版本号
- 根页「系统」hero 卡的主数值从"提示词状态"换成**当前版本号**（`BuildConfig.VERSION_NAME`），
  说明行仍是 `网页控制台 :8080`。提示词状态在「提示词」子页的 hero 里已经说过，根页不重复。
- 内测阶段这条信息最有用：一眼能看出表上装的是哪一版。

## [0.09-beta] - 2026-11-14

### 外观页的两处文案
- 「大卡片」的说明从 `谷歌风格卡片，右侧圆环指示器` 改成 `官方 M3E 卡片：一行一张，滚到边缘会缩小` ——
  圆环在这个版本里早就不存在了，旧文案是错的。
- 组标题不再与页首 hero 重复：`页面风格`（hero 标签）下面改成 `可选风格`。

## [0.08-beta] - 2026-11-14

### 品牌卡
- 供应商标签左侧增加 16dp 品牌标识：同一张卡上角色在上、标识在标签旁，两套资产各就各位
  （实测「🐋 模型 / deepseek-v4-pro / 上下文 30 轮 · 温度 0.7」）。
- 副标题在 `40 项可移植设置` 这类中西混排上不再折行：退档判据改成按 em 估宽。

### 文档
- `docs/PERF-0.05.md` 补了默认气泡档的实测结论：整页高度的竖向渐变是它一半掉帧的来源
  （换纯色后 70~85% → 39.6%、p50 26 → 16ms）。**没有上线** —— 改渐变就是改默认风格像素，留给用户拍板。

## [0.07-beta] - 2026-11-14

### 性能：干掉每帧重画的 432×3113 图层
- `atrace` 切片里发现 `drawLayer [graphicsLayer] 432.0 x 3113.0`：`ScalingLazyColumn` 会给**每个 item**
  挂一个缩放图层，而卡片档把全部分区行放在同一个 lazy item 里 —— 于是 3000+ px、5.4MB 的位图每帧重画一次。
- 卡片档的缩放参数改成平坦（`edgeScale = 1f / edgeAlpha = 1f`），边缘几何继续由每张卡自己的
  `CardEdgeZoom` 负责。实测：该图层消失，`doFrame` p90 **52.7ms → 20.8ms**，<16ms 的帧 **78% → 86%**，
  视觉不变（边缘卡仍在缩小淡出）。
- 卡片档里那块 3000+ px 的分区块不再参与逐行浮动动画（`SettingsStaggeredRow` 新增 `animate` 开关）。

### 排版
- 主数值退档判据从"数字符"改成"按 em 估宽"（ASCII 0.55em、其它 1em）：`40 项可移植设置` 8 个字符
  同样装不下一行，之前会折成两行。

### 测试
- 新增 `BrandAssetsTest`（11 家各有标识与角色、角色是 12×12 点阵、两套资产不同目录不同前缀、
  host 优先 / 模型名兜底 / 兜底标识三条识别规则）。
- `SettingsScalingMathTest` 的判据跟着设计变更改写：从"必须有 0.84f"改成"两套共用平坦参数、
  且不许再出现 0.84f（附原因）"。
- 单测 1186 条全绿。

## [0.06-beta] - 2026-11-14

> 0.05-beta 发布后半小时内的修正版：只改卡片档的结构与排版，不新增功能。

### 卡片档的结构修正
- 根页从「整页一个 lazy item」改成「四张 hero 卡各一个 item + 分区行一个 item」：
  官方的居中锚点与边缘缩放都以 item 为单位，整页一个 item 时首卡永远贴不到屏心，
  滚动也只剩一整块位移。
- 关掉卡片档的 `autoCentering`：首项曾被撑到屏心，从聊天页滑进来看到的是半块被切掉的标题。
- 卡片档不再画根页的焦点文字块：品牌身份卡已经说了同一个模型名，两处并排是重复。
- 卡片档去掉气球收口那 56dp 留白：每行自带 8dp 间隙，再留出一块空屏。

### 排版
- 型号超过 12 个单位退一档字号（titleLarge → titleMedium），`deepseek-v4-pro` 不再在连字符处折成
  `deepseek-v4- / pro`。身份卡与 hero 卡用同一条尺子。

### 文档
- 新增 `docs/PERF-0.05.md`：五种界面的实测掉帧、两次拆件对照实验，以及一条**被实验推翻的假设**
  （"每帧写 snapshot state 是掉帧主因"）。默认气泡风格的性能问题仍在，后续用 Perfetto 定位。

## [0.05-beta] - 2026-11-14

### 大卡片档改用官方 M3E 组件
- 卡片外壳从自绘 `Box + clip + background` 换成 Wear Material3 的官方 `Card`：外形、内边距、
  涟漪、按下态全部由官方组件决定，一行一张卡，卡间距 8dp。
- 主数值字号按卡片可用宽度选档（15 个单位以内用 displayMedium，超出退一档并允许三行），
  `deepseek-v4-pro` 不再被截成 `dee…`。

### 列表边缘空间缩放（只缩不放）
- 大卡片档的每张卡按自己离屏心的距离等比缩小并淡出：屏心 100%，贴边 70% 不透明度 50%，
  中间段走官方 `CubicBezierEasing(0.3, 0, 0.7, 1)`。缩放恒在 `[0.70, 1]` 且 `scaleX == scaleY`，
  结构上不存在横向拉伸的可能；其余三套风格一个像素不动。
- 位置只在 `graphicsLayer` 的绘制阶段读，滚动时只失效图层，不进组合、不进布局。

### 品牌识别
- 按端点 host 识别 11 家 AI 供应商（host 优先、模型名兜底）：DeepSeek / OpenAI / Anthropic /
  Gemini / Kimi / 千问 / Ollama / Mistral / Grok / Llama，自建与中转端点落到统一兜底标识 ——
  不再出现"没配图就是一片空白"。
- 品牌身份卡：点阵角色在上，供应商标签、模型名、上下文与温度居中排在下；长模型名拿满整卡宽度。
- 两套**互不复用**的资产：11 个 24dp 单色品牌矢量（`brand_*_mark`，按品牌色 tint），
  11 个 12×12 点阵拟人角色（`BrandCharacters`，与标识不同目录、不同编码、不同调用点）。
- 设计的取舍与「哈基米」考证见 `docs/brand/BRAND-MARKS.md` 与 `docs/brand/CHARACTERS.md`。

### 二级页首卡全覆盖
- 13 个二级页补上页首 hero 卡，17 个二级页现在全部有 hero：端口、令牌掩码、工具开启数、
  记忆轮数、当前风格、上次体检结论等真实状态直接进卡，没有一处占位符。

## [0.04-beta] - 2026-11-13

### 设置页四套风格
- 新增「页面风格」开关（外观与点阵 → 页面风格），四档实时切换：**气泡 / 方角 / 谷歌大卡片 / 终端**。
  风格真值落在偏好仓库（`settingsStyle`），外观子页、配置导入导出与网页控制台共用同一份。
- **方角**：不画曲面、组标题居中且去掉强调短横、行之间只留 1px 发丝线，返回键换成 40dp 方框描边键。
- **大卡片**：一行一张独立圆角卡 + 根页三张 hero 卡（模型 / 工具与 MCP / 语音）。颜色、圆角、环直径全部按
  Pixel Watch 2 上 Google Health 的实测像素定：卡片底 `#2A313C`、强调色 `#7FCFFF / #A8C7FA / #C4EED0`、
  环轨道 `#48556A`、环直径 50dp、圆角 24dp（实测 49px @2x）。规格见 `docs/DESIGN-GOOGLE-HEALTH.md`。
- **终端**（原创第四套）：整页等宽字体、没有任何底色与圆角，组标题带箭头前缀，只留字与发丝线。
- 大卡片主数值按半宽单位选字号：7 个单位以内用 displayMedium，超出退一档并允许两行 ——
  模型名不再被截成 `dee…`。

### 五项高频 Agent 设置
- **请求超时**（10–180s，默认 40）：直接喂给调度器的 `readTimeout`。
- **自动重试**（0–3 次，默认 1）：网络异常与 5xx 各自消耗一次预算。
- **重试退避**（0–5000ms，默认 800）：重试前等待。
- **仅 WiFi 请求**：开启后蜂窝网络下直接给出可读回执，不发请求。
- **触觉反馈**：关掉后 `vibrateTick` / `vibrateConfirm` 全部静音。
- 五项都有夹取、导入导出与控制台字段；`NetworkSettingsPreferenceTest` 钉住默认值、夹取与跨实例持久化。

### 工具调用账本
- `AgentToolRegistry.executeTool` 记录调用过的工具名，`getToolDefinitions` 记录注册数，
  工具卡因此能用圆环显示「已调用 / 已注册」；没有数据时显示 `—` 而不是 `0 / 0`。

### 网页控制台
- 新增「网络与反馈」页：请求超时 / 重试 / 退避三个滑杆、仅 WiFi 与触觉两个开关、
  设置页风格与回答风格两个分段控件。
- 修掉一个真实缺口：`SERVER_KEYS` 少了 7 个已可写的键（含此前的 `replyStyle`），
  网页端提交时会被本地剔除、存不进表里。

### 性能
- 气球轮廓改用调用方复用的 `Path` 重建，画刷提到 `remember`：滚动时每帧少两次分配。
- 行内 `onGloballyPositioned` 的写入阈值从 0.5px 提到 2px，逐帧失效次数下降。
- 实测（TicWatch，6 次滚动，`dumpsys gfxinfo`）：p50 14ms / p90 21ms，GPU 2–7ms ——
  瓶颈在输入注入抖动而非绘制，后续要以真手指采样再谈优化。

### 视觉与交互
- 修正设置页主气球两肩的小犄角。原实现把 48+48 个采样点交给闭合 Catmull-Rom 平滑，肩部控制点被相邻采样拉成近水平切线，曲线在圆弦外侧鼓出 8dp——实机截图上气球最宽处 461px，而侧壁只有 431px，肩外各多出 15px。改为解析轮廓：侧壁按圆弦的解析导数逐段生成三次曲线，穹顶两端复用同一个导数，顶点保持水平切线，整圈只有一条连续曲线，不再有过冲。新增 `SettingsBalloonOutlineTest` 守住三条几何约束：轮廓不得越过屏幕圆弦、最宽处必须撑满内容盒、左右必须镜像闭合。
- 封口从固定 20dp 浅弧改为按半宽反解的圆拱：20dp 起、最高 48dp，半宽与拱高之比不超过 2.4:1。半宽越大拱越高，肩部曲率突变随半宽摊薄，不会再压成「平盖 + 中间一个尖」。
- 气球内容上下内缩从 32dp 提到 56dp：穹顶长高后首末行不会坐进拱里。

## [0.03-beta] - 2026-11-12

### 视觉与交互
- 设置根页压缩顶部空洞：焦点块去掉额外顶部内距，首组不再重复 24dp 组间距，首个有效分组整体上移 32dp。
- 组标题签名从三颗中性点改为 8dp × 2dp 运行时强调色短横；短横仍在 14dp 左槽沟内绘制，不移动标题与行标签的共同左轴。组内分隔从点线退回 1 物理像素 Border 发丝线，减少边缘噪声。
- 长说明与长取值改为按 148dp 行内可用宽度估算：放不下时整段移到标题下方，不再把标题挤成两行，也不再用省略号隐藏关键值；覆盖中英文、URL、掩码、空值与阈值边界。
- 子页标题与首个分组标题相同时不再重复渲染，安全策略、远程控制台等页直接进入内容，减少一层同义标题。
- 设置页改为**每页一个主气球**：分区标题、卡片行与卡外动作块共享同一条屏幕弦长曲面，不再拆成多张互不相干的小气球。外壳中心最多 216dp，向屏幕边缘连续收缩，顶端用 20dp 单段浅弧收口。
- 内容只允许等比缩小：屏心保持 1.0×，靠近屏幕上下边缘才整体收小；彻底取消 `scaleX` 横向拉伸，文字不再被压扁，也不再用横向形变动画。
- 子页返回键改为官方 M3E `ScreenScaffold.edgeButton`：`EdgeButton` 跟随列表滚动，末尾放大淡入、向上滚缩小淡出；移除左上角圆形返回键与重复的“完成返回”。
- 设置开关从会吞掉整行宽度的官方 `SwitchButton`/`TextToggleButton` 改成右槽 44×26dp 胶囊开关；标签与说明留在左槽，选中态只染轨道，不再出现半屏蓝色大胶囊。开关补 `Role.Switch` 与中英文本地化的开/关状态描述。
- 单主气球顶部加入静态受光渐变（SurfaceAlt → Surface，320dp 内衰减），只提升材质层次，不改轮廓与文字尺寸。
- 新增回答风格三档（精炼 / 平衡 / 详尽）：写入系统提示词的表达约束，并纳入配置导入导出与网页控制台字段。
- 聊天表面重做：Agent 回复改为无常态描边的纵向渐变，用户气泡改为强调色渐变 + 不对称小尾；消息入场从 1.02 等比收回，不做横向缩放。
- 修正组标题被注册成卡片行的问题：此前 `rowSlots` 会把上下两段独立卡片误并成一组。
- 引导页统一为 96dp 点/环 Canvas 插图、说明文字降为次级色，第 5 页审批示意改为单色强调面替代大面积红绿拼色，页脚与页码点保留独立 8dp 间隔。

## [1.0] - 2026-11-11（首个公开版本）

### 语音
- 接通「边说边显示」：此前中间结果开关是空实现，识别期间界面没有任何实时反馈。这次真的订阅了部分结果并逐段上屏，且受同一开关控制。

### 对话
- 修复聊天列表自动滚动的不单调减速（用户感知为「卡来卡去」）。根因不在消息卡片入场动画——逐帧测量显示卡片首帧中心零位移、宽度单调收敛；真正的抖动来自自动滚动：锚点取的是卡片**底边**，而这条边每 32ms 被流式正文撑开、又随缩放被收回，导致每帧目标在 ±20px 内来回跳，单帧步长被夹到剩余距离就成了肉眼可见的硬跳。改为单一驱动 + 一格邮箱合并（同帧多次追加只产生一条目标更新）+ **不对称限速**（目标变大立刻承认，变小每帧最多 340px/s），单帧位移由此单调收敛。
- 回答的对齐改为自适应：同屏还有用户消息时靠左对齐全宽，整屏只剩回答时居中。
- 审批「记住并放行」此前是假开关：落盘与读取两端都通，但 UI 上没有调用者，用户触发不到。现在审批滑轨下方有一条长按 500ms 才生效的条带；破坏性命令永远不可记住；记住后可在设置里显式撤销。

### Agent
- 修复上下文修剪在窗口末尾残留孤立 tool 回执：原实现只从窗口开头剥 TOOL 消息，落在末尾的回执（发起方已被剪掉）会一路留在上下文里，模型会收到一个没有发起方的工具结果。改为按 toolCallId 精确判定，并从更早的消息补回同等条数。
- 新增工具审批策略三档、沙箱等级三档、上下文压缩阈值三个此前连存储都没有的字段，且都有真实消费点。
- 新增长期记忆写入工具 remember_fact。此前记忆闸门是真的，但没有任何写入方，用户拨了开关聊半小时什么也记不住。它落在**强制审批档**——判据是「之后会不会一直生效」而不是「这一下危险不危险」：写入的内容之后每一轮都注入系统提示词，是唯一能长期左右后续对话的隐式通道。
- 新增 MCP 客户端（Streamable HTTP + SSE）。此前 mcpEndpoint 设置项存在但全仓没有客户端，等于假开关。远端工具注册进本机工具清单，只认 http(s)、拒绝跨主机重定向、失败全部降级为可读回执。
- 修复子代理越权：执行侧把 parentDepth 写死成 0，子代理自造 run_subagent 调用会以 depth 0 再派一层，深度上限此前只是「清单里不告知」。补上执行侧硬拒。
- 子代理请求体补上推理努力档（原先漏发，永远不按用户选的档跑）。
- 新增手势绑定：表冠与侧键的长按 / 双击 / 短按可绑终止、切模型、忘掉放行清单。此前设置页有 4 条绑定选项，但全仓没有任何按键事件源，等于假开关。
- 删除视觉识别 beta 开关：agent 侧零消费点，打开只多一个按钮、点击只显示「识别服务尚未接入」。要真实现需要给消息模型加图片 content part 并改两处请求体拼装，而目标设备没有摄像头、手表上没有采集手段。

### 视觉与点阵
- **点阵从壁纸变成可交互骨架。** 背景点阵此前只自己呼吸，和前景元素没有任何几何关系——这是「看起来像随便拼的」的深层原因。现在按下主键时，被按的那一格放大到 1.35 倍、周围格点沿径向被推开并随距离衰减到零、松手用弹簧弹回。真机像素差分验证：60–120px 环带变化 1564 个像素，120px（60dp）以外**逐像素完全不动**，证明是径向梯度而非整盘平移。
- 性能守住了：幅度为 0 时不新增任何状态读取，空闲相位量化的整帧绘制指令复用未被破坏——真机静止两帧间隔 200ms 的差分是 **0 像素**；位移写进随网格一次性预分配的 FloatArray，热路径走标量版函数避免 Pair 装箱。
- 设置页从「每行一张圆角卡」改成「每组一张卡」：组首上圆角、中间方角、组尾下圆角，组内 1px 分隔线，组间 24dp。此前四五张卡片叠成一摞，组内呼吸和组间呼吸看起来一样，分组根本读不出来。
- 修掉右侧取值贴死卡片圆角（padding 只给了 start/top/bottom，漏了 end）、滑轨是 14dp 实心高饱和蓝胶囊（改成 6dp 轨道 + 18dp 白色滑块）、供应商页模型名被劈成两行/或截成 5 个字符（改成副标题独占整行，`deepseek-reasoner` 完整显示）。
- 引导页页脚原本把「跳过 / 页码点 / 继续」三类元素并排在圆屏最底部那条最窄的弦上，改为页码点移到说明文字正下方、页脚只留两个元素左右分置。
- 设置页组内分隔线改成**点线**：直径 3dp、间距 16dp（正好等于点阵格距）、右端仍留 24dp。判定抽成纯对象 `SettingsDotRule`，规则是「圆心加半径必须整颗落在可用宽内」，骑边界的末点整颗丢弃而不是被裁半个；148dp 轨道排 8 颗，首点正中左槽线。组标题左侧加三颗 2dp 点作点阵签名（`drawBehind` 绘制、不占布局宽度，所以标题与行标签仍逐像素同轴）。
- 可点行补按下态：按下换底色、松开 `Wear.Motion.quick()` 回落，无缩放（避免相邻行跳动）无涟漪。
- 审批浮层的可滚动内容带加 12dp 顶边渐隐：钉底之后被折叠线切开的半行原本像渲染故障，加遮罩后读作「上面还有内容」。遮罩只在滚动量大于 0 时绘制，第一行不会被平白压暗。
- 审批浮层改为纵向分带：真机实测发现「最坏情况」第 2 行整行落在滑轨胶囊下面被盖住 11.5dp，且四块文案各自封顶把句子截断。改为「固定头 + 可滚动内容带 + 12dp 间隙 + 滑轨 + 记住条带」，文案不再封顶行数、超长滚动且钉底——判断风险优先于看完整命令。
- 新增 `docs/UI-DOTMATRIX.md`：把点阵确立为唯一度量单位（格距 16dp，屏心为锚点），并列出六条禁忌——每行一张圆角卡、强调色铺成实心块、三类元素挤同一基线、文字贴圆角、一屏两个等权焦点、用字号堆层级。
- **强调色运行时投影覆盖全站**：设置里能选 5 档色，但此前 21 处元素绕过运行时投影直接读死令牌，换了色那些地方不变。现在主色与两个派生投影（`AccentSurface` 实底 / `AccentRail` 轨道）覆盖到气泡、徽标、开关轨道、引导页示意图、主键静息高光、会话抽屉选中态。
  系数按 **Oklab** 反推（Compose 1.10.4 的 `lerp(Color, Color, Float)` 走 Oklab 而非 sRGB）：0.425 / 0.765，落在旧的 `AccentDim` `#0E3A4E` 与 `AccentTrack` `#3788AD` 附近，默认配色观感不变。`AccentTrack` 因此没有任何读者，从色板删除，**15 → 14 个色值**。
- 会话抽屉与设置页统一语言：选中模型胶囊由实心高饱和蓝块改成透明填充 + 1dp 强调描边 + 强调文字；会话卡描边改中性，当前会话改用 6dp 点标记。抽屉里强调色只剩 2 处，都不是面积填充。
- 组卡圆角收进令牌 `Wear.Radius.Group = 16dp`，删掉 `SettingsDesignMath` 里最后一个裸形状常量；形状值现在只在 `Wear.Radius` 定义一次（Control 12 / Group 16 / Card 24 / Pill 999）。

### 设置
- 视觉重做：把「每行都居中」改成「块居中 + 行内左右两槽对齐」——圆屏上整块居中保证重心不偏，行内两端对齐给出可扫读的左边缘。强调色从装饰退回重音（全目录只剩 2 处，根页 0 处）；语义色只保留在 6dp 状态点上；整行分隔线删除，改用 24dp 组间留白。
- 可自定义：字号三档（100/115/130%）整体缩放；强调色 5 档运行时覆盖，设计令牌仍是 14 个（原 15 个：`AccentTrack` 已由运行时投影 `Wear.AccentRail` 取代并删除）；最大输出 Token 从 5 个固定档位补上自定义输入。
- 审计并删除 5 个零消费设置项与随之失效的文案。
- 新增配置导出 / 导入（**33** 个可移植键，密钥不入快照）。字号与强调色原本存在独立偏好文件、不在快照里，导出再导入会丢；现已纳入，且 `snapshot`/`apply` 强制要求传入外观存储（不给默认值，杜绝再次静默丢项）。非法值有明确回落：字号夹回最近档、颜色不在五档回落默认；旧快照不动外观。
- 全部界面文案走资源：字号档名、强调色档名、分组标题、自定义 Token 后缀与区间提示等 16 条硬编码中文迁入 `strings`（中英各 379 条、键集完全对齐、占位符一致）。
- 修复「端点测速与握手 Ping」按钮点了没反应：结果行原本渲染在视口之外，且自动刷新期间按钮被禁用、点击被静默吞掉。改成三态机、结果紧贴按钮、连点可取消重发。

### 网页控制台
- 修复密钥掩码写回：用户在网页上只改别的字段，也会把显示的掩码当成新密钥写回，直接毁掉真钥。三重判定挡回写。连带修掉一个更隐蔽的：旧掩码函数对 ≤4 字符的值直接回原样，短密钥会明文出网。
- 修复两个真实可利用的拒绝服务：限流原先是全局计数，局域网内任何人发 5 个错码即可锁死所有者；请求队列原先无界，队列满时异常会逃进 HTTP 服务的 accept 循环直接打死监听。
- 新增 GET /healthz；可写字段补齐到 35 个；写入被存储层拒绝时不再假装成功；数值字段加范围校验。
- **端口漂移可见**：首选 8080 被占用时控制台会静默漂到 8081/8082，用户不知道真实端口。现在设置页那一行读的是**真实监听端口**，漂移时显示「8080 → 8081」并给副标题说明，语义色只染 6dp 状态点。顺带修掉一个真 bug：根页直接读非 Compose 状态，从子页启动控制台回来后端口不刷新。
- 前端默认值对齐到手表侧真实默认值共 5 处，其中一处是此前凭空捏造的 5 套示例配置档案。

### 引导
- 引导页重写为 5 页真实手势教学。此前的文案与代码实际绑定不符（例如「左右滑切换」在代码里不存在），这次按真实绑定逐条重写，并把几何约束写成可测判据。

### 构建
- 删除不再使用的 mobile 模块。
- 代码卫生：清理 41 条未使用 import 与 2 个零引用声明（`RightButtonAction` 枚举、`ChevronMark` composable）。判定标准是「该符号在文件正文整词 0 命中」，KDoc 里的 `[Symbol]` 也算命中，所以文档锚点不会被误删；`by` 委托的隐式操作符白名单一律保留。
- versionName 1.0 / versionCode 10。
- **生产包不再带调试仪器**：`SettingsTopAnchorDebug` 与 `PullDownDismissDebug` 两个开关此前写死为 `true`，导致设置页每次布局变化都会 snapshotFlow 收集一次列表几何并打日志、每次下拉都打坐标——已改为 `false`（探针保留，排查时一行改回）。同时在 R8 规则里剥离 `Log.d`/`Log.v`，只保留 `Log.w`/`Log.e`（控制台端口绑定失败这类生产排障日志必须留）。
- 仓库卫生：`docs/` 只跟踪 4 份面向使用者的文档，真机取证与工作草稿全部忽略；全仓跟踪文件里 0 个密钥串，git pack 1.45 MiB。

## [0.01] - 2026-11-07（内部测试版）

### 语音
- 录音中按停止改为"提前收尾"：此前按停止只关系统识别器，第三方采集仍会录满 8 秒窗口；现在停止会把已录到的音频直接送去转写，取消则丢弃并立即释放麦克风。采集循环的退出判定（继续 / 收尾 / 取消 / 读错误）抽成纯函数，优先级由单测固定：取消 > 收尾 > 读错误。

### 会话
- 聊天会话落盘：此前会话只存在内存里，进程一退出就全丢，启动时永远是新对话。现在会话列表与当前会话 id 写入 `filesDir`，启动时恢复上次会话，并同步把历史灌回引擎（**模型接着上文说**，不是只把旧消息显示出来）。
- 只存文本轮次与文本气泡；工具调用与工具结果不落盘，避免恢复出没有 `tool_calls` 配对的孤儿 `tool` 消息把请求拼成非法结构。
- 流式回答期间会话高频变化，用 `collectLatest + 800ms delay` 合并成一次写盘。没有真实 `filesDir` 时（单测环境）完全不碰磁盘，杜绝用例间互相污染。
- 真机验证：注入两个会话后重启，落盘文件保持注入内容、界面显示注入气泡而非默认问候。

### 设置
- 网页控制台改设置后**即时生效**：此前写入直接落到偏好层，界面仍用组合期读到的旧值，必须重启应用才看到变化（真机实测：关掉动画后仍 89 帧，重启才降下来）。现在每次写入成功递增一次版本号，界面据此重读偏好。真机验证：动画开 89 帧 → 关闭（不重启）0 帧 → 再打开（不重启）87 帧。

### 特权执行
- su 改为**常驻会话**：此前每条 root 命令都 spawn 一次 `su -c`，在会弹授权的 Magisk 上等于每调用一次工具就打扰用户一次，且每条命令多付几十到两百毫秒启动开销；现在整个进程只申请一次授权，所有命令复用同一个 shell。命令超时或流异常会杀掉会话并在下次重新申请，避免后续命令读到错位输出。
- 会话的 stdin 随本进程关闭而 EOF，su 子进程自行退出，无需显式回收；**不修改 Magisk 策略库**——那等于绕过授权，不属于应用该做的事。

### 性能与流畅度
- 点阵呼吸纳入"动画效果"总开关：关闭后不再创建无限动画，相位钉在中位。真机同一 10 秒窗口实测待机产帧由 **87 降至 2**。

## [1.1.0] - 2026-11-07（已作废：该编号不再作为发布版本，内容与 0.01 合并）

### 语音
- 录音可取消：此前取消只关掉系统识别器，第三方转写通道仍会把麦克风占用到 8 秒窗口结束；现在取消会打断阻塞中的采集循环，并在释放录音器后不再上报任何结果或"录音失败"提示。

### 网页控制台
- 补齐 9 个后端早已支持的开关（深度思考 / 供应商托管搜索 / 上下文压缩 / 视觉输入 / 子代理 / 动画效果 / 紧凑排版 / 自动朗读 / 实时字幕）；长期记忆行的写入键补齐 `memoryEnabled` 别名，不再出现同一字段两处开关。

### 开源化准备
- `applicationId` 由 `com.example.myapplication` 改为 `dev.ghr.wristagent`（`namespace` 保持不变）；应用名拆为中英两份资源（`Wrist Agent` / `腕上助手`）；
- 启动图标换为自绘点阵主题（深底 `#0B0E14` + 5x5 白点阵矢量，无位图无文字）；
- release 构建改用自建 keystore（凭据存 `keystore.properties`，与 `release.keystore` 一并忽略入库）；凭据缺失时自动退化为 debug 签名，保证他人克隆后可构建；
- 从 `gradle.properties` 移除写死的本机 JDK 路径（原值会让其他机器 Gradle 直接启动失败）；
- 新增 GitHub Actions：JDK 17 下运行 `:wear:testDebugUnitTest` 与 `:wear:lintDebug`，不涉及签名构建、无需任何仓库 Secrets。

### 架构
- `MainActivity` 由 1660 行收敛至 87 行：新增 `AgentRepository`（`AgentGateway` 契约 + 纯转发，引擎零改动）、`ChatViewModel`（`StateFlow<ChatUiState>` 承载会话/审批续体/草稿/提示）、`ChatUiState`；
- 聊天界面拆分为 `ui/chat/`（`ChatScreen`/`ConversationList`/`ApprovalOverlay`/`TextInputDialog`/`AnimatedMessageCard`/`ChatApp`）；审批塌缩动画留在 Compose，续体在 ViewModel 内按同一常量延时后交回。

### 国际化
- 界面文案抽为资源并补齐中英两套（`strings_chat`/`strings_ui`/`strings_settings`/`strings_tools`，英文为默认语言、中文走 `values-zh`）；
- `ChatNotice` 由携带中文文本改为携带字符串资源 id 与占位参数，语音错误话术同样资源化；
- 工具展示名（联网搜索/时间天气/设备状态/马达振动/电源模式/点阵网格）改为资源 id，未知工具回落工具名原文；
- 未抽取：系统提示词、工具描述、风险话术、ASR 原始错误文本、预设角色提示词正文（均属领域文本，参与模型请求或逻辑比较）。

### 性能与流畅度
- 点阵背景：逐点 `drawCircle`（113 次/帧）改为按「颜色类别 × alpha 桶 × 半径档」分组后一次 `drawPoints`，稳态降到 **3~9 次 draw call**；同时把 `thinkingWavePhase` 改为仅在行波活跃时读取，待机呼吸改用 `derivedStateOf` 量化相位——**待机产帧从 60fps 降到约 8.6fps**，且不引入闪烁；
- 时钟：时间状态从 ChatScreen 下沉到 `TopClockBar` 自身，避免每秒跳变触发整屏重组；`SimpleDateFormat` 复用并对齐整秒唤醒；
- 流式正文：新增 `StreamingMarkdownStripper` 增量清洗（只处理新增的完整行）并配合 `remember(text, isStreaming)` 记忆化，把 2000 字回复的 O(n²) 重解析降为 O(Δ)；
- 滚动：顶部遮罩改 `drawWithCache`（尺寸变化才重建渐变 Brush），滚动位置的 `Pair` 分配改为 `Long` 编码零分配；
- 思考省略号、执行中圆点与麦克风波形原本各自常驻一个无限动画，空闲时也在逐帧重组；改为只在对应状态为真时创建动画，波形相位移到 `Canvas` 绘制阶段读取，空闲不再因这三个组件产生失效；
- 实测：连续滚动 172 帧 **0% Janky / 50th 12ms / 90th 18ms**；待机 6 秒仅产 57 帧；点阵待机连拍 8 帧亮度稳定（38.61~39.14），无空白帧。

### 阅读体验（补充）
- 提词器自动下滚到达输出结尾时，底栏从弧条**自动恢复成主键**（此时正文下方正好空出位置）；该恢复不受「唤醒挂起 3 秒」限制，因为挂起只约束收起方向。
### 阅读体验
- 新增提词器式阅读镜头：回复开始出现时先限速对齐到该回合顶部（可读区顶部 34dp+8dp），随后按人类默读速度**恒定 6.6dp/s（≈13.3px/s）**自动下滚，用户无需动手即可读完；速度取 500 字/分钟 × 0.7 ≈ 350 字/分钟（5.8 字/秒），按每行 14 字、行高 16dp 反推；
- 到内容末尾、用户手动滚动（表冠/手指）、进入审批·停止·录音·输入弹窗·抽屉时立即停止且不回弹；一轮结束不再跳转到结尾；
- 镜头新增 8dp 死区并删除速度地板，修复「回复前画面以 45px/s 匀速缓慢上移」的永动抖动；底栏预留统一 76dp（正文底边 116dp），修复自己的消息被主键遮挡；
- 底栏可收起：用户一旦滚动即缩成 56×8dp 小白条，点击小白条恢复主键，执行中/审批/录音/弹窗/抽屉强制展开。

### 新增与优化特性
- **设计令牌化与全站视觉重构（手表圆屏）**：
  - 新增 `WearTokens`（颜色/圆角/间距/尺寸/字号阶梯唯一来源）与 `WearComponents`（`WearCard`/`WearRow`/`WearChip`/`WearBadge`/`WearBanner`/`WearDivider`）；全仓消灭 <12sp 字号（原设置子页最低 7.5sp）与散落的硬编码色值，可点区域统一 ≥44dp；
  - 主界面：删除"假毛玻璃"（每张卡片复制整屏点阵再做 22px `RenderEffect` 模糊），改为实底卡片 + 1dp 描边；时间字号提升至 20sp；提示与错误条从"压在底栏主键上"改为底栏上方独立槽位；
  - 交互修复：主键拖动补 `change.consume()`，避免未消费的横向位移被系统"右滑返回"抢走导致应用被切到后台；底栏新增仅认横向拖动的兜底热区；
  - 会话抽屉：改为不透明底消除底层文本重影，退出提示由文字箭头改为 Canvas 叉号，末行补底部留白防圆屏裁切；
  - 设置中心：8 个分区收敛为扁平两行式条目（标题优先，永不被值/徽章挤压），"模型与服务"徽章改为按 `apiKey` 真实状态显示已配置/未配置，子页标题与底部按钮收进圆周内；
  - 引导页与语音悬浮层令牌化；点阵背景改为组合期预计算坐标 + 平行 `FloatArray`，绘制阶段零分配，`UnifiedMatrixCanvas` 签名未变。
- **输入闭环、模型真相源统一与第三方语音通道**：
  - 文本输入弹窗改为 insets 驱动避让输入法（`imePadding` + 可滚动输入区 + 固定底部按钮），键盘展开时"发送/取消"始终可见可点；返回键只关闭弹窗且未发送文本保留；
  - 会话抽屉与模型设置面板的模型入口改为从 `cachedRemoteModels` 派生（新增 `ModelLabels` 短标签与分行纯逻辑），删除硬编码的 gemini 入口；模型清单为空时给出中文提示，超过 3 项改为横向滚动而非挤压错位；
  - 语音链路失败全部可见：无引擎、无麦克风权限、录音失败、未配置第三方密钥、第三方错误码、网络失败均给出可读中文原因（唯一错误出口 `reportError`），不再静默；
  - 新增可插拔 `AsrProvider` 契约与百度短语音识别（REST）实现（token 缓存、WAV 封装、错误码中文映射），设置中心新增"语音转写"分区（厂商/密钥/语言/测试转写），未填密钥时自动回退系统识别器；
  - 外部 intent prompt 入口真正生效并保证同一 intent 只触发一次；
  - 默认输出上限由 300 提升至 2048，并在超参面板补齐 2048 档位；
  - 新增单测：`ModelLabelsTest`、`ModelCycleTest`、`TextInputDialogStateMachineTest`、`ExternalPromptConsumptionTest`、`SpeechErrorNoticeTest`、`WavEncoderTest`、`BaiduTokenCacheTest`、`BaiduAsrProviderTest`、`AsrServiceRouterTest`、`VoiceAsrGatewayTest`、`AgentPreferencesDefaultsTest`。
- **手势闪烁根治、卡片弹簧微动效与全按钮化调试体验重塑**：
  - 定位并根治手势拖动停止时因 `Modifier.offset` 与局部坐标 `deltaX` 产生正负反馈循环导致 60Hz 抽搐闪烁的底层缺陷，全面改用物理增量驱动的 `detectHorizontalDragGestures` 与 `graphicsLayer` 硬件层位移，滑动停驻绝对物理稳定、零闪烁；
  - 彻底移除主界面时间下方常驻的微型会话胶囊（“当前对话”），保持圆屏视觉通透与层级纯净；
  - 为会话抽屉集成快捷模型调试工具条，支持在主界面左右滑动随时呼出并在 `Flash` / `Pro` / `DeepSeek` 之间一键切换；
  - 消息与回复卡片新增 `AnimatedMessageCard` 物理弹簧微动效，新对话框出现时以微缩放、微上浮与淡入优雅浮现；
  - 优化子设置面板进入动效为自然微晶弹性展开，告别生硬一闪；
  - 设置中心全量按钮化调试改造：将原有冗长文字描述全面替换为 2x2 快捷模型调试矩阵、预设温度一键切换按钮（0.2/0.7/1.2）、预设角色人设直选调试按钮（极简助手/架构师/生活秘书/清空测试）、会话轮数大药丸与三级安全防线大卡片按键，实现纯按钮交互与触觉微震闭环。
- **设置中心模块化解体与穿戴触控体验全面重塑**：
  - 彻底解体原有 1408 行单文件巨石架构，在 `presentation/ui/settings/` 下拆分为 `SettingsShared`、`ModelConfigSection`、`HyperParamsSection`、`ToolsMcpSection`、`PromptViewerSection` 与 `MemoryAndSecuritySections` 独立清晰模块；
  - 彻底消灭 7.5sp/8sp/9sp 密恐小字体，全量规范为 11sp ~ 16sp 高对比度清晰排印，标题加粗醒目，次要信息层次分明；
  - 彻底消除仅 4dp 内边距的微型小方块按键：超参调优面板全面升级为 46dp 标准大触控双按钮（`− 0.1` / `+ 0.1`），配合 2x2 网格大药丸 Token 选择器与触觉马达（`EFFECT_TICK`）物理刻度微震回馈；
  - 升级子弹窗与设置页视觉材质：引入深邃蓝黑底色（`0xFF161922`）与超椭圆微高光描边，全弹窗底部配备 42dp 常驻“完成返回”大胶囊按键，支持任意区域右滑手势顺畅退出；
  - 实现设置参数的双向响应式数据闭环：子弹窗调整即时同步并刷新父级概览卡片胶囊徽章。
- **真实 SSE 流式传输与工具诚实化重构**：
  - 彻底废除客户端 `delay(12)` 人造假打字机，接入基于 HTTP SSE（Server-Sent Events）的实时逐字流式读取，服务端输出 Token 即刻实时上屏，实现真实的首字极速响应（TTFT）；
  - 穿戴端工具全面诚实化：时间工具准确返回系统本地时间并如实声明无内置气象传感器（彻底废除硬编码假造的 22℃ 晴朗微风）；搜索工具接入 DuckDuckGo 真实端点，未配置或检索无结果时如实报告，彻底清除硬编码假新闻摘要模板；
  - 新增针对时间动态格式与搜索参数校验的单元测试套件 `AgentToolHonestyTest`。
- **视觉排版与抽搐动效彻底根治**：
  - 彻底移除 `SpringStreamingText` 中每吐一个字就将整段文本缩放至 1.025f 的癫痫式弹簧震颤动效，改用主体绝对稳定的排版搭配末尾轻柔呼吸光标，保障小屏幕上的阅读稳定性；
  - 修复 `OnboardingScreen` 旋转表冠事件空转直接返回 `true` 的缺陷，正确桥接至列表滚动状态；
  - 优化底栏药丸手势状态机判定门限，加大横滑与纵滑的死区判定，彻底消除长按录音与滑动切键的误触互斥；
- **状态管理规范化与静态伴生泄漏彻底铲除**：
  - 彻底移除 `MainActivity.companion` 上的 `onBackPressedCustom` 与 `onPromptTrigger` 静态变量，全面换用系统标准的 `BackHandler` 与生命周期安全的响应式通道；
  - 修复任务取消或停止时 `pendingContinuation` 悬空死锁底栏的隐患，在取消及完成时强制安全释放并置空；
  - 抽离 `TopClockBar` 与 `VoiceListeningOverlay` 独立 UI 组件，精简主入口文件。
- **全面对标 Operit 架构与去浮夸化重构**：
  - 彻底清除全系统所有浮夸、中二与科幻修饰词汇（如伴生体、微晶点阵谐振、小白模式/大佬模式、超新星等），重构为专业、直白、严谨的穿戴智能助手系统；
  - 深度对标标杆软件 Operit 设置架构，重构系统设置为 6 大清晰功能域（模型与服务、模型参数、内置工具、系统提示词、会话与上下文、操作安全确认）；
  - 安全确认机制改为“严格确认模式（全部确认并带 3 秒倒计时锁）/ 标准模式（高危敏感工具确认）/ 自动执行模式”；
  - 调整系统默认 System 提示词为高信息密度且遵循穿戴场景的工程师提示词规范；
  - 全部二级下钻子面板均提供规范标题与直白的主操作按钮，支持右滑顺畅返回。
- **语音按键触发异常与自动劫持为键盘弹窗 Bug 根治**：
  - 在 `AndroidManifest.xml` 中补全 Android 11+ 软件包可见性声明 `<queries>`（覆盖 `android.speech.action.RECOGNIZE_SPEECH` 与 `android.speech.RecognitionService`），彻底解决系统语音服务查询返回 false 的底链隐患；
  - 接入 Google Wear OS 官方语音识别组件与 ActivityResult 协议，轻触与长按均精准拉起穿戴原生语音识别界面（Microphone Voice Screen）；
  - 彻底移除语音出错或未识别时自动弹出键盘输入框的劫持逻辑，杜绝无论怎么点都跳出键盘的问题；
  - 新增应用内 `VoiceListeningOverlay` 倾听悬浮层与状态路由分发单元测试。
- **文字气泡卡片底部大留白与多余空白彻底根除**：
  - 彻底移除正文卡片容器硬编码的 145dp 最小高度限制，改为 `wrapContentHeight` 自适应包裹；
  - 短回复紧致贴合（单行/短句卡片即刻收敛），长回复随文字延伸排布，内边距严格收敛，彻底杜绝下方空旷黑洞。
- **全系统硬件物理表冠（Rotary Crown）与齿轮触觉微震联动**：
  - 主列表、会话抽屉、系统设置与新手引导全量挂载 `FocusRequester` 与 `onRotaryScrollEvent`；
  - 旋转表冠顺滑滚动列表，并联动穿戴震动马达 `EFFECT_TICK` 产生精准清脆的齿轮刻度手感。
- **Google Wear OS Material 3 会话抽屉与全层级右滑推回手势**：
  - 进出动画完全对齐物理手势：向左划自右向左推入，向右划（`Swipe-to-Dismiss`）顺滑推回退出；
  - 阻尼手势跟手位移：向右拖拽时实时跟随手指，松手未过半弹簧复位，过半顺滑飞出退出，彻底废除必须点小红叉退出的反人类交互；
  - 设置面板及全部二级配置子面板均支持向右轻划顺畅关闭退出。
- **底栏微晶矢量图标与无歧义输入交互流转**：
  - 中央主按键与两翼副按键全面升级为极简微晶 Canvas 矢量图标（键盘边框与点阵、呼吸波动三态声波柱、模型分层线条、参数滑块线条），全面取代粗糙文本缩写；
  - 状态流转清晰：键盘模式单触直接呼出打字框，语音模式长按录音、松手发送，滑动弹出两翼副键轻触即刻切换模式与持久化。
- **四阶段全动效微晶点阵新手引导**：
  - 阶段一中心粒子爆炸扩散波浪铺满全屏，唤醒全屏微晶点阵坐标；
  - 阶段二粒子向心凝聚为柔和白圆，居中显示欢迎引导文案与胶囊按钮；
  - 阶段三白圆弹性缩回中心原点，点阵背景下方平滑升起 URL 与 API Key 配置卡片；
  - 阶段四配置保存完成后平滑降维落位主界面。
- **调度引擎防死锁、异常自愈与动态时空电量注入**：
  - 穿戴单任务自主 ReAct 轮次硬上限收敛为 5 轮安全限额，防止后台自旋烧干手表电量；
  - 工具调用加入自愈包装，底层执行报错转换为结构化诊断数据回传 LLM 辅助纠错；
  - 动态捕获当前精准时间、星期、电池电量百分比、充电状态与剪贴板内容，贴在首层 System 提示词顶层注入。
  - 彻底重构会话抽屉布局，各项采用 14dp 圆角 Squircle 扁平横条（Squircle Strips），依托 Wear OS 官方 `ScalingLazyColumn` 居中排布，随圆形屏幕上下边缘曲率自然产生平滑的缩放、挤压与变形；
  - 交互双向对齐：从左侧向右平滑滑出抽屉，向右轻划（`deltaX > 24dp`）自然触发物理推回退出，同时保留背景遮罩与底部返回按钮轻触退出。
- **底栏主副按键双向形态演变与持久锁定**：
  - 展开底栏轻触 [TXT] 副按键后，中央主按键直接演变为文字输入形态，轻触即刻弹出全屏快捷文字输入窗口；滑动展开后副按键自动变为 [MIC]，轻触即可随时切回语音主按键形态，并即时持久化到偏好设置。
- **大模型端点在线探针与 `/v1/models` 动态拉取**：
  - 彻底废除写死模型的死板模式，新增远端探测机制：支持自定义 Base URL 与 API Key，发起真实 HTTP 请求并测算网络延迟（Ping ms），动态拉取服务器返回的所有可用模型并填充单选列表。
- **极客/创客调参控制台全面重构**：
  - 新增推理超参数调优（Hyperparameters Tuning）：采样温度 Temperature 支持 +/- 0.1 步进精密微调（0.0 ~ 1.5），最大输出长度 Max Output Tokens 支持 150/300/600/1200 胶囊单选，支持流式输出开关；
  - 穿戴原生工具细粒度权限放行：6 大穿戴工具（电量温控感知、马达触觉反馈、微晶点阵谐振、系统能耗协同、高精度时钟气象、广域联网搜索）全面开放独立开关，LLM 动态仅感知用户已放行的工具 Schema；
  - 上下文滑窗轮数调节：支持 4 轮节能、10 轮标准、30 轮深度单选滑窗，结合首层 System 提示词持久锁定长期记忆。

### 新增与优化特性
- **手势区域严格空间隔离与操作状态防冲突门禁**：
  - 彻底重构手势判定中枢，将抽屉滑动手势严格限制于主内容区（避开顶部 42dp 状态栏与底部 96dp 交互控制台），底栏左右滑动切换模型/键盘与上滑进入设置绝不再误触发会话抽屉；
  - 增加操作状态门禁：在文本输入弹窗激活、语音录制中、智能体自主执行中与高危安全审批放行中，彻底静默抽屉手势，彻底杜绝交互打架；
  - 手势与动画方向严格匹配：正文区向左划动或轻触顶部会话胶囊，会话抽屉平滑自屏幕左侧推入（`slideInHorizontally { -it }`），向右轻划或轻触关闭即刻回退。
- **智能体系统设置全面重构为硬核控制台（遵循 Wear OS M3 官方规范）**：
  - 彻底根除凑数项：删掉矩阵密度手动调节（由 AI 自身工具自主协同）、删掉鸡肋假省电开关与写死的单选框；
  - 遵循 Wear OS Material 3 官方组件体系（`ScalingLazyColumn` + `ListHeader` + `Card`），实现全圆屏上下边缘弧度自适应贴合收缩；
  - 深度落地硬核 Agent 体系：
    1. 当前运行大模型与端点动态读取（卡片展示实时生效模型与 Base URL，二级弹窗支持切换 DeepSeek-V3/R1、Gemini、Claude、Ollama 本地服务并配置密钥）；
    2. 工具集与 MCP 服务协议中心（展示 6 项穿戴原生工具激活状态与 MCP SSE/StdIO 服务探针）；
    3. 伴生系统提示词与角色设定（卡片展示全自主伴生体定位，支持查看完整 Prompt 与微调自定义）；
    4. 上下文与长记忆管理（显示 30 轮滚动策略、长记忆持久事实槽状态，提供一键重置长记忆功能）；
    5. 两翼快捷按键卡片化二级交互（点击弹出单选弹窗，告别平铺堆砌）；
    6. AI 安全审查三级护栏模式（小白模式 3 秒锁定 / 普通模式智能预审 / 大佬模式极速自主）。
- **页面转场白色屏幕闪烁根治**：
  - Activity Window DecorView 锁定绝对纯黑（`Color.BLACK`），转场容器包裹全屏纯黑基底；
  - 废除引起透底缝隙的 `scaleOut` 缩放转场，重构为微垂直位移配合淡入淡出（`slideInVertically` + `fadeIn`），彻底消除进出设置页时的白色闪烁。

---

## [1.0.0] - 2026-10-23

### 新增与优化特性
- **语音输入点击与设备服务缺省闭环修复**：
  - 彻底移除出现错误时随机注入预设指令的逻辑，杜绝误触发无关操作；
  - 修复轻触单击同时触发录音启动与结束导致底层 `SpeechRecognizer` 报 `ERROR_CLIENT` 崩溃的问题，支持单击启动录音/无语音服务平滑弹出输入窗、长按说话松手触发；
  - 针对无 Google 语音包环境（如 Wear OS 纯净系统/模拟器），轻触时自动呼出快捷输入弹窗。
- **思考同心阵列开头双闪根治**：
  - 将背景点阵行波相位 `thinkingWavePhase` 重构为平滑连续循环驱动（`infiniteRepeatable`），振幅由 `thinkingIntensity` 平滑平移渐变，消除多次触发 `snapTo(0f)` 导致的中心微晶两下强闪。
- **底栏形态与状态展示降噪**：
  - 废除输出完成时短暂拉长为 118dp 胶囊并停留 1 秒的突兀动效，完成后直接平稳回归待机正圆（44dp）；
  - 修复文本流式输出过程中一直展示“停止输出”胶囊的问题：仅在 LLM 深度思考与网络工具调用等待期间展示微型停止键，一旦开始吐字即收敛为闲置正圆，不干扰正文阅读。
- **大圆角偏正方形 Squircle 造型与双重镜像漫射真正毛玻璃**：
  - 卡片圆角由 6dp 扩展至 18dp，比例调优为现代超椭圆偏正方形 Squircle（`minHeight = 145dp`），更加温润大气；
  - 彻底解决下层锐利小点生硬透出的缺陷：底层增设不透明深黑板阻隔屏幕下层未模糊点阵，内部专属将点阵系统与流动光波置入 Android API 33 `RenderEffect.createBlurEffect(22f, 22f, CLAMP)` 物理硬件漫射层；卡片外部为根根分明的锐利点阵，卡片内部点阵真正散开为发光的柔和微晶星云，光波漫射为清澈柔光带。
- **真实 DeepSeek Agent 引擎与硬件工具调用全链路联调**：
  - 接入 DeepSeek 官方 API 密钥（通过 `local.properties` 与 `BuildConfig` 安全隔离，严格杜绝提交进 Git 仓库），打通 `api.deepseek.com` 真实对话回路；
  - 实机联调多轮复杂任务闭环：模型自主决策并成功交错调用穿戴设备底层硬件电量与温控检测（`get_device_status`）及线性马达触觉脉冲（`vibrate_device`），并在卡片内自动生成高密度事实排版。
- **彻底剥离假材质与还原极简通透模板**：
  - 彻底剥离伪噪点贴图、伪菲涅尔复杂渐变边框与多余的装饰性表头（如 `● ASSISTANT ONLINE` 等标签），还原为纯净的现代简约模板；
  - 卡片外壳回归极简单色微细线（`0.6dp`, `Color(0xFF334155).copy(alpha = 0.55f)`）与纯净半透深底板（`Color(0xFF0B1120).copy(alpha = 0.58f)`）；
  - 去除底部弹窗内的 Emoji 装饰，采用纯色扁平化现代视觉规范。
- **点阵外波浪与磨砂漫射内光波系统（外点阵内光波）**：
  - 外波浪：外部背景点阵物理涟漪（`DotMatrixBackground`）保持锐利点阵位移与光晕发散；
  - 内波浪：在卡片内部背景层由 `SimpleLightWaveBacking` 渲染一道淡淡柔光带（`alpha = 0.28f`），穿过 Android 原生 API 33 `RenderEffect.createBlurEffect(24f, 24f, CLAMP)` 物理硬件漫射，在卡片内部化为一层通透清澈的淡淡光波。
- **手势交互与前台常亮保障**：
  - Window 属性配置 `FLAG_KEEP_SCREEN_ON`、`setShowWhenLocked(true)`、`setTurnScreenOn(true)`，确保手势交互与调试过程中屏幕持续常亮不休眠。
- **Agent 回复方形框内嵌彩色工具调用与弹簧展开动效**：
  - 结构化整合：废除 Assistant 的外部散落卡片，将同一回合的思考胶囊、工具调用胶囊与回复文本 100% 收纳在单个宽幅方形框（`AgentSquareBlurBox`，`RoundedCornerShape(4.dp)`）内部，用户指令保留右对齐紧凑气泡（`UserChatBubble`）；
  - 工具专属色彩与状态文案（`SpringColoredToolPill`）：根据工具类型深度映射霓虹青（联网搜索）、琥珀金（时间天气）、翡翠绿（设备状态）、紫罗兰（马达振动）、珊瑚橙（电源模式），执行中显示生成进度，执行后显示清洗后的具体执行摘要与“干了什么”；
  - 物理弹簧弹性展开（Spring Animation）：点击胶囊触发 `spring(dampingRatio = 0.65f, stiffness = 350f)` 弹性展开/收起，右侧指示箭头以阻尼弹簧弹性旋转 90 度，内部以高对比半透微晶排版展示结构化入参与执行结果；
  - 逐字流式弹簧跳动与呼吸跳动光标（`SpringStreamingText`）：大模型 Token 吐字时驱动 `scale` 弹簧回弹跳动与末尾呼吸跳动光标，赋予文本有机微晶跃动感；
  - 真实原生高斯模糊：在方形框底层深度应用 Android API 33 `RenderEffect.createBlurEffect(26f, 26f, CLAMP)` 真实硬件物理漫射底层唯一点阵系统，消除假噪点与死黑遮盖。
- **Agent 回复方形框与真实原生点阵高斯模糊**：彻底废弃 Agent 回复上的聊天气泡样式，改用极简纯正方形框（`AgentSquareBlurBox`，`RoundedCornerShape(4.dp)`），仅用户发送的消息保留右对齐紧凑气泡（`UserChatBubble`）；在方形框背景底层真正挂载 Android API 31+ `RenderEffect.createBlurEffect(24f, 24f, CLAMP)` 漫射微晶点阵，点阵在方形框下化作真实的物理光学冷光高斯云雾，搭配 65% 半透暗底与 0.8dp 极简细框，实现真正的原生毛玻璃质感。
- **纯粹安卓原生高斯模糊与极简边框**：彻底移除假噪点画布（`FineGrainNoiseOverlay`）、顶部反光条与深黑死底，全量采用 Android API 31+ 原生硬件接口 `RenderEffect.createBlurEffect(18f, 18f, CLAMP)` 漫射底层点阵；搭配极简 0.8dp 细边框与半透自然暗底，还原真实、干净的高斯模糊。
- **宽幅排版与顶部时间防穿孔视口渐隐**：文字边距由 14dp 扩展至 8dp，打破在屏幕中央狭窄挤压的视觉瓶颈，文字贴近表盘自然边缘展开；顶部时间区域叠加原生高斯模糊与微黑边遮罩，文字向上滑动时在时间下方平滑淡出，绝不在时间区域内重叠显示；底部按键区域平滑渐隐，文字可在全屏自如纵向滚动。
- **点阵动画系统梳理与内存/算力全面优化**：
  - 静态几何节点预缓存（`MatrixGridPoint`）：单次预计算所有网格在圆屏内的坐标、圆心距离、边缘渐隐因子及发散距离，消除每帧 400 余次 `sqrt` 与 `pow` 重复浮点运算。
  - 动态行波节流：闲置待机状态下彻底挂起思考持续行波帧驱动，停止无意义的 60fps 背景重绘，大幅缩减功耗与发热。
  - 静态色彩复用：消除绘制循环内部瞬时 `Color.copy` 对象分配，杜绝 GC 内存抖动。
- **文字与磨砂遮罩一体联动手势滚动**：彻底移除固定在屏幕中央的静态死框，将高对比黑曜石磨砂卡片（`FrostedPhysicalMaskCard`）直接作为文字自身的气泡外壳置入滚动流；用户气泡、Agent 正文、思考胶囊与工具胶囊外框随手势 100% 同步上下滑动，消除生硬视窗割裂感。
- **微晶喷砂噪点细腻度与物理磨砂质感强化**：噪点步长精细化至 2.2dp、微晶粒子半径收敛至 0.45dp，高频伪随机多阶灰度高光漫射消除粗糙颗粒感，呈现细腻高贵的 AG 蚀刻喷砂雾面质感；多层深邃黑曜石渐变底色与顶部 0.8dp 菲涅尔双重高光微边缘增强物理厚度与光影折射；文字视口上下边缘增设渐隐平滑退场，绝不撞切圆形表盘与顶部时间。
- **方案 A 旗舰级硬件高斯模糊文字遮罩**：引入 Android 13+ API 33 原生 `RenderEffect.createBlurEffect(24f, 24f, CLAMP)` 硬件光学模糊，为文字整体打造专属高透微晶磨砂遮罩容器（`FrostedTextMaskContainer`）；底层点阵在文字背后化为朦胧冷光云雾光斑，文字如浮于悬空雾面毛玻璃之上，兼顾高级朦胧氛围感与极致可读性。
- **光点层级与高质感加大**：点阵绘制层级移至上下文字渐隐防撞遮罩之上，彻底解决光点被遮罩遮挡导致暗淡丑陋的问题；微晶光点基础半径加大至 2.3dp（直径 4.6dp），波纹掠过时动态扩张至 7~8dp；增强按钮定向水波冲击波纹的扩散对比度与光晕冲击感。
- **标准笛卡尔方形微晶点阵与径向渐隐**：点阵回归标准直角坐标系 (x, y) 均匀对称栅格，铺满整个圆形表盘（包含四周月半弧区域，绝无居中方盒硬裁切感）；从圆心零点到屏幕物理边缘依据半径距离自然平滑渐隐衰减。
- **按键定向发散射出水波纹**：点击或松开底部主按键时，以按键为震源激发出定向水波冲击波（Shockwave），沿整片方形微晶点阵自底向上扩散，点亮沿途微晶粒子。
- **两翼按键弹性弹射与弹性收回**：左右滑动底部主按键时，左右副键（TXT 键盘录入与 MDL/SET/CLR 功能键）以弹性动量弹射展开；展开状态下单次轻触任意位置即可瞬时弹性收回归位。
- **极坐标弧形微晶时间**：顶部时间完全贴合圆形表盘上圆弧，采用高精细极坐标逐字弧形贴边排版，居中对称、间距工整、字体端正，绝无文字重叠或旋转倒字。
- **设置页全屏右滑返回与防裁切控制台**：设置页面支持全屏右滑手势返回（Swipe-to-Dismiss），同时顶部导航重构为安全区域内完全居中的胶囊返回键与标题，彻底解决圆屏四角裁切显示不全的缺陷。
- **纯黑无界排版与防切角安全区**：彻底移除生硬外框矩形卡片，采用纯黑（#000000）OLED 底色与正文两侧 36dp 宽裕留白，文字自然流淌于屏幕中心，绝不发生圆屏上下两端切字现象。
- **交错流时间线与微型小胶囊**：思考过程与工具调用默认折叠为紧凑微型小胶囊（如 [思考过程]、[调用: web_search]），穿插在两段文本之间，点击可无缝展开查看详情，核心正文占据绝对主空间。
- **点阵四重功能性光相变**：点阵呼吸放缓至 3.0s 沉稳节奏；审批时自顶部倾泻重力红瀑；同意放行转通透翡翠绿并淡出；拒绝时红瀑自下而上退潮；任务完工时一道 30 度角表镜冷光反光扫过全屏。
- **向左滑动审批与三级安全护栏**：审批横条改为向左滑动放行（Swipe Left to Allow），轻触任意空白区域拒绝，彻底避开 Wear OS 向右滑系统退出手势冲突；内置致命指令硬黑名单绝对拦截，提供小白模式（默认带 3 秒倒计时锁）、普通人模式与大佬模式。
- **原生网络搜索与长记忆槽**：新增 `web_search` 原生工具并对搜索结果进行前 150 字摘要清洗；建立永久锁死在 System 最顶层的长记忆持久化仓储（AgentMemory）。

### 新增特性
- **HTTP 400 智能自愈与闭环容错机制**：在 Agent 发起大模型 API 交互前，自动检测会话历史中的 `tool_calls`。对被用户中断或异常孤立的工具调用回执，自动补全合成 `cancelled` 结果，杜绝 OpenAI / DeepSeek 协议规范校验失败导致的崩溃。
- **点阵待机收敛与松手 Shockwave 冲击波**：录音期间圆形表盘点阵粒子流体场完全宁静平稳收敛；松开主按键瞬间，按键回弹缩小的同时激发全屏辐射同心 Shockwave 冲击波，波前点阵瞬间点亮白光并扩散。
- **Hero 齿轮绽放转场与极简表盘**：完全移除右上角常驻齿轮图标，优化圆屏视觉空间。支持底部主按键双击或向上轻滑，触发中心齿轮弹性绽放放大转场直达设置面板。
- **高对比微晶发光设置面板**：重构设置面板，采用纯黑 OLED 像素级基底，搭配极光电光蓝、翡翠荧光青与亮琥珀金发光卡片，提升穿戴屏幕户外观感与辨识度。
- **单体形态审批滑块 (Morphing Approval Slider)**：在“正在思考”胶囊状态上直接通过 Spring 弹性拉伸（144dp 扩展至 186dp），内嵌高透微晶滑块。支持右滑授权与左侧轻触拒绝，授权完成后顺畅弹回初始状态。
- **穿戴系统物理返回拦截**：在根 Activity 深度接管 Wear OS 物理返回按键与侧滑手势，防止从设置页返回时误杀主进程导致应用闪退。
- **系统架构与文件剖析交接文档**：在根目录 `README.md` 建立客观翔实的工程交接文档，涵盖全工程文件物理路径、逐文件源码职责剖析、UI 与系统层分层映射、现存技术债务与客观问题、纯黑微晶工业美学评估以及用户满意度轨迹复盘。
- **配置安全与秘钥脱敏**：彻底移除源码中的硬编码 API Key 默认回退值，统一收敛至穿戴设置界面安全配置。
- **全局零 Emoji 纯净规范**：清除全工程界面、提示文案及业务流中的所有 Emoji 符号，确保专业严肃的穿戴交互体验。
