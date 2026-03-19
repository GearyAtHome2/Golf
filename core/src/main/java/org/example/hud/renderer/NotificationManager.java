package org.example.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.hud.HUD;

public class NotificationManager {
    private static class Notification {
        String text;
        Color color;
        float timer;
        float maxDuration;
        float scale;
        boolean isHazard;

        Notification(String text, Color color, float duration, float scale, boolean isHazard) {
            this.text = text;
            this.color = color;
            this.timer = duration;
            this.maxDuration = duration;
            this.scale = scale;
            this.isHazard = isHazard;
        }
    }

    private Notification activeNotification;
    private final GlyphLayout layout = new GlyphLayout();

    public void showHazard(String text, Color color, float duration) {
        // High visibility scale for hazards
        activeNotification = new Notification(text, color, duration, 3.5f, true);
    }

    public void showFeedback(String text, Color color, float duration, float scale) {
        activeNotification = new Notification(text, color, duration, scale, false);
    }

    public void update(float delta) {
        if (activeNotification != null) {
            activeNotification.timer -= delta;
            if (activeNotification.timer <= 0) {
                activeNotification = null;
            }
        }
    }

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (activeNotification == null) return;

        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        // Apply the global platform scale from HUD
        float finalScale = activeNotification.scale * HUD.UI_SCALE;
        float alpha = MathUtils.clamp(activeNotification.timer, 0, 1f);

        // 1. Render Main Text (Hazard or Shot Rating)
        font.getData().setScale(finalScale);
        font.setColor(activeNotification.color.r, activeNotification.color.g, activeNotification.color.b, alpha);

        layout.setText(font, activeNotification.text);
        // Vertical offset is also scaled to ensure the gap looks correct on all screens
        float mainY = centerY + (100 * HUD.UI_SCALE);
        font.draw(batch, activeNotification.text, centerX - (layout.width / 2f), mainY);

        // 2. Render Secondary Text for Hazards
        if (activeNotification.isHazard) {
            // Half size of the scaled main text
            font.getData().setScale(finalScale * 0.5f);
            font.setColor(1, 1, 1, alpha * 0.8f);

            String penaltyText = "+1 STROKE PENALTY";
            layout.setText(font, penaltyText);

            // Offset the penalty line based on the current font size
            float penaltyOffset = (finalScale * 25f);
            font.draw(batch, penaltyText, centerX - (layout.width / 2f), mainY - penaltyOffset);
        }

        // Reset font properties for the rest of the HUD loop
        font.getData().setScale(1.0f);
        font.setColor(Color.WHITE);
    }
}