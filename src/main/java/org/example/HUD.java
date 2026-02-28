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

    private float lastDisplayedSpin = 0f;

    public HUD() {
        batch = new SpriteBatch();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void renderStartMenu(int selection) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        font.getData().setScale(3f);
        font.setColor(Color.WHITE);
        font.draw(batch, "ULTIMATE GOLF", centerX - 180, centerY + 150);

        font.getData().setScale(1.5f);

        // Selection 0: Play
        font.setColor(selection == 0 ? Color.YELLOW : Color.WHITE);
        String playText = (selection == 0 ? "> " : "  ") + "PLAY GAME";
        font.draw(batch, playText, centerX - 80, centerY);

        // Selection 1: Practice
        font.setColor(selection == 1 ? Color.YELLOW : Color.WHITE);
        String practiceText = (selection == 1 ? "> " : "  ") + "PRACTICE RANGE";
        font.draw(batch, practiceText, centerX - 80, centerY - 60);

        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 150);

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

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(1.5f);

        // --- Top Left: Mode & Shots ---
        if (isPractice) {
            font.setColor(Color.CYAN);
            font.draw(batch, "PRACTICE MODE", 40, viewport.getWorldHeight() - 40);
            font.setColor(Color.WHITE);
            font.draw(batch, "Shots: " + shotCount, 40, viewport.getWorldHeight() - 80);
        } else {
            font.setColor(Color.WHITE);
            font.draw(batch, "Shots: " + shotCount, 40, viewport.getWorldHeight() - 40);
        }

        // --- Bottom Right: Equipment & Physics ---
        float rightX = viewport.getWorldWidth() - 250;

        font.setColor(Color.WHITE);
        font.draw(batch, "Club: " + currentClub.name(), rightX, 140);

        float currentSpin = ball.getSpin();
        // Scaling to match your HUD's expected "k RPM" look
        float krpm = (currentSpin * 100f) / 1000f;

        if (currentSpin > 0.1f) {
            if (currentSpin > lastDisplayedSpin) {
                font.setColor(Color.GREEN);
            } else {
                font.setColor(Color.RED);
            }
            font.draw(batch, String.format("Spin: %.1fk RPM", krpm), rightX, 100);
        } else {
            font.setColor(Color.GRAY);
            font.draw(batch, "Spin: 0k RPM", rightX, 100);
        }

        lastDisplayedSpin = currentSpin;

        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Speed: %.1fx", gameSpeed), rightX, 60);

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