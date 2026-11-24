# SPDX-License-Identifier: AGPL-3.0-or-later
"""The Formica ant, drawn once and shared by the icon and the store graphics.

Design chosen by the owner on 2026-11-24: a cheerful honey-coloured ant that walks from
right to left, toward the viewer, with an open box of groceries on its back. Debug builds
("Formica-D") use the same drawing in draft greys with a "D" badge.

Coordinates use the 108x108 grid of Android adaptive icons; launchers show its centre 72x72
and may cut it to a circle of radius 33 around (54, 54), the safe zone.
"""

INK, PAPER = "#000000", "#FFFFFF"

BASE = dict(line="#4A2A1C", box="#F0CF98", box2="#DDAE6E", tape="#FFF4DC", eye="#FFFFFF",
            pupil="#2A1A12", cheek="#FF8F8F", tongue="#F2727A", mouth="#5A2A22", bread="#E3A857",
            bread2="#C9853A", leaf="#5FAE5A", apple="#E5534B")
RELEASE = dict(BASE, bg="#BFE3F4", body="#E0A042", hi="#F7CC85")
DEBUG = dict(BASE, bg="#4A4A55", body="#B9B9C2", hi="#D8D8DF", line="#2A2A31", box="#E2E2E8",
             box2="#C4C4CC", tape="#F4F4F7", cheek="#9A9AA4", tongue="#8E8E98", mouth="#3A3A42",
             bread="#CFCFD6", bread2="#A9A9B2", leaf="#9EA3A0", apple="#8F8F98", pupil="#22222A")
BADGE = dict(fill="#FFD15C", letter="#2A2A31")

# Monochrome (themed icons): the launcher paints every opaque pixel in one colour, so the
# drawing is reduced to ink, and the details that must stay visible become holes (paper)
MONO = {key: INK for key in RELEASE}
MONO.update(bg=PAPER, eye=PAPER, pupil=PAPER, box=PAPER, tape=PAPER, tongue=PAPER, bread=PAPER,
            mouth=PAPER)
MONO_BADGE = dict(fill=INK, letter=PAPER, ring=PAPER)

# Whole ant scaled into the safe zone, slightly below the centre (the antennae go up)
ICON_TRANSFORM = "translate(54 57) scale(0.78) translate(-54 -54)"


def _stroke(p, d, width, colour=None, opacity=None):
    extra = f' opacity="{opacity}"' if opacity else ""
    return (f'<path d="{d}" fill="none" stroke="{colour or p["line"]}" stroke-width="{width}" '
            f'stroke-linecap="round" stroke-linejoin="round"{extra}/>')


def _leg(p, points, width=2.2, back=False):
    d = "M" + " L".join(f"{x},{y}" for x, y in points)
    return _stroke(p, d, width, opacity=".45" if back else None)


def _eye(p, x, y):
    """Round shiny eye without white, two highlights, a thin raised brow."""
    return (f'<ellipse cx="{x}" cy="{y + 0.4}" rx="2.3" ry="2.9" fill="{p["pupil"]}"/>'
            f'<circle cx="{x + 0.7}" cy="{y - 0.6}" r="0.95" fill="{p["eye"]}"/>'
            f'<circle cx="{x - 0.6}" cy="{y + 1.6}" r="0.45" fill="{p["eye"]}"/>'
            + _stroke(p, f"M{x - 2.4},{y - 3.8} Q{x},{y - 5} {x + 2.4},{y - 3.8}", 0.9))


def ant(p):
    """The ant, drawn facing right and mirrored: it walks to the left, toward the reader."""
    s = []
    line = p["line"]
    s.append(_leg(p, [(52, 61), (47, 69), (45, 76)], 1.8, back=True))
    s.append(_leg(p, [(57, 62), (61, 69), (62, 76)], 1.8, back=True))
    s.append(f'<ellipse cx="37" cy="61" rx="12.5" ry="9.5" transform="rotate(-12 37 61)" '
             f'fill="{p["body"]}" stroke="{line}" stroke-width="1.5"/>')
    s.append(f'<ellipse cx="33" cy="57" rx="5" ry="2.6" transform="rotate(-20 33 57)" fill="{p["hi"]}"/>')
    s.append(f'<ellipse cx="54" cy="60" rx="7" ry="5.5" fill="{p["body"]}" stroke="{line}" stroke-width="1.5"/>')
    s.append(_leg(p, [(50, 63), (43, 70), (39, 77)]))
    s.append(_leg(p, [(55, 64), (54, 71), (56, 77)]))
    # Front leg lifted: a skipping step
    s.append(_leg(p, [(59, 62), (66, 66), (72, 63)]))
    # Open box of groceries on its back: baguette, apple, carrot leaves
    s.append('<g transform="rotate(-7 39 42)">')
    s.append(f'<rect x="27" y="18" width="7" height="20" rx="3.5" transform="rotate(-28 30.5 28)" '
             f'fill="{p["bread"]}" stroke="{line}" stroke-width="1.1"/>')
    for y in (22, 26, 30):
        s.append(f'<g transform="rotate(-28 30.5 28)">'
                 + _stroke(p, f"M28.6,{y} L32.2,{y - 1.2}", 1, p["bread2"]) + '</g>')
    s.append(f'<circle cx="42" cy="30" r="4.6" fill="{p["apple"]}" stroke="{line}" stroke-width="1.1"/>')
    s.append(_stroke(p, "M42,25.6 Q43.8,23 46,23.6", 1.6, p["leaf"]))
    for dx, dy in ((-3, -9), (0, -10.5), (3, -9)):
        s.append(_stroke(p, f"M49,30 Q{49 + dx * 0.3},{30 + dy * 0.6} {49 + dx},{30 + dy}", 2, p["leaf"]))
    s.append(f'<rect x="25" y="33" width="29" height="19" rx="1.6" fill="{p["box"]}" stroke="{line}" stroke-width="1.5"/>')
    s.append(f'<path d="M25,33 L20,29 L24,27.5 L29,33 Z" fill="{p["box2"]}" stroke="{line}" '
             f'stroke-width="1.1" stroke-linejoin="round"/>')
    s.append(f'<path d="M54,33 L59,29 L55,27.5 L50,33 Z" fill="{p["box2"]}" stroke="{line}" '
             f'stroke-width="1.1" stroke-linejoin="round"/>')
    s.append(f'<rect x="36.9" y="33" width="5.2" height="19" fill="{p["tape"]}" stroke="{line}" stroke-width="0.8"/>')
    s.append('</g>')
    s.append(_leg(p, [(58, 57), (60, 49), (54, 44)]))
    # Head, face turned to the viewer
    s.append(_stroke(p, "M64,40 Q61,32 61,26", 1.8))
    s.append(f'<circle cx="61" cy="26" r="2.3" fill="{p["body"]}" stroke="{line}" stroke-width="1.1"/>')
    s.append(_stroke(p, "M73,39 Q77,30 83,29", 1.8))
    s.append(f'<circle cx="83" cy="29" r="2.3" fill="{p["body"]}" stroke="{line}" stroke-width="1.1"/>')
    s.append(f'<circle cx="70" cy="50" r="12" fill="{p["body"]}" stroke="{line}" stroke-width="1.5"/>')
    s.append(f'<ellipse cx="70" cy="40.2" rx="3.4" ry="1.3" fill="{p["hi"]}"/>')
    s.append(_eye(p, 66.8, 47.2))
    s.append(_eye(p, 74.6, 47.2))
    s.append(f'<circle cx="62.8" cy="54" r="2" fill="{p["cheek"]}" opacity=".85"/>')
    s.append(f'<circle cx="78.6" cy="54" r="2" fill="{p["cheek"]}" opacity=".85"/>')
    s.append(f'<path d="M65.4,53.4 Q70.7,61.5 76,53.4 Z" fill="{p["mouth"]}" stroke="{line}" '
             f'stroke-width="1" stroke-linejoin="round"/>')
    s.append(f'<path d="M67.6,57.2 Q70.7,59.8 73.8,57.2 Q70.7,55.6 67.6,57.2 Z" fill="{p["tongue"]}"/>')
    return f'<g transform="translate(108 0) scale(-1 1)">{"".join(s)}</g>'


def badge(fill, letter, ring, cx=71, cy=71, r=8.5):
    """Round "D" badge of debug builds, inside the safe zone, cut out of the ant by a ring."""
    d = (f"M{cx - 2.9},{cy - 4.3} L{cx - 0.6},{cy - 4.3} Q{cx + 3.7},{cy - 4.3} {cx + 3.7},{cy} "
         f"Q{cx + 3.7},{cy + 4.3} {cx - 0.6},{cy + 4.3} L{cx - 2.9},{cy + 4.3} Z")
    return (f'<circle cx="{cx}" cy="{cy}" r="{r + 1.6}" fill="{ring}"/>'
            f'<circle cx="{cx}" cy="{cy}" r="{r}" fill="{fill}"/>'
            f'<path d="{d}" fill="none" stroke="{letter}" stroke-width="2.1" stroke-linejoin="round"/>')


def foreground(p, debug=False, badge_colours=None):
    """Adaptive-icon foreground (no background), as the inner SVG of a 108x108 viewBox."""
    body = f'<g transform="{ICON_TRANSFORM}">{ant(p)}</g>'
    if debug:
        colours = badge_colours or dict(BADGE, ring=p["bg"])
        body += badge(**colours)
    return body


def svg(inner, background=None, view_box="0 0 108 108", size=None):
    size_attr = f' width="{size}" height="{size}"' if size else ""
    back = f'<rect width="108" height="108" fill="{background}"/>' if background else ""
    return (f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="{view_box}"{size_attr}>'
            f'{back}{inner}</svg>')
