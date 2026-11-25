package com.avragramm.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.avragramm.AvraMute;
import com.avragramm.AvraMuteLog;
import com.avragramm.AvraPresence;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.AvatarDrawable;
import org.telegram.ui.Components.BackupImageView;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.UItem;
import org.telegram.ui.Components.UniversalAdapter;
import org.telegram.ui.Components.UniversalFragment;

import java.util.ArrayList;
import java.util.HashSet;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * "Said behind your back": every muted person (and everyone who has an archive), with the number
 * of archived messages and the last one. Tap — open their messages; long press — unmute / clear.
 */
public class AvraBehindActivity extends UniversalFragment {

    private static final int ID_BASE = 100000;

    private final ArrayList<AvraMuteLog.Person> people = new ArrayList<>();
    private final ArrayList<PersonRow> rows = new ArrayList<>();
    private boolean loaded;

    @Override
    protected CharSequence getTitle() {
        return getString(R.string.AvraBehind);
    }

    @Override
    public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        final int account = currentAccount;
        AvraMuteLog.queue.postRunnable(() -> {
            ArrayList<AvraMuteLog.Person> list = AvraMuteLog.get(account).people();
            AndroidUtilities.runOnUIThread(() -> {
                // muted people without archived messages are listed too
                HashSet<Long> seen = new HashSet<>();
                for (AvraMuteLog.Person p : list) seen.add(p.userId);
                for (long id : AvraMute.mutedIds(account)) {
                    if (seen.add(id)) {
                        AvraMuteLog.Person p = new AvraMuteLog.Person();
                        p.userId = id;
                        p.lastText = "";
                        list.add(p);
                    }
                }
                people.clear();
                people.addAll(list);
                loaded = true;
                if (listView != null) listView.adapter.update(true);
            });
        });
    }

    @Override
    protected void fillItems(ArrayList<UItem> items, UniversalAdapter adapter) {
        items.add(UItem.asHeader(getString(R.string.AvraBehindPeople)));
        if (getContext() == null) return;
        while (rows.size() < people.size()) rows.add(new PersonRow(getContext()));
        for (int i = 0; i < people.size(); i++) {
            PersonRow r = rows.get(i);
            r.bind(people.get(i));
            items.add(UItem.asCustom(ID_BASE + i, r));
        }
        if (loaded && people.isEmpty()) {
            items.add(UItem.asShadow(getString(R.string.AvraBehindEmpty)));
        } else {
            items.add(UItem.asShadow(getString(R.string.AvraBehindInfo)));
        }
    }

    @Override
    protected void onClick(UItem item, View view, int position, float x, float y) {
    }

    @Override
    protected boolean onLongClick(UItem item, View view, int position, float x, float y) {
        return false;
    }

    private String nameOf(long userId) {
        TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(userId);
        if (u == null) return formatString(R.string.AvraBehindUnknown, userId);
        String n = UserObject.getUserName(u);
        return TextUtils.isEmpty(n) ? formatString(R.string.AvraBehindUnknown, userId) : n;
    }

    private void showActions(AvraMuteLog.Person p) {
        if (getParentActivity() == null) return;
        boolean muted = AvraMute.isMuted(currentAccount, p.userId);
        CharSequence[] items = {
                getString(muted ? R.string.AvraUnmuteUser : R.string.AvraMuteUser),
                getString(R.string.AvraBehindClear)
        };
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(nameOf(p.userId));
        b.setItems(items, (d, which) -> {
            if (which == 0) {
                AvraMute.toggle(currentAccount, p.userId);
                load();
            } else {
                final int account = currentAccount;
                AvraMuteLog.queue.postRunnable(() -> {
                    AvraMuteLog.get(account).clear(p.userId);
                    AndroidUtilities.runOnUIThread(this::load);
                });
            }
        });
        showDialog(b.create());
    }

    /** Avatar, name (+ "muted" badge), count and last message. */
    @SuppressLint("ViewConstructor")
    private class PersonRow extends FrameLayout {
        private final BackupImageView avatar;
        private final AvatarDrawable avatarDrawable = new AvatarDrawable();
        private final TextView name, sub, badge;
        private AvraMuteLog.Person person;

        PersonRow(Context context) {
            super(context);
            setForeground(Theme.getSelectorDrawable(false));
            avatar = new BackupImageView(context);
            avatar.setRoundRadius(AndroidUtilities.dp(23));
            addView(avatar, LayoutHelper.createFrame(46, 46, Gravity.START | Gravity.CENTER_VERTICAL, 16, 0, 0, 0));
            LinearLayout texts = new LinearLayout(context);
            texts.setOrientation(LinearLayout.VERTICAL);
            LinearLayout line = new LinearLayout(context);
            line.setOrientation(LinearLayout.HORIZONTAL);
            line.setGravity(Gravity.CENTER_VERTICAL);
            name = new TextView(context);
            name.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16);
            name.setSingleLine(true);
            name.setEllipsize(TextUtils.TruncateAt.END);
            name.setTypeface(AndroidUtilities.bold());
            line.addView(name, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f));
            badge = new TextView(context);
            badge.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 11);
            badge.setTypeface(AndroidUtilities.bold());
            badge.setPadding(AndroidUtilities.dp(6), AndroidUtilities.dp(1), AndroidUtilities.dp(6), AndroidUtilities.dp(1));
            badge.setText(getString(R.string.AvraBehindMutedBadge));
            line.addView(badge, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, 8, 0, 0, 0));
            texts.addView(line, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
            sub = new TextView(context);
            sub.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            sub.setSingleLine(true);
            sub.setEllipsize(TextUtils.TruncateAt.END);
            texts.addView(sub, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 3, 0, 0));
            addView(texts, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.START | Gravity.CENTER_VERTICAL, 76, 0, 16, 0));
            setOnClickListener(v -> {
                if (person != null) presentFragment(new AvraBehindChatActivity(person.userId));
            });
            setOnLongClickListener(v -> {
                if (person != null) showActions(person);
                return true;
            });
        }

        void bind(AvraMuteLog.Person p) {
            person = p;
            TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(p.userId);
            if (u != null) {
                avatarDrawable.setInfo(currentAccount, u);
                avatar.setForUserOrChat(u, avatarDrawable);
            } else {
                avatarDrawable.setInfo(p.userId, "?", null);
                avatar.setImageDrawable(avatarDrawable);
            }
            String at = u == null ? null : AvraPresence.at(UserObject.getPublicUsername(u));
            name.setText(nameOf(p.userId) + (at != null ? "  " + at : ""));
            boolean muted = AvraMute.isMuted(currentAccount, p.userId);
            badge.setVisibility(muted ? VISIBLE : GONE);
            String last;
            if (p.count == 0) {
                last = getString(R.string.AvraBehindNothing);
            } else {
                int label = AvraMuteLog.kindLabel(p.lastKind);
                String text = p.lastText == null ? "" : p.lastText.replace('\n', ' ');
                if (label != 0) text = getString(label) + (text.isEmpty() ? "" : " " + text);
                last = formatString(R.string.AvraBehindCount, p.count) + " · " + LocaleController.formatDateAudio(p.lastDate, true) + "\n" + text;
            }
            sub.setText(last.replace('\n', ' ').trim());
            name.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteBlackText));
            sub.setTextColor(Theme.getColor(Theme.key_windowBackgroundWhiteGrayText));
            badge.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            badge.setBackground(AvraFunUi.rounded(androidx.core.graphics.ColorUtils.setAlphaComponent(Theme.getColor(Theme.key_text_RedBold), 30), AndroidUtilities.dp(8)));
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            super.onMeasure(widthMeasureSpec, MeasureSpec.makeMeasureSpec(AndroidUtilities.dp(68), MeasureSpec.EXACTLY));
        }
    }
}
