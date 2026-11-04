#!/usr/bin/env python3
"""Procedural sound generator for the expedition-framework asset set.

Every clip is synthesized from oscillators + filtered noise — no samples,
no licensing surface, deterministic per seed. Style rules:
  - two voices, mirroring the bone/ember duality of the texture set:
      CHARTER — struck glass/ceramic, inharmonic bell partials, airy
                whooshes; precise, cold, institutional
      BLOOM   — sub-thumps, resonant squelch, formant shrieks; wet,
                granular, alive
  - one motif family (A-minor): stings, bells and chimes all speak it
  - mono 44.1kHz, normalized to -1.5dBFS peak; positional events stay
    mono so the game engine spatialize them correctly

Requires: pip3 install soundfile  (bundled libsndfile writes Ogg Vorbis)
Run: python3 tools/gen_sounds.py   (writes into src/main/resources,
                                  including sounds.json)
"""
import functools
import json
import math
import os
import random

import soundfile as sf

ROOT = os.path.join(os.path.dirname(__file__), "..",
                    "src/main/resources/assets/expeditions")
SOUNDS_DIR = os.path.join(ROOT, "sounds")
SR = 44100
PEAK = 0.85  # ≈ -1.4 dBFS


# ----------------------------------------------------------------- core dsp
def buf(dur):
    return [0.0] * int(round(dur * SR))


def mix(dst, src, gain=1.0, at=0.0):
    start = int(at * SR)
    n = min(len(src), len(dst) - start)
    for i in range(n):
        dst[start + i] += src[i] * gain
    return dst


def note(midi):
    return 440.0 * 2 ** ((midi - 69) / 12.0)


def sine(freq, dur, phase=0.0):
    """Sine wave; freq may be a float or f(t_seconds) -> hz (glides)."""
    n = int(dur * SR)
    out = [0.0] * n
    ph = phase
    if callable(freq):
        for i in range(n):
            ph += 2 * math.pi * freq(i / SR) / SR
            out[i] = math.sin(ph)
    else:
        step = 2 * math.pi * freq / SR
        for i in range(n):
            ph += step
            out[i] = math.sin(ph)
    return out


def noise(dur, seed):
    n = int(dur * SR)
    rng = random.Random(seed)
    return [rng.uniform(-1.0, 1.0) for _ in range(n)]


def env_exp(n, tau):
    """Exponential decay over n samples, time constant tau seconds."""
    return [math.exp(-i / (tau * SR)) for i in range(n)]


def apply_env(x, attack=0.0, decay_tau=None, sustain=1.0, release=0.0):
    n = len(x)
    a = int(attack * SR)
    r = int(release * SR)
    out = list(x)
    for i in range(min(a, n)):
        out[i] *= i / max(1, a)
    if decay_tau is not None:
        e = env_exp(n, decay_tau)
        for i in range(n):
            out[i] *= sustain + (1.0 - sustain) * e[i]
    for i in range(min(r, n)):
        j = n - 1 - i
        out[j] *= i / max(1, r)
    return out


def lp(x, cutoff):
    """One-pole lowpass."""
    a = 1.0 - math.exp(-2 * math.pi * min(cutoff, SR * 0.45) / SR)
    y = 0.0
    out = [0.0] * len(x)
    for i, s in enumerate(x):
        y += a * (s - y)
        out[i] = y
    return out


def hp(x, cutoff):
    l = lp(x, cutoff)
    return [s - l[i] for i, s in enumerate(x)]


def bp(x, f0, q):
    """RBJ bandpass biquad."""
    w = 2 * math.pi * f0 / SR
    alpha = math.sin(w) / (2 * q)
    cw = math.cos(w)
    b0, b2, a0 = alpha, -alpha, 1 + alpha
    a1, a2 = -2 * cw, 1 - alpha
    b0 /= a0; b2 /= a0; a1 /= a0; a2 /= a0
    x1 = x2 = y1 = y2 = 0.0
    out = [0.0] * len(x)
    for i, s in enumerate(x):
        y = b0 * s + b2 * x2 - a1 * y1 - a2 * y2
        x2, x1 = x1, s
        y2, y1 = y1, y
        out[i] = y
    return out


def bp_sweep(x, f_lo, f_hi, q):
    """Chunked swept bandpass (formant/wah motion)."""
    out = [0.0] * len(x)
    step = 2048
    for start in range(0, len(x), step):
        chunk = x[start:start + step]
        f = f_lo + (f_hi - f_lo) * (start / len(x))
        seg = bp(chunk, f, q)
        for i, s in enumerate(seg):
            out[start + i] += s * 0.5 if start else s  # mild overlap smooth
    return out


_COMBS = (1557, 1617, 1491, 1422)
_ALLPASS = (225, 556)


def _comb(x, delay, fb):
    out = [0.0] * len(x)
    for i in range(len(x)):
        out[i] = x[i] + (out[i - delay] * fb if i >= delay else 0.0)
    return out


def _allpass(x, delay, g=0.5):
    out = [0.0] * len(x)
    for i in range(len(x)):
        buf_s = x[i - delay] if i >= delay else 0.0
        out[i] = buf_s - g * x[i]
        x[i] = x[i] + g * out[i]
    return out


def reverb(x, wet=0.22, fb=0.72):
    """Small Schroeder 'stone hall' — cheap, enough for stings/portal."""
    src = list(x)
    acc = [0.0] * len(src)
    for d in _COMBS:
        c = _comb(src, d, fb)
        for i in range(len(src)):
            acc[i] += c[i]
    for d in _ALLPASS:
        acc = _allpass(acc, d)
    scale = wet / len(_COMBS)
    return [x[i] + acc[i] * scale for i in range(len(x))]


def finalize(x, peak=PEAK, tail_pad=0.05):
    m = max((abs(s) for s in x), default=0.0)
    if m > 0:
        g = peak / m
        x = [s * g for s in x]
    x += [0.0] * int(tail_pad * SR)
    return x


# --------------------------------------------------------- charter voices
# Inharmonic struck-glass partials — the Charter's institutional voice.
GLASS_RATIOS = (1.0, 2.756, 5.404, 8.933)
GLASS_AMPS = (1.0, 0.42, 0.18, 0.06)


def bell(dst, freq, at, dur, gain, detune=0.0):
    for r, a in zip(GLASS_RATIOS, GLASS_AMPS):
        f = freq * r * (1 + detune)
        tone = sine(f, dur)
        # higher partials die faster — glass, not brass
        tone = apply_env(tone, attack=0.003, decay_tau=dur / (3.5 * r),
                         sustain=0.0)
        mix(dst, tone, gain * a, at)
    # attack tick
    tick = apply_env(noise(0.02, int(freq)), decay_tau=0.006, sustain=0.0)
    mix(dst, hp(tick, 3000), gain * 0.18, at)


def air_sweep(dur, f_lo, f_hi, q, seed, gain=1.0, reverse=False):
    """Filtered-noise whoosh."""
    n = noise(dur, seed)
    src = n[::-1] if reverse else n
    out = bp_sweep(src, f_hi if reverse else f_lo,
                   f_lo if reverse else f_hi, q)
    return [s * gain for s in out]


def sub_drop(dur, f_start, f_end, gain=1.0):
    k = math.log(f_end / f_start)
    return [s * gain for s in
            apply_env(sine(lambda t: f_start * math.exp(k * t / dur), dur),
                      attack=0.005, decay_tau=dur * 0.4, sustain=0.0)]


# ------------------------------------------------------------ bloom voices
def squelch(dur, seed, drips=3, f_sweep=(900, 220)):
    """Wet granular slither — noise through a falling resonant sweep,
    plus descending 'drip' sines."""
    rng = random.Random(seed)
    out = buf(dur)
    wet = bp_sweep(noise(dur, seed), f_sweep[0], f_sweep[1], 6.0)
    wet = apply_env(wet, attack=0.01, decay_tau=dur * 0.3, sustain=0.0)
    mix(out, wet, 0.9)
    for _ in range(drips):
        t = rng.uniform(0.05, dur * 0.8)
        f = rng.uniform(500, 1400)
        drip = apply_env(sine(lambda tt, f=f: f * math.exp(-2.2 * tt), 0.09),
                         decay_tau=0.03, sustain=0.0)
        mix(out, drip, rng.uniform(0.15, 0.3), t)
    return out


def heartbeat(dur, lub_f=58, dub_f=46, wet=0.4, seed=7):
    """Lub-dub; 'wet' mixes a squelch layer under the thumps."""
    out = buf(dur)
    lub = apply_env(sine(lambda t: lub_f - 14 * t, 0.28),
                    decay_tau=0.09, sustain=0.0)
    mix(out, lub, 1.0)
    thump = apply_env(lp(noise(0.12, seed), 240), decay_tau=0.05, sustain=0.0)
    mix(out, thump, 0.5)
    dub_at = 0.3
    dub = apply_env(sine(lambda t: dub_f - 12 * t, 0.22),
                    decay_tau=0.08, sustain=0.0)
    mix(out, dub, 0.7, dub_at)
    if wet > 0:
        mix(out, squelch(0.5, seed + 1, drips=1), wet * 0.35, 0.02)
    return out


def formant_growl(dur, f0, seed, formants=(320, 850), trem_hz=9.0):
    """Detuned saws through swept formant bandpasses + tremolo."""
    n = int(dur * SR)
    rng = random.Random(seed)
    src = [0.0] * n
    for i in range(n):
        t = i / SR
        ph = (f0 * t) % 1.0
        saw = 2 * ph - 1
        ph2 = (f0 * 1.007 * t + 0.3) % 1.0
        src[i] = saw + (2 * ph2 - 1)
    out = buf(dur)
    for f in formants:
        mix(out, bp_sweep(src, f, f * 0.55, 7.0), 1.0)
    trem = [0.5 + 0.5 * math.sin(2 * math.pi * trem_hz * i / SR + rng.random())
            for i in range(n)]
    return [out[i] * trem[i] for i in range(n)]


# ------------------------------------------------------------- event builds
def b_terminal_activate():
    out = buf(0.8)
    bell(out, note(81), 0.0, 0.5, 0.5)   # A5
    bell(out, note(88), 0.09, 0.6, 0.4)  # E6
    mix(out, air_sweep(0.18, 1500, 5000, 3, 11, 0.12))
    return finalize(out)


def b_terminal_refuse():
    out = buf(0.5)
    for i, at in enumerate((0.0, 0.16)):
        k = apply_env(sine(lambda t: 170 - 60 * t, 0.12),
                      decay_tau=0.05, sustain=0.0)
        mix(out, lp(k, 900), 0.8 - 0.15 * i, at)
    mix(out, lp(noise(0.3, 4), 500), 0.1)
    return finalize(out)


def b_checkpoint_activate():
    out = buf(1.7)
    for i, m in enumerate((69, 72, 76, 81)):  # A4 C5 E5 A5 — minor rise
        bell(out, note(m), i * 0.11, 1.0, 0.4)
    mix(out, air_sweep(0.5, 800, 6000, 4, 21, 0.15), 0.25)
    return finalize(reverb(out, wet=0.3))


def b_portal_open():
    out = buf(1.9)
    mix(out, sub_drop(1.2, 95, 27, 0.9))                      # the thoom
    mix(out, air_sweep(1.4, 300, 3200, 2.5, 31, 0.3), 0.15)   # air rushing in
    for i, m in enumerate((76, 81, 88)):
        bell(out, note(m), 0.5 + i * 0.13, 1.1, 0.22)
    return finalize(reverb(out, wet=0.35))


def b_portal_hum():
    """Loopable ~3.2s ambient bed: beating lows + slow airy band."""
    dur = 3.2
    out = buf(dur)
    n = len(out)
    # integer-cycle carriers so the loop point is seamless
    for f, g in ((40.0, 0.5), (80.0, 0.3), (120.0, 0.12)):
        cycles = round(f * dur)
        cyc_f = cycles / dur
        for i in range(n):
            out[i] += g * math.sin(2 * math.pi * cyc_f * i / n)
    lfo = [0.65 + 0.35 * math.sin(2 * math.pi * (2 / dur) * i / n)
           for i in range(n)]
    air = lp(bp(noise(dur, 41), 1200, 1.2), 3000)
    for i in range(n):
        out[i] += air[i] * 0.25 * lfo[i]
    return finalize(out)


def b_portal_travel():
    out = buf(1.7)
    # whoosh swells in then snaps to silence = stepping through
    whoosh = apply_env(air_sweep(0.9, 400, 5200, 3.5, 51, 0.6),
                       attack=0.55, decay_tau=0.3, sustain=0.0)
    mix(out, whoosh)
    mix(out, sub_drop(0.7, 80, 30, 0.7), 0.75)
    bell(out, note(88), 0.82, 0.7, 0.3)
    bell(out, note(93), 0.95, 0.6, 0.2)
    return finalize(reverb(out, wet=0.28))


def b_event_start():
    out = buf(2.1)
    mix(out, apply_env(sine(55, 1.4), attack=0.6, decay_tau=0.7,
                       sustain=0.0), 0.5)
    for i, m in enumerate((57, 62, 69)):  # A3 D4 A4 — the departure call
        bell(out, note(m), 0.55 + i * 0.14, 1.2, 0.35)
    mix(out, air_sweep(1.0, 500, 4000, 3, 61, 0.18), 0.5)
    return finalize(reverb(out, wet=0.32))


def b_event_complete():
    out = buf(2.5)
    for i, m in enumerate((69, 72, 76, 81, 84)):
        bell(out, note(m), i * 0.09, 1.5, 0.34)
    bell(out, note(45), 0.0, 1.8, 0.3)  # low anchor
    mix(out, air_sweep(1.4, 2000, 8000, 5, 71, 0.12), 0.3)
    return finalize(reverb(out, wet=0.4))


def b_event_fail():
    out = buf(2.0)
    for i, m in enumerate((69, 65)):  # A4 F4 — falls away
        tone = apply_env(sine(lambda t, m=m: note(m) * (1 - 0.06 * t), 1.0),
                         attack=0.01, decay_tau=0.55, sustain=0.0)
        mix(out, lp(tone, 2200 - 900 * i), 0.5, i * 0.45)
    mix(out, lp(sub_drop(1.4, 70, 24), 400), 0.7, 0.35)
    return finalize(reverb(out, wet=0.3))


def b_event_redeem():
    out = buf(1.0)
    rng = random.Random(81)
    for i in range(3):
        f = rng.uniform(4200, 6600)
        clink = apply_env(sine(f, 0.12), decay_tau=0.03, sustain=0.0)
        mix(out, clink, 0.5, 0.02 + i * 0.09 + rng.uniform(0, 0.02))
        clink2 = apply_env(sine(f * 2.76, 0.08), decay_tau=0.02, sustain=0.0)
        mix(out, clink2, 0.15, 0.02 + i * 0.09)
    bell(out, note(88), 0.32, 0.55, 0.3)
    return finalize(out)


def b_compass_ping():
    """Bone knock — the engine double-fires it for the lub-dub pulse."""
    out = buf(0.25)
    knock = apply_env(sine(lambda t: 210 - 320 * t, 0.09),
                      decay_tau=0.025, sustain=0.0)
    mix(out, lp(knock, 1400), 1.0)
    tick = apply_env(noise(0.015, 91), decay_tau=0.005, sustain=0.0)
    mix(out, hp(tick, 2500), 0.22)
    return finalize(out)


def b_compass_chime():
    """FM-ish glass bell; code sweeps pitch for elevation cues."""
    out = buf(1.2)
    car, mod = note(88), note(88) * 3.01
    n = len(out)
    for i in range(n):
        t = i / SR
        m = math.sin(2 * math.pi * mod * t) * math.exp(-t / 0.25) * 2.4
        out[i] = math.sin(2 * math.pi * car * t + m)
    out = apply_env(out, attack=0.004, decay_tau=0.35, sustain=0.0)
    bell(out, note(100), 0.0, 0.9, 0.12)
    return finalize(out)


def b_heart_beat():
    return finalize(heartbeat(0.9, lub_f=58, dub_f=44, wet=0.5, seed=101))


def b_infection_heartbeat():
    """The Bloom inside you — muffled, no air at all."""
    x = heartbeat(0.75, lub_f=52, dub_f=40, wet=0.0, seed=111)
    return finalize(lp(x, 300))


def b_layer_spread(seed):
    return finalize(squelch(0.65, seed, drips=random.Random(seed).randint(2, 4)))


def b_horde_spawn():
    """The Bloom calls the locals: formant shriek falling to a hiss."""
    out = buf(1.5)
    n = int(1.1 * SR)
    src = [0.0] * n
    for i in range(n):
        t = i / SR
        f = 1150 - 650 * (t / 1.1)
        ph = (f * t) % 1.0
        src[i] = (2 * ph - 1) + 0.5 * math.sin(2 * math.pi * f * 0.5 * t)
    mix(out, apply_env(bp_sweep(src, 1800, 700, 8), attack=0.02,
                     decay_tau=0.45, sustain=0.0), 0.8)
    breath = apply_env(bp(noise(1.4, 121), 900, 1.5), attack=0.3,
                       decay_tau=0.5, sustain=0.0)
    mix(out, breath, 0.4, 0.1)
    return finalize(reverb(out, wet=0.2))


def b_organism_roar():
    out = buf(2.8)
    mix(out, formant_growl(2.2, 55, 131, formants=(300, 780), trem_hz=8.0),
        0.7, 0.15)
    mix(out, apply_env(sine(lambda t: 42 - 8 * t, 2.4), attack=0.4,
                       decay_tau=1.0, sustain=0.0), 0.8, 0.15)
    mix(out, apply_env(lp(noise(2.4, 137), 900), attack=0.5,
                       decay_tau=1.0, sustain=0.0), 0.35, 0.1)
    return finalize(reverb(out, wet=0.3))


# ---------------------------------------------------------------- registry
# (event key, [relative ogg paths], builder-per-file or shared builder)
def _variants(builder, seeds):
    return [functools.partial(builder, s) for s in seeds]


REGISTRY = [
    ("block.expedition_terminal.activate",
     ["terminal/activate"], [b_terminal_activate]),
    ("block.expedition_terminal.refuse",
     ["terminal/refuse"], [b_terminal_refuse]),
    ("block.expedition_checkpoint.activate",
     ["checkpoint/activate"], [b_checkpoint_activate]),
    ("block.expedition_portal.open",
     ["portal/open"], [b_portal_open]),
    ("block.expedition_portal.hum",
     ["portal/hum"], [b_portal_hum]),
    ("block.expedition_portal.travel",
     ["portal/travel"], [b_portal_travel]),
    ("event.expedition.start",
     ["event/start"], [b_event_start]),
    ("event.expedition.complete",
     ["event/complete"], [b_event_complete]),
    ("event.expedition.fail",
     ["event/fail"], [b_event_fail]),
    ("event.expedition.redeem",
     ["event/redeem"], [b_event_redeem]),
    ("item.expedition_compass.ping",
     ["compass/ping"], [b_compass_ping]),
    ("item.expedition_compass.chime",
     ["compass/chime"], [b_compass_chime]),
    ("block.assimilation_heart.beat",
     ["heart/beat"], [b_heart_beat]),
    ("block.assimilation_layer.spread",
     ["layer/spread1", "layer/spread2", "layer/spread3"],
     _variants(b_layer_spread, (201, 202, 203))),
    ("event.infection.heartbeat",
     ["infection/heartbeat"], [b_infection_heartbeat]),
    ("event.horde.spawn",
     ["horde/spawn"], [b_horde_spawn]),
    ("event.organism.roar",
     ["organism/roar"], [b_organism_roar]),
]


def main():
    sounds_json = {}
    for event, paths, builders in REGISTRY:
        assert len(paths) == len(builders)
        for rel, build in zip(paths, builders):
            data = build()
            path = os.path.join(SOUNDS_DIR, rel + ".ogg")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            sf.write(path, data, SR, format="OGG", subtype="VORBIS")
            print(f"  {rel + '.ogg':<28} {len(data) / SR:5.2f}s")
        sounds_json[event] = {
            "sounds": [f"expeditions:{p}" for p in paths],
            "subtitle": f"subtitles.expeditions.{event}",
        }
    with open(os.path.join(ROOT, "sounds.json"), "w") as f:
        json.dump(sounds_json, f, indent=2, sort_keys=True)
        f.write("\n")
    print(f"\n{len(REGISTRY)} events -> sounds.json")


if __name__ == "__main__":
    main()
