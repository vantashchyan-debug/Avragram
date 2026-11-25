package com.avragramm;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import androidx.collection.LongSparseArray;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.HashSet;

/**
 * Remembers which messages the server asked to delete, so they stay visible with a "deleted" mark.
 * Key: dialogKey = -channelId for channels/supergroups, 0 for private chats and basic groups
 * (their message ids are unique per account) — exactly what delete updates carry.
 */
public final class AvraDeleted {

    private static final AvraDeleted[] instances = new AvraDeleted[UserConfig.MAX_ACCOUNT_COUNT];

    public static AvraDeleted get(int account) {
        AvraDeleted i = instances[account];
        if (i == null) {
            synchronized (AvraDeleted.class) {
                i = instances[account];
                if (i == null) instances[account] = i = new AvraDeleted(account);
            }
        }
        return i;
    }

    private final Helper helper;
    private final HashSet<String> cache = new HashSet<>();

    private AvraDeleted(int account) {
        helper = new Helper(account);
        try (Cursor c = helper.getReadableDatabase().rawQuery("SELECT dialog, mid FROM deleted", null)) {
            while (c.moveToNext()) cache.add(key(c.getLong(0), c.getInt(1)));
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static String key(long dialogKey, int mid) {
        return dialogKey + ":" + mid;
    }

    public static long dialogKey(TLRPC.Message m) {
        if (m != null && m.peer_id != null && m.peer_id.channel_id != 0) return -m.peer_id.channel_id;
        return 0;
    }

    public void remember(LongSparseArray<ArrayList<Integer>> deleted) {
        int now = (int) (System.currentTimeMillis() / 1000);
        SQLiteDatabase db = helper.getWritableDatabase();
        db.beginTransaction();
        try {
            for (int a = 0; a < deleted.size(); a++) {
                long dialogKey = deleted.keyAt(a);
                for (Integer mid : deleted.valueAt(a)) {
                    ContentValues v = new ContentValues();
                    v.put("dialog", dialogKey);
                    v.put("mid", mid);
                    v.put("date", now);
                    db.insertWithOnConflict("deleted", null, v, SQLiteDatabase.CONFLICT_IGNORE);
                    synchronized (cache) {
                        cache.add(key(dialogKey, mid));
                    }
                }
            }
            db.setTransactionSuccessful();
        } catch (Exception e) {
            FileLog.e(e);
        } finally {
            db.endTransaction();
        }
    }

    public boolean isDeleted(MessageObject mo) {
        if (mo == null || mo.messageOwner == null || mo.getId() <= 0) return false;
        synchronized (cache) {
            return cache.contains(key(dialogKey(mo.messageOwner), mo.getId()));
        }
    }

    private static final class Helper extends SQLiteOpenHelper {
        Helper(int account) {
            super(ApplicationLoader.applicationContext, "avra_deleted_" + account + ".db", null, 1);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE deleted (dialog INTEGER NOT NULL, mid INTEGER NOT NULL, date INTEGER, PRIMARY KEY (dialog, mid))");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }
    }
}
