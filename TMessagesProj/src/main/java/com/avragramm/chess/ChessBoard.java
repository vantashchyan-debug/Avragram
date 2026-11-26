package com.avragramm.chess;

import java.util.ArrayList;
import java.util.Random;

/**
 * Full chess rules: legal move generation (castling, en passant, promotion), check / mate / stalemate,
 * threefold repetition, 50-move rule, insufficient material, UCI and figurine SAN notation.
 *
 * Squares are 0..63, a1 = 0, h1 = 7, a8 = 56. Pieces: P N B R Q K = 1..6, white > 0, black < 0.
 * A move is packed into an int: from | to << 6 | promo << 12 | flags << 16.
 */
public final class ChessBoard {

    public static final int EMPTY = 0, PAWN = 1, KNIGHT = 2, BISHOP = 3, ROOK = 4, QUEEN = 5, KING = 6;
    public static final int WHITE = 1, BLACK = -1;

    public static final int F_CAPTURE = 1, F_EP = 2, F_CASTLE = 4, F_DOUBLE = 8;

    public static final int ONGOING = 0, MATE = 1, STALEMATE = 2, REPETITION = 3, FIFTY = 4, MATERIAL = 5;

    public final int[] sq = new int[64];
    public int side = WHITE;
    /** 1 = white O-O, 2 = white O-O-O, 4 = black O-O, 8 = black O-O-O. */
    public int castling = 15;
    public int ep = -1;
    public int halfmove = 0;
    public int fullmove = 1;
    public long key;

    private final int[] undoCaptured = new int[1024];
    private final int[] undoCastling = new int[1024];
    private final int[] undoEp = new int[1024];
    private final int[] undoHalf = new int[1024];
    private final int[] undoMove = new int[1024];
    private final long[] history = new long[1024];
    private int ply;

    private static final long[][] Z_PIECE = new long[13][64];
    private static final long[] Z_CASTLE = new long[16];
    private static final long[] Z_EP = new long[8];
    private static final long Z_SIDE;

    static {
        Random r = new Random(0xA77A6E55L);
        for (int p = 0; p < 13; p++) for (int s = 0; s < 64; s++) Z_PIECE[p][s] = r.nextLong();
        for (int i = 0; i < 16; i++) Z_CASTLE[i] = r.nextLong();
        for (int i = 0; i < 8; i++) Z_EP[i] = r.nextLong();
        Z_SIDE = r.nextLong();
    }

    public ChessBoard() {
        reset();
    }

    public ChessBoard copy() {
        ChessBoard b = new ChessBoard();
        System.arraycopy(sq, 0, b.sq, 0, 64);
        b.side = side;
        b.castling = castling;
        b.ep = ep;
        b.halfmove = halfmove;
        b.fullmove = fullmove;
        b.key = key;
        b.ply = ply;
        System.arraycopy(history, 0, b.history, 0, ply + 1);
        // move history too, so lastMove(), moveAt() and unmake() work on the copy
        System.arraycopy(undoCaptured, 0, b.undoCaptured, 0, ply);
        System.arraycopy(undoCastling, 0, b.undoCastling, 0, ply);
        System.arraycopy(undoEp, 0, b.undoEp, 0, ply);
        System.arraycopy(undoHalf, 0, b.undoHalf, 0, ply);
        System.arraycopy(undoMove, 0, b.undoMove, 0, ply);
        return b;
    }

    public void reset() {
        int[] back = {ROOK, KNIGHT, BISHOP, QUEEN, KING, BISHOP, KNIGHT, ROOK};
        for (int i = 0; i < 64; i++) sq[i] = EMPTY;
        for (int f = 0; f < 8; f++) {
            sq[f] = back[f];
            sq[8 + f] = PAWN;
            sq[48 + f] = -PAWN;
            sq[56 + f] = -back[f];
        }
        side = WHITE;
        castling = 15;
        ep = -1;
        halfmove = 0;
        fullmove = 1;
        ply = 0;
        key = computeKey();
        history[0] = key;
    }

    /** Loads a FEN position (used by tests and puzzles). */
    public void loadFen(String fen) {
        String[] parts = fen.trim().split("\\s+");
        for (int i = 0; i < 64; i++) sq[i] = EMPTY;
        int r = 7, f = 0;
        for (char c : parts[0].toCharArray()) {
            if (c == '/') {
                r--;
                f = 0;
            } else if (Character.isDigit(c)) {
                f += c - '0';
            } else {
                int t = "pnbrqk".indexOf(Character.toLowerCase(c)) + 1;
                sq[r * 8 + f] = Character.isUpperCase(c) ? t : -t;
                f++;
            }
        }
        side = parts.length > 1 && parts[1].equals("b") ? BLACK : WHITE;
        castling = 0;
        if (parts.length > 2) {
            if (parts[2].contains("K")) castling |= 1;
            if (parts[2].contains("Q")) castling |= 2;
            if (parts[2].contains("k")) castling |= 4;
            if (parts[2].contains("q")) castling |= 8;
        }
        ep = parts.length > 3 && !parts[3].equals("-") ? (parts[3].charAt(0) - 'a') + (parts[3].charAt(1) - '1') * 8 : -1;
        halfmove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;
        fullmove = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
        ply = 0;
        key = computeKey();
        history[0] = key;
    }

    public static int file(int s) {
        return s & 7;
    }

    public static int rank(int s) {
        return s >> 3;
    }

    public static int from(int m) {
        return m & 63;
    }

    public static int to(int m) {
        return (m >> 6) & 63;
    }

    public static int promo(int m) {
        return (m >> 12) & 15;
    }

    public static int flags(int m) {
        return (m >> 16) & 15;
    }

    private static int pack(int from, int to, int promo, int flags) {
        return from | to << 6 | promo << 12 | flags << 16;
    }

    private static int zIndex(int piece) {
        return piece + 6;
    }

    private long computeKey() {
        long k = 0;
        for (int s = 0; s < 64; s++) if (sq[s] != EMPTY) k ^= Z_PIECE[zIndex(sq[s])][s];
        k ^= Z_CASTLE[castling];
        if (ep >= 0) k ^= Z_EP[file(ep)];
        if (side == BLACK) k ^= Z_SIDE;
        return k;
    }

    public int moveCount() {
        return ply;
    }

    public int lastMove() {
        return ply == 0 ? 0 : undoMove[ply - 1];
    }

    // ---- attacks ------------------------------------------------------------------------------

    private static final int[][] KNIGHT_D = {{1, 2}, {2, 1}, {2, -1}, {1, -2}, {-1, -2}, {-2, -1}, {-2, 1}, {-1, 2}};
    private static final int[][] KING_D = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}};
    private static final int[][] BISHOP_D = {{1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
    private static final int[][] ROOK_D = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    /** Is square s attacked by a piece of {@code by}. */
    public boolean attacked(int s, int by) {
        int f = file(s), r = rank(s);
        // pawns
        int pr = r - by;
        if (pr >= 0 && pr < 8) {
            if (f > 0 && sq[pr * 8 + f - 1] == by * PAWN) return true;
            if (f < 7 && sq[pr * 8 + f + 1] == by * PAWN) return true;
        }
        for (int[] d : KNIGHT_D) {
            int nf = f + d[0], nr = r + d[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && sq[nr * 8 + nf] == by * KNIGHT) return true;
        }
        for (int[] d : KING_D) {
            int nf = f + d[0], nr = r + d[1];
            if (nf >= 0 && nf < 8 && nr >= 0 && nr < 8 && sq[nr * 8 + nf] == by * KING) return true;
        }
        for (int[] d : BISHOP_D) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int p = sq[nr * 8 + nf];
                if (p != EMPTY) {
                    if (p == by * BISHOP || p == by * QUEEN) return true;
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        for (int[] d : ROOK_D) {
            int nf = f + d[0], nr = r + d[1];
            while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                int p = sq[nr * 8 + nf];
                if (p != EMPTY) {
                    if (p == by * ROOK || p == by * QUEEN) return true;
                    break;
                }
                nf += d[0];
                nr += d[1];
            }
        }
        return false;
    }

    public int kingSquare(int color) {
        for (int s = 0; s < 64; s++) if (sq[s] == color * KING) return s;
        return -1;
    }

    public boolean inCheck(int color) {
        int k = kingSquare(color);
        return k >= 0 && attacked(k, -color);
    }

    public boolean inCheck() {
        return inCheck(side);
    }

    // ---- generation ---------------------------------------------------------------------------

    /** Pseudo-legal moves of the side to move (king may be left in check). */
    public void pseudo(ArrayList<Integer> out, boolean capturesOnly) {
        int us = side;
        for (int s = 0; s < 64; s++) {
            int p = sq[s];
            if (p == EMPTY || (p > 0 ? WHITE : BLACK) != us) continue;
            int t = Math.abs(p), f = file(s), r = rank(s);
            if (t == PAWN) {
                int dir = us == WHITE ? 1 : -1;
                int start = us == WHITE ? 1 : 6, last = us == WHITE ? 7 : 0;
                int nr = r + dir;
                if (nr < 0 || nr > 7) continue;
                int one = nr * 8 + f;
                if (sq[one] == EMPTY) {
                    if (nr == last) addPromos(out, s, one, 0);
                    else if (!capturesOnly) {
                        out.add(pack(s, one, 0, 0));
                        int two = (r + 2 * dir) * 8 + f;
                        if (r == start && sq[two] == EMPTY) out.add(pack(s, two, 0, F_DOUBLE));
                    }
                }
                for (int df = -1; df <= 1; df += 2) {
                    int nf = f + df;
                    if (nf < 0 || nf > 7) continue;
                    int c = nr * 8 + nf;
                    if (sq[c] != EMPTY && (sq[c] > 0 ? WHITE : BLACK) != us) {
                        if (nr == last) addPromos(out, s, c, F_CAPTURE);
                        else out.add(pack(s, c, 0, F_CAPTURE));
                    } else if (c == ep) {
                        out.add(pack(s, c, 0, F_CAPTURE | F_EP));
                    }
                }
            } else if (t == KNIGHT || t == KING) {
                for (int[] d : t == KNIGHT ? KNIGHT_D : KING_D) {
                    int nf = f + d[0], nr = r + d[1];
                    if (nf < 0 || nf > 7 || nr < 0 || nr > 7) continue;
                    int c = nr * 8 + nf;
                    if (sq[c] == EMPTY) {
                        if (!capturesOnly) out.add(pack(s, c, 0, 0));
                    } else if ((sq[c] > 0 ? WHITE : BLACK) != us) {
                        out.add(pack(s, c, 0, F_CAPTURE));
                    }
                }
                if (t == KING && !capturesOnly) addCastles(out, s);
            } else {
                int[][] dirs = t == BISHOP ? BISHOP_D : t == ROOK ? ROOK_D : null;
                for (int pass = 0; pass < (t == QUEEN ? 2 : 1); pass++) {
                    int[][] ds = t == QUEEN ? (pass == 0 ? BISHOP_D : ROOK_D) : dirs;
                    for (int[] d : ds) {
                        int nf = f + d[0], nr = r + d[1];
                        while (nf >= 0 && nf < 8 && nr >= 0 && nr < 8) {
                            int c = nr * 8 + nf;
                            if (sq[c] == EMPTY) {
                                if (!capturesOnly) out.add(pack(s, c, 0, 0));
                            } else {
                                if ((sq[c] > 0 ? WHITE : BLACK) != us) out.add(pack(s, c, 0, F_CAPTURE));
                                break;
                            }
                            nf += d[0];
                            nr += d[1];
                        }
                    }
                }
            }
        }
    }

    private static void addPromos(ArrayList<Integer> out, int from, int to, int flags) {
        out.add(pack(from, to, QUEEN, flags));
        out.add(pack(from, to, KNIGHT, flags));
        out.add(pack(from, to, ROOK, flags));
        out.add(pack(from, to, BISHOP, flags));
    }

    private void addCastles(ArrayList<Integer> out, int k) {
        int us = side;
        if (us == WHITE && k == 4) {
            if ((castling & 1) != 0 && sq[5] == EMPTY && sq[6] == EMPTY && sq[7] == ROOK
                    && !attacked(4, BLACK) && !attacked(5, BLACK) && !attacked(6, BLACK)) out.add(pack(4, 6, 0, F_CASTLE));
            if ((castling & 2) != 0 && sq[3] == EMPTY && sq[2] == EMPTY && sq[1] == EMPTY && sq[0] == ROOK
                    && !attacked(4, BLACK) && !attacked(3, BLACK) && !attacked(2, BLACK)) out.add(pack(4, 2, 0, F_CASTLE));
        } else if (us == BLACK && k == 60) {
            if ((castling & 4) != 0 && sq[61] == EMPTY && sq[62] == EMPTY && sq[63] == -ROOK
                    && !attacked(60, WHITE) && !attacked(61, WHITE) && !attacked(62, WHITE)) out.add(pack(60, 62, 0, F_CASTLE));
            if ((castling & 8) != 0 && sq[59] == EMPTY && sq[58] == EMPTY && sq[57] == EMPTY && sq[56] == -ROOK
                    && !attacked(60, WHITE) && !attacked(59, WHITE) && !attacked(58, WHITE)) out.add(pack(60, 58, 0, F_CASTLE));
        }
    }

    public ArrayList<Integer> legal() {
        ArrayList<Integer> list = new ArrayList<>(48);
        pseudo(list, false);
        ArrayList<Integer> out = new ArrayList<>(list.size());
        int us = side;
        for (int m : list) {
            make(m);
            if (!inCheck(us)) out.add(m);
            unmake();
        }
        return out;
    }

    // ---- make / unmake ------------------------------------------------------------------------

    private static final int[] CASTLE_MASK = new int[64];

    static {
        for (int i = 0; i < 64; i++) CASTLE_MASK[i] = 15;
        CASTLE_MASK[4] = 15 & ~3;
        CASTLE_MASK[7] = 15 & ~1;
        CASTLE_MASK[0] = 15 & ~2;
        CASTLE_MASK[60] = 15 & ~12;
        CASTLE_MASK[63] = 15 & ~4;
        CASTLE_MASK[56] = 15 & ~8;
    }

    public void make(int m) {
        int from = from(m), to = to(m), fl = flags(m), pr = promo(m);
        int p = sq[from];
        undoMove[ply] = m;
        undoCastling[ply] = castling;
        undoEp[ply] = ep;
        undoHalf[ply] = halfmove;
        int captured;
        long k = key;
        k ^= Z_CASTLE[castling];
        if (ep >= 0) k ^= Z_EP[file(ep)];
        if ((fl & F_EP) != 0) {
            int cs = to - 8 * side;
            captured = sq[cs];
            sq[cs] = EMPTY;
            k ^= Z_PIECE[zIndex(captured)][cs];
        } else {
            captured = sq[to];
            if (captured != EMPTY) k ^= Z_PIECE[zIndex(captured)][to];
        }
        undoCaptured[ply] = captured;
        k ^= Z_PIECE[zIndex(p)][from];
        sq[from] = EMPTY;
        int placed = pr != 0 ? pr * side : p;
        sq[to] = placed;
        k ^= Z_PIECE[zIndex(placed)][to];
        if ((fl & F_CASTLE) != 0) {
            int rf, rt;
            if (to == 6) { rf = 7; rt = 5; } else if (to == 2) { rf = 0; rt = 3; } else if (to == 62) { rf = 63; rt = 61; } else { rf = 56; rt = 59; }
            int rook = sq[rf];
            sq[rf] = EMPTY;
            sq[rt] = rook;
            k ^= Z_PIECE[zIndex(rook)][rf] ^ Z_PIECE[zIndex(rook)][rt];
        }
        castling &= CASTLE_MASK[from] & CASTLE_MASK[to];
        ep = (fl & F_DOUBLE) != 0 ? (from + to) / 2 : -1;
        halfmove = (Math.abs(p) == PAWN || captured != EMPTY) ? 0 : halfmove + 1;
        if (side == BLACK) fullmove++;
        side = -side;
        k ^= Z_CASTLE[castling];
        if (ep >= 0) k ^= Z_EP[file(ep)];
        k ^= Z_SIDE;
        key = k;
        ply++;
        history[ply] = key;
    }

    public void unmake() {
        if (ply == 0) return;
        ply--;
        int m = undoMove[ply];
        int from = from(m), to = to(m), fl = flags(m), pr = promo(m);
        side = -side;
        if (side == BLACK) fullmove--;
        int p = pr != 0 ? PAWN * side : sq[to];
        sq[from] = p;
        if ((fl & F_EP) != 0) {
            sq[to] = EMPTY;
            sq[to - 8 * side] = undoCaptured[ply];
        } else {
            sq[to] = undoCaptured[ply];
        }
        if ((fl & F_CASTLE) != 0) {
            int rf, rt;
            if (to == 6) { rf = 7; rt = 5; } else if (to == 2) { rf = 0; rt = 3; } else if (to == 62) { rf = 63; rt = 61; } else { rf = 56; rt = 59; }
            sq[rf] = sq[rt];
            sq[rt] = EMPTY;
        }
        castling = undoCastling[ply];
        ep = undoEp[ply];
        halfmove = undoHalf[ply];
        key = history[ply];
    }

    public int capturedAt(int plyIndex) {
        return undoCaptured[plyIndex];
    }

    public int moveAt(int plyIndex) {
        return undoMove[plyIndex];
    }

    // ---- result -------------------------------------------------------------------------------

    public boolean repetition(int times) {
        int count = 1;
        for (int i = ply - 2; i >= 0 && i >= ply - halfmove; i -= 2) {
            if (history[i] == key && ++count >= times) return true;
        }
        return false;
    }

    public boolean insufficient() {
        int minors = 0;
        for (int s = 0; s < 64; s++) {
            int t = Math.abs(sq[s]);
            if (t == PAWN || t == ROOK || t == QUEEN) return false;
            if (t == KNIGHT || t == BISHOP) minors++;
        }
        return minors <= 1;
    }

    public int status() {
        if (legal().isEmpty()) return inCheck() ? MATE : STALEMATE;
        if (insufficient()) return MATERIAL;
        if (halfmove >= 100) return FIFTY;
        if (repetition(3)) return REPETITION;
        return ONGOING;
    }

    // ---- notation -----------------------------------------------------------------------------

    public static String square(int s) {
        return "" + (char) ('a' + file(s)) + (char) ('1' + rank(s));
    }

    public static String uci(int m) {
        String s = square(from(m)) + square(to(m));
        int p = promo(m);
        if (p != 0) s += "nbrq".charAt(p == KNIGHT ? 0 : p == BISHOP ? 1 : p == ROOK ? 2 : 3);
        return s;
    }

    /** Finds the legal move written as "e2e4" / "e7e8q"; 0 when it is not legal. */
    public int parseUci(String u) {
        if (u == null || u.length() < 4) return 0;
        int f = (u.charAt(0) - 'a') + (u.charAt(1) - '1') * 8;
        int t = (u.charAt(2) - 'a') + (u.charAt(3) - '1') * 8;
        int pr = 0;
        if (u.length() > 4) {
            char c = Character.toLowerCase(u.charAt(4));
            pr = c == 'n' ? KNIGHT : c == 'b' ? BISHOP : c == 'r' ? ROOK : QUEEN;
        }
        for (int m : legal()) {
            if (from(m) == f && to(m) == t && promo(m) == pr) return m;
        }
        return 0;
    }

    private static final String FIGURINES = " ♙♘♗♖♕♔";

    /** Figurine notation (language-free): "♘f3", "exd5", "O-O", "e8=♕+". Call BEFORE making the move. */
    public String san(int m) {
        int from = from(m), to = to(m), fl = flags(m);
        int t = Math.abs(sq[from]);
        StringBuilder b = new StringBuilder();
        if ((fl & F_CASTLE) != 0) {
            b.append(file(to) == 6 ? "O-O" : "O-O-O");
        } else {
            if (t == PAWN) {
                if ((fl & F_CAPTURE) != 0) b.append((char) ('a' + file(from)));
            } else {
                b.append(FIGURINES.charAt(t));
                boolean sameFile = false, sameRank = false, other = false;
                for (int o : legal()) {
                    if (o != m && to(o) == to && Math.abs(sq[from(o)]) == t) {
                        other = true;
                        if (file(from(o)) == file(from)) sameFile = true;
                        if (rank(from(o)) == rank(from)) sameRank = true;
                    }
                }
                if (other) {
                    if (!sameFile) b.append((char) ('a' + file(from)));
                    else if (!sameRank) b.append((char) ('1' + rank(from)));
                    else b.append(square(from));
                }
            }
            if ((fl & F_CAPTURE) != 0) b.append('x');
            b.append(square(to));
            if (promo(m) != 0) b.append('=').append(FIGURINES.charAt(promo(m)));
        }
        make(m);
        if (inCheck()) b.append(legal().isEmpty() ? '#' : '+');
        unmake();
        return b.toString();
    }

    /** Material balance in pawns for the captured-pieces line. */
    public static int value(int type) {
        switch (type) {
            case PAWN: return 1;
            case KNIGHT:
            case BISHOP: return 3;
            case ROOK: return 5;
            case QUEEN: return 9;
            default: return 0;
        }
    }
}
