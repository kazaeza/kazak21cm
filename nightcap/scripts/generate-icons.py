#!/usr/bin/env python3
"""Generate an unmistakable, lush cosmic cocktail glass Android launcher icon for nightcap."""

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


def make_glow(size: int, cx: float, cy: float, rx: float, ry: float, color, alpha: int) -> Image.Image:
    layer = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(layer)
    x0, y0 = int(size * (cx - rx)), int(size * (cy - ry))
    x1, y1 = int(size * (cx + rx)), int(size * (cy + ry))
    draw.ellipse((x0, y0, x1, y1), fill=(*color, alpha))
    return layer.filter(ImageFilter.GaussianBlur(radius=max(3, int(size * max(rx, ry) * 0.55))))


def draw_cocktail_foreground(out_size: int, content_scale: float = 0.72) -> Image.Image:
    """
    Render a crisp, luminous cocktail martini glass with gradient drink,
    skewered green olive with red pimento, citrus slice on the rim, and nightcap bubbles.
    Uses 4x supersampling (up to 1024px) for razor-sharp anti-aliasing.
    """
    S = max(768, out_size * 4)
    img = Image.new("RGBA", (S, S), (0, 0, 0, 0))

    # Helper to map normalized icon coords [-1, 1] -> pixel coords inside safe zone
    def pt(nx: float, ny: float):
        return (S * (0.5 + nx * 0.5 * content_scale), S * (0.5 + ny * 0.5 * content_scale))

    def sc(v: float) -> float:
        return v * S * 0.5 * content_scale

    # 1. Soft cosmic neon aura behind the glass
    img = Image.alpha_composite(img, make_glow(S, 0.48, 0.42, 0.25 * content_scale / 0.72, 0.22 * content_scale / 0.72, (255, 72, 148), 115))
    img = Image.alpha_composite(img, make_glow(S, 0.54, 0.34, 0.21 * content_scale / 0.72, 0.19 * content_scale / 0.72, (168, 92, 255), 95))
    img = Image.alpha_composite(img, make_glow(S, 0.50, 0.58, 0.17 * content_scale / 0.72, 0.17 * content_scale / 0.72, (255, 166, 86), 70))

    # 2. Citrus lime wheel tucked behind the right rim
    citrus_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    cd = ImageDraw.Draw(citrus_layer)
    lcx, lcy = pt(0.56, -0.44)
    lr = sc(0.24)
    cd.ellipse((lcx - lr, lcy - lr, lcx + lr, lcy + lr), fill=(34, 156, 78, 255))
    cd.ellipse((lcx - lr * 0.86, lcy - lr * 0.86, lcx + lr * 0.86, lcy + lr * 0.86), fill=(228, 252, 200, 255))
    cd.ellipse((lcx - lr * 0.76, lcy - lr * 0.76, lcx + lr * 0.76, lcy + lr * 0.76), fill=(154, 224, 62, 255))
    # Lime segments
    for deg in range(0, 360, 45):
        ang = math.radians(deg)
        cd.line(
            (lcx, lcy, lcx + math.cos(ang) * lr * 0.76, lcy + math.sin(ang) * lr * 0.76),
            fill=(232, 254, 208, 230),
            width=max(2, int(sc(0.022))),
        )
    cd.ellipse((lcx - lr * 0.14, lcy - lr * 0.14, lcx + lr * 0.14, lcy + lr * 0.14), fill=(238, 255, 218, 255))
    img = Image.alpha_composite(img, citrus_layer)

    # 3. Glass interior subtle fill & stem + base
    glass_back = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gd = ImageDraw.Draw(glass_back)

    rim_l = pt(-0.62, -0.44)
    rim_r = pt(0.62, -0.44)
    apex = pt(0.0, 0.20)
    stem_top = pt(0.0, 0.18)
    stem_bot = pt(0.0, 0.78)
    base_l = pt(-0.38, 0.82)
    base_r = pt(0.38, 0.82)

    # Translucent crystal bowl
    gd.polygon([rim_l, rim_r, apex], fill=(255, 255, 255, 28))

    # Stem
    stem_w = max(4, int(sc(0.068)))
    gd.line([stem_top, stem_bot], fill=(238, 232, 255, 215), width=stem_w)
    # Bright specular strip along stem
    gd.line(
        [pt(-0.014, 0.21), pt(-0.014, 0.75)],
        fill=(255, 255, 255, 220),
        width=max(2, int(sc(0.022))),
    )

    # Base foot (elliptical crystal pedestal)
    bx0, by0 = pt(-0.38, 0.75)
    bx1, by1 = pt(0.38, 0.86)
    gd.ellipse((bx0, by0, bx1, by1), fill=(224, 214, 255, 78), outline=(245, 240, 255, 225), width=max(3, int(sc(0.036))))
    # Smooth neck joint
    gd.polygon([pt(-0.09, 0.79), pt(0.09, 0.79), pt(0.035, 0.71), pt(-0.035, 0.71)], fill=(238, 232, 255, 195))
    img = Image.alpha_composite(img, glass_back)

    # 4. Rich gradient cocktail liquid clipped to inner triangle
    liq_top_y_n = -0.25
    # Linear interpolation along bowl edge from rim (-0.44) to apex (+0.20)
    t_liq = (liq_top_y_n - (-0.44)) / (0.20 - (-0.44))
    half_w = 0.62 * (1.0 - t_liq) * 0.94
    liq_l = pt(-half_w, liq_top_y_n)
    liq_r = pt(half_w, liq_top_y_n)
    liq_apex = pt(0.0, 0.145)

    liq_mask = Image.new("L", (S, S), 0)
    ImageDraw.Draw(liq_mask).polygon([liq_l, liq_r, liq_apex], fill=255)

    # Vertical sunset-berry-neon gradient inside the liquid
    liq_grad = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    lpx = liq_grad.load()
    y_min = int(liq_l[1]) - 2
    y_max = int(liq_apex[1]) + 2
    x_min = int(liq_l[0]) - 2
    x_max = int(liq_r[0]) + 2
    for y in range(max(0, y_min), min(S, y_max)):
        f = max(0.0, min(1.0, (y - y_min) / max(1, y_max - y_min)))
        # Top: warm sunset peach/coral (#ff9a62) -> Mid: electric rose (#ff4d8d) -> Bottom: deep berry violet (#9333ea)
        if f < 0.5:
            k = f * 2.0
            r = int(255 * (1 - k) + 255 * k)
            g = int(164 * (1 - k) + 74 * k)
            b = int(98 * (1 - k) + 146 * k)
        else:
            k = (f - 0.5) * 2.0
            r = int(255 * (1 - k) + 148 * k)
            g = int(74 * (1 - k) + 52 * k)
            b = int(146 * (1 - k) + 235 * k)
        for x in range(max(0, x_min), min(S, x_max)):
            lpx[x, y] = (r, g, b, 245)

    liq_layer = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    liq_layer.paste(liq_grad, (0, 0), liq_mask)

    # Liquid meniscus ellipse on top
    md = ImageDraw.Draw(liq_layer)
    men_h = sc(0.055)
    md.ellipse((liq_l[0], liq_l[1] - men_h, liq_r[0], liq_r[1] + men_h), fill=(255, 206, 152, 210))
    img = Image.alpha_composite(img, liq_layer)

    # 5. Cocktail pick + plump green olive with red pimento!
    garnish = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gdr = ImageDraw.Draw(garnish)
    pick_start = pt(-0.18, -0.04)
    pick_end = pt(0.36, -0.68)
    gdr.line([pick_start, pick_end], fill=(248, 244, 255, 245), width=max(3, int(sc(0.03))))
    # Little golden pearl finial at top of pick
    pr = sc(0.055)
    gdr.ellipse((pick_end[0] - pr, pick_end[1] - pr, pick_end[0] + pr, pick_end[1] + pr), fill=(255, 214, 128, 255), outline=(255, 248, 220, 255), width=max(2, int(sc(0.015))))

    # Olive body drawn on rotated sub-image for crisp ellipse angle
    olive_box = int(sc(0.46))
    olive_img = Image.new("RGBA", (olive_box, olive_box), (0, 0, 0, 0))
    od = ImageDraw.Draw(olive_img)
    oc = olive_box * 0.5
    orx, ory = sc(0.16), sc(0.125)
    od.ellipse((oc - orx, oc - ory, oc + orx, oc + ory), fill=(116, 184, 32, 255), outline=(58, 102, 16, 255), width=max(2, int(sc(0.02))))
    # Gloss highlight on olive
    od.ellipse((oc - orx * 0.25, oc - ory * 0.65, oc + orx * 0.55, oc - ory * 0.15), fill=(214, 245, 145, 165))
    # Red pimento center
    prx, pry = sc(0.048), sc(0.056)
    od.ellipse((oc - orx * 0.65 - prx, oc - pry, oc - orx * 0.65 + prx, oc + pry), fill=(235, 42, 62, 255))
    olive_rot = olive_img.rotate(48, resample=Image.Resampling.BICUBIC)
    ox, oy = pt(0.02, -0.28)
    garnish.alpha_composite(olive_rot, (int(ox - olive_box * 0.5), int(oy - olive_box * 0.5)))
    img = Image.alpha_composite(img, garnish)

    # 6. Crystal glass outline, rim & bright specular highlight
    glass_front = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    gfd = ImageDraw.Draw(glass_front)
    outline_w = max(4, int(sc(0.046)))
    # Crisp V-bowl sides + rim
    gfd.line([rim_l, apex], fill=(246, 242, 255, 242), width=outline_w)
    gfd.line([rim_r, apex], fill=(246, 242, 255, 242), width=outline_w)
    gfd.line([rim_l, rim_r], fill=(255, 255, 255, 245), width=max(3, int(sc(0.036))))
    # Round caps at rim corners & apex
    cap_r = outline_w * 0.5
    for cx, cy in [rim_l, rim_r, apex]:
        gfd.ellipse((cx - cap_r, cy - cap_r, cx + cap_r, cy + cap_r), fill=(250, 246, 255, 245))
    # Inner crystal sheen line on left slope
    gfd.line([pt(-0.46, -0.35), pt(-0.14, -0.02)], fill=(255, 255, 255, 165), width=max(3, int(sc(0.032))))

    # Tiny effervescent bubbles rising above the drink (playful nightcap vibe, NOT Gemini!)
    bubbles = [
        (-0.34, -0.62, 0.042, (255, 210, 235, 215)),
        (-0.20, -0.74, 0.028, (175, 224, 255, 200)),
        (-0.44, -0.49, 0.022, (255, 228, 175, 190)),
    ]
    for bx, by, br, col in bubbles:
        px, py = pt(bx, by)
        r = sc(br)
        gfd.ellipse((px - r, py - r, px + r, py + r), fill=(*col[:3], 85), outline=col, width=max(2, int(sc(0.015))))

    img = Image.alpha_composite(img, glass_front)
    return img.resize((out_size, out_size), Image.Resampling.LANCZOS)


def render_full_icon(size: int, circular: bool) -> Image.Image:
    # Deep cosmic night gradient background
    bg = Image.new("RGBA", (size, size), (8, 7, 20, 255))
    bg = Image.alpha_composite(bg, make_glow(size, 0.32, 0.26, 0.42, 0.42, (76, 32, 138), 165))
    bg = Image.alpha_composite(bg, make_glow(size, 0.72, 0.74, 0.38, 0.38, (148, 34, 98), 120))
    fg = draw_cocktail_foreground(size, content_scale=0.78)
    composed = Image.alpha_composite(bg, fg)

    if circular:
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).ellipse((0, 0, size - 1, size - 1), fill=255)
        out = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        out.paste(composed, (0, 0), mask)
        return out
    return composed


def main():
    # Adaptive foreground & background (432x432 xxxhdpi with strict safe-zone scaling)
    fg_dir = os.path.join(RES_DIR, "mipmap-xxxhdpi")
    os.makedirs(fg_dir, exist_ok=True)
    bg = Image.new("RGBA", (432, 432), (8, 7, 20, 255))
    bg = Image.alpha_composite(bg, make_glow(432, 0.32, 0.26, 0.44, 0.44, (76, 32, 138), 175))
    bg = Image.alpha_composite(bg, make_glow(432, 0.72, 0.74, 0.40, 0.40, (148, 34, 98), 130))
    bg.save(os.path.join(fg_dir, "ic_launcher_background.png"))

    fg = draw_cocktail_foreground(432, content_scale=0.60)
    fg.save(os.path.join(fg_dir, "ic_launcher_foreground.png"))

    for folder, sz in DENSITIES.items():
        d = os.path.join(RES_DIR, folder)
        os.makedirs(d, exist_ok=True)
        render_full_icon(sz, circular=False).save(os.path.join(d, "ic_launcher.png"))
        render_full_icon(sz, circular=True).save(os.path.join(d, "ic_launcher_round.png"))

    print("Generated cocktail glass Android launcher icons cleanly.")


if __name__ == "__main__":
    main()
