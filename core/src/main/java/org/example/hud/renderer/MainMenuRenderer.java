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

public class MainMenuRenderer {

    public enum MenuState {
        MAIN, EIGHTEEN_HOLES, DIFFICULTY_SELECT, PRACTICE
    }

    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection, MenuState state, CompetitiveSessions sessions) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        String title = "GEARY GOLF";
        float baseTitleScale = isAndroid ? screenH * 0.0035f : screenH * 0.0043f;
        float titleY = screenH * 0.92f;

        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * (baseTitleScale * 0.03f);
        font.getData().setScale(pulse);
        layout.setText(font, title);

        float centeredX = (screenW - layout.width) / 2f;

        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, centeredX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, centeredX, titleY);

        if (!isAndroid) {
            renderDesktopMenu(batch, font, screenW, screenH, selection, state, sessions, baseTitleScale);
        }

        font.getData().setScale(1.0f);
    }

    private void renderDesktopMenu(SpriteBatch batch, BitmapFont font, float screenW, float screenH, int selection, MenuState state, CompetitiveSessions sessions, float baseScale) {
        float fixedLeftX = screenW * 0.03f;
        float menuStartY = screenH * 0.60f;
        float spacing = screenH * 0.075f;
        float optionScale = baseScale * 0.32f;

        font.getData().setScale(optionScale);

        switch (state) {
            case MAIN -> renderMainMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case EIGHTEEN_HOLES -> renderEighteenMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, sessions);
            case DIFFICULTY_SELECT -> renderDifficultyMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
            case PRACTICE -> renderPracticeMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
        }

        font.getData().setScale(optionScale * 0.6f);
        font.setColor(Color.GRAY);
        String hint = state == MenuState.MAIN ? "Use UP/DOWN to select, ENTER to start" : "ESC or BACK to return";
        font.draw(batch, hint, fixedLeftX, menuStartY - (spacing * 7f));
    }

    private void renderMainMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s) {
        drawOption(batch, font, selection == 0, true, "PLAY GAME", x, y);
        drawOption(batch, font, selection == 1, true, "18 HOLES >", x, y - s);
        drawOption(batch, font, selection == 2, true, "INSTRUCTIONS", x, y - (s * 2));
        drawOption(batch, font, selection == 3, true, "PRACTICE >", x, y - (s * 3));

        String cleanSeed = getClipboardSeed();
        boolean hasValidSeed = !cleanSeed.isEmpty();
        String seedText = hasValidSeed ? "PLAY SEED [" + cleanSeed + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(batch, font, selection == 4, hasValidSeed, seedText, x, y - (s * 4));
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
        drawOption(batch, font, selection == 1, !daily18Finished, daily18Text, x, y - s);
        drawOption(batch, font, selection == 2, !daily9Finished, daily9Text, x, y - (s * 2));
        drawOption(batch, font, selection == 3, !daily1Finished, daily1Text, x, y - (s * 3));
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
        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, fullText, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, fullText, x, y);
    }

    private String getClipboardSeed() {
        String content = Gdx.app.getClipboard().getContents();
        if (content != null && !content.isEmpty()) {
            try { return String.valueOf(Long.parseLong(content.trim())); } catch (Exception ignored) {}
        }
        return "";
    }
}
