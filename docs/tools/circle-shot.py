#!/usr/bin/env python
"""把耳拍截图裁成**圆屏**：圆外区域涂成纯黑（手表那里是看不见的边框）。

为什么必须这么做：TicWatch Pro 5 是 466x466 的**圆形**屏，而 `screencap` 给的是 466x466 方图。
方图的四个角在物理屏上根本不存在 —— 只看方图会做出两类错判：
  1. 把"角落里的空白"当成设计留白（用户看不到那块）；
  2. 忽略贴边内容被**圆弧切掉**（例如右下角的按钮、最后一行文字）。

用法：
    python docs/tools/circle-shot.py docs/device/v2five/*.png
    python docs/tools/circle-shot.py --out docs/device/v2five/circle docs/device/v2five/*.png
"""
import sys
import os
import glob
from PIL import Image, ImageDraw

RING = 4  # 圆周描边宽度（像素），只为看清边界，不影响判定


def to_circle(src: str, dst: str) -> None:
    im = Image.open(src).convert("RGBA")
    w, h = im.size
    d = min(w, h)
    # 以短边为准做正圆（方图直接内切；非方图居中裁切）
    left = (w - d) // 2
    top = (h - d) // 2
    im = im.crop((left, top, left + d, top + d))

    mask = Image.new("L", (d, d), 0)
    ImageDraw.Draw(mask).ellipse((0, 0, d - 1, d - 1), fill=255)

    out = Image.new("RGBA", (d, d), (0, 0, 0, 255))
    out.paste(im, (0, 0), mask)

    # 描一圈浅灰，便于肉眼确认"这是屏幕边界"
    ImageDraw.Draw(out).ellipse((0, 0, d - 1, d - 1), outline=(90, 90, 90, 255), width=RING)
    out.save(dst)
    return d


def main(argv):
    args = argv[1:]
    out_dir = None
    if args and args[0] == "--out":
        out_dir = args[1]
        args = args[2:]
    if not args:
        print("usage: circle-shot.py [--out DIR] <png...>")
        return 2

    files = []
    for pattern in args:
        hits = glob.glob(pattern)
        files.extend(hits if hits else [pattern])

    total = 0
    for src in files:
        if not os.path.isfile(src):
            print(f"SKIP {src} (not found)")
            continue
        name = os.path.basename(src)
        dst_dir = out_dir or os.path.dirname(os.path.abspath(src))
        os.makedirs(dst_dir, exist_ok=True)
        dst = os.path.join(dst_dir, name.replace(".png", "-circle.png"))
        d = to_circle(src, dst)
        print(f"OK {os.path.relpath(dst)} ({d}x{d})")
        total += 1
    print(f"CIRCLE_SHOTS_OK {total}")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
