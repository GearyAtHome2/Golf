package org.example.hud;

import com.badlogic.gdx.Application;
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
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.GameConfig;
import org.example.ball.Ball;
import org.example.ball.MinigameResult;
import org.example.ball.ShotController;
import org.example.ball.ShotDifficulty;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
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
    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private final GameConfig config;
    private final MinigameController minigameController = new MinigameController();
    private final VictoryRenderer victoryRenderer = new VictoryRenderer();
    private com.badlogic.gdx.scenes.scene2d.ui.Label mobileClubLabel;
    private final WindIndicatorRenderer windRenderer = new WindIndicatorRenderer();
    private final GameInfoRenderer gameInfoRenderer = new GameInfoRenderer();
    private final PauseMenuRenderer pauseMenuRenderer = new PauseMenuRenderer();
    private final OverlayRenderer overlayRenderer = new OverlayRenderer();
    private final NotificationManager notificationManager = new NotificationManager();
    private final ShotDistanceTracker distanceTracker = new ShotDistanceTracker();
    private final HoleTimerRenderer holeTimerRenderer = new HoleTimerRenderer();
    private Table startMenuTable;
    private boolean mobileUIInitialized = false;
    private MobileUIFactory.MobileUIPackage mobileUIPackage;
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

        this.viewport = new ExtendViewport(1280, 720);
        this.stage = new Stage(viewport, batch);
        this.startMenuStage = new Stage(viewport, batch);
        this.pauseMenuStage = new Stage(viewport, batch);

this.skin = getSkin();
        this.spinIndicator = new SpinIndicator(shapeRenderer, font);
        this.preShotDebugActor = new PreShotDebugActor(font);
        this.minigameController.setNotificationManager(this.notificationManager);
        this.minigameController.setOnShotFinalized(this::incrementShots);
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
        if (startMenuStage != null) startMenuStage.getViewport().update(width, height, true);
        if (pauseMenuStage != null) pauseMenuStage.getViewport().update(width, height, true);
        if (stage != null) stage.getViewport().update(width, height, true);
        updateLeaderboardLayout();
    }

    private void updateLeaderboardLayout() {
        if (leaderboardUI == null) return;

        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float screenW = startMenuStage.getViewport().getWorldWidth();
        float screenH = startMenuStage.getViewport().getWorldHeight();

        float menuWidth = 620f;
        float minGap = 20f;
        float edgePadding = 15f;

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

        if (startMenuTable != null) {
            startMenuTable.setTransform(true);
            startMenuTable.setScale(squeeze);
        }
    }

    public void setupMobileUI(MobileInputProcessor input) {
        if (mobileUIInitialized) return;
        this.mobileUIPackage = MobileUIFactory.create(
                viewport, batch, font, config, input, spinIndicator, preShotDebugActor
        );
        this.stage = mobileUIPackage.stage;
        this.startMenuStage = mobileUIPackage.startMenuStage;
        this.pauseMenuStage = mobileUIPackage.pauseMenuStage;
        this.gameplayTable = mobileUIPackage.gameplayTable;
        this.victoryTable = mobileUIPackage.victoryTable;
        this.infoToggleBtn = mobileUIPackage.infoToggleBtn;
        this.skin = mobileUIPackage.skin;
        this.startMenuTable = mobileUIPackage.startMenuTable;
        this.mobileClubLabel = mobileUIPackage.clubLabel;

        this.infoToggleBtn.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                showInfoDisplay = !showInfoDisplay;
                infoToggleBtn.setVisible(!showInfoDisplay);
            }
        });
        mobileUIInitialized = true;
    }

    public void renderStartMenu(MenuManager menuManager, MenuManager.MenuHandler callback, CompetitiveSessions sessions) {
        viewport.apply();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        mainMenuRenderer.render(batch, font, viewport, menuManager.getMenuSelection(), menuManager.getCurrentMenuState(), sessions, menuManager.getMapScrollOffset());
        batch.end();

        if (leaderboardUI == null && startMenuStage != null) {
            leaderboardUI = new LeaderboardUI(getSkin(), highscoreService);
            startMenuStage.addActor(leaderboardUI);
            leaderboardUI.bindStage(startMenuStage);
            updateLeaderboardLayout();
        }

        if (startMenuStage != null) {
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
                if (lastMobileMenuState != menuManager.getCurrentMenuState()) {
                    setupMobileStartMenu(menuManager, callback, sessions);
                    lastMobileMenuState = menuManager.getCurrentMenuState();
                }
            }
            if (leaderboardUI != null) {
                leaderboardUI.setVisible(menuManager.getCurrentMenuState() == MainMenuRenderer.MenuState.MAIN);
            }
            startMenuStage.act();
            batch.setBlendFunction(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
            startMenuStage.draw();
        }
    }

    private void setupMobileStartMenu(MenuManager menuManager, MenuManager.MenuHandler callback, CompetitiveSessions sessions) {
        if (startMenuTable == null) return;
        startMenuTable.clearChildren();
        MobileUIFactory.buildStartMenuButtons(startMenuTable, menuManager, callback, sessions, viewport, font);
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
        if (session != null && session.getMode() == GameSession.GameMode.DAILY_1) {
            holeTimerRenderer.render(batch, font, viewport, session.getElapsedTimeSeconds(), session.isStarted());
        }
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
            UIUtils.drawShadowedText(batch, font, distanceText, (viewport.getWorldWidth() / 2f) - (layout.width / 2f), viewport.getWorldHeight() - (40 * (fontSize / 0.6f)), new Color(1, 1, 0, Math.min(1, distanceDisplayTimer)));
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
        if (infoToggleBtn != null) infoToggleBtn.setVisible(false);
        if (victoryTable != null) victoryTable.setVisible(true);
        if (mobileUIPackage != null && mobileUIPackage.arrowContainer != null) mobileUIPackage.arrowContainer.setVisible(false);

        if (mobileUIPackage != null && Gdx.app.getType() == Application.ApplicationType.Android) {
            boolean isFinished = (session != null && session.isFinished());
            boolean isDaily = (session != null && isDailyMode(session.getMode()));
            mobileUIPackage.nextLevelBtn.setVisible(!isFinished);
            mobileUIPackage.submitScoreBtn.setVisible(isFinished && isDaily);
            mobileUIPackage.mainMenuBtn.setVisible(isFinished);
        }

        batch.begin();
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, session);
        batch.end();

        if (Gdx.app.getType() == Application.ApplicationType.Android && stage != null) {
            stage.act();
            stage.draw();
        }
    }

    private boolean isDailyMode(GameSession.GameMode mode) {
        return mode == GameSession.GameMode.DAILY_18 || mode == GameSession.GameMode.DAILY_9 || mode == GameSession.GameMode.DAILY_1;
    }

    public void reset() {
        if (gameplayTable != null) gameplayTable.setVisible(true);
        if (victoryTable != null) victoryTable.setVisible(false);
        if (mobileUIPackage != null && mobileUIPackage.arrowContainer != null) mobileUIPackage.arrowContainer.setVisible(true);
        showInfoDisplay = false;
        if (infoToggleBtn != null) infoToggleBtn.setVisible(true);
        minigameController.reset();
        distanceDisplayTimer = 0;
        seedFeedbackTimer = 0;
        mainMenuRequested = false;
        spinDot.set(0, 0);
        overlayRenderer.resetScrolls();
    }

    public void renderInstructions(GameInputProcessor input) {
        overlayRenderer.renderInstructions(batch, shapeRenderer, font, viewport, input, () -> {});
    }

    public void renderCameraConfig(GameInputProcessor input) {
        overlayRenderer.renderCameraConfig(batch, shapeRenderer, font, viewport, config, input, () -> {});
    }

    public void resetCameraConfigScroll() { overlayRenderer.getCameraConfigRenderer().resetScroll(); }
    public boolean isTouchInsideCameraConfig(float x, float y) {
        return overlayRenderer.getCameraConfigRenderer().isClickInside(x, y);
    }
    public void resetInstructionScroll() { overlayRenderer.getInstructionRenderer().resetScroll(); }
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
        spinIndicator.reset();
    }

    public Stage getStage() { return stage; }
    public Stage getStartMenuStage() { return startMenuStage; }
    public Stage getPauseMenuStage() { return pauseMenuStage; }

    public Skin getSkin() {
        if (skin == null) {
            try { skin = new Skin(Gdx.files.internal("ui/uiskin.json")); }
            catch (Exception e) { skin = new Skin(); }
        }
        UIUtils.registerDefaultStyles(skin, font);
        if (!skin.has("default-font", BitmapFont.class)) skin.add("default-font", font);
        if (!skin.has("default", TextButton.TextButtonStyle.class)) {
            TextButton.TextButtonStyle style = new TextButton.TextButtonStyle();
            style.font = skin.getFont("default-font");
            style.fontColor = Color.WHITE;
            style.downFontColor = Color.GRAY;
            if (skin.has("default-round", Drawable.class)) {
                style.up = skin.getDrawable("default-round");
                style.down = skin.getDrawable("default-round-down");
            } else {
                style.up = UIUtils.createRoundedRectDrawable(Color.DARK_GRAY, 6);
                style.down = UIUtils.createRoundedRectDrawable(Color.LIGHT_GRAY, 6);
            }
            skin.add("default", style);
        }
        return skin;
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
