package com.avragramm.chess;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;

/**
 * The AvraChess bot: alpha-beta with iterative deepening, quiescence on captures, MVV-LVA ordering
 * and piece-square tables. Four levels from "makes silly moves" to a solid club player.
 */
public final class ChessAi {

    public static final int LEVELS = 4;
    /** Approximate strength shown next to the level name. */
    public static final int[] LEVEL_RATING = {600, 1000, 1400, 1800};
    private static final int[] DEPTH = {1, 2, 3, 6};
    private static final long[] TIME_MS = {300, 600, 1200, 2200};
    private static final int[] NOISE = {220, 60, 15, 0};
    private static final int[] BLUNDER_PCT = {30, 8, 0, 0};

    private static final int INF = 1_000_000, MATE = 100_000;
    private static final int[] VALUE = {0, 100, 320, 330, 500, 900, 0};

    // Piece-square tables, from white's point of view, a8 first (index = (7 - rank) * 8 + file)
    private static final int[] PST_P = {
            0, 0, 0, 0, 0, 0, 0, 0,
            50, 50, 50, 50, 50, 50, 50, 50,
            10, 10, 20, 30, 30, 20, 10, 10,
            5, 5, 10, 25, 25, 10, 5, 5,
            0, 0, 0, 20, 20, 0, 0, 0,
            5, -5, -10, 0, 0, -10, -5, 5,
            5, 10, 10, -20, -20, 10, 10, 5,
            0, 0, 0, 0, 0, 0, 0, 0};
    private static final int[] PST_N = {
            -50, -40, -30, -30, -30, -30, -40, -50,
            -40, -20, 0, 0, 0, 0, -20, -40,
            -30, 0, 10, 15, 15, 10, 0, -30,
            -30, 5, 15, 20, 20, 15, 5, -30,
            -30, 0, 15, 20, 20, 15, 0, -30,
            -30, 5, 10, 15, 15, 10, 5, -30,
            -40, -20, 0, 5, 5, 0, -20, -40,
            -50, -40, -30, -30, -30, -30, -40, -50};
    private static final int[] PST_B = {
            -20, -10, -10, -10, -10, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 10, 10, 5, 0, -10,
            -10, 5, 5, 10, 10, 5, 5, -10,
            -10, 0, 10, 10, 10, 10, 0, -10,
            -10, 10, 10, 10, 10, 10, 10, -10,
            -10, 5, 0, 0, 0, 0, 5, -10,
            -20, -10, -10, -10, -10, -10, -10, -20};
    private static final int[] PST_R = {
            0, 0, 0, 0, 0, 0, 0, 0,
            5, 10, 10, 10, 10, 10, 10, 5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            -5, 0, 0, 0, 0, 0, 0, -5,
            0, 0, 0, 5, 5, 0, 0, 0};
    private static final int[] PST_Q = {
            -20, -10, -10, -5, -5, -10, -10, -20,
            -10, 0, 0, 0, 0, 0, 0, -10,
            -10, 0, 5, 5, 5, 5, 0, -10,
            -5, 0, 5, 5, 5, 5, 0, -5,
            0, 0, 5, 5, 5, 5, 0, -5,
            -10, 5, 5, 5, 5, 5, 0, -10,
            -10, 0, 5, 0, 0, 0, 0, -10,
            -20, -10, -10, -5, -5, -10, -10, -20};
    private static final int[] PST_K_MID = {
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -30, -40, -40, -50, -50, -40, -40, -30,
            -20, -30, -30, -40, -40, -30, -30, -20,
            -10, -20, -20, -20, -20, -20, -20, -10,
            20, 20, 0, 0, 0, 0, 20, 20,
            20, 30, 10, 0, 0, 10, 30, 20};
    private static final int[] PST_K_END = {
            -50, -40, -30, -20, -20, -30, -40, -50,
            -30, -20, -10, 0, 0, -10, -20, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 30, 40, 40, 30, -10, -30,
            -30, -10, 20, 30, 30, 20, -10, -30,
            -30, -30, 0, 0, 0, 0, -30, -30,
            -50, -30, -30, -30, -30, -30, -30, -50};

    private final Random random = new Random();
    private long deadline;
    private boolean stopped;
    private int nodes;

    /** Picks a move for the side to move, or 0 when there is none. Runs on a background thread. */
    public int think(ChessBoard src, int level) {
        level = Math.max(0, Math.min(LEVELS - 1, level));
        ChessBoard b = src.copy();
        ArrayList<Integer> moves = b.legal();
        if (moves.isEmpty()) return 0;
        if (moves.size() == 1) return moves.get(0);
        if (random.nextInt(100) < BLUNDER_PCT[level]) return moves.get(random.nextInt(moves.size()));

        deadline = System.currentTimeMillis() + TIME_MS[level];
        stopped = false;
        int best = moves.get(0);
        order(b, moves);
        for (int depth = 1; depth <= DEPTH[level]; depth++) {
            int alpha = -INF, bestHere = 0, bestScore = -INF;
            ArrayList<int[]> scored = new ArrayList<>();
            for (int m : moves) {
                b.make(m);
                // with noise every root move needs an exact score: a cut-off bound plus noise could beat the real best move
                int score = -search(b, depth - 1, -INF, NOISE[level] > 0 ? INF : -alpha, 1);
                b.unmake();
                if (stopped) break;
                if (NOISE[level] > 0) score += random.nextInt(NOISE[level] * 2 + 1) - NOISE[level];
                scored.add(new int[]{m, score});
                if (score > bestScore) {
                    bestScore = score;
                    bestHere = m;
                }
                if (score > alpha) alpha = score;
            }
            if (stopped && bestHere == 0) break;
            if (bestHere != 0) best = bestHere;
            if (stopped) break;
            // best moves first for the next iteration
            Collections.sort(scored, (x, y) -> Integer.compare(y[1], x[1]));
            moves.clear();
            for (int[] s : scored) moves.add(s[0]);
            if (bestScore > MATE - 1000) break;
        }
        return best;
    }

    private int search(ChessBoard b, int depth, int alpha, int beta, int ply) {
        if ((++nodes & 1023) == 0 && System.currentTimeMillis() > deadline) stopped = true;
        if (stopped) return 0;
        if (ply > 0 && (b.halfmove >= 100 || b.repetition(2))) return 0;
        boolean check = b.inCheck();
        if (depth <= 0 && !check) return quiesce(b, alpha, beta, 0);
        ArrayList<Integer> list = new ArrayList<>(48);
        b.pseudo(list, false);
        order(b, list);
        int us = b.side, legal = 0;
        for (int m : list) {
            b.make(m);
            if (b.inCheck(us)) {
                b.unmake();
                continue;
            }
            legal++;
            int score = -search(b, depth - 1, -beta, -alpha, ply + 1);
            b.unmake();
            if (stopped) return 0;
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        if (legal == 0) return check ? -MATE + ply : 0;
        return alpha;
    }

    private int quiesce(ChessBoard b, int alpha, int beta, int qply) {
        int stand = evaluate(b);
        if (stand >= beta) return beta;
        if (stand > alpha) alpha = stand;
        if (qply > 6) return alpha;
        ArrayList<Integer> list = new ArrayList<>(16);
        b.pseudo(list, true);
        order(b, list);
        int us = b.side;
        for (int m : list) {
            b.make(m);
            if (b.inCheck(us)) {
                b.unmake();
                continue;
            }
            int score = -quiesce(b, -beta, -alpha, qply + 1);
            b.unmake();
            if (score >= beta) return beta;
            if (score > alpha) alpha = score;
        }
        return alpha;
    }

    private static void order(ChessBoard b, ArrayList<Integer> moves) {
        Collections.sort(moves, (x, y) -> Integer.compare(orderScore(b, y), orderScore(b, x)));
    }

    private static int orderScore(ChessBoard b, int m) {
        int s = 0;
        if ((ChessBoard.flags(m) & ChessBoard.F_CAPTURE) != 0) {
            int victim = Math.abs(b.sq[ChessBoard.to(m)]);
            if (victim == 0) victim = ChessBoard.PAWN;
            s += 10 * VALUE[victim] - VALUE[Math.abs(b.sq[ChessBoard.from(m)])] / 10 + 10000;
        }
        if (ChessBoard.promo(m) == ChessBoard.QUEEN) s += 9000;
        return s;
    }

    /** Static evaluation from the side to move's point of view, centipawns. */
    public static int evaluate(ChessBoard b) {
        int score = 0, material = 0;
        for (int s = 0; s < 64; s++) {
            int t = Math.abs(b.sq[s]);
            if (t != ChessBoard.KING && t != ChessBoard.PAWN) material += VALUE[t];
        }
        boolean endgame = material <= 1300;
        for (int s = 0; s < 64; s++) {
            int p = b.sq[s];
            if (p == 0) continue;
            int t = Math.abs(p);
            int idx = p > 0 ? (7 - ChessBoard.rank(s)) * 8 + ChessBoard.file(s) : ChessBoard.rank(s) * 8 + ChessBoard.file(s);
            int v = VALUE[t];
            switch (t) {
                case ChessBoard.PAWN: v += PST_P[idx]; break;
                case ChessBoard.KNIGHT: v += PST_N[idx]; break;
                case ChessBoard.BISHOP: v += PST_B[idx]; break;
                case ChessBoard.ROOK: v += PST_R[idx]; break;
                case ChessBoard.QUEEN: v += PST_Q[idx]; break;
                case ChessBoard.KING: v += endgame ? PST_K_END[idx] : PST_K_MID[idx]; break;
            }
            score += p > 0 ? v : -v;
        }
        return score * b.side;
    }
}
