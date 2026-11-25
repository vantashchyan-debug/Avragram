#!/usr/bin/env python3
"""
Avragram soundpad: synthesizes the built-in sounds (no samples, no third-party audio) and writes them
as raw PCM, 16-bit little-endian, mono, 48 kHz to TMessagesProj/src/main/assets/soundpad/<id>.pcm.
The order and ids must match com.avragramm.AvraSoundpad.BUILTIN.

Run from the repo root:  uv run --with numpy python Tools/avra_soundpad.py
"""
import os

import numpy as np

SR = 48000
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, "TMessagesProj/src/main/assets/soundpad")
rng = np.random.default_rng(7)


def t(sec):
    return np.arange(int(SR * sec)) / SR


def env(n, attack=0.005, release=0.05):
    e = np.ones(n)
    a, r = int(SR * attack), int(SR * release)
    if a: e[:a] = np.linspace(0, 1, a)
    if r: e[-r:] = np.minimum(e[-r:], np.linspace(1, 0, r))
    return e


def saw(freq, tt):
    ph = np.cumsum(np.broadcast_to(freq, tt.shape) / SR)
    return 2 * (ph % 1) - 1


def square(freq, tt):
    ph = np.cumsum(np.broadcast_to(freq, tt.shape) / SR)
    return np.where(ph % 1 < 0.5, 1.0, -1.0)


def sine(freq, tt):
    ph = np.cumsum(np.broadcast_to(freq, tt.shape) / SR)
    return np.sin(2 * np.pi * ph)


def lowpass(x, alpha):
    y = np.empty_like(x)
    acc = 0.0
    for i, v in enumerate(x):
        acc += alpha * (v - acc)
        y[i] = acc
    return y


def highpass(x, alpha):
    return x - lowpass(x, alpha)


def cat(*parts):
    return np.concatenate(parts)


def silence(sec):
    return np.zeros(int(SR * sec))


def airhorn():
    def blast(sec):
        tt = t(sec)
        f = 466 * (1 + 0.004 * np.sin(2 * np.pi * 5 * tt))
        x = saw(f, tt) + 0.7 * saw(f * 1.26, tt) + 0.5 * saw(f * 1.5, tt)
        return lowpass(x, 0.25) * env(len(tt), 0.01, 0.06)
    return cat(blast(0.22), silence(0.06), blast(0.22), silence(0.06), blast(0.9))


def rimshot():
    def tom(f0, sec):
        tt = t(sec)
        return sine(f0 * np.exp(-tt * 6), tt) * np.exp(-tt * 9)
    tt = t(1.4)
    cym = highpass(rng.standard_normal(len(tt)), 0.35) * np.exp(-tt * 3.2) * 0.6
    return cat(tom(220, 0.18), tom(160, 0.25), cym)


def sad_trombone():
    notes = [(392, 0.45), (370, 0.45), (349, 0.45), (330, 1.3)]
    out = []
    for i, (f, sec) in enumerate(notes):
        tt = t(sec)
        vib = 1 + (0.02 * np.sin(2 * np.pi * 6 * tt) * np.clip(tt * 2, 0, 1) if i == 3 else 0)
        x = saw(f * vib, tt)
        out.append(lowpass(x, 0.08) * env(len(tt), 0.03, 0.12))
    return cat(*out)


def fanfare():
    seq = [(523, 0.14), (523, 0.14), (523, 0.14), (659, 0.45), (523, 0.2), (659, 0.2), (784, 0.9)]
    out = []
    for f, sec in seq:
        tt = t(sec)
        x = square(f, tt) * 0.5 + saw(f, tt) * 0.5 + 0.3 * saw(f * 2, tt)
        out.append(lowpass(x, 0.2) * env(len(tt), 0.01, 0.05))
    return cat(*out)


def beep():
    tt = t(0.9)
    return sine(1000, tt) * env(len(tt), 0.005, 0.01)


def siren():
    tt = t(2.4)
    f = 750 + 350 * np.sin(2 * np.pi * 0.9 * tt)
    x = sine(f, tt) + 0.3 * square(f, tt)
    return lowpass(x, 0.3) * env(len(tt), 0.05, 0.2)


def drumroll():
    n = int(SR * 2.2)
    x = np.zeros(n)
    hit = int(SR * 0.045)
    k = 0
    while k + hit < int(SR * 1.8):
        tt = np.arange(hit) / SR
        amp = 0.35 + 0.65 * k / (SR * 1.8)
        x[k:k + hit] += rng.standard_normal(hit) * np.exp(-tt * 60) * amp
        k += int(SR * 0.05)
    tt = t(0.4)
    crash = highpass(rng.standard_normal(len(tt)), 0.3) * np.exp(-tt * 5)
    x[-len(tt):] += crash
    return lowpass(x, 0.5)


def crickets():
    out = []
    for _ in range(4):
        tt = t(0.32)
        chirp = sine(4400, tt) * (0.5 + 0.5 * square(30, tt)) * np.where(tt < 0.18, 1.0, 0.0)
        out.append(chirp * 0.6)
        out.append(silence(0.28))
    return cat(*out)


def laser():
    out = []
    for _ in range(3):
        tt = t(0.22)
        f = 2400 * np.exp(-tt * 12) + 200
        out.append(square(f, tt) * 0.6 * np.exp(-tt * 6))
        out.append(silence(0.05))
    return lowpass(cat(*out), 0.4)


def ding():
    tt = t(2.0)
    parts = [(1318, 1.0, 2.5), (2637, 0.5, 4), (3564, 0.3, 6), (4950, 0.2, 8)]
    return sum(sine(f, tt) * a * np.exp(-tt * d) for f, a, d in parts) * env(len(tt), 0.002, 0.05)


def boing():
    tt = t(1.0)
    f = 180 + 120 * np.exp(-tt * 3) * np.sin(2 * np.pi * 9 * tt)
    return sine(f, tt) * np.exp(-tt * 3)


def applause():
    n = int(SR * 2.4)
    x = np.zeros(n)
    clap = int(SR * 0.02)
    for _ in range(420):
        k = rng.integers(0, n - clap)
        tt = np.arange(clap) / SR
        x[k:k + clap] += rng.standard_normal(clap) * np.exp(-tt * 180) * rng.uniform(0.3, 1.0)
    x = highpass(x, 0.15)
    return x * env(n, 0.3, 0.6)


SOUNDS = [
    ("airhorn", airhorn), ("rimshot", rimshot), ("fail", sad_trombone), ("fanfare", fanfare),
    ("beep", beep), ("siren", siren), ("drumroll", drumroll), ("crickets", crickets),
    ("laser", laser), ("ding", ding), ("boing", boing), ("applause", applause),
]


def main():
    os.makedirs(OUT, exist_ok=True)
    for name, fn in SOUNDS:
        x = fn().astype(np.float64)
        peak = np.max(np.abs(x)) or 1.0
        pcm = np.clip(x / peak * 0.85 * 32767, -32768, 32767).astype("<i2")
        with open(os.path.join(OUT, name + ".pcm"), "wb") as f:
            f.write(pcm.tobytes())
        print("wrote", name, "%.2fs" % (len(pcm) / SR))


if __name__ == "__main__":
    main()
