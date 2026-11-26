package com.avragramm.sea;

import java.util.ArrayList;
import java.util.Random;

/**
 * The AvraSea bot. Level 0 shoots at random (and only sometimes finishes a wounded ship), level 1
 * hunts on a checkerboard and finishes ships, level 2 counts every possible placement of the
 * remaining fleet and fires where a ship is most likely.
 */
public final class SeaAi {

    private SeaAi() {
    }

    public static final int LEVELS = 3;
    private static final Random random = new Random();

    /** knowledge: {@link SeaBoard#knowledge}; sunkLens: lengths of the ships already sunk. */
    public static int pick(int level, int[] knowledge, ArrayList<Integer> sunkLens) {
        ArrayList<Integer> free = new ArrayList<>();
        for (int c = 0; c < SeaBoard.CELLS; c++) if (knowledge[c] == SeaBoard.UNKNOWN) free.add(c);
        if (free.isEmpty()) return -1;
        ArrayList<Integer> wounded = new ArrayList<>();
        for (int c = 0; c < SeaBoard.CELLS; c++) if (knowledge[c] == SeaBoard.HIT) wounded.add(c);

        if (level <= 0) {
            if (!wounded.isEmpty() && random.nextInt(100) < 35) {
                int t = target(knowledge, wounded);
                if (t >= 0) return t;
            }
            // a cabin boy does not know that ships never touch: he also wastes shots next to wrecks
            ArrayList<Integer> any = new ArrayList<>(free);
            for (int c = 0; c < SeaBoard.CELLS; c++) if (knowledge[c] == SeaBoard.NEAR) any.add(c);
            return any.get(random.nextInt(any.size()));
        }
        if (level == 1) {
            if (!wounded.isEmpty()) {
                int t = target(knowledge, wounded);
                if (t >= 0) return t;
            }
            ArrayList<Integer> parity = new ArrayList<>();
            for (int c : free) if ((c % 10 + c / 10) % 2 == 0) parity.add(c);
            ArrayList<Integer> from = parity.isEmpty() ? free : parity;
            return from.get(random.nextInt(from.size()));
        }
        return density(knowledge, sunkLens, wounded, free);
    }

    /** Next to the wounded ship: along its line when two hits are known. */
    private static int target(int[] k, ArrayList<Integer> wounded) {
        ArrayList<Integer> options = new ArrayList<>();
        boolean line = false, vertical = false;
        if (wounded.size() >= 2) {
            line = true;
            vertical = wounded.get(0) % 10 == wounded.get(1) % 10;
        }
        for (int c : wounded) {
            int x = c % 10, y = c / 10;
            int[][] d = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
            for (int[] v : d) {
                if (line && (vertical ? v[0] != 0 : v[1] != 0)) continue;
                int nx = x + v[0], ny = y + v[1];
                if (nx < 0 || ny < 0 || nx > 9 || ny > 9) continue;
                int n = ny * 10 + nx;
                if (k[n] == SeaBoard.UNKNOWN && !options.contains(n)) options.add(n);
            }
        }
        return options.isEmpty() ? -1 : options.get(random.nextInt(options.size()));
    }

    private static int density(int[] k, ArrayList<Integer> sunkLens, ArrayList<Integer> wounded, ArrayList<Integer> free) {
        ArrayList<Integer> left = new ArrayList<>();
        for (int l : SeaBoard.FLEET) left.add(l);
        for (int l : sunkLens) left.remove(Integer.valueOf(l));
        int[] score = new int[SeaBoard.CELLS];
        for (int len : left) {
            for (int o = 0; o < 2; o++) {
                for (int y = 0; y < 10; y++) for (int x = 0; x < 10; x++) {
                    if (o == 0 ? x + len > 10 : y + len > 10) continue;
                    if (len == 1 && o == 1) continue;
                    boolean ok = true;
                    int hits = 0;
                    for (int i = 0; i < len && ok; i++) {
                        int c = o == 0 ? y * 10 + x + i : (y + i) * 10 + x;
                        int s = k[c];
                        if (s == SeaBoard.MISS || s == SeaBoard.SUNK || s == SeaBoard.NEAR) ok = false;
                        else if (s == SeaBoard.HIT) hits++;
                    }
                    if (!ok) continue;
                    if (!wounded.isEmpty() && hits == 0) continue;
                    int w = wounded.isEmpty() ? 1 : 1 + hits * 40;
                    for (int i = 0; i < len; i++) {
                        int c = o == 0 ? y * 10 + x + i : (y + i) * 10 + x;
                        if (k[c] == SeaBoard.UNKNOWN) score[c] += w;
                    }
                }
            }
        }
        int best = -1, bestScore = -1;
        for (int c : free) {
            int s = score[c] * 8 + random.nextInt(8);
            if (s > bestScore) {
                bestScore = s;
                best = c;
            }
        }
        return best;
    }
}
