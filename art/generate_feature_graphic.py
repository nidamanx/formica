# SPDX-License-Identifier: AGPL-3.0-or-later
"""Generates the store feature graphic (1024x500) in the five languages, and on request
the repository's social preview (1280x640, English).

Design "B2, scene" (owner's choice, 2026-11-24): the ant carries its groceries to the
anthill pantry, with the name and the slogan in the sky. The slogan is split at its comma
into two lines, so the longest language still fits.

Usage: python3 art/generate_feature_graphic.py [--social FILE]
Needs cairosvg and the Roboto font (Debian packages python3-cairosvg and fonts-roboto).
Outputs: fastlane/metadata/android/<locale>/images/featureGraphic.png; with --social, the
social preview at FILE (uploaded by hand in the GitHub settings, not kept in the repository).
"""
import argparse
import sys
from pathlib import Path

sys.dont_write_bytecode = True  # no __pycache__ in art/ when importing formica_art

import formica_art as art

ROOT = Path(__file__).resolve().parent.parent
FONT = "Roboto"
SLOGANS = {
    "en-US": "Your food at home, always in order.",
    "it-IT": "La dispensa di casa, sempre in ordine.",
    "fr-FR": "Le garde-manger de la maison, toujours en ordre.",
    "de-DE": "Der Vorrat zu Hause, immer in Ordnung.",
    "es-ES": "La despensa de casa, siempre en orden.",
}


def banner(width: int, height: int, slogan: str) -> str:
    p = art.SCENE
    k = height / 500
    scene_width = 560 * k
    scale = scene_width / art.SCENE_WIDTH
    scene_height = art.SCENE_HEIGHT * scale
    top = height - scene_height
    ground = top + art.SCENE_GROUND * scale
    text_x = scene_width + 40 * k
    first, second = (part.replace("&", "&amp;") for part in slogan.split(", ", 1))
    return f"""<svg xmlns="http://www.w3.org/2000/svg" width="{width}" height="{height}" viewBox="0 0 {width} {height}">
<rect width="{width}" height="{height}" fill="{p["bg"]}"/>
<circle cx="{width - 90 * k}" cy="{80 * k}" r="{44 * k}" fill="{p["sun"]}"/>
<rect y="{ground}" width="{width}" height="{height - ground}" fill="{p["ground"]}"/>
<svg x="{12 * k}" y="{top}" width="{scene_width}" height="{scene_height}" viewBox="0 0 {art.SCENE_WIDTH} {art.SCENE_HEIGHT}" overflow="visible">{art.scene()}</svg>
<text x="{text_x}" y="{200 * k}" font-family="{FONT}" font-size="{80 * k}" font-weight="500" fill="{p["title"]}">Formica</text>
<text font-family="{FONT}" font-size="{27 * k}" fill="{p["slogan"]}"><tspan x="{text_x + 3 * k}" y="{256 * k}">{first},</tspan><tspan x="{text_x + 3 * k}" y="{292 * k}">{second}</tspan></text>
</svg>"""


def main() -> int:
    parser = argparse.ArgumentParser(description="Store feature graphic and social preview")
    parser.add_argument("--social", type=Path, help="also write the social preview (PNG) here")
    args = parser.parse_args()
    try:
        import cairosvg
    except (ImportError, OSError) as e:
        sys.exit(f"cairosvg is needed: {e}")
    for locale, slogan in SLOGANS.items():
        out = ROOT / "fastlane/metadata/android" / locale / "images/featureGraphic.png"
        out.parent.mkdir(parents=True, exist_ok=True)
        cairosvg.svg2png(bytestring=banner(1024, 500, slogan).encode(), write_to=str(out))
        print(out.relative_to(ROOT))
    if args.social:
        cairosvg.svg2png(bytestring=banner(1280, 640, SLOGANS["en-US"]).encode(), write_to=str(args.social))
        print(args.social)
    return 0


if __name__ == "__main__":
    sys.exit(main())
