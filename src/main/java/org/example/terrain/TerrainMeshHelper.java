package org.example.terrain;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class TerrainMeshHelper {

    // --- LIGHTING CONFIGURATION ---
    // TWEAKED: Shifted Z from -0.8 to -0.6 to break 45-degree symmetry
    private static final Vector3 LIGHT_DIR = new Vector3(-0.9f, 0.4f, -0.6f).nor();

    // --- SHADING TWEAKS ---
    private static final float SHADING_POWER = 1.4f;
    private static final float HALF_LAMBERT_BIAS = 0.2f;
    private static final float MIN_SHADE = 0.8f;
    private static final float MAX_SHADE = 1.6f;

    // --- MOWING PATTERN (STRIPES) ---
    private static final float STRIPE_WIDTH = 4.0f;
    private static final float STRIPE_INTENSITY = 0.027f;

    // --- STONE NOISE ---
    private static final float STONE_NOISE_STRENGTH = 0.08f; // +/- 8% variance

    // --- SHADOW TINTING ---
    private static final float SHADOW_THRESHOLD = 0.7f;
    private static final float SHADOW_LERP_STRENGTH = 0.15f;
    private static final Color COOL_SHADOW_COLOR = new Color(0.05f, 0.1f, 0.15f, 1f);

    public static void buildTriangle(MeshPartBuilder builder, Vector3 v1, Vector3 v2, Vector3 v3,
                                     Terrain.TerrainType type, float minH, float maxH) {

        Vector3 edge1 = new Vector3(v3).sub(v1);
        Vector3 edge2 = new Vector3(v2).sub(v1);
        Vector3 normal = edge1.crs(edge2).nor();

        float slope = 1f - normal.y;
        Color color = new Color();

        // 1. Base Color Selection
        if (type == Terrain.TerrainType.GREEN) {
            float avgY = (v1.y + v2.y + v3.y) / 3.0f;
            float range = maxH - minH;
            float t = (range > 0.01f) ? MathUtils.clamp((avgY - minH) / range, 0f, 1f) : 0.5f;
            color.set(0.1f * t, 0.4f + (0.4f * t), 0.1f * t, 1f);
        } else {
            color.set(type.color);
        }

        // 2. Lighting Calculation
        float dot = normal.dot(LIGHT_DIR);
        float halfLambert = (dot * HALF_LAMBERT_BIAS + (1f - HALF_LAMBERT_BIAS));
        float lightIntensity = (halfLambert * halfLambert) * SHADING_POWER;

        // 3. Texture/Pattern Logic
        float textureMod = 1.0f;

        if (type == Terrain.TerrainType.FAIRWAY || type == Terrain.TerrainType.TEE) {
            // Mowing Stripes
            boolean isDarkStripe = ((int) Math.floor(v1.z / STRIPE_WIDTH) % 2 == 0);
            textureMod = isDarkStripe ? (1.0f + STRIPE_INTENSITY) : (1.0f - STRIPE_INTENSITY);
        } else if (type == Terrain.TerrainType.STONE) {
            // Deterministic Stone Noise based on world position
            // We use the sum of vertices to ensure the whole triangle gets one consistent "rock shade"
            float seed = (v1.x * 12.9898f + v1.y * 45.323f + v1.z * 78.233f);
            float noise = (MathUtils.sin(seed) * 43758.5453f) % 1.0f;
            textureMod = 1.0f + (noise * STONE_NOISE_STRENGTH);
        }

        // 4. Slope Dimming
        float slopeFactor = 1.0f / (1.0f + (slope * 0.8f));

        // Combine and Clamp
        float finalShading = MathUtils.clamp(lightIntensity * slopeFactor * textureMod, MIN_SHADE, MAX_SHADE);

        // 5. Atmospheric Shadowing
        if (finalShading < SHADOW_THRESHOLD) {
            color.lerp(COOL_SHADOW_COLOR, SHADOW_LERP_STRENGTH);
        }

        // Apply final multipliers
        color.mul(finalShading, finalShading, finalShading, 1f);

        builder.setColor(color);
        builder.triangle(v1, v3, v2);
    }
}