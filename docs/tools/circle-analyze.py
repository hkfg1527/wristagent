#!/usr/bin/env python
"""圆屏截图的**数值**检查（不需要人眼/图像输入）。

输出：
  - 每一行的"有内容像素"区间（亮度 > 阈值）
  - 圆内可用弦长 vs 实际内容宽度
  - 首屏可见的设置项行数（按 y 方向的墨迹带计数）
"""
import sys
from PIL import Image

THRESH = 40


def analyze(path):
    im = Image.open(path).convert("RGB")
    w, h = im.size
    cx, cy, r = w / 2, h / 2, min(w, h) / 2
    rows = []
    for y in range(h):
        xs = [x for x in range(w)
              if ((x - cx) ** 2 + (y - cy) ** 2) <= r ** 2
              and sum(im.getpixel((x, y))) > THRESH]
        rows.append((y, xs))
    # 连续有内容的 y 区间
    bands, start = [], None
    for y, xs in rows:
        has = len(xs) > 3
        if has and start is None:
            start = y
        if not has and start is not None:
            bands.append((start, y - 1))
            start = None
    if start is not None:
        bands.append((start, h - 1))
    # 每条带最宽的内容跨度
    spans = []
    for a, b in bands:
        best = 0
        for y in range(a, b + 1):
            xs = rows[y][1]
            if xs:
                best = max(best, max(xs) - min(xs))
        spans.append((a, b, best))
    print(f"file={path} size={w}x{h}")
    print(f"圆内墨迹带数={len(bands)}（≈屏幕上可见的行/卡片数）")
    for a, b, span in spans[:14]:
        print(f"  y={a:3d}..{b:3d}  高={b-a+1:3d}px  最宽内容跨度={span:3d}px")
    print(f"圆直径={int(2*r)}px  最大内容跨度={max((s[2] for s in spans), default=0)}px")


if __name__ == "__main__":
    for p in sys.argv[1:]:
        analyze(p)
