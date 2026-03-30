package org.example.hud.mobile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;
import org.example.gameManagers.GameSession;
import org.example.gameManagers.MenuManager;
import org.example.hud.HoldButton;
import org.example.hud.PreShotDebugActor;
import org.example.hud.SpinIndicator;
import org.example.hud.renderer.MainMenuRenderer;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;

import static org.example.hud.UIUtils.createRoundedRectDrawable;
import static org.example.hud.mobile.MobileUIValues.*;

public class MobileUIFactory {

    public static class MobileUIPackage {
        public Stage stage, startMenuStage, pauseMenuStage;
        public Table gameplayTable, victoryTable, startMenuTable;
        public TextButton infoToggleBtn;
        public HoldButton resetBallBtn, newMapBtn;
        public Label clubLabel;
        public Skin skin;
        public TextButton nextLevelBtn, submitScoreBtn, mainMenuBtn;
    }

    public static MobileUIPackage create(Viewport viewport, SpriteBatch batch, BitmapFont font, GameConfig config, MobileInputProcessor input, Texture whitePixel, SpinIndicator spinIndicator, PreShotDebugActor debugActor) {
        MobileUIPackage ui = new MobileUIPackage();
        ui.stage = new Stage(viewport, batch);
        ui.startMenuStage = new Stage(viewport, batch);
        ui.pauseMenuStage = new Stage(viewport, batch);

        ui.skin = setupSkin(font);
        TextButton.TextButtonStyle baseStyle = createBaseStyle(font, ui.skin);

        ui.gameplayTable = new Table();
        ui.gameplayTable.setFillParent(true);
        ui.stage.addActor(ui.gameplayTable);

        ui.victoryTable = new Table();
        ui.victoryTable.setFillParent(true);
        ui.victoryTable.setVisible(false);
        ui.stage.addActor(ui.victoryTable);

        ui.startMenuTable = new Table();
        ui.startMenuTable.setFillParent(true);
        ui.startMenuStage.addActor(ui.startMenuTable);

        setupLeftStack(ui, input, spinIndicator, debugActor, baseStyle, viewport);
        setupRightStack(ui, input, whitePixel, baseStyle, viewport);
        setupClubSelection(ui, input, baseStyle, viewport);
        setupPauseMenu(ui, font, viewport, config, input);
        setupVictoryMenu(ui, input, baseStyle, viewport);

        return ui;
    }

    /**
     * Refreshes the start menu buttons. Now handles the "IN PROGRESS" status for sessions.
     */
    public static void buildStartMenuButtons(Table table, MenuManager menuManager, MenuManager.MenuHandler callback, GameSession standard, GameSession daily, Viewport viewport, BitmapFont font) {
        table.clearChildren();
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);

        MainMenuRenderer.MenuState state = menuManager.getCurrentMenuState();
        String[] options = getOptionsForState(state);

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();

        float leftPadding = screenW * 0.05f;
        float bW = screenW * 0.45f;
        float bH = screenH * 0.09f;
        float spacing = screenH * 0.012f;

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            String text = options[i];
            boolean isDailyFinished = false;

            if (state == MainMenuRenderer.MenuState.EIGHTEEN_HOLES) {
                if (i == 0 && standard != null && !standard.isFinished()) {
                    text = "RESUME 18 (" + (standard.getCurrentHoleIndex() + 1) + "/18)";
                } else if (i == 1 && daily != null) {
                    if (daily.isFinished()) {
                        text = "DAILY CHALLENGE [COMPLETE]";
                        isDailyFinished = true;
                    } else {
                        text = "RESUME DAILY (" + (daily.getCurrentHoleIndex() + 1) + "/18)";
                    }
                }
            }

            TextButton btn = new TextButton(text, menuStyle);
            btn.getLabel().setFontScale(FONT_SCALE_START_MENU * 0.35f);

            if (isDailyFinished) {
                btn.setDisabled(true);
                btn.getLabel().setColor(Color.GRAY);
                btn.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
                // Using a darker tint and making the font color dim
                btn.setColor(0.17f, 0.17f, 0.17f, 1f);
                btn.getLabel().getStyle().fontColor = Color.DARK_GRAY;
            } else {
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        menuManager.handleExternalSelection(index, callback, standard, daily);
                    }
                });
            }

            table.add(btn).width(bW).height(bH).padBottom(spacing).left().row();
        }

        table.top().left().padTop(screenH * 0.22f).padLeft(leftPadding);
    }

    private static String[] getOptionsForState(MainMenuRenderer.MenuState state) {
        return switch (state) {
            case MAIN -> new String[]{"QUICK PLAY", "18 HOLES", "INSTRUCTIONS", "PRACTICE", "CLIPBOARD SEED"};
            case EIGHTEEN_HOLES -> new String[]{"STANDARD 18", "DAILY CHALLENGE", "BACK"};
            case PRACTICE -> new String[]{"DRIVING RANGE", "PUTTING GREEN", "BACK"};
            case DIFFICULTY_SELECT -> GameConfig.Difficulty.getNames();
        };
    }


    private static void setupLeftStack(MobileUIPackage ui, MobileInputProcessor input, SpinIndicator spin, PreShotDebugActor debug, TextButton.TextButtonStyle style, Viewport viewport) {
        Table leftStack = new Table();
        ui.gameplayTable.add(leftStack).expandY().fillY().left().padLeft(getEdgePad(viewport)).padTop(getLeftStackTopPad(viewport));
        Table leftButtons = new Table();
        float btnW = getBtnWidth(viewport);
        float btnH = getBtnHeight(viewport);
        float spacing = getStackSpacing(viewport);
        addActionButton(leftButtons, "MENU", style, input, GameInputProcessor.Action.PAUSE, btnW, btnH).padBottom(spacing).row();
        addActionButton(leftButtons, "PROJ", style, input, GameInputProcessor.Action.PROJECTION, btnW, btnH).padBottom(spacing).row();
        addActionButton(leftButtons, "DISTANCE", style, input, GameInputProcessor.Action.SHOW_RANGE, btnW, btnH).padBottom(spacing).row();
        addActionButton(leftButtons, "OVERVIEW", style, input, GameInputProcessor.Action.OVERHEAD_VIEW, btnW, btnH).row();
        leftStack.add(leftButtons).expandY().top().left().row();
        leftStack.add(debug).width(getDebugWidth(viewport)).height(viewport.getWorldHeight() * 0.18f).left().padBottom(spacing).row();
        leftStack.add(spin).size(getSpinSize(viewport)).bottom().left().padBottom(getEdgePad(viewport));
    }

    private static void setupRightStack(MobileUIPackage ui, MobileInputProcessor input, Texture whitePixel, TextButton.TextButtonStyle style, Viewport viewport) {
        Table rightStack = new Table();
        float edge = getEdgePad(viewport);
        float btnW = getBtnWidth(viewport);
        float btnH = getBtnHeight(viewport);
        float hitW = getHitBtnWidth(viewport);
        float hitH = getHitBtnHeight(viewport);
        float spacing = getStackSpacing(viewport);
        ui.gameplayTable.add(rightStack).expand().top().right().padRight(edge).padTop(viewport.getWorldHeight() * (1.0f - MAX_BTN_Y));
        TextButton.TextButtonStyle maxStyle = new TextButton.TextButtonStyle(style);
        maxStyle.up = ui.skin.getDrawable("maxHitUp");
        maxStyle.down = ui.skin.getDrawable("maxHitDown");
        rightStack.add(createTriggerButton(maxStyle, "MAX", input, GameInputProcessor.Action.MAX_POWER_SHOT, FONT_SCALE_GAMEPLAY * 0.5f)).width(hitW * 0.8f).height(btnH).right().padBottom(spacing).row();
        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up = ui.skin.getDrawable("hitUp");
        hitStyle.down = ui.skin.getDrawable("hitDown");
        rightStack.add(createTriggerButton(hitStyle, "HIT", input, GameInputProcessor.Action.CHARGE_SHOT, FONT_SCALE_GAMEPLAY * 0.85f)).width(hitW).height(hitH).right().padBottom(spacing).row();
        ui.resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input, whitePixel);
        ui.resetBallBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 0.5f);
        rightStack.add(ui.resetBallBtn).width(btnW).height(btnH).right().padBottom(spacing).row();
        ui.newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input, whitePixel);
        ui.newMapBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 0.5f);
        rightStack.add(ui.newMapBtn).width(btnW).height(btnH).right().padBottom(spacing).row();
        ui.infoToggleBtn = new TextButton("INFO", style);
        ui.infoToggleBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 0.5f);
        rightStack.add(ui.infoToggleBtn).width(btnW).height(btnH).right();
    }

    private static void setupClubSelection(MobileUIPackage ui, MobileInputProcessor input, TextButton.TextButtonStyle style, Viewport viewport) {
        Table arrowContainer = new Table();
        ui.stage.addActor(arrowContainer);
        arrowContainer.setFillParent(true);
        arrowContainer.bottom().right().padBottom(viewport.getWorldHeight() * CLUB_ARROW_Y).padRight(getEdgePad(viewport));
        Table arrowRow = new Table();
        addActionButton(arrowRow, "<", style, input, GameInputProcessor.Action.CLUB_UP, getArrowWidth(viewport), getArrowHeight(viewport)).padRight(10);
        addActionButton(arrowRow, ">", style, input, GameInputProcessor.Action.CLUB_DOWN, getArrowWidth(viewport), getArrowHeight(viewport));
        arrowContainer.add(arrowRow);
    }

    private static Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 0.6f);
        if (input != null && action != null) {
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (action == GameInputProcessor.Action.OVERHEAD_VIEW) input.setActionState(action, btn.isChecked());
                    else input.triggerAction(action);
                }
            });
        }
        return table.add(btn).width(w).height(h);
    }

    private static TextButton createTriggerButton(TextButton.TextButtonStyle style, String text, MobileInputProcessor input, GameInputProcessor.Action action, float fontScale) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(fontScale);
        btn.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(action, true);
                return true;
            }
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(action, false);
                if (action == GameInputProcessor.Action.CHARGE_SHOT) input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
            }
        });
        return btn;
    }

    private static Skin setupSkin(BitmapFont font) {
        Skin skin = new Skin();

        // 1. Core Resources
        skin.add("default", font);
        skin.add("white", createRoundedRectDrawable(Color.WHITE, 2), Drawable.class);

        // 2. Standard Button Drawables
        skin.add("btnUp", createRoundedRectDrawable(COLOR_BTN_UP, RADIUS_STD), Drawable.class);
        skin.add("btnDown", createRoundedRectDrawable(COLOR_BTN_DOWN, RADIUS_STD), Drawable.class);
        skin.add("hitUp", createRoundedRectDrawable(COLOR_HIT_UP, RADIUS_HIT), Drawable.class);
        skin.add("hitDown", createRoundedRectDrawable(COLOR_HIT_DOWN, RADIUS_HIT), Drawable.class);
        skin.add("maxHitUp", createRoundedRectDrawable(COLOR_MAX_UP, RADIUS_HIT), Drawable.class);
        skin.add("maxHitDown", createRoundedRectDrawable(COLOR_MAX_DOWN, RADIUS_HIT), Drawable.class);

        // 3. TextFieldStyle (Fixes first crash)
        TextField.TextFieldStyle tfs = new TextField.TextFieldStyle();
        tfs.font = font;
        tfs.fontColor = Color.WHITE;
        tfs.background = createRoundedRectDrawable(new Color(0.15f, 0.15f, 0.15f, 0.9f), RADIUS_STD);
        tfs.cursor = skin.newDrawable("white", Color.GOLD);
        tfs.cursor.setMinWidth(2f);
        tfs.selection = skin.newDrawable("white", new Color(0.3f, 0.3f, 0.8f, 0.5f));
        skin.add("default", tfs);

        // 4. WindowStyle (Fixes second crash for Dialogs)
        Window.WindowStyle ws = new Window.WindowStyle();
        ws.titleFont = font;
        ws.titleFontColor = Color.GOLD;
        ws.background = createRoundedRectDrawable(new Color(0.05f, 0.05f, 0.05f, 0.95f), RADIUS_STD);
        skin.add("default", ws);

        return skin;
    }

    private static TextButton.TextButtonStyle createBaseStyle(BitmapFont font, Skin skin) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = skin.getDrawable("btnUp");
        style.down = skin.getDrawable("btnDown");
        style.fontColor = Color.WHITE;
        return style;
    }

    private static void setupPauseMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport, GameConfig config, MobileInputProcessor input) {
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        ui.pauseMenuStage.addActor(pauseTable);
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        float bW = viewport.getWorldWidth() * 0.45f, bH = viewport.getWorldHeight() * 0.09f;
        float scaledFont = FONT_SCALE_PAUSE_MENU * 0.4f;
        pauseTable.add(createMenuButton("RESUME", menuStyle, input, GameInputProcessor.Action.PAUSE, scaledFont)).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();
        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.getLabel().setFontScale(scaledFont);
        animBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
            }
        });
        pauseTable.add(animBtn).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();
        pauseTable.add(createMenuButton("HELP", menuStyle, input, GameInputProcessor.Action.HELP, scaledFont)).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();
        pauseTable.add(createMenuButton("CAMERA", menuStyle, input, GameInputProcessor.Action.CAM_CONFIG, scaledFont)).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();
        pauseTable.add(createMenuButton("MAIN MENU", menuStyle, input, GameInputProcessor.Action.MAIN_MENU, scaledFont)).width(bW).height(bH).row();
        pauseTable.top().padTop(viewport.getWorldHeight() * 0.22f);
    }

    private static void setupVictoryMenu(MobileUIPackage ui, MobileInputProcessor input, TextButton.TextButtonStyle style, Viewport viewport) {
        ui.victoryTable.clearChildren();
        ui.victoryTable.bottom().padBottom(viewport.getWorldHeight() * 0.04f);

        float btnW = viewport.getWorldWidth() * 0.28f;
        float btnH = viewport.getWorldHeight() * 0.12f;
        float fontScale = FONT_SCALE_GAMEPLAY * 0.6f;

        TextButton.TextButtonStyle primaryStyle = new TextButton.TextButtonStyle(style);
        primaryStyle.up = ui.skin.getDrawable("hitUp");
        primaryStyle.down = ui.skin.getDrawable("hitDown");

        // Initialize buttons
        ui.nextLevelBtn = new TextButton("NEXT LEVEL", primaryStyle);
        ui.nextLevelBtn.getLabel().setFontScale(fontScale);
        ui.nextLevelBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.NEW_LEVEL);
            }
        });

        ui.submitScoreBtn = new TextButton("SUBMIT SCORE", primaryStyle);
        ui.submitScoreBtn.getLabel().setFontScale(fontScale);
        ui.submitScoreBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.SUBMIT_SCORE);
            }
        });

        ui.mainMenuBtn = new TextButton("MAIN MENU", style);
        ui.mainMenuBtn.getLabel().setFontScale(fontScale);
        ui.mainMenuBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.MAIN_MENU);
            }
        });

        Table buttonTable = new Table();

        buttonTable.add(ui.submitScoreBtn).width(btnW).height(btnH).left().padLeft(20);

        buttonTable.add().width(viewport.getWorldWidth() * 0.25f);

        buttonTable.add(ui.mainMenuBtn).width(btnW).height(btnH).right().padRight(20);

        ui.victoryTable.add(ui.nextLevelBtn).width(viewport.getWorldWidth() * 0.35f).height(btnH).center();
        ui.victoryTable.row();
        ui.victoryTable.add(buttonTable).center(); // Center the whole button row
    }

    private static TextButton createMenuButton(String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float fontScale) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(fontScale);
        if (action != null) {
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    input.triggerAction(action);
                }
            });
        }
        return btn;
    }

    private static TextButton.TextButtonStyle createMenuStyle(BitmapFont font) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = createRoundedRectDrawable(COLOR_MENU_UP, RADIUS_STD);
        style.down = createRoundedRectDrawable(COLOR_MENU_DOWN, RADIUS_STD);
        style.fontColor = Color.WHITE;
        return style;
    }
}