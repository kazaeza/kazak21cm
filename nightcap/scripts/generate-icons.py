#!/usr/bin/env python3
"""Generate cosmic nightcap. Android launcher icons (legacy + round + adaptive foreground)."""

import math
import os
from PIL import Image, ImageDraw, ImageFilter

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
RES_DIR = os.path.join(ROOT, "android", "res")

DENSITIES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}


def draw_four_point_star(size: int, scale: float = 0.56, offset=(0.0, 0.0)) -> Image.Image:
    """Render a smooth, anti-aliased pastel-gradient 4-point star spark."""
    hi = size * 2
    cx = hi * (0.5 + offset[0])
    cy = hi * (0.5 + offset[1])
    radius = hi * 0.5 * scale

    # Supersampled mask
    mask = Image.new("L", (hi, hi), 0)
    px = mask.load()
    # Astroid-like concave 4-point star: |x/r|^0.58 + |y/r|^0.58 <= 1
    p = 0.54
    x0 = max(0, int(cx - radius - 2))
    x1 = min(hi, int(cx + radius + 3))
    y0 = max(0, int(cy - radius - 2))
    y1 = min(hi, int(cy + radius + 3))

    for y in range(y0, y1):
        dy = abs(y - cy) / radius
        dy_p = dy ** p
        if dy_p > 1.05:
            continue
        for x in range(x0, x1):
            dx = abs(x - cx) / radius
            val = (dx ** p) + dy_p
            if val <= 1.0:
                alpha = 255
            elif val < 1.045:
                alpha = int(255 * (1.0 - (val - 1.0) / 0.045))
            else:
                alpha = 0
            if alpha > 0:
                px[x, y] = alpha

    # Pastel gradient fill: #ffd5b8 -> #ff8fb8 -> #8cb4ff
    grad = Image.new("RGBA", (hi, hi), (0, 0, 0, 0))
    gpx = grad.load()
    for y in range(y0, y1):
        for x in range(x0, x1):
            a = px[x, y]
            if a == 0:
                continue
            t = ((x - x0) / max(1, x1 - x0) * 0.55) + ((y - y0) / max(1, y1 - y0) * 0.45)
            t = max(0.0, min(1.0, t))
            if t < 0.5:
                k = t * 2.0
                r = int(255 * (1 - k) + 255 * k)
                g = int(218 * (1 - k) + 143 * k)
                b = int(188 * (1 - k) + 190 * k)
            else:
                k = (t - 0.5) * 2.0
                r = int(255 * (1 - k) + 140 * k)
                g = int(143 * (1 - k) + 184 * k)
                b = int(190 * (1 - k) + 255 * k)
            # Inner white luminous core
            dist = math.hypot((x - cx) / radius, (y - cy) / radius)
            core = max(0.0, 1.0 - dist * 2.4)
            r = min(255, int(r + (255 - r) * core * 0.6))
            g = min(255, int(g + (255 - g) * core * 0.6))
            b = min(255, int(b + (255 - b) * core * 0.6))
            gpx[x, y] = (r, g, b, a)

    return grad.resize((size, size), Image.Resampling.LANCZOS)


def make_glow_blob(size: int, center, rad: float, color, alpha: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    cx, cy = int(size * center[0]), int(size * center[1])
    r = int(size * rad)
    draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(*color, alpha))
    return layer.filter(ImageFilter.GaussianBlur(radius=max(2, int(size * rad * 0.55))))


def render_cosmic_foreground(size: int, star_scale: float) -> Image.Image:
    img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    # Soft nebula aura behind the star
    img = Image.alpha_composite(img, make_glow_blob(size, (0.42, 0.44), 0.24, (190, 110, 255), 115))
    img = Image.alpha_composite(img, make_glow_blob(size, (0.60, 0.56), 0.22, (255, 118, 175), 95))
    img = Image.alpha_composite(img, make_glow_blob(size, (0.52, 0.36), 0.19, (110, 185, 255), 85))

    # Main 4-point nightcap spark
    star = draw_four_point_star(size, scale=star_scale)
    # Soft halo copy of star
    star_halo = star.filter(ImageFilter.GaussianBlur(radius=max(2, int(size * 0.045))))
    img = Image.alpha_composite(img, star_halo)
    img = Image.alpha_composite(img, star)

    # Two tiny companion cosmic sparkles
    img = Image.alpha_composite(img, draw_four_point_star(size, scale=star_scale * 0.24, offset=(0.21, -0.19)))
    img = Image.alpha_composite(img, draw_four_point_star(size, scale=star_scale * 0.16, offset=(-0.22, 0.18)))
    return img


def render_full_icon(size: int, circular: bool) -> Image.Image:
    bg = Image.new("RGBA", (size, size), (7, 6, 17, 255))
    bg = Image.alpha_composite(bg, make_glow_blob(size, (0.5, 0.48), 0.42, (36, 22, 74), 210))
    fg = render_cosmic_foreground(size, star_scale=0.66)
    composed = Image.alpha_composite(bg, fg)

    if circular:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(composed, (0, 0), mask)
        return out
    return composed


def main():
    # Adaptive foreground (432x432 xxxhdpi safe-zone scale)
    fg_dir = os.path.join(RES_DIR, "mipmap-xxxhdpi")
    os.makedirs(fg_dir, exist_ok=True)
    fg = render_cosmic_foreground(432, star_scale=0.46)
    fg.save(os.path.join(fg_dir, "ic_launcher_foreground.png"))

    # Legacy & round icons for all densities
    for folder, sz in DENSITIES.items():
        d = os.path.join(RES_DIR, folder)
        os.makedirs(d, exist_ok=True)
        render_full_icon(sz, circular=False).save(os.path.join(d, "ic_launcher.png"))
        render_full_icon(sz, circular=True).save(os.path.join(d, "ic_launcher_round.png"))

    print("Generated all Nightcap Android launcher icons cleanly.")


if __name__ == "__main__":
    main()
