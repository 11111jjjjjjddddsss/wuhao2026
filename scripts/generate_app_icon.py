from __future__ import annotations

import base64
import io
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parents[1]
ART_DIR = ROOT / "artwork" / "app-icon"
APP_RES = ROOT / "app" / "src" / "main" / "res"
REFERENCE = ART_DIR / "reference.png"

CANVAS = 1024
BACKGROUND = "#030303"
ALPHA_THRESHOLD = 18
FOREGROUND_TARGET = 760

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


def alpha_bbox(img: Image.Image) -> tuple[int, int, int, int]:
    px = img.load()
    xs: list[int] = []
    ys: list[int] = []
    for y in range(img.height):
        for x in range(img.width):
            r, g, b, a = px[x, y]
            if a and max(r, g, b) > ALPHA_THRESHOLD:
                xs.append(x)
                ys.append(y)
    if not xs:
        raise RuntimeError("reference icon is empty")
    return min(xs), min(ys), max(xs) + 1, max(ys) + 1


def build_black_bg(reference: Image.Image) -> Image.Image:
    if reference.size != (CANVAS, CANVAS):
        reference = reference.resize((CANVAS, CANVAS), Image.Resampling.LANCZOS)
    return reference


def build_foreground(reference: Image.Image) -> Image.Image:
    left, top, right, bottom = alpha_bbox(reference)
    cropped = reference.crop((left, top, right, bottom))

    px = cropped.load()
    for y in range(cropped.height):
        for x in range(cropped.width):
            r, g, b, a = px[x, y]
            if max(r, g, b) <= ALPHA_THRESHOLD:
                px[x, y] = (0, 0, 0, 0)

    canvas = Image.new("RGBA", (CANVAS, CANVAS), (0, 0, 0, 0))
    scale = min(FOREGROUND_TARGET / cropped.width, FOREGROUND_TARGET / cropped.height)
    resized = cropped.resize(
        (round(cropped.width * scale), round(cropped.height * scale)),
        Image.Resampling.LANCZOS,
    )
    offset = ((CANVAS - resized.width) // 2, (CANVAS - resized.height) // 2)
    canvas.alpha_composite(resized, offset)
    return canvas


def build_svg_from_png(foreground: Image.Image) -> str:
    buffer = io.BytesIO()
    foreground.save(buffer, format="PNG")
    encoded = base64.b64encode(buffer.getvalue()).decode("ascii")
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{CANVAS}" height="{CANVAS}" viewBox="0 0 {CANVAS} {CANVAS}">
  <defs>
    <style>
      :root {{
        --icon-bg: {BACKGROUND};
      }}
    </style>
  </defs>
  <image width="{CANVAS}" height="{CANVAS}" href="data:image/png;base64,{encoded}" />
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
    black_bg = build_black_bg(reference)
    foreground = build_foreground(reference)

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
