package org.example.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;

public class MinigameUI {
    private final Color colorOrange = new Color(1f, 0.5f, 0f, 0.4f);
    private final Color colorRed = new Color(1f, 0f, 0f, 0.3f);
    private final Color colorGreen = new Color(0.1f, 0.8f, 0.1f, 0.7f);

    public void draw(ShapeRenderer shapeRenderer, SpriteBatch batch, BitmapFont font,
                     float screenW, float screenH, MinigameEngine engine,
                     float barSwellTimer, float glowTimer, Color glowColor,
                     Array<ModAnimation> activeAnims) {

        float width = screenW * 0.6f;
        float baseH = 30f;
        float swell = (barSwellTimer > 0) ? MathUtils.sin((1f - barSwellTimer / 0.3f) * MathUtils.PI) * 15f : 0;
        float height = baseH + swell;
        float x = (screenW - width) / 2f;
        float y = (screenH - 150f) - (height - baseH) / 2f;

        com.badlogic.gdx.Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        // 1. Background
        shapeRenderer.setColor(0, 0, 0, 0.6f);
        shapeRenderer.rect(x - 4, y - 4, width + 8, height + 8);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapeRenderer.rect(x, y, width, height);

        // 2. Metrics for the "Safety" Zones
        float greenHw = (0.25f * engine.displayedWidthMult) / 2f * width;
        float greenCx = x + engine.greenCenter * width;

        // 3. Penalty Zones
        float redHw = greenHw + (greenHw * 1.0f);
        float orangeHw = greenHw + (greenHw * 0.5f);
        drawZone(shapeRenderer, greenCx, redHw, y, height, colorRed);
        drawZone(shapeRenderer, greenCx, orangeHw, y, height, colorOrange);

        // 4. Draw Green Zone
        drawZone(shapeRenderer, greenCx, greenHw, y, height, colorGreen);

        // 5. Nested Gradient Logic
        Color parentColor = colorGreen;
        float parentHw = greenHw;

        for (int i = 0; i < engine.sweetSpots.size; i++) {
            MinigameEngine.Zone z = engine.sweetSpots.get(i);
            float childHw = parentHw * z.widthRatio;
            float childCx = x + z.center * width;

            drawGradientRect(shapeRenderer, childCx - childHw, y, childHw, height, parentColor, z.color, true);
            drawGradientRect(shapeRenderer, childCx, y, childHw, height, z.color, parentColor, true);

            parentColor = z.color;
            parentHw = childHw;
        }

        // 6. Glow Effect
        if (glowTimer > 0) {
            shapeRenderer.setColor(glowColor.r, glowColor.g, glowColor.b, glowTimer * 0.8f);
            shapeRenderer.rect(x - 10, y - 10, width + 20, height + 20);
        }

        // 7. Needle
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(x + (width * engine.needlePos) - 3, y - 15, 6, height + 30);
        shapeRenderer.end();

        // 8. Text Animations
        if (activeAnims.size > 0) {
            batch.begin();
            for (ModAnimation anim : activeAnims) {
                font.getData().setScale(1.5f * anim.scale);
                font.setColor(anim.color.r, anim.color.g, anim.color.b, anim.alpha);
                font.draw(batch, anim.text, greenCx - 80, (screenH - 150f) + anim.yOffset);
            }
            batch.end();
        }
        com.badlogic.gdx.Gdx.gl.glDisable(GL20.GL_BLEND);
    }

    private void drawZone(ShapeRenderer sr, float cx, float hw, float y, float h, Color c) {
        sr.setColor(c);
        sr.rect(cx - hw, y, hw * 2, h);
    }

    private void drawGradientRect(ShapeRenderer sr, float x, float y, float w, float h,
                                  Color c1, Color c2, boolean horizontal) {
        // rect(x, y, width, height, colorBottomLeft, colorBottomRight, colorTopRight, colorTopLeft)
        if (horizontal) {
            sr.rect(x, y, w, h, c1, c2, c2, c1);
        } else {
            sr.rect(x, y, w, h, c1, c1, c2, c2);
        }
    }
}