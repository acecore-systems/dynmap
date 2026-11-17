package org.dynmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.yaml.snakeyaml.error.YAMLException;

public class ConfigurationNodeTest {
    private static final String TITLE = "Caf\u00e9 \u5730\u56f3";
    private static final String WINDOWS_CONFIG = "%YAML 1.1\r\n---\r\n" +
        "defaults: &defaults\r\n" +
        "    enabled: yes\r\n" +
        "world:\r\n" +
        "    <<: *defaults\r\n" +
        "    title: " + TITLE + "\r\n" +
        "    center:\r\n" +
        "    maps: [surface, cave]\r\n";

    @Rule
    public final TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void readsWindowsLineEndingsWithAndWithoutUnicodeBom() throws IOException {
        checkLoad(StandardCharsets.UTF_8);
        checkLoad(StandardCharsets.UTF_8, (byte) 0xef, (byte) 0xbb, (byte) 0xbf);
        checkLoad(StandardCharsets.UTF_16LE, (byte) 0xff, (byte) 0xfe);
        checkLoad(StandardCharsets.UTF_16BE, (byte) 0xfe, (byte) 0xff);
    }

    @Test
    public void savesYaml11AsUtf8WithBlankNullValues() throws IOException {
        File saved = temp.newFile();
        ConfigurationNode original = new ConfigurationNode(input(WINDOWS_CONFIG));
        assertTrue(original.save(saved));

        String written = new String(Files.readAllBytes(saved.toPath()), StandardCharsets.UTF_8);
        assertTrue(written.startsWith("%YAML 1.1\n---\n"));
        assertTrue(written.contains(TITLE));
        assertTrue(written.matches("(?s).*\\n\\s+center:[ \\t]*\\n.*"));

        ConfigurationNode reloaded = new ConfigurationNode(saved);
        assertTrue(reloaded.load());
        assertEquals(original.entries, reloaded.entries);
    }

    @Test
    public void loadsBundledWorldTemplate() throws IOException {
        try (InputStream in = getClass().getResourceAsStream("/templates/normal.txt")) {
            ConfigurationNode config = new ConfigurationNode(in);
            assertTrue(config.getBoolean("templates/normal/enabled", false));
            assertEquals("org.dynmap.hdmap.HDMap", config.getNodes("templates/normal/maps").get(0).getString("class"));
        }
    }

    @Test
    public void refusesJavaObjectTagsInConfiguration() throws IOException {
        String content = "world: !!java.util.ArrayList [invalid]\n";
        File file = temp.newFile();
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

        assertThrows(YAMLException.class, () -> new ConfigurationNode(input(content)));
        assertThrows(YAMLException.class, () -> new ConfigurationNode(file).load());
    }

    @Test
    public void limitsCollectionAliasExpansion() {
        StringBuilder content = new StringBuilder("anchor: &anchor [item]\n");
        for (int i = 0; i < 51; i++) {
            content.append("alias").append(i).append(": *anchor\n");
        }
        assertThrows(YAMLException.class, () -> new ConfigurationNode(input(content.toString())));
    }

    private void checkLoad(Charset charset, byte... bom) throws IOException {
        byte[] content = WINDOWS_CONFIG.getBytes(charset);
        byte[] bytes = Arrays.copyOf(bom, bom.length + content.length);
        System.arraycopy(content, 0, bytes, bom.length, content.length);
        File file = temp.newFile();
        Files.write(file.toPath(), bytes);

        ConfigurationNode fromFile = new ConfigurationNode(file);
        assertTrue(fromFile.load());
        assertEquals(TITLE, fromFile.getString("world/title"));
        assertTrue(fromFile.getBoolean("world/enabled", false));
        assertTrue(fromFile.getNode("world").containsKey("center"));
        assertNull(fromFile.getObject("world/center"));
        assertEquals(Arrays.asList("surface", "cave"), fromFile.getList("world/maps"));
        assertEquals(fromFile.entries, new ConfigurationNode(new ByteArrayInputStream(bytes)).entries);
    }

    private static InputStream input(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }
}
