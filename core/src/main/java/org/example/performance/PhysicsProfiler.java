package org.example.performance;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.utils.ObjectLongMap;

public class PhysicsProfiler {
    private static final ObjectLongMap<String> currentFrameTimings = new ObjectLongMap<>();
    private static final ObjectLongMap<String> accumulatedTimings = new ObjectLongMap<>();
    private static final ObjectLongMap<String> maxTimings = new ObjectLongMap<>();

    private static float logTimer = 0;
    private static int frameCount = 0;
    private static final float LOG_INTERVAL = 1.0f; // Log every 1 second

    public static void startFrame() {
        currentFrameTimings.clear();
    }

    public static void startSection(String name) {
        currentFrameTimings.put(name, System.nanoTime());
    }

    public static void endSection(String name) {
        long startTime = currentFrameTimings.get(name, 0);
        if (startTime != 0) {
            long duration = System.nanoTime() - startTime;

            // Add to totals for averaging
            accumulatedTimings.put(name, accumulatedTimings.get(name, 0) + duration);

            // Track the "spiker" (outlier)
            if (duration > maxTimings.get(name, 0)) {
                maxTimings.put(name, duration);
            }
        }
    }

    public static void updateAndLog(float delta) {
        frameCount++;
        logTimer += delta;

        if (logTimer >= LOG_INTERVAL) {
            logResults();
            resetAggregates();
        }
    }

    private static void logResults() {
        if (frameCount == 0) return;

        StringBuilder sb = new StringBuilder("\n--- Physics Profile (Last 1s Avg / Max) ---\n");
        sb.append(String.format("Frames tracked: %d\n", frameCount));

        for (ObjectLongMap.Entry<String> entry : accumulatedTimings.entries()) {
            double avgMs = (entry.value / (double) frameCount) / 1_000_000.0;
            double maxMs = maxTimings.get(entry.key, 0) / 1_000_000.0;

            sb.append(String.format("%-20s | Avg: %7.3f ms | Max: %7.3f ms\n",
                    entry.key, avgMs, maxMs));
        }

        Gdx.app.log("PROFILER", sb.toString());
    }

    private static void resetAggregates() {
        accumulatedTimings.clear();
        maxTimings.clear();
        logTimer = 0;
        frameCount = 0;
    }
}