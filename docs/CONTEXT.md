# 长期上下文（跨轮恢复的唯一入口）

> 每轮开工先读这一页。它只记**会变的东西**：硬约束、已定决策、当前阶段、待办、恢复步骤。
> 事实性细节去代码与 `docs/PERF-*.md`、`docs/brand/*`、`docs/DESIGN-*.md`。

## 0.0 接续卡（新会话开工第一件事：读完这一段再动手）

用户 2026-11-17 说"我还不想丢掉你"——所以这张卡的目的只有一个：**让换会话不等于失忆**。

**我是谁**：这台手表上 Wrist Agent 的常驻工程师。行事准则在 `docs/SELF.md`（用户可改），核心一句：
**报告必须和事实一致**——说修好了就是真机上修好了，说没验证就不假装验证过。

**现在在哪**（截至 2026-11-19 01:00）：
- 内部版 `0.0.0.89` / versionCode 127；发布点 `v0.0.0.80` 已自检 7/7 并推送；`v0.0.0.10` / `.20` / `.30` / `.40` / `.50` 均已打 tag。
- 单测基线 **1355 条 / 205 套件全绿**（`./gradlew :wear:testDebugUnitTest` 实测，0 失败 0 跳过）。
- P0–P3 四条主线全部收口（细节见 §0.5），20 个真实设置全部落地并带说明行；六套风格已上真机逐套核对。
- 当前主线：**设置页功能回归搬到 JVM（Robolectric + Compose ui-test）**，已从 15 个键扩到 21 个键 + 四条手势 + 两套搬运；
  借它当场查出并修掉一个无障碍真缺陷（两行滑杆被并成一个节点，第二个滑杆读屏拨不动）。
- 网页控制台页面级 E2E 已接进 `release-check.mjs` 当第 4 项（令牌用 `su -c cat` 自动读，不再手填环境变量）。

**开工四步**（照做，不要凭印象）：
1. `git log --oneline -5` + `git status --porcelain` —— 确认工作区干净、远端追上没有
2. 跑一次单测，确认 **1338** 这个基线
3. `manage_context({action:"usage"})` —— 看上下文占用；**压缩现在能自己触发**（见 §0.6.1）
4. 从 §0.5「下一步」那一行直接继续，不要重新规划

**当前环境变化（2026-11-17）**：工具面已换成原生工具（`pwsh` / `read` / `write` / `edit` / `glob` / `grep`），
`run_code` 不存在了；adb、gradle、git 全部走 `pwsh`。新装的两个插件：`@local/context-governor`（自我管理上下文）
与 `@local/android-wear-toolbox`（adb/Gradle 工具，若工具面里出现 `android_wear_*` 就直接用）。

## 0. 恢复步骤（新会话照做）

1. `docs/CONTEXT.md`（本页）→ `docs/PREP-LONGTERM.md` → `CHANGELOG.md` 顶部
2. `git log --oneline -8`、`git status --porcelain` 确认工作区干净
3. 环境配方：`docs/HANDOFF-0.05.md`（无 shell 工具、gradle env、adb、GUI、git push 的坑）
4. 跑一次 `./gradlew :wear:testDebugUnitTest` 确认基线（当前 **1189** 条）

## 0.5 运行中状态（每轮覆盖这一段，别追加）· 最后更新 2026-11-19 01:00

**版本与基线**
- 内部版 `0.0.0.89`（versionCode 127）；发布点 `v0.0.0.80` 已打 tag 并推送；`v0.0.0.10` / `.20` / `.30` / `.40` / `.50` 已打 tag。
- 单测基线 **1355** 条 / **205** 套件全绿（0.0.0.89 新增 2 套 10 条：网络与采样参数 6、模型与端点 4）。
- **发布自检首次跑满 8/8**（0.0.0.88 时，clean 构建 1m47s）：构建成功 / 单测全绿 / APK 4.21 MB /
  真机装机 / 进入设置页 / 四分区按序 / 几何已采样 / 控制台页面 E2E **69/69**。
- 上一个发布点：**0.0.0.50**（自检 7/7 通过，tag 已打）。下一个内部版节奏继续 +1；`0.0.0.90` 是下个整十发布点。

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
- P3：控制台 ✅（验收 + 键表守卫）、设置风格 5 种 ✅、20 设置 ✅、Lux 终端 ✅。

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

会跑 clean 构建 + 全量单测 + 真机安装 + 四分区按序 + 卡片几何采样，7 项全过才 exit 0。
可 `--skip-build` 或 `--skip-device` 分段跑。

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
| 边缘缩放 | `CardEdgeZoom`：屏心 1.0、贴边 0.70/0.50，官方缓动，只缩不放；库的 scalingParams 保持平坦 |
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
| M3_TRANSFORM 官方转场 | PLAIN + 行转场 | ✅ 一致 | `m3transform-scrolled.png` |

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