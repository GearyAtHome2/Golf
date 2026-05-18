package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.JsonValue;

/**
 * A single shot broadcast over RTDB.
 *
 * Contains everything needed to replay the shot physics on a remote device:
 *   - Ball position at the moment of hit (start)
 *   - Velocity vector immediately after hit()  (= shootDir * power * powerMult)
 *   - Angular velocity (spin) set by ShotController after hit()
 *   - 1-indexed stroke number on the current hole
 *
 * RTDB path:  rooms/{code}/shots/{holeIndex}/{uid}/{strokeNum}
 * Short field keys are used to minimise payload size.
 */
public class ShotPacket {

    /** Ball position when hit() was called. */
    public float sx, sy, sz;

    /** Ball velocity immediately after hit() — encodes direction, power, and club multiplier. */
    public float vx, vy, vz;

    /** Angular velocity (spin) set by ShotController after hit(). */
    public float wx, wy, wz;

    /** 1-indexed stroke number on this hole (1 = first shot, 2 = second, …). */
    public int strokeNum;

    /** uid of the player — set locally when reading from RTDB, not written into the node. */
    public transient String uid;

    // -------------------------------------------------------------------------
    // Serialisation
    // -------------------------------------------------------------------------

    public String toJson() {
        Gdx.app.log("ShotDet", "SEND n=" + strokeNum
                + " pos=[0x" + Integer.toHexString(Float.floatToRawIntBits(sx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(sy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(sz)) + "]"
                + " vel=[0x" + Integer.toHexString(Float.floatToRawIntBits(vx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(vy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(vz)) + "]"
                + " spin=[0x" + Integer.toHexString(Float.floatToRawIntBits(wx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(wy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(wz)) + "]");
        return "{\"sx\":" + sx + ",\"sy\":" + sy + ",\"sz\":" + sz
             + ",\"vx\":" + vx + ",\"vy\":" + vy + ",\"vz\":" + vz
             + ",\"wx\":" + wx + ",\"wy\":" + wy + ",\"wz\":" + wz
             + ",\"n\":"  + strokeNum + "}";
    }

    public static ShotPacket fromJson(JsonValue j, String uid) {
        ShotPacket p = new ShotPacket();
        p.sx = j.getFloat("sx", 0f); p.sy = j.getFloat("sy", 0f); p.sz = j.getFloat("sz", 0f);
        p.vx = j.getFloat("vx", 0f); p.vy = j.getFloat("vy", 0f); p.vz = j.getFloat("vz", 0f);
        p.wx = j.getFloat("wx", 0f); p.wy = j.getFloat("wy", 0f); p.wz = j.getFloat("wz", 0f);
        p.strokeNum = j.getInt("n", 0);
        p.uid = uid;
        Gdx.app.log("ShotDet", "RECV n=" + p.strokeNum
                + " pos=[0x" + Integer.toHexString(Float.floatToRawIntBits(p.sx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.sy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.sz)) + "]"
                + " vel=[0x" + Integer.toHexString(Float.floatToRawIntBits(p.vx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.vy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.vz)) + "]"
                + " spin=[0x" + Integer.toHexString(Float.floatToRawIntBits(p.wx))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.wy))
                + " 0x" + Integer.toHexString(Float.floatToRawIntBits(p.wz)) + "]");
        return p;
    }
}
