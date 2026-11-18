# 设置项功能实测报告（真机 · 2026-11-18）

> 用户要求："全部功能测试一下设置页的功能，是否切换了能正常使用，全部测试全盘测试。"
> 本文件记录**已经测到的**、**测了但结论不成立的**、以及**还没测的**。三者分开写，不混。

## 〇、JVM 功能回归（Robolectric，0.0.0.87 起）——当前最可信的一层

真机探针受坐标与落点影响，结论反复不可靠；改成在 JVM 上**渲染真实页面 composable**，
点语义节点、读 `AgentPreferences` 真值，5 秒跑完、可重复。覆盖如下（全部 0 失败）：

| 测试类 | 页面 | 点到的行 | 断言的真值 |
|---|---|---|---|
| `SettingsPagesRobolectricTest` | 网络与请求 | 仅 WiFi 请求 / 触觉反馈 | `wifiOnlyRequests` / `hapticsEnabled` |
| 同上 | 模型参数 | 开启 SSE 流式 / 极速 0.2 | `streamMode` / `temperature = 0.2` |
| 同上 | 语音转写 | 语音转写开关 | `asrEnabled` |
| `SettingsAppearanceAndToolsRobolectricTest` | 外观 | 六套风格逐个点 | `settingsStyle` + `WearAppearance.style` 同步 |
| 同上 | 外观 | 紧凑模式 | `compactMode` |
| 同上 | 工具与 MCP | 六个工具开关逐个点 | 六个 `tool*Enabled` 各自翻转，互不串 |
| `SettingsSecurityAndMemoryRobolectricTest` | 安全策略 | 三档逐个点 | 回调收到的 `SecurityMode` |
| 同上 | 记忆 | 启用记忆 | `memoryEnabled` |
| `SettingsSwitchRowRobolectricTest` | 控件层 | 开关行 | 文案 / 点击取反 / `stateDescription` |
| `SettingsAppearanceDeepRobolectricTest` | 外观 | 触觉三档逐个点 | `hapticAmplitude` |
| 同上 | 外观 | 字号三档逐个点 | `WearAppearance.fontScale` **+ 落盘** `AppearanceStore.fontScale()` |
| 同上 | 外观 | 强调色五档逐个点 | `WearAppearance.accentOverride` **+ 落盘** `AppearanceStore.accent()` |
| 同上 | 外观 | 点阵间距滑杆（语义拨动 9.4） | `matrixSpacingDp = 9`（吸附 + 区间必须是它自己的） |
| 同上 | 外观 | 底栏延时滑杆（拨 3300） | `bottomBarAutoHideMs = 3500`（吸附 0.5s） |
| 同上 | 外观 | 表冠增益滑杆（拨 2.27） | `rotaryScrollGain = 2.3`（吸附 0.1） |
| `SettingsGestureRobolectricTest` | 手势与侧键 | 四段各点一个动作 | 四条绑定**逐个**落盘，且其余三条不动 |
| `SettingsProviderAndTransferRobolectricTest` | 供应商档案 | 逐个切档案 | `modelName` / `baseUrl` / `activeProfileId` 三项一起换 |
| 同上 | 供应商档案 | 复制当前配置 / 删除当前配置 | `profiles.size` +1 / −1 |
| 同上 | 导入导出 | 导出 → 改温度 → 导入 | `temperature` 回到导出时的 0.3 |
| 同上 | 导入导出 | 剪贴板塞一句废话再导入 | **一个字都不写**（`temperature` 保持 0.7） |
| `SettingsSliderSweepRobolectricTest` | 记忆 | 上下文轮数（拨 30.4） | `maxContextTurns = 30`（吸附整数） |
| 同上 | 记忆 | 压缩阈值（拨 0.62） | `historyCompressThreshold = 0.60`（吸附 0.05） |
| 同上 | 记忆 | 单条记忆上限（拨 137） | `memoryMaxFactLength = 140`（吸附 20） |
| 同上 | 语音 | 播报语速（拨 1.37） | `ttsSpeechRate = 1.4`（吸附 0.1） |
| 同上 | 语音 | 播报音量（拨 0.44） | `ttsVolume = 0.4`（吸附 0.1） |
| 同上 | 外观 · **官方档** | 点阵间距点三次加号 | `matrixSpacingDp = 4 → 7`（官方 Slider 一段一步） |
| `SettingsLuxTerminalRobolectricTest` | Lux 终端 | 自由输入 2001 字符并确认 | 转录里留下「被拒」回显（进 shell 之前就被挡下） |

合计 **26 个设置键 + 6 套风格 + 3 档安全模式 + 四条手势绑定 + 两套跨对象搬运 + 终端闸门**，从"只能真机手点"变成每次跑单测都验一遍。
仍未覆盖：终端里"真跑一条命令"（要 Android 的 `sh`，JVM 上没有可信替身）、MCP 端点文本框（表端只读展示，写入在网页端）。

> **踩到的坑（写在这里免得再踩）**：`WearAppearance.style` 是全局单例，测试类之间会互相污染 ——
> 上一个用例切到 `M3_LIST`，下一个用例的滑杆就换成官方 `Slider`（没有 `contentDescription`），
> 断言会以"找不到节点"的样子红，看起来像控件坏了。所以每个滑杆用例前都要显式 `applySettingsStyle(BALLOON)`。


### 〇·二 这一层当场查出的缺陷（0.0.0.88）

自绘滑轨 `SettingsThinSlider` 原来**没有挂任何无障碍语义**：读屏读不到、也拨不动。补语义时又发现更糟的一层：

- 卡组外层是 `Card(onClick = {})`，可点即 `mergeDescendants`；滑杆行不可点，于是**被整张卡吸成一个无障碍节点**。
- 实测（Robolectric 打印被匹配到的节点）：一个节点的 `contentDescription` 同时是
  **【底栏自动收起延时, 表冠滚动增益】**，`ProgressBarRangeInfo` 只保留了前者 `3000.0 / 1000..8000`。
- 后果：读屏用户看到的是一整块卡，第二个滑杆的 `SetProgress` 被丢弃 —— 它**不可调**。
- 修法：滑杆行自己加一个空语义边界（`Modifier.semantics(mergeDescendants = true) {}`），
  每行独立成节点；同时给自绘轨道补 `contentDescription` + `ProgressBarRangeInfo` + `SetProgress`。
- 回归判据：三个滑杆测试都断言"匹配到的节点区间必须是它自己的"，串了就直接红。


## 方法

每点一行，就把 `/data/data/dev.ghr.wristagent/shared_prefs/agent_prefs.xml` 前后各读一次做 diff：

- `string` 型是文本节点（`<string name="k">v</string>`）；
- `boolean/int/long/float` 是**属性**（`<boolean name="k" value="true" />`）—— 第一次跑出 9 个假 FAIL
  就是因为按文本节点解析布尔，一条都读不到。修好后才拿到真实数据。

判据：点一次 → 对应键变化 → 再点一次 → 键回原值（单选/枚举类只要求"变化"）。

## 一、已验证通过（真机，键名与变化都可查）

| 页面 | 行 | 键 | 实测 |
|---|---|---|---|
| 工具与 MCP | 电量与温度感知 | `tool_device_status_enabled` | true → false → true |
| 工具与 MCP | 马达振动提醒 | `tool_vibrate_enabled` | true → false → true |
| 工具与 MCP | 表盘点阵间距 | `tool_matrix_density_enabled` | true → false → true |
| 安全策略 | 严格安全模式 / 标准提示模式 | `security_mode` | STANDARD ↔ NOVICE ↔ STANDARD |
| 外观与动效 | 气泡 / 方角 | `settings_style` | SQUARE ↔ BALLOON |

**5 组、6 个键位，双向都能落盘。**

## 二、测了但结论不成立（**不算通过，也不算缺陷**）

第二批（模型参数 / 网络与请求 / 语音与唤醒）**全部"无变化"**，但同一页的分区标题
（`网络与交互`、`语音`）也报"无变化" —— 分区标题本来就点不动。
这说明**点击根本没落在可点区域**，是探针落点问题，不是设置项坏了。

具体疑点：这三页的行高比工具页更高（带说明行 + 滑杆），滚到底/贴边时
`centerOf` 取到的中心点会落到文字与外框之间的空白。要拿到可信结论，
下一版探针必须**每次点击后验证 UI 状态真的变了**（例如开关的选中语义、滑杆数值文本），
而不是假设"没变 prefs 就是没响应"。

## 三、还没测的

- 滑杆类（`temperature` / `requestTimeoutSeconds` / `netDiagTimeoutMs` / `bottom_bar_auto_hide_ms` 等）：
  需要拖动或点击轨道，比点开关复杂。
- 枚举类对话框（供应商、底纹风格以外的档位、底栏副键四键位）：点开是弹层，要单独判据。
- 9 个其余页面（模型与服务 / 供应商 / 提示词与记忆 / 底栏副键 / 特权执行 / Lux 终端 /
  网页控制台 / 配置导入导出 / 关于）。

## 三·五 探针已入库（0.0.0.64）\n\n工具：`docs/tools/sweep-settings.mjs`（从 build-logs 提进仓库，附带两个坑的说明：\n布尔值是 XML 属性、点击落点要接受 110..370 且滚不动时记 INCONCLUSIVE 而不是判失败）。\n**它目前还不能给出这三页的可信结论** —— 见上一节的"结论不成立"。\n\n## 三·六 巡检探针加了自诊断（0.0.0.65）

`sweep-pages.mjs` 现在每次点击前先存"我看到的这一屏"，点完屏幕没变就写
`build-logs/sweep-miss-<标签>.txt`（含点击坐标与整屏节点）。

跑第 2..4 页的结果：**一个 miss 文件都没生成**，说明点击**确实改变了屏幕**，
但没有打开目标页 —— 那不是"点空"，而是"点到了屏幕上的别的位置"。
目前不知道具体点到了什么（诊断文件只在"完全没变化"时写），下一版要把
"变化后的整屏"也存下来，才能定位。

## 三·七 巡检"打不开"的根因定位（0.0.0.66）

加自诊断后拿到关键证据：失败行的诊断文件里 **`"tapped": false`** ——
也就是说**点击压根没发生**，不是点错位置。

`tapped=false` 只有一个来源：`tapScrolled` 在 20 步滚动里从没让目标行落进 110..370 安全带。
而同一时刻的屏上文本里明明有那一行 —— 说明**滚动本身没生效**。
滚动用的是表冠（`crown()`），表冠只把事件送给持有焦点的节点；子页返回根页后焦点不保证回到列表上
（`SettingsScreen` 里 `LaunchedEffect(activeDialog, ...)` 会重新 requestFocus，但实测存在失效窗口）。

**试过的修法（已回滚）**：把滚动换成触摸 `input swipe`（不依赖焦点）。结果更糟 ——
三页全变成 `open=FAIL back=FAIL`，说明触摸拖动把列表带到了别的状态，反而破坏了返回。
所以这个改法**没有留在仓库里**，`sweep-pages.mjs` 保持 0.0.0.65 的版本。

**下一步（写清楚给下一个人/下一轮）**：
1. 不要再猜坐标。先在 `tapScrolled` 里**验证滚动是否真的发生了**：
   滚动前后各取一次首条文本，若完全相同 → 判定"滚动无效"，此时主动点一下空白区域夺回焦点再滚。
2. 或者绕开表冠：直接用 `uiautomator` 的节点点击（`UiDevice.findObject(By.text(...)).click()`），
   需要写一个 20 行的 instrumentation 或 appium 式脚本，成本高但一次解决。
## 三·八 又试了两种改法，都没过（0.0.0.67，已回滚）

1. **加"表冠是否生效"检测**：滚动一次看首条文本变不变，不变就标 BLOCKED。
   实测 `crownDead=false`（表冠确实推动了列表），但仍 FAIL —— 说明问题不是焦点。
2. **`toTop()` 滚动到顶**：反复 crown(30) 直到首条文本连续两次不变。
   仍然 FAIL，而且第 4 页的"实际标题"变成了「模型与服务」，说明落点还是乱的。

两次改动都**没有留在仓库**（`sweep-pages.mjs` 保持 0.0.0.65 版本）。
结论：这套"数表冠格数 + 按坐标点"的方案在这台设备上不稳定（SQUARE/官方档下根页行高不同，
滚动步长与行高不是线性关系）。**不要再微调参数**，直接换方案：

- 方案 A（推荐）：写 instrumentation 测试，用 `UiDevice.findObject(By.text("模型参数")).click()`，
  按节点点，完全不碰坐标。
- 方案 B：用 `uiautomator dump` 拿到目标节点 bounds 后，**每次点击前重新 dump 一次**并立即点击
  （中间不加任何 sleep），把坐标漂移窗口压到最小。
## 四、探针现状

`build-logs/probe-settings-sweep.mjs`：能跑，判据是"prefs 有没有变"。
**已知缺陷**：不验证点击是否命中；页面标题判据在官方档下要放宽（首条文本是「返回」）。

下一版要加的三件事：
1. 点击后读 UI 语义（`stateDescription` / 控件 checked）而不是只读 prefs；
2. 滑杆用「点轨道两端 + 读数值文本」两步验证；
3. 每次点击前重新 dump 并确认目标行在安全带宽内（110..370）。

## 三·九 冷启重进：3 页里救回 1 页（0.0.0.78）

改动：`sweep-pages.mjs` 在**每一页之前**都 `enterSettings()`（force-stop + 重启 + 划进设置），
理由是失败行 `tapped=false` 说明表冠推不动列表（焦点不在列表上），冷启能拿到干净焦点。

结果（第 2..4 页）：**语音与唤醒 open=ok**，模型参数 / 提示词与记忆 仍 FAIL，
且失败时首条文本是 `deepseek-v4-pro`（根页焦点块）——说明点击要么被焦点块吸收，要么没落进行里。

下一步不要再猜：先 dump 一次"点击前后的整屏节点 + 我自己算出的坐标"，看清坐标落在哪个节点的 bounds 里。
若确实被焦点块压住，就改成"点行尾控件区域"（x 取行右侧 1/3）而不是行中心。
## 五、15 页巡检：14/15（0.0.0.79）

**根因是我自己设的"安全带"太窄。** 我用"点击必须落在 y=110..370"来避免圆屏边角点不准，
结果靠下的行永远进不了这个带，探针就越滚越远、再也找不到它。
实测证明那条带是错的：`模型参数` 在 y=442（比带子下沿还低 72px）**点得中**，
点完页面正常打开（`probe-hit.mjs` 打印过命中节点就是它本身）。

改动：安全带放宽到 **y=60..430**，并保留"每页冷启重进"。
全量重跑结果：

| 结果 | 页数 |
|---|---|
| 打开 + 返回都 ok | **14** |
| 仅"打开"失败 | 1（模型参数） |
| 返回失败 | 0 |

唯一失败的 `模型参数`：冷启后它落在首屏焦点块下方、滚动第一步就滑出视野，探针没再点中它。
手工用坐标 149,442 点它是能开的（同一轮验证过），所以**页面本身没问题，仍是探针的落点问题**。

至此 HANDOFF §0.5 第 ① 条（15 页巡检）可以算收口：**14/15 自动化通过 + 1 页手工验证通过**。
## 五·二 15 页巡检：**15/15 全通过**（0.0.0.81）

把点击安全带从 60..430 再放宽到 **60..450** 之后，`模型参数`（中心 y=442）也能点中了。
全量重跑：**15 页全部 open=ok / back=ok，0 失败**。

| 版本 | 结果 | 差在哪 |
|---|---|---|
| 0.0.0.79 | 14/15 | 模型参数打开失败 |
| **0.0.0.81** | **15/15** | —— |

HANDOFF §0.5 第①条 **正式收口**，不再需要"自动化 + 手工"的混合结论。
## 六、设置项实测：工具已判定"不可用于结论"（0.0.0.82）

把点击安全带放宽到 60..450 后重跑三页（语音与唤醒 / 网络与请求 / 模型参数）：
**OK=0 / INCONCLUSIVE=11 / SKIP=3**，仍然一条可信结论都没有。

失败模式已经查清，不是坐标问题：
- 滑杆行**点标签不改值**（要拖轨道或点加减号）；
- 分区标题（`> 语音`、`> 网络与交互`）本来不可点，却被当成了"候选行"；
- 部分行点开是弹层（`界面变了但键没变`）；
- 值文本（`3.0s`、`0.8`）也被当成行去点。

**结论**：这个脚本（`docs/tools/sweep-settings.mjs`）停在"能跑但不可信"的状态，
文件头已写明警告。要拿到真结论只能换 instrumentation：
`UiDevice.findObject(By.text(...)).click()` + 读 `UiObject2.isChecked/isSelected` 或
`AccessibilityNodeInfo.stateDescription`，然后才断言 `agent_prefs.xml` 的变化。

已通过的 6 个键（工具页三个开关 + security_mode + settings_style）仍是唯一可信数据。