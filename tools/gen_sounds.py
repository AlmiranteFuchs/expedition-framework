#!/usr/bin/env python3
"""Procedural sound generator for the expedition-framework asset set.

Every clip is synthesized from oscillators + filtered noise — no samples,
no licensing surface, deterministic per seed. Style rules:
  - HORROR FIRST. The Bloom is an infection eating dimensions and it
    WANTS you. No bells, no arpeggios, nothing bright. Everything is
    tolls, drones, breath, wet tearing, sub pressure.
  - two voices:
      CHARTER — dying machinery: low clunks, warbling detuned tones,
                tape hiss; institutional and already half-dead
      BLOOM   — sub-thumps, resonant squelch, formant shrieks, and
                whispered almost-speech (vowel-formant sweeps that
                nearly resolve into words — that is the point)
  - dread lives in dissonance: minor-second toll pairs, beating
    detuned drones, pitch that sags instead of resolving
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
# Vorbis decodes with intersample overshoot on sharp transients —
# keep ~3dB true-peak headroom or the codec clips what PCM wouldn't.
PEAK = 0.70  # ≈ -3 dBFS


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


# ------------------------------------------------------------- the voices
def toll(dst, freq, at, dur, gain, detune=0.006):
    """Funeral bell: inharmonic partials, deliberately mistuned, struck
    soft — resonance that feels wrong, not bright."""
    ratios = (1.0, 2.01, 2.74, 4.07, 5.43)
    amps = (1.0, 0.35, 0.5, 0.22, 0.1)
    for r, a in zip(ratios, amps):
        f = freq * r * (1 + detune)
        tone = apply_env(sine(f, dur), attack=0.01,
                         decay_tau=dur / (4.0 * r), sustain=0.0)
        mix(dst, tone, gain * a, at)
    thud = apply_env(lp(noise(0.1, int(freq)), 300), decay_tau=0.05,
                   sustain=0.0)
    mix(dst, thud, gain * 0.4, at)


def drone(dur, freqs, gain=1.0, wobble_hz=0.0, wobble_depth=0.0):
    """Beating detuned sines — close frequencies rubbing against each
    other is the cheapest dread there is."""
    out = buf(dur)
    for i, f in enumerate(freqs):
        if wobble_hz:
            f_fn = lambda t, f=f, ph=i: f * (1 + wobble_depth *
                                             math.sin(2 * math.pi *
                                                      wobble_hz * t + ph))
            tone = sine(f_fn, dur)
        else:
            tone = sine(f, dur)
        mix(out, tone, gain / len(freqs))
    return out


def breath(dur, seed, attack=0.35, cutoff=900, gain=1.0):
    """An exhale: shaped broadband noise, lowpassed into a hiss."""
    n = apply_env(noise(dur, seed), attack=attack, decay_tau=dur * 0.35,
                  sustain=0.0)
    return [s * gain for s in lp(n, cutoff)]


def whisper(dur, seed, gain=1.0):
    """Almost-speech: noise swept across vowel formant pairs. It should
    nearly say something — the Bloom reaching for your language.
    Formant pairs approximate 'oo -> ah -> ee -> ah' at ~300-900Hz."""
    n = noise(dur, seed)
    out = buf(dur)
    # two parallel formant bands drifting between vowel centers
    f1_path = (700, 500, 800, 600, 350)
    f2_path = (1200, 1700, 2400, 1500, 1100)
    seg = len(n) // len(f1_path)
    for k in range(len(f1_path)):
        a, b = k * seg, min(len(n), (k + 1) * seg)
        chunk = n[a:b]
        mix(out, bp(chunk, f1_path[k], 6.0), 0.8, a / SR)
        mix(out, bp(chunk, f2_path[k], 8.0), 0.5, a / SR)
    out = apply_env(out, attack=0.1, decay_tau=dur * 0.4, sustain=0.0)
    # amplitude gating — syllable-ish stutter
    rng = random.Random(seed)
    n2 = len(out)
    rate = rng.uniform(5.0, 8.0)
    for i in range(n2):
        t = i / SR
        gate = 0.35 + 0.65 * max(0.0, math.sin(2 * math.pi * rate * t +
                                             rng.uniform(0, 0.4)))
        out[i] *= gate
    return [s * gain for s in out]


def creak(dur, f_lo, f_hi, seed, gain=1.0):
    """Living-wood groan: a jittery pitch-rising saw through a resonant
    band — fibers under load."""
    rng = random.Random(seed)
    n = int(dur * SR)
    src = [0.0] * n
    ph = 0.0
    for i in range(n):
        t = i / SR
        f = f_lo + (f_hi - f_lo) * (t / dur) + rng.uniform(-18, 18)
        ph += 2 * math.pi * f / SR
        src[i] = math.sin(ph) + 0.35 * math.sin(2 * ph)
    return [s * gain for s in bp_sweep(src, 350, 800, 5.0)]


def wood_knock(dst, at, gain=1.0, freq=400, seed=301):
    """Short woody impact — branch, not board."""
    k = apply_env(sine(lambda t: freq - freq * 0.6 * t, 0.07),
                  decay_tau=0.025, sustain=0.0)
    mix(dst, lp(k, freq * 2.5), gain, at)
    tick = apply_env(bp(noise(0.03, seed), freq * 2, 3.0),
                     decay_tau=0.012, sustain=0.0)
    mix(dst, tick, gain * 0.4, at)


def clunk(dst, at, gain=1.0, freq=90):
    """Dead mechanical impact — the Charter's machines still moving."""
    k = apply_env(sine(lambda t: freq - freq * 0.5 * t, 0.14),
                  decay_tau=0.06, sustain=0.0)
    mix(dst, lp(k, 700), gain, at)
    tick = apply_env(noise(0.02, int(freq * 7)), decay_tau=0.008,
                     sustain=0.0)
    mix(dst, lp(tick, 2500), gain * 0.3, at)


def warble(freq, dur, vib_hz=7.0, vib_depth=0.02, seed=3):
    """A tone that can't hold pitch — warbling like a dying radio."""
    rng = random.Random(seed)
    drift = rng.uniform(-0.03, -0.08)
    return sine(lambda t: freq * (1 + drift * t +
                                  vib_depth * math.sin(2 * math.pi *
                                                       vib_hz * t)), dur)


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
        f = rng.uniform(220, 620)
        drip = apply_env(sine(lambda tt, f=f: f * math.exp(-1.3 * tt), 0.1),
                         decay_tau=0.035, sustain=0.0)
        mix(out, lp(drip, 1200), rng.uniform(0.15, 0.3), t)
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
    """Dying machine acknowledging you: clunk, warbling tone, hiss."""
    out = buf(0.9)
    clunk(out, 0.0, gain=0.9, freq=85)
    w = apply_env(warble(104, 0.5, vib_hz=4.0, vib_depth=0.012), attack=0.02,
                  decay_tau=0.22, sustain=0.0)
    mix(out, lp(w, 1200), 0.4, 0.1)
    mix(out, lp(noise(0.4, 12), 1400), 0.12, 0.12)
    return finalize(out)


def b_terminal_refuse():
    """Two dead thuds and a low wrong-note buzz. No."""
    out = buf(0.7)
    clunk(out, 0.0, gain=0.9, freq=70)
    clunk(out, 0.22, gain=0.7, freq=60)
    buzz = apply_env(lp([2 * ((60 * i / SR) % 1.0) - 1 for i in
                         range(int(0.3 * SR))], 300),
                   attack=0.01, decay_tau=0.12, sustain=0.0)
    mix(out, buzz, 0.35, 0.05)
    return finalize(out)


def b_checkpoint_activate():
    """The witness stamps its answer: a ground-shaking stomp, wood
    cracking under the weight, a rumble rolling out, and then one low
    restrained note — the Charter's instrument, not a fanfare."""
    out = buf(2.0)
    impact = apply_env(sine(lambda t: 78 - 48 * t, 0.22),
                       decay_tau=0.08, sustain=0.0)
    mix(out, lp(impact, 320), 1.0)
    crack = apply_env(bp(noise(0.1, 201), 750, 2.0), decay_tau=0.035,
                    sustain=0.0)
    mix(out, crack, 0.6, 0.012)
    rumble = apply_env(lp(noise(0.7, 203), 150), attack=0.02,
                     decay_tau=0.3, sustain=0.0)
    mix(out, rumble, 0.5, 0.05)
    toll(out, note(45), 0.28, 1.5, 0.28)  # one low held answer
    return finalize(reverb(out, wet=0.35))


def b_portal_open():
    """Something opens its throat: sub thoom + wet gape + drone."""
    out = buf(2.1)
    mix(out, sub_drop(1.3, 85, 24, 0.9))
    gape = squelch(1.0, 33, drips=2, f_sweep=(500, 140))
    mix(out, gape, 0.5, 0.25)
    mix(out, apply_env(drone(1.6, (55, 55 * 2 ** (1 / 12), 82.5)),
                       attack=0.5, decay_tau=0.9, sustain=0.0), 0.35, 0.3)
    mix(out, whisper(1.0, 35, gain=0.18), 0.7)
    return finalize(reverb(out, wet=0.4))


def b_portal_hum():
    """Loopable ~3.2s dread bed: beating detuned subs, a slow air
    band, and a faint whisper that never quite forms words."""
    dur = 3.2
    n = int(dur * SR)
    out = buf(dur)
    # integer-cycle carriers so the loop point is seamless; the 3Hz
    # beat between 40 and 43Hz is the unease
    for f, g in ((40.0, 0.5), (43.0, 0.4), (80.0, 0.22), (121.0, 0.08)):
        cycles = round(f * dur)
        cyc_f = cycles / dur
        for i in range(n):
            out[i] += g * math.sin(2 * math.pi * cyc_f * i / n)
    lfo = [0.6 + 0.4 * math.sin(2 * math.pi * (2 / dur) * i / n)
           for i in range(n)]
    air = lp(bp(noise(dur, 41), 900, 1.2), 2200)
    for i in range(n):
        out[i] += air[i] * 0.22 * lfo[i]
    # whisper tucked under — looped so it stays seamless
    w = whisper(dur, 43, gain=0.1)
    for i in range(n):
        out[i] += w[i]
    return finalize(out)


def b_portal_travel():
    """It takes you: a long suck-in that snaps to a wet gulp, then the
    world lands wrong (muffled boom, no air)."""
    out = buf(1.8)
    suck = air_sweep(0.85, 300, 4200, 3.5, 51, 0.5)
    suck = apply_env(suck, attack=0.7, decay_tau=0.12, sustain=0.0)
    mix(out, suck)
    gulp = squelch(0.35, 53, drips=1, f_sweep=(400, 120))
    mix(out, gulp, 0.7, 0.8)
    mix(out, lp(sub_drop(0.6, 70, 26), 350), 0.8, 0.85)
    mix(out, breath(0.6, 55, attack=0.15, cutoff=500), 0.4, 0.95)
    return finalize(reverb(out, wet=0.25))


def b_event_start():
    """Departure: a door closing somewhere you'll never see. Deep boom,
    a cluster of high detuned sines (barely audible, deeply wrong),
    heartbeat underneath — then silence decides."""
    out = buf(2.3)
    mix(out, sub_drop(1.4, 70, 22, 0.9))
    mix(out, apply_env(drone(1.8, (522.0, 522.0 * 2 ** (1 / 12), 615.0),
                             wobble_hz=0.7, wobble_depth=0.004),
                       attack=0.5, decay_tau=1.0, sustain=0.0), 0.16, 0.3)
    mix(out, heartbeat(1.4, lub_f=50, dub_f=40, wet=0.0, seed=57), 0.35, 0.2)
    return finalize(reverb(out, wet=0.35))


def b_event_complete():
    """You got out. That is all this sting is allowed to say: one deep
    toll, a long exhale, and the whisper follows you home."""
    out = buf(2.6)
    toll(out, note(33), 0.0, 2.0, 0.75)
    mix(out, breath(2.0, 63, attack=0.35, cutoff=600), 0.55, 0.3)
    mix(out, whisper(1.1, 67, gain=0.12), 1.3)
    return finalize(reverb(out, wet=0.45))


def b_event_fail():
    """Everything sags. The pitch gives up mid-note — a machine dying
    with you — then the Bloom grunts from very far away."""
    out = buf(2.2)
    sag = apply_env(warble(138, 1.6, vib_hz=5.5, vib_depth=0.03, seed=69),
                    attack=0.01, decay_tau=0.7, sustain=0.0)
    mix(out, lp(sag, 900), 0.5)
    mix(out, lp(sub_drop(1.5, 65, 20), 300), 0.7, 0.3)
    grunt = formant_growl(0.7, 70, 71, formants=(250, 600), trem_hz=11.0)
    mix(out, lp(grunt, 900), 0.4, 1.1)
    return finalize(reverb(out, wet=0.3))


def b_event_redeem():
    """Bone rattle: knuckle-bones shaken in a fist, then a dead clunk."""
    out = buf(0.95)
    rng = random.Random(81)
    for i in range(5):
        t = 0.02 + i * 0.07 + rng.uniform(0, 0.03)
        f = rng.uniform(350, 800)
        knock = apply_env(bp(noise(0.05, 80 + i), f, 4.0),
                          decay_tau=0.02, sustain=0.0)
        mix(out, knock, rng.uniform(0.3, 0.55), t)
    clunk(out, 0.42, gain=0.7, freq=75)
    return finalize(out)


def b_compass_ping():
    """Bone knocked on a coffin lid — hollower than a clean tok.
    The engine double-fires it for the lub-dub pulse."""
    out = buf(0.3)
    knock = apply_env(sine(lambda t: 180 - 260 * t, 0.1),
                      decay_tau=0.03, sustain=0.0)
    mix(out, lp(knock, 1100), 1.0)
    res = apply_env(sine(255, 0.12), decay_tau=0.05, sustain=0.0)
    mix(out, lp(res, 800), 0.2, 0.015)
    tick = apply_env(noise(0.015, 91), decay_tau=0.005, sustain=0.0)
    mix(out, hp(tick, 2500), 0.15)
    return finalize(out)


def b_compass_chime():
    """Not a chime — a voice humming one note wrong. Breath around a
    slow-vibrato tone; the code sweeps pitch for elevation cues."""
    out = buf(1.3)
    v = apply_env(warble(183, 1.1, vib_hz=3.5, vib_depth=0.012, seed=93),
                  attack=0.25, decay_tau=0.4, sustain=0.0)
    mix(out, lp(v, 1600), 0.5)
    mix(out, apply_env(bp(noise(1.1, 95), 1400, 1.5), attack=0.3,
                     decay_tau=0.4, sustain=0.0), 0.25)
    mix(out, whisper(0.8, 97, gain=0.15), 0.25)
    return finalize(out)


def b_heart_beat():
    return finalize(heartbeat(0.9, lub_f=58, dub_f=44, wet=0.5, seed=101))


def b_infection_heartbeat():
    """The Bloom inside you — muffled, no air, and something moving
    between the beats."""
    x = heartbeat(0.75, lub_f=52, dub_f=40, wet=0.25, seed=111)
    mix(x, squelch(0.4, 113, drips=1, f_sweep=(350, 120)), 0.15, 0.35)
    return finalize(lp(x, 300))


def b_layer_spread(seed):
    """Wet slither plus flesh tearing — the colony reaching."""
    rng = random.Random(seed)
    out = squelch(0.7, seed, drips=rng.randint(2, 4))
    tear = apply_env(bp_sweep(noise(0.4, seed + 1), 2500, 600, 3.0),
                     attack=0.02, decay_tau=0.15, sustain=0.0)
    mix(out, tear, 0.3, rng.uniform(0.05, 0.25))
    return finalize(out)


def b_horde_spawn():
    """The Bloom calls the locals: a shriek that is trying to be a
    word, guttural breath, and the whisper riding under it all."""
    out = buf(1.6)
    n = int(1.1 * SR)
    src = [0.0] * n
    for i in range(n):
        t = i / SR
        f = 950 - 560 * (t / 1.1)
        ph = (f * t) % 1.0
        src[i] = (2 * ph - 1) + 0.5 * math.sin(2 * math.pi * f * 0.5 * t)
    mix(out, apply_env(bp_sweep(src, 1800, 700, 8), attack=0.02,
                     decay_tau=0.45, sustain=0.0), 0.8)
    b_ = apply_env(bp(noise(1.4, 121), 900, 1.5), attack=0.3,
                   decay_tau=0.5, sustain=0.0)
    mix(out, b_, 0.4, 0.1)
    mix(out, formant_growl(0.8, 85, 123, formants=(280, 700), trem_hz=13.0),
        0.4, 0.5)
    mix(out, whisper(1.2, 127, gain=0.22), 0.2)
    return finalize(reverb(out, wet=0.25))


def b_organism_roar():
    """Short and unbearable — the pressure event, not a monster showreel."""
    out = buf(1.8)
    mix(out, formant_growl(1.4, 55, 131, formants=(300, 780), trem_hz=8.0),
        0.7, 0.1)
    mix(out, apply_env(sine(lambda t: 42 - 8 * t, 1.6), attack=0.25,
                       decay_tau=0.7, sustain=0.0), 0.8, 0.1)
    mix(out, apply_env(lp(noise(1.6, 137), 900), attack=0.35,
                       decay_tau=0.7, sustain=0.0), 0.35, 0.05)
    return finalize(reverb(out, wet=0.3))


def _b_bloom_source(opening, seed):
    """The heart's membrane shutter — a wet peel, not a trapdoor."""
    out = buf(0.7)
    sq = squelch(0.55, seed, drips=1,
                 f_sweep=(250, 750) if opening else (750, 180))
    mix(out, sq, 0.9)
    if not opening:
        clunk(out, 0.32, gain=0.35, freq=110)
    return finalize(out)


def b_bloom_source_open():
    return _b_bloom_source(True, 211)


def b_bloom_source_close():
    return _b_bloom_source(False, 212)


def b_resonance_fuel():
    """A LIVING machine swallowing essence — gulp, then the beat
    moves into the casing."""
    out = buf(0.9)
    gulp = squelch(0.4, 221, drips=1, f_sweep=(500, 150))
    mix(out, gulp, 0.7)
    mix(out, heartbeat(0.6, lub_f=62, dub_f=50, wet=0.0, seed=223), 0.4, 0.25)
    return finalize(out)


def b_resonance_work():
    """Loopable operating thrum — beating detuned subs under a low
    rumble of moving parts, with an irregular laboring pulse. Machine
    noise, not a chord."""
    dur = 2.0
    n = int(dur * SR)
    out = buf(dur)
    # detuned carriers — the 4.5Hz beat is the labored breathing
    for f, g in ((52.0, 0.5), (56.5, 0.42), (104.0, 0.14), (127.0, 0.08)):
        cycles = round(f * dur)
        cyc_f = cycles / dur
        for i in range(n):
            out[i] += g * math.sin(2 * math.pi * cyc_f * i / n)
    # irregular pulse: two incommensurate LFOs multiplied
    rng = random.Random(225)
    ph2 = rng.uniform(0, 6)
    pulse = [max(0.0, math.sin(2 * math.pi * (3 / dur) * i / n)) *
             (0.5 + 0.5 * math.sin(2 * math.pi * (7 / dur) * i / n + ph2))
             for i in range(n)]
    rumble = lp(noise(dur, 227), 240)
    grind = lp(bp(noise(dur, 229), 480, 2.0), 900)
    for i in range(n):
        out[i] = out[i] * (0.55 + 0.45 * pulse[i]) + \
            rumble[i] * 0.35 + grind[i] * 0.1 * pulse[i]
    return finalize(out)


def b_resonance_complete():
    """Work done: a deep ring held too long, then a settling clunk."""
    out = buf(1.6)
    toll(out, note(52), 0.0, 1.2, 0.5)   # E3 — resonant, not bright
    clunk(out, 0.85, gain=0.5, freq=75)
    return finalize(reverb(out, wet=0.3))


def b_recall_answer():
    """The recall tree answers in the route instruments' register:
    two wood knocks and a low held ring."""
    out = buf(1.4)
    wood_knock(out, 0.0, gain=0.8, freq=330)
    wood_knock(out, 0.16, gain=0.6, freq=290)
    toll(out, note(45), 0.2, 1.0, 0.3)
    return finalize(reverb(out, wet=0.3))


def b_basin_reclaim():
    """Items dissolve into Return Sap — a busy bubbling dissolve,
    then the basin swallows."""
    out = buf(1.2)
    rng = random.Random(231)
    for i in range(9):
        t = 0.05 + i * 0.09 + rng.uniform(0, 0.05)
        f = rng.uniform(180, 480)
        drip = apply_env(sine(lambda tt, f=f: f * (1 - 1.1 * tt), 0.09),
                         decay_tau=0.035, sustain=0.0)
        mix(out, lp(drip, 1000), rng.uniform(0.15, 0.35), t)
    mix(out, squelch(0.6, 233, drips=2, f_sweep=(600, 200)), 0.5, 0.4)
    toll(out, note(45), 0.7, 0.8, 0.18)
    return finalize(out)


def b_wayroot_graft():
    """Sap drawn up through the graft — a creak settling under load,
    a wet draw, a short ring."""
    out = buf(1.1)
    mix(out, creak(0.5, 140, 260, 241), 0.5, 0.05)
    mix(out, squelch(0.45, 243, drips=2, f_sweep=(300, 650)), 0.4, 0.3)
    toll(out, note(52), 0.55, 0.7, 0.2)
    return finalize(out)


# ------------------------------------------------------- rootbound guardian
def b_guardian_windup():
    """Living wood drawn taut — rising creak, fibers ticking, about
    to release."""
    out = buf(0.8)
    mix(out, creak(0.6, 120, 320, 251), 0.8)
    rng = random.Random(253)
    for i in range(4):
        wood_knock(out, 0.1 + i * 0.1 + rng.uniform(0, 0.04),
                   gain=rng.uniform(0.15, 0.3), freq=rng.uniform(300, 520))
    mix(out, apply_env(lp(noise(0.5, 255), 400), attack=0.3,
                     decay_tau=0.3, sustain=0.0), 0.3, 0.15)
    return finalize(out)


def b_guardian_strike():
    """The blow lands: massive stomp through the ground, wood
    splintering, debris settling."""
    out = buf(1.4)
    impact = apply_env(sine(lambda t: 65 - 40 * t, 0.3),
                       decay_tau=0.12, sustain=0.0)
    mix(out, lp(impact, 280), 1.0)
    crack = apply_env(bp(noise(0.14, 261), 900, 2.0), decay_tau=0.05,
                    sustain=0.0)
    mix(out, crack, 0.7, 0.015)
    crack2 = apply_env(bp(noise(0.1, 263), 1600, 3.0), decay_tau=0.04,
                     sustain=0.0)
    mix(out, crack2, 0.35, 0.03)
    debris = apply_env(lp(noise(0.9, 265), 700), attack=0.05,
                     decay_tau=0.35, sustain=0.0)
    mix(out, debris, 0.35, 0.1)
    return finalize(reverb(out, wet=0.25))


def b_guardian_enrage():
    """Half dead means twice as angry: a creak torn up into a roar."""
    out = buf(1.7)
    mix(out, creak(0.5, 100, 300, 271), 0.6)
    mix(out, formant_growl(1.2, 70, 273, formants=(350, 900), trem_hz=10.0),
        0.7, 0.3)
    mix(out, apply_env(sine(lambda t: 48 - 10 * t, 1.4), attack=0.2,
                       decay_tau=0.6, sustain=0.0), 0.7, 0.3)
    mix(out, breath(1.2, 275, attack=0.4, cutoff=700), 0.4, 0.4)
    return finalize(reverb(out, wet=0.3))


def b_guardian_hurt(seed):
    """Bark splitting under the hit — short crack and groan."""
    rng = random.Random(seed)
    out = buf(0.45)
    crack = apply_env(bp(noise(0.1, seed), rng.uniform(900, 1500), 3.0),
                    decay_tau=0.04, sustain=0.0)
    mix(out, crack, 0.8)
    mix(out, creak(0.3, 150, rng.uniform(180, 260), seed + 1), 0.4, 0.05)
    return finalize(out)


def b_guardian_death():
    """The roots let go: a long splitting crack, the trunk-groan
    sagging, then everything comes down at once."""
    out = buf(2.6)
    mix(out, creak(1.2, 260, 60, 281), 0.7)
    crack = apply_env(bp(noise(0.3, 283), 1100, 2.0), decay_tau=0.12,
                    sustain=0.0)
    mix(out, crack, 0.8, 0.9)
    mix(out, lp(sub_drop(1.0, 80, 22), 300), 0.8, 1.0)
    mix(out, apply_env(lp(noise(1.2, 285), 500), attack=0.1,
                     decay_tau=0.5, sustain=0.0), 0.4, 1.1)
    mix(out, whisper(0.9, 287, gain=0.14), 1.2)
    return finalize(reverb(out, wet=0.35))


# -------------------------------------------------------- events / journal
def b_frontier_warning():
    """Something out there noticed. A pulse you feel more than hear,
    then the whisper — escalation, not alarm."""
    out = buf(1.8)
    for i, at in enumerate((0.0, 0.5)):
        pulse = apply_env(sine(lambda t: 55 - 8 * t, 0.3),
                          decay_tau=0.14, sustain=0.0)
        mix(out, lp(pulse, 200), 0.8 - 0.2 * i, at)
    mix(out, drone(1.2, (110, 110 * 2 ** (1 / 12))), 0.15, 0.4)
    mix(out, whisper(1.0, 291, gain=0.2), 0.6)
    return finalize(reverb(out, wet=0.3))


def b_journal_discovery():
    """The field journal stirs: pages turning on their own, and under
    the paper — the whisper reads along."""
    out = buf(1.2)
    for i, at in enumerate((0.0, 0.18)):
        swish = apply_env(hp(noise(0.14, 295 + i), 1800), attack=0.03,
                          decay_tau=0.05, sustain=0.0)
        mix(out, swish, 0.5 - 0.15 * i, at)
    mix(out, whisper(0.9, 297, gain=0.25), 0.3)
    return finalize(out)


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
    ("block.bloom_source.open",
     ["bloom_source/open"], [b_bloom_source_open]),
    ("block.bloom_source.close",
     ["bloom_source/close"], [b_bloom_source_close]),
    ("block.resonance_machine.fuel",
     ["resonance/fuel"], [b_resonance_fuel]),
    ("block.resonance_machine.work",
     ["resonance/work"], [b_resonance_work]),
    ("block.resonance_machine.complete",
     ["resonance/complete"], [b_resonance_complete]),
    ("block.recall_tree.answer",
     ["recall/answer"], [b_recall_answer]),
    ("block.reclaiming_basin.reclaim",
     ["basin/reclaim"], [b_basin_reclaim]),
    ("block.wayroot.graft",
     ["wayroot/graft"], [b_wayroot_graft]),
    ("entity.rootbound_guardian.windup",
     ["guardian/windup"], [b_guardian_windup]),
    ("entity.rootbound_guardian.strike",
     ["guardian/strike"], [b_guardian_strike]),
    ("entity.rootbound_guardian.enrage",
     ["guardian/enrage"], [b_guardian_enrage]),
    ("entity.rootbound_guardian.hurt",
     ["guardian/hurt1", "guardian/hurt2"],
     _variants(b_guardian_hurt, (271, 272))),
    ("entity.rootbound_guardian.death",
     ["guardian/death"], [b_guardian_death]),
    ("event.frontier.warning",
     ["frontier/warning"], [b_frontier_warning]),
    ("item.expedition_field_journal.discovery",
     ["journal/discovery"], [b_journal_discovery]),
]


def main():
    sounds_json = {}
    for event, paths, builders in REGISTRY:
        assert len(paths) == len(builders)
        for rel, build in zip(paths, builders):
            data = build()
            path = os.path.join(SOUNDS_DIR, rel + ".ogg")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            sf.write(path, data, SR, format="OGG", subtype="VORBIS",
                     compression_level=0.7)
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
