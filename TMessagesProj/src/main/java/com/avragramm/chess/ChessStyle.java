package com.avragramm.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;

/**
 * AvraChess look, independent of the Telegram / Avragram theme: board colors, piece set and colors,
 * screen palette, highlights, sounds and behaviour switches. Stored in its own preferences.
 */
public final class ChessStyle {

    private ChessStyle() {
    }

    // ---- boards ---------------------------------------------------------------------------------

    public static final int[] BOARD_NAMES = {R.string.AvraChessBoardGreen, R.string.AvraChessBoardWood, R.string.AvraChessBoardBlue,
            R.string.AvraChessBoardMarble, R.string.AvraChessBoardNeon, R.string.AvraChessBoardPink, R.string.AvraChessBoardNight,
            R.string.AvraChessBoardIce, R.string.AvraChessBoardCandy, R.string.AvraChessBoardSlop, R.string.AvraChessBoardCustom};
    /** {light, dark} per board; the last one is replaced by the custom colors. */
    public static final int[][] BOARDS = {
            {0xFFEEEED2, 0xFF769656}, {0xFFF0D9B5, 0xFFB58863}, {0xFFDEE3E6, 0xFF8CA2AD}, {0xFFEDEDED, 0xFF9E9E9E},
            {0xFF2D1B69, 0xFF120B33}, {0xFFFCE4EC, 0xFFF06292}, {0xFF4B5563, 0xFF1F2937}, {0xFFE0F7FF, 0xFF7FB3D5},
            {0xFFFFF59D, 0xFF4DD0E1}, {0xFFC6FF00, 0xFFFF00E5}, {0xFFEEEED2, 0xFF769656}};
    public static final int BOARD_CUSTOM = 10;

    // ---- pieces ---------------------------------------------------------------------------------

    /** Piece sets, drawn from res/drawable-nodpi/chess_<file>_<w|b><p|n|b|r|q|k>.webp (see Tools/chess-art). */
    public static final String[] SET_FILES = {"classic", "merida", "chessnut", "fantasy", "spatial", "celtic", "papercut",
            "candy", "minimal", "neon", "gold", "tint"};
    public static final int SET_CLASSIC = 0, SET_NEON = 9, SET_GOLD = 10, SET_TINT = 11;
    public static final int[] SET_NAMES = {R.string.AvraChessSetClassic, R.string.AvraChessSetMerida, R.string.AvraChessSetChessnut,
            R.string.AvraChessSetFantasy, R.string.AvraChessSetSpatial, R.string.AvraChessSetCeltic, R.string.AvraChessSetPapercut,
            R.string.AvraChessSetCandy, R.string.AvraChessSetMinimal, R.string.AvraChessSetNeon, R.string.AvraChessSetGold,
            R.string.AvraChessSetTint};

    /** Swatches for piece, board and highlight colors. */
    public static final int[] SWATCHES = {0xFFFFFFFF, 0xFFF5F0E1, 0xFFFFD54F, 0xFFFF8A65, 0xFFE53935, 0xFFEC407A, 0xFFAB47BC,
            0xFF5C6BC0, 0xFF29B6F6, 0xFF26A69A, 0xFF66BB6A, 0xFFC6FF00, 0xFF8D6E63, 0xFF78909C, 0xFF37474F, 0xFF111111};

    // ---- screen palettes --------------------------------------------------------------------------

    public static final int[] UI_NAMES = {R.string.AvraChessUiDark, R.string.AvraChessUiLight, R.string.AvraChessUiWood,
            R.string.AvraChessUiNeon, R.string.AvraChessUiOcean, R.string.AvraChessUiSlop};
    /** {background, surface, text, muted, accent} */
    public static final int[][] UI = {
            {0xFF161512, 0xFF262421, 0xFFEDEDED, 0xFF9B9893, 0xFF81B64C},
            {0xFFF2F1EE, 0xFFFFFFFF, 0xFF1F1F1F, 0xFF75726C, 0xFF5D9948},
            {0xFF2B1D12, 0xFF3D2A1B, 0xFFF4E6D0, 0xFFBFA585, 0xFFE0A44C},
            {0xFF0A0618, 0xFF180F33, 0xFFF3EEFF, 0xFFA89BD1, 0xFF00F0FF},
            {0xFF06202B, 0xFF0D3240, 0xFFE4F6FB, 0xFF86B3C2, 0xFF22C3D6},
            {0xFFC6FF00, 0xFFFFF200, 0xFF2B0057, 0xFFD5006D, 0xFFFF00E5}};

    public static final int[] SOUND_NAMES = {R.string.AvraChessSoundOff, R.string.AvraChessSoundWood, R.string.AvraChessSoundClick,
            R.string.AvraChessSound8bit, R.string.AvraChessSoundSoft, R.string.AvraChessSoundCringe};

    // ---- settings -------------------------------------------------------------------------------

    public static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("avra_chess", Context.MODE_PRIVATE);
    }

    public static int getInt(String key, int def) {
        return prefs().getInt(key, def);
    }

    public static void setInt(String key, int v) {
        prefs().edit().putInt(key, v).apply();
    }

    public static boolean getBool(String key, boolean def) {
        return prefs().getBoolean(key, def);
    }

    public static void setBool(String key, boolean v) {
        prefs().edit().putBoolean(key, v).apply();
    }

    public static int board() {
        return getInt("board", 0);
    }

    public static int light() {
        return board() == BOARD_CUSTOM ? getInt("customLight", BOARDS[0][0]) : BOARDS[Math.min(board(), BOARDS.length - 1)][0];
    }

    public static int dark() {
        return board() == BOARD_CUSTOM ? getInt("customDark", BOARDS[0][1]) : BOARDS[Math.min(board(), BOARDS.length - 1)][1];
    }

    public static int pieceSet() {
        return getInt("pieces", SET_CLASSIC);
    }

    public static int whiteColor() {
        return getInt("whiteColor", 0xFFFFFFFF);
    }

    public static int blackColor() {
        return getInt("blackColor", 0xFF111111);
    }

    public static int highlight() {
        return getInt("highlight", 0xFFFFD54F);
    }

    public static int ui() {
        return Math.min(getInt("ui", 0), UI.length - 1);
    }

    public static int bg() {
        return UI[ui()][0];
    }

    public static int surface() {
        return UI[ui()][1];
    }

    public static int text() {
        return UI[ui()][2];
    }

    public static int muted() {
        return UI[ui()][3];
    }

    public static int accent() {
        return UI[ui()][4];
    }

    public static int sound() {
        return getInt("sound", 1);
    }

    public static boolean coords() {
        return getBool("coords", true);
    }

    public static boolean hints() {
        return getBool("hints", true);
    }

    public static boolean lastMove() {
        return getBool("lastMove", true);
    }

    public static boolean animate() {
        return getBool("animate", true);
    }

    public static boolean autoQueen() {
        return getBool("autoQueen", false);
    }

    public static boolean confirmResign() {
        return getBool("confirmResign", true);
    }

    public static boolean flipLocal() {
        return getBool("flipLocal", false);
    }

    // ---- piece drawing --------------------------------------------------------------------------

    private static final String LETTERS = "?pnbrqk";
    private static int loadedSet = -1;
    private static final android.graphics.Bitmap[] bitmaps = new android.graphics.Bitmap[13];
    private static final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
    private static final RectF rect = new RectF();
    private static int tintW, tintB;
    private static android.graphics.ColorFilter filterW, filterB;

    private static int contrast(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? 0xFF111111 : 0xFFF5F5F5;
    }

    private static android.graphics.Bitmap bitmap(int set, int piece) {
        if (set < 0 || set >= SET_FILES.length) set = SET_CLASSIC;
        if (set != loadedSet) {
            java.util.Arrays.fill(bitmaps, null);
            loadedSet = set;
        }
        int idx = (piece > 0 ? 0 : 6) + Math.abs(piece);
        android.graphics.Bitmap b = bitmaps[idx];
        if (b == null) {
            try {
                Context ctx = ApplicationLoader.applicationContext;
                String name = "chess_" + SET_FILES[set] + "_" + (piece > 0 ? "w" : "b") + LETTERS.charAt(Math.abs(piece));
                int res = ctx.getResources().getIdentifier(name, "drawable", ctx.getPackageName());
                android.graphics.BitmapFactory.Options o = new android.graphics.BitmapFactory.Options();
                o.inScaled = false;
                b = android.graphics.BitmapFactory.decodeResource(ctx.getResources(), res, o);
                bitmaps[idx] = b;
            } catch (Throwable e) {
                org.telegram.messenger.FileLog.e(e);
            }
        }
        return b;
    }

    /** "Own colors": the white artwork's fill becomes the color, its lines a contrasting tone. */
    private static android.graphics.ColorFilter tint(boolean white) {
        int color = white ? whiteColor() : blackColor();
        if (white ? (filterW == null || tintW != color) : (filterB == null || tintB != color)) {
            int line = contrast(color);
            float[] m = new float[20];
            int[] sh = {16, 8, 0};
            for (int i = 0; i < 3; i++) {
                int f = (color >> sh[i]) & 0xFF, l = (line >> sh[i]) & 0xFF;
                m[i * 5] = (f - l) / 255f;
                m[i * 5 + 4] = l;
            }
            m[18] = 1;
            android.graphics.ColorFilter cf = new android.graphics.ColorMatrixColorFilter(m);
            if (white) {
                filterW = cf;
                tintW = color;
            } else {
                filterB = cf;
                tintB = color;
            }
        }
        return white ? filterW : filterB;
    }

    /** Draws a piece centred at (cx, cy) inside a square of the given size. */
    public static void drawPiece(Canvas c, int piece, float cx, float cy, float size, int set, float alpha) {
        if (piece == 0 || alpha <= 0) return;
        android.graphics.Bitmap b = bitmap(set, piece);
        if (b == null) return;
        bmp.setAlpha((int) (255 * Math.min(1f, alpha)));
        bmp.setColorFilter(set == SET_TINT ? tint(piece > 0) : null);
        float h = size / 2f;
        rect.set(cx - h, cy - h, cx + h, cy + h);
        c.drawBitmap(b, null, rect, bmp);
    }
}
