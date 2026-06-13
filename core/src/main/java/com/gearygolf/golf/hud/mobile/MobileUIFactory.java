package com.gearygolf.golf.hud.mobile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.multiplayer.LiveScoreboardActor;
import com.gearygolf.golf.session.CompetitiveSessions;
import com.gearygolf.golf.gameManagers.MenuManager;
import com.gearygolf.golf.hud.SparkleButton;
import com.gearygolf.golf.terrain.level.LevelData;
import com.gearygolf.golf.hud.HoldButton;
import com.gearygolf.golf.hud.PreShotDebugActor;
import com.gearygolf.golf.hud.SpinIndicator;
import com.gearygolf.golf.hud.renderer.MainMenuRenderer;
import com.gearygolf.golf.input.GameInputProcessor;
import com.gearygolf.golf.input.MobileInputProcessor;
import com.gearygolf.golf.scoreBoard.DailySubmissionCache;

import com.gearygolf.golf.hud.MenuButtonDescriptor;
import com.gearygolf.golf.hud.MenuButtonResolver;
import com.gearygolf.golf.hud.UIUtils;
import com.gearygolf.golf.tutorial.TutorialPrefs;
import static com.gearygolf.golf.hud.mobile.MobileUIValues.*;

/**
 * Orchestrates creation of all mobile UI stages.
 * Style creation is delegated to {@link MobileButtonStyles}.
 * Gameplay/club-selection layout is delegated to {@link GameplayLayoutBuilder}.
 */
public class MobileUIFactory {

    private static final GlyphLayout gl = new GlyphLayout();

    public static class MobileUIPackage {
        public Stage stage, startMenuStage, pauseMenuStage;
        public Table gameplayTable, victoryTable, startMenuTable;
        public TextButton infoToggleBtn;
        public TextButton scoreboardToggleBtn;
        public LiveScoreboardActor liveScoreboard;
        public HoldButton resetBallBtn, newMapBtn;
        public TextButton difficultyBtn, projectBtn, distanceBtn;
        /** Left column: PAUSE / PROJECT / DISTANCE / OVERVIEW + spin indicator. Hidden during swing view. */
        public Table leftPanel;
        public Label clubLabel;
        public Skin skin;
        public TextButton nextLevelBtn, submitScoreBtn, uploadScoreBtn, mainMenuBtn;
        public Table arrowContainer;
        public Table clubArrowRow;
        public TextButton hitBtn, maxHitBtn, testSwingBtn;
    }

    public static MobileUIPackage create(Viewport viewport, SpriteBatch batch, BitmapFont font,
                                          GameConfig config, MobileInputProcessor input,
                                          SpinIndicator spinIndicator, PreShotDebugActor debugActor) {
        MobileUIPackage ui = new MobileUIPackage();
        ui.stage           = new Stage(viewport, batch);
        ui.startMenuStage  = new Stage(viewport, batch);
        ui.pauseMenuStage  = new Stage(viewport, batch);

        ui.skin = MobileButtonStyles.buildSkin(font);
        TextButton.TextButtonStyle baseStyle = MobileButtonStyles.baseStyle(font, ui.skin);

        ui.gameplayTable = new Table();
        ui.gameplayTable.setFillParent(true);
        ui.stage.addActor(ui.gameplayTable);

        ui.victoryTable = new Table();
        ui.victoryTable.setFillParent(true);
        ui.victoryTable.setVisible(false);
        ui.stage.addActor(ui.victoryTable);

        ui.startMenuTable = new Table();
        ui.startMenuTable.top().left();
        ui.startMenuStage.addActor(ui.startMenuTable);

        GameplayLayoutBuilder.buildGameplayLayout(ui, input, spinIndicator, debugActor, baseStyle, viewport, config);
        GameplayLayoutBuilder.buildClubSelection(ui, input, baseStyle, viewport);
        setupPauseMenu(ui, font, viewport, config, input);
        setupVictoryMenu(ui, input, baseStyle, viewport);

        return ui;
    }

    // ── Start menu ───────────────────────────────────────────────────────────────────────────────────

    public static void buildStartMenuButtons(Table table, MenuManager menuManager,
                                              MenuManager.MenuHandler callback,
                                              CompetitiveSessions sessions,
                                              DailySubmissionCache dailyCache,
                                              Viewport viewport, BitmapFont font,
                                              com.gearygolf.golf.glamour.SoundManager soundManager) {
        table.clearChildren();
        TextButton.TextButtonStyle menuStyle = MobileButtonStyles.menuStyle(font);
        MainMenuRenderer.MenuState state = menuManager.getCurrentMenuState();
        String[] options = getOptionsForState(state);
        float screenH = viewport.getWorldHeight();
        float screenW = viewport.getWorldWidth();

        table.setSize(screenW * MENU_TABLE_WIDTH_FRAC, screenH);
        table.setPosition(0, 0);
        table.top().left().padLeft(screenW * MENU_PAD_LEFT_FRAC).padTop(screenH * MENU_PAD_TOP_FRAC);

        float bW      = screenW * MENU_BUTTON_WIDTH_FRAC;
        float bH      = screenH * MENU_BUTTON_HEIGHT_FRAC;
        float spacing = screenH * MENU_SPACING_FRAC;

        // MAP_SELECT uses a ScrollPane so the growing archetype list doesn't overflow the screen
        if (state == MainMenuRenderer.MenuState.MAP_SELECT) {
            Table innerTable = new Table();
            innerTable.top().left();
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                TextButton btn = new TextButton(options[i], menuStyle);
                float baseMenuScale = FONT_SCALE_START_MENU * 0.32f;
                btn.getLabel().setFontScale(UIUtils.fitFontScale(font, gl, options[i], baseMenuScale, bW * 0.85f));
                btn.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        if (soundManager != null) soundManager.playButtonDown(); return false;
                    }
                });
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        menuManager.handleExternalSelection(index, callback, sessions, dailyCache);
                    }
                });
                innerTable.add(btn).width(bW).height(bH).padBottom(spacing).left().row();
            }
            ScrollPane.ScrollPaneStyle sps = new ScrollPane.ScrollPaneStyle();
            ScrollPane scrollPane = new ScrollPane(innerTable, sps);
            scrollPane.setScrollingDisabled(true, false);
            scrollPane.setOverscroll(false, false);
            scrollPane.setFadeScrollBars(true);
            scrollPane.setSmoothScrolling(true);
            table.add(scrollPane).width(bW + screenW * MENU_PAD_LEFT_FRAC).height(screenH * MENU_SCROLL_HEIGHT_FRAC).top().left();
            return;
        }

        // For MAIN and EIGHTEEN_HOLES, descriptors from the resolver drive label/locked/sparkle.
        java.util.List<MenuButtonDescriptor> descs = MenuButtonResolver.resolve(state, sessions, dailyCache);

        Table innerTable = new Table();
        innerTable.top().left();

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            String text = options[i];
            boolean isLocked  = false;
            boolean doSparkle = false;

            if (descs != null && i < descs.size()) {
                MenuButtonDescriptor d = descs.get(i);
                text      = d.label;
                isLocked  = d.locked;
                doSparkle = d.sparkle;
            } else if (state == MainMenuRenderer.MenuState.PLAY_OPTIONS && i == 2) {
                String seed = UIUtils.getClipboardSeed();
                if (seed.isEmpty()) {
                    text     = "PLAY SEED (EMPTY)";
                    isLocked = true;
                } else {
                    text = "PLAY SEED [" + seed + "]";
                }
            }

            TextButton btn = doSparkle ? new SparkleButton(text, menuStyle) : new TextButton(text, menuStyle);
            if (doSparkle) ((SparkleButton) btn).setSparkleEnabled(true);

            float baseMenuScale = FONT_SCALE_START_MENU * 0.32f;
            btn.getLabel().setFontScale(UIUtils.fitFontScale(font, gl, text, baseMenuScale, bW * 0.85f));

            if (isLocked) {
                btn.setDisabled(true);
                btn.getLabel().setColor(Color.GRAY);
                btn.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
                btn.setColor(0.17f, 0.17f, 0.17f, 1f);
                if (btn.getStyle().fontColor != null) {
                    btn.getLabel().getStyle().fontColor = Color.DARK_GRAY;
                }
            } else {
                btn.addListener(new InputListener() {
                    @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                        if (soundManager != null) soundManager.playButtonDown(); return false;
                    }
                });
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        menuManager.handleExternalSelection(index, callback, sessions, dailyCache);
                    }
                });
            }
            innerTable.add(btn).width(bW).height(bH).padBottom(spacing).left().row();
        }

        ScrollPane.ScrollPaneStyle sps = new ScrollPane.ScrollPaneStyle();
        ScrollPane scrollPane = new ScrollPane(innerTable, sps);
        scrollPane.setScrollingDisabled(true, false);
        scrollPane.setOverscroll(false, false);
        scrollPane.setFadeScrollBars(true);
        scrollPane.setSmoothScrolling(true);
        table.add(scrollPane).width(bW + screenW * MENU_PAD_LEFT_FRAC).height(screenH * MENU_SCROLL_HEIGHT_FRAC).top().left();
    }

    private static String[] getOptionsForState(MainMenuRenderer.MenuState state) {
        return switch (state) {
            case MAIN -> TutorialPrefs.isComplete()
                ? new String[]{"PLAY", "COMPETITIVE", "SETTINGS", "PRACTICE", "MULTIPLAYER", "LOG OUT"}
                : new String[]{"TUTORIAL", "PLAY", "COMPETITIVE", "SETTINGS", "PRACTICE", "MULTIPLAYER", "LOG OUT"};
            case SETTINGS -> new String[]{"SOUND", "INSTRUCTIONS", "PROFILE", "< BACK"};
            case PLAY_OPTIONS -> new String[]{"RANDOM MAP", "SELECT MAP", "PLAY SEED", "BACK"};
            case MAP_SELECT -> {
                LevelData.Archetype[] archetypes = LevelData.Archetype.values();
                String[] result = new String[archetypes.length + 1];
                for (int i = 0; i < archetypes.length; i++) {
                    result[i] = MainMenuRenderer.archetypeDisplayName(archetypes[i]);
                }
                result[archetypes.length] = "BACK";
                yield result;
            }
            case EIGHTEEN_HOLES -> new String[]{"STANDARD 18", "DAILY 18", "DAILY 9", "DAILY PAR 3", "DAILY PAR 4", "DAILY PAR 5", "< BACK"};
            case PRACTICE -> TutorialPrefs.isComplete()
                ? new String[]{"DRIVING RANGE", "PUTTING GREEN", "TUTORIAL", "IMPORT SHOT", "BACK"}
                : new String[]{"DRIVING RANGE", "PUTTING GREEN", "IMPORT SHOT", "BACK"};
            case DIFFICULTY_SELECT -> {
                String[] diffs = GameConfig.Difficulty.getNames();
                String[] withBack = new String[diffs.length + 1];
                System.arraycopy(diffs, 0, withBack, 0, diffs.length);
                withBack[diffs.length] = "BACK";
                yield withBack;
            }
        };
    }

    // ── Pause menu ───────────────────────────────────────────────────────────────────────────────────

    private static void setupPauseMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport,
                                        GameConfig config, MobileInputProcessor input) {
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        ui.pauseMenuStage.addActor(pauseTable);
        TextButton.TextButtonStyle menuStyle = MobileButtonStyles.menuStyle(font);
        float bW = viewport.getWorldWidth() * 0.45f, bH = viewport.getWorldHeight() * 0.075f;
        float scaledFont = FONT_SCALE_PAUSE_MENU * 0.4f;
        float pad = viewport.getWorldHeight() * 0.009f;

        pauseTable.add(createMenuButton("RESUME", menuStyle, input, GameInputProcessor.Action.PAUSE, scaledFont)).width(bW).height(bH).padBottom(pad).row();

        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.getLabel().setFontScale(scaledFont);
        animBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
            }
        });
        pauseTable.add(animBtn).width(bW).height(bH).padBottom(pad).row();

        ui.difficultyBtn = new TextButton("DIFFICULTY: " + config.difficulty.name(), menuStyle);
        ui.difficultyBtn.getLabel().setFontScale(scaledFont * 0.72f);
        ui.difficultyBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                if (!ui.difficultyBtn.isDisabled()) {
                    config.cycleDifficulty();
                    ui.difficultyBtn.setText("DIFFICULTY: " + config.difficulty.name());
                }
            }
        });
        pauseTable.add(ui.difficultyBtn).width(bW).height(bH).padBottom(pad).row();

        pauseTable.add(createMenuButton("SETTINGS",     menuStyle, input, GameInputProcessor.Action.OPEN_SOUND_SETTINGS, scaledFont)).width(bW).height(bH).padBottom(pad).row();
        pauseTable.add(createMenuButton("INSTRUCTIONS", menuStyle, input, GameInputProcessor.Action.HELP,                scaledFont)).width(bW).height(bH).padBottom(pad).row();
        pauseTable.add(createMenuButton("MAIN MENU",    menuStyle, input, GameInputProcessor.Action.MAIN_MENU,           scaledFont)).width(bW).height(bH).row();

        pauseTable.top().padTop(viewport.getWorldHeight() * 0.355f);

        // Seed + shot export buttons anchored to bottom-right
        Table bottomRightTable = new Table();
        bottomRightTable.setFillParent(true);
        float sBtnW     = viewport.getWorldWidth()  * 0.2f;
        float sBtnH     = viewport.getWorldHeight() * 0.07f;
        float smallFont = scaledFont * 0.67f;
        float padRight  = viewport.getWorldWidth()  * 0.02f;
        float padBottom = viewport.getWorldHeight() * 0.02f;

        bottomRightTable.bottom().right().padRight(padRight).padBottom(padBottom);
        bottomRightTable.add(createMenuButton("COPY SEED",   menuStyle, input, GameInputProcessor.Action.COPY_SEED,   smallFont)).width(sBtnW).height(sBtnH).padBottom(viewport.getWorldHeight() * 0.01f).row();
        bottomRightTable.add(createMenuButton("EXPORT SHOT", menuStyle, input, GameInputProcessor.Action.EXPORT_SHOT, smallFont)).width(sBtnW).height(sBtnH);

        ui.pauseMenuStage.addActor(bottomRightTable);
    }

    // ── Victory menu ─────────────────────────────────────────────────────────────────────────────────

    private static void setupVictoryMenu(MobileUIPackage ui, MobileInputProcessor input,
                                          TextButton.TextButtonStyle style, Viewport viewport) {
        ui.victoryTable.clearChildren();
        ui.victoryTable.bottom().padBottom(viewport.getWorldHeight() * 0.01f);

        float btnW      = viewport.getWorldWidth()  * 0.28f;
        float btnH      = viewport.getWorldHeight() * 0.1f;
        float fontScale = FONT_SCALE_GAMEPLAY * 0.8f;

        TextButton.TextButtonStyle primaryStyle = new TextButton.TextButtonStyle(style);
        primaryStyle.up   = ui.skin.getDrawable("hitUp");
        primaryStyle.down = ui.skin.getDrawable("hitDown");

        ui.nextLevelBtn = new TextButton("NEXT LEVEL", primaryStyle);
        ui.nextLevelBtn.getLabel().setFontScale(fontScale);
        ui.nextLevelBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.NEW_LEVEL);
            }
        });

        ui.submitScoreBtn = new TextButton("SUBMIT SCORE", primaryStyle);
        ui.submitScoreBtn.getLabel().setFontScale(fontScale * .85f);
        ui.submitScoreBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.SUBMIT_SCORE);
            }
        });

        ui.uploadScoreBtn = new TextButton("UPLOAD SCORE", primaryStyle);
        ui.uploadScoreBtn.getLabel().setFontScale(fontScale * .85f);
        ui.uploadScoreBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.UPLOAD_SCORE);
            }
        });

        ui.mainMenuBtn = new TextButton("MAIN MENU", style);
        ui.mainMenuBtn.getLabel().setFontScale(fontScale);
        ui.mainMenuBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.MAIN_MENU);
            }
        });

        // submitScoreBtn (daily) and uploadScoreBtn (standard 18) share the left slot —
        // they are mutually exclusive; HUD.renderVictory() controls which is visible.
        Stack leftBtnStack = new Stack();
        leftBtnStack.add(ui.submitScoreBtn);
        leftBtnStack.add(ui.uploadScoreBtn);

        Table buttonTable = new Table();
        buttonTable.setFillParent(false);
        buttonTable.add(leftBtnStack).width(btnW).height(btnH).left();
        buttonTable.add().expandX();
        buttonTable.add(ui.mainMenuBtn).width(btnW).height(btnH).right();

        ui.victoryTable.add(ui.nextLevelBtn).width(viewport.getWorldWidth() * 0.35f).height(btnH).center().padBottom(0);
        ui.victoryTable.row();
        float hPad = viewport.getWorldWidth() * 0.023f;
        ui.victoryTable.add(buttonTable).expandX().fillX().padLeft(hPad).padRight(hPad);
    }

    // ── Private helpers ──────────────────────────────────────────────────────────────────────────────

    private static TextButton createMenuButton(String text, TextButton.TextButtonStyle style,
                                                MobileInputProcessor input,
                                                GameInputProcessor.Action action,
                                                float fontScale) {
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
}
