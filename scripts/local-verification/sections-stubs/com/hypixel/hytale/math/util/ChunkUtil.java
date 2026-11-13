package com.hypixel.hytale.math.util;
public final class ChunkUtil {
public static final int HEIGHT_SECTIONS = 10;
public static long indexChunk(int x,int z) { return ((long)x << 32) | (z & 0xffffffffL); }
public static int xOfChunkIndex(long index) { return (int)(index >> 32); }
public static int zOfChunkIndex(long index) { return (int)index; }
}
