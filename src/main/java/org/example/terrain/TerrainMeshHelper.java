package org.example.terrain;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class TerrainMeshHelper {

    private static final Vector3 LIGHT_DIR = new Vector3(-0.5f, 1.0f, -0.5f).nor();
    private static final Color ROCK_COLOR = new Color(0.4f, 0.42f, 0.45f, 1f);

    public static void buildTriangle(MeshPartBuilder builder, Vector3 v1, Vector3 v2, Vector3 v3,
                                     Terrain.TerrainType type, float minH, float maxH) {

        Vector3 normal = new Vector3(v3).sub(v1).crs(new Vector3(v2).sub(v1)).nor();
        float slope = 1f - normal.y;
        Color color = new Color();

        if (type == Terrain.TerrainType.GREEN) {
            float avgY = (v1.y + v2.y + v3.y) / 3.0f;
            float range = maxH - minH;
            float t = (range > 0.01f) ? MathUtils.clamp((avgY - minH) / range, 0f, 1f) : 0.5f;
            color.set(0.1f * t, 0.4f + (0.4f * t), 0.1f * t, 1f);
        } else {
            color.set(type.color);
        }

        // Apply Shading
        float dot = normal.dot(LIGHT_DIR);
        float shading = MathUtils.clamp(0.8f + (dot * 0.3f) - (slope * 1.2f), 0.2f, 1.1f);
        color.mul(shading, shading, shading, 1f);

        builder.setColor(color);
        builder.triangle(v1, v3, v2);
    }
}