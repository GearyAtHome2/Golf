package com.gearygolf.golf;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.utils.viewport.ExtendViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.auth.AuthService;
import com.gearygolf.golf.auth.GoogleSignInProvider;
import com.gearygolf.golf.auth.LoginScreen;
import com.gearygolf.golf.auth.UserSession;
import com.gearygolf.golf.multiplayer.DeterminismRecorder;
import com.gearygolf.golf.multiplayer.MultiplayerCoordinator;
import com.gearygolf.golf.multiplayer.MultiplayerLobbyScreen;
import com.gearygolf.golf.multiplayer.RemoteBall;
import com.gearygolf.golf.multiplayer.RoomService;
import com.gearygolf.golf.multiplayer.ShotPacket;
import com.gearygolf.golf.ball.Ball;
import com.gearygolf.golf.ball.ShotController;
import com.gearygolf.golf.camera.CameraController;
import com.gearygolf.golf.gameManagers.GhostManager;
import com.gearygolf.golf.gameManagers.HazardManager;
import com.gearygolf.golf.gameManagers.LevelManager;
import com.gearygolf.golf.gameManagers.MenuManager;
import com.gearygolf.golf.glamour.ParticleManager;
import com.gearygolf.golf.glamour.WindManager;
import com.gearygolf.golf.hud.HUD;
import com.gearygolf.golf.hud.SwingUIController;
import com.gearygolf.golf.hud.renderer.MainMenuRenderer.MenuState;
import com.gearygolf.golf.input.DesktopInputProcessor;
import com.gearygolf.golf.input.GameInputProcessor;
import com.gearygolf.golf.input.MobileInputProcessor;
import com.gearygolf.golf.performance.PhysicsProfiler;
import com.gearygolf.golf.scoreBoard.CourseType;
import com.gearygolf.golf.scoreBoard.RoundHistoryService;
import com.gearygolf.golf.scoreBoard.SkillRatingStore;
import com.gearygolf.golf.scoreBoard.SubmissionCoordinator;
import com.gearygolf.golf.session.GameSession;
import com.gearygolf.golf.session.SessionManager;
import com.gearygolf.golf.terrain.ITerrainGenerator;
import com.gearygolf.golf.terrain.Terrain;
import com.gearygolf.golf.terrain.level.LevelData;
import com.gearygolf.golf.terrain.level.LevelDataGenerator;
import com.gearygolf.golf.terrain.level.LevelFactory;
import com.gearygolf.golf.shot.ShotExportPacket;
import com.gearygolf.golf.terrain.TerrainGenVersion;
import com.gearygolf.golf.flow.GameFlowController;
import com.gearygolf.golf.flow.GameState;
import com.gearygolf.golf.flow.LevelLifecycleManager;
import com.gearygolf.golf.tutorial.TutorialBridge;
import com.gearygolf.golf.tutorial.TutorialPrefs;
import com.gearygolf.golf.render.RenderOrchestrator;

public class GolfGame extends ApplicationAdapter implements MenuManager.MenuHandler, HazardManager.HazardListener {

    private final GoogleSignInProvider googleSignInProvider;

    public GolfGame() { this(null); }

    public GolfGame(GoogleSignInProvider googleSignInProvider) {
        this.googleSignInProvider = googleSignInProvider;
    }

    private LevelLifecycleManager levelLifecycle;
    private GameFlowController gameFlow;

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
    private SwingUIController swingUI;
    private GameInputProcessor inputProcessor;
    private ShotController shotController;
    private LevelData currentLevelData;
    private Club currentClub = Club.DRIVER;
    private boolean autoSwitchedToPutterThisHole = false;
    private ParticleManager particleManager;
    private WindManager windManager;
    private com.gearygolf.golf.glamour.SoundManager soundManager;
    private float foliageRustleCooldown = 0f;
    private float twigSnapCooldown      = 0f;
    private boolean wasChargingShot   = false; // tracks swing-view camera transition
    private boolean wasImpactCaptured = false; // guards one-shot divot particle spawn
    private Ball.State prevBallState      = Ball.State.STATIONARY;
    private Ball.State prevBallStateToast = Ball.State.STATIONARY;
    private TutorialBridge tutorialBridge;
    private SubmissionCoordinator submissionCoordinator;
    private final RoundHistoryService profileHistoryService = new RoundHistoryService();
    private RenderOrchestrator renderOrchestrator;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);

    //max driver distance on new mode = 527.1yds
    private MultiplayerCoordinator mpCoordinator;

    // Determinism self-test state
    // phases: 0=pre-fire1, 1=flight1, 2=pre-fire2, 3=flight2, 4=L-vs-R setup,
    //         5=L-vs-R flight, 6=combined result
    private int determPhase = 0;
    private DeterminismRecorder.Recording determRecording1;
    private String determResultText  = "";
    private String determLLResult    = "";  // L vs L one-liner
    private RemoteBall determRemoteBall = null;
    private final Vector3 determShotDir = new Vector3();
    private AuthService authService;
    private UserSession userSession;
    private LoginScreen loginScreen;
    private RoomService roomService;
    private MultiplayerLobbyScreen multiplayerLobbyScreen;
    private final com.gearygolf.golf.scoreBoard.DailySubmissionCache dailySubmissionCache = new com.gearygolf.golf.scoreBoard.DailySubmissionCache();

    @Override
    public void create() {
        try {
            initCoreSystems();
            initInputSystems();
            initLevelLifecycle();
            initGameFlow();
            initRenderOrchestrator();
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
        swingUI = hud.getSwingUIController();
        levelManager = new LevelManager();
        levelFactory = new LevelFactory();
        ghostManager = new GhostManager(8);
        menuManager = new MenuManager();
        hazardManager = new HazardManager();
        shotController = new ShotController();
        particleManager = new ParticleManager();
        windManager = new WindManager();
        soundManager = new com.gearygolf.golf.glamour.SoundManager();
        soundManager.setMasterVolume(com.gearygolf.golf.glamour.SoundPrefs.loadMaster());
        soundManager.setSfxVolume(com.gearygolf.golf.glamour.SoundPrefs.loadSfx());
        soundManager.setAmbientVolume(com.gearygolf.golf.glamour.SoundPrefs.loadAmbient());
        soundManager.setArcadeFlightSoundsEnabled(com.gearygolf.golf.glamour.SoundPrefs.loadArcadeFlight());
        config.cinematicMode  = com.gearygolf.golf.glamour.SoundPrefs.loadCinematicMode();
        config.swingModeNew   = com.gearygolf.golf.glamour.SoundPrefs.loadSwingModeNew();
        if (config.difficulty.requiresNewSwing()) config.swingModeNew = true;
        soundManager.setBounceScale(com.gearygolf.golf.glamour.SoundPrefs.loadBounce());
        soundManager.setArcadeAirborneScale(com.gearygolf.golf.glamour.SoundPrefs.loadArcadeAirborne());
        soundManager.setAirWhooshScale(com.gearygolf.golf.glamour.SoundPrefs.loadAirWhoosh());
        soundManager.setAmbientTreesScale(com.gearygolf.golf.glamour.SoundPrefs.loadAmbTrees());
        soundManager.setAmbientWaterScale(com.gearygolf.golf.glamour.SoundPrefs.loadAmbWater());
        soundManager.setBirdsongScale(com.gearygolf.golf.glamour.SoundPrefs.loadBirdsong());
        soundManager.prewarm();
        shotController.setSoundManager(soundManager);
        menuManager.setSoundManager(soundManager);
        hud.setSoundManager(soundManager);
        particleManager.setSoundManager(soundManager);
        sessionManager = new SessionManager(config);

        hud.setLeaderboardPlayDailyCallback(mode -> {
            GameSession session = switch (mode) {
                case DAILY_PAR3 -> sessionManager.getDailyPar3();
                case DAILY_PAR4 -> sessionManager.getDailyPar4();
                case DAILY_PAR5 -> sessionManager.getDailyPar5();
                default -> null;
            };
            int pendingMode = switch (mode) {
                case DAILY_PAR4 -> 4;
                case DAILY_PAR5 -> 5;
                default -> 3;
            };
            selectDaily(session, pendingMode);
        });

        authService = new AuthService();
        userSession = new UserSession();
        userSession.load();
        // Pre-load daily sessions using the uid already stored in prefs — don't wait for async
        // auto-login. This ensures "CONTINUE" buttons appear even if token exchange is slow/fails.
        if (userSession.isLoggedIn()) {
            sessionManager.reloadDailySessions(userSession.getUid());
        }

        submissionCoordinator = new SubmissionCoordinator(
                sessionManager, userSession, authService, dailySubmissionCache,
                new RoundHistoryService(),
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
                        gameFlow.exitToMainMenu();
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
            refreshSkillRatingDisplay();
            sessionManager.reloadDailySessions(r.uid);
            uploadPendingStandard18IfPresent();
            dailySubmissionCache.fetch(r.uid, () -> {
                tryAutoRetryPending();
                hud.notifyLeaderboardCacheReady();
            });
            TutorialPrefs.markFirstLoginDone();
            changeState(GameState.START);
        });

        roomService = new RoomService();
        mpCoordinator = new MultiplayerCoordinator(
                roomService, userSession, sessionManager, hud, particleManager, camera, soundManager,
                new MultiplayerCoordinator.Host() {
                    @Override public void onStartLoadingNextHole() { levelLifecycle.startLoadingLevel(GameState.COMPETITIVE, -1); }
                    @Override public void onScoreboardStateChanged() { setupInputProcessor(); }
                    @Override public void onExitToMainMenu() { exitToMainMenu(); }
                });
        multiplayerLobbyScreen = new MultiplayerLobbyScreen(
            hud.getSkin(), roomService, userSession,
            new MultiplayerLobbyScreen.Callback() {
                @Override public void onStartGame(RoomService.RoomState room) {
                    String localUid = userSession.getUid();
                    RoomService.PlayerMeta localMeta = room.playerMeta.get(localUid);
                    GameConfig.Difficulty diff = GameConfig.Difficulty.NOVICE;
                    if (localMeta != null && localMeta.difficulty != null) {
                        try { diff = GameConfig.Difficulty.valueOf(localMeta.difficulty); }
                        catch (Exception ignored) {}
                    }
                    config.setDifficulty(diff);
                    mpCoordinator.startMatch(room, diff);
                    sessionManager.startMultiplayerMatch(room.seed, diff);
                    levelLifecycle.startLoadingLevel(GameState.COMPETITIVE, -1);
                }
                @Override public void onBack() {
                    changeState(GameState.START);
                }
            });

        tutorialBridge = new TutorialBridge(hud, dailySubmissionCache,
                new TutorialBridge.Listener() {
                    @Override public void onLoadTutorialLevel() { levelLifecycle.startLoadingLevel(GameState.PLAYING, -1); }
                    @Override public void onTutorialComplete()  { exitToMainMenu(); }
                    @Override public void onToggleClubInfo()    { if (gameFlow != null) gameFlow.setShowClubInfo(!gameFlow.isShowClubInfo()); }
                });

        if (userSession.isLoggedIn()) {
            userSession.tryAutoLogin(authService, new AuthService.AuthCallback() {
                @Override
                public void onSuccess(AuthService.AuthResult r) {
                    Gdx.app.log("UserSession", "Auto-login OK — " + r.displayName);
                    hud.setLoggedInUser(r.displayName);
                    refreshSkillRatingDisplay();
                    sessionManager.reloadDailySessions(r.uid);
                    uploadPendingStandard18IfPresent();
                    dailySubmissionCache.fetch(r.uid, () -> {
                        tryAutoRetryPending();
                        hud.notifyLeaderboardCacheReady();
                    });
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

    private void initLevelLifecycle() {
        levelLifecycle = new LevelLifecycleManager(
                levelManager, levelFactory, particleManager, sessionManager,
                hazardManager, ghostManager, shotController, hud,
                tutorialBridge, mpCoordinator, config, camera, soundManager,
                inputProcessor,
                new LevelLifecycleManager.Listener() {
                    @Override public void onStartLoading(GameState targetState) {
                        changeState(GameState.LOADING);
                    }
                    @Override public void onClearBall() {
                        if (ball != null) { ball.dispose(); ball = null; }
                    }
                    @Override public void onBallReady(Ball b, CameraController cam, Ball.State toastState) {
                        ball = b;
                        cameraController = cam;
                        prevBallState      = Ball.State.STATIONARY;
                        prevBallStateToast = toastState;
                        if (renderOrchestrator != null) renderOrchestrator.resetAmbientTimer();
                    }
                    @Override public void onLevelReady(GameState targetState, LevelData levelData, Club defaultClub) {
                        currentLevelData = levelData;
                        currentClub      = defaultClub;
                        autoSwitchedToPutterThisHole = false;
                        if (gameFlow != null) gameFlow.resetForNewLevel();
                        setupInputProcessor();
                        changeState(targetState);
                    }
                    @Override public void onBallReset(Ball b) {
                        if (gameFlow != null) gameFlow.setVictory(false);
                        if (cameraController != null) cameraController.update(b.getPosition(), inputProcessor);
                    }
                });
    }

    private void initGameFlow() {
        gameFlow = new GameFlowController(
                hud, soundManager, levelLifecycle, submissionCoordinator,
                menuManager, sessionManager, tutorialBridge, mpCoordinator,
                config, shotController, authService, userSession, dailySubmissionCache,
                this, // MenuManager.MenuHandler
                new GameFlowController.Host() {
                    @Override public void onSetupInputProcessor()    { setupInputProcessor(); }
                    @Override public void onCameraSetPaused(boolean p) {
                        if (cameraController != null) cameraController.setPaused(p);
                    }
                    @Override public void onCameraSetNull()          { cameraController = null; }
                    @Override public void onLoginReset()             { loginScreen.reset(); }
                });
    }

    private void initRenderOrchestrator() {
        renderOrchestrator = new RenderOrchestrator(
                modelBatch, environment, camera,
                hud, swingUI, levelManager,
                shotController, particleManager,
                ghostManager, windManager,
                soundManager,
                mpCoordinator, tutorialBridge,
                sessionManager, submissionCoordinator,
                gameFlow, levelLifecycle,
                config);
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
        renderOrchestrator.initHighlight(mb);
        mpCoordinator.initModels(mb);
    }

    private void setupInputProcessor() {
        com.badlogic.gdx.InputMultiplexer multiplexer = new com.badlogic.gdx.InputMultiplexer();

        GameState cs = gameFlow != null ? gameFlow.getCurrentState() : GameState.LOGIN;
        com.badlogic.gdx.scenes.scene2d.Stage activeStage;
        if (cs == GameState.LOGIN) {
            activeStage = loginScreen.getStage();
        } else if (cs == GameState.MULTIPLAYER_LOBBY) {
            activeStage = multiplayerLobbyScreen.getStage();
        } else if (cs == GameState.START) {
            activeStage = hud.getStartMenuStage();
        } else if (cs == GameState.PAUSED) {
            activeStage = hud.getPauseMenuStage();
        } else if (isOverlayState(cs)) {
            activeStage = null; // overlay input handled via Gdx.input directly; no stage needed
        } else {
            activeStage = hud.getStage();
        }

        if (mpCoordinator.isScoreboardVisible()) {
            multiplexer.addProcessor(mpCoordinator.resizeAndGetScoreboardStage(
                    Gdx.graphics.getWidth(), Gdx.graphics.getHeight()));
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
        gameFlow.changeState(newState);
    }

    /**
     * Handles club selection scrolling/buttons. Called each frame from render().
     * Kept in GolfGame because currentClub ownership stays here until Step 5.
     */
    private void updateClubSelection() {
        if (!GameFlowController.isGameplayState(gameFlow.getCurrentState())) return;
        if (gameFlow.isVictory()) return;
        if (levelLifecycle.isShotReplayReady()) return;
        if (shotController.isCharging()) return;
        if (inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION)) return;

        int direction = 0;
        if (!Platform.isAndroid() && !config.cinematicMode) {
            float scroll = inputProcessor.getActionValue(GameInputProcessor.Action.SCROLL_Y);
            if (scroll != 0) direction = scroll > 0 ? 1 : -1;
        } else if (Platform.isAndroid()) {
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

    private void refreshSkillRatingDisplay() { gameFlow.refreshSkillRatingDisplay(); }
    private void uploadPendingStandard18IfPresent() { gameFlow.uploadPendingStandard18IfPresent(); }
    private void tryAutoRetryPending() { gameFlow.tryAutoRetryPending(); }

    private void triggerVictory() {
        Terrain terrain = levelManager.getTerrain();
        gameFlow.setVictory(true);
        float vel = ball.getVelocity().len();
        ball.getVelocity().setZero();
        if (soundManager != null) soundManager.playBallCup(vel);
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
        if (levelLifecycle.getGameplayState() == GameState.COMPETITIVE && active != null) {
            if (mpCoordinator.isActive()) {
                mpCoordinator.onVictory(terrain, active.getCurrentHoleIndex(), hud.getShotCount());
            } else {
                active.advanceHole();
                sessionManager.saveActive();
            }
        }

        // For L3: if victory fires while hint steps are still showing, advance past them
        // so we land on STEP_L3_6_CONGRATS directly.
        tutorialBridge.onVictory();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        inputProcessor.update(delta);
        gameFlow.handleInput(ball, levelManager.getTerrain(), cameraController, inputProcessor, currentLevelData, currentClub);
        updateClubSelection();

        gameViewport.apply();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        GameState cs = gameFlow.getCurrentState();
        if (cs == GameState.LOGIN) {
            loginScreen.render();
        } else if (cs == GameState.MULTIPLAYER_LOBBY) {
            multiplayerLobbyScreen.render(delta);
        } else if (cs == GameState.START) {
            if (soundManager != null) soundManager.updateMenu(delta);
            hud.renderStartMenu(menuManager, this, sessionManager.getCompetitiveSessions(), dailySubmissionCache, soundManager);
        } else if (cs == GameState.SOUND_SETTINGS) {
            hud.renderSoundSettings(config);
        } else if (cs == GameState.PROFILE_SCREEN) {
            hud.renderProfileScreen(profileHistoryService, userSession.getUid(), userSession.getIdToken());
        } else if (cs == GameState.RANK_INFO) {
            hud.renderRankInfo();
        } else if (cs == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (cs == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else if (cs == GameState.LOADING) {
            hud.renderLoadingScreen();
        } else {
            updateLogic(delta);
            renderOrchestrator.renderScene(ball, cameraController, currentLevelData);
            renderOrchestrator.renderUI(ball, cameraController, currentLevelData, currentClub, inputProcessor, determResultText, determPhase);
        }
        if (inputProcessor instanceof MobileInputProcessor) ((MobileInputProcessor) inputProcessor).resetDrags();
    }

    private void updateLogic(float delta) {
        Terrain terrain = levelManager.getTerrain();
        if (terrain == null) return;

        GameState cs = gameFlow.getCurrentState();
        boolean victory = gameFlow.isVictory();
        float speedMultiplier = config.getGameSpeed();
        float effDelta = (cs == GameState.PAUSED) ? 0 : delta * speedMultiplier;
        float particleDelta = (victory || cs == GameState.PAUSED) ? delta * 0.06f : delta * speedMultiplier;

        if (cs == GameState.DETERM_TEST) {
            updateDetermTest(effDelta, terrain);
        } else if (isGameplayState() || cs == GameState.PAUSED) {
            updateGameplaySystems(delta, effDelta, particleDelta, terrain);
        }

        updateDaily1Timer(delta);

        terrain.updateCameraOcclusion(camera.position, ball.getPosition(), delta);
        terrain.updateFlag(camera.position, ball.getPosition());

        // Tutorial watch-step transitions (L1 green putt prompt, L2/L3 landing checks).
        // STEP_L2_5_WATCH is silent until triggerVictory fires (ball goes in hole).
        tutorialBridge.updateWatchSteps(ball, terrain);
    }

    /**
     * Advances the elapsed timer for DAILY_PAR3/4/5 modes when gameplay is active.
     */
    private void updateDaily1Timer(float delta) {
        if (levelLifecycle.getGameplayState() != GameState.COMPETITIVE
                || gameFlow.getCurrentState() == GameState.PAUSED
                || gameFlow.isVictory()) return;
        GameSession active = sessionManager.getActive();
        if (active == null) return;
        GameSession.GameMode m = active.getMode();
        if (m == GameSession.GameMode.DAILY_PAR3
         || m == GameSession.GameMode.DAILY_PAR4
         || m == GameSession.GameMode.DAILY_PAR5) {
            active.addElapsedTime(delta);
        }
    }

    private void updateGameplaySystems(float delta, float effDelta, float particleDelta, Terrain terrain) {
        Vector3 currentWind = (levelLifecycle.getGameplayState() == GameState.PUTTING_GREEN || currentLevelData == null) ? zeroWind : currentLevelData.getWind();

        GameState cs = gameFlow.getCurrentState();
        boolean victory = gameFlow.isVictory();

        if (cs != GameState.PAUSED) {
            windManager.update(effDelta, currentWind, camera.position);
            if (soundManager != null) {
                soundManager.update(effDelta, currentWind, camera.position);
                renderOrchestrator.updateAmbientPositions(effDelta, terrain);
            }

            if (!victory && !(levelLifecycle.getGameplayState() == GameState.SHOT_REPLAY && levelLifecycle.isShotReplayReady())) {
                updateShotLogic(delta, terrain);
            }

            ball.update(effDelta, terrain, currentWind);

            if (soundManager != null) {
                com.gearygolf.golf.terrain.Terrain.TerrainType surface =
                        terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
                float bounceImpact = ball.consumeBounceImpact();
                if (bounceImpact > 0) {
                    com.gearygolf.golf.ball.Ball.Interaction interaction = ball.getLastInteraction();
                    if (interaction == com.gearygolf.golf.ball.Ball.Interaction.WOOD_OBJECT) {
                        soundManager.playBallBounceWood(camera.position, ball.getPosition(), bounceImpact);
                    } else {
                        com.gearygolf.golf.terrain.Terrain.TerrainType bounceSurface =
                                interaction == com.gearygolf.golf.ball.Ball.Interaction.STONE_OBJECT
                                ? com.gearygolf.golf.terrain.Terrain.TerrainType.STONE : surface;
                        Gdx.app.log("BOUNCE", String.format(
                                "playBounce impact=%.3f surface=%s interaction=%s pos=(%.2f,%.2f,%.2f)",
                                bounceImpact, bounceSurface, interaction, ball.getPosition().x, ball.getPosition().y, ball.getPosition().z));
                        soundManager.playBallBounce(camera.position, ball.getPosition(), bounceImpact, bounceSurface);
                    }
                }
                boolean isAirborne = ball.getState() == Ball.State.AIR;
                soundManager.updateFlightSound(effDelta, ball.getVelocity().len(), isAirborne, camera.position, ball.getPosition());
                float rollSpeed = (ball.getState() == Ball.State.ROLLING) ? ball.getVelocity().len() : 0f;
                if (ball.getState() == Ball.State.ROLLING) Gdx.app.log("ROLL", "state=ROLLING speed=" + rollSpeed);
                soundManager.updateBallRoll(effDelta, camera.position, ball.getPosition(), rollSpeed, surface);

                float foliageSpeed = ball.consumeFoliageRustleSpeed();
                soundManager.updateFoliageRustle(effDelta, foliageSpeed > 0);
                foliageRustleCooldown -= effDelta;
                if (foliageSpeed > 0 && foliageRustleCooldown <= 0f) {
                    soundManager.playFoliageRustle(camera.position, ball.getPosition(), foliageSpeed);
                    foliageRustleCooldown = com.badlogic.gdx.math.MathUtils.lerp(0.30f, 0.12f,
                            com.badlogic.gdx.math.MathUtils.clamp(foliageSpeed / 20f, 0f, 1f));
                }

                float snapSpeed = ball.consumeTwigSnapSpeed();
                twigSnapCooldown -= effDelta;
                if (snapSpeed > 0 && twigSnapCooldown <= 0f) {
                    soundManager.playTwigSnap(camera.position, ball.getPosition(), snapSpeed);
                    twigSnapCooldown = 0.35f;
                }
            }
        }

        if (cameraController != null) {
            boolean chargingNow = shotController != null && shotController.isCharging();
            if (chargingNow && !wasChargingShot) {
                // Capture aim direction and terrain type at the moment charging starts.
                cameraController.setSwingAimDir(camera.direction);
                com.gearygolf.golf.terrain.Terrain.TerrainType swingTerrain =
                        terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
                swingUI.setTerrainType(swingTerrain);
                // No divot on tee (ball is elevated — can't strike the ground first).
                swingUI.setDivotEnabled(clubMakesDivot(currentClub)
                        && swingTerrain != com.gearygolf.golf.terrain.Terrain.TerrainType.TEE);
                // Seed attack angle from the current club's natural arc-bottom position.
                swingUI.setNaturalAttackAngle(currentClub.naturalAttackAngleDeg);
                wasImpactCaptured = false;
                boolean isPutter = currentClub == Club.PUTTER;
                com.gearygolf.golf.ball.SwingDifficultyCalculator diffParams =
                        com.gearygolf.golf.ball.SwingDifficultyCalculator.build(config.difficulty, isPutter);
                shotController.setDifficultyParams(diffParams);
                swingUI.setFollowThroughThreshold(diffParams.ftAngleThreshold);
                float[] clubSz = com.gearygolf.golf.ball.SwingDifficultyCalculator.clubSize(config.difficulty);
                swingUI.setClubSize(clubSz[0], clubSz[1]);
                swingUI.setFullPowerSpeed(com.gearygolf.golf.ball.ShotController.SWING_FULL_POWER_SPEED);
                if (isPutter) {
                    swingUI.setBaseTempoWindows(5.0f, 10.0f);
                } else {
                    float terrainTempoMult = com.badlogic.gdx.math.MathUtils.clamp(
                            1.0f / swingTerrain.tempoDifficulty, 0.5f, 2.0f);
                    swingUI.setBaseTempoWindows(
                            com.gearygolf.golf.ball.SwingDifficultyCalculator.perfectFraction(config.difficulty, currentClub) * terrainTempoMult,
                            com.gearygolf.golf.ball.SwingDifficultyCalculator.maxFraction(config.difficulty, currentClub) * terrainTempoMult);
                }
            }
            if (!chargingNow) wasImpactCaptured = false;
            wasChargingShot = chargingNow;
            cameraController.setSwingViewActive(config.swingModeNew && chargingNow);
            if (config.swingModeNew) {
                cameraController.setSwingCamAimOffset(swingUI.getCamAimOffset());
            }
            cameraController.update(ball.getPosition(), inputProcessor);
        }

        boolean inSwingView = cameraController != null && cameraController.isInSwingView();
        swingUI.update(inSwingView);

        if (inSwingView && config.particlesEnabled) {
            boolean nowCaptured = swingUI.getAnalyser().isImpactCaptured();
            if (nowCaptured && !wasImpactCaptured) {
                particleManager.spawnDivot(
                        ball.getPosition().cpy(),
                        cameraController.getSwingAimDir(),
                        terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z));
            }
            wasImpactCaptured = nowCaptured;
        }

        if (config.particlesEnabled) particleManager.handleBallInteraction(ball, terrain, camera.position);

        if (!victory) {
            hazardManager.update(effDelta, ball, terrain, levelLifecycle.getGameplayState() == GameState.PRACTICE_RANGE, this);
            if (ball.checkVictory(terrain)) triggerVictory();
        }

        if (!victory && levelLifecycle.getGameplayState() == GameState.COMPETITIVE) {
            Ball.State currentBallState = ball.getState();
            if (currentBallState == Ball.State.STATIONARY && prevBallState != Ball.State.STATIONARY) {
                GameSession active = sessionManager.getActive();
                if (active != null) {
                    Vector3 pos = ball.getPosition();
                    active.setBallRestPosition(pos.x, pos.y, pos.z);
                    sessionManager.saveActive();
                }
                int holeIdx = sessionManager.getActive() != null
                        ? sessionManager.getActive().getCurrentHoleIndex() : 0;
                mpCoordinator.onBallRested(ball, holeIdx);
            }
            prevBallState = currentBallState;
        }

        // Terrain toast: show label on rest, dismiss on shot (all game modes).
        Ball.State toastState = ball.getState();
        if (!victory && toastState == Ball.State.STATIONARY && prevBallStateToast != Ball.State.STATIONARY) {
            com.gearygolf.golf.terrain.Terrain.TerrainType restType =
                    terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
            hud.triggerTerrainToast(restType, ball.getPosition(), camera);
            if (restType == com.gearygolf.golf.terrain.Terrain.TerrainType.GREEN && !autoSwitchedToPutterThisHole) {
                currentClub = Club.PUTTER;
                autoSwitchedToPutterThisHole = true;
            }
            if (cameraController != null) {
                cameraController.rotateTowardHoleIfNeeded(ball.getPosition(), terrain.getHolePosition(), 25f);
            }
        } else if (toastState != Ball.State.STATIONARY && prevBallStateToast == Ball.State.STATIONARY) {
            hud.dismissTerrainToast();
        }
        prevBallStateToast = toastState;

        // Multiplayer: remote ball physics, audio, shot polling, live scoreboard, scoreboard polling.
        if (mpCoordinator.isActive()) {
            Vector3 mpWind = (currentLevelData != null) ? currentLevelData.getWind() : zeroWind;
            mpCoordinator.update(effDelta, terrain, mpWind);
        }

        particleManager.update(particleDelta, terrain);
    }

    private void updateShotLogic(float delta, Terrain terrain) {
        if (hud.wasMinigameCanceled()) shotController.reset();

        PhysicsProfiler.startSection("ShotControllerCharge");
        shotController.setSwingModeNew(config.swingModeNew);
        shotController.setGuidelineAvailable(config.difficulty.hasShotProjection());
        shotController.setPerfectShotEnabled(levelLifecycle.getGameplayState() == GameState.PRACTICE_RANGE);
        if (shotController.update(delta, ball, camera.direction, currentClub, hud, swingUI, terrain, inputProcessor)) {
            ball.snapshotShotStart(); // trailer mode: capture initial state for replay
            levelLifecycle.captureLastShot(ball, currentLevelData);
            if (GameState.PRACTICE_RANGE != levelLifecycle.getGameplayState()) hud.resetSpin();
            hazardManager.setBallHit(true);
            if (levelLifecycle.getGameplayState() == GameState.COMPETITIVE) {
                GameSession active = sessionManager.getActive();
                if (active != null) active.clearBallRestPosition();
            }
            sessionManager.saveActive();
            mpCoordinator.onBallHit(ball);
            // Tutorial: shot has fired — advance to the next step
            tutorialBridge.onShotFired();
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
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, com.gearygolf.golf.ball.MinigameResult.Rating.GREAT);
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
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, com.gearygolf.golf.ball.MinigameResult.Rating.GREAT);
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
                ball.hit(determShotDir, 30f, 0.1f, 1.0f, com.gearygolf.golf.ball.MinigameResult.Rating.GREAT);

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
                if (inputProcessor.isActionJustPressed(com.gearygolf.golf.input.GameInputProcessor.Action.PAUSE) ||
                    inputProcessor.isActionJustPressed(com.gearygolf.golf.input.GameInputProcessor.Action.CANCEL_MENU) ||
                    inputProcessor.isActionJustPressed(com.gearygolf.golf.input.GameInputProcessor.Action.MAIN_MENU)) {
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

    private void exitToMainMenu() {
        gameFlow.exitToMainMenu();
    }

    private void enterSoundSettings()  { gameFlow.enterSoundSettings(); }
    private void enterProfileScreen()  { gameFlow.enterProfileScreen(); }
    private void enterInstructions()   { gameFlow.enterInstructions(); }
    private void enterCameraConfig()   { gameFlow.enterCameraConfig(); }

    @Override
    public void onOutOfBounds() {
        hud.showOutOfBounds();
        levelLifecycle.resetBallToLastShot(ball);
    }

    @Override
    public void onWaterHazard() {
        hud.showWaterHazard();
        levelLifecycle.resetBallToLastShot(ball);
    }

    @Override
    public void onPracticeReset() {
        ghostManager.archiveBall(ball);
        Vector3 tee = levelManager.getTerrain().getTeePosition();
        ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config, 200L);
        ball.setSpinLogging(true);
        hazardManager.setBallHit(false);
        hud.dismissTerrainToast();
    }

    @Override
    public void onStartQuickPlay() {
        levelLifecycle.setSelectedArchetype(null);
        levelLifecycle.startLoadingLevel(GameState.PLAYING, -1);
    }

    @Override
    public void onShowInstructions() {
        enterInstructions();
    }

    @Override
    public void onShowSoundSettings() {
        enterSoundSettings();
    }

    @Override
    public void onShowProfile() {
        enterProfileScreen();
    }

    @Override
    public void onStartWithClipboardSeed() {
        levelLifecycle.setSelectedArchetype(null);
        long seed = -1;
        String clip = Gdx.app.getClipboard().getContents();
        try {
            if (clip != null) seed = Long.parseLong(clip.trim());
        } catch (Exception ignored) {
        }
        levelLifecycle.startLoadingLevel(GameState.PLAYING, seed);
    }

    @Override
    public void onStartWithArchetype(LevelData.Archetype archetype) {
        levelLifecycle.setSelectedArchetype(archetype);
        levelLifecycle.startLoadingLevel(GameState.PLAYING, -1);
    }

    @Override
    public void onSelectStandard18() {
        GameSession standard = sessionManager.getStandard();
        if (standard != null && !standard.isFinished()) {
            sessionManager.setActive(standard);
            config.setDifficulty(standard.getDifficulty());
            levelLifecycle.startLoadingLevel(GameState.COMPETITIVE, -1);
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
    public void onSelectDailyPar3() {
        selectDaily(sessionManager.getDailyPar3(), 3);
    }

    @Override
    public void onSelectDailyPar4() {
        selectDaily(sessionManager.getDailyPar4(), 4);
    }

    @Override
    public void onSelectDailyPar5() {
        selectDaily(sessionManager.getDailyPar5(), 5);
    }

    private void selectDaily(GameSession daily, int pendingMatchMode) {
        if (daily != null && !daily.isFinished()) {
            // In-progress session — resume it
            sessionManager.setActive(daily);
            config.setDifficulty(daily.getDifficulty());
            levelLifecycle.startLoadingLevel(GameState.COMPETITIVE, -1);
        } else if (daily == null) {
            // No session for today — let the player start one
            menuManager.setPendingMatchMode(pendingMatchMode);
            menuManager.setMenuState(MenuState.DIFFICULTY_SELECT);
            menuManager.setMenuSelection(0);
        }
        // daily.isFinished() → already played today; tapping the button does nothing.
        // The menu shows submission status separately; we must not create a new session.
    }

    @Override
    public void onDifficultyFinalized(GameConfig.Difficulty difficulty, int mode) {
        config.setDifficulty(difficulty);
        shotController.setGuidelineEnabled(false);

        switch (mode) {
            case 0 -> sessionManager.startStandardMatch();
            case 1 -> sessionManager.startDaily18();
            case 2 -> sessionManager.startDaily9();
            case 3 -> sessionManager.startDailyPar3();
            case 4 -> sessionManager.startDailyPar4();
            case 5 -> sessionManager.startDailyPar5();
        }

        levelLifecycle.startLoadingLevel(GameState.COMPETITIVE, -1);
    }

    @Override
    public void onStartPracticeRange() {
        levelLifecycle.startLoadingLevel(GameState.PRACTICE_RANGE, -1);
    }

    @Override
    public void onImportShot() {
        levelLifecycle.handleImportShot(this::onImportShot, levelLifecycle::dismissImportOverlay);
    }

    @Override
    public void onStartPuttingGreen() {
        levelLifecycle.startLoadingLevel(GameState.PUTTING_GREEN, -1);
    }

    @Override
    public void onStartDetermTest() {
        determPhase = 0;
        determRecording1 = null;
        determResultText = "Firing shot 1...";
        determLLResult   = "";
        determRemoteBall = null;
        levelLifecycle.startLoadingLevel(GameState.DETERM_TEST, -1);
    }

    @Override
    public void onStartTutorial() {
        config.setDifficulty(GameConfig.Difficulty.NOVICE);
        tutorialBridge.start(config.difficulty);
        levelLifecycle.setSelectedArchetype(null);
        levelLifecycle.startLoadingLevel(GameState.PLAYING, -1);
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

    private boolean isGameplayState(GameState state) { return GameFlowController.isGameplayState(state); }
    private boolean isGameplayState()                 { return GameFlowController.isGameplayState(gameFlow.getCurrentState()); }
    private boolean isOverlayState(GameState state)   { return GameFlowController.isOverlayState(state); }

    @Override
    public void resize(int width, int height) {
        gameViewport.update(width, height, true);
        hud.resize(width, height);
        if (loginScreen != null) loginScreen.resize(width, height);
        if (multiplayerLobbyScreen != null) multiplayerLobbyScreen.resize(width, height);
    }

    @Override
    public void pause() {
        if (soundManager != null) soundManager.onAppPause();
        sessionManager.saveActive();
    }

    @Override
    public void resume() {
        if (soundManager != null) soundManager.onAppResume();
    }

    /**
     * Returns true if this club type produces a divot on contact.
     * Driver and fairway woods use a sweeping/upward strike — no divot.
     * Putter is handled separately (no swing overlay divot mechanic).
     * Everything else (irons, hybrids, wedges) takes a descending strike and makes a divot.
     */
    private static boolean clubMakesDivot(Club club) {
        return club != Club.DRIVER
            && !club.name().startsWith("WOOD_")
            && club != Club.PUTTER;
    }

    @Override
    public void dispose() {
        // Last-chance save: covers low-memory kills where pause() was never called.
        if (sessionManager != null) sessionManager.saveActive();
        modelBatch.dispose();
        hud.dispose();
        if (loginScreen != null) loginScreen.dispose();
        if (multiplayerLobbyScreen != null) multiplayerLobbyScreen.dispose();
        shotController.dispose();
        levelManager.dispose();
        ghostManager.dispose();
        particleManager.dispose();
        if (ball != null) ball.dispose();
        renderOrchestrator.dispose();
        mpCoordinator.dispose();
        if (soundManager != null) soundManager.dispose();
    }

}
