package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
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
    private final Rectangle boxBounds = new Rectangle();
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameConfig config, GameInputProcessor input) {
        handleNavigation(input);
        handleInteraction(config, input);

        float delta = Gdx.graphics.getDeltaTime();
        float scrollSpeed = 450f * delta;
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) {
            scrollY = Math.max(0, scrollY - scrollSpeed);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) {
            scrollY = Math.min(MAX_SCROLL, scrollY + scrollSpeed);
        }

        // --- LAYOUT CALCULATIONS (RATIO BASED) ---
        float boxW = viewport.getWorldWidth() * (isAndroid ? 0.85f : 0.60f);
        float boxH = viewport.getWorldHeight() * (isAndroid ? 0.70f : 0.60f);
        float boxX = (viewport.getWorldWidth() - boxW) / 2f;
        float boxY = (viewport.getWorldHeight() - boxH) / 2f;
        boxBounds.set(boxX, boxY, boxW, boxH);

        // Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.05f, 0.12f, 0.95f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        if (batch.isDrawing()) batch.end();
        Rectangle scissor = new Rectangle();
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), boxBounds, scissor);

        if (ScissorStack.pushScissors(scissor)) {
            batch.begin();

            float currentY = boxY + boxH - (boxH * 0.1f) + scrollY;
            float lineSpace = boxH * (isAndroid ? 0.12f : 0.08f);
            float margin = boxX + (boxW * 0.1f);

            // --- TITLE ---
            font.getData().setScale(isAndroid ? 2.2f : 0.8f);
            font.setColor(Color.GOLD);
            layout.setText(font, "CAMERA CONFIGURATION");
            font.draw(batch, "CAMERA CONFIGURATION", (viewport.getWorldWidth() - layout.width) / 2f, currentY);
            currentY -= (lineSpace * 1.5f);

            font.getData().setScale(isAndroid ? 1.1f : 0.45f);

            // Settings Rows
            drawSettingRow(batch, font, "Control Style", config.cameraSettings.controlStyle.name(), boxX, boxW, currentY, selectedIndex == 0);
            currentY -= lineSpace;

            drawSettingRow(batch, font, "Invert X-Axis", config.cameraSettings.invertX ? "ON" : "OFF", boxX, boxW, currentY, selectedIndex == 1);
            currentY -= lineSpace;

            drawSettingRow(batch, font, "Invert Y-Axis", config.cameraSettings.invertY ? "ON" : "OFF", boxX, boxW, currentY, selectedIndex == 2);
            currentY -= lineSpace;

            drawSettingRow(batch, font, "Mouse Sensitivity", String.format("%.2f", config.cameraSettings.mouseSensitivity), boxX, boxW, currentY, selectedIndex == 3);
            currentY -= lineSpace;

            drawSettingRow(batch, font, "Ball tracking speed", String.format("%.1f", config.cameraSettings.lerpSpeed), boxX, boxW, currentY, selectedIndex == 4);
            currentY -= (lineSpace * 1.2f);

            // Helper text
            font.setColor(Color.CYAN);
            font.draw(batch, "Use [UP/DOWN] or [W/S] to select", margin, currentY);
            currentY -= (lineSpace * 0.5f);
            font.draw(batch, "Use [LEFT/RIGHT] or [A/D] to change values", margin, currentY);

            batch.flush();
            batch.end();
            ScissorStack.popScissors();
            batch.begin();
        }

        // Footer
        font.getData().setScale(isAndroid ? 1.1f : 0.45f);
        font.setColor(Color.YELLOW);
        String footer = isAndroid ? "Tap outside to Return to Pause Menu" : "[ESC] to Return to Pause Menu";
        layout.setText(font, footer);
        font.draw(batch, footer, (viewport.getWorldWidth() - layout.width) / 2f, boxY - (viewport.getWorldHeight() * 0.05f));
    }

    private void drawSettingRow(SpriteBatch batch, BitmapFont font, String label, String value, float boxX, float boxW, float y, boolean selected) {
        float labelX = boxX + (boxW * 0.1f);
        float valueX = boxX + (boxW * 0.65f);

        if (selected) {
            font.setColor(Color.WHITE);
            font.draw(batch, ">", labelX - (boxW * 0.04f), y);
            font.setColor(Color.CYAN);
        } else {
            font.setColor(Color.LIGHT_GRAY);
        }

        font.draw(batch, label, labelX, y);

        if (selected) font.setColor(Color.YELLOW);
        else font.setColor(Color.GOLD);

        font.draw(batch, value, valueX, y);
    }

    // handleNavigation and handleInteraction remain unchanged as they logic-only
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
                if (left)
                    config.cameraSettings.mouseSensitivity = Math.max(0.05f, config.cameraSettings.mouseSensitivity - 0.05f);
                if (right)
                    config.cameraSettings.mouseSensitivity = Math.min(2.0f, config.cameraSettings.mouseSensitivity + 0.05f);
                break;
            case 4: // Smoothing
                if (left) config.cameraSettings.lerpSpeed = Math.max(1f, config.cameraSettings.lerpSpeed - 0.5f);
                if (right) config.cameraSettings.lerpSpeed = Math.min(20f, config.cameraSettings.lerpSpeed + 0.5f);
                break;
        }
    }

    public boolean isClickInside(float x, float y) {
        return boxBounds.contains(x, y);
    }

    public void resetScroll() {
        this.scrollY = 0;
        this.selectedIndex = 0;
    }
}