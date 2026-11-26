package com.avragramm.sea;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import com.avragramm.chess.ChessNet;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AvraSea online: sea battle with a friend over hidden private-chat messages, like AvraChess.
 *
 * Fair play without a server: before the game each side sends a commitment (hash of its layout and a
 * secret salt). Shots and results travel in a log every message carries in full
 * ("a45h.a46k.a47m.b00?" — shooter a/b, cell, result m/h/k or ? while unanswered). The target's
 * Avragram answers by itself from its own layout. At the end both layouts are revealed and checked
 * against the commitments and every answer; a mismatch marks the game as cheated.
 * The challenger is "a" and shoots first; a hit or a kill gives another shot.
 */
public final class SeaNet {

    private SeaNet() {
    }

    public static final int INVITE_OUT = 0, INVITE_IN = 1, ACTIVE = 2, FINISHED = 3, DECLINED = 4;
    static final String TAG = "#avrasea";
    private static final Pattern LINE = Pattern.compile("#avrasea ([^\\n]*)$");

    public static final class Game {
        public String id;
        public int account;
        public long peer;
        public boolean starter;
        public int state;
        public String myLayout = "", mySalt = "", myCommit = "", oppCommit = "", oppLayout = "";
        public String log = "";
        /** 1 you won, -1 you lost, 0 not yet. */
        public int winner;
        public String reason = "";
        public boolean cheat, verified, revealSent, cardSent;
        public String oppName = "";
        public long created, updated;
        public final ArrayList<Integer> inIds = new ArrayList<>(), outIds = new ArrayList<>();

        public char me() {
            return starter ? 'a' : 'b';
        }

        public char them() {
            return starter ? 'b' : 'a';
        }

        public ArrayList<String> entries() {
            ArrayList<String> out = new ArrayList<>();
            if (!TextUtils.isEmpty(log)) Collections.addAll(out, log.split("\\."));
            return out;
        }

        /** Shots of one side: {cell, result char}. */
        public ArrayList<int[]> shots(char who) {
            ArrayList<int[]> out = new ArrayList<>();
            for (String e : entries()) {
                if (e.length() != 4 || e.charAt(0) != who) continue;
                out.add(new int[]{Integer.parseInt(e.substring(1, 3)), e.charAt(3)});
            }
            return out;
        }

        /** 'a' / 'b' to shoot, '?' while a shot waits for its answer, 0 when over. */
        public char turn() {
            if (state != ACTIVE) return 0;
            ArrayList<String> e = entries();
            if (e.isEmpty()) return 'a';
            String last = e.get(e.size() - 1);
            char r = last.charAt(3), who = last.charAt(0);
            if (r == '?') return '?';
            return r == 'm' ? (who == 'a' ? 'b' : 'a') : who;
        }

        public boolean myTurn() {
            return turn() == me();
        }

        public int kills(char who) {
            int k = 0;
            for (int[] s : shots(who)) if (s[1] == 'k') k++;
            return k;
        }

        JSONObject toJson() throws Exception {
            JSONArray ii = new JSONArray(), oi = new JSONArray();
            for (int i = Math.max(0, inIds.size() - 40); i < inIds.size(); i++) ii.put(inIds.get(i));
            for (int i = Math.max(0, outIds.size() - 40); i < outIds.size(); i++) oi.put(outIds.get(i));
            return new JSONObject().put("id", id).put("a", account).put("p", peer).put("st", starter).put("s", state)
                    .put("ml", myLayout).put("ms", mySalt).put("mc", myCommit).put("oc", oppCommit).put("ol", oppLayout)
                    .put("l", log).put("w", winner).put("why", reason).put("ch", cheat).put("vf", verified)
                    .put("rs", revealSent).put("cs", cardSent).put("on", oppName).put("cr", created).put("up", updated)
                    .put("ii", ii).put("oi", oi);
        }

        static Game fromJson(JSONObject o) {
            Game g = new Game();
            g.id = o.optString("id");
            g.account = o.optInt("a");
            g.peer = o.optLong("p");
            g.starter = o.optBoolean("st");
            g.state = o.optInt("s");
            g.myLayout = o.optString("ml");
            g.mySalt = o.optString("ms");
            g.myCommit = o.optString("mc");
            g.oppCommit = o.optString("oc");
            g.oppLayout = o.optString("ol");
            g.log = o.optString("l");
            g.winner = o.optInt("w");
            g.reason = o.optString("why");
            g.cheat = o.optBoolean("ch");
            g.verified = o.optBoolean("vf");
            g.revealSent = o.optBoolean("rs");
            g.cardSent = o.optBoolean("cs");
            g.oppName = o.optString("on");
            g.created = o.optLong("cr");
            g.updated = o.optLong("up");
            JSONArray ii = o.optJSONArray("ii"), oi = o.optJSONArray("oi");
            for (int i = 0; ii != null && i < ii.length(); i++) g.inIds.add(ii.optInt(i));
            for (int i = 0; oi != null && i < oi.length(); i++) g.outIds.add(oi.optInt(i));
            return g;
        }
    }

    public interface Listener {
        /** event: what happened to the game ('h' hit / 'k' kill / 'm' miss of the last shot, 0 other). */
        void onSeaUpdate(Game g, boolean incoming);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static HashMap<String, Game> games;
    public static String openGameId;

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("avra_sea", Context.MODE_PRIVATE);
    }

    private static synchronized HashMap<String, Game> map() {
        if (games == null) {
            games = new HashMap<>();
            try {
                JSONArray a = new JSONArray(prefs().getString("games", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    Game g = Game.fromJson(a.getJSONObject(i));
                    games.put(g.id, g);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return games;
    }

    static synchronized void save() {
        try {
            ArrayList<Game> list = new ArrayList<>(map().values());
            Collections.sort(list, (x, y) -> Long.compare(y.created, x.created));
            JSONArray a = new JSONArray();
            int done = 0;
            for (Game g : list) {
                if ((g.state == FINISHED || g.state == DECLINED) && ++done > 20) {
                    games.remove(g.id);
                    continue;
                }
                a.put(g.toJson());
            }
            prefs().edit().putString("games", a.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static Game get(String id) {
        return id == null ? null : map().get(id);
    }

    public static ArrayList<Game> list(int account) {
        ArrayList<Game> out = new ArrayList<>();
        for (Game g : map().values()) if (g.account == account && g.state != DECLINED) out.add(g);
        Collections.sort(out, (x, y) -> {
            int px = priority(x), py = priority(y);
            if (px != py) return Integer.compare(px, py);
            return Long.compare(Math.max(y.updated, y.created), Math.max(x.updated, x.created));
        });
        return out;
    }

    private static int priority(Game g) {
        if (g.state == INVITE_IN) return 0;
        if (g.myTurn()) return 1;
        if (g.state == ACTIVE) return 2;
        if (g.state == INVITE_OUT) return 3;
        return 4;
    }

    public static int pending(int account) {
        int n = 0;
        for (Game g : map().values()) if (g.account == account && (g.state == INVITE_IN || g.myTurn())) n++;
        return n;
    }

    public static void delete(String id) {
        map().remove(id);
        save();
    }

    // ---- stats ----------------------------------------------------------------------------------

    /** kind: "online" or "bot0".."bot2"; {wins, losses}. */
    public static int[] stats(String kind) {
        return new int[]{prefs().getInt("w_" + kind, 0), prefs().getInt("l_" + kind, 0)};
    }

    public static void addResult(String kind, boolean won) {
        String k = (won ? "w_" : "l_") + kind;
        prefs().edit().putInt(k, prefs().getInt(k, 0) + 1).apply();
    }

    // ---- actions --------------------------------------------------------------------------------

    private static String newId() {
        String a = "abcdefghijkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) b.append(a.charAt(r.nextInt(a.length())));
        return b.toString();
    }

    private static void setLayout(Game g, SeaBoard b) {
        g.myLayout = b.encode();
        g.mySalt = SeaBoard.newSalt();
        g.myCommit = SeaBoard.commit(g.myLayout, g.mySalt);
    }

    /** Challenges a friend with your fleet already placed. */
    public static Game invite(int account, long peer, SeaBoard fleet) {
        Game g = new Game();
        g.id = newId();
        g.account = account;
        g.peer = peer;
        g.starter = true;
        g.state = INVITE_OUT;
        g.created = g.updated = System.currentTimeMillis();
        setLayout(g, fleet);
        TLRPC.User u = MessagesController.getInstance(account).getUser(peer);
        if (u != null) g.oppName = UserObject.getUserName(u);
        map().put(g.id, g);
        save();
        send(g, "inv", LocaleController.getString(R.string.AvraSeaMsgInvite));
        SeaCards.sendInvite(g);
        notifyListeners(g, false);
        return g;
    }

    public static void accept(Game g, SeaBoard fleet) {
        if (g.state != INVITE_IN) return;
        setLayout(g, fleet);
        g.state = ACTIVE;
        g.updated = System.currentTimeMillis();
        save();
        send(g, "acc", LocaleController.getString(R.string.AvraSeaMsgAccepted));
        SeaSound.play(SeaSound.START);
        notifyListeners(g, false);
    }

    public static void decline(Game g) {
        if (g.state != INVITE_IN && g.state != INVITE_OUT) return;
        boolean in = g.state == INVITE_IN;
        g.state = DECLINED;
        save();
        send(g, "dec", LocaleController.getString(in ? R.string.AvraSeaMsgDeclined : R.string.AvraSeaMsgCancelled));
        if (g.starter) SeaCards.deleteCards(g, true);
        notifyListeners(g, false);
    }

    public static void fire(Game g, int cell) {
        if (!g.myTurn() || cell < 0 || cell >= SeaBoard.CELLS) return;
        for (int[] s : g.shots(g.me())) if (s[0] == cell) return;
        g.log = (g.log.isEmpty() ? "" : g.log + ".") + g.me() + String.format("%02d", cell) + "?";
        g.updated = System.currentTimeMillis();
        save();
        send(g, "sh", LocaleController.formatString(R.string.AvraSeaMsgShot, SeaBoard.cellName(cell, letters())));
        notifyListeners(g, false);
    }

    public static void resign(Game g) {
        if (g.state != ACTIVE) return;
        finish(g, -1, "resign");
        send(g, "res", LocaleController.getString(R.string.AvraSeaMsgResign));
        notifyListeners(g, false);
    }

    public static String letters() {
        return LocaleController.getString(R.string.AvraSeaLetters);
    }

    private static void send(Game g, String status, String human) {
        long self = UserConfig.getInstance(g.account).getClientUserId();
        long a = g.starter ? self : g.peer, b = g.starter ? g.peer : self;
        StringBuilder t = new StringBuilder();
        t.append("🚢 AvraSea\n").append(human).append('\n');
        t.append(TAG).append(" v=1 g=").append(g.id).append(" a=").append(a).append(" b=").append(b)
                .append(" s=").append(status).append(" l=").append(g.log.isEmpty() ? "-" : g.log)
                .append(" c=").append(g.myCommit);
        if (g.state == FINISHED) {
            t.append(" rv=").append(g.myLayout).append(':').append(g.mySalt);
            g.revealSent = true;
            save();
        }
        String name = selfName(g.account);
        if (!TextUtils.isEmpty(name)) t.append(" n=").append(name.replace(' ', '_'));
        String text = t.toString();
        AndroidUtilities.runOnUIThread(() -> ChessNet.sendRaw(g.account, g.peer, text, id -> {
            g.outIds.add(id);
            save();
        }));
    }

    private static String selfName(int account) {
        TLRPC.User u = UserConfig.getInstance(account).getCurrentUser();
        if (u == null) return "";
        String un = UserObject.getPublicUsername(u);
        return !TextUtils.isEmpty(un) ? "@" + un : UserObject.getUserName(u);
    }

    // ---- receiving ------------------------------------------------------------------------------

    /** Every new message (any thread): true for a hidden AvraSea message (dropped, applied, deleted). */
    public static boolean onMessage(int account, TLRPC.Message message) {
        if (message == null || message.message == null || message.peer_id == null) return false;
        if (message.peer_id.user_id == 0 || !message.message.contains(TAG + " ")) return false;
        final long peer = message.peer_id.user_id;
        final boolean out = message.out;
        final String text = message.message;
        final int id = message.id;
        AndroidUtilities.runOnUIThread(() -> {
            if (out) return; // our own echo: nothing new
            Game g = process(account, peer, text);
            if (id > 0) {
                ArrayList<Integer> ids = new ArrayList<>(1);
                ids.add(id);
                ChessNet.deleteRemote(account, ids);
                if (g != null && !g.inIds.contains(id)) {
                    g.inIds.add(id);
                    save();
                }
            }
        });
        return true;
    }

    /** Re-reads unread AvraSea messages from a person (missed updates). */
    public static void sync(int account, long peer, Runnable done) {
        TLRPC.InputPeer ip = MessagesController.getInstance(account).getInputPeer(peer);
        if (ip == null) {
            if (done != null) done.run();
            return;
        }
        TLRPC.TL_messages_search req = new TLRPC.TL_messages_search();
        req.peer = ip;
        req.q = TAG;
        req.filter = new TLRPC.TL_inputMessagesFilterEmpty();
        req.limit = 30;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                ArrayList<TLRPC.Message> list = new ArrayList<>(((TLRPC.messages_Messages) response).messages);
                Collections.sort(list, (x, y) -> Integer.compare(x.date, y.date));
                ArrayList<Integer> read = new ArrayList<>();
                for (TLRPC.Message m : list) {
                    if (m.message == null || m.out) continue;
                    process(account, peer, m.message);
                    read.add(m.id);
                }
                ChessNet.deleteRemote(account, read);
            }
            if (done != null) done.run();
        }));
    }

    private static HashMap<String, String> fields(String text) {
        Matcher mt = LINE.matcher(text);
        if (!mt.find()) return null;
        HashMap<String, String> f = new HashMap<>();
        for (String part : mt.group(1).trim().split("\\s+")) {
            int i = part.indexOf('=');
            if (i > 0) f.put(part.substring(0, i), part.substring(i + 1));
        }
        return f;
    }

    /** Is {@code theirs} our log with answers filled in and/or new entries added. */
    private static boolean extendsLog(ArrayList<String> ours, ArrayList<String> theirs) {
        if (theirs.size() < ours.size()) return false;
        for (int i = 0; i < ours.size(); i++) {
            String o = ours.get(i), t = theirs.get(i);
            if (o.equals(t)) continue;
            if (o.length() == 4 && t.length() == 4 && o.substring(0, 3).equals(t.substring(0, 3)) && o.charAt(3) == '?') continue;
            return false;
        }
        return true;
    }

    private static Game process(int account, long sender, String text) {
        try {
            HashMap<String, String> f = fields(text);
            if (f == null || !f.containsKey("g")) return null;
            long self = UserConfig.getInstance(account).getClientUserId();
            long a = Long.parseLong(f.get("a")), b = Long.parseLong(f.get("b"));
            if (a != self && b != self) return null;
            long peer = a == self ? b : a;
            if (sender != peer) return null;
            String id = f.get("g"), status = f.get("s");
            Game g = map().get(id);
            if (g == null) {
                if (!"inv".equals(status)) return null; // without our own layout this game cannot go on
                g = new Game();
                g.id = id;
                g.account = account;
                g.peer = peer;
                g.starter = false;
                g.state = INVITE_IN;
                g.created = System.currentTimeMillis();
                map().put(id, g);
            }
            if (g.account != account) return null;
            g.updated = System.currentTimeMillis();
            String n = f.get("n");
            if (n != null) g.oppName = n.replace('_', ' ');
            String c = f.get("c");
            if (!TextUtils.isEmpty(c) && TextUtils.isEmpty(g.oppCommit)) g.oppCommit = c;

            // the log
            String l = f.get("l");
            ArrayList<String> theirs = new ArrayList<>();
            if (l != null && !l.equals("-") && !l.isEmpty()) Collections.addAll(theirs, l.split("\\."));
            char event = 0;
            if (extendsLog(g.entries(), theirs) && !TextUtils.join(".", theirs).equals(g.log)) {
                g.log = TextUtils.join(".", theirs);
                if (!theirs.isEmpty()) {
                    String last = theirs.get(theirs.size() - 1);
                    if (last.charAt(0) == g.me()) event = last.charAt(3);
                }
            }

            switch (status == null ? "" : status) {
                case "acc":
                    if (g.state == INVITE_OUT) {
                        g.state = ACTIVE;
                        SeaSound.play(SeaSound.START);
                    }
                    break;
                case "dec":
                    if (g.state == INVITE_OUT || g.state == INVITE_IN) {
                        g.state = DECLINED;
                        if (g.starter) SeaCards.deleteCards(g, true);
                    }
                    break;
                case "res":
                    if (g.state == ACTIVE) finish(g, 1, "resign");
                    break;
            }
            String rv = f.get("rv");
            if (rv != null && TextUtils.isEmpty(g.oppLayout)) {
                int i = rv.indexOf(':');
                if (i > 0) verify(g, rv.substring(0, i), rv.substring(i + 1));
            }

            // the result of your shot
            if (event != 0) SeaSound.play(event == 'm' ? SeaSound.SPLASH : event == 'k' ? SeaSound.SUNK : SeaSound.HIT);
            if (g.state == ACTIVE && g.kills(g.me()) >= SeaBoard.FLEET.length) finish(g, 1, "fleet");

            // a shot at you: answer it from your own layout
            if (g.state == ACTIVE && g.turn() == '?') {
                ArrayList<String> e = g.entries();
                String last = e.get(e.size() - 1);
                if (last.charAt(0) == g.them()) {
                    int cell = Integer.parseInt(last.substring(1, 3));
                    SeaBoard mine = SeaBoard.decode(g.myLayout);
                    boolean[] hit = new boolean[SeaBoard.CELLS];
                    for (int[] s : g.shots(g.them())) if (s[1] != 'm' && s[1] != '?') hit[s[0]] = true;
                    char r = mine.resolve(cell, hit);
                    e.set(e.size() - 1, last.substring(0, 3) + r);
                    g.log = TextUtils.join(".", e);
                    SeaSound.play(r == 'm' ? SeaSound.SPLASH : r == 'k' ? SeaSound.SUNK : SeaSound.HIT);
                    if (g.kills(g.them()) >= SeaBoard.FLEET.length) finish(g, -1, "fleet");
                    String cellName = SeaBoard.cellName(cell, letters());
                    send(g, "rs", LocaleController.formatString(r == 'm' ? R.string.AvraSeaMsgMiss : r == 'k' ? R.string.AvraSeaMsgKill : R.string.AvraSeaMsgHit, cellName));
                }
            }
            if (g.state == FINISHED && !g.revealSent) send(g, "rev", LocaleController.getString(R.string.AvraSeaMsgReveal));
            save();
            notifyListeners(g, true);
            return g;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    /** Checks the revealed layout against the commitment and every answer the opponent gave. */
    private static void verify(Game g, String layout, String salt) {
        g.oppLayout = layout;
        SeaBoard b = SeaBoard.decode(layout);
        boolean ok = b.valid() && SeaBoard.commit(layout, salt).equals(g.oppCommit);
        if (ok) {
            boolean[] hit = new boolean[SeaBoard.CELLS];
            for (int[] s : g.shots(g.me())) {
                if (s[1] == '?') continue;
                char real = b.resolve(s[0], hit);
                if (real != s[1]) {
                    ok = false;
                    break;
                }
                if (real != 'm') hit[s[0]] = true;
            }
        }
        g.verified = ok;
        g.cheat = !ok;
    }

    private static void finish(Game g, int winner, String reason) {
        if (g.state == FINISHED) return;
        g.state = FINISHED;
        g.winner = winner;
        g.reason = reason;
        addResult("online", winner > 0);
        save();
        SeaSound.play(winner > 0 ? SeaSound.WIN : SeaSound.LOSE);
        AndroidUtilities.runOnUIThread(() -> {
            if (g.starter && !g.cardSent) {
                g.cardSent = true;
                save();
                SeaCards.sendResult(g);
                SeaCards.deleteCards(g, true);
            }
            ChessNet.deleteRemote(g.account, new ArrayList<>(g.inIds));
        }, 1500);
    }

    private static void notifyListeners(Game g, boolean incoming) {
        SeaCards.refresh(g);
        for (Listener l : listeners) {
            try {
                l.onSeaUpdate(g, incoming);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    /** Shots, hits and accuracy of a side for the result card. */
    public static int[] accuracy(Game g, char who) {
        int shots = 0, hits = 0;
        for (int[] s : g.shots(who)) {
            if (s[1] == '?') continue;
            shots++;
            if (s[1] != 'm') hits++;
        }
        return new int[]{shots, hits, shots == 0 ? 0 : hits * 100 / shots};
    }
}
