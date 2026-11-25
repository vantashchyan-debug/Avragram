package com.avragramm;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.SendMessagesHelper;
import org.telegram.ui.Components.BulletinFactory;

import java.util.HashMap;

/**
 * Repeats a text message in one chat: N times with a fixed interval.
 * Limits keep it within what Telegram tolerates (FLOOD_WAIT, account restrictions are still
 * possible — the user is warned in the dialog).
 */
public final class AvraSpam {

    public static final int MAX_COUNT = 100;
    /** Android timers and Telegram anti-flood make anything below ~100 ms meaningless. */
    public static final int MIN_INTERVAL_MS = 100;

    private static final HashMap<Long, Job> jobs = new HashMap<>();

    private static final class Job implements Runnable {
        final int account;
        final long dialogId;
        final String text;
        final int total;
        final long intervalMs;
        int sent;
        boolean cancelled;

        Job(int account, long dialogId, String text, int total, long intervalMs) {
            this.account = account;
            this.dialogId = dialogId;
            this.text = text;
            this.total = total;
            this.intervalMs = intervalMs;
        }

        @Override
        public void run() {
            if (cancelled) return;
            SendMessagesHelper.getInstance(account).sendMessage(SendMessagesHelper.SendMessageParams.of(text, dialogId));
            sent++;
            if (sent >= total) {
                jobs.remove(dialogId);
                toast("Спам завершён: отправлено " + sent);
            } else {
                AndroidUtilities.runOnUIThread(this, intervalMs);
            }
        }
    }

    private AvraSpam() {}

    public static boolean isRunning(long dialogId) {
        return jobs.containsKey(dialogId);
    }

    public static void start(int account, long dialogId, String text, int count, long intervalMs) {
        stop(dialogId, false);
        count = Math.max(1, Math.min(MAX_COUNT, count));
        intervalMs = Math.max(MIN_INTERVAL_MS, intervalMs);
        Job job = new Job(account, dialogId, text, count, intervalMs);
        jobs.put(dialogId, job);
        AndroidUtilities.runOnUIThread(job);
        toast("Спам запущен: " + count + " × каждые " + intervalMs + " мс");
    }

    public static void stop(long dialogId, boolean notify) {
        Job job = jobs.remove(dialogId);
        if (job != null) {
            job.cancelled = true;
            AndroidUtilities.cancelRunOnUIThread(job);
            if (notify) toast("Спам остановлен: отправлено " + job.sent + " из " + job.total);
        }
    }

    private static void toast(String text) {
        try {
            BulletinFactory.global().createSimpleBulletin(org.telegram.messenger.R.raw.info, text).show();
        } catch (Throwable ignore) {
        }
    }
}
