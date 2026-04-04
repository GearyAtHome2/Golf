package org.example.hud;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextField;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;

public class UIUtils {

    // Set to false for a fixed 1-pixel shadow regardless of font scale.
    public static final boolean SCALED_SHADOW_OFFSET = true;

    public static void drawShadowedText(SpriteBatch batch, BitmapFont font, String text, float x, float y, Color color) {
        if (!batch.isDrawing()) return;
        float offset = SCALED_SHADOW_OFFSET ? font.getScaleX() * 1.1f : 1f;
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.6f);
        font.draw(batch, text, x + offset, y - offset);
        font.setColor(color);
        font.draw(batch, text, x, y);
    }

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

    private static void fillRoundedRect(Pixmap p, int x, int y, int w, int h, int r) {
        p.fillRectangle(x + r, y, w - 2 * r, h);
        p.fillRectangle(x, y + r, w, h - 2 * r);
        p.fillCircle(x + r,     y + r,     r);
        p.fillCircle(x + w - r, y + r,     r);
        p.fillCircle(x + r,     y + h - r, r);
        p.fillCircle(x + w - r, y + h - r, r);
    }

    /** Rounded rect with a dark shadow strip at bottom-right — gives a raised 3D look. */
    public static Drawable createRaisedButtonDrawable(Color base, int radius, int shadowOffset) {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        // Shadow layer: offset down-right, darker
        pixmap.setColor(new Color(base.r * 0.35f, base.g * 0.35f, base.b * 0.35f, 1f));
        fillRoundedRect(pixmap, shadowOffset, shadowOffset, size - shadowOffset, size - shadowOffset, radius);

        // Main face: top-left, same dimensions, covers shadow except the exposed strip
        pixmap.setColor(base);
        fillRoundedRect(pixmap, 0, 0, size - shadowOffset, size - shadowOffset, radius);

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        // Extra right/bottom NinePatch border keeps the shadow strip fixed-width when stretched
        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture),
                radius, radius + shadowOffset, radius, radius + shadowOffset));
    }

    /**
     * Embossed style: bright highlight on top/left, dark shadow on bottom/right.
     * Uses 3 rounded-rect layers so all four corners curve naturally — no straight-edge artifacts.
     */
    public static Drawable createEmbossedButtonDrawable(Color base, int radius, int bevelSize) {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        Color shadow    = new Color(base.r * 0.32f, base.g * 0.32f, base.b * 0.32f, base.a);
        Color highlight = new Color(Math.min(1f, base.r + 0.45f), Math.min(1f, base.g + 0.45f), Math.min(1f, base.b + 0.45f), base.a);

        // Layer 1 — full area in highlight (shows at top/left bevel edges)
        pixmap.setColor(highlight);
        fillRoundedRect(pixmap, 0, 0, size, size, radius);

        // Layer 2 — inset from top-left, in shadow: its rounded corners sit at the
        // actual bottom-right of the button, so no highlight bleeds through there
        pixmap.setColor(shadow);
        fillRoundedRect(pixmap, bevelSize, bevelSize, size - bevelSize, size - bevelSize, radius);

        // Layer 3 — main face, inset from all sides
        pixmap.setColor(base);
        fillRoundedRect(pixmap, bevelSize, bevelSize, size - 2 * bevelSize, size - 2 * bevelSize, Math.max(radius - bevelSize, 2));

        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture), radius, radius, radius, radius));
    }

    /**
     * Inset/pressed style: dark shadow on top/left, bright highlight on bottom/right.
     * Opposite of embossed — looks sunken. 3-layer rounded-rect approach for natural corners.
     */
    public static Drawable createInsetButtonDrawable(Color base, int radius, int bevelSize) {
        int size = 64;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        Color shadow    = new Color(base.r * 0.28f, base.g * 0.28f, base.b * 0.28f, base.a);
        Color highlight = new Color(Math.min(1f, base.r + 0.35f), Math.min(1f, base.g + 0.35f), Math.min(1f, base.b + 0.35f), base.a);
        Color pressed   = new Color(base.r * 0.78f, base.g * 0.78f, base.b * 0.78f, base.a);

        // Layer 1 — full area in shadow (shows at top/left bevel edges)
        pixmap.setColor(shadow);
        fillRoundedRect(pixmap, 0, 0, size, size, radius);

        // Layer 2 — inset from top-left, in highlight: its rounded corners sit at the
        // bottom-right of the button, so no shadow bleeds through there
        pixmap.setColor(highlight);
        fillRoundedRect(pixmap, bevelSize, bevelSize, size - bevelSize, size - bevelSize, radius);

        // Layer 3 — main face, inset from all sides
        pixmap.setColor(pressed);
        fillRoundedRect(pixmap, bevelSize, bevelSize, size - 2 * bevelSize, size - 2 * bevelSize, Math.max(radius - bevelSize, 2));

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

    /**
     * NinePatch panel background: goldWidth px gold border → 1 px dark bevel → stretchy dark centre.
     * Corners stay crisp at any panel size.
     */
    public static NinePatchDrawable createGoldBorderedPanel(Color bgColor, int goldWidth) {
        int bevel  = 1;
        int border = goldWidth + bevel;
        int size   = border * 2 + 1;

        Pixmap pm = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pm.setBlending(Pixmap.Blending.None);

        pm.setColor(1.0f, 0.80f, 0.15f, 1f);
        pm.fill();

        pm.setColor(0.28f, 0.18f, 0.02f, 1f);
        pm.fillRectangle(goldWidth, goldWidth, size - goldWidth * 2, size - goldWidth * 2);

        pm.setColor(bgColor);
        pm.fillRectangle(border, border, 1, 1);

        Texture tex = new Texture(pm);
        pm.dispose();

        NinePatch np = new NinePatch(tex, border, border, border, border);
        return new NinePatchDrawable(np);
    }
}