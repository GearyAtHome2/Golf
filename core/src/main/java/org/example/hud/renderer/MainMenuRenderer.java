package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.gameManagers.GameSession;

public class MainMenuRenderer {

    public enum MenuState {
        MAIN,
        EIGHTEEN_HOLES,
        DIFFICULTY_SELECT,
        PRACTICE
    }

    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection, MenuState state, GameSession standard, GameSession daily) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        float centerX = screenW / 2f;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        float baseTitleScale = (screenH * 0.0035f) * (isAndroid ? 1.2f : 1.0f);
        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * (baseTitleScale * 0.03f);
        font.getData().setScale(pulse);

        String title = "GEARY GOLF";
        layout.setText(font, title);
        float titleY = screenH * 0.92f;

        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, centerX - (layout.width / 2f) + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, centerX - (layout.width / 2f), titleY);

        float optionScale = baseTitleScale * 0.35f;
        font.getData().setScale(optionScale);
        float spacing = screenH * 0.065f;
        float menuStartY = screenH * 0.55f;

        if (!isAndroid) {
            switch (state) {
                case MAIN:
                    renderMainMenu(batch, font, selection, centerX, menuStartY, spacing);
                    break;
                case EIGHTEEN_HOLES:
                    renderEighteenMenu(batch, font, selection, centerX, menuStartY, spacing, standard, daily);
                    break;
                case DIFFICULTY_SELECT:
                    renderDifficultyMenu(batch, font, selection, centerX, menuStartY, spacing);
                    break;
                case PRACTICE:
                    renderPracticeMenu(batch, font, selection, centerX, menuStartY, spacing);
                    break;
            }

            font.getData().setScale(optionScale * 0.6f);
            font.setColor(Color.GRAY);
            String hint = state == MenuState.MAIN ? "Use UP/DOWN to select, ENTER to start" : "ESC or BACK to return";
            layout.setText(font, hint);
            font.draw(batch, hint, centerX - (layout.width / 2f), menuStartY - (spacing * 7f));
        }

        font.getData().setScale(1.0f);
    }

    private void renderMainMenu(SpriteBatch batch, BitmapFont font, int selection, float cx, float y, float s) {
        drawCenteredOption(batch, font, selection == 0, true, "PLAY GAME", cx, y);
        drawCenteredOption(batch, font, selection == 1, true, "18 HOLES >", cx, y - s);
        drawCenteredOption(batch, font, selection == 2, true, "INSTRUCTIONS", cx, y - (s * 2));
        drawCenteredOption(batch, font, selection == 3, true, "PRACTICE >", cx, y - (s * 3));

        String cleanSeed = getClipboardSeed();
        boolean hasValidSeed = !cleanSeed.isEmpty();
        String seedText = hasValidSeed ? "PLAY SEED [" + cleanSeed + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawCenteredOption(batch, font, selection == 4, hasValidSeed, seedText, cx, y - (s * 4));
    }

    private void renderEighteenMenu(SpriteBatch batch, BitmapFont font, int selection, float cx, float y, float s, GameSession standard, GameSession daily) {
        String play18Text = (standard != null && !standard.isFinished())
                ? "CONTINUE 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)"
                : "PLAY 18";

        String daily18Text = (daily != null && !daily.isFinished())
                ? "CONTINUE DAILY (" + (daily.getCurrentHoleIndex() + 1) + "/18)"
                : "DAILY 18";

        drawCenteredOption(batch, font, selection == 0, true, play18Text, cx, y);
        drawCenteredOption(batch, font, selection == 1, true, daily18Text, cx, y - s);
        drawCenteredOption(batch, font, selection == 2, true, "< BACK TO MAIN", cx, y - (s * 2.5f));
    }

    private void renderDifficultyMenu(SpriteBatch batch, BitmapFont font, int selection, float cx, float y, float s) {
        org.example.GameConfig.Difficulty[] diffs = org.example.GameConfig.Difficulty.values();

        for (int i = 0; i < diffs.length; i++) {
            drawCenteredOption(batch, font, selection == i, true, diffs[i].name().replace("_", " "), cx, y - (s * i));
        }

        int backIndex = diffs.length;
        drawCenteredOption(batch, font, selection == backIndex, true, "< BACK", cx, y - (s * (backIndex + 0.5f)));
    }

    private void renderPracticeMenu(SpriteBatch batch, BitmapFont font, int selection, float cx, float y, float s) {
        drawCenteredOption(batch, font, selection == 0, true, "PRACTICE RANGE", cx, y);
        drawCenteredOption(batch, font, selection == 1, true, "PUTTING GREEN", cx, y - s);
        drawCenteredOption(batch, font, selection == 2, true, "< BACK TO MAIN", cx, y - (s * 2.5f));
    }

    private String getClipboardSeed() {
        String content = Gdx.app.getClipboard().getContents();
        if (content != null && !content.isEmpty()) {
            try {
                return String.valueOf(Long.parseLong(content.trim()));
            } catch (NumberFormatException ignored) {}
        }
        return "";
    }

    private void drawCenteredOption(SpriteBatch batch, BitmapFont font, boolean selected, boolean enabled, String text, float centerX, float y) {
        String fullText = (selected && enabled) ? "> " + text : text;
        layout.setText(font, fullText);
        float x = centerX - (layout.width / 2f);
        Color targetColor = !enabled ? Color.DARK_GRAY : (selected ? Color.YELLOW : Color.WHITE);

        font.setColor(0, 0, 0, 0.7f);
        font.draw(batch, fullText, x + 1, y - 1);
        font.setColor(targetColor);
        font.draw(batch, fullText, x, y);
    }
}