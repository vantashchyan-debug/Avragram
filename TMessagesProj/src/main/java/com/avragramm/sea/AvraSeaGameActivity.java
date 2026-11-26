package com.avragramm.sea;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.R;
import org.telegram.messenger.UserObject;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;
import java.util.Random;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * An AvraSea game: against the bot (three levels) or online with a friend. Phases: placing the fleet,
 * waiting for the friend, the battle (enemy waters on top, your fleet below) and the result with
 * the opponent's revealed fleet.
 */
public class AvraSeaGameActivity extends BaseFragment implements SeaNet.Listener {

    static final int BG = 0xFF061827, SURFACE = 0xFF0D2B42, TEXT = 0xFFF1F8FF, MUTED = 0xFF8FB3CC, ACCENT = 0xFF29B6F6, RED = 0xFFFF5252, GOLD = 0xFFFFD54F;
    private static final int KIND_BOT = 0, KIND_CHALLENGE = 1, KIND_ONLINE = 2;
    public static final int[] LEVEL_NAMES = {R.string.AvraSeaLevel0, R.string.AvraSeaLevel1, R.string.AvraSeaLevel2};

    private final int kind;
    private int level;
    private long peer;
    private String gameId;

    // bot game
    private SeaBoard myFleet = SeaBoard.random(new Random()), botFleet;
    private final ArrayList<int[]> myShots = new ArrayList<>(), botShots = new ArrayList<>();
    private boolean placing = true, botTurn, botOver;
    private int botWinner;

    private LinearLayout root;
    private TextView title, subtitle, banner, hint;
    private SeaView enemyView, ownView;
    private FleetView enemyFleetView, ownFleetView;
    private TextView enemyLabel, ownLabel;
    private LinearLayout buttons;
    private FrameLayout enemyFrame;
    private int lastMyShots = -1, lastTheirShots = -1;

    public static AvraSeaGameActivity bot(int level) {
        AvraSeaGameActivity a = new AvraSeaGameActivity(KIND_BOT);
        a.level = level;
        return a;
    }

    public static AvraSeaGameActivity challenge(long peer) {
        AvraSeaGameActivity a = new AvraSeaGameActivity(KIND_CHALLENGE);
        a.peer = peer;
        return a;
    }

    public static AvraSeaGameActivity online(String id) {
        AvraSeaGameActivity a = new AvraSeaGameActivity(KIND_ONLINE);
        a.gameId = id;
        return a;
    }

    private AvraSeaGameActivity(int kind) {
        this.kind = kind;
    }

    private SeaNet.Game game() {
        return kind == KIND_ONLINE ? SeaNet.get(gameId) : null;
    }

    @Override
    public boolean onFragmentCreate() {
        SeaNet.addListener(this);
        SeaSound.preload();
        SeaNet.Game g = game();
        if (kind == KIND_ONLINE) {
            if (g == null) return false;
            placing = g.state == SeaNet.INVITE_IN;
            if (!TextUtils.isEmpty(g.myLayout)) myFleet = SeaBoard.decode(g.myLayout);
            SeaNet.sync(currentAccount, g.peer, null);
        }
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        SeaNet.removeListener(this);
        super.onFragmentDestroy();
    }

    @Override
    public void onResume() {
        super.onResume();
        if (gameId != null) SeaNet.openGameId = gameId;
    }

    @Override
    public void onPause() {
        super.onPause();
        if (gameId != null && gameId.equals(SeaNet.openGameId)) SeaNet.openGameId = null;
    }

    @Override
    public boolean isSwipeBackEnabled(MotionEvent event) {
        return false;
    }

    @Override
    public boolean isLightStatusBar() {
        return false;
    }

    // ---- UI ---------------------------------------------------------------------------------------

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle("🚢 " + getString(R.string.AvraSeaTitle));
        actionBar.setBackgroundColor(SURFACE);
        actionBar.setTitleColor(TEXT);
        actionBar.setSubtitleColor(MUTED);
        actionBar.setItemsColor(TEXT, false);
        actionBar.setItemsBackgroundColor(0x2229B6F6, false);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
            }
        });

        ScrollView scroll = new ScrollView(context);
        scroll.setBackgroundColor(BG);
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(root, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        title = text(context, 20, true, TEXT);
        title.setGravity(Gravity.CENTER);
        root.addView(title, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 12, 12, 0));
        subtitle = text(context, 14, false, MUTED);
        subtitle.setGravity(Gravity.CENTER);
        root.addView(subtitle, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 12, 2, 12, 8));

        enemyLabel = label(context);
        root.addView(enemyLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 4, 14, 2));
        enemyFrame = new FrameLayout(context);
        enemyView = new SeaView(context, SeaView.MODE_ENEMY);
        enemyView.setLetters(SeaNet.letters());
        enemyView.setListener(new SeaView.Listener() {
            @Override
            public void onCell(int cell) {
                fire(cell);
            }

            @Override
            public void onFleetChanged() {
            }
        });
        enemyFrame.addView(enemyView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        banner = text(context, 30, true, TEXT);
        banner.setGravity(Gravity.CENTER);
        banner.setShadowLayer(dp(6), 0, dp(2), 0xCC000000);
        banner.setAlpha(0f);
        enemyFrame.addView(banner, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.CENTER));
        root.addView(enemyFrame, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 14, 0));
        enemyFleetView = new FleetView(context);
        root.addView(enemyFleetView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22, 20, 6, 20, 4));

        ownLabel = label(context);
        root.addView(ownLabel, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 14, 10, 14, 2));
        ownView = new SeaView(context, SeaView.MODE_PLACE);
        ownView.setLetters(SeaNet.letters());
        ownView.setFleet(myFleet, true);
        ownView.setListener(new SeaView.Listener() {
            @Override
            public void onCell(int cell) {
            }

            @Override
            public void onFleetChanged() {
            }
        });
        root.addView(ownView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 8, 0, 14, 0));
        ownFleetView = new FleetView(context);
        root.addView(ownFleetView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 22, 20, 6, 20, 4));

        hint = text(context, 13, false, MUTED);
        hint.setGravity(Gravity.CENTER);
        root.addView(hint, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 16, 6, 16, 4));

        buttons = new LinearLayout(context);
        buttons.setGravity(Gravity.CENTER);
        root.addView(buttons, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 52, 8, 6, 8, 18));

        fragmentView = scroll;
        refresh(false);
        return fragmentView;
    }

    private static TextView text(Context c, int sp, boolean bold, int color) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_DIP, sp);
        if (bold) t.setTypeface(AndroidUtilities.bold());
        t.setTextColor(color);
        return t;
    }

    private static TextView label(Context c) {
        TextView t = text(c, 13, true, MUTED);
        t.setAllCaps(true);
        t.setLetterSpacing(0.08f);
        return t;
    }

    private TextView button(String label, boolean accent, View.OnClickListener l) {
        TextView b = text(getContext(), 15, true, accent ? 0xFF04121D : TEXT);
        b.setText(label);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(8), 0, dp(8), 0);
        GradientDrawable d = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                accent ? new int[]{0xFF4FC3F7, 0xFF0288D1} : new int[]{0xFF15405F, SURFACE});
        d.setCornerRadius(dp(14));
        b.setBackground(d);
        b.setOnClickListener(l);
        return b;
    }

    private void addButton(TextView b) {
        buttons.addView(b, LayoutHelper.createLinear(0, 46, 1f, 4, 0, 4, 0));
    }

    private void showBanner(String s, int color) {
        banner.setText(s);
        banner.setTextColor(color);
        banner.animate().cancel();
        banner.setAlpha(0f);
        banner.setScaleX(0.4f);
        banner.setScaleY(0.4f);
        banner.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(260)
                .setInterpolator(new android.view.animation.OvershootInterpolator(2.2f))
                .withEndAction(() -> banner.animate().alpha(0f).setStartDelay(650).setDuration(300).start()).start();
    }

    private String oppName() {
        SeaNet.Game g = game();
        long p = g != null ? g.peer : peer;
        TLRPC.User u = MessagesController.getInstance(currentAccount).getUser(p);
        String n = u != null ? UserObject.getUserName(u) : g != null ? g.oppName : "";
        return TextUtils.isEmpty(n) ? getString(R.string.AvraSeaOpponent) : n;
    }

    // ---- state -> screen ----------------------------------------------------------------------

    private void refresh(boolean animate) {
        if (fragmentView == null) return;
        buttons.removeAllViews();
        SeaNet.Game g = game();
        boolean place = placing && (kind != KIND_ONLINE || g != null && g.state == SeaNet.INVITE_IN);
        String opp = kind == KIND_BOT ? "🤖 " + getString(LEVEL_NAMES[level]) : oppName();
        actionBar.setSubtitle(kind == KIND_BOT ? getString(R.string.AvraSeaVsBot) : opp);

        if (place) {
            title.setText(getString(R.string.AvraSeaPlaceTitle));
            subtitle.setText(getString(R.string.AvraSeaPlaceInfo));
            setEnemyVisible(false);
            ownLabel.setText(getString(R.string.AvraSeaYourFleet));
            ownView.setMode(SeaView.MODE_PLACE);
            ownView.setFleet(myFleet, true);
            ownView.setMarks(new int[SeaBoard.CELLS], false);
            ownFleetView.set(new ArrayList<>());
            hint.setText(getString(R.string.AvraSeaPlaceHint));
            addButton(button("🎲 " + getString(R.string.AvraSeaRandom), false, v -> {
                myFleet = SeaBoard.random(new Random());
                ownView.setFleet(myFleet, true);
                SeaSound.play(SeaSound.PLACE);
            }));
            String go = kind == KIND_CHALLENGE ? "📨 " + getString(R.string.AvraSeaSendChallenge)
                    : kind == KIND_ONLINE ? "⚓ " + getString(R.string.AvraSeaAcceptBattle) : "⚓ " + getString(R.string.AvraSeaToBattle);
            addButton(button(go, true, v -> startBattle()));
            if (kind == KIND_ONLINE && g != null) {
                hint.setText(formatString(R.string.AvraSeaInviteFrom, opp) + "\n" + getString(R.string.AvraSeaPlaceHint));
                addButton(button(getString(R.string.AvraChessDecline), false, v -> {
                    SeaNet.decline(g);
                    finishFragment();
                }));
            }
            return;
        }

        setEnemyVisible(true);
        ownView.setMode(SeaView.MODE_OWN);
        ownView.setFleet(myFleet, true);
        enemyLabel.setText(formatString(R.string.AvraSeaEnemyWaters, opp));
        ownLabel.setText(getString(R.string.AvraSeaYourFleet));

        int[] enemyMarks, ownMarks;
        int pending = -1;
        boolean myTurn, over, won = false;
        String waitText = null;
        if (kind == KIND_BOT) {
            enemyMarks = SeaBoard.knowledge(myShots);
            ownMarks = SeaBoard.knowledge(botShots);
            myTurn = !botTurn && !botOver;
            over = botOver;
            won = botWinner > 0;
            enemyView.setFleet(botFleet, over);
        } else {
            enemyMarks = SeaBoard.knowledge(resolved(g.shots(g.me())));
            ownMarks = SeaBoard.knowledge(resolved(g.shots(g.them())));
            for (int[] s : g.shots(g.me())) if (s[1] == '?') pending = s[0];
            myTurn = g.myTurn();
            over = g.state == SeaNet.FINISHED || g.state == SeaNet.DECLINED;
            won = g.winner > 0;
            enemyView.setFleet(TextUtils.isEmpty(g.oppLayout) ? null : SeaBoard.decode(g.oppLayout), over);
            if (g.state == SeaNet.INVITE_OUT) waitText = getString(R.string.AvraSeaWaitAccept);
            else if (g.state == SeaNet.DECLINED) waitText = getString(R.string.AvraChessDeclined);
            else if (g.turn() == '?') waitText = pending >= 0 ? getString(R.string.AvraSeaWaitAnswer) : getString(R.string.AvraSeaTheirTurn);
            else if (!myTurn && !over) waitText = getString(R.string.AvraSeaTheirTurn);
        }
        enemyView.setMarks(enemyMarks, animate);
        ownView.setMarks(ownMarks, animate);
        enemyView.setPending(pending);
        enemyView.setActive(myTurn && !over && pending < 0);
        enemyFleetView.set(SeaBoard.sunkLengths(enemyMarks));
        ownFleetView.set(SeaBoard.sunkLengths(ownMarks));

        if (over && (kind == KIND_BOT || g.state == SeaNet.FINISHED)) {
            title.setText(won ? "🏆 " + getString(R.string.AvraSeaVictory) : "💀 " + getString(R.string.AvraSeaDefeat));
            title.setTextColor(won ? GOLD : RED);
            String sub = kind == KIND_BOT ? "" : "resign".equals(g.reason) ? getString(won ? R.string.AvraSeaTheyResigned : R.string.AvraSeaYouResigned) : "";
            if (kind == KIND_ONLINE) {
                String check = g.cheat ? "⚠️ " + getString(R.string.AvraSeaCheat)
                        : g.verified ? "✅ " + getString(R.string.AvraSeaVerified) : "⏳ " + getString(R.string.AvraSeaWaitReveal);
                sub = sub.isEmpty() ? check : sub + "\n" + check;
            }
            char me = kind == KIND_BOT ? 'x' : g.me();
            int[] acc = kind == KIND_BOT ? accuracy(myShots) : SeaNet.accuracy(g, me);
            subtitle.setText((sub.isEmpty() ? "" : sub + "\n") + formatString(R.string.AvraSeaCardStats, acc[0], acc[2]));
            hint.setText("");
            addButton(button("🔁 " + getString(R.string.AvraSeaRematch), true, v -> {
                if (kind == KIND_BOT) presentFragment(bot(level), true);
                else presentFragment(challenge(g.peer), true);
            }));
            addButton(button(getString(R.string.AvraSeaToLobby), false, v -> finishFragment()));
        } else {
            title.setTextColor(TEXT);
            if (waitText != null) {
                title.setText(waitText);
                subtitle.setText(kind == KIND_ONLINE && g.state == SeaNet.INVITE_OUT ? getString(R.string.AvraSeaWaitAcceptInfo) : getString(R.string.AvraSeaTheirTurnInfo));
            } else {
                title.setText("🎯 " + getString(R.string.AvraSeaYourTurn));
                subtitle.setText(getString(R.string.AvraSeaYourTurnInfo));
            }
            hint.setText("");
            if (kind == KIND_ONLINE && g.state == SeaNet.INVITE_OUT) {
                addButton(button(getString(R.string.AvraChessCancelInvite), false, v -> {
                    SeaNet.decline(g);
                    finishFragment();
                }));
            } else {
                addButton(button("🏳 " + getString(R.string.AvraSeaResign), false, v -> confirmResign()));
            }
        }

        // banners for the newest answered shot
        if (kind == KIND_ONLINE) {
            ArrayList<int[]> ms = resolved(g.shots(g.me())), ts = resolved(g.shots(g.them()));
            if (animate && lastMyShots >= 0) {
                if (ms.size() > lastMyShots) bannerFor((char) ms.get(ms.size() - 1)[1], true);
                else if (ts.size() > lastTheirShots) bannerFor((char) ts.get(ts.size() - 1)[1], false);
                if (myTurn && !over && pending < 0 && (ms.size() > lastMyShots || ts.size() > lastTheirShots)
                        && (ts.size() > lastTheirShots)) SeaSound.play(SeaSound.TURN);
            }
            lastMyShots = ms.size();
            lastTheirShots = ts.size();
        }
    }

    private static ArrayList<int[]> resolved(ArrayList<int[]> shots) {
        ArrayList<int[]> out = new ArrayList<>();
        for (int[] s : shots) if (s[1] != '?') out.add(s);
        return out;
    }

    private static int[] accuracy(ArrayList<int[]> shots) {
        int hits = 0;
        for (int[] s : shots) if (s[1] != 'm') hits++;
        return new int[]{shots.size(), hits, shots.isEmpty() ? 0 : hits * 100 / shots.size()};
    }

    private void bannerFor(char r, boolean mine) {
        if (r == 'm') showBanner(mine ? "💧 " + getString(R.string.AvraSeaMiss) : "😮‍💨 " + getString(R.string.AvraSeaTheyMissed), 0xFFB3E5FC);
        else if (r == 'k') showBanner(mine ? "🔥 " + getString(R.string.AvraSeaKill) : "💀 " + getString(R.string.AvraSeaTheyKilled), mine ? GOLD : RED);
        else if (r == 'h') showBanner(mine ? "💥 " + getString(R.string.AvraSeaHit) : "💢 " + getString(R.string.AvraSeaTheyHit), mine ? 0xFFFFAB40 : RED);
    }

    private void setEnemyVisible(boolean v) {
        int vis = v ? View.VISIBLE : View.GONE;
        enemyLabel.setVisibility(vis);
        enemyFrame.setVisibility(vis);
        enemyFleetView.setVisibility(vis);
    }

    private void confirmResign() {
        new AlertDialog.Builder(getContext()).setTitle(getString(R.string.AvraSeaResignTitle))
                .setMessage(getString(R.string.AvraSeaResignInfo))
                .setPositiveButton(getString(R.string.AvraSeaResign), (d, w) -> {
                    if (kind == KIND_BOT) {
                        botOver = true;
                        botWinner = -1;
                        SeaNet.addResult("bot" + level, false);
                        SeaSound.play(SeaSound.LOSE);
                        refresh(false);
                    } else {
                        SeaNet.Game g = game();
                        if (g != null) SeaNet.resign(g);
                    }
                })
                .setNegativeButton(getString(R.string.Cancel), null).show();
    }

    // ---- actions ------------------------------------------------------------------------------

    private void startBattle() {
        if (!myFleet.valid()) {
            showBanner("⚠️", RED);
            return;
        }
        if (kind == KIND_BOT) {
            placing = false;
            botFleet = SeaBoard.random(new Random());
            SeaSound.play(SeaSound.START);
            refresh(false);
        } else if (kind == KIND_CHALLENGE) {
            SeaNet.Game g = SeaNet.invite(currentAccount, peer, myFleet);
            presentFragment(online(g.id), true);
        } else {
            SeaNet.Game g = game();
            if (g == null) return;
            placing = false;
            SeaNet.accept(g, myFleet);
        }
    }

    private void fire(int cell) {
        if (kind == KIND_ONLINE) {
            SeaNet.Game g = game();
            if (g == null) return;
            SeaSound.play(SeaSound.SHOT);
            SeaNet.fire(g, cell);
            return;
        }
        if (botTurn || botOver) return;
        for (int[] s : myShots) if (s[0] == cell) return;
        boolean[] hit = new boolean[SeaBoard.CELLS];
        for (int[] s : myShots) if (s[1] != 'm') hit[s[0]] = true;
        char r = botFleet.resolve(cell, hit);
        myShots.add(new int[]{cell, r});
        SeaSound.play(r == 'm' ? SeaSound.SPLASH : r == 'k' ? SeaSound.SUNK : SeaSound.HIT);
        bannerFor(r, true);
        int kills = 0;
        for (int[] s : myShots) if (s[1] == 'k') kills++;
        if (kills >= SeaBoard.FLEET.length) {
            botOver = true;
            botWinner = 1;
            SeaNet.addResult("bot" + level, true);
            AndroidUtilities.runOnUIThread(() -> SeaSound.play(SeaSound.WIN), 900);
        } else if (r == 'm') {
            botTurn = true;
            AndroidUtilities.runOnUIThread(this::botShoot, 900);
        }
        refresh(true);
    }

    private void botShoot() {
        if (fragmentView == null || botOver) return;
        int[] k = SeaBoard.knowledge(botShots);
        int cell = SeaAi.pick(level, k, SeaBoard.sunkLengths(k));
        if (cell < 0) return;
        boolean[] hit = new boolean[SeaBoard.CELLS];
        for (int[] s : botShots) if (s[1] != 'm') hit[s[0]] = true;
        char r = myFleet.resolve(cell, hit);
        botShots.add(new int[]{cell, r});
        SeaSound.play(r == 'm' ? SeaSound.SPLASH : r == 'k' ? SeaSound.SUNK : SeaSound.HIT);
        bannerFor(r, false);
        int kills = 0;
        for (int[] s : botShots) if (s[1] == 'k') kills++;
        if (kills >= SeaBoard.FLEET.length) {
            botOver = true;
            botWinner = -1;
            SeaNet.addResult("bot" + level, false);
            AndroidUtilities.runOnUIThread(() -> SeaSound.play(SeaSound.LOSE), 900);
        } else if (r == 'm') {
            botTurn = false;
            AndroidUtilities.runOnUIThread(() -> SeaSound.play(SeaSound.TURN), 500);
        } else {
            AndroidUtilities.runOnUIThread(this::botShoot, 1000);
        }
        refresh(true);
    }

    @Override
    public void onSeaUpdate(SeaNet.Game g, boolean incoming) {
        if (kind != KIND_ONLINE || g == null || !g.id.equals(gameId)) return;
        if (g.state != SeaNet.INVITE_IN) placing = false;
        if (!TextUtils.isEmpty(g.myLayout)) myFleet = SeaBoard.decode(g.myLayout);
        refresh(true);
    }

    /** A row of the fleet: every ship as a little hull, crossed out when sunk. */
    static class FleetView extends View {
        private ArrayList<Integer> sunk = new ArrayList<>();
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        FleetView(Context c) {
            super(c);
        }

        void set(ArrayList<Integer> s) {
            sunk = new ArrayList<>(s);
            invalidate();
        }

        @Override
        protected void onDraw(Canvas c) {
            int total = 0;
            for (int l : SeaBoard.FLEET) total += l;
            float gap = dp(6), unit = (getWidth() - gap * (SeaBoard.FLEET.length - 1)) / total;
            unit = Math.min(unit, dp(14));
            float x = (getWidth() - (unit * total + gap * (SeaBoard.FLEET.length - 1))) / 2, h = getHeight() * 0.55f, y = (getHeight() - h) / 2;
            ArrayList<Integer> left = new ArrayList<>(sunk);
            for (int len : SeaBoard.FLEET) {
                boolean dead = left.remove(Integer.valueOf(len));
                float w = unit * len - dp(2);
                p.setStyle(Paint.Style.FILL);
                p.setColor(dead ? 0xFF3E2723 : 0xFF90A4AE);
                android.graphics.RectF r = new android.graphics.RectF(x, y, x + w, y + h);
                c.drawRoundRect(r, h / 2, h / 2, p);
                if (dead) {
                    p.setColor(RED);
                    p.setStrokeWidth(dp(2));
                    c.drawLine(x + dp(2), y + h / 2, x + w - dp(2), y + h / 2, p);
                }
                x += unit * len + gap;
            }
        }
    }
}
