package com.gearygolf.golf.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.Image;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.Club;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.ball.Ball;
import com.gearygolf.golf.ball.MinigameResult;
import com.gearygolf.golf.multiplayer.LiveScoreboardActor;
import com.gearygolf.golf.ball.ShotController;
import com.gearygolf.golf.ball.ShotDifficulty;
import com.gearygolf.golf.gameManagers.MenuManager;
import com.gearygolf.golf.hud.minigame.MinigameController;
import com.gearygolf.golf.hud.mobile.MobileHUDController;
import com.gearygolf.golf.hud.renderer.*;
import com.gearygolf.golf.input.GameInputProcessor;
import com.gearygolf.golf.input.MobileInputProcessor;
import com.gearygolf.golf.scoreBoard.HighscoreService;
import com.gearygolf.golf.scoreBoard.LeaderboardUI;
import com.gearygolf.golf.session.CompetitiveSessions;
import com.gearygolf.golf.session.GameSession;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.level.LevelData;
import com.gearygolf.golf.shot.ShotExportPacket;
import com.gearygolf.golf.tutorial.TutorialController;

public class HUD {
    private final MainMenuRenderer mainMenuRenderer = new MainMenuRenderer();
    private final TutorialHUDCoordinator tutorialCoordinator;
    private LeaderboardUI leaderboardUI;
    private final HighscoreService highscoreService = new HighscoreService();
    private java.util.function.Consumer<GameSession.GameMode> leaderboardPlayDailyCallback;
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private final GameConfig config;
    private final MinigameController minigameController = new MinigameController();
    private final VictoryRenderer victoryRenderer = new VictoryRenderer();
    private final WindIndicatorRenderer windRenderer = new WindIndicatorRenderer();
    private final GameInfoRenderer gameInfoRenderer = new GameInfoRenderer();
    private final TerrainToastRenderer terrainToastRenderer = new TerrainToastRenderer();
    private com.gearygolf.golf.glamour.SoundManager soundManager;
    public void setSoundManager(com.gearygolf.golf.glamour.SoundManager sm) { this.soundManager = sm; }
    private final PauseMenuRenderer pauseMenuRenderer = new PauseMenuRenderer();
    private final com.gearygolf.golf.hud.renderer.SoundSettingsRenderer soundSettingsRenderer = new com.gearygolf.golf.hud.renderer.SoundSettingsRenderer();
    private final com.gearygolf.golf.hud.renderer.ProfileRenderer  profileRenderer  = new com.gearygolf.golf.hud.renderer.ProfileRenderer();
    private final com.gearygolf.golf.hud.renderer.RankInfoRenderer rankInfoRenderer = new com.gearygolf.golf.hud.renderer.RankInfoRenderer();
    private final OverlayRenderer overlayRenderer = new OverlayRenderer();
    private final NotificationManager notificationManager = new NotificationManager();
    private final ShotDistanceTracker distanceTracker = new ShotDistanceTracker();
    private final HoleTimerRenderer holeTimerRenderer = new HoleTimerRenderer();
    private int shotCount = 0;
    private final Vector2 spinDot = new Vector2(0, 0);
    private float distanceDisplayTimer = 0;
    private String distanceText = "";
    private Club lastRenderedClub = null;
    private final Color tempDistanceColor = new Color();
    private float seedFeedbackTimer = 0;
    private float shotExportFeedbackTimer = 0;
    private boolean mainMenuRequested = false;
    private Stage stage;
    private Skin skin;
    private Stage startMenuStage;
    private Stage pauseMenuStage;
    private Actor importRetryOverlay;
    private final SwingUIController swingUIController;
    private final SpinIndicator spinIndicator;
    private final PreShotDebugActor preShotDebugActor;
    private final MobileHUDController mobileHUD;

    private GameSession activeSession;
    private final com.badlogic.gdx.graphics.g2d.GlyphLayout layout = new com.badlogic.gdx.graphics.g2d.GlyphLayout();
    private final Vector3 tempV3 = new Vector3();
    public static final float UI_SCALE = Platform.isAndroid() ? 2.0f : 1.0f;

    // Distance display: Y offset from top of viewport, in world units, at base font scale.
    private static final float DISTANCE_TEXT_TOP_OFFSET = 55f;

    // Shot-replay overlay: vertical positions as fractions of viewport height.
    private static final float SHOT_REPLAY_TITLE_Y  = 0.88f;
    private static final float SHOT_REPLAY_INFO_Y   = 0.82f;
    private static final float SHOT_REPLAY_PROMPT_Y = 0.14f;

    public HUD(GameConfig config) {
        this.config = config;
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont(Gdx.files.internal("font/golf.fnt"));
        font.getRegion().getTexture().setFilter(
                Texture.TextureFilter.Linear,
                Texture.TextureFilter.Linear
        );

        this.viewport = new ExtendViewport(1280, 720);
        this.stage = new Stage(viewport, batch);
        this.startMenuStage = new Stage(viewport, batch);
        this.pauseMenuStage = new Stage(viewport, batch);

        this.skin = initSkin();
        this.spinIndicator = new SpinIndicator(shapeRenderer, font);
        this.preShotDebugActor = new PreShotDebugActor(font);
        this.minigameController.setNotificationManager(this.notificationManager);
        this.minigameController.setOnShotFinalized(this::incrementShots);
        this.swingUIController = new SwingUIController(batch, shapeRenderer, font, viewport);
        this.mobileHUD = new MobileHUDController(viewport, batch, font, config, spinIndicator, swingUIController);
        this.tutorialCoordinator = new TutorialHUDCoordinator(batch, shapeRenderer, font, viewport, mobileHUD);
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

    public void resetShots() {
        shotCount = 0;
    }

    public int getShotCount() {
        return shotCount;
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
        if (startMenuStage != null) startMenuStage.getViewport().update(width, height, true);
        if (pauseMenuStage != null) pauseMenuStage.getViewport().update(width, height, true);
        if (stage != null) stage.getViewport().update(width, height, true);
        updateLeaderboardLayout();
    }

    private void updateLeaderboardLayout() {
        if (leaderboardUI == null) return;

        boolean isAndroid = Platform.isAndroid();
        float screenW = startMenuStage.getViewport().getWorldWidth();
        float screenH = startMenuStage.getViewport().getWorldHeight();

        float menuWidth = screenW * 0.485f;
        float minGap = screenW * 0.016f;
        float edgePadding = screenW * 0.012f;

        float targetLeaderboardWidth = screenW * 0.45f;
        float totalRequired = menuWidth + targetLeaderboardWidth + minGap + edgePadding;

        float squeeze = 1.0f;
        if (totalRequired > screenW) {
            squeeze = screenW / totalRequired;
        }

        float finalWidth = targetLeaderboardWidth * squeeze;
        float finalHeight = screenH * 0.75f;

        leaderboardUI.setSize(finalWidth, finalHeight);
        leaderboardUI.setPosition(screenW - edgePadding, screenH * 0.42f, com.badlogic.gdx.utils.Align.right);
        leaderboardUI.rebuild(squeeze);

        Table startMenuTable = mobileHUD.getStartMenuTable();
        if (startMenuTable != null) {
            startMenuTable.setTransform(true);
            startMenuTable.setScale(squeeze);
        }
    }

    public void setupMobileUI(MobileInputProcessor input) {
        if (mobileHUD.isInitialized()) return;
        mobileHUD.setup(input, preShotDebugActor);
        this.stage = mobileHUD.getStage();
        this.startMenuStage = mobileHUD.getStartMenuStage();
        this.pauseMenuStage = mobileHUD.getPauseMenuStage();
        this.skin = mobileHUD.getSkin();
    }

    public void renderStartMenu(MenuManager menuManager, MenuManager.MenuHandler callback, CompetitiveSessions sessions, com.gearygolf.golf.scoreBoard.DailySubmissionCache dailyCache, com.gearygolf.golf.glamour.SoundManager soundManager) {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        mainMenuRenderer.render(batch, font, viewport, menuManager.getMenuSelection(), menuManager.getCurrentMenuState(), sessions, menuManager.getMapScrollOffset(), dailyCache);
        batch.end();

        // Rank badge — drawn with ShapeRenderer after text, only on the MAIN menu state
        if (menuManager.getCurrentMenuState() == MainMenuRenderer.MenuState.MAIN
                && mainMenuRenderer.getBadgeRadius() > 0) {
            shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
            com.gearygolf.golf.hud.renderer.RankBadge b = mainMenuRenderer.getBadge();
            b.renderCircle(shapeRenderer, batch, mainMenuRenderer.getBadgeCX(), mainMenuRenderer.getBadgeCY(), mainMenuRenderer.getBadgeRadius());
            batch.begin();
            b.renderText(batch, font, mainMenuRenderer.getBadgeCX(), mainMenuRenderer.getBadgeCY(), mainMenuRenderer.getBadgeRadius());
            batch.end();
        }

        if (leaderboardUI == null && startMenuStage != null) {
            leaderboardUI = new LeaderboardUI(getSkin(), font, highscoreService);
            leaderboardUI.setDailySubmissionCache(dailyCache);
            if (leaderboardPlayDailyCallback != null) {
                leaderboardUI.setPlayDailyCallback(leaderboardPlayDailyCallback);
            }
            startMenuStage.addActor(leaderboardUI);
            leaderboardUI.bindStage(startMenuStage);
            updateLeaderboardLayout();
            // If the cache fetch completed before the leaderboard was created (common on fast
            // networks), the notification was lost. Replay it now so the loading state resolves.
            if (dailyCache != null && dailyCache.isFetched()) {
                leaderboardUI.notifyCacheReady();
            }
        }

        if (startMenuStage != null) {
            if (Platform.isAndroid()) mobileHUD.syncStartMenu(menuManager, callback, sessions, dailyCache, soundManager);
            if (leaderboardUI != null) {
                leaderboardUI.setVisible(menuManager.getCurrentMenuState() == MainMenuRenderer.MenuState.MAIN);
            }
            startMenuStage.act();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            startMenuStage.draw();
        }
    }

    public void renderPauseMenu(LevelData levelData, GameInputProcessor input, GameSession session, boolean blockInput, String lastShotExport) {
        if (batch.isDrawing()) batch.end();
        if (!blockInput) handlePauseInput(levelData, input, session, lastShotExport);
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        pauseMenuRenderer.render(batch, font, viewport, config, seedFeedbackTimer, shotExportFeedbackTimer, lastShotExport != null, session);
        batch.end();
        if (Platform.isAndroid() && pauseMenuStage != null) {
            pauseMenuStage.act();
            pauseMenuStage.draw();
        }
    }

    private void handlePauseInput(LevelData levelData, GameInputProcessor input, GameSession session, String lastShotExport) {
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_ANIMATION)) config.cycleAnimation();
        if (input.isActionJustPressed(GameInputProcessor.Action.CYCLE_DIFFICULTY)) {
            if (session == null) config.cycleDifficulty();
            else notificationManager.showHazard("DIFFICULTY LOCKED", Color.GRAY, 0.5f);
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.TOGGLE_PARTICLES))
            config.particlesEnabled = !config.particlesEnabled;
        if (input.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) mainMenuRequested = true;
        if (input.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && levelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
            seedFeedbackTimer = 2.0f;
        }
        if (input.isActionJustPressed(GameInputProcessor.Action.EXPORT_SHOT) && lastShotExport != null) {
            Gdx.app.getClipboard().setContents(lastShotExport);
            shotExportFeedbackTimer = 2.0f;
        }
        if (seedFeedbackTimer > 0) seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
        if (shotExportFeedbackTimer > 0) shotExportFeedbackTimer -= Gdx.graphics.getDeltaTime();
    }

    public void renderPlayingHUD(Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, GameSession session, GameInputProcessor input, boolean showClubInfo, ShotController shotController) {
        if (batch.isDrawing()) batch.end();
        float delta = Gdx.graphics.getDeltaTime();
        boolean shouldShowDebug = updateGameplayState(currentClub, ball, isPractice, terrain, session, input, shotController, gameCamera, delta);
        renderGameplay(currentClub, ball, isPractice, levelData, gameCamera, terrain, session, input, showClubInfo, shotController, delta, shouldShowDebug);
    }

    /** Processes input and advances per-frame game state. Returns whether the debug actor should be shown. */
    private boolean updateGameplayState(Club currentClub, Ball ball, boolean isPractice, Terrain terrain,
                                        GameSession session, GameInputProcessor input,
                                        ShotController shotController, Camera gameCamera, float delta) {
        boolean isAndroid = Platform.isAndroid();
        if (isAndroid) {
            if (!mobileHUD.isInitialized()) setupMobileUI((MobileInputProcessor) input);
            this.spinDot.set(spinIndicator.getSpinDot());
        }
        this.lastRenderedClub = currentClub;
        if (!config.swingModeNew) updateSpinInput(delta, input);
        if (isAndroid) mobileHUD.updateButtonStates(currentClub, session, shotController);
        if (input.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE) && config.difficulty.hasRangeFinder()) {
            distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
            distanceDisplayTimer = 3.0f;
        }
        if (isPractice) updatePracticeDistanceLogic(ball);
        terrainToastRenderer.update(delta);
        preShotDebugActor.update(terrain, ball, gameCamera);
        boolean shouldShowDebug = (ball.getState() == Ball.State.STATIONARY);
        preShotDebugActor.setVisible(!isAndroid && shouldShowDebug);
        return shouldShowDebug;
    }

    /** Draws all gameplay HUD elements for the current frame. */
    private void renderGameplay(Club currentClub, Ball ball, boolean isPractice, LevelData levelData,
                                Camera gameCamera, Terrain terrain, GameSession session,
                                GameInputProcessor input, boolean showClubInfo, ShotController shotController,
                                float delta, boolean shouldShowDebug) {
        boolean isAndroid = Platform.isAndroid();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        spinIndicator.updateScaling(viewport);
        batch.begin();
        if (levelData != null && config.difficulty.hasWindicator())
            windRenderer.render(batch, shapeRenderer, font, viewport, levelData.getWind(), gameCamera);
        if (!isAndroid && shouldShowDebug) {
            preShotDebugActor.setBounds(40, 150, 400, 140);
            preShotDebugActor.draw(batch, 1.0f);
        }
        if (!isAndroid && !config.swingModeNew) spinIndicator.draw(batch, 1.0f);
        if (isAndroid) spinIndicator.setVisible(!config.swingModeNew);
        float rangeScale = isAndroid ? 2.8f : 1.8f;
        renderDistanceDisplay(delta, rangeScale);
        if (isPractice) renderShotDistance(ball, isAndroid ? 2.2f : 1.4f);
        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, session, terrain);
        if (session != null && (session.getMode() == GameSession.GameMode.DAILY_PAR3
                             || session.getMode() == GameSession.GameMode.DAILY_PAR4
                             || session.getMode() == GameSession.GameMode.DAILY_PAR5)) {
            holeTimerRenderer.render(batch, font, viewport, session.getElapsedTimeSeconds(), session.isStarted());
        }
        batch.end();
        renderOverlays(currentClub, gameCamera, terrain, input, delta, showClubInfo, shotController);
    }

    public void renderNotifications() {
        if (batch.isDrawing()) batch.end();
        notificationManager.update(Gdx.graphics.getDeltaTime());
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        notificationManager.render(batch, font, viewport);
        batch.end();
        if (terrainToastRenderer.isActive()) {
            terrainToastRenderer.render(batch, font, viewport);
        }
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, GameSession session, Terrain terrain) {
        String soundDebug = soundManager != null ? soundManager.getActiveSoundDebug() : "";
        gameInfoRenderer.render(batch, font, viewport, config, isPractice, levelData, club, ball, session, terrain, shotCount, soundDebug);
    }

    private void updateSpinInput(float delta, GameInputProcessor input) {
        float SPIN_SPEED = 2.0f;
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP))
            spinDot.y = MathUtils.clamp(spinDot.y + SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN))
            spinDot.y = MathUtils.clamp(spinDot.y - SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_LEFT))
            spinDot.x = MathUtils.clamp(spinDot.x - SPIN_SPEED * delta, -1f, 1f);
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_RIGHT))
            spinDot.x = MathUtils.clamp(spinDot.x + SPIN_SPEED * delta, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
        spinIndicator.getSpinDot().set(this.spinDot);
    }

    private void updatePracticeDistanceLogic(Ball ball) {
        distanceTracker.update(Gdx.graphics.getDeltaTime(), ball);
    }

    private void renderOverlays(Club currentClub, Camera gameCamera, Terrain terrain, GameInputProcessor input, float delta, boolean showClubInfo, ShotController shotController) {
        if (mobileHUD.isInitialized() && stage != null && Platform.isAndroid()) {
            stage.act(delta);
            stage.draw();
            if (mobileHUD.isShowingInfo()) {
                renderClubInfo(currentClub);
                mobileHUD.handleInfoClick(input);
            }
            if (!config.swingModeNew && spinIndicator.isBigModeActive()) {
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
        if (!config.swingModeNew && minigameController.isActive()) {
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot, config.animSpeed, config.difficulty, shapeRenderer, batch, font, viewport, input, shotController);
        }
    }

    public void renderClubInfo(Club club) {
        overlayRenderer.renderClubInfo(batch, shapeRenderer, font, viewport, club);
    }

    private void renderShotDistance(Ball ball, float baseScale) {
        boolean isMoving = ball.getState() == Ball.State.AIR || ball.getState() == Ball.State.ROLLING || ball.getState() == Ball.State.CONTACT;
        if (isMoving || distanceTracker.shouldShow()) {
            float distanceToShow = isMoving ? ball.getShotDistance() : distanceTracker.getDisplayDistance();
            float responsiveScale = (viewport.getWorldHeight() * 0.035f) / font.getData().lineHeight;
            font.getData().setScale(responsiveScale * baseScale);
            if (!isMoving) font.setColor(1f, 0.85f, 0f, MathUtils.clamp(distanceTracker.getTimer(), 0, 1));
            else font.setColor(Color.WHITE);
            String text = String.format("DISTANCE: %.1f yds", distanceToShow);
            layout.setText(font, text);
            UIUtils.drawShadowedText(batch, font, text, viewport.getWorldWidth() - layout.width - (viewport.getWorldWidth() * 0.02f), (viewport.getWorldHeight() * 0.2f) + layout.height, font.getColor());
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
            UIUtils.drawShadowedText(batch, font, distanceText, (viewport.getWorldWidth() / 2f) - (layout.width / 2f), viewport.getWorldHeight() - (DISTANCE_TEXT_TOP_OFFSET * (fontSize / 0.6f)), tempDistanceColor.set(1, 1, 0, Math.min(1, distanceDisplayTimer)));
            font.getData().setScale(1.0f);
        }
    }

    public void triggerTerrainToast(com.gearygolf.golf.terrain.Terrain.TerrainType type,
                                     Vector3 ballWorldPos, Camera camera) {
        terrainToastRenderer.trigger(type, ballWorldPos, camera, viewport, config.animSpeed);
    }

    public void dismissTerrainToast() {
        terrainToastRenderer.dismiss();
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

    public void showGameNotification(String text, Color color, float duration) {
        notificationManager.showHazard(text, color, duration);
    }

    public void renderVictory(int shots, LevelData levelData, GameSession session, boolean uploadDone, boolean isTutorial) {
        if (Platform.isAndroid()) mobileHUD.onVictoryShown(isTutorial, session, uploadDone);

        batch.begin();
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, session, uploadDone);
        batch.end();

        if (Platform.isAndroid() && stage != null) {
            stage.act();
            stage.draw();
        }
    }

    public void reset() {
        if (Platform.isAndroid()) mobileHUD.resetForNewHole();
        minigameController.reset();
        distanceDisplayTimer = 0;
        seedFeedbackTimer = 0;
        mainMenuRequested = false;
        spinDot.set(0, 0);
        overlayRenderer.resetScrolls();
    }

    public void renderLoadingScreen() {
        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        viewport.apply();

        float scale = screenH * 0.0035f;
        font.getData().setScale(scale);
        layout.setText(font, "LOADING MAP...");

        float padW = layout.width * 0.5f;
        float padH = layout.height * 1.6f;
        float panelW = layout.width + padW * 2f;
        float panelH = layout.height + padH * 2f;
        float panelX = (screenW - panelW) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float textX = (screenW - layout.width) / 2f;
        float textY = panelY + (panelH + layout.height) / 2f;

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3)
                .draw(batch, panelX, panelY, panelW, panelH);
        font.setColor(Color.WHITE);
        font.draw(batch, "LOADING MAP...", textX, textY);
        font.getData().setScale(1.0f);
        batch.end();
    }

    /**
     * Renders the "ready to launch" overlay for imported shot replay.
     * Ball is stationary at the shot position; user fires it manually.
     */
    public void renderShotReplayReadyHUD(ShotExportPacket.ShotReplayData replayData) {
        if (batch.isDrawing()) batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        float scale   = screenH * 0.0022f;
        font.getData().setScale(scale * 1.6f);

        // Title
        String title = "-- SHOT REPLAY --";
        layout.setText(font, title);
        font.setColor(Color.YELLOW);
        font.draw(batch, title, (screenW - layout.width) / 2f, screenH * SHOT_REPLAY_TITLE_Y);

        // Archetype + hole
        font.getData().setScale(scale);
        if (replayData != null) {
            String archetypeName = replayData.archetype.name().replace('_', ' ');
            String info = archetypeName + "  |  HOLE " + (replayData.holeIndex + 1);
            layout.setText(font, info);
            font.setColor(Color.WHITE);
            font.draw(batch, info, (screenW - layout.width) / 2f, screenH * SHOT_REPLAY_INFO_Y);
        }

        // Launch prompt
        font.getData().setScale(scale * 1.1f);
        String prompt = Platform.isAndroid() ? "TAP THE SCREEN TO LAUNCH" : "PRESS [SPACE] TO LAUNCH";
        layout.setText(font, prompt);
        font.setColor(new Color(0.4f, 1f, 0.4f, 1f));
        font.draw(batch, prompt, (screenW - layout.width) / 2f, screenH * SHOT_REPLAY_PROMPT_Y);

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        batch.end();
    }

    /**
     * Renders the determinism self-test overlay.
     * phase 0-3 = test in progress; phase 4 = result ready.
     */
    public void showDetermTestOverlay(String statusText, int phase) {
        float screenW = viewport.getWorldWidth();
        float screenH = viewport.getWorldHeight();
        viewport.apply();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        float scale = screenH * 0.0028f;
        font.getData().setScale(scale);

        boolean complete = phase >= 4;
        String title = complete ? "DETERM TEST - COMPLETE" : "DETERM TEST - RUNNING...";

        float panelW = screenW * 0.82f;
        float panelH = screenH * 0.48f;
        float panelX = (screenW - panelW) / 2f;
        float panelY = (screenH - panelH) / 2f;
        float pad    = screenH * 0.035f;
        float textX  = panelX + pad;
        float wrapW  = panelW - pad * 2f;

        UIUtils.createGoldBorderedPanel(new Color(0.04f, 0.04f, 0.04f, 0.97f), 3)
                .draw(batch, panelX, panelY, panelW, panelH);

        layout.setText(font, title);
        float titleLineH = layout.height * 2.0f;
        float curY = panelY + panelH - pad;

        font.setColor(Color.YELLOW);
        font.draw(batch, title, textX, curY);
        curY -= titleLineH;

        // Draw a separator line
        batch.end();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.5f, 0.5f, 0.1f, 1f);
        shapeRenderer.rect(textX, curY - pad * 0.2f, wrapW, 2f);
        shapeRenderer.end();
        curY -= pad * 0.6f;
        batch.begin();

        // Render each line with colour-coding
        float bodyScale = scale * 0.78f;
        font.getData().setScale(bodyScale);
        layout.setText(font, "X");
        float bodyLineH = layout.height * 1.9f;

        String[] lines = statusText.split("\n");
        for (String line : lines) {
            if (line.trim().isEmpty()) { curY -= bodyLineH * 0.5f; continue; }
            if (line.contains("PASS"))           font.setColor(Color.GREEN);
            else if (line.contains("FAIL"))      font.setColor(new Color(1f, 0.35f, 0.35f, 1f));
            else if (line.startsWith("["))       font.setColor(Color.GRAY);
            else                                 font.setColor(Color.WHITE);
            font.draw(batch, line, textX, curY, wrapW, com.badlogic.gdx.utils.Align.left, true);
            // Measure actual drawn height for wrapping
            layout.setText(font, line, font.getColor(), wrapW, com.badlogic.gdx.utils.Align.left, true);
            curY -= Math.max(bodyLineH, layout.height + bodyLineH * 0.25f);
        }

        font.getData().setScale(1.0f);
        batch.end();
    }

    public void renderSoundSettings(com.gearygolf.golf.GameConfig config) {
        if (batch.isDrawing()) batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        soundSettingsRenderer.render(batch, shapeRenderer, font, viewport, soundManager, config);
    }

    public boolean isTouchInsideSoundSettings(float x, float y) {
        return soundSettingsRenderer.isClickInside(x, y);
    }

    public void renderProfileScreen(com.gearygolf.golf.scoreBoard.RoundHistoryService service,
                                    String uid, String idToken) {
        if (batch.isDrawing()) batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        profileRenderer.load(service, uid, idToken);
        profileRenderer.render(batch, shapeRenderer, font, viewport);
    }

    public void resetProfileScreen() {
        profileRenderer.reset();
    }

    public boolean isTouchInsideProfileScreen(float x, float y) {
        return profileRenderer.isClickInside(x, y);
    }

    /** Forwards a click to the profile screen. Returns true if consumed, false if outside (should close). */
    public boolean handleProfileClick(float x, float y) {
        return profileRenderer.handleClick(x, y);
    }

    /** Handles back action within profile screen. Returns true if navigated internally (don't close). */
    public boolean handleProfileBack() {
        return profileRenderer.handleBack();
    }

    public void renderRankInfo() {
        if (batch.isDrawing()) batch.end();
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.enableBlending();
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        rankInfoRenderer.render(batch, shapeRenderer, font, viewport, mainMenuRenderer.getBadge());
    }

    public boolean isTouchInsideRankInfo(float x, float y) {
        return rankInfoRenderer.isClickInside(x, y);
    }

    public boolean isBadgeTouched(float x, float y) {
        float r = mainMenuRenderer.getBadgeRadius();
        if (r <= 0) return false;
        float dx = x - mainMenuRenderer.getBadgeCX();
        float dy = y - mainMenuRenderer.getBadgeCY();
        return dx * dx + dy * dy <= r * r;
    }

public void renderInstructions(GameInputProcessor input) {
        overlayRenderer.renderInstructions(batch, shapeRenderer, font, viewport, input, () -> {
        });
    }

    public void renderCameraConfig(GameInputProcessor input) {
        overlayRenderer.renderCameraConfig(batch, shapeRenderer, font, viewport, config, input, () -> {
        });
    }

    public void resetCameraConfigScroll() {
        overlayRenderer.getCameraConfigRenderer().resetScroll();
    }

    public boolean isTouchInsideCameraConfig(float x, float y) {
        return overlayRenderer.getCameraConfigRenderer().isClickInside(x, y);
    }

    public void resetInstructionScroll() {
        overlayRenderer.getInstructionRenderer().resetScroll();
    }

    public boolean isTouchInsideInstructions(float x, float y) {
        return overlayRenderer.getInstructionRenderer().isClickInside(x, y);
    }

    public boolean isTouchInsideClubInfo(float x, float y) {
        if (Platform.isAndroid()) return mobileHUD.isTouchInsideClubInfo(x, y);
        float width  = viewport.getWorldWidth()  * 0.25f;
        float height = viewport.getWorldHeight() * 0.25f;
        float boxX   = viewport.getWorldWidth()  - width - viewport.getWorldWidth() * 0.016f;
        float boxY   = viewport.getWorldHeight() * 0.175f;
        return x >= boxX && x <= boxX + width && y >= boxY && y <= boxY + height;
    }

    public void setClubInfoVisible(boolean visible) {
        if (Platform.isAndroid()) mobileHUD.setClubInfoVisible(visible);
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

    public void showImportRetryOverlay(Runnable onRetry, Runnable onDismiss) {
        hideImportRetryOverlay();

        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        float panelW = w * 0.422f, panelH = h * 0.292f;

        // Root group — contains dimmer and panel as siblings (no event bubbling between siblings)
        Group root = new Group();
        root.setSize(w, h);

        // Dimmer: full-screen Image — tap anywhere on it to dismiss
        Image dimmer = new Image(UIUtils.createRoundedRectDrawable(new Color(0, 0, 0, 0.6f), 0));
        dimmer.setSize(w, h);
        dimmer.addListener(new InputListener() {
            @Override public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                // Defer removal to after the touch event is fully processed so the stage
                // retains focus on the dimmer and the gesture detector never sees the touchUp.
                Gdx.app.postRunnable(onDismiss);
                return true;
            }
        });
        root.addActor(dimmer);

        // Panel: Touchable.enabled so it catches all taps in its area (blocks dimmer below)
        Table panel = new Table();
        panel.setBackground(UIUtils.createRoundedRectDrawable(new Color(0.1f, 0.1f, 0.1f, 0.97f), 12));
        panel.setTouchable(Touchable.enabled);
        panel.setSize(panelW, panelH);
        panel.setPosition(w / 2f - panelW / 2f, h / 2f - panelH / 2f);

        Label.LabelStyle ls = new Label.LabelStyle(font, Color.RED);
        Label label = new Label("NO VALID SHOT ON CLIPBOARD", ls);
        float labelScale = UIUtils.fitFontScale(font, layout, "NO VALID SHOT ON CLIPBOARD", 0.45f, panelW * 0.85f);
        label.setFontScale(labelScale);
        label.setAlignment(Align.center);

        TextButton.TextButtonStyle btnStyle = new TextButton.TextButtonStyle();
        btnStyle.font = font;
        btnStyle.fontColor = Color.WHITE;
        Color btnBase = new Color(0.38f, 0.26f, 0.14f, 0.95f);
        btnStyle.up   = UIUtils.createRaisedButtonDrawable(btnBase, 12, 5);
        btnStyle.down = UIUtils.createRoundedRectDrawable(new Color(0.20f, 0.14f, 0.07f, 1f), 12);
        TextButton retryBtn = new TextButton("RETRY", btnStyle);
        retryBtn.getLabel().setFontScale(1.2f);
        retryBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { onRetry.run(); }
        });

        panel.pad(panelH * 0.133f);
        panel.add(label).padBottom(panelH * 0.086f).row();
        panel.add(retryBtn).width(panelW * 0.63f).height(panelH * 0.371f);

        root.addActor(panel);
        importRetryOverlay = root;
        startMenuStage.addActor(root);
        Table startMenuTable = mobileHUD.getStartMenuTable();
        if (startMenuTable != null) startMenuTable.setTouchable(Touchable.disabled);
    }

    public void hideImportRetryOverlay() {
        if (importRetryOverlay != null) {
            importRetryOverlay.remove();
            importRetryOverlay = null;
        }
        Table startMenuTable = mobileHUD.getStartMenuTable();
        if (startMenuTable != null) startMenuTable.setTouchable(Touchable.childrenOnly);
    }

    public MinigameResult getMinigameResult() {
        return minigameController.getResult();
    }

    public Vector2 getSpinOffset() {
        return spinDot;
    }

    public boolean isSpinBigMode() { return spinIndicator.isBigModeActive(); }
    public void setSpinBigMode(boolean active) { spinIndicator.setBigModeActive(active); }

    public void resetSpin() {
        this.spinDot.set(0, 0);
        spinIndicator.reset();
    }

    public SwingUIController getSwingUIController() {
        return swingUIController;
    }

    public Stage getStage() {
        return stage;
    }

    public Stage getStartMenuStage() {
        return startMenuStage;
    }

    public Stage getPauseMenuStage() {
        return pauseMenuStage;
    }

    public void invalidateMobileMenuState() {
        mobileHUD.invalidateMenuState();
    }

    private Skin initSkin() {
        Skin s;
        try {
            s = new Skin(Gdx.files.internal("ui/uiskin.json"));
        } catch (Exception e) {
            s = new Skin();
        }
        UIUtils.registerDefaultStyles(s, font);
        if (!s.has("default-font", BitmapFont.class)) s.add("default-font", font);
        if (!s.has("default", TextButton.TextButtonStyle.class)) {
            TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
            style.font = s.getFont("default-font");
            style.fontColor = Color.WHITE;
            style.downFontColor = Color.GRAY;
            if (s.has("default-round", Drawable.class)) {
                style.up = s.getDrawable("default-round");
                style.down = s.getDrawable("default-round-down");
            } else {
                style.up = UIUtils.createRoundedRectDrawable(Color.DARK_GRAY, 6);
                style.down = UIUtils.createRoundedRectDrawable(Color.LIGHT_GRAY, 6);
            }
            s.add("default", style);
        }
        return s;
    }

    public Skin getSkin() {
        return skin;
    }

    /**
     * Records the logged-in user's display name so the main menu can show it.
     */
    public void setSkillRating(String rating) {
        mainMenuRenderer.setSkillRating(rating);
    }

    public void setLoggedInUser(String displayName) {
        mainMenuRenderer.setLoggedInUser(displayName);
    }

    /**
     * Stores a callback invoked when the user presses a "PLAY DAILY PAR X" button
     * in the leaderboard. Applied immediately if the leaderboard already exists,
     * or deferred and applied at lazy-creation time.
     */
    public void setLeaderboardPlayDailyCallback(java.util.function.Consumer<GameSession.GameMode> callback) {
        this.leaderboardPlayDailyCallback = callback;
        if (leaderboardUI != null) leaderboardUI.setPlayDailyCallback(callback);
    }

    /**
     * Called when DailySubmissionCache finishes its async fetch so the leaderboard
     * can re-evaluate any gated par tabs that were showing "Loading...".
     */
    public void notifyLeaderboardCacheReady() {
        if (leaderboardUI != null) leaderboardUI.notifyCacheReady();
    }

    /** Triggers a full leaderboard refresh (re-fetches counts and scores). */
    public void refreshLeaderboard() {
        if (leaderboardUI != null) leaderboardUI.refresh();
    }

    /**
     * Shows a brief toast notification on the start-menu stage (fades in, holds, fades out).
     */
    public void showToast(String message) {
        if (startMenuStage == null) return;

        Label.LabelStyle style = new Label.LabelStyle(font, Color.WHITE);
        Label label = new Label(message, style);
        label.setFontScale(0.55f);

        Table box = new Table();
        box.setBackground(UIUtils.createRoundedRectDrawable(new Color(0f, 0f, 0f, 0.75f), 8));
        box.add(label).pad(16, 32, 16, 32);

        Table toast = new Table();
        toast.setFillParent(true);
        toast.bottom().padBottom(startMenuStage.getViewport().getWorldHeight() * 0.12f);
        toast.add(box);

        toast.getColor().a = 0f;
        toast.addAction(Actions.sequence(
                Actions.fadeIn(0.3f),
                Actions.delay(2.5f),
                Actions.fadeOut(0.5f),
                Actions.removeActor()
        ));

        startMenuStage.addActor(toast);
    }

    /**
     * Computes the screen bounds of the RANGE distance display (top-centre text).
     * Returns null when the display is not currently visible.
     */
    private com.badlogic.gdx.math.Rectangle getRangeDisplayBounds() {
        if (distanceDisplayTimer <= 0 || distanceText == null || distanceText.isEmpty()) return null;
        float rangeScale = Platform.isAndroid() ? 2.8f : 1.8f;
        float fontSize = rangeScale * 0.6f;
        font.getData().setScale(fontSize);
        layout.setText(font, distanceText);
        float tw = layout.width;
        float th = layout.height;
        font.getData().setScale(1.0f);
        float w = viewport.getWorldWidth();
        float h = viewport.getWorldHeight();
        float textX = w / 2f - tw / 2f;
        float textY = h - DISTANCE_TEXT_TOP_OFFSET * (fontSize / 0.6f); // ascent line
        float pad = 8f;
        return new com.badlogic.gdx.math.Rectangle(textX - pad, textY - th - pad, tw + pad * 2f, th + pad * 2f);
    }

    /**
     * Computes the screen bounds of the club-name text drawn bottom-right by GameInfoRenderer.
     * Returns null when no club has been rendered yet.
     */
    private com.badlogic.gdx.math.Rectangle getClubNameDisplayBounds() {
        if (lastRenderedClub == null) return null;
        float h = viewport.getWorldHeight();
        float w = viewport.getWorldWidth();
        boolean isAndroid = Platform.isAndroid();
        float baseScale = (h * 0.0015f) * (isAndroid ? 1.5f : 1.0f);
        float lineSpacing = h * (isAndroid ? 0.06f : 0.045f);
        float bottomY = h * 0.06f;
        float clubYOffset = isAndroid ? (lineSpacing * 2.05f) : (lineSpacing * 1.8f);
        float textY = bottomY + clubYOffset; // ascent line
        font.getData().setScale(baseScale);
        String clubName = lastRenderedClub.name().replace("_", " ");
        layout.setText(font, clubName);
        float tw = layout.width;
        float th = layout.height;
        font.getData().setScale(1.0f);
        float rightX = w * 0.98f;
        float pad = 6f;
        return new com.badlogic.gdx.math.Rectangle(rightX - tw - pad, textY - th - pad, tw + pad * 2f, th + pad * 2f);
    }

    /**
     * Draws the tutorial step overlay on top of the gameplay UI.
     *
     * @param wind Current level wind (for aim-hint text), may be null.
     */
    public void renderTutorialOverlay(TutorialController.Step step, com.badlogic.gdx.math.Vector3 wind, float aimYawDelta) {
        com.badlogic.gdx.math.Rectangle highlightBounds = tutorialCoordinator.getTutorialHighlightBounds(step, getClubNameDisplayBounds());
        com.badlogic.gdx.math.Rectangle rangeBounds =
                (step == TutorialController.Step.STEP_2_AIM) ? getRangeDisplayBounds() : null;
        tutorialCoordinator.renderOverlay(step, wind, aimYawDelta, highlightBounds, rangeBounds);
    }

    public boolean isNextButtonHit() {
        return tutorialCoordinator.isNextButtonHit();
    }

    public com.badlogic.gdx.math.Rectangle getTutorialHighlightBounds(TutorialController.Step step) {
        return tutorialCoordinator.getTutorialHighlightBounds(step, getClubNameDisplayBounds());
    }

    public boolean consumeInfoToggled() {
        return Platform.isAndroid() && mobileHUD.consumeInfoToggled();
    }

    public void clearTutorialBlock() {
        tutorialCoordinator.clearTutorialBlock();
    }

    public void applyTutorialButtonBlock(TutorialController.Step step) {
        tutorialCoordinator.applyTutorialButtonBlock(step);
    }

    public void updateLiveScoreboard(java.util.List<LiveScoreboardActor.ScoreEntry> entries) {
        mobileHUD.updateLiveScoreboard(entries);
    }

    private static final com.badlogic.gdx.graphics.Color[] REMOTE_TAG_COLORS = {
        new com.badlogic.gdx.graphics.Color(1f,    0.45f, 0.75f, 1f), // pink
        new com.badlogic.gdx.graphics.Color(0.72f, 0.35f, 1f,   1f), // purple
        new com.badlogic.gdx.graphics.Color(0.3f,  1f,   0.45f, 1f), // green
        new com.badlogic.gdx.graphics.Color(1f,    0.95f, 0.3f,  1f), // yellow
    };

    /**
     * Draws a username tag above each remote ball. Call after all other HUD rendering.
     * Tags fade from fully opaque within 10 units to a 0.15 floor at 200 units;
     * always solid in overhead mode. Hidden when the ball is holed out.
     */
    public void drawRemoteNameTags(java.util.Collection<com.gearygolf.golf.multiplayer.RemoteBall> remoteBalls,
                                   com.badlogic.gdx.graphics.Camera camera,
                                   boolean isOverhead) {
        if (remoteBalls == null || remoteBalls.isEmpty()) return;

        final float PAD = 4f * UI_SCALE;

        // Collect visible tags first so we can do boxes and text in separate passes
        // (ShapeRenderer and SpriteBatch can't overlap).
        int max = remoteBalls.size();
        float[] tx = new float[max], ty = new float[max];
        float[] tw = new float[max], th = new float[max];
        float[] ta = new float[max], ts = new float[max];
        String[] tn = new String[max];
        com.badlogic.gdx.graphics.Color[] tc = new com.badlogic.gdx.graphics.Color[max];
        int count = 0;
        int colorIdx = 0;

        viewport.apply();

        for (com.gearygolf.golf.multiplayer.RemoteBall rb : remoteBalls) {
            com.badlogic.gdx.graphics.Color col = REMOTE_TAG_COLORS[colorIdx % REMOTE_TAG_COLORS.length];
            colorIdx++;
            if (!rb.hasRestPosition() || rb.isHoledOut()) continue;
            String name = rb.displayName;
            if (name == null || name.isEmpty()) continue;

            com.badlogic.gdx.math.Vector3 ballPos = rb.isInFlight() ? rb.getPosition() : rb.getLastRestPosition();
            // Project the ball itself — no world-space offset so distant/close labels
            // both land near the ball on screen.
            tempV3.set(ballPos);
            camera.project(tempV3);
            if (tempV3.z > 1f) continue;

            float hudX = tempV3.x / Gdx.graphics.getWidth()  * viewport.getWorldWidth();
            float hudY = tempV3.y / Gdx.graphics.getHeight() * viewport.getWorldHeight();

            float alpha;
            if (isOverhead) {
                alpha = 1f;
            } else {
                float t = com.badlogic.gdx.math.MathUtils.clamp((camera.position.dst(ballPos) - 10f) / 190f, 0f, 1f);
                alpha = 1f - t * 0.85f;
            }

            // Scale font with distance so the label stays visually proportional to the ball.
            float dist = camera.position.dst(ballPos);
            float fontScale = com.badlogic.gdx.math.MathUtils.clamp(60f / dist, 0.3f, 1.0f) * UI_SCALE;
            font.getData().setScale(fontScale);
            layout.setText(font, name);

            // Fixed screen-space gap above the projected ball position.
            float screenGap = 8f * UI_SCALE;
            tx[count] = hudX - layout.width / 2f;
            ty[count] = hudY + layout.height + screenGap;
            tw[count] = layout.width;
            th[count] = layout.height;
            ta[count] = alpha;
            ts[count] = fontScale;
            tn[count] = name;
            tc[count] = col;
            count++;
        }

        if (count == 0) { font.getData().setScale(1f); return; }

        // Pass 1 — semi-transparent background boxes via ShapeRenderer
        if (batch.isDrawing()) batch.end();
        com.badlogic.gdx.Gdx.gl.glEnable(com.badlogic.gdx.graphics.GL20.GL_BLEND);
        com.badlogic.gdx.Gdx.gl.glBlendFunc(com.badlogic.gdx.graphics.GL20.GL_SRC_ALPHA,
                com.badlogic.gdx.graphics.GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(com.badlogic.gdx.graphics.glutils.ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < count; i++) {
            shapeRenderer.setColor(0f, 0f, 0f, 0.45f * ta[i]);
            shapeRenderer.rect(tx[i] - PAD, ty[i] - th[i] - PAD,
                               tw[i] + PAD * 2f, th[i] + PAD * 2f);
        }
        shapeRenderer.end();
        com.badlogic.gdx.Gdx.gl.glDisable(com.badlogic.gdx.graphics.GL20.GL_BLEND);

        // Pass 2 — coloured text via SpriteBatch
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        for (int i = 0; i < count; i++) {
            font.getData().setScale(ts[i]);
            com.badlogic.gdx.graphics.Color col = tc[i];
            font.setColor(col.r, col.g, col.b, ta[i]);
            font.draw(batch, tn[i], tx[i], ty[i]);
        }
        batch.end();

        font.getData().setScale(1f);
        font.setColor(com.badlogic.gdx.graphics.Color.WHITE);
    }

    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
        spinIndicator.dispose();
        if (stage != null) stage.dispose();
        if (startMenuStage != null) startMenuStage.dispose();
        if (pauseMenuStage != null) pauseMenuStage.dispose();
        if (skin != null) skin.dispose();
    }
}
