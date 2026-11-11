#!/usr/bin/env python3
"""Regenerates art/palettes/: the Expeditions palette as a GIMP/Aseprite
.gpl file plus a swatch strip PNG. Import the .gpl in Aseprite
(Sprite > Palette > Import) or GIMP; sample the PNG anywhere else.
"""
import os

OUT = os.path.join(os.path.dirname(__file__), "..", "art/palettes")

RAMPS = {
    "Bone":    ["#3A3026", "#6E5E4A", "#A08C6E", "#CEBA96", "#ECDCB8", "#FAF2D8"],
    "Flesh":   ["#483E3A", "#766862", "#A2948A", "#CABEB0", "#E6DECE", "#F6F0E2"],
    "Ember":   ["#22120A", "#7A240E", "#D9480F", "#F08A20", "#FFC43D", "#FFE97A"],
    "Leather": ["#2A1A10", "#50321E", "#7A5030", "#9E6E42", "#C4925C", "#DEB076"],
    "Metal":   ["#26221E", "#4A443C", "#6E665A", "#948C7E", "#BAB2A2", "#DCD6C6"],
    "Outline": ["#261E18"],
}
ORDER = ["Bone", "Flesh", "Ember", "Leather", "Metal", "Outline"]


def hex_rgb(h):
    return tuple(int(h[i:i + 2], 16) for i in (1, 3, 5))


def main():
    os.makedirs(OUT, exist_ok=True)
    lines = ["GIMP Palette", "Name: Expeditions",
             "Columns: 6", "# bone=flesh=ember=leather=metal ramps, dark outline"]
    cells = []
    for name in ORDER:
        for i, h in enumerate(RAMPS[name]):
            r, g, b = hex_rgb(h)
            lines.append(f"{r:3d} {g:3d} {b:3d}\t{name} {i}")
            cells.append(hex_rgb(h))
    with open(os.path.join(OUT, "expeditions.gpl"), "w", encoding="utf-8") as f:
        f.write("\n".join(lines) + "\n")

    try:
        from PIL import Image
        img = Image.new("RGB", (6 * 16, len(ORDER) * 16))
        for row, name in enumerate(ORDER):
            for col, h in enumerate(RAMPS[name]):
                for y in range(16):
                    for x in range(16):
                        img.putpixel((col * 16 + x, row * 16 + y), hex_rgb(h))
        img.save(os.path.join(OUT, "expeditions.png"))
        print("wrote", os.path.join(OUT, "expeditions.gpl"), "+ expeditions.png")
    except ImportError:
        print("wrote expeditions.gpl (install Pillow for the swatch PNG)")


if __name__ == "__main__":
    main()
