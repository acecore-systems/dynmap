package org.dynmap.bukkit;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class MinecraftVersionTest {
    @Test
    public void readsLegacyPaperVersion() {
        assertEquals("1.21.8", MinecraftVersion.fromServer("1.21.8-60-abcdef (MC: 1.21.8)", "1.21.8-R0.1-SNAPSHOT"));
    }

    @Test
    public void readsNewPaperVersionWithoutMcSuffix() {
        assertEquals("26.2", MinecraftVersion.fromServer("26.2-105-abcdef", "26.2-R0.1-SNAPSHOT"));
    }

    @Test
    public void acceptsVersionAtStartAndKeepsPatchNumber() {
        assertEquals("1.20.1", MinecraftVersion.fromServer("(MC: 1.20.1)", "unknown"));
        assertEquals("26.2.1", MinecraftVersion.fromServer("custom", "26.2.1-R0.1-SNAPSHOT"));
    }

    @Test
    public void doesNotTreatServerBuildNumberAsMinecraftVersion() {
        assertEquals("1.0.0", MinecraftVersion.fromServer("git-custom-12345", "unknown"));
        assertEquals("1.0.0", MinecraftVersion.fromServer("26.20-105", "26.2evil"));
    }
}
