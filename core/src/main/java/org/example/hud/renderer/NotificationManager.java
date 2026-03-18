package org.example.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;

public class NotificationManager {
    private float hazardTimer = 0;
    private String hazardText = "";
    private Color hazardColor = Color.WHITE;

    private float feedbackTimer = 0;
    private String feedbackText = "";
    private Color feedbackColor = Color.WHITE;

    public void showHazard(String text, Color color, float duration) {
        this.hazardText = text;
        this.hazardColor = color;
        this.hazardTimer = duration;
    }

    public void showFeedback(String text, Color color, float duration) {
        this.feedbackText = text;
        this.feedbackColor = color;
        this.feedbackTimer = duration;
    }

    public void update(float delta) {
        if (hazardTimer > 0) hazardTimer -= delta;
        if (feedbackTimer > 0) feedbackTimer -= delta;
    }

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (hazardTimer > 0) {
            font.getData().setScale(2.0f);
            font.setColor(hazardColor.r, hazardColor.g, hazardColor.b, MathUtils.clamp(hazardTimer, 0, 1));
            font.draw(batch, hazardText, viewport.getWorldWidth() / 2f - 150, viewport.getWorldHeight() / 2f + 100);
        }

        if (feedbackTimer > 0) {
            font.getData().setScale(1.5f);
            font.setColor(feedbackColor.r, feedbackColor.g, feedbackColor.b, MathUtils.clamp(feedbackTimer, 0, 1));
            font.draw(batch, feedbackText, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() / 2f - 50);
        }
    }
}