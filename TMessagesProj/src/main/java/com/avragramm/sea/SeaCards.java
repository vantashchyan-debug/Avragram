package com.avragramm.sea;

import android.text.TextUtils;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.BotInlineKeyboard;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessageObject;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import com.avragramm.chess.ChessNet;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * AvraSea cards in the chat: the challenge "🚢 Вам бросили вызов! Игра: Морской бой" with Accept / Decline
 * and the result "🏁 Игра завершена! Победил @username" with Rematch / Battlefield. Same mechanism as
 * {@link com.avragramm.chess.ChessCards}.
 */
public final class SeaCards {

    private SeaCards() {
    }

    private static final String URL = "https://github.com/vantashchyan-debug/Avragram#avrasea-";
    private static final String KIND_INVITE = "inv", KIND_END = "end";

    public static final int BTN_ACCEPT = 1101, BTN_DECLINE = 1102, BTN_OPEN = 1103, BTN_CANCEL = 1104, BTN_REMATCH = 1105, BTN_REVIEW = 1106;

    // ---- sending --------------------------------------------------------------------------------

    private static void sendCard(SeaNet.Game g, String text, int linkStart, int linkLength, String kind) {
        ArrayList<TLRPC.MessageEntity> entities = new ArrayList<>();
        TLRPC.TL_messageEntityTextUrl link = new TLRPC.TL_messageEntityTextUrl();
        link.offset = linkStart;
        link.length = linkLength;
        link.url = URL + g.id + "-" + kind;
        entities.add(link);
        TLRPC.TL_messageEntityBold bold = new TLRPC.TL_messageEntityBold();
        bold.offset = 0;
        bold.length = text.indexOf('\n') > 0 ? text.indexOf('\n') : text.length();
        entities.add(bold);
        AndroidUtilities.runOnUIThread(() -> SendMessagesHelper.getInstance(g.account).sendMessage(
                SendMessagesHelper.SendMessageParams.of(text, g.peer, null, null, null, false, entities, null, null, true, 0, 0, null, false)));
    }

    /** "🚢 Вам бросили вызов! / Игра: Морской бой / Расставь корабли и жми «В бой!»". */
    public static void sendInvite(SeaNet.Game g) {
        String game = LocaleController.getString(R.string.AvraSeaTitle);
        String head = LocaleController.getString(R.string.AvraSeaCardInvite);
        String line = LocaleController.formatString(R.string.AvraChessCardGame, game);
        String text = head + "\n" + line + "\n" + LocaleController.getString(R.string.AvraSeaCardInviteHint);
        int start = head.length() + 1 + line.indexOf(game);
        sendCard(g, text, start, game.length(), KIND_INVITE);
    }

    /** "🏁 Игра завершена! Победил(а) @username", sent by whoever started the game. */
    public static void sendResult(SeaNet.Game g) {
        String over = LocaleController.getString(R.string.AvraChessCardOver);
        long self = UserConfig.getInstance(g.account).getClientUserId();
        long winner = g.winner > 0 ? self : g.peer;
        char w = g.winner > 0 ? g.me() : g.them();
        int[] acc = SeaNet.accuracy(g, w);
        String who = LocaleController.formatString(R.string.AvraSeaCardWon, name(g.account, winner));
        String why = LocaleController.getString("resign".equals(g.reason) ? R.string.AvraSeaReasonResign : R.string.AvraSeaReasonFleet);
        String text = "🏁 " + over + " " + who + "\n🚢 " + LocaleController.getString(R.string.AvraSeaTitle) + " · " + why + " · "
                + LocaleController.formatString(R.string.AvraSeaCardStats, acc[0], acc[2]);
        sendCard(g, text, 3, over.length(), KIND_END);
    }

    private static String name(int account, long id) {
        TLRPC.User u = MessagesController.getInstance(account).getUser(id);
        if (u == null) return "ID " + id;
        String un = UserObject.getPublicUsername(u);
        return !TextUtils.isEmpty(un) ? "@" + un : UserObject.getUserName(u);
    }

    /** Removes the cards of a game from the chat (for both): the challenge only, or all of them. */
    public static void deleteCards(SeaNet.Game g, boolean inviteOnly) {
        MessagesController mc = MessagesController.getInstance(g.account);
        TLRPC.InputPeer ip = mc.getInputPeer(g.peer);
        if (ip == null) return;
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = ip;
        req.q = "";
        req.filter = new TLRPC.TL_inputMessagesFilterUrl();
        req.limit = 60;
        ConnectionsManager.getInstance(g.account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.messages_Messages)) return;
            ArrayList<Integer> ids = new ArrayList<>();
            for (TLRPC.Message m : ((TLRPC.messages_Messages) response).messages) {
                String[] c = parse(m);
                if (c != null && c[0].equals(g.id) && (!inviteOnly || KIND_INVITE.equals(c[1]))) ids.add(m.id);
            }
            if (!ids.isEmpty()) mc.deleteMessages(ids, null, null, g.peer, 0, true, 0);
        }));
    }

    // ---- reading --------------------------------------------------------------------------------

    /** {gameId, kind} of a card, or null. */
    public static String[] parse(TLRPC.Message m) {
        if (m == null || m.entities == null || m.peer_id == null || m.peer_id.user_id == 0) return null;
        for (int i = 0; i < m.entities.size(); i++) {
            TLRPC.MessageEntity e = m.entities.get(i);
            if (!(e instanceof TLRPC.TL_messageEntityTextUrl) || e.url == null) continue;
            int at = e.url.indexOf("#avrasea-");
            if (at < 0) continue;
            String[] p = e.url.substring(at + 9).split("-");
            if (p.length >= 2 && !p[0].isEmpty()) return new String[]{p[0], p[1]};
        }
        return null;
    }

    private static final HashMap<String, ArrayList<WeakReference<MessageObject>>> shown = new HashMap<>();

    /** Called while a message's inline buttons are built: adds the AvraChess ones for a card. */
    public static void addButtons(MessageObject mo, BotInlineKeyboard.Builder builder) {
        if (mo == null || mo.messageOwner == null) return;
        String[] c = parse(mo.messageOwner);
        if (c == null) return;
        try {
            synchronized (shown) {
                ArrayList<WeakReference<MessageObject>> list = shown.get(c[0]);
                if (list == null) shown.put(c[0], list = new ArrayList<>());
                for (int i = list.size() - 1; i >= 0; i--) if (list.get(i).get() == null || list.get(i).get() == mo) list.remove(i);
                if (list.size() < 8) list.add(new WeakReference<>(mo));
            }
            SeaNet.Game g = SeaNet.get(c[0]);
            boolean out = mo.isOut();
            if (KIND_INVITE.equals(c[1])) {
                int state = g == null ? (out ? -1 : SeaNet.INVITE_IN) : g.state;
                switch (state) {
                    case SeaNet.INVITE_IN:
                        builder.addRow(button(BTN_ACCEPT, R.string.AvraSeaBtnAccept), button(BTN_DECLINE, R.string.AvraChessBtnDecline));
                        break;
                    case SeaNet.INVITE_OUT:
                        builder.addRow(button(BTN_OPEN, R.string.AvraSeaBtnOpen), button(BTN_CANCEL, R.string.AvraChessBtnCancel));
                        break;
                    case SeaNet.ACTIVE:
                        builder.addRow(button(BTN_OPEN, R.string.AvraSeaBtnOpen));
                        break;
                    case SeaNet.FINISHED:
                        builder.addRow(button(BTN_REMATCH, R.string.AvraChessBtnRematch), button(BTN_REVIEW, R.string.AvraSeaBtnField));
                        break;
                }
            } else if (KIND_END.equals(c[1]) && g != null) {
                builder.addRow(button(BTN_REMATCH, R.string.AvraChessBtnRematch), button(BTN_REVIEW, R.string.AvraSeaBtnField));
            }
        } catch (Throwable t) {
            org.telegram.messenger.FileLog.e(t);
        }
    }

    private static BotInlineKeyboard.ButtonCustom button(int id, int text) {
        return new BotInlineKeyboard.ButtonCustom(id, text, 0);
    }

    /** Rebuilds the visible cards of a game after its state changed. */
    static void refresh(SeaNet.Game g) {
        ArrayList<WeakReference<MessageObject>> list;
        synchronized (shown) {
            list = shown.get(g.id);
            if (list == null) return;
            list = new ArrayList<>(list);
        }
        HashMap<Long, ArrayList<MessageObject>> byDialog = new HashMap<>();
        for (WeakReference<MessageObject> r : list) {
            MessageObject old = r.get();
            if (old == null || old.messageOwner == null) continue;
            MessageObject fresh = new MessageObject(old.currentAccount, old.messageOwner, true, true);
            ArrayList<MessageObject> l = byDialog.get(old.getDialogId());
            if (l == null) byDialog.put(old.getDialogId(), l = new ArrayList<>());
            l.add(fresh);
        }
        for (HashMap.Entry<Long, ArrayList<MessageObject>> e : byDialog.entrySet()) {
            NotificationCenter.getInstance(g.account).postNotificationName(NotificationCenter.replaceMessagesObjects, e.getKey(), e.getValue());
        }
    }

    // ---- buttons --------------------------------------------------------------------------------

    /** A card button was pressed in a chat; returns false when it is not an AvraChess button. */
    public static boolean onButton(BaseFragment fragment, MessageObject mo, int buttonId) {
        if (buttonId < BTN_ACCEPT || buttonId > BTN_REVIEW || mo == null) return false;
        String[] c = parse(mo.messageOwner);
        if (c == null) return true;
        SeaNet.Game g = SeaNet.get(c[0]);
        if (g == null) {
            // the hidden challenge has not been read yet: fetch it, then try again
            int account = mo.currentAccount;
            SeaNet.sync(account, mo.getDialogId(), () -> {
                if (SeaNet.get(c[0]) != null) onButton(fragment, mo, buttonId);
                else BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.AvraChessCardGone)).show();
            });
            return true;
        }
        switch (buttonId) {
            case BTN_ACCEPT:
                if (g.state == SeaNet.INVITE_IN || g.state == SeaNet.ACTIVE || g.state == SeaNet.FINISHED) {
                    fragment.presentFragment(AvraSeaGameActivity.online(g.id));
                } else {
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.error, LocaleController.getString(R.string.AvraChessCardGone)).show();
                }
                break;
            case BTN_DECLINE:
                if (g.state == SeaNet.INVITE_IN) {
                    SeaNet.decline(g);
                    BulletinFactory.of(fragment).createSimpleBulletin(R.raw.chats_infotip, LocaleController.getString(R.string.AvraChessDeclined)).show();
                }
                break;
            case BTN_CANCEL:
                if (g.state == SeaNet.INVITE_OUT) SeaNet.decline(g);
                break;
            case BTN_OPEN:
            case BTN_REVIEW:
                fragment.presentFragment(AvraSeaGameActivity.online(g.id));
                break;
            case BTN_REMATCH:
                fragment.presentFragment(AvraSeaGameActivity.challenge(g.peer));
                break;
        }
        return true;
    }
}
