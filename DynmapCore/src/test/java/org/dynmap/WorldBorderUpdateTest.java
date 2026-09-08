package org.dynmap;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;

import org.dynmap.common.DynmapListenerManager;
import org.dynmap.common.DynmapListenerManager.EventType;
import org.dynmap.common.DynmapServerInterface;
import org.dynmap.utils.Polygon;
import org.junit.Before;
import org.junit.Test;

public class WorldBorderUpdateTest {
    private MapManager manager;
    private DynmapWorld world;
    private DynmapListenerManager listeners;
    private Runnable check;

    @Before
    public void setUp() throws Exception {
        manager = mock(MapManager.class);
        manager.worlds = new ArrayList<DynmapWorld>();
        DynmapCore core = mock(DynmapCore.class);
        listeners = mock(DynmapListenerManager.class);
        core.listenerManager = listeners;
        DynmapServerInterface server = mock(DynmapServerInterface.class);
        doCallRealMethod().when(core).setServer(server);
        core.setServer(server);
        when(server.callSyncMethod(any())).thenAnswer(invocation -> {
            Callable<?> task = invocation.getArgument(0);
            // Let exceptions fail the test instead of being swallowed by the job's logger.
            return CompletableFuture.completedFuture(task.call());
        });
        Field coreField = MapManager.class.getDeclaredField("core");
        coreField.setAccessible(true);
        coreField.set(manager, core);
        Constructor<?> constructor = Class.forName("org.dynmap.MapManager$CheckWorldTimes")
                .getDeclaredConstructor(MapManager.class);
        constructor.setAccessible(true);
        check = (Runnable) constructor.newInstance(manager);
        world = newWorld();
        manager.worlds.add(world);
    }

    private DynmapWorld newWorld() {
        DynmapWorld result = mock(DynmapWorld.class);
        result.mapstate = new ArrayList<MapTypeState>();
        when(result.getName()).thenReturn("world");
        when(result.isLoaded()).thenReturn(true);
        return result;
    }

    private Polygon border(double offset, double size) {
        Polygon result = new Polygon();
        result.addVertex(offset, offset);
        result.addVertex(offset + size, offset);
        result.addVertex(offset + size, offset + size);
        result.addVertex(offset, offset + size);
        return result;
    }

    @Test
    public void equivalentNewPolygonsOnlyNotifyOnce() {
        when(world.getWorldBorder()).thenAnswer(invocation -> border(0, 100));
        for (int i = 0; i < 120; i++) check.run();
        verify(listeners, times(1)).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
    }

    @Test
    public void absentBorderDoesNotNotify() {
        check.run();
        check.run();
        verifyNoInteractions(listeners);
    }

    @Test
    public void additionRemovalResizeAndMoveEachNotifyOnce() {
        when(world.getWorldBorder()).thenReturn(null, border(0, 100), border(0, 100),
                border(0, 200), border(10, 200), null, null);
        for (int i = 0; i < 7; i++) check.run();
        verify(listeners, times(4)).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
    }

    @Test
    public void mutablePolygonAndVerticesAreSnapshotted() {
        Polygon polygon = border(0, 100);
        when(world.getWorldBorder()).thenReturn(polygon);
        check.run();
        polygon.getVertex(0).x = -10;
        check.run();
        check.run();
        polygon.addVertex(0, -10);
        check.run();
        verify(listeners, times(3)).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
    }

    @Test
    public void unloadedWorldIsCheckedAgainWhenReloaded() {
        when(world.getWorldBorder()).thenReturn(border(0, 100));
        check.run();
        when(world.isLoaded()).thenReturn(false);
        check.run();
        when(world.isLoaded()).thenReturn(true);
        check.run();
        verify(listeners, times(2)).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
    }

    @Test
    public void removedWorldDoesNotKeepItsPreviousBorder() {
        when(world.getWorldBorder()).thenReturn(border(0, 100));
        check.run();
        manager.worlds.clear();
        check.run();
        manager.worlds.add(world);
        check.run();
        check.run();
        verify(listeners, times(2)).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
    }

    @Test
    public void replacementWorldWithSameNameIsCheckedAgain() {
        when(world.getWorldBorder()).thenReturn(border(0, 100));
        check.run();
        manager.worlds.clear();
        DynmapWorld replacement = newWorld();
        when(replacement.getWorldBorder()).thenReturn(border(0, 100));
        manager.worlds.add(replacement);
        check.run();
        check.run();
        verify(listeners).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, world);
        verify(listeners).processWorldEvent(EventType.WORLD_SPAWN_CHANGE, replacement);
    }
}
