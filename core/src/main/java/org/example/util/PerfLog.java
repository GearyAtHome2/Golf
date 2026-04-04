package org.example.util;

import com.badlogic.gdx.Gdx;

/**
 * Lightweight generation profiler.
 * All times in milliseconds; memory figures are Java heap / native heap in MB.
 * Remove or gate behind a flag before shipping.
 */
public class PerfLog {

    private static final String TAG = "PERF";

    public static long now() {
        return System.nanoTime();
    }

    /** Log elapsed time and current heap snapshot since nanoStart. */
    public static void log(String section, long nanoStart) {
        long ms = (System.nanoTime() - nanoStart) / 1_000_000L;
        Gdx.app.log(TAG, String.format("[%-45s] %4dms  |  java heap: %3dMB  native: %3dMB",
                section, ms, Gdx.app.getJavaHeap() / (1024 * 1024), Gdx.app.getNativeHeap() / (1024 * 1024)));
    }

    /** Same as log() but with a visual separator — use for top-level totals. */
    public static void total(String section, long nanoStart) {
        long ms = (System.nanoTime() - nanoStart) / 1_000_000L;
        Gdx.app.log(TAG, String.format("=== [%-42s] %4dms  |  java heap: %3dMB  native: %3dMB ===",
                section, ms, Gdx.app.getJavaHeap() / (1024 * 1024), Gdx.app.getNativeHeap() / (1024 * 1024)));
    }

    /** Snapshot with no elapsed time — useful to mark state at a point in time. */
    public static void snapshot(String label) {
        Gdx.app.log(TAG, String.format("--- [%-45s]        |  java heap: %3dMB  native: %3dMB",
                label, Gdx.app.getJavaHeap() / (1024 * 1024), Gdx.app.getNativeHeap() / (1024 * 1024)));
    }
}
