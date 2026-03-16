package org.example.glamour;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

import java.util.ArrayList;
import java.util.List;

public class WindManager {
    private static class WindStreak {
        Vector3 worldPos = new Vector3();
        float life;
        float maxLife;
        float speedOffset;
    }

    private final List<WindStreak> streaks = new ArrayList<>();
    private final int MAX_STREAKS = 60; // Increased pool for better density
    private final float SPAWN_RADIUS = 30f; // Tighter radius to keep them in view
    private final ShapeRenderer shapeRenderer;

    private final Vector3 tempStart = new Vector3();
    private final Vector3 tempEnd = new Vector3();
    private final Vector3 visualWindDir = new Vector3();

    public WindManager() {
        shapeRenderer = new ShapeRenderer();
        for (int i = 0; i < MAX_STREAKS; i++) {
            streaks.add(new WindStreak());
        }
    }

    private void resetStreak(WindStreak s, Vector3 cameraPos) {
        // Reduced the vertical spread and overall radius to ensure visibility
        s.worldPos.set(
                cameraPos.x + MathUtils.random(-SPAWN_RADIUS, SPAWN_RADIUS),
                cameraPos.y + MathUtils.random(-3f, 10f),
                cameraPos.z + MathUtils.random(-SPAWN_RADIUS, SPAWN_RADIUS)
        );
        s.maxLife = MathUtils.random(1.0f, 1.8f);
        s.life = 0;
        s.speedOffset = MathUtils.random(0.9f, 1.1f);
    }

    public void update(float delta, Vector3 wind, Vector3 cameraPos) {
        float trueWindSpeed = wind.len();
        if (trueWindSpeed < 0.1f) return;

        // Visual speed mapping
        float visualSpeed = MathUtils.map(0f, 30f, 6.0f, 25.0f, trueWindSpeed);
        visualSpeed = MathUtils.clamp(visualSpeed, 6.0f, 25.0f);

        visualWindDir.set(wind).nor();

        for (WindStreak s : streaks) {
            s.life += delta;

            s.worldPos.add(
                    visualWindDir.x * visualSpeed * delta * s.speedOffset,
                    visualWindDir.y * visualSpeed * delta * s.speedOffset,
                    visualWindDir.z * visualSpeed * delta * s.speedOffset
            );

            float distToCam = s.worldPos.dst(cameraPos);
            // Respawn if too far or life ended
            if (s.life > s.maxLife || distToCam > SPAWN_RADIUS * 1.3f) {
                resetStreak(s, cameraPos);
            }
        }
    }

    public void render(Camera camera, Vector3 wind) {
        float trueWindSpeed = wind.len();
        if (trueWindSpeed < 0.5f) return;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shapeRenderer.setProjectionMatrix(camera.combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);

        // Increase the multiplier for more streaks, and set a higher minimum (15)
        int streaksToShow = MathUtils.clamp((int)(trueWindSpeed * 3.0f) + 10, 15, MAX_STREAKS);

        for (int i = 0; i < streaksToShow; i++) {
            WindStreak s = streaks.get(i);
            float lifePercent = s.life / s.maxLife;
            float fade = MathUtils.sin(lifePercent * MathUtils.PI);

            // Boosted alpha: 0.15 to 0.4 range is much more visible
            float baseAlpha = MathUtils.map(0f, 30f, 0.15f, 0.40f, trueWindSpeed);
            shapeRenderer.setColor(1, 1, 1, baseAlpha * fade);

            tempStart.set(s.worldPos);

            // Slightly longer lines
            float visualSpeed = MathUtils.map(0f, 30f, 6.0f, 25.0f, trueWindSpeed);
            float lengthFactor = visualSpeed * 0.25f;

            tempEnd.set(visualWindDir).scl(lengthFactor).add(tempStart);

            shapeRenderer.line(tempStart, tempEnd);
        }
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    public void dispose() {
        shapeRenderer.dispose();
    }
}