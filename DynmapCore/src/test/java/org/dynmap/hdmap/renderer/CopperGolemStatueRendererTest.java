package org.dynmap.hdmap.renderer;

import java.util.BitSet;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.utils.PatchDefinition;
import org.dynmap.utils.PatchDefinitionFactory;
import org.junit.Test;
import static org.junit.Assert.*;

public class CopperGolemStatueRendererTest {
    @Test public void allStatueStatesHaveDetailedMeshesAndWaterDoesNotChangeGeometry() {
        Set<String> shapes = new HashSet<>();
        int checked = 0;
        for (String wax : new String[]{"", "waxed_"}) for (String oxidation : new String[]{"", "exposed_", "weathered_", "oxidized_"}) {
            String name = "minecraft:" + wax + oxidation + "copper_golem_statue";
            CopperGolemStatueRenderer renderer = new CopperGolemStatueRenderer();
            assertTrue(renderer.initializeRenderer(new PatchDefinitionFactory(), name, new BitSet(), Collections.<String,String>emptyMap()));
            for (String pose : new String[]{"standing", "sitting", "running", "star"}) for (String facing : new String[]{"north", "east", "south", "west"}) {
                RenderPatch[] dry = null;
                for (boolean water : new boolean[]{false, true}) {
                    DynmapBlockState state = new DynmapBlockState(null, 0, name,
                            "waterlogged=" + water + ",copper_golem_pose=" + pose + ",facing=" + facing, "COPPER");
                    RenderPatch[] patches = renderer.meshFor(state);
                    assertEquals(pose.equals("sitting") ? 66 : 54, patches.length);
                    StringBuilder shape = new StringBuilder();
                    double maxY = 0;
                    for (RenderPatch patch : patches) {
                        assertNotNull(patch);
                        PatchDefinition p = (PatchDefinition) patch;
                        assertTrue(p.validate());
                        assertTrue(p.textureindex >= 0 && p.textureindex < renderer.getMaximumTextureCount());
                        assertTrue(p.umax > 0 && p.umax <= 1 && p.vmax > 0 && p.vmax <= 1);
                        for (double u : new double[]{0, p.umax}) for (double v : new double[]{0, p.vmax})
                            maxY = Math.max(maxY, p.y0 + (p.yu-p.y0)*u + (p.yv-p.y0)*v);
                        shape.append(p.toString());
                    }
                    assertTrue("Antenna is not truncated", maxY > 1.1 && maxY < 1.6);
                    if (dry == null) dry = patches; else assertSame(dry, patches);
                    shapes.add(shape.toString());
                    checked++;
                }
            }
        }
        assertEquals(256, checked);
        assertEquals("Four distinct poses in each of four directions", 16, shapes.size());
    }
}
