package com.avragramm.ui;

import android.view.View;

import com.avragramm.AvraDesign;

import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.SharedConfig;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;
import org.telegram.ui.LiteModeSettingsActivity;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Animations: screen transitions (Telegram / Android / Spring + Avragram's own), their speed, how new
 * messages appear and their speed. Each style has its own animations; everything here is an override.
 * Replaces the client's "Transition animation" option (moved here from Navigation).
 */
public class AvraAnimActivity extends AvraDesignFragment {

    private static final int ID_TR_BASE = 100, ID_TR_SPEED = 150, ID_MSG_BASE = 200, ID_MSG_SPEED = 250;
    private static final int ID_PREVIEW = 300, ID_LITE = 301;

    static final int[] TRANSITIONS = {R.string.AvraTrTelegram, R.string.AvraTrAndroid, R.string.AvraTrSpring, R.string.AvraTrSlide,
            R.string.AvraTrFade, R.string.AvraTrZoom, R.string.AvraTrSlideUp, R.string.AvraTrFlip, R.string.AvraTrGlitch,
            R.string.AvraTrMist, R.string.AvraTrNone};
    static final int[] MESSAGES = {R.string.AvraMsgTelegram, R.string.AvraMsgFade, R.string.AvraMsgPop, R.string.AvraMsgSlide,
            R.string.AvraMsgSwing, R.string.AvraMsgRise};

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraTabAnim);
    }

    @Override
    protected AvraDesign.Field[] ownFields() {
        return new AvraDesign.Field[]{AvraDesign.Field.TRANSITION, AvraDesign.Field.TRANSITION_SPEED, AvraDesign.Field.MSG_ANIM, AvraDesign.Field.MSG_SPEED};
    }

    @Override
    protected AvraDesign.Field fieldFor(int id) {
        if (id >= ID_TR_BASE && id <= ID_TR_BASE + AvraDesign.TR_MAX) return AvraDesign.Field.TRANSITION;
        if (id >= ID_MSG_BASE && id <= ID_MSG_BASE + AvraDesign.MSG_MAX) return AvraDesign.Field.MSG_ANIM;
        if (id == ID_TR_SPEED) return AvraDesign.Field.TRANSITION_SPEED;
        if (id == ID_MSG_SPEED) return AvraDesign.Field.MSG_SPEED;
        return null;
    }

    /** Short name of the active transition, for the hub and the theme gear. */
    static String transitionTitle(int t) {
        return getString(TRANSITIONS[Math.max(0, Math.min(TRANSITIONS.length - 1, t))]);
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        int tr = AvraDesign.value(AvraDesign.Field.TRANSITION);
        items.add(UItem.asHeader(getString(R.string.AvraTransitions) + mark(AvraDesign.Field.TRANSITION)));
        for (int i = 0; i < TRANSITIONS.length; i++) {
            items.add(UItem.asRadio(ID_TR_BASE + i, getString(TRANSITIONS[i])).setChecked(tr == i));
        }
        // Android and Spring are physics / system driven: the speed slider applies to the others.
        if (tr != AvraDesign.TR_ANDROID && tr != AvraDesign.TR_SPRING && tr != AvraDesign.TR_NONE) {
            items.add(speed(ID_TR_SPEED, AvraDesign.Field.TRANSITION_SPEED, R.string.AvraTransitionSpeed));
        }
        items.add(UItem.asButton(ID_PREVIEW, R.drawable.msg_played, getString(R.string.AvraAnimPreview)));
        items.add(UItem.asShadow(getString(R.string.AvraTransitionsInfo)));

        int msg = AvraDesign.value(AvraDesign.Field.MSG_ANIM);
        items.add(UItem.asHeader(getString(R.string.AvraNewMessages) + mark(AvraDesign.Field.MSG_ANIM)));
        for (int i = 0; i < MESSAGES.length; i++) {
            items.add(UItem.asRadio(ID_MSG_BASE + i, getString(MESSAGES[i])).setChecked(msg == i));
        }
        if (msg != AvraDesign.MSG_TELEGRAM) {
            items.add(speed(ID_MSG_SPEED, AvraDesign.Field.MSG_SPEED, R.string.AvraMessageSpeed));
        }
        items.add(UItem.asShadow(getString(R.string.AvraNewMessagesInfo)));

        items.add(UItem.asButton(ID_LITE, R.drawable.msg_speed, getString(R.string.AvraLiteMode)));
        items.add(UItem.asShadow(getString(R.string.AvraLiteModeInfo)));

        addResetSection(items);
    }

    private UItem speed(int id, AvraDesign.Field field, int title) {
        UItem s = UItem.asIntSlideView(1, AvraDesign.SPEED_MIN, AvraDesign.value(field), AvraDesign.SPEED_MAX,
                v -> v + "%", v -> {
                    AvraDesign.setOverride(field, v);
                    scheduleRebuild();
                });
        s.id = id;
        s.text = getString(title) + mark(field);
        return s;
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
        int id = item.id;
        if (id >= ID_TR_BASE && id <= ID_TR_BASE + AvraDesign.TR_MAX) {
            int t = id - ID_TR_BASE;
            AvraDesign.setOverride(AvraDesign.Field.TRANSITION, t);
            if (t != AvraDesign.TR_NONE && !SharedConfig.animationsEnabled()) {
                // Same as the client did: a chosen animation needs Telegram's animations switched on.
                MessagesController.getGlobalMainSettings().edit().putBoolean("view_animations", true).apply();
                SharedConfig.setAnimationsEnabled(true);
            }
            changed(false);
        } else if (id >= ID_MSG_BASE && id <= ID_MSG_BASE + AvraDesign.MSG_MAX) {
            AvraDesign.setOverride(AvraDesign.Field.MSG_ANIM, id - ID_MSG_BASE);
            changed(false);
        } else if (id == ID_PREVIEW) {
            presentFragment(new Preview());
        } else if (id == ID_LITE) {
            presentFragment(new LiteModeSettingsActivity());
        } else {
            super.onClick(item, view, position, x, y);
        }
    }

    /** A small screen to see the transition in and out. */
    public static class Preview extends UniversalFragment {
        @Override
        protected CharSequence getTitle() {
            return getString(R.string.AvraAnimPreview);
        }

        @Override
        protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
            items.add(UItem.asHeader(transitionTitle(AvraDesign.transition())));
            items.add(UItem.asButton(1, R.drawable.msg_played, getString(R.string.AvraAnimPreviewAgain)));
            items.add(UItem.asShadow(getString(R.string.AvraAnimPreviewInfo)));
        }

        @Override
        protected void onClick(UItem item, View view, int position, float x, float y) {
            if (item.id == 1) presentFragment(new Preview());
        }

        @Override
        protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
            return false;
        }
    }
}
