package com.avragramm;

import org.telegram.messenger.voip.VoIPService;

import java.nio.ByteBuffer;

/**
 * Glue between audio capture points and per-chat voice changer settings.
 * Voice messages and round videos use the chat that is open; calls use the call's peer.
 */
public final class AvraVoice {

    /** Dialog of the chat currently open in the foreground (set by ChatActivity). */
    public static volatile long currentChatDialogId;

    private AvraVoice() {}

    public static long callDialogId() {
        try {
            VoIPService s = VoIPService.getSharedInstance();
            if (s == null) return 0;
            if (s.groupCall != null && s.getChat() != null) return -s.getChat().id;
            if (s.getUser() != null) return s.getUser().id;
        } catch (Throwable ignore) {
        }
        return 0;
    }

    /** For voice messages / round videos recorded in the open chat. */
    public static void processRecording(AvraVoiceFx fx, ByteBuffer buf, int bytes, int sampleRate, int channels) {
        long did = currentChatDialogId;
        if (did == 0) return;
        fx.process(buf, bytes, sampleRate, channels, AvraConfig.voicePreset(did), AvraConfig.voicePitch(did));
    }

    /** For the microphone during a call. */
    public static void processCall(AvraVoiceFx fx, ByteBuffer buf, int bytes, int sampleRate, int channels) {
        long did = callDialogId();
        if (did == 0) return;
        fx.process(buf, bytes, sampleRate, channels, AvraConfig.voicePreset(did), AvraConfig.voicePitch(did));
    }
}
