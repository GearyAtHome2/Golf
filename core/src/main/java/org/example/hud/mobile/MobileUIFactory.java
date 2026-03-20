package org.example.hud.mobile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;
import org.example.hud.HoldButton;
import org.example.hud.PreShotDebugActor;
import org.example.hud.SpinIndicator;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;

import static org.example.hud.UIUtils.createRoundedRectDrawable;
import static org.example.hud.mobile.MobileUIValues.*;

public class MobileUIFactory {

    public static class MobileUIPackage {
        public Stage stage, startMenuStage, pauseMenuStage;
        public Table gameplayTable, victoryTable;
        public TextButton infoToggleBtn;
        public HoldButton resetBallBtn, newMapBtn;
        public Label clubLabel;
        public Skin skin;
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

        // Initialize Victory Table
        ui.victoryTable = new Table();
        ui.victoryTable.setFillParent(true);
        ui.victoryTable.setVisible(false);
        ui.stage.addActor(ui.victoryTable);

        setupLeftStack(ui, input, spinIndicator, debugActor, baseStyle);
        setupRightStack(ui, input, whitePixel, baseStyle);
        setupClubSelection(ui, input, font, baseStyle);

        setupStartMenu(ui, font, viewport, input);
        setupPauseMenu(ui, font, viewport, config, input);
        setupVictoryMenu(ui, input, baseStyle);

        return ui;
    }

    private static void setupVictoryMenu(MobileUIPackage ui, MobileInputProcessor input, TextButton.TextButtonStyle style) {
        TextButton.TextButtonStyle nextStyle = new TextButton.TextButtonStyle(style);
        nextStyle.up = ui.skin.getDrawable("hitUp");
        nextStyle.down = ui.skin.getDrawable("hitDown");

        TextButton nextBtn = new TextButton("NEXT LEVEL", nextStyle);
        nextBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY * 1.2f);

        nextBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                input.triggerAction(GameInputProcessor.Action.NEW_LEVEL);
            }
        });

        ui.victoryTable.bottom().padBottom(40);
        ui.victoryTable.add(nextBtn).width(300).height(100);
    }

    private static void setupLeftStack(MobileUIPackage ui, MobileInputProcessor input, SpinIndicator spin, PreShotDebugActor debug, TextButton.TextButtonStyle style) {
        Table leftStack = new Table();
        leftStack.setBackground((Drawable)null);
        ui.gameplayTable.add(leftStack).expandY().fillY().left().padLeft(PAD_SCREEN_EDGE).padTop(LEFT_STACK_TOP_PAD);

        Table leftButtons = new Table();
        addActionButton(leftButtons, "MENU", style, input, GameInputProcessor.Action.PAUSE, BTN_WIDTH_STD, BTN_HEIGHT_LARGE).padBottom(BTN_SPACING_V).row();
        addActionButton(leftButtons, "PROJ", style, input, GameInputProcessor.Action.PROJECTION, BTN_WIDTH_STD, BTN_HEIGHT_LARGE).padBottom(BTN_SPACING_V).row();
        addActionButton(leftButtons, "DISTANCE", style, input, GameInputProcessor.Action.SHOW_RANGE, BTN_WIDTH_STD, BTN_HEIGHT_LARGE).padBottom(BTN_SPACING_V).row();
        addActionButton(leftButtons, "OVERVIEW", style, input, GameInputProcessor.Action.OVERHEAD_VIEW, BTN_WIDTH_STD, BTN_HEIGHT_LARGE).row();

        leftStack.add(leftButtons).top().left().row();
        leftStack.add(debug).width(DEBUG_ACTOR_WIDTH).height(DEBUG_ACTOR_HEIGHT).left().padTop(10).row();
        leftStack.add(spin).size(SPIN_INDICATOR_SIZE).bottom().left().expandY().padBottom(PAD_SCREEN_EDGE);
    }

    private static void setupRightStack(MobileUIPackage ui, MobileInputProcessor input, Texture whitePixel, TextButton.TextButtonStyle style) {
        Table rightStack = new Table();
        rightStack.setBackground((Drawable)null);
        ui.gameplayTable.add(rightStack).expand().top().right().padRight(PAD_SCREEN_EDGE).padTop(RIGHT_STACK_TOP_PAD);
        rightStack.add().height(RIGHT_STACK_SPACER).row();

        TextButton.TextButtonStyle maxStyle = new TextButton.TextButtonStyle(style);
        maxStyle.up = ui.skin.getDrawable("maxHitUp");
        maxStyle.down = ui.skin.getDrawable("maxHitDown");
        TextButton maxBtn = createTriggerButton(maxStyle, "MAX", input, GameInputProcessor.Action.MAX_POWER_SHOT, FONT_SCALE_GAMEPLAY);
        rightStack.add(maxBtn).width(MAX_BTN_WIDTH).height(MAX_BTN_HEIGHT).right().padBottom(15).row();

        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up = ui.skin.getDrawable("hitUp");
        hitStyle.down = ui.skin.getDrawable("hitDown");
        TextButton hitBtn = createTriggerButton(hitStyle, "HIT", input, GameInputProcessor.Action.CHARGE_SHOT, FONT_SCALE_GAMEPLAY * 2f);
        rightStack.add(hitBtn).width(HIT_BTN_WIDTH).height(HIT_BTN_HEIGHT).right().padBottom(BTN_SPACING_V).row();

        ui.resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input, whitePixel);
        ui.resetBallBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY);
        rightStack.add(ui.resetBallBtn).width(BTN_WIDTH_STD).height(BTN_HEIGHT_STD).right().padBottom(BTN_SPACING_V).row();

        ui.newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input, whitePixel);
        ui.newMapBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY);
        rightStack.add(ui.newMapBtn).width(BTN_WIDTH_STD).height(BTN_HEIGHT_STD).right().expandY().top().row();

        ui.infoToggleBtn = new TextButton("INFO", style);
        ui.infoToggleBtn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY);
        rightStack.add(ui.infoToggleBtn).width(INFO_BTN_WIDTH).height(INFO_BTN_HEIGHT).bottom().right().padTop(65);
    }

    private static void setupClubSelection(MobileUIPackage ui, MobileInputProcessor input, BitmapFont font, TextButton.TextButtonStyle style) {
        Table arrowContainer = new Table();
        ui.stage.addActor(arrowContainer);
        arrowContainer.setFillParent(true);
        arrowContainer.bottom().right().padBottom(20).padRight(PAD_SCREEN_EDGE);

        Table arrowRow = new Table();
        addActionButton(arrowRow, "<", style, input, GameInputProcessor.Action.CLUB_UP, 100, 80).padRight(20);
        addActionButton(arrowRow, ">", style, input, GameInputProcessor.Action.CLUB_DOWN, 100, 80);
        arrowContainer.add(arrowRow);

        Table labelContainer = new Table();
        ui.stage.addActor(labelContainer);
        labelContainer.setFillParent(true);
        labelContainer.bottom().right().padBottom(110).padRight(PAD_SCREEN_EDGE);

        Label.LabelStyle labelStyle = new Label.LabelStyle(font, Color.WHITE);
        ui.clubLabel = new Label("DRIVER", labelStyle);
        ui.clubLabel.setFontScale(6.4f);
        ui.clubLabel.setAlignment(Align.right);
        labelContainer.add(ui.clubLabel).right();
    }

    private static com.badlogic.gdx.scenes.scene2d.ui.Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(FONT_SCALE_GAMEPLAY);

        if (input != null && action != null) {
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    if (action == GameInputProcessor.Action.OVERHEAD_VIEW) {
                        input.setActionState(action, btn.isChecked());
                    } else {
                        input.triggerAction(action);
                    }
                }
            });
        } else {
            btn.setColor(Color.GRAY);
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
                if (action == GameInputProcessor.Action.CHARGE_SHOT) {
                    input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
                }
            }
        });
        return btn;
    }

    private static Skin setupSkin(BitmapFont font) {
        Skin skin = new Skin();
        skin.add("default", font);
        skin.add("btnUp", createRoundedRectDrawable(COLOR_BTN_UP, RADIUS_STD), Drawable.class);
        skin.add("btnDown", createRoundedRectDrawable(COLOR_BTN_DOWN, RADIUS_STD), Drawable.class);
        skin.add("hitUp", createRoundedRectDrawable(COLOR_HIT_UP, RADIUS_HIT), Drawable.class);
        skin.add("hitDown", createRoundedRectDrawable(COLOR_HIT_DOWN, RADIUS_HIT), Drawable.class);
        skin.add("maxHitUp", createRoundedRectDrawable(COLOR_MAX_UP, RADIUS_HIT), Drawable.class);
        skin.add("maxHitDown", createRoundedRectDrawable(COLOR_MAX_DOWN, RADIUS_HIT), Drawable.class);
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

    private static void setupStartMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport, MobileInputProcessor input) {
        Table startTable = new Table();
        startTable.setFillParent(true);
        ui.startMenuStage.addActor(startTable);
        float h = viewport.getWorldHeight() > 0 ? viewport.getWorldHeight() : 720;
        float w = viewport.getWorldWidth() > 0 ? viewport.getWorldWidth() : 1280;
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);

        String[] options = {"START GAME", "COMPETITIVE", "INSTRUCTIONS", "PRACTICE RANGE", "PUTTING GREEN", "CLIPBOARD SEED"};
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextButton optBtn = createMenuButton(options[i], menuStyle, input, null, FONT_SCALE_START_MENU);
            optBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    input.triggerAction(GameInputProcessor.Action.valueOf("SELECT_OPTION_" + index));
                }
            });
            startTable.add(optBtn).width(w * MENU_BTN_WIDTH_PCT).height(h * MENU_BTN_HEIGHT_PCT).padBottom(h * 0.015f).row();
        }
        startTable.top().padTop(h * MENU_TOP_OFFSET_PCT);
    }

    private static void setupPauseMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport, GameConfig config, MobileInputProcessor input) {
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        ui.pauseMenuStage.addActor(pauseTable);
        float h = viewport.getWorldHeight() > 0 ? viewport.getWorldHeight() : 720;
        float w = viewport.getWorldWidth() > 0 ? viewport.getWorldWidth() : 1280;
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);

        float bW = w * MENU_BTN_WIDTH_PCT, bH = h * MENU_BTN_HEIGHT_PCT, pB = h * 0.015f;
        pauseTable.add(createMenuButton("RESUME", menuStyle, input, GameInputProcessor.Action.PAUSE, FONT_SCALE_PAUSE_MENU)).width(bW).height(bH).padBottom(pB).row();

        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.getLabel().setFontScale(FONT_SCALE_PAUSE_MENU);
        animBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
            }
        });
        pauseTable.add(animBtn).width(bW).height(bH).padBottom(pB).row();

        pauseTable.add(createMenuButton("HELP", menuStyle, input, GameInputProcessor.Action.HELP, FONT_SCALE_PAUSE_MENU)).width(bW).height(bH).padBottom(pB).row();
        pauseTable.add(createMenuButton("CAMERA", menuStyle, input, GameInputProcessor.Action.CAM_CONFIG, FONT_SCALE_PAUSE_MENU)).width(bW).height(bH).padBottom(pB).row();
        pauseTable.add(createMenuButton("MAIN MENU", menuStyle, input, GameInputProcessor.Action.MAIN_MENU, FONT_SCALE_PAUSE_MENU)).width(bW).height(bH).padBottom(pB).row();
        pauseTable.top().padTop(h * MENU_TOP_OFFSET_PCT);
    }

    private static TextButton createMenuButton(String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float fontScale) {
        TextButton btn = new TextButton(text, style);
        btn.getLabel().setFontScale(fontScale);
        if (action != null) {
            btn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { input.triggerAction(action); }
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