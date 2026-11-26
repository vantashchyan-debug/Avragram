package com.avragramm.chess;

import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.Build;

import org.telegram.messenger.FileLog;
import org.telegram.messenger.Utilities;

import java.util.HashMap;
import java.util.Random;

/**
 * Synthesised AvraChess sounds (no audio files in the APK): wood, click, 8-bit, soft and cringe sets
 * for move, capture, check, castle, game end, low time and "opponent moved".
 */
public final class ChessSound {

    private ChessSound() {
    }

    public static final int MOVE = 0, CAPTURE = 1, CHECK = 2, CASTLE = 3, END = 4, LOW_TIME = 5, NOTIFY = 6, START = 7;
    public static final int OFF = 0, WOOD = 1, CLICK = 2, BIT8 = 3, SOFT = 4, CRINGE = 5;

    private static final int RATE = 22050;
    private static final HashMap<Integer, short[]> cache = new HashMap<>();

    public static void play(int event) {
        play(ChessStyle.sound(), event);
    }

    public static void play(int style, int event) {
        if (style == OFF) return;
        Utilities.globalQueue.postRunnable(() -> {
            try {
                short[] pcm;
                synchronized (cache) {
                    int key = style * 16 + event;
                    pcm = cache.get(key);
                    if (pcm == null) {
                        pcm = synth(style, event);
                        cache.put(key, pcm);
                    }
                }
                AudioTrack track;
                if (Build.VERSION.SDK_INT >= 23) {
                    track = new AudioTrack.Builder()
                            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
                                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                            .setAudioFormat(new AudioFormat.Builder().setSampleRate(RATE).setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.length * 2).build();
                } else {
                    track = new AudioTrack(android.media.AudioManager.STREAM_MUSIC, RATE, AudioFormat.CHANNEL_OUT_MONO,
                            AudioFormat.ENCODING_PCM_16BIT, pcm.length * 2, AudioTrack.MODE_STATIC);
                }
                track.write(pcm, 0, pcm.length);
                track.play();
                final AudioTrack t = track;
                Utilities.globalQueue.postRunnable(t::release, pcm.length * 1000L / RATE + 200);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        });
    }

    private static short[] synth(int style, int event) {
        switch (style) {
            case WOOD: return wood(event);
            case CLICK: return click(event);
            case BIT8: return bit8(event);
            case SOFT: return soft(event);
            default: return cringe(event);
        }
    }

    private static short[] buf(double seconds) {
        return new short[(int) (RATE * seconds)];
    }

    private static void put(short[] out, int i, double v) {
        v = Math.max(-1, Math.min(1, v));
        int s = out[i] + (int) (v * 30000);
        out[i] = (short) Math.max(-32767, Math.min(32767, s));
    }

    /** Knock: a filtered noise burst plus a low thump. */
    private static void knock(short[] out, int start, double freq, double gain, double decay) {
        Random r = new Random(7);
        double lp = 0;
        for (int i = start; i < out.length; i++) {
            double t = (i - start) / (double) RATE;
            double env = Math.exp(-t * decay);
            if (env < 0.001) break;
            lp += (r.nextDouble() * 2 - 1 - lp) * 0.35;
            double v = (lp * 0.9 + Math.sin(2 * Math.PI * freq * t) * 0.8) * env * gain;
            put(out, i, v);
        }
    }

    private static void tone(short[] out, int start, double f0, double f1, double len, double gain, int wave) {
        int n = (int) (len * RATE);
        double phase = 0;
        for (int k = 0; k < n && start + k < out.length; k++) {
            double t = k / (double) RATE;
            double f = f0 + (f1 - f0) * (k / (double) n);
            phase += 2 * Math.PI * f / RATE;
            double env = Math.min(1, k / (RATE * 0.004)) * Math.min(1, (n - k) / (RATE * 0.02));
            double s;
            if (wave == 1) s = Math.sin(phase) >= 0 ? 0.6 : -0.6; // square
            else if (wave == 2) s = Math.sin(phase) + 0.3 * Math.sin(phase * 2.01); // rich
            else s = Math.sin(phase);
            put(out, start + k, s * env * gain);
        }
    }

    private static int at(double sec) {
        return (int) (sec * RATE);
    }

    private static short[] wood(int e) {
        short[] o = buf(e == END || e == START ? 0.5 : 0.18);
        switch (e) {
            case CAPTURE: knock(o, 0, 150, 0.9, 30); knock(o, at(0.035), 210, 0.7, 40); break;
            case CASTLE: knock(o, 0, 170, 0.8, 35); knock(o, at(0.09), 170, 0.8, 35); break;
            case CHECK: knock(o, 0, 220, 0.9, 30); tone(o, 0, 880, 880, 0.12, 0.25, 0); break;
            case END:
            case START: tone(o, 0, 523, 523, 0.14, 0.4, 2); tone(o, at(0.15), 659, 659, 0.14, 0.4, 2); tone(o, at(0.3), 784, 784, 0.18, 0.4, 2); break;
            case LOW_TIME: tone(o, 0, 1200, 1200, 0.06, 0.4, 0); break;
            case NOTIFY: tone(o, 0, 740, 990, 0.12, 0.35, 0); break;
            default: knock(o, 0, 180, 0.85, 38);
        }
        return o;
    }

    private static short[] click(int e) {
        short[] o = buf(e == END || e == START ? 0.4 : 0.12);
        switch (e) {
            case CAPTURE: tone(o, 0, 2400, 2400, 0.012, 0.8, 1); tone(o, at(0.03), 1800, 1800, 0.012, 0.8, 1); break;
            case CASTLE: tone(o, 0, 2000, 2000, 0.012, 0.7, 1); tone(o, at(0.06), 2000, 2000, 0.012, 0.7, 1); break;
            case CHECK: tone(o, 0, 3000, 3000, 0.02, 0.8, 1); tone(o, at(0.04), 3000, 3000, 0.02, 0.8, 1); break;
            case END:
            case START: tone(o, 0, 1500, 1500, 0.02, 0.7, 1); tone(o, at(0.1), 2000, 2000, 0.02, 0.7, 1); tone(o, at(0.2), 2500, 2500, 0.03, 0.7, 1); break;
            case LOW_TIME: tone(o, 0, 3500, 3500, 0.015, 0.6, 1); break;
            case NOTIFY: tone(o, 0, 1600, 2600, 0.04, 0.5, 0); break;
            default: tone(o, 0, 2000, 2000, 0.012, 0.8, 1);
        }
        return o;
    }

    private static short[] bit8(int e) {
        short[] o = buf(e == END || e == START ? 0.6 : 0.2);
        switch (e) {
            case CAPTURE: tone(o, 0, 880, 220, 0.12, 0.5, 1); break;
            case CASTLE: tone(o, 0, 440, 440, 0.06, 0.5, 1); tone(o, at(0.07), 660, 660, 0.06, 0.5, 1); break;
            case CHECK: tone(o, 0, 990, 990, 0.05, 0.5, 1); tone(o, at(0.07), 990, 990, 0.05, 0.5, 1); break;
            case END:
            case START: tone(o, 0, 523, 523, 0.1, 0.45, 1); tone(o, at(0.12), 659, 659, 0.1, 0.45, 1); tone(o, at(0.24), 784, 784, 0.1, 0.45, 1); tone(o, at(0.36), 1046, 1046, 0.18, 0.45, 1); break;
            case LOW_TIME: tone(o, 0, 1320, 1320, 0.05, 0.4, 1); break;
            case NOTIFY: tone(o, 0, 660, 1320, 0.1, 0.4, 1); break;
            default: tone(o, 0, 660, 660, 0.05, 0.5, 1);
        }
        return o;
    }

    private static short[] soft(int e) {
        short[] o = buf(e == END || e == START ? 0.7 : 0.25);
        switch (e) {
            case CAPTURE: tone(o, 0, 392, 330, 0.18, 0.5, 0); break;
            case CASTLE: tone(o, 0, 440, 440, 0.1, 0.45, 0); tone(o, at(0.11), 554, 554, 0.12, 0.45, 0); break;
            case CHECK: tone(o, 0, 659, 659, 0.12, 0.5, 0); tone(o, at(0.1), 784, 784, 0.12, 0.4, 0); break;
            case END:
            case START: tone(o, 0, 392, 392, 0.2, 0.4, 0); tone(o, at(0.2), 494, 494, 0.2, 0.4, 0); tone(o, at(0.4), 587, 587, 0.28, 0.4, 0); break;
            case LOW_TIME: tone(o, 0, 880, 880, 0.08, 0.35, 0); break;
            case NOTIFY: tone(o, 0, 523, 784, 0.18, 0.35, 0); break;
            default: tone(o, 0, 523, 523, 0.12, 0.45, 0);
        }
        return o;
    }

    private static short[] cringe(int e) {
        short[] o = buf(e == END || e == START ? 0.9 : 0.35);
        switch (e) {
            case CAPTURE: tone(o, 0, 900, 120, 0.3, 0.6, 2); break; // "ой"
            case CASTLE: tone(o, 0, 200, 900, 0.15, 0.5, 2); tone(o, at(0.16), 900, 200, 0.15, 0.5, 2); break;
            case CHECK: tone(o, 0, 1500, 1500, 0.06, 0.6, 1); tone(o, at(0.08), 1500, 1500, 0.06, 0.6, 1); tone(o, at(0.16), 1500, 1500, 0.06, 0.6, 1); break;
            case END:
            case START: tone(o, 0, 300, 1200, 0.25, 0.5, 2); tone(o, at(0.28), 1200, 150, 0.5, 0.5, 2); break; // sad trombone-ish
            case LOW_TIME: tone(o, 0, 1800, 1700, 0.05, 0.5, 1); break;
            case NOTIFY: tone(o, 0, 250, 750, 0.2, 0.5, 2); break;
            default: tone(o, 0, 300, 900, 0.18, 0.55, 2); // boing
        }
        return o;
    }
}
