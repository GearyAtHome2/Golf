package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import org.example.Platform;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.utils.viewport.Viewport;

public class ClubInfoRenderer {
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shape, BitmapFont font, Viewport viewport,
                       String name, String desc, String dist, String power, String loft) {

        boolean isAndroid = Platform.isAndroid();

        // --- COMPACT SIZING RATIOS ---
        float widthRatio = isAndroid ? 0.22f : 0.25f;
        float heightRatio = isAndroid ? 0.18f : 0.25f;

        // Increased from 0.12f to 0.15f to move it up slightly and clear the "DRIVER" text
        float yRatio = isAndroid ? 0.22f : 0.175f;

        float width = viewport.getWorldWidth() * widthRatio;
        float height = viewport.getWorldHeight() * heightRatio;

        if (isAndroid) {
            width = Math.max(width, 220f);
            height = Math.max(height, 120f);
        } else {
            width = Math.max(width, 280f);
            height = Math.max(height, 180f);
        }

        float x = viewport.getWorldWidth() - width - 20;
        float y = viewport.getWorldHeight() * yRatio;

        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();

        // --- SHAPE RENDERING ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(viewport.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0, 0, 0, 0.6f);
        shape.rect(x, y, width, height);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(Color.GOLD);
        shape.rect(x, y, width, height);
        shape.end();

        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- TEXT RENDERING ---
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float padding = width * 0.06f;

        if (isAndroid) {
            // Scale so each line is ~27% of box height, then centre the 3-line block
            float nativeLineH = font.getData().lineHeight;
            float scale = (height * 0.27f) / nativeLineH;
            font.getData().setScale(scale);
            float lineH      = font.getLineHeight();
            float lineSpacing = lineH * 1.05f;
            float blockH     = lineSpacing * 2 + lineH;
            float startY     = y + (height + blockH) / 2f;

            // Distance: Strip " yds" or "yds" if present
            String distClean = dist.replace(" yds", "");
            font.setColor(Color.CYAN);
            font.draw(batch, "Dist: " + distClean, x + padding, startY);

            font.setColor(new Color(0.2f, 0.8f, 1.0f, 1f));
            String pVal = power.toLowerCase().contains("power") ? power.substring(power.indexOf(":") + 1).trim() : power;
            font.draw(batch, "Power: " + pVal, x + padding, startY - lineSpacing);

            font.setColor(Color.WHITE);
            String lVal = loft.toLowerCase().contains("loft") ? loft.substring(loft.indexOf(":") + 1).trim() : loft;
            font.draw(batch, "Loft: " + lVal, x + padding, startY - (lineSpacing * 2));

        } else {
            float currentY = y + height - padding;

            font.getData().setScale(0.5f);
            font.setColor(Color.GOLD);
            font.draw(batch, name.replace("_", " ").toUpperCase(), x + padding, currentY);
            currentY -= (height * 0.15f);

            font.getData().setScale(0.4f);
            font.setColor(Color.CYAN);
            font.draw(batch, dist, x + padding, currentY);
            font.draw(batch, power, x + width / 2f, currentY);
            currentY -= (height * 0.12f);

            font.setColor(Color.WHITE);
            font.draw(batch, loft, x + padding, currentY);
            currentY -= (height * 0.15f);

            font.getData().setScale(0.35f);
            font.setColor(Color.LIGHT_GRAY);
            layout.setText(font, desc, Color.LIGHT_GRAY, width - (padding * 2), 0, true);
            font.draw(batch, layout, x + padding, currentY);
        }

        batch.end();
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}