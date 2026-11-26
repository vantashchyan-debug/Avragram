package com.avragramm;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Mute" a person in a private chat: every new message they send is deleted for both sides
 * the moment it arrives, and never reaches this client's chat list, history or notifications.
 *
 * Telegram lets either side of a private chat delete any message for both, so this is a plain
 * messages.deleteMessages(revoke) — queued as a pending task, i.e. retried after restart/offline.
 * Secret chats, groups and channels are not affected. Messages sent before the mute stay.
 * Before deletion a local copy goes to {@link AvraMuteLog} ("Said behind your back").
 *
 * Muted ids are per account, kept in prefs "avragramm" as "mute_<account>" and mirrored in a
 * concurrent set, because the check runs on the stage queue (update processing).
 */
public final class AvraMute {

    private static final Set<Long>[] cache = new Set[UserConfig.MAX_ACCOUNT_COUNT];
    private static final Set<Long>[] fakeCache = new Set[UserConfig.MAX_ACCOUNT_COUNT];

    private AvraMute() {}

    private static Set<Long> set(int account) {
        return set(account, false);
    }

    private static synchronized Set<Long> set(int account, boolean fake) {
        Set<Long>[] cache = fake ? fakeCache : AvraMute.cache;
        Set<Long> s = cache[account];
        if (s == null) {
            s = Collections.newSetFromMap(new ConcurrentHashMap<>());
            if (ApplicationLoader.applicationContext != null) {
                for (String v : AvraConfig.prefs().getStringSet(key(account, fake), Collections.emptySet())) {
                    try {
                        s.add(Long.parseLong(v));
                    } catch (NumberFormatException ignored) {
                    }
                }
            }
            cache[account] = s;
        }
        return s;
    }

    private static String key(int account, boolean fake) {
        return (fake ? "fmute_" : "mute_") + account;
    }

    public static boolean isMuted(int account, long userId) {
        return userId > 0 && set(account).contains(userId);
    }

    /** Ids of all muted people of an account. */
    public static long[] mutedIds(int account) {
        Set<Long> s = set(account);
        long[] out = new long[s.size()];
        int i = 0;
        for (Long id : s) {
            if (i < out.length) out[i++] = id;
        }
        return i == out.length ? out : java.util.Arrays.copyOf(out, i);
    }

    /** Mutes / unmutes a user; returns the new state. */
    public static boolean toggle(int account, long userId) {
        return toggle(account, userId, false);
    }

    /** Mutes / unmutes (real or fake); the two modes exclude each other. Returns the new state. */
    public static synchronized boolean toggle(int account, long userId, boolean fake) {
        if (userId <= 0) return false;
        Set<Long> other = set(account, !fake);
        if (other.remove(userId)) save(account, !fake, other);
        Set<Long> s = set(account, fake);
        boolean muted;
        if (s.contains(userId)) {
            s.remove(userId);
            muted = false;
        } else {
            s.add(userId);
            muted = true;
        }
        save(account, fake, s);
        return muted;
    }

    private static void save(int account, boolean fake, Set<Long> s) {
        HashSet<String> out = new HashSet<>();
        for (Long id : s) out.add(String.valueOf(id));
        SharedPreferences.Editor e = AvraConfig.prefs().edit();
        if (out.isEmpty()) e.remove(key(account, fake));
        else e.putStringSet(key(account, fake), out);
        e.apply();
    }

    /** Fake mute: their messages are deleted for them at once, but stay here marked as deleted. */
    public static boolean isFakeMuted(int account, long userId) {
        return userId > 0 && set(account, true).contains(userId);
    }

    /** Ids deleted by fake mute that this client must keep even if a delete update comes back. */
    private static final Set<Integer>[] fakeDeleted = new Set[UserConfig.MAX_ACCOUNT_COUNT];

    private static synchronized Set<Integer> fakeDeleted(int account) {
        if (fakeDeleted[account] == null) fakeDeleted[account] = Collections.newSetFromMap(new ConcurrentHashMap<>());
        return fakeDeleted[account];
    }

    /**
     * Called by update processing with the private-chat/basic-group deletions (dialog key 0):
     * removes the fake-muted ids from the list (they are kept) and returns them; null if none.
     */
    public static ArrayList<Integer> keepFakeDeleted(int account, ArrayList<Integer> ids) {
        if (ids == null || fakeDeleted[account] == null || fakeDeleted[account].isEmpty()) return null;
        ArrayList<Integer> kept = null;
        for (int i = ids.size() - 1; i >= 0; i--) {
            Integer id = ids.get(i);
            if (fakeDeleted[account].remove(id)) {
                if (kept == null) kept = new ArrayList<>();
                kept.add(id);
                ids.remove(i);
            }
        }
        return kept;
    }

    private static void fakeDelete(int account, TLRPC.Message message) {
        final int id = message.id;
        fakeDeleted(account).add(id);
        AndroidUtilities.runOnUIThread(() -> {
            MessagesController mc = MessagesController.getInstance(account);
            TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
            req.revoke = true;
            req.id.add(id);
            org.telegram.tgnet.ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
                if (response instanceof TLRPC.TL_messages_affectedMessages) {
                    TLRPC.TL_messages_affectedMessages res = (TLRPC.TL_messages_affectedMessages) response;
                    // The deletion is ours: advance pts without deleting anything locally.
                    mc.processNewDifferenceParams(-1, res.pts, -1, res.pts_count);
                    androidx.collection.LongSparseArray<ArrayList<Integer>> mark = new androidx.collection.LongSparseArray<>();
                    ArrayList<Integer> list = new ArrayList<>(1);
                    list.add(id);
                    mark.put(0, list);
                    org.telegram.messenger.MessagesStorage.getInstance(account).getStorageQueue().postRunnable(() -> {
                        AvraDeleted.get(account).remember(mark);
                        AndroidUtilities.runOnUIThread(() -> org.telegram.messenger.NotificationCenter.getInstance(account)
                                .postNotificationName(org.telegram.messenger.NotificationCenter.avraMessagesMarkedDeleted, mark));
                    });
                } else {
                    fakeDeleted(account).remove(id);
                }
            });
        });
    }

    /** Can this chat be muted: a private chat with another person (not yourself, not a secret chat). */
    public static boolean canMute(int account, TLRPC.User user, boolean encrypted) {
        return !encrypted && user != null && user.id > 0 && user.id != UserConfig.getInstance(account).getClientUserId()
                && user.id != 777000 && user.id != 42777 && user.id != 333000;
    }

    /**
     * Called for every new incoming message before it is processed. Returns true when the message
     * is from a muted person in a private chat: the caller drops it, and it is deleted for both.
     */
    public static boolean intercept(int account, TLRPC.Message message) {
        if (com.avragramm.chess.ChessNet.onMessage(account, message)) return true; // hidden AvraChess move: applied, never shown
        if (com.avragramm.sea.SeaNet.onMessage(account, message)) return true; // hidden AvraSea shot
        if (message == null || message.out || message.peer_id == null || message.id <= 0) return false;
        if (message.peer_id.chat_id != 0 || message.peer_id.channel_id != 0) return false;
        if (message.dialog_id != 0 && DialogObject.isEncryptedDialog(message.dialog_id)) return false;
        long userId = message.peer_id.user_id;
        if (isFakeMuted(account, userId)) {
            AvraMuteLog.get(account).store(message);
            fakeDelete(account, message);
            return false; // shown here as usual
        }
        if (!isMuted(account, userId)) return false;
        AvraMuteLog.get(account).store(message); // "Said behind your back" keeps a local copy
        final ArrayList<Integer> ids = new ArrayList<>(1);
        ids.add(message.id);
        AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account)
                .deleteMessages(ids, null, null, userId, 0, true, 0));
        return true;
    }
}
