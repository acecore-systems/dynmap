package org.dynmap.utils;

import org.dynmap.renderer.RenderPatchFactory.SideVisible;
import org.junit.Test;
import static org.junit.Assert.*;

public class PatchDefinitionTest {
    @Test public void visibleCornersUseIndependentUvBounds() {
        PatchDefinitionFactory factory = new PatchDefinitionFactory();
        // Only a quarter of the long U vector is visible; using vmax as U rejects it incorrectly.
        assertNotNull(factory.getPatch(0, 0, 0, 8, 0, 0, 0, 1, 0, 0, .25, 0, 1, SideVisible.TOP, 0));
    }
    @Test public void trapezoidBoundsAtUmaxAreChecked() {
        PatchDefinitionFactory factory = new PatchDefinitionFactory();
        assertNull(factory.getPatch(0, 0, 0, 1, 0, 0, 0, 4, 0, 0, 1, 0, 0, .25, 1, SideVisible.TOP, 0));
        assertNotNull(factory.getPatch(0, 0, 0, 1, 0, 0, 0, 4, 0, 0, 1, 0, 0, .25, .5, SideVisible.TOP, 0));
    }
}
