"""
Re-pack all WAV files to bare 16-bit PCM 48kHz with only fmt+data chunks.
Fixes LibGDX desktop loading failures caused by extra metadata chunks (JUNK, LIST, bext, etc.)
and unsupported bit depths (24-bit, 32-bit float).
"""
import wave, os, glob, struct

TARGET_RATE  = 48000
TARGET_WIDTH = 2  # 16-bit signed PCM

def read_samples_as_float(frames, width):
    n = len(frames) // width
    if width == 1:
        return [(b / 128.0 - 1.0) for b in frames]
    elif width == 2:
        return [s / 32768.0 for s in struct.unpack_from(f'<{n}h', frames)]
    elif width == 3:
        out = []
        for i in range(0, len(frames), 3):
            b = frames[i:i+3]
            v = struct.unpack('<i', b + (b'\xff' if b[2] & 0x80 else b'\x00'))[0]
            out.append(v / 8388608.0)
        return out
    elif width == 4:
        # Try float first (32-bit float is the most common DAW export)
        floats = struct.unpack_from(f'<{n}f', frames)
        if all(-2.0 < f < 2.0 for f in floats[:min(100, n)]):
            return list(floats)
        return [v / 2147483648.0 for v in struct.unpack_from(f'<{n}i', frames)]
    else:
        raise ValueError(f"Unsupported sample width: {width} bytes")

def resample_channel(samples, src_rate, dst_rate):
    if src_rate == dst_rate:
        return samples
    ratio = src_rate / dst_rate
    length = int(len(samples) / ratio)
    out = []
    for i in range(length):
        pos = i * ratio
        lo = int(pos)
        hi = min(lo + 1, len(samples) - 1)
        frac = pos - lo
        out.append(samples[lo] * (1 - frac) + samples[hi] * frac)
    return out

def convert(path):
    with wave.open(path, 'rb') as src:
        channels = src.getnchannels()
        width    = src.getsampwidth()
        rate     = src.getframerate()
        frames   = src.readframes(src.getnframes())

    already_ok = (width == TARGET_WIDTH and rate == TARGET_RATE)
    status = "re-packing (strip metadata)" if already_ok else f"{width*8}-bit {rate}Hz -> 16-bit {TARGET_RATE}Hz"
    print(f"  {status}: {os.path.basename(path)}")

    samples = read_samples_as_float(frames, width)

    if channels > 1:
        ch = [resample_channel(samples[c::channels], rate, TARGET_RATE) for c in range(channels)]
        min_len = min(len(c) for c in ch)
        resampled = [s for i in range(min_len) for s in [c[i] for c in ch]]
    else:
        resampled = resample_channel(samples, rate, TARGET_RATE)

    out_bytes = bytearray()
    for v in resampled:
        v = max(-1.0, min(1.0, v))
        out_bytes += struct.pack('<h', int(v * 32767))

    tmp = path + ".tmp.wav"
    with wave.open(tmp, 'wb') as dst:
        dst.setnchannels(channels)
        dst.setsampwidth(TARGET_WIDTH)
        dst.setframerate(TARGET_RATE)
        dst.writeframes(bytes(out_bytes))

    os.replace(tmp, path)

files = glob.glob("assets/sounds/**/*.wav", recursive=True)
print(f"Found {len(files)} WAV files - stripping to bare fmt+data chunks\n")
ok = 0
failed = 0
for f in files:
    try:
        convert(f)
        ok += 1
    except Exception as e:
        print(f"  FAILED {f}: {e}")
        failed += 1
print(f"\nDone. {ok} converted, {failed} failed.")
