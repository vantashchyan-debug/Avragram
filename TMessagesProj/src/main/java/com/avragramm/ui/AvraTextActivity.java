package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;
import com.avragramm.AvraFonts;

import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Font and text: the app font (override over the style's font; "System"/"Roboto" replace the
 * client's old "use system fonts" switch) and Telegram's message text size, with the live preview.
 */
public class AvraTextActivity extends AvraDesignFragment {

    /** Telegram's own range for the message text size (ThemeActivity.TextSizeCell). */
    private static final int TEXT_MIN = 12, TEXT_MAX = 30;
    private static final int ID_FONT = 10;

    private ThemePreviewMessagesCell preview;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabText);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[]{AvraDesign.Field.FONT};
    }

    @Override
    protected AvraDesign.Field fieldFor(int itemId) {
        return itemId == ID_FONT ? AvraDesign.Field.FONT : null;
    }

    @Override
    public View createView(Context context) {
        preview = new ThemePreviewMessagesCell(context, getParentLayout(), 0);
        return super.createView(context);
    }

    private static String fontName(String id) {
        String n = AvraFonts.FONTS[AvraFonts.indexOf(id)][1];
        return n == null ? getString(R.string.AvraFontSystem) : n;
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (preview != null) items.add(UItem.asCustom(preview));

        items.add(UItem.asHeader(getString(R.string.AvraFont)));
        items.add(UItem.asButton(ID_FONT, R.drawable.msg_text_outlined, getString(R.string.AvraFont), fontName(AvraDesign.font()) + mark(AvraDesign.Field.FONT)));
        items.add(UItem.asShadow(getString(R.string.AvraFontInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraTextSize)));
        int size = Math.max(TEXT_MIN, Math.min(TEXT_MAX, SharedConfig.fontSize));
        items.add(UItem.asIntSlideView(1, TEXT_MIN, size, TEXT_MAX, v -> v + " pt", this::setTextSize));
        items.add(UItem.asShadow(null));

        addResetSection(items);
    }

    /** Same steps as Telegram's ThemeActivity.setFontSize, plus the preview refresh. */
    private void setTextSize(int size) {
        if (size == SharedConfig.fontSize) return;
        SharedConfig.fontSize = size;
        SharedConfig.fontSizeIsDefault = false;
        ApplicationLoader.applicationContext.getSharedPreferences("mainconfig", Context.MODE_PRIVATE).edit().putInt("fons_size", size).apply();
        Theme.createCommonMessageResources();
        refreshPreview();
        scheduleRebuild();
    }

    private void refreshPreview() {
        if (preview == null) return;
        for (ChatMessageCell c : preview.getCells()) {
            if (c.getMessageObject() != null) c.getMessageObject().resetLayout();
            c.requestLayout();
        }
        preview.invalidate();
    }

    @Override
    protected void onDesignReset() {
        refreshPreview();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_FONT) {
            String current = AvraDesign.font();
            String[] items = new String[AvraFonts.FONTS.length];
            for (int i = 0; i < items.length; i++) {
                String id = AvraFonts.FONTS[i][0];
                items[i] = (id.equals(current) ? "● " : "○ ") + fontName(id) + (id.equals(AvraDesign.baseFont()) ? " · " + getString(R.string.AvraStyle) : "");
            }
            AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
            b.setTitle(getString(R.string.AvraFont));
            b.setItems(items, (d, which) -> {
                String id = AvraFonts.FONTS[which][0];
                // Picking the style's own font clears the override, so later style switches change it again.
                boolean font = id.equals(AvraDesign.baseFont()) ? AvraDesign.reset(AvraDesign.Field.FONT) : AvraDesign.setFont(id);
                refreshPreview();
                changed(font);
            });
            b.setNegativeButton(getString(R.string.AvraCancel), null);
            showDialog(b.create());
            return;
        }
        super.onClick(item, view, position, x, y);
    }
}
