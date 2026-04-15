package org.example.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Platform;
import org.example.ball.MinigameResult;

public class NotificationManager {
    private static class Notification {
        final String mainText;
        final String subText;
        final Color color;
        float timer;
        final float relativeScale; // Scale relative to our base proportional scale

        Notification(String main, String sub, Color color, float duration, float relativeScale) {
            this.mainText = main;
            this.subText = sub;
            this.color = color;
            this.timer = duration;
            this.relativeScale = relativeScale;
        }
    }

    private Notification activeNotification;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tempColor = new Color();

    public void showHazard(String text, Color color, float duration) {
        activeNotification = new Notification(text, "", color, duration, 1.8f);
    }

    public void showFeedback(MinigameResult result, float duration) {
        if (result == null || result.getRating() == null) return;

        MinigameResult.Rating rating = result.getRating();
        activeNotification = new Notification(
                rating.name(),
                rating.getRandomPhrase(),
                rating.getColor(),
                duration,
                getScaleForRating(rating)
        );
    }

    private float getScaleForRating(MinigameResult.Rating rating) {
        return switch (rating) {
            case PERFECTION -> 1.6f; // Was 5.5 (Now significantly reined in)
            case SUPER -> 1.45f;      // Was 4.5
            case GREAT -> 1.28f;      // Was 3.5
            case GOOD -> 1.15f;       // Was 2.5
            default -> 1.0f;
        };
    }

    public void update(float delta) {
        if (activeNotification != null) {
            activeNotification.timer -= delta;
            if (activeNotification.timer <= 0) activeNotification = null;
        }
    }

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport) {
        if (activeNotification == null) return;

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Platform.isAndroid();

        // Base proportional scale: approx 0.25% of screen height per font unit
        float baseScale = (screenH * 0.0025f) * (isAndroid ? 1.4f : 1.0f);
        float finalScale = baseScale * activeNotification.relativeScale;

        float centerX = screenW / 2f;
        float anchorY = screenH * 0.70f; // Slightly lower for better visibility

        float alpha = MathUtils.clamp(activeNotification.timer * 2f, 0, 1f); // Faster fade-out
        tempColor.set(activeNotification.color).a *= alpha;

        // 1. Draw Main Text
        font.getData().setScale(finalScale);
        font.setColor(tempColor);
        layout.setText(font, activeNotification.mainText);

        float textY = anchorY + (layout.height / 2f);
        font.draw(batch, activeNotification.mainText, centerX - (layout.width / 2f), textY);

        // 2. Draw Subtext
        if (activeNotification.subText != null) {
            //todo: subtext should scale depending on the length of the String
            String subtext = activeNotification.subText;
            int len = subtext.length();
            float shrinkingFactor = len - 15;
            if (shrinkingFactor < 0) shrinkingFactor = 0;
            shrinkingFactor *= 0.04f;
            shrinkingFactor = 1 - shrinkingFactor;
            font.getData().setScale(finalScale * 0.6f * shrinkingFactor);
            font.setColor(1, 1, 1, alpha * 0.8f);
            layout.setText(font, activeNotification.subText);

            // Dynamic gap based on font size
            float verticalGap = layout.height + (screenH * 0.05f);
            font.draw(batch, activeNotification.subText, centerX - (layout.width / 2f), textY - verticalGap);
        }

        font.getData().setScale(1.0f);
    }
}