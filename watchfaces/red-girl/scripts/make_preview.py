#!/usr/bin/env python3
"""Generate reproducible Red Girl launcher icon and watchface preview.

Input is the approved 200x228 watchface artwork already committed in resources.
Run: python3 -m pip install Pillow && python3 scripts/make_preview.py
"""
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "resources" / "images" / "red-girl-base.png"
OUT = ROOT / "resources" / "images"
PREVIEWS = ROOT / "previews"

ORANGE = "#FF5500"
MUTED = "#B8A39A"
BLACK = "#050303"
CARD = "#160604"
RED = "#FF1A0A"
BAR_BG = "#330503"

def font(size):
    for filename in (
        "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf",
        "/usr/share/fonts/truetype/liberation2/LiberationSans-Bold.ttf",
    ):
        if Path(filename).is_file():
            return ImageFont.truetype(filename, size)
    return ImageFont.load_default()

def label(draw, xy, text, max_width, size, color, embolden=False):
    # Fit actual measured glyphs; this is a gallery preview, not an exact SDK font.
    while size > 12:
        f = font(size)
        bbox = draw.textbbox((0, 0), text, font=f)
        if bbox[2] - bbox[0] <= max_width:
            break
        size -= 1
    draw.text(xy, text, font=f, fill=color,
              stroke_width=1 if embolden else 0, stroke_fill=color)

def main():
    base = Image.open(ART).convert("RGB")
    if base.size != (200, 228):
        raise ValueError(f"Unexpected Red Girl art size: {base.size}")
    im = base.copy()
    d = ImageDraw.Draw(im)

    # Match the native C renderer's background masks.
    for box, color in [
        ((4, 3, 94, 17), BLACK),
        ((5, 17, 105, 63), BLACK),
        ((7, 64, 78, 92), BLACK),
        ((31, 103, 70, 136), CARD),
        ((31, 143, 72, 176), CARD),
        ((29, 184, 86, 212), BLACK),
        ((118, 184, 179, 212), BLACK),
    ]:
        d.rectangle(box, fill=color)

    label(d, (5, 18), "12:48", 115, 28, ORANGE, embolden=True)
    label(d, (8, 54), "20 ОКТ", 76, 18, ORANGE)
    label(d, (8, 74), "ВТ", 88, 14, MUTED)
    label(d, (34, 104), "78", 43, 24, ORANGE)
    label(d, (34, 144), "24°", 45, 24, ORANGE)
    label(d, (31, 184), "78%", 58, 24, ORANGE)
    label(d, (120, 184), "63%", 61, 24, ORANGE)
    for x, width, percent, color in ((33, 55, 78, RED), (121, 60, 63, ORANGE)):
        d.rectangle((x, 215, x + width - 1, 218), fill=BAR_BG)
        d.rectangle((x, 215, x + width * percent // 100 - 1, 218), fill=color)

    PREVIEWS.mkdir(parents=True, exist_ok=True)
    im.save(PREVIEWS / "red-girl-cover.png", optimize=True)
    im.resize((400, 456), resample=Image.Resampling.NEAREST).save(
        PREVIEWS / "red-girl-cover-2x.png", optimize=True)

    # Authentic artwork crop for the watch launcher. Pebble requires <=25x25.
    # Keep crisp blocky pixels and a high-contrast rim at this small size.
    avatar = base.crop((101, 22, 196, 117)).resize(
        (23, 23), resample=Image.Resampling.NEAREST)
    icon = Image.new("RGB", (25, 25), BLACK)
    icon.paste(avatar, (1, 1))
    pix = ImageDraw.Draw(icon)
    pix.rectangle((0, 0, 24, 24), outline=ORANGE, width=1)
    icon.save(OUT / "red-girl-menu-icon.png", optimize=True)

    print("Generated 25x25 menu icon and 200x228 / 400x456 preview covers")

if __name__ == "__main__":
    main()
