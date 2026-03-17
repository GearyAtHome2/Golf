package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
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
    private boolean mobileUIInitialized = false;

    // Temporary vectors for HUD calculations to avoid allocation
    private final Vector3 tempV1 = new Vector3();
    private final Vector3 tempV2 = new Vector3();

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
    }

    private void drawShadowedText(String text, float x, float y, Color color) {
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
        if (stage != null) stage.getViewport().update(width, height, true);
    }
    
    public void setupMobileUI(MobileInputProcessor input) {
        if (mobileUIInitialized) return;
        
        stage = new Stage(new ScreenViewport());
        
        // Note: For a real app we'd load a .json skin, but for now we create a basic one
        skin = new Skin();
        skin.add("default", font);
        
        TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
        style.font = font;
        style.up = null; // We could add textures here if available
        style.down = null;
        style.fontColor = Color.WHITE;
        style.downFontColor = Color.YELLOW;

        Table table = new Table();
        table.setFillParent(true);
        stage.addActor(table);

        // Club Up/Down buttons
        TextButton clubUp = new TextButton("[ Club + ]", style);
        clubUp.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.CLUB_UP);
            }
        });

        TextButton clubDown = new TextButton("[ Club - ]", style);
        clubDown.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.CLUB_DOWN);
            }
        });

        TextButton projBtn = new TextButton("[ PROJ ]", style);
        projBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                input.triggerAction(GameInputProcessor.Action.PROJECTION);
            }
        });

        // Power button (hold to charge)
        TextButton powerBtn = new TextButton("[ HIT ]", style);
        powerBtn.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, true);
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                input.setActionState(GameInputProcessor.Action.CHARGE_SHOT, false);
            }
        });

        table.bottom().left().pad(20);
        table.add(clubUp).pad(10);
        table.add(clubDown).pad(10);
        table.add(projBtn).pad(10);
        table.add(powerBtn).pad(10);

        mobileUIInitialized = true;
    }

    public void renderStartMenu(int selection) {
        mainMenuRenderer.render(batch, font, viewport, selection);
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

        font.getData().setScale(2.5f);
        drawShadowedText("PAUSED", centerX - 80, viewport.getWorldHeight() - 100, Color.WHITE);

        font.getData().setScale(1.2f);
        drawShadowedText("--- SETTINGS ---", centerX - 80, centerY + 120, Color.YELLOW);
        drawShadowedText("[A] ANIMATION: " + config.animSpeed.name(), centerX - 120, centerY + 80, Color.WHITE);
        drawShadowedText("[D] DIFFICULTY: " + config.difficulty.name(), centerX - 120, centerY + 40, Color.WHITE);
        drawShadowedText("[L] PARTICLES: " + (config.particlesEnabled ? "ON" : "OFF"), centerX - 120, centerY, config.particlesEnabled ? Color.GREEN : Color.RED);

        if (seedFeedbackTimer > 0) {
            float delta = Gdx.graphics.getDeltaTime();
            seedFeedbackTimer -= delta;
            font.setColor(0, 1, 0, MathUtils.clamp(seedFeedbackTimer, 0, 1));
            font.draw(batch, "SEED COPIED TO CLIPBOARD", centerX - 120, centerY - 60);
        }

        font.setColor(Color.GRAY);
        font.draw(batch, "----------------", centerX - 80, centerY - 40);
        drawShadowedText("[R] RESET BALL  |  [N] NEW LEVEL  |  [M] MAIN MENU  |  [C] COPY SEED  |  [I] HELP  |  [O] CAM CONFIG", 50, 100, Color.WHITE);

        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, CompetitiveScore compScore, GameInputProcessor input) {
        if (input instanceof MobileInputProcessor) {
            setupMobileUI((MobileInputProcessor) input);
            if (stage != null) {
                Gdx.input.setInputProcessor(stage);
                stage.act(Gdx.graphics.getDeltaTime());
                stage.draw();
            }
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

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        drawSpinUI();
        if (levelData != null) renderWindIndicator(levelData.getWind(), gameCamera);

        batch.begin();
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

        if (ball.getState() == Ball.State.STATIONARY && !minigameController.isActive())
            renderShotDebug(ball.getPosition(), gameCamera.direction, terrain);
        batch.end();

        if (minigameController.isActive()) {
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot, config.animSpeed, config.difficulty, shapeRenderer, batch, font, viewport, input);
            if (minigameController.isNeedleStopped() && minigameController.getGlowTimer() >= 0.98f) {
                MinigameResult res = minigameController.getResult();
                shotFeedbackTimer = 1.5f;
                shotFeedbackColor = res.rating.color.cpy();
                shotFeedbackText = res.rating.getRandomPhrase() + " (" + (int) (res.powerMod * 100) + "%)";
            }
        }
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, float speed, CompetitiveScore compScore, Terrain terrain) {
        float topY = viewport.getWorldHeight() - 40;
        font.getData().setScale(1.5f);

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
        drawShadowedText("Club: " + club.name().replace("_", " "), rightX, 140, Color.WHITE);

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
                ClubInfoManager.getLoftInfo(club)
        );

        if (batch.isDrawing()) {
            batch.end();
        }
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
        batch.begin(); font.getData().setScale(1.2f); drawShadowedText(String.format("%.0f MPH", worldWind.len()), uiX - 30, uiY - radius - 10, Color.WHITE); batch.end();
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
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, compScore);
    }

    public void reset() {
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
        if (input.isActionPressed(GameInputProcessor.Action.CYCLE_ANIMATION)) spinDot.x = MathUtils.clamp(spinDot.x - d * 2, -1f, 1f); // Reusing A/D
        if (input.isActionPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY)) spinDot.x = MathUtils.clamp(spinDot.x + d * 2, -1f, 1f);
        
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) spinDot.y = MathUtils.clamp(spinDot.y + d * 2, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) spinDot.y = MathUtils.clamp(spinDot.y - d * 2, -1f, 1f);
        
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public void renderInstructions(GameInputProcessor input) { instructionRenderer.render(batch, shapeRenderer, font, viewport, input); }
    public boolean wasInstructionsRequested() { return instructionsRequested; }
    public void clearInstructionsRequest() { instructionsRequested = false; }
    public void resetInstructionScroll() { instructionRenderer.resetScroll(); }

    public void renderCameraConfig(GameInputProcessor input) { cameraConfigRenderer.render(batch, shapeRenderer, font, viewport, config, input); }
    public boolean wasCameraConfigRequested() { return cameraConfigRequested; }
    public void clearCameraConfigRequest() { cameraConfigRequested = false; }
    public void resetCameraConfigScroll() { cameraConfigRenderer.resetScroll(); }

    public boolean isMinigameComplete() { return !minigameController.isActive() && minigameController.isNeedleStopped() && minigameController.getGlowTimer() <= 0 && minigameController.getResult() != null; }
    public boolean wasMinigameCanceled() { return minigameController.wasCanceled(); }
    public boolean wasMainMenuRequested() { boolean m = mainMenuRequested; mainMenuRequested = false; return m; }
    public MinigameResult getMinigameResult() { return minigameController.getResult(); }
    public Vector2 getSpinOffset() { return spinDot; }
    public void dispose() { batch.dispose(); shapeRenderer.dispose(); font.dispose(); }
}