package com.avragramm.ui;

import android.content.Context;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.avragramm.AvraMute;
import com.avragramm.AvraMuteLog;
import com.avragramm.AvraPresence;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.BulletinFactory;
import org.telegram.ui.Components.LayoutHelper;
import org.telegram.ui.Components.RecyclerListView;

import java.util.ArrayList;
import java.util.Calendar;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * All archived messages of one muted person, as incoming bubbles grouped by day, in the colours of
 * the active chat theme. Long press copies a message. Menu: unmute / clear the archive.
 */
public class AvraBehindChatActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_MUTE = 1, MENU_CLEAR = 2;

    private final long userId;
    private final ArrayList<Object> rows = new ArrayList<>(); // String = day header, Entry = message
    private RecyclerListView list;
    private Adapter adapter;
    private TextView emptyView;
    private FrameLayout root;
    private org.telegram.ui.ActionBar.ActionBarMenuSubItem muteItem;

    public AvraBehindChatActivity(long userId) {
        this.userId = userId;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) applyColors();
    }

    private String name() {
        TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(userId);
        if (u == null) return formatString(R.string.AvraBehindUnknown, userId);
        String at = AvraPresence.at(UserObject.getPublicUsername(u));
        String n = UserObject.getUserName(u);
        if (TextUtils.isEmpty(n)) n = at != null ? at : formatString(R.string.AvraBehindUnknown, userId);
        return n;
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(name());
        actionBar.setSubtitle(getString(R.string.AvraBehind));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    finishFragment();
                } else if (id == MENU_MUTE) {
                    boolean muted = AvraMute.toggle(currentAccount, userId);
                    updateMenu();
                    BulletinFactory.of(AvraBehindChatActivity.this).createSimpleBulletin(muted ? R.raw.ic_ban : R.raw.contact_check,
                            formatString(muted ? R.string.AvraMuted : R.string.AvraUnmuted, name())).show();
                } else if (id == MENU_CLEAR) {
                    confirmClear();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        ActionBarMenuItem other = menu.addItem(0, R.drawable.ic_ab_other);
        muteItem = other.addSubItem(MENU_MUTE, R.drawable.msg_block2, getString(R.string.AvraUnmuteUser));
        other.addSubItem(MENU_CLEAR, R.drawable.msg_delete, getString(R.string.AvraBehindClear));
        updateMenu();

        root = new FrameLayout(context);
        list = new RecyclerListView(context);
        LinearLayoutManager lm = new LinearLayoutManager(context, LinearLayoutManager.VERTICAL, false);
        lm.setStackFromEnd(true);
        list.setLayoutManager(lm);
        list.setPadding(0, AndroidUtilities.dp(8), 0, AndroidUtilities.dp(12));
        list.setClipToPadding(false);
        adapter = new Adapter();
        list.setAdapter(adapter);
        list.setOnItemLongClickListener((view, position) -> {
            if (position < 0 || position >= rows.size() || !(rows.get(position) instanceof AvraMuteLog.Entry)) return false;
            AvraMuteLog.Entry e = (AvraMuteLog.Entry) rows.get(position);
            if (TextUtils.isEmpty(e.text)) return false;
            AndroidUtilities.addToClipboard(e.text);
            BulletinFactory.of(this).createCopyBulletin(getString(R.string.TextCopied)).show();
            return true;
        });
        root.addView(list, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        emptyView = AvraFunUi.text(context, 15, false, true);
        emptyView.setText(getString(R.string.AvraBehindNothing));
        emptyView.setVisibility(View.GONE);
        root.addView(emptyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 32, 0, 32, 0));
        fragmentView = root;
        applyColors();
        load();
        return root;
    }

    private void updateMenu() {
        if (muteItem != null) {
            boolean muted = AvraMute.isMuted(currentAccount, userId);
            muteItem.setText(getString(muted ? R.string.AvraUnmuteUser : R.string.AvraMuteUser));
        }
    }

    private void load() {
        final int account = currentAccount;
        AvraMuteLog.queue.postRunnable(() -> {
            ArrayList<AvraMuteLog.Entry> msgs = AvraMuteLog.get(account).messages(userId);
            AndroidUtilities.runOnUIThread(() -> {
                rows.clear();
                int lastDay = -1;
                Calendar cal = Calendar.getInstance();
                for (AvraMuteLog.Entry e : msgs) {
                    cal.setTimeInMillis(e.date * 1000L);
                    int day = cal.get(Calendar.YEAR) * 1000 + cal.get(Calendar.DAY_OF_YEAR);
                    if (day != lastDay) {
                        rows.add(LocaleController.formatDateChat(e.date));
                        lastDay = day;
                    }
                    rows.add(e);
                }
                if (adapter != null) adapter.notifyDataSetChanged();
                if (emptyView != null) emptyView.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
                if (list != null && !rows.isEmpty()) list.scrollToPosition(rows.size() - 1);
                if (actionBar != null) actionBar.setSubtitle(getString(R.string.AvraBehind) + " · " + formatString(R.string.AvraBehindCount, msgs.size()));
            });
        });
    }

    private void confirmClear() {
        if (getParentActivity() == null) return;
        AlertDialog d = new AlertDialog.Builder(getParentActivity(), getResourceProvider())
                .setTitle(getString(R.string.AvraBehindClear))
                .setMessage(formatString(R.string.AvraBehindClearText, name()))
                .setPositiveButton(getString(R.string.Delete), (dialog, w) -> {
                    final int account = currentAccount;
                    AvraMuteLog.queue.postRunnable(() -> {
                        AvraMuteLog.get(account).clear(userId);
                        AndroidUtilities.runOnUIThread(this::load);
                    });
                })
                .setNegativeButton(getString(R.string.Cancel), null)
                .create();
        showDialog(d);
        TextView b = (TextView) d.getButton(android.content.DialogInterface.BUTTON_POSITIVE);
        if (b != null) b.setTextColor(Theme.getColor(Theme.key_text_RedBold));
    }

    private void applyColors() {
        if (root == null) return;
        root.setBackgroundColor(Theme.getColor(Theme.key_windowBackgroundGray));
        AvraFunUi.restyleText(emptyView);
        if (adapter != null) adapter.notifyDataSetChanged();
    }

    // ---- list -------------------------------------------------------------------------------

    private class Adapter extends RecyclerListView.SelectionAdapter {

        @Override
        public boolean isEnabled(RecyclerView.ViewHolder holder) {
            return holder.getItemViewType() == 1;
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position) instanceof String ? 0 : 1;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = viewType == 0 ? new DayView(parent.getContext()) : new BubbleView(parent.getContext());
            v.setLayoutParams(new RecyclerView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return new RecyclerListView.Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Object o = rows.get(position);
            if (holder.itemView instanceof DayView) ((DayView) holder.itemView).bind((String) o);
            else ((BubbleView) holder.itemView).bind((AvraMuteLog.Entry) o);
        }
    }

    private static class DayView extends FrameLayout {
        private final TextView text;

        DayView(Context context) {
            super(context);
            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            text.setTypeface(AndroidUtilities.bold());
            text.setPadding(AndroidUtilities.dp(10), AndroidUtilities.dp(3), AndroidUtilities.dp(10), AndroidUtilities.dp(4));
            addView(text, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 8, 0, 8));
        }

        void bind(String day) {
            text.setText(day);
            text.setTextColor(Theme.getColor(Theme.key_chat_serviceText));
            text.setBackground(AvraFunUi.rounded(Theme.getColor(Theme.key_chat_serviceBackground), AndroidUtilities.dp(12)));
        }
    }

    private static class BubbleView extends FrameLayout {
        private final LinearLayout bubble;
        private final TextView kind, text, time;

        BubbleView(Context context) {
            super(context);
            bubble = new LinearLayout(context);
            bubble.setOrientation(LinearLayout.VERTICAL);
            bubble.setPadding(AndroidUtilities.dp(12), AndroidUtilities.dp(7), AndroidUtilities.dp(12), AndroidUtilities.dp(6));
            kind = new TextView(context);
            kind.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13);
            kind.setTypeface(AndroidUtilities.bold());
            bubble.addView(kind, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            text = new TextView(context);
            text.setTextSize(TypedValue.COMPLEX_UNIT_DIP, org.telegram.messenger.SharedConfig.fontSize);
            text.setTextIsSelectable(false);
            bubble.addView(text, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
            time = new TextView(context);
            time.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 12);
            bubble.addView(time, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.END, 0, 2, 0, 0));
            addView(bubble, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.START, 10, 2, 64, 2));
        }

        void bind(AvraMuteLog.Entry e) {
            int label = AvraMuteLog.kindLabel(e.kind);
            kind.setVisibility(label != 0 ? VISIBLE : GONE);
            if (label != 0) kind.setText(getString(label));
            text.setVisibility(TextUtils.isEmpty(e.text) ? GONE : VISIBLE);
            text.setText(e.text);
            time.setText(LocaleController.getInstance().getFormatterDay().format(e.date * 1000L));
            // bubble radius of the active style (Avragram override layer included)
            float r = AndroidUtilities.dp(org.telegram.messenger.SharedConfig.bubbleRadius);
            bubble.setBackground(AvraFunUi.rounded(Theme.getColor(Theme.key_chat_inBubble), r));
            kind.setTextColor(Theme.getColor(Theme.key_chat_inReplyNameText));
            text.setTextColor(Theme.getColor(Theme.key_chat_messageTextIn));
            time.setTextColor(Theme.getColor(Theme.key_chat_inTimeText));
        }
    }
}
