package com.avragramm;

import android.text.TextUtils;

import org.telegram.messenger.ChatObject;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * "Where is the online user right now" for the chat header: replaces the plain "online" with
 * "online · with @user in «Group»" when the client really knows it.
 *
 * Telegram never tells a third party what someone does in chats you are not part of, nor whom they
 * talk to privately. The only honest sources are the chats you share with them, already in this
 * client's memory:
 *  1. typing updates: the user is typing in a group / channel discussion you are a member of;
 *  2. the last cached message of a shared group, sent by the user in the last few minutes
 *     (and, if it is a reply, the author of the replied message = "with whom").
 * Nothing found → "online · whom they chat with is hidden" (never null, empty or invented data).
 *
 * All calls happen on the UI thread (ChatAvatarContainer.updateSubtitle); printingUsers is a
 * ConcurrentHashMap, dialogs and dialogMessage are UI-thread structures.
 */
public final class AvraPresence {

    /** How long a message in a shared group still counts as "sitting there". */
    public static final int RECENT_SECONDS = 5 * 60;
    /** How often the header re-checks while the user is online (expiry of the window above). */
    public static final long REFRESH_MS = 30_000L;
    /** Demo user shown in test mode. */
    private static final String DEMO_USERNAME = "@avragram_demo";

    private AvraPresence() {}

    /**
     * Subtitle for an online user, or null when the feature is off / not applicable — then the
     * caller keeps the standard "online" (so nothing is ever shown twice).
     */
    public static CharSequence onlineSubtitle(int account, TLRPC.User user) {
        AvraConfig.ensureLoaded();
        if (user == null || user.bot || UserObject.isUserSelf(user)) return null;
        String online = LocaleController.getString(R.string.Online);
        if (AvraConfig.presenceDemo) {
            return LocaleController.formatString(R.string.AvraPresenceWithIn, online, DEMO_USERNAME,
                    LocaleController.getString(R.string.AvraPresenceDemoChat));
        }
        if (!AvraConfig.presenceBar) return null;

        MessagesController mc = MessagesController.getInstance(account);
        // 1. Typing right now in a shared chat (not in this private chat).
        long typingIn = typingDialog(mc, user.id);
        if (typingIn != 0) {
            return LocaleController.formatString(R.string.AvraPresenceTyping, online, dialogLabel(mc, typingIn));
        }
        // 2. Recent message in a shared group.
        Found f = recentMessage(account, mc, user.id);
        if (f != null) {
            String where = dialogLabel(mc, f.dialogId);
            if (f.withUserId != 0 && f.withUserId != user.id) {
                String with = peerLabel(mc, f.withUserId);
                if (with != null) {
                    return LocaleController.formatString(R.string.AvraPresenceWithIn, online, with, where);
                }
            }
            return LocaleController.formatString(R.string.AvraPresenceIn, online, where);
        }
        // 3. Honest fallback.
        return LocaleController.formatString(R.string.AvraPresenceHidden, online);
    }

    /** First non-private dialog where the user is typing now, 0 if none. */
    private static long typingDialog(MessagesController mc, long userId) {
        ConcurrentHashMap<Long, ConcurrentHashMap<Integer, ArrayList<MessagesController.PrintingUser>>> all = mc.printingUsers;
        if (all == null) return 0;
        for (Map.Entry<Long, ConcurrentHashMap<Integer, ArrayList<MessagesController.PrintingUser>>> d : all.entrySet()) {
            Long dialogId = d.getKey();
            if (dialogId == null || !DialogObject.isChatDialog(dialogId)) continue;
            ConcurrentHashMap<Integer, ArrayList<MessagesController.PrintingUser>> threads = d.getValue();
            if (threads == null) continue;
            for (ArrayList<MessagesController.PrintingUser> users : threads.values()) {
                if (users == null) continue;
                // the list is replaced/mutated on the UI thread too, copy defensively
                for (MessagesController.PrintingUser pu : new ArrayList<>(users)) {
                    if (pu != null && pu.userId == userId) return dialogId;
                }
            }
        }
        return 0;
    }

    private static final class Found {
        long dialogId;
        long withUserId;
        int date;
    }

    /** The freshest shared group whose last cached message is from the user, within RECENT_SECONDS. */
    private static Found recentMessage(int account, MessagesController mc, long userId) {
        int now = ConnectionsManager.getInstance(account).getCurrentTime();
        Found best = null;
        ArrayList<TLRPC.Dialog> dialogs = mc.getAllDialogs();
        if (dialogs == null) return null;
        for (int i = 0, n = dialogs.size(); i < n; i++) {
            TLRPC.Dialog dialog = dialogs.get(i);
            if (dialog == null || !DialogObject.isChatDialog(dialog.id)) continue;
            TLRPC.Chat chat = mc.getChat(-dialog.id);
            if (chat == null || ChatObject.isChannel(chat) && !chat.megagroup) continue;
            ArrayList<MessageObject> last = mc.dialogMessage.get(dialog.id);
            if (last == null || last.isEmpty()) continue;
            for (MessageObject mo : last) {
                if (mo == null || mo.messageOwner == null || mo.getFromChatId() != userId) continue;
                int date = mo.messageOwner.date;
                if (now - date > RECENT_SECONDS || best != null && best.date >= date) continue;
                best = new Found();
                best.dialogId = dialog.id;
                best.date = date;
                MessageObject reply = mo.replyMessageObject;
                if (reply != null && reply.messageOwner != null) best.withUserId = reply.getFromChatId();
            }
        }
        return best;
    }

    /** @username of a chat if public, otherwise «Title». */
    private static String dialogLabel(MessagesController mc, long dialogId) {
        TLRPC.Chat chat = mc.getChat(-dialogId);
        if (chat == null) return LocaleController.getString(R.string.AvraPresenceSomeChat);
        String u = at(ChatObject.getPublicUsername(chat));
        if (u != null) return u;
        String title = chat.title == null ? "" : chat.title.trim();
        return title.isEmpty() ? LocaleController.getString(R.string.AvraPresenceSomeChat) : "«" + title + "»";
    }

    /** @username of a user/chat peer, otherwise their name; null if unknown. */
    private static String peerLabel(MessagesController mc, long peerId) {
        if (peerId > 0) {
            TLRPC.User u = mc.getUser(peerId);
            if (u == null) return null;
            String name = at(UserObject.getPublicUsername(u));
            if (name != null) return name;
            name = UserObject.getUserName(u);
            return TextUtils.isEmpty(name) ? null : name.trim();
        } else if (peerId < 0) {
            TLRPC.Chat c = mc.getChat(-peerId);
            if (c == null) return null;
            String name = at(ChatObject.getPublicUsername(c));
            if (name != null) return name;
            return TextUtils.isEmpty(c.title) ? null : c.title.trim();
        }
        return null;
    }

    /** Normalizes a username to "@name"; null for null/blank. */
    public static String at(String username) {
        if (username == null) return null;
        String s = username.trim();
        while (s.startsWith("@")) s = s.substring(1);
        return s.isEmpty() ? null : "@" + s;
    }
}
