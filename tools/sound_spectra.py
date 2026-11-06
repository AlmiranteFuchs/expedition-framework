#!/usr/bin/env python3
"""Render spectrogram PNGs of the generated sound set for eyeball QA.

Log-frequency magnitude spectrograms into build/sound-spectra/ —
the horror palette lives below ~300Hz except noise bands, so tonal
content higher than that shows up as a bright horizontal streak and
is easy to spot. Prints band-energy stats per file as well.

Requires: pip3 install soundfile numpy; PIL already used by the
texture script. Run: python3 tools/sound_spectra.py [name-filter]
"""
import math
import os
import sys

import numpy as np
import soundfile as sf
from PIL import Image

ROOT = os.path.join(os.path.dirname(__file__), "..",
                    "src/main/resources/assets/expeditions/sounds")
OUT = os.environ.get("SPECTRA_OUT", "/tmp/sound-spectra")
SR = 44100
FFT = 1024
HOP = 256
F_LO, F_HI = 20.0, 8000.0
ROWS = 96  # frequency bins (log-spaced)


def render(path, out_path):
    x, sr = sf.read(path)
    if x.ndim > 1:
        x = x.mean(axis=1)
    n = len(x)
    win = np.hanning(FFT)
    frames = []
    for i in range(0, n - FFT, HOP):
        frames.append(np.abs(np.fft.rfft(x[i:i + FFT] * win)))
    spec = np.array(frames).T  # [freq, time]
    freqs = np.fft.rfftfreq(FFT, 1 / sr)
    # remap to log-frequency rows
    targets = np.exp(np.linspace(math.log(F_LO), math.log(F_HI), ROWS))
    idx = np.searchsorted(freqs, targets).clip(0, len(freqs) - 1)
    log_spec = spec[idx]
    db = 20 * np.log10(log_spec + 1e-6)
    lo, hi = db.max() - 70, db.max()
    img = ((db - lo) / (hi - lo) * 255).clip(0, 255).astype(np.uint8)
    img = img[::-1]  # low freq at bottom
    Image.fromarray(img, "L").resize((img.shape[1] * 4, ROWS * 4),
                                     Image.NEAREST).save(out_path)
    # band energies (linear, relative to total)
    bands = [(20, 150), (150, 400), (400, 1200), (1200, 4000), (4000, 16000)]
    total = 0.0
    out = []
    p = spec ** 2
    for a, b in bands:
        m = (freqs >= a) & (freqs < b)
        e = float(p[m].mean()) if m.any() else 0.0
        out.append(e)
        total += e
    shares = [f"{e / total:.2f}" if total else "0" for e in out]
    return shares


def main():
    os.makedirs(OUT, exist_ok=True)
    filt = sys.argv[1] if len(sys.argv) > 1 else ""
    print(f"{'file':<28} {'20-150':>6} {'150-400':>7} {'400-1k2':>7} "
          f"{'1k2-4k':>7} {'4k+':>6}")
    for dp, _, fs in os.walk(ROOT):
        for f in sorted(fs):
            if not f.endswith(".ogg"):
                continue
            rel = os.path.relpath(os.path.join(dp, f), ROOT)[:-4]
            if filt and filt not in rel:
                continue
            shares = render(os.path.join(dp, f),
                            os.path.join(OUT, rel.replace("/", "_") + ".png"))
            flag = "  <-- bright highs" if float(shares[3]) + float(shares[4]) > 0.35 else ""
            print(f"{rel:<28} {shares[0]:>6} {shares[1]:>7} {shares[2]:>7} "
                  f"{shares[3]:>7} {shares[4]:>6}{flag}")
    print(f"\nPNGs -> {os.path.relpath(OUT)}")


if __name__ == "__main__":
    main()
