#!/usr/bin/env python3
"""Avragram launcher icons: an original paper-plane glyph in creative scenes (108x108 adaptive canvas).
Writes TMessagesProj/appicons/avragram/<id>/ic_launcher_{background,foreground,monochrome}.svg + icons.json;
then run ./gradlew :TMessagesProj:generateAppIcons."""
import json, math, os, random, shutil

ROOT = os.path.join(os.path.dirname(__file__), '..', 'TMessagesProj', 'appicons')

PLANE = ("M29.6 53.2 L76.6 33.9 C79.0 32.9 80.6 34.4 80.1 36.8 L72.3 73.1 C71.8 75.4 69.8 76.1 67.9 74.8 "
         "L56.6 66.6 L50.6 72.4 C49.3 73.7 47.6 73.1 47.5 71.2 L46.9 62.0 L67.6 43.4 L43.4 58.3 "
         "L31.0 55.6 C28.4 55.0 28.0 53.9 29.6 53.2 Z")
FOLD = "M46.9 62.0 L47.5 71.2 C47.6 73.1 49.3 73.7 50.6 72.4 L56.6 66.6 Z"
# polygonal plane for origami / pixel art (same silhouette)
WING_L = "M29.6 53.2 L78.6 34.6 L46.9 62.0 L31.0 55.6 Z"
WING_R = "M46.9 62.0 L78.6 34.6 L70.5 74.6 L56.6 66.6 Z"
KEEL = "M46.9 62.0 L56.6 66.6 L48.6 72.6 Z"


def svg(body, defs=''):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
            + ('<defs>' + defs + '</defs>' if defs else '') + body + '</svg>\n')


def lin(id_, stops, x1=0, y1=0, x2=1, y2=1):
    s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    return f'<linearGradient id="{id_}" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>'


def rad(id_, color, op, cx=0.5, cy=0.5, r=0.6):
    return (f'<radialGradient id="{id_}" cx="{cx}" cy="{cy}" r="{r}"><stop offset="0" stop-color="{color}" stop-opacity="{op}"/>'
            f'<stop offset="1" stop-color="{color}" stop-opacity="0"/></radialGradient>')


def plane(color='#FFFFFF', fold=0.18, shadow=0.12, extra=''):
    b = ''
    if shadow:
        b += f'<path d="{PLANE}" fill="#000" opacity="{shadow}" transform="translate(0.8 1.8)"/>'
    b += f'<path d="{PLANE}" fill="{color}" {extra}/><path d="{FOLD}" fill="#000" opacity="{fold}"/>'
    return b


MONO = svg(f'<path d="{PLANE}" fill="#FFF"/>')
R = random.Random(7)

# ------------------------------------------------------------------ scenes (background, foreground)

def classic():
    return svg('<rect width="108" height="108" fill="url(#g)"/><rect width="108" height="108" fill="url(#r)"/>',
               lin('g', [(0, '#3FB8F0'), (1, '#1C8ADB')]) + rad('r', '#FFF', .25, .3, .2)), svg(plane())


def solid(stops, glow, color, fold=0.18):
    def f():
        d = lin('g', stops) + (rad('r', glow[0], glow[1], glow[2], glow[3]) if glow else '')
        return (svg('<rect width="108" height="108" fill="url(#g)"/>' + ('<rect width="108" height="108" fill="url(#r)"/>' if glow else ''), d),
                svg(plane(color, fold)))
    return f


def space():
    stars = ''.join(f'<circle cx="{R.uniform(2,106):.1f}" cy="{R.uniform(2,106):.1f}" r="{R.choice([.4,.5,.6,.8,1.1]):.1f}" fill="#FFF" opacity="{R.uniform(.4,1):.2f}"/>' for _ in range(70))
    sparkle = ''.join(f'<path d="M{x} {y-3} L{x+.8} {y-.8} L{x+3} {y} L{x+.8} {y+.8} L{x} {y+3} L{x-.8} {y+.8} L{x-3} {y} L{x-.8} {y-.8} Z" fill="#FFF"/>' for x, y in ((22, 26), (86, 80), (80, 22)))
    planet = ('<circle cx="22" cy="86" r="13" fill="url(#p)"/><ellipse cx="22" cy="86" rx="21" ry="5" fill="none" stroke="#F7C6FF" stroke-width="1.6" opacity=".8" transform="rotate(-18 22 86)"/>')
    bg = svg('<rect width="108" height="108" fill="url(#g)"/><rect width="108" height="108" fill="url(#n)"/>' + stars + sparkle + planet,
             lin('g', [(0, '#0B0730'), (1, '#1C0F4A')]) + rad('n', '#7B5CFF', .45, .65, .35, .55) + lin('p', [(0, '#FF8AD8'), (1, '#7A4DF0')]))
    trail = '<path d="M14 70 Q 26 62 33 56" stroke="#FFF" stroke-width="1.6" stroke-dasharray="2 3" fill="none" opacity=".7"/>'
    return bg, svg(trail + plane('#FFFFFF', .2, .25))


def pixel():
    # rasterize the plane silhouette into 4.5px cells
    from math import floor
    cells = ''
    s = 3.0
    def inside(px, py, poly):
        n = len(poly); c = False
        for i in range(n):
            x1, y1 = poly[i]; x2, y2 = poly[(i + 1) % n]
            if (y1 > py) != (y2 > py) and px < (x2 - x1) * (py - y1) / (y2 - y1) + x1:
                c = not c
        return c
    L = [(29.6, 53.2), (78.6, 34.6), (46.9, 62.0), (31.0, 55.6)]
    Rr = [(46.9, 62.0), (78.6, 34.6), (70.5, 74.6), (56.6, 66.6)]
    K = [(46.9, 62.0), (56.6, 66.6), (48.6, 72.6)]
    y = 30.0
    while y < 80:
        x = 25.0
        while x < 84:
            cx, cy = x + s / 2, y + s / 2
            col = None
            if inside(cx, cy, Rr): col = '#FFFFFF'
            elif inside(cx, cy, L): col = '#FFE8FB'
            elif inside(cx, cy, K): col = '#C9A6FF'
            if col: cells += f'<rect x="{x:.1f}" y="{y:.1f}" width="{s+.05:.2f}" height="{s+.05:.2f}" fill="{col}"/>'
            x += s
        y += s
    grid = ''.join(f'<rect x="{i*9}" y="{j*9}" width="9" height="9" fill="#FFFFFF" opacity="{0.06 if (i+j)%2 else 0}"/>' for i in range(12) for j in range(12))
    clouds = '<rect x="12" y="78" width="18" height="5" fill="#FFF" opacity=".5"/><rect x="16" y="74" width="9" height="4" fill="#FFF" opacity=".5"/><rect x="78" y="18" width="16" height="4" fill="#FFF" opacity=".45"/>'
    bg = svg('<rect width="108" height="108" fill="url(#g)"/>' + grid + clouds, lin('g', [(0, '#FF4FD8'), (1, '#5B2BD8')], 0, 0, 0, 1))
    return bg, svg('<g transform="translate(1.2 1.6)" opacity=".25">' + cells.replace('#FFFFFF', '#000').replace('#FFE8FB', '#000').replace('#C9A6FF', '#000') + '</g>' + cells)


def neon():
    bg = svg('<rect width="108" height="108" fill="#07060F"/><rect width="108" height="108" fill="url(#r)"/>'
             + ''.join(f'<line x1="0" y1="{y}" x2="108" y2="{y}" stroke="#FF2BD6" stroke-width=".4" opacity=".18"/>' for y in range(8, 108, 8)),
             rad('r', '#3A0CA3', .7, .5, .5, .7))
    glow = ('<filter id="f" x="-30%" y="-30%" width="160%" height="160%"><feGaussianBlur stdDeviation="2.2"/></filter>')
    o = f'<path d="{PLANE}" fill="none" stroke="#00F0FF" stroke-width="3" stroke-linejoin="round" filter="url(#f)"/>' \
        f'<path d="{PLANE}" fill="none" stroke="#FF2BD6" stroke-width="2.6" stroke-linejoin="round" filter="url(#f)" transform="translate(1.5 1.5)"/>' \
        f'<path d="{PLANE}" fill="none" stroke="#E9FDFF" stroke-width="1.7" stroke-linejoin="round"/>' \
        f'<path d="M46.9 62.0 L67.6 43.4" stroke="#E9FDFF" stroke-width="1.4" stroke-linecap="round"/>'
    return bg, svg(o, glow)


def origami():
    paper = ''.join(f'<circle cx="{R.uniform(0,108):.1f}" cy="{R.uniform(0,108):.1f}" r="{R.uniform(.3,.9):.1f}" fill="#6B4A2B" opacity="{R.uniform(.08,.22):.2f}"/>' for _ in range(120))
    bg = svg('<rect width="108" height="108" fill="url(#g)"/>' + paper
             + '<path d="M0 0 L108 108" stroke="#FFF" stroke-width=".6" opacity=".25"/><path d="M108 0 L0 108" stroke="#FFF" stroke-width=".6" opacity=".18"/>',
             lin('g', [(0, '#E8C9A0'), (1, '#C99A63')]))
    fg = (f'<path d="{WING_L}" fill="#000" opacity=".14" transform="translate(1 2)"/><path d="{WING_R}" fill="#000" opacity=".14" transform="translate(1 2)"/>'
          f'<path d="{WING_L}" fill="#FFFDF7"/><path d="{WING_R}" fill="#F1E6D3"/><path d="{KEEL}" fill="#D8C4A4"/>'
          '<path d="M46.9 62.0 L78.6 34.6" stroke="#C9B28E" stroke-width=".8"/>')
    return bg, svg(fg)


def synthwave():
    stripes = ''.join(f'<rect x="20" y="{y}" width="68" height="{h}" fill="#1A0533"/>' for y, h in ((58, 1.6), (63, 2.2), (68, 2.8), (73, 3.4)))
    grid = ''.join(f'<line x1="{54+(x-54)*.25:.1f}" y1="78" x2="{x}" y2="108" stroke="#FF4FD8" stroke-width=".7" opacity=".8"/>' for x in range(-40, 160, 14)) \
        + ''.join(f'<line x1="0" y1="{y}" x2="108" y2="{y}" stroke="#FF4FD8" stroke-width=".7" opacity=".8"/>' for y in (80, 84, 90, 98))
    bg = svg('<rect width="108" height="108" fill="url(#g)"/><circle cx="54" cy="56" r="30" fill="url(#s)"/>' + stripes
             + '<rect y="78" width="108" height="30" fill="#1A0533"/>' + grid,
             lin('g', [(0, '#2B0A5A'), (.7, '#7A1E8F'), (1, '#1A0533')], 0, 0, 0, 1) + lin('s', [(0, '#FFE66D'), (1, '#FF2E88')], 0, 0, 0, 1))
    return bg, svg(plane('#FFFFFF', .22, .3))


def watermelon():
    seeds = ''.join(f'<ellipse cx="{x}" cy="{y}" rx="1.4" ry="2.4" fill="#2A0F0F" transform="rotate({a} {x} {y})"/>' for x, y, a in ((24, 30, 20), (40, 20, -10), (84, 28, 30), (88, 56, 10), (20, 72, -30), (36, 88, 15), (70, 90, -20), (60, 18, 0)))
    bg = svg('<rect width="108" height="108" fill="#1E7A3A"/><rect x="5" y="5" width="98" height="98" rx="0" fill="#BDF2B0"/><rect x="9" y="9" width="90" height="90" fill="url(#g)"/>' + seeds,
             lin('g', [(0, '#FF5A6E'), (1, '#E0244A')]))
    return bg, svg(plane('#FFFFFF', .16, .2))


def blueprint():
    grid = ''.join(f'<line x1="{i}" y1="0" x2="{i}" y2="108" stroke="#BFE3FF" stroke-width="{.6 if i % 18 == 0 else .25}" opacity=".5"/>' for i in range(0, 109, 6)) \
        + ''.join(f'<line x1="0" y1="{i}" x2="108" y2="{i}" stroke="#BFE3FF" stroke-width="{.6 if i % 18 == 0 else .25}" opacity=".5"/>' for i in range(0, 109, 6))
    bg = svg('<rect width="108" height="108" fill="#0F4C9C"/>' + grid)
    fg = (f'<path d="{PLANE}" fill="#FFFFFF" fill-opacity=".12" stroke="#FFFFFF" stroke-width="1.6" stroke-linejoin="round"/>'
          '<path d="M46.9 62.0 L67.6 43.4" stroke="#FFF" stroke-width="1.2"/>'
          '<path d="M29 80 L80 80" stroke="#FFF" stroke-width=".8"/><path d="M29 78 L29 82 M80 78 L80 82" stroke="#FFF" stroke-width=".8"/>'
          '<circle cx="78.6" cy="34.6" r="3" fill="none" stroke="#FFF" stroke-width=".8" stroke-dasharray="1.5 1.5"/>')
    return bg, svg(fg)


def clouds():
    def cloud(x, y, s, o):
        return (f'<g opacity="{o}" transform="translate({x} {y}) scale({s})"><circle cx="0" cy="0" r="6" fill="#FFF"/><circle cx="7" cy="-3" r="7.5" fill="#FFF"/>'
                f'<circle cx="15" cy="0" r="5.5" fill="#FFF"/><rect x="0" y="0" width="15" height="5.5" fill="#FFF"/></g>')
    bg = svg('<rect width="108" height="108" fill="url(#g)"/><circle cx="84" cy="22" r="9" fill="#FFE58A"/>' + cloud(10, 30, 1, .9) + cloud(64, 84, 1.3, .95) + cloud(14, 88, .8, .8),
             lin('g', [(0, '#8FD3FF'), (1, '#4AA8F0')], 0, 0, 0, 1))
    trail = '<path d="M8 74 C 18 70, 22 60, 31 56" stroke="#FFF" stroke-width="2" stroke-dasharray="3 3" fill="none" stroke-linecap="round"/>'
    return bg, svg(trail + plane('#FFFFFF', .14, .15))


def candy():
    stripes = ''.join(f'<rect x="{x}" y="-40" width="8" height="200" fill="#FF3D7F" transform="rotate(35 54 54)"/>' for x in range(-60, 170, 16))
    bg = svg('<rect width="108" height="108" fill="#FFF4F8"/>' + stripes + '<rect width="108" height="108" fill="url(#r)"/>', rad('r', '#FFFFFF', .5, .3, .25))
    return bg, svg(plane('#FFFFFF', .18, .3, 'stroke="#7A1E3A" stroke-width="1.2" stroke-linejoin="round"'))


def holo():
    bg = svg('<rect width="108" height="108" fill="url(#a)"/><rect width="108" height="108" fill="url(#b)" opacity=".75"/><rect width="108" height="108" fill="url(#c)" opacity=".5"/>',
             lin('a', [(0, '#A0F0FF'), (.35, '#C9B6FF'), (.7, '#FFC2E8'), (1, '#FFF3B0')]) +
             lin('b', [(0, '#B8FFE4', ), (.5, '#00000000'), (1, '#9EC5FF')], 1, 0, 0, 1) + rad('c', '#FFFFFF', .9, .7, .25, .4))
    return bg, svg(plane('#FFFFFF', .12, .1, 'stroke="#FFFFFF" stroke-opacity=".9" stroke-width=".6"') +
                   f'<path d="{PLANE}" fill="url(#h)" opacity=".35"/>', lin('h', [(0, '#7FE3FF'), (1, '#FF9AD5')]))


def moon():
    stars = ''.join(f'<circle cx="{R.uniform(4,104):.1f}" cy="{R.uniform(4,60):.1f}" r="{R.uniform(.3,.8):.1f}" fill="#FFF" opacity="{R.uniform(.4,.9):.2f}"/>' for _ in range(30))
    bg = svg('<rect width="108" height="108" fill="url(#g)"/>' + stars +
             '<circle cx="76" cy="30" r="14" fill="#FFF6D6"/><circle cx="82" cy="25" r="13" fill="#152A5C"/>'
             '<path d="M0 92 Q 20 80 40 90 T 80 88 T 108 86 L108 108 L0 108 Z" fill="#0B1838"/>',
             lin('g', [(0, '#0B1838'), (1, '#24407E')], 0, 0, 0, 1))
    return bg, svg(plane('#FFF6D6', .2, .2))


def lava():
    blobs = ''.join(f'<circle cx="{x}" cy="{y}" r="{r}" fill="url(#b)"/>' for x, y, r in ((20, 86, 22), (88, 92, 18), (54, 104, 20), (90, 20, 12)))
    bg = svg('<rect width="108" height="108" fill="url(#g)"/>' + blobs,
             lin('g', [(0, '#2A0505'), (1, '#6B0F0F')], 0, 0, 0, 1) + rad('b', '#FF8A00', 1, .5, .5, .5))
    return bg, svg(plane('#FFE7A3', .25, .3))


def camo():
    cols = ['#4B5D2E', '#6E7F3A', '#2F3A1E', '#8C8F5A']
    blobs = ''
    for _ in range(26):
        x, y = R.uniform(-10, 118), R.uniform(-10, 118)
        pts = ' '.join(f'{x + math.cos(a) * R.uniform(6, 14):.1f},{y + math.sin(a) * R.uniform(5, 12):.1f}' for a in [i * math.pi / 4 for i in range(8)])
        blobs += f'<polygon points="{pts}" fill="{R.choice(cols)}"/>'
    bg = svg('<rect width="108" height="108" fill="#5C6B34"/>' + blobs)
    return bg, svg(plane('#F2EBD3', .2, .3))


def matrix():
    cols = ''
    for i in range(0, 108, 6):
        y = R.uniform(-30, 40)
        n = R.randint(5, 14)
        for k in range(n):
            op = (k + 1) / n
            cols += f'<rect x="{i+1.5}" y="{y + k*6:.1f}" width="3" height="4" rx=".6" fill="#22FF7A" opacity="{op*.85:.2f}"/>'
    bg = svg('<rect width="108" height="108" fill="#020A04"/>' + cols)
    return bg, svg(f'<path d="{PLANE}" fill="#000" opacity=".6" transform="translate(0 1)"/>' + plane('#D6FFE4', .25, 0))


def bglass():
    bg = svg('<rect width="108" height="108" fill="#EEF0FF"/><circle cx="24" cy="30" r="34" fill="url(#a)"/><circle cx="92" cy="58" r="30" fill="url(#b)"/><circle cx="40" cy="100" r="32" fill="url(#c)"/>',
             rad('a', '#7A7CF5', .75, .5, .5, .5) + rad('b', '#FF9B78', .65, .5, .5, .5) + rad('c', '#5ADCB4', .6, .5, .5, .5))
    return bg, svg(plane('#5457EE', .22, .15, 'stroke="#FFFFFF" stroke-width="1.4" stroke-linejoin="round"'))


def dark_glass():
    bg = svg('<rect width="108" height="108" fill="#07080F"/><circle cx="26" cy="30" r="34" fill="url(#a)"/><circle cx="92" cy="62" r="30" fill="url(#b)"/><circle cx="36" cy="102" r="32" fill="url(#c)"/>',
             rad('a', '#4F5BF0', .85, .5, .5, .5) + rad('b', '#E2553A', .6, .5, .5, .5) + rad('c', '#0E8E6E', .7, .5, .5, .5))
    return bg, svg(plane('#FFFFFF', .2, .3))


def sakura():
    petals = ''.join(f'<ellipse cx="{R.uniform(4,104):.1f}" cy="{R.uniform(4,104):.1f}" rx="3" ry="1.8" fill="#FF8FB8" opacity="{R.uniform(.5,.9):.2f}" transform="rotate({R.uniform(0,180):.0f} {54} {54})"/>' for _ in range(22))
    bg = svg('<rect width="108" height="108" fill="url(#g)"/>' + petals, lin('g', [(0, '#FFE6EF'), (1, '#FFC2D6')]))
    return bg, svg(plane('#FFFFFF', .14, .12, 'stroke="#F06A9A" stroke-width=".8"'))


ICONS = [
    # id, title ru, description ru, scene
    ("default", "Классика", "Голубой самолётик", classic),
    ("space", "Космос", "Звёзды, планета и след", space),
    ("pixel", "8 бит", "Пиксельный самолётик", pixel),
    ("neon", "Неон", "Светящийся контур", neon),
    ("origami", "Оригами", "Бумажный самолётик", origami),
    ("synthwave", "Ретровейв", "Закат и неоновая сетка", synthwave),
    ("blueprint", "Чертёж", "Инженерный эскиз", blueprint),
    ("clouds", "В облаках", "Небо, солнце, пунктир", clouds),
    ("glass", "B Glass", "Жидкое стекло", bglass),
    ("glass_dark", "B Glass тёмная", "Тёмное стекло", dark_glass),
    ("holo", "Голограмма", "Переливы", holo),
    ("moon", "Луна", "Ночной полёт", moon),
    ("watermelon", "Арбуз", "Сочный", watermelon),
    ("candy", "Леденец", "Полоски", candy),
    ("lava", "Лава", "Горячий", lava),
    ("camo", "Камуфляж", "Хаки", camo),
    ("matrix", "Матрица", "Цифровой дождь", matrix),
    ("sakura", "Сакура", "Лепестки", sakura),
    ("obsidian", "Обсидиан", "Чёрный и лайм", solid([(0, '#202024'), (1, '#050507')], ('#C6F432', .25, .3, .2), '#C6F432', .25)),
    ("mono", "Белая", "Минимализм", solid([(0, '#FFFFFF'), (1, '#E9EDF2')], None, '#2AA3E6', .22)),
]


def main():
    keep = {'icons.json', 'README.md'}
    for name in os.listdir(ROOT):
        if name not in keep:
            p = os.path.join(ROOT, name)
            shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    entries = []
    base = os.path.join(ROOT, 'avragram')
    for iid, title, desc, scene in ICONS:
        bg, fg = scene()
        d = os.path.join(base, iid)
        os.makedirs(d)
        open(os.path.join(d, 'ic_launcher_background.svg'), 'w').write(bg)
        open(os.path.join(d, 'ic_launcher_foreground.svg'), 'w').write(fg)
        open(os.path.join(d, 'ic_launcher_monochrome.svg'), 'w').write(MONO)
        e = {"id": iid, "title": title, "description": desc, "author": "avragram", "lossless": True}
        if iid == 'default':
            e.update({"resource": "ic_launcher", "component": "DefaultIcon", "round": True})
        entries.append(e)
        if iid == 'default':
            bd = os.path.join(base, 'beta'); os.makedirs(bd)
            for f in os.listdir(d):
                shutil.copy(os.path.join(d, f), bd)
            entries.append({"id": "beta", "resource": "ic_launcher_beta", "author": "avragram", "assetsOnly": True})
    json.dump({"icons": entries}, open(os.path.join(ROOT, 'icons.json'), 'w'), ensure_ascii=False, indent=2)
    print(len(entries), 'icons')


main()
