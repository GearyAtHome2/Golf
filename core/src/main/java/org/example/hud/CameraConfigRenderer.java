package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;
import org.example.input.GameInputProcessor;

public class CameraConfigRenderer {

    private float scrollY = 0;
    private final float MAX_SCROLL = 200f;
    private int selectedIndex = 0;
    private final int TOTAL_SETTINGS = 5;

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameConfig config, GameInputProcessor input) {
        handleNavigation(input);
        handleInteraction(config, input);

        float delta = Gdx.graphics.getDeltaTime();
        float scrollSpeed = 450f * delta;

        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) {
            scrollY = Math.max(0, scrollY - scrollSpeed);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) {
            scrollY = Math.min(MAX_SCROLL, scrollY + scrollSpeed);
        }

        float centerX = viewport.getWorldWidth() / 2f;
        float boxW = 800;
        float boxH = viewport.getWorldHeight() - 180;
        float boxX = centerX - boxW / 2f;
        float boxY = 120;

        // Draw Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.05f, 0.12f, 0.95f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        Rectangle scissor = new Rectangle();
        Rectangle area = new Rectangle(boxX, boxY, boxW, boxH);
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), area, scissor);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        if (ScissorStack.pushScissors(scissor)) {
            float currentY = boxY + boxH - 50 + scrollY;

            // --- TITLE ---
            font.getData().setScale(2.2f);
            font.setColor(Color.GOLD);
            font.draw(batch, "CAMERA CONFIGURATION", centerX - 220, currentY);
            currentY -= 80;

            font.getData().setScale(1.1f);

            // 0: Control Style
            drawSettingRow(batch, font, "Control Style", config.cameraSettings.controlStyle.name(), centerX, currentY, selectedIndex == 0);
            currentY -= 45;

            // 1: Invert X
            drawSettingRow(batch, font, "Invert X-Axis", config.cameraSettings.invertX ? "ON" : "OFF", centerX, currentY, selectedIndex == 1);
            currentY -= 45;

            // 2: Invert Y
            drawSettingRow(batch, font, "Invert Y-Axis", config.cameraSettings.invertY ? "ON" : "OFF", centerX, currentY, selectedIndex == 2);
            currentY -= 45;

            // 3: Sensitivity
            drawSettingRow(batch, font, "Mouse Sensitivity", String.format("%.2f", config.cameraSettings.mouseSensitivity), centerX, currentY, selectedIndex == 3);
            currentY -= 45;

            // 4: Smoothing
            drawSettingRow(batch, font, "Ball tracking speed", String.format("%.1f", config.cameraSettings.lerpSpeed), centerX, currentY, selectedIndex == 4);
            currentY -= 60;

            font.setColor(Color.CYAN);
            font.draw(batch, "Use [UP/DOWN] or [W/S] to select", centerX - 330, currentY);
            currentY -= 25;
            font.draw(batch, "Use [LEFT/RIGHT] or [A/D] to change values", centerX - 330, currentY);

            batch.flush();
            ScissorStack.popScissors();
        }

        font.getData().setScale(1.1f);
        font.setColor(Color.YELLOW);
        font.draw(batch, "[ESC] to Return to Pause Menu", centerX - 140, boxY - 30);

        batch.end();
    }

    private void handleNavigation(GameInputProcessor input) {
        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_UP)) {
            selectedIndex = (selectedIndex - 1 + TOTAL_SETTINGS) % TOTAL_SETTINGS;
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.MENU_DOWN)) {
            selectedIndex = (selectedIndex + 1) % TOTAL_SETTINGS;
        }
    }

    private void handleInteraction(GameConfig config, GameInputProcessor input) {
        boolean left = input.isActionJustPressed(GameInputProcessor.Action.CYCLE_ANIMATION);
        boolean right = input.isActionJustPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY);

        switch (selectedIndex) {
            case 0: // Control Style
                if (left || right) {
                    int next = (config.cameraSettings.controlStyle.ordinal() + 1) % GameConfig.CameraConfig.ControlStyle.values().length;
                    config.cameraSettings.controlStyle = GameConfig.CameraConfig.ControlStyle.values()[next];
                }
                break;
            case 1: // Invert X
                if (left || right) config.cameraSettings.invertX = !config.cameraSettings.invertX;
                break;
            case 2: // Invert Y
                if (left || right) config.cameraSettings.invertY = !config.cameraSettings.invertY;
                break;
            case 3: // Sensitivity
                if (left) config.cameraSettings.mouseSensitivity = Math.max(0.05f, config.cameraSettings.mouseSensitivity - 0.05f);
                if (right) config.cameraSettings.mouseSensitivity = Math.min(2.0f, config.cameraSettings.mouseSensitivity + 0.05f);
                break;
            case 4: // Smoothing
                if (left) config.cameraSettings.lerpSpeed = Math.max(1f, config.cameraSettings.lerpSpeed - 0.5f);
                if (right) config.cameraSettings.lerpSpeed = Math.min(20f, config.cameraSettings.lerpSpeed + 0.5f);
                break;
        }
    }

    private void drawSettingRow(SpriteBatch batch, BitmapFont font, String label, String value, float centerX, float y, boolean selected) {
        if (selected) {
            font.setColor(Color.WHITE);
            font.draw(batch, ">", centerX - 360, y);
            font.setColor(Color.CYAN);
        } else {
            font.setColor(Color.LIGHT_GRAY);
        }

        font.draw(batch, label, centerX - 330, y);

        if (selected) font.setColor(Color.YELLOW);
        else font.setColor(Color.GOLD);

        font.draw(batch, value, centerX + 120, y);
    }

    public void resetScroll() {
        this.scrollY = 0;
        this.selectedIndex = 0;
    }
}