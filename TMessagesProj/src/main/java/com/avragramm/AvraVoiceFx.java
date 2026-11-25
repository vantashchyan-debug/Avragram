package com.avragramm;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.ShortBuffer;

/**
 * Real-time voice changer on interleaved 16-bit PCM, in place.
 * One instance per audio stream (voice message, round video, call microphone).
 */
public final class AvraVoiceFx {

    public static final int OFF = 0, BASS = 1, HELIUM = 2, ROBOT = 3, ECHO = 4, RADIO = 5, DEMON = 6, ANON = 7, CHIPMUNK = 8, GIANT = 9;
    public static final String[] NAMES = {"Без эффекта", "Бас", "Хелий", "Робот", "Эхо", "Рация", "Демон", "Аноним", "Бурундук", "Великан"};

    private int sampleRate;
    private int preset = -1;
    private int pitchSemis = Integer.MIN_VALUE;

    private float[] psBuf;
    private int psWrite;
    private float psPhase;
    private float psRatio = 1f;
    private int psWindow;

    private float[] dlBuf;
    private int dlWrite;
    private double ringPhase;
    private float hpPrevIn, hpPrevOut, lp1, lp2;
    private short[] tmp = new short[0];

    public void reset() {
        preset = -1;
        pitchSemis = Integer.MIN_VALUE;
    }

    public static boolean isActive(int preset, int pitch) {
        return preset != OFF || pitch != 0;
    }

    /** Processes {@code bytes} bytes of 16-bit PCM starting at index 0 of {@code buf}. */
    public void process(ByteBuffer buf, int bytes, int sampleRate, int channels, int preset, int extraPitch) {
        if (!isActive(preset, extraPitch) || bytes <= 0 || channels <= 0 || buf == null) return;
        configure(sampleRate, preset, presetPitch(preset) + extraPitch);

        ByteBuffer d = buf.duplicate();
        d.clear();
        d.limit(Math.min(bytes, d.capacity()));
        ShortBuffer sb = d.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
        int total = sb.remaining();
        if (tmp.length < total) tmp = new short[total];
        sb.get(tmp, 0, total);
        process(tmp, total, channels);
        sb.position(0);
        sb.put(tmp, 0, total);
    }

    /** Same for a short[] buffer (used by recorders that read into arrays). */
    public void process(short[] data, int samples, int sampleRate, int channels, int preset, int extraPitch) {
        if (!isActive(preset, extraPitch) || samples <= 0 || channels <= 0 || data == null) return;
        configure(sampleRate, preset, presetPitch(preset) + extraPitch);
        process(data, samples, channels);
    }

    private void process(short[] data, int total, int channels) {
        int frames = total / channels;
        for (int f = 0; f < frames; f++) {
            int base = f * channels;
            float x = 0;
            for (int c = 0; c < channels; c++) x += data[base + c];
            x = x / channels / 32768f;
            float y = processSample(x);
            if (y > 1f) y = 1f; else if (y < -1f) y = -1f;
            short s = (short) (y * 32767f);
            for (int c = 0; c < channels; c++) data[base + c] = s;
        }
    }

    private static int presetPitch(int p) {
        switch (p) {
            case BASS: return -5;
            case HELIUM: return 7;
            case DEMON: return -9;
            case ANON: return -4;
            case CHIPMUNK: return 12;
            case GIANT: return -12;
            default: return 0;
        }
    }

    private void configure(int sr, int p, int semis) {
        if (sr <= 0) sr = 48000;
        if (sr == sampleRate && p == preset && semis == pitchSemis) return;
        boolean srChanged = sr != sampleRate;
        sampleRate = sr;
        preset = p;
        pitchSemis = semis;
        psRatio = (float) Math.pow(2.0, semis / 12.0);
        psWindow = Math.max(64, (int) (sr * 0.045f));
        if (srChanged || psBuf == null || psBuf.length < psWindow * 2 + 4) {
            psBuf = new float[psWindow * 2 + 4];
            psWrite = 0;
            psPhase = 0;
            dlBuf = new float[sr];
            dlWrite = 0;
            hpPrevIn = hpPrevOut = lp1 = lp2 = 0;
        }
    }

    private float processSample(float x) {
        float y = pitchSemis != 0 ? pitchShift(x) : x;
        switch (preset) {
            case ROBOT:
                y = ring(y, 70.0, 1.0f);
                y = comb(y, 0.006f, 0.55f, 1.0f);
                break;
            case ECHO:
                y = comb(y, 0.22f, 0.38f, 0.5f);
                break;
            case RADIO:
                y = radio(y);
                break;
            case DEMON:
                y = softClip(y * 1.8f) * 0.8f;
                y = comb(y, 0.012f, 0.3f, 0.35f);
                break;
            case ANON:
                y = ring(y, 28.0, 0.35f);
                break;
        }
        return y;
    }

    private float pitchShift(float x) {
        int n = psBuf.length;
        psBuf[psWrite] = x;
        float w = psWindow;
        psPhase += 1f - psRatio;
        while (psPhase < 0) psPhase += w;
        while (psPhase >= w) psPhase -= w;
        float d1 = psPhase;
        float d2 = psPhase + w * 0.5f;
        if (d2 >= w) d2 -= w;
        float g1 = (float) Math.sin(Math.PI * d1 / w);
        g1 *= g1;
        float out = tap(d1 + 1, n) * g1 + tap(d2 + 1, n) * (1f - g1);
        psWrite = (psWrite + 1) % n;
        return out;
    }

    private float tap(float delay, int n) {
        float pos = psWrite - delay;
        while (pos < 0) pos += n;
        int i0 = (int) pos;
        float frac = pos - i0;
        return psBuf[i0 % n] * (1 - frac) + psBuf[(i0 + 1) % n] * frac;
    }

    private float ring(float x, double freq, float mix) {
        ringPhase += 2 * Math.PI * freq / sampleRate;
        if (ringPhase > 2 * Math.PI) ringPhase -= 2 * Math.PI;
        return x * (1 - mix) + x * (float) Math.sin(ringPhase) * mix;
    }

    private float comb(float x, float seconds, float feedback, float mix) {
        int n = dlBuf.length;
        int d = Math.min(n - 1, Math.max(1, (int) (seconds * sampleRate)));
        int r = dlWrite - d;
        if (r < 0) r += n;
        float delayed = dlBuf[r];
        dlBuf[dlWrite] = x + delayed * feedback;
        dlWrite = (dlWrite + 1) % n;
        return x + delayed * mix;
    }

    private float radio(float x) {
        float dt = 1f / sampleRate;
        float rc = 1f / (2f * (float) Math.PI * 300f);
        float a = rc / (rc + dt);
        float hp = a * (hpPrevOut + x - hpPrevIn);
        hpPrevIn = x;
        hpPrevOut = hp;
        float b = dt / (1f / (2f * (float) Math.PI * 3000f) + dt);
        lp1 += b * (hp - lp1);
        lp2 += b * (lp1 - lp2);
        return softClip(lp2 * 4f) * 0.7f;
    }

    private static float softClip(float x) {
        if (x > 1f) return 2f / 3f;
        if (x < -1f) return -2f / 3f;
        return x - x * x * x / 3f;
    }
}
