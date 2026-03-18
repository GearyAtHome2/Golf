package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import org.example.ball.Ball;
import org.example.ball.ShotDifficulty;
import org.example.terrain.Terrain;

public class PreShotDebugActor extends Actor {
    private Terrain terrain;
    private Ball ball;
    private Camera camera;
    private final BitmapFont font;

    public PreShotDebugActor(BitmapFont font) {
        this.font = font;
    }

    public void update(Terrain terrain, Ball ball, Camera camera) {
        this.terrain = terrain;
        this.ball = ball;
        this.camera = camera;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (terrain == null || ball == null || camera == null) return;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        Vector3 ballPos = ball.getPosition();
        Vector3 aimDir = camera.direction;
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);

        float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());

        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();

        float x = getX();
        float y;
        float lineSpacing;

        if (isAndroid) {
            // Mobile: Scaled up and positioned to clear the Spin-Stick
            font.getData().setScale(1.2f);
            y = getY() + getHeight() - 20;
            lineSpacing = 35f;
        } else {
            // Desktop: Restore original tight corner look
            font.getData().setScale(1.0f);
            // On desktop, we want it closer to the bottom edge
            y = getY() + 85;
            lineSpacing = 22f;
        }

        drawShadowedText(batch, "TERRAIN: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z), x, y, Color.YELLOW);
        drawShadowedText(batch, String.format("DIFFICULTY: %.2fx", diff.terrainDifficulty), x, y - lineSpacing, Color.YELLOW);
        drawShadowedText(batch, String.format("SLOPE: %.2fx", diff.slopeDifficulty), x, y - (lineSpacing * 2), Color.YELLOW);

        if (Math.abs(sidePush) > 0.05f) {
            Color kickColor = sidePush > 0 ? Color.RED : Color.CYAN;
            drawShadowedText(batch, String.format("KICK: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), x, y - (lineSpacing * 3), kickColor);
        }

        font.getData().setScale(oldScaleX, oldScaleY);
    }

    private void drawShadowedText(Batch batch, String text, float x, float y, Color color) {
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.7f);
        font.draw(batch, text, x + 1, y - 1);

        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}