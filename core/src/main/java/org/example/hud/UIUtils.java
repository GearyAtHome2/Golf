package org.example.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class UIUtils {

    public static Drawable createRoundedRectDrawable(Color color, int radius) {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        pixmap.setColor(color);
        pixmap.fillRectangle(radius, 0, size - 2 * radius, size);
        pixmap.fillRectangle(0, radius, size, size - 2 * radius);
        pixmap.fillCircle(radius, radius, radius);
        pixmap.fillCircle(size - radius, radius, radius);
        pixmap.fillCircle(radius, size - radius, radius);
        pixmap.fillCircle(size - radius, size - radius, radius);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();

        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture), radius, radius, radius, radius));
    }
}