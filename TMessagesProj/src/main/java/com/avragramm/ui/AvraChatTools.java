package com.avragramm.ui;

import android.content.Context;
import android.text.InputType;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.avragramm.AvraConfig;
import com.avragramm.AvraSpam;
import com.avragramm.AvraVoiceFx;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.R;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;
import org.telegram.ui.Components.LayoutHelper;

/** Chat ⋮ menu tools: per-chat voice changer and message repeater ("Спам"). */
public final class AvraChatTools {

    private AvraChatTools() {}

    // ---------------------------------------------------------------- voice changer

    public static void showVoiceChanger(BaseFragment fragment, long dialogId) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        int current = AvraConfig.voicePreset(dialogId);
        int pitch = AvraConfig.voicePitch(dialogId);
        String[] items = new String[AvraVoiceFx.TITLES.length + 1];
        for (int i = 0; i < AvraVoiceFx.TITLES.length; i++) {
            items[i] = (i == current ? "● " : "○ ") + AvraVoiceFx.name(i);
        }
        items[AvraVoiceFx.TITLES.length] = formatString(R.string.AvraVoicePitch, (pitch > 0 ? "+" : "") + pitch);
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(getString(R.string.AvraVoiceTitle));
        b.setItems(items, (d, which) -> {
            if (which == AvraVoiceFx.TITLES.length) {
                showPitch(fragment, dialogId);
            } else {
                AvraConfig.setVoice(dialogId, which, AvraConfig.voicePitch(dialogId));
                toast(which == 0 ? getString(R.string.AvraVoiceOff) : formatString(R.string.AvraVoiceOn, AvraVoiceFx.name(which)));
            }
        });
        b.setNegativeButton(getString(R.string.AvraClose), null);
        fragment.showDialog(b.create());
    }

    private static void showPitch(BaseFragment fragment, long dialogId) {
        int[] values = {-12, -9, -7, -5, -3, -2, 0, 2, 3, 5, 7, 9, 12};
        int pitch = AvraConfig.voicePitch(dialogId);
        String[] items = new String[values.length];
        for (int i = 0; i < values.length; i++) {
            items[i] = (values[i] == pitch ? "● " : "○ ") + (values[i] > 0 ? "+" : "") + values[i];
        }
        AlertDialog.Builder b = new AlertDialog.Builder(fragment.getParentActivity());
        b.setTitle(getString(R.string.AvraVoicePitchTitle));
        b.setItems(items, (d, which) -> {
            AvraConfig.setVoice(dialogId, AvraConfig.voicePreset(dialogId), values[which]);
            showVoiceChanger(fragment, dialogId);
        });
        fragment.showDialog(b.create());
    }

    // ---------------------------------------------------------------- fun

    /** "Fun" popup from the ⋮ menu: the games, the soundpad and the settings page. */
    public static void showFun(BaseFragment fragment, long dialogId) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        int games = com.avragramm.AvraGames.IDS.length;
        CharSequence[] items = new CharSequence[games + 3];
        int[] icons = new int[games + 3];
        for (int i = 0; i < games; i++) {
            int best = com.avragramm.AvraGames.bestAny(i);
            items[i] = com.avragramm.AvraGames.TITLES[i]
                    + (best > 0 ? "  · " + formatString(R.string.AvraGameBestShort, best) : "");
            icons[i] = R.drawable.dialog_media_game_20;
        }
        items[games] = getString(R.string.AvraSoundpad);
        icons[games] = R.drawable.bot_music;
        items[games + 1] = getString(R.string.AvraFunSettingsItem);
        icons[games + 1] = R.drawable.msg_settings;
        int chessPending = com.avragramm.chess.ChessNet.pending(fragment.getCurrentAccount());
        items[games + 2] = "AvraChess" + (chessPending > 0 ? "  · " + formatString(R.string.AvraChessPending, chessPending) : "");
        icons[games + 2] = R.drawable.dialog_media_game_20;
        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(getString(R.string.AvraFun));
        b.setItems(items, icons, (d, which) -> {
            if (which < games) {
                fragment.presentFragment(new AvraGameActivity(which, dialogId));
            } else if (which == games) {
                new AvraSoundpadSheet(ctx, Theme.isCurrentThemeDark()).show();
            } else if (which == games + 2) {
                fragment.presentFragment(new com.avragramm.chess.AvraChessActivity(dialogId));
            } else {
                fragment.presentFragment(new AvraFunActivity(dialogId));
            }
        });
        b.setNegativeButton(getString(R.string.AvraClose), null);
        fragment.showDialog(b.create());
    }

    // ---------------------------------------------------------------- spam

    public static void showSpam(BaseFragment fragment, int account, long dialogId, String presetText) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null) return;
        if (AvraSpam.isRunning(dialogId)) {
            AvraSpam.stop(dialogId, true);
            return;
        }
        LinearLayout ll = new LinearLayout(ctx);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setPadding(AndroidUtilities.dp(24), AndroidUtilities.dp(4), AndroidUtilities.dp(24), 0);

        EditTextBoldCursor text = field(ctx, getString(R.string.AvraSpamText), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        if (!TextUtils.isEmpty(presetText)) text.setText(presetText);
        EditTextBoldCursor count = field(ctx, AvraSpam.maxCount() > AvraSpam.MAX_COUNT ? getString(R.string.AvraSpamCountHintUnlimited)
                : formatString(R.string.AvraSpamCountHint, AvraSpam.MAX_COUNT), InputType.TYPE_CLASS_NUMBER);
        count.setText("10");
        EditTextBoldCursor interval = field(ctx, formatString(R.string.AvraSpamIntervalHint, AvraSpam.MIN_INTERVAL_MS), InputType.TYPE_CLASS_NUMBER);
        interval.setText("1000");

        ll.addView(label(ctx, getString(R.string.AvraSpamMessage)));
        ll.addView(text, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        ll.addView(label(ctx, getString(R.string.AvraSpamCount)));
        ll.addView(count, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 12));
        ll.addView(label(ctx, getString(R.string.AvraSpamInterval)));
        ll.addView(interval, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 0, 0, 8));
        TextView warn = label(ctx, formatString(R.string.AvraSpamWarn, AvraSpam.MIN_INTERVAL_MS));
        warn.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
        ll.addView(warn);

        AlertDialog.Builder b = new AlertDialog.Builder(ctx);
        b.setTitle(getString(R.string.AvraSpam));
        b.setView(ll);
        b.setPositiveButton(getString(R.string.AvraSpamStart), (d, w) -> {
            String msg = text.getText() == null ? "" : text.getText().toString().trim();
            if (msg.isEmpty()) {
                toast(getString(R.string.AvraSpamEmpty));
                return;
            }
            int n = parseInt(count.getText() == null ? "" : count.getText().toString(), 10);
            double ms = parseDouble(interval.getText() == null ? "" : interval.getText().toString(), 1000);
            AvraSpam.start(account, dialogId, msg, n, (long) Math.ceil(ms));
        });
        b.setNegativeButton(getString(R.string.AvraCancel), null);
        fragment.showDialog(b.create());
    }

    private static EditTextBoldCursor field(Context ctx, String hint, int inputType) {
        EditTextBoldCursor e = new EditTextBoldCursor(ctx);
        e.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
        e.setTextColor(Theme.getColor(Theme.key_dialogTextBlack));
        e.setHintTextColor(Theme.getColor(Theme.key_dialogTextHint));
        e.setHint(hint);
        e.setInputType(inputType);
        e.setBackground(null);
        e.setLineColors(Theme.getColor(Theme.key_dialogInputField), Theme.getColor(Theme.key_dialogInputFieldActivated), Theme.getColor(Theme.key_text_RedBold));
        e.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        e.setCursorColor(Theme.getColor(Theme.key_dialogTextBlack));
        e.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        return e;
    }

    private static TextView label(Context ctx, String s) {
        TextView t = new TextView(ctx);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
        t.setTextColor(Theme.getColor(Theme.key_dialogTextGray2));
        return t;
    }

    private static int parseInt(String s, int def) {
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return def;
        }
    }

    private static double parseDouble(String s, double def) {
        try {
            return Double.parseDouble(s.trim().replace(',', '.'));
        } catch (Exception e) {
            return def;
        }
    }

    private static void toast(String text) {
        try {
            org.telegram.ui.Components.BulletinFactory.global().createSimpleBulletin(org.telegram.messenger.R.raw.info, text).show();
        } catch (Throwable ignore) {
        }
    }

    /**
     * Mute / unmute a person in a private chat. Muting asks first: from now on every message they
     * send is deleted for both sides on arrival and never shown here. Unmuting is immediate.
     */
    public static void showMute(BaseFragment fragment, int account, org.telegram.tgnet.TLRPC.User user, Runnable onChanged) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || user == null) return;
        String name = com.avragramm.AvraPresence.at(org.telegram.messenger.UserObject.getPublicUsername(user));
        if (name == null) name = org.telegram.messenger.UserObject.getUserName(user);
        final String who = TextUtils.isEmpty(name) ? getString(R.string.AvraMuteThisPerson) : name;
        if (com.avragramm.AvraMute.isMuted(account, user.id)) {
            com.avragramm.AvraMute.toggle(account, user.id);
            if (onChanged != null) onChanged.run();
            org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                    formatString(R.string.AvraUnmuted, who)).show();
            return;
        }
        AlertDialog d = new AlertDialog.Builder(ctx, fragment.getResourceProvider())
                .setTitle(formatString(R.string.AvraMuteTitle, who))
                .setMessage(AndroidUtilities.replaceTags(getString(R.string.AvraMuteText)))
                .setPositiveButton(getString(R.string.AvraMuteUser), (dialog, which) -> {
                    if (!com.avragramm.AvraMute.isMuted(account, user.id)) com.avragramm.AvraMute.toggle(account, user.id);
                    if (onChanged != null) onChanged.run();
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_ban,
                            formatString(R.string.AvraMuted, who)).show();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        fragment.showDialog(d);
        android.widget.TextView button = (android.widget.TextView) d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (button != null) button.setTextColor(Theme.getColor(Theme.key_text_RedBold, fragment.getResourceProvider()));
    }

    /** Fake mute: their new messages vanish for them at once, but stay here marked as deleted. */
    public static void showFakeMute(BaseFragment fragment, int account, org.telegram.tgnet.TLRPC.User user, Runnable onChanged) {
        Context ctx = fragment.getParentActivity();
        if (ctx == null || user == null) return;
        String name = com.avragramm.AvraPresence.at(org.telegram.messenger.UserObject.getPublicUsername(user));
        if (name == null) name = org.telegram.messenger.UserObject.getUserName(user);
        final String who = TextUtils.isEmpty(name) ? getString(R.string.AvraMuteThisPerson) : name;
        if (com.avragramm.AvraMute.isFakeMuted(account, user.id)) {
            com.avragramm.AvraMute.toggle(account, user.id, true);
            if (onChanged != null) onChanged.run();
            org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.contact_check,
                    formatString(R.string.AvraFakeUnmuted, who)).show();
            return;
        }
        AlertDialog d = new AlertDialog.Builder(ctx, fragment.getResourceProvider())
                .setTitle(formatString(R.string.AvraFakeMuteTitle, who))
                .setMessage(AndroidUtilities.replaceTags(getString(R.string.AvraFakeMuteText)))
                .setPositiveButton(getString(R.string.AvraFakeMuteUser), (dialog, which) -> {
                    if (!com.avragramm.AvraMute.isFakeMuted(account, user.id)) com.avragramm.AvraMute.toggle(account, user.id, true);
                    if (onChanged != null) onChanged.run();
                    org.telegram.ui.Components.BulletinFactory.of(fragment).createSimpleBulletin(R.raw.ic_ban,
                            formatString(R.string.AvraFakeMuted, who)).show();
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        fragment.showDialog(d);
    }
}
