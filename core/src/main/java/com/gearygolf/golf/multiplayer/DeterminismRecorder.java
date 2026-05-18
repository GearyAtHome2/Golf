package com.gearygolf.golf.multiplayer;

import java.util.ArrayList;
import java.util.List;

/**
 * Records per-step physics state (position/velocity/spin) for a single shot,
 * serialises to CSV, and compares two recordings for bit-identical equality.
 *
 * No LibGDX dependencies — suitable for both JUnit tests and on-device use.
 *
 * CSV format:
 *   Line 0:  # header line with shot inputs (human-readable, ignored on parse)
 *   Line 1+: stepIndex,px,py,pz,vx,vy,vz,sx,sy,sz
 *            where each float is stored as its raw IEEE 754 int bits (decimal),
 *            guaranteeing exact round-trip with no rounding.
 */
public class DeterminismRecorder {

    // ── Snapshot ────────────────────────────────────────────────────────────────

    public static class Snapshot {
        public final int   step;
        public final float px, py, pz;
        public final float vx, vy, vz;
        public final float sx, sy, sz;

        public Snapshot(int step,
                        float px, float py, float pz,
                        float vx, float vy, float vz,
                        float sx, float sy, float sz) {
            this.step = step;
            this.px = px; this.py = py; this.pz = pz;
            this.vx = vx; this.vy = vy; this.vz = vz;
            this.sx = sx; this.sy = sy; this.sz = sz;
        }

        /** Bit-exact equality (NaN-safe, distinguishes +0 from -0). */
        public boolean bitEquals(Snapshot o) {
            return step == o.step
                && Float.floatToRawIntBits(px) == Float.floatToRawIntBits(o.px)
                && Float.floatToRawIntBits(py) == Float.floatToRawIntBits(o.py)
                && Float.floatToRawIntBits(pz) == Float.floatToRawIntBits(o.pz)
                && Float.floatToRawIntBits(vx) == Float.floatToRawIntBits(o.vx)
                && Float.floatToRawIntBits(vy) == Float.floatToRawIntBits(o.vy)
                && Float.floatToRawIntBits(vz) == Float.floatToRawIntBits(o.vz)
                && Float.floatToRawIntBits(sx) == Float.floatToRawIntBits(o.sx)
                && Float.floatToRawIntBits(sy) == Float.floatToRawIntBits(o.sy)
                && Float.floatToRawIntBits(sz) == Float.floatToRawIntBits(o.sz);
        }

        /** Euclidean distance between the two position vectors, in metres. */
        public float positionDeltaMetres(Snapshot o) {
            float dx = px - o.px, dy = py - o.py, dz = pz - o.pz;
            return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }

    // ── Recording ────────────────────────────────────────────────────────────────

    public static class Recording {
        public String            header = "";
        public final List<Snapshot> steps  = new ArrayList<>();

        public String toCsv() {
            StringBuilder sb = new StringBuilder();
            sb.append("# ").append(header).append('\n');
            for (Snapshot s : steps) {
                sb.append(s.step)
                  .append(',').append(Float.floatToRawIntBits(s.px))
                  .append(',').append(Float.floatToRawIntBits(s.py))
                  .append(',').append(Float.floatToRawIntBits(s.pz))
                  .append(',').append(Float.floatToRawIntBits(s.vx))
                  .append(',').append(Float.floatToRawIntBits(s.vy))
                  .append(',').append(Float.floatToRawIntBits(s.vz))
                  .append(',').append(Float.floatToRawIntBits(s.sx))
                  .append(',').append(Float.floatToRawIntBits(s.sy))
                  .append(',').append(Float.floatToRawIntBits(s.sz))
                  .append('\n');
            }
            return sb.toString();
        }

        public static Recording fromCsv(String csv) {
            Recording rec = new Recording();
            for (String line : csv.split("\n")) {
                line = line.trim();
                if (line.isEmpty()) continue;
                if (line.startsWith("#")) { rec.header = line.substring(1).trim(); continue; }
                String[] p = line.split(",");
                if (p.length != 10) continue;
                rec.steps.add(new Snapshot(
                    Integer.parseInt(p[0]),
                    Float.intBitsToFloat(Integer.parseInt(p[1])),
                    Float.intBitsToFloat(Integer.parseInt(p[2])),
                    Float.intBitsToFloat(Integer.parseInt(p[3])),
                    Float.intBitsToFloat(Integer.parseInt(p[4])),
                    Float.intBitsToFloat(Integer.parseInt(p[5])),
                    Float.intBitsToFloat(Integer.parseInt(p[6])),
                    Float.intBitsToFloat(Integer.parseInt(p[7])),
                    Float.intBitsToFloat(Integer.parseInt(p[8])),
                    Float.intBitsToFloat(Integer.parseInt(p[9]))
                ));
            }
            return rec;
        }
    }

    // ── CompareResult ────────────────────────────────────────────────────────────

    public static class CompareResult {
        public final boolean identical;
        /** -1 if identical */
        public final int     firstDivergentStep;
        /** cm; position delta at the last step of the shorter recording */
        public final float   restDivergenceCm;
        public final String  report;

        private CompareResult(boolean identical, int firstDivergentStep,
                              float restDivergenceCm, String report) {
            this.identical          = identical;
            this.firstDivergentStep = firstDivergentStep;
            this.restDivergenceCm   = restDivergenceCm;
            this.report             = report;
        }
    }

    public static CompareResult compare(Recording a, Recording b) {
        int len = Math.min(a.steps.size(), b.steps.size());
        if (len == 0) {
            return new CompareResult(false, 0, 0f, "FAIL: one or both recordings are empty");
        }

        int firstDiv = -1;
        for (int i = 0; i < len; i++) {
            if (!a.steps.get(i).bitEquals(b.steps.get(i))) {
                firstDiv = i;
                break;
            }
        }

        float restDelta = a.steps.get(len - 1).positionDeltaMetres(b.steps.get(len - 1)) * 100f; // → cm

        if (firstDiv == -1 && a.steps.size() == b.steps.size()) {
            return new CompareResult(true, -1, restDelta,
                String.format("PASS  %d steps  rest delta %.2f cm", len, restDelta));
        }

        if (firstDiv == -1) {
            // Same through the shorter one but lengths differ
            firstDiv = len;
        }

        Snapshot sa = a.steps.get(firstDiv < a.steps.size() ? firstDiv : a.steps.size() - 1);
        Snapshot sb = b.steps.get(firstDiv < b.steps.size() ? firstDiv : b.steps.size() - 1);
        float divDelta = sa.positionDeltaMetres(sb) * 100f;

        return new CompareResult(false, firstDiv, restDelta,
            String.format("FAIL  first divergence at step %d (%.2f cm)  rest delta %.2f cm  steps A=%d B=%d",
                firstDiv, divDelta, restDelta, a.steps.size(), b.steps.size()));
    }

    // ── Instance: recording hook called from Ball ─────────────────────────────────

    private final Recording recording = new Recording();

    public void setHeader(String header) { recording.header = header; }

    /** Called by Ball after each processPhysicsStep() / quantizeState(). */
    public void onStep(int step,
                       float px, float py, float pz,
                       float vx, float vy, float vz,
                       float sx, float sy, float sz) {
        recording.steps.add(new Snapshot(step, px, py, pz, vx, vy, vz, sx, sy, sz));
    }

    /** Returns the completed recording and resets internal state for reuse. */
    public Recording finish() {
        Recording done = new Recording();
        done.header = recording.header;
        done.steps.addAll(recording.steps);
        recording.header = "";
        recording.steps.clear();
        return done;
    }
}
