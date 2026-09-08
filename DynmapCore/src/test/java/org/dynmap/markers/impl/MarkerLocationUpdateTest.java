package org.dynmap.markers.impl;

import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.util.Map;

import org.dynmap.Client;
import org.dynmap.MapManager;
import org.dynmap.markers.MarkerIcon.MarkerSize;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

public class MarkerLocationUpdateTest {
    private MarkerAPIImpl previousApi;
    private MapManager previousManager;
    private MarkerAPIImpl api;
    private MapManager manager;
    private MarkerImpl marker;

    @Before
    public void setUp() {
        previousApi = MarkerAPIImpl.api;
        previousManager = MapManager.mapman;
        api = new MarkerAPIImpl();
        MarkerAPIImpl.api = api;
        manager = mock(MapManager.class);
        MapManager.mapman = manager;
        MarkerIconImpl icon = mock(MarkerIconImpl.class);
        when(icon.getMarkerIconID()).thenReturn("default");
        when(icon.getMarkerIconSize()).thenReturn(MarkerSize.MARKER_16x16);
        MarkerSetImpl set = mock(MarkerSetImpl.class);
        when(set.getMarkerSetID()).thenReturn("markers");
        marker = new MarkerImpl("spawn", "Spawn", true, "old/world", 1, 64, 2, icon, true, set);
    }

    @After
    public void tearDown() {
        MarkerAPIImpl.api = previousApi;
        MapManager.mapman = previousManager;
    }

    private Object field(String name) throws Exception {
        Field field = MarkerAPIImpl.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(api);
    }

    @Test
    public void identicalLocationDoesNotDirtyOrNotify() throws Exception {
        for (int i = 0; i < 120; i++) marker.setLocation(new String("old/world"), 1, 64, 2);
        verifyNoInteractions(manager);
        assertTrue(((Map<?, ?>) field("dirty_worlds")).isEmpty());
        assertEquals(false, field("dirty_markers"));
    }

    @Test
    public void coordinateChangeNotifiesAndPersists() throws Exception {
        marker.setLocation("old/world", 3, 65, 4);
        verify(manager).pushUpdate(eq("old-world"), any(Client.Update.class));
        assertTrue(((Map<?, ?>) field("dirty_worlds")).containsKey("old-world"));
        assertEquals(true, field("dirty_markers"));
        assertEquals(3, marker.getX(), 0);
        assertEquals(65, marker.getY(), 0);
        assertEquals(4, marker.getZ(), 0);
    }

    @Test
    public void eachCoordinateCanIndependentlyTriggerAnUpdate() {
        marker.setLocation("old/world", 3, 64, 2);
        marker.setLocation("old/world", 3, 65, 2);
        marker.setLocation("old/world", 3, 65, 4);
        marker.setLocation("old/world", 3, 65, 4);
        verify(manager, times(3)).pushUpdate(eq("old-world"), any(Client.Update.class));
    }

    @Test
    public void worldMoveDeletesOldAndUpdatesNewWorld() throws Exception {
        marker.setLocation("new/world", 1, 64, 2);
        ArgumentCaptor<Client.Update> oldUpdate = ArgumentCaptor.forClass(Client.Update.class);
        ArgumentCaptor<Client.Update> newUpdate = ArgumentCaptor.forClass(Client.Update.class);
        InOrder order = inOrder(manager);
        order.verify(manager).pushUpdate(eq("old-world"), oldUpdate.capture());
        order.verify(manager).pushUpdate(eq("new-world"), newUpdate.capture());
        assertEquals("markerdeleted", ((MarkerAPIImpl.MarkerUpdated) oldUpdate.getValue()).msg);
        assertEquals("markerupdated", ((MarkerAPIImpl.MarkerUpdated) newUpdate.getValue()).msg);
        assertEquals("new-world", marker.getNormalizedWorld());
        Map<?, ?> dirty = (Map<?, ?>) field("dirty_worlds");
        assertTrue(dirty.containsKey("old-world"));
        assertTrue(dirty.containsKey("new-world"));
        assertEquals(true, field("dirty_markers"));
    }
}
