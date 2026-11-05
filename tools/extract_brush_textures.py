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
    """Up to eight separate, whole leaves cut out of the reference stroke, one per cell. Clumps of overlapping leaves are skipped."""
    from scipy.spatial import ConvexHull

    image = crop('brushes samples.jpg', (340, 2190, 1910, 2540))
    alpha = clean(darkness_alpha(image), 0.22)
    labels, count = ndimage.label(alpha > 0.35)
    slices = ndimage.find_objects(labels)
    found = []
    for index in range(1, count + 1):
        box = slices[index - 1]
        if box[0].start == 0 or box[1].start == 0 or box[0].stop >= alpha.shape[0] or box[1].stop >= alpha.shape[1]:
            continue
        mask = ndimage.binary_fill_holes(labels[box] == index)
        area = mask.sum()
        if not 1800 < area < 9000:
            continue
        points = np.argwhere(mask)
        solidity = area / ConvexHull(points).volume
        if solidity < 0.86:
            continue
        found.append((area, alpha[box] * ndimage.binary_dilation(mask, iterations=1)))
    found.sort(key=lambda item: -item[0])
    step = max(1, len(found) // 8)
    print(len(found), 'whole leaves found')
    # The reference leaves are slightly see-through; the brush's own opacity sets how much shows, so firm them up.
    leaves = [fit(leaf ** 0.45) for _, leaf in found[::step][:8]]
    # Two of these are pairs of leaves that overlap in the reference, so they are left out.
    return [leaf for i, leaf in enumerate(leaves) if i not in (1, 2)]


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


if __name__ == '__main__':
    save_cells(leaf_stamps(), 'leaf-stamps.png', 3)
    save_cells([blot_stamp()], 'blot-stamp.png', 1)
    save_cells([grain_stamp()], 'grain-stamp.png', 1)
