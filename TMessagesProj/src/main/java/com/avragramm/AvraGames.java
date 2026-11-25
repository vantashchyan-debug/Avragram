package com.avragramm;

import android.content.SharedPreferences;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.DialogObject;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Avragram mini-games: registry, personal records and the result message sent to a chat.
 *
 * Result message (language of the sender, but machine-readable for any Avragram):
 *   🎮 AvraSnake · ★★☆ Normal
 *   Score: 42
 *   Record🏆: 57 — @someone            (only when this game was already played in this chat)
 * The chat record is found with messages.search for the game name in that chat, so it covers
 * results of every participant, not only ours. Stars = difficulty, independent of language.
 */
public final class AvraGames {

    public static final int EASY = 0, NORMAL = 1, HARD = 2;

    public static final String[] IDS = {"avrablast", "avrasnake", "avradrop", "avrarun", "avrabird", "avra48"};
    /** Game names are brands of Avragram itself and are not translated. */
    public static final String[] TITLES = {"AvraBlast", "AvraSnake", "AvraDrop", "AvraRun", "AvraBird", "Avra48"};
    public static final String[] EMOJI = {"🧩", "🐍", "🧱", "🏃", "🐦", "🔢"};
    public static final int[] DESCRIPTIONS = {R.string.AvraGameBlastInfo, R.string.AvraGameSnakeInfo, R.string.AvraGameDropInfo,
            R.string.AvraGameRunInfo, R.string.AvraGameBirdInfo, R.string.AvraGame48Info};
    public static final int[] DIFFICULTY_TITLES = {R.string.AvraDifficultyEasy, R.string.AvraDifficultyNormal, R.string.AvraDifficultyHard};

    private static final Pattern RESULT = Pattern.compile("^🎮\\s*(Avra\\w+)\\s*·\\s*(★+)(☆*)[^\\n]*\\n[^:\\n]*:\\s*(\\d+)");

    private AvraGames() {}

    public static int index(String id) {
        for (int i = 0; i < IDS.length; i++) if (IDS[i].equals(id)) return i;
        return 0;
    }

    public static String stars(int difficulty) {
        return difficulty == EASY ? "★☆☆" : difficulty == NORMAL ? "★★☆" : "★★★";
    }

    // ---- records ---------------------------------------------------------------------------

    private static SharedPreferences prefs() {
        return AvraConfig.prefs();
    }

    public static int best(int game, int difficulty) {
        return prefs().getInt("game_" + IDS[game] + "_" + difficulty, 0);
    }

    /** Best over all difficulties (for the list). */
    public static int bestAny(int game) {
        int b = 0;
        for (int d = EASY; d <= HARD; d++) b = Math.max(b, best(game, d));
        return b;
    }

    /** Saves a finished round as a personal record; returns true for a new record. */
    public static boolean submit(int game, int difficulty, int score) {
        SharedPreferences p = prefs();
        String key = "game_" + IDS[game] + "_" + difficulty;
        boolean record = score > p.getInt(key, 0);
        if (record) p.edit().putInt(key, score).apply();
        return record;
    }

    /** Our best result in one chat (fallback for chats that cannot be searched); -1 if none. */
    private static int chatBest(int game, int difficulty, long dialogId) {
        return prefs().getInt("game_" + IDS[game] + "_" + difficulty + "_" + dialogId, -1);
    }

    private static void saveChatBest(int game, int difficulty, long dialogId, int score) {
        String k = "game_" + IDS[game] + "_" + difficulty + "_" + dialogId;
        SharedPreferences p = prefs();
        if (score > p.getInt(k, -1)) p.edit().putInt(k, score).apply();
    }

    /** Whether results go to the chat the games were opened from. */
    public static boolean sendResults() {
        return prefs().getBoolean("game_send", true);
    }

    public static void setSendResults(boolean v) {
        prefs().edit().putBoolean("game_send", v).apply();
    }

    // ---- result message --------------------------------------------------------------------

    public interface SentCallback {
        /** Called on the UI thread; {@code record} is the chat record line or null for a first game. */
        void onSent(String text, String record);
    }

    /**
     * Looks up earlier results of this game in the chat (all participants), then sends the message.
     * Must be called on the UI thread.
     */
    public static void sendResult(int account, long dialogId, int game, int difficulty, int score, SentCallback callback) {
        if (dialogId == 0) return;
        MessagesController mc = MessagesController.getInstance(account);
        TLRPC.InputPeer peer = DialogObject.isEncryptedDialog(dialogId) ? null : mc.getInputPeer(dialogId);
        if (peer == null) {
            finishSend(account, dialogId, game, difficulty, score, localRecord(game, difficulty, dialogId, score, account), callback);
            return;
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = peer;
        req.q = TITLES[game];
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 100;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            Holder best = null;
            if (response instanceof TLRPC.messages_Messages) {
                TLRPC.messages_Messages res = (TLRPC.messages_Messages) response;
                mc.putUsers(res.users, false);
                mc.putChats(res.chats, false);
                for (TLRPC.Message m : res.messages) {
                    if (m == null || m.message == null) continue;
                    Matcher mt = RESULT.matcher(m.message);
                    if (!mt.find() || !TITLES[game].equals(mt.group(1)) || mt.group(2).length() - 1 != difficulty) continue;
                    int s;
                    try {
                        s = Integer.parseInt(mt.group(4));
                    } catch (NumberFormatException e) {
                        continue;
                    }
                    if (best == null || s > best.score) {
                        best = new Holder();
                        best.score = s;
                        best.peerId = m.from_id != null ? MessageObject.getPeerId(m.from_id)
                                : m.peer_id != null ? MessageObject.getPeerId(m.peer_id) : 0;
                        if (m.out) best.peerId = UserConfig.getInstance(account).getClientUserId();
                    }
                }
                finishSend(account, dialogId, game, difficulty, score, best == null ? null : recordLine(account, best, score), callback);
            } else {
                finishSend(account, dialogId, game, difficulty, score, localRecord(game, difficulty, dialogId, score, account), callback);
            }
        }));
    }

    private static final class Holder {
        int score;
        long peerId;
    }

    /** Offline / secret chat: our own earlier results in this chat. */
    private static String localRecord(int game, int difficulty, long dialogId, int score, int account) {
        int prev = chatBest(game, difficulty, dialogId);
        if (prev < 0) return null;
        Holder h = new Holder();
        h.score = prev;
        h.peerId = UserConfig.getInstance(account).getClientUserId();
        return recordLine(account, h, score);
    }

    private static String recordLine(int account, Holder prevBest, int score) {
        long me = UserConfig.getInstance(account).getClientUserId();
        int value;
        long holder;
        if (score >= prevBest.score) {
            value = score;
            holder = me;
        } else {
            value = prevBest.score;
            holder = prevBest.peerId;
        }
        return LocaleController.formatString(R.string.AvraGameChatRecord, value, peerName(account, holder));
    }

    private static String peerName(int account, long peerId) {
        MessagesController mc = MessagesController.getInstance(account);
        if (peerId > 0) {
            TLRPC.User u = mc.getUser(peerId);
            if (u == null && peerId == UserConfig.getInstance(account).getClientUserId()) u = UserConfig.getInstance(account).getCurrentUser();
            if (u != null) {
                String at = AvraPresence.at(UserObject.getPublicUsername(u));
                if (at != null) return at;
                String n = UserObject.getUserName(u);
                if (n != null && !n.trim().isEmpty()) return n.trim();
            }
        } else if (peerId < 0) {
            TLRPC.Chat c = mc.getChat(-peerId);
            if (c != null) {
                String at = AvraPresence.at(org.telegram.messenger.ChatObject.getPublicUsername(c));
                if (at != null) return at;
                if (c.title != null && !c.title.trim().isEmpty()) return c.title.trim();
            }
        }
        return LocaleController.getString(R.string.AvraGameSomeone);
    }

    private static void finishSend(int account, long dialogId, int game, int difficulty, int score, String record, SentCallback callback) {
        StringBuilder sb = new StringBuilder();
        sb.append("🎮 ").append(TITLES[game]).append(" · ").append(stars(difficulty)).append(' ')
                .append(LocaleController.getString(DIFFICULTY_TITLES[difficulty])).append('\n')
                .append(LocaleController.formatString(R.string.AvraGameChatScore, score));
        if (record != null) sb.append('\n').append(record);
        String text = sb.toString();
        saveChatBest(game, difficulty, dialogId, score);
        SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId));
        if (callback != null) callback.onSent(text, record);
    }
}
