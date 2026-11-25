package com.avragramm.ui;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.avragramm.AvraGames;
import com.avragramm.games.Avra48View;
import com.avragramm.games.AvraBirdView;
import com.avragramm.games.AvraBlastView;
import com.avragramm.games.AvraDropView;
import com.avragramm.games.AvraGameView;
import com.avragramm.games.AvraRunView;
import com.avragramm.games.AvraSnakeView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.ActionBarMenuItem;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.ArrayList;

import static org.telegram.messenger.LocaleController.formatString;
import static org.telegram.messenger.LocaleController.getString;

/**
 * Shell of every mini-game: score bar, the game view and themed panels on top of it —
 * start (Start / Exit) → difficulty → playing ⇄ pause → result (sent to the chat with the chat record).
 * Colours/font/corners come from the active theme and layout and are re-applied on
 * NotificationCenter.didSetNewTheme, so a theme switch never restarts the round.
 */
public class AvraGameActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int STATE_MENU = 0, STATE_PICK = 1, STATE_PLAYING = 2, STATE_PAUSED = 3, STATE_OVER = 4, STATE_WIN = 5;
    private static final int MENU_PAUSE = 1;

    private final int game;
    private final long dialogId;
    private int difficulty = AvraGames.NORMAL;
    private int state = STATE_MENU;
    private int lastScore;
    private boolean continuedAfterWin;

    private FrameLayout root;
    private LinearLayout hud;
    private TextView scoreView, bestView, scoreLabel, bestLabel;
    private AvraGameView gameView;
    private FrameLayout dim;
    private LinearLayout panel;
    private ActionBarMenuItem pauseItem;
    private final ArrayList<TextView> buttons = new ArrayList<>();
    private final ArrayList<TextView> texts = new ArrayList<>();

    public AvraGameActivity(int game, long dialogId) {
        this.game = Math.max(0, Math.min(AvraGames.IDS.length - 1, game));
        this.dialogId = dialogId;
    }

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        return super.onFragmentCreate();
    }

    @Override
    public void onFragmentDestroy() {
        NotificationCenter.getGlobalInstance().removeObserver(this, NotificationCenter.didSetNewTheme);
        keepScreenOn(false);
        if (gameView != null) {
            gameView.stop();
            gameView.setListener(null);
        }
        super.onFragmentDestroy();
    }

    @Override
    public void didReceivedNotification(int id, int account, Object... args) {
        if (id == NotificationCenter.didSetNewTheme) applyColors();
    }

    private AvraGameView createGame(Context context) {
        switch (AvraGames.IDS[game]) {
            case "avra48": return new Avra48View(context);
            case "avrasnake": return new AvraSnakeView(context);
            case "avrabird": return new AvraBirdView(context);
            case "avradrop": return new AvraDropView(context);
            case "avrarun": return new AvraRunView(context);
            default: return new AvraBlastView(context);
        }
    }

    @Override
    public View createView(Context context) {
        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(AvraGames.TITLES[game]);
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) {
                    if (onBackPressed(true)) finishFragment();
                } else if (id == MENU_PAUSE) {
                    pauseGame();
                }
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        pauseItem = menu.addItem(MENU_PAUSE, R.drawable.ic_action_pause);
        pauseItem.setContentDescription(getString(R.string.AvraGamePause));
        pauseItem.setVisibility(View.GONE);

        root = new FrameLayout(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        hud = new LinearLayout(context);
        hud.setOrientation(LinearLayout.HORIZONTAL);
        hud.setPadding(AndroidUtilities.dp(16), AndroidUtilities.dp(10), AndroidUtilities.dp(16), AndroidUtilities.dp(6));
        LinearLayout scoreBox = stat(context, true);
        LinearLayout bestBox = stat(context, false);
        hud.addView(scoreBox, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 0, 0, 6, 0));
        hud.addView(bestBox, LayoutHelper.createLinear(0, LayoutHelper.WRAP_CONTENT, 1f, 6, 0, 0, 0));
        column.addView(hud, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));

        gameView = createGame(context);
        gameView.setListener(new AvraGameView.Listener() {
            @Override
            public void onScore(int score) {
                if (scoreView != null) scoreView.setText(String.valueOf(score));
            }

            @Override
            public void onGameOver(int score) {
                onRoundOver(score);
            }

            @Override
            public void onWin(int score) {
                if (!continuedAfterWin && state == STATE_PLAYING) {
                    lastScore = score;
                    setState(STATE_WIN);
                }
            }
        });
        column.addView(gameView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        dim = new FrameLayout(context);
        dim.setClickable(true);
        root.addView(dim, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));
        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setGravity(Gravity.CENTER_HORIZONTAL);
        panel.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(22), AndroidUtilities.dp(20), AndroidUtilities.dp(20));
        panel.setClickable(true);
        dim.addView(panel, LayoutHelper.createFrame(340, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 20, 0, 20, 0));

        fragmentView = root;
        updateBest();
        scoreView.setText("0");
        setState(STATE_MENU);
        applyColors();
        return root;
    }

    private LinearLayout stat(Context context, boolean score) {
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(0, AndroidUtilities.dp(6), 0, AndroidUtilities.dp(6));
        TextView label = AvraFunUi.text(context, 12, false, true);
        label.setText(getString(score ? R.string.AvraGameScore : R.string.AvraGameBest));
        TextView value = AvraFunUi.text(context, 22, true, false);
        box.addView(label, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        box.addView(value, LayoutHelper.createLinear(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT));
        if (score) {
            scoreLabel = label;
            scoreView = value;
        } else {
            bestLabel = label;
            bestView = value;
        }
        box.setTag("statbox");
        return box;
    }

    private void updateBest() {
        if (bestView == null) return;
        int b = state == STATE_MENU ? AvraGames.bestAny(game) : AvraGames.best(game, difficulty);
        bestView.setText(String.valueOf(b));
    }

    // ---- panels -----------------------------------------------------------------------------

    private void setState(int s) {
        state = s;
        boolean playing = s == STATE_PLAYING;
        if (pauseItem != null) pauseItem.setVisibility(playing ? View.VISIBLE : View.GONE);
        keepScreenOn(playing);
        if (playing) {
            AvraFunUi.show(dim, false);
            return;
        }
        buildPanel();
        AvraFunUi.show(dim, true);
        panel.setAlpha(0f);
        panel.setTranslationY(AndroidUtilities.dp(16));
        panel.animate().alpha(1f).translationY(0).setDuration(AvraFunUi.duration(240)).start();
        applyColors();
    }

    private void buildPanel() {
        Context context = panel.getContext();
        panel.removeAllViews();
        buttons.clear();
        texts.clear();
        String emoji = AvraGames.EMOJI[game];
        switch (state) {
            case STATE_MENU: {
                addText(context, emoji, 44, false, false, 0);
                addText(context, AvraGames.TITLES[game], 24, true, false, 6);
                addText(context, getString(AvraGames.DESCRIPTIONS[game]), 14, false, true, 6);
                int best = AvraGames.bestAny(game);
                if (best > 0) addText(context, formatString(R.string.AvraGameBestValue, best), 14, true, false, 10);
                addButton(context, getString(R.string.AvraGameStart), AvraFunUi.PRIMARY, 18, v -> setState(STATE_PICK));
                addButton(context, getString(R.string.AvraGameExit), AvraFunUi.SECONDARY, 10, v -> finishFragment());
                break;
            }
            case STATE_PICK: {
                addText(context, getString(R.string.AvraDifficulty), 22, true, false, 0);
                addText(context, getString(R.string.AvraDifficultyInfo), 14, false, true, 6);
                for (int d = AvraGames.EASY; d <= AvraGames.HARD; d++) {
                    final int dd = d;
                    int best = AvraGames.best(game, d);
                    String label = AvraGames.stars(d) + "  " + getString(AvraGames.DIFFICULTY_TITLES[d])
                            + (best > 0 ? "  ·  " + formatString(R.string.AvraGameBestShort, best) : "");
                    addButton(context, label, d == AvraGames.NORMAL ? AvraFunUi.PRIMARY : AvraFunUi.SECONDARY, d == AvraGames.EASY ? 18 : 10, v -> startRound(dd));
                }
                addButton(context, getString(R.string.AvraGameBack), AvraFunUi.CARD, 10, v -> setState(STATE_MENU));
                break;
            }
            case STATE_PAUSED: {
                addText(context, "⏸", 40, false, false, 0);
                addText(context, getString(R.string.AvraGamePaused), 22, true, false, 6);
                addText(context, formatString(R.string.AvraGameScoreValue, gameView.getScore()), 15, false, true, 6);
                addButton(context, getString(R.string.AvraGameResume), AvraFunUi.PRIMARY, 18, v -> {
                    setState(STATE_PLAYING);
                    gameView.resume();
                });
                addButton(context, getString(R.string.AvraGameRestart), AvraFunUi.SECONDARY, 10, v -> startRound(difficulty));
                addButton(context, getString(R.string.AvraGameExit), AvraFunUi.DANGER, 10, v -> finishFragment());
                break;
            }
            case STATE_WIN: {
                addText(context, "🎉", 44, false, false, 0);
                addText(context, getString(R.string.AvraGame48Win), 22, true, false, 6);
                addText(context, formatString(R.string.AvraGameScoreValue, lastScore), 15, false, true, 6);
                addButton(context, getString(R.string.AvraGameContinue), AvraFunUi.PRIMARY, 18, v -> {
                    continuedAfterWin = true;
                    setState(STATE_PLAYING);
                    gameView.resume();
                });
                addButton(context, getString(R.string.AvraGameFinish), AvraFunUi.SECONDARY, 10, v -> {
                    int s = gameView.getScore();
                    gameView.stop();
                    onRoundOver(s);
                });
                break;
            }
            case STATE_OVER: {
                addText(context, emoji, 40, false, false, 0);
                addText(context, getString(R.string.AvraGameOver), 22, true, false, 6);
                addText(context, String.valueOf(lastScore), 40, true, false, 4);
                TextView recordNote = addText(context, "", 14, true, false, 2);
                recordNote.setTag("accent");
                recordNote.setVisibility(View.GONE);
                TextView chatNote = addText(context, "", 13, false, true, 8);
                chatNote.setVisibility(View.GONE);
                panel.setTag(new TextView[]{recordNote, chatNote});
                addButton(context, getString(R.string.AvraGameAgain), AvraFunUi.PRIMARY, 18, v -> startRound(difficulty));
                addButton(context, getString(R.string.AvraDifficulty), AvraFunUi.SECONDARY, 10, v -> setState(STATE_PICK));
                addButton(context, getString(R.string.AvraGameExit), AvraFunUi.CARD, 10, v -> finishFragment());
                break;
            }
        }
    }

    private TextView addText(Context context, String s, float size, boolean bold, boolean gray, int topDp) {
        TextView t = AvraFunUi.text(context, size, bold, gray);
        t.setText(s);
        panel.addView(t, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, topDp, 0, 0));
        texts.add(t);
        return t;
    }

    private void addButton(Context context, String s, int kind, int topDp, View.OnClickListener l) {
        TextView b = AvraFunUi.button(context, s, kind);
        b.setOnClickListener(l);
        panel.addView(b, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 50, 0, topDp, 0, 0));
        buttons.add(b);
    }

    // ---- round ------------------------------------------------------------------------------

    private void startRound(int d) {
        difficulty = d;
        continuedAfterWin = false;
        setState(STATE_PLAYING);
        updateBest();
        gameView.start(d);
    }

    private void pauseGame() {
        if (state != STATE_PLAYING) return;
        gameView.pause();
        setState(STATE_PAUSED);
    }

    private void onRoundOver(int score) {
        if (state == STATE_OVER || getParentActivity() == null) return;
        lastScore = score;
        boolean record = score > 0 && AvraGames.submit(game, difficulty, score);
        setState(STATE_OVER);
        updateBest();
        Object tag = panel.getTag();
        if (!(tag instanceof TextView[])) return;
        TextView recordNote = ((TextView[]) tag)[0], chatNote = ((TextView[]) tag)[1];
        if (record) {
            recordNote.setText(getString(R.string.AvraGameNewRecord));
            recordNote.setVisibility(View.VISIBLE);
        }
        if (dialogId != 0 && AvraGames.sendResults()) {
            chatNote.setText(getString(R.string.AvraGameSending));
            chatNote.setVisibility(View.VISIBLE);
            AvraGames.sendResult(currentAccount, dialogId, game, difficulty, score, (text, chatRecord) -> {
                if (chatNote.isAttachedToWindow()) {
                    chatNote.setText(TextUtils.isEmpty(chatRecord) ? getString(R.string.AvraGameSent)
                            : getString(R.string.AvraGameSent) + "\n" + chatRecord);
                }
            });
        }
        applyColors();
    }

    private void keepScreenOn(boolean on) {
        if (getParentActivity() == null) return;
        if (on) getParentActivity().getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getParentActivity().getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---- lifecycle --------------------------------------------------------------------------

    @Override
    public void onPause() {
        super.onPause();
        pauseGame(); // also covers leaving the app / losing focus
        keepScreenOn(false);
    }

    @Override
    public void onBecomeFullyHidden() {
        super.onBecomeFullyHidden();
        pauseGame();
    }

    @Override
    public boolean onBackPressed(boolean invoked) {
        if (state == STATE_PLAYING) {
            if (invoked) pauseGame();
            return false;
        }
        if (state == STATE_PICK) {
            if (invoked) setState(STATE_MENU);
            return false;
        }
        return super.onBackPressed(invoked);
    }

    // ---- theme ------------------------------------------------------------------------------

    private void applyColors() {
        if (root == null) return;
        int bg = AvraFunUi.bgColor();
        root.setBackgroundColor(bg);
        hud.setBackgroundColor(bg);
        float r = AvraFunUi.radius();
        for (int i = 0; i < hud.getChildCount(); i++) {
            hud.getChildAt(i).setBackground(AvraFunUi.rounded(AvraFunUi.cardColor(), Math.min(r, AndroidUtilities.dp(18))));
        }
        AvraFunUi.restyleText(scoreLabel);
        AvraFunUi.restyleText(bestLabel);
        AvraFunUi.restyleText(scoreView);
        AvraFunUi.restyleText(bestView);
        scoreView.setTypeface(AndroidUtilities.bold());
        bestView.setTypeface(AndroidUtilities.bold());
        dim.setBackgroundColor((bg & 0x00FFFFFF) | 0xB8000000);
        panel.setBackground(AvraFunUi.rounded(bg, Math.max(r, AndroidUtilities.dp(14))));
        panel.setElevation(AndroidUtilities.dp(6));
        for (TextView b : buttons) AvraFunUi.restyle(b);
        for (TextView t : texts) {
            if ("accent".equals(t.getTag())) t.setTextColor(AvraFunUi.accentColor());
            else AvraFunUi.restyleText(t);
        }
        gameView.invalidate();
        if (actionBar != null) {
            actionBar.setBackgroundColor(Theme.getColor(Theme.key_actionBarDefault));
            actionBar.setTitleColor(Theme.getColor(Theme.key_actionBarDefaultTitle));
            actionBar.setItemsColor(Theme.getColor(Theme.key_actionBarDefaultIcon), false);
            actionBar.setItemsBackgroundColor(Theme.getColor(Theme.key_actionBarDefaultSelector), false);
        }
    }

    @Override
    public boolean isLightStatusBar() {
        return AndroidUtilities.computePerceivedBrightness(Theme.getColor(Theme.key_actionBarDefault)) > 0.721f;
    }
}
