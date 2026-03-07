from __future__ import annotations

from dataclasses import dataclass
from pathlib import Path
from math import cos, sin, radians, hypot, pi
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / 'artwork' / 'app-icon'
APP_RES = ROOT / 'app' / 'src' / 'main' / 'res'

CANVAS = 1024
CENTER = CANVAS / 2.0
R_SCALE = 0.89
BACKGROUND = '#030303'
LEAF_MAIN = '#44D864'
LEAF_DEEP = '#199847'
SAMPLES = 140

LEGACY_SIZES = {
    'mdpi': 48,
    'hdpi': 72,
    'xhdpi': 96,
    'xxhdpi': 144,
    'xxxhdpi': 192,
}

FOREGROUND_SIZES = {
    'mdpi': 108,
    'hdpi': 162,
    'xhdpi': 216,
    'xxhdpi': 324,
    'xxxhdpi': 432,
}


@dataclass(frozen=True)
class Point:
    x: float
    y: float


def polar(radius: float, angle_deg: float) -> Point:
    radius *= R_SCALE
    angle = radians(angle_deg)
    return Point(CENTER + radius * cos(angle), CENTER + radius * sin(angle))


def rotate_point(point: Point, angle_deg: float) -> Point:
    angle = radians(angle_deg)
    dx = point.x - CENTER
    dy = point.y - CENTER
    return Point(
        CENTER + dx * cos(angle) - dy * sin(angle),
        CENTER + dx * sin(angle) + dy * cos(angle),
    )


def normalize(x: float, y: float) -> tuple[float, float]:
    length = hypot(x, y)
    if length == 0:
        return (0.0, 0.0)
    return (x / length, y / length)


def spiral_centerline(s: float) -> Point:
    angle = -56 + 82 * (s ** 0.96)
    radius = 130 + 205 * (s ** 0.98)
    return polar(radius, angle)


def leaf_width(s: float) -> float:
    ribbon = sin(pi * s) ** 0.84
    bias = 0.78 + 0.22 * s
    return (22 + 88 * ribbon * bias) * R_SCALE


def leaf_geometry() -> tuple[str, list[Point]]:
    left: list[Point] = []
    right: list[Point] = []
    eps = 1.0 / (SAMPLES - 1)

    for index in range(SAMPLES):
        s = index / (SAMPLES - 1)
        p = spiral_centerline(s)
        prev = spiral_centerline(max(0.0, s - eps))
        nxt = spiral_centerline(min(1.0, s + eps))
        tx = nxt.x - prev.x
        ty = nxt.y - prev.y
        nx, ny = normalize(-ty, tx)
        width = leaf_width(s)
        left.append(Point(p.x + nx * width * 0.5, p.y + ny * width * 0.5))
        right.append(Point(p.x - nx * width * 0.5, p.y - ny * width * 0.5))

    polygon = left + list(reversed(right))
    path_data = 'M ' + ' L '.join(f'{p.x:.3f},{p.y:.3f}' for p in polygon) + ' Z'
    return path_data, polygon


def hex_to_rgb(value: str) -> tuple[int, int, int]:
    value = value.lstrip('#')
    return tuple(int(value[i:i+2], 16) for i in (0, 2, 4))


def lerp(a: int, b: int, t: float) -> int:
    return round(a + (b - a) * t)


def build_gradient(size: int) -> Image.Image:
    main = hex_to_rgb(LEAF_MAIN)
    deep = hex_to_rgb(LEAF_DEEP)
    img = Image.new('RGBA', (size, size))
    px = img.load()
    scale = size / CANVAS
    center = CENTER * scale
    outer = 342 * R_SCALE * scale
    inner = 108 * R_SCALE * scale
    for y in range(size):
        dy = y - center
        for x in range(size):
            dx = x - center
            dist = hypot(dx, dy)
            t = (dist - inner) / max(outer - inner, 1)
            t = 0.0 if t < 0.0 else 1.0 if t > 1.0 else t
            eased = t * t * (3 - 2 * t)
            px[x, y] = (
                lerp(deep[0], main[0], eased),
                lerp(deep[1], main[1], eased),
                lerp(deep[2], main[2], eased),
                255,
            )
    return img


def render_symbol(size: int, transparent: bool) -> Image.Image:
    scale = size / CANVAS
    _, base_points = leaf_geometry()
    symbol_mask = Image.new('L', (size, size), 0)
    draw = ImageDraw.Draw(symbol_mask)

    for index in range(6):
        angle = -90 + index * 60
        polygon = []
        for point in base_points:
            rotated = rotate_point(point, angle)
            polygon.append((rotated.x * scale, rotated.y * scale))
        draw.polygon(polygon, fill=255)

    gradient = build_gradient(size)
    leaf_layer = Image.new('RGBA', (size, size), (0, 0, 0, 0))
    leaf_layer.paste(gradient, (0, 0), mask=symbol_mask)

    if transparent:
        return leaf_layer

    base = Image.new('RGBA', (size, size), BACKGROUND)
    base.alpha_composite(leaf_layer)
    return base


def build_svg(path_data: str) -> str:
    uses = []
    for index in range(6):
        angle = -90 + index * 60
        uses.append(
            f'    <use href="#leaf" transform="rotate({angle:.0f} {CENTER:.1f} {CENTER:.1f})" />'
        )
    return f'''<svg xmlns="http://www.w3.org/2000/svg" width="1024" height="1024" viewBox="0 0 1024 1024" fill="none">
  <defs>
    <radialGradient id="leafGradient" cx="50%" cy="50%" r="44%">
      <stop offset="0%" stop-color="{LEAF_DEEP}" />
      <stop offset="100%" stop-color="{LEAF_MAIN}" />
    </radialGradient>
    <path id="leaf" d="{path_data}" fill="url(#leafGradient)" />
  </defs>
  <g>
{chr(10).join(uses)}
  </g>
</svg>
'''


def save_png(img: Image.Image, path: Path, size: int | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if size is not None:
        img = img.resize((size, size), Image.Resampling.LANCZOS)
    img.save(path)


def main() -> None:
    ART_DIR.mkdir(parents=True, exist_ok=True)
    path_data, _ = leaf_geometry()

    (ART_DIR / 'icon.svg').write_text(build_svg(path_data), encoding='utf-8')

    high_res_foreground = render_symbol(1024, transparent=True)
    high_res_black = render_symbol(1024, transparent=False)

    save_png(high_res_foreground, ART_DIR / 'icon_foreground.png')
    save_png(high_res_black, ART_DIR / 'icon_black_bg.png')
    save_png(high_res_black, ART_DIR / 'ic_launcher-playstore.png', 512)

    for density, size in LEGACY_SIZES.items():
        mipmap_dir = APP_RES / f'mipmap-{density}'
        save_png(high_res_black, mipmap_dir / 'ic_launcher.png', size)
        save_png(high_res_black, mipmap_dir / 'ic_launcher_round.png', size)

    for density, size in FOREGROUND_SIZES.items():
        mipmap_dir = APP_RES / f'mipmap-{density}'
        save_png(high_res_foreground, mipmap_dir / 'ic_launcher_foreground.png', size)


if __name__ == '__main__':
    main()
