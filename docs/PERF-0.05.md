# 0.05/0.06 性能实测

> 只写真的量过的东西。**三次结论修正都留在文末**，因为前两次的推断都被后来的数据推翻了。

## 设备与方法

- TicWatch Pro 5（`C101X35122973`），466×466，屏幕 **60Hz**（`dumpsys display` 报 `fps=60.000004`），
  一帧预算 16.67ms。
- release APK（`assembleRelease`），不是 debug 包。
- 采集：`adb shell dumpsys gfxinfo <pkg> reset` → `adb shell input swipe` 脚本化滑动（每场景 10~12 次，
  300ms 一次、间隔 420~450ms，位移 190~210px）→ `dumpsys gfxinfo`（直方图）与
  `dumpsys gfxinfo <pkg> framestats`（逐帧时间戳）。
- 判据用平台自己的直方图（`Janky frames (legacy)`、50/90/99th percentile），不自己造公式。

## 直方图（同一套脚本，多次运行）

| 场景 | 首次 | 复跑 | p50 | p90 | p99 |
|---|---|---|---|---|---|
| 聊天页 | 2.53% | **17.82%** | 12 / 14ms | 15 / 23ms | 17ms |
| 设置页 · 终端档 | 9.39% | 4.78% | 13 / 12ms | 17 / 15ms | 25 / 22ms |
| 设置页 · 大卡片档 | 32.14% | 37.54% | 16ms | 28 / 32ms | 73ms |
| 设置页 · 方角档 | **75.17%** | **7.58%** | 22 / 13ms | 31 / 16ms | 53 / 24ms |
| 设置页 · 气泡档 | **84.91%** | **70.93%** | 26 / 21ms | 34 / 31ms | 53 / 42ms |

## 逐帧相位拆解（`framestats`，按列算）

| 相位 | 含义 | 气泡档 p50 / p90 | 聊天页 p50 / p90 |
|---|---|---|---|
| `IntendedVsync → PerformTraversalsStart` | Choreographer 回调 + Compose 重组与应用快照（**framestats 不细分**） | 5.49 / 7.01ms | 3.30 / 5.79ms |
| `PerformTraversalsStart → DrawStart` | 布局 | 0.08 / 0.09ms | — |
| `DrawStart → SyncStart` | 记录绘制指令（含气球路径构建、渐变填充、每行内容） | 2.16 / 3.13ms | 2.28 / 4.11ms |
| `IssueDrawCommandsStart → SwapBuffers` | 提交与等 GPU | 1.69 / 2.71ms | 3.53 / 6.75ms |
| **整帧** | `IntendedVsync → FrameCompleted` | **12.39 / 19.52ms** | **14.75 / 24.34ms** |

## 读数（这一版才是站得住的）

1. **绘制不是瓶颈。** 记录指令 2.2ms、等 GPU 1.7~3.5ms、布局 0.08ms —— 三段加起来只占整帧的
   一半多一点。之前"气球路径每帧重建是主因"的推断因此是错的。
2. **同一场景跑两次可以差一个数量级**（方角档 75% ↔ 7.6%；气泡档 85% ↔ 71%；聊天页 2.5% ↔ 17.8%）。
   在这个样本量下，"哪种风格更卡"的排序**不成立**，只能说：这台表上所有场景的 p50 都落在 12~26ms，
   也就是**卡在 16.67ms 预算上下**，全都在边缘。
3. 最大的一块没解释的时间在 `IntendedVsync → PerformTraversalsStart`（气泡档 5.5ms、聊天页 3.3ms），
   那是 Choreographer 回调 + Compose 重组 + 快照应用，`framestats` 到这一层就断了。
   要再往下切，只能上 Perfetto 的 CPU 火焰图。

## 三次被推翻的推断（留着当反面教材）

| 推断 | 实验 | 结果 |
|---|---|---|
| "每帧写 snapshot state 是掉帧主因" | 行内缩放改成绘制阶段 `scale()`、去掉图层与 state 写入 | **错**：85% → 91%。`graphicsLayer` 的图层缓存是在帮忙 |
| "整页气球轮廓每帧重建是主因" | 跳过 `buildGroupPath`+`drawPath` | 掉帧减半，但相位拆解显示绘制总共只有 2.2ms —— 减掉的是别的东西（很可能是一次 saveLayer 级别的图层代价），不是路径构建 |
| "气泡档 85% 而聊天页 2.5%，所以设置页比聊天页差得多" | 复跑 | **错**：复跑后两者是 71% vs 17.8%，同一场景自身波动就有一个数量级 |

## 下一步（按证据排序）

1. Perfetto 抓 `preTrav` 那 3~5.5ms（`perfetto -c -o /data/local/tmp/t.pftrace sched freq gfx view am`），
   看是重组、是快照应用、还是主线程被别的回调占着。
2. 每场景跑 5 次取中位数，把波动压下去再排序。
3. 若确实卡在重组：用 `Composition Tracing`（studio 的 recomposition counts / `Layout Inspector`）
   找出滚动时被无关状态顶着重组的节点。
