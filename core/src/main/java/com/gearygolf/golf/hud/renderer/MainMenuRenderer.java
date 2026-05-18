package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.gearygolf.golf.Platform;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.hud.MenuButtonDescriptor;
import com.gearygolf.golf.hud.MenuButtonResolver;
import com.gearygolf.golf.hud.UIUtils;
import com.gearygolf.golf.scoreBoard.DailySubmissionCache;
import com.gearygolf.golf.session.CompetitiveSessions;
import com.gearygolf.golf.terrain.level.LevelData;
import com.gearygolf.golf.tutorial.TutorialPrefs;

public class MainMenuRenderer {

    public enum MenuState {
        MAIN, EIGHTEEN_HOLES, DIFFICULTY_SELECT, PRACTICE, PLAY_OPTIONS, MAP_SELECT, SETTINGS
    }

    private static final int SCROLL_WINDOW = 8;

    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();
    private float maxMenuTextWidth = Float.MAX_VALUE;
    private String loggedInUser = "";

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection, MenuState state, CompetitiveSessions sessions, int mapScrollOffset, DailySubmissionCache dailyCache) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Platform.isAndroid();

        String title = "GEARY GOLF";
        float baseTitleScale = isAndroid ? screenH * 0.0035f : screenH * 0.0043f;
        float titleY = screenH * 0.95f;

        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * (baseTitleScale * 0.03f);
        font.getData().setScale(pulse);
        layout.setText(font, title);

        float centeredX = (screenW - layout.width) / 2f;

        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, centeredX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, centeredX, titleY);

        if (isAndroid && !loggedInUser.isEmpty() && state == MenuState.MAIN) {
            badgeRadius = screenH * 0.028f;
            float textX  = screenW * 0.03f;
            float textY  = screenH * 0.80f;

            font.getData().setScale(baseTitleScale * 0.36f);
            font.setColor(Color.WHITE);
            String userLine = loggedInUser + ": " + (skillRating.isEmpty() ? "NOVICE" : skillRating);
            layout.setText(font, userLine);
            font.draw(batch, userLine, textX, textY);

            badgeCX = textX + layout.width + badgeRadius + 8f;
            badgeCY = textY - badgeRadius * 0.5f;
        } else if (isAndroid) {
            badgeRadius = 0f;
        }

        if (!isAndroid) {
            renderDesktopMenu(batch, font, screenW, screenH, selection, state, sessions, baseTitleScale, mapScrollOffset, dailyCache);
        }

        font.getData().setScale(1.0f);
    }

    private void renderDesktopMenu(SpriteBatch batch, BitmapFont font, float screenW, float screenH, int selection, MenuState state, CompetitiveSessions sessions, float baseScale, int mapScrollOffset, DailySubmissionCache dailyCache) {
        float fixedLeftX = screenW * 0.03f;
        float menuStartY = screenH * 0.65f;
        float spacing = screenH * 0.075f;
        float optionScale = baseScale * 0.32f;

        maxMenuTextWidth = screenW * 0.50f;
        font.getData().setScale(optionScale);

        switch (state) {
            case MAIN -> renderMainMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, sessions, dailyCache);
            case PLAY_OPTIONS -> renderPlayOptionsMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case MAP_SELECT -> renderMapSelectMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, mapScrollOffset);
            case EIGHTEEN_HOLES -> renderEighteenMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, sessions, dailyCache);
            case DIFFICULTY_SELECT -> renderDifficultyMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case PRACTICE -> renderPracticeMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case SETTINGS -> renderSettingsMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
        }

        if (state == MenuState.MAIN) {
            if (!loggedInUser.isEmpty()) {
                badgeRadius = screenH * 0.022f;
                float textY = menuStartY + spacing * 1.1f;

                font.getData().setScale(optionScale * 0.52f);
                font.setColor(Color.WHITE);
                String userLine = loggedInUser + ": " + (skillRating.isEmpty() ? "NOVICE" : skillRating);
                layout.setText(font, userLine);
                font.draw(batch, userLine, fixedLeftX, textY);

                badgeCX = fixedLeftX + layout.width + badgeRadius + 8f;
                badgeCY = textY - badgeRadius * 0.5f;
            } else {
                badgeRadius = 0f;
            }
            font.getData().setScale(optionScale * 0.6f);
            font.setColor(Color.GRAY);
            font.draw(batch, "Use UP/DOWN to select, ENTER to start", fixedLeftX, menuStartY - (spacing * 7f));
        }
    }

    private String    skillRating = "";
    private RankBadge badge       = RankBadge.fromLabel("NOVICE");

    // Badge position/size on the home screen — updated each render, read by HUD for drawing + hit-testing
    private float badgeCX, badgeCY, badgeRadius;

    public void setLoggedInUser(String name) {
        this.loggedInUser = name != null ? name : "";
    }

    public void setSkillRating(String rating) {
        this.skillRating = rating != null ? rating : "";
        this.badge       = RankBadge.fromLabel(this.skillRating);
    }

    public RankBadge getBadge()      { return badge; }
    public float getBadgeCX()        { return badgeCX; }
    public float getBadgeCY()        { return badgeCY; }
    public float getBadgeRadius()    { return badgeRadius; }

    private void renderMainMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        java.util.List<MenuButtonDescriptor> descs = MenuButtonResolver.resolve(MenuState.MAIN, sessions, dailyCache);
        // Last item is always LOG OUT, rendered separately with special styling.
        int regularCount = descs.size() - 1;
        int logoutIdx    = descs.size() - 1;

        for (int i = 0; i < regularCount; i++) {
            MenuButtonDescriptor d = descs.get(i);
            if (d.sparkle) drawSparkleOption(batch, font, selection == i, true, true, true, d.label, x, y - (s * i));
            else           drawOption(batch, font, selection == i, true, d.label, x, y - (s * i));
        }

        float savedScale = font.getScaleX();

        String logoutText = (selection == logoutIdx) ? "> LOG OUT" : "LOG OUT";
        Color logoutColor = (selection == logoutIdx) ? new Color(1f, 0.55f, 0.2f, 1f) : new Color(0.85f, 0.4f, 0.2f, 0.9f);
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, logoutText, x + 1, y - (s * (regularCount + 1f)) - 1);
        font.setColor(logoutColor);
        font.draw(batch, logoutText, x, y - (s * (regularCount + 1f)));
        font.getData().setScale(savedScale);
    }

    private void renderPlayOptionsMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "RANDOM MAP", x, y);
        drawOption(batch, font, selection == 1, true, "SELECT MAP >", x, y - s);

        String cleanSeed = UIUtils.getClipboardSeed();
        boolean hasValidSeed = !cleanSeed.isEmpty();
        String seedText = hasValidSeed ? "PLAY SEED [" + cleanSeed + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(batch, font, selection == 2, hasValidSeed, seedText, x, y - (s * 2));

        drawOption(batch, font, selection == 3, true, "< BACK", x, y - (s * 3.5f));
    }

    private void renderMapSelectMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, int scrollOffset) {
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();
        int totalItems = archetypes.length + 1;
        int visibleCount = Math.min(SCROLL_WINDOW, totalItems);

        float savedScale = font.getScaleX();

        if (scrollOffset > 0) {
            font.getData().setScale(savedScale * 0.55f);
            font.setColor(Color.GRAY);
            font.draw(batch, "^ more", x, y + s * 0.5f);
            font.getData().setScale(savedScale);
        }

        for (int i = 0; i < visibleCount; i++) {
            int itemIdx = scrollOffset + i;
            if (itemIdx >= totalItems) break;
            float itemY = y - (s * i);
            if (itemIdx < archetypes.length) {
                drawOption(batch, font, selection == itemIdx, true, archetypeDisplayName(archetypes[itemIdx]), x, itemY);
            } else {
                drawOption(batch, font, selection == itemIdx, true, "< BACK", x, itemY);
            }
        }

        if (scrollOffset + visibleCount < totalItems) {
            font.getData().setScale(savedScale * 0.55f);
            font.setColor(Color.GRAY);
            font.draw(batch, "v more", x, y - (s * visibleCount));
            font.getData().setScale(savedScale);
        }
    }

    private void renderEighteenMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, CompetitiveSessions sessions, DailySubmissionCache dailyCache) {
        java.util.List<MenuButtonDescriptor> descs = MenuButtonResolver.resolve(MenuState.EIGHTEEN_HOLES, sessions, dailyCache);
        float[] yOffsets = {0, -s, -(s * 2), -(s * 3), -(s * 4.5f)};
        for (int i = 0; i < descs.size(); i++) {
            MenuButtonDescriptor d = descs.get(i);
            if (d.sparkle) drawSparkleOption(batch, font, selection == i, !d.locked, true, true, d.label, x, y + yOffsets[i]);
            else           drawOption(batch, font, selection == i, !d.locked, d.label, x, y + yOffsets[i]);
        }
    }

    private void renderDifficultyMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        com.gearygolf.golf.GameConfig.Difficulty[] diffs = com.gearygolf.golf.GameConfig.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            drawOption(batch, font, selection == i, true, diffs[i].name().replace("_", " "), x, y - (s * i));
        }
        drawOption(batch, font, selection == diffs.length, true, "< BACK", x, y - (s * (diffs.length + 0.5f)));
    }

    private void renderSettingsMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "SOUND",           x, y);
        drawOption(batch, font, selection == 1, true, "INSTRUCTIONS",   x, y - s);
        drawOption(batch, font, selection == 2, true, "PROFILE",        x, y - (s * 2));
        drawOption(batch, font, selection == 3, true, "< BACK TO MAIN", x, y - (s * 3.5f));
    }

    private void renderPracticeMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "PRACTICE RANGE", x, y);
        drawOption(batch, font, selection == 1, true, "PUTTING GREEN", x, y - s);
        if (TutorialPrefs.isComplete()) {
            drawOption(batch, font, selection == 2, true, "TUTORIAL",        x, y - (s * 2));
            drawOption(batch, font, selection == 3, true, "< BACK TO MAIN",  x, y - (s * 3.5f));
        } else {
            drawOption(batch, font, selection == 2, true, "< BACK TO MAIN",  x, y - (s * 2.5f));
        }
    }

    private void drawOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, String text, float x, float y) {
        String fullText = (selected && enabled) ? "> " + text : text;
        Color targetColor = !enabled ? Color.DARK_GRAY : (selected ? Color.YELLOW : Color.WHITE);

        float savedScale = font.getScaleX();
        layout.setText(font, fullText);
        if (layout.width > maxMenuTextWidth) {
            font.getData().setScale(savedScale * (maxMenuTextWidth / layout.width));
        }

        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, fullText, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, fullText, x, y);

        font.getData().setScale(savedScale);
    }

    private void drawSparkleOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, boolean sparkle, boolean warmText, String text, float x, float y) {
        if (!sparkle) {
            drawOption(batch, font, selected, enabled, text, x, y);
            return;
        }

        float savedScale = font.getScaleX();
        String fullText = (selected && enabled) ? "> " + text : text;
        layout.setText(font, fullText);
        float textWidth = Math.min(layout.width, maxMenuTextWidth);
        int textLength = text.length();

        float textSeed = (text.hashCode() & 0x7FFF) * 0.001f;
        int starCount = Math.max(5, (int)(textLength * 1.4f));
        font.getData().setScale(savedScale * 0.48f);
        for (int i = 0; i < starCount; i++) {
            float cycleLen = 1.8f + i * 0.11f;
            float offsetTime = pulseTimer + i * 0.137f * cycleLen;
            long cycleNumber = (long)(offsetTime / cycleLen);
            float phase = (offsetTime / cycleLen) - cycleNumber;

            float seed = i * 2.399f + (cycleNumber % 997) * 1.618f + textSeed;
            float relX = MathUtils.sin(seed) * 0.5f + 0.5f;
            float relY = MathUtils.sin(seed * 1.531f + 0.7f) * 0.4f;

            float alpha = 0.45f + 0.55f * MathUtils.sin(phase * MathUtils.PI2);
            if (alpha < 0.05f) continue;
            float sx = x + relX * textWidth;
            float sy = y - savedScale * 14f + relY * savedScale * 18f;
            font.setColor(1f, 0.88f, 0.2f, alpha);
            font.draw(batch, "*", sx, sy);
        }
        font.getData().setScale(savedScale);

        if (warmText && enabled) {
            layout.setText(font, fullText);
            if (layout.width > maxMenuTextWidth) font.getData().setScale(savedScale * (maxMenuTextWidth / layout.width));
            Color warmColor = selected ? new Color(1f, 0.92f, 0.38f, 1f) : new Color(1f, 0.85f, 0.52f, 0.95f);
            font.setColor(0, 0, 0, 0.7f);
            font.draw(batch, fullText, x + 1, y - 1);
            font.setColor(warmColor);
            font.draw(batch, fullText, x, y);
            font.getData().setScale(savedScale);
        } else {
            drawOption(batch, font, selected, enabled, text, x, y);
        }
    }

    public static String archetypeDisplayName(LevelData.Archetype arch) {
        String raw = arch.name().replace('_', ' ');
        StringBuilder sb = new StringBuilder();
        for (String word : raw.split(" ")) {
            if (!word.isEmpty()) {
                sb.append(Character.toUpperCase(word.charAt(0)));
                sb.append(word.substring(1).toLowerCase());
                sb.append(' ');
            }
        }
        return sb.toString().trim();
    }
}