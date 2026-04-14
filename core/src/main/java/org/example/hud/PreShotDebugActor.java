package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import org.example.Platform;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
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
    private final GlyphLayout layout = new GlyphLayout();

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

        boolean isAndroid = Platform.isAndroid();
        float screenW = Gdx.graphics.getWidth();
        float screenH = Gdx.graphics.getHeight();

        Vector3 ballPos = ball.getPosition();
        Vector3 aimDir = camera.direction;
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
        float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());

        // --- SUBTLE SCALING ---
        // Reduced base multiplier from 0.0013f to 0.0008f for a smaller, cleaner look
        float baseScale = (screenH * 0.0008f) * (isAndroid ? 1.4f : 1.0f);
        font.getData().setScale(baseScale);

        // --- CORNER ANCHORING ---
        float marginX = screenW * 0.02f;
        float lineSpacing = screenH * 0.025f; // Tighter spacing

        // Starting height: Just high enough to clear the spin-dot circle if it grows
        float currentY = screenH * 0.29f;

        // Draw from top to bottom (traditional style)
        drawShadowedText(batch, "TERRAIN: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z), marginX, currentY, Color.YELLOW);
        currentY -= lineSpacing;

        drawShadowedText(batch, String.format("DIFFICULTY: %.2fx", diff.terrainDifficulty), marginX, currentY, Color.YELLOW);
        currentY -= lineSpacing;

        drawShadowedText(batch, String.format("SLOPE: %.2fx", diff.slopeDifficulty), marginX, currentY, Color.YELLOW);
        currentY -= lineSpacing;

        if (Math.abs(sidePush) > 0.05f) {
            Color kickColor = sidePush > 0 ? Color.RED : Color.CYAN;
            String kickText = String.format("KICK: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush);
            drawShadowedText(batch, kickText, marginX, currentY, kickColor);
        }

        font.getData().setScale(1.0f);
    }

    private void drawShadowedText(Batch batch, String text, float x, float y, Color color) {
        float offset = font.getScaleX() * 0.8f; // Thinner shadow for smaller text

        font.setColor(0, 0, 0, color.a * 0.5f);
        font.draw(batch, text, x + offset, y - offset);

        font.setColor(color);
        font.draw(batch, text, x, y);
    }
}