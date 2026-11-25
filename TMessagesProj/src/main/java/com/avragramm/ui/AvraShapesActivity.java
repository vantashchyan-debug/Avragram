package com.avragramm.ui;

import android.content.Context;
import android.view.View;

import com.avragramm.AvraDesign;
import com.exteragram.messenger.preferences.appearance.components.AvatarCornersPreviewCell;
import com.exteragram.messenger.preferences.appearance.components.FabShapeCell;

import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Shapes and geometry: buttons, avatars (with the client's live preview), lists and cards,
 * centred title and the floating button (with the client's live preview). Every value is an
 * override over the active style; the previews write through {@link AvraDesign} as well.
 */
public class AvraShapesActivity extends AvraDesignFragment {

    private static final int ID_BUTTON_BASE = 100;
    private static final int ID_AVATAR = 200, ID_SINGLE_CORNER = 201;
    private static final int ID_DIVIDER_BASE = 300, ID_SECTION = 310, ID_SEPARATE_HEADERS = 311;
    private static final int ID_CENTER_TITLE = 400, ID_FAB = 401;

    private AvatarCornersPreviewCell avatarPreview;
    private FabShapeCell fabPreview;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabShapes);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[]{AvraDesign.Field.BUTTON, AvraDesign.Field.AVATAR, AvraDesign.Field.SINGLE_CORNER,
                AvraDesign.Field.DIVIDER, AvraDesign.Field.SECTION, AvraDesign.Field.SEPARATE_HEADERS,
                AvraDesign.Field.CENTER_TITLE, AvraDesign.Field.SQUARE_FAB};
    }

    @Override
    protected AvraDesign.Field fieldFor(int id) {
        if (id >= ID_BUTTON_BASE && id < ID_BUTTON_BASE + 5) return AvraDesign.Field.BUTTON;
        if (id >= ID_DIVIDER_BASE && id < ID_DIVIDER_BASE + 3) return AvraDesign.Field.DIVIDER;
        switch (id) {
            case ID_AVATAR: return AvraDesign.Field.AVATAR;
            case ID_SINGLE_CORNER: return AvraDesign.Field.SINGLE_CORNER;
            case ID_SECTION: return AvraDesign.Field.SECTION;
            case ID_SEPARATE_HEADERS: return AvraDesign.Field.SEPARATE_HEADERS;
            case ID_CENTER_TITLE: return AvraDesign.Field.CENTER_TITLE;
            case ID_FAB: return AvraDesign.Field.SQUARE_FAB;
            default: return null;
        }
    }

    @Override
    public View createView(Context context) {
        avatarPreview = new AvatarCornersPreviewCell(context, this, getResourceProvider());
        fabPreview = new FabShapeCell(context) {
            @Override
            protected void rebuildFragments() {
                // FabShapeCell already stored the override (AvraDesign.setOverride); refresh marks and other screens.
                changed(false);
            }
        };
        return super.createView(context);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        // ---- buttons
        items.add(UItem.asHeader(getString(R.string.AvraButtons) + mark(AvraDesign.Field.BUTTON)));
        int[] titles = {R.string.AvraButtonsTelegram, R.string.AvraButtonsPill, R.string.AvraButtonsRounded, R.string.AvraButtonsSharp, R.string.AvraButtonsTile};
        int button = AvraDesign.value(AvraDesign.Field.BUTTON);
        for (int i = 0; i < titles.length; i++) {
            items.add(UItem.asRadio(ID_BUTTON_BASE + i, getString(titles[i])).setChecked(button == i));
        }
        items.add(UItem.asShadow(getString(R.string.AvraButtonsInfo)));

        // ---- avatars
        items.add(UItem.asHeader(getString(R.string.AvraAvatars) + mark(AvraDesign.Field.AVATAR)));
        if (avatarPreview != null) items.add(UItem.asCustom(ID_AVATAR, avatarPreview));
        items.add(UItem.asSwitchNoIcon(ID_SINGLE_CORNER, getString(R.string.SingleCornerRadius) + mark(AvraDesign.Field.SINGLE_CORNER)).setChecked(AvraDesign.flag(AvraDesign.Field.SINGLE_CORNER)));
        items.add(UItem.asShadow(getString(R.string.SingleCornerRadiusInfo)));

        // ---- lists and cards
        items.add(UItem.asHeader(getString(R.string.AvraSections) + mark(AvraDesign.Field.DIVIDER)));
        int divider = AvraDesign.value(AvraDesign.Field.DIVIDER);
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_CARDS, getString(R.string.DividerStyleSegments)).setChecked(divider == AvraDesign.DIVIDER_CARDS));
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_LINE, getString(R.string.DividerStyleLine)).setChecked(divider == AvraDesign.DIVIDER_LINE));
        items.add(UItem.asRadio(ID_DIVIDER_BASE + AvraDesign.DIVIDER_HIDDEN, getString(R.string.DividerStyleHidden)).setChecked(divider == AvraDesign.DIVIDER_HIDDEN));
        UItem section = UItem.asIntSlideView(1, 0, AvraDesign.value(AvraDesign.Field.SECTION), AvraDesign.SECTION_MAX,
                v -> v == 0 ? getString(R.string.BlurOff) : v + " dp", v -> {
                    AvraDesign.setOverride(AvraDesign.Field.SECTION, v);
                    if (listView != null) {
                        listView.invalidate();
                        listView.invalidateItemDecorations();
                    }
                    scheduleRebuild();
                });
        section.id = ID_SECTION;
        section.text = getString(R.string.AvraSectionRadius) + mark(AvraDesign.Field.SECTION);
        items.add(section);
        // Cards always have separate headers (client rule), so the switch is only meaningful for the other styles.
        items.add(UItem.asSwitchNoIcon(ID_SEPARATE_HEADERS, getString(R.string.SeparateHeaders) + mark(AvraDesign.Field.SEPARATE_HEADERS))
                .setChecked(divider == AvraDesign.DIVIDER_CARDS || AvraDesign.flag(AvraDesign.Field.SEPARATE_HEADERS))
                .setEnabled(divider != AvraDesign.DIVIDER_CARDS));
        items.add(UItem.asShadow(null));

        // ---- title and floating button
        items.add(UItem.asSwitchNoIcon(ID_CENTER_TITLE, getString(R.string.AvraCenterTitle) + mark(AvraDesign.Field.CENTER_TITLE)).setChecked(AvraDesign.flag(AvraDesign.Field.CENTER_TITLE)));
        items.add(UItem.asShadow(null));
        items.add(UItem.asHeader(getString(R.string.AvraFab) + mark(AvraDesign.Field.SQUARE_FAB)));
        if (fabPreview != null) items.add(UItem.asCustom(ID_FAB, fabPreview));
        items.add(UItem.asShadow(null));

        addResetSection(items);
    }

    @Override
    protected void onDesignReset() {
        if (avatarPreview != null) avatarPreview.invalidate();
        if (fabPreview != null) fabPreview.invalidate();
        Theme.applyCommonTheme();
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_BUTTON_BASE && id < ID_BUTTON_BASE + 5) {
            AvraDesign.setOverride(AvraDesign.Field.BUTTON, id - ID_BUTTON_BASE);
        } else if (id == ID_SINGLE_CORNER) {
            AvraDesign.setOverride(AvraDesign.Field.SINGLE_CORNER, !AvraDesign.flag(AvraDesign.Field.SINGLE_CORNER));
            if (avatarPreview != null) avatarPreview.invalidate();
        } else if (id >= ID_DIVIDER_BASE && id < ID_DIVIDER_BASE + 3) {
            AvraDesign.setOverride(AvraDesign.Field.DIVIDER, id - ID_DIVIDER_BASE);
            Theme.applyCommonTheme();
            if (listView != null) listView.invalidateItemDecorations();
        } else if (id == ID_SEPARATE_HEADERS) {
            if (AvraDesign.value(AvraDesign.Field.DIVIDER) == AvraDesign.DIVIDER_CARDS) return;
            AvraDesign.setOverride(AvraDesign.Field.SEPARATE_HEADERS, !AvraDesign.flag(AvraDesign.Field.SEPARATE_HEADERS));
            if (listView != null) listView.invalidateItemDecorations();
        } else if (id == ID_CENTER_TITLE) {
            AvraDesign.setOverride(AvraDesign.Field.CENTER_TITLE, !AvraDesign.flag(AvraDesign.Field.CENTER_TITLE));
        } else {
            super.onClick(item, view, position, x, y);
            return;
        }
        changed(false);
    }
}
