package com.avragramm;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.exteragram.messenger.api.dto.BadgeDTO;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.BulletinFactory;

import java.util.HashMap;
import java.util.HashSet;

/**
 * "A" badge next to the names of other Avragram users.
 *
 * Telegram has no way to see someone's client, and Avragram has no server, so two Avragram apps
 * find each other with a hidden handshake: when you open a private chat, the app sends one
 * "watching an emoji" chat action (messages.setTyping) with a marker emoticon. Another Avragram
 * recognises it, never shows it, remembers you and answers once; other apps see at most a short
 * "watching 🅰" in the header. Nothing is sent when the badge is turned off. Only Avragram users
 * get a badge: exteraGram's server badges are not shown (see BadgesController.getBadge).
 */
public final class AvraBadge {

    private AvraBadge() {
    }

    /** Marker emoticon of the handshake. */
    public static final String MARK = "🅰";
    /** Fake custom emoji id: SwapAnimatedEmojiDrawable draws {@link #drawable()} for it. */
    public static final long DOCUMENT_ID = -0x41A7A6A1L;
    public static final BadgeDTO BADGE = new BadgeDTO(DOCUMENT_ID, null);

    private static final long PING_EVERY_MS = 6 * 60 * 60 * 1000L;
    private static final long ANSWER_PAUSE_MS = 10 * 60 * 1000L;

    private static final HashSet<String> known = new HashSet<>();
    private static final HashMap<String, Long> lastSent = new HashMap<>();
    private static boolean loaded;

    public static boolean enabled() {
        AvraConfig.ensureLoaded();
        return AvraConfig.avraBadge;
    }

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences("avra_badge", Context.MODE_PRIVATE);
    }

    private static String key(int account, long userId) {
        return account + "_" + userId;
    }

    private static synchronized void ensureLoaded() {
        if (loaded) return;
        loaded = true;
        known.addAll(prefs().getStringSet("known", new HashSet<>()));
    }

    /** Is this user known to use Avragram? You yourself always are. */
    public static boolean isAvragram(TLRPC.User user) {
        if (user == null || !enabled()) return false;
        if (UserObject.isUserSelf(user)) return true;
        ensureLoaded();
        synchronized (AvraBadge.class) {
            for (int a = 0; a < UserConfig.MAX_ACCOUNT_COUNT; a++) {
                if (known.contains(key(a, user.id))) return true;
            }
        }
        return false;
    }

    /** ChatActivity: a private chat with this user was opened. */
    public static void onChatOpened(int account, TLRPC.User user) {
        if (user == null || !enabled() || user.bot || user.deleted || UserObject.isUserSelf(user)
                || MessagesController.isSupportUser(user) || UserObject.isReplyUser(user)) return;
        Long last = lastSent.get(key(account, user.id));
        if (last != null && System.currentTimeMillis() - last < PING_EVERY_MS) return;
        send(account, user.id);
    }

    /** MessagesController: a chat action from a user in a private chat. True = it was the handshake, drop it. */
    public static boolean onUserAction(int account, long userId, TLRPC.SendMessageAction action) {
        if (!(action instanceof TLRPC.TL_sendMessageEmojiInteractionSeen)) return false;
        if (!MARK.equals(((TLRPC.TL_sendMessageEmojiInteractionSeen) action).emoticon)) return false;
        AndroidUtilities.runOnUIThread(() -> received(account, userId));
        return true;
    }

    private static void received(int account, long userId) {
        if (!enabled() || userId <= 0) return;
        ensureLoaded();
        String k = key(account, userId);
        boolean added;
        synchronized (AvraBadge.class) {
            added = known.add(k);
            if (added) prefs().edit().putStringSet("known", new HashSet<>(known)).apply();
        }
        if (added) {
            NotificationCenter.getInstance(account).postNotificationName(NotificationCenter.updateInterfaces, MessagesController.UPDATE_MASK_EMOJI_STATUS);
        }
        Long last = lastSent.get(k);
        if (last == null || System.currentTimeMillis() - last > ANSWER_PAUSE_MS) send(account, userId);
    }

    private static void send(int account, long userId) {
        TLRPC.InputPeer peer = MessagesController.getInstance(account).getInputPeer(userId);
        if (peer == null || peer instanceof TLRPC.TL_inputPeerEmpty) return;
        lastSent.put(key(account, userId), System.currentTimeMillis());
        TLRPC.TL_messages_setTyping req = new TLRPC.TL_messages_setTyping();
        req.peer = peer;
        TLRPC.TL_sendMessageEmojiInteractionSeen action = new TLRPC.TL_sendMessageEmojiInteractionSeen();
        action.emoticon = MARK;
        req.action = action;
        ConnectionsManager.getInstance(account).sendRequest(req, null);
    }

    /** Forgets every found user (when the badge is turned off). */
    public static synchronized void clear() {
        ensureLoaded();
        known.clear();
        lastSent.clear();
        prefs().edit().remove("known").apply();
    }

    /** Tap on the badge. */
    public static void showInfo(BaseFragment fragment, TLRPC.User user) {
        if (fragment == null || user == null) return;
        CharSequence text = UserObject.isUserSelf(user)
                ? LocaleController.getString(R.string.AvraBadgeSelf)
                : LocaleController.formatString(R.string.AvraBadgeInfo, UserObject.getFirstName(user));
        BulletinFactory.of(fragment).createSimpleBulletin(new BadgeDrawable(AndroidUtilities.dp(28)), text,
                LocaleController.getString(R.string.AvraBadgeInfoSub)).show();
    }

    public static Drawable drawable() {
        return new BadgeDrawable(-1);
    }

    /** Round gradient badge with a white bold "A". Keeps its colors under the status tint. */
    public static final class BadgeDrawable extends Drawable {
        private final Paint bg = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int size;
        private int alpha = 255;
        private int lastH;

        public BadgeDrawable(int size) {
            this.size = size;
            text.setColor(0xFFFFFFFF);
            text.setTypeface(AndroidUtilities.bold());
            text.setTextAlign(Paint.Align.CENTER);
        }

        @Override
        public void draw(@NonNull Canvas canvas) {
            Rect b = getBounds();
            float d = Math.min(b.width(), b.height()) * 0.86f;
            if (d <= 0) return;
            float cx = b.exactCenterX(), cy = b.exactCenterY(), r = d / 2f;
            if (lastH != (int) d) {
                lastH = (int) d;
                bg.setShader(new LinearGradient(0, -r, 0, r, 0xFF7B61FF, 0xFFE0418F, Shader.TileMode.CLAMP));
            }
            canvas.save();
            canvas.translate(cx, cy);
            bg.setAlpha(alpha);
            canvas.drawCircle(0, 0, r, bg);
            text.setAlpha(alpha);
            text.setTextSize(d * 0.62f);
            Paint.FontMetrics fm = text.getFontMetrics();
            canvas.drawText("A", 0, -(fm.ascent + fm.descent) / 2f, text);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            this.alpha = alpha;
        }

        @Override
        public void setColorFilter(@Nullable ColorFilter colorFilter) {
        }

        @Override
        public int getIntrinsicWidth() {
            return size;
        }

        @Override
        public int getIntrinsicHeight() {
            return size;
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }
}
