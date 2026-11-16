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
