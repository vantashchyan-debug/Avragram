package com.avragramm.sea;

import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Random;

/**
 * A 10×10 sea with the classic fleet: one 4-deck, two 3-deck, three 2-deck and four 1-deck ships.
 * Ships may not touch, not even at the corners. Cells are numbered y * 10 + x.
 */
public final class SeaBoard {

    public static final int N = 10, CELLS = 100;
    public static final int[] FLEET = {4, 3, 3, 2, 2, 2, 1, 1, 1, 1};

    public static final class Ship {
        public int x, y, len;
        public boolean vertical;

        public Ship(int x, int y, int len, boolean vertical) {
            this.x = x;
            this.y = y;
            this.len = len;
            this.vertical = vertical;
        }

        public int cell(int i) {
            return vertical ? (y + i) * N + x : y * N + x + i;
        }

        public boolean contains(int c) {
            for (int i = 0; i < len; i++) if (cell(i) == c) return true;
            return false;
        }

        public boolean inside() {
            return x >= 0 && y >= 0 && (vertical ? y + len <= N && x < N : x + len <= N && y < N);
        }

        public Ship copy() {
            return new Ship(x, y, len, vertical);
        }
    }

    public final ArrayList<Ship> ships = new ArrayList<>();

    public SeaBoard copy() {
        SeaBoard b = new SeaBoard();
        for (Ship s : ships) b.ships.add(s.copy());
        return b;
    }

    public int shipAt(int cell) {
        for (int i = 0; i < ships.size(); i++) if (ships.get(i).contains(cell)) return i;
        return -1;
    }

    /** Can {@code s} stand here without touching any other ship (ignoring {@code skip}). */
    public boolean fits(Ship s, Ship skip) {
        if (!s.inside()) return false;
        for (Ship o : ships) {
            if (o == skip) continue;
            for (int i = 0; i < s.len; i++) {
                int a = s.cell(i), ax = a % N, ay = a / N;
                for (int j = 0; j < o.len; j++) {
                    int b = o.cell(j), bx = b % N, by = b / N;
                    if (Math.abs(ax - bx) <= 1 && Math.abs(ay - by) <= 1) return false;
                }
            }
        }
        return true;
    }

    /** A complete classic fleet, no ship touching another. */
    public boolean valid() {
        if (ships.size() != FLEET.length) return false;
        int[] need = new int[5], have = new int[5];
        for (int l : FLEET) need[l]++;
        for (Ship s : ships) {
            if (s.len < 1 || s.len > 4) return false;
            have[s.len]++;
            if (!fits(s, s)) return false;
        }
        for (int i = 0; i < 5; i++) if (need[i] != have[i]) return false;
        return true;
    }

    public static SeaBoard random(Random r) {
        while (true) {
            SeaBoard b = new SeaBoard();
            boolean ok = true;
            for (int len : FLEET) {
                boolean placed = false;
                for (int t = 0; t < 300 && !placed; t++) {
                    Ship s = new Ship(r.nextInt(N), r.nextInt(N), len, r.nextBoolean());
                    if (b.fits(s, null)) {
                        b.ships.add(s);
                        placed = true;
                    }
                }
                if (!placed) {
                    ok = false;
                    break;
                }
            }
            if (ok) return b;
        }
    }

    /** "xylo" per ship (o = h / v), comma separated: "004v,52 3h…". */
    public String encode() {
        StringBuilder b = new StringBuilder();
        for (Ship s : ships) {
            if (b.length() > 0) b.append(',');
            b.append(s.x).append(s.y).append(s.len).append(s.vertical ? 'v' : 'h');
        }
        return b.toString();
    }

    public static SeaBoard decode(String s) {
        SeaBoard b = new SeaBoard();
        if (s == null || s.isEmpty()) return b;
        try {
            for (String p : s.split(",")) {
                if (p.length() != 4) return new SeaBoard();
                b.ships.add(new Ship(p.charAt(0) - '0', p.charAt(1) - '0', p.charAt(2) - '0', p.charAt(3) == 'v'));
            }
        } catch (Exception e) {
            return new SeaBoard();
        }
        return b;
    }

    /** Commitment sent before the game: the layout can be checked once it is revealed at the end. */
    public static String commit(String layout, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] h = md.digest((layout + ":" + salt).getBytes("UTF-8"));
            StringBuilder b = new StringBuilder();
            for (int i = 0; i < 12; i++) b.append(String.format("%02x", h[i]));
            return b.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public static String newSalt() {
        String a = "abcdefghijkmnpqrstuvwxyz23456789";
        Random r = new java.security.SecureRandom();
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < 12; i++) b.append(a.charAt(r.nextInt(a.length())));
        return b.toString();
    }

    // ---- shots --------------------------------------------------------------------------------

    public static final int UNKNOWN = 0, MISS = 1, HIT = 2, SUNK = 3, NEAR = 4;

    /**
     * What a shooter knows of the target: miss / hit / sunk per cell, and the water around sunk ships
     * (NEAR) which cannot hold a ship any more. shots: cell → result ('m', 'h', 'k').
     */
    public static int[] knowledge(ArrayList<int[]> shots) {
        int[] k = new int[CELLS];
        for (int[] s : shots) k[s[0]] = s[1] == 'm' ? MISS : HIT;
        for (int[] s : shots) {
            if (s[1] != 'k') continue;
            // the sunk ship: the line of hits through this cell
            ArrayList<Integer> part = new ArrayList<>();
            ArrayList<Integer> stack = new ArrayList<>();
            stack.add(s[0]);
            boolean[] seen = new boolean[CELLS];
            while (!stack.isEmpty()) {
                int c = stack.remove(stack.size() - 1);
                if (seen[c]) continue;
                seen[c] = true;
                if (k[c] != HIT && k[c] != SUNK) continue;
                part.add(c);
                int x = c % N, y = c / N;
                if (x > 0) stack.add(c - 1);
                if (x < N - 1) stack.add(c + 1);
                if (y > 0) stack.add(c - N);
                if (y < N - 1) stack.add(c + N);
            }
            for (int c : part) {
                k[c] = SUNK;
                int x = c % N, y = c / N;
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = x + dx, ny = y + dy;
                    if (nx < 0 || ny < 0 || nx >= N || ny >= N) continue;
                    int n = ny * N + nx;
                    if (k[n] == UNKNOWN) k[n] = NEAR;
                }
            }
        }
        return k;
    }

    /** Result of a shot at this board given the earlier shots at it: 'm', 'h' or 'k'. */
    public char resolve(int cell, boolean[] hitBefore) {
        int i = shipAt(cell);
        if (i < 0) return 'm';
        Ship s = ships.get(i);
        for (int j = 0; j < s.len; j++) {
            int c = s.cell(j);
            if (c != cell && !hitBefore[c]) return 'h';
        }
        return 'k';
    }

    public static String cellName(int cell, String letters) {
        return letters.charAt(cell % N) + String.valueOf(cell / N + 1);
    }

    /** Sunk ships in a knowledge grid, each as its list of cells. */
    public static ArrayList<ArrayList<Integer>> sunkShips(int[] k) {
        ArrayList<ArrayList<Integer>> out = new ArrayList<>();
        boolean[] seen = new boolean[CELLS];
        for (int c0 = 0; c0 < CELLS; c0++) {
            if (k[c0] != SUNK || seen[c0]) continue;
            ArrayList<Integer> part = new ArrayList<>();
            ArrayList<Integer> stack = new ArrayList<>();
            stack.add(c0);
            while (!stack.isEmpty()) {
                int c = stack.remove(stack.size() - 1);
                if (seen[c] || k[c] != SUNK) continue;
                seen[c] = true;
                part.add(c);
                int x = c % N, y = c / N;
                if (x > 0) stack.add(c - 1);
                if (x < N - 1) stack.add(c + 1);
                if (y > 0) stack.add(c - N);
                if (y < N - 1) stack.add(c + N);
            }
            java.util.Collections.sort(part);
            out.add(part);
        }
        return out;
    }

    public static ArrayList<Integer> sunkLengths(int[] k) {
        ArrayList<Integer> l = new ArrayList<>();
        for (ArrayList<Integer> s : sunkShips(k)) l.add(s.size());
        return l;
    }
}
