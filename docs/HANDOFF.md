# 交接文档 · 给下一个 AI

> 你接手的是 **Wrist Agent**（Wear OS 手表上的 AI 助手，包名 `dev.ghr.wristagent`）。
> 这份文档只回答一件事：**按什么顺序读哪些文件，才能在三分钟内接上**。
> 读完它再动手，不要先看代码。

---

## 0. 三十秒版

| 问题 | 答案 |
|---|---|
| 项目在哪 | `D:\AndroidProjects\MyApplication`（**不是** `C:\Users\GHR\AndroidStudioProjects\...`，那是搬走后留下的空壳） |
| 现在什么版本 | `0.0.0.100` / versionCode 138；发布点 `v0.0.0.10` / `.20` / `.30` / `.40` / `.50` / `.80` / `.90` 已打 tag |
| 单测基线 | **1377 条 / 209 套件全绿**（`./gradlew :wear:testDebugUnitTest` 实测；改任何东西前先跑一遍确认） |
| 下一个里程碑 | 内部版满 100 时的整十发布点；每个整十都要跑 `node docs/tools/release-check.mjs` |
| 主线状态 | P0–P3 四条全部收口（细节在 `docs/CONTEXT.md` §0.5）；当前主线是"设置页功能回归搬进 JVM"（35 个键）+ 控制台页面级 E2E 已接进发布自检 |

---

## 0.5 前任留下的欠账（接手先看这里，别重新想"该干什么"）

> 2026-11-19 按实测重写：旧版这张表里 5 条已经做完，留着只会误导下一个人。

| # | 欠着什么 | 状态 / 该怎么接 |
|---|---|---|
| 1 | ~~设置页 15 页巡检~~ | **已完成**：0.0.0.47 与 0.0.0.81 两次都是 **15/15 能打开 + 能返回根页**（证据 `build-logs/sweep-*.jsonl`、`docs/SETTINGS-SWEEP.md`） |
| 2 | **行高 1.42 的版式手术** | **没做，且在等用户点头**（`docs/DECISIONS-PENDING.md` D2）。现状主题行高 1.25–1.33，比 Material 自家 1.43 紧。**顺序不能反**：先改 `ApprovalBandMetricsTest` 的几何推导 → 再上机重量 → 最后才动 typography |
| 3 | **验收矩阵还剩三行** | ①磁滑（`RotaryScrollEvent` 之外的路径）②内层页（二级页里再进一层）③底栏自动收起的真实时序。矩阵在 `docs/ACCEPTANCE-MATRIX.md` §2.5–2.7，照表格补行 |
| 4 | ~~网页控制台只验到首屏~~ | **已完成并超出**：`docs/tools/console-e2e.mjs` 用真浏览器 + CDP 跑 **69 条**页面级断言，且已接进 `release-check.mjs` 第 4 段（第 8 条判据） |
| 5 | ~~版本停在 0.0.0.44~~ | 已过时：现在 `0.0.0.100`。升版本必须同步 `docs/CONTEXT.md`（`ContextDocFreshnessGuardTest` 钉着） |
| 6 | **发布自检的判据数** | 现在是 **8 条**：构建成功 / 单测全绿 / APK 产出 / 安装成功 / 进入设置页 / 四分区按序 / 几何已采样 / 控制台页面 E2E。全部通过才打 tag |
| 7 | **工作区路径** | 仍是新会话注意事项：GUI 里要选 D 盘工作区，否则读不到 `docs/CONTEXT.md` |
| 8 | ~~压缩只能用户发 `/compact`~~ | **已解决**（2026-11-18 插件更新）：`manage_context({action:"compact"})` 现在会排队到本轮结束后执行 |
| 9 | ~~`@local/android-wear-toolbox` 没验过~~ | **已可用**：当前工具面有 `android_devices` / `android_build` / `android_install` / `android_screenshot` / `android_ui_dump` / `android_logcat` |

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

# 2) 单测基线（约 2 分钟；必须是 1377 条 / 209 套件 / 0 失败）
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
| 发布自检 | `node docs/tools/release-check.mjs`，4 段共 **8 条判据**（构建/单测/APK/安装/进入设置页/四分区/几何/控制台 E2E） |

---

## 4. 硬约束（违反即返工）

1. **禁止任何横向拉伸 / 横向形变**；动画**只允许缩小**，`scaleX == scaleY`。
2. **凡是改变 scale 的动画必须不过冲**：`dampingRatio = 1`（有守卫测试 `ScaleAnimationOvershootGuardTest`）。
3. **M3E 卡片不得相互重合**，且**所有卡片同一宽度**（176dp / 实测 352px，边缘只淡出不缩放）。
4. **所有交付必须真机验证**，不能用"编译通过"代替交互验收。

---

## 5. 不要做的事

- **不要用方形截图判断布局**：屏幕是 466×466 的**圆**，方图四角在物理屏上不存在。
  看任何截图前先跑 `python docs/tools/circle-shot.py <png>`（`style-shots-v2.mjs` 已自动做）。
  这条被用户提醒过两次，别再犯。

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
| `wear/src/test/java/...` | 1377 条单测；**结构性守卫**在这里（改动前先看它们） |
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
