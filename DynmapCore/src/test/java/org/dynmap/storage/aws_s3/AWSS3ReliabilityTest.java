package org.dynmap.storage.aws_s3;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.lang.reflect.Field;
import org.dynmap.*;
import org.dynmap.storage.*;
import org.dynmap.utils.BufferOutputStream;
import org.junit.Before;
import org.junit.Test;
import io.github.linktosriram.s3lite.api.client.S3Client;
import io.github.linktosriram.s3lite.api.exception.NoSuchKeyException;
import io.github.linktosriram.s3lite.api.request.*;
import io.github.linktosriram.s3lite.api.response.*;
import io.github.linktosriram.s3lite.http.spi.request.RequestBody;

public class AWSS3ReliabilityTest {
    private AWSS3MapStorage storage;
    private S3Client client;
    private DynmapWorld world;
    private MapStorageTile tile;
    private void field(String name, Object value) throws Exception {
        Field f = AWSS3MapStorage.class.getDeclaredField(name); f.setAccessible(true); f.set(storage,value);
    }
    @Before public void setup() throws Exception {
        storage = new AWSS3MapStorage(); client = mock(S3Client.class);
        field("cpool",new S3Client[] {client,null,null,null}); field("cpoolCount",1);
        field("bucketname","test"); field("prefix","");
        world = mock(DynmapWorld.class); when(world.getName()).thenReturn("world");
        MapType map = mock(MapType.class); when(map.getPrefix()).thenReturn("flat");
        when(map.getImageFormat()).thenReturn(MapType.ImageFormat.FORMAT_PNG);
        tile = storage.getTile(world,map,0,0,0,MapType.ImageVariant.STANDARD);
    }
    private BufferOutputStream bytes(int value) { BufferOutputStream b = new BufferOutputStream(); b.write(new byte[] {(byte)value}); return b; }
    @Test public void failedWriteDoesNotQueueZoomOrCacheSuccess() {
        when(client.putObject(any(PutObjectRequest.class),any(RequestBody.class)))
            .thenThrow(new UncheckedIOException(new IOException()));
        assertFalse(tile.write(1,bytes(1),1)); verify(world,never()).enqueueZoomOutUpdate(any());
        assertFalse(storage.setStandaloneFile("test.json",bytes(1)));
        assertFalse(storage.setStandaloneFile("test.json",bytes(1)));
        verify(client,times(3)).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test public void successfulWriteQueuesZoom() {
        assertTrue(tile.write(1,bytes(1),1)); verify(world).enqueueZoomOutUpdate(tile);
    }
    @Test public void readFailureIsNotMissingTile() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(new UncheckedIOException(new IOException()));
        try { tile.read(); fail(); } catch (StorageReadException expected) { }
    }
    @Test public void realNotFoundIsMissingTile() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(mock(NoSuchKeyException.class));
        assertNull(tile.read());
    }
    @Test public void sameAssetSkipsPutButDifferentAssetIsPublished() {
        ResponseBytes<GetObjectResponse> existing = mock(ResponseBytes.class);
        when(existing.getBytes()).thenReturn(new byte[] {1});
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenReturn(existing);
        assertTrue(storage.setStaticWebFile("asset.js",bytes(1)));
        verify(client,never()).putObject(any(PutObjectRequest.class),any(RequestBody.class));
        assertTrue(storage.setStaticWebFile("asset.js",bytes(2)));
        verify(client).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test public void missingAssetIsPublishedAndLiveJsonIsNeverReadCached() {
        when(client.getObjectAsBytes(any(GetObjectRequest.class))).thenThrow(mock(NoSuchKeyException.class));
        assertTrue(storage.setStaticWebFile("asset.js",bytes(1)));
        assertTrue(storage.setStandaloneFile("update.json",bytes(1)));
        verify(client).getObjectAsBytes(any(GetObjectRequest.class));
        verify(client,times(2)).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
    @Test public void digestIgnoresUnusedBufferCapacity() {
        BufferOutputStream a = bytes(1), b = bytes(1); b.buf[b.len] = 42;
        assertTrue(storage.setStandaloneFile("update.json",a));
        assertTrue(storage.setStandaloneFile("update.json",b));
        verify(client).putObject(any(PutObjectRequest.class),any(RequestBody.class));
    }
}
