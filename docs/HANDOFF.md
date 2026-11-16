# 交接文档 · 给下一个 AI

> 你接手的是 **Wrist Agent**（Wear OS 手表上的 AI 助手，包名 `dev.ghr.wristagent`）。
> 这份文档只回答一件事：**按什么顺序读哪些文件，才能在三分钟内接上**。
> 读完它再动手，不要先看代码。

---

## 0. 三十秒版

| 问题 | 答案 |
|---|---|
| 项目在哪 | `D:\AndroidProjects\MyApplication`（**不是** `C:\Users\GHR\AndroidStudioProjects\...`，那是搬走后留下的空壳） |
| 现在什么版本 | `0.0.0.44` / versionCode 82；发布点 `v0.0.0.10` / `.20` / `.30` / `.40` 已打 tag |
| 单测基线 | **1289 条 / 188 套件全绿**（改任何东西前先跑一遍确认） |
| 下一个里程碑 | `0.0.0.50` 发布点（还差 6 个内部版） |
| 主线状态 | P0–P3 四条全部收口（细节在 `docs/CONTEXT.md` §0.5） |

---

## 0.5 前任留下的欠账（接手先看这里，别重新想"该干什么"）

按"有多确定"排序，**每条都写了验证方式**，别猜。

| # | 欠着什么 | 状态 / 该怎么接 |
|---|---|---|
| 1 | **设置页全量巡检** | 脚本已写好在 `build-logs/sweep2.mjs`（配 `docs/tools/nav.mjs`），但上次跑到中途超时，15 个页面只验了 7 个且没跑完。**接法**：把页数砍到每批 5 个分几次跑，判据是"每页能打开 + 能返回根页" |
| 2 | **行高 1.42 的版式手术** | **没做，且在等用户点头**。现状主题行高 1.25–1.33，比 Material 自家 1.43 紧，这是用户最初抱怨"字体过扁"的可能来源。**顺序不能反**：先改 `ApprovalBandMetricsTest` 的几何推导 → 再上机重新量审批带/设置行/卡片间隙 → 最后才动 typography（写进 `docs/DESIGN-SPEC.md`） |
| 3 | **P1 验收矩阵还剩三行** | ①磁滑（`RotaryScrollEvent` 之外的路径）未测；②内层页只抽了 1 个；③底栏自动收起的真实时序（需要一轮完整流式回复）。矩阵在 `docs/ACCEPTANCE-MATRIX.md` §2.5–2.7，照表格补行 |
| 4 | **网页控制台只验到首屏** | 三档皮肤截图有了（`docs/device/v11/console-*.png`），但 10 页里还有几页是只读展示，交互路径没测。接口层已验证：`/` 200、无令牌 401、带令牌 POST 写入成功 |
| 5 | **版本停在 `0.0.0.44`** | 之后三个提交都是 docs（`8f1423c` / `8292ed1` / `043aa01`）**没动代码**。下次动 `wear/src` 要 bump 到 `0.0.0.45`，并同步 `docs/CONTEXT.md`（有守卫测试钉着） |
| 6 | **下一个发布点 `0.0.0.50`** | 还差 6 个内部版。到点就跑 `node docs/tools/release-check.mjs`，7/7 通过后打 tag |
| 7 | **工作区路径还没修** | GUI 的工作区记录仍指向 `C:\Users\GHR\AndroidStudioProjects\MyApplication`（**空壳，0 个文件**）。真项目在 `D:\AndroidProjects\MyApplication`。**新会话前必须新建一个 D 盘工作区**，否则读不到 `docs/CONTEXT.md` |
| 8 | **压缩只能用户发 `/compact`** | `manage_context` 的 9 个 action 里 8 个能用，只有 `compact` 报 `compaction backend is not mounted`——梁神预设把 `compaction` 隔离在自己的 realm。要压缩就请用户发命令，别反复试 |
| 9 | **`@local/android-wear-toolbox` 没验过** | 用户装了这个插件（adb/Gradle 工具：列设备、截图、dump UI、logcat、构建、安装）。它**从未进过任何会话的工具面**。新会话第一件事：看工具面里有没有 `android_wear_*`，有就直接用，别再手搓 `spawnSync` |

---

## 1. 必读顺序（按这个顺序，不要跳）

| # | 文件 | 读哪几节 | 为什么 |
|---|---|---|---|
| 1 | `docs/CONTEXT.md` | **§0.0 接续卡** → **§0.5 运行中状态** → §1 硬约束 → §3 已定决策 | 唯一记忆源。§0.0 告诉你"我是谁、现在在哪、开工四步"，§0.5 是最新状态 |
| 2 | `docs/SELF.md` | 全文（不长） | 人设与行事准则。用户亲手要的角色卡，**它决定你说话的样子** |
| 3 | `CHANGELOG.md` | 顶部 2–3 个条目 | 最近三个版本到底改了什么，以及踩过哪些坑 |
| 4 | `docs/ACCEPTANCE-MATRIX.md` | §2.5–2.7 | 真机验收矩阵（表冠 / 触摸 / 网页三档皮肤），改 UI 后照着重跑 |
| 5 | `docs/DESIGN-SPEC.md` | 「行高倍数审计」一节 | 有一条**没做**的版式手术，做完要按它写的顺序来 |
| 6 | `docs/SETTINGS-IA.md` | §1.2 + §2.2–2.5 | 设置页四分区结构；文档里的页面名**必须等于界面文案**（有守卫测试钉住） |

读完这六份再开始读代码。**代码里注释密度很高，但这六份文件里的信息代码里没有。**

---

## 2. 开工四步（照做，不要凭印象）

```powershell
# 1) 工作区状态
git log --oneline -5; git status --porcelain

# 2) 单测基线（约 2 分钟；必须是 1289 条 / 188 套件 / 0 失败）
.\gradlew.bat :wear:testDebugUnitTest

# 3) 上下文占用（有 manage_context 插件时）
#    manage_context({action:"usage"})  → 看 ~多少 routed tokens

# 4) 从 docs/CONTEXT.md §0.5 的「下一步」那一行直接继续，不要重新规划
```

---

## 3. 环境配方（每次都会踩的坑）

| 事项 | 做法 |
|---|---|
| Shell | 用 `pwsh` 工具（原生）。早前版本用的是 `run_code` + `spawnSync`，**现在没有了** |
| Gradle | 必须给全 env：`JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`、`GRADLE_USER_HOME=D:\gradle-home`；命令 `cmd /c gradlew.bat --console=plain :wear:testDebugUnitTest` |
| ADB | `E:\apkkkk\platform-tools\adb.exe`，设备序列号 `C101X35122973`（466×466 / density 320） |
| 表冠注入 | 走 `su -c 'for i in $(seq 1 N); do sendevent /dev/input/event0 2 8 ±5; ...'`；**每次 su 都弹 Magisk toast，把连续滚动合并成一次调用，等 6 秒再截图** |
| `git push` | 常断，用 `-c http.curloptResolve=github.com:443:<ip>` 轮换 `140.82.112/113/114/116.4` |
| 中文写文件 | **不要用 `Set-Content -Encoding UTF8`**（会造成 mojibake）；用 `write` / `edit` 工具，或 `Out-File -Encoding utf8NoBOM` |
| 导航自动化 | `docs/tools/nav.mjs`：`tapRow` 点完会检查有没有弹层；**点了不算成功，要验证效果** |
| 发布自检 | `node docs/tools/release-check.mjs`，7 项判据（构建/单测/APK/安装/四分区/几何） |

---

## 4. 硬约束（违反即返工）

1. **禁止任何横向拉伸 / 横向形变**；动画**只允许缩小**，`scaleX == scaleY`。
2. **凡是改变 scale 的动画必须不过冲**：`dampingRatio = 1`（有守卫测试 `ScaleAnimationOvershootGuardTest`）。
3. **M3E 卡片不得相互重合**，且**所有卡片同一宽度**（176dp / 实测 352px，边缘只淡出不缩放）。
4. **所有交付必须真机验证**，不能用"编译通过"代替交互验收。

---

## 5. 不要做的事

- **不要重做已完成项**（清单在 `docs/CONTEXT.md` §0.5），先确认它没做完再动手。
- **不要替用户做审美决定**：换像素、改行高、调渐变之前先问。实验和最小改法写在文档里就够了。
- **不要用形容词代替证据**："丝滑""优雅""完美"这类词一律换成数字或截图。
- **不要按文档里的名字去界面上找东西**——先 `grep` `values-zh/strings_settings.xml` 拿真实文案（我为此浪费过三次）。

---

## 6. 关键路径速查

| 路径 | 作用 |
|---|---|
| `wear/src/main/java/.../presentation/ui/settings/` | 设置页全部实现（`SettingsShared.kt` 是 DSL 内核） |
| `wear/src/main/java/.../presentation/ui/chat/` | 聊天页（`ConversationList.kt` / `ChatScreen.kt`） |
| `wear/src/main/java/.../agent/AgentPreferences.kt` | 全部偏好（20 个可调设置都在这里，带 `KEY_*` / `DEFAULT_*`） |
| `wear/src/test/java/...` | 1289 条单测；**18 条结构性守卫**在这里（改动前先看它们） |
| `docs/device/v11/*.png` | 真机截图证据（同名文件更新即代表最近一次验证） |
| `docs/tools/` | `release-check.mjs` / `nav.mjs` / `console-shot.mjs` |

---

## 7. 如果你只记得一句话

**报告必须和事实一致。** 说修好了就是真机上修好了；说没验证就写"未验证"，并把它写进 `docs/CONTEXT.md` 的下一步。

这个项目的用户会认真读你的每一句结论，也会因为你糊弄他一次而失去信任。**别糊弄。**

---

## 8. 关于"一直不停工"——用户到底要什么（别理解成加班）

用户的指令是「无限期持续迭代，无固定结束日期，不停工」。**这不是让你连轴转，是让你别停摆。**

他真正要的是这三件事：

1. **每一轮都要有实物。** 代码、文档、真机证据——三样里至少要有一样，而且不能是重复劳动。空转（"我检查了一下，一切正常"）不算交付。
2. **没有人催你，你得自己找下一件事。** 不要问"接下来做什么？"——`docs/CONTEXT.md` §0.5 的「下一步」和 `docs/HANDOFF.md` §0.5 的欠账清单就是答案。只有**真正需要他拍板**的事（审美方向、要不要动全局几何、要不要公开发布）才停下来问。
3. **节奏是定好的**：每轮内部版 +1；每 10 个内部版做一次完整回归并打 tag；每 100 个内部版复审信息架构与视觉系统。到点就做，不用问。

**同时他也讨厌瞎忙。** 所以"不停工"和"别堆改动"不矛盾，判据是：

> 这一轮的改动，**能不能用一句话说清它解决了谁的什么问题**？
> 能 → 就做，并留证据。
> 不能 → 宁可把不确定性变成一个好问题去问他，也别为了显得勤奋而动代码。

还有一条他亲口说过的偏好：**"别糊弄我。"** 宁可写"这条我没验完，卡在哪"，也不要写一个漂亮的"已完成"。他读得出来。别试。
