package com.avragramm.chess;

import org.json.JSONArray;
import org.json.JSONObject;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Game review: every move is checked by {@link ChessAi#analyse} and labelled like on the big chess
 * sites — brilliant, great, best, good, okay, book, inaccuracy, mistake, blunder — from how much winning
 * chance it threw away. Also gives the accuracy of each side and the evaluation after each move.
 */
public final class ChessReview {

    private ChessReview() {
    }

    public static final int BRILLIANT = 0, GREAT = 1, BEST = 2, GOOD = 3, OK = 4, BOOK = 5, INACCURACY = 6, MISTAKE = 7, BLUNDER = 8;
    public static final int KINDS = 9;
    public static final int[] NAMES = {R.string.AvraChessClsBrilliant, R.string.AvraChessClsGreat, R.string.AvraChessClsBest,
            R.string.AvraChessClsGood, R.string.AvraChessClsOk, R.string.AvraChessClsBook, R.string.AvraChessClsInaccuracy,
            R.string.AvraChessClsMistake, R.string.AvraChessClsBlunder};
    public static final String[] SYMBOLS = {"!!", "!", "★", "✓", "•", "≡", "?!", "?", "??"};
    public static final int[] COLORS = {0xFF1BADA6, 0xFF5C8BB0, 0xFF81B64C, 0xFF77A35A, 0xFF97AF8B, 0xFFA88865, 0xFFF0C02F,
            0xFFFF8F3C, 0xFFE8382B};

    /** Short popular opening lines (UCI) — the moves inside count as "book". */
    private static final String[] OPENINGS = {
            "e2e4.e7e5.g1f3.b8c6.f1b5.a7a6.b5a4.g8f6.e1g1.f8e7.f1e1.b7b5.a4b3.d7d6.c2c3.e8g8",
            "e2e4.e7e5.g1f3.b8c6.f1b5.g8f6.e1g1.f6e4.d2d4.e4d6.b5c6.d7c6.d4e5.d6f5",
            "e2e4.e7e5.g1f3.b8c6.f1c4.f8c5.c2c3.g8f6.d2d3.d7d6.e1g1.e8g8",
            "e2e4.e7e5.g1f3.b8c6.f1c4.g8f6.f3g5.d7d5.e4d5.c6a5.c4b5.c7c6.d5c6.b7c6",
            "e2e4.e7e5.g1f3.b8c6.d2d4.e5d4.f3d4.g8f6.d4c6.b7c6.e4e5.d8e7",
            "e2e4.e7e5.g1f3.g8f6.f3e5.d7d6.e5f3.f6e4.d2d4.d6d5.f1d3",
            "e2e4.e7e5.b1c3.g8f6.f2f4.d7d5.f4e5.f6e4",
            "e2e4.e7e5.f2f4.e5f4.g1f3.g7g5",
            "e2e4.c7c5.g1f3.d7d6.d2d4.c5d4.f3d4.g8f6.b1c3.a7a6.c1e3.e7e5.d4b3.c8e6",
            "e2e4.c7c5.g1f3.b8c6.d2d4.c5d4.f3d4.g8f6.b1c3.e7e5.d4b5.d7d6",
            "e2e4.c7c5.g1f3.e7e6.d2d4.c5d4.f3d4.b8c6.b1c3.d8c7",
            "e2e4.c7c5.b1c3.b8c6.g2g3.g7g6.f1g2.f8g7.d2d3.d7d6",
            "e2e4.c7c5.c2c3.g8f6.e4e5.f6d5.d2d4.c5d4.g1f3",
            "e2e4.c7c5.g1f3.d7d6.d2d4.c5d4.f3d4.g8f6.b1c3.g7g6.c1e3.f8g7.f2f3.e8g8",
            "e2e4.e7e6.d2d4.d7d5.b1c3.f8b4.e4e5.c7c5.a2a3.b4c3.b2c3.g8e7",
            "e2e4.e7e6.d2d4.d7d5.e4e5.c7c5.c2c3.b8c6.g1f3.d8b6",
            "e2e4.c7c6.d2d4.d7d5.e4e5.c8f5.g1f3.e7e6.f1e2",
            "e2e4.c7c6.d2d4.d7d5.b1c3.d5e4.c3e4.c8f5.e4g3.f5g6.h2h4.h7h6",
            "e2e4.d7d5.e4d5.d8d5.b1c3.d5a5.d2d4.g8f6.g1f3.c7c6",
            "e2e4.g8f6.e4e5.f6d5.d2d4.d7d6.g1f3",
            "e2e4.d7d6.d2d4.g8f6.b1c3.g7g6.f2f4.f8g7.g1f3",
            "e2e4.g7g6.d2d4.f8g7.b1c3.d7d6",
            "d2d4.d7d5.c2c4.e7e6.b1c3.g8f6.c1g5.f8e7.e2e3.e8g8.g1f3.h7h6",
            "d2d4.d7d5.c2c4.d5c4.g1f3.g8f6.e2e3.e7e6.f1c4.c7c5.e1g1.a7a6",
            "d2d4.d7d5.c2c4.c7c6.g1f3.g8f6.b1c3.d5c4.a2a4.c8f5",
            "d2d4.g8f6.c2c4.g7g6.b1c3.f8g7.e2e4.d7d6.g1f3.e8g8.f1e2.e7e5.e1g1.b8c6",
            "d2d4.g8f6.c2c4.e7e6.b1c3.f8b4.e2e3.e8g8.f1d3.d7d5.g1f3.c7c5",
            "d2d4.g8f6.c2c4.e7e6.g1f3.b7b6.g2g3.c8a6.b2b3.f8b4.c1d2.b4e7",
            "d2d4.g8f6.c2c4.g7g6.b1c3.d7d5.c4d5.f6d5.e2e4.d5c3.b2c3.f8g7",
            "d2d4.g8f6.c2c4.c7c5.d4d5.e7e6.b1c3.e6d5.c4d5.d7d6",
            "d2d4.f7f5.g2g3.g8f6.f1g2.g7g6.g1f3.f8g7.e1g1.e8g8.c2c4.d7d6",
            "d2d4.d7d5.c1f4.g8f6.e2e3.e7e6.g1f3.c7c5.c2c3.b8c6.b1d2.f8d6.f4g3.e8g8",
            "d2d4.g8f6.c1f4.d7d5.e2e3.c7c5.c2c3.b8c6.b1d2.e7e6.g1f3.f8d6.f4g3.e8g8",
            "d2d4.d7d5.c2c4.e7e6.g1f3.g8f6.g2g3.f8e7.f1g2.e8g8.e1g1.d5c4",
            "c2c4.e7e5.b1c3.g8f6.g2g3.d7d5.c4d5.f6d5.f1g2.d5b6",
            "c2c4.g8f6.b1c3.e7e6.e2e4.d7d5.e4e5",
            "c2c4.c7c5.g1f3.b8c6.b1c3.g7g6.g2g3.f8g7.f1g2.g8f6.e1g1.e8g8",
            "g1f3.d7d5.g2g3.g8f6.f1g2.c7c6.e1g1.c8g4.d2d3.b8d7",
            "g1f3.g8f6.c2c4.g7g6.b1c3.f8g7.e2e4.d7d6.d2d4.e8g8",
            "e2e4.e7e5.g1f3.d7d6.d2d4.e5d4.f3d4.g8f6.b1c3.f8e7",
            "e2e4.e7e5.g1f3.b8c6.b1c3.g8f6.d2d4.e5d4.f3d4.f8b4.d4c6.b7c6.f1d3.d7d5",
            "e2e4.e7e5.f1c4.g8f6.d2d3.c7c6.g1f3.d7d5.c4b3",
            "b2b3.e7e5.c1b2.b8c6.e2e3.d7d5.f1b5.f8d6",
            "g2g3.d7d5.f1g2.e7e5.d2d3.g8f6.g1f3.b8c6.e1g1.f8e7",
            "e2e4.e7e5.g1f3.b8c6.f1c4.f8c5.b2b4.c5b4.c2c3.b4a5.d2d4.e5d4.e1g1",
            "d2d4.d7d5.c2c4.e7e6.b1c3.c7c6.e2e4.d5e4.c3e4.f8b4",
            "d2d4.g8f6.c2c4.e7e6.g2g3.d7d5.f1g2.f8e7.g1f3.e8g8.e1g1.d5c4.d1c2.a7a6",
            "d2d4.g8f6.g1f3.e7e6.e2e3.c7c5.f1d3.d7d5.b2b3.b8c6.e1g1.f8d6.c1b2.e8g8",
            "e2e4.c7c5.g1f3.d7d6.f1b5.c8d7.b5d7.d8d7.e1g1.b8c6.c2c3.g8f6",
            "e2e4.c7c5.g1f3.e7e6.d2d4.c5d4.f3d4.a7a6.f1d3.g8f6.e1g1.d8c7",
            "e2e4.e7e5.g1f3.b8c6.f1b5.a7a6.b5c6.d7c6.e1g1.f7f6.d2d4",
            "d2d4.d7d5.g1f3.g8f6.c2c4.e7e6.b1c3.c7c5.c4d5.f6d5",
            "e2e4.c7c5.g1f3.b8c6.f1b5.g7g6.e1g1.f8g7.f1e1.e7e5",
            "d2d4.g8f6.c2c4.e7e6.g1f3.d7d5.b1c3.f8e7.c1f4.e8g8.e2e3.c7c5"
    };

    public static final class Result {
        public int[] kind;
        /** Engine's best move in the position before each ply (0 if none). */
        public int[] best;
        /** Evaluation after each ply, centipawns from White's side; evalStart before the first move. */
        public int[] eval;
        public int evalStart;
        public float accWhite, accBlack;
        public final int[][] counts = new int[2][KINDS];
    }

    public interface Progress {
        void onProgress(int done, int total);
    }

    /** Win chance in percent for a centipawn score. */
    public static float winChance(int cp) {
        if (ChessAi.isMate(cp)) return cp > 0 ? 100 : 0;
        cp = Math.max(-1500, Math.min(1500, cp));
        return (float) (50 + 50 * (2 / (1 + Math.exp(-0.00368208 * cp)) - 1));
    }

    private static boolean inBook(List<String> moves, int upto) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i <= upto; i++) {
            if (i > 0) b.append('.');
            b.append(moves.get(i));
        }
        String prefix = b.toString();
        for (String line : OPENINGS) {
            if (line.equals(prefix) || line.startsWith(prefix + ".")) return true;
        }
        return false;
    }

    /** Is the move a real sacrifice: a piece left where the opponent can win it. Board BEFORE the move. */
    private static boolean sacrifice(ChessBoard b, int m) {
        int from = ChessBoard.from(m), to = ChessBoard.to(m);
        int t = Math.abs(b.sq[from]);
        if (t < ChessBoard.KNIGHT || t > ChessBoard.QUEEN || ChessBoard.promo(m) != 0) return false;
        int gain = b.sq[to] == 0 ? 0 : ChessBoard.value(Math.abs(b.sq[to]));
        int worth = ChessBoard.value(t);
        if (worth - gain < 2) return false;
        int us = b.side;
        b.make(m);
        try {
            boolean defended = b.attacked(to, us);
            ArrayList<Integer> caps = new ArrayList<>();
            b.pseudo(caps, true);
            int lowest = 99;
            for (int c : caps) {
                if (ChessBoard.to(c) != to) continue;
                int attacker = Math.abs(b.sq[ChessBoard.from(c)]);
                b.make(c);
                boolean legal = !b.inCheck(-us);
                b.unmake();
                if (legal) lowest = Math.min(lowest, attacker == ChessBoard.KING ? 100 : ChessBoard.value(attacker));
            }
            if (lowest == 99) return false;
            return !defended || lowest < worth;
        } finally {
            b.unmake();
        }
    }

    private static float accuracy(float loss) {
        return (float) Math.max(0, Math.min(100, 103.1668 * Math.exp(-0.04354 * loss) - 3.1669));
    }

    /** Runs on a background thread; may take a few seconds per ten moves. */
    public static Result analyse(List<String> moves, Progress progress) {
        int n = moves.size();
        Result r = new Result();
        r.kind = new int[n];
        r.best = new int[n];
        r.eval = new int[n];
        ChessAi ai = new ChessAi();
        ChessBoard b = new ChessBoard();
        float sumW = 0, sumB = 0;
        int cntW = 0, cntB = 0;
        boolean book = true;
        for (int i = 0; i < n; i++) {
            if (progress != null) progress.onProgress(i, n);
            int m = b.parseUci(moves.get(i));
            if (m == 0) {
                r.kind = java.util.Arrays.copyOf(r.kind, i);
                break;
            }
            int mover = b.side;
            ArrayList<int[]> scores = ai.analyse(b, 5, 320);
            int bestScore = scores.isEmpty() ? 0 : scores.get(0)[1];
            int second = scores.size() > 1 ? scores.get(1)[1] : bestScore;
            int played = bestScore;
            for (int[] s : scores) if (s[0] == m) played = s[1];
            r.best[i] = scores.isEmpty() ? 0 : scores.get(0)[0];
            if (i == 0) r.evalStart = bestScore * mover;
            r.eval[i] = played * mover;
            float wBest = winChance(bestScore), wPlayed = winChance(played);
            float loss = Math.max(0, wBest - wPlayed);
            book = book && inBook(moves, i);
            int kind;
            if (book) {
                kind = BOOK;
                loss = 0;
            } else if (scores.size() <= 1 || m == r.best[i] || loss < 1.5f) {
                if (wPlayed >= 50 && wBest < 97 && sacrifice(b, m)) kind = BRILLIANT;
                else if (scores.size() > 1 && wBest - winChance(second) >= 18 && wBest > 15 && wBest < 95) kind = GREAT;
                else kind = BEST;
            } else if (loss < 5) {
                kind = GOOD;
            } else if (loss < 10) {
                kind = OK;
            } else if (loss < 18) {
                kind = INACCURACY;
            } else if (loss < 28) {
                kind = MISTAKE;
            } else {
                kind = BLUNDER;
            }
            r.kind[i] = kind;
            r.counts[mover == 1 ? 0 : 1][kind]++;
            float acc = accuracy(loss);
            if (mover == 1) {
                sumW += acc;
                cntW++;
            } else {
                sumB += acc;
                cntB++;
            }
            b.make(m);
        }
        r.accWhite = cntW == 0 ? 0 : sumW / cntW;
        r.accBlack = cntB == 0 ? 0 : sumB / cntB;
        if (progress != null) progress.onProgress(n, n);
        return r;
    }

    // ---- cache: a review is done once per game ------------------------------------------------------

    private static String key(List<String> moves) {
        return "rv_" + Integer.toHexString(android.text.TextUtils.join(".", moves).hashCode()) + "_" + moves.size();
    }

    public static Result cached(List<String> moves) {
        try {
            String s = ChessStyle.prefs().getString(key(moves), null);
            if (s == null) return null;
            JSONObject o = new JSONObject(s);
            Result r = new Result();
            JSONArray k = o.getJSONArray("k"), bm = o.getJSONArray("b"), ev = o.getJSONArray("e");
            r.kind = new int[k.length()];
            r.best = new int[k.length()];
            r.eval = new int[k.length()];
            for (int i = 0; i < k.length(); i++) {
                r.kind[i] = k.getInt(i);
                r.best[i] = bm.getInt(i);
                r.eval[i] = ev.getInt(i);
                r.counts[i % 2][r.kind[i]]++;
            }
            r.evalStart = o.optInt("s");
            r.accWhite = (float) o.optDouble("aw");
            r.accBlack = (float) o.optDouble("ab");
            return r;
        } catch (Exception e) {
            FileLog.e(e);
            return null;
        }
    }

    public static void store(List<String> moves, Result r) {
        try {
            JSONArray k = new JSONArray(), bm = new JSONArray(), ev = new JSONArray();
            for (int i = 0; i < r.kind.length; i++) {
                k.put(r.kind[i]);
                bm.put(r.best[i]);
                ev.put(r.eval[i]);
            }
            JSONObject o = new JSONObject().put("k", k).put("b", bm).put("e", ev).put("s", r.evalStart)
                    .put("aw", r.accWhite).put("ab", r.accBlack);
            String key = key(moves);
            JSONArray keys = new JSONArray(ChessStyle.prefs().getString("rv_keys", "[]"));
            JSONArray kept = new JSONArray();
            android.content.SharedPreferences.Editor e = ChessStyle.prefs().edit();
            for (int i = 0; i < keys.length(); i++) {
                String x = keys.getString(i);
                if (x.equals(key)) continue;
                if (keys.length() - i > 24) e.remove(x);
                else kept.put(x);
            }
            kept.put(key);
            e.putString(key, o.toString()).putString("rv_keys", kept.toString()).apply();
        } catch (Exception e) {
            FileLog.e(e);
        }
    }
}
