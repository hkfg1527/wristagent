# 长期上下文（跨轮恢复的唯一入口）

> 每轮开工先读这一页。它只记**会变的东西**：硬约束、已定决策、当前阶段、待办、恢复步骤。
> 事实性细节去代码与 `docs/PERF-*.md`、`docs/brand/*`、`docs/DESIGN-*.md`。

## 0. 恢复步骤（新会话照做）

1. `docs/CONTEXT.md`（本页）→ `docs/PREP-LONGTERM.md` → `CHANGELOG.md` 顶部
2. `git log --oneline -8`、`git status --porcelain` 确认工作区干净
3. 环境配方：`docs/HANDOFF-0.05.md`（无 shell 工具、gradle env、adb、GUI、git push 的坑）
4. 跑一次 `./gradlew :wear:testDebugUnitTest` 确认基线（当前 **1189** 条）

## 0.5 运行中状态（每轮覆盖这一段，别追加）

- 当前阶段：**P0 1/3 完成**（卡片重合已修，0.0.0.2 上机验证）；P1 工具链已打通（表冠可注入、input-probe、验收矩阵）。
- 待用户拍板：返回键两条改法、字体“扁”的具体位置、圆环规则。
- 版本：`0.0.0.2`（versionCode 40），**未打 tag、未发 release**（按 10 版一发的规矩）。
- 工作区：干净；远端 == 本地（不一致就先重推，用 `http.curloptResolve`）。
- 单测基线：**1192**。
- 台账：`docs/PREP-LONGTERM.md`（准备事项）、`docs/DESIGN-SPEC.md`（动效规范）、`docs/PERF-0.05.md`（性能）。
- 下一步（解锁后第一件事）：修"入场动画期间卡片重合"（已定位：`RowEnterOffset` 24dp 位移）。

## 0.6 上下文自管理（怎么用、边界在哪）

**结论先说：我调不了压缩接口。** 本会话工具面里没有"压缩上下文"这类工具，自动压缩由 harness 在上下文将满时触发
（本会话已经发生过一次）。所以我的职责不是"按下压缩键"，而是**让压缩无损**：

1. **唯一记忆源**：`docs/CONTEXT.md`。压缩后不能丢的东西必须落在这里，不留在聊天里。
2. **钉住不变量**：`fact_register` 最多 12 条，压缩后仍在眼前。
3. **控制工作集**：先 `grep` 定位再 `read` 窄区间；大日志只取尾部；不重复读同一文件。
4. **阶段边界主动总结**：一个任务收口就写进 CONTEXT / CHANGELOG，再开下一个任务。
5. **每轮自检**：三句话回答"硬约束是什么 / 当前 P0 是什么 / 版本号到哪"，答案必须来自仓库文件。
6. **想要更快的压缩**：开新会话即可 —— 只要 `docs/CONTEXT.md` 在，新会话直接接上。
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
