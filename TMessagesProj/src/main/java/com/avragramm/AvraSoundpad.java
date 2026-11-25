package com.avragramm;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.net.Uri;
import android.provider.OpenableColumns;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.messenger.voip.VoIPService;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Soundpad for calls and voice chats. Sounds are mixed into the microphone stream right where the
 * voice changer works (WebRtcAudioRecord), so the other side hears them; outside a call a sound is
 * previewed through the speaker. Format of every sound: raw PCM, 16-bit LE, mono, 48 kHz.
 *
 * Built-in sounds live in assets/soundpad (synthesized by Tools/avra_soundpad.py). The user's own
 * sounds are decoded from any audio file Android can read, trimmed to {@link #MAX_SECONDS} and kept in
 * files/avra_soundpad, with names in the "avragramm" preferences.
 */
public final class AvraSoundpad {

    public static final int SAMPLE_RATE = 48000;
    public static final int MAX_SECONDS = 30;
    /** Request code used with LaunchActivity for the file picker. */
    public static final int REQUEST_PICK = 47301;

    private static final String KEY_CUSTOM = "soundpad.custom";
    private static final String KEY_VOLUME = "soundpad.volume";

    public static final class Sound {
        public final String id;      // built-in: asset name, custom: uuid
        public final String emoji;
        public String name;          // custom only
        public final int titleRes;   // built-in only
        public final boolean custom;

        Sound(String id, String emoji, int titleRes) {
            this.id = id;
            this.emoji = emoji;
            this.titleRes = titleRes;
            this.custom = false;
        }

        Sound(String id, String name) {
            this.id = id;
            this.emoji = "🎵";
            this.name = name;
            this.titleRes = 0;
            this.custom = true;
        }

        public String title() {
            return custom ? name : org.telegram.messenger.LocaleController.getString(titleRes);
        }
    }

    public static final Sound[] BUILTIN = {
            new Sound("airhorn", "📯", R.string.AvraSndAirhorn),
            new Sound("rimshot", "🥁", R.string.AvraSndRimshot),
            new Sound("fail", "🎺", R.string.AvraSndFail),
            new Sound("fanfare", "🏆", R.string.AvraSndFanfare),
            new Sound("beep", "🤐", R.string.AvraSndBeep),
            new Sound("siren", "🚨", R.string.AvraSndSiren),
            new Sound("drumroll", "🪘", R.string.AvraSndDrumroll),
            new Sound("crickets", "🦗", R.string.AvraSndCrickets),
            new Sound("laser", "🔫", R.string.AvraSndLaser),
            new Sound("ding", "🔔", R.string.AvraSndDing),
            new Sound("boing", "🪀", R.string.AvraSndBoing),
            new Sound("applause", "👏", R.string.AvraSndApplause),
    };

    /** What the audio thread plays now. Replaced atomically; never mutated after publishing except pos. */
    private static final class Playback {
        final String id;
        final short[] pcm;
        double pos;

        Playback(String id, short[] pcm) {
            this.id = id;
            this.pcm = pcm;
        }
    }

    private static volatile Playback playing;
    private static AudioTrack preview;
    private static short[] mixTmp = new short[0];
    /** Small cache of decoded sounds (built-ins are ~200 KB each). */
    private static final Map<String, short[]> cache = Collections.synchronizedMap(new LinkedHashMap<String, short[]>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, short[]> e) {
            return size() > 8;
        }
    });

    /** Called on the UI thread when the playing sound changes / ends (for the sheet). */
    public interface Listener {
        void onSoundpadChanged();
    }

    private static Listener listener;

    private AvraSoundpad() {}

    public static void setListener(Listener l) {
        listener = l;
    }

    private static void notifyChanged() {
        AndroidUtilities.runOnUIThread(() -> {
            if (listener != null) listener.onSoundpadChanged();
        });
    }

    // ------------------------------------------------------------------------------------ settings

    private static SharedPreferences prefs() {
        return AvraConfig.prefs();
    }

    /** Soundpad volume in percent (10..200). */
    public static int volume() {
        return Math.max(10, Math.min(200, prefs().getInt(KEY_VOLUME, 100)));
    }

    public static void setVolume(int v) {
        prefs().edit().putInt(KEY_VOLUME, Math.max(10, Math.min(200, v))).apply();
    }

    public static boolean inCall() {
        VoIPService s = VoIPService.getSharedInstance();
        return s != null && s.getCallState() != VoIPService.STATE_ENDED;
    }

    // ------------------------------------------------------------------------------------ list

    public static List<Sound> all() {
        ArrayList<Sound> list = new ArrayList<>();
        Collections.addAll(list, BUILTIN);
        list.addAll(custom());
        return list;
    }

    public static List<Sound> custom() {
        ArrayList<Sound> list = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(prefs().getString(KEY_CUSTOM, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String id = o.optString("id");
                if (!id.isEmpty() && file(id).exists()) list.add(new Sound(id, o.optString("name", "?")));
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return list;
    }

    private static void saveCustom(List<Sound> list) {
        JSONArray a = new JSONArray();
        try {
            for (Sound s : list) a.put(new JSONObject().put("id", s.id).put("name", s.name));
        } catch (Throwable e) {
            FileLog.e(e);
        }
        prefs().edit().putString(KEY_CUSTOM, a.toString()).apply();
    }

    private static File dir() {
        File d = new File(ApplicationLoader.applicationContext.getFilesDir(), "avra_soundpad");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File file(String id) {
        return new File(dir(), id + ".pcm");
    }

    public static void rename(Sound s, String name) {
        if (!s.custom || name == null || name.trim().isEmpty()) return;
        List<Sound> list = custom();
        for (Sound c : list) if (c.id.equals(s.id)) c.name = name.trim();
        saveCustom(list);
    }

    public static void delete(Sound s) {
        if (!s.custom) return;
        Playback p = playing;
        if (p != null && p.id.equals(s.id)) stop();
        List<Sound> list = custom();
        for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).id.equals(s.id)) list.remove(i);
        saveCustom(list);
        cache.remove(s.id);
        //noinspection ResultOfMethodCallIgnored
        file(s.id).delete();
    }

    // ------------------------------------------------------------------------------------ playback

    public static boolean isPlaying(Sound s) {
        Playback p = playing;
        return p != null && p.id.equals(s.id);
    }

    public static boolean isPlaying() {
        return playing != null;
    }

    /** Plays a sound into the call (or previews it on the speaker when there is no call). Tap again = stop. */
    public static void toggle(Sound s) {
        if (isPlaying(s)) {
            stop();
            return;
        }
        Utilities.globalQueue.postRunnable(() -> {
            short[] pcm = load(s);
            if (pcm == null || pcm.length == 0) return;
            stopPreview();
            if (inCall()) {
                playing = new Playback(s.id, pcm);
            } else {
                playing = new Playback(s.id, pcm);
                startPreview(pcm);
            }
            notifyChanged();
        });
    }

    public static void stop() {
        playing = null;
        stopPreview();
        notifyChanged();
    }

    private static short[] load(Sound s) {
        short[] c = cache.get(s.id);
        if (c != null) return c;
        try (InputStream in = s.custom ? new FileInputStream(file(s.id))
                : ApplicationLoader.applicationContext.getAssets().open("soundpad/" + s.id + ".pcm")) {
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] b = new byte[16384];
            int n;
            while ((n = in.read(b)) > 0) bos.write(b, 0, n);
            byte[] bytes = bos.toByteArray();
            short[] pcm = new short[bytes.length / 2];
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm);
            cache.put(s.id, pcm);
            return pcm;
        } catch (Throwable e) {
            FileLog.e(e);
            return null;
        }
    }

    private static synchronized void startPreview(short[] pcm) {
        try {
            float gain = volume() / 100f;
            short[] scaled = new short[pcm.length];
            for (int i = 0; i < pcm.length; i++) scaled[i] = clip(pcm[i] * gain);
            AudioTrack t = new AudioTrack.Builder()
                    .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                    .setAudioFormat(new AudioFormat.Builder().setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .setBufferSizeInBytes(scaled.length * 2)
                    .build();
            t.write(scaled, 0, scaled.length);
            t.setNotificationMarkerPosition(scaled.length);
            t.setPlaybackPositionUpdateListener(new AudioTrack.OnPlaybackPositionUpdateListener() {
                @Override
                public void onMarkerReached(AudioTrack track) {
                    AndroidUtilities.runOnUIThread(() -> {
                        if (preview == track) {
                            playing = null;
                            stopPreview();
                            notifyChanged();
                        }
                    });
                }

                @Override
                public void onPeriodicNotification(AudioTrack track) {
                }
            });
            preview = t;
            t.play();
        } catch (Throwable e) {
            FileLog.e(e);
            playing = null;
        }
    }

    private static synchronized void stopPreview() {
        AudioTrack t = preview;
        preview = null;
        if (t != null) {
            try {
                t.stop();
            } catch (Throwable ignore) {
            }
            t.release();
        }
    }

    private static short clip(float v) {
        return (short) Math.max(-32768, Math.min(32767, Math.round(v)));
    }

    /**
     * Mixes the playing sound into {@code bytes} bytes of 16-bit PCM in {@code buf} (call capture
     * thread). Works on a muted microphone too: the sound is sent instead of silence.
     */
    public static void mix(ByteBuffer buf, int bytes, int sampleRate, int channels) {
        Playback p = playing;
        if (p == null || buf == null || bytes <= 0 || channels <= 0 || sampleRate <= 0 || preview != null) return;
        try {
            ByteBuffer d = buf.duplicate();
            d.clear();
            d.limit(Math.min(bytes, d.capacity()));
            java.nio.ShortBuffer sb = d.slice().order(ByteOrder.LITTLE_ENDIAN).asShortBuffer();
            int total = sb.remaining();
            short[] tmp = mixTmp;
            if (tmp.length < total) mixTmp = tmp = new short[total];
            sb.get(tmp, 0, total);
            float gain = volume() / 100f;
            double step = (double) SAMPLE_RATE / sampleRate;
            int frames = total / channels;
            short[] pcm = p.pcm;
            double pos = p.pos;
            for (int f = 0; f < frames; f++) {
                int i = (int) pos;
                if (i >= pcm.length - 1) {
                    pos = pcm.length;
                    break;
                }
                float frac = (float) (pos - i);
                float s = (pcm[i] + (pcm[i + 1] - pcm[i]) * frac) * gain;
                for (int c = 0; c < channels; c++) {
                    int k = f * channels + c;
                    tmp[k] = clip(tmp[k] + s);
                }
                pos += step;
            }
            p.pos = pos;
            sb.position(0);
            sb.put(tmp, 0, total);
            if (pos >= pcm.length - 1 && playing == p) {
                playing = null;
                notifyChanged();
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    // ------------------------------------------------------------------------------------ import

    /** Opens the system file picker for an audio file; the result comes to {@link #onPicked}. */
    public static void pick(Activity activity) {
        if (activity == null) return;
        Intent i = new Intent(Intent.ACTION_GET_CONTENT);
        i.setType("audio/*");
        i.addCategory(Intent.CATEGORY_OPENABLE);
        try {
            activity.startActivityForResult(Intent.createChooser(i, null), REQUEST_PICK);
        } catch (Throwable e) {
            FileLog.e(e);
        }
    }

    public interface ImportCallback {
        void done(Sound sound, String error);
    }

    private static ImportCallback importCallback;

    public static void setImportCallback(ImportCallback cb) {
        importCallback = cb;
    }

    /** LaunchActivity.onActivityResult hook. Returns true if the result was ours. */
    public static boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != REQUEST_PICK) return false;
        if (resultCode != Activity.RESULT_OK || data == null || data.getData() == null) return true;
        Uri uri = data.getData();
        Utilities.globalQueue.postRunnable(() -> {
            Sound s = null;
            String error = null;
            try {
                s = importUri(uri);
            } catch (Throwable e) {
                FileLog.e(e);
                error = e.getMessage();
            }
            final Sound fs = s;
            final String fe = fs == null && error == null ? "decode" : error;
            AndroidUtilities.runOnUIThread(() -> {
                if (importCallback != null) importCallback.done(fs, fe);
                notifyChanged();
            });
        });
        return true;
    }

    private static Sound importUri(Uri uri) throws Exception {
        Context ctx = ApplicationLoader.applicationContext;
        String name = displayName(ctx, uri);
        short[] pcm = decode(ctx, uri);
        if (pcm == null || pcm.length == 0) return null;
        String id = UUID.randomUUID().toString();
        try (FileOutputStream out = new FileOutputStream(file(id))) {
            ByteBuffer bb = ByteBuffer.allocate(pcm.length * 2).order(ByteOrder.LITTLE_ENDIAN);
            bb.asShortBuffer().put(pcm);
            out.write(bb.array());
        }
        Sound s = new Sound(id, name);
        List<Sound> list = custom();
        list.add(s);
        saveCustom(list);
        return s;
    }

    private static String displayName(Context ctx, Uri uri) {
        String name = null;
        try (Cursor c = ctx.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) name = c.getString(0);
        } catch (Throwable ignore) {
        }
        if (name == null || name.isEmpty()) name = "Sound";
        int dot = name.lastIndexOf('.');
        if (dot > 0) name = name.substring(0, dot);
        return name.length() > 40 ? name.substring(0, 40) : name;
    }

    /** Decodes the first audio track to mono 48 kHz 16-bit, at most {@link #MAX_SECONDS}. */
    private static short[] decode(Context ctx, Uri uri) throws Exception {
        MediaExtractor ex = new MediaExtractor();
        MediaCodec codec = null;
        try {
            ex.setDataSource(ctx, uri, null);
            int track = -1;
            MediaFormat fmt = null;
            for (int i = 0; i < ex.getTrackCount(); i++) {
                MediaFormat f = ex.getTrackFormat(i);
                String mime = f.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    track = i;
                    fmt = f;
                    break;
                }
            }
            if (track < 0) return null;
            ex.selectTrack(track);
            int srcRate = fmt.getInteger(MediaFormat.KEY_SAMPLE_RATE);
            int srcCh = fmt.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
            codec = MediaCodec.createDecoderByType(fmt.getString(MediaFormat.KEY_MIME));
            codec.configure(fmt, null, null, 0);
            codec.start();

            int maxOut = SAMPLE_RATE * MAX_SECONDS;
            ShortList mono = new ShortList(SAMPLE_RATE * 4);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();
            boolean inputDone = false, outputDone = false;
            boolean floatPcm = false;
            long maxSrcFrames = (long) srcRate * MAX_SECONDS;
            long srcFrames = 0;
            while (!outputDone && srcFrames < maxSrcFrames) {
                if (!inputDone) {
                    int in = codec.dequeueInputBuffer(10_000);
                    if (in >= 0) {
                        ByteBuffer ib = codec.getInputBuffer(in);
                        int n = ib == null ? -1 : ex.readSampleData(ib, 0);
                        if (n < 0) {
                            codec.queueInputBuffer(in, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                            inputDone = true;
                        } else {
                            codec.queueInputBuffer(in, 0, n, ex.getSampleTime(), 0);
                            ex.advance();
                        }
                    }
                }
                int out = codec.dequeueOutputBuffer(info, 10_000);
                if (out == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    MediaFormat of = codec.getOutputFormat();
                    if (of.containsKey(MediaFormat.KEY_SAMPLE_RATE)) srcRate = of.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    if (of.containsKey(MediaFormat.KEY_CHANNEL_COUNT)) srcCh = of.getInteger(MediaFormat.KEY_CHANNEL_COUNT);
                    if (of.containsKey("pcm-encoding")) floatPcm = of.getInteger("pcm-encoding") == AudioFormat.ENCODING_PCM_FLOAT;
                    maxSrcFrames = (long) srcRate * MAX_SECONDS;
                } else if (out >= 0) {
                    ByteBuffer ob = codec.getOutputBuffer(out);
                    if (ob != null && info.size > 0) {
                        ob.position(info.offset);
                        ob.limit(info.offset + info.size);
                        ByteBuffer o = ob.slice().order(ByteOrder.nativeOrder());
                        int ch = Math.max(1, srcCh);
                        if (floatPcm) {
                            java.nio.FloatBuffer fb = o.asFloatBuffer();
                            while (fb.remaining() >= ch) {
                                float sum = 0;
                                for (int c = 0; c < ch; c++) sum += fb.get();
                                mono.add(clip(sum / ch * 32767f));
                                srcFrames++;
                            }
                        } else {
                            java.nio.ShortBuffer sbuf = o.asShortBuffer();
                            while (sbuf.remaining() >= ch) {
                                int sum = 0;
                                for (int c = 0; c < ch; c++) sum += sbuf.get();
                                mono.add((short) (sum / ch));
                                srcFrames++;
                            }
                        }
                    }
                    codec.releaseOutputBuffer(out, false);
                    if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) outputDone = true;
                }
            }
            short[] src = mono.toArray();
            if (src.length == 0) return null;
            // Resample to 48 kHz (linear) and normalise quiet files a little.
            int outLen = (int) Math.min(maxOut, (long) src.length * SAMPLE_RATE / Math.max(1, srcRate));
            short[] dst = new short[outLen];
            double step = (double) srcRate / SAMPLE_RATE;
            int peak = 1;
            for (int i = 0; i < outLen; i++) {
                double pos = i * step;
                int k = (int) pos;
                int a = src[Math.min(k, src.length - 1)], b = src[Math.min(k + 1, src.length - 1)];
                dst[i] = (short) (a + (b - a) * (pos - k));
                peak = Math.max(peak, Math.abs(dst[i]));
            }
            if (peak < 16000) {
                float g = 26000f / peak;
                for (int i = 0; i < outLen; i++) dst[i] = clip(dst[i] * g);
            }
            return dst;
        } finally {
            if (codec != null) {
                try {
                    codec.stop();
                } catch (Throwable ignore) {
                }
                codec.release();
            }
            ex.release();
        }
    }

    /** Growable short array without boxing. */
    private static final class ShortList {
        short[] a;
        int n;

        ShortList(int cap) {
            a = new short[cap];
        }

        void add(short v) {
            if (n == a.length) a = java.util.Arrays.copyOf(a, a.length * 2);
            a[n++] = v;
        }

        short[] toArray() {
            return java.util.Arrays.copyOf(a, n);
        }
    }
}
