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
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
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
import static com.gearygolf.golf.hud.UIUtils.createRoundedRectDrawable;
import static com.gearygolf.golf.hud.mobile.MobileUIValues.*;

public class MobileUIFactory {

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

    public static MobileUIPackage create(Viewport viewport, SpriteBatch batch, BitmapFont font, GameConfig config, MobileInputProcessor input, SpinIndicator spinIndicator, PreShotDebugActor debugActor) {
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
        ui.startMenuTable.top().left();
        ui.startMenuStage.addActor(ui.startMenuTable);

        setupGameplayLayout(ui, input, spinIndicator, debugActor, baseStyle, viewport, config);
        setupClubSelection(ui, input, baseStyle, viewport);
        setupPauseMenu(ui, font, viewport, config, input);
        setupVictoryMenu(ui, input, baseStyle, viewport);

        return ui;
    }

    private static void setupGameplayLayout(MobileUIPackage ui, MobileInputProcessor input, SpinIndicator spin, PreShotDebugActor debug, TextButton.TextButtonStyle style, Viewport viewport, GameConfig config) {
        ui.gameplayTable.clear();

        float rightEdge = 10f;
        float leftEdge = 5f;

        float btnW = getBtnWidth(viewport);
        float btnH = getBtnHeight(viewport);
        float spacing = getStackSpacing(viewport);
        float hitW = getHitBtnWidth(viewport);
        float hitH = getHitBtnHeight(viewport);

        float globalFontScale = FONT_SCALE_GAMEPLAY * 0.5f;

        Table leftStack = new Table();
        leftStack.top().left();
        addActionButton(leftStack, "PAUSE", style, input, GameInputProcessor.Action.PAUSE, btnW, btnH, globalFontScale).left().padBottom(spacing).row();
        Cell<TextButton> projCell = addActionButton(leftStack, "PROJECT", style, input, GameInputProcessor.Action.PROJECTION, btnW, btnH, globalFontScale);
        ui.projectBtn = projCell.getActor();
        projCell.left().padBottom(spacing).row();
        Cell<TextButton> distCell = addActionButton(leftStack, "DISTANCE", style, input, GameInputProcessor.Action.SHOW_RANGE, btnW, btnH, globalFontScale);
        ui.distanceBtn = distCell.getActor();
        distCell.left().padBottom(spacing).row();
        addActionButton(leftStack, "OVERVIEW", style, input, GameInputProcessor.Action.OVERHEAD_VIEW, btnW, btnH, globalFontScale).left().row();

        leftStack.add(debug).width(getDebugWidth(viewport)).height(viewport.getWorldHeight() * 0.18f).left().padTop(spacing).row();

        float spinSize = getSpinSize(viewport);

        Table spinRow = new Table();
        spinRow.add(spin).size(spinSize);
        leftStack.add(spinRow).bottom().left().padTop(spacing);

        // Scoreboard toggle button — floats to the right of the spindicator, only visible in multiplayer.
        ui.scoreboardToggleBtn = new TextButton("SCORES", style);
        ui.scoreboardToggleBtn.getLabel().setFontScale(globalFontScale * 0.42f);
        ui.scoreboardToggleBtn.setVisible(false);
        Table sbBtnWrapper = new Table();
        sbBtnWrapper.setFillParent(true);
        sbBtnWrapper.bottom().left().padLeft(leftEdge + spinSize + spacing).padBottom(spacing);
        sbBtnWrapper.add(ui.scoreboardToggleBtn).width(btnW).height(spinSize * 0.48f);
        ui.stage.addActor(sbBtnWrapper);

        // Live scoreboard panel — separate fillParent table, same anchor as the toggle button.
        ui.liveScoreboard = new LiveScoreboardActor(ui.skin, globalFontScale * 0.84f, () -> {
            ui.liveScoreboard.setVisible(false);
            ui.scoreboardToggleBtn.setVisible(true);
        });
        Table sbWrapper = new Table();
        sbWrapper.setFillParent(true);
        sbWrapper.bottom().left().padLeft(leftEdge + spinSize + spacing).padBottom(spacing);
        sbWrapper.add(ui.liveScoreboard);
        ui.stage.addActor(sbWrapper);

        Table rightStack = new Table();
        rightStack.top().right();

        TextButton.TextButtonStyle maxStyle = new TextButton.TextButtonStyle(style);
        maxStyle.up   = UIUtils.createEmbossedButtonDrawable(COLOR_MAX_UP, RADIUS_HIT, 5);
        maxStyle.down = UIUtils.createInsetButtonDrawable(COLOR_MAX_DOWN, RADIUS_HIT, 5);
        ui.maxHitBtn = createTriggerButton(maxStyle, "MAX", input, GameInputProcessor.Action.MAX_POWER_SHOT, globalFontScale);

        // TEST SWING button — debug only, shown in new swing mode in place of MAX.
        // Fires a synthetic perfect swing (+5° path) for distance calibration.
        TextButton.TextButtonStyle testStyle = new TextButton.TextButtonStyle(style);
        testStyle.up   = UIUtils.createEmbossedButtonDrawable(new Color(0f, 0.6f, 0.6f, 0.85f), RADIUS_HIT, 5);
        testStyle.down = UIUtils.createInsetButtonDrawable(new Color(0f, 0.4f, 0.4f, 0.85f), RADIUS_HIT, 5);
        //disabled for release
        ui.testSwingBtn = createTriggerButton(testStyle, "TEST", input, GameInputProcessor.Action.TEST_SWING, globalFontScale);
        ui.testSwingBtn.setVisible(false); // HUD.java toggles both at runtime based on swingModeNew

        // Both buttons share a single Stack cell so neither shifts the layout when hidden.
        // setVisible(false) on a plain Table cell still reserves the cell's height in LibGDX,
        // which is why both cannot be separate rows.
        Stack topBtnStack = new Stack();
        topBtnStack.add(ui.maxHitBtn);
        topBtnStack.add(ui.testSwingBtn);
        rightStack.add(topBtnStack).width(hitW * 0.8f).height(btnH).right().padBottom(spacing).row();

        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up   = UIUtils.createEmbossedButtonDrawable(COLOR_HIT_UP, RADIUS_HIT, 5);
        hitStyle.down = UIUtils.createInsetButtonDrawable(COLOR_HIT_DOWN, RADIUS_HIT, 5);
        ui.hitBtn = new TextButton("HIT", hitStyle);
        ui.hitBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 0.75f);
        ui.hitBtn.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (config.swingModeNew) {
                    // Tap-to-start / tap-to-cancel: one-shot trigger consumed by ShotController
                    input.triggerAction(GameInputProcessor.Action.CHARGE_SHOT);
                } else {
                    input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, true);
                }
                return true;
            }
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (!config.swingModeNew) {
                    input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, false);
                    boolean releasedOnButton = x >= 0 && x <= ui.hitBtn.getWidth() && y >= 0 && y <= ui.hitBtn.getHeight();
                    if (releasedOnButton) input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
                }
            }
        });
        rightStack.add(ui.hitBtn).width(hitW).height(hitH).right().padBottom(spacing).row();

        Drawable holdFill = createRoundedRectDrawable(new Color(0.95f, 0.60f, 0.10f, 0.70f), RADIUS_STD);
        ui.resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input, holdFill);
        ui.resetBallBtn.getLabel().setFontScale(globalFontScale * 0.78f);
        rightStack.add(ui.resetBallBtn).width(btnW).height(btnH).right().padBottom(spacing).row();

        ui.newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input, holdFill);
        ui.newMapBtn.getLabel().setFontScale(globalFontScale);
        rightStack.add(ui.newMapBtn).width(btnW).height(btnH).right().padBottom(spacing).row();

        ui.infoToggleBtn = new TextButton("INFO", style);
        ui.infoToggleBtn.getLabel().setFontScale(globalFontScale);
        rightStack.add(ui.infoToggleBtn).width(btnW).height(btnH).right();

        ui.leftPanel = leftStack;
        ui.gameplayTable.add(leftStack).expandX().fillY().left().padLeft(leftEdge).padTop(getLeftStackTopPad(viewport));
        ui.gameplayTable.add(rightStack).expandX().fillY().right().padRight(rightEdge).padTop(viewport.getWorldHeight() * (1.0f - MAX_BTN_Y));
    }

    private static Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h, float fontScale) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(fontScale);
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

    public static void buildStartMenuButtons(Table table, MenuManager menuManager, MenuManager.MenuHandler callback, CompetitiveSessions sessions, DailySubmissionCache dailyCache, Viewport viewport, BitmapFont font, com.gearygolf.golf.glamour.SoundManager soundManager) {
        table.clearChildren();
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        MainMenuRenderer.MenuState state = menuManager.getCurrentMenuState();
        String[] options = getOptionsForState(state);
        float screenH = viewport.getWorldHeight();

        float screenW = viewport.getWorldWidth();
        table.setSize(screenW * 0.485f, screenH);
        table.setPosition(0, 0);
        table.top().left().padLeft(screenW * 0.016f).padTop(screenH * 0.255f);

        float bW = screenW * 0.453f;
        float bH = screenH * 0.09f;
        float spacing = screenH * 0.010f;

        // MAP_SELECT uses a ScrollPane so the growing archetype list doesn't overflow the screen
        if (state == MainMenuRenderer.MenuState.MAP_SELECT) {
            Table innerTable = new Table();
            innerTable.top().left();
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                TextButton btn = new TextButton(options[i], menuStyle);
                float baseMenuScale = FONT_SCALE_START_MENU * 0.32f;
                GlyphLayout gl = new GlyphLayout();
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
            table.add(scrollPane).width(bW + screenW * 0.016f).height(screenH * 0.70f).top().left();
            return;
        }

        // For MAIN and EIGHTEEN_HOLES, descriptors from the resolver drive label/locked/sparkle.
        java.util.List<MenuButtonDescriptor> descs = MenuButtonResolver.resolve(state, sessions, dailyCache);

        Table innerTable = new Table();
        innerTable.top().left();

        for (int i = 0; i < options.length; i++) {
            final int index = i;
            String text = options[i];
            boolean isLocked = false;
            boolean doSparkle = false;

            if (descs != null && i < descs.size()) {
                MenuButtonDescriptor d = descs.get(i);
                text = d.label;
                isLocked = d.locked;
                doSparkle = d.sparkle;
            } else if (state == MainMenuRenderer.MenuState.PLAY_OPTIONS && i == 2) {
                String seed = UIUtils.getClipboardSeed();
                if (seed.isEmpty()) {
                    text = "PLAY SEED (EMPTY)";
                    isLocked = true;
                } else {
                    text = "PLAY SEED [" + seed + "]";
                }
            }

            TextButton btn = doSparkle ? new SparkleButton(text, menuStyle) : new TextButton(text, menuStyle);
            if (doSparkle) ((SparkleButton) btn).setSparkleEnabled(true);

            float baseMenuScale = FONT_SCALE_START_MENU * 0.32f;
            GlyphLayout gl = new GlyphLayout();
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
        table.add(scrollPane).width(bW + screenW * 0.016f).height(screenH * 0.70f).top().left();
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

    private static void setupClubSelection(MobileUIPackage ui, MobileInputProcessor input, TextButton.TextButtonStyle style, Viewport viewport) {
        ui.arrowContainer = new Table();
        Table arrowContainer = ui.arrowContainer;
        ui.stage.addActor(arrowContainer);
        arrowContainer.setFillParent(true);
        arrowContainer.bottom().right().padBottom(viewport.getWorldHeight() * CLUB_ARROW_Y).padRight(getEdgePad(viewport) * 0.5f);

        float innerW  = getArrowWidth(viewport);
        float outerW  = getSmallArrowWidth(viewport);
        float h       = getArrowHeight(viewport);
        float gapInner = viewport.getWorldWidth() * 0.02f;  // gap between < and >
        float gapOuter = viewport.getWorldWidth() * 0.01f;  // gap between outer and inner

        float smallFontScale = FONT_SCALE_GAMEPLAY * 0.45f;

        Table arrowRow = new Table();
        addActionButton(arrowRow, "<<", style, input, GameInputProcessor.Action.CLUB_FIRST, outerW, h, smallFontScale).padRight(gapOuter);
        addActionButton(arrowRow, "<",  style, input, GameInputProcessor.Action.CLUB_UP,    innerW, h).padRight(gapInner);
        addActionButton(arrowRow, ">",  style, input, GameInputProcessor.Action.CLUB_DOWN,  innerW, h).padRight(gapOuter);
        addActionButton(arrowRow, ">>", style, input, GameInputProcessor.Action.CLUB_LAST,  outerW, h, smallFontScale);
        arrowContainer.add(arrowRow);
        ui.clubArrowRow = arrowRow;
    }

    private static Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h) {
        return addActionButton(table, text, style, input, action, w, h, FONT_SCALE_GAMEPLAY * 0.6f);
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
                if (action == GameInputProcessor.Action.CHARGE_SHOT) {
                    boolean releasedOnButton = x >= 0 && x <= btn.getWidth() && y >= 0 && y <= btn.getHeight();
                    if (releasedOnButton) input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
                }
            }
        });
        return btn;
    }

    private static Skin setupSkin(BitmapFont font) {
        Skin skin = new Skin();
        skin.add("default", font);
        skin.add("white", createRoundedRectDrawable(Color.WHITE, 2), Drawable.class);
        skin.add("btnUp", createRoundedRectDrawable(COLOR_BTN_UP, RADIUS_STD), Drawable.class);
        skin.add("btnDown", createRoundedRectDrawable(COLOR_BTN_DOWN, RADIUS_STD), Drawable.class);
        skin.add("hitUp", createRoundedRectDrawable(COLOR_HIT_UP, RADIUS_HIT), Drawable.class);
        skin.add("hitDown", createRoundedRectDrawable(COLOR_HIT_DOWN, RADIUS_HIT), Drawable.class);
        skin.add("maxHitUp", createRoundedRectDrawable(COLOR_MAX_UP, RADIUS_HIT), Drawable.class);
        skin.add("maxHitDown", createRoundedRectDrawable(COLOR_MAX_DOWN, RADIUS_HIT), Drawable.class);
        TextField.TextFieldStyle tfs = new TextField.TextFieldStyle();
        tfs.font = font;
        tfs.fontColor = Color.WHITE;
        tfs.background = createRoundedRectDrawable(new Color(0.15f, 0.15f, 0.15f, 0.9f), RADIUS_STD);
        tfs.cursor = skin.newDrawable("white", Color.GOLD);
        tfs.cursor.setMinWidth(2f);
        tfs.selection = skin.newDrawable("white", new Color(0.3f, 0.3f, 0.8f, 0.5f));
        skin.add("default", tfs);
        Window.WindowStyle ws = new Window.WindowStyle();
        ws.titleFont = font;
        ws.titleFontColor = Color.GOLD;
        ws.background = createRoundedRectDrawable(new Color(0.05f, 0.05f, 0.05f, 0.95f), RADIUS_STD);
        skin.add("default", ws);
        TextButton.TextButtonStyle defaultBtnStyle = new TextButton.TextButtonStyle();
        defaultBtnStyle.font = font;
        defaultBtnStyle.fontColor = Color.WHITE;
        defaultBtnStyle.up   = skin.getDrawable("btnUp");
        defaultBtnStyle.down = skin.getDrawable("btnDown");
        skin.add("default", defaultBtnStyle);
        Label.LabelStyle defaultLabelStyle = new Label.LabelStyle();
        defaultLabelStyle.font = font;
        defaultLabelStyle.fontColor = Color.WHITE;
        skin.add("default", defaultLabelStyle);
        skin.add("default", new ScrollPane.ScrollPaneStyle());
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

        pauseTable.add(createMenuButton("SETTINGS", menuStyle, input, GameInputProcessor.Action.OPEN_SOUND_SETTINGS, scaledFont)).width(bW).height(bH).padBottom(pad).row();
        pauseTable.add(createMenuButton("INSTRUCTIONS", menuStyle, input, GameInputProcessor.Action.HELP, scaledFont)).width(bW).height(bH).padBottom(pad).row();
        pauseTable.add(createMenuButton("MAIN MENU", menuStyle, input, GameInputProcessor.Action.MAIN_MENU, scaledFont)).width(bW).height(bH).row();

        pauseTable.top().padTop(viewport.getWorldHeight() * 0.355f);

        // Seed + shot export buttons anchored to bottom-right, slightly smaller
        Table bottomRightTable = new Table();
        bottomRightTable.setFillParent(true);
        float sBtnW = viewport.getWorldWidth() * 0.2f, sBtnH = viewport.getWorldHeight() * 0.07f;
        float smallFont = scaledFont * 0.67f;
        float padRight  = viewport.getWorldWidth()  * 0.02f;
        float padBottom = viewport.getWorldHeight() * 0.02f;

        bottomRightTable.bottom().right().padRight(padRight).padBottom(padBottom);
        bottomRightTable.add(createMenuButton("COPY SEED", menuStyle, input, GameInputProcessor.Action.COPY_SEED, smallFont))
                .width(sBtnW).height(sBtnH).padBottom(viewport.getWorldHeight() * 0.01f).row();
        bottomRightTable.add(createMenuButton("EXPORT SHOT", menuStyle, input, GameInputProcessor.Action.EXPORT_SHOT, smallFont))
                .width(sBtnW).height(sBtnH);

        ui.pauseMenuStage.addActor(bottomRightTable);
    }

    private static void setupVictoryMenu(MobileUIPackage ui, MobileInputProcessor input, TextButton.TextButtonStyle style, Viewport viewport) {
        ui.victoryTable.clearChildren();
        ui.victoryTable.bottom().padBottom(viewport.getWorldHeight() * 0.01f);

        float btnW = viewport.getWorldWidth() * 0.28f;
        float btnH = viewport.getWorldHeight() * 0.1f;
        float fontScale = FONT_SCALE_GAMEPLAY * 0.8f;

        TextButton.TextButtonStyle primaryStyle = new TextButton.TextButtonStyle(style);
        primaryStyle.up = ui.skin.getDrawable("hitUp");
        primaryStyle.down = ui.skin.getDrawable("hitDown");

        ui.nextLevelBtn = new TextButton("NEXT LEVEL", primaryStyle);
        ui.nextLevelBtn.getLabel().setFontScale(fontScale);
        ui.nextLevelBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.NEW_LEVEL);
            }
        });

        ui.submitScoreBtn = new TextButton("SUBMIT SCORE", primaryStyle);
        ui.submitScoreBtn.getLabel().setFontScale(fontScale*.85f);
        ui.submitScoreBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.SUBMIT_SCORE);
            }
        });

        ui.uploadScoreBtn = new TextButton("UPLOAD SCORE", primaryStyle);
        ui.uploadScoreBtn.getLabel().setFontScale(fontScale*.85f);
        ui.uploadScoreBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.UPLOAD_SCORE);
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
        style.fontColor = Color.WHITE;
        Color base = new Color(0.38f, 0.26f, 0.14f, 0.95f);
        style.up   = UIUtils.createRaisedButtonDrawable(base, RADIUS_STD, 5);
        style.down = createRoundedRectDrawable(new Color(0.20f, 0.14f, 0.07f, 1f), RADIUS_STD);
        return style;
    }
}
