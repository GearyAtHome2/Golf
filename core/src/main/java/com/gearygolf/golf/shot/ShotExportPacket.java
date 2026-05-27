package com.gearygolf.golf.shot;

import com.badlogic.gdx.utils.Base64Coder;
import com.gearygolf.golf.terrain.TerrainGenVersion;
import com.gearygolf.golf.terrain.level.LevelData;

import java.nio.ByteBuffer;

/**
 * Binary-encodes a shot snapshot into a compact clipboard string (~68 chars).
 *
 * Format: "GS1:" prefix + base64(48 bytes)
 *
 * Byte layout (big-endian):
 *   [0]     format version  (currently 1)
 *   [1]     TerrainGenVersion ordinal
 *   [2]     LevelData.Archetype ordinal
 *   [3-10]  hole seed (long)
 *   [11]    hole index (0-17, display context)
 *   [12-23] sx, sy, sz  — ball position at hit()
 *   [24-35] vx, vy, vz  — velocity after hit() + spin applied
 *   [36-47] wx, wy, wz  — angular velocity (spin)
 *
 * The archetype is stored explicitly so future archetype pool additions don't
 * break existing strings. TerrainGenVersion gates which generator code runs,
 * so generator changes don't break existing strings either.
 */
public final class ShotExportPacket {

    public static final String PREFIX = "GS1:";
    private static final byte FORMAT_VERSION = 1;
    private static final int BYTE_COUNT = 48;

    private ShotExportPacket() {}

    /**
     * Encodes a shot into a clipboard-ready string.
     * Returns null if archetype is null (e.g. practice range has no archetype).
     */
    public static String encode(TerrainGenVersion genVersion,
                                LevelData.Archetype archetype,
                                long holeSeed, int holeIndex,
                                float sx, float sy, float sz,
                                float vx, float vy, float vz,
                                float wx, float wy, float wz) {
        if (archetype == null) return null;
        ByteBuffer buf = ByteBuffer.allocate(BYTE_COUNT);
        buf.put(FORMAT_VERSION);
        buf.put((byte) genVersion.ordinal());
        buf.put((byte) archetype.ordinal());
        buf.putLong(holeSeed);
        buf.put((byte) holeIndex);
        buf.putFloat(sx); buf.putFloat(sy); buf.putFloat(sz);
        buf.putFloat(vx); buf.putFloat(vy); buf.putFloat(vz);
        buf.putFloat(wx); buf.putFloat(wy); buf.putFloat(wz);
        return PREFIX + new String(Base64Coder.encode(buf.array()));
    }

    /** Returns true if the string looks like a valid export (prefix + correct length). */
    public static boolean isValid(String s) {
        if (s == null || !s.startsWith(PREFIX)) return false;
        String payload = s.substring(PREFIX.length()).trim();
        // base64 of 48 bytes = 64 chars
        return payload.length() == 64;
    }

    /** Decoded data from an import string. */
    public static final class ShotReplayData {
        public final TerrainGenVersion genVersion;
        public final LevelData.Archetype archetype;
        public final long holeSeed;
        public final int holeIndex;
        public final float sx, sy, sz;
        public final float vx, vy, vz;
        public final float wx, wy, wz;

        ShotReplayData(TerrainGenVersion genVersion, LevelData.Archetype archetype,
                       long holeSeed, int holeIndex,
                       float sx, float sy, float sz,
                       float vx, float vy, float vz,
                       float wx, float wy, float wz) {
            this.genVersion = genVersion; this.archetype = archetype;
            this.holeSeed = holeSeed;    this.holeIndex = holeIndex;
            this.sx = sx; this.sy = sy; this.sz = sz;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.wx = wx; this.wy = wy; this.wz = wz;
        }
    }

    /**
     * Decodes a shot export string. Returns null if the string is invalid or unrecognised.
     */
    public static ShotReplayData decode(String s) {
        if (!isValid(s)) return null;
        try {
            byte[] bytes = Base64Coder.decode(s.substring(PREFIX.length()).trim().toCharArray());
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.wrap(bytes);
            buf.get(); // format version byte — reserved for future use
            int genOrd      = buf.get() & 0xFF;
            int archetypeOrd = buf.get() & 0xFF;
            long holeSeed   = buf.getLong();
            int holeIndex   = buf.get() & 0xFF;
            float sx = buf.getFloat(), sy = buf.getFloat(), sz = buf.getFloat();
            float vx = buf.getFloat(), vy = buf.getFloat(), vz = buf.getFloat();
            float wx = buf.getFloat(), wy = buf.getFloat(), wz = buf.getFloat();

            TerrainGenVersion genVersion = TerrainGenVersion.fromOrdinal(genOrd);
            LevelData.Archetype[] archetypes = LevelData.Archetype.values();
            if (archetypeOrd < 0 || archetypeOrd >= archetypes.length) return null;
            LevelData.Archetype archetype = archetypes[archetypeOrd];

            return new ShotReplayData(genVersion, archetype, holeSeed, holeIndex,
                    sx, sy, sz, vx, vy, vz, wx, wy, wz);
        } catch (Exception e) {
            return null;
        }
    }
}
