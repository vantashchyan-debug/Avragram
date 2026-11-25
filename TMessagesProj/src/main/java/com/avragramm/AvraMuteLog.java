package com.avragramm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.DispatchQueue;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;

/**
 * "Said behind your back": a local archive of what muted people wrote. AvraMute deletes their
 * messages for both sides on arrival; right before that the text (or a media label) is stored here,
 * per account, in "avra_behind_<account>.db". Nothing leaves the device.
 *
 * Writes go through a dedicated single-thread queue; reads are done by the screens on that queue too.
 */
public final class AvraMuteLog {

    public static final int KIND_TEXT = 0, KIND_PHOTO = 1, KIND_VIDEO = 2, KIND_VOICE = 3, KIND_ROUND = 4,
            KIND_STICKER = 5, KIND_GIF = 6, KIND_FILE = 7, KIND_MUSIC = 8, KIND_LOCATION = 9, KIND_CONTACT = 10,
            KIND_POLL = 11, KIND_DICE = 12, KIND_SERVICE = 13, KIND_OTHER = 14;

    /** Label of a media kind (string resource), 0 for plain text. */
    public static int kindLabel(int kind) {
        switch (kind) {
            case KIND_PHOTO: return R.string.AvraBehindPhoto;
            case KIND_VIDEO: return R.string.AvraBehindVideo;
            case KIND_VOICE: return R.string.AvraBehindVoice;
            case KIND_ROUND: return R.string.AvraBehindRound;
            case KIND_STICKER: return R.string.AvraBehindSticker;
            case KIND_GIF: return R.string.AvraBehindGif;
            case KIND_FILE: return R.string.AvraBehindFile;
            case KIND_MUSIC: return R.string.AvraBehindMusic;
            case KIND_LOCATION: return R.string.AvraBehindLocation;
            case KIND_CONTACT: return R.string.AvraBehindContact;
            case KIND_POLL: return R.string.AvraBehindPoll;
            case KIND_DICE: return R.string.AvraBehindDice;
            case KIND_SERVICE: return R.string.AvraBehindService;
            case KIND_OTHER: return R.string.AvraBehindOther;
            default: return 0;
        }
    }

    public static final class Entry {
        public long userId;
        public int mid;
        public int date;
        public int kind;
        /** Message text, caption, sticker emoji, file name or poll question; never null. */
        public String text;
    }

    public static final class Person {
        public long userId;
        public int count;
        public int lastDate;
        public String lastText;
        public int lastKind;
    }

    private static final AvraMuteLog[] instances = new AvraMuteLog[UserConfig.MAX_ACCOUNT_COUNT];
    public static final DispatchQueue queue = new DispatchQueue("avraBehind");

    public static AvraMuteLog get(int account) {
        AvraMuteLog i = instances[account];
        if (i == null) {
            synchronized (AvraMuteLog.class) {
                i = instances[account];
                if (i == null) instances[account] = i = new AvraMuteLog(account);
            }
        }
        return i;
    }

    private final Helper helper;

    private AvraMuteLog(int account) {
        helper = new Helper(account);
    }

    /** Stores a muted person's message (called before it is deleted). Safe from any thread. */
    public void store(TLRPC.Message m) {
        if (m == null || m.peer_id == null) return;
        final Entry e = new Entry();
        e.userId = m.peer_id.user_id;
        e.mid = m.id;
        e.date = m.date;
        e.kind = kindOf(m);
        e.text = textOf(m, e.kind);
        queue.postRunnable(() -> {
            try {
                ContentValues v = new ContentValues();
                v.put("user_id", e.userId);
                v.put("mid", e.mid);
                v.put("date", e.date);
                v.put("kind", e.kind);
                v.put("text", e.text);
                helper.getWritableDatabase().insertWithOnConflict("msgs", null, v, SQLiteDatabase.CONFLICT_REPLACE);
            } catch (Exception ex) {
                FileLog.e(ex);
            }
        });
    }

    /** People with archived messages, newest activity first. Call on {@link #queue}. */
    public ArrayList<Person> people() {
        ArrayList<Person> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT m.user_id, COUNT(*), MAX(m.date), " +
                        "(SELECT text FROM msgs x WHERE x.user_id = m.user_id ORDER BY date DESC, mid DESC LIMIT 1), " +
                        "(SELECT kind FROM msgs x WHERE x.user_id = m.user_id ORDER BY date DESC, mid DESC LIMIT 1) " +
                        "FROM msgs m GROUP BY m.user_id ORDER BY MAX(m.date) DESC", null)) {
            while (c.moveToNext()) {
                Person p = new Person();
                p.userId = c.getLong(0);
                p.count = c.getInt(1);
                p.lastDate = c.getInt(2);
                p.lastText = c.isNull(3) ? "" : c.getString(3);
                p.lastKind = c.isNull(4) ? KIND_TEXT : c.getInt(4);
                out.add(p);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return out;
    }

    /** All archived messages of one person, oldest first. Call on {@link #queue}. */
    public ArrayList<Entry> messages(long userId) {
        ArrayList<Entry> out = new ArrayList<>();
        try (Cursor c = helper.getReadableDatabase().rawQuery(
                "SELECT mid, date, kind, text FROM msgs WHERE user_id = ? ORDER BY date ASC, mid ASC",
                new String[]{String.valueOf(userId)})) {
            while (c.moveToNext()) {
                Entry e = new Entry();
                e.userId = userId;
                e.mid = c.getInt(0);
                e.date = c.getInt(1);
                e.kind = c.getInt(2);
                e.text = c.isNull(3) ? "" : c.getString(3);
                out.add(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return out;
    }

    /** Removes the archive of one person. Call on {@link #queue}. */
    public void clear(long userId) {
        try {
            helper.getWritableDatabase().delete("msgs", "user_id = ?", new String[]{String.valueOf(userId)});
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    static int kindOf(TLRPC.Message m) {
        if (m.action != null && !(m.action instanceof TLRPC.TL_messageActionEmpty)) return KIND_SERVICE;
        TLRPC.MessageMedia media = m.media;
        if (media == null || media instanceof TLRPC.TL_messageMediaEmpty || media instanceof TLRPC.TL_messageMediaWebPage) return KIND_TEXT;
        if (media instanceof TLRPC.TL_messageMediaPhoto) return KIND_PHOTO;
        if (media instanceof TLRPC.TL_messageMediaGeo || media instanceof TLRPC.TL_messageMediaGeoLive || media instanceof TLRPC.TL_messageMediaVenue) return KIND_LOCATION;
        if (media instanceof TLRPC.TL_messageMediaContact) return KIND_CONTACT;
        if (media instanceof TLRPC.TL_messageMediaPoll) return KIND_POLL;
        if (media instanceof TLRPC.TL_messageMediaDice) return KIND_DICE;
        if (media instanceof TLRPC.TL_messageMediaDocument && media.document != null) {
            boolean animated = false, video = false, round = false;
            for (TLRPC.DocumentAttribute a : media.document.attributes) {
                if (a instanceof TLRPC.TL_documentAttributeSticker) return KIND_STICKER;
                if (a instanceof TLRPC.TL_documentAttributeAudio) return a.voice ? KIND_VOICE : KIND_MUSIC;
                if (a instanceof TLRPC.TL_documentAttributeAnimated) animated = true;
                if (a instanceof TLRPC.TL_documentAttributeVideo) {
                    video = true;
                    round |= a.round_message;
                }
            }
            if (round) return KIND_ROUND;
            if (animated) return KIND_GIF;
            if (video) return KIND_VIDEO;
            return KIND_FILE;
        }
        return KIND_OTHER;
    }

    static String textOf(TLRPC.Message m, int kind) {
        String text = m.message == null ? "" : m.message.trim();
        if (!text.isEmpty()) return text;
        TLRPC.MessageMedia media = m.media;
        if (media == null) return "";
        switch (kind) {
            case KIND_STICKER:
                for (TLRPC.DocumentAttribute a : media.document.attributes) {
                    if (a instanceof TLRPC.TL_documentAttributeSticker && a.alt != null) return a.alt;
                }
                return "";
            case KIND_FILE:
            case KIND_MUSIC:
                if (media.document != null) {
                    for (TLRPC.DocumentAttribute a : media.document.attributes) {
                        if (a instanceof TLRPC.TL_documentAttributeAudio && (a.title != null || a.performer != null)) {
                            return ((a.performer == null ? "" : a.performer + " — ") + (a.title == null ? "" : a.title)).trim();
                        }
                        if (a instanceof TLRPC.TL_documentAttributeFilename && a.file_name != null) return a.file_name;
                    }
                }
                return "";
            case KIND_CONTACT:
                return ((media.first_name == null ? "" : media.first_name) + " " + (media.last_name == null ? "" : media.last_name)).trim()
                        + (media.phone_number == null || media.phone_number.isEmpty() ? "" : " · " + media.phone_number);
            case KIND_DICE:
                return media instanceof TLRPC.TL_messageMediaDice && ((TLRPC.TL_messageMediaDice) media).emoticon != null
                        ? ((TLRPC.TL_messageMediaDice) media).emoticon + " " + ((TLRPC.TL_messageMediaDice) media).value : "";
            case KIND_LOCATION:
                if (media instanceof TLRPC.TL_messageMediaVenue && media.title != null) return media.title;
                return media.geo != null ? String.format(java.util.Locale.US, "%.5f, %.5f", media.geo.lat, media.geo._long) : "";
            default:
                return "";
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(int account) {
            super(ApplicationLoader.applicationContext, "avra_behind_" + account + ".db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE msgs (user_id INTEGER NOT NULL, mid INTEGER NOT NULL, date INTEGER, kind INTEGER, text TEXT, PRIMARY KEY (user_id, mid))");
            db.execSQL("CREATE INDEX msgs_user_date ON msgs (user_id, date)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
