package com.avragramm;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.TextUtils;

import org.telegram.PhoneFormat.PhoneFormat;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.UserConfig;
import org.telegram.tgnet.ConnectionsManager;
import org.telegram.tgnet.TLRPC;
import org.telegram.ui.Components.TypefaceSpan;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Locale;

/**
 * "Phone region": country, ISO code and flag of a phone number, shown in the menu of a number in
 * profiles and in messages. Uses Telegram's own country code list (help.getCountriesList, with
 * per-country prefixes such as +7 7xx → Kazakhstan); until it arrives, the bundled countries.txt.
 */
public final class AvraPhoneRegion {

    private AvraPhoneRegion() {
    }

    public static final class Info {
        public String iso, name, code;

        public String flag() {
            if (iso == null || iso.length() != 2) return "";
            String up = iso.toUpperCase(Locale.US);
            if (up.charAt(0) < 'A' || up.charAt(0) > 'Z' || up.charAt(1) < 'A' || up.charAt(1) > 'Z') return "";
            return new String(Character.toChars(0x1F1E6 + up.charAt(0) - 'A'))
                    + new String(Character.toChars(0x1F1E6 + up.charAt(1) - 'A'));
        }
    }

    /** One matchable entry: country code + optional national prefix. */
    private static final class Entry {
        String code, prefix, iso, name;
    }

    private static ArrayList<Entry> entries = new ArrayList<>();
    private static boolean bundledLoaded, serverRequested;
    private static String serverLang;

    public static boolean enabled() {
        AvraConfig.ensureLoaded();
        return AvraConfig.phoneRegion;
    }

    /** Finds the country of a number. A number without "+" is read as a local number of your own country. */
    public static Info lookup(int account, String phone) {
        if (TextUtils.isEmpty(phone)) return null;
        ensureLoaded(account);
        String digits = PhoneFormat.stripExceptNumbers(phone);
        if (digits.isEmpty()) return null;
        boolean international = phone.trim().startsWith("+") || phone.trim().startsWith("00");
        if (phone.trim().startsWith("00")) digits = digits.substring(2);
        if (!international) {
            String own = UserConfig.getInstance(account).getClientPhone();
            Info mine = own == null ? null : match(PhoneFormat.stripExceptNumbers(own));
            if (mine == null) {
                return match(digits); // stored without "+", e.g. user.phone
            }
            if (digits.startsWith(mine.code) && digits.length() > 10) {
                return match(digits); // already has the country code
            }
            String national = digits;
            if (national.startsWith("0")) national = national.substring(1);
            else if ("7".equals(mine.code) && national.startsWith("8") && national.length() == 11) national = national.substring(1);
            return match(mine.code + national);
        }
        return match(digits);
    }

    private static Info match(String digits) {
        Entry best = null;
        int bestLen = -1;
        for (Entry e : entries) {
            if (!digits.startsWith(e.code)) continue;
            int len = e.code.length() * 10; // a longer country code wins over any prefix of a shorter one
            if (e.prefix != null) {
                if (!digits.startsWith(e.prefix, e.code.length())) continue;
                len += e.prefix.length();
            }
            if (len > bestLen) {
                bestLen = len;
                best = e;
            }
        }
        if (best == null) return null;
        Info i = new Info();
        i.iso = best.iso;
        i.code = best.code;
        i.name = TextUtils.isEmpty(best.name) ? displayName(best.iso) : best.name;
        return i;
    }

    private static String displayName(String iso) {
        try {
            Locale l = LocaleController.getInstance().getCurrentLocale();
            String n = new Locale("", iso).getDisplayCountry(l != null ? l : Locale.getDefault());
            if (!TextUtils.isEmpty(n) && !n.equalsIgnoreCase(iso)) return n;
        } catch (Exception ignore) {
        }
        return iso;
    }

    private static void ensureLoaded(int account) {
        if (!bundledLoaded) {
            bundledLoaded = true;
            loadBundled();
        }
        String lang = LocaleController.getInstance().getCurrentLocaleInfo() != null
                ? LocaleController.getInstance().getCurrentLocaleInfo().getLangCode() : Locale.getDefault().getLanguage();
        if (!serverRequested || !TextUtils.equals(lang, serverLang)) {
            serverRequested = true;
            serverLang = lang;
            requestServer(account, lang);
        }
    }

    private static void loadBundled() {
        ArrayList<Entry> list = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(ApplicationLoader.applicationContext.getResources().getAssets().open("countries.txt")))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] a = line.split(";");
                if (a.length < 3) continue;
                Entry e = new Entry();
                e.code = a[0].trim();
                e.iso = a[1].trim();
                e.name = displayName(e.iso);
                if (!e.code.isEmpty()) list.add(e);
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        if (!list.isEmpty()) entries = list;
    }

    private static void requestServer(int account, String lang) {
        TLRPC.TL_help_getCountriesList req = new TLRPC.TL_help_getCountriesList();
        req.lang_code = lang == null ? "" : lang;
        ConnectionsManager.getInstance(account).sendRequest(req, (response, error) -> AndroidUtilities.runOnUIThread(() -> {
            if (!(response instanceof TLRPC.TL_help_countriesList)) {
                serverRequested = false; // try again next time
                return;
            }
            ArrayList<Entry> list = new ArrayList<>();
            for (TLRPC.TL_help_country c : ((TLRPC.TL_help_countriesList) response).countries) {
                String name = !TextUtils.isEmpty(c.name) ? c.name : c.default_name;
                for (TLRPC.TL_help_countryCode cc : c.country_codes) {
                    if (cc == null || TextUtils.isEmpty(cc.country_code)) continue;
                    if (cc.prefixes == null || cc.prefixes.isEmpty()) {
                        list.add(entry(cc.country_code, null, c.iso2, name));
                    } else {
                        for (String prefix : cc.prefixes) list.add(entry(cc.country_code, prefix, c.iso2, name));
                    }
                }
            }
            if (!list.isEmpty()) entries = list;
        }));
    }

    private static Entry entry(String code, String prefix, String iso, String name) {
        Entry e = new Entry();
        e.code = code;
        e.prefix = TextUtils.isEmpty(prefix) ? null : prefix;
        e.iso = iso;
        e.name = name;
        return e;
    }

    /** "🇰🇿 Kazakhstan" (bold) + new line + "KZ · +7". */
    public static CharSequence describe(Info i) {
        SpannableStringBuilder b = new SpannableStringBuilder();
        String flag = i.flag();
        if (!flag.isEmpty()) b.append(flag).append("  ");
        int start = b.length();
        b.append(i.name);
        b.setSpan(new TypefaceSpan(AndroidUtilities.bold()), start, b.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        b.append("\n").append(i.iso.toUpperCase(Locale.US)).append(" · +").append(i.code);
        return b;
    }
}
