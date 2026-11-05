"""Builds the brush textures in app/src/main/assets/brush_textures from the owner's reference screenshots.

The screenshots live in reference/notewise, which is not in git. Each texture is a white picture whose alpha is
how much ink the reference had there, so it takes any brush color. Run: python tools/extract_brush_textures.py
Needs: pip install pillow numpy scipy
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


# ---- stamped textures: one picture for each particle of a brush, turned along the stroke ----
from scipy import ndimage  # noqa: E402

CELL = 160


def fit(alpha, cell=CELL, margin=6):
    """The picture's bounding box, scaled to fit a cell and centered in it."""
    rows = np.where(alpha.max(axis=1) > 0.05)[0]
    columns = np.where(alpha.max(axis=0) > 0.05)[0]
    alpha = alpha[rows[0]:rows[-1] + 1, columns[0]:columns[-1] + 1]
    scale = (cell - 2 * margin) / max(alpha.shape)
    image = Image.fromarray((alpha * 255).astype(np.uint8)).resize(
        (max(1, round(alpha.shape[1] * scale)), max(1, round(alpha.shape[0] * scale))), Image.LANCZOS)
    out = Image.new('L', (cell, cell), 0)
    out.paste(image, ((cell - image.width) // 2, (cell - image.height) // 2))
    return np.asarray(out).astype(np.float32) / 255


def save_cells(cells, name, columns):
    rows = (len(cells) + columns - 1) // columns
    sheet = Image.new('L', (columns * CELL, rows * CELL), 0)
    for i, cell in enumerate(cells):
        sheet.paste(Image.fromarray((cell * 255).astype(np.uint8)), ((i % columns) * CELL, (i // columns) * CELL))
    white = Image.new('RGBA', sheet.size, (255, 255, 255, 0))
    white.putalpha(sheet)
    white.save(os.path.join(OUT, name), optimize=True)
    print(name, white.size, len(cells), 'pictures')


def leaf_stamps():
    """The leaf sheet: the owner's maple and generic leaves drawn here. The reference stroke's own leaves are not used, as they have no clear shape."""
    return [maple_stamp()] + drawn_leaves()


def maple_stamp():
    """The owner's own maple silhouette (tools/sources/maple-leaf.png): black on white."""
    image = Image.open(os.path.join(ROOT, 'tools', 'sources', 'maple-leaf.png')).convert('L')
    return fit(1 - np.asarray(image).astype(np.float32) / 255)


def drawn_leaves():
    """Generic leaves drawn here: oak, birch, ginkgo, willow and linden, each with a midrib and veins."""
    from PIL import ImageDraw

    size = 640
    cx = size / 2
    top, bottom = 40, size - 100

    def render(outline_points, veins=True):
        image = Image.new('L', (size, size), 0)
        draw = ImageDraw.Draw(image)
        draw.polygon(outline_points, fill=255)
        draw.rectangle((cx - 6, bottom - 10, cx + 6, size - 20), fill=255)
        if veins:
            draw.line((cx, bottom, cx, top + (bottom - top) * 0.1), fill=150, width=7)
            for step in range(1, 5):
                y = bottom - (bottom - top) * (0.12 + step * 0.17)
                reach = (bottom - top) * 0.2
                draw.line((cx, y, cx - reach, y - reach * 0.75), fill=170, width=5)
                draw.line((cx, y, cx + reach, y - reach * 0.75), fill=170, width=5)
        return np.asarray(image).astype(np.float32) / 255

    def profile(width_at, steps=240, right=None):
        left_points, right_points = [], []
        for i in range(steps + 1):
            t = i / steps
            y = top + t * (bottom - top)
            left_points.append((cx - width_at(t) * size, y))
            right_points.append((cx + (right or width_at)(t) * size, y))
        return right_points + left_points[::-1]

    body = lambda t, power=1.0: np.sin(np.pi * t ** power)
    saw = lambda t: t - np.floor(t)
    leaves = []
    # Oak: long, with rounded lobes.
    leaves.append(render(profile(lambda t: 0.34 * body(t, 0.85) * (0.72 + 0.28 * abs(np.cos(t * np.pi * 3.5))))))
    # Birch: a pointed oval with a toothed edge.
    leaves.append(render(profile(lambda t: 0.32 * body(t, 0.75) * (0.9 + 0.1 * saw(t * 14)))))
    # Willow: long and narrow.
    leaves.append(render(profile(lambda t: 0.13 * body(t, 0.9))))
    # Linden: a heart.
    leaves.append(render(profile(lambda t: 0.42 * body(t, 0.6) * (1 - max(0.0, (t - 0.88) * 4)))))
    # Ginkgo: a fan with a notch in its rim.
    points = [(cx, bottom)]
    for i in range(161):
        r = i / 160
        angle = -np.pi / 2 + (r - 0.5) * np.pi * 0.92
        notch = 1 - 0.45 * max(0.0, 1 - abs(r - 0.5) * 16)
        reach = 0.5 * size * notch * (0.96 + 0.04 * np.cos(r * np.pi * 10))
        points.append((cx + np.cos(angle) * reach, bottom + np.sin(angle) * reach))
    leaves.append(render(points, veins=False))
    # Elm: an oval whose two sides differ, with a toothed edge.
    leaves.append(render(profile(lambda t: 0.34 * body(t, 0.8) * (0.9 + 0.1 * saw(t * 11)), right=lambda t: 0.26 * body(t, 0.7) * (0.9 + 0.1 * saw(t * 11 + 0.3)))))
    # Aspen: nearly round with a drawn-out tip.
    leaves.append(render(profile(lambda t: 0.40 * np.sqrt(np.maximum(0.0, body(t, 0.55))) * (t / 0.18 if t < 0.18 else 1.0))))
    # Beech: a smooth pointed oval.
    leaves.append(render(profile(lambda t: 0.27 * body(t, 0.7))))
    return [fit(leaf) for leaf in leaves]


def blot_stamp():
    return fit(darkness_alpha(crop('watercolor, oil and comic.jpg', (560, 165, 740, 335))), margin=4)


def grain_stamp():
    """A round patch of graphite grain, soft at its edge, from the middle of the pencil stroke."""
    alpha = darkness_alpha(crop('brushes samples.jpg', (900, 195, 1100, 310)), gamma=0.9)
    side = min(alpha.shape)
    alpha = alpha[:side, :side]
    yy, xx = np.mgrid[:side, :side]
    radius = np.hypot(yy - side / 2, xx - side / 2) / (side / 2)
    return fit((alpha * np.clip((1 - radius) / 0.2, 0, 1)) ** 0.6, margin=2)


def oil_stamp():
    """A slice of the oil stroke, one bristle-width tall and a few diamonds wide, faded at both ends so slices join."""
    alpha = np.clip((darkness_alpha(crop('watercolor, oil and comic.jpg', (980, 470, 1240, 690))) - 0.15) / 0.7, 0, 1) ** 1.2
    window = np.sin(np.linspace(0, np.pi, alpha.shape[1])) ** 0.8
    return alpha * window[None, :]


if __name__ == '__main__':
    save_cells(leaf_stamps(), 'leaf-stamps.png', 3)
    save_cells([blot_stamp()], 'blot-stamp.png', 1)
    save_cells([grain_stamp()], 'grain-stamp.png', 1)
    # The slice keeps its own shape: wider than tall, so it is saved as it is, not fitted to a square.
    oil = oil_stamp()
    white = Image.new('RGBA', (oil.shape[1], oil.shape[0]), (255, 255, 255, 0))
    white.putalpha(Image.fromarray((oil * 255).astype(np.uint8)))
    white.save(os.path.join(OUT, 'oil-stamp.png'), optimize=True)
    print('oil-stamp.png', white.size)
