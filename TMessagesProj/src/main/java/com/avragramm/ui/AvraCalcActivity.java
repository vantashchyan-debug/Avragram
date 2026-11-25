package com.avragramm.ui;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.graphics.ColorUtils;

import com.avragramm.AvraCalc;
import com.avragramm.games.AvraGameView;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.NotificationCenter;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.ActionBar;
import org.telegram.ui.ActionBar.ActionBarMenu;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.BaseFragment;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.text.DecimalFormatSymbols;
import java.util.ArrayList;
import java.util.Arrays;

import static org.telegram.messenger.LocaleController.getString;

/**
 * Engineering calculator of the "Fun" section. Same evaluator as /calc"…" (AvraCalc).
 * Scientific keys: brackets, memory (MC M+ M− MR), x! x² x³ xʸ eˣ π, 1/x √ ∛ ʸ√x, Inv (inverse
 * functions), Deg/Rad, e ln lg sin cos tan. The live result is shown under the expression.
 * Colours, font, key corners and animations follow the active theme, font and animation settings,
 * and are re-applied on a theme switch. History (last 30) is kept on the device.
 */
public class AvraCalcActivity extends BaseFragment implements NotificationCenter.NotificationCenterDelegate {

    private static final int MENU_HISTORY = 1;
    private static final int K_DIGIT = 0, K_OP = 1, K_FN = 2, K_EQ = 3, K_CLEAR = 4;
    private static final String PREFS = "avragramm";

    private final StringBuilder expr = new StringBuilder();
    private boolean degrees = true, inverse, justEvaluated;
    private double memory;
    private char sep = '.';

    private FrameLayout root;
    private TextView exprView, resultView, modeView;
    private HorizontalScrollView exprScroll;
    private final ArrayList<TextView> keys = new ArrayList<>();
    private TextView invKey, degKey, sinKey, cosKey, tanKey, lnKey, lgKey;

    @Override
    public boolean onFragmentCreate() {
        NotificationCenter.getGlobalInstance().addObserver(this, NotificationCenter.didSetNewTheme);
        SharedPreferences p = prefs();
        degrees = p.getBoolean("calc_deg", true);
        memory = Double.longBitsToDouble(p.getLong("calc_mem", Double.doubleToLongBits(0)));
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

    private static SharedPreferences prefs() {
        return ApplicationLoader.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    @Override
    public View createView(Context context) {
        DecimalFormatSymbols dfs = DecimalFormatSymbols.getInstance(LocaleController.getInstance().getCurrentLocale());
        sep = dfs.getDecimalSeparator() == ',' ? ',' : '.';

        actionBar.setBackButtonImage(R.drawable.ic_ab_back);
        actionBar.setAllowOverlayTitle(true);
        actionBar.setTitle(getString(R.string.AvraCalc));
        actionBar.setActionBarMenuOnItemClick(new ActionBar.ActionBarMenuOnItemClick() {
            @Override
            public void onItemClick(int id) {
                if (id == -1) finishFragment();
                else if (id == MENU_HISTORY) showHistory();
            }
        });
        ActionBarMenu menu = actionBar.createMenu();
        menu.addItem(MENU_HISTORY, R.drawable.msg_recent).setContentDescription(getString(R.string.AvraCalcHistory));

        root = new FrameLayout(context);
        LinearLayout column = new LinearLayout(context);
        column.setOrientation(LinearLayout.VERTICAL);
        root.addView(column, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT));

        // display
        LinearLayout display = new LinearLayout(context);
        display.setOrientation(LinearLayout.VERTICAL);
        display.setGravity(Gravity.BOTTOM | Gravity.END);
        display.setPadding(AndroidUtilities.dp(20), AndroidUtilities.dp(8), AndroidUtilities.dp(20), AndroidUtilities.dp(12));
        modeView = AvraFunUi.text(context, 12, true, true);
        modeView.setGravity(Gravity.START);
        display.addView(modeView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT));
        exprScroll = new HorizontalScrollView(context);
        exprScroll.setHorizontalScrollBarEnabled(false);
        exprScroll.setFillViewport(true);
        exprView = AvraFunUi.text(context, 48, false, false);
        exprView.setGravity(Gravity.END | Gravity.BOTTOM);
        exprView.setSingleLine(true);
        exprScroll.addView(exprView, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT, FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.END));
        display.addView(exprScroll, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 12, 0, 0));
        resultView = AvraFunUi.text(context, 28, false, true);
        resultView.setGravity(Gravity.END);
        resultView.setSingleLine(true);
        resultView.setEllipsize(TextUtils.TruncateAt.START);
        display.addView(resultView, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, LayoutHelper.WRAP_CONTENT, 0, 4, 0, 0));
        column.addView(display, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1f));

        // keypad
        LinearLayout pad = new LinearLayout(context);
        pad.setOrientation(LinearLayout.VERTICAL);
        pad.setPadding(AndroidUtilities.dp(10), 0, AndroidUtilities.dp(10), AndroidUtilities.dp(12));
        String[][] sci = {
                {"(", ")", "MC", "M+", "M−", "MR"},
                {"x!", "x²", "x³", "xʸ", "eˣ", "π"},
                {"1/x", "√", "∛", "ʸ√x", "Inv", "Deg"},
                {"e", "ln", "lg", "sin", "cos", "tan"},
        };
        String[][] basic = {
                {"C", "×", "÷", "⌫"},
                {"7", "8", "9", "−"},
                {"4", "5", "6", "+"},
                {"1", "2", "3", "%"},
                {"±", "0", String.valueOf(sep), "="},
        };
        for (String[] row : sci) pad.addView(row(context, row, true), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 0.82f));
        for (String[] row : basic) pad.addView(row(context, row, false), LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 1.05f));
        column.addView(pad, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 0, 2.6f));

        fragmentView = root;
        refresh(false);
        applyColors();
        return root;
    }

    private LinearLayout row(Context context, String[] labels, boolean small) {
        LinearLayout r = new LinearLayout(context);
        r.setOrientation(LinearLayout.HORIZONTAL);
        for (String label : labels) {
            int kind = kindOf(label);
            TextView k = AvraFunUi.button(context, label, AvraFunUi.SECONDARY);
            k.setTag(kind);
            k.setTextSize(TypedValue.COMPLEX_UNIT_DIP, small ? 16 : 26);
            k.setPadding(0, 0, 0, 0);
            k.setOnClickListener(v -> onKey(label));
            if (label.equals("Inv")) invKey = k;
            else if (label.equals("Deg")) degKey = k;
            else if (label.equals("sin")) sinKey = k;
            else if (label.equals("cos")) cosKey = k;
            else if (label.equals("tan")) tanKey = k;
            else if (label.equals("ln")) lnKey = k;
            else if (label.equals("lg")) lgKey = k;
            keys.add(k);
            r.addView(k, LayoutHelper.createLinear(0, LayoutHelper.MATCH_PARENT, 1f, 4, 4, 4, 4));
        }
        return r;
    }

    private int kindOf(String label) {
        if ("=".equals(label)) return K_EQ;
        if ("C".equals(label)) return K_CLEAR;
        if (label.length() == 1 && (Character.isDigit(label.charAt(0)) || label.charAt(0) == sep)) return K_DIGIT;
        if (Arrays.asList("×", "÷", "−", "+", "%", "⌫", "±").contains(label)) return K_OP;
        return K_FN;
    }

    // ---- input ------------------------------------------------------------------------------

    private void onKey(String k) {
        if (justEvaluated) {
            // after "=" a digit starts a new expression, an operator continues with the result
            boolean continues = Arrays.asList("×", "÷", "−", "+", "%", "x!", "x²", "x³", "xʸ", "⌫").contains(k);
            if (!continues && kindOf(k) == K_DIGIT) expr.setLength(0);
            justEvaluated = false;
        }
        switch (k) {
            case "C": expr.setLength(0); break;
            case "⌫": backspace(); break;
            case "=": evaluate(); return;
            case "×": append("×"); break;
            case "÷": append("÷"); break;
            case "−": append("−"); break;
            case "+": append("+"); break;
            case "%": append("%"); break;
            case "±": toggleSign(); break;
            case "(": append("("); break;
            case ")": append(")"); break;
            case "x!": append("!"); break;
            case "x²": append("²"); break;
            case "x³": append("³"); break;
            case "xʸ": append("^"); break;
            case "eˣ": append("e^("); break;
            case "π": append("π"); break;
            case "e": append("e"); break;
            case "1/x":
                if (expr.length() == 0) append("1/");
                else {
                    expr.insert(0, "1/(");
                    expr.append(')');
                }
                break;
            case "√": append("√"); break;
            case "∛": append("∛"); break;
            case "ʸ√x": append("^(1/"); break;
            case "Inv":
                inverse = !inverse;
                updateFnLabels();
                applyColors();
                return;
            case "Deg":
                degrees = !degrees;
                prefs().edit().putBoolean("calc_deg", degrees).apply();
                updateFnLabels();
                break;
            case "sin": append(inverse ? "asin(" : "sin("); break;
            case "cos": append(inverse ? "acos(" : "cos("); break;
            case "tan": append(inverse ? "atan(" : "tan("); break;
            case "ln": append(inverse ? "e^(" : "ln("); break;
            case "lg": append(inverse ? "10^(" : "lg("); break;
            case "MC": memory = 0; saveMemory(); break;
            case "M+": memory += currentValue(); saveMemory(); break;
            case "M−": memory -= currentValue(); saveMemory(); break;
            case "MR": append(AvraCalc.format(memory, sep)); break;
            default: append(k); // digits and the decimal separator
        }
        refresh(true);
    }

    private void append(String s) {
        if (expr.length() > 300) return;
        expr.append(s);
    }

    private void backspace() {
        if (expr.length() == 0) return;
        String[] multi = {"asin(", "acos(", "atan(", "sin(", "cos(", "tan(", "ln(", "lg(", "e^(", "10^(", "^(1/"};
        String s = expr.toString();
        for (String m : multi) {
            if (s.endsWith(m)) {
                expr.setLength(s.length() - m.length());
                return;
            }
        }
        expr.setLength(s.length() - 1);
    }

    /** ± on the last number of the expression. */
    private void toggleSign() {
        int i = expr.length();
        while (i > 0 && (Character.isDigit(expr.charAt(i - 1)) || expr.charAt(i - 1) == sep || expr.charAt(i - 1) == '.')) i--;
        if (i > 0 && expr.charAt(i - 1) == '−' && (i == 1 || "(×÷+−^".indexOf(expr.charAt(i - 2)) >= 0)) {
            expr.deleteCharAt(i - 1);
        } else {
            expr.insert(i, '−');
        }
    }

    private double currentValue() {
        try {
            return AvraCalc.eval(expr.toString(), degrees);
        } catch (AvraCalc.CalcException e) {
            return 0;
        }
    }

    private void saveMemory() {
        prefs().edit().putLong("calc_mem", Double.doubleToLongBits(memory)).apply();
    }

    private void evaluate() {
        if (expr.length() == 0) return;
        String src = expr.toString();
        try {
            double v = AvraCalc.eval(src, degrees);
            String res = AvraCalc.format(v, sep);
            addHistory(src + " = " + res);
            // the result flies up into the expression line
            resultView.animate().cancel();
            long d = AvraFunUi.duration(200);
            resultView.animate().translationY(-AndroidUtilities.dp(24)).alpha(0f).setDuration(d).withEndAction(() -> {
                expr.setLength(0);
                expr.append(res);
                justEvaluated = true;
                resultView.setTranslationY(0);
                resultView.setAlpha(1f);
                refresh(false);
            }).start();
        } catch (AvraCalc.CalcException e) {
            resultView.setText(getString(R.string.AvraCalcError));
            resultView.setTextColor(Theme.getColor(Theme.key_text_RedBold));
            if (AvraFunUi.duration(100) > 0) AndroidUtilities.shakeViewSpring(exprView, 4);
        }
    }

    private void refresh(boolean animate) {
        exprView.setText(expr.length() == 0 ? "0" : expr.toString());
        modeView.setText((degrees ? "DEG" : "RAD") + (inverse ? " · INV" : "") + (memory != 0 ? " · M" : ""));
        resultView.setTextColor(AvraFunUi.grayColor());
        String preview = "";
        if (expr.length() > 0 && !justEvaluated) {
            try {
                preview = AvraCalc.format(AvraCalc.eval(expr.toString(), degrees), sep);
                if (preview.equals(expr.toString())) preview = "";
            } catch (AvraCalc.CalcException ignored) {
            }
        }
        resultView.setText(preview);
        exprScroll.post(() -> exprScroll.fullScroll(View.FOCUS_RIGHT));
        // shrink long expressions
        int len = exprView.getText().length();
        exprView.setTextSize(TypedValue.COMPLEX_UNIT_DIP, len <= 9 ? 48 : len <= 14 ? 38 : 30);
    }

    private void updateFnLabels() {
        if (sinKey == null) return;
        sinKey.setText(inverse ? "asin" : "sin");
        cosKey.setText(inverse ? "acos" : "cos");
        tanKey.setText(inverse ? "atan" : "tan");
        lnKey.setText(inverse ? "eˣ" : "ln");
        lgKey.setText(inverse ? "10ˣ" : "lg");
        degKey.setText(degrees ? "Deg" : "Rad");
    }

    // ---- history ----------------------------------------------------------------------------

    private void addHistory(String line) {
        ArrayList<String> h = history();
        h.add(0, line);
        while (h.size() > 30) h.remove(h.size() - 1);
        prefs().edit().putString("calc_history", TextUtils.join("\n", h)).apply();
    }

    private ArrayList<String> history() {
        String s = prefs().getString("calc_history", "");
        ArrayList<String> out = new ArrayList<>();
        if (!TextUtils.isEmpty(s)) out.addAll(Arrays.asList(s.split("\n")));
        return out;
    }

    private void showHistory() {
        if (getParentActivity() == null) return;
        ArrayList<String> h = history();
        AlertDialog.Builder b = new AlertDialog.Builder(getParentActivity(), getResourceProvider());
        b.setTitle(getString(R.string.AvraCalcHistory));
        if (h.isEmpty()) {
            b.setMessage(getString(R.string.AvraCalcHistoryEmpty));
        } else {
            CharSequence[] items = h.toArray(new CharSequence[0]);
            b.setItems(items, (d, which) -> {
                String line = h.get(which);
                int eq = line.lastIndexOf(" = ");
                expr.setLength(0);
                expr.append(eq > 0 ? line.substring(eq + 3) : line);
                justEvaluated = true;
                refresh(true);
            });
            b.setNeutralButton(getString(R.string.AvraCalcHistoryClear), (d, w) -> prefs().edit().remove("calc_history").apply());
        }
        b.setPositiveButton(getString(R.string.Close), null);
        showDialog(b.create());
    }

    // ---- theme ------------------------------------------------------------------------------

    private void applyColors() {
        if (root == null) return;
        int bg = AvraFunUi.bgColor();
        root.setBackgroundColor(bg);
        int card = AvraFunUi.cardColor(), text = AvraFunUi.textColor(), accent = AvraFunUi.accentColor();
        int opBg = ColorUtils.blendARGB(card, text, 0.12f);
        int fnBg = ColorUtils.blendARGB(card, bg, 0.35f);
        for (TextView k : keys) {
            Object tag = k.getTag();
            int kind = tag instanceof Integer ? (Integer) tag : K_FN;
            int kb, kf;
            switch (kind) {
                case K_EQ: kb = accent; kf = AvraFunUi.accentTextColor(); break;
                case K_CLEAR: kb = opBg; kf = Theme.getColor(Theme.key_text_RedBold); break;
                case K_OP: kb = opBg; kf = text; break;
                case K_DIGIT: kb = card; kf = text; break;
                default: kb = fnBg; kf = text;
            }
            if ((k == invKey && inverse) || (k == degKey && !degrees)) {
                kb = ColorUtils.blendARGB(card, accent, 0.4f);
            }
            float h = k.getHeight() > 0 ? k.getHeight() : AndroidUtilities.dp(56);
            int radius = (int) Math.min(h / 2f, AvraFunUi.radius());
            k.setBackground(Theme.createSimpleSelectorRoundRectDrawable(radius, kb, ColorUtils.blendARGB(kb, kf, 0.18f)));
            k.setTextColor(kf);
            k.setTypeface(kind == K_DIGIT ? AvraGameView.regularFont() : AndroidUtilities.bold());
        }
        exprView.setTextColor(text);
        exprView.setTypeface(AvraGameView.regularFont());
        resultView.setTypeface(AvraGameView.regularFont());
        AvraFunUi.restyleText(resultView);
        AvraFunUi.restyleText(modeView);
        updateFnLabels();
        // radii depend on the real key height: re-apply once laid out
        if (!keys.isEmpty() && keys.get(0).getHeight() == 0) keys.get(0).post(this::applyColors);
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
