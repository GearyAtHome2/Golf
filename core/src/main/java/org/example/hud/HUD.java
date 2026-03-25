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
        font = new BitmapFont(Gdx.files.internal("font/golf.fnt"));
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear,
                Texture.TextureFilter.Linear
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

        MobileUIFactory.MobileUIPackage ui = MobileUIFactory.create(
                viewport, batch, font, config, input, whitePixel, spinIndicator, preShotDebugActor
        );

        this.stage = ui.stage;
        this.startMenuStage = ui.startMenuStage;
        this.pauseMenuStage = ui.pauseMenuStage;
        this.gameplayTable = ui.gameplayTable;
        this.victoryTable = ui.victoryTable;
        this.infoToggleBtn = ui.infoToggleBtn;
        this.skin = ui.skin;

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

    public void renderStartMenu(int selection, MainMenuRenderer.MenuState state) {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        // Passing the state through to the refactored renderer
        mainMenuRenderer.render(batch, font, viewport, selection, state);

        batch.end();

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && startMenuStage != null) {
            // Android Stage still handles the button overlay if applicable
            startMenuStage.act();
            startMenuStage.draw();
        }
    }

    public void renderPauseMenu(LevelData levelData, GameInputProcessor input) {
        if (batch.isDrawing()) batch.end();

        handlePauseInput(levelData, input);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        pauseMenuRenderer.render(batch, font, viewport, config, seedFeedbackTimer);
        batch.end();

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

        boolean isAndroid = com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        float delta = com.badlogic.gdx.Gdx.graphics.getDeltaTime();

        if (isAndroid) {
            if (!mobileUIInitialized) {
                setupMobileUI((MobileInputProcessor) input);
            }
            this.spinDot.set(spinIndicator.getSpinDot());
        }

        if (mobileClubLabel != null) {
            mobileClubLabel.setText(currentClub.name.toUpperCase());
        }

        notificationManager.update(delta);
        updateSpinInput(delta, input);

        if (input.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
            distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
            distanceDisplayTimer = 3.0f;
        }

        if (isPractice) {
            updatePracticeDistanceLogic(ball);
        }

        preShotDebugActor.update(terrain, ball, gameCamera);
        boolean shouldShowDebug = (ball.getState() == Ball.State.STATIONARY);

        if (isAndroid) {
            preShotDebugActor.setVisible(false);
        } else {
            preShotDebugActor.setVisible(shouldShowDebug);
        }

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        spinIndicator.updateScaling(viewport);

        batch.begin();

        if (levelData != null) {
            windRenderer.render(batch, shapeRenderer, font, viewport, levelData.getWind(), gameCamera);
        }

        if (!isAndroid) {
            if (shouldShowDebug) {
                preShotDebugActor.setBounds(40, 150, 400, 140);
                preShotDebugActor.draw(batch, 1.0f);
            }
            spinIndicator.draw(batch, 1.0f);
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

        renderOverlays(currentClub, gameCamera, terrain, input, delta, showClubInfo, shotController);
    }

    private void updateSpinInput(float delta, GameInputProcessor input) {
        float SPIN_SPEED = 2.0f;

        // We update the HUD's master spinDot
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

        // Sync the master dot back to the indicator so the visual matches the keyboard input
        spinIndicator.getSpinDot().set(this.spinDot);
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

            // FIXED: Using isTouchInsideClubInfo logic here to ensure consistency
            if (isTouchInsideClubInfo(tempV3.x, tempV3.y)) {
                showInfoDisplay = false;
                if (infoToggleBtn != null) infoToggleBtn.setVisible(true);

                if (input instanceof MobileInputProcessor mobileInput) {
                    mobileInput.consumeCurrentTouch();
                }
            }
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

            float responsiveScale = (viewport.getWorldHeight() * 0.035f) / font.getLineHeight();
            font.getData().setScale(responsiveScale * baseScale);

            if (!isMoving) {
                float alpha = MathUtils.clamp(distanceTracker.getTimer(), 0, 1);
                font.setColor(1f, 0.85f, 0f, alpha);
            } else {
                font.setColor(Color.WHITE);
            }

            float marginX = viewport.getWorldWidth() * 0.02f; // 5% margin from right
            float marginY = viewport.getWorldHeight() * 0.2f; // 10% margin from bottom

            String text = String.format("DISTANCE: %.1f yds", distanceToShow);

            layout.setText(font, text);
            float drawX = viewport.getWorldWidth() - layout.width - marginX;
            float drawY = marginY + layout.height;

            drawShadowedText(
                    text,
                    drawX,
                    drawY,
                    font.getColor()
            );

            // Reset state
            font.setColor(Color.WHITE);
            font.getData().setScale(1.0f);
        }
    }

    private void renderDistanceDisplay(float delta, float baseScale) {
        if (distanceDisplayTimer > 0) {
            distanceDisplayTimer -= delta;

            float fontSize = baseScale * 0.6f;
            font.getData().setScale(fontSize);

            layout.setText(font, distanceText);
            float actualWidth = layout.width;

            Color fadeYellow = new Color(1, 1, 0, Math.min(1, distanceDisplayTimer));

            float x = (viewport.getWorldWidth() / 2f) - (actualWidth / 2f);
            float y = viewport.getWorldHeight() - (40 * (fontSize / 0.6f));

            drawShadowedText(distanceText, x, y, fadeYellow);

            font.getData().setScale(1.0f);
        }
    }

    private void drawSpinUI() {
        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();

        float marginX = screenW * 0.06f;
        float marginY = screenH * 0.12f;
        float radius = screenH * 0.065f;
        float baseScale = screenH * 0.0013f;

        float centerX = marginX;
        float centerY = marginY;

        boolean isLocked = minigameController.isActive() && minigameController.isNeedleStopped();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(centerX, centerY, radius);

        shapeRenderer.setColor(isLocked ? Color.LIGHT_GRAY : Color.WHITE);
        shapeRenderer.circle(centerX, centerY, radius - (4f * baseScale));

        shapeRenderer.setColor(isLocked ? Color.MAROON : Color.RED);
        float dotRadius = 4.5f * baseScale;
        float dotOffsetLimit = radius - (10f * baseScale);

        shapeRenderer.circle(
                centerX + (spinDot.x * dotOffsetLimit),
                centerY + (spinDot.y * dotOffsetLimit),
                dotRadius
        );
        shapeRenderer.end();

        batch.begin();
        font.getData().setScale(baseScale * 0.65f);
        font.setColor(Color.WHITE);
        layout.setText(font, "SPIN");
        font.draw(batch, "SPIN", centerX - (layout.width / 2f), centerY - radius - (radius * 0.3f));
        batch.end();
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

    // FIXED: Synchronized these coordinates with the ClubInfoRenderer visual box
    public boolean isTouchInsideClubInfo(float x, float y) {
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;

        // Match ratios from ClubInfoRenderer
        float widthRatio = isAndroid ? 0.22f : 0.25f;
        float heightRatio = isAndroid ? 0.18f : 0.25f;
        float yRatio = isAndroid ? 0.22f : 0.175f; // Using the fixed yRatio of 0.32f

        float width = viewport.getWorldWidth() * widthRatio;
        float height = viewport.getWorldHeight() * heightRatio;

        if (isAndroid) {
            width = Math.max(width, 220f);
            height = Math.max(height, 120f);
        } else {
            width = Math.max(width, 280f);
            height = Math.max(height, 180f);
        }

        float boxX = viewport.getWorldWidth() - width - 20;
        float boxY = viewport.getWorldHeight() * yRatio;

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

    public void resetSpin() {
        this.spinDot.set(0, 0);
        if (spinIndicator != null) {
            spinIndicator.reset();
        }
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