package com.avragramm.chess;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;

import org.telegram.messenger.R;
import org.telegram.messenger.Utilities;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.getString;

/** AvraChess customization: board, pieces, colors, screen palette, sounds and behaviour. */
public class AvraChessSettingsActivity extends UniversalFragment {

    private static final int ID_BOARD = 100, ID_SET = 200, ID_UI = 300, ID_SOUND = 400,
            ID_LIGHT = 1, ID_DARK = 2, ID_WHITE = 3, ID_BLACK = 4, ID_HL = 5,
            ID_COORDS = 10, ID_HINTS = 11, ID_LAST = 12, ID_ANIM = 13, ID_QUEEN = 14, ID_CONFIRM = 15, ID_FLIP = 16, ID_SHARE = 17,
            ID_RESET = 20;

    private ChessBoardView preview;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraChessSettings);
    }

    private View previewView(Context context) {
        FrameLayout frame = new FrameLayout(context);
        preview = new ChessBoardView(context);
        ChessBoard b = new ChessBoard();
        for (String u : new String[]{"e2e4", "e7e5", "g1f3", "b8c6", "f1c4", "g8f6", "f3g5", "d7d5"}) b.make(b.parseUci(u));
        preview.setBoard(b, false);
        preview.setControls(0);
        frame.addView(preview, LayoutHelper.createFrame(240, 240, Gravity.CENTER));
        frame.setPadding(0, dp(12), 0, dp(12));
        return frame;
    }

    private void redraw() {
        if (preview != null) preview.invalidate();
        if (listView != null) listView.adapter.update(true);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asCustom(previewView(getContext())));
        items.add(UItem.asShadow(getString(R.string.AvraChessPreviewInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraChessBoard)));
        for (int i = 0; i < ChessStyle.BOARD_NAMES.length; i++) {
            items.add(UItem.asRadio(ID_BOARD + i, getString(ChessStyle.BOARD_NAMES[i])).setChecked(ChessStyle.board() == i));
        }
        if (ChessStyle.board() == ChessStyle.BOARD_CUSTOM) {
            items.add(UItem.asButton(ID_LIGHT, R.drawable.msg_colors, getString(R.string.AvraChessLightSquares), hex(ChessStyle.light())));
            items.add(UItem.asButton(ID_DARK, R.drawable.msg_colors, getString(R.string.AvraChessDarkSquares), hex(ChessStyle.dark())));
        }
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraChessPieces)));
        for (int i = 0; i < ChessStyle.SET_NAMES.length; i++) {
            items.add(UItem.asRadio(ID_SET + i, getString(ChessStyle.SET_NAMES[i])).setChecked(ChessStyle.pieceSet() == i));
        }
        items.add(UItem.asButton(ID_WHITE, R.drawable.msg_colors, getString(R.string.AvraChessWhiteColor), hex(ChessStyle.whiteColor())));
        items.add(UItem.asButton(ID_BLACK, R.drawable.msg_colors, getString(R.string.AvraChessBlackColor), hex(ChessStyle.blackColor())));
        items.add(UItem.asShadow(null));

        items.add(UItem.asHeader(getString(R.string.AvraChessScreen)));
        for (int i = 0; i < ChessStyle.UI_NAMES.length; i++) {
            items.add(UItem.asRadio(ID_UI + i, getString(ChessStyle.UI_NAMES[i])).setChecked(ChessStyle.ui() == i));
        }
        items.add(UItem.asButton(ID_HL, R.drawable.msg_colors, getString(R.string.AvraChessHighlight), hex(ChessStyle.highlight())));
        items.add(UItem.asShadow(getString(R.string.AvraChessScreenInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraChessSounds)));
        for (int i = 0; i < ChessStyle.SOUND_NAMES.length; i++) {
            items.add(UItem.asRadio(ID_SOUND + i, getString(ChessStyle.SOUND_NAMES[i])).setChecked(ChessStyle.sound() == i));
        }
        items.add(UItem.asShadow(getString(R.string.AvraChessSoundsInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraChessBehaviour)));
        items.add(UItem.asSwitchNoIcon(ID_COORDS, getString(R.string.AvraChessCoords)).setChecked(ChessStyle.coords()));
        items.add(UItem.asSwitchNoIcon(ID_HINTS, getString(R.string.AvraChessHints)).setChecked(ChessStyle.hints()));
        items.add(UItem.asSwitchNoIcon(ID_LAST, getString(R.string.AvraChessLastMove)).setChecked(ChessStyle.lastMove()));
        items.add(UItem.asSwitchNoIcon(ID_ANIM, getString(R.string.AvraChessAnimate)).setChecked(ChessStyle.animate()));
        items.add(UItem.asSwitchNoIcon(ID_QUEEN, getString(R.string.AvraChessAutoQueen)).setChecked(ChessStyle.autoQueen()));
        items.add(UItem.asSwitchNoIcon(ID_CONFIRM, getString(R.string.AvraChessConfirmResign)).setChecked(ChessStyle.confirmResign()));
        items.add(UItem.asSwitchNoIcon(ID_FLIP, getString(R.string.AvraChessFlipLocal)).setChecked(ChessStyle.flipLocal()));
        items.add(UItem.asCheck(ID_SHARE, getString(R.string.AvraChessShareTable), getString(R.string.AvraChessShareTableInfo), true).setChecked(ChessNet.shareTable()));
        items.add(UItem.asShadow(null));

        items.add(UItem.asButton(ID_RESET, R.drawable.msg_reset, getString(R.string.AvraChessReset)).red());
        items.add(UItem.asShadow(null));
    }

    private static String hex(int c) {
        return String.format("#%06X", c & 0xFFFFFF);
    }

    private void pickColor(String key, int current) {
        Context ctx = getContext();
        GridLayout grid = new GridLayout(ctx);
        grid.setColumnCount(8);
        grid.setPadding(dp(16), dp(12), dp(16), dp(4));
        AlertDialog[] dialog = new AlertDialog[1];
        for (int c : ChessStyle.SWATCHES) {
            View v = new View(ctx);
            GradientDrawable d = new GradientDrawable();
            d.setShape(GradientDrawable.OVAL);
            d.setColor(c);
            d.setStroke(dp(c == current ? 3 : 1), c == current ? 0xFF2196F3 : 0x55000000);
            v.setBackground(d);
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width = lp.height = dp(30);
            lp.setMargins(dp(3), dp(3), dp(3), dp(3));
            v.setOnClickListener(x -> {
                ChessStyle.setInt(key, c);
                if (dialog[0] != null) dialog[0].dismiss();
                redraw();
            });
            grid.addView(v, lp);
        }
        dialog[0] = new AlertDialog.Builder(ctx).setTitle(getString(R.string.AvraChessPickSwatch)).setView(grid)
                .setNegativeButton(getString(R.string.Cancel), null).create();
        showDialog(dialog[0]);
    }

    private void toggle(String key, boolean current) {
        ChessStyle.setBool(key, !current);
        redraw();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_BOARD && id < ID_BOARD + ChessStyle.BOARD_NAMES.length) {
            ChessStyle.setInt("board", id - ID_BOARD);
            redraw();
        } else if (id >= ID_SET && id < ID_SET + ChessStyle.SET_NAMES.length) {
            ChessStyle.setInt("pieces", id - ID_SET);
            redraw();
        } else if (id >= ID_UI && id < ID_UI + ChessStyle.UI_NAMES.length) {
            ChessStyle.setInt("ui", id - ID_UI);
            redraw();
        } else if (id >= ID_SOUND && id < ID_SOUND + ChessStyle.SOUND_NAMES.length) {
            ChessStyle.setInt("sound", id - ID_SOUND);
            ChessSound.play(id - ID_SOUND, ChessSound.MOVE);
            Utilities.globalQueue.postRunnable(() -> ChessSound.play(id - ID_SOUND, ChessSound.CAPTURE), 350);
            redraw();
        } else if (id == ID_LIGHT) {
            pickColor("customLight", ChessStyle.light());
        } else if (id == ID_DARK) {
            pickColor("customDark", ChessStyle.dark());
        } else if (id == ID_WHITE) {
            pickColor("whiteColor", ChessStyle.whiteColor());
        } else if (id == ID_BLACK) {
            pickColor("blackColor", ChessStyle.blackColor());
        } else if (id == ID_HL) {
            pickColor("highlight", ChessStyle.highlight());
        } else if (id == ID_COORDS) {
            toggle("coords", ChessStyle.coords());
        } else if (id == ID_HINTS) {
            toggle("hints", ChessStyle.hints());
        } else if (id == ID_LAST) {
            toggle("lastMove", ChessStyle.lastMove());
        } else if (id == ID_ANIM) {
            toggle("animate", ChessStyle.animate());
        } else if (id == ID_QUEEN) {
            toggle("autoQueen", ChessStyle.autoQueen());
        } else if (id == ID_CONFIRM) {
            toggle("confirmResign", ChessStyle.confirmResign());
        } else if (id == ID_FLIP) {
            toggle("flipLocal", ChessStyle.flipLocal());
        } else if (id == ID_SHARE) {
            toggle("shareTable", ChessNet.shareTable());
        } else if (id == ID_RESET) {
            ChessStyle.prefs().edit().remove("board").remove("customLight").remove("customDark").remove("pieces")
                    .remove("whiteColor").remove("blackColor").remove("highlight").remove("ui").remove("sound")
                    .remove("coords").remove("hints").remove("lastMove").remove("animate").remove("autoQueen")
                    .remove("confirmResign").remove("flipLocal").apply();
            redraw();
        }
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }
}
