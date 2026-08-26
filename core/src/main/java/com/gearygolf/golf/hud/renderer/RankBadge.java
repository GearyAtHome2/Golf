package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.gearygolf.golf.GameConfig;

/**
 * Represents a player's rank badge — colour, inner text, display name, and overlay message.
 * Parsed from the stored rating label (e.g. "NOVICE -3", "PRO +", "NOVICE").
 *
 * The sphere is rendered using a pre-generated per-pixel Phong texture (same technique as
 * SpinIndicator.renderBigOverlay), tinted with the badge colour.  One 256×256 texture is
 * cached per Difficulty and reused for all badge sizes.
 */
public class RankBadge {

    // Colours match difficulty order: NOVICE, INTERMEDIATE, ADVANCED, SCRATCH, PRO, TOUR_PRO
    private static final Color COLOR_NOVICE       = new Color(0.72f, 0.72f, 0.72f, 1f); // grey
    private static final Color COLOR_INTERMEDIATE = new Color(0.20f, 0.72f, 0.28f, 1f); // green
    private static final Color COLOR_ADVANCED     = new Color(0.90f, 0.82f, 0.10f, 1f); // yellow
    private static final Color COLOR_SCRATCH      = new Color(0.95f, 0.50f, 0.15f, 1f); // orange
    private static final Color COLOR_PRO          = new Color(0.95f, 0.60f, 0.78f, 1f); // pink
    private static final Color COLOR_TOUR_PRO     = new Color(0.58f, 0.28f, 0.82f, 1f); // purple

    /** One texture per difficulty — generated once on first use, shared across badge instances. */
    private static final java.util.EnumMap<GameConfig.Difficulty, Texture> TEXTURE_CACHE =
            new java.util.EnumMap<>(GameConfig.Difficulty.class);

    public final GameConfig.Difficulty difficulty;
    public final boolean isUnranked;
    public final String  rawMarker; // "+", "0", "-1".."-5", or null when unranked

    private RankBadge(GameConfig.Difficulty difficulty, boolean isUnranked, String rawMarker) {
        this.difficulty = difficulty;
        this.isUnranked = isUnranked;
        this.rawMarker  = rawMarker;
    }

    // ── Factory ───────────────────────────────────────────────────────────────

    /**
     * Builds a RankBadge from the stored label string produced by ProfileRenderer.computeRatingLabel().
     * Examples: "NOVICE", "NOVICE -3", "ADVANCED +", "TOUR_PRO 0".
     */
    public static RankBadge fromLabel(String label) {
        if (label == null || label.isEmpty()) return noviceUnranked();
        String[] parts = label.trim().split(" ", 2);
        try {
            GameConfig.Difficulty diff = GameConfig.Difficulty.valueOf(parts[0]);
            if (parts.length == 1) return new RankBadge(diff, true, null);
            return new RankBadge(diff, false, parts[1]);
        } catch (Exception e) {
            return noviceUnranked();
        }
    }

    private static RankBadge noviceUnranked() {
        return new RankBadge(GameConfig.Difficulty.NOVICE, true, null);
    }

    // ── Data ─────────────────────────────────────────────────────────────────

    public Color getColor() {
        return switch (difficulty) {
            case NOVICE       -> COLOR_NOVICE;
            case INTERMEDIATE -> COLOR_INTERMEDIATE;
            case ADVANCED     -> COLOR_ADVANCED;
            case SCRATCH      -> COLOR_SCRATCH;
            case PRO          -> COLOR_PRO;
            case TOUR_PRO     -> COLOR_TOUR_PRO;
        };
    }

    /** Text displayed inside the badge circle. */
    public String getBadgeText() {
        if (isUnranked || rawMarker == null) return "...";
        return switch (rawMarker) {
            case "+", "0" -> rawMarker;
            default       -> toRoman(rawMarker); // "-3" → "III"
        };
    }

    /** Human-readable rank name shown in the overlay header, e.g. "PRO III", "NOVICE PLUS", "UNRANKED NOVICE". */
    public String getDisplayName() {
        if (isUnranked) return "UNRANKED NOVICE";
        String base = difficulty.name().replace('_', ' ');
        return switch (rawMarker) {
            case "+" -> base + " PLUS";
            case "0" -> base + " 0";
            default  -> base + " " + getBadgeText();
        };
    }

    /**
     * Optional message shown below the rank name in the overlay.
     * Returns null for plain numeric ranks (nothing extra to say).
     */
    public String getOverlayMessage() {
        if (isUnranked) {
            return "Play at least 8 18-hole rounds on NOVICE\ndifficulty to receive a ranking.\n\nYour ranking can be recalculated\nfrom the Profile page.";
        }
        if ("+".equals(rawMarker)) {
            return switch (difficulty) {
                case NOVICE       -> "It's time to move on to INTERMEDIATE golf.";
                case INTERMEDIATE -> "It's time to move on to ADVANCED golf.";
                case ADVANCED     -> "It's time to move on to SCRATCH golf.";
                case SCRATCH      -> "It's time to move on to PRO golf.";
                case PRO          -> "It's time to move on to TOUR PRO golf.";
                case TOUR_PRO     -> "There's nothing left for you to learn.\nYou Are Golf.";
            };
        }
        return null;
    }

    // ── Rendering ─────────────────────────────────────────────────────────────

    /**
     * Draws the badge: gold bezel via ShapeRenderer, then a per-pixel Phong sphere via SpriteBatch.
     * Call with neither sr nor batch currently active (both will be opened/closed internally).
     */
    public void renderCircle(ShapeRenderer sr, SpriteBatch batch, float cx, float cy, float radius) {
        Color c = getColor();
        float border = radius * 0.07f;

        // ── Gold bezel + drop shadow (ShapeRenderer) ──────────────────────────
        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);

        sr.begin(ShapeRenderer.ShapeType.Filled);

        // Drop shadow
        sr.setColor(0f, 0f, 0f, 0.42f);
        sr.circle(cx + radius * 0.06f, cy - radius * 0.06f, radius + border * 1.7f, 52);

        // Gold bezel ring
        sr.setColor(0.92f, 0.74f, 0.10f, 1f);
        sr.circle(cx, cy, radius + border, 52);

        // Dark inner bevel (badge-colour tint so it bleeds into the sphere edge)
        sr.setColor(c.r * 0.14f, c.g * 0.14f, c.b * 0.14f, 1f);
        sr.circle(cx, cy, radius + border * 0.22f, 52);

        sr.end();

        // ── Sphere texture (SpriteBatch) ───────────────────────────────────────
        // Reset tint to white — font rendering (renderText) sets the batch colour to black
        // for the badge label and never restores it, so without this the sphere renders black.
        // enableBlending() resets SpriteBatch's internal flag — Scene2D widgets can call
        // disableBlending() and leave it false, which causes flush() to call glDisable(GL_BLEND)
        // even after we called glEnable above directly.
        batch.setProjectionMatrix(sr.getProjectionMatrix());
        batch.setColor(1f, 1f, 1f, 1f);
        batch.enableBlending();
        batch.begin();
        batch.draw(getSphereTexture(), cx - radius, cy - radius, radius * 2f, radius * 2f);
        batch.end();
    }

    /** Draws the text inside the badge. Call inside an active SpriteBatch begin block. */
    public void renderText(SpriteBatch batch, BitmapFont font, float cx, float cy, float radius) {
        String text = getBadgeText();
        float savedScale = font.getScaleX();

        GlyphLayout layout = new GlyphLayout();
        float scale = radius * 0.055f;
        font.getData().setScale(scale);
        layout.setText(font, text);

        // Scale down if text overflows the circle
        float maxW = radius * 1.4f;
        if (layout.width > maxW) {
            scale *= maxW / layout.width;
            font.getData().setScale(scale);
            layout.setText(font, text);
        }

        font.setColor(0f, 0f, 0f, isUnranked ? 0.5f : 0.8f);
        font.draw(batch, text, cx - layout.width / 2f, cy + layout.height / 2f);
        font.getData().setScale(savedScale);
    }

    /** Disposes all cached sphere textures. Call on game shutdown. */
    public static void disposeAll() {
        for (Texture t : TEXTURE_CACHE.values()) t.dispose();
        TEXTURE_CACHE.clear();
    }

    // ── Sphere texture generation ──────────────────────────────────────────────

    private Texture getSphereTexture() {
        return TEXTURE_CACHE.computeIfAbsent(difficulty, d -> buildSphereTexture());
    }

    /**
     * Generates a 256×256 per-pixel Phong-shaded sphere texture tinted with the badge colour.
     * Same technique as SpinIndicator.buildBallTexture() — diffuse + specular from upper-left,
     * limb darkening, soft edge feathering.  Specular pushes all channels toward white regardless
     * of badge colour, giving a clean highlight.
     */
    private Texture buildSphereTexture() {
        Color c = getColor();
        int size = 256;
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();

        float pcx = size / 2f;
        float pcy = size / 2f;
        float r   = size / 2f - 2f;

        // Light direction upper-left (same as SpinIndicator)
        float lx = -0.45f, ly = -0.62f, lz = 0.64f;

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                float sx = (px - pcx) / r;
                float sy = (py - pcy) / r;
                float d2 = sx * sx + sy * sy;
                if (d2 > 1f) continue;

                float sz = (float) Math.sqrt(1.0 - d2);

                // Diffuse
                float diff = Math.max(0f, lx * sx + ly * sy + lz * sz);

                // Specular — rz^28 approximation from SpinIndicator
                float NdotL = lx * sx + ly * sy + lz * sz;
                float rz    = Math.max(0f, 2f * NdotL * sz - lz);
                float s2 = rz * rz; float s4 = s2 * s2; float s8 = s4 * s4;
                float spec  = s8 * s8 * s8 * s4 * 0.85f; // rz^28

                // Limb darkening
                float ao  = sz * sz * 0.35f + 0.65f;

                float ambient = 0.38f;
                float lit     = ambient + diff * 0.62f;

                // Tint lit surface with badge colour; specular adds uniformly (pushes toward white)
                float fr = Math.min(1f, c.r * lit * ao + spec);
                float fg = Math.min(1f, c.g * lit * ao + spec);
                float fb = Math.min(1f, c.b * lit * ao + spec * 0.97f);

                // Soft edge feathering
                float alpha = d2 > 0.96f ? (1f - d2) / 0.04f : 1f;

                pixmap.drawPixel(px, py, Color.rgba8888(fr, fg, fb, alpha));
            }
        }

        Texture tex = new Texture(pixmap);
        tex.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        return tex;
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private static String toRoman(String marker) {
        try {
            return switch (Math.abs(Integer.parseInt(marker))) {
                case 1  -> "I";
                case 2  -> "II";
                case 3  -> "III";
                case 4  -> "IV";
                case 5  -> "V";
                default -> marker;
            };
        } catch (NumberFormatException e) {
            return marker;
        }
    }
}
