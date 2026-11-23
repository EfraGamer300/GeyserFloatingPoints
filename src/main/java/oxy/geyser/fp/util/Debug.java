package oxy.geyser.fp.util;

import org.geysermc.geyser.GeyserImpl;

/**
 * Small console logger so we can follow what the extension is doing at runtime.
 * Everything is prefixed to make the lines easy to grep out of a server log.
 */
public final class Debug {
    private static final String PREFIX = "[GeyserFloatingPoints] ";

    private Debug() {
    }

    public static void info(String message) {
        log(message, false);
    }

    public static void warn(String message) {
        log(message, true);
    }

    private static void log(String message, boolean warning) {
        final String line = PREFIX + message;
        final GeyserImpl geyser = GeyserImpl.getInstance();
        if (geyser == null) {
            System.out.println(line);
            return;
        }
        if (warning) {
            geyser.getLogger().warning(line);
        } else {
            geyser.getLogger().info(line);
        }
    }
}
