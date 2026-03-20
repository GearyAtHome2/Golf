package org.example.hud.renderer;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.hud.HUD;
import org.example.ball.MinigameResult;

public class NotificationManager {
    private static class Notification {
        final String mainText;
        final String subText;
        final Color color;
        float timer;
        final float scale;

        Notification(String main, String sub, Color color, float duration, float scale) {
            this.mainText = main;
            this.subText = sub;
            this.color = color;
            this.timer = duration;
            this.scale = scale;
        }
    }

    private Notification activeNotification;
    private final GlyphLayout layout = new GlyphLayout();
    private final Color tempColor = new Color();

    public void showHazard(String text, Color color, float duration) {
        activeNotification = new Notification(text, "+1 STROKE PENALTY", color, duration, 4.0f);
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
            case PERFECTION -> 5.5f;
            case SUPER -> 4.5f;
            case GREAT -> 3.5f;
            case GOOD -> 2.5f;
            default -> 1.8f;
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

        float centerX = viewport.getWorldWidth() / 2f;
        float anchorY = viewport.getWorldHeight() * 0.72f;

        float finalScale = activeNotification.scale * HUD.UI_SCALE;
        float alpha = MathUtils.clamp(activeNotification.timer, 0, 1f);

        tempColor.set(activeNotification.color).a *= alpha;

        // 1. Draw Main Text
        font.getData().setScale(finalScale);
        font.setColor(tempColor);
        layout.setText(font, activeNotification.mainText);

        float mainWidth = layout.width;
        float mainHeight = layout.height;
        float textY = anchorY + (mainHeight / 2f);

        font.draw(batch, activeNotification.mainText, centerX - (mainWidth / 2f), textY);

        // 2. Draw Subtext
        if (activeNotification.subText != null) {
            // Scale up subtext to be closer to main text size
            font.getData().setScale(finalScale * 0.7f);
            font.setColor(1, 1, 1, alpha * 0.9f);
            layout.setText(font, activeNotification.subText);

            // Gap is now based on half the height of both strings + a small buffer
            float subHeight = layout.height;
            float verticalGap = mainHeight + (subHeight * 0.5f) + (10 * HUD.UI_SCALE);

            font.draw(batch, activeNotification.subText, centerX - (layout.width / 2f), textY - verticalGap);
        }

        resetFont(font);
    }

    private void resetFont(BitmapFont font) {
        font.getData().setScale(1.0f);
        font.setColor(Color.WHITE);
    }
}