package org.dynmap;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import org.dynmap.storage.*;
import org.junit.*;

public class ZoomStorageFailureTest {
    private MapManager previous;
    private DynmapWorld world;
    private MapTypeState state;
    private MapStorageTile child, parent;
    @Before public void setup() throws Exception {
        previous = MapManager.mapman; MapManager.mapman = mock(MapManager.class);
        world = mock(DynmapWorld.class, CALLS_REAL_METHODS);
        doNothing().when(world).enqueueZoomOutUpdate(any());
        MapType map = mock(MapType.class);
        when(map.getTileSize()).thenReturn(128);
        when(map.getMapZoomOutLevels()).thenReturn(3);
        when(map.getImageFormat()).thenReturn(MapType.ImageFormat.FORMAT_PNG);
        when(map.getVariants()).thenReturn(new MapType.ImageVariant[] {MapType.ImageVariant.STANDARD});
        state = new MapTypeState(world,map);
        world.mapstate = new ArrayList<>(); world.mapstate.add(state);
        MapStorage storage = mock(MapStorage.class);
        Field f = DynmapWorld.class.getDeclaredField("storage"); f.setAccessible(true); f.set(world,storage);
        child = mock(MapStorageTile.class); parent = mock(MapStorageTile.class);
        Field mapField = MapStorageTile.class.getDeclaredField("map"); mapField.setAccessible(true); mapField.set(child,map);
        // Public final coordinate fields on the mocks default to zero (the origin).
        when(child.getZoomOutTile()).thenReturn(parent);
        when(storage.getTile(eq(world),any(),anyInt(),anyInt(),anyInt(),any())).thenReturn(child);
        when(parent.exists()).thenReturn(true);
        state.setZoomOutInv(0,0,0);
    }
    @After public void cleanup() { MapManager.mapman = previous; }
    @Test public void failedReadPreservesParentAndRetriesNextPass() {
        when(child.read()).thenThrow(new StorageReadException(new IOException()));
        world.freshenZoomOutFiles();
        verify(parent,never()).delete(); verifyNoInteractions(MapManager.mapman);
        assertNotNull(state.saveZoomOut());
        world.freshenZoomOutFiles();
        verify(child,times(2)).read();
    }
    @Test public void failedDeleteDoesNotPublishAndRetryCanRecover() {
        when(parent.delete()).thenReturn(false,true);
        world.freshenZoomOutFiles();
        verifyNoInteractions(MapManager.mapman); assertNotNull(state.saveZoomOut());
        world.freshenZoomOutFiles();
        verify(parent,times(2)).delete();
        verify(MapManager.mapman).pushUpdate(eq(world),any(Client.Update.class));
    }
}
