package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
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
                       String name, String desc, String dist, String power, String loft, float y) {

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        float width = 320f;
        float height = 200f;
        float x = viewport.getWorldWidth() - width - 20;

        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        float oldScaleX = font.getScaleX();
        float oldScaleY = font.getScaleY();

        // --- SHAPE RENDERING ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        shape.setProjectionMatrix(viewport.getCamera().combined);
        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0, 0, 0, 0.7f);
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

        float padding = 15f;

        if (isAndroid) {
            // --- ANDROID: LARGE VERTICAL STACK ---
            // Doubled the previous scale (was 1.1f/1.4f on desktop, now ~2.2f)
            font.getData().setScale(2.2f);
            float lineSpacing = 60f;
            float startY = y + height - 35f;

            // Distance line
            font.setColor(Color.CYAN);
            font.draw(batch, "Dist: " + dist, x + padding, startY);

            // Power line
            font.setColor(new Color(0.2f, 0.8f, 1.0f, 1f));
            font.draw(batch, "Power: " + power.replace("Power: ", ""), x + padding, startY - lineSpacing);

            // Loft line
            font.setColor(Color.WHITE);
            font.draw(batch, "Loft: " + loft.replace("Loft: ", ""), x + padding, startY - (lineSpacing * 2));

        } else {
            // --- DESKTOP: FULL INFO (Original) ---
            float currentY = y + height - padding;

            font.getData().setScale(1.4f);
            font.setColor(Color.GOLD);
            font.draw(batch, name.replace("_", " ").toUpperCase(), x + padding, currentY);
            currentY -= 35f;

            font.getData().setScale(1.1f);
            font.setColor(Color.CYAN);
            font.draw(batch, dist, x + padding, currentY);
            font.draw(batch, power, x + width/2f + padding, currentY);
            currentY -= 22f;

            font.setColor(Color.WHITE);
            font.draw(batch, loft, x + padding, currentY);
            currentY -= 30f;

            font.getData().setScale(1.0f);
            font.setColor(Color.LIGHT_GRAY);
            layout.setText(font, desc, Color.LIGHT_GRAY, width - (padding * 2), 0, true);
            font.draw(batch, layout, x + padding, currentY);
        }

        batch.end();
        font.getData().setScale(oldScaleX, oldScaleY);
    }
}