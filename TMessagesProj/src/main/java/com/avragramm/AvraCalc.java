package com.avragramm;

import android.text.SpannableStringBuilder;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Engineering expression evaluator shared by the /calc"…" message command and the calculator screen.
 *
 * Grammar (recursive descent, standard precedence):
 *   expr    := term (('+' | '-') term)*
 *   term    := unary (('*' | '/' | '×' | '÷' | implicit) unary)*
 *   unary   := ('-' | '+') unary | power
 *   power   := postfix ('^' unary)?            (right-associative)
 *   postfix := primary ('!' | '%' | '²' | '³')*
 *   primary := number | constant | function '(' expr ')' | function primary | '(' expr ')' | '√' unary | '∛' unary
 * Functions: sin cos tan asin acos atan sinh cosh tanh ln lg log log2 sqrt cbrt exp abs floor ceil round
 * Constants: pi π e. "," and "." are both decimal separators; spaces are ignored.
 */
public final class AvraCalc {

    public static final class CalcException extends Exception {
        public CalcException(String message) {
            super(message);
        }
    }

    /** /calc"…", with straight, curly, «» or „“ quotes; the quotes are required. */
    private static final Pattern COMMAND = Pattern.compile("/calc\\s*([\"“”«„])([^\"“”«»„]{1,500})([\"“”»])", Pattern.CASE_INSENSITIVE);

    private final String src;
    private final boolean degrees;
    private int pos;

    private AvraCalc(String src, boolean degrees) {
        this.src = src;
        this.degrees = degrees;
    }

    /** Evaluates an expression; angles in degrees when {@code degrees}. */
    public static double eval(String expression, boolean degrees) throws CalcException {
        if (expression == null) throw new CalcException("empty");
        String s = expression.replace('−', '-').replace('·', '*').replace('∗', '*').replace(':', '/');
        if (s.trim().isEmpty()) throw new CalcException("empty");
        AvraCalc c = new AvraCalc(s, degrees);
        double v = c.parseExpr();
        c.skipSpaces();
        if (c.pos < c.src.length()) throw new CalcException("unexpected '" + c.src.charAt(c.pos) + "'");
        if (Double.isNaN(v)) throw new CalcException("not a number");
        return v;
    }

    /** Human-friendly result: up to 12 significant digits, no trailing zeros, "∞" for infinity. */
    public static String format(double v) {
        if (Double.isInfinite(v)) return v > 0 ? "∞" : "-∞";
        if (Double.isNaN(v)) return "NaN";
        if (v == 0) return "0";
        double abs = Math.abs(v);
        if (abs >= 1e15 || abs < 1e-9) {
            return String.format(Locale.US, "%.10E", v).replaceAll("\\.?0+E", "E").replace("E+", "E");
        }
        BigDecimal bd = new BigDecimal(v).round(new MathContext(12, RoundingMode.HALF_UP)).stripTrailingZeros();
        return bd.scale() < 0 ? bd.setScale(0, RoundingMode.UNNECESSARY).toPlainString() : bd.toPlainString();
    }

    /** {@link #format(double)} with the decimal separator of the UI language ("," for Russian). */
    public static String format(double v, char decimalSeparator) {
        String s = format(v);
        return decimalSeparator == '.' ? s : s.replace('.', decimalSeparator);
    }

    /** True when the text has at least one /calc"…" command. */
    public static boolean hasCommand(CharSequence text) {
        return text != null && text.length() > 6 && COMMAND.matcher(text).find();
    }

    /**
     * Replaces every /calc"expr" in an outgoing message with its result (formatting spans of the rest
     * of the message are kept). An invalid expression is replaced by "expr = ?" so nothing is lost.
     */
    public static CharSequence replaceInMessage(CharSequence text) {
        return replaceInMessage(text, '.');
    }

    public static CharSequence replaceInMessage(CharSequence text, char decimalSeparator) {
        if (!hasCommand(text)) return text;
        SpannableStringBuilder out = new SpannableStringBuilder(text);
        Matcher m = COMMAND.matcher(text.toString());
        int shift = 0;
        while (m.find()) {
            String expr = m.group(2).trim();
            String result;
            try {
                result = format(eval(expr, false), decimalSeparator);
            } catch (CalcException e) {
                result = expr + " = ?";
            }
            out.replace(m.start() + shift, m.end() + shift, result);
            shift += result.length() - (m.end() - m.start());
        }
        return out;
    }

    // ---- parser ----------------------------------------------------------------------------

    private void skipSpaces() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }

    private boolean eat(char c) {
        skipSpaces();
        if (pos < src.length() && src.charAt(pos) == c) {
            pos++;
            return true;
        }
        return false;
    }

    private char peek() {
        skipSpaces();
        return pos < src.length() ? src.charAt(pos) : 0;
    }

    private double parseExpr() throws CalcException {
        double v = parseTerm();
        while (true) {
            if (eat('+')) v += parseTerm();
            else if (eat('-')) v -= parseTerm();
            else return v;
        }
    }

    private double parseTerm() throws CalcException {
        double v = parseUnary();
        while (true) {
            char c = peek();
            if (c == '*' || c == '×' || c == 'x' && !isLetterAt(pos + 1)) {
                pos++;
                v *= parseUnary();
            } else if (c == '/' || c == '÷') {
                pos++;
                double d = parseUnary();
                if (d == 0) throw new CalcException("division by zero");
                v /= d;
            } else if (c == '(' || c == 'π' || c == '√' || c == '∛' || Character.isLetter(c) && c != 'x') {
                v *= parseUnary(); // implicit multiplication: 2π, 3(4+1), 2sin(30)
            } else {
                return v;
            }
        }
    }

    private boolean isLetterAt(int i) {
        return i < src.length() && Character.isLetter(src.charAt(i));
    }

    private double parseUnary() throws CalcException {
        if (eat('-')) return -parseUnary();
        if (eat('+')) return parseUnary();
        return parsePower();
    }

    private double parsePower() throws CalcException {
        double base = parsePostfix();
        if (eat('^')) {
            double exp = parseUnary();
            return Math.pow(base, exp);
        }
        return base;
    }

    private double parsePostfix() throws CalcException {
        double v = parsePrimary();
        while (true) {
            char c = peek();
            if (c == '!') {
                pos++;
                v = factorial(v);
            } else if (c == '%') {
                pos++;
                v /= 100.0;
            } else if (c == '²') {
                pos++;
                v = v * v;
            } else if (c == '³') {
                pos++;
                v = v * v * v;
            } else {
                return v;
            }
        }
    }

    private double parsePrimary() throws CalcException {
        char c = peek();
        if (c == 0) throw new CalcException("unexpected end");
        if (c == '(') {
            pos++;
            double v = parseExpr();
            if (!eat(')')) {
                // tolerate missing closing brackets at the very end, like a calculator does
                skipSpaces();
                if (pos < src.length()) throw new CalcException("')' expected");
            }
            return v;
        }
        if (c == '√') {
            pos++;
            return checkedSqrt(parseUnary());
        }
        if (c == '∛') {
            pos++;
            return Math.cbrt(parseUnary());
        }
        if (c == 'π') {
            pos++;
            return Math.PI;
        }
        if (Character.isDigit(c) || c == '.' || c == ',') return parseNumber();
        if (Character.isLetter(c)) {
            int start = pos;
            int end = pos;
            while (end < src.length() && Character.isLetter(src.charAt(end))) end++;
            String run = src.substring(start, end).toLowerCase(Locale.ROOT);
            // the longest known name at the start of the letter run ("pie" → pi·e, "sinpi" → sin(pi))
            String best = null;
            for (String n : NAMES) {
                if (run.startsWith(n) && (best == null || n.length() > best.length())) best = n;
            }
            if (best == null) throw new CalcException("unknown '" + run + "'");
            pos = start + best.length();
            if ("log".equals(best) && pos < src.length() && src.charAt(pos) == '2'
                    && (pos + 1 >= src.length() || src.charAt(pos + 1) == '(')) {
                best = "log2";
                pos++;
            }
            String name = best;
            switch (name) {
                case "pi": return Math.PI;
                case "e": return Math.E;
            }
            if (!isFunction(name)) throw new CalcException("unknown '" + name + "'");
            double arg;
            if (peek() == '(') {
                pos++;
                arg = parseExpr();
                if (!eat(')')) {
                    skipSpaces();
                    if (pos < src.length()) throw new CalcException("')' expected");
                }
            } else {
                arg = parsePostfix();
            }
            return apply(name, arg);
        }
        throw new CalcException("unexpected '" + c + "'");
    }

    private static final String[] NAMES = {"pi", "e", "sin", "cos", "tan", "tg", "asin", "acos", "atan", "arcsin", "arccos", "arctan",
            "sinh", "cosh", "tanh", "ln", "lg", "log", "log2", "sqrt", "cbrt", "exp", "abs", "floor", "ceil", "round"};

    private static boolean isName(String n) {
        for (String s : NAMES) if (s.equals(n)) return true;
        return false;
    }

    private static boolean isFunction(String n) {
        return isName(n) && !"pi".equals(n) && !"e".equals(n);
    }

    private double parseNumber() throws CalcException {
        skipSpaces();
        int start = pos;
        boolean dot = false;
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char ch = src.charAt(pos);
            if (Character.isDigit(ch)) {
                sb.append(ch);
            } else if ((ch == '.' || ch == ',') && !dot) {
                dot = true;
                sb.append('.');
            } else if (ch == ' ' && pos + 1 < src.length() && Character.isDigit(src.charAt(pos + 1)) && sb.length() > 0 && !dot) {
                // "12 937" — digit grouping with spaces
            } else {
                break;
            }
            pos++;
        }
        // scientific notation 1.5E3 / 2e-4 (only when followed by digits, so "2e" still means 2·e)
        if (pos + 1 < src.length() && (src.charAt(pos) == 'E' || src.charAt(pos) == 'e')) {
            int p = pos + 1;
            if (p < src.length() && (src.charAt(p) == '+' || src.charAt(p) == '-')) p++;
            if (p < src.length() && Character.isDigit(src.charAt(p))) {
                sb.append('E').append(src, pos + 1, p);
                pos = p;
                while (pos < src.length() && Character.isDigit(src.charAt(pos))) sb.append(src.charAt(pos++));
            }
        }
        String s = sb.toString();
        if (s.isEmpty() || ".".equals(s)) throw new CalcException("number expected at " + start);
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new CalcException("bad number '" + s + "'");
        }
    }

    private double toRad(double a) {
        return degrees ? Math.toRadians(a) : a;
    }

    private double fromRad(double a) {
        return degrees ? Math.toDegrees(a) : a;
    }

    private double apply(String f, double a) throws CalcException {
        switch (f) {
            case "sin": return clean(Math.sin(toRad(a)));
            case "cos": return clean(Math.cos(toRad(a)));
            case "tan":
            case "tg": {
                double c = Math.cos(toRad(a));
                if (Math.abs(c) < 1e-15) throw new CalcException("tan undefined");
                return clean(Math.tan(toRad(a)));
            }
            case "asin":
            case "arcsin":
                if (a < -1 || a > 1) throw new CalcException("asin domain");
                return fromRad(Math.asin(a));
            case "acos":
            case "arccos":
                if (a < -1 || a > 1) throw new CalcException("acos domain");
                return fromRad(Math.acos(a));
            case "atan":
            case "arctan": return fromRad(Math.atan(a));
            case "sinh": return Math.sinh(a);
            case "cosh": return Math.cosh(a);
            case "tanh": return Math.tanh(a);
            case "ln":
                if (a <= 0) throw new CalcException("ln domain");
                return Math.log(a);
            case "lg":
            case "log":
                if (a <= 0) throw new CalcException("log domain");
                return Math.log10(a);
            case "log2":
                if (a <= 0) throw new CalcException("log2 domain");
                return Math.log(a) / Math.log(2);
            case "sqrt": return checkedSqrt(a);
            case "cbrt": return Math.cbrt(a);
            case "exp": return Math.exp(a);
            case "abs": return Math.abs(a);
            case "floor": return Math.floor(a);
            case "ceil": return Math.ceil(a);
            case "round": return Math.round(a);
            default: throw new CalcException("unknown '" + f + "'");
        }
    }

    /** Snaps values like sin(180°) = 1.2e-16 to 0. */
    private static double clean(double v) {
        return Math.abs(v) < 1e-14 ? 0 : v;
    }

    private static double checkedSqrt(double a) throws CalcException {
        if (a < 0) throw new CalcException("sqrt domain");
        return Math.sqrt(a);
    }

    private static double factorial(double v) throws CalcException {
        if (v < 0 || v != Math.floor(v)) throw new CalcException("factorial domain");
        if (v > 170) return Double.POSITIVE_INFINITY;
        double r = 1;
        for (int i = 2; i <= (int) v; i++) r *= i;
        return r;
    }
}
