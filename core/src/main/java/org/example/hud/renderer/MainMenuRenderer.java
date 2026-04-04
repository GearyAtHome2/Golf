package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
import org.example.terrain.level.LevelData;

public class MainMenuRenderer {

    public enum MenuState {
        MAIN, EIGHTEEN_HOLES, DIFFICULTY_SELECT, PRACTICE, PLAY_OPTIONS, MAP_SELECT
    }

    private static final int SCROLL_WINDOW = 8;

    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();
    private float maxMenuTextWidth = Float.MAX_VALUE;

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection, MenuState state, CompetitiveSessions sessions, int mapScrollOffset) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

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

        if (!isAndroid) {
            renderDesktopMenu(batch, font, screenW, screenH, selection, state, sessions, baseTitleScale, mapScrollOffset);
        }

        font.getData().setScale(1.0f);
    }

    private void renderDesktopMenu(SpriteBatch batch, BitmapFont font, float screenW, float screenH, int selection, MenuState state, CompetitiveSessions sessions, float baseScale, int mapScrollOffset) {
        float fixedLeftX = screenW * 0.03f;
        float menuStartY = screenH * 0.60f;
        float spacing = screenH * 0.075f;
        float optionScale = baseScale * 0.32f;

        maxMenuTextWidth = screenW * 0.50f;
        font.getData().setScale(optionScale);

        switch (state) {
            case MAIN -> renderMainMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, sessions);
            case PLAY_OPTIONS -> renderPlayOptionsMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case MAP_SELECT -> renderMapSelectMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, mapScrollOffset);
            case EIGHTEEN_HOLES -> renderEighteenMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, sessions);
            case DIFFICULTY_SELECT -> renderDifficultyMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case PRACTICE -> renderPracticeMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
        }

        font.getData().setScale(optionScale * 0.6f);
        font.setColor(Color.GRAY);
        String hint = state == MenuState.MAIN ? "Use UP/DOWN to select, ENTER to start" : "ESC or BACK to return";
        font.draw(batch, hint, fixedLeftX, menuStartY - (spacing * 7f));

    }

    private void renderMainMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, CompetitiveSessions sessions) {
        boolean competitiveSparkle = anyCompetitiveUnfinished(sessions);
        drawOption(batch, font, selection == 0, true, "PLAY >", x, y);
        drawSparkleOption(batch, font, selection == 1, true, competitiveSparkle, true, "COMPETITIVE >", x, y - s);
        drawOption(batch, font, selection == 2, true, "INSTRUCTIONS", x, y - (s * 2));
        drawOption(batch, font, selection == 3, true, "PRACTICE >", x, y - (s * 3));
    }

    private void renderPlayOptionsMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "RANDOM MAP", x, y);
        drawOption(batch, font, selection == 1, true, "SELECT MAP >", x, y - s);

        String cleanSeed = getClipboardSeed();
        boolean hasValidSeed = !cleanSeed.isEmpty();
        String seedText = hasValidSeed ? "PLAY SEED [" + cleanSeed + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(batch, font, selection == 2, hasValidSeed, seedText, x, y - (s * 2));

        drawOption(batch, font, selection == 3, true, "< BACK", x, y - (s * 3.5f));
    }

    private void renderMapSelectMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, int scrollOffset) {
        LevelData.Archetype[] archetypes = LevelData.Archetype.values();
        int totalItems = archetypes.length + 1; // +1 for BACK
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

    private void renderEighteenMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, CompetitiveSessions sessions) {
        GameSession standard = sessions != null ? sessions.standard : null;
        GameSession daily18 = sessions != null ? sessions.daily18 : null;
        GameSession daily9 = sessions != null ? sessions.daily9 : null;
        GameSession daily1 = sessions != null ? sessions.daily1 : null;

        boolean standardFinished = standard != null && standard.isFinished();
        String play18Text = standardFinished
            ? "18 HOLES (COMPLETED)"
            : (standard != null ? "CONTINUE 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)" : "PLAY 18");

        boolean daily18Finished = daily18 != null && daily18.isFinished();
        String daily18Text = daily18Finished
            ? "DAILY 18 (COMPLETED)"
            : (daily18 != null ? "CONTINUE DAILY 18 (" + (daily18.getCurrentHoleIndex() + 1) + "/18)" : "DAILY 18");

        boolean daily9Finished = daily9 != null && daily9.isFinished();
        String daily9Text = daily9Finished
            ? "DAILY 9 (COMPLETED)"
            : (daily9 != null ? "CONTINUE DAILY 9 (" + (daily9.getCurrentHoleIndex() + 1) + "/9)" : "DAILY 9");

        boolean daily1Finished = daily1 != null && daily1.isFinished();
        String daily1Text = daily1Finished
            ? "DAILY 1-HOLE (COMPLETED)"
            : (daily1 != null ? "CONTINUE DAILY 1-HOLE (1/1)" : "DAILY 1-HOLE");

        drawOption(batch, font, selection == 0, !standardFinished, play18Text, x, y);
        drawSparkleOption(batch, font, selection == 1, !daily18Finished, !daily18Finished, true, daily18Text, x, y - s);
        drawSparkleOption(batch, font, selection == 2, !daily9Finished, !daily9Finished, true, daily9Text, x, y - (s * 2));
        drawSparkleOption(batch, font, selection == 3, !daily1Finished, !daily1Finished, true, daily1Text, x, y - (s * 3));
        drawOption(batch, font, selection == 4, true, "< BACK TO MAIN", x, y - (s * 4.5f));
    }

    private void renderDifficultyMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        org.example.GameConfig.Difficulty[] diffs = org.example.GameConfig.Difficulty.values();
        for (int i = 0; i < diffs.length; i++) {
            drawOption(batch, font, selection == i, true, diffs[i].name().replace("_", " "), x, y - (s * i));
        }
        drawOption(batch, font, selection == diffs.length, true, "< BACK", x, y - (s * (diffs.length + 0.5f)));
    }

    private void renderPracticeMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "PRACTICE RANGE", x, y);
        drawOption(batch, font, selection == 1, true, "PUTTING GREEN", x, y - s);
        drawOption(batch, font, selection == 2, true, "< BACK TO MAIN", x, y - (s * 2.5f));
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

        // Stars: twinkle and reappear at new positions each cycle
        float textSeed = (text.hashCode() & 0x7FFF) * 0.001f; // per-option offset
        int starCount = Math.max(5, (int)(textLength * 1.4f));
        font.getData().setScale(savedScale * 0.48f);
        for (int i = 0; i < starCount; i++) {
            float cycleLen = 1.8f + i * 0.11f;
            // pulseTimer is fine precision-wise (starts at 0); stagger phases per star
            float offsetTime = pulseTimer + i * 0.137f * cycleLen;
            long cycleNumber = (long)(offsetTime / cycleLen);
            float phase = (offsetTime / cycleLen) - cycleNumber; // precise 0..1

            // Position re-randomised each cycle, varies per option
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

        // Text: warm gold when sparkle+warmText, otherwise standard colours
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

    private static boolean anyCompetitiveUnfinished(CompetitiveSessions sessions) {
        if (sessions == null) return true;
        return isSessionUnfinished(sessions.daily18) || isSessionUnfinished(sessions.daily9) || isSessionUnfinished(sessions.daily1);
    }

    private static boolean isSessionUnfinished(GameSession s) {
        return s == null || !s.isFinished();
    }

    /** Formats an archetype enum value into a human-readable display name. */
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

    private String getClipboardSeed() {
        String content = Gdx.app.getClipboard().getContents();
        if (content != null && !content.isEmpty()) {
            try { return String.valueOf(Long.parseLong(content.trim())); } catch (Exception ignored) {}
        }
        return "";
    }
}
