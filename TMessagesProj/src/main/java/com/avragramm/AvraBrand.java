package com.avragramm;

import android.os.Build;

import org.telegram.messenger.BuildVars;
import org.telegram.ui.ActionBar.Theme;

/**
 * Avragram branding & attribution. Avragram is created on the basis of exteraGram
 * (https://github.com/exteraSquad/exteraGram, channel @exteraGram) — this attribution must stay.
 */
public final class AvraBrand {
    public static final String NAME = "Avragram";
    public static final String[] AUTHORS = {"mangofag", "obijenniy_baka"};
    public static final String EXTERA_CHANNEL = "exteraGram";
    public static final String EXTERA_SOURCE = "https://github.com/exteraSquad/exteraGram";
    public static final String BASED_ON = "Avragram создан на основе exteraGram";

    private AvraBrand() {}

    public static String version() {
        return NAME + " " + BuildVars.BUILD_VERSION_STRING + " (" + BuildVars.BUILD_VERSION + ")";
    }

    public static String footer() {
        return version() + "\n" + BASED_ON + " · @" + EXTERA_CHANNEL;
    }

    /** Bug report template: technical info only — never chats, phone numbers or keys. */
    public static String reportTemplate() {
        String theme = "";
        try {
            Theme.ThemeInfo t = Theme.getActiveTheme();
            if (t != null) theme = t.getName();
        } catch (Throwable ignore) {
        }
        return "Проблема в " + version() + "\n"
                + "Устройство: " + Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (" + Build.SUPPORTED_ABIS[0] + ")\n"
                + "Тема: " + theme + "\n\n"
                + "Шаги воспроизведения:\n1. \n2. \n3. \n\n"
                + "Что ожидалось:\n\nЧто произошло:\n\n"
                + "(Не прикладывайте переписки, номера телефонов, коды входа и ключи.)";
    }
}
