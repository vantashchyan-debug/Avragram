#!/usr/bin/env python3
"""Avragram launcher icons: paper-plane glyph on themed backgrounds (108x108 adaptive canvas)."""
import json, os, shutil, sys
ROOT = os.path.join(os.path.dirname(__file__), '..', 'TMessagesProj', 'appicons')

PLANE = ("M29.6 53.2 L76.6 33.9 C79.0 32.9 80.6 34.4 80.1 36.8 L72.3 73.1 C71.8 75.4 69.8 76.1 67.9 74.8 "
         "L56.6 66.6 L50.6 72.4 C49.3 73.7 47.6 73.1 47.5 71.2 L46.9 62.0 L67.6 43.4 L43.4 58.3 "
         "L31.0 55.6 C28.4 55.0 28.0 53.9 29.6 53.2 Z")
FOLD = "M46.9 62.0 L47.5 71.2 C47.6 73.1 49.3 73.7 50.6 72.4 L56.6 66.6 Z"

def svg(body):
    return ('<svg xmlns="http://www.w3.org/2000/svg" width="108" height="108" viewBox="0 0 108 108">'
            + body + '</svg>\n')

def bg(stops, glow=None, angle=(0, 0, 1, 1)):
    x1, y1, x2, y2 = angle
    s = ''.join(f'<stop offset="{o}" stop-color="{c}"/>' for o, c in stops)
    body = (f'<defs><linearGradient id="g" x1="{x1}" y1="{y1}" x2="{x2}" y2="{y2}">{s}</linearGradient>')
    if glow:
        body += (f'<radialGradient id="r" cx="{glow[0]}" cy="{glow[1]}" r="0.6"><stop offset="0" stop-color="{glow[2]}" stop-opacity="{glow[3]}"/>'
                 f'<stop offset="1" stop-color="{glow[2]}" stop-opacity="0"/></radialGradient>')
    body += '</defs><rect width="108" height="108" fill="url(#g)"/>'
    if glow:
        body += '<rect width="108" height="108" fill="url(#r)"/>'
    return svg(body)

def fg(color, fold_opacity=0.18, fold_color='#000', shadow=True):
    body = ''
    if shadow:
        body += f'<path d="{PLANE}" fill="#000" opacity="0.10" transform="translate(0.8 1.6)"/>'
    body += f'<path d="{PLANE}" fill="{color}"/><path d="{FOLD}" fill="{fold_color}" opacity="{fold_opacity}"/>'
    return svg(body)

MONO = svg(f'<path d="{PLANE}" fill="#FFF"/>')

# id, title, description, bg-stops, glow, plane colour
ICONS = [
    ("default", "Классика", "Голубой самолётик", [(0, '#3FB8F0'), (1, '#1C8ADB')], ('0.3', '0.2', '#FFFFFF', 0.25), '#FFFFFF'),
    ("glass", "B Glass", "Светлое жидкое стекло", [(0, '#EEF0FF'), (0.55, '#B9B8F7'), (1, '#F7D8CC')], ('0.75', '0.3', '#7A7CF5', 0.55), '#5457EE'),
    ("glass_dark", "B Glass тёмная", "Тёмное стекло", [(0, '#0B0D24'), (0.6, '#24277A'), (1, '#4A2A3A')], ('0.7', '0.35', '#5B5FF5', 0.7), '#FFFFFF'),
    ("night", "Ночь", "Глубокий синий", [(0, '#232946'), (1, '#0F1220')], ('0.3', '0.25', '#3FB8F0', 0.35), '#3FB8F0'),
    ("obsidian", "Обсидиан", "Чёрное стекло", [(0, '#202024'), (1, '#050507')], ('0.3', '0.2', '#FFFFFF', 0.12), '#FFFFFF'),
    ("neon_sunset", "Неоновый закат", "Розовый неон и закат", [(0, '#FF3CAC'), (0.5, '#784BA0'), (1, '#2B86C5')], ('0.8', '0.85', '#FFB347', 0.6), '#FFFFFF'),
    ("sakura", "Сакура", "Нежно-розовый", [(0, '#FFE1EC'), (1, '#F58BB0')], ('0.3', '0.2', '#FFFFFF', 0.5), '#FFFFFF'),
    ("ocean", "Океан", "Бирюза и глубина", [(0, '#00C9B7'), (1, '#0A4D8C')], ('0.3', '0.2', '#FFFFFF', 0.25), '#FFFFFF'),
    ("matrix", "Матрица", "Зелёный на чёрном", [(0, '#04140A'), (1, '#000000')], ('0.5', '0.5', '#00FF66', 0.25), '#22FF7A'),
    ("gold", "Золото", "Тёплый песок", [(0, '#FFE6A7'), (1, '#D89A2B')], ('0.3', '0.2', '#FFFFFF', 0.4), '#FFFFFF'),
    ("forest", "Лес", "Глубокий зелёный", [(0, '#3E8E5E'), (1, '#173B2A')], None, '#E9F7EC'),
    ("sunrise", "Рассвет", "Оранжево-красный", [(0, '#FFB14A'), (1, '#F0405A')], ('0.3', '0.2', '#FFFFFF', 0.3), '#FFFFFF'),
    ("mono", "Белая", "Минимализм", [(0, '#FFFFFF'), (1, '#E9EDF2')], None, '#2AA3E6'),
]

def main():
    keep = {'icons.json', 'README.md'}
    for name in os.listdir(ROOT):
        if name not in keep:
            p = os.path.join(ROOT, name)
            shutil.rmtree(p) if os.path.isdir(p) else os.remove(p)
    entries = []
    base = os.path.join(ROOT, 'avragram')
    for i, (iid, title, desc, stops, glow, plane) in enumerate(ICONS):
        d = os.path.join(base, iid)
        os.makedirs(d)
        open(os.path.join(d, 'ic_launcher_background.svg'), 'w').write(bg(stops, glow))
        dark_plane = plane.upper() not in ('#FFFFFF', '#E9F7EC')
        open(os.path.join(d, 'ic_launcher_foreground.svg'), 'w').write(
            fg(plane, fold_opacity=0.22 if dark_plane else 0.16, fold_color='#000' if not dark_plane else '#000'))
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
