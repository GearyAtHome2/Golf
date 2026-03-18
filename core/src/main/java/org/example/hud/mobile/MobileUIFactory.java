package org.example.hud.mobile;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.GameConfig;
import org.example.hud.HoldButton;
import org.example.hud.PreShotDebugActor;
import org.example.hud.SpinIndicator;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;

import static org.example.hud.UIUtils.createRoundedRectDrawable;

public class MobileUIFactory {

    public static class MobileUIPackage {
        public Stage stage, startMenuStage, pauseMenuStage;
        public Table gameplayTable, victoryTable;
        public TextButton infoToggleBtn;
        public HoldButton resetBallBtn, newMapBtn;
        public Skin skin;
    }

    public static MobileUIPackage create(Viewport viewport, SpriteBatch batch, BitmapFont font, GameConfig config, MobileInputProcessor input, Texture whitePixel, SpinIndicator spinIndicator, PreShotDebugActor debugActor) {
        MobileUIPackage ui = new MobileUIPackage();
        
        ui.stage = new Stage(viewport, batch);
        ui.startMenuStage = new Stage(viewport, batch);
        ui.pauseMenuStage = new Stage(viewport, batch);

        ui.skin = new Skin();
        ui.skin.add("default", font);

        Drawable btnUp = createRoundedRectDrawable(new Color(0.2f, 0.2f, 0.2f, 0.5f), 12);
        Drawable btnDown = createRoundedRectDrawable(new Color(0.4f, 0.4f, 0.4f, 0.7f), 12);
        ui.skin.add("btnUp", btnUp, Drawable.class);
        ui.skin.add("btnDown", btnDown, Drawable.class);

        Drawable hitUp = createRoundedRectDrawable(new Color(0.8f, 0.2f, 0.2f, 0.7f), 15);
        Drawable hitDown = createRoundedRectDrawable(new Color(0.5f, 0.1f, 0.1f, 0.8f), 15);
        ui.skin.add("hitUp", hitUp, Drawable.class);
        ui.skin.add("hitDown", hitDown, Drawable.class);

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = ui.skin.getDrawable("btnUp");
        style.down = ui.skin.getDrawable("btnDown");
        style.fontColor = Color.WHITE;

        // --- GAMEPLAY UI ---
        ui.gameplayTable = new Table();
        ui.gameplayTable.setFillParent(true);
        ui.stage.addActor(ui.gameplayTable);

        Table leftStack = new Table();
        ui.gameplayTable.add(leftStack).expandY().fillY().left().padLeft(20);
        Table rightStack = new Table();
        ui.gameplayTable.add(rightStack).expand().right().fillY().padRight(20);

        leftStack.add().height(150).row();
        Table leftButtons = new Table();
        leftStack.add(leftButtons).expandY().top().left().row();

        addActionButton(leftButtons, "MENU", style, input, GameInputProcessor.Action.PAUSE, 210, 120).padBottom(15).row();
        addActionButton(leftButtons, "PROJ", style, input, GameInputProcessor.Action.PROJECTION, 210, 120).padBottom(15).row();
        addActionButton(leftButtons, "CLUB+", style, input, GameInputProcessor.Action.CLUB_UP, 210, 120).padBottom(15).row();
        addActionButton(leftButtons, "CLUB-", style, input, GameInputProcessor.Action.CLUB_DOWN, 210, 120).row();

        leftStack.add(debugActor).width(400).height(140).left().padBottom(10).row();
        leftStack.add(spinIndicator).size(160).bottom().left().padBottom(20);

        rightStack.add().height(150).row();
        TextButton hitBtn = new TextButton("HIT", style);
        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up = ui.skin.getDrawable("hitUp");
        hitStyle.down = ui.skin.getDrawable("hitDown");
        hitBtn.setStyle(hitStyle);
        hitBtn.addListener(new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, true);
                return true;
            }
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, false);
                input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
            }
        });
        rightStack.add(hitBtn).width(200).height(140).expandY().top().right().padTop(20).row();

        ui.resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input, whitePixel);
        ui.newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input, whitePixel);
        rightStack.add(ui.resetBallBtn).width(210).height(100).right().padBottom(20).row();
        rightStack.add(ui.newMapBtn).width(210).height(100).right().padBottom(150).row();

        ui.infoToggleBtn = new TextButton("INFO", style);
        rightStack.add(ui.infoToggleBtn).width(120).height(80).bottom().right().padBottom(220);

        // --- VICTORY UI ---
        ui.victoryTable = new Table();
        ui.victoryTable.setFillParent(true);
        ui.victoryTable.setVisible(false);
        ui.stage.addActor(ui.victoryTable);

        addActionButton(ui.victoryTable, "NEXT HOLE", style, input, GameInputProcessor.Action.NEW_LEVEL, 240, 100).pad(10);
        addActionButton(ui.victoryTable, "MENU", style, input, GameInputProcessor.Action.MAIN_MENU, 160, 100).pad(10);
        ui.victoryTable.bottom().center().pad(50);

        // --- START MENU ---
        setupStartMenu(ui, font, input);

        // --- PAUSE MENU ---
        setupPauseMenu(ui, font, config, input);

        return ui;
    }

    private static com.badlogic.gdx.scenes.scene2d.ui.Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h) {
        TextButton btn = new TextButton(text, style);
        btn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(action);
            }
        });
        return table.add(btn).width(w).height(h);
    }

    private static void setupStartMenu(MobileUIPackage ui, BitmapFont font, MobileInputProcessor input) {
        Table startTable = new Table();
        startTable.setFillParent(true);
        ui.startMenuStage.addActor(startTable);

        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        String[] options = {"START GAME", "COMPETITIVE (18 HOLES)", "INSTRUCTIONS", "PRACTICE RANGE", "PUTTING GREEN", "PLAY FROM CLIPBOARD SEED"};
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextButton optBtn = new TextButton(options[i], menuStyle);
            optBtn.addListener(new ChangeListener() {
                public void changed(ChangeEvent event, Actor actor) {
                    input.triggerAction(GameInputProcessor.Action.valueOf("SELECT_OPTION_" + index));
                }
            });
            startTable.add(optBtn).width(600).height(85).padBottom(12).row();
        }
        startTable.top().padTop(320);
    }

    private static void setupPauseMenu(MobileUIPackage ui, BitmapFont font, GameConfig config, MobileInputProcessor input) {
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        ui.pauseMenuStage.addActor(pauseTable);
        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);

        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
                event.cancel();
            }
        });

        // Simplified addition for brevity in the factory
        pauseTable.add(createActionButton(input, GameInputProcessor.Action.PAUSE, "RESUME", menuStyle)).width(600).height(85).padBottom(12).row();
        pauseTable.add(animBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(createActionButton(input, GameInputProcessor.Action.HELP, "HELP", menuStyle)).width(600).height(85).padBottom(12).row();
        pauseTable.add(createActionButton(input, GameInputProcessor.Action.CAM_CONFIG, "CAMERA", menuStyle)).width(600).height(85).padBottom(12).row();
        pauseTable.add(createActionButton(input, GameInputProcessor.Action.MAIN_MENU, "MAIN MENU", menuStyle)).width(600).height(85).padBottom(12).row();
        pauseTable.center().top().padTop(320);
    }

    private static TextButton createActionButton(MobileInputProcessor input, GameInputProcessor.Action action, String text, TextButton.TextButtonStyle style) {
        TextButton btn = new TextButton(text, style);
        btn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, Actor actor) { input.triggerAction(action); }
        });
        return btn;
    }

    private static TextButton.TextButtonStyle createMenuStyle(BitmapFont font) {
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = createRoundedRectDrawable(new Color(0.15f, 0.15f, 0.15f, 0.6f), 12);
        style.down = createRoundedRectDrawable(new Color(0.4f, 0.4f, 0.1f, 0.8f), 12);
        style.fontColor = Color.WHITE;
        return style;
    }
}