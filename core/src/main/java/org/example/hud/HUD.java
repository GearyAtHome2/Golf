package org.example.hud;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.GameConfig;
import org.example.ball.*;
import org.example.hud.mobile.MobileUIFactory;
import org.example.hud.renderer.*;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

public class HUD {
    private final MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
    private boolean instructionsRequested = false;
    private boolean cameraConfigRequested = false;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private final GameConfig config;
    private final MinigameController minigameController = new MinigameController();
    private final InstructionRenderer instructionRenderer = new InstructionRenderer();
    private final CameraConfigRenderer cameraConfigRenderer = new CameraConfigRenderer();
    private final VictoryRenderer victoryRenderer = new VictoryRenderer();
    private com.badlogic.gdx.scenes.scene2d.ui.Label mobileClubLabel;
    private final WindIndicatorRenderer windRenderer = new WindIndicatorRenderer();
    private final GameInfoRenderer gameInfoRenderer = new GameInfoRenderer();
    private final PauseMenuRenderer pauseMenuRenderer = new PauseMenuRenderer();
    private final OverlayRenderer overlayRenderer = new OverlayRenderer();
    private final NotificationManager notificationManager = new NotificationManager();
    private final ShotDistanceTracker distanceTracker = new ShotDistanceTracker();
    private int shotCount = 0;
    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;
    private float distanceDisplayTimer = 0;
    private String distanceText = "";
    private float seedFeedbackTimer = 0;
    private boolean mainMenuRequested = false;
    private Stage stage;
    private Skin skin;
    private Stage startMenuStage;
    private Stage pauseMenuStage;
    private Table gameplayTable;
    private Table victoryTable;
    private boolean mobileUIInitialized = false;
    private final SpinIndicator spinIndicator;
    private final PreShotDebugActor preShotDebugActor;
    private TextButton infoToggleBtn;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    private boolean showInfoDisplay = false;
    private final Vector3 tempV3 = new Vector3();
    private Texture whitePixel;
    public static final float UI_SCALE = (Gdx.app.getType() == Application.ApplicationType.Android) ? 2.0f : 1.0f;

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
        this.spinIndicator = new SpinIndicator(shapeRenderer, font);
        this.preShotDebugActor = new PreShotDebugActor(font);
        this.minigameController.setNotificationManager(this.notificationManager);
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

    public void incrementShots() {
        shotCount++;
    }

    public void resetShots() {
        shotCount = 0;
    }

    public int getShotCount() {
        return shotCount;
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void setupMobileUI(MobileInputProcessor input) {
        if (mobileUIInitialized) return;

        // Delegate creation to Factory
        MobileUIFactory.MobileUIPackage ui = MobileUIFactory.create(
                viewport, batch, font, config, input, whitePixel, spinIndicator, preShotDebugActor
        );

        // Link HUD members to Factory-created objects
        this.stage = ui.stage;
        this.startMenuStage = ui.startMenuStage;
        this.pauseMenuStage = ui.pauseMenuStage;
        this.gameplayTable = ui.gameplayTable;
        this.victoryTable = ui.victoryTable;
        this.infoToggleBtn = ui.infoToggleBtn;
        this.skin = ui.skin;

        // Hook up the specific toggle listener that needs HUD state
        this.infoToggleBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showInfoDisplay = !showInfoDisplay;
                infoToggleBtn.setVisible(!showInfoDisplay);
            }
        });

        this.mobileClubLabel = ui.clubLabel;
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

    public void renderPauseMenu(LevelData levelData, GameInputProcessor input) {
        if (batch.isDrawing()) batch.end();

        // 1. Input/State Handling
        handlePauseInput(levelData, input);

        // 2. Rendering
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        pauseMenuRenderer.render(batch, font, viewport, config, seedFeedbackTimer);

        batch.end();

        // 3. Android UI Layer
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && pauseMenuStage != null) {
            pauseMenuStage.act();
            pauseMenuStage.draw();
        }
    }

    private void handlePauseInput(LevelData levelData, GameInputProcessor input) {
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_ANIMATION)) config.cycleAnimation();
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY)) config.cycleDifficulty();
        if (input.isActionJustPressed(GameInputProcessor.Action.TOGGLE_PARTICLES))
            config.particlesEnabled = !config.particlesEnabled;

        if (input.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) mainMenuRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.HELP)) instructionsRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.CAM_CONFIG)) cameraConfigRequested = true;

        if (input.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && levelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
            seedFeedbackTimer = 2.0f;
        }

        if (seedFeedbackTimer > 0) {
            seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
        }
    }

    public void renderPlayingHUD(Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, CompetitiveScore compScore, GameInputProcessor input, boolean showClubInfo, ShotController shotController) {
        if (batch.isDrawing()) batch.end();

        // 1. Contextual Flags
        boolean isAndroid = com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        float delta = com.badlogic.gdx.Gdx.graphics.getDeltaTime();

        // 2. Mobile Initialization & Logic
        if (isAndroid) {
            if (!mobileUIInitialized) {
                setupMobileUI((MobileInputProcessor) input);
            }
            this.spinDot.set(spinIndicator.getSpinDot());
        }

        if (mobileClubLabel != null) {
            mobileClubLabel.setText(currentClub.name.toUpperCase());
        }

        // 3. Update States
        notificationManager.update(delta);
        updateSpinInput(delta, input);

        if (input.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
            distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
            distanceDisplayTimer = 3.0f;
        }

        if (isPractice) {
            updatePracticeDistanceLogic(ball);
        }

        // 4. Debug & Transformation Setup
        preShotDebugActor.update(terrain, ball, gameCamera);
        boolean shouldShowDebug = (ball.getState() == Ball.State.STATIONARY);

        if (isAndroid) {
            preShotDebugActor.setVisible(shouldShowDebug);
        }

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        // 5. Background / Shape Rendering
        if (!isAndroid) {
            drawSpinUI();
        }

        // 6. Main Text/HUD Batch
        batch.begin();

        // Wind Indicator
        if (levelData != null) {
            windRenderer.render(batch, shapeRenderer, font, viewport, levelData.getWind(), gameCamera);
        }

        // Desktop Debug Overlay
        if (!isAndroid && shouldShowDebug) {
            preShotDebugActor.setBounds(40, 150, 400, 140);
            preShotDebugActor.draw(batch, 1.0f);
        }

        notificationManager.render(batch, font, viewport);

        float rangeScale = isAndroid ? 2.8f : 1.8f;
        renderDistanceDisplay(delta, rangeScale);

        if (isPractice) {
            float shotScale = isAndroid ? 2.2f : 1.4f;
            renderShotDistance(ball, shotScale);
        }

        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, compScore, terrain);
        batch.end();

        // 7. Overlay Layer (Stage, Minigames, etc.)
        renderOverlays(currentClub, gameCamera, terrain, input, delta, showClubInfo, shotController);
    }

    private void updatePracticeDistanceLogic(Ball ball) {
        distanceTracker.update(Gdx.graphics.getDeltaTime(), ball);
    }

    private void renderOverlays(Club currentClub, Camera gameCamera, Terrain terrain,
                                GameInputProcessor input, float delta, boolean showClubInfo,
                                org.example.ball.ShotController shotController) {
        if (mobileUIInitialized && stage != null && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            stage.act(delta);
            stage.draw();

            if (showInfoDisplay) {
                renderClubInfo(currentClub);
                handleMobileInfoClick(input);
            }
            if (spinIndicator.isBigModeActive()) {
                batch.begin();
                spinIndicator.renderBigOverlay(batch, viewport);
                batch.end();

                if (Gdx.input.isTouched()) {
                    tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                    viewport.unproject(tempV3);

                    boolean insideBall = spinIndicator.isInsideBigBall(tempV3.x, tempV3.y, viewport);

                    if (insideBall) {
                        spinIndicator.updateBigInput(tempV3.x, tempV3.y, viewport);
                        ((MobileInputProcessor) input).consumeCurrentTouch();
                    } else if (Gdx.input.justTouched()) {
                        spinIndicator.setBigModeActive(false);
                        ((MobileInputProcessor) input).consumeCurrentTouch();
                    }
                }
            }
        } else if (showClubInfo) {
            renderClubInfo(currentClub);
        }

        if (minigameController.isActive()) {
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot,
                    config.animSpeed, config.difficulty,
                    shapeRenderer, batch, font, viewport,
                    input, shotController);
        }
    }

    private void handleMobileInfoClick(GameInputProcessor input) {
        if (Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(tempV3);

            if (tempV3.x > viewport.getWorldWidth() - 340 && tempV3.x < viewport.getWorldWidth() - 20 &&
                    tempV3.y > 220 && tempV3.y < 420) {

                showInfoDisplay = false;
                if (infoToggleBtn != null) infoToggleBtn.setVisible(true);

                if (input instanceof MobileInputProcessor mobileInput) {
                    mobileInput.consumeCurrentTouch();
                }
            }
        }
    }

    private void updateSpinInput(float delta, GameInputProcessor input) {
        float SPIN_SPEED = 2.0f;

        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) {
            spinDot.y = MathUtils.clamp(spinDot.y + SPIN_SPEED * delta, -1f, 1f);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) {
            spinDot.y = MathUtils.clamp(spinDot.y - SPIN_SPEED * delta, -1f, 1f);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT)) {
            spinDot.x = MathUtils.clamp(spinDot.x - SPIN_SPEED * delta, -1f, 1f);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) {
            spinDot.x = MathUtils.clamp(spinDot.x + SPIN_SPEED * delta, -1f, 1f);
        }

        if (spinDot.len() > 1f) {
            spinDot.nor();
        }
    }

    public void renderClubInfo(Club club) {
        overlayRenderer.renderClubInfo(batch, shapeRenderer, font, viewport, club);
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, CompetitiveScore compScore, Terrain terrain) {
        gameInfoRenderer.render(batch, font, viewport, config, isPractice, levelData, club, ball, compScore, terrain, shotCount);
    }

    private void renderShotDistance(Ball ball, float baseScale) {
        boolean isMoving = ball.getState() == Ball.State.AIR ||
                ball.getState() == Ball.State.ROLLING ||
                ball.getState() == Ball.State.CONTACT;

        if (isMoving || distanceTracker.shouldShow()) {
            float distanceToShow = isMoving ? ball.getShotDistance() : distanceTracker.getDisplayDistance();

            // Apply the scale passed in
            font.getData().setScale(baseScale);

            if (!isMoving) {
                float alpha = MathUtils.clamp(distanceTracker.getTimer(), 0, 1);
                font.setColor(1f, 0.85f, 0f, alpha); // Golden yellow fade
            } else {
                font.setColor(Color.WHITE);
            }

            // We also scale the X/Y offsets so the text doesn't overlap or fall off on small screens
            float xOffset = 300 * (baseScale / 1.4f);
            float yOffset = 200 * (baseScale / 1.4f);

            drawShadowedText(
                    String.format("SHOT DISTANCE: %.1f yds", distanceToShow),
                    viewport.getWorldWidth() - xOffset,
                    yOffset,
                    font.getColor()
            );

            font.setColor(Color.WHITE);
            font.getData().setScale(1.0f); // IMPORTANT: Reset scale for the rest of the HUD
        }
    }

    private void renderDistanceDisplay(float delta, float baseScale) {
        if (distanceDisplayTimer > 0) {
            distanceDisplayTimer -= delta;

            font.getData().setScale(baseScale);

            layout.setText(font, distanceText);
            float actualWidth = layout.width;

            Color fadeYellow = new Color(1, 1, 0, Math.min(1, distanceDisplayTimer));

            float x = (viewport.getWorldWidth() / 2f) - (actualWidth / 2f);
            float y = viewport.getWorldHeight() - (50 * (baseScale / 1.8f));

            drawShadowedText(distanceText, x, y, fadeYellow);

            font.getData().setScale(1.0f);
        }
    }

    private void drawSpinUI() {
        float spinX = 80, spinY = 80;

        Vector2 displaySpin = spinDot;
        boolean isLocked = minigameController.isActive() && minigameController.isNeedleStopped();


        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(isLocked ? Color.GRAY : Color.WHITE); // Visual cue
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);

        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 3);

        // Draw the red dot. If locked, maybe make it darker red.
        shapeRenderer.setColor(isLocked ? Color.MAROON : Color.RED);
        shapeRenderer.circle(spinX + (displaySpin.x * (SPIN_UI_RADIUS - 10)),
                spinY + (displaySpin.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();
    }

    public void logShotInitiated(Vector3 ballPos, Club club, ShotDifficulty diff, float powerMod) {
        minigameController.start(ballPos, club, diff, powerMod, config.animSpeed, config.difficulty);
    }

    public void cancelMinigame() {
        minigameController.cancel();
    }

    public void showWaterHazard() {
        notificationManager.showHazard("WATER HAZARD", Color.CYAN, 1.1f);
    }

    public void showOutOfBounds() {
        notificationManager.showHazard("OUT OF BOUNDS", Color.RED, 1.1f);
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
        distanceDisplayTimer = 0;
        seedFeedbackTimer = 0;
        mainMenuRequested = false;
        instructionsRequested = false;
        cameraConfigRequested = false;
        spinDot.set(0, 0);
        overlayRenderer.resetScrolls();
    }

    public void renderInstructions(GameInputProcessor input) {
        overlayRenderer.renderInstructions(batch, shapeRenderer, font, viewport, input, () -> instructionsRequested = false);
    }

    public void renderCameraConfig(GameInputProcessor input) {
        overlayRenderer.renderCameraConfig(batch, shapeRenderer, font, viewport, config, input, () -> cameraConfigRequested = false);
    }

    public boolean wasCameraConfigRequested() {
        return cameraConfigRequested;
    }

    public void clearCameraConfigRequest() {
        cameraConfigRequested = false;
    }

    public void resetCameraConfigScroll() {
        cameraConfigRenderer.resetScroll();
    }

    public boolean isTouchInsideCameraConfig(float x, float y) {
        return cameraConfigRequested && overlayRenderer.getCameraConfigRenderer().isClickInside(x, y);
    }

    public boolean wasInstructionsRequested() {
        return instructionsRequested;
    }

    public void clearInstructionsRequest() {
        instructionsRequested = false;
    }

    public void resetInstructionScroll() {
        instructionRenderer.resetScroll();
    }

    public boolean isTouchInsideInstructions(float x, float y) {
        return overlayRenderer.getInstructionRenderer().isClickInside(x, y);
    }

    public boolean isTouchInsideClubInfo(float x, float y) {
        float width = 320f, height = 200f;
        float boxX = viewport.getWorldWidth() - width - 20;
        float boxY = 220f;
        return x >= boxX && x <= boxX + width && y >= boxY && y <= boxY + height;
    }

    public void setClubInfoVisible(boolean visible) {
        if (infoToggleBtn != null) infoToggleBtn.setVisible(!visible);
    }

    public boolean isMinigameComplete() {
        return !minigameController.isActive() && minigameController.isNeedleStopped() && minigameController.getGlowTimer() <= 0 && minigameController.getResult() != null;
    }

    public boolean wasMinigameCanceled() {
        return minigameController.wasCanceled();
    }

    public boolean wasMainMenuRequested() {
        boolean m = mainMenuRequested;
        mainMenuRequested = false;
        return m;
    }

    public MinigameResult getMinigameResult() {
        return minigameController.getResult();
    }

    public Vector2 getSpinOffset() {
        return spinDot;
    }

    public Stage getStage() {
        return stage != null ? stage : new Stage(viewport, batch);
    }

    public Stage getStartMenuStage() {
        return startMenuStage != null ? startMenuStage : new Stage(viewport, batch);
    }

    public Stage getPauseMenuStage() {
        return pauseMenuStage != null ? pauseMenuStage : new Stage(viewport, batch);
    }

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