package org.dynmap.bukkit;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

final class MinecraftVersion {
    private static final Pattern LEGACY = Pattern.compile("\\(MC: ([0-9]+(?:\\.[0-9]+){1,2})\\)");
    private static final Pattern API = Pattern.compile("^([0-9]+(?:\\.[0-9]+){1,2})(?:-|$)");

    private MinecraftVersion() { }

    static String fromServer(String serverVersion, String bukkitVersion) {
        Matcher legacy = LEGACY.matcher(serverVersion);
        if (legacy.find()) { return legacy.group(1); }
        Matcher api = API.matcher(bukkitVersion);
        return api.find() ? api.group(1) : "1.0.0";
    }
}
