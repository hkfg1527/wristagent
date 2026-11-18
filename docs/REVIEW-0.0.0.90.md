# 0.0.0.90 整十代码审查（5 路子智能体）

> 触发规则：用户定下「每 10 个内部版派 ≥5 个子智能体做全代码审查」。
> 本轮派 5 路：设置页 Compose / 控制台与网页 / agent 核心 / 测试有效性 / 文档一致性。
> **4 路有结论，1 路（设置页 Compose）运行中失败、无产出**，已排进 0.0.0.91。
> 全部只读审查（不改文件、不跑构建），下面每条都由我复核代码后决定处置。

## 一、本轮就修（3 条高危，全部带回归）

| # | 缺陷 | 证据 | 处置 |
|---|---|---|---|
| 1 | `ShellPolicy.normalize` 把 `\n` 折成空格，`ls\nreboot -p` 归一化后只剩首词 → 判 `SAFE`；`sh -c` 两条都跑 | `ShellPolicy.kt` 旧 `dangerOf` 只看首词 | 按原始串的 `[\n\r;|&]` 拆开逐条判、取最坏档；`ShellPolicyTest` +4 条（换行 / 分号 / 多目标 rm / 不过度升档） |
| 2 | `rm` 只检查**第一个**目标：`rm -rf /sdcard/Download /data` 的 `/data` 从未被查 → 判 RISKY（可被"记住此命令"永久放行） | 旧 `rmInvocation` 正则只捕一个目标 | 改成捕获整段参数、逐个非选项词判定，选项与目标任意排序都成立；同批测试覆盖 |
| 3 | 控制台把 NanoHTTPD 的 `postData`（其实是**正文文本**）当文件路径 `File(raw).isFile → readBytes → delete()`，且发生在鉴权**之前** → 无令牌 POST 一个真实路径即可删任意文件 | `ConsoleServer.kt` 旧 139-167 行 | 整段删除，正文一律按文本处理；`ConsoleServerTest` +1 条（POST 一个真实路径，断言 401 且文件仍在） |

附带同批修掉：**审批回调缺失时默认放行**（`AgentDispatcher` 两处 `?: true` → `?: false`）。
`ChatViewModel.onCleared` 会把回调置空，回收瞬间排队的审批点原本会自动通过。

## 二、记录在案，排进后续（按严重度）

### agent 核心（12 条，已修 4 条）

- ~~**高**｜子代理工具执行漏 `SandboxGate`~~ → **已在 0.0.0.92 修**，`SandboxWiringGuardTest` 2 条守卫钉住（变异验证过）。
- **中**｜`conversationHistory` 是裸 `mutableListOf`，执行中切会话可能 `ConcurrentModificationException` 或串会话。
- **中**｜请求侧 `takeLast(turns*2)` 不剔除孤立 `tool` 回执；`maxContextTurns` 无 `coerceIn`（0 会静默丢全部历史）。
- **中**｜`PrivilegedCommandRunner` 的外层 `withTimeoutOrNull` 与 `RootShell` 的 `future.get(15s)` 口径冲突 → 超时后命令仍在跑且持锁。
- **中**｜子代理读超时上限 180s > 总预算 120s，`SUB_AGENT_READ_TIMEOUT_MS` 是死常量。
- **中**｜`CompressPolicy.triggerAt` 与 `keepMessages` 口径冲突：阈值 ≤0.6 时压缩从不生效（0.3–0.6 是假开关）。
- **中**｜`SecurityGuardrail.isFatalCommand` 对**全部**工具做 `contains("format ")` → `web_search`"how to format a disk"被硬拦。
- **低**｜Shizuku 绑定超时不 `unbind`；`fetchRemoteModels` 不 `disconnect()`；`PrivilegeManager.release()` 不 cancel scope。

### 控制台（10 条，已修 1 条）

- **中**｜`stop()` 先 `shutdownNow()` 再关监听 → 在跑的 `clear-memory`/批量写可能半套生效。
- **中**｜网页「测试连接」打的是服务端不存在的 `connection-test` action，恒 404。
- **中**｜第 6 次错令牌变 429，网页把它渲染成「服务没起来」；`/api/*` 错令牌 POST 是简单请求，可被跨站打满。
- **中**｜存储层拒绝被混进 `ignored`，网页随即用旧值抹掉用户改动，只提示"已保存"。
- **中**｜档案超 5 条/重名时静默丢弃仍回 200，网页重绘把正在输入的行拆掉。
- **低**｜`PooledRunner` 先 submit 后 add 的注册竞态；500 无日志；开关行没有 dirty 保护；`tokenMasked` 对 4 位令牌其实是明文；含 `**` 的密钥被判"掩码回传"永远写不进去；`E2E_SKIP` 会让 47 条断言静默消失而退出码仍 0。

### 测试有效性（12 条）

最值得先修的三条（都属于"守卫看起来在守、其实恒绿"）：

1. `SettingsSubtitleLineGuardTest`：`substringBefore("可进入行")` 的分隔符在 `SettingsShared.kt` 里**不存在** → 切片变成 526 行整段文件。
2. `SettingsStylePolicyTest`：`spec.shell != null`、`back in SettingsBackControl.values()` 被类型系统保证恒真 → 该用例实际只测了"有 6 套"。
3. `SettingsVisualGuardTest`：断言 `slot.contains("trailing")`，而切片起点正是声明 `trailing` 形参的那个函数 → 永远绿。

另外：`AgentToolHonestyTest` 三条是自证式断言（自己 new 日期、自己比较常量），从未调用被测工具；
`WearAppearance` 全局单例跨 12 个 Robolectric 类不复位（已在两处注释里被观察到）；`FakeMcpServer` 的超时断言上界与假服务器睡眠同值。

### 文档一致性（12 条，本轮修 7 条）

本轮已修：HANDOFF §0/§0.5 全表（旧版 5 条已完成）、CONTEXT 两处写死的旧基线（1338 / 1189）、
风格"5 种"→6 套、发布自检"7 项"→8 条判据、M3_TRANSFORM"行转场 ✅"→未接线、边缘缩放"0.70/0.50"→只淡出、
§4 历史待办加更正抬头、`SETTINGS-FUNCTIONAL-TEST` 与 `CONTEXT` 的"N 个设置键"口径不一的说法改成"11 个测试类 / 39 条用例"。

仍待修：`SETTINGS-IA.md` 的四个统计口径（偏好属性 58 / 常量 56 / 二级页 17 / 双语 414）、
根页行数 14→15 且 S3 补 Lux 终端、HANDOFF §6"20 个可调设置"（实为 51 个 `KEY_*`）。

## 三、教训

- **安全闸门的默认值必须是"关"**：`?: true` 这种写法在安全路径上等于后门。
- **判档要看原始串**：任何"先归一化再判断"的安全检查，都必须确认归一化没有丢掉语义（换行就是分隔符）。
- **守卫测试自己也会烂**：`substringAfter/Before` 找不到锚点时会返回整串，断言于是静默成立 ——
  以后写源码守卫，一律先 `assertTrue(source.contains(anchor))` 再切片。
