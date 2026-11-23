#!/usr/bin/env python3
"""Generate crisp anti-aliased Android launcher icons for КАМЕРА."""
import os
from PIL import Image, ImageDraw

RES_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "android", "res")
SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def render_icon(size: int, round_mask: bool = False) -> Image.Image:
    scale = 4
    S = size * scale
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    # Background shape (rounded square or circle)
    bg_color = (24, 25, 28, 255)  # #18191C
    pad = int(S * 0.04)
    if round_mask:
        d.ellipse([pad, pad, S - pad, S - pad], fill=bg_color)
    else:
        d.rounded_rectangle([pad, pad, S - pad, S - pad], radius=int(S * 0.24), fill=bg_color)

    # Soft pastel coral/terracotta accent circle inside bottom-right
    d.ellipse([int(S * 0.16), int(S * 0.16), int(S * 0.84), int(S * 0.84)], fill=(38, 40, 46, 255))

    # Top butter-yellow S-hook ring
    hook_w = max(2, int(S * 0.045))
    d.arc(
        [int(S * 0.42), int(S * 0.16), int(S * 0.58), int(S * 0.30)],
        start=180,
        end=40,
        fill=(245, 213, 71, 255),
        width=hook_w,
    )
    d.line(
        [(int(S * 0.50), int(S * 0.28)), (int(S * 0.50), int(S * 0.36))],
        fill=(250, 248, 241, 255),
        width=hook_w,
    )

    # Plump artisanal hanging cut in warm coral (#EA7B72)
    cut_box = [int(S * 0.32), int(S * 0.35), int(S * 0.68), int(S * 0.82)]
    d.rounded_rectangle(cut_box, radius=int(S * 0.17), fill=(234, 123, 114, 255))

    # Inner deep marbling highlight + twine lines
    d.rounded_rectangle(
        [int(S * 0.37), int(S * 0.41), int(S * 0.44), int(S * 0.74)],
        radius=int(S * 0.04),
        fill=(255, 224, 216, 110),
    )

    twine_w = max(2, int(S * 0.028))
    for y_frac in (0.49, 0.60, 0.71):
        y = int(S * y_frac)
        d.line([(int(S * 0.33), y), (int(S * 0.67), y)], fill=(250, 248, 241, 225), width=twine_w)

    # Butter-yellow progress badge dot at top-right of emblem
    bx, by, br = int(S * 0.72), int(S * 0.31), int(S * 0.09)
    d.ellipse([bx - br, by - br, bx + br, by + br], fill=(245, 213, 71, 255))

    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    for folder, px in SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        render_icon(px, round_mask=False).save(os.path.join(out_dir, "ic_launcher.png"))
        render_icon(px, round_mask=True).save(os.path.join(out_dir, "ic_launcher_round.png"))
    print("Generated launcher icons for all densities.")


if __name__ == "__main__":
    main()
