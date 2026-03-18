package org.example.hud;

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

        float width = 320f;
        float height = 200f;
        float x = viewport.getWorldWidth() - width - 20;

        // --- ANTIALIASING FIX ---
        font.getRegion().getTexture().setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);

        // --- FIXED TRANSLUCENT BACKGROUND ---
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        // Disable depth writing so the transparent box doesn't "cut out" 3D objects behind it
        Gdx.gl.glDepthMask(false);

        shape.begin(ShapeRenderer.ShapeType.Filled);
        shape.setColor(0, 0, 0, 0.7f); // 70% alpha black
        shape.rect(x, y, width, height);
        shape.end();

        shape.begin(ShapeRenderer.ShapeType.Line);
        shape.setColor(Color.GOLD);
        shape.rect(x, y, width, height);
        shape.end();

        Gdx.gl.glDepthMask(true);
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // --- TEXT RENDERING ---
        batch.begin();

        float padding = 15f;
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

        // Batch end is still handled by HUD.renderClubInfo()
    }
}