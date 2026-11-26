package com.avragramm.chess;

import android.content.SharedPreferences;
import android.text.TextUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AvraChess online: games with friends played through ordinary private-chat messages, Elo ratings
 * and the shared leaderboard. Avragram has no server, so:
 *
 *  - every move is a hidden message (sent silently, never shown in Avragram and deleted by the
 *    opponent's Avragram once read) whose last line carries the full game state: "#avrachess g=id t=tc w=uid b=uid m=e2e4.e7e5 c=ms,ms s=mv r=ratings n=name lb=…".
 *    Any Avragram rebuilds the game from the newest message, so a lost message is never a problem;
 *  - clocks use the message dates (server time);
 *  - ratings are classic Elo, kept by each player; both sides compute the same result;
 *  - the leaderboard is gossip: every message carries the sender's best players, so the table
 *    grows with everyone your opponents have played. Turn sharing off in the AvraChess settings.
 */
public final class ChessNet {

    private ChessNet() {
    }

    // ---- time controls --------------------------------------------------------------------------

    public static final int CAT_BULLET = 0, CAT_BLITZ = 1, CAT_RAPID = 2, CAT_DAILY = 3;
    public static final int[] CAT_NAMES = {R.string.AvraChessBullet, R.string.AvraChessBlitz, R.string.AvraChessRapid, R.string.AvraChessDaily};
    public static final String[] CAT_EMOJI = {"⚡", "🔥", "🕒", "📅"};
    /** {base seconds, increment seconds, category}; the last one is "3 days per move". */
    public static final int[][] TC = {{60, 0, CAT_BULLET}, {120, 1, CAT_BULLET}, {180, 2, CAT_BLITZ}, {300, 0, CAT_BLITZ},
            {600, 0, CAT_RAPID}, {900, 10, CAT_RAPID}, {3 * 86400, 0, CAT_DAILY}};
    public static final int TC_DAILY = 6;
    public static final int DEFAULT_RATING = 1200;

    public static String tcTitle(int tc) {
        if (tc < 0 || tc >= TC.length) return "?";
        int[] t = TC[tc];
        String cat = LocaleController.getString(CAT_NAMES[t[2]]);
        if (t[2] == CAT_DAILY) return CAT_EMOJI[CAT_DAILY] + " " + cat;
        return CAT_EMOJI[t[2]] + " " + cat + " " + (t[0] / 60) + "+" + t[1];
    }

    public static boolean isDaily(int tc) {
        return tc == TC_DAILY;
    }

    // ---- games ----------------------------------------------------------------------------------

    public static final int INVITE_OUT = 0, INVITE_IN = 1, ACTIVE = 2, FINISHED = 3, DECLINED = 4;
    public static final int RESULT_NONE = 2;

    public static final class Game {
        public String id;
        public int account;
        public long peer;
        public int myColor;
        public int tc;
        public final ArrayList<String> moves = new ArrayList<>();
        public long wms, bms;
        /** Server time (ms) of the last state change: the clock of the side to move runs from here. */
        public long lastMs;
        public int state;
        public int result = RESULT_NONE;
        public String reason = "";
        public boolean applied;
        public String oppName = "";
        public int oppRating = DEFAULT_RATING;
        public boolean drawOfferIn, drawOfferOut;
        public long created;
        public int myRatingBefore, ratingDelta;
        /** You sent the challenge: you post the result card at the end. */
        public boolean starter;
        public boolean cardSent;
        /** Hidden protocol messages: received ones (deleted once read) and your own. */
        public final ArrayList<Integer> inIds = new ArrayList<>(), outIds = new ArrayList<>();

        public ChessBoard board() {
            ChessBoard b = new ChessBoard();
            for (String u : moves) {
                int m = b.parseUci(u);
                if (m == 0) break;
                b.make(m);
            }
            return b;
        }

        public int sideToMove() {
            return moves.size() % 2 == 0 ? ChessBoard.WHITE : ChessBoard.BLACK;
        }

        public boolean myTurn() {
            return state == ACTIVE && sideToMove() == myColor;
        }

        /** Remaining time of a color right now, ms (daily: until the move deadline). */
        public long remaining(int color) {
            long now = ConnectionsManager.getInstance(account).getCurrentTimeMillis();
            if (isDaily(tc)) {
                if (state != ACTIVE || color != sideToMove()) return TC[tc][0] * 1000L;
                return TC[tc][0] * 1000L - (now - lastMs);
            }
            long stored = color == ChessBoard.WHITE ? wms : bms;
            if (state == ACTIVE && color == sideToMove() && lastMs > 0) stored -= now - lastMs;
            return stored;
        }

        JSONObject toJson() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id).put("a", account).put("p", peer).put("c", myColor).put("tc", tc)
                    .put("m", TextUtils.join(".", moves)).put("w", wms).put("b", bms).put("l", lastMs)
                    .put("s", state).put("r", result).put("why", reason).put("ap", applied)
                    .put("on", oppName).put("or", oppRating).put("di", drawOfferIn).put("do", drawOfferOut)
                    .put("cr", created).put("rb", myRatingBefore).put("rd", ratingDelta)
                    .put("st", starter).put("cs", cardSent).put("ii", ids(inIds)).put("oi", ids(outIds));
            return o;
        }

        static Game fromJson(JSONObject o) {
            Game g = new Game();
            g.id = o.optString("id");
            g.account = o.optInt("a");
            g.peer = o.optLong("p");
            g.myColor = o.optInt("c", 1);
            g.tc = o.optInt("tc");
            String m = o.optString("m");
            if (!m.isEmpty()) Collections.addAll(g.moves, m.split("\\."));
            g.wms = o.optLong("w");
            g.bms = o.optLong("b");
            g.lastMs = o.optLong("l");
            g.state = o.optInt("s");
            g.result = o.optInt("r", RESULT_NONE);
            g.reason = o.optString("why");
            g.applied = o.optBoolean("ap");
            g.oppName = o.optString("on");
            g.oppRating = o.optInt("or", DEFAULT_RATING);
            g.drawOfferIn = o.optBoolean("di");
            g.drawOfferOut = o.optBoolean("do");
            g.created = o.optLong("cr");
            g.myRatingBefore = o.optInt("rb");
            g.ratingDelta = o.optInt("rd");
            g.starter = o.optBoolean("st");
            g.cardSent = o.optBoolean("cs");
            readIds(o.optJSONArray("ii"), g.inIds);
            readIds(o.optJSONArray("oi"), g.outIds);
            return g;
        }
    }

    private static JSONArray ids(ArrayList<Integer> list) {
        JSONArray a = new JSONArray();
        for (int i = Math.max(0, list.size() - 40); i < list.size(); i++) a.put(list.get(i));
        return a;
    }

    private static void readIds(JSONArray a, ArrayList<Integer> out) {
        for (int i = 0; a != null && i < a.length(); i++) out.add(a.optInt(i));
    }

    public interface Listener {
        void onChessUpdate(Game game, boolean incoming);
    }

    private static final CopyOnWriteArrayList<Listener> listeners = new CopyOnWriteArrayList<>();
    private static HashMap<String, Game> games;

    /** The game on screen right now (it plays its own move sounds). */
    public static String openGameId;

    public static void addListener(Listener l) {
        listeners.add(l);
    }

    public static void removeListener(Listener l) {
        listeners.remove(l);
    }

    private static SharedPreferences prefs() {
        return ChessStyle.prefs();
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

    private static synchronized void save() {
        try {
            JSONArray a = new JSONArray();
            ArrayList<Game> list = new ArrayList<>(map().values());
            // keep every unfinished game and the 30 newest finished ones
            Collections.sort(list, (x, y) -> Long.compare(y.created, x.created));
            int finished = 0;
            for (Game g : list) {
                if (g.state == FINISHED || g.state == DECLINED) {
                    if (++finished > 30) {
                        games.remove(g.id);
                        continue;
                    }
                }
                a.put(g.toJson());
            }
            prefs().edit().putString("games", a.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    public static Game get(String id) {
        return map().get(id);
    }

    /** Games of the current account, the ones needing your answer first. */
    public static ArrayList<Game> list(int account) {
        ArrayList<Game> out = new ArrayList<>();
        for (Game g : map().values()) if (g.account == account) out.add(g);
        Collections.sort(out, (x, y) -> {
            int px = priority(x), py = priority(y);
            if (px != py) return Integer.compare(px, py);
            return Long.compare(Math.max(y.lastMs, y.created), Math.max(x.lastMs, x.created));
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

    public static void delete(String id) {
        map().remove(id);
        save();
    }

    // ---- ratings --------------------------------------------------------------------------------

    public static int rating(int cat) {
        return prefs().getInt("rating_" + cat, DEFAULT_RATING);
    }

    public static int games(int cat) {
        return prefs().getInt("games_" + cat, 0);
    }

    public static int[] wdl(int cat) {
        return new int[]{prefs().getInt("w_" + cat, 0), prefs().getInt("d_" + cat, 0), prefs().getInt("l_" + cat, 0)};
    }

    public static int bestRating() {
        int best = 0;
        for (int c = 0; c < 4; c++) if (games(c) > 0) best = Math.max(best, rating(c));
        return best == 0 ? DEFAULT_RATING : best;
    }

    public static int totalGames() {
        int n = 0;
        for (int c = 0; c < 4; c++) n += games(c);
        return n;
    }

    static double expected(int r, int opp) {
        return 1.0 / (1 + Math.pow(10, (opp - r) / 400.0));
    }

    static int kFactor(int games) {
        return games < 20 ? 40 : 20;
    }

    /** Applies the Elo change of a finished rated game once. */
    private static void applyRating(Game g) {
        if (g.applied || g.result == RESULT_NONE || g.moves.size() < 2) {
            g.applied = true;
            return;
        }
        g.applied = true;
        int cat = TC[g.tc][2];
        int r = rating(cat), n = games(cat);
        double score = g.result == 0 ? 0.5 : g.result == g.myColor ? 1 : 0;
        int delta = (int) Math.round(kFactor(n) * (score - expected(r, g.oppRating)));
        g.myRatingBefore = r;
        g.ratingDelta = delta;
        SharedPreferences.Editor e = prefs().edit();
        e.putInt("rating_" + cat, Math.max(100, r + delta));
        e.putInt("games_" + cat, n + 1);
        String k = score == 1 ? "w_" : score == 0 ? "l_" : "d_";
        e.putInt(k + cat, prefs().getInt(k + cat, 0) + 1);
        e.apply();
        // the opponent's new rating for the table (they will report the exact one next time)
        int oppDelta = (int) Math.round(32 * ((1 - score) - expected(g.oppRating, r)));
        Player p = player(g.peer, true);
        p.ratings[cat] = Math.max(100, g.oppRating + oppDelta);
        p.games++;
        p.updated = System.currentTimeMillis();
        savePlayers();
    }

    // ---- leaderboard ----------------------------------------------------------------------------

    public static final class Player {
        public long id;
        public String name = "";
        public final int[] ratings = new int[4];
        public int games;
        /** You played this person yourself (not only heard of them from someone). */
        public boolean direct;
        public long updated;
        /** Best rating heard from other players' tables (category unknown). */
        public int heard;

        public int best() {
            int b = 0;
            for (int r : ratings) b = Math.max(b, r);
            return b > 0 ? b : heard;
        }
    }

    private static HashMap<Long, Player> players;

    private static synchronized HashMap<Long, Player> players() {
        if (players == null) {
            players = new HashMap<>();
            try {
                JSONArray a = new JSONArray(prefs().getString("players", "[]"));
                for (int i = 0; i < a.length(); i++) {
                    JSONObject o = a.getJSONObject(i);
                    Player p = new Player();
                    p.id = o.optLong("id");
                    p.name = o.optString("n");
                    JSONArray r = o.optJSONArray("r");
                    for (int c = 0; r != null && c < 4 && c < r.length(); c++) p.ratings[c] = r.optInt(c);
                    p.games = o.optInt("g");
                    p.direct = o.optBoolean("d");
                    p.updated = o.optLong("u");
                    p.heard = o.optInt("h");
                    players.put(p.id, p);
                }
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
        return players;
    }

    private static synchronized void savePlayers() {
        try {
            ArrayList<Player> list = new ArrayList<>(players().values());
            Collections.sort(list, (x, y) -> Integer.compare(y.best(), x.best()));
            JSONArray a = new JSONArray();
            for (int i = 0; i < list.size() && i < 300; i++) {
                Player p = list.get(i);
                JSONArray r = new JSONArray();
                for (int c = 0; c < 4; c++) r.put(p.ratings[c]);
                a.put(new JSONObject().put("id", p.id).put("n", p.name).put("r", r).put("g", p.games).put("d", p.direct).put("u", p.updated).put("h", p.heard));
            }
            prefs().edit().putString("players", a.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private static Player player(long id, boolean create) {
        Player p = players().get(id);
        if (p == null && create) {
            p = new Player();
            p.id = id;
            players().put(id, p);
        }
        return p;
    }

    public static boolean shareTable() {
        return ChessStyle.getBool("shareTable", true);
    }

    /** Everyone known, plus you; best first. category -1 = best of all time controls. */
    public static ArrayList<Player> leaderboard(int account, int category) {
        ArrayList<Player> out = new ArrayList<>();
        long self = UserConfig.getInstance(account).getClientUserId();
        for (Player p : players().values()) {
            if (p.id == self) continue;
            int r = category < 0 ? p.best() : p.ratings[category];
            if (r > 0) out.add(p);
        }
        Player me = new Player();
        me.id = self;
        me.direct = true;
        me.name = selfName(account);
        for (int c = 0; c < 4; c++) me.ratings[c] = games(c) > 0 ? rating(c) : 0;
        me.games = totalGames();
        if (category < 0 && me.best() == 0) me.ratings[CAT_RAPID] = DEFAULT_RATING;
        if (category < 0 || me.ratings[category] > 0) out.add(me);
        final int cat = category;
        Collections.sort(out, (x, y) -> Integer.compare(cat < 0 ? y.best() : y.ratings[cat], cat < 0 ? x.best() : x.ratings[cat]));
        return out;
    }

    /** "@username", else the name, else "ID 123456789". */
    public static String displayName(int account, Player p) {
        TLRPC.User u = MessagesController.getInstance(account).getUser(p.id);
        if (u != null) {
            String un = UserObject.getPublicUsername(u);
            if (!TextUtils.isEmpty(un)) return "@" + un;
            String n = UserObject.getUserName(u);
            if (!TextUtils.isEmpty(n)) return n;
        }
        if (!TextUtils.isEmpty(p.name)) return p.name;
        return "ID " + p.id;
    }

    private static String selfName(int account) {
        TLRPC.User u = UserConfig.getInstance(account).getCurrentUser();
        if (u == null) return "";
        String un = UserObject.getPublicUsername(u);
        return !TextUtils.isEmpty(un) ? "@" + un : UserObject.getUserName(u);
    }

    private static String gossip(int account) {
        if (!shareTable()) return "";
        ArrayList<Player> list = leaderboard(account, -1);
        StringBuilder b = new StringBuilder();
        int n = 0;
        for (Player p : list) {
            if (n++ >= 8) break;
            if (b.length() > 0) b.append(',');
            b.append(p.id).append(':').append(p.best()).append(':').append(enc(p.name));
        }
        return b.toString();
    }

    private static void readGossip(int account, String lb) {
        if (TextUtils.isEmpty(lb)) return;
        long self = UserConfig.getInstance(account).getClientUserId();
        for (String e : lb.split(",")) {
            String[] x = e.split(":", 3);
            if (x.length < 2) continue;
            try {
                long id = Long.parseLong(x[0]);
                int r = Integer.parseInt(x[1]);
                if (id == self || r < 100 || r > 3500) continue;
                Player p = player(id, true);
                if (!p.direct) { // never overwrite what you saw yourself with hearsay
                    p.heard = r;
                    p.updated = System.currentTimeMillis();
                }
                if (x.length > 2 && !TextUtils.isEmpty(x[2]) && TextUtils.isEmpty(p.name)) p.name = dec(x[2]);
            } catch (Exception ignore) {
            }
        }
    }

    private static String enc(String s) {
        try {
            return URLEncoder.encode(s == null ? "" : s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    private static String dec(String s) {
        try {
            return URLDecoder.decode(s, "UTF-8");
        } catch (Exception e) {
            return "";
        }
    }

    // ---- protocol: sending ----------------------------------------------------------------------

    private static final String TAG = "#avrachess";
    private static final Pattern LINE = Pattern.compile("#avrachess ([^\\n]*)$");

    private static String newId() {
        String a = "abcdefghijkmnpqrstuvwxyz23456789";
        Random r = new Random();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 6; i++) b.append(a.charAt(r.nextInt(a.length())));
        return b.toString();
    }

    private static long now(int account) {
        return ConnectionsManager.getInstance(account).getCurrentTimeMillis();
    }

    /** Sends a challenge. color: 1 white, -1 black, 0 random. */
    public static Game invite(int account, long peer, int tc, int color) {
        Game g = new Game();
        g.id = newId();
        g.account = account;
        g.peer = peer;
        g.tc = tc;
        g.myColor = color == 0 ? (new Random().nextBoolean() ? 1 : -1) : color;
        g.wms = g.bms = TC[tc][0] * 1000L;
        g.state = INVITE_OUT;
        g.created = System.currentTimeMillis();
        g.starter = true;
        TLRPC.User u = MessagesController.getInstance(account).getUser(peer);
        if (u != null) g.oppName = UserObject.getUserName(u);
        Player p = players().get(peer);
        if (p != null && p.ratings[TC[tc][2]] > 0) g.oppRating = p.ratings[TC[tc][2]];
        map().put(g.id, g);
        save();
        send(g, "inv", LocaleController.formatString(R.string.AvraChessMsgInvite,
                LocaleController.getString(g.myColor == 1 ? R.string.AvraChessMsgYouBlack : R.string.AvraChessMsgYouWhite)));
        ChessCards.sendInvite(g);
        notifyListeners(g, false);
        return g;
    }

    public static void accept(Game g) {
        if (g.state != INVITE_IN) return;
        g.state = ACTIVE;
        g.lastMs = now(g.account);
        save();
        send(g, "acc", LocaleController.getString(R.string.AvraChessMsgAccepted));
        ChessSound.play(ChessSound.START);
        notifyListeners(g, false);
    }

    public static void decline(Game g) {
        if (g.state != INVITE_IN && g.state != INVITE_OUT) return;
        boolean wasIn = g.state == INVITE_IN;
        g.state = DECLINED;
        save();
        send(g, "dec", LocaleController.getString(wasIn ? R.string.AvraChessMsgDeclined : R.string.AvraChessMsgCancelled));
        if (g.starter) ChessCards.deleteCards(g, true);
        notifyListeners(g, false);
    }

    /** Your move (already legal on the board). */
    public static void move(Game g, ChessBoard before, int m) {
        if (!g.myTurn()) return;
        String san = before.san(m);
        long t = now(g.account);
        if (!isDaily(g.tc)) {
            long left = g.remaining(g.myColor) + TC[g.tc][1] * 1000L;
            if (g.myColor == 1) g.wms = Math.max(0, left);
            else g.bms = Math.max(0, left);
        }
        g.lastMs = t;
        g.moves.add(ChessBoard.uci(m));
        g.drawOfferIn = false;
        String human = LocaleController.formatString(R.string.AvraChessMsgMove, (g.moves.size() + 1) / 2, san);
        String status = "mv";
        if (g.drawOfferOut) status = "dof";
        checkEnd(g);
        if (g.state == FINISHED) human += " · " + resultText(g);
        save();
        send(g, status, human);
        notifyListeners(g, false);
    }

    public static void resign(Game g) {
        if (g.state != ACTIVE) return;
        finish(g, -g.myColor, "resign");
        send(g, "res", LocaleController.getString(R.string.AvraChessMsgResign));
        notifyListeners(g, false);
    }

    public static void offerDraw(Game g) {
        if (g.state != ACTIVE) return;
        if (g.drawOfferIn) {
            finish(g, 0, "agreed");
            send(g, "dac", LocaleController.getString(R.string.AvraChessMsgDrawAgreed));
        } else {
            g.drawOfferOut = true;
            save();
            send(g, "dof", LocaleController.getString(R.string.AvraChessMsgDrawOffer));
        }
        notifyListeners(g, false);
    }

    public static void declineDraw(Game g) {
        if (!g.drawOfferIn) return;
        g.drawOfferIn = false;
        save();
        send(g, "ddc", LocaleController.getString(R.string.AvraChessMsgDrawDeclined));
        notifyListeners(g, false);
    }

    /** Claims a win (or loses) when a clock reached zero. */
    public static void claimTimeout(Game g) {
        if (g.state != ACTIVE) return;
        int side = g.sideToMove();
        if (g.remaining(side) > 0) return;
        ChessBoard b = g.board();
        boolean canMate = false;
        for (int s = 0; s < 64; s++) {
            int p = b.sq[s];
            if (p != 0 && (p > 0 ? 1 : -1) == -side && Math.abs(p) != ChessBoard.KING) {
                canMate = true;
                break;
            }
        }
        finish(g, canMate ? -side : 0, "time");
        send(g, "flag", resultText(g));
        notifyListeners(g, false);
    }

    private static void send(Game g, String status, String human) {
        StringBuilder b = new StringBuilder();
        b.append("♟ AvraChess · ").append(tcTitle(g.tc)).append('\n').append(human).append('\n');
        long self = UserConfig.getInstance(g.account).getClientUserId();
        long white = g.myColor == 1 ? self : g.peer, black = g.myColor == 1 ? g.peer : self;
        b.append(TAG).append(" v=1 g=").append(g.id).append(" t=").append(g.tc)
                .append(" w=").append(white).append(" b=").append(black)
                .append(" m=").append(g.moves.isEmpty() ? "-" : TextUtils.join(".", g.moves))
                .append(" c=").append(g.wms).append(',').append(g.bms)
                .append(" s=").append(status);
        if (g.state == FINISHED) b.append(" res=").append(g.result).append(" why=").append(g.reason);
        StringBuilder r = new StringBuilder();
        for (int c = 0; c < 4; c++) {
            if (c > 0) r.append(',');
            r.append(games(c) > 0 || c == TC[g.tc][2] ? rating(c) : 0);
        }
        b.append(" r=").append(r);
        String name = selfName(g.account);
        if (!TextUtils.isEmpty(name)) b.append(" n=").append(enc(name));
        String lb = gossip(g.account);
        if (!lb.isEmpty()) b.append(" lb=").append(lb);
        String text = b.toString();
        AndroidUtilities.runOnUIThread(() -> sendHidden(g, text));
    }

    /**
     * Sends a protocol message without adding it to the chat here: a raw messages.sendMessage whose
     * result only moves the update state on. The echo (other devices, difference) is dropped by
     * {@link #onMessage}; the opponent's Avragram reads it, then deletes it for both sides.
     */
    private static void sendHidden(Game g, String text) {
        sendRaw(g.account, g.peer, text, id -> {
            g.outIds.add(id);
            save();
        });
    }

    /** Hidden send shared by AvraChess and AvraSea; {@code onId} gets the new message id (UI thread). */
    public static void sendRaw(int account, long peer, String text, org.telegram.messenger.Utilities.Callback<Integer> onId) {
        MessagesController mc = MessagesController.getInstance(account);
        TLRPC.InputPeer ip = mc.getInputPeer(peer);
        if (ip == null) return;
        TLRPC.TL_messages_sendMessage req = new TLRPC.TL_messages_sendMessage();
        req.peer = ip;
        req.message = text;
        req.random_id = org.telegram.messenger.Utilities.random.nextLong();
        req.no_webpage = true;
        req.silent = true;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            int id = 0;
            if (response instanceof TLRPC.TL_updateShortSentMessage) {
                TLRPC.TL_updateShortSentMessage r = (TLRPC.TL_updateShortSentMessage) response;
                id = r.id;
                mc.processNewDifferenceParams(-1, r.pts, r.date, r.pts_count);
            } else if (response instanceof TLRPC.Updates) {
                TLRPC.Updates u = (TLRPC.Updates) response;
                for (TLRPC.Update up : u.updates) {
                    if (up instanceof org.telegram.tgnet.tl.TL_update.TL_updateMessageID && ((org.telegram.tgnet.tl.TL_update.TL_updateMessageID) up).random_id == req.random_id) {
                        id = ((org.telegram.tgnet.tl.TL_update.TL_updateMessageID) up).id;
                    }
                }
                mc.processUpdates(u, false);
            } else if (error != null) {
                FileLog.e("AvraChess send: " + error.text);
            }
            if (id > 0 && onId != null) onId.run(id);
        }));
    }

    /** Deletes messages for both sides without touching this client's chat (they were never shown). */
    public static void deleteRemote(int account, ArrayList<Integer> ids) {
        if (ids == null || ids.isEmpty()) return;
        TLRPC.TL_messages_deleteMessages req = new TLRPC.TL_messages_deleteMessages();
        req.revoke = true;
        req.id.addAll(ids);
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> {
            if (response instanceof TLRPC.TL_messages_affectedMessages) {
                TLRPC.TL_messages_affectedMessages r = (TLRPC.TL_messages_affectedMessages) response;
                AndroidUtilities.runOnUIThread(() -> MessagesController.getInstance(account).processNewDifferenceParams(-1, r.pts, -1, r.pts_count));
            }
        });
    }

    /** First line of every hidden protocol message (also what a push notification starts with). */
    public static boolean isProtocolText(String text) {
        return text != null && (text.startsWith("♟ AvraChess") || text.contains(TAG + " v=")
                || text.startsWith("🚢 AvraSea") || text.contains("#avrasea v="));
    }

    /** History loads: drops the hidden protocol messages (keeps the page if nothing else is left). */
    public static void filterLoaded(ArrayList<TLRPC.Message> list) {
        if (list == null || list.isEmpty()) return;
        int keep = 0;
        for (TLRPC.Message m : list) if (m == null || !isProtocolText(m.message) || m.peer_id == null || m.peer_id.user_id == 0) keep++;
        if (keep == 0 || keep == list.size()) return;
        for (int i = list.size() - 1; i >= 0; i--) {
            TLRPC.Message m = list.get(i);
            if (m != null && m.peer_id != null && m.peer_id.user_id != 0 && isProtocolText(m.message)) list.remove(i);
        }
    }

    // ---- protocol: receiving --------------------------------------------------------------------

    /**
     * Called for every new message (any thread) before it is stored. Returns true for a hidden AvraChess
     * protocol message: it is dropped (never shown, no notification), applied, and — if it came from the
     * opponent — deleted for both sides.
     */
    public static boolean onMessage(int account, TLRPC.Message message) {
        if (message == null || message.message == null || message.peer_id == null) return false;
        if (message.peer_id.user_id == 0 || !message.message.contains(TAG)) return false;
        final long peer = message.peer_id.user_id;
        final boolean out = message.out;
        final String text = message.message;
        final long date = message.date * 1000L;
        final int id = message.id;
        AndroidUtilities.runOnUIThread(() -> {
            long self = UserConfig.getInstance(account).getClientUserId();
            Game g = process(account, out ? self : peer, text, date, !out);
            if (!out && id > 0) {
                ArrayList<Integer> ids = new ArrayList<>(1);
                ids.add(id);
                deleteRemote(account, ids);
                if (g != null && !g.inIds.contains(id)) {
                    g.inIds.add(id);
                    save();
                }
            }
        });
        return true;
    }

    /** Re-reads the last AvraChess messages with a person (after a reinstall or missed updates). */
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
        req.limit = 40;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (response instanceof TLRPC.messages_Messages) {
                ArrayList<TLRPC.Message> list = new ArrayList<>(((TLRPC.messages_Messages) response).messages);
                Collections.sort(list, (x, y) -> Integer.compare(x.date, y.date));
                long self = UserConfig.getInstance(account).getClientUserId();
                ArrayList<Integer> read = new ArrayList<>();
                for (TLRPC.Message m : list) {
                    if (m.message == null) continue;
                    long sender = m.out ? self : peer;
                    Game g = process(account, sender, m.message, m.date * 1000L, !m.out);
                    if (!m.out) read.add(m.id);
                    else if (g != null && g.state == FINISHED && !isLatestOut(g, m.id)) read.add(m.id);
                }
                deleteRemote(account, read);
            }
            if (done != null) done.run();
        }));
    }

    private static boolean isLatestOut(Game g, int id) {
        return !g.outIds.isEmpty() && g.outIds.get(g.outIds.size() - 1) == id;
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

    private static Game process(int account, long sender, String text, long dateMs, boolean incoming) {
        try {
            HashMap<String, String> f = fields(text);
            if (f == null || !f.containsKey("g")) return null;
            long self = UserConfig.getInstance(account).getClientUserId();
            long white = Long.parseLong(f.get("w")), black = Long.parseLong(f.get("b"));
            if (white != self && black != self) return null;
            long peer = white == self ? black : white;
            if (incoming && sender != peer) return null;
            int tc = Integer.parseInt(f.get("t"));
            if (tc < 0 || tc >= TC.length) return null;
            String id = f.get("g"), status = f.get("s");
            Game g = map().get(id);
            boolean isNew = false;
            if (g == null) {
                if (!"inv".equals(status) && !incoming) return null;
                g = new Game();
                g.id = id;
                g.account = account;
                g.peer = peer;
                g.tc = tc;
                g.myColor = white == self ? 1 : -1;
                g.wms = g.bms = TC[tc][0] * 1000L;
                g.state = incoming ? INVITE_IN : INVITE_OUT;
                g.created = dateMs;
                if (!"inv".equals(status)) g.state = ACTIVE;
                g.starter = !incoming && "inv".equals(status);
                map().put(id, g);
                isNew = true;
            }
            if (g.account != account) return null;

            if (incoming) {
                // what the opponent says about themselves
                String n = f.get("n");
                if (n != null) g.oppName = dec(n);
                String r = f.get("r");
                if (r != null) {
                    String[] rs = r.split(",");
                    Player p = player(peer, true);
                    p.direct = true;
                    if (!TextUtils.isEmpty(g.oppName)) p.name = g.oppName;
                    for (int c = 0; c < 4 && c < rs.length; c++) {
                        int v = Integer.parseInt(rs[c]);
                        if (v > 0) p.ratings[c] = v;
                    }
                    int mine = TC[tc][2] < rs.length ? Integer.parseInt(rs[TC[tc][2]]) : 0;
                    if (mine > 0 && g.state != FINISHED) g.oppRating = mine;
                    p.updated = System.currentTimeMillis();
                }
                readGossip(account, f.get("lb"));
                savePlayers();
            }

            // the move list: adopt it when it continues ours
            String m = f.get("m");
            ArrayList<String> theirs = new ArrayList<>();
            if (m != null && !m.equals("-") && !m.isEmpty()) Collections.addAll(theirs, m.split("\\."));
            boolean moved = false;
            if (theirs.size() > g.moves.size() && startsWith(theirs, g.moves) && legal(theirs)) {
                g.moves.clear();
                g.moves.addAll(theirs);
                moved = true;
            }
            String c = f.get("c");
            if (c != null && (moved || isNew)) {
                String[] cs = c.split(",");
                g.wms = Long.parseLong(cs[0]);
                g.bms = Long.parseLong(cs[1]);
            }
            if (moved || isNew) g.lastMs = dateMs;
            // an older message (re-read by sync) must not replay its draw offer / result
            boolean current = theirs.size() == g.moves.size();
            if (!current) status = "";

            switch (status == null ? "" : status) {
                case "inv":
                    break;
                case "acc":
                    if (g.state == INVITE_OUT || g.state == INVITE_IN) {
                        g.state = ACTIVE;
                        g.lastMs = dateMs;
                        if (incoming) ChessSound.play(ChessSound.START);
                    }
                    break;
                case "dec":
                    if (g.state == INVITE_OUT || g.state == INVITE_IN) {
                        g.state = DECLINED;
                        if (g.starter && incoming) ChessCards.deleteCards(g, true);
                    }
                    break;
                case "res":
                    if (g.state == ACTIVE) finish(g, incoming ? g.myColor : -g.myColor, "resign");
                    break;
                case "dof":
                    if (g.state == ACTIVE && incoming) g.drawOfferIn = true;
                    break;
                case "ddc":
                    if (incoming) g.drawOfferOut = false;
                    break;
                case "dac":
                    if (g.state == ACTIVE) finish(g, 0, "agreed");
                    break;
                case "flag":
                    if (g.state == ACTIVE && f.containsKey("res")) finish(g, Integer.parseInt(f.get("res")), "time");
                    break;
                default:
                    if (g.state == INVITE_OUT || g.state == INVITE_IN) g.state = ACTIVE;
                    break;
            }
            if (moved && incoming) {
                g.drawOfferOut = false;
                if ("dof".equals(status)) g.drawOfferIn = true;
            } else if (moved) {
                g.drawOfferIn = false;
            }
            if (g.state == ACTIVE) checkEnd(g);
            save();
            if (incoming) {
                if (moved && !id.equals(openGameId)) ChessSound.play(ChessSound.NOTIFY);
                notifyListeners(g, true);
            } else {
                notifyListeners(g, false);
            }
            return g;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    private static boolean startsWith(ArrayList<String> a, ArrayList<String> prefix) {
        if (prefix.size() > a.size()) return false;
        for (int i = 0; i < prefix.size(); i++) if (!a.get(i).equals(prefix.get(i))) return false;
        return true;
    }

    private static boolean legal(ArrayList<String> moves) {
        ChessBoard b = new ChessBoard();
        for (String u : moves) {
            if (b.status() != ChessBoard.ONGOING) return false;
            int m = b.parseUci(u);
            if (m == 0) return false;
            b.make(m);
        }
        return true;
    }

    private static void checkEnd(Game g) {
        int st = g.board().status();
        if (st == ChessBoard.ONGOING) return;
        if (st == ChessBoard.MATE) finish(g, -g.sideToMove(), "mate");
        else finish(g, 0, st == ChessBoard.STALEMATE ? "stalemate" : st == ChessBoard.REPETITION ? "repetition" : st == ChessBoard.FIFTY ? "fifty" : "material");
    }

    private static void finish(Game g, int result, String reason) {
        if (g.state == FINISHED) return;
        g.state = FINISHED;
        g.result = result;
        g.reason = reason;
        g.drawOfferIn = g.drawOfferOut = false;
        applyRating(g);
        save();
        ChessSound.play(ChessSound.END);
        final Game fg = g;
        // after the final protocol message went out: the result card from whoever started the game
        AndroidUtilities.runOnUIThread(() -> {
            if (fg.starter && !fg.cardSent) {
                fg.cardSent = true;
                save();
                ChessCards.sendResult(fg);
                ChessCards.deleteCards(fg, true);
            }
            // received protocol messages are gone already; repeat in case a delete failed
            deleteRemote(fg.account, new ArrayList<>(fg.inIds));
        }, 1500);
    }

    public static String resultText(Game g) {
        String who = g.result == 1 ? LocaleController.getString(R.string.AvraChessWhiteWins)
                : g.result == -1 ? LocaleController.getString(R.string.AvraChessBlackWins)
                : LocaleController.getString(R.string.AvraChessDraw);
        return who + " · " + reasonText(g.reason);
    }

    public static String reasonText(String reason) {
        switch (reason == null ? "" : reason) {
            case "mate": return LocaleController.getString(R.string.AvraChessReasonMate);
            case "resign": return LocaleController.getString(R.string.AvraChessReasonResign);
            case "time": return LocaleController.getString(R.string.AvraChessReasonTime);
            case "agreed": return LocaleController.getString(R.string.AvraChessReasonAgreed);
            case "stalemate": return LocaleController.getString(R.string.AvraChessReasonStalemate);
            case "repetition": return LocaleController.getString(R.string.AvraChessReasonRepetition);
            case "fifty": return LocaleController.getString(R.string.AvraChessReasonFifty);
            default: return LocaleController.getString(R.string.AvraChessReasonMaterial);
        }
    }

    private static void notifyListeners(Game g, boolean incoming) {
        ChessCards.refresh(g);
        for (Listener l : listeners) {
            try {
                l.onChessUpdate(g, incoming);
            } catch (Exception e) {
                FileLog.e(e);
            }
        }
    }

    /** Games waiting for your answer (badge in the menu). */
    public static int pending(int account) {
        int n = 0;
        for (Game g : map().values()) if (g.account == account && (g.state == INVITE_IN || g.myTurn())) n++;
        return n;
    }

    @SuppressWarnings("unused")
    private static void cleanup() {
        Iterator<Game> it = map().values().iterator();
        while (it.hasNext()) if (it.next().state == DECLINED) it.remove();
    }
}
