# Google Health 像素级复刻规格（0.04-beta）

## 取数方法

1. 从 Pixel Watch 2（`42021RTJWWP97H`，384×384，320dpi → density 2.0）拉取
   `com.fitbit.FitbitMobile` 的 base.apk（31.8 MB）到 `docs/ref/health-base.apk`。
2. `aapt2 dump resources` 导出资源表（`docs/ref/health-resources.txt`）：颜色/dimen 资源名
   全部被混淆成 `0_resource_name_obfuscated`，Compose 的颜色写在 dex 里，资源表扒不出主题色，
   因此**改用界面上取像素**（等价且可验证）。
3. 用 PIL 对实机截图逐像素统计主色、卡片 bbox、圆角弧、环直径
   （脚本 `docs/ref/measure_health.py`，截图 `docs/device/pixelwatch2/H04/H05`）。

## 实测值（density 2.0，px → dp 除以 2）

| 项目 | 实测（px） | dp | 用途 |
| --- | --- | --- | --- |
| 页面底色 | #000000 | — | 全屏纯黑 |
| 卡片底色 | #2A313C（42,49,60） | — | 所有度量卡 |
| 卡片圆角 | ≈49 | 24 | `Wear.Radius.Card` |
| 卡片左右内缩 | 20 | 10 | 宽 344px = 172dp |
| 环直径 | ≈100 | 50 | 卡片右侧指示器 |
| 环轨道色 | #48556A | — | track |
| 强调色·蓝 | #7FCFFF（127,207,255） | — | 步数卡（进度环 + chip） |
| 强调色·长春花 | #A8C7FA（168,199,250） | — | 第二档度量 |
| 强调色·薄荷 | #C4EED0（196,238,208） | — | 第三档度量（爬楼层数标签） |
| 次级文字 | #BDCBE2（189,203,226） | — | 卡片标签 |
| 主文字 | #FFFFFF | — | 大数值 |

圆角验证（H04 步数卡顶边逐行宽度）：顶行 246px、第 8 行 296px、第 15 行 312px；
圆半径 49px 的圆弧在这些偏移上算得 39.1 / 22.2 / 13.7px 内缩，实测 41 / 24 / 16px，
误差 ≤2px，判定 r≈49px。

## 落到实现

- `Wear.CardModel/CardTools/CardVoice/CardSystem` = 上述四种实测强调色（**不跟随主题强调色**，
  谷歌给每种度量固定色相）。
- `SettingsCardSurface = #2A313C`（卡片档专用局部材质色，不进 Palette：上限 14 token）。
- `SettingsCardStyle.RingSize = 50.dp`，轨道 4dp / 进度 6dp。
- 卡片圆角沿用 `Wear.Radius.Card`（24dp），与实测一致。
- 大数值用 `Wear.TextDisplay`（displayMedium），标签用 `Wear.TextCaption`。

## 与谷歌的差异（有意）

- 卡片宽度取 `SettingsContentWidth`（176dp）而不是满宽 172dp：本项目所有设置行共用这条内容轴，
  改宽会破坏三套风格的统一。
- 环内放矢量图标（麦克风/滑轨/品牌字），谷歌用的是度量专属插图；表端无位图素材，
  先用矢量，后续可换位图。
