package org.example.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
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

    public static void registerDefaultStyles(Skin skin, BitmapFont font) {
        if (!skin.has("white", Drawable.class)) {
            skin.add("white", createRoundedRectDrawable(Color.WHITE, 2));
        }

        // TextField Style for Score Submission
        if (!skin.has("default", TextField.TextFieldStyle.class)) {
            TextField.TextFieldStyle tfs = new TextField.TextFieldStyle();
            tfs.font = font;
            tfs.fontColor = Color.WHITE;
            tfs.background = createRoundedRectDrawable(new Color(0.15f, 0.15f, 0.15f, 0.9f), 6);
            tfs.cursor = skin.newDrawable("white", Color.GOLD);
            tfs.cursor.setMinWidth(2f);
            tfs.selection = skin.newDrawable("white", new Color(0.3f, 0.3f, 0.8f, 0.5f));
            skin.add("default", tfs);
        }

        // Window Style for Dialogs
        if (!skin.has("default", Window.WindowStyle.class)) {
            Window.WindowStyle ws = new Window.WindowStyle();
            ws.titleFont = font;
            ws.titleFontColor = Color.GOLD;
            ws.background = createRoundedRectDrawable(new Color(0.05f, 0.05f, 0.05f, 0.95f), 6);
            skin.add("default", ws);
        }
    }
}