package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;

import org.telegram.messenger.R;
import org.telegram.ui.Cells.ChatMessageCell;
import org.telegram.ui.Cells.ThemePreviewMessagesCell;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.WallpapersListActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Bubbles and messages: Telegram's bubble radius, Avragram's grouped-corner radius, the client's
 * message tail (all as overrides over the style) and the chat background, with a live preview.
 */
public class AvraBubblesActivity extends AvraDesignFragment {

    private static final int ID_RADIUS = 10, ID_NEAR = 11, ID_TAIL = 12, ID_BACKGROUND = 13;

    private ThemePreviewMessagesCell preview;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabBubbles);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[]{AvraDesign.Field.BUBBLE, AvraDesign.Field.BUBBLE_NEAR, AvraDesign.Field.TAIL};
    }

    @Override
    protected AvraDesign.Field fieldFor(int itemId) {
        switch (itemId) {
            case ID_RADIUS: return AvraDesign.Field.BUBBLE;
            case ID_NEAR: return AvraDesign.Field.BUBBLE_NEAR;
            case ID_TAIL: return AvraDesign.Field.TAIL;
            default: return null;
        }
    }

    @Override
    public View createView(Context context) {
        preview = new ThemePreviewMessagesCell(context, getParentLayout(), 0);
        return super.createView(context);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        if (preview != null) items.add(UItem.asCustom(preview));

        items.add(UItem.asHeader(getString(R.string.AvraBubbleRadius) + mark(AvraDesign.Field.BUBBLE)));
        UItem radius = UItem.asIntSlideView(1, 0, AvraDesign.value(AvraDesign.Field.BUBBLE), AvraDesign.BUBBLE_MAX, v -> v + " dp", v -> {
            AvraDesign.setOverride(AvraDesign.Field.BUBBLE, v);
            refreshPreview();
            scheduleRebuild();
        });
        radius.id = ID_RADIUS;
        items.add(radius);
        items.add(UItem.asShadow(getString(R.string.AvraBubblesInfo)));

        items.add(UItem.asHeader(getString(R.string.AvraBubbleNear) + mark(AvraDesign.Field.BUBBLE_NEAR)));
        UItem near = UItem.asIntSlideView(1, 0, AvraDesign.value(AvraDesign.Field.BUBBLE_NEAR), AvraDesign.BUBBLE_MAX, v -> v + " dp", v -> {
            AvraDesign.setOverride(AvraDesign.Field.BUBBLE_NEAR, v);
            refreshPreview();
            scheduleRebuild();
        });
        near.id = ID_NEAR;
        items.add(near);
        items.add(UItem.asShadow(getString(R.string.AvraBubbleNearInfo)));

        items.add(UItem.asSwitchNoIcon(ID_TAIL, getString(R.string.AvraTail) + mark(AvraDesign.Field.TAIL)).setChecked(AvraDesign.flag(AvraDesign.Field.TAIL)));
        items.add(UItem.asButton(ID_BACKGROUND, R.drawable.msg_background, getString(R.string.AvraChatBackground)));
        items.add(UItem.asShadow(null));

        addResetSection(items);
    }

    private void refreshPreview() {
        if (preview == null) return;
        for (ChatMessageCell c : preview.getCells()) {
            if (c.getMessageObject() != null) c.getMessageObject().resetLayout();
            c.requestLayout();
            c.invalidate();
        }
        preview.invalidate();
    }

    @Override
    protected void onDesignReset() {
        refreshPreview();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        if (item.id == ID_TAIL) {
            AvraDesign.setOverride(AvraDesign.Field.TAIL, !AvraDesign.flag(AvraDesign.Field.TAIL));
            refreshPreview();
            changed(false);
        } else if (item.id == ID_BACKGROUND) {
            presentFragment(new WallpapersListActivity(WallpapersListActivity.TYPE_ALL));
        } else {
            super.onClick(item, view, position, x, y);
        }
    }
}
