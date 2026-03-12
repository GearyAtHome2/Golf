package org.example.performance;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ObjectLongMap;

public class PhysicsProfiler {
    private static final ObjectLongMap<String> timings = new ObjectLongMap<>();
    private static long frameStart;

    public static void startFrame() {
        timings.clear();
        frameStart = System.nanoTime();
    }

    public static void startSection(String name) {
        timings.put(name, System.nanoTime());
    }

    public static void endSection(String name) {
        long startTime = timings.get(name, 0);
        if (startTime != 0) {
            timings.put(name, System.nanoTime() - startTime);
        }
    }

    public static void logResults() {
        StringBuilder sb = new StringBuilder("--- Physics Profile ---\n");
        for (ObjectLongMap.Entry<String> entry : timings.entries()) {
            sb.append(String.format("%s: %.3f ms\n", entry.key, entry.value / 1_000_000f));
        }
        Gdx.app.log("PROFILER", sb.toString());
    }
}