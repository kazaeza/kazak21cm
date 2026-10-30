#!/usr/bin/env python3
"""Ультракринжовая гифка на день рождения Романа Андреевича Мерзликина.

Всё рисуется кодом (Pillow + numpy): именинник, шприцы, перчатки, пиво,
сноуборд, велик, ковёр на стене, тендерный протокол и бегущая строка.

    python3 make_gif.py                  # -> roman_bday.gif (+ roman_bday.mp4, если есть ffmpeg)
    python3 make_gif.py --size 540       # другой размер квадрата
    python3 make_gif.py --face face.jpg  # вклеить настоящее лицо вместо нарисованного

Если рядом со скриптом лежит face.jpg / face.png, лицо подхватится само.
"""

import argparse
import colorsys
import math
import os
import random
import shutil
import subprocess
import sys
from functools import lru_cache
from multiprocessing import Pool
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
FONT_DIR = HERE / "fonts"

W = H = 960          # всё рисуется на этом холсте, потом ужимается до --size
FRAME_MS = 80        # 12.5 кадра в секунду
DT = FRAME_MS / 1000
TRANS = 7            # длина перехода между сценами, кадров
TICKER_H = 58

BICUBIC = Image.Resampling.BICUBIC
BILINEAR = Image.Resampling.BILINEAR
LANCZOS = Image.Resampling.LANCZOS

FONT_FILES = {
    "lobster": "Lobster-Regular.ttf",
    "marck": "MarckScript-Regular.ttf",
    "russo": "RussoOne-Regular.ttf",
    "bubbles": "RubikBubbles-Regular.ttf",
    "pixel": "PressStart2P-Regular.ttf",
    "serif": "PT_Serif-Web-Regular.ttf",
    "serif_b": "PT_Serif-Web-Bold.ttf",
    "hand": "Neucha.ttf",
}
EMOJI_FONTS = (
    "/usr/share/fonts/truetype/noto/NotoColorEmoji.ttf",
    "/usr/share/fonts/noto/NotoColorEmoji.ttf",
    "/usr/share/fonts/google-noto-emoji/NotoColorEmoji.ttf",
    "/System/Library/Fonts/Apple Color Emoji.ttc",
    "C:/Windows/Fonts/seguiemj.ttf",
)

NAME = "Роман Андреевич Мерзликин"
TITLE = "С Днём Рождения!"
POEM = (
    "Бросать пивко — совсем не сложно:",
    "Роман бросал раз двадцать пять!",
    "Ведь без пивка прожить-то можно...",
    "Но тендер взят — и пьёт опять!",
)
TICKER = (
    ("С ДНЁМ РОЖДЕНИЯ, РОМАН АНДРЕЕВИЧ!", "🎂"),
    ("КОРОЛЬ ТЕНДЕРОВ", "📄"),
    ("ВЛАСТЕЛИН ШПРИЦЕВ И ПЕРЧАТОК", "💉"),
    ("ГРОЗА СКЛОНОВ И ВЕЛОДОРОЖЕК", "🏂"),
    ("МАСТЕР СПОРТА ПО ЗАВЯЗЫВАНИЮ", "🍺"),
    ("ДОМА — БЕЗ ДРЕСС-КОДА", "🙈"),
)

INK = (40, 22, 30)
SKIN = (255, 204, 158)
SKIN_D = (232, 162, 120)
HAIR = (92, 58, 34)
GLOVE = (70, 140, 255)
GOLD = ((0, (255, 252, 215)), (0.45, (255, 214, 70)), (0.62, (210, 135, 10)), (1, (255, 236, 140)))
ICE = ((0, (255, 255, 255)), (0.5, (175, 235, 255)), (1, (80, 170, 255)))
SUN = ((0, (255, 255, 170)), (0.5, (255, 200, 30)), (1, (255, 110, 0)))
PINK = ((0, (255, 230, 245)), (0.5, (255, 110, 200)), (1, (190, 40, 210)))
SPARKLE_COLORS = ((255, 255, 255), (255, 250, 170), (255, 190, 240), (190, 255, 255))


# ---------------------------------------------------------------- мелочи

def clamp01(x):
    return 0.0 if x < 0 else 1.0 if x > 1 else x


def lerp(a, b, k):
    return a + (b - a) * k


def ease_out_back(x, s=1.7):
    x = clamp01(x) - 1
    return 1 + (s + 1) * x ** 3 + s * x ** 2


def ease_in_out(x):
    return 0.5 - 0.5 * math.cos(math.pi * clamp01(x))


def hsv(h, s=1.0, v=1.0):
    r, g, b = colorsys.hsv_to_rgb(h % 1.0, s, v)
    return int(r * 255), int(g * 255), int(b * 255)


def shade(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c[:3])


def add(a, b):
    return a[0] + b[0], a[1] + b[1]


def mul(a, k):
    return a[0] * k, a[1] * k


def ddir(deg):
    """Единичный вектор: 0° — вниз, 90° — вправо, 180° — вверх."""
    r = math.radians(deg)
    return math.sin(r), math.cos(r)


class Xf:
    """Локальные координаты -> экран: масштаб, поворот по часовой, сдвиг."""

    def __init__(self, x, y, s=1.0, rot=0.0):
        self.x, self.y, self.s, self.rot = x, y, s, rot
        r = math.radians(rot)
        self.c, self.sn = math.cos(r), math.sin(r)

    def __call__(self, p):
        return (self.x + self.s * (p[0] * self.c - p[1] * self.sn),
                self.y + self.s * (p[0] * self.sn + p[1] * self.c))

    def pts(self, ps):
        return [self(p) for p in ps]

    @classmethod
    def pinned(cls, local, screen, s=1.0, rot=0.0):
        """Преобразование, при котором локальная точка local попадает в screen."""
        px, py = cls(0, 0, s, rot)(local)
        return cls(screen[0] - px, screen[1] - py, s, rot)


# ---------------------------------------------------------------- шрифты и эмодзи

@lru_cache(maxsize=None)
def font(name, size):
    return ImageFont.truetype(str(FONT_DIR / FONT_FILES[name]), int(size))


def fit_font(name, text, max_w, size, min_size=12):
    size = int(size)
    while size > min_size and font(name, size).getlength(text) > max_w:
        size -= 2
    return font(name, size)


@lru_cache(maxsize=None)
def _emoji_font():
    for path in EMOJI_FONTS:
        if not os.path.exists(path):
            continue
        for size in (109, 160, 137, 96, 64):
            try:
                return ImageFont.truetype(path, size)
            except OSError:
                continue
    return None


@lru_cache(maxsize=None)
def emoji(ch, size):
    """Цветной эмодзи заданного размера или None, если эмодзи-шрифта нет."""
    f = _emoji_font()
    if f is None:
        return None
    probe = Image.new("RGBA", (f.size * 2, f.size * 2), (0, 0, 0, 0))
    try:
        ImageDraw.Draw(probe).text((f.size // 4, f.size // 4), ch, font=f, embedded_color=True)
    except OSError:
        return None
    box = probe.getbbox()
    if not box:
        return None
    im = probe.crop(box)
    k = size / max(im.size)
    return im.resize((max(1, round(im.width * k)), max(1, round(im.height * k))), LANCZOS)


# ---------------------------------------------------------------- градиенты и вклейка

def hsv_np(h, s, v):
    h = np.mod(h, 1.0) * 6.0
    i = np.floor(h).astype(np.int32) % 6
    f = h - np.floor(h)
    s = np.broadcast_to(np.float32(s), h.shape)
    v = np.broadcast_to(np.float32(v), h.shape)
    p, q, t = v * (1 - s), v * (1 - s * f), v * (1 - s * (1 - f))
    conds = [i == k for k in range(6)]
    r = np.select(conds, [v, q, p, p, t, v])
    g = np.select(conds, [t, v, v, q, p, p])
    b = np.select(conds, [p, p, t, v, v, q])
    return np.stack([r, g, b], axis=-1)


def rainbow(w, h, phase, angle=0.0, cycles=1.0, s=0.9, v=1.0, res=8):
    """Радужный градиент; считается в низком разрешении и растягивается."""
    sw, sh = max(2, w // res), max(2, h // res)
    yy, xx = np.mgrid[0:sh, 0:sw].astype(np.float32)
    a = math.radians(angle)
    u = xx / sw * math.cos(a) + yy / sh * math.sin(a)
    rgb = hsv_np(u * cycles + phase, s, v)
    return Image.fromarray((rgb * 255).astype(np.uint8), "RGB").resize((w, h), BILINEAR)


def vgrad(w, h, stops):
    ys = np.linspace(0.0, 1.0, h)
    pos = [p for p, _ in stops]
    cols = np.array([c for _, c in stops], dtype=np.float32)
    col = np.stack([np.interp(ys, pos, cols[:, i]) for i in range(3)], axis=-1)
    return Image.fromarray(np.repeat(col[:, None, :], w, axis=1).astype(np.uint8), "RGB")


def paste(img, spr, x, y):
    img.paste(spr, (int(round(x)), int(round(y))), spr)


def paste_c(img, spr, cx, cy):
    paste(img, spr, cx - spr.width / 2, cy - spr.height / 2)


def paste_rot(img, spr, cx, cy, angle=0.0, anchor=None, scale=1.0):
    """Вклеить спрайт, повернув по часовой на angle так, чтобы anchor попал в (cx, cy)."""
    if spr is None:
        return
    if scale != 1.0:
        spr = spr.resize((max(1, round(spr.width * scale)), max(1, round(spr.height * scale))), BICUBIC)
        if anchor is not None:
            anchor = (anchor[0] * scale, anchor[1] * scale)
    if anchor is not None:
        ax, ay = anchor
        hw = int(math.ceil(max(ax, spr.width - ax))) + 1
        hh = int(math.ceil(max(ay, spr.height - ay))) + 1
        pad = Image.new("RGBA", (2 * hw, 2 * hh), (0, 0, 0, 0))
        pad.paste(spr, (round(hw - ax), round(hh - ay)))
        spr = pad
    if angle:
        spr = spr.rotate(-angle, resample=BICUBIC, expand=True)
    paste_c(img, spr, cx, cy)


# ---------------------------------------------------------------- геометрия

def circ(d, c, r, fill=None, outline=None, width=1):
    r = max(0.5, r)
    d.ellipse([c[0] - r, c[1] - r, c[0] + r, c[1] + r], fill=fill, outline=outline, width=max(1, int(width)))


def star_pts(cx, cy, ro, ri, n=5, rot=0.0, squash=1.0):
    pts = []
    for k in range(2 * n):
        r = ro if k % 2 == 0 else ri
        a = rot + math.pi * k / n - math.pi / 2
        pts.append((cx + r * math.cos(a), cy + r * math.sin(a) * squash))
    return pts


def heart_pts(cx, cy, size, n=90):
    pts = []
    for k in range(n):
        a = 2 * math.pi * k / n
        x = 16 * math.sin(a) ** 3
        y = -(13 * math.cos(a) - 5 * math.cos(2 * a) - 2 * math.cos(3 * a) - math.cos(4 * a))
        pts.append((cx + x * size / 16, cy + y * size / 16))
    return pts


def sparkle(d, x, y, r, fill=(255, 255, 255), rot=0.0):
    if r >= 1:
        d.polygon(star_pts(x, y, r, r * 0.22, 4, rot), fill=fill)


def ellipse_pts(c, rx, ry, rot=0.0, n=28):
    cr, sr = math.cos(math.radians(rot)), math.sin(math.radians(rot))
    return [(c[0] + rx * math.cos(a) * cr - ry * math.sin(a) * sr,
             c[1] + rx * math.cos(a) * sr + ry * math.sin(a) * cr)
            for a in (2 * math.pi * k / n for k in range(n))]


def stadium_pts(cx, cy, hl, hh, n=10):
    pts = [(cx + hl - hh + hh * math.cos(a), cy + hh * math.sin(a))
           for a in (-math.pi / 2 + math.pi * k / n for k in range(n + 1))]
    pts += [(cx - hl + hh + hh * math.cos(a), cy + hh * math.sin(a))
            for a in (math.pi / 2 + math.pi * k / n for k in range(n + 1))]
    return pts


def ik(root, target, l1, l2, bend):
    """Двухзвенная кинематика: средний сустав и (досягаемый) конец."""
    dx, dy = target[0] - root[0], target[1] - root[1]
    dist = min(max(math.hypot(dx, dy), abs(l1 - l2) + 1e-3), l1 + l2 - 1e-3)
    a = math.atan2(dy, dx)
    A = math.acos(max(-1.0, min(1.0, (l1 * l1 + dist * dist - l2 * l2) / (2 * l1 * dist))))
    mid = (root[0] + l1 * math.cos(a + bend * A), root[1] + l1 * math.sin(a + bend * A))
    return mid, (root[0] + dist * math.cos(a), root[1] + dist * math.sin(a))


# ---------------------------------------------------------------- фоны и текст

@lru_cache(maxsize=None)
def glow_sprite(radius, color, alpha):
    size = 2 * radius
    yy, xx = np.mgrid[0:size, 0:size].astype(np.float32)
    r = np.hypot(xx - radius, yy - radius) / radius
    a = (np.clip(1 - r, 0, 1) ** 1.5 * alpha).astype(np.uint8)
    im = Image.new("RGBA", (size, size), tuple(color) + (0,))
    im.putalpha(Image.fromarray(a, "L"))
    return im


def glow(img, cx, cy, radius, color=(255, 255, 255), alpha=220):
    paste_c(img, glow_sprite(int(radius), tuple(color), int(alpha)), cx, cy)


def sunburst(img, cx, cy, n, rot, color_of):
    d = ImageDraw.Draw(img)
    R = W * 1.6
    for i in range(n):
        a0 = rot + 2 * math.pi * i / n
        a1 = a0 + 2 * math.pi / n
        d.polygon([(cx, cy), (cx + R * math.cos(a0), cy + R * math.sin(a0)),
                   (cx + R * math.cos(a1), cy + R * math.sin(a1))], fill=color_of(i))


def add_shine(layer, mask, t, speed=0.45, strength=190):
    """Блик, пробегающий по буквам по диагонали."""
    lw, lh = layer.size
    m = np.asarray(mask, dtype=np.float32) / 255.0
    yy, xx = np.mgrid[0:lh, 0:lw].astype(np.float32)
    span = lw + lh * 0.6
    c = ((t * speed) % 1.0) * span * 1.5 - span * 0.25
    band = np.clip(1.0 - np.abs(xx + yy * 0.6 - c) / (lh * 0.3 + 1), 0, 1)
    layer.paste((255, 255, 255, 255), (0, 0), Image.fromarray((band * m * strength).astype(np.uint8), "L"))


def wordart(img, text, fnt, cx, cy, t, fill="rainbow", wave=10.0, wave_speed=1.0, extrude=7,
            ext_dir=(1.0, 1.2), ext_color=(70, 10, 90), outline=6, outline_color=(25, 5, 35),
            hue_speed=0.35, shine=True, angle=0.0):
    """Текст а-ля WordArt: волна по буквам, 3D-выдавливание, обводка, переливы и блик."""
    n = len(text)
    asc, desc = fnt.getmetrics()
    xs = [fnt.getlength(text[:i]) for i in range(n)]
    pad = int(outline + extrude * max(abs(ext_dir[0]), abs(ext_dir[1])) + abs(wave) + 8)
    lw, lh = int(fnt.getlength(text)) + 2 * pad, asc + desc + 2 * pad
    layer = Image.new("RGBA", (lw, lh), (0, 0, 0, 0))
    mask = Image.new("L", (lw, lh), 0)
    dl, dm = ImageDraw.Draw(layer), ImageDraw.Draw(mask)
    base = pad + asc
    pos = [(pad + xs[i], base + wave * math.sin(2 * math.pi * t * wave_speed - i * 0.55)) for i in range(n)]
    for k in range(extrude, 0, -1):
        col = shade(ext_color, 0.6 + 0.6 * (1 - k / extrude))
        for (x, y), ch in zip(pos, text):
            if not ch.isspace():
                dl.text((x + k * ext_dir[0], y + k * ext_dir[1]), ch, font=fnt, anchor="ls",
                        fill=col, stroke_width=outline, stroke_fill=col)
    for (x, y), ch in zip(pos, text):
        if not ch.isspace():
            dl.text((x, y), ch, font=fnt, anchor="ls", fill=outline_color,
                    stroke_width=outline, stroke_fill=outline_color)
            dm.text((x, y), ch, font=fnt, anchor="ls", fill=255)
    grad = rainbow(lw, lh, t * hue_speed, angle=15) if fill == "rainbow" else vgrad(lw, lh, fill)
    layer.paste(grad, (0, 0), mask)
    if shine:
        add_shine(layer, mask, t)
    if angle:
        layer = layer.rotate(-angle, resample=BICUBIC, expand=True)
    paste_c(img, layer, cx, cy)


# ---------------------------------------------------------------- спрайты

@lru_cache(maxsize=None)
def syringe_sprite(L, liquid=(255, 50, 150)):
    """Шприц иглой влево."""
    h = int(L * 0.26)
    im = Image.new("RGBA", (L, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    cy, ow, ink = h / 2, max(2, L // 70), (30, 50, 90)
    d.line([(2, cy), (L * 0.24, cy)], fill=(160, 165, 180), width=max(2, L // 90))
    d.rectangle([L * 0.20, cy - h * 0.09, L * 0.27, cy + h * 0.09], fill=(120, 210, 255), outline=ink, width=ow)
    bx0, bx1, by0, by1 = L * 0.26, L * 0.76, cy - h * 0.22, cy + h * 0.22
    d.rectangle([bx0, by0, bx1, by1], fill=(225, 242, 255), outline=ink, width=ow)
    d.rectangle([bx0 + ow, by0 + ow, L * 0.52, by1 - ow], fill=liquid)
    for k in range(1, 10):
        x = bx0 + (bx1 - bx0) * k / 10
        d.line([(x, by0), (x, by0 + h * (0.12 if k % 2 else 0.2))], fill=ink, width=max(1, ow // 2 + 1))
    d.rectangle([bx1, cy - h * 0.40, bx1 + L * 0.025, cy + h * 0.40], fill=(200, 225, 255), outline=ink, width=ow)
    d.rectangle([L * 0.52, cy - h * 0.07, L * 0.93, cy + h * 0.07], fill=(250, 250, 255), outline=ink, width=ow)
    d.rectangle([L * 0.50, by0 + ow, L * 0.54, by1 - ow], fill=(40, 40, 50))
    d.rectangle([L * 0.93, cy - h * 0.33, L * 0.985, cy + h * 0.33], fill=(250, 250, 255), outline=ink, width=ow)
    d.line([(bx0 + 2 * ow, by0 + 2 * ow), (bx1 - 2 * ow, by0 + 2 * ow)], fill=(255, 255, 255), width=max(1, ow))
    return im


@lru_cache(maxsize=None)
def glove_sprite(size, color=GLOVE, face=True):
    """Надутая медицинская перчатка-шарик с рожицей."""
    s = size
    im = Image.new("RGBA", (s, s), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ink, ow, fw = (25, 30, 70), max(2, s // 40), s * 0.15
    bases = [(s * (0.33 + 0.113 * i), s * 0.47) for i in range(4)]
    tips = []
    for (ang, ln), b in zip(((-38, 0.30), (-13, 0.36), (12, 0.35), (36, 0.29)), bases):
        a = math.radians(ang)
        tips.append((b[0] + math.sin(a) * s * ln, b[1] - math.cos(a) * s * ln))
    bases.append((s * 0.30, s * 0.62))
    tips.append((s * 0.11, s * 0.47))
    for col, grow in ((ink, ow), (color, 0)):
        for b, tp in zip(bases, tips):
            d.line([b, tp], fill=col, width=int(fw + 2 * grow))
            circ(d, tp, fw / 2 + grow, col)
        d.ellipse([s * 0.24 - grow, s * 0.36 - grow, s * 0.76 + grow, s * 0.86 + grow], fill=col)
        d.rectangle([s * 0.39 - grow, s * 0.80, s * 0.61 + grow, s * 0.96 + grow], fill=col)
    d.rounded_rectangle([s * 0.35, s * 0.89, s * 0.65, s * 0.97], radius=s * 0.03,
                        fill=shade(color, 1.2), outline=ink, width=ow)
    d.ellipse([s * 0.30, s * 0.42, s * 0.40, s * 0.56], fill=shade(color, 1.45))
    if face:
        for ex in (0.43, 0.58):
            circ(d, (s * ex, s * 0.62), s * 0.05, (255, 255, 255), ink, max(1, ow // 2))
            circ(d, (s * ex + s * 0.012, s * 0.625), s * 0.022, ink)
        d.arc([s * 0.42, s * 0.62, s * 0.60, s * 0.76], 20, 160, fill=ink, width=ow)
    return im


@lru_cache(maxsize=None)
def beer_sprite(size, handle_left=False):
    """Кружка пива. Ручка: центр в (0.92*size, 0.62*size), при handle_left — зеркально."""
    w, h = int(size * 1.3), size
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ink, ow = (60, 30, 10), max(2, size // 28)
    x0, x1, y0, y1 = size * 0.08, size * 0.80, size * 0.24, size * 0.97
    hb = [size * 0.66, size * 0.40, size * 1.18, size * 0.84]
    d.ellipse(hb, outline=ink, width=int(size * 0.15))
    d.ellipse([hb[0] + ow, hb[1] + ow, hb[2] - ow, hb[3] - ow], outline=(250, 236, 205), width=int(size * 0.15 - 2 * ow))
    d.rounded_rectangle([x0, y0, x1, y1], radius=size * 0.08, fill=ink)
    d.rounded_rectangle([x0 + ow, y0 + ow, x1 - ow, y1 - ow], radius=size * 0.07, fill=(255, 175, 25))
    for k in range(1, 4):
        x = x0 + (x1 - x0) * k / 4
        d.line([(x, y0 + size * 0.1), (x, y1 - size * 0.06)], fill=(255, 205, 90), width=max(2, size // 22))
    rng = random.Random(size)
    for _ in range(7):
        circ(d, (rng.uniform(x0 + size * 0.08, x1 - size * 0.08), rng.uniform(y0 + size * 0.2, y1 - size * 0.1)),
             size * 0.025, (255, 240, 180))
    for col, r in ((ink, size * 0.11), ((255, 255, 250), size * 0.11 - ow)):
        for k in range(6):
            circ(d, (x0 + (x1 - x0) * k / 5, y0 + 2), r, col)
    d.rounded_rectangle([x0 + size * 0.12, y0, x0 + size * 0.2, y0 + size * 0.22], radius=size * 0.04, fill=(255, 255, 250))
    d.rectangle([x0 + size * 0.06, y0 + size * 0.2, x0 + size * 0.1, y1 - size * 0.12], fill=(255, 232, 160))
    return im.transpose(Image.Transpose.FLIP_LEFT_RIGHT) if handle_left else im


@lru_cache(maxsize=None)
def snowflake_sprite(size):
    im = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    c, r, w = size / 2, size * 0.46, max(1, size // 12)
    for k in range(6):
        a = math.pi * k / 3
        d.line([(c, c), (c + r * math.cos(a), c + r * math.sin(a))], fill=(255, 255, 255), width=w)
        m = (c + r * 0.55 * math.cos(a), c + r * 0.55 * math.sin(a))
        for da in (0.6, -0.6):
            d.line([m, (m[0] + r * 0.3 * math.cos(a + da), m[1] + r * 0.3 * math.sin(a + da))], fill=(255, 255, 255), width=w)
    return im


@lru_cache(maxsize=None)
def censor_sprite(w, h, lines):
    im = Image.new("RGBA", (w, h), (10, 10, 12, 255))
    d = ImageDraw.Draw(im)
    fnt = fit_font("russo", max(lines, key=len), w * 0.88, h * (0.56 if len(lines) == 1 else 0.36))
    for i, ln in enumerate(lines):
        d.text((w / 2, h * (i + 0.5) / len(lines)), ln, font=fnt, anchor="mm", fill=(255, 255, 255))
    return im


def balloon(img, x, y, color, t, size):
    d = ImageDraw.Draw(img)
    pts = [(x + 7 * math.sin(t * 3 + k * 0.6), y + size * 0.45 + k * 26) for k in range(16)]
    d.line(pts, fill=(250, 250, 255), width=3)
    paste_rot(img, glove_sprite(size, color), x, y, 8 * math.sin(t * 1.7))


# ---------------------------------------------------------------- именинник

HEAD_R, NECK, TORSO = 66, 10, 136
SHW, HIPW = 60, 30
UA, FA, ARM_W = 70, 66, 30
TH, SH, LEG_W = 84, 80, 38
HAND_R = 19

NAKED = dict(torso=SKIN, ua=SKIN, fa=SKIN, th=SKIN, sh=SKIN, foot=SKIN, naked=True)
SNOWSUIT = dict(torso=(255, 50, 150), ua=(255, 50, 150), fa=(255, 50, 150), th=(40, 200, 190),
                sh=(40, 200, 190), foot=(60, 60, 70), stripe=(255, 230, 0))
CYCLIST = dict(torso=(255, 225, 0), ua=(255, 225, 0), fa=SKIN, th=(30, 30, 40), sh=SKIN,
               foot=(245, 245, 245), stripe=(0, 170, 90))


@lru_cache(maxsize=None)
def face_photo(diam):
    path = os.environ.get("ROMAN_FACE")
    if not path or not os.path.exists(path):
        return None
    im = Image.open(path).convert("RGB")
    side = min(im.size)
    im = im.crop(((im.width - side) // 2, (im.height - side) // 2,
                  (im.width + side) // 2, (im.height + side) // 2)).resize((diam, diam), LANCZOS).convert("RGBA")
    m = Image.new("L", (diam * 4, diam * 4), 0)
    ImageDraw.Draw(m).ellipse([0, 0, diam * 4 - 1, diam * 4 - 1], fill=255)
    im.putalpha(m.resize((diam, diam), LANCZOS))
    return im


def draw_face(d, cx, cy, R, t, ow, eyes=True):
    d.chord([cx - R, cy - R, cx + R, cy + R], 205, 335, fill=HAIR, outline=INK, width=int(ow))
    yl = cy + R * math.sin(math.radians(205))
    for k in range(6):
        x0 = cx - R * 0.74 + k * R * 0.25
        d.polygon([(x0, yl - 2), (x0 + R * 0.25, yl - 2), (x0 + R * 0.1, yl + R * 0.12)], fill=HAIR)
    if eyes:
        lx, ly = math.sin(t * 1.9), math.cos(t * 2.7)
        for sx in (-1, 1):
            ex, ey = cx + sx * R * 0.36, cy + R * 0.02
            d.ellipse([ex - R * 0.21, ey - R * 0.25, ex + R * 0.21, ey + R * 0.25],
                      fill=(255, 255, 255), outline=INK, width=max(1, int(ow * 0.8)))
            px, py = ex + lx * R * 0.08, ey + ly * R * 0.1 + R * 0.03
            circ(d, (px, py), R * 0.1, INK)
            circ(d, (px - R * 0.035, py - R * 0.04), R * 0.035, (255, 255, 255))
            d.arc([ex - R * 0.22, ey - R * 0.44, ex + R * 0.22, ey - R * 0.22], 200, 340, fill=(55, 30, 15),
                  width=max(1, int(ow * 1.3)))
    d.ellipse([cx - R * 0.09, cy + R * 0.16, cx + R * 0.09, cy + R * 0.33], fill=SKIN_D)
    d.chord([cx - R * 0.42, cy + R * 0.18, cx + R * 0.42, cy + R * 0.74], 0, 180, fill=(140, 20, 40),
            outline=INK, width=int(ow))
    d.rectangle([cx - R * 0.28, cy + R * 0.47, cx + R * 0.28, cy + R * 0.54], fill=(255, 255, 255))
    d.ellipse([cx - R * 0.16, cy + R * 0.56, cx + R * 0.16, cy + R * 0.69], fill=(240, 100, 120))
    for sx in (-1, 1):
        circ(d, (cx + sx * R * 0.62, cy + R * 0.36), R * 0.13, (255, 150, 160))


def draw_goggles(im, d, cx, y, R, t):
    ow = max(2, R * 0.06)
    d.rectangle([cx - R * 1.0, y - R * 0.11, cx + R * 1.0, y + R * 0.11], fill=(40, 40, 55))
    box = [cx - R * 0.74, y - R * 0.3, cx + R * 0.74, y + R * 0.3]
    d.rounded_rectangle([box[0] - ow * 1.5, box[1] - ow * 1.5, box[2] + ow * 1.5, box[3] + ow * 1.5],
                        radius=R * 0.27, fill=INK)
    d.rounded_rectangle(box, radius=R * 0.24, fill=(250, 250, 250))
    x0, y0 = int(box[0] + ow * 1.3), int(box[1] + ow * 1.3)
    lw, lh = int(box[2] - ow * 1.3) - x0, int(box[3] - ow * 1.3) - y0
    m = Image.new("L", (lw, lh), 0)
    ImageDraw.Draw(m).rounded_rectangle([0, 0, lw - 1, lh - 1], radius=R * 0.18, fill=255)
    im.paste(rainbow(lw, lh, t * 0.5, angle=35, cycles=1.2, s=0.8, res=2), (x0, y0), m)
    d.line([(x0 + lw * 0.12, y0 + lh * 0.45), (x0 + lw * 0.28, y0 + lh * 0.18)], fill=(255, 255, 255), width=int(ow))


SHADES = ("XXXXXXXXXXXXXXXXX",
          ".XWWXXXX.XWWXXXX.",
          ".XXWWXXX.XXWWXXX.",
          "..XXXXX...XXXXX..")


def draw_shades(d, cx, y, R):
    cell = R * 0.115
    x0, y0 = cx - cell * len(SHADES[0]) / 2, y - cell * 1.5
    for r, row in enumerate(SHADES):
        for c, ch in enumerate(row):
            if ch != ".":
                d.rectangle([x0 + c * cell, y0 + r * cell, x0 + (c + 1) * cell, y0 + (r + 1) * cell],
                            fill=(255, 255, 255) if ch == "W" else (10, 10, 10))


def draw_party_hat(d, cx, cy, R, t):
    ow = max(2, int(R * 0.06))
    bl, br, tip = (cx - R * 0.55, cy - R * 0.70), (cx + R * 0.45, cy - R * 0.86), (cx + R * 0.30, cy - R * 2.3)

    def edge(a, k):
        return lerp(a[0], tip[0], k), lerp(a[1], tip[1], k)

    d.polygon([bl, br, tip], fill=hsv(t * 0.3, 0.75, 1.0))
    for i in range(3):
        k0, k1 = 0.1 + i * 0.29, 0.22 + i * 0.29
        d.polygon([edge(bl, k0), edge(br, k0), edge(br, k1), edge(bl, k1)], fill=hsv(t * 0.3 + 0.33 + i * 0.2, 0.8, 1.0))
    d.line([bl, br, tip, bl], fill=INK, width=ow, joint="curve")
    circ(d, tip, R * 0.2, (255, 230, 0), INK, ow)


def head_sprite(R, t, hat=True, goggles=None, shades=False):
    """Голова (с колпаком и прочим). Возвращает (спрайт, центр головы в спрайте)."""
    R = max(8, int(R))
    sw, sh = int(R * 3.4), int(R * 4.8)
    cx, cy = sw / 2, sh - R * 1.4
    im = Image.new("RGBA", (sw, sh), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    ow = max(2, R * 0.07)
    for sx in (-1, 1):
        circ(d, (cx + sx * R * 0.96, cy + R * 0.05), R * 0.24, SKIN, INK, ow)
        circ(d, (cx + sx * R * 0.96, cy + R * 0.05), R * 0.1, SKIN_D)
    circ(d, (cx, cy), R, SKIN, INK, ow)
    photo = face_photo(int(2 * (R - ow)))
    if photo is not None:
        im.alpha_composite(photo, (int(cx - photo.width / 2), int(cy - photo.height / 2)))
    else:
        draw_face(d, cx, cy, R, t, ow, eyes=goggles != "eyes" and not shades)
    if goggles:
        draw_goggles(im, d, cx, cy - (R * 0.5 if goggles == "forehead" else R * 0.02), R, t)
    if shades:
        draw_shades(d, cx, cy + R * 0.02, R)
    if hat:
        draw_party_hat(d, cx, cy, R, t)
    return im, (cx, cy)


def limb(d, X, pts, colors, w, ow):
    sp = X.pts(pts)
    d.line(sp, fill=INK, width=int(w + 2 * ow), joint="curve")
    circ(d, sp[0], w / 2 + ow, INK)
    circ(d, sp[-1], w / 2 + ow, INK)
    for i in range(len(sp) - 1):
        d.line([sp[i], sp[i + 1]], fill=colors[i], width=int(w))
    for i, p in enumerate(sp):
        circ(d, p, w / 2, colors[min(i, len(colors) - 1)])


def solve_pose(pose):
    tilt = pose.get("tilt", 0.0)
    up, right = ddir(180 - tilt), ddir(90 - tilt)
    shw, hipw = pose.get("shw", SHW), pose.get("hipw", HIPW)
    sh_c = mul(up, TORSO - 12)
    j = dict(up=up, right=right, tilt=tilt, shw=shw, hipw=hipw)
    j["shL"], j["shR"] = add(sh_c, mul(right, -shw)), add(sh_c, mul(right, shw))
    j["hipL"], j["hipR"] = mul(right, -hipw), mul(right, hipw)
    j["head"] = add(mul(up, TORSO), mul(ddir(180 - tilt - pose.get("head", 0.0)), HEAD_R * 0.88 + NECK))
    for side in "LR":
        key = side.lower()
        sh = j["sh" + side]
        if key + "_hand" in pose:
            j["el" + side], j["wr" + side] = ik(sh, pose[key + "_hand"], UA, FA, pose.get(key + "_abend", 1))
        else:
            el = add(sh, mul(ddir(pose[key + "_ua"]), UA))
            j["el" + side], j["wr" + side] = el, add(el, mul(ddir(pose[key + "_fa"]), FA))
        j["kn" + side], j["ft" + side] = ik(j["hip" + side], pose[key + "_foot"], TH, SH,
                                            pose.get(key + "_kbend", 1 if side == "L" else -1))
    return j


def draw_roman(img, X, t, pose, outfit, hat=True, goggles=None, shades=False, beer=False,
               syringe=False, board=False, censor=None, censor_size=(150, 56), foot_dir="out"):
    """Рисует Романа Андреевича. Начало локальных координат — таз, единица ~ пиксель при X.s = 1."""
    j = solve_pose(pose)
    d = ImageDraw.Draw(img)
    s = X.s
    ow = max(2.0, 5 * s)
    up, right, tilt = j["up"], j["right"], j["tilt"]

    def T(u, v):
        return add(mul(right, u), mul(up, v))

    if board:
        by = max(j["ftL"][1], j["ftR"][1]) + 22
        d.polygon(X.pts(stadium_pts(0, by, 158, 15)), fill=hsv(t * 0.25 + 0.85, 0.85, 1.0), outline=INK, width=int(ow))
        d.polygon(X.pts(stadium_pts(0, by, 136, 5)), fill=(255, 235, 60))
        for ft in (j["ftL"], j["ftR"]):
            d.polygon(X.pts([(ft[0] - 24, by - 19), (ft[0] + 34, by - 19), (ft[0] + 34, by - 8), (ft[0] - 24, by - 8)]),
                      fill=(30, 30, 40))

    for side, sgn in (("L", -1), ("R", 1)):
        limb(d, X, [j["hip" + side], j["kn" + side], j["ft" + side]], (outfit["th"], outfit["sh"]), LEG_W * s, ow)
        fc = add(j["ft" + side], (sgn * 16 if foot_dir == "out" else 18, 8))
        d.polygon(X.pts(ellipse_pts(fc, 31, 15)), fill=outfit["foot"], outline=INK, width=int(ow))

    # туловище — суперэллипс с намёком на пивной животик
    vc, b, e = TORSO * 0.47, TORSO * 0.5 + 16, 2.5
    top_w, bot_w = j["shw"] + 14, j["hipw"] + 38

    def half_w(v):
        fr = clamp01((v - vc + b) / (2 * b))
        return lerp(bot_w, top_w, fr) + 10 * math.sin(math.pi * fr)

    tp = []
    for k in range(44):
        a = 2 * math.pi * k / 44
        c, sn = math.cos(a), math.sin(a)
        v = vc + b * math.copysign(abs(sn) ** (2 / e), sn)
        tp.append(T(half_w(v) * math.copysign(abs(c) ** (2 / e), c), v))
    d.polygon(X.pts(tp), fill=outfit["torso"], outline=INK, width=int(ow))
    if outfit.get("naked"):
        circ(d, X(T(0, 30)), 4 * s, SKIN_D)
        for u, v in ((-22, 100), (0, 92), (22, 100)):
            p = X(T(u, v))
            d.arc([p[0] - 7 * s, p[1] - 5 * s, p[0] + 7 * s, p[1] + 5 * s], 200, 340, fill=HAIR, width=max(1, int(2.5 * s)))
    if outfit.get("stripe"):
        def inner(v):
            return half_w(v) * (1 - (abs(v - vc) / b) ** e) ** (1 / e) - 4
        v0, v1 = 66, 88
        d.polygon(X.pts([T(-inner(v0), v0), T(inner(v0), v0), T(inner(v1), v1), T(-inner(v1), v1)]), fill=outfit["stripe"])

    if censor:
        p = X(T(0, -16))
        paste_rot(img, censor_sprite(int(censor_size[0] * s), int(censor_size[1] * s), tuple(censor)),
                  p[0], p[1], X.rot + tilt)

    if syringe:
        el, wr = X(j["elL"]), X(j["wrL"])
        ang = math.degrees(math.atan2(wr[1] - el[1], wr[0] - el[0]))
        L = int(230 * s)
        spr = syringe_sprite(L)
        paste_rot(img, spr, wr[0], wr[1], ang - 180, anchor=(L * 0.74, spr.height / 2))
    if beer:
        wr, size = X(j["wrR"]), int(120 * s)
        spr = beer_sprite(size, True)
        paste_rot(img, spr, wr[0], wr[1], X.rot * 0.3 + 6 * math.sin(t * 3), anchor=(spr.width - size * 0.92, size * 0.62))

    for side in "LR":
        limb(d, X, [j["sh" + side], j["el" + side], j["wr" + side]], (outfit["ua"], outfit["fa"]), ARM_W * s, ow)
        hc = X(j["wr" + side])
        circ(d, X(add(j["wr" + side], (0, -15))), 9 * s, GLOVE, INK, ow * 0.8)
        circ(d, hc, HAND_R * s, GLOVE, INK, ow)
        circ(d, (hc[0] - 5 * s, hc[1] - 6 * s), 5 * s, shade(GLOVE, 1.4))

    hc = X(j["head"])
    spr, anc = head_sprite(HEAD_R * s, t, hat=hat, goggles=goggles, shades=shades)
    paste_rot(img, spr, hc[0], hc[1], X.rot + tilt + pose.get("head", 0.0), anchor=anc)


# ---------------------------------------------------------------- велосипед

BIKE = dict(rh=(-125, 152), fh=(195, 152), bb=(28, 120), seat=(-6, 30), ht=(150, -20), hb=(162, 32),
            grip=(180, -44), wheel=82)


def draw_bike(d, X, t, frame_col):
    s = X.s
    phi = t * 2 * math.pi * 1.3
    for hub in (BIKE["rh"], BIKE["fh"]):
        c, R = X(hub), BIKE["wheel"] * s
        circ(d, c, R, None, INK, 15 * s)
        circ(d, c, R - 12 * s, None, (200, 200, 210), 4 * s)
        for k in range(12):
            a = phi * 1.6 + k * math.pi / 6
            d.line([c, (c[0] + (R - 12 * s) * math.cos(a), c[1] + (R - 12 * s) * math.sin(a))],
                   fill=(225, 225, 235), width=max(1, int(2 * s)))
        circ(d, c, 9 * s, (80, 80, 90), INK, 2 * s)
    tubes = (("bb", "rh"), ("seat", "rh"), ("bb", "seat"), ("seat", "ht"), ("bb", "hb"), ("ht", "hb"), ("hb", "fh"))
    for col, w in ((INK, 17), (frame_col, 10)):
        for a, b in tubes:
            d.line([X(BIKE[a]), X(BIKE[b])], fill=col, width=int(w * s))
    d.line([X(BIKE["ht"]), X((148, -48)), X(BIKE["grip"])], fill=INK, width=int(12 * s), joint="curve")
    d.polygon(X.pts(ellipse_pts((-12, 18), 42, 11)), fill=(30, 30, 35), outline=INK)
    bb = BIKE["bb"]
    circ(d, X(bb), 22 * s, (150, 150, 160), INK, 3 * s)
    pedals = []
    for k in (0, 1):
        a = phi + k * math.pi
        p = (bb[0] + 38 * math.cos(a), bb[1] + 38 * math.sin(a))
        d.line([X(bb), X(p)], fill=(60, 60, 70), width=int(8 * s))
        d.polygon(X.pts(stadium_pts(p[0], p[1], 16, 5)), fill=(40, 40, 40))
        pedals.append(p)
    return pedals


# ---------------------------------------------------------------- сцена 1: всё вместе

@lru_cache(maxsize=None)
def hero_items():
    items = (syringe_sprite(150), emoji("🍺", 80), glove_sprite(105, GLOVE), emoji("🏂", 80),
             emoji("📄", 70), emoji("🚴", 80), glove_sprite(105, (175, 110, 255)), emoji("💉", 76),
             beer_sprite(84), emoji("🎁", 70), emoji("🧤", 74), emoji("💊", 66))
    return tuple(i for i in items if i is not None)


def ribbon(img, cx, cy, w, h, text, t):
    d = ImageDraw.Draw(img)
    dark, mid, light = (120, 0, 25), (200, 15, 45), (240, 70, 95)
    for sgn in (-1, 1):
        xi, xo = cx + sgn * (w / 2 - 40), cx + sgn * (w / 2 + 62)
        top, bot = cy - h / 2 + 24, cy + h / 2 + 24
        d.polygon([(xi, top), (xo, top), (xo - sgn * 34, (top + bot) / 2), (xo, bot), (xi, bot)],
                  fill=mid, outline=INK, width=4)
        d.polygon([(cx + sgn * (w / 2 - 40), cy + h / 2), (cx + sgn * w / 2, cy + h / 2),
                   (cx + sgn * (w / 2 - 40), cy + h / 2 + 24)], fill=dark, outline=INK, width=3)
    d.rectangle([cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2], fill=mid, outline=INK, width=4)
    d.rectangle([cx - w / 2 + 4, cy - h / 2 + 7, cx + w / 2 - 4, cy - h / 2 + 15], fill=light)
    wordart(img, text, fit_font("lobster", text, w - 70, 66), cx, cy - 4, t + 0.5, fill=GOLD, wave=3,
            extrude=3, ext_color=(90, 0, 20), outline=4, outline_color=(70, 0, 15))


def scene_hero(f):
    t = f * DT
    img = Image.new("RGB", (W, H))
    cx, cy = W / 2, 540
    sunburst(img, cx, cy, 20, t * 0.45, lambda i: hsv(i / 20 + t * 0.12, 0.5, 1.0))
    glow(img, cx, cy, 430, (255, 255, 255), 235)
    items = hero_items()
    for k, spr in enumerate(items):
        a = 2 * math.pi * k / len(items) + t * 0.7
        paste_rot(img, spr, cx + 405 * math.cos(a), cy - 10 + 315 * math.sin(a),
                  (t * 100 + k * 40) * (1 if k % 2 else -1))
    for bx, by, col, ph in ((105, 300, GLOVE, 0.0), (858, 330, (175, 110, 255), 1.9)):
        balloon(img, bx, by + 16 * math.sin(t * 2.3 + ph), col, t + ph, 160)
    bob = 9 * math.sin(t * 2 * math.pi * 1.1)
    pose = dict(tilt=2.5 * math.sin(t * 2.7), head=-6 * math.sin(t * 2.7),
                l_ua=-156, l_fa=-172 + 5 * math.sin(t * 3.1), r_ua=72, r_fa=116 + 10 * math.sin(t * 3.1),
                l_foot=(-64, 156), r_foot=(64, 156))
    draw_roman(img, Xf(cx, 548 + bob, 0.92, 2 * math.sin(t * 1.9)), t, pose, NAKED,
               goggles="forehead", beer=True, syringe=True, board=True, censor=("ЦЕНЗУРА",))
    wordart(img, TITLE, fit_font("lobster", TITLE, 880, 132), W / 2, 100, t, wave=12, extrude=9)
    ribbon(img, W / 2, 836, 760, 84, NAME, t)
    return img


# ---------------------------------------------------------------- сцена 2: тендер

DOC_W, DOC_H = 800, 650
SLAM = 15
DOC_NAME = "МЕРЗЛИКИН Р. А."


@lru_cache(maxsize=None)
def tender_doc_base():
    im = Image.new("RGBA", (DOC_W, DOC_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, DOC_W - 1, DOC_H - 1], fill=(253, 251, 244), outline=(150, 150, 165), width=3)
    ink = (25, 25, 50)
    reg, bold = font("serif", 29), font("serif_b", 30)
    d.text((DOC_W / 2, 54), "ПРОТОКОЛ № ДР-2026", font=font("serif_b", 42), anchor="mm", fill=ink)
    d.text((DOC_W / 2, 98), "подведения итогов определения поставщика", font=font("serif", 25), anchor="mm", fill=ink)
    d.line([(40, 124), (DOC_W - 40, 124)], fill=ink, width=2)
    d.text((44, 172), "Предмет закупки:", font=reg, anchor="ls", fill=ink)
    d.text((44, 214), "поставка поздравлений с Днём рождения", font=bold, anchor="ls", fill=ink)
    d.text((44, 262), "НМЦК:", font=reg, anchor="ls", fill=ink)
    d.text((44 + d.textlength("НМЦК: ", font=reg), 262), "бесценно", font=bold, anchor="ls", fill=ink)
    d.text((44, 306), "Подано заявок: 1. Отклонено: 0.", font=reg, anchor="ls", fill=ink)
    d.text((44, 352), "Победитель:", font=reg, anchor="ls", fill=ink)
    d.text((44, 478), "Обеспечение исполнения: ящик пива", font=reg, anchor="ls", fill=ink)
    d.text((44, 522), "Срок поставки: ежегодно, бессрочно", font=reg, anchor="ls", fill=ink)
    d.text((44, 600), "Комиссия: друзья", font=reg, anchor="ls", fill=ink)
    d.line([(300 + k * 6, 590 + 14 * math.sin(k * 0.7) - k * 0.6) for k in range(36)],
           fill=(30, 40, 160), width=3, joint="curve")
    # фото победителя: голый, в колпаке, как положено
    photo = Image.new("RGB", (160, 186), (205, 230, 255))
    pd = ImageDraw.Draw(photo)
    pd.ellipse([14, 150, 146, 260], fill=SKIN, outline=INK, width=3)
    head, anc = head_sprite(38, 0.6)
    paste_rot(photo, head, 80, 122, anchor=anc)
    pd.rectangle([0, 0, 159, 185], outline=ink, width=3)
    im.paste(photo, (596, 284))
    d.text((676, 494), "фото", font=font("serif", 20), anchor="mm", fill=(90, 90, 110))
    return im


def tender_doc(f):
    im = tender_doc_base().copy()
    d = ImageDraw.Draw(im)
    fnt = fit_font("serif_b", DOC_NAME, 530, 54)
    p = clamp01((f - SLAM - 1) / 6)
    if p > 0:
        d.rectangle([38, 376, 38 + (d.textlength(DOC_NAME, font=fnt) + 14) * p, 434], fill=(255, 238, 0))
    d.text((44, 422), DOC_NAME, font=fnt, anchor="ls", fill=(20, 20, 60))
    return im


@lru_cache(maxsize=None)
def stamp_sprite():
    w, h = 430, 176
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    red = (215, 20, 40)
    d.rounded_rectangle([5, 5, w - 5, h - 5], radius=28, outline=red, width=10)
    d.rounded_rectangle([22, 22, w - 22, h - 22], radius=16, outline=red, width=4)
    d.text((w / 2, h / 2 - 14), "ПОБЕДИТЕЛЬ", font=fit_font("russo", "ПОБЕДИТЕЛЬ", w - 70, 64), anchor="mm", fill=red)
    d.text((w / 2, h / 2 + 42), "ЗАКУПКА ВЫИГРАНА", font=font("russo", 26), anchor="mm", fill=red)
    noise = np.random.default_rng(7).random((h // 6 + 1, w // 6 + 1))
    holes = Image.fromarray(((noise > 0.12) * 255).astype(np.uint8), "L").resize((w, h), Image.Resampling.NEAREST)
    a = np.asarray(im.getchannel("A"), dtype=np.uint16) * np.asarray(holes, dtype=np.uint16) // 255
    im.putalpha(Image.fromarray(a.astype(np.uint8), "L"))
    return im


@lru_cache(maxsize=None)
def confetti_items():
    items = (syringe_sprite(110), glove_sprite(80, GLOVE), emoji("💊", 54), emoji("🧤", 58),
             syringe_sprite(90, (60, 220, 120)), emoji("💉", 60), glove_sprite(80, (175, 110, 255)), emoji("🍺", 60))
    return tuple(i for i in items if i is not None)


def burst(img, origin, age, seed, n=16):
    items = confetti_items()
    rng = random.Random(seed)
    for k in range(n):
        ang = rng.uniform(-math.pi * 0.95, -math.pi * 0.05)
        sp = rng.uniform(450, 1000)
        x = origin[0] + math.cos(ang) * sp * age
        y = origin[1] + math.sin(ang) * sp * age + 900 * age * age
        paste_rot(img, items[k % len(items)], x, y, rng.uniform(-500, 500) * age)


def scene_tender(f):
    t = f * DT
    img = Image.new("RGB", (W, H))
    sunburst(img, W / 2, 560, 24, -t * 0.35, lambda i: (25, 70, 190) if i % 2 else (60, 130, 245))
    glow(img, W / 2, 560, 470, (255, 235, 140), 210)
    for k in range(9):
        rng = random.Random(500 + k)
        x, sp, y0 = rng.uniform(40, W - 40), rng.uniform(110, 210), rng.uniform(0, H)
        spr = emoji(("💰", "💸", "📈", "🏆")[k % 4], int(rng.uniform(50, 80)))
        paste_rot(img, spr, x + 20 * math.sin(t * 2 + k), (y0 + sp * t) % (H + 120) - 60, 25 * math.sin(t * 3 + k))
    dx, dy = W / 2, lerp(H + 380, 528, ease_out_back(f / 8))
    if SLAM <= f < SLAM + 6:
        rng = random.Random(f)
        dx, dy = dx + rng.uniform(-14, 14), dy + rng.uniform(-14, 14)
    paste_rot(img, Image.new("RGBA", (DOC_W, DOC_H), (0, 0, 0, 90)), dx + 14, dy + 18, -2.5)
    paste_rot(img, tender_doc(f), dx, dy, -2.5)
    sx, sy = dx + 175, dy + 228
    if f >= SLAM - 4:
        q = clamp01((f - (SLAM - 4)) / 4)
        paste_rot(img, stamp_sprite(), sx, sy, -14, scale=lerp(3.0, 1.0, q * q))
    if f >= SLAM:
        burst(img, (sx, sy - 40), (f - SLAM) * DT, seed=3)
    flash = (f // 3) % 2
    wordart(img, "ИТОГИ ТЕНДЕРА!", fit_font("bubbles", "ИТОГИ ТЕНДЕРА!", 860, 96), W / 2, 100, t,
            fill=((0, (255, 255, 140)), (1, (255, 150, 0))) if flash else ((0, (255, 140, 140)), (1, (220, 0, 40))),
            wave=8, extrude=6, ext_color=(90, 0, 20))
    return img


# ---------------------------------------------------------------- сцена 3: зимой сноуборд, летом велик

SLOPE_A, SLOPE_K = 470.0, 0.58


def slope_y(x):
    return SLOPE_A + SLOPE_K * x


@lru_cache(maxsize=None)
def winter_bg():
    img = vgrad(W, H, ((0, (20, 60, 150)), (0.55, (140, 200, 255)), (1, (220, 240, 255))))
    d = ImageDraw.Draw(img)
    for px, py in ((-40, 330), (120, 250), (300, 300), (430, 235), (560, 320)):
        hw = (560 - py) * 0.9
        d.polygon([(px - hw, 560), (px, py), (px + hw, 560)], fill=(105, 135, 190))
        k = 0.32
        d.polygon([(px - hw * k, py + (560 - py) * k), (px - hw * k * 0.4, py + (560 - py) * k * 0.75),
                   (px, py + (560 - py) * k), (px + hw * k * 0.5, py + (560 - py) * k * 0.7),
                   (px + hw * k, py + (560 - py) * k), (px, py)], fill=(245, 250, 255))
    return img


def tree(d, x, y, s):
    d.rectangle([x - 8 * s, y - 28 * s, x + 8 * s, y], fill=(110, 70, 40))
    for w, hgt, yy in ((66, 70, -20), (52, 62, -60), (38, 54, -98)):
        d.polygon([(x - w * s, y + yy * s), (x + w * s, y + yy * s), (x, y + (yy - hgt) * s)],
                  fill=(30, 125, 70), outline=(15, 70, 40))
        d.polygon([(x - w * 0.33 * s, y + (yy - hgt * 0.67) * s), (x + w * 0.33 * s, y + (yy - hgt * 0.67) * s),
                   (x, y + (yy - hgt) * s)], fill=(250, 252, 255))


def winter_half(t):
    img = winter_bg().copy()
    d = ImageDraw.Draw(img, "RGBA")
    d.polygon([(-10, slope_y(-10)), (W, slope_y(W)), (W, H), (-10, H)], fill=(238, 246, 255))
    for k in range(5):
        off = (k * 90 + t * 160) % 450
        d.line([(0, slope_y(0) + 30 + off), (W, slope_y(W) + 30 + off)], fill=(205, 225, 248), width=5)
    trees = []
    for k, depth in enumerate((35, 150, 80, 190, 20, 110)):
        x = (k * 115 - t * 330) % 690 - 100
        trees.append((slope_y(x) + depth, x, 0.55 + depth / 380))
    for y, x, s in sorted(trees):
        tree(d, x, y, s)
    bx = 238
    X = Xf.pinned((0, 172), (bx, slope_y(bx) - 4 + 3 * math.sin(t * 9)), 0.6,
                  math.degrees(math.atan(SLOPE_K)) * 0.9)
    tail = X((-165, 176))
    for k in range(26):
        rng = random.Random(k)
        life = 0.55 + 0.3 * rng.random()
        age = ((t + rng.random() * life) % life) / life
        tt = age * life * 1.6
        x = tail[0] - rng.uniform(120, 320) * tt
        y = tail[1] - rng.uniform(80, 260) * tt + 300 * tt * tt
        r = (6 + 10 * rng.random()) * (1 - age * 0.6)
        d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 255, int(230 * (1 - age))))
    w = math.sin(t * 4)
    pose = dict(tilt=-6 + 3 * w, head=8, l_ua=-118 + 12 * w, l_fa=-140 + 10 * w, r_ua=102 - 10 * w,
                r_fa=78 - 12 * w, l_foot=(-74, 150), r_foot=(74, 150))
    draw_roman(img, X, t, pose, SNOWSUIT, goggles="eyes", board=True)
    for k in range(22):
        rng = random.Random(100 + k)
        x = rng.uniform(0, 470) + 18 * math.sin(t * 1.7 + k)
        y = (rng.uniform(0, H) + 90 * t * rng.uniform(0.7, 1.3)) % (H + 40) - 20
        paste_rot(img, snowflake_sprite(int(rng.uniform(14, 30))), x, y, t * 60 + k * 20)
    wordart(img, "ЗИМОЙ —", font("russo", 44), 238, 52, t, fill=ICE, wave=4, extrude=4,
            ext_color=(10, 40, 110), outline=4)
    wordart(img, "ГРОЗА СКЛОНОВ!", fit_font("bubbles", "ГРОЗА СКЛОНОВ!", 420, 64), 238, 122, t,
            fill=ICE, wave=6, extrude=5, ext_color=(10, 40, 110))
    return img


@lru_cache(maxsize=None)
def summer_bg():
    return vgrad(W, H, ((0, (60, 170, 255)), (0.6, (170, 225, 255)), (1, (255, 245, 200))))


def draw_sun(d, cx, cy, r, t):
    for k in range(12):
        a = t * 0.8 + k * math.pi / 6
        d.polygon([(cx + r * 1.15 * math.cos(a - 0.13), cy + r * 1.15 * math.sin(a - 0.13)),
                   (cx + r * 1.75 * math.cos(a), cy + r * 1.75 * math.sin(a)),
                   (cx + r * 1.15 * math.cos(a + 0.13), cy + r * 1.15 * math.sin(a + 0.13))], fill=(255, 200, 0))
    circ(d, (cx, cy), r, (255, 230, 40), (230, 120, 0), 4)
    d.rounded_rectangle([cx - r * 0.78, cy - r * 0.28, cx - r * 0.06, cy + r * 0.18], radius=8, fill=INK)
    d.rounded_rectangle([cx + r * 0.06, cy - r * 0.28, cx + r * 0.78, cy + r * 0.18], radius=8, fill=INK)
    d.line([(cx - r * 0.1, cy - r * 0.16), (cx + r * 0.1, cy - r * 0.16)], fill=INK, width=4)
    d.arc([cx - r * 0.45, cy - r * 0.05, cx + r * 0.45, cy + r * 0.62], 20, 160, fill=INK, width=5)


def cloud(d, x, y, s):
    for dx, dy, r in ((-50, 10, 34), (-15, -12, 44), (30, 0, 38), (60, 14, 28)):
        circ(d, (x + dx * s, y + dy * s), r * s, (255, 255, 255))


def summer_half(t):
    img = summer_bg().copy()
    d = ImageDraw.Draw(img, "RGBA")
    draw_sun(d, 868, 262, 50, t)
    for k in range(3):
        cloud(d, (k * 260 + 600 - t * 40) % 760 + 380, 340 + k * 45, 0.8 + 0.2 * k)
    for k in range(4):
        x = (k * 260 - t * 60) % 1040 + 300
        d.ellipse([x - 220, 560, x + 220, 860], fill=(90, 190, 90) if k % 2 else (70, 170, 80))
    d.rectangle([0, 700, W, 790], fill=(95, 95, 110))
    d.rectangle([0, 790, W, H], fill=(80, 180, 70))
    for k in range(9):
        x = (k * 120 - t * 420) % 1080 - 60
        d.rectangle([x, 741, x + 60, 749], fill=(255, 255, 255))
    for k in range(6):
        y, x = 470 + k * 38, (k * 37 - t * 600) % 140 + 500
        d.line([(x, y), (x + 70, y)], fill=(255, 255, 255, 190), width=5)
    X = Xf(706, 628 - 3 * abs(math.sin(t * 7)), 0.6)
    pedals = draw_bike(d, X, t, hsv(t * 0.2 + 0.9, 0.85, 1.0))
    pose = dict(tilt=30, head=-22, shw=24, hipw=8, l_hand=BIKE["grip"], r_hand=add(BIKE["grip"], (6, 4)),
                l_foot=add(pedals[1], (-14, -10)), r_foot=add(pedals[0], (-14, -10)), l_kbend=-1, r_kbend=-1)
    draw_roman(img, X, t, pose, CYCLIST, shades=True, foot_dir="fwd")
    wordart(img, "ЛЕТОМ —", font("russo", 44), 722, 52, t, fill=SUN, wave=4, extrude=4,
            ext_color=(120, 40, 0), outline=4)
    wordart(img, "ГРОЗА ВЕЛОДОРОЖЕК!", fit_font("bubbles", "ГРОЗА ВЕЛОДОРОЖЕК!", 420, 64), 722, 122, t,
            fill=SUN, wave=6, extrude=5, ext_color=(120, 40, 0))
    return img


def scene_sport(f):
    t = f * DT
    zig = [(W / 2 + (26 if k % 2 else -26) + 5 * math.sin(t * 6 + k), -40 + k * 75) for k in range(15)]
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).polygon([(0, -40)] + zig + [(0, zig[-1][1])], fill=255)
    img = Image.composite(winter_half(t), summer_half(t), mask)
    d = ImageDraw.Draw(img)
    d.line(zig, fill=INK, width=22, joint="curve")
    d.line(zig, fill=hsv(t * 0.5, 0.6, 1.0), width=12, joint="curve")
    return img


# ---------------------------------------------------------------- сцена 4: дома в чём мать родила

def draw_carpet(d, x0, y0, x1, y1):
    red, dark, gold, navy, cream = (165, 20, 35), (95, 10, 25), (235, 175, 50), (30, 35, 90), (245, 230, 200)
    for x in range(x0 + 6, x1 - 4, 9):
        d.line([(x, y0 - 14), (x, y0)], fill=cream, width=3)
        d.line([(x, y1), (x, y1 + 14)], fill=cream, width=3)
    d.rectangle([x0, y0, x1, y1], fill=dark)
    d.rectangle([x0 + 14, y0 + 14, x1 - 14, y1 - 14], fill=gold)
    d.rectangle([x0 + 22, y0 + 22, x1 - 22, y1 - 22], fill=navy)
    for x in range(x0 + 36, x1 - 30, 28):
        for y in (y0 + 36, y1 - 36):
            d.polygon([(x, y - 8), (x + 8, y), (x, y + 8), (x - 8, y)], fill=gold)
    for y in range(y0 + 64, y1 - 50, 28):
        for x in (x0 + 36, x1 - 36):
            d.polygon([(x, y - 8), (x + 8, y), (x, y + 8), (x - 8, y)], fill=gold)
    d.rectangle([x0 + 50, y0 + 50, x1 - 50, y1 - 50], fill=red)
    step = 56
    for gy in range(y0 + 50 + step // 2, y1 - 50, step):
        for gx in range(x0 + 50 + step // 2, x1 - 50, step):
            col = (gold, navy, cream)[((gx - x0) // step + (gy - y0) // step) % 3]
            d.polygon([(gx, gy - 20), (gx + 20, gy), (gx, gy + 20), (gx - 20, gy)], fill=col)
            d.polygon([(gx, gy - 7), (gx + 7, gy), (gx, gy + 7), (gx - 7, gy)], fill=dark)
    cx, cy = (x0 + x1) / 2, (y0 + y1) / 2
    for r, col in ((200, dark), (180, gold), (160, navy), (130, red), (100, gold), (70, dark), (40, cream)):
        d.polygon([(cx, cy - r), (cx + r * 1.3, cy), (cx, cy + r), (cx - r * 1.3, cy)], fill=col)


@lru_cache(maxsize=None)
def home_bg():
    img = Image.new("RGB", (W, H), (250, 229, 198))
    d = ImageDraw.Draw(img)
    for x in range(0, W, 64):
        d.rectangle([x, 0, x + 22, 740], fill=(242, 214, 182))
    for y in range(30, 740, 64):
        for x in range(43 + (y // 64 % 2) * 32, W, 64):
            circ(d, (x, y), 6, (235, 150, 160))
            circ(d, (x, y), 2.5, (250, 229, 198))
    draw_carpet(d, 150, 228, 810, 718)
    d.rectangle([0, 728, W, 748], fill=(120, 70, 35))
    d.rectangle([0, 748, W, H], fill=(178, 112, 62))
    for row, y in enumerate(range(748, H, 34)):
        d.line([(0, y), (W, y)], fill=(140, 85, 45), width=3)
        for x in range(-((row * 90) % 180), W, 180):
            d.line([(x, y), (x, y + 34)], fill=(140, 85, 45), width=3)
    return img


@lru_cache(maxsize=None)
def sticky_sprite():
    w, h = 290, 190
    im = Image.new("RGBA", (w + 10, h + 10), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rectangle([10, 10, w + 9, h + 9], fill=(0, 0, 0, 70))
    d.rectangle([0, 0, w, h], fill=(255, 240, 90, 255))
    d.rectangle([0, 0, w, 26], fill=(250, 222, 60, 255))
    lines = ("Дресс-код:", "не предусмотрен", "техзаданием")
    fnt = fit_font("hand", lines[1], w - 26, 42)
    for i, line in enumerate(lines):
        d.text((w / 2, 64 + i * 46), line, font=fnt, anchor="mm", fill=(20, 40, 140, 255))
    return im


def scene_home(f):
    t = f * DT
    img = home_bg().copy()
    d = ImageDraw.Draw(img)
    for k in range(8):
        rng = random.Random(40 + k + 1000 * ((f + k * 3) // 8))
        sparkle(d, rng.uniform(170, 790), rng.uniform(250, 700), 14 * math.sin(math.pi * ((f + k * 3) % 8 + 0.5) / 8),
                (255, 245, 180), rng.random())
    for bx, by, col, ph in ((92, 520, (255, 120, 190), 0.4), (872, 560, GLOVE, 2.2)):
        balloon(img, bx, by + 14 * math.sin(t * 2.4 + ph), col, t + ph, 150)
    wave = math.sin(t * 2 * math.pi * 1.5)
    hop = abs(math.sin(t * 2 * math.pi * 0.75)) * 14
    pose = dict(tilt=3 * wave, head=-6 * wave, l_ua=-38, l_fa=50, r_ua=140, r_fa=168 + 24 * wave,
                l_foot=(-44, 166), r_foot=(44, 166))
    draw_roman(img, Xf(480, 572 - hop, 0.97), t, pose, NAKED, censor=("КОММЕРЧЕСКАЯ", "ТАЙНА"), censor_size=(196, 78))
    paste_rot(img, sticky_sprite(), 812, 345, 7 + 2 * math.sin(t * 2))
    if (f // 3) % 2 == 0:
        paste_rot(img, emoji("🔞", 92), 118, 330, -12)
    fnt = fit_font("lobster", "мать родила!", 860, 92)
    wordart(img, "Дома — в чём", fnt, W / 2, 70, t, fill=PINK, wave=8, extrude=7, ext_color=(110, 0, 60))
    wordart(img, "мать родила!", fnt, W / 2, 158, t - 0.25, fill=PINK, wave=8, extrude=7, ext_color=(110, 0, 60))
    return img


# ---------------------------------------------------------------- сцена 5: пиво и стих

POEM_AT = (4, 16, 28, 40)
RESET = 50


@lru_cache(maxsize=None)
def beer_bg():
    img = vgrad(W, H, ((0, (255, 225, 110)), (0.45, (245, 170, 30)), (1, (165, 80, 5))))
    d = ImageDraw.Draw(img)
    for x in range(0, W + 40, 40):
        circ(d, (x, 6), 30, (255, 252, 240))
    return img


@lru_cache(maxsize=None)
def poem_line(i):
    widest = max(POEM, key=lambda s: font("marck", 64).getlength(s))
    fnt = fit_font("marck", widest, 880, 66)
    asc, desc = fnt.getmetrics()
    w, h = int(fnt.getlength(POEM[i])) + 40, asc + desc + 30
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.text((w / 2 + 4, h / 2 + 5), POEM[i], font=fnt, anchor="mm", fill=(60, 25, 0, 150),
           stroke_width=6, stroke_fill=(60, 25, 0, 150))
    d.text((w / 2, h / 2), POEM[i], font=fnt, anchor="mm", fill=(255, 255, 250, 255),
           stroke_width=6, stroke_fill=(80, 30, 0, 255))
    return im


@lru_cache(maxsize=None)
def cheers_sprite():
    w, h = 330, 170
    im = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.polygon(star_pts(w / 2, h / 2, w / 2 - 4, w / 2 - 40, 14, squash=(h - 8) / (w - 8)),
              fill=(255, 230, 0, 255), outline=(200, 20, 30, 255), width=6)
    d.text((w / 2, h / 2), "БУДЕМ!", font=font("russo", 48), anchor="mm", fill=(215, 20, 40, 255),
           stroke_width=4, stroke_fill=(255, 255, 255, 255))
    return im


def beer_sign(img, f):
    d = ImageDraw.Draw(img)
    x0, y0, x1, y1 = 70, 34, 890, 214
    d.rounded_rectangle([x0 + 8, y0 + 10, x1 + 8, y1 + 10], radius=24, fill=(120, 60, 0))
    d.rounded_rectangle([x0, y0, x1, y1], radius=24, fill=(250, 250, 245), outline=(0, 120, 60), width=12)
    green = (0, 110, 55)
    d.text((x0 + 44, y0 + 84), "ДНЕЙ", font=font("russo", 64), anchor="ls", fill=green)
    d.text((x0 + 44, y0 + 150), "БЕЗ ПИВА:", font=font("russo", 64), anchor="ls", fill=green)
    val = 1 + f // 12 if f < RESET else 0
    flash = RESET <= f < RESET + 8 and f % 2 == 0
    bx0, by0, bx1, by1 = 596, 52, 864, 196
    d.rounded_rectangle([bx0, by0, bx1, by1], radius=14, fill=(210, 0, 0) if flash else (20, 20, 25))
    d.text(((bx0 + bx1) / 2, by0 + 64), str(val), font=font("pixel", 76), anchor="mm",
           fill=(255, 255, 255) if flash else (255, 60, 60) if val == 0 else (60, 255, 120))
    d.text(((bx0 + bx1) / 2, by1 - 20), "попытка №26", font=font("russo", 24), anchor="mm", fill=(200, 200, 200))


def scene_beer(f):
    t = f * DT
    img = beer_bg().copy()
    d = ImageDraw.Draw(img, "RGBA")
    for k in range(36):
        rng = random.Random(900 + k)
        x = rng.uniform(10, W - 10) + 8 * math.sin(t * 3 + k)
        r = rng.uniform(5, 16)
        y = H + 20 - (rng.uniform(0, H) + rng.uniform(80, 200) * t) % (H + 40)
        d.ellipse([x - r, y - r, x + r, y + r], outline=(255, 250, 220, 180), width=3)
        circ(d, (x - r * 0.35, y - r * 0.35), r * 0.25, (255, 255, 255, 200))
    beer_sign(img, f)
    for i in range(len(POEM)):
        if f < POEM_AT[i]:
            continue
        sc = ease_out_back((f - POEM_AT[i]) / 5)
        wob = 2.5 * math.sin(t * 7) if i == len(POEM) - 1 and f >= RESET else 0.0
        if sc > 0.05:
            paste_rot(img, poem_line(i), W / 2, 296 + i * 92, wob, scale=sc)
    if f < RESET - 7:
        k = 0.0
    elif f < RESET:
        k = ease_in_out((f - (RESET - 7)) / 7)
    else:
        k = 1.0 - 0.3 * clamp01((f - RESET) / 5)
    bob = 6 * math.sin(t * 5)
    paste_rot(img, beer_sprite(150, True), lerp(240, 392, k), 790 + bob, 18 * k)
    paste_rot(img, beer_sprite(150), lerp(720, 568, k), 790 - bob, -18 * k)
    if f >= RESET:
        a = (f - RESET) * DT
        rng = random.Random(77)
        for _ in range(18):
            ang, sp, r = rng.uniform(-math.pi * 0.95, -math.pi * 0.05), rng.uniform(250, 600), rng.uniform(8, 18)
            if a < 1.2:
                x = 480 + math.cos(ang) * sp * a
                y = 720 + math.sin(ang) * sp * a + 900 * a * a
                r *= max(0.2, 1 - a)
                d.ellipse([x - r, y - r, x + r, y + r], fill=(255, 255, 245, int(255 * (1 - a / 1.2))))
        paste_rot(img, cheers_sprite(), 480, 676, 6 * math.sin(t * 6), scale=max(0.05, ease_out_back((f - RESET) / 4)))
    return img


# ---------------------------------------------------------------- монтаж

SCENES = (                          # (функция, длительность в кадрах, переход на входе в сцену)
    (scene_hero, 36, "star"),
    (scene_tender, 44, "star"),
    (scene_sport, 34, "heart"),
    (scene_home, 36, "clock"),
    (scene_beer, 88, "spin"),
)
STARTS = [sum(s[1] for s in SCENES[:i]) for i in range(len(SCENES))]
TOTAL = sum(s[1] for s in SCENES)


def transition(kind, prev, nxt, p):
    if kind == "spin":
        out, k = nxt.copy(), 1 - p
        if k > 0.02:
            spr = prev.convert("RGBA").resize((max(1, int(W * k)), max(1, int(H * k))), BILINEAR)
            paste_c(out, spr.rotate(p * 540, resample=BILINEAR, expand=True), W / 2, H / 2)
        return out
    mask = Image.new("L", (W, H), 0)
    dm = ImageDraw.Draw(mask)
    rim = None
    if kind == "star":
        rim = star_pts(W / 2, H / 2, p * W * 1.7, p * W * 0.85, 5, rot=p * 2.5)
    elif kind == "heart":
        rim = heart_pts(W / 2, H / 2 + 40, p * W * 2.0)
    else:
        dm.pieslice([-W, -H, 2 * W, 2 * H], -90, -90 + 360 * p, fill=255)
    if rim:
        dm.polygon(rim, fill=255)
    out = Image.composite(nxt, prev, mask)
    d = ImageDraw.Draw(out)
    if rim:
        d.line(rim + rim[:1], fill=(255, 225, 0), width=14, joint="curve")
    else:
        a = math.radians(-90 + 360 * p)
        d.line([(W / 2, H / 2), (W / 2 + 2 * W * math.cos(a), H / 2 + 2 * H * math.sin(a))], fill=(255, 225, 0), width=14)
    return out


@lru_cache(maxsize=None)
def ticker_strip():
    fnt = font("russo", 34)
    gap = 22
    segs = []
    for text, em in TICKER:
        segs.append((text, int(fnt.getlength(text)) + 2 * gap))
        segs.append((em, 44 + 2 * gap))
    period = sum(w for _, w in segs)
    strip = vgrad(period + W, TICKER_H, ((0, (130, 0, 95)), (0.5, (65, 0, 115)), (1, (20, 0, 60))))
    d = ImageDraw.Draw(strip)
    d.line([(0, 2), (period + W, 2)], fill=(255, 220, 0), width=4)
    x = 0
    while x < period + W:
        for i, (val, w) in enumerate(segs):
            if i % 2 == 0:
                d.text((x + gap, TICKER_H / 2 + 2), val, font=fnt, anchor="lm", fill=(255, 235, 60),
                       stroke_width=3, stroke_fill=(40, 0, 30))
            else:
                e = emoji(val, 42)
                if e is not None:
                    paste(strip, e, x + gap, (TICKER_H - e.height) / 2 + 2)
            x += w
    return strip, period


@lru_cache(maxsize=None)
def border_mask(bw=12):
    m = Image.new("L", (W, H), 0)
    ImageDraw.Draw(m).rectangle([0, 0, W - 1, H - 1], outline=255, width=bw)
    return m


def overlay(img, g):
    """Поверх всех сцен: бегущая строка, радужная рамка, блёстки. Всё зациклено по общему таймлайну."""
    phase = g / TOTAL
    strip, period = ticker_strip()
    off = int(phase * period) % period
    img.paste(strip.crop((off, 0, off + W, TICKER_H)), (0, H - TICKER_H))
    img.paste(rainbow(W, H, phase * 2, angle=45, cycles=1.5, s=0.85, res=16), (0, 0), border_mask())
    d = ImageDraw.Draw(img)
    for k in range(12):
        life = 9 + k % 4
        n = g + k * 5
        rng = random.Random(k * 7919 + n // life)
        x, y = rng.uniform(30, W - 30), rng.uniform(30, H - TICKER_H - 20)
        r = (16 + 14 * rng.random()) * math.sin(math.pi * (n % life + 0.5) / life)
        sparkle(d, x, y, r, rng.choice(SPARKLE_COLORS), rot=rng.random())


def locate(g):
    """Глобальный кадр -> (номер сцены, кадр внутри неё, идёт ли переход)."""
    k = max(i for i, s in enumerate(STARTS) if s <= g)
    f = g - STARTS[k]
    return k, f, (k > 0 and f < TRANS) or (k == len(SCENES) - 1 and f >= SCENES[k][1] - TRANS)


def compose(g):
    k, f, _ = locate(g)
    fn, dur, _ = SCENES[k]
    if k > 0 and f < TRANS:
        pfn, pdur, _ = SCENES[k - 1]
        img = transition(SCENES[k][2], pfn(f + pdur), fn(f), (f + 1) / (TRANS + 1))
    elif k == len(SCENES) - 1 and f >= dur - TRANS:
        i = f - (dur - TRANS)
        img = transition(SCENES[0][2], fn(f), SCENES[0][0](i - TRANS), (i + 1) / (TRANS + 1))
    else:
        img = fn(f)
    overlay(img, g)
    return img


# ---------------------------------------------------------------- запуск

_OUT = 480
_RAW = False


def _init(out_size, raw):
    global _OUT, _RAW
    _OUT, _RAW = out_size, raw


def _render(g):
    img = compose(g)
    return img.resize((_OUT, _OUT), LANCZOS), (img.tobytes() if _RAW else None)


def encode_gif(frames, path, colors, lossy):
    """Палитра общая на всю сцену: неподвижный фон тогда совпадает между кадрами
    точь-в-точь, и Pillow заменяет его прозрачностью. Потом, если есть, gifsicle."""
    groups = {}
    for g in range(len(frames)):
        k, _, trans = locate(g)
        groups.setdefault(("transition", g) if trans else ("scene", k), []).append(g)
    quantized = [None] * len(frames)
    for idx in groups.values():
        pick = idx[::max(1, len(idx) // 8)][:8]
        sample = Image.new("RGB", (frames[0].width, frames[0].height * len(pick)))
        for n, g in enumerate(pick):
            sample.paste(frames[g], (0, n * frames[0].height))
        pal = sample.quantize(colors, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        for g in idx:
            quantized[g] = frames[g].quantize(palette=pal, dither=Image.Dither.NONE)
    quantized[0].save(path, save_all=True, append_images=quantized[1:], duration=FRAME_MS, loop=0,
                      disposal=1, optimize=True)
    if lossy and shutil.which("gifsicle"):
        subprocess.run(["gifsicle", "--batch", "-w", "-O3", f"--lossy={lossy}", path], check=True)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--size", type=int, default=480, help="сторона квадрата гифки, px")
    ap.add_argument("--out", default=str(HERE / "roman_bday.gif"))
    ap.add_argument("--mp4", default=str(HERE / "roman_bday.mp4"), help="куда положить mp4 ('' — не делать)")
    ap.add_argument("--colors", type=int, default=128, help="цветов в палитре сцены (до 255)")
    ap.add_argument("--lossy", type=int, default=60, help="уровень gifsicle --lossy (0 — без потерь)")
    ap.add_argument("--face", help="фото лица именинника (по умолчанию face.jpg/face.png рядом со скриптом)")
    ap.add_argument("--sheet", help="вместо гифки сохранить контактный лист кадров (для отладки)")
    ap.add_argument("--step", type=int, default=6, help="шаг кадров для --sheet")
    ap.add_argument("--jobs", type=int, default=os.cpu_count() or 1)
    args = ap.parse_args()

    face = args.face or next((str(p) for p in (HERE / "face.jpg", HERE / "face.png") if p.exists()), None)
    if face:
        os.environ["ROMAN_FACE"] = os.path.abspath(face)

    if args.sheet:
        idx = list(range(0, TOTAL, args.step))
        cols, cell = 6, 320
        sheet = Image.new("RGB", (cols * cell, math.ceil(len(idx) / cols) * cell), "black")
        for n, g in enumerate(idx):
            sheet.paste(compose(g).resize((cell, cell), LANCZOS), ((n % cols) * cell, (n // cols) * cell))
        sheet.save(args.sheet)
        print(f"{args.sheet}: кадры {idx}")
        return

    ff = None
    if args.mp4:
        if shutil.which("ffmpeg"):
            ff = subprocess.Popen(
                ["ffmpeg", "-y", "-loglevel", "error", "-f", "rawvideo", "-pix_fmt", "rgb24", "-s", f"{W}x{H}",
                 "-framerate", str(1000 / FRAME_MS), "-i", "-", "-c:v", "libx264", "-preset", "slow", "-crf", "22",
                 "-pix_fmt", "yuv420p", "-movflags", "+faststart", args.mp4],
                stdin=subprocess.PIPE)
        else:
            print("ffmpeg не найден — mp4 пропускаю", file=sys.stderr)

    frames = []
    with Pool(args.jobs, initializer=_init, initargs=(args.size, ff is not None)) as pool:
        for n, (small, raw) in enumerate(pool.imap(_render, range(TOTAL), chunksize=2)):
            frames.append(small)
            if ff:
                ff.stdin.write(raw)
            print(f"\rкадр {n + 1}/{TOTAL}", end="", flush=True)
    print()
    if not shutil.which("gifsicle"):
        print("gifsicle не найден — гифка будет толще", file=sys.stderr)
    encode_gif(frames, args.out, min(255, args.colors), args.lossy)
    print(f"{args.out}: {TOTAL} кадров, {TOTAL * FRAME_MS / 1000:.1f} с, {os.path.getsize(args.out) / 1e6:.1f} МБ")
    if ff:
        ff.stdin.close()
        ff.wait()
        print(f"{args.mp4}: {os.path.getsize(args.mp4) / 1e6:.1f} МБ")


if __name__ == "__main__":
    main()
