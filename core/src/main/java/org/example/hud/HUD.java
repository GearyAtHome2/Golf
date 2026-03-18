package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.graphics.g2d.Batch;
import org.example.Club;
import org.example.GameConfig;
import org.example.ball.Ball;
import org.example.ball.CompetitiveScore;
import org.example.ball.MinigameResult;
import org.example.ball.ShotDifficulty;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

public class HUD {
    private final MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();

    private float hazardTimer = 0;
    private String hazardText = "";
    private Color hazardColor = Color.WHITE;
    private boolean isPracticeState = false; // Track this for penalty display
    private boolean instructionsRequested = false;
    private boolean cameraConfigRequested = false;

    private float persistentShotDistance = 0f;
    private float maxDistanceSeen = 0f;
    private float shotDistanceDisplayTimer = 0f;
    private boolean ballWasActive = false;

    public static class ModAnimation {
        public String text;
        public float yOffset, alpha, scale, life;
        public Color color;
        public boolean hitBar = false;

        public ModAnimation(String text, Color color) {
            this.text = text;
            this.color = color;
            this.yOffset = -5f;
            this.alpha = 0f;
            this.scale = 0.8f;
            this.life = 1.0f;
        }
    }

    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private final GameConfig config;

    private final MinigameController minigameController = new MinigameController();
    private final InstructionRenderer instructionRenderer = new InstructionRenderer();
    private final CameraConfigRenderer cameraConfigRenderer = new CameraConfigRenderer();
    private final VictoryRenderer victoryRenderer = new VictoryRenderer();
    private final ClubInfoRenderer clubInfoRenderer = new ClubInfoRenderer();

    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;
    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;

    private float distanceDisplayTimer = 0;
    private String distanceText = "";

    private float seedFeedbackTimer = 0, shotFeedbackTimer = 0;
    private String shotFeedbackText = "";
    private Color shotFeedbackColor = Color.WHITE;

    private boolean mainMenuRequested = false;
    
    private Stage stage;
    private Skin skin;
    private Stage startMenuStage;
    private Stage pauseMenuStage;
    private Table gameplayTable;
    private Table victoryTable;
    private boolean mobileUIInitialized = false;

    // Mobile UI Actors
    private SpinIndicatorActor spinActor;
    private PreShotDebugActor debugActor;
    private TextButton infoToggleBtn;
    private HoldButton resetBallBtn;
    private HoldButton newMapBtn;
    private boolean showInfoDisplay = false;

    // Temporary vectors for HUD calculations to avoid allocation
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();
    private final Vector3 tempV3 = new Vector3();
    private Texture whitePixel;

    private void setupCamera() {
    }

    private Drawable createRoundedRectDrawable(Color color, int radius) {
        int size = 64; 
        Pixmap pixmap = new Pixmap(size, size, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(0, 0, 0, 0);
        pixmap.fill();
        
        pixmap.setColor(color);
        pixmap.fillRectangle(radius, 0, size - 2 * radius, size);
        pixmap.fillRectangle(0, radius, size, size - 2 * radius);
        pixmap.fillCircle(radius, radius, radius);
        pixmap.fillCircle(size - radius, radius, radius);
        pixmap.fillCircle(radius, size - radius, radius);
        pixmap.fillCircle(size - radius, size - radius, radius);
        
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Linear, Texture.TextureFilter.Linear);
        pixmap.dispose();
        
        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture), radius, radius, radius, radius));
    }

    class SpinIndicatorActor extends Actor {
        public SpinIndicatorActor() {
            setSize(160, 160);
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    updateSpin(x, y);
                    return true;
                }
                @Override
                public void touchDragged(InputEvent event, float x, float y, int pointer) {
                    updateSpin(x, y);
                }
                private void updateSpin(float x, float y) {
                    float radius = getWidth() / 2f;
                    float dx = (x - radius) / radius;
                    float dy = (y - radius) / radius;
                    float len = (float) Math.sqrt(dx * dx + dy * dy);
                    if (len > 1) {
                        dx /= len;
                        dy /= len;
                    }
                    spinDot.set(dx, dy);
                }
            });
        }
        @Override
        public void draw(Batch batch, float parentAlpha) {
            batch.end();
            shapeRenderer.setProjectionMatrix(batch.getProjectionMatrix());
            shapeRenderer.setTransformMatrix(batch.getTransformMatrix());
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            float radius = getWidth() / 2f;
            float centerX = getX() + radius;
            float centerY = getY() + radius;
            shapeRenderer.setColor(1, 1, 1, 0.4f * parentAlpha);
            shapeRenderer.circle(centerX, centerY, radius);
            shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 0.6f * parentAlpha);
            shapeRenderer.circle(centerX, centerY, radius - 4);
            shapeRenderer.setColor(Color.RED);
            shapeRenderer.circle(centerX + spinDot.x * (radius - 12), centerY + spinDot.y * (radius - 12), 8);
            shapeRenderer.end();
            batch.begin();
            
            font.getData().setScale(1.1f);
            drawShadowedText("SPIN", getX() + radius - 25, getY() - 10, Color.WHITE);
        }
    }

    class PreShotDebugActor extends Actor {
        private Terrain terrain;
        private Ball ball;
        private Camera camera;

        public void update(Terrain terrain, Ball ball, Camera camera) {
            this.terrain = terrain;
            this.ball = ball;
            this.camera = camera;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            if (terrain == null || ball == null || camera == null) return;
            Vector3 ballPos = ball.getPosition();
            Vector3 aimDir = camera.direction;
            ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
            Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
            float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());
            
            font.getData().setScale(1.2f);
            float x = getX();
            float y = getY() + getHeight() - 20;
            drawShadowedText("TERRAIN: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z), x, y, Color.YELLOW);
            drawShadowedText(String.format("DIFFICULTY: %.2fx", diff.terrainDifficulty), x, y - 25, Color.YELLOW);
            drawShadowedText(String.format("SLOPE: %.2fx", diff.slopeDifficulty), x, y - 50, Color.YELLOW);
            if (Math.abs(sidePush) > 0.05f) {
                Color kickColor = sidePush > 0 ? Color.RED : Color.CYAN;
                drawShadowedText(String.format("KICK: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), x, y - 75, kickColor);
            }
        }
    }

    private class HoldButton extends TextButton {
        private float holdTime = 0;
        private final float requiredTime = 1.2f;
        private final GameInputProcessor.Action action;
        private final MobileInputProcessor mobileInput;

        public HoldButton(String text, TextButtonStyle style, GameInputProcessor.Action action, MobileInputProcessor mobileInput) {
            super(text, style);
            this.action = action;
            this.mobileInput = mobileInput;
            
            addListener(new InputListener() {
                @Override
                public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                    if (isDisabled()) return false;
                    holdTime = 0;
                    return true;
                }

                @Override
                public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                    if (holdTime >= requiredTime) {
                        mobileInput.triggerAction(action);
                    }
                    holdTime = 0;
                }
            });
        }

        @Override
        public void act(float delta) {
            super.act(delta);
            if (isPressed() && !isDisabled()) {
                holdTime += delta;
            } else {
                holdTime = 0;
            }
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            if (isPressed() && !isDisabled() && holdTime > 0) {
                float progress = Math.min(holdTime / requiredTime, 1f);
                float barW = getWidth() * progress;
                float barH = getHeight();
                
                batch.setColor(1f, 1f, 1f, 0.4f);
                batch.draw(whitePixel, getX(), getY(), barW, barH);
                batch.setColor(Color.WHITE);
            }
        }
    }

    public HUD(GameConfig config) {
        this.config = config;
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        font.getRegion().getTexture().setFilter(
                com.badlogic.gdx.graphics.Texture.TextureFilter.Linear,
                com.badlogic.gdx.graphics.Texture.TextureFilter.Linear
        );

        viewport = new ScreenViewport();
        
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(Color.WHITE);
        pm.fill();
        whitePixel = new Texture(pm);
        pm.dispose();
    }

    private void drawShadowedText(String text, float x, float y, Color color) {
        if (batch == null || !batch.isDrawing()) return;
        float alpha = color.a;
        font.setColor(0, 0, 0, alpha * 0.7f);
        font.draw(batch, text, x + 1, y - 1);

        font.setColor(color);
        font.draw(batch, text, x, y);
    }

    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }
    public void resize(int width, int height) { 
        viewport.update(width, height, true); 
    }
    
    public void setupMobileUI(MobileInputProcessor input) {
        if (mobileUIInitialized) return;
        System.out.println("[DEBUG_LOG] HUD.setupMobileUI() - Starting");
        
        // Use the same viewport to ensure coordinates match manual text rendering
        stage = new Stage(viewport, batch);
        startMenuStage = new Stage(viewport, batch);
        pauseMenuStage = new Stage(viewport, batch);
        
        System.out.println("[DEBUG_LOG] HUD.setupMobileUI() - Stages created");
        skin = new Skin();
        skin.add("default", font);
        
        // Translucent gray rounded backgrounds
        Drawable btnUp = createRoundedRectDrawable(new Color(0.2f, 0.2f, 0.2f, 0.5f), 12);
        Drawable btnDown = createRoundedRectDrawable(new Color(0.4f, 0.4f, 0.4f, 0.7f), 12);
        skin.add("btnUp", btnUp, Drawable.class);
        skin.add("btnDown", btnDown, Drawable.class);
        
        // Dark Red for HIT button pressed state
        Drawable hitUp = createRoundedRectDrawable(new Color(0.8f, 0.2f, 0.2f, 0.7f), 15);
        Drawable hitDown = createRoundedRectDrawable(new Color(0.5f, 0.1f, 0.1f, 0.8f), 15);
        skin.add("hitUp", hitUp, Drawable.class);
        skin.add("hitDown", hitDown, Drawable.class);

        System.out.println("[DEBUG_LOG] HUD.setupMobileUI() - Skin created");

        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = skin.getDrawable("btnUp");
        style.down = skin.getDrawable("btnDown");
        style.fontColor = Color.WHITE;

        // --- GAMEPLAY UI ---
        gameplayTable = new Table();
        gameplayTable.setFillParent(true);
        stage.addActor(gameplayTable);

        // LEFT SIDE - Split into Top (Hole Info), Middle (Buttons), and Bottom (Spin/Debug)
        Table leftStack = new Table();
        gameplayTable.add(leftStack).expandY().fillY().left().padLeft(20);

        Table rightStack = new Table();
        gameplayTable.add(rightStack).expand().right().fillY().padRight(20);

        // LEFT STACK CONTENT
        leftStack.add().height(150).row(); // Spacer for Top-Left Hole/Par Info

        Table leftButtons = new Table();
        leftStack.add(leftButtons).expandY().top().left().row();

        TextButton menuBtn = new TextButton("MENU", style);
        menuBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.PAUSE);
            }
        });

        TextButton projBtn = new TextButton("PROJ", style);
        projBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.PROJECTION);
            }
        });

        TextButton clubPlus = new TextButton("CLUB+", style);
        clubPlus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.CLUB_UP);
            }
        });

        TextButton clubMinus = new TextButton("CLUB-", style);
        clubMinus.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.CLUB_DOWN);
            }
        });

        float btnW = 210f, btnH = 120f;
        leftButtons.add(menuBtn).width(btnW).height(btnH).padBottom(15).left().row();
        leftButtons.add(projBtn).width(btnW).height(btnH).padBottom(15).left().row();
        leftButtons.add(clubPlus).width(btnW).height(btnH).padBottom(15).left().row();
        leftButtons.add(clubMinus).width(btnW).height(btnH).left().row();

        debugActor = new PreShotDebugActor();
        leftStack.add(debugActor).width(400).height(140).left().padBottom(10).row();

        spinActor = new SpinIndicatorActor();
        leftStack.add(spinActor).size(160).bottom().left().padBottom(20);

        // RIGHT STACK CONTENT
        rightStack.add().height(150).row(); // Spacer for Top-Right Wind Indicator

        TextButton hitBtn = new TextButton("HIT", style);
        TextButton.TextButtonStyle hitStyle = new TextButton.TextButtonStyle(style);
        hitStyle.up = skin.getDrawable("hitUp");
        hitStyle.down = skin.getDrawable("hitDown");
        hitBtn.setStyle(hitStyle);

        // Right side buttons
        hitBtn.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                System.out.println("[DEBUG_LOG] HIT button touchDown - pointer: " + pointer);
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, true);
                return true;
            }
            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                System.out.println("[DEBUG_LOG] HIT button touchUp - pointer: " + pointer);
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, false);
                input.triggerAction(GameInputProcessor.Action.STOP_NEEDLE);
            }
        });
        
        // HIT button at the top right
        rightStack.add(hitBtn).width(200).height(140).expandY().top().right().padTop(20).row();

        // --- NEW BUTTONS (RESET & NEW MAP) ---
        resetBallBtn = new HoldButton("RESET BALL", style, GameInputProcessor.Action.RESET_BALL, input);
        newMapBtn = new HoldButton("NEW MAP", style, GameInputProcessor.Action.NEW_LEVEL, input);

        // Positioned between HIT and INFO, clearing the info box area (y=220-420)
        // Enlarged and pushed UPWARDS from the bottom to avoid overlap
        rightStack.add(resetBallBtn).width(210).height(100).right().padBottom(20).row();
        rightStack.add(newMapBtn).width(210).height(100).right().padBottom(150).row(); 

        infoToggleBtn = new TextButton("INFO", style);
        infoToggleBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showInfoDisplay = !showInfoDisplay;
                infoToggleBtn.setVisible(!showInfoDisplay);
            }
        });
        // Positioned just above the club name text (e.g. above DRIVER at y=180)
        // anchored at y=220 to match the club info renderer's display area
        rightStack.add(infoToggleBtn).width(120).height(80).bottom().right().padBottom(220);

        // --- VICTORY UI ---
        victoryTable = new Table();
        victoryTable.setFillParent(true);
        victoryTable.setVisible(false);
        stage.addActor(victoryTable);

        TextButton nextBtn = new TextButton("NEXT HOLE", style);
        nextBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.NEW_LEVEL);
            }
        });

        TextButton victoryMenuBtn = new TextButton("MENU", style);
        victoryMenuBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.MAIN_MENU);
            }
        });

        victoryTable.bottom().center().pad(50);
        victoryTable.add(nextBtn).width(240).height(100).pad(10);
        victoryTable.add(victoryMenuBtn).width(160).height(100).pad(10);

        // --- START MENU UI ---
        Table startTable = new Table();
        startTable.setFillParent(true);
        startMenuStage.addActor(startTable);

        TextButton.TextButtonStyle menuStyle = new TextButton.TextButtonStyle();
        menuStyle.font = font;
        menuStyle.up = createRoundedRectDrawable(new Color(0.15f, 0.15f, 0.15f, 0.6f), 12);
        menuStyle.down = createRoundedRectDrawable(new Color(0.4f, 0.4f, 0.1f, 0.8f), 12);
        menuStyle.fontColor = Color.WHITE;

        String[] options = {"START GAME", "COMPETITIVE (18 HOLES)", "INSTRUCTIONS", "PRACTICE RANGE", "PUTTING GREEN", "PLAY FROM CLIPBOARD SEED"};
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            TextButton optBtn = new TextButton(options[i], menuStyle);
            optBtn.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    input.triggerAction(GameInputProcessor.Action.valueOf("SELECT_OPTION_" + index));
                }
            });
            startTable.add(optBtn).width(600).height(85).padBottom(12);
            startTable.row();
        }
        startTable.top().padTop(320);

        // --- PAUSE MENU UI ---
        Table pauseTable = new Table();
        pauseTable.setFillParent(true);
        pauseMenuStage.addActor(pauseTable);

        // Pause buttons that update their text dynamically
        final TextButton animBtn = new TextButton("ANIMATION: " + config.animSpeed.name(), menuStyle);
        animBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                config.cycleAnimation();
                animBtn.setText("ANIMATION: " + config.animSpeed.name());
                event.cancel(); // Prevent double-trigger if input multiplexer also fires
            }
        });

        final TextButton diffBtn = new TextButton("DIFFICULTY: " + config.difficulty.name(), menuStyle);
        diffBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                config.cycleDifficulty();
                diffBtn.setText("DIFFICULTY: " + config.difficulty.name());
                event.cancel();
            }
        });

        final TextButton partBtn = new TextButton("PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"), menuStyle);
        partBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                config.particlesEnabled = !config.particlesEnabled;
                partBtn.setText("PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"));
                event.cancel();
            }
        });

        TextButton helpBtn = new TextButton("HELP", menuStyle);
        helpBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.HELP);
            }
        });

        TextButton camBtn = new TextButton("CAMERA", menuStyle);
        camBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.CAM_CONFIG);
            }
        });

        TextButton pauseMenuBtn = new TextButton("MAIN MENU", menuStyle);
        pauseMenuBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.MAIN_MENU);
            }
        });

        TextButton resumeBtn = new TextButton("RESUME", menuStyle);
        resumeBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.PAUSE); // Action.PAUSE toggles pause state
            }
        });

        pauseTable.add(resumeBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(animBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(diffBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(partBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(helpBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(camBtn).width(600).height(85).padBottom(12).row();
        pauseTable.add(pauseMenuBtn).width(600).height(85).padBottom(12).row();
        pauseTable.center();
        pauseTable.top().padTop(320);

        mobileUIInitialized = true;
    }

    public void renderStartMenu(int selection) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        mainMenuRenderer.render(batch, font, viewport, selection);
        batch.end();

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && startMenuStage != null) {
            startMenuStage.act();
            startMenuStage.draw();
        }
    }

    public void renderPauseMenu(boolean isPractice, LevelData levelData, GameInputProcessor input) {
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_ANIMATION)) {
            config.animSpeed = GameConfig.AnimSpeed.values()[(config.animSpeed.ordinal() + 1) % GameConfig.AnimSpeed.values().length];
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY)) {
            config.difficulty = GameConfig.Difficulty.values()[(config.difficulty.ordinal() + 1) % GameConfig.Difficulty.values().length];
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.TOGGLE_PARTICLES)) {
            config.particlesEnabled = !config.particlesEnabled;
        }

        if (input.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) mainMenuRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.HELP)) instructionsRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.CAM_CONFIG)) cameraConfigRequested = true;

        // Handle seed copying specifically for visual feedback trigger
        if (input.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && levelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
            seedFeedbackTimer = 2.0f;
        }

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        float titleScale = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android ? 4.0f : 2.5f;
        float textScale = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android ? 2.0f : 1.2f;
        float spacing = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android ? 80f : 40f;

        font.getData().setScale(titleScale);
        drawShadowedText("PAUSED", centerX - (80 * (titleScale / 2.5f)), viewport.getWorldHeight() - 100, Color.WHITE);

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            font.getData().setScale(textScale);
            drawShadowedText("--- SETTINGS ---", centerX - (80 * (textScale / 1.2f)), centerY + (spacing * 3), Color.YELLOW);
            drawShadowedText("[A] ANIMATION: " + config.animSpeed.name(), centerX - (120 * (textScale / 1.2f)), centerY + (spacing * 2), Color.WHITE);
            drawShadowedText("[D] DIFFICULTY: " + config.difficulty.name(), centerX - (120 * (textScale / 1.2f)), centerY + spacing, Color.WHITE);
            drawShadowedText("[L] PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"), centerX - (120 * (textScale / 1.2f)), centerY, config.particlesEnabled ? Color.GREEN : Color.RED);

            if (seedFeedbackTimer > 0) {
                float delta = Gdx.graphics.getDeltaTime();
                seedFeedbackTimer -= delta;
                font.setColor(0, 1, 0, MathUtils.clamp(seedFeedbackTimer, 0, 1));
                font.draw(batch, "SEED COPIED TO CLIPBOARD", centerX - (120 * (textScale / 1.2f)), centerY - spacing - 20);
            }

            font.setColor(Color.GRAY);
            font.draw(batch, "----------------", centerX - (80 * (textScale / 1.2f)), centerY - spacing);

            font.setColor(Color.WHITE);
            drawShadowedText("[O] CAMERA CONFIG", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 2), Color.WHITE);
            drawShadowedText("[I] INSTRUCTIONS", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 3), Color.WHITE);
            drawShadowedText("[C] COPY SEED", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 4), Color.WHITE);
            drawShadowedText("[M] EXIT TO MAIN MENU", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 5), Color.WHITE);
            drawShadowedText("[ESC] RESUME", centerX - (120 * (textScale / 1.2f)), centerY - (spacing * 6), Color.YELLOW);
        } else if (pauseMenuStage != null) {
            font.getData().setScale(textScale);
            font.setColor(Color.WHITE);
        }

        batch.end();

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && pauseMenuStage != null) {
            pauseMenuStage.act();
            pauseMenuStage.draw();
        }
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, CompetitiveScore compScore, GameInputProcessor input) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && !mobileUIInitialized) {
            setupMobileUI((MobileInputProcessor) input);
        }

        float delta = Gdx.graphics.getDeltaTime();
        this.isPracticeState = isPractice;

        if (!minigameController.isActive()) {
            updateSpinInput(delta, input);
            if (input.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
                distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
                distanceDisplayTimer = 3.0f;
            }
            if (input.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && levelData != null) {
                Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
                seedFeedbackTimer = 2.0f;
            }
        }

        if (isPractice) {
            Ball.State s = ball.getState();
            boolean isMoving = (s == Ball.State.AIR || s == Ball.State.ROLLING || s == Ball.State.CONTACT);
            if (isMoving) {
                ballWasActive = true;
                float currentShotDist = ball.getShotDistance();
                if (currentShotDist > maxDistanceSeen) maxDistanceSeen = currentShotDist;
                shotDistanceDisplayTimer = 0f;
            } else if (s == Ball.State.STATIONARY && ballWasActive) {
                persistentShotDistance = maxDistanceSeen;
                shotDistanceDisplayTimer = 5.0f;
                ballWasActive = false;
                maxDistanceSeen = 0f;
            }
        }

        if (mobileUIInitialized && stage != null) {
            if (debugActor != null) debugActor.update(terrain, ball, gameCamera);

            if (newMapBtn != null) {
                boolean isCompetitive = !isPractice && compScore != null;
                newMapBtn.setDisabled(isCompetitive);
                newMapBtn.setColor(isCompetitive ? Color.GRAY : Color.WHITE);
            }

            stage.getViewport().apply();
            stage.act(delta);
            stage.draw();
        }

        viewport.apply();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        batch.setProjectionMatrix(viewport.getCamera().combined);

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            drawSpinUI();
        }

        batch.begin();
        if (levelData != null) renderWindIndicator(levelData.getWind(), gameCamera);
        renderFeedbackMessages(delta);
        renderDistanceDisplay(delta);
        renderHazardPopUp(delta);
        if (isPractice) renderShotDistance(ball, delta);
        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, gameSpeed, compScore, terrain);

        if (seedFeedbackTimer > 0) {
            seedFeedbackTimer -= delta;
            font.getData().setScale(1.2f);
            Color c = Color.GREEN.cpy();
            c.a = MathUtils.clamp(seedFeedbackTimer, 0, 1);
            drawShadowedText("SEED COPIED!", viewport.getWorldWidth() / 2f - 60, viewport.getWorldHeight() - 100, c);
        }

        if (Gdx.app.getType() != com.badlogic.gdx.Application.ApplicationType.Android) {
            if (ball.getState() == Ball.State.STATIONARY && !minigameController.isActive())
                renderShotDebug(ball.getPosition(), gameCamera.direction, terrain);
        } else if (showInfoDisplay) {
            // We call this while the batch is already open
            renderClubInfo(currentClub);

            if (Gdx.input.justTouched()) {
                tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                viewport.unproject(tempV3);

                if (tempV3.x > viewport.getWorldWidth() - 340 && tempV3.x < viewport.getWorldWidth() - 20 &&
                        tempV3.y > 220 && tempV3.y < 420) {
                    showInfoDisplay = false;
                    if (infoToggleBtn != null) infoToggleBtn.setVisible(true);
                }
            }
        }

        batch.end();

        if (minigameController.isActive()) {
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot, config.animSpeed, config.difficulty, shapeRenderer, batch, font, viewport, input);
            if (minigameController.isNeedleStopped() && minigameController.getGlowTimer() >= 0.98f) {
                MinigameResult res = minigameController.getResult();
                shotFeedbackTimer = 1.5f;
                shotFeedbackColor = res.rating.color.cpy();
                shotFeedbackText = res.rating.getRandomPhrase() + " (" + (int) (res.powerMod * 100) + "%)";
            }
        } else if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            if (debugActor != null) debugActor.update(terrain, ball, gameCamera);
            stage.draw();
        }
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, float speed, CompetitiveScore compScore, Terrain terrain) {
        float topY = viewport.getWorldHeight() - 40;
        font.getData().setScale(1.5f);
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        if (isPractice) {
            drawShadowedText("PRACTICE", 40, topY, Color.CYAN);
        } else if (levelData != null) {
            String header = levelData.getArchetype().name().replace("_", " ") + (compScore != null ? " - HOLE " + compScore.getCurrentHoleNumber() : "");
            drawShadowedText(header, 40, topY, Color.GOLD);
            drawShadowedText("Par " + levelData.getPar(), 40, topY - 40, Color.WHITE);
            if (compScore != null) {
                drawShadowedText("TO PAR: " + compScore.getToParString(), 40, topY - 80, Color.YELLOW);
            }
        }

        float shotsY = (!isPractice && levelData != null) ? (compScore != null ? topY - 120 : topY - 80) : topY - 40;
        drawShadowedText("Shots: " + shotCount, 40, shotsY, Color.WHITE);

        float rightX = viewport.getWorldWidth() - 250;
        if (!isAndroid) {
            drawShadowedText("Club: " + club.name().replace("_", " "), rightX, 140, Color.WHITE);
        } else {
            font.getData().setScale(1.8f);
            drawShadowedText(club.name().replace("_", " "), rightX + 50, 180, Color.WHITE);
        }

        float currentSpinMag = ball.getSpin().len();
        String spinLabel = "NONE";
        Color typeColor = Color.GRAY;

        if (currentSpinMag > 0.1f) {
            Vector3 norm = terrain.getNormalAt(ball.getPosition().x, ball.getPosition().z);
            tempV1.set(ball.getVelocity()).set(tempV1.x, 0, tempV1.z).nor(); // Movement Direction
            tempV2.set(norm).crs(tempV1).nor(); // Proper Right-Hand axle for forward rolling

            float alignment = ball.getSpin().dot(tempV2);
            if (alignment > 25f) {
                spinLabel = "TOP";
                typeColor = Color.CHARTREUSE;
            } else if (alignment < -25f) {
                spinLabel = "BACK";
                typeColor = Color.ORANGE;
            } else {
                spinLabel = "SIDE";
                typeColor = Color.VIOLET;
            }
        }

        String spinStr = String.format("Spin: %.1fk (%s)", (currentSpinMag * 100f) / 1000f, spinLabel);
        drawShadowedText(spinStr, rightX, 100, (currentSpinMag < 0.1f) ? Color.GRAY : typeColor);
        lastDisplayedSpin = currentSpinMag;

        drawShadowedText(String.format("Speed: %.1fx", config.getGameSpeed()), rightX, 60, Color.WHITE);
    }

    public void renderClubInfo(Club club) {
        if (batch.isDrawing()) batch.end();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        clubInfoRenderer.render(
                batch,
                shapeRenderer,
                font,
                viewport,
                club.name(),
                ClubInfoManager.getClubDescription(club),
                ClubInfoManager.getCarryDistanceInfo(club),
                ClubInfoManager.getPowerInfo(club),
                ClubInfoManager.getLoftInfo(club),
                220f // Pass explicit Y position
        );

        if (batch.isDrawing()) {
            batch.end();
        }
        batch.begin();
    }

    private void renderShotDistance(Ball ball, float delta) {
        float toRender = 0;
        Ball.State state = ball.getState();
        boolean isMoving = (state == Ball.State.AIR || state == Ball.State.ROLLING || state == Ball.State.CONTACT);
        if (isMoving) {
            toRender = ball.getShotDistance();
            font.getData().setScale(1.4f);
            drawShadowedText(String.format("SHOT DISTANCE: %.1f yds", toRender), viewport.getWorldWidth() - 300, 200, Color.WHITE);
        } else if (shotDistanceDisplayTimer > 0) {
            toRender = persistentShotDistance;
            shotDistanceDisplayTimer -= delta;
            font.getData().setScale(1.4f);
            Color fadeColor = new Color(1f, 0.85f, 0f, MathUtils.clamp(shotDistanceDisplayTimer, 0, 1));
            drawShadowedText(String.format("SHOT DISTANCE: %.1f yds", toRender), viewport.getWorldWidth() - 300, 200, fadeColor);
        }
    }

    private void renderDistanceDisplay(float delta) {
        if (distanceDisplayTimer > 0) {
            distanceDisplayTimer -= delta;
            font.getData().setScale(1.8f);
            Color fadeYellow = new Color(1, 1, 0, Math.min(1, distanceDisplayTimer));
            drawShadowedText(distanceText, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() - 50, fadeYellow);
        }
    }

    private void drawSpinUI() {
        float spinX = 80, spinY = 80;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 3);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(spinX + (spinDot.x * (SPIN_UI_RADIUS - 10)), spinY + (spinDot.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();
    }

    private void renderFeedbackMessages(float delta) {
        if (shotFeedbackTimer > 0) {
            shotFeedbackTimer -= delta;
            font.getData().setScale(2.5f * (shotFeedbackTimer / 1.5f + 0.5f));
            Color fadeColor = new Color(shotFeedbackColor.r, shotFeedbackColor.g, shotFeedbackColor.b, Math.min(1, shotFeedbackTimer * 2));
            drawShadowedText(shotFeedbackText, viewport.getWorldWidth() / 2f - 150, viewport.getWorldHeight() / 2f + 150, fadeColor);
        }
    }

    private void renderShotDebug(Vector3 ballPos, Vector3 aimDir, Terrain terrain) {
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
        float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());
        font.getData().setScale(1f);
        float dy = 250;
        drawShadowedText("--- PRE-SHOT DEBUG ---", 40, dy, Color.YELLOW);
        drawShadowedText("Terrain: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z) + " (x" + String.format("%.2f", diff.terrainDifficulty) + ")", 40, dy - 20, Color.YELLOW);
        drawShadowedText(String.format("Slope Multiplier: %.2fx", diff.slopeDifficulty), 40, dy - 40, Color.YELLOW);
        if (Math.abs(sidePush) > 0.05f) {
            Color kickColor = sidePush > 0 ? Color.RED : Color.CYAN;
            drawShadowedText(String.format("Terrain Kick: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), 40, dy - 60, kickColor);
        }
    }

    public void logShotInitiated(Vector3 ballPos, Club club, Terrain terrain, ShotDifficulty diff, float powerMod) {
        minigameController.start(ballPos, club, diff, powerMod, config.animSpeed, config.difficulty);
        this.maxDistanceSeen = 0f;
    }

    private void renderWindIndicator(Vector3 worldWind, Camera camera) {
        float uiX = viewport.getWorldWidth() - 80, uiY = viewport.getWorldHeight() - 80, radius = 40f;
        
        batch.end();
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); shapeRenderer.setColor(Color.WHITE); shapeRenderer.circle(uiX, uiY, radius); shapeRenderer.end();
        if (worldWind.len() > 0.1f) {
            Vector3 camForward = new Vector3(camera.direction.x, 0, camera.direction.z).nor();
            Vector3 camRight = new Vector3(camera.direction).crs(camera.up).nor();
            camRight.y = 0; camRight.nor();
            float angle = MathUtils.atan2(worldWind.dot(camForward), worldWind.dot(camRight));
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled); shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rectLine(uiX - MathUtils.cos(angle) * 30, uiY - MathUtils.sin(angle) * 30, uiX + MathUtils.cos(angle) * 30, uiY + MathUtils.sin(angle) * 30, 3f);
            shapeRenderer.triangle(uiX + MathUtils.cos(angle) * radius, uiY + MathUtils.sin(angle) * radius, uiX + MathUtils.cos(angle + 2.6f) * 15, uiY + MathUtils.sin(angle + 2.6f) * 15, uiX + MathUtils.cos(angle - 2.6f) * 15, uiY + MathUtils.sin(angle - 2.6f) * 15);
            shapeRenderer.end();
        }
        batch.begin();
        font.getData().setScale(1.2f); drawShadowedText(String.format("%.0f MPH", worldWind.len()), uiX - 30, uiY - radius - 10, Color.WHITE);
    }

    public void showWaterHazard() { this.hazardText = "WATER HAZARD"; this.hazardColor = Color.CYAN; this.hazardTimer = 1.0f; }
    public void showOutOfBounds() { this.hazardText = "OUT OF BOUNDS"; this.hazardColor = Color.RED; this.hazardTimer = 1.1f; }

    private void renderHazardPopUp(float delta) {
        if (hazardTimer > 0) {
            hazardTimer -= delta;
            float centerX = viewport.getWorldWidth() / 2f;
            float centerY = viewport.getWorldHeight() / 2f;
            float alpha = MathUtils.clamp(hazardTimer * 2f, 0, 1);

            // Main Hazard Text
            font.getData().setScale(3.0f * (1.0f + (MathUtils.sin(hazardTimer * 10f) * 0.1f)));
            Color mainColor = new Color(hazardColor.r, hazardColor.g, hazardColor.b, alpha);
            drawShadowedText(hazardText, centerX - 200, centerY + 100, mainColor);

            // Penalty Text (only if not practice)
            if (!isPracticeState) {
                font.getData().setScale(1.5f);
                Color penaltyColor = new Color(1, 0, 0, alpha); // Pure Red
                drawShadowedText("+1 STROKE PENALTY", centerX - 120, centerY + 40, penaltyColor);
            }
        }
    }

    public void renderVictory(int shots, LevelData levelData, CompetitiveScore compScore) {
        if (gameplayTable != null) gameplayTable.setVisible(false);
        if (victoryTable != null) victoryTable.setVisible(true);
        batch.begin();
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, compScore);
        batch.end();
        
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && stage != null) {
            stage.act();
            stage.draw();
        }
    }

    public void reset() {
        if (gameplayTable != null) gameplayTable.setVisible(true);
        if (victoryTable != null) victoryTable.setVisible(false);
        minigameController.reset();
        hazardTimer = 0;
        shotFeedbackTimer = 0;
        distanceDisplayTimer = 0;
        seedFeedbackTimer = 0;
        mainMenuRequested = false;
        instructionsRequested = false;
        cameraConfigRequested = false;
        spinDot.set(0, 0);
    }

    public void updateSpinInput(float d, GameInputProcessor input) {
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT)) spinDot.x = MathUtils.clamp(spinDot.x - d * 2, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) spinDot.x = MathUtils.clamp(spinDot.x + d * 2, -1f, 1f);
        
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) spinDot.y = MathUtils.clamp(spinDot.y + d * 2, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) spinDot.y = MathUtils.clamp(spinDot.y - d * 2, -1f, 1f);
        
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public void renderInstructions(GameInputProcessor input) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            tempV1.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(tempV1);
            if (!instructionRenderer.isClickInside(tempV1.x, tempV1.y)) {
                instructionsRequested = false;
            }
        }
        batch.begin();
        instructionRenderer.render(batch, shapeRenderer, font, viewport, input);
        batch.end();
    }

    public void renderCameraConfig(GameInputProcessor input) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            tempV1.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(tempV1);
            if (!cameraConfigRenderer.isClickInside(tempV1.x, tempV1.y)) {
                cameraConfigRequested = false;
            }
        }
        batch.begin();
        cameraConfigRenderer.render(batch, shapeRenderer, font, viewport, config, input);
        batch.end();
    }
    public boolean wasCameraConfigRequested() { return cameraConfigRequested; }
    public void clearCameraConfigRequest() { cameraConfigRequested = false; }
    public void resetCameraConfigScroll() { cameraConfigRenderer.resetScroll(); }
    public boolean isTouchInsideCameraConfig(float x, float y) { return cameraConfigRenderer.isClickInside(x, y); }

    public boolean wasInstructionsRequested() { return instructionsRequested; }
    public void clearInstructionsRequest() { instructionsRequested = false; }
    public void resetInstructionScroll() { instructionRenderer.resetScroll(); }
    public boolean isTouchInsideInstructions(float x, float y) { return instructionRenderer.isClickInside(x, y); }
    public boolean isTouchInsideClubInfo(float x, float y) {
        float width = 320f, height = 200f;
        float boxX = viewport.getWorldWidth() - width - 20;
        float boxY = 220f; // Matches target y in renderClubInfo
        return x >= boxX && x <= boxX + width && y >= boxY && y <= boxY + height;
    }
    public void setClubInfoVisible(boolean visible) {
        if (infoToggleBtn != null) infoToggleBtn.setVisible(!visible);
    }

    public boolean isMinigameComplete() { return !minigameController.isActive() && minigameController.isNeedleStopped() && minigameController.getGlowTimer() <= 0 && minigameController.getResult() != null; }
    public boolean wasMinigameCanceled() { return minigameController.wasCanceled(); }
    public boolean wasMainMenuRequested() { boolean m = mainMenuRequested; mainMenuRequested = false; return m; }
    public MinigameResult getMinigameResult() { return minigameController.getResult(); }
    public Vector2 getSpinOffset() { return spinDot; }
    public Stage getStage() { return stage != null ? stage : new Stage(viewport, batch); }
    public Stage getStartMenuStage() { return startMenuStage != null ? startMenuStage : new Stage(viewport, batch); }
    public Stage getPauseMenuStage() { return pauseMenuStage != null ? pauseMenuStage : new Stage(viewport, batch); }
    public void dispose() { 
        batch.dispose(); 
        shapeRenderer.dispose(); 
        font.dispose(); 
        if (whitePixel != null) whitePixel.dispose();
        if (stage != null) stage.dispose();
        if (startMenuStage != null) startMenuStage.dispose();
        if (pauseMenuStage != null) pauseMenuStage.dispose();
        if (skin != null) skin.dispose();
    }
}