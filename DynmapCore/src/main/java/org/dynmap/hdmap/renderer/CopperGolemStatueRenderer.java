package org.dynmap.hdmap.renderer;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import org.dynmap.Log;
import org.dynmap.renderer.CustomRenderer;
import org.dynmap.renderer.DynmapBlockState;
import org.dynmap.renderer.MapDataContext;
import org.dynmap.renderer.RenderPatch;
import org.dynmap.renderer.RenderPatchFactory;
import org.dynmap.renderer.RenderPatchFactory.SideVisible;

/** Block-state-only statue mesh, including the antenna above the owning block. */
public class CopperGolemStatueRenderer extends CustomRenderer {
    private static final String[] POSES = {"standing", "sitting", "running", "star"};
    private static final String[] FACINGS = {"north", "east", "south", "west"};
    private final RenderPatch[][] meshes = new RenderPatch[16][];

    @Override
    public boolean initializeRenderer(RenderPatchFactory factory, String name, BitSet states, Map<String, String> parameters) {
        if (!super.initializeRenderer(factory, name, states, parameters)) return false;
        List<List<RenderPatch>> lists = new ArrayList<>();
        for (int i = 0; i < meshes.length; i++) lists.add(new ArrayList<RenderPatch>());
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
                CopperGolemStatueRenderer.class.getResourceAsStream("/copper-golem-statue.csv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("#") || line.isEmpty()) continue;
                String[] fields = line.split(",");
                int pose = indexOf(POSES, fields[0]);
                int texture = Integer.parseInt(fields[1]);
                double width = Double.parseDouble(fields[2]) / 16;
                double height = Double.parseDouble(fields[3]) / 16;
                SideVisible side = "1".equals(fields[4]) ? SideVisible.BOTTOM : SideVisible.TOP;
                double[] points = new double[9];
                for (int i = 0; i < points.length; i++) points[i] = Double.parseDouble(fields[i + 5]);
                for (int facing = 0; facing < 4; facing++) {
                    double[] rotated = rotate(points, facing);
                    RenderPatch patch = factory.getPatch(rotated[0], rotated[1], rotated[2],
                            rotated[3], rotated[4], rotated[5], rotated[6], rotated[7], rotated[8],
                            0, width, 0, height, side, texture);
                    if (patch == null) throw new IOException("Invalid statue patch: " + line);
                    lists.get(pose * 4 + facing).add(patch);
                }
            }
            for (int i = 0; i < meshes.length; i++) meshes[i] = lists.get(i).toArray(new RenderPatch[0]);
            return true;
        } catch (IOException | RuntimeException error) {
            Log.severe("Cannot load copper golem statue model", error);
            return false;
        }
    }

    private static int indexOf(String[] values, String value) {
        for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return i;
        throw new IllegalArgumentException("Unknown statue state: " + value);
    }

    private static double[] rotate(double[] source, int facing) {
        double[] result = source.clone();
        for (int i = 0; i < result.length; i += 3) {
            double x = source[i] - 0.5, z = source[i + 2] - 0.5;
            for (int n = 0; n < facing; n++) { double oldX = x; x = -z; z = oldX; }
            result[i] = x + 0.5;
            result[i + 2] = z + 0.5;
        }
        return result;
    }

    RenderPatch[] meshFor(DynmapBlockState state) {
        int pose = 0, facing = 0;
        for (int i = 0; i < POSES.length; i++) if (state.isStateMatch("copper_golem_pose", POSES[i])) pose = i;
        for (int i = 0; i < FACINGS.length; i++) if (state.isStateMatch("facing", FACINGS[i])) facing = i;
        return meshes[pose * 4 + facing];
    }

    @Override public RenderPatch[] getRenderPatchList(MapDataContext context) { return meshFor(context.getBlockType()); }
    @Override public int getMaximumTextureCount() { return 65; }
    @Override public boolean isOnlyBlockStateSensitive() { return true; }
}
