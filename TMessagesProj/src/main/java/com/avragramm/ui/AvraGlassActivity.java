package com.avragramm.ui;

import android.os.Build;
import android.view.View;

import com.avragramm.AvraConfig;
import com.avragramm.AvraDesign;
import com.exteragram.messenger.ExteraConfig;
import com.exteragram.messenger.GlassOutlineStyle;
import com.exteragram.messenger.HeaderBlurStyle;

import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Glass and effects: Avragram's glass mode together with the client's blur options (outline,
 * glass message menu, header blur). Device defaults stay: off on weak devices (tablet), liquid on
 * strong ones. These are device preferences, not style geometry, so they have no theme layer.
 */
public class AvraGlassActivity extends AvraDesignFragment {

    private static final int ID_OFF = 10, ID_FROSTED = 11, ID_LIQUID = 12, ID_OUTLINE = 20, ID_MENU = 21, ID_HEADER_BLUR = 22;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabGlass);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[0];
    }

    private static String[] outlines() {
        return new String[]{getString(R.string.GlassOutlineGlare), getString(R.string.GlassOutlineSolid), getString(R.string.GlassOutlineHidden)};
    }

    private static String[] headerBlurs() {
        return new String[]{getString(R.string.HeaderBlurDefault), getString(R.string.HeaderBlurProgressive), getString(R.string.HeaderBlurFade)};
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        AvraConfig.ensureLoaded();
        items.add(UItem.asHeader(getString(R.string.AvraGlass)));
        items.add(UItem.asRadio(ID_LIQUID, getString(R.string.AvraGlassLiquid), "Android 13+").setChecked(AvraConfig.glassMode == AvraConfig.GLASS_LIQUID));
        items.add(UItem.asRadio(ID_FROSTED, getString(R.string.AvraGlassFrosted)).setChecked(AvraConfig.glassMode == AvraConfig.GLASS_FROSTED));
        items.add(UItem.asRadio(ID_OFF, getString(R.string.AvraGlassOff)).setChecked(AvraConfig.glassMode == AvraConfig.GLASS_OFF));
        items.add(UItem.asShadow(getString(R.string.AvraGlassInfo)));

        items.add(UItem.asHeader(getString(R.string.BlurOptions)));
        items.add(UItem.asButton(ID_OUTLINE, getString(R.string.GlassOutlineStyle), outlines()[ExteraConfig.getGlassOutlineStyle().ordinal()]));
        items.add(UItem.asSwitchNoIcon(ID_MENU, getString(R.string.GlassMessageMenu)).setChecked(ExteraConfig.getGlassMessageMenu()));
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            items.add(UItem.asButton(ID_HEADER_BLUR, getString(R.string.HeaderBlurStyle), headerBlurs()[ExteraConfig.getHeaderBlurStyle().ordinal()]));
        }
        items.add(UItem.asShadow(getString(R.string.GlassMessageMenuInfo)));
    }

    private interface Pick { void run(int i); }

    private void pick(String title, String[] options, int selected, Pick cb) {
        String[] rows = new String[options.length];
        for (int i = 0; i < options.length; i++) rows[i] = (i == selected ? "● " : "○ ") + options[i];
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(title);
        b.setItems(rows, (d, w) -> {
            cb.run(w);
            changed(false);
        });
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        showDialog(b.create());
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id == ID_OFF || id == ID_FROSTED || id == ID_LIQUID) {
            AvraConfig.glassMode = id == ID_OFF ? AvraConfig.GLASS_OFF : id == ID_FROSTED ? AvraConfig.GLASS_FROSTED : AvraConfig.GLASS_LIQUID;
            AvraConfig.save();
            NotificationCenter.getGlobalInstance().postNotificationName(NotificationCenter.didSetNewTheme, false, false);
            listView.adapter.update(true);
        } else if (id == ID_OUTLINE) {
            pick(getString(R.string.GlassOutlineStyle), outlines(), ExteraConfig.getGlassOutlineStyle().ordinal(),
                    i -> ExteraConfig.setGlassOutlineStyle(GlassOutlineStyle.values()[i]));
        } else if (id == ID_MENU) {
            ExteraConfig.setGlassMessageMenu(!ExteraConfig.getGlassMessageMenu());
            changed(false);
        } else if (id == ID_HEADER_BLUR) {
            pick(getString(R.string.HeaderBlurStyle), headerBlurs(), ExteraConfig.getHeaderBlurStyle().ordinal(),
                    i -> ExteraConfig.setHeaderBlurStyle(HeaderBlurStyle.values()[i]));
        } else {
            super.onClick(item, view, position, x, y);
        }
    }
}
