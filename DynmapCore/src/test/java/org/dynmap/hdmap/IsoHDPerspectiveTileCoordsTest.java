package org.dynmap.hdmap;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.dynmap.ConfigurationNode;
import org.dynmap.utils.TileFlags.TileCoord;
import org.junit.Test;

public class IsoHDPerspectiveTileCoordsTest {
    private IsoHDPerspective perspective(double azimuth, double inclination, double scale) {
        ConfigurationNode config = new ConfigurationNode();
        config.put("name", "tile-coordinates-test");
        config.put("azimuth", azimuth);
        config.put("inclination", inclination);
        config.put("scale", scale);
        return new IsoHDPerspective(null, config);
    }

    @Test
    public void scaledRangeUsesSameTileSizeAsPointUpdate() {
        IsoHDPerspective p = perspective(180, 90, 4);
        List<TileCoord> point = p.getTileCoords(null, 1025, 64, 1025, 2);
        assertEquals(1, point.size());
        assertEquals(8, point.get(0).x);
        assertEquals(-9, point.get(0).y);
        List<TileCoord> range = p.getTileCoords(null, 1025, 64, 1025, 1025, 64, 1025, 2);
        assertTrue(range.containsAll(point));
        assertEquals(3, range.size());
        for (TileCoord tile : range) {
            assertEquals(8, tile.x);
            assertTrue(tile.y >= -10 && tile.y <= -8);
        }
    }

    @Test
    public void rangesCoverEveryBlockAcrossScalesPerspectivesAndBoundaries() {
        double[][] views = {{180, 90, 4}, {135, 60, 16}, {45, 30, 1}, {0, 20, 64}};
        for (double[] view : views) {
            IsoHDPerspective p = perspective(view[0], view[1], view[2]);
            for (int scale = 0; scale <= 4; scale++) {
                int boundary = 128 << scale;
                int[] positions = {-boundary - 1, -boundary, -1, 0, boundary - 1, boundary, 1025};
                for (int x : positions) {
                    for (int z : positions) {
                        for (int y : new int[] {-64, 64, 319}) {
                            Set<TileCoord> range = new HashSet<>(p.getTileCoords(null,
                                    x, y, z, x + 2, y + 2, z + 2, scale));
                            for (int dx = 0; dx <= 2; dx++) {
                                for (int dy = 0; dy <= 2; dy++) {
                                    for (int dz = 0; dz <= 2; dz++) {
                                        assertTrue("Missing point tile: scale=" + scale + ", x=" + x
                                                + ", y=" + y + ", z=" + z,
                                                range.containsAll(p.getTileCoords(null,
                                                        x + dx, y + dy, z + dz, scale)));
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
