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
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ball.Ball;
import org.example.ball.ShotController;
import org.example.camera.CameraController;
import org.example.gameManagers.GhostManager;
import org.example.gameManagers.HazardManager;
import org.example.gameManagers.LevelManager;
import org.example.gameManagers.MenuManager;
import org.example.glamour.ParticleManager;
import org.example.glamour.WindManager;
import org.example.hud.HUD;
import org.example.hud.renderer.MainMenuRenderer.MenuState;
import org.example.input.DesktopInputProcessor;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;
import org.example.performance.PhysicsProfiler;
import org.example.auth.AuthService;
import org.example.auth.LoginScreen;
import org.example.auth.UserSession;
import org.example.scoreBoard.ScoreSubmissionHandler;
import org.example.session.CompetitiveSessions;
import org.example.session.GameSession;
import org.example.session.SessionManager;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.example.terrain.level.LevelFactory;

public class GolfGame extends ApplicationAdapter implements MenuManager.MenuHandler, HazardManager.HazardListener {

    private enum GameState {LOGIN, START, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS, CAMERA_CONFIG}

    private GameState currentState = GameState.LOGIN;
    private GameState previousState = GameState.START;
    private GameState gameplayState = GameState.PLAYING;
    private LevelData.Archetype selectedArchetype = null;

    private GhostManager ghostManager;
    private MenuManager menuManager;
    private HazardManager hazardManager;
    private SessionManager sessionManager;
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
    private boolean submissionStarted = false;
    private Model highlightModel;
    private ModelInstance highlightInstance;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);
    private final Vector3 tempV3 = new Vector3();
    private final Vector2 stageTouch = new Vector2();
    private AuthService  authService;
    private UserSession  userSession;
    private LoginScreen  loginScreen;

    @Override
    public void create() {
        try {
            initCoreSystems();
            initInputSystems();
            setupHighlight();
            setupInputProcessor();
        } catch (Exception e) {
            Gdx.app.error("CRITICAL", "Failed to initialize game", e);
        }
    }

    private void initCoreSystems() {
        setupCamera();
        setupEnvironment();
        modelBatch     = new ModelBatch();
        hud            = new HUD(config);
        levelManager   = new LevelManager();
        levelFactory   = new LevelFactory();
        ghostManager   = new GhostManager(8);
        menuManager    = new MenuManager();
        hazardManager  = new HazardManager();
        shotController = new ShotController();
        particleManager = new ParticleManager();
        windManager    = new WindManager();
        sessionManager = new SessionManager(config);

        authService = new AuthService();
        userSession = new UserSession();
        userSession.load();

        loginScreen = new LoginScreen(hud.getSkin(), authService, userSession, r -> {
            Gdx.app.log("Login", "Welcome, " + r.displayName);
            hud.setLoggedInUser(r.displayName);
            changeState(GameState.START);
        });

        if (userSession.isLoggedIn()) {
            userSession.tryAutoLogin(authService, new AuthService.AuthCallback() {
                @Override public void onSuccess(AuthService.AuthResult r) {
                    Gdx.app.log("UserSession", "Auto-login OK — " + r.displayName);
                    hud.setLoggedInUser(r.displayName);
                    changeState(GameState.START);
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.log("UserSession", "Auto-login failed — showing login screen.");
                    // currentState stays LOGIN; loginScreen is already visible.
                }
            });
        }
    }

    private void initInputSystems() {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            inputProcessor = new MobileInputProcessor();
            hud.setupMobileUI((MobileInputProcessor) inputProcessor);
        } else {
            inputProcessor = new DesktopInputProcessor();
        }
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

    private void setupInputProcessor() {
        com.badlogic.gdx.InputMultiplexer multiplexer = new com.badlogic.gdx.InputMultiplexer();

        com.badlogic.gdx.scenes.scene2d.Stage activeStage;
        if (currentState == GameState.LOGIN) {
            activeStage = loginScreen.getStage();
        } else if (currentState == GameState.START) {
            activeStage = hud.getStartMenuStage();
        } else if (currentState == GameState.PAUSED) {
            activeStage = hud.getPauseMenuStage();
        } else if (isOverlayState(currentState)) {
            activeStage = null; // overlay input handled via Gdx.input directly; no stage needed
        } else {
            activeStage = hud.getStage();
        }

        if (activeStage != null) {
            activeStage.getViewport().update(Gdx.graphics.getWidth(), Gdx.graphics.getHeight(), true);
            multiplexer.addProcessor(activeStage);
        }

        if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            multiplexer.addProcessor(new com.badlogic.gdx.input.GestureDetector((com.badlogic.gdx.input.GestureDetector.GestureListener) inputProcessor));
        } else {
            multiplexer.addProcessor((com.badlogic.gdx.InputProcessor) inputProcessor);
        }

        com.badlogic.gdx.Gdx.input.setInputProcessor(multiplexer);
    }

    private void changeState(GameState newState) {
        if (this.currentState == newState) return;
        Gdx.app.log("STATE_CHANGE", currentState + " -> " + newState);

        if (isGameplayState(newState)) this.gameplayState = newState;
        if (!isOverlayState(this.currentState)) this.previousState = this.currentState;

        this.currentState = newState;

        switch (newState) {
            case LOGIN:
            case START:
            case PAUSED:
            case INSTRUCTIONS:
            case CAMERA_CONFIG:
                Gdx.app.log("CURSOR_CHECK", "Releasing cursor in changeState for: " + newState);
                Gdx.input.setCursorCatched(false);
                break;
            default:
                Gdx.input.setCursorCatched(!(isVictory || submissionStarted));
                break;
        }

        if (cameraController != null) {
            cameraController.setPaused(newState == GameState.PAUSED || newState == GameState.START);
        }
        setupInputProcessor();
    }

    private void handleInput() {
        if (hud.wasMainMenuRequested()) { exitToMainMenu(); return; }
        if (submissionStarted) return;

        switch (currentState) {
            case LOGIN -> {} // input handled by the login stage via the input multiplexer
            case START -> {
                menuManager.handleInput(inputProcessor, this, sessionManager.getCompetitiveSessions());
                // Android: tap the right half of the screen to go back from any sub-menu
                if (com.badlogic.gdx.Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android
                        && menuManager.getCurrentMenuState() != org.example.hud.renderer.MainMenuRenderer.MenuState.MAIN
                        && com.badlogic.gdx.Gdx.input.justTouched()) {
                    stageTouch.set(com.badlogic.gdx.Gdx.input.getX(), com.badlogic.gdx.Gdx.input.getY());
                    hud.getStartMenuStage().getViewport().unproject(stageTouch);
                    if (stageTouch.x > hud.getStartMenuStage().getViewport().getWorldWidth() / 2f) {
                        menuManager.navigateBack();
                    }
                }
            }
            case INSTRUCTIONS, CAMERA_CONFIG -> handleOverlayInput();
            case PAUSED -> handlePauseInput();
            default -> handleGameplayInput();
        }
    }

    private void handleGameplayInput() {
        if (isVictory) {
            handleVictoryInput();
            return;
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PAUSE)) {
            changeState(GameState.PAUSED);
            return;
        }

        if (!shotController.isCharging() &&
                !inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) &&
                !inputProcessor.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW)) {

            int direction = 0;
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Desktop) {
                float scroll = inputProcessor.getActionValue(GameInputProcessor.Action.SCROLL_Y);
                if (scroll != 0) direction = scroll > 0 ? 1 : -1;
            } else {
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_UP)) direction = -1;
                else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_DOWN)) direction = 1;
            }

            if (direction != 0) {
                int index = MathUtils.clamp(currentClub.ordinal() + direction, 0, Club.values().length - 1);
                currentClub = Club.values()[index];
            }
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) showClubInfo = !showClubInfo;
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.RESET_BALL)) resetBallToLastShot();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.NEW_LEVEL)) handleNewLevelInput();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_UP)) config.adjustGameSpeed(true);
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_DOWN)) config.adjustGameSpeed(false);
    }

    private void handlePauseInput() {
        // TODO Phase 2 test — remove after verification. Press T on pause screen to test auth + session.
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.T)) {
            Gdx.app.log("AuthTest", "Session state — loggedIn: " + userSession.isLoggedIn()
                + ", name: " + userSession.getDisplayName()
                + ", idToken empty: " + userSession.getIdToken().isEmpty());

            AuthService.AuthCallback saveAndLog = new AuthService.AuthCallback() {
                @Override public void onSuccess(AuthService.AuthResult r) {
                    userSession.save(r);
                    Gdx.app.log("AuthTest", "Auth OK — UID: " + r.uid + ", name: " + r.displayName
                        + " | Session saved. Restart the game to verify auto-login.");
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("AuthTest", "Auth failed: " + msg);
                }
            };

            authService.signIn("test@geary-golf.com", "test1234", new AuthService.AuthCallback() {
                @Override public void onSuccess(AuthService.AuthResult r) { saveAndLog.onSuccess(r); }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("AuthTest", "Sign-in failed: " + msg);
                }
            });
        }
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) {
            exitToMainMenu();
        } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PAUSE)) {
            changeState(gameplayState);
        } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) {
            enterInstructions();
        } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CAM_CONFIG)) {
            enterCameraConfig();
        }
    }

    private void handleOverlayInput() {
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU)) {
            changeState(previousState);
        } else if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            hud.getStage().getViewport().unproject(tempV3);
            if ((currentState == GameState.INSTRUCTIONS && !hud.isTouchInsideInstructions(tempV3.x, tempV3.y)) ||
                    (currentState == GameState.CAMERA_CONFIG && !hud.isTouchInsideCameraConfig(tempV3.x, tempV3.y))) {
                changeState(previousState);
            }
        }
    }

    private void handleNewLevelInput() {
        if (isOverlayState(currentState) || currentState == GameState.LOGIN || currentState == GameState.START || currentState == GameState.PAUSED) return;

        if (gameplayState == GameState.COMPETITIVE) {
            GameSession active = sessionManager.getActive();
            if (isVictory && active != null && !active.isFinished()) {
                sessionManager.saveActive();
                changeState(GameState.COMPETITIVE);
                initLevel();
            }
        } else {
            initLevel();
        }
    }

    private void handleVictoryInput() {
        if (isVictoryCourseComplete()) {
            if (sessionManager.isDailyActive() && inputProcessor.isActionJustPressed(GameInputProcessor.Action.SUBMIT_SCORE)) {
                processScoreSubmission();
            } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU)) {
                exitToMainMenu();
            }
        } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.NEW_LEVEL)) {
            handleNewLevelInput();
        }
    }

    private void processScoreSubmission() {
        Gdx.app.log("GOLF_GAME", "Starting Score Submission Process");
        submissionStarted = true;
        if (inputProcessor instanceof DesktopInputProcessor dip) dip.setInputBlocked(true);
        setupInputProcessor();

        new ScoreSubmissionHandler(
                sessionManager.getActive(),
                () -> {
                    Gdx.app.postRunnable(() -> {
                        Gdx.app.log("GOLF_GAME", "Submission successful, exiting to main menu");
                        submissionStarted = false;
                        if (inputProcessor instanceof DesktopInputProcessor dip) dip.setInputBlocked(false);
                        exitToMainMenu();
                    });
                },
                () -> {
                    Gdx.app.postRunnable(() -> {
                        Gdx.app.log("GOLF_GAME", "Submission cancelled, resetting flags");
                        submissionStarted = false;
                        if (inputProcessor instanceof DesktopInputProcessor dip) dip.setInputBlocked(false);
                        setupInputProcessor();
                    });
                }
        ).trigger(hud.getStage(), hud.getSkin());
    }

    private void initLevel() {
        initLevel(-1);
    }

    private void initLevel(long manualSeed) {
        clearCurrentLevelState();
        LevelFactory.LevelCreationResult result = generateLevelResult(manualSeed);

        currentLevelData = result.data;
        currentClub = result.defaultClub;

        levelManager.buildLevel(result.generator, result.waterLevel, result.distance);
        setupSpawnAndPins();
        setupInputProcessor();

        isVictory = false;
        submissionStarted = false;
        hazardManager.resetTimer();
        particleManager.clear();
    }

    private void clearCurrentLevelState() {
        if (ball != null) ball.dispose();
        ghostManager.clear();
        shotController.reset();
        hud.reset();
    }

    private LevelFactory.LevelCreationResult generateLevelResult(long manualSeed) {
        LevelFactory.GameMode mode = switch (gameplayState) {
            case START          -> LevelFactory.GameMode.START;
            case PRACTICE_RANGE -> LevelFactory.GameMode.PRACTICE_RANGE;
            case PUTTING_GREEN  -> LevelFactory.GameMode.PUTTING_GREEN;
            default             -> LevelFactory.GameMode.PLAYING;
        };

        GameSession active = sessionManager.getActive();
        if (gameplayState == GameState.COMPETITIVE && active != null) {
            hud.setActiveSession(active);
            LevelData data = active.getCourseLayout().get(active.getCurrentHoleIndex());
            ITerrainGenerator generator = new ClassicGenerator(data);
            return new LevelFactory.LevelCreationResult(generator, data, Club.DRIVER, data.getWaterLevel(), data.getDistance());
        } else if (mode == LevelFactory.GameMode.PLAYING && selectedArchetype != null) {
            long seed = (manualSeed == -1) ? System.nanoTime() : manualSeed;
            LevelData data = LevelDataGenerator.createFixedLevelData(seed, selectedArchetype);
            ITerrainGenerator generator = new ClassicGenerator(data);
            hud.resetShots();
            return new LevelFactory.LevelCreationResult(generator, data, Club.DRIVER, data.getWaterLevel(), data.getDistance());
        } else {
            LevelFactory.LevelCreationResult res = levelFactory.createLevel(mode, manualSeed);
            hud.resetShots();
            return res;
        }
    }

    private void setupSpawnAndPins() {
        Terrain terrain = levelManager.getTerrain();
        Vector3 tee = terrain.getTeePosition();
        Vector3 hole = terrain.getHolePosition();

        if (gameplayState == GameState.PUTTING_GREEN) {
            terrain.setTeePosition(new Vector3(tee.x, terrain.getHeightAt(tee.x, tee.z), tee.z));
            terrain.setHolePosition(new Vector3(hole.x, terrain.getHeightAt(hole.x, hole.z), hole.z));
            ball = new Ball(new Vector3(tee.x, tee.y + 0.5f, tee.z), particleManager, config, 100L);
            ball.setState(Ball.State.AIR);
        } else {
            long seed = currentLevelData != null ? currentLevelData.getSeed() : 100L;
            ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config, seed);
        }
        hazardManager.setBallHit(false);
        refreshCameraController(hole);
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

        GameSession active = sessionManager.getActive();
        if (gameplayState == GameState.COMPETITIVE && active != null) {
            active.advanceHole();
            sessionManager.saveActive();
        }
    }

    private void refreshCameraController(Vector3 lookAtTarget) {
        cameraController = new CameraController(camera, 12f, ball.getPosition(), config.cameraSettings);
        cameraController.setOrientation(lookAtTarget);
        cameraController.update(ball.getPosition(), inputProcessor);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        inputProcessor.update(delta);
        handleInput();

        gameViewport.apply();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        if (currentState == GameState.LOGIN) {
            loginScreen.render();
        } else if (currentState == GameState.START) {
            hud.renderStartMenu(menuManager, this, sessionManager.getCompetitiveSessions());
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

    private void updateLogic(float delta) {
        Terrain terrain = levelManager.getTerrain();
        if (terrain == null) return;

        float speedMultiplier = config.getGameSpeed();
        float effDelta = (currentState == GameState.PAUSED) ? 0 : delta * speedMultiplier;
        float particleDelta = (isVictory || currentState == GameState.PAUSED) ? delta * 0.06f : delta * speedMultiplier;

        if (isGameplayState() || currentState == GameState.PAUSED) {
            updateGameplaySystems(delta, effDelta, particleDelta, terrain);
        }

        updateDaily1Timer(delta);

        terrain.updateCameraOcclusion(camera.position, ball.getPosition(), delta);
        terrain.updateFlag(camera.position);
    }

    /** Advances the elapsed timer for DAILY_1 mode when gameplay is active. */
    private void updateDaily1Timer(float delta) {
        if (gameplayState != GameState.COMPETITIVE || currentState == GameState.PAUSED || isVictory) return;
        GameSession active = sessionManager.getActive();
        if (active != null && active.getMode() == GameSession.GameMode.DAILY_1) {
            active.addElapsedTime(delta);
        }
    }

    private void updateGameplaySystems(float delta, float effDelta, float particleDelta, Terrain terrain) {
        Vector3 currentWind = (gameplayState == GameState.PUTTING_GREEN || currentLevelData == null) ? zeroWind : currentLevelData.getWind();

        if (currentState != GameState.PAUSED) {
            windManager.update(effDelta, currentWind, camera.position);

            if (!isVictory) {
                updateShotLogic(delta, terrain);
            }

            ball.update(effDelta, terrain, currentWind);
        }

        if (cameraController != null) {
            cameraController.update(ball.getPosition(), inputProcessor);
        }

        if (config.particlesEnabled) particleManager.handleBallInteraction(ball, terrain);

        if (!isVictory) {
            hazardManager.update(effDelta, ball, terrain, gameplayState == GameState.PRACTICE_RANGE, this);
            if (ball.checkVictory(terrain)) triggerVictory();
        }

        particleManager.update(particleDelta, terrain);
    }

    private void updateShotLogic(float delta, Terrain terrain) {
        if (hud.wasMinigameCanceled()) shotController.reset();

        PhysicsProfiler.startSection("ShotControllerCharge");
        if (ball.getState() == Ball.State.STATIONARY || shotController.isCharging()) {
            if (shotController.update(delta, ball, camera.direction, currentClub, hud, terrain, inputProcessor)) {
                if (GameState.PRACTICE_RANGE != gameplayState) hud.resetSpin();
                hazardManager.setBallHit(true);
                shotController.update(0, ball, camera.direction, currentClub, hud, terrain, inputProcessor);
                sessionManager.saveActive();
            }
        }
        PhysicsProfiler.endSection("ShotControllerCharge");
    }

    private void renderScene() {
        if (levelManager.getTerrain() == null) return;
        modelBatch.begin(camera);
        levelManager.render(modelBatch, environment);
        ball.render(modelBatch, environment);
        ball.renderTrail(modelBatch, environment);
        ghostManager.render(modelBatch, environment);
        if (cameraController != null && cameraController.isOverhead()) {
            highlightInstance.transform.setToTranslation(ball.getPosition());
            modelBatch.render(highlightInstance, environment);
        }
        shotController.render(modelBatch, environment, ball.getPosition(), camera.position);
        particleManager.render(modelBatch, environment);
        modelBatch.end();
        if (currentLevelData != null && !isVictory && gameplayState != GameState.PUTTING_GREEN) {
            windManager.render(camera, currentLevelData.getWind());
        }
    }

    private void renderUI() {
        boolean isPractice = (gameplayState == GameState.PRACTICE_RANGE || gameplayState == GameState.PUTTING_GREEN);
        GameSession active = sessionManager.getActive();

        if (isVictory) {
            hud.renderVictory(hud.getShotCount(), currentLevelData, active);
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu(currentLevelData, inputProcessor, active, submissionStarted);
        } else {
            hud.renderPlayingHUD(currentClub, ball, isPractice, currentLevelData, camera, levelManager.getTerrain(), active, inputProcessor, showClubInfo, shotController);
        }

        com.badlogic.gdx.scenes.scene2d.Stage uiStage =
                (currentState == GameState.START) ? hud.getStartMenuStage() : hud.getStage();

        if (uiStage != null && currentState != GameState.PAUSED) {
            uiStage.act(Gdx.graphics.getDeltaTime());
            uiStage.draw();

            if (Gdx.input.justTouched()) {
                stageTouch.set(Gdx.input.getX(), Gdx.input.getY());
                uiStage.screenToStageCoordinates(stageTouch);
                Gdx.app.log("STAGE_DEBUG", "Stage Units Touch: x=" + stageTouch.x + ", y=" + stageTouch.y);
            }
        }
    }

    private void exitToMainMenu() {
        shotController.reset();
        hud.resetShots();
        isVictory = false;
        submissionStarted = false;
        sessionManager.clearActive();
        menuManager.setMenuState(MenuState.MAIN);
        menuManager.setMenuSelection(0);
        cameraController = null;
        changeState(GameState.START);
    }

    private void enterInstructions() {
        hud.resetInstructionScroll();
        changeState(GameState.INSTRUCTIONS);
    }

    private void enterCameraConfig() {
        hud.resetCameraConfigScroll();
        changeState(GameState.CAMERA_CONFIG);
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
        ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config, 200L);
        hazardManager.setBallHit(false);
    }

    @Override
    public void onStartQuickPlay() {
        selectedArchetype = null;
        changeState(GameState.PLAYING);
        initLevel();
    }

    @Override
    public void onShowInstructions() {
        enterInstructions();
    }

    @Override
    public void onStartWithClipboardSeed() {
        selectedArchetype = null;
        long seed = -1;
        String clip = Gdx.app.getClipboard().getContents();
        try {
            if (clip != null) seed = Long.parseLong(clip.trim());
        } catch (Exception ignored) {
        }
        changeState(GameState.PLAYING);
        initLevel(seed);
    }

    @Override
    public void onStartWithArchetype(LevelData.Archetype archetype) {
        selectedArchetype = archetype;
        changeState(GameState.PLAYING);
        initLevel(-1);
    }

    @Override
    public void onSelectStandard18() {
        GameSession standard = sessionManager.getStandard();
        if (standard != null && !standard.isFinished()) {
            sessionManager.setActive(standard);
            changeState(GameState.COMPETITIVE);
            initLevel();
        } else {
            menuManager.setPendingMatchMode(0);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onSelectDaily18() {
        GameSession daily = sessionManager.getDaily18();
        if (daily != null && !daily.isFinished()) {
            sessionManager.setActive(daily);
            changeState(GameState.COMPETITIVE);
            initLevel();
        } else {
            menuManager.setPendingMatchMode(1);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onSelectDaily9() {
        GameSession daily = sessionManager.getDaily9();
        if (daily != null && !daily.isFinished()) {
            sessionManager.setActive(daily);
            changeState(GameState.COMPETITIVE);
            initLevel();
        } else {
            menuManager.setPendingMatchMode(2);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onSelectDaily1() {
        GameSession daily = sessionManager.getDaily1();
        if (daily != null && !daily.isFinished()) {
            sessionManager.setActive(daily);
            changeState(GameState.COMPETITIVE);
            initLevel();
        } else {
            menuManager.setPendingMatchMode(3);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode) {
        config.setDifficulty(difficulty);
        shotController.setGuidelineEnabled(false);

        switch (mode) {
            case 0 -> sessionManager.startStandardMatch();
            case 1 -> sessionManager.startDaily18();
            case 2 -> sessionManager.startDaily9();
            case 3 -> sessionManager.startDaily1();
        }

        changeState(GameState.COMPETITIVE);
        initLevel();
    }

    @Override
    public void onStartPracticeRange() {
        changeState(GameState.PRACTICE_RANGE);
        initLevel();
    }

    @Override
    public void onStartPuttingGreen() {
        changeState(GameState.PUTTING_GREEN);
        initLevel();
    }

    @Override
    public void onLogout() {
        userSession.clear();
        hud.setLoggedInUser("");
        loginScreen.reset();
        exitToMainMenu();
        changeState(GameState.LOGIN);
    }

    private boolean isVictoryCourseComplete() {
        GameSession active = sessionManager.getActive();
        return isVictory && gameplayState == GameState.COMPETITIVE && active != null && active.isFinished();
    }

    private boolean isGameplayState(GameState state) {
        return state == GameState.PLAYING || state == GameState.COMPETITIVE ||
                state == GameState.PRACTICE_RANGE || state == GameState.PUTTING_GREEN;
    }

    private boolean isGameplayState() {
        return isGameplayState(this.currentState);
    }

    private boolean isOverlayState(GameState state) {
        return state == GameState.INSTRUCTIONS || state == GameState.CAMERA_CONFIG;
    }

    @Override
    public void resize(int width, int height) {
        gameViewport.update(width, height, true);
        hud.resize(width, height);
        if (loginScreen != null) loginScreen.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hud.dispose();
        if (loginScreen != null) loginScreen.dispose();
        shotController.dispose();
        levelManager.dispose();
        ghostManager.dispose();
        particleManager.dispose();
        if (ball != null) ball.dispose();
        if (highlightModel != null) highlightModel.dispose();
    }
}
