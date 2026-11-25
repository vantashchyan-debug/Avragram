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
 *
 * Muted ids are per account, kept in prefs "avragramm" as "mute_<account>" and mirrored in a
 * concurrent set, because the check runs on the stage queue (update processing).
 */
public final class AvraMute {

    private static final Set<Long>[] cache = new Set[UserConfig.MAX_ACCOUNT_COUNT];

    private AvraMute() {}

    private static synchronized Set<Long> set(int account) {
        Set<Long> s = cache[account];
        if (s == null) {
            s = Collections.newSetFromMap(new ConcurrentHashMap<>());
            if (ApplicationLoader.applicationContext != null) {
                for (String v : AvraConfig.prefs().getStringSet(key(account), Collections.emptySet())) {
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

    private static String key(int account) {
        return "mute_" + account;
    }

    public static boolean isMuted(int account, long userId) {
        return userId > 0 && set(account).contains(userId);
    }

    /** Mutes / unmutes a user; returns the new state. */
    public static synchronized boolean toggle(int account, long userId) {
        if (userId <= 0) return false;
        Set<Long> s = set(account);
        boolean muted;
        if (s.contains(userId)) {
            s.remove(userId);
            muted = false;
        } else {
            s.add(userId);
            muted = true;
        }
        HashSet<String> out = new HashSet<>();
        for (Long id : s) out.add(String.valueOf(id));
        SharedPreferences.Editor e = AvraConfig.prefs().edit();
        if (out.isEmpty()) e.remove(key(account));
        else e.putStringSet(key(account), out);
        e.apply();
        return muted;
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
        if (message == null || message.out || message.peer_id == null || message.id <= 0) return false;
        if (message.peer_id.chat_id != 0 || message.peer_id.channel_id != 0) return false;
        if (message.dialog_id != 0 && DialogObject.isEncryptedDialog(message.dialog_id)) return false;
        long userId = message.peer_id.user_id;
        if (!isMuted(account, userId)) return false;
        final ArrayList<Integer> ids = new ArrayList<>(1);
        ids.add(message.id);
        AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account)
                .deleteMessages(ids, null, null, userId, 0, true, 0));
        return true;
    }
}
