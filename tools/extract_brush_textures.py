"""Builds the brush textures in app/src/main/assets/brush_textures from the owner's reference screenshots.

The screenshots live in reference/notewise, which is not in git. Each texture is a white picture whose alpha is
how much ink the reference had there, so it takes any brush color. Run: python tools/extract_brush_textures.py
"""
import os
import sys

import numpy as np
from PIL import Image

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
REF = os.path.join(ROOT, 'reference', 'notewise')
OUT = os.path.join(ROOT, 'app', 'src', 'main', 'assets', 'brush_textures')


def crop(name, box):
    return Image.open(os.path.join(REF, name)).convert('RGB').crop(box)


def darkness_alpha(image, background=None, gamma=1.0):
    """Alpha from how far each pixel is from the paper: black on white, or any color on cream."""
    pixels = np.asarray(image).astype(np.float32)
    if background is None:
        background = np.percentile(pixels.reshape(-1, 3), 97, axis=0)
    distance = np.sqrt(((pixels - background) ** 2).sum(axis=2))
    alpha = distance / max(distance.max(), 1e-3)
    return np.clip(alpha, 0, 1) ** gamma


def clean(alpha, floor):
    """Drops faint marks, such as the dots of the page grid, and stretches the rest back to full."""
    return np.clip((alpha - floor) / (1 - floor), 0, 1)


def save(alpha, name, width):
    height = max(1, round(alpha.shape[0] * width / alpha.shape[1]))
    image = Image.fromarray((alpha * 255).astype(np.uint8)).resize((width, height), Image.LANCZOS)
    white = Image.new('RGBA', image.size, (255, 255, 255, 0))
    white.putalpha(image)
    os.makedirs(OUT, exist_ok=True)
    white.save(os.path.join(OUT, name), optimize=True)
    print(name, white.size)


REGIONS = {
    # name: (file, box, background or None, gamma, output width)
    'pencil': ('brushes samples.jpg', (520, 215, 1620, 295), None, 0.9, 1024),
    'wash': ('watercolor, oil and comic.jpg', (900, 175, 1850, 290), None, 1.0, 1024),
    'leaves': ('brushes samples.jpg', (430, 2200, 1840, 2530), None, 1.0, 1024),
}

if __name__ == '__main__':
    only = sys.argv[1:] or list(REGIONS)
    for key in only:
        file, box, background, gamma, width = REGIONS[key]
        alpha = darkness_alpha(crop(file, box), background, gamma)
        save(clean(alpha, 0.22) if key == 'leaves' else alpha, key + '.png', width)
