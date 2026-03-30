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
        MAIN, EIGHTEEN_HOLES, DIFFICULTY_SELECT, PRACTICE
    }

    private float pulseTimer = 0;
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, BitmapFont font, Viewport viewport, int selection, MenuState state, GameSession standard, GameSession daily) {
        pulseTimer += Gdx.graphics.getDeltaTime();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        String title = "GEARY GOLF";
        float baseTitleScale = (screenH * 0.0035f) * (isAndroid ? 1.2f : 1.0f);

        // --- FIXED POSITIONING LOGIC ---
        // 1. Calculate a STATIC left padding for the title/menu using base scale (no pulse)
        font.getData().setScale(baseTitleScale);
        layout.setText(font, title);
        float staticTitleWidth = layout.width;

        // This X remains constant even when the title pulses
        float fixedLeftX = isAndroid ? screenW * 0.05f : screenW * 0.09f;

        float titleY = screenH * 0.92f;
        float menuStartY = screenH * 0.60f;
        float spacing = screenH * 0.075f;

        // 2. Title Rendering (With Pulse)
        float pulse = baseTitleScale + MathUtils.sin(pulseTimer * 2f) * (baseTitleScale * 0.03f);
        font.getData().setScale(pulse);

        // Draw Title (using the fixed X so it pulses from the center/left, rather than moving)
        font.setColor(0, 0, 0, 0.5f);
        font.draw(batch, title, fixedLeftX + 2, titleY - 2);
        font.setColor(Color.WHITE);
        font.draw(batch, title, fixedLeftX, titleY);

        // 3. Menu Options (Using the same fixed X)
        float optionScale = baseTitleScale * 0.35f;
        font.getData().setScale(optionScale);

        if (!isAndroid) {
            // Use fixedLeftX here so options stay perfectly aligned under the title
            switch (state) {
                case MAIN:
                    renderMainMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
                    break;
                case EIGHTEEN_HOLES:
                    renderEighteenMenu(batch, font, selection, fixedLeftX, menuStartY, spacing, standard, daily);
                    break;
                case DIFFICULTY_SELECT:
                    renderDifficultyMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
                    break;
                case PRACTICE:
                    renderPracticeMenu(batch, font, selection, fixedLeftX, menuStartY, spacing);
                    break;
            }

            // Hint Text - Centered to Screen
            font.getData().setScale(optionScale * 0.6f);
            font.setColor(Color.GRAY);
            String hint = state == MenuState.MAIN ? "Use UP/DOWN to select, ENTER to start" : "ESC or BACK to return";
            layout.setText(font, hint);
            font.draw(batch, hint, (screenW - layout.width) / 2f, menuStartY - (spacing * 7f));
        }

        font.getData().setScale(1.0f);
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

    private void renderEighteenMenu(SpriteBatch batch, BitmapFont font, int selection, float x, float y, float s, GameSession standard, GameSession daily) {
        boolean standardFinished = standard != null && standard.isFinished();
        String play18Text = standardFinished ? "18 HOLES (COMPLETED)" : (standard != null ? "CONTINUE 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)" : "PLAY 18");
        boolean dailyFinished = daily != null && daily.isFinished();
        String daily18Text = dailyFinished ? "DAILY 18 (COMPLETED)" : (daily != null ? "CONTINUE DAILY (" + (daily.getCurrentHoleIndex() + 1) + "/18)" : "DAILY 18");

        drawOption(batch, font, selection == 0, !standardFinished, play18Text, x, y);
        drawOption(batch, font, selection == 1, !dailyFinished, daily18Text, x, y - s);
        drawOption(batch, font, selection == 2, true, "< BACK TO MAIN", x, y - (s * 2.5f));
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