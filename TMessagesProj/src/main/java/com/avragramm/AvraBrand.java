package com.avragramm;

import android.os.Build;

import org.telegram.messenger.BuildVars;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.ui.ActionBar.Theme;

/**
 * Avragram branding. Avragram is based on exteraGram (https://github.com/exteraSquad/exteraGram):
 * the single "Based on exteraGram" line in the settings footer must stay.
 */
public final class AvraBrand {
    public static final String NAME = "Avragram";
    /** Avragram's own release number (see CHANGELOG.md); the base client version stays in versionName for plugin compatibility. */
    public static final String VERSION = "1.11.0";
    public static final String[] AUTHORS = {"mangofag", "obijenniy_baka"};
    /** The single attribution line required by the base project's license (GPL-3 additional terms). */
    public static final String BASED_ON_URL = "https://github.com/exteraSquad/exteraGram";

    private AvraBrand() {}

    public static String version() {
        return NAME + " " + VERSION + " (" + BuildVars.BUILD_VERSION_STRING + ", " + BuildVars.BUILD_VERSION + ")";
    }

    public static String footer() {
        return version() + "\n" + LocaleController.getString(R.string.AvraBasedOn);
    }

    /** Bug report template: technical info only — never chats, phone numbers or keys. */
    public static String reportTemplate() {
        String theme = "";
        try {
            Theme.ThemeInfo t = Theme.getActiveTheme();
            if (t != null) theme = t.getName();
        } catch (Throwable ignore) {
        }
        String device = Build.MANUFACTURER + " " + Build.MODEL + ", Android " + Build.VERSION.RELEASE + " (" + Build.SUPPORTED_ABIS[0] + ")";
        return LocaleController.formatString(R.string.AvraReportTemplate, version(), device, theme);
    }
}
