package org.example.terrain;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class TerrainMeshHelper {

    // --- LIGHTING CONFIGURATION ---
    private static final Vector3 LIGHT_DIR = new Vector3(-0.9f, 0.4f, -0.6f).nor();

    // --- SHADING TWEAKS ---
    private static final float SHADING_POWER = 1.4f;
    private static final float HALF_LAMBERT_BIAS = 0.2f;
    private static final float MIN_SHADE = 0.75f;
    private static final float MAX_SHADE = 1.65f;

    // --- MOWING PATTERN (STRIPES) ---
    private static final float STRIPE_WIDTH = 3.5f;
    private static final float STRIPE_INTENSITY = 0.05f;

    /**
     * Tweak this for the "In-Between" look:
     * 0.0 = Pure Sine (Very blurry)
     * 0.5 = Balanced (Noticeable edge but soft)
     * 1.0 = Pure Blocky (Sharp edges)
     */
    private static final float MOWING_SHARPNESS = 0.6f;

    // --- NOISE STRENGTHS ---
    private static final float STONE_NOISE_STRENGTH = 0.12f;
    private static final float ROUGH_NOISE_STRENGTH = 0.06f;
    private static final float SAND_NOISE_STRENGTH = 0.04f;

    // --- SHADOW TINTING ---
    private static final float SHADOW_THRESHOLD = 0.75f;
    private static final float SHADOW_LERP_STRENGTH = 0.18f;
    private static final Color COOL_SHADOW_COLOR = new Color(0.04f, 0.08f, 0.2f, 1f);

    public static void buildTriangle(MeshPartBuilder builder, Vector3 v1, Vector3 v2, Vector3 v3,
                                     Terrain.TerrainType type, float minH, float maxH) {

        Vector3 edge1 = new Vector3(v3).sub(v1);
        Vector3 edge2 = new Vector3(v2).sub(v1);
        Vector3 normal = edge1.crs(edge2).nor();

        float slope = 1f - normal.y;
        Color color = new Color();

        float worldSeed = (v1.x * 12.9898f + v1.z * 78.233f);
        float noise = (Math.abs(MathUtils.sin(worldSeed) * 43758.5453f)) % 1.0f;
        float textureMod = 1.0f;

        if (type == Terrain.TerrainType.GREEN) {
            float avgY = (v1.y + v2.y + v3.y) / 3.0f;
            float range = maxH - minH;
            float t = (range > 0.01f) ? MathUtils.clamp((avgY - minH) / range, 0f, 1f) : 0.5f;
            color.set(0.05f + (0.1f * t), 0.5f + (0.3f * t), 0.1f + (0.1f * t), 1f);
        } else {
            color.set(type.color);
        }

        switch (type) {
            case FAIRWAY:
            case TEE:
                // 1. Create a periodic triangle wave (0 to 1 and back to 0)
                // This eliminates the "sawtooth" jump
                float triangleWave = Math.abs((v1.z % (STRIPE_WIDTH * 2)) / STRIPE_WIDTH - 1.0f);

                // 2. Apply a smoothstep filter based on SHARPNESS
                // This creates the "flat top" and "flat bottom" with a tunable edge
                float edgeWidth = (1.0f - MOWING_SHARPNESS);
                float tStripe = smoothStepFilter(triangleWave, 0.5f, edgeWidth);

                textureMod = MathUtils.lerp(1.0f - STRIPE_INTENSITY, 1.0f + STRIPE_INTENSITY, tStripe);
                break;
            case STONE:
                textureMod = 1.0f + (noise * STONE_NOISE_STRENGTH);
                break;
            case ROUGH:
                textureMod = 1.0f + ((noise - 0.5f) * ROUGH_NOISE_STRENGTH);
                break;
            case SAND:
                textureMod = 1.0f + ((noise - 0.5f) * SAND_NOISE_STRENGTH);
                break;
        }

        float dot = normal.dot(LIGHT_DIR);
        float halfLambert = (dot * HALF_LAMBERT_BIAS + (1f - HALF_LAMBERT_BIAS));
        float lightIntensity = (halfLambert * halfLambert) * SHADING_POWER;

        float slopeFactor = 1.0f / (1.0f + (slope * 1.2f));
        float finalShading = MathUtils.clamp(lightIntensity * slopeFactor * textureMod, MIN_SHADE, MAX_SHADE);

        if (finalShading < SHADOW_THRESHOLD) {
            color.lerp(COOL_SHADOW_COLOR, SHADOW_LERP_STRENGTH);
        }

        color.mul(finalShading, finalShading, finalShading, 1f);
        if (finalShading > 1.2f) {
            color.add(0.02f, 0.02f, 0f, 0f);
        }

        builder.setColor(color);
        builder.triangle(v1, v3, v2);
    }

    /**
     * Replaces the old smoothPulse to fix the sawtooth issue.
     * Uses a standard smoothstep approach to sharpen a triangle wave.
     */
    private static float smoothStepFilter(float val, float threshold, float edge) {
        if (edge <= 0.001f) return val > threshold ? 1f : 0f;
        float v = MathUtils.clamp((val - (threshold - edge / 2f)) / edge, 0f, 1f);
        return v * v * (3f - 2f * v); // Hermitian interpolation
    }
}