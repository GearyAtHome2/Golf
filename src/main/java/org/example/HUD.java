package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;

public class HUD {
    private final SpriteBatch batch;
    private final BitmapFont font;
    private final Viewport viewport;
    private int shotCount = 0;

    public HUD() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    // --- RESTORED METHODS ---
    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }

    // Vital for Fullscreen support
    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void renderStartMenu() {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(3f);
        font.draw(batch, "ULTIMATE GOLF", 100, viewport.getWorldHeight() / 2f + 100);
        font.getData().setScale(1.5f);
        font.draw(batch, "PRESS ENTER TO TEE OFF", 100, viewport.getWorldHeight() / 2f);
        batch.end();
    }

    public void renderPauseMenu() {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(2.5f);
        font.draw(batch, "PAUSED", viewport.getWorldWidth()/2f - 80, viewport.getWorldHeight() - 100);
        font.getData().setScale(1.2f);
        font.draw(batch, "[R] RESET BALL  |  [N] NEW LEVEL  |  [ESC] RESUME", 50, 100);
        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(1.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Shots: " + shotCount, 40, viewport.getWorldHeight() - 40);
        font.draw(batch, String.format("Speed: %.1fx", gameSpeed), viewport.getWorldWidth() - 160, 60);
        batch.end();
    }

    public void renderVictory(int finalShots) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(4f);
        font.setColor(Color.GOLD);
        font.draw(batch, "HOLE IN ONE!", viewport.getWorldWidth() / 2f - 200, viewport.getWorldHeight() / 2f + 100);

        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Total Strokes: " + finalShots, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() / 2f);

        font.getData().setScale(1.2f);
        font.draw(batch, "Press [N] for a New Level", viewport.getWorldWidth() / 2f - 110, viewport.getWorldHeight() / 2f - 100);
        batch.end();
    }

    public void dispose() {
        batch.dispose();
        font.dispose();
    }
}