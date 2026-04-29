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
import org.example.multiplayer.DeterminismRecorder;
import org.example.multiplayer.LiveScoreboardActor;
import org.example.multiplayer.MultiplayerLobbyScreen;
import org.example.multiplayer.MultiplayerScoreboardOverlay;
import org.example.multiplayer.RemoteBall;
import org.example.multiplayer.RoomService;
import org.example.multiplayer.ShotPacket;
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

    private enum GameState {LOGIN, START, LOADING, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS, CAMERA_CONFIG, DETERM_TEST, MULTIPLAYER_LOBBY}

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
    private TutorialSession tutorialSession = null;
    private SubmissionCoordinator submissionCoordinator;
    private Model highlightModel;
    private ModelInstance highlightInstance;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);

    private boolean isMultiplayer = false;

    // Multiplayer shot replay state
    private RoomService.RoomState      currentMultiplayerRoom = null;
    private int                        localStrokeNum         = 0;   // 1-indexed, reset per hole
    private final java.util.Map<String, Integer>   consumedStrokes = new java.util.HashMap<>();
    private final java.util.Map<String, RemoteBall> remoteBalls    = new java.util.LinkedHashMap<>();
    private float                      shotPollTimer          = 0f;
    private static final float         SHOT_POLL_INTERVAL     = 0.5f;
    // Per remote player: one ModelInstance for the ball + TRAIL_LENGTH instances for trail points.
    private com.badlogic.gdx.graphics.g3d.Model remoteBallModel = null;
    private final java.util.Map<String, java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance>>
            remoteBallInstances = new java.util.LinkedHashMap<>();

    // Multiplayer scoreboard state
    private boolean mpScoreboardVisible       = false;
    private boolean mpAllPlayersFinishedHole  = false;
    private boolean mpIsFinalScoreboard       = false;
    private float   mpScoreboardPollTimer     = 0f;
    private final java.util.Map<String, int[]>  mpHoleScores         = new java.util.LinkedHashMap<>();
    private final java.util.Map<String, String> mpPlayerDifficulties = new java.util.LinkedHashMap<>();
    private MultiplayerScoreboardOverlay mpScoreboardOverlay = null;

    // Pending upload retry queues — populated on network failure, flushed each poll tick.
    // Shots and rest positions use idempotent PUTs, so retrying is always safe.
    private static class PendingShot { ShotPacket packet; int holeIndex; }
    private final java.util.List<PendingShot> pendingShotUploads   = new java.util.ArrayList<>();
    private RoomService.RestPacket            pendingBallRest      = null; // ball-at-rest position
    private int                               pendingBallRestHole  = -1;
    private RoomService.RestPacket            pendingHoleRest      = null; // hole-out position
    private int                               pendingHoleRestHole  = -1;
    private int                               pendingScoreHoleIdx  = -1;   // score for completed hole
    private int                               pendingScoreStrokes  = -1;

    // Determinism self-test state
    // phases: 0=pre-fire1, 1=flight1, 2=pre-fire2, 3=flight2, 4=L-vs-R setup,
    //         5=L-vs-R flight, 6=combined result
    private int determPhase = 0;
    private DeterminismRecorder.Recording determRecording1;
    private String determResultText  = "";
    private String determLLResult    = "";  // L vs L one-liner
    private RemoteBall determRemoteBall = null;
    private final Vector3 determShotDir = new Vector3();
    private final Vector3 tempV3 = new Vector3();
    private final Vector2 stageTouch = new Vector2();
    private AuthService authService;
    private UserSession userSession;
    private LoginScreen loginScreen;
    private RoomService roomService;
    private MultiplayerLobbyScreen multiplayerLobbyScreen;
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

        roomService = new RoomService();
        multiplayerLobbyScreen = new MultiplayerLobbyScreen(
            hud.getSkin(), roomService, userSession,
            new MultiplayerLobbyScreen.Callback() {
                @Override public void onStartGame(RoomService.RoomState room) {
                    // Read this player's chosen difficulty from per-player meta
                    String localUid = userSession.getUid();
                    RoomService.PlayerMeta localMeta = room.playerMeta.get(localUid);
                    GameConfig.Difficulty diff = GameConfig.Difficulty.NOVICE;
                    if (localMeta != null && localMeta.difficulty != null) {
                        try { diff = GameConfig.Difficulty.valueOf(localMeta.difficulty); }
                        catch (Exception ignored) {}
                    }
                    config.setDifficulty(diff);

                    // Build uid → difficulty map for scoreboard display
                    mpPlayerDifficulties.clear();
                    for (java.util.Map.Entry<String, RoomService.PlayerMeta> e : room.playerMeta.entrySet()) {
                        if (e.getValue() != null && e.getValue().difficulty != null) {
                            mpPlayerDifficulties.put(e.getKey(), e.getValue().difficulty);
                        }
                    }

                    isMultiplayer = true;
                    currentMultiplayerRoom = room;
                    mpHoleScores.clear();
                    mpScoreboardVisible = false;
                    sessionManager.startMultiplayerMatch(room.seed, diff);
                    startLoadingLevel(GameState.COMPETITIVE, -1);
                }
                @Override public void onBack() {
                    changeState(GameState.START);
                }
            });

        mpScoreboardOverlay = new MultiplayerScoreboardOverlay(
            hud.getSkin(), this::onMpNextHole, this::exitToMainMenu);

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

        // Small red sphere used to render each remote player's ball during replay.
        // BlendingAttribute enables distance-based alpha fading (see render loop).
        float d = Ball.BALL_RADIUS * 2f;
        remoteBallModel = mb.createSphere(d, d, d, 12, 12,
                new Material(ColorAttribute.createDiffuse(new Color(1f, 0.2f, 0.2f, 1f)),
                             new BlendingAttribute(1f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
    }

    private void setupInputProcessor() {
        com.badlogic.gdx.InputMultiplexer multiplexer = new com.badlogic.gdx.InputMultiplexer();

        com.badlogic.gdx.scenes.scene2d.Stage activeStage;
        if (currentState == GameState.LOGIN) {
            activeStage = loginScreen.getStage();
        } else if (currentState == GameState.MULTIPLAYER_LOBBY) {
            activeStage = multiplayerLobbyScreen.getStage();
        } else if (currentState == GameState.START) {
            activeStage = hud.getStartMenuStage();
        } else if (currentState == GameState.PAUSED) {
            activeStage = hud.getPauseMenuStage();
        } else if (isOverlayState(currentState)) {
            activeStage = null; // overlay input handled via Gdx.input directly; no stage needed
        } else {
            activeStage = hud.getStage();
        }

        if (mpScoreboardVisible && mpScoreboardOverlay != null) {
            mpScoreboardOverlay.resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
            multiplexer.addProcessor(mpScoreboardOverlay.getStage());
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
            case MULTIPLAYER_LOBBY:
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
        if (tutorialSession == null || !tutorialSession.controller.isActive()) return;
        switch (tutorialSession.controller.getCurrentStep()) {
            case STEP_1_DISTANCE:
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SHOW_RANGE)) {
                    tutorialSession.controller.advance(); // → STEP_2_AIM
                    tutorialSession.aimStartYaw = (cameraController != null) ? cameraController.getYaw() : Float.NaN;
                }
                break;

            case STEP_2_AIM:
                if (cameraController != null && !Float.isNaN(tutorialSession.aimStartYaw)) {
                    float delta = Math.abs(cameraController.getYaw() - tutorialSession.aimStartYaw);
                    if (delta > 8f) {
                        tutorialSession.controller.advance(); // → STEP_3_INFO
                    }
                }
                break;

            case STEP_3_INFO:
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) {
                    showClubInfo = !showClubInfo; // allow the club info panel to open normally
                    tutorialSession.controller.advance(); // → STEP_4_CLUB
                } else if (hud.consumeInfoToggled()) {
                    tutorialSession.controller.advance();
                }
                break;

            case STEP_4_CLUB:
                if (currentClub == Club.IRON_9) {
                    tutorialSession.controller.advance();
                }
                break;
            case STEP_5_POWER:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (shotController.isCharging()) {
                    tutorialSession.controller.advance(); // → STEP_6_HIT
                }
                break;

            case STEP_8_PUTTER:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (currentClub == Club.PUTTER) {
                    tutorialSession.controller.advance();
                }
                break;

            case STEP_9_PROJECT:
                // Advance once the shot controller starts charging (MAX was triggered)
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PROJECTION)) {
                    tutorialSession.controller.advance();
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

        if (tutorialSession != null) {
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
                !inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION)) {

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
        if (isMultiplayer && mpScoreboardVisible) return; // scoreboard stage handles input
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
        // Propagate the new level seed to all remote balls so foliage RNG matches.
        long levelSeed = currentLevelData != null ? currentLevelData.getSeed() : 0L;
        for (RemoteBall rb : remoteBalls.values()) rb.setLevelSeed(levelSeed);

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
        resetMultiplayerReplayState();
    }

    private void resetMultiplayerReplayState() {
        localStrokeNum = 0;
        shotPollTimer  = 0f;
        consumedStrokes.clear();
        // Discard any per-hole pending uploads — they belong to the hole just completed.
        // pendingScore and pendingHoleRest are left intact: they survive into the scoreboard
        // phase and are cleared when successfully retried or when exitToMainMenu() is called.
        pendingShotUploads.clear();
        pendingBallRest = null; pendingBallRestHole = -1;
        remoteBalls.clear();
        remoteBallInstances.clear();
        if (isMultiplayer && currentMultiplayerRoom != null && remoteBallModel != null) {
            String myUid = userSession.getUid();
            // ball instance (index 0) + one instance per trail point (indices 1..TRAIL_LENGTH)
            int instancesNeeded = 1 + RemoteBall.TRAIL_LENGTH;
            for (java.util.Map.Entry<String, String> e : currentMultiplayerRoom.players.entrySet()) {
                if (e.getKey().equals(myUid)) continue;
                remoteBalls.put(e.getKey(),
                        new RemoteBall(e.getKey(), e.getValue(), particleManager));
                java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> instances =
                        new java.util.ArrayList<>();
                for (int i = 0; i < instancesNeeded; i++) {
                    instances.add(new com.badlogic.gdx.graphics.g3d.ModelInstance(remoteBallModel));
                }
                remoteBallInstances.put(e.getKey(), instances);
            }
        }
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
            if (cameraController != null) cameraController.update(ball.getPosition(), inputProcessor);
        }
    }

    private void triggerVictory() {
        Terrain terrain = levelManager.getTerrain();
        isVictory = true;
        float vel = ball.getVelocity().len();
        ball.getVelocity().setZero();
        ball.getPosition().set(terrain.getHolePosition());
        int shots = hud.getShotCount();
        int par   = currentLevelData != null ? currentLevelData.getPar() : 4;
        int diff  = (shots == 1) ? -99 : shots - par; // -99 = HIO sentinel

        // Colour: purple=HIO/albatross, pink=eagle, yellow=birdie, green=par, bogey→quad+ brown gradient
        Color celebColor;
        if      (diff <= -3) celebColor = new Color(0.72f, 0.18f, 1.00f, 1f); // purple
        else if (diff == -2) celebColor = new Color(1.00f, 0.40f, 0.85f, 1f); // pink
        else if (diff == -1) celebColor = new Color(1.00f, 0.92f, 0.20f, 1f); // yellow
        else if (diff ==  0) celebColor = new Color(0.20f, 0.80f, 0.20f, 1f); // green
        else if (diff ==  1) celebColor = new Color(0.55f, 0.42f, 0.28f, 1f); // grey-brown  (bogey)
        else if (diff ==  2) celebColor = new Color(0.42f, 0.30f, 0.18f, 1f); // medium brown (double)
        else if (diff ==  3) celebColor = new Color(0.30f, 0.20f, 0.10f, 1f); // dark brown   (triple)
        else                 celebColor = new Color(0.20f, 0.12f, 0.06f, 1f); // darkest brown (quad+)

        // Speed: base proportionality halved; scaled further by score
        float scoreMultiplier;
        if      (diff <= -3) scoreMultiplier = 2.00f;
        else if (diff == -2) scoreMultiplier = 1.50f;
        else if (diff == -1) scoreMultiplier = 1.20f;
        else if (diff ==  0) scoreMultiplier = 1.00f;
        else if (diff ==  1) scoreMultiplier = 0.60f;
        else if (diff ==  2) scoreMultiplier = 0.45f;
        else if (diff ==  3) scoreMultiplier = 0.35f;
        else                 scoreMultiplier = 0.25f;

        float celebForce = (vel * 0.5f + 5f) * scoreMultiplier;
        particleManager.spawn(terrain.getHolePosition(), celebColor, 40, celebForce, 50.0f, 4.0f);

        GameSession active = sessionManager.getActive();
        if (gameplayState == GameState.COMPETITIVE && active != null) {
            if (isMultiplayer) {
                int holeIdx = active.getCurrentHoleIndex();
                int strokes = hud.getShotCount();
                mpHoleScores.computeIfAbsent(userSession.getUid(), k -> new int[9])[holeIdx] = strokes;
                roomService.submitScore(
                    userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                    userSession.getUid(), holeIdx, strokes,
                    new RoomService.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String msg) {
                            Gdx.app.error("Multiplayer", "Score submit failed — queuing for retry: " + msg);
                            pendingScoreHoleIdx = holeIdx;
                            pendingScoreStrokes = strokes;
                        }
                    });
                // Broadcast authoritative hole position as rest packet so remote
                // balls lerp to the hole even if their physics replay fell short.
                Vector3 holePos = terrain.getHolePosition();
                RoomService.RestPacket holeRest = new RoomService.RestPacket();
                holeRest.x = holePos.x; holeRest.y = holePos.y; holeRest.z = holePos.z;
                holeRest.strokeNum = localStrokeNum;
                holeRest.holed = true;
                roomService.broadcastRest(
                    userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                    holeIdx, userSession.getUid(), holeRest,
                    new RoomService.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String msg) {
                            Gdx.app.error("Multiplayer", "broadcastRest (hole) failed — queuing for retry: " + msg);
                            pendingHoleRest = holeRest;
                            pendingHoleRestHole = holeIdx;
                        }
                    });
                mpScoreboardVisible = true;
                mpAllPlayersFinishedHole = false;
                mpIsFinalScoreboard = false;
                mpScoreboardPollTimer = 0f;
                if (mpScoreboardOverlay != null) mpScoreboardOverlay.resetHidden();
                refreshMpScoreboard();
                setupInputProcessor(); // add scoreboard stage to multiplexer
            } else {
                active.advanceHole();
                sessionManager.saveActive();
            }
        }

        if (tutorialSession != null) {
//            tutorialSession.controller.advance(); // STEP_8_PUTT → DONE (or wherever we are)
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
        } else if (currentState == GameState.MULTIPLAYER_LOBBY) {
            multiplayerLobbyScreen.render(delta);
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

        if (currentState == GameState.DETERM_TEST) {
            updateDetermTest(effDelta, terrain);
        } else if (isGameplayState() || currentState == GameState.PAUSED) {
            updateGameplaySystems(delta, effDelta, particleDelta, terrain);
        }

        updateDaily1Timer(delta);

        terrain.updateCameraOcclusion(camera.position, ball.getPosition(), delta);
        terrain.updateFlag(camera.position, ball.getPosition());

        // Tutorial: once ball is stationary on the green, prompt the putt
        if (tutorialSession != null
                && tutorialSession.controller.getCurrentStep() == TutorialController.Step.STEP_7_WATCH
                && ball != null && ball.getState() == org.example.ball.Ball.State.STATIONARY) {
            if (terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z)
                    == org.example.terrain.Terrain.TerrainType.GREEN) {
                tutorialSession.controller.advance();
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
            Ball.State currentBallState = ball.getState();
            if (currentBallState == Ball.State.STATIONARY && prevBallState != Ball.State.STATIONARY) {
                GameSession active = sessionManager.getActive();
                if (active != null) {
                    Vector3 pos = ball.getPosition();
                    active.setBallRestPosition(pos.x, pos.y, pos.z);
                    sessionManager.saveActive();
                }
                if (isMultiplayer && localStrokeNum > 0 && currentMultiplayerRoom != null) {
                    RoomService.RestPacket rp = new RoomService.RestPacket();
                    Vector3 pos = ball.getPosition();
                    rp.x = pos.x; rp.y = pos.y; rp.z = pos.z;
                    rp.strokeNum = localStrokeNum;
                    int holeIdx = sessionManager.getActive() != null
                            ? sessionManager.getActive().getCurrentHoleIndex() : 0;
                    roomService.broadcastRest(
                        userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                        holeIdx, userSession.getUid(), rp,
                        new RoomService.SimpleCallback() {
                            @Override public void onSuccess() {}
                            @Override public void onFailure(String msg) {
                                Gdx.app.error("Multiplayer", "broadcastRest failed — queuing for retry: " + msg);
                                pendingBallRest = rp;
                                pendingBallRestHole = holeIdx;
                            }
                        });
                }
            }
            prevBallState = currentBallState;
        }

        // Continuous 500ms shot poll + per-ball physics (multiplayer only).
        if (isMultiplayer && !remoteBalls.isEmpty()) {
            Vector3 wind = (currentLevelData != null) ? currentLevelData.getWind() : zeroWind;
            for (RemoteBall rb : remoteBalls.values()) rb.update(effDelta, terrain, wind);

            shotPollTimer += effDelta;
            if (shotPollTimer >= SHOT_POLL_INTERVAL) {
                shotPollTimer = 0f;
                pollRemoteShots();
            }
        }

        if (isMultiplayer && currentMultiplayerRoom != null) {
            hud.updateLiveScoreboard(buildLiveScoreEntries());
        }

        if (isMultiplayer && mpScoreboardVisible) {
            mpScoreboardPollTimer += effDelta;
            if (mpScoreboardPollTimer >= SHOT_POLL_INTERVAL) {
                mpScoreboardPollTimer = 0f;
                pollMultiplayerScoreboard();
            }
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
            if (isMultiplayer && currentMultiplayerRoom != null) {
                broadcastMultiplayerShot();
                pollRemoteShots();
            }
            // Tutorial: shot has fired — move to the "watch" step
            if (tutorialSession != null
                    && (tutorialSession.controller.getCurrentStep() == TutorialController.Step.STEP_6_HIT || tutorialSession.controller.getCurrentStep() == TutorialController.Step.STEP_10_AIM)) {
                tutorialSession.controller.advance(); // → STEP_7_WATCH
            }
        }
        PhysicsProfiler.endSection("ShotControllerCharge");
    }

    private void updateDetermTest(float effDelta, Terrain terrain) {
        if (ball == null) return;
        switch (determPhase) {
            case 0: { // compute tee-to-hole direction, attach recorder, fire shot 1
                Vector3 hole = terrain.getHolePosition();
                determShotDir.set(hole).sub(ball.getPosition()).nor();
                determShotDir.y = 0.45f; // loft upward regardless of XZ direction
                determShotDir.nor();
                DeterminismRecorder r = new DeterminismRecorder();
                r.setHeader("determ_shot_1 power=30 wind=zero");
                ball.setRecorder(r);
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, org.example.ball.MinigameResult.Rating.GREAT);
                determPhase = 1;
                break;
            }
            case 1: // flight 1 — wait for stationary (fixed delta for determinism)
                ball.update(1f / 60f, terrain, zeroWind);
                if (ball.getState() == Ball.State.STATIONARY) {
                    determRecording1 = ball.detachRecorder();
                    ball.resetToLastPosition();
                    ball.resetRandom(); // reset RNG so shot 2 matches shot 1 exactly
                    determResultText = "Shot 1 done (" + determRecording1.steps.size() + " steps). Firing shot 2...";
                    determPhase = 2;
                }
                break;
            case 2: { // attach recorder and fire shot 2 with identical inputs
                DeterminismRecorder r2 = new DeterminismRecorder();
                r2.setHeader("determ_shot_2 power=30 wind=zero");
                ball.setRecorder(r2);
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, org.example.ball.MinigameResult.Rating.GREAT);
                determPhase = 3;
                break;
            }
            case 3: // flight 2 — wait for stationary (same fixed delta as shot 1)
                ball.update(1f / 60f, terrain, zeroWind);
                if (ball.getState() == Ball.State.STATIONARY) {
                    DeterminismRecorder.Recording rec2 = ball.detachRecorder();
                    DeterminismRecorder.CompareResult result = DeterminismRecorder.compare(determRecording1, rec2);
                    determLLResult = result.report;
                    Gdx.app.log("DETERM", "L vs L: " + determLLResult);
                    saveDetermFiles(determRecording1, rec2, result.report);
                    // Reset and prepare for local-vs-remote test
                    ball.resetToLastPosition();
                    ball.resetRandom();
                    determResultText = "L vs L: " + determLLResult + "\nStarting L vs R...";
                    determPhase = 4;
                }
                break;
            case 4: { // set up local-vs-remote comparison
                // Create a fresh RemoteBall for this test
                determRemoteBall = new RemoteBall("determ_r", "RemoteTest", particleManager);
                if (currentLevelData != null) determRemoteBall.setLevelSeed(currentLevelData.getSeed());

                // Attach recorders to both
                DeterminismRecorder localRec = new DeterminismRecorder();
                localRec.setHeader("local_LR power=30 wind=zero");
                ball.setRecorder(localRec);

                DeterminismRecorder remoteRec = new DeterminismRecorder();
                remoteRec.setHeader("remote_LR power=30 wind=zero");
                determRemoteBall.setRecorder(remoteRec);

                // Fire local ball
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, org.example.ball.MinigameResult.Rating.GREAT);

                // Deliver identical initial state to RemoteBall via a shot packet
                ShotPacket p = new ShotPacket();
                p.sx = ball.getPosition().x; p.sy = ball.getPosition().y; p.sz = ball.getPosition().z;
                p.vx = ball.getVelocity().x; p.vy = ball.getVelocity().y; p.vz = ball.getVelocity().z;
                p.wx = ball.getSpin().x;      p.wy = ball.getSpin().y;      p.wz = ball.getSpin().z;
                p.strokeNum = 1;
                determRemoteBall.offerPacket(p);

                determResultText = "L vs L: " + determLLResult + "\nL vs R: running...";
                determPhase = 5;
                break;
            }
            case 5: { // flight L vs R — step both at fixed rate until both rest
                ball.update(1f / 60f, terrain, zeroWind);
                if (determRemoteBall != null) determRemoteBall.update(1f / 60f, terrain, zeroWind);

                boolean localDone  = (ball.getState() == Ball.State.STATIONARY);
                boolean remoteDone = (determRemoteBall == null || !determRemoteBall.isInFlight());
                if (localDone && remoteDone) {
                    DeterminismRecorder.Recording lrLocal  = ball.detachRecorder();
                    DeterminismRecorder.Recording lrRemote = determRemoteBall != null
                            ? determRemoteBall.detachRecorder() : new DeterminismRecorder.Recording();
                    DeterminismRecorder.CompareResult lr = DeterminismRecorder.compare(lrLocal, lrRemote);
                    String lrText = lr.report;
                    Gdx.app.log("DETERM", "L vs R: " + lrText);
                    saveDetermLRFiles(lrLocal, lrRemote, lrText);
                    determResultText = "L vs L: " + determLLResult + "\nL vs R: " + lrText
                            + "\n\n[any key to exit]";
                    determPhase = 6;
                }
                break;
            }
            case 6: // combined result — wait for user to exit
                if (inputProcessor.isActionJustPressed(org.example.input.GameInputProcessor.Action.PAUSE) ||
                    inputProcessor.isActionJustPressed(org.example.input.GameInputProcessor.Action.CANCEL_MENU) ||
                    inputProcessor.isActionJustPressed(org.example.input.GameInputProcessor.Action.MAIN_MENU)) {
                    exitToMainMenu();
                }
                break;
            default:
                Gdx.app.error("DETERM", "Unknown determPhase: " + determPhase + " — resetting to 0");
                determPhase = 0;
                break;
        }
        if (cameraController != null) cameraController.update(ball.getPosition(), inputProcessor);
    }

    private void saveDetermFiles(DeterminismRecorder.Recording a, DeterminismRecorder.Recording b, String summary) {
        try {
            Gdx.files.local("determ_record_A.csv").writeString(a.toCsv(), false);
            Gdx.files.local("determ_record_B.csv").writeString(b.toCsv(), false);
            Gdx.files.local("determ_result.txt").writeString(summary + "\n", false);
            Gdx.app.log("DETERM", "Saved L vs L CSV files to local storage");
        } catch (Exception e) {
            Gdx.app.error("DETERM", "Failed to save L vs L CSV files: " + e.getMessage());
        }
    }

    private void saveDetermLRFiles(DeterminismRecorder.Recording local, DeterminismRecorder.Recording remote, String summary) {
        try {
            Gdx.files.local("determ_local_LR.csv").writeString(local.toCsv(), false);
            Gdx.files.local("determ_remote_LR.csv").writeString(remote.toCsv(), false);
            Gdx.files.local("determ_LR_result.txt").writeString(summary + "\n", false);
            Gdx.app.log("DETERM", "Saved L vs R CSV files to local storage");
        } catch (Exception e) {
            Gdx.app.error("DETERM", "Failed to save L vs R CSV files: " + e.getMessage());
        }
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
        for (java.util.Map.Entry<String, RemoteBall> entry : remoteBalls.entrySet()) {
            RemoteBall rb = entry.getValue();
            if (!rb.hasRestPosition() || rb.isHoledOut()) continue;
            java.util.List<com.badlogic.gdx.graphics.g3d.ModelInstance> instances = remoteBallInstances.get(entry.getKey());
            if (instances == null || instances.isEmpty()) continue;

            // Ball body (index 0) — scale up and fade alpha with distance so it's
            // visible from far away but looks normal size up close.
            com.badlogic.gdx.graphics.g3d.ModelInstance ballInst = instances.get(0);
            com.badlogic.gdx.math.Vector3 ballPos = rb.isInFlight() ? rb.getPosition() : rb.getLastRestPosition();
            float rbDist  = camera.position.dst(ballPos);
            float rbT     = Math.max(0f, Math.min(1f, (rbDist - 10f) / 140f)); // 0 at ≤10, 1 at ≥150
            float rbScale = 1f + rbT * 4f;   // 1× up close, 5× at distance
            float rbAlpha = 1f - rbT * 0.4f; // fully opaque up close, 0.6 at distance
            ballInst.transform.setToTranslation(ballPos).scale(rbScale, rbScale, rbScale);
            com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute rbColor =
                    (com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute)
                    ballInst.materials.get(0).get(com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute.Diffuse);
            if (rbColor != null) rbColor.color.a = rbAlpha;
            modelBatch.render(ballInst, environment);

            // Trail (indices 1..trailSize) — only while in flight
            if (rb.isInFlight()) {
                int trailSize = rb.getTrailSize();
                for (int i = 0; i < trailSize && (i + 1) < instances.size(); i++) {
                    com.badlogic.gdx.graphics.g3d.ModelInstance trailInst = instances.get(i + 1);
                    float frac = (float)(i + 1) / trailSize; // 0=oldest, 1=newest
                    float scale = 0.2f + 0.6f * frac;
                    com.badlogic.gdx.math.Vector3 tp = rb.getTrailPoint(i);
                    trailInst.transform.setToTranslation(tp).scale(scale, scale, scale);
                    modelBatch.render(trailInst, environment);
                }
            }
        }
        particleManager.render(modelBatch, environment);
        modelBatch.end();
        if (currentLevelData != null && !isVictory && gameplayState != GameState.PUTTING_GREEN) {
            windManager.render(camera, currentLevelData.getWind());
        }
    }

    private void renderUI() {
        boolean isPractice = (gameplayState == GameState.PRACTICE_RANGE || gameplayState == GameState.PUTTING_GREEN);
        GameSession active = sessionManager.getActive();

        if (currentState == GameState.DETERM_TEST) {
            hud.showDetermTestOverlay(determResultText, determPhase);
        } else if (isVictory && !(isMultiplayer && mpScoreboardVisible)) {
            hud.renderVictory(hud.getShotCount(), currentLevelData, active);
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu(currentLevelData, inputProcessor, active, submissionCoordinator.isSubmissionInProgress());
        } else {
            if (!config.difficulty.hasClubInfo()) showClubInfo = false;
            Terrain terrain = levelManager.getTerrain();
            if (terrain != null) {
                hud.renderPlayingHUD(currentClub, ball, isPractice, currentLevelData, camera, terrain, active, inputProcessor, showClubInfo, shotController);
            }
        }

        com.badlogic.gdx.scenes.scene2d.Stage uiStage =
                (currentState == GameState.START) ? hud.getStartMenuStage() : hud.getStage();

        if (tutorialSession != null) {
            hud.applyTutorialButtonBlock(tutorialSession.controller.isActive() ? tutorialSession.controller.getCurrentStep() : null);
        }

        if (uiStage != null && currentState != GameState.PAUSED) {
            uiStage.act(Gdx.graphics.getDeltaTime());
            uiStage.draw();
        }

        if (mpScoreboardVisible && mpScoreboardOverlay != null) {
            mpScoreboardOverlay.act(Gdx.graphics.getDeltaTime());
            mpScoreboardOverlay.draw();
        }

        if (tutorialSession != null && tutorialSession.controller.getCurrentStep().isOverlayVisible()) {
            com.badlogic.gdx.math.Vector3 wind = (currentLevelData != null) ? currentLevelData.getWind() : null;
            hud.renderTutorialOverlay(tutorialSession.controller.getCurrentStep(), wind);
        }
    }

    // -------------------------------------------------------------------------
    // Multiplayer shot broadcast + replay
    // -------------------------------------------------------------------------

    private void broadcastMultiplayerShot() {
        localStrokeNum++;
        ShotPacket packet = new ShotPacket();
        Vector3 pos = ball.getPosition();
        Vector3 vel = ball.getVelocity();
        Vector3 sp  = ball.getSpin();
        packet.sx = pos.x; packet.sy = pos.y; packet.sz = pos.z;
        packet.vx = vel.x; packet.vy = vel.y; packet.vz = vel.z;
        packet.wx = sp.x;  packet.wy = sp.y;  packet.wz = sp.z;
        packet.strokeNum = localStrokeNum;

        int holeIndex = sessionManager.getActive() != null
                ? sessionManager.getActive().getCurrentHoleIndex() : 0;

        roomService.broadcastShot(
            userSession.getIdToken(),
            currentMultiplayerRoom.roomCode,
            holeIndex,
            userSession.getUid(),
            packet,
            new RoomService.SimpleCallback() {
                @Override public void onSuccess() {}
                @Override public void onFailure(String msg) {
                    Gdx.app.error("Multiplayer", "Shot broadcast failed — queuing for retry: " + msg);
                    PendingShot ps = new PendingShot();
                    ps.packet = packet;
                    ps.holeIndex = holeIndex;
                    pendingShotUploads.add(ps);
                }
            });
    }

    private void pollRemoteShots() {
        if (currentMultiplayerRoom == null) return;
        int holeIndex = sessionManager.getActive() != null
                ? sessionManager.getActive().getCurrentHoleIndex() : 0;
        String myUid = userSession.getUid();

        // --- Retry any uploads that failed since the last poll tick ---

        // Shots: idempotent PUT, safe to re-send. Discard if the hole has advanced.
        if (!pendingShotUploads.isEmpty()) {
            java.util.List<PendingShot> toRetry = new java.util.ArrayList<>(pendingShotUploads);
            pendingShotUploads.clear();
            for (PendingShot ps : toRetry) {
                if (ps.holeIndex != holeIndex) continue; // stale — hole has advanced
                final PendingShot fPs = ps;
                roomService.broadcastShot(
                    userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                    ps.holeIndex, myUid, ps.packet,
                    new RoomService.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String msg) {
                            Gdx.app.error("Multiplayer", "Shot retry failed — re-queuing: " + msg);
                            pendingShotUploads.add(fPs);
                        }
                    });
            }
        }

        // Ball-at-rest position: only the latest matters; discard if hole has advanced.
        if (pendingBallRest != null) {
            if (pendingBallRestHole == holeIndex) {
                final RoomService.RestPacket r = pendingBallRest;
                final int rh = pendingBallRestHole;
                pendingBallRest = null; pendingBallRestHole = -1;
                roomService.broadcastRest(
                    userSession.getIdToken(), currentMultiplayerRoom.roomCode, rh, myUid, r,
                    new RoomService.SimpleCallback() {
                        @Override public void onSuccess() {}
                        @Override public void onFailure(String msg) {
                            Gdx.app.error("Multiplayer", "Ball rest retry failed — re-queuing: " + msg);
                            pendingBallRest = r; pendingBallRestHole = rh;
                        }
                    });
            } else {
                pendingBallRest = null; pendingBallRestHole = -1; // stale
            }
        }

        roomService.fetchShotsForHole(
            userSession.getIdToken(),
            currentMultiplayerRoom.roomCode,
            holeIndex,
            new RoomService.ShotsCallback() {
                @Override public void onSuccess(java.util.List<ShotPacket> packets) {
                    // Discard if the hole advanced while this request was in-flight —
                    // stale shots from hole N arriving after advance to hole N+1 would
                    // pollute consumedStrokes with old strokeNums, blocking all new shots.
                    GameSession g = sessionManager.getActive();
                    if (g != null && g.getCurrentHoleIndex() != holeIndex) return;
                    for (ShotPacket p : packets) {
                        if (p.uid.equals(myUid)) continue;
                        int consumed = consumedStrokes.getOrDefault(p.uid, 0);
                        if (p.strokeNum > consumed) {
                            consumedStrokes.put(p.uid, p.strokeNum);
                            RemoteBall rb = remoteBalls.get(p.uid);
                            if (rb != null) rb.offerPacket(p);
                        }
                    }
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("Multiplayer", "Shot poll failed: " + msg);
                }
            });

        roomService.fetchRestsForHole(
            userSession.getIdToken(),
            currentMultiplayerRoom.roomCode,
            holeIndex,
            new RoomService.RestCallback() {
                @Override public void onSuccess(java.util.List<RoomService.RestPacket> packets) {
                    // Same staleness guard as shots callback.
                    GameSession g = sessionManager.getActive();
                    if (g != null && g.getCurrentHoleIndex() != holeIndex) return;
                    for (RoomService.RestPacket p : packets) {
                        if (p.uid.equals(myUid)) continue;
                        RemoteBall rb = remoteBalls.get(p.uid);
                        if (rb != null) rb.offerRestPacket(p);
                    }
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("Multiplayer", "Rest poll failed: " + msg);
                }
            });
    }

    // -------------------------------------------------------------------------
    // Multiplayer hole progression + scoreboard
    // -------------------------------------------------------------------------

    /** Rebuilds the scoreboard overlay with the latest local data. */
    private void refreshMpScoreboard() {
        if (mpScoreboardOverlay == null || currentMultiplayerRoom == null) return;
        GameSession active = sessionManager.getActive();
        int holeIdx = active != null ? active.getCurrentHoleIndex() : 0;
        // advanceHole() pushes the index past the end when the session finishes;
        // clamp so the overlay always receives a valid hole index.
        if (active != null && !active.getCourseLayout().isEmpty()) {
            holeIdx = Math.min(holeIdx, active.getCourseLayout().size() - 1);
        }
        boolean isHost = userSession.getUid().equals(currentMultiplayerRoom.hostUid);
        mpScoreboardOverlay.update(
            currentMultiplayerRoom.players,
            mpPlayerDifficulties,
            mpHoleScores,
            holeIdx,
            mpAllPlayersFinishedHole,
            mpIsFinalScoreboard,
            isHost);
    }

    /** Polls the room every 0.5s while the scoreboard is showing. */
    private void pollMultiplayerScoreboard() {
        if (currentMultiplayerRoom == null) return;
        roomService.pollRoom(userSession.getIdToken(), currentMultiplayerRoom.roomCode,
            new RoomService.RoomCallback() {
                @Override public void onSuccess(RoomService.RoomState room) {
                    // Merge incoming scores into local cache
                    for (java.util.Map.Entry<String, java.util.Map<Integer, Integer>> e : room.scores.entrySet()) {
                        int[] arr = mpHoleScores.computeIfAbsent(e.getKey(), k -> new int[9]);
                        for (java.util.Map.Entry<Integer, Integer> h : e.getValue().entrySet()) {
                            arr[h.getKey()] = h.getValue();
                        }
                    }
                    // Check if all players have submitted for the current hole
                    GameSession active = sessionManager.getActive();
                    int holeIdx = active != null ? active.getCurrentHoleIndex() : 0;
                    boolean allDone = true;
                    for (String uid : currentMultiplayerRoom.players.keySet()) {
                        java.util.Map<Integer, Integer> s = room.scores.get(uid);
                        if (s == null || !s.containsKey(holeIdx)) { allDone = false; break; }
                    }
                    mpAllPlayersFinishedHole = allDone;

                    // Retry score submission if it failed at hole-out time.
                    // This is what would keep the host's NEXT HOLE button disabled — retrying
                    // here means a brief connection loss at hole-out auto-recovers silently.
                    if (pendingScoreHoleIdx >= 0) {
                        final int rh = pendingScoreHoleIdx;
                        final int rs = pendingScoreStrokes;
                        pendingScoreHoleIdx = -1; pendingScoreStrokes = -1;
                        roomService.submitScore(
                            userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                            userSession.getUid(), rh, rs,
                            new RoomService.SimpleCallback() {
                                @Override public void onSuccess() {}
                                @Override public void onFailure(String msg) {
                                    Gdx.app.error("Multiplayer", "Score retry failed — re-queuing: " + msg);
                                    pendingScoreHoleIdx = rh; pendingScoreStrokes = rs;
                                }
                            });
                    }

                    // Retry hole-out rest broadcast (lets remotes lerp the ball into the hole).
                    if (pendingHoleRest != null && pendingHoleRestHole == holeIdx) {
                        final RoomService.RestPacket r = pendingHoleRest;
                        final int rh = pendingHoleRestHole;
                        pendingHoleRest = null; pendingHoleRestHole = -1;
                        roomService.broadcastRest(
                            userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                            rh, userSession.getUid(), r,
                            new RoomService.SimpleCallback() {
                                @Override public void onSuccess() {}
                                @Override public void onFailure(String msg) {
                                    Gdx.app.error("Multiplayer", "Hole rest retry failed — re-queuing: " + msg);
                                    pendingHoleRest = r; pendingHoleRestHole = rh;
                                }
                            });
                    }

                    boolean isHost = userSession.getUid().equals(currentMultiplayerRoom.hostUid);
                    if (!isHost) {
                        // Detect host advancing to next hole
                        if ("finished".equals(room.state)) {
                            mpIsFinalScoreboard = true;
                        } else if (room.currentHole > holeIdx) {
                            advanceMultiplayerHole();
                            return;
                        }
                    }
                    refreshMpScoreboard();
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("Multiplayer", "Scoreboard poll failed: " + msg);
                }
            });
    }

    /**
     * Builds the live scoreboard entry list for all players in the current multiplayer match.
     * Local player is always first; remote players follow in insertion order.
     */
    private java.util.List<LiveScoreboardActor.ScoreEntry> buildLiveScoreEntries() {
        java.util.List<LiveScoreboardActor.ScoreEntry> entries = new java.util.ArrayList<>();
        GameSession active = sessionManager.getActive();
        int holeIndex = active != null ? active.getCurrentHoleIndex() : 0;
        String localUid = userSession.getUid();

        // Local player
        int localHoleStrokes = active != null ? active.getCurrentHoleStrokes() : 0;
        int localTotal = active != null ? active.getTotalScore() : 0;
        boolean localHoledOut = mpScoreboardVisible;
        entries.add(new LiveScoreboardActor.ScoreEntry(
                userSession.getDisplayName(), localHoleStrokes, localTotal, localHoledOut, true));

        // Remote players
        for (java.util.Map.Entry<String, String> e : currentMultiplayerRoom.players.entrySet()) {
            String uid = e.getKey();
            if (uid.equals(localUid)) continue;
            String name = e.getValue();
            int holeStrokes = consumedStrokes.getOrDefault(uid, 0);
            int[] prevScores = mpHoleScores.get(uid);
            int prevTotal = 0;
            if (prevScores != null) {
                for (int i = 0; i < holeIndex && i < prevScores.length; i++) prevTotal += prevScores[i];
            }
            int total = prevTotal + holeStrokes;
            RemoteBall rb = remoteBalls.get(uid);
            boolean holedOut = rb != null && rb.isHoledOut();
            entries.add(new LiveScoreboardActor.ScoreEntry(name, holeStrokes, total, holedOut, false));
        }
        return entries;
    }

    /** Called by the host's "Next Hole" / "End Match" button. */
    private void onMpNextHole() {
        if (!mpAllPlayersFinishedHole) return;
        GameSession active = sessionManager.getActive();
        int nextHole = (active != null ? active.getCurrentHoleIndex() : 0) + 1;
        boolean isFinalHole = (active != null && active.getMode().holeCount() == nextHole);

        // Grey out the button immediately so the host can't double-fire
        if (mpScoreboardOverlay != null) mpScoreboardOverlay.setNextHoleInFlight(true);
        refreshMpScoreboard();

        if (isFinalHole) {
            // Mark match finished in RTDB, then advance locally to final scoreboard
            roomService.setFinished(userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                new RoomService.SimpleCallback() {
                    @Override public void onSuccess() { advanceMultiplayerHole(); }
                    @Override public void onFailure(String msg) {
                        Gdx.app.error("Multiplayer", "setFinished failed: " + msg);
                        if (mpScoreboardOverlay != null) mpScoreboardOverlay.setNextHoleInFlight(false);
                        refreshMpScoreboard();
                    }
                });
        } else {
            roomService.advanceHole(userSession.getIdToken(), currentMultiplayerRoom.roomCode,
                nextHole,
                new RoomService.SimpleCallback() {
                    @Override public void onSuccess() { advanceMultiplayerHole(); }
                    @Override public void onFailure(String msg) {
                        Gdx.app.error("Multiplayer", "advanceHole failed: " + msg);
                        if (mpScoreboardOverlay != null) mpScoreboardOverlay.setNextHoleInFlight(false);
                        refreshMpScoreboard();
                    }
                });
        }
    }

    /** Advances local session to the next hole (or shows final scoreboard if all holes done). */
    private void advanceMultiplayerHole() {
        GameSession active = sessionManager.getActive();
        if (active != null) active.advanceHole();
        localStrokeNum = 0;
        consumedStrokes.clear();
        resetMultiplayerReplayState();

        if (active != null && active.isFinished()) {
            // All 9 holes done — show final scoreboard
            mpScoreboardVisible = true;
            mpIsFinalScoreboard = true;
            mpAllPlayersFinishedHole = true;
            refreshMpScoreboard();
            setupInputProcessor();
        } else {
            mpScoreboardVisible = false;
            mpAllPlayersFinishedHole = false;
            mpIsFinalScoreboard = false;
            setupInputProcessor();
            startLoadingLevel(GameState.COMPETITIVE, -1);
        }
    }

    private void exitToMainMenu() {
        shotController.reset();
        hud.resetShots();
        isVictory                   = false;
        isMultiplayer               = false;
        currentMultiplayerRoom      = null;
        mpScoreboardVisible         = false;
        mpAllPlayersFinishedHole    = false;
        mpIsFinalScoreboard         = false;
        mpHoleScores.clear();
        mpPlayerDifficulties.clear();
        pendingHoleRest = null; pendingHoleRestHole = -1;
        pendingScoreHoleIdx = -1; pendingScoreStrokes = -1;
        resetMultiplayerReplayState();
        sessionManager.clearActive();
        menuManager.setMenuState(MenuState.MAIN);
        menuManager.setMenuSelection(0);
        cameraController = null;
        if (tutorialSession != null) {
            config.setDifficulty(tutorialSession.preDifficulty);
            tutorialSession = null;
            selectedArchetype = null;
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
    public void onStartDetermTest() {
        determPhase = 0;
        determRecording1 = null;
        determResultText = "Firing shot 1...";
        determLLResult   = "";
        determRemoteBall = null;
        startLoadingLevel(GameState.DETERM_TEST, -1);
    }

    @Override
    public void onStartTutorial() {
        config.setDifficulty(GameConfig.Difficulty.NOVICE);
        tutorialSession = new TutorialSession(new TutorialController(), config.difficulty);
        selectedArchetype = TutorialController.TUTORIAL_ARCHETYPE;
        startLoadingLevel(GameState.PLAYING, TutorialController.TUTORIAL_SEED);
    }

    @Override
    public void onOpenMultiplayerLobby() {
        changeState(GameState.MULTIPLAYER_LOBBY);
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
                state == GameState.PRACTICE_RANGE || state == GameState.PUTTING_GREEN ||
                state == GameState.DETERM_TEST;
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
        if (multiplayerLobbyScreen != null) multiplayerLobbyScreen.resize(width, height);
        if (mpScoreboardOverlay != null) mpScoreboardOverlay.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hud.dispose();
        if (loginScreen != null) loginScreen.dispose();
        if (multiplayerLobbyScreen != null) multiplayerLobbyScreen.dispose();
        shotController.dispose();
        levelManager.dispose();
        ghostManager.dispose();
        particleManager.dispose();
        if (ball != null) ball.dispose();
        if (highlightModel != null) highlightModel.dispose();
        if (remoteBallModel != null) remoteBallModel.dispose();
        if (mpScoreboardOverlay != null) mpScoreboardOverlay.dispose();
    }

    /** Holds all per-tutorial-run state so the 4 individual fields can be replaced by a single nullable reference. */
    private static final class TutorialSession {
        final TutorialController controller;
        final GameConfig.Difficulty preDifficulty;
        float aimStartYaw = Float.NaN;

        TutorialSession(TutorialController controller, GameConfig.Difficulty preDifficulty) {
            this.controller = controller;
            this.preDifficulty = preDifficulty;
        }
    }
}
