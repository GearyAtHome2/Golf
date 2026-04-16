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
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.auth.AuthService;
import org.example.auth.GoogleSignInProvider;
import org.example.auth.LoginScreen;
import org.example.auth.UserSession;
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
import org.example.scoreBoard.CourseType;
import org.example.scoreBoard.SubmissionCoordinator;
import org.example.session.GameSession;
import org.example.session.SessionManager;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.example.terrain.level.LevelFactory;
import org.example.tutorial.TutorialController;
import org.example.tutorial.TutorialPrefs;

public class GolfGame extends ApplicationAdapter implements MenuManager.MenuHandler, HazardManager.HazardListener {

    private final GoogleSignInProvider googleSignInProvider;

    public GolfGame() { this(null); }

    public GolfGame(GoogleSignInProvider googleSignInProvider) {
        this.googleSignInProvider = googleSignInProvider;
    }

    private enum GameState {LOGIN, START, LOADING, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS, CAMERA_CONFIG}

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
    private Ball.State prevBallState = Ball.State.STATIONARY;
    private boolean showClubInfo = false;
    private boolean inTutorial = false;
    private TutorialController tutorialController = null;
    private GameConfig.Difficulty preTutorialDifficulty = null;
    private float tutorialAimStartYaw = Float.NaN; // yaw when STEP_2_AIM begins
    private SubmissionCoordinator submissionCoordinator;
    private Model highlightModel;
    private ModelInstance highlightInstance;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);
    private final Vector3 tempV3 = new Vector3();
    private final Vector2 stageTouch = new Vector2();
    private AuthService authService;
    private UserSession userSession;
    private LoginScreen loginScreen;
    private final org.example.scoreBoard.DailySubmissionCache dailySubmissionCache = new org.example.scoreBoard.DailySubmissionCache();

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
        sessionManager = new SessionManager(config);

        authService = new AuthService();
        userSession = new UserSession();
        userSession.load();

        submissionCoordinator = new SubmissionCoordinator(
                sessionManager, userSession, authService, dailySubmissionCache,
                new SubmissionCoordinator.Callbacks() {
                    @Override
                    public void onSubmissionStarted() {
                        if (inputProcessor instanceof DesktopInputProcessor dip) dip.setInputBlocked(true);
                        setupInputProcessor();
                    }

                    @Override
                    public void onSubmissionEnded() {
                        if (inputProcessor instanceof DesktopInputProcessor dip) dip.setInputBlocked(false);
                        setupInputProcessor();
                    }

                    @Override
                    public void onMenuInvalidate() {
                        hud.invalidateMobileMenuState();
                    }

                    @Override
                    public void onSubmitSuccess() {
                        exitToMainMenu();
                    }

                    @Override
                    public void onAutoRetrySuccess() {
                        hud.showToast("SCORE SUBMITTED!");
                    }

                    @Override
                    public Stage getSubmitStage() {
                        return hud.getStage();
                    }

                    @Override
                    public Stage getMenuStage() {
                        return hud.getStartMenuStage();
                    }

                    @Override
                    public Skin getSkin() {
                        return hud.getSkin();
                    }
                }
        );

        loginScreen = new LoginScreen(hud.getSkin(), authService, userSession, googleSignInProvider, r -> {
            Gdx.app.log("Login", "Welcome, " + r.displayName);
            hud.setLoggedInUser(r.displayName);
            sessionManager.reloadDailySessions(r.uid);
            dailySubmissionCache.fetch(r.uid, this::tryAutoRetryPending);
            TutorialPrefs.markFirstLoginDone();
            changeState(GameState.START);
        });

        if (userSession.isLoggedIn()) {
            userSession.tryAutoLogin(authService, new AuthService.AuthCallback() {
                @Override
                public void onSuccess(AuthService.AuthResult r) {
                    Gdx.app.log("UserSession", "Auto-login OK — " + r.displayName);
                    hud.setLoggedInUser(r.displayName);
                    sessionManager.reloadDailySessions(r.uid);
                    dailySubmissionCache.fetch(r.uid, GolfGame.this::tryAutoRetryPending);
                    changeState(GameState.START);
                }

                @Override
                public void onFailure(String msg) {
                    Gdx.app.log("UserSession", "Auto-login failed — showing login screen.");
                    // currentState stays LOGIN; loginScreen is already visible.
                }
            });
        }
    }

    private void initInputSystems() {
        if (Platform.isAndroid()) {
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

        if (Platform.isAndroid()) {
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
                Gdx.input.setCursorCatched(false);
                break;
            default:
                Gdx.input.setCursorCatched(!(isVictory || submissionCoordinator.isSubmissionInProgress()));
                break;
        }

        if (cameraController != null) {
            cameraController.setPaused(newState == GameState.PAUSED || newState == GameState.START);
        }
        setupInputProcessor();
    }

    private void handleInput() {
        if (currentState == GameState.LOADING) return;
        if (hud.wasMainMenuRequested()) {
            exitToMainMenu();
            return;
        }
        if (submissionCoordinator.isSubmissionInProgress()) return;

        switch (currentState) {
            case LOGIN -> {
            } // input handled by the login stage via the input multiplexer
            case START -> {
                menuManager.handleInput(inputProcessor, this, sessionManager.getCompetitiveSessions(), dailySubmissionCache);
                // Android: tap the right half of the screen to go back from any sub-menu
                if (Platform.isAndroid()
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

    private void handleTutorialInput() {
        if (tutorialController == null || !tutorialController.isActive()) return;
        switch (tutorialController.getCurrentStep()) {
            case STEP_1_DISTANCE:
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
                    tutorialController.advance(); // → STEP_2_AIM
                    tutorialAimStartYaw = (cameraController != null) ? cameraController.getYaw() : Float.NaN;
                }
                break;

            case STEP_2_AIM:
                if (cameraController != null && !Float.isNaN(tutorialAimStartYaw)) {
                    float delta = Math.abs(cameraController.getYaw() - tutorialAimStartYaw);
                    if (delta > 8f) {
                        tutorialController.advance(); // → STEP_3_INFO
                    }
                }
                break;

            case STEP_3_INFO:
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) {
                    showClubInfo = !showClubInfo; // allow the club info panel to open normally
                    tutorialController.advance(); // → STEP_4_CLUB
                } else if (hud.consumeInfoToggled()) {
                    tutorialController.advance();
                }
                break;

            case STEP_4_CLUB:
                if (currentClub == Club.IRON_9) {
                    tutorialController.advance();
                }
                break;
            case STEP_5_POWER:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (shotController.isCharging()) {
                    tutorialController.advance(); // → STEP_6_HIT
                }
                break;

            case STEP_8_PUTTER:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (currentClub == Club.PUTTER) {
                    tutorialController.advance();
                }
                break;

            case STEP_9_PROJECT:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PROJECTION)) {
                    tutorialController.advance();
                }
                break;

            default:
                break;
        }
    }

    private void handleGameplayInput() {
        if (isVictory) {
            handleVictoryInput();
            return;
        }

        if (inTutorial && tutorialController != null) {
            handleTutorialInput();
            // No early return — club changes and normal input continue.
            // Mobile: unwanted buttons are blocked via Touchable in applyTutorialButtonBlock().
            // Desktop: tutorial is hint-only, free input is fine.
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PAUSE)) {
            changeState(GameState.PAUSED);
            return;
        }

        if (!shotController.isCharging() &&
                !inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) &&
                !inputProcessor.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW)) {

            int direction = 0;
            if (!Platform.isAndroid()) {
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

            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_FIRST))
                currentClub = Club.values()[0];
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_LAST))
                currentClub = Club.values()[Club.values().length - 1];
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP) && config.difficulty.hasClubInfo())
            showClubInfo = !showClubInfo;
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.RESET_BALL)) resetBallToLastShot();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.NEW_LEVEL)) handleNewLevelInput();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_UP)) config.adjustGameSpeed(true);
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_DOWN)) config.adjustGameSpeed(false);
    }

    private void handlePauseInput() {
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
        } else if (Platform.isAndroid() && Gdx.input.justTouched()) {
            tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
            hud.getStage().getViewport().unproject(tempV3);
            if ((currentState == GameState.INSTRUCTIONS && !hud.isTouchInsideInstructions(tempV3.x, tempV3.y)) ||
                    (currentState == GameState.CAMERA_CONFIG && !hud.isTouchInsideCameraConfig(tempV3.x, tempV3.y))) {
                changeState(previousState);
            }
        }
    }

    private void handleNewLevelInput() {
        if (isOverlayState(currentState) || currentState == GameState.LOGIN || currentState == GameState.START || currentState == GameState.PAUSED)
            return;

        if (gameplayState == GameState.COMPETITIVE) {
            GameSession active = sessionManager.getActive();
            if (isVictory && active != null && !active.isFinished()) {
                sessionManager.saveActive();
                startLoadingLevel(GameState.COMPETITIVE, -1);
            }
        } else {
            startLoadingLevel(gameplayState, -1);
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
        submissionCoordinator.submitEndOfRound(sessionManager.getActive());
        setupInputProcessor();
    }

    private void startLoadingLevel(GameState targetState, long seed) {
        if (isGameplayState(targetState)) gameplayState = targetState;
        changeState(GameState.LOADING);
        Gdx.app.postRunnable(() -> {
            initLevel(seed);
            changeState(targetState);
        });
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
            case START -> LevelFactory.GameMode.START;
            case PRACTICE_RANGE -> LevelFactory.GameMode.PRACTICE_RANGE;
            case PUTTING_GREEN -> LevelFactory.GameMode.PUTTING_GREEN;
            default -> LevelFactory.GameMode.PLAYING;
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
            Vector3 spawnPos = new Vector3(tee.x, tee.y + 0.17f, tee.z);
            if (gameplayState == GameState.COMPETITIVE) {
                GameSession active = sessionManager.getActive();
                if (active != null) {
                    float[] rest = active.getBallRestPosition();
                    if (rest != null) spawnPos.set(rest[0], rest[1], rest[2]);
                }
            }
            ball = new Ball(spawnPos, particleManager, config, seed);
        }
        prevBallState = Ball.State.STATIONARY; // suppress spurious save on first frame
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

        if (inTutorial) {
//            tutorialController.advance(); // STEP_8_PUTT → DONE (or wherever we are)
            TutorialPrefs.markComplete();
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
            hud.renderStartMenu(menuManager, this, sessionManager.getCompetitiveSessions(), dailySubmissionCache);
        } else if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (currentState == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else if (currentState == GameState.LOADING) {
            hud.renderLoadingScreen();
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
        terrain.updateFlag(camera.position, ball.getPosition());

        // Tutorial: once ball is stationary on the green, prompt the putt
        if (inTutorial && tutorialController != null
                && tutorialController.getCurrentStep() == TutorialController.Step.STEP_7_WATCH
                && ball != null && ball.getState() == org.example.ball.Ball.State.STATIONARY) {
            if (terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z)
                    == org.example.terrain.Terrain.TerrainType.GREEN) {
                tutorialController.advance();
            }
        }
    }

    /**
     * Advances the elapsed timer for DAILY_1 mode when gameplay is active.
     */
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

        if (!isVictory && gameplayState == GameState.COMPETITIVE) {
            Ball.State currentState = ball.getState();
            if (currentState == Ball.State.STATIONARY && prevBallState != Ball.State.STATIONARY) {
                GameSession active = sessionManager.getActive();
                if (active != null) {
                    Vector3 pos = ball.getPosition();
                    active.setBallRestPosition(pos.x, pos.y, pos.z);
                    sessionManager.saveActive();
                }
            }
            prevBallState = currentState;
        }

        particleManager.update(particleDelta, terrain);
    }

    private void updateShotLogic(float delta, Terrain terrain) {
        if (hud.wasMinigameCanceled()) shotController.reset();

        PhysicsProfiler.startSection("ShotControllerCharge");
        shotController.setGuidelineAvailable(config.difficulty.hasShotProjection());
        if (shotController.update(delta, ball, camera.direction, currentClub, hud, terrain, inputProcessor)) {
            if (GameState.PRACTICE_RANGE != gameplayState) hud.resetSpin();
            hazardManager.setBallHit(true);
            if (gameplayState == GameState.COMPETITIVE) {
                GameSession active = sessionManager.getActive();
                if (active != null) active.clearBallRestPosition();
            }
            sessionManager.saveActive();
            // Tutorial: shot has fired — move to the "watch" step
            if (inTutorial && tutorialController != null
                    && (tutorialController.getCurrentStep() == TutorialController.Step.STEP_6_HIT || tutorialController.getCurrentStep() == TutorialController.Step.STEP_10_AIM)) {
                tutorialController.advance(); // → STEP_7_WATCH
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
            hud.renderPauseMenu(currentLevelData, inputProcessor, active, submissionCoordinator.isSubmissionInProgress());
        } else {
            if (!config.difficulty.hasClubInfo()) showClubInfo = false;
            hud.renderPlayingHUD(currentClub, ball, isPractice, currentLevelData, camera, levelManager.getTerrain(), active, inputProcessor, showClubInfo, shotController);
        }

        com.badlogic.gdx.scenes.scene2d.Stage uiStage =
                (currentState == GameState.START) ? hud.getStartMenuStage() : hud.getStage();

        if (inTutorial && tutorialController != null) {
            hud.applyTutorialButtonBlock(tutorialController.isActive() ? tutorialController.getCurrentStep() : null);
        }

        if (uiStage != null && currentState != GameState.PAUSED) {
            uiStage.act(Gdx.graphics.getDeltaTime());
            uiStage.draw();
        }

        if (inTutorial && tutorialController != null && tutorialController.getCurrentStep().isOverlayVisible()) {
            com.badlogic.gdx.math.Vector3 wind = (currentLevelData != null) ? currentLevelData.getWind() : null;
            hud.renderTutorialOverlay(tutorialController.getCurrentStep(), wind);
        }
    }

    private void exitToMainMenu() {
        shotController.reset();
        hud.resetShots();
        isVictory = false;
        sessionManager.clearActive();
        menuManager.setMenuState(MenuState.MAIN);
        menuManager.setMenuSelection(0);
        cameraController = null;
        if (inTutorial) {
            if (preTutorialDifficulty != null) config.setDifficulty(preTutorialDifficulty);
            inTutorial = false;
            tutorialController = null;
            preTutorialDifficulty = null;
            selectedArchetype = null;
            tutorialAimStartYaw = Float.NaN;
            hud.clearTutorialBlock();
        }
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
        startLoadingLevel(GameState.PLAYING, -1);
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
        startLoadingLevel(GameState.PLAYING, seed);
    }

    @Override
    public void onStartWithArchetype(LevelData.Archetype archetype) {
        selectedArchetype = archetype;
        startLoadingLevel(GameState.PLAYING, -1);
    }

    @Override
    public void onSelectStandard18() {
        GameSession standard = sessionManager.getStandard();
        if (standard != null && !standard.isFinished()) {
            sessionManager.setActive(standard);
            config.setDifficulty(standard.getDifficulty());
            startLoadingLevel(GameState.COMPETITIVE, -1);
        } else {
            menuManager.setPendingMatchMode(0);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
    }

    @Override
    public void onSelectDaily18() {
        selectDaily(sessionManager.getDaily18(), 1);
    }

    @Override
    public void onSelectDaily9() {
        selectDaily(sessionManager.getDaily9(), 2);
    }

    @Override
    public void onSelectDaily1() {
        selectDaily(sessionManager.getDaily1(), 3);
    }

    private void selectDaily(GameSession daily, int pendingMatchMode) {
        if (daily != null && !daily.isFinished()) {
            sessionManager.setActive(daily);
            config.setDifficulty(daily.getDifficulty());
            startLoadingLevel(GameState.COMPETITIVE, -1);
        } else {
            menuManager.setPendingMatchMode(pendingMatchMode);
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

        startLoadingLevel(GameState.COMPETITIVE, -1);
    }

    @Override
    public void onStartPracticeRange() {
        startLoadingLevel(GameState.PRACTICE_RANGE, -1);
    }

    @Override
    public void onStartPuttingGreen() {
        startLoadingLevel(GameState.PUTTING_GREEN, -1);
    }

    @Override
    public void onStartTutorial() {
        config.setDifficulty(GameConfig.Difficulty.NOVICE);
        preTutorialDifficulty = config.difficulty;
        inTutorial = true;
        tutorialController = new TutorialController();
        selectedArchetype = TutorialController.TUTORIAL_ARCHETYPE;
        startLoadingLevel(GameState.PLAYING, TutorialController.TUTORIAL_SEED);
    }

    @Override
    public void onLogout() {
        userSession.clear();
        dailySubmissionCache.clear();
        sessionManager.reloadDailySessions("");
        hud.setLoggedInUser("");
        loginScreen.reset();
        exitToMainMenu();
        changeState(GameState.LOGIN);
    }

    @Override
    public void onResubmitDaily(CourseType type) {
        submissionCoordinator.resubmitDaily(type);
    }

    private void tryAutoRetryPending() {
        submissionCoordinator.tryAutoRetryAll();
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
