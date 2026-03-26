package org.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ball.Ball;
import org.example.ball.ShotController;
import org.example.camera.CameraController;
import org.example.gameManagers.*;
import org.example.glamour.ParticleManager;
import org.example.glamour.WindManager;
import org.example.hud.HUD;
import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.input.DesktopInputProcessor;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.performance.PhysicsProfiler;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.example.terrain.level.LevelFactory;

import java.util.Calendar;

public class GolfGame extends ApplicationAdapter implements MenuManager.MenuHandler, HazardManager.HazardListener {

    private enum GameState {START, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS, CAMERA_CONFIG}

    private GameState currentState = GameState.START;
    private GameState previousState = GameState.PLAYING;
    private GameState returnState = GameState.PLAYING;

    private GhostManager ghostManager;
    private MenuManager menuManager;
    private HazardManager hazardManager;

    private PerspectiveCamera camera;
    private Viewport gameViewport;
    private ModelBatch modelBatch;
    private Environment environment;

    private LevelManager levelManager;
    private LevelFactory levelFactory;
    private Ball ball;
    private final GameConfig config = new GameConfig();
    private CameraController cameraController;
    private HUD hud;
    private GameInputProcessor inputProcessor;
    private ShotController shotController;
    private LevelData currentLevelData;

    private Club currentClub = Club.DRIVER;
    private ParticleManager particleManager;
    private WindManager windManager;
    private boolean isVictory = false;
    private boolean showClubInfo = false;

    private Model highlightModel;
    private ModelInstance highlightInstance;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);

    private GameSession activeSession;
    private GameSession standardSession;
    private GameSession dailySession;
    private final Vector3 tempV3 = new Vector3();

    @Override
    public void create() {
        try {
            setupCamera();
            setupEnvironment();
            modelBatch = new ModelBatch();
            hud = new HUD(config);
            levelManager = new LevelManager();
            levelFactory = new LevelFactory();
            ghostManager = new GhostManager(8);
            menuManager = new MenuManager();
            hazardManager = new HazardManager();
            shotController = new ShotController();
            particleManager = new ParticleManager();
            windManager = new WindManager();

            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
                inputProcessor = new MobileInputProcessor();
                hud.setupMobileUI((MobileInputProcessor) inputProcessor);
            } else {
                inputProcessor = new DesktopInputProcessor();
            }

            this.standardSession = SessionPersistence.loadSession(GameSession.GameMode.STANDARD_18);
            if (standardSession != null) {
                standardSession.setSaveCallback(() -> SessionPersistence.saveSession(standardSession));
            }

            this.dailySession = SessionPersistence.loadSession(GameSession.GameMode.DAILY_CHALLENGE);
            if (dailySession != null) {
                dailySession.setSaveCallback(() -> SessionPersistence.saveSession(dailySession));
            }

            setupHighlight();
            setupInputProcessor();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void resize(int width, int height) {
        if (gameViewport != null) gameViewport.update(width, height, true);
        if (hud != null) hud.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hud.dispose();
        shotController.dispose();
        levelManager.dispose();
        ghostManager.dispose();
        particleManager.dispose();
        if (ball != null) ball.dispose();
        if (highlightModel != null) highlightModel.dispose();
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.4f;
        camera.far = 950f;
        gameViewport = new ExtendViewport(1280, 720, camera);
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));
        Gdx.gl.glClearColor(0.53f, 0.81f, 0.92f, 1f);
    }

    private void setupHighlight() {
        ModelBuilder mb = new ModelBuilder();
        highlightModel = mb.createSphere(8f, 8f, 8f, 24, 24,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.4f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        highlightInstance = new ModelInstance(highlightModel);
    }

    private void initLevel() {
        initLevel(-1);
    }

    private void initLevel(long manualSeed) {
        if (ball != null) ball.dispose();
        ghostManager.clear();
        shotController.reset();
        hud.reset();

        LevelFactory.GameMode mode = LevelFactory.GameMode.valueOf(currentState.name());
        LevelFactory.LevelCreationResult result;

        if (currentState == GameState.COMPETITIVE && activeSession != null) {
            hud.setActiveSession(activeSession);
            currentLevelData = activeSession.getCourseLayout().get(activeSession.getCurrentHoleIndex());
            ITerrainGenerator generator = new ClassicGenerator(currentLevelData);
            result = new LevelFactory.LevelCreationResult(generator, currentLevelData, Club.DRIVER, currentLevelData.getWaterLevel(), currentLevelData.getDistance());
        } else {
            result = levelFactory.createLevel(mode, manualSeed);
            currentLevelData = result.data;
            hud.resetShots();
        }

        currentClub = result.defaultClub;
        levelManager.buildLevel(result.generator, result.waterLevel, result.distance);
        setupSpawnAndPins();
        setupInputProcessor();
        isVictory = false;
        hazardManager.resetTimer();
        particleManager.clear();
    }

    private void setupSpawnAndPins() {
        Terrain terrain = levelManager.getTerrain();
        Vector3 tee = terrain.getTeePosition();
        Vector3 hole = terrain.getHolePosition();

        if (currentState == GameState.PUTTING_GREEN) {
            terrain.setTeePosition(new Vector3(tee.x, terrain.getHeightAt(tee.x, tee.z), tee.z));
            terrain.setHolePosition(new Vector3(hole.x, terrain.getHeightAt(hole.x, hole.z), hole.z));
            ball = new Ball(new Vector3(tee.x, tee.y + 0.5f, tee.z), particleManager, config);
            ball.setState(Ball.State.AIR);
        } else {
            ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config);
        }

        hazardManager.setBallHit(false);
        refreshCameraController(hole);
    }

    private void refreshCameraController(Vector3 lookAtTarget) {
        cameraController = new CameraController(camera, 12f, ball.getPosition(), config.cameraSettings);
        cameraController.setOrientation(lookAtTarget);
        cameraController.update(ball.getPosition(), inputProcessor);
    }

    private void updateLogic(float delta) {
        Terrain terrain = levelManager.getTerrain();
        if (!isVictory && isGameplayState()) {
            float effDelta = delta * config.getGameSpeed();
            Vector3 currentWind = (currentState == GameState.PUTTING_GREEN || currentLevelData == null) ? zeroWind : currentLevelData.getWind();

            windManager.update(effDelta, currentWind, camera.position);
            if (hud.wasMinigameCanceled()) shotController.reset();

            PhysicsProfiler.startSection("ShotControllerCharge");
            if (ball.getState() == Ball.State.STATIONARY || shotController.isCharging()) {
                if (shotController.update(delta, ball, camera.direction, currentClub, hud, terrain, inputProcessor)) {
                    if (GameState.PRACTICE_RANGE != currentState) hud.resetSpin();
                    hazardManager.setBallHit(true);
                    shotController.update(0, ball, camera.direction, currentClub, hud, terrain, inputProcessor);
                    if (activeSession != null) SessionPersistence.saveSession(activeSession);
                }
            }
            PhysicsProfiler.endSection("ShotControllerCharge");

            ball.update(effDelta, terrain, currentWind);
            cameraController.update(ball.getPosition(), inputProcessor);

            if (config.particlesEnabled) {
                PhysicsProfiler.startSection("ParticleInteractions");
                particleManager.handleBallInteraction(ball, terrain);
                PhysicsProfiler.endSection("ParticleInteractions");
            }

            hazardManager.update(delta, ball, terrain, currentState == GameState.PRACTICE_RANGE, this);
            if (ball.checkVictory(terrain)) triggerVictory();
        }

        particleManager.update(delta, terrain);
        if (terrain != null) {
            PhysicsProfiler.startSection("CameraOcclusion");
            terrain.updateCameraOcclusion(camera.position, ball.getPosition(), delta);
            terrain.updateFlag(camera.position);
            PhysicsProfiler.endSection("CameraOcclusion");
        }
    }

    @Override
    public void onOutOfBounds() {
        hud.showOutOfBounds();
        resetBallToLastShot();
    }

    @Override
    public void onWaterHazard() {
        hud.showWaterHazard();
        resetBallToLastShot();
    }

    @Override
    public void onPracticeReset() {
        ghostManager.archiveBall(ball);
        Vector3 tee = levelManager.getTerrain().getTeePosition();
        ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config);
        hazardManager.setBallHit(false);
    }

    private void resetBallToLastShot() {
        if (ball != null) {
            ball.resetToLastPosition();
            ball.setState(Ball.State.STATIONARY);
            shotController.reset();
            hud.cancelMinigame();
            hazardManager.setBallHit(false);
            isVictory = false;
            hazardManager.resetTimer();
            cameraController.update(ball.getPosition(), inputProcessor);
        }
    }

    private void triggerVictory() {
        Terrain terrain = levelManager.getTerrain();
        isVictory = true;
        float vel = ball.getVelocity().len();
        ball.getVelocity().setZero();
        ball.getPosition().set(terrain.getHolePosition());
        particleManager.spawn(terrain.getHolePosition(), Color.GOLD, 40, vel + 5, 50.0f, 4.0f);

        if (currentState == GameState.COMPETITIVE && activeSession != null) {
            activeSession.getCompetitiveScore().recordStroke(activeSession.getCurrentHoleIndex(), hud.getShotCount());
            activeSession.advanceHole();
            SessionPersistence.saveSession(activeSession);
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        inputProcessor.update(delta);
        handleInput();

        gameViewport.apply();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        boolean showMouse = (currentState == GameState.START || currentState == GameState.PAUSED ||
                currentState == GameState.INSTRUCTIONS || currentState == GameState.CAMERA_CONFIG || isVictory);
        Gdx.input.setCursorCatched(!showMouse);

        if (currentState == GameState.START) {
            hud.renderStartMenu(menuManager, this, standardSession, dailySession);
        } else if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (currentState == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else {
            updateLogic(delta);
            renderScene();
            renderUI();
        }

        if (inputProcessor instanceof MobileInputProcessor) ((MobileInputProcessor) inputProcessor).resetDrags();
    }

    private void renderScene() {
        if (levelManager.getTerrain() == null) return;
        modelBatch.begin(camera);
        levelManager.render(modelBatch, environment);
        ball.render(modelBatch, environment);
        ball.renderTrail(modelBatch, environment);
        ghostManager.render(modelBatch, environment);

        if (cameraController.isOverhead()) {
            highlightInstance.transform.setToTranslation(ball.getPosition());
            modelBatch.render(highlightInstance, environment);
        }

        shotController.render(modelBatch, environment, ball.getPosition(), camera.position);
        particleManager.render(modelBatch, environment);
        modelBatch.end();

        if (currentLevelData != null && !isVictory && currentState != GameState.PUTTING_GREEN) {
            windManager.render(camera, currentLevelData.getWind());
        }
    }

    private void renderUI() {
        boolean isPractice = (currentState == GameState.PRACTICE_RANGE || currentState == GameState.PUTTING_GREEN);
        if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (currentState == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else if (isVictory) {
            hud.renderVictory(hud.getShotCount(), currentLevelData, activeSession);
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu(currentLevelData, inputProcessor, activeSession);
        } else {
            hud.renderPlayingHUD(currentClub, ball, isPractice, currentLevelData, camera, levelManager.getTerrain(), activeSession, inputProcessor, showClubInfo, shotController);
        }
    }

    private void handleInput() {
        GameState oldState = currentState;
        if (hud.wasMainMenuRequested() || isVictoryCourseComplete()) {
            exitToMainMenu();
        } else if (hud.wasInstructionsRequested()) {
            enterInstructions();
        } else if (hud.wasCameraConfigRequested()) {
            enterCameraConfig();
        } else if (currentState == GameState.START) {
            menuManager.handleInput(inputProcessor, this, standardSession, dailySession);
        } else if (currentState == GameState.INSTRUCTIONS || currentState == GameState.CAMERA_CONFIG) {
            handleOverlayInput();
        } else {
            handleGameplayInput();
        }

        if (currentState != oldState && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            setupInputProcessor();
        }
    }

    private void handleGameplayInput() {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            hud.getStage().getViewport().unproject(tempV3);
            if (hud.isTouchInsideClubInfo(tempV3.x, tempV3.y)) {
                showClubInfo = false;
                hud.setClubInfoVisible(false);
            }
        }

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
            float scroll = inputProcessor.getActionValue(GameInputProcessor.Action.SCROLL_Y);
            if (scroll != 0 && !shotController.isCharging()) {
                boolean isMod = inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) ||
                        inputProcessor.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW);
                if (!isMod) {
                    int index = MathUtils.clamp(currentClub.ordinal() + (scroll > 0 ? 1 : -1), 0, Club.values().length - 1);
                    currentClub = Club.values()[index];
                }
            }
        }

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && !shotController.isCharging()) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_UP)) {
                currentClub = Club.values()[MathUtils.clamp(currentClub.ordinal() - 1, 0, Club.values().length - 1)];
            } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_DOWN)) {
                currentClub = Club.values()[MathUtils.clamp(currentClub.ordinal() + 1, 0, Club.values().length - 1)];
            }
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PAUSE)) {
            if (currentState == GameState.PAUSED) {
                currentState = previousState;
                cameraController.setPaused(false);
            } else {
                previousState = currentState;
                currentState = GameState.PAUSED;
                cameraController.setPaused(true);
            }
            cameraController.update(ball.getPosition(), inputProcessor);
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) setupInputProcessor();
        }

        if (currentState != GameState.PAUSED && currentState != GameState.INSTRUCTIONS && currentState != GameState.CAMERA_CONFIG) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) showClubInfo = !showClubInfo;
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.RESET_BALL)) resetBallToLastShot();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.NEW_LEVEL)) handleNewLevelInput();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_UP)) config.adjustGameSpeed(true);
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_DOWN)) config.adjustGameSpeed(false);
    }

    private void handleNewLevelInput() {
        if (currentState == GameState.COMPETITIVE || (currentState == GameState.PAUSED && previousState == GameState.COMPETITIVE)) {
            if (isVictory && activeSession != null && activeSession.getCurrentHoleIndex() < activeSession.getCourseLayout().size()) {
                SessionPersistence.saveSession(activeSession);
                currentState = GameState.COMPETITIVE;
                initLevel();
            }
        } else {
            if (currentState == GameState.PAUSED) currentState = previousState;
            initLevel();
        }
    }

    private void handleOverlayInput() {
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU)) {
            currentState = returnState;
            if (cameraController != null) cameraController.updateCursorState();
        }
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            hud.getStage().getViewport().unproject(tempV3);
            boolean close = false;
            if (currentState == GameState.INSTRUCTIONS && !hud.isTouchInsideInstructions(tempV3.x, tempV3.y)) close = true;
            if (currentState == GameState.CAMERA_CONFIG && !hud.isTouchInsideCameraConfig(tempV3.x, tempV3.y)) close = true;
            if (close) {
                currentState = returnState;
                if (cameraController != null) cameraController.updateCursorState();
            }
        }
    }

    private void setupInputProcessor() {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            com.badlogic.gdx.InputMultiplexer multiplexer = new com.badlogic.gdx.InputMultiplexer();
            if (currentState == GameState.START) {
                com.badlogic.gdx.scenes.scene2d.Stage s = hud.getStartMenuStage();
                if (s != null) multiplexer.addProcessor(s);
            } else if (currentState == GameState.PAUSED) {
                com.badlogic.gdx.scenes.scene2d.Stage s = hud.getPauseMenuStage();
                if (s != null) multiplexer.addProcessor(s);
            } else if (isGameplayState()) {
                com.badlogic.gdx.scenes.scene2d.Stage s = hud.getStage();
                if (s != null) multiplexer.addProcessor(s);
            }
            multiplexer.addProcessor(new com.badlogic.gdx.input.GestureDetector((com.badlogic.gdx.input.GestureDetector.GestureListener) inputProcessor));
            Gdx.input.setInputProcessor(multiplexer);
        } else {
            Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputProcessor);
        }
    }

    private void exitToMainMenu() {
        shotController.reset();
        hud.resetShots();
        currentState = GameState.START;
        menuManager.setMenuState(MenuState.MAIN);
        menuManager.setMenuSelection(0);
        isVictory = false;
    }

    private void enterInstructions() {
        hud.clearInstructionsRequest();
        hud.resetInstructionScroll();
        returnState = currentState;
        currentState = GameState.INSTRUCTIONS;
    }

    private void enterCameraConfig() {
        hud.clearCameraConfigRequest();
        hud.resetCameraConfigScroll();
        returnState = currentState;
        currentState = GameState.CAMERA_CONFIG;
    }

    private void activateSession(GameSession session) {
        this.activeSession = session;
    }

    private void startCompetitiveMatch(long seed, GameSession.GameMode mode) {
        currentState = GameState.COMPETITIVE;
        long today = SessionPersistence.getTodayTimestamp();
        activeSession = new GameSession(seed, config.difficulty, mode, today);
        activeSession.setSaveCallback(() -> SessionPersistence.saveSession(activeSession));
        activeSession.setCourseLayout(LevelDataGenerator.generate18Holes(seed));
        if (mode == GameSession.GameMode.STANDARD_18) standardSession = activeSession;
        else dailySession = activeSession;
        shotController.setGuidelineEnabled(false);
        SessionPersistence.saveSession(activeSession);
        initLevel();
    }

    @Override
    public void onStartQuickPlay() {
        currentState = GameState.PLAYING;
        initLevel();
    }

    @Override
    public void onShowInstructions() {
        enterInstructions();
    }

    @Override
    public void onStartWithClipboardSeed() {
        long seed = -1;
        String clip = Gdx.app.getClipboard().getContents();
        try { if (clip != null) seed = Long.parseLong(clip.trim()); } catch (Exception ignored) {}
        currentState = GameState.PLAYING;
        initLevel(seed);
    }

    @Override
    public void onSelectStandard18() {
        if (standardSession != null && !standardSession.isFinished()) {
            activateSession(standardSession);
            currentState = GameState.COMPETITIVE;
            initLevel();
        } else {
            menuManager.setPendingMatchMode(0);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onSelectDailyChallenge() {
        if (dailySession != null && !dailySession.isFinished()) {
            activateSession(dailySession);
            currentState = GameState.COMPETITIVE;
            initLevel();
        } else {
            menuManager.setPendingMatchMode(1);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode) {
        config.setDifficulty(difficulty);
        if (mode == 0) {
            long randomSeed = MathUtils.random.nextLong();
            if (randomSeed < 0) randomSeed *= -1; // Keep it positive for cleaner UI/Logs
            startCompetitiveMatch(randomSeed, GameSession.GameMode.STANDARD_18);
        } else {
            Calendar cal = Calendar.getInstance();
            long dailySeed = (long) cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH);
            startCompetitiveMatch(applySecretSauce(dailySeed), GameSession.GameMode.DAILY_CHALLENGE);
        }
    }

    @Override
    public void onStartPracticeRange() {
        currentState = GameState.PRACTICE_RANGE;
        initLevel();
    }

    @Override
    public void onStartPuttingGreen() {
        currentState = GameState.PUTTING_GREEN;
        initLevel();
    }

    private long applySecretSauce(long seed) {
        for (int i = 0; i < 10; i++) {
            seed = (seed * 13) + 8;
            if ((seed + "").length() > 9) seed = seed / 17;
        }
        return seed;
    }

    private boolean isGameplayState() {
        return currentState == GameState.PLAYING || currentState == GameState.COMPETITIVE ||
                currentState == GameState.PRACTICE_RANGE || currentState == GameState.PUTTING_GREEN;
    }

    private boolean isVictoryCourseComplete() {
        return isVictory && currentState == GameState.COMPETITIVE && activeSession != null && activeSession.isFinished() && inputProcessor.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU);
    }
}