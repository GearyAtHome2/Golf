package org.example.hud.mobile;

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
import org.example.GameConfig;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
import org.example.gameManagers.MenuManager;
import org.example.hud.SparkleButton;
import org.example.terrain.level.LevelData;
import org.example.hud.HoldButton;
import org.example.hud.PreShotDebugActor;
import org.example.hud.SpinIndicator;
import org.example.hud.renderer.MainMenuRenderer;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.scoreBoard.DailySubmissionCache;

import org.example.hud.MenuButtonDescriptor;
import org.example.hud.MenuButtonResolver;
import org.example.hud.UIUtils;
import static org.example.hud.UIUtils.createRoundedRectDrawable;
import static org.example.hud.mobile.MobileUIValues.*;

public class MobileUIFactory {

public static class MobileUIPackage {
        public Stage stage, startMenuStage, pauseMenuStage;
        public Table gameplayTable, victoryTable, startMenuTable;
        public TextButton infoToggleBtn;
        public HoldButton resetBallBtn, newMapBtn;
        public TextButton difficultyBtn, projectBtn, distanceBtn;
        public Label clubLabel;
        public Skin skin;
        public TextButton nextLevelBtn, submitScoreBtn, mainMenuBtn;
        public Table arrowContainer;
        // Debug: hit/max buttons exposed so style can be cycled at runtime
        public TextButton hitBtn, maxHitBtn;
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

        setupGameplayLayout(ui, input, spinIndicator, debugActor, baseStyle, viewport);
        setupClubSelection(ui, input, baseStyle, viewport);
        setupPauseMenu(ui, font, viewport, config, input);
        setupVictoryMenu(ui, input, baseStyle, viewport);

        return ui;
    }

    private static void setupGameplayLayout(MobileUIPackage ui, MobileInputProcessor input, SpinIndicator spin, PreShotDebugActor debug, TextButton.TextButtonStyle style, Viewport viewport) {
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
        leftStack.add(spin).size(getSpinSize(viewport)).bottom().left().padTop(spacing);

        Table rightStack = new Table();
        rightStack.top().right();

        TextButton.TextButtonStyle maxStyle = new TextButton.TextButtonStyle(style);
        maxStyle.up   = UIUtils.createEmbossedButtonDrawable(COLOR_MAX_UP, RADIUS_HIT, 5);
        maxStyle.down = UIUtils.createInsetButtonDrawable(COLOR_MAX_DOWN, RADIUS_HIT, 5);
        ui.maxHitBtn = createTriggerButton(maxStyle, "MAX", input, GameInputProcessor.Action.MAX_POWER_SHOT, globalFontScale);
        rightStack.add(ui.maxHitBtn).width(hitW * 0.8f).height(btnH).right().padBottom(spacing).row();

        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up   = UIUtils.createEmbossedButtonDrawable(COLOR_HIT_UP, RADIUS_HIT, 5);
        hitStyle.down = UIUtils.createInsetButtonDrawable(COLOR_HIT_DOWN, RADIUS_HIT, 5);
        ui.hitBtn = createTriggerButton(hitStyle, "HIT", input, GameInputProcessor.Action.CHARGE_SHOT, FONT_SCALE_GAMEPLAY * 0.75f);
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

    public static void buildStartMenuButtons(Table table, MenuManager menuManager, MenuManager.MenuHandler callback, CompetitiveSessions sessions, DailySubmissionCache dailyCache, Viewport viewport, BitmapFont font) {
        table.clearChildren();
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        MainMenuRenderer.MenuState state = menuManager.getCurrentMenuState();
        String[] options = getOptionsForState(state);
        float screenH = viewport.getWorldHeight();

        table.setSize(620f, screenH);
        table.setPosition(0, 0);
        table.top().left().padLeft(20f).padTop(screenH * 0.18f);

        float bW = 580f;
        float bH = screenH * 0.10f;
        float spacing = screenH * 0.015f;

        // MAP_SELECT uses a ScrollPane so the growing archetype list doesn't overflow the screen
        if (state == MainMenuRenderer.MenuState.MAP_SELECT) {
            Table innerTable = new Table();
            innerTable.top().left();
            for (int i = 0; i < options.length; i++) {
                final int index = i;
                TextButton btn = new TextButton(options[i], menuStyle);
                float baseMenuScale = FONT_SCALE_START_MENU * 0.32f;
                GlyphLayout gl = new GlyphLayout();
                font.getData().setScale(1.0f);
                gl.setText(font, options[i]);
                float textWidthAtBase = gl.width * baseMenuScale;
                float finalMenuScale = textWidthAtBase > bW * 0.85f ? baseMenuScale * (bW * 0.85f / textWidthAtBase) : baseMenuScale;
                btn.getLabel().setFontScale(finalMenuScale);
                font.getData().setScale(1.0f);
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
            table.add(scrollPane).width(bW + 20f).height(screenH * 0.70f).top().left();
            return;
        }

        // For MAIN and EIGHTEEN_HOLES, descriptors from the resolver drive label/locked/sparkle.
        java.util.List<MenuButtonDescriptor> descs = MenuButtonResolver.resolve(state, sessions, dailyCache);

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
            float maxTextWidth = bW * 0.85f;
            GlyphLayout gl = new GlyphLayout();
            font.getData().setScale(1.0f);
            gl.setText(font, text);
            float textWidthAtBase = gl.width * baseMenuScale;
            float finalMenuScale = textWidthAtBase > maxTextWidth ? baseMenuScale * (maxTextWidth / textWidthAtBase) : baseMenuScale;
            btn.getLabel().setFontScale(finalMenuScale);
            font.getData().setScale(1.0f);

            if (isLocked) {
                btn.setDisabled(true);
                btn.getLabel().setColor(Color.GRAY);
                btn.setTouchable(com.badlogic.gdx.scenes.scene2d.Touchable.disabled);
                btn.setColor(0.17f, 0.17f, 0.17f, 1f);
                if (btn.getStyle().fontColor != null) {
                    btn.getLabel().getStyle().fontColor = Color.DARK_GRAY;
                }
            } else {
                btn.addListener(new ChangeListener() {
                    @Override
                    public void changed(ChangeEvent event, Actor actor) {
                        menuManager.handleExternalSelection(index, callback, sessions, dailyCache);
                    }
                });
            }
            table.add(btn).width(bW).height(bH).padBottom(spacing).left().row();
        }

    }

    private static String[] getOptionsForState(MainMenuRenderer.MenuState state) {
        return switch (state) {
            case MAIN -> new String[]{"PLAY", "COMPETITIVE", "INSTRUCTIONS", "PRACTICE", "LOG OUT"};
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
            case EIGHTEEN_HOLES -> new String[]{"STANDARD 18", "DAILY 18", "DAILY 9", "DAILY 1-HOLE", "BACK"};
            case PRACTICE -> new String[]{"DRIVING RANGE", "PUTTING GREEN", "BACK"};
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
        pauseTable.add(ui.difficultyBtn).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();

        pauseTable.add(createMenuButton("INSTRUCTIONS", menuStyle, input, GameInputProcessor.Action.HELP, scaledFont)).width(bW).height(bH).padBottom(viewport.getWorldHeight() * 0.012f).row();
        pauseTable.add(createMenuButton("MAIN MENU", menuStyle, input, GameInputProcessor.Action.MAIN_MENU, scaledFont)).width(bW).height(bH).row();

        pauseTable.top().padTop(viewport.getWorldHeight() * 0.40f);
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
        buttonTable.setFillParent(false);
        buttonTable.add(ui.submitScoreBtn).width(btnW).height(btnH).left();
        buttonTable.add().expandX();
        buttonTable.add(ui.mainMenuBtn).width(btnW).height(btnH).right();

        ui.victoryTable.add(ui.nextLevelBtn).width(viewport.getWorldWidth() * 0.35f).height(btnH).center().padBottom(0);
        ui.victoryTable.row();
        ui.victoryTable.add(buttonTable).expandX().fillX().padLeft(30).padRight(30);
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
