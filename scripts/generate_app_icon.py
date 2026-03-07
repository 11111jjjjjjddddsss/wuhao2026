from __future__ import annotations

import base64
import io
from collections import deque
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "artwork" / "app-icon"
APP_RES = ROOT / "app" / "src" / "main" / "res"
REFERENCE = ART_DIR / "reference.png"

CANVAS = 1024
CENTER = CANVAS / 2
BACKGROUND = "#030303"
ALPHA_THRESHOLD = 18
GREEN_THRESHOLD = 60
LEAF_SEED = (650, 330)
SYMBOL_SCALE = 0.86
GREEN_BRIGHTNESS = 1.1
GREEN_LIFT = (6, 14, 2)

LEGACY_SIZES = {
    "mdpi": 48,
    "hdpi": 72,
    "xhdpi": 96,
    "xxhdpi": 144,
    "xxxhdpi": 192,
}

FOREGROUND_SIZES = {
    "mdpi": 108,
    "hdpi": 162,
    "xhdpi": 216,
    "xxhdpi": 324,
    "xxxhdpi": 432,
}


def load_reference() -> Image.Image:
    return Image.open(REFERENCE).convert("RGBA")


def extract_leaf_canvas(reference: Image.Image) -> Image.Image:
    px = reference.load()
    sx, sy = LEAF_SEED
    sr, sg, sb, sa = px[sx, sy]
    if not sa or sg < GREEN_THRESHOLD or sg < sr or sg < sb:
        raise RuntimeError("leaf seed is outside the reference symbol")

    queue = deque([(sx, sy)])
    seen = {(sx, sy)}
    component: list[tuple[int, int]] = []
    while queue:
        x0, y0 = queue.popleft()
        component.append((x0, y0))
        for nx in range(max(0, x0 - 1), min(CANVAS, x0 + 2)):
            for ny in range(max(0, y0 - 1), min(CANVAS, y0 + 2)):
                if (nx, ny) in seen:
                    continue
                r, g, b, a = px[nx, ny]
                if a and g >= GREEN_THRESHOLD and g >= r and g >= b:
                    seen.add((nx, ny))
                    queue.append((nx, ny))

    if not component:
        raise RuntimeError("failed to isolate mother leaf")

    leaf = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    out = leaf.load()
    for x, y in component:
        out[x, y] = px[x, y]
    return leaf


def scale_about_center(img: Image.Image, scale: float) -> Image.Image:
    if scale == 1.0:
        return img
    scaled_size = max(1, round(CANVAS * scale))
    resized = img.resize((scaled_size, scaled_size), Image.Resampling.LANCZOS)
    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    offset = ((CANVAS - scaled_size) // 2, (CANVAS - scaled_size) // 2)
    canvas.alpha_composite(resized, offset)
    return canvas


def build_symbol(leaf_canvas: Image.Image) -> Image.Image:
    leaf_canvas = scale_about_center(leaf_canvas, SYMBOL_SCALE)
    symbol = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    for index in range(6):
        rotated = leaf_canvas.rotate(index * 60, resample=Image.Resampling.BICUBIC, center=(CENTER, CENTER))
        symbol.alpha_composite(rotated)
    return symbol


def trim_black(img: Image.Image) -> Image.Image:
    rgba = img.copy()
    px = rgba.load()
    for y in range(rgba.height):
        for x in range(rgba.width):
            r, g, b, a = px[x, y]
            if a and max(r, g, b) <= ALPHA_THRESHOLD:
                px[x, y] = (0, 0, 0, 0)
    return rgba


def brighten_greens(img: Image.Image) -> Image.Image:
    out = img.copy()
    px = out.load()
    lift_r, lift_g, lift_b = GREEN_LIFT
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            px[x, y] = (
                min(255, round(r * GREEN_BRIGHTNESS) + lift_r),
                min(255, round(g * GREEN_BRIGHTNESS) + lift_g),
                min(255, round(b * GREEN_BRIGHTNESS) + lift_b),
                a,
            )
    return out


def build_svg_from_png(foreground: Image.Image) -> str:
    buffer = io.BytesIO()
    foreground.save(buffer, format="PNG")
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"""<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"{CANVAS}\" height=\"{CANVAS}\" viewBox=\"0 0 {CANVAS} {CANVAS}\">
  <defs>
    <style>
      :root {{
        --icon-bg: {BACKGROUND};
      }}
    </style>
  </defs>
  <image width=\"{CANVAS}\" height=\"{CANVAS}\" href=\"data:image/png;base64,{encoded}\" />
</svg>
"""


def save_png(img: Image.Image, path: Path, size: int | None = None) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    if size is not None:
        img = img.resize((size, size), Image.Resampling.LANCZOS)
    img.save(path)


def main() -> None:
    ART_DIR.mkdir(parents=True, exist_ok=True)
    reference = load_reference()
    mother_leaf = extract_leaf_canvas(reference)
    foreground = brighten_greens(trim_black(build_symbol(mother_leaf)))
    black_bg = Image.new("RGBA", (CANVAS, CANVAS), BACKGROUND)
    black_bg.alpha_composite(foreground)

    (ART_DIR / "icon.svg").write_text(build_svg_from_png(foreground), encoding="utf-8")
    save_png(foreground, ART_DIR / "icon_foreground.png")
    save_png(black_bg, ART_DIR / "icon_black_bg.png")
    save_png(black_bg, ART_DIR / "ic_launcher-playstore.png", 512)

    for density, size in LEGACY_SIZES.items():
        mipmap_dir = APP_RES / f"mipmap-{density}"
        save_png(black_bg, mipmap_dir / "ic_launcher.png", size)
        save_png(black_bg, mipmap_dir / "ic_launcher_round.png", size)

    for density, size in FOREGROUND_SIZES.items():
        mipmap_dir = APP_RES / f"mipmap-{density}"
        save_png(foreground, mipmap_dir / "ic_launcher_foreground.png", size)


if __name__ == "__main__":
    main()
