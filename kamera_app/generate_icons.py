#!/usr/bin/env python3
"""Generate artisanal editorial 'f' + botanical fermentation drop launcher & adaptive icons for ferment."""
import math
import os
from PIL import Image, ImageDraw, ImageFont

ROOT_DIR = os.path.dirname(os.path.abspath(__file__))
RES_DIR = os.path.join(ROOT_DIR, "android", "res")

SIZES = {
    "mipmap-mdpi": 48,
    "mipmap-hdpi": 72,
    "mipmap-xhdpi": 96,
    "mipmap-xxhdpi": 144,
    "mipmap-xxxhdpi": 192,
}

ADAPTIVE_FG_SIZES = {
    "mipmap-mdpi": 108,
    "mipmap-hdpi": 162,
    "mipmap-xhdpi": 216,
    "mipmap-xxhdpi": 324,
    "mipmap-xxxhdpi": 432,
}

FONT_CANDIDATES = [
    "/usr/share/fonts/truetype/freefont/FreeSerifBoldItalic.ttf",
    "/usr/share/fonts/truetype/liberation/LiberationSerif-BoldItalic.ttf",
    os.path.join(ROOT_DIR, "..", "roman_merzlikin_bday", "fonts", "PT_Serif-Web-Bold.ttf"),
]


def load_serif_font(px_size: int) -> ImageFont.FreeTypeFont:
    for path in FONT_CANDIDATES:
        if os.path.exists(path):
            return ImageFont.truetype(path, px_size)
    return ImageFont.load_default()


def draw_organic_droplet(canvas: Image.Image, cx: float, cy: float, radius: float, angle_deg: float, color: tuple):
    """Draw a smooth tangent-matched botanical/fermentation droplet."""
    box_s = int(radius * 5.2)
    drop = Image.new("RGBA", (box_s, box_s), (0, 0, 0, 0))
    dd = ImageDraw.Draw(drop)
    mid = box_s / 2.0

    # Circle center at (mid, mid + 0.35*r), radius = r, apex at (mid, mid - 1.50*r)
    # Exact tangent angle: sin(alpha) = r / D where D = 1.85 * r -> alpha = asin(1 / 1.85) ~ 32.7 deg
    cy_off = 0.35 * radius
    apex_off = -1.50 * radius
    D = cy_off - apex_off  # 1.85 * r
    alpha = math.degrees(math.asin(radius / D))  # ~32.72 deg

    poly = [(mid, mid + apex_off)]
    start_deg = -alpha
    end_deg = 180.0 + alpha
    steps = 60
    for i in range(steps + 1):
        deg = start_deg + (end_deg - start_deg) * (i / steps)
        rad = math.radians(deg)
        px = mid + math.cos(rad) * radius
        py = mid + cy_off + math.sin(rad) * radius
        poly.append((px, py))

    dd.polygon(poly, fill=color)
    rotated = drop.rotate(angle_deg, resample=Image.Resampling.BICUBIC, expand=False)
    canvas.alpha_composite(rotated, (int(cx - mid), int(cy - mid)))


def draw_foreground_emblem(canvas: Image.Image, box_x0: float, box_y0: float, box_size: float):
    """Draw signature editorial italic 'f' + terracotta drop + warm gold spore."""
    d = ImageDraw.Draw(canvas)

    # Editorial serif bold-italic 'f' in warm cream (#FAF8F1)
    font_px = int(box_size * 0.72)
    font = load_serif_font(font_px)
    bbox = d.textbbox((0, 0), "f", font=font)
    glyph_w = bbox[2] - bbox[0]
    glyph_h = bbox[3] - bbox[1]

    tx = box_x0 + (box_size - glyph_w) * 0.42 - bbox[0]
    ty = box_y0 + (box_size - glyph_h) * 0.48 - bbox[1]
    d.text((tx, ty), "f", font=font, fill=(250, 248, 241, 255))

    # Terracotta fermentation droplet (#EA7B72) balanced in lower-right counter
    drop_cx = box_x0 + box_size * 0.675
    drop_cy = box_y0 + box_size * 0.615
    draw_organic_droplet(
        canvas,
        cx=drop_cx,
        cy=drop_cy,
        radius=box_size * 0.088,
        angle_deg=-20,
        color=(234, 123, 114, 255),
    )

    # Warm butter-gold culture spore dot (#F5D547) floating softly above droplet
    dot_cx = box_x0 + box_size * 0.735
    dot_cy = box_y0 + box_size * 0.435
    dot_r = box_size * 0.032
    d.ellipse([dot_cx - dot_r, dot_cy - dot_r, dot_cx + dot_r, dot_cy + dot_r], fill=(245, 213, 71, 255))


def render_legacy_icon(size: int, round_mask: bool = False) -> Image.Image:
    scale = 4
    S = size * scale
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)

    pad = int(S * 0.04)
    bg = (24, 25, 28, 255)  # Warm graphite #18191C
    if round_mask:
        d.ellipse([pad, pad, S - pad, S - pad], fill=bg)
    else:
        d.rounded_rectangle([pad, pad, S - pad, S - pad], radius=int(S * 0.24), fill=bg)

    draw_foreground_emblem(img, pad, pad, S - 2 * pad)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def render_adaptive_foreground(size: int) -> Image.Image:
    scale = 4
    S = size * scale
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    safe_box = S * (66.0 / 108.0)
    offset = (S - safe_box) / 2.0
    draw_foreground_emblem(img, offset, offset, safe_box)
    return img.resize((size, size), Image.Resampling.LANCZOS)


def main():
    for folder, px in SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        render_legacy_icon(px, round_mask=False).save(os.path.join(out_dir, "ic_launcher.png"))
        render_legacy_icon(px, round_mask=True).save(os.path.join(out_dir, "ic_launcher_round.png"))

    for folder, px in ADAPTIVE_FG_SIZES.items():
        out_dir = os.path.join(RES_DIR, folder)
        os.makedirs(out_dir, exist_ok=True)
        render_adaptive_foreground(px).save(os.path.join(out_dir, "ic_launcher_fg.png"))

    anydpi_dir = os.path.join(RES_DIR, "mipmap-anydpi-v26")
    os.makedirs(anydpi_dir, exist_ok=True)
    adaptive_xml = """<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@color/ic_launcher_bg" />
    <foreground android:drawable="@mipmap/ic_launcher_fg" />
</adaptive-icon>
"""
    for fname in ("ic_launcher.xml", "ic_launcher_round.xml"):
        with open(os.path.join(anydpi_dir, fname), "w", encoding="utf-8") as fh:
            fh.write(adaptive_xml)

    preview_dir = "/home/antigravity/.gemini/jetski/brain/v1/default/jetski_remote_agents_3p_devbox/e45bab5e-ad8a-4b54-86c9-e752711466df/scratch"
    os.makedirs(preview_dir, exist_ok=True)
    render_legacy_icon(512, round_mask=False).save(os.path.join(preview_dir, "icon_v5_preview.png"))
    print("Generated refined legacy + adaptive launcher icons for ferment.")


if __name__ == "__main__":
    main()
