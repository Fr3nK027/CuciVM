package top.niunaijun.blackbox.utils;

import java.io.File;

/**
 * CuciVM: upstream uploaded the full device logcat and device info to a third-party server
 * (logs-sender-api.vercel.app -> a Telegram chat) after crashes. Removed: nothing leaves the phone.
 */
public class LogSender {
    public static String send(String chatId, File logFile, String caption) {
        return "log upload disabled in CuciVM";
    }
}
