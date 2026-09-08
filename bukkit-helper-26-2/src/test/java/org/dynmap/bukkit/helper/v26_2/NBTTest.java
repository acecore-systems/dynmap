package org.dynmap.bukkit.helper.v26_2;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import org.junit.Test;

import static org.junit.Assert.*;

public class NBTTest {
    @Test
    public void preservesNumericTypesAndMissingDefaults() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("b", (byte) 1);
        tag.putShort("s", (short) 32000);
        tag.putInt("i", -123456);
        tag.putLong("l", 1234567890123L);
        tag.putFloat("f", 0.25F);
        tag.putDouble("d", 0.125);
        NBT.NBTCompound nbt = new NBT.NBTCompound(tag);
        assertTrue(nbt.getBoolean("b"));
        assertEquals(32000, nbt.getShort("s"));
        assertEquals(-123456, nbt.getInt("i"));
        assertEquals(1234567890123L, nbt.getLong("l"));
        assertEquals(0.25F, nbt.getFloat("f"), 0);
        assertEquals(0.125, nbt.getDouble("d"), 0);
        assertTrue(nbt.contains("i", 99));
        assertFalse(nbt.contains("i", 8));
        assertFalse(nbt.contains("missing", 99));
        assertEquals(0, nbt.getInt("missing"));
        assertFalse(nbt.getBoolean("missing"));
    }

    @Test
    public void preservesChunkPaletteAndNestedCompounds() {
        CompoundTag state = new CompoundTag();
        state.putString("Name", "minecraft:sulfur_stairs");
        CompoundTag properties = new CompoundTag();
        properties.putString("facing", "east");
        state.put("Properties", properties);
        ListTag palette = new ListTag();
        palette.add(state);
        CompoundTag chunk = new CompoundTag();
        chunk.put("palette", palette);
        NBT.NBTCompound nbt = new NBT.NBTCompound(chunk);
        assertEquals(1, nbt.getList("palette", 10).size());
        assertEquals("minecraft:sulfur_stairs", nbt.getList("palette", 10).getCompound(0).getString("Name"));
        assertEquals("east", nbt.getList("palette", 10).getCompound(0).getCompound("Properties").getString("facing"));
        assertEquals(0, nbt.getList("missing", 10).size());
        assertEquals("", nbt.getCompound("missing").getString("Name"));
    }

    @Test
    public void preservesArraysAndStringLists() {
        CompoundTag tag = new CompoundTag();
        tag.putByteArray("b", new byte[] {1, -1});
        tag.putIntArray("i", new int[] {0, Integer.MAX_VALUE});
        tag.putLongArray("l", new long[] {Long.MIN_VALUE, 7});
        ListTag list = new ListTag();
        list.add(StringTag.valueOf("minecraft:plains"));
        tag.put("biomes", list);
        NBT.NBTCompound nbt = new NBT.NBTCompound(tag);
        assertArrayEquals(new byte[] {1, -1}, nbt.getByteArray("b"));
        assertArrayEquals(new int[] {0, Integer.MAX_VALUE}, nbt.getIntArray("i"));
        assertArrayEquals(new long[] {Long.MIN_VALUE, 7}, nbt.getLongArray("l"));
        assertEquals("minecraft:plains", nbt.getList("biomes", 8).getString(0));
        assertArrayEquals(new long[0], nbt.getLongArray("missing"));
    }

    @Test
    public void decodesPackedStatesAcrossLongBoundary() {
        // Five-bit palette entries use 12 values per long, with four padding bits.
        long[] data = new long[2];
        for (int i = 0; i < 20; i++) { data[i / 12] |= (long) i << ((i % 12) * 5); }
        NBT.OurBitStorage storage = new NBT.OurBitStorage(5, 20, data);
        for (int i = 0; i < 20; i++) { assertEquals(i, storage.get(i)); }
    }
}
