package com.avragramm.chess;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;

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

    public static final int SET_CLASSIC = 0, SET_OUTLINE = 1, SET_NEON = 2, SET_LETTERS = 3, SET_PIXEL = 4, SET_EMOJI = 5, SET_BOLD = 6;
    public static final int[] SET_NAMES = {R.string.AvraChessSetClassic, R.string.AvraChessSetOutline, R.string.AvraChessSetNeon,
            R.string.AvraChessSetLetters, R.string.AvraChessSetPixel, R.string.AvraChessSetEmoji, R.string.AvraChessSetBold};

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

    private static final String SOLID = " ♟♞♝♜♛♚";
    private static final String HOLLOW = " ♙♘♗♖♕♔";
    private static final String LETTERS = " PNBRQK";
    private static final String[] EMOJI = {"", "🪖", "🐴", "🧙", "🏰", "💃", "👑"};
    private static final String VS15 = "︎"; // text presentation, never the color emoji pawn

    private static Typeface pixel;
    private static final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final Paint disc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private static final RectF rect = new RectF();

    private static Typeface pixelFont() {
        if (pixel == null) {
            try {
                pixel = Typeface.createFromAsset(ApplicationLoader.applicationContext.getAssets(), "fonts/avra/pressstart2p.ttf");
            } catch (Exception e) {
                pixel = Typeface.MONOSPACE;
            }
        }
        return pixel;
    }

    private static int contrast(int color) {
        int r = (color >> 16) & 0xFF, g = (color >> 8) & 0xFF, b = color & 0xFF;
        return (r * 299 + g * 587 + b * 114) / 1000 > 140 ? 0xFF111111 : 0xFFFFFFFF;
    }

    /** Draws a piece centred at (cx, cy) inside a square of the given size. */
    public static void drawPiece(Canvas c, int piece, float cx, float cy, float size, int set, float alpha) {
        if (piece == 0) return;
        int t = Math.abs(piece);
        boolean white = piece > 0;
        int color = white ? whiteColor() : blackColor();
        int edge = contrast(color);
        int a = (int) (255 * alpha);
        fill.setTypeface(Typeface.DEFAULT);
        fill.setTextAlign(Paint.Align.CENTER);
        fill.setShadowLayer(0, 0, 0, 0);
        stroke.setTypeface(Typeface.DEFAULT);
        stroke.setTextAlign(Paint.Align.CENTER);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        switch (set) {
            case SET_LETTERS:
            case SET_PIXEL:
            case SET_EMOJI: {
                float r = size * 0.38f;
                disc.setColor(color);
                disc.setAlpha(a);
                if (set == SET_PIXEL) {
                    rect.set(cx - r, cy - r, cx + r, cy + r);
                    c.drawRect(rect, disc);
                } else {
                    c.drawCircle(cx, cy, r, disc);
                }
                stroke.setColor(edge);
                stroke.setAlpha(a);
                stroke.setStrokeWidth(size * 0.035f);
                if (set == SET_PIXEL) c.drawRect(rect, stroke);
                else c.drawCircle(cx, cy, r, stroke);
                String s = set == SET_EMOJI ? EMOJI[t] : String.valueOf(LETTERS.charAt(t));
                fill.setColor(edge);
                fill.setAlpha(a);
                if (set == SET_PIXEL) fill.setTypeface(pixelFont());
                else if (set == SET_LETTERS) fill.setTypeface(Typeface.DEFAULT_BOLD);
                fill.setTextSize(set == SET_PIXEL ? size * 0.34f : set == SET_EMOJI ? size * 0.42f : size * 0.46f);
                Paint.FontMetrics fm = fill.getFontMetrics();
                c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, fill);
                return;
            }
            default:
                break;
        }
        float ts = size * (set == SET_BOLD ? 0.92f : 0.82f);
        fill.setTextSize(ts);
        stroke.setTextSize(ts);
        Paint.FontMetrics fm = fill.getFontMetrics();
        float y = cy - (fm.ascent + fm.descent) / 2f;
        String solid = SOLID.charAt(t) + VS15, hollow = HOLLOW.charAt(t) + VS15;
        if (set == SET_OUTLINE) {
            fill.setColor(color);
            fill.setAlpha(a);
            fill.setShadowLayer(size * 0.04f, 0, 0, edge);
            c.drawText(hollow, cx, y, fill);
            return;
        }
        if (set == SET_NEON) {
            fill.setColor(0xFF0A0A14);
            fill.setAlpha(a);
            c.drawText(solid, cx, y, fill);
            fill.setColor(color);
            fill.setAlpha(a);
            fill.setShadowLayer(size * 0.12f, 0, 0, color);
            c.drawText(hollow, cx, y, fill);
            fill.setShadowLayer(0, 0, 0, 0);
            return;
        }
        // classic / bold: solid body in the piece color, outline glyph on top
        stroke.setColor(edge);
        stroke.setAlpha(a);
        stroke.setStrokeWidth(size * (set == SET_BOLD ? 0.06f : 0.035f));
        c.drawText(solid, cx, y, stroke);
        fill.setColor(color);
        fill.setAlpha(a);
        c.drawText(solid, cx, y, fill);
        fill.setColor(edge);
        fill.setAlpha(a);
        c.drawText(hollow, cx, y, fill);
    }
}
