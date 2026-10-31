"""
Draws the Operator Console's frequencies window background
(src/main/resources/assets/create_construction_site/textures/gui/operator_console.png).

In the style of Create's own windows (Linked Controller): a brass title bar, a dark dithered
panel with slot pairs, a light bottom bar. Only the colours are taken from Create; the picture
is drawn here. Slot positions must match OperatorConsoleMenu.

Run: python tools/gui/generate_console_gui.py   (needs Pillow: pip install pillow)
"""
import os
import random
from PIL import Image

W, H = 226, 160
HEADER_BOTTOM = 14
BAR_TOP = 130
COLUMN, GROUP_GAP = 24, 11
TOP_ROW_X, TOP_ROW_Y = 14, 31
BOTTOM_ROW_X, BOTTOM_ROW_Y = (W - (4 * COLUMN + 18)) // 2, 87
CROSS, BONUS, LEVERS = 4, 4, 5

BLACK = (0, 0, 0, 255)
BRASS_LIGHT = (0xFF, 0xEB, 0x8C, 255)
BRASS = (0xE4, 0xB7, 0x63, 255)
BRASS_DARK = (0x59, 0x24, 0x24, 255)
PANEL_A = (0x2B, 0x2A, 0x30, 255)
PANEL_B = (0x38, 0x37, 0x3C, 255)
PANEL_LIGHT = (0xB8, 0xB8, 0xB8, 255)
PANEL_TOP = (0x90, 0x90, 0x90, 255)
PANEL_BOTTOM = (0x78, 0x78, 0x78, 255)
SEPARATOR = (0x4A, 0x49, 0x50, 255)
SLOT_DARK = (0x37, 0x37, 0x37, 255)
SLOT = (0x8B, 0x8B, 0x8B, 255)
SLOT_LIGHT = (0xFF, 0xFF, 0xFF, 255)
BAR_LIGHT = (0xFF, 0xFF, 0xFF, 255)
BAR = (0xC6, 0xC6, 0xC6, 255)
BAR_SHADOW = (0x55, 0x55, 0x55, 255)


def rect(img, x0, y0, x1, y1, colour):
    """Fills x0..x1, y0..y1 (inclusive)."""
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            img.putpixel((x, y), colour)


def slot(img, x, y):
    """A slot whose item area is x..x+15, y..y+15."""
    rect(img, x - 1, y - 1, x + 16, y - 1, SLOT_DARK)
    rect(img, x - 1, y - 1, x - 1, y + 16, SLOT_DARK)
    rect(img, x, y, x + 15, y + 15, SLOT)
    rect(img, x, y + 16, x + 16, y + 16, SLOT_LIGHT)
    rect(img, x + 16, y, x + 16, y + 16, SLOT_LIGHT)


def control_x(c):
    if c >= CROSS + BONUS:
        return BOTTOM_ROW_X + (c - CROSS - BONUS) * COLUMN
    return TOP_ROW_X + c * COLUMN + (GROUP_GAP if c >= CROSS else 0)


def control_y(c):
    return BOTTOM_ROW_Y if c >= CROSS + BONUS else TOP_ROW_Y


def main():
    img = Image.new("RGBA", (256, 256), (0, 0, 0, 0))
    rng = random.Random(7)

    # Title bar.
    rect(img, 0, 0, W - 1, HEADER_BOTTOM, BLACK)
    rect(img, 1, 1, W - 2, HEADER_BOTTOM - 2, BRASS)
    rect(img, 1, 1, W - 2, 1, BRASS_LIGHT)
    rect(img, 1, 1, 2, HEADER_BOTTOM - 1, BRASS_LIGHT)
    rect(img, 2, HEADER_BOTTOM - 1, W - 2, HEADER_BOTTOM - 1, BRASS_DARK)

    # Panel, one pixel in from the sides, dithered in 2x1 cells.
    top, bottom = HEADER_BOTTOM + 1, BAR_TOP - 2
    rect(img, 1, top, 1, bottom, BLACK)
    rect(img, W - 2, top, W - 2, bottom, BLACK)
    for y in range(top, bottom + 1):
        for x in range(2, W - 2, 2):
            colour = PANEL_A if rng.random() < 0.6 else PANEL_B
            rect(img, x, y, min(x + 1, W - 3), y, colour)
    rect(img, 2, top, 2, bottom, PANEL_LIGHT)
    rect(img, 2, top, W - 3, top, PANEL_TOP)
    rect(img, 2, bottom, W - 3, bottom, PANEL_BOTTOM)

    # Separators: between the cross and the bonus buttons, and between the two rows.
    gap_x = TOP_ROW_X + CROSS * COLUMN - 6 + GROUP_GAP // 2
    rect(img, gap_x, top + 4, gap_x, TOP_ROW_Y + 36 + 2, SEPARATOR)
    row_gap = (TOP_ROW_Y + 36 + BOTTOM_ROW_Y - 12) // 2
    rect(img, 8, row_gap, W - 9, row_gap, SEPARATOR)

    # Slot pairs.
    for c in range(CROSS + BONUS + LEVERS):
        slot(img, control_x(c), control_y(c))
        slot(img, control_x(c), control_y(c) + 18)

    # Bottom bar (the confirm button is drawn by the game on top of it).
    rect(img, 0, BAR_TOP - 1, W - 1, H - 1, BLACK)
    rect(img, 1, BAR_TOP, W - 2, H - 2, BAR)
    rect(img, 1, BAR_TOP, W - 2, BAR_TOP, BAR_LIGHT)
    rect(img, 1, BAR_TOP, 2, H - 3, BAR_LIGHT)
    rect(img, 1, H - 2, W - 2, H - 2, BAR_SHADOW)
    rect(img, W - 2, BAR_TOP + 1, W - 2, H - 2, BAR_SHADOW)

    here = os.path.dirname(os.path.abspath(__file__))
    out = os.path.join(here, "..", "..", "src", "main", "resources", "assets", "create_construction_site",
                       "textures", "gui", "operator_console.png")
    img.save(os.path.normpath(out))
    print("wrote", os.path.normpath(out))


if __name__ == "__main__":
    main()
