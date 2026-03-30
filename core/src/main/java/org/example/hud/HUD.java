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
import org.example.ball.Ball;
import org.example.ball.MinigameResult;
import org.example.ball.ShotController;
import org.example.ball.ShotDifficulty;
import org.example.gameManagers.GameSession;
import org.example.gameManagers.MenuManager;
import org.example.hud.minigame.MinigameController;
import org.example.hud.mobile.MobileUIFactory;
import org.example.hud.renderer.*;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.scoreBoard.HighscoreService;
import org.example.scoreBoard.LeaderboardUI;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

public class HUD {
    private final MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
    private LeaderboardUI leaderboardUI;
    private final HighscoreService highscoreService = new HighscoreService();
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
    private Table startMenuTable;
    private boolean mobileUIInitialized = false;
    private MainMenuRenderer.MenuState lastMobileMenuState = null;
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
    private final SpinIndicator spinIndicator;
    private final PreShotDebugActor preShotDebugActor;
    private TextButton infoToggleBtn;

    private GameSession activeSession;
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
        this.stage = new Stage(viewport, batch);
        this.startMenuStage = new Stage(viewport, batch);

        try {
            this.skin = new Skin(Gdx.files.internal("ui/uiskin.json"));
        } catch (Exception e) {
            Gdx.app.error("HUD", "Could not load uiskin.json. Make sure it exists in assets/ui/");
        }
        this.spinIndicator = new SpinIndicator(shapeRenderer, font);
        this.preShotDebugActor = new PreShotDebugActor(font);
        this.minigameController.setNotificationManager(this.notificationManager);
        this.minigameController.setOnShotFinalized(this::incrementShots);

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

    public void setActiveSession(GameSession session) {
        this.activeSession = session;
        if (session != null) {
            this.shotCount = session.getCurrentHoleStrokes();
        }
    }

    public void incrementShots() {
        shotCount++;
        if (activeSession != null) {
            activeSession.incrementStrokes();
        }
    }

    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (startMenuStage != null) {
            startMenuStage.getViewport().update(width, height, true);
            updateLeaderboardLayout();
        }
    }

    private void updateLeaderboardLayout() {
        if (leaderboardUI != null) {
            // 1. Calculate a dynamic width (e.g., 40% of screen)
            float targetWidth = viewport.getWorldWidth() * 0.40f;

            // 2. Clamp it: 320px is the absolute floor for 5 buttons; 600px is the ceiling
            float finalWidth = MathUtils.clamp(targetWidth, 320f, 600f);

            // 3. Scale height relative to screen
            float finalHeight = viewport.getWorldHeight() * 0.85f;

            leaderboardUI.setSize(finalWidth, finalHeight);

            // 4. Position it with a relative margin
            float margin = viewport.getWorldWidth() * 0.02f;
            float xPos = viewport.getWorldWidth() - finalWidth - margin;
            float yPos = (viewport.getWorldHeight() - finalHeight) / 2f;

            leaderboardUI.setPosition(xPos, yPos);

            // 5. Force the UI to re-layout its internal children based on new size
            leaderboardUI.invalidateHierarchy();
        }
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
        this.startMenuTable = ui.startMenuTable;
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

    public void renderStartMenu(MenuManager menuManager, MenuManager.MenuHandler callback, GameSession standard, GameSession daily) {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);

        batch.begin();
        mainMenuRenderer.render(batch, font, viewport, menuManager.getMenuSelection(), menuManager.getCurrentMenuState(), standard, daily);
        batch.end();

        if (leaderboardUI == null && startMenuStage != null) {
            leaderboardUI = new LeaderboardUI(getSkin(), highscoreService);
            startMenuStage.addActor(leaderboardUI);
            updateLeaderboardLayout();
        }

        if (startMenuStage != null) {
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
                if (lastMobileMenuState != menuManager.getCurrentMenuState()) {
                    setupMobileStartMenu(menuManager, callback, standard, daily);
                    lastMobileMenuState = menuManager.getCurrentMenuState();
                }
            }

            if (leaderboardUI != null) {
                leaderboardUI.setVisible(menuManager.getCurrentMenuState() == MainMenuRenderer.MenuState.MAIN);
            }

            startMenuStage.act();
            startMenuStage.draw();
        }
    }

    private void setupMobileStartMenu(MenuManager menuManager, MenuManager.MenuHandler callback, GameSession standard, GameSession daily) {
        if (startMenuTable == null) return;
        startMenuTable.clearChildren();
        MobileUIFactory.buildStartMenuButtons(startMenuTable, menuManager, callback, standard, daily, viewport, font);
    }

    public void renderPauseMenu(LevelData levelData, GameInputProcessor input, GameSession session, boolean blockInput) {
        if (batch.isDrawing()) batch.end();
        if (!blockInput) handlePauseInput(levelData, input, session);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        pauseMenuRenderer.render(batch, font, viewport, config, seedFeedbackTimer, session);
        batch.end();

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && pauseMenuStage != null) {
            pauseMenuStage.act();
            pauseMenuStage.draw();
        }
    }

    private void handlePauseInput(LevelData levelData, GameInputProcessor input, GameSession session) {
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_ANIMATION)) config.cycleAnimation();
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY)) {
            if (session == null) config.cycleDifficulty();
            else notificationManager.showHazard("DIFFICULTY LOCKED", Color.GRAY, 0.5f);
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.TOGGLE_PARTICLES))
            config.particlesEnabled = !config.particlesEnabled;
        if (input.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) mainMenuRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.HELP)) instructionsRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.CAM_CONFIG)) cameraConfigRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && levelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
            seedFeedbackTimer = 2.0f;
        }
        if (seedFeedbackTimer > 0) seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
    }

    public void renderPlayingHUD(Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, GameSession session, GameInputProcessor input, boolean showClubInfo, ShotController shotController) {
        if (batch.isDrawing()) batch.end();
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float delta = Gdx.graphics.getDeltaTime();

        if (isAndroid) {
            if (!mobileUIInitialized) setupMobileUI((MobileInputProcessor) input);
            this.spinDot.set(spinIndicator.getSpinDot());
        }
        if (mobileClubLabel != null) mobileClubLabel.setText(currentClub.name.toUpperCase());

        notificationManager.update(delta);
        updateSpinInput(delta, input);

        if (input.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
            distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
            distanceDisplayTimer = 3.0f;
        }
        if (isPractice) updatePracticeDistanceLogic(ball);

        preShotDebugActor.update(terrain, ball, gameCamera);
        boolean shouldShowDebug = (ball.getState() == Ball.State.STATIONARY);
        preShotDebugActor.setVisible(!isAndroid && shouldShowDebug);

        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spinIndicator.updateScaling(viewport);

        batch.begin();
        if (levelData != null) windRenderer.render(batch, shapeRenderer, font, viewport, levelData.getWind(), gameCamera);
        if (!isAndroid && shouldShowDebug) {
            preShotDebugActor.setBounds(40, 150, 400, 140);
            preShotDebugActor.draw(batch, 1.0f);
        }
        if (!isAndroid) spinIndicator.draw(batch, 1.0f);
        notificationManager.render(batch, font, viewport);

        float rangeScale = isAndroid ? 2.8f : 1.8f;
        renderDistanceDisplay(delta, rangeScale);
        if (isPractice) renderShotDistance(ball, isAndroid ? 2.2f : 1.4f);
        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, session, terrain);
        batch.end();

        renderOverlays(currentClub, gameCamera, terrain, input, delta, showClubInfo, shotController);
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, GameSession session, Terrain terrain) {
        gameInfoRenderer.render(batch, font, viewport, config, isPractice, levelData, club, ball, session, terrain, shotCount);
    }

    private void updateSpinInput(float delta, GameInputProcessor input) {
        float SPIN_SPEED = 2.0f;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) spinDot.y = MathUtils.clamp(spinDot.y + SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) spinDot.y = MathUtils.clamp(spinDot.y - SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT)) spinDot.x = MathUtils.clamp(spinDot.x - SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT)) spinDot.x = MathUtils.clamp(spinDot.x + SPIN_SPEED * delta, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
        spinIndicator.getSpinDot().set(this.spinDot);
    }

    private void updatePracticeDistanceLogic(Ball ball) {
        distanceTracker.update(Gdx.graphics.getDeltaTime(), ball);
    }

    private void renderOverlays(Club currentClub, Camera gameCamera, Terrain terrain, GameInputProcessor input, float delta, boolean showClubInfo, ShotController shotController) {
        if (mobileUIInitialized && stage != null && Gdx.app.getType() == Application.ApplicationType.Android) {
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
                    if (spinIndicator.isInsideBigBall(tempV3.x, tempV3.y, viewport)) {
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
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot, config.animSpeed, config.difficulty, shapeRenderer, batch, font, viewport, input, shotController);
        }
    }

    private void handleMobileInfoClick(GameInputProcessor input) {
        if (Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            viewport.unproject(tempV3);
            if (isTouchInsideClubInfo(tempV3.x, tempV3.y)) {
                showInfoDisplay = false;
                if (infoToggleBtn != null) infoToggleBtn.setVisible(true);
                if (input instanceof MobileInputProcessor mobileInput) mobileInput.consumeCurrentTouch();
            }
        }
    }

    public void renderClubInfo(Club club) {
        overlayRenderer.renderClubInfo(batch, shapeRenderer, font, viewport, club);
    }

    private void renderShotDistance(Ball ball, float baseScale) {
        boolean isMoving = ball.getState() == Ball.State.AIR || ball.getState() == Ball.State.ROLLING || ball.getState() == Ball.State.CONTACT;
        if (isMoving || distanceTracker.shouldShow()) {
            float distanceToShow = isMoving ? ball.getShotDistance() : distanceTracker.getDisplayDistance();
            float responsiveScale = (viewport.getWorldHeight() * 0.035f) / font.getLineHeight();
            font.getData().setScale(responsiveScale * baseScale);
            if (!isMoving) font.setColor(1f, 0.85f, 0f, MathUtils.clamp(distanceTracker.getTimer(), 0, 1));
            else font.setColor(Color.WHITE);
            String text = String.format("DISTANCE: %.1f yds", distanceToShow);
            layout.setText(font, text);
            drawShadowedText(text, viewport.getWorldWidth() - layout.width - (viewport.getWorldWidth() * 0.02f), (viewport.getWorldHeight() * 0.2f) + layout.height, font.getColor());
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
            drawShadowedText(distanceText, (viewport.getWorldWidth() / 2f) - (layout.width / 2f), viewport.getWorldHeight() - (40 * (fontSize / 0.6f)), new Color(1, 1, 0, Math.min(1, distanceDisplayTimer)));
            font.getData().setScale(1.0f);
        }
    }

    public void logShotInitiated(Vector3 ballPos, Club club, ShotDifficulty diff, float powerMod) {
        minigameController.start(ballPos, club, diff, powerMod, config.animSpeed, config.difficulty);
    }

    public void cancelMinigame() { minigameController.cancel(); }
    public void showWaterHazard() { notificationManager.showHazard("WATER HAZARD", Color.CYAN, 1.1f); }
    public void showOutOfBounds() { notificationManager.showHazard("OUT OF BOUNDS", Color.RED, 1.1f); }

    public void renderVictory(int shots, LevelData levelData, GameSession session) {
        if (gameplayTable != null) gameplayTable.setVisible(false);
        if (victoryTable != null) victoryTable.setVisible(true);
        batch.begin();
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, session);
        batch.end();
        if (Gdx.app.getType() == Application.ApplicationType.Android && stage != null) {
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

    public boolean wasCameraConfigRequested() { return cameraConfigRequested; }
    public void clearCameraConfigRequest() { cameraConfigRequested = false; }
    public void resetCameraConfigScroll() { cameraConfigRenderer.resetScroll(); }

    public boolean isTouchInsideCameraConfig(float x, float y) {
        return cameraConfigRequested && overlayRenderer.getCameraConfigRenderer().isClickInside(x, y);
    }

    public boolean wasInstructionsRequested() { return instructionsRequested; }
    public void clearInstructionsRequest() { instructionsRequested = false; }
    public void resetInstructionScroll() { instructionRenderer.resetScroll(); }

    public boolean isTouchInsideInstructions(float x, float y) {
        return overlayRenderer.getInstructionRenderer().isClickInside(x, y);
    }

    public boolean isTouchInsideClubInfo(float x, float y) {
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float width = Math.max(viewport.getWorldWidth() * (isAndroid ? 0.22f : 0.25f), isAndroid ? 220f : 280f);
        float height = Math.max(viewport.getWorldHeight() * (isAndroid ? 0.18f : 0.25f), isAndroid ? 120f : 180f);
        float boxX = viewport.getWorldWidth() - width - 20;
        float boxY = viewport.getWorldHeight() * (isAndroid ? 0.22f : 0.175f);
        return x >= boxX && x <= boxX + width && y >= boxY && y <= boxY + height;
    }

    public void setClubInfoVisible(boolean visible) { if (infoToggleBtn != null) infoToggleBtn.setVisible(!visible); }
    public boolean isMinigameComplete() { return !minigameController.isActive() && minigameController.isNeedleStopped() && minigameController.getGlowTimer() <= 0 && minigameController.getResult() != null; }
    public boolean wasMinigameCanceled() { return minigameController.wasCanceled(); }
    public boolean wasMainMenuRequested() { boolean m = mainMenuRequested; mainMenuRequested = false; return m; }
    public MinigameResult getMinigameResult() { return minigameController.getResult(); }
    public Vector2 getSpinOffset() { return spinDot; }

    public void resetSpin() {
        this.spinDot.set(0, 0);
        if (spinIndicator != null) spinIndicator.reset();
    }

    public Stage getStage() { return stage != null ? stage : new Stage(viewport, batch); }
    public Stage getStartMenuStage() { return startMenuStage != null ? startMenuStage : new Stage(viewport, batch); }
    public Stage getPauseMenuStage() { return pauseMenuStage != null ? pauseMenuStage : new Stage(viewport, batch); }

    public Skin getSkin() {
        if (skin == null) {
            try { skin = new Skin(Gdx.files.internal("ui/uiskin.json")); }
            catch (Exception e) { skin = new Skin(); }
        }
        return skin;
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