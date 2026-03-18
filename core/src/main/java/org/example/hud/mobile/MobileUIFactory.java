package org.example.hud.mobile;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
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

        ui.skin.add("btnUp", createRoundedRectDrawable(new Color(0.2f, 0.2f, 0.2f, 0.5f), 12), Drawable.class);
        ui.skin.add("btnDown", createRoundedRectDrawable(new Color(0.4f, 0.4f, 0.4f, 0.7f), 12), Drawable.class);

        // Standard HIT colors
        ui.skin.add("hitUp", createRoundedRectDrawable(new Color(0.8f, 0.2f, 0.2f, 0.7f), 15), Drawable.class);
        ui.skin.add("hitDown", createRoundedRectDrawable(new Color(0.5f, 0.1f, 0.1f, 0.8f), 15), Drawable.class);

        // MAX HIT colors (Darker red/maroon)
        ui.skin.add("maxHitUp", createRoundedRectDrawable(new Color(0.5f, 0.05f, 0.05f, 0.75f), 15), Drawable.class);
        ui.skin.add("maxHitDown", createRoundedRectDrawable(new Color(0.3f, 0.02f, 0.02f, 0.85f), 15), Drawable.class);

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
        ui.gameplayTable.add(leftStack).expandY().fillY().left().padLeft(20).padTop(180);

        Table rightStack = new Table();
        ui.gameplayTable.add(rightStack).expand().right().fillY().padRight(20);

        float btnW = 230;
        float btnH = 130;

        Table leftButtons = new Table();
        leftStack.add(leftButtons).top().left().row();

        addActionButton(leftButtons, "MENU", style, input, GameInputProcessor.Action.PAUSE, btnW, btnH).padBottom(15).row();
        addActionButton(leftButtons, "PROJ", style, input, GameInputProcessor.Action.PROJECTION, btnW, btnH).padBottom(15).row();
        addActionButton(leftButtons, "CLUB+", style, input, GameInputProcessor.Action.CLUB_UP, btnW, btnH).padBottom(15).row();
        addActionButton(leftButtons, "CLUB-", style, input, GameInputProcessor.Action.CLUB_DOWN, btnW, btnH).row();

        leftStack.add(debugActor).width(400).height(140).left().padTop(10).row();
        leftStack.add(spinIndicator).size(160).bottom().left().expandY().padBottom(20);

        // RIGHT SIDE: HIT + Controls
        // Spacer reduced slightly to accommodate the extra button in the vertical stack
        rightStack.add().height(180).row();

        // --- MAX BUTTON (New) ---
        TextButton maxBtn = new TextButton("MAX", style);
        TextButton.TextButtonStyle maxStyle = new TextButton.TextButtonStyle(style);
        maxStyle.up = ui.skin.getDrawable("maxHitUp");
        maxStyle.down = ui.skin.getDrawable("maxHitDown");
        maxBtn.setStyle(maxStyle);
        maxBtn.addListener(new InputListener() {
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.MAX_POWER_SHOT, true);
                return true;
            }
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.MAX_POWER_SHOT, false);
            }
        });
        // Slightly smaller width/height than the main HIT button
        rightStack.add(maxBtn).width(200).height(110).right().padBottom(10).row();

        // --- HIT BUTTON ---
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
        rightStack.add(hitBtn).width(240).height(150).right().padBottom(15).row();

        ui.resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input, whitePixel);
        ui.newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input, whitePixel);

        rightStack.add(ui.resetBallBtn).width(btnW).height(110).right().padBottom(15).row();
        rightStack.add(ui.newMapBtn).width(btnW).height(110).right().expandY().top().row();

        ui.infoToggleBtn = new TextButton("INFO", style);
        rightStack.add(ui.infoToggleBtn).width(120).height(80).bottom().right().padBottom(250);

        setupStartMenu(ui, font, viewport, input);
        setupPauseMenu(ui, font, viewport, config, input);

        return ui;
    }

    private static com.badlogic.gdx.scenes.scene2d.ui.Cell<TextButton> addActionButton(Table table, String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action, float w, float h) {
        TextButton btn = new TextButton(text, style);
        btn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                input.triggerAction(action);
            }
        });
        return table.add(btn).width(w).height(h);
    }

    private static void setupStartMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport, MobileInputProcessor input) {
        Table startTable = new Table();
        startTable.setFillParent(true);
        ui.startMenuStage.addActor(startTable);
        float h = viewport.getWorldHeight() > 0 ? viewport.getWorldHeight() : 720;
        float w = viewport.getWorldWidth() > 0 ? viewport.getWorldWidth() : 1280;

        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        menuStyle.font.getData().setScale(2.0f);

        String[] options = {"START GAME", "COMPETITIVE (18 HOLES)", "INSTRUCTIONS", "PRACTICE RANGE", "PUTTING GREEN", "PLAY FROM CLIPBOARD SEED"};
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextButton optBtn = new TextButton(options[i], menuStyle);
            optBtn.addListener(new ChangeListener() {
                public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                    input.triggerAction(GameInputProcessor.Action.valueOf("SELECT_OPTION_" + index));
                }
            });
            startTable.add(optBtn).width(w * 0.65f).height(h * 0.11f).padBottom(h * 0.015f).row();
        }
        startTable.top().padTop(h * 0.22f);
    }

    private static void setupPauseMenu(MobileUIPackage ui, BitmapFont font, Viewport viewport, GameConfig config, MobileInputProcessor input) {
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        ui.pauseMenuStage.addActor(pauseTable);
        float h = viewport.getWorldHeight() > 0 ? viewport.getWorldHeight() : 720;
        float w = viewport.getWorldWidth() > 0 ? viewport.getWorldWidth() : 1280;

        TextButton.TextButtonStyle menuStyle = createMenuStyle(font);
        menuStyle.font.getData().setScale(1.8f);

        float bW = w * 0.65f;
        float bH = h * 0.11f;
        float pB = h * 0.015f;

        pauseTable.add(createMenuButton("RESUME", menuStyle, input, GameInputProcessor.Action.PAUSE)).width(bW).height(bH).padBottom(pB).row();

        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
                event.cancel();
            }
        });
        pauseTable.add(animBtn).width(bW).height(bH).padBottom(pB).row();

        pauseTable.add(createMenuButton("HELP", menuStyle, input, GameInputProcessor.Action.HELP)).width(bW).height(bH).padBottom(pB).row();
        pauseTable.add(createMenuButton("CAMERA", menuStyle, input, GameInputProcessor.Action.CAM_CONFIG)).width(bW).height(bH).padBottom(pB).row();
        pauseTable.add(createMenuButton("MAIN MENU", menuStyle, input, GameInputProcessor.Action.MAIN_MENU)).width(bW).height(bH).padBottom(pB).row();

        pauseTable.top().padTop(h * 0.22f);
    }

    private static TextButton createMenuButton(String text, TextButton.TextButtonStyle style, MobileInputProcessor input, GameInputProcessor.Action action) {
        TextButton btn = new TextButton(text, style);
        btn.addListener(new ChangeListener() {
            public void changed(ChangeEvent event, com.badlogic.gdx.scenes.scene2d.Actor actor) { input.triggerAction(action); }
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