# 设置项功能实测报告（真机 · 2026-11-18）

> 用户要求："全部功能测试一下设置页的功能，是否切换了能正常使用，全部测试全盘测试。"
> 本文件记录**已经测到的**、**测了但结论不成立的**、以及**还没测的**。三者分开写，不混。

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

## 三·五 探针已入库（0.0.0.64）\n\n工具：`docs/tools/sweep-settings.mjs`（从 build-logs 提进仓库，附带两个坑的说明：\n布尔值是 XML 属性、点击落点要接受 110..370 且滚不动时记 INCONCLUSIVE 而不是判失败）。\n**它目前还不能给出这三页的可信结论** —— 见上一节的"结论不成立"。\n\n## 四、探针现状

`build-logs/probe-settings-sweep.mjs`：能跑，判据是"prefs 有没有变"。
**已知缺陷**：不验证点击是否命中；页面标题判据在官方档下要放宽（首条文本是「返回」）。

下一版要加的三件事：
1. 点击后读 UI 语义（`stateDescription` / 控件 checked）而不是只读 prefs；
2. 滑杆用「点轨道两端 + 读数值文本」两步验证；
3. 每次点击前重新 dump 并确认目标行在安全带宽内（110..370）。
