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
import org.example.ball.CompetitiveScore;
import org.example.ball.ShotController;
import org.example.camera.CameraController;
import org.example.gameManagers.GhostManager;
import org.example.gameManagers.LevelManager;
import org.example.glamour.ParticleManager;
import org.example.glamour.WindManager;
import org.example.hud.HUD;
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

import java.util.ArrayList;
import java.util.List;

public class GolfGame extends ApplicationAdapter {

    private enum GameState {START, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS, CAMERA_CONFIG}

    private GameState currentState = GameState.START;
    private GameState previousState = GameState.PLAYING;
    private GameState returnState = GameState.PLAYING;

    private GhostManager ghostManager;
    private float resetTimer = 0f;
    private static final float RESET_DELAY = 1.0f;
    private boolean hasCurrentBallBeenHit = false;

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

    private int menuSelection = 0;
    private Club currentClub = Club.DRIVER;
    private ParticleManager particleManager;
    private WindManager windManager;
    private boolean isVictory = false;
    private boolean showClubInfo = false;

    private Model highlightModel;
    private ModelInstance highlightInstance;
    private final Vector3 zeroWind = new Vector3(0, 0, 0);

    private List<LevelData> competitiveCourse = new ArrayList<>();
    private int currentHoleIndex = 0;
    private CompetitiveScore competitiveScore;
    private final Vector3 tempV3 = new Vector3();

    @Override
    public void create() {
        try {
            // MOVE VIEWPORT INITIALIZATION TO THE VERY TOP
            setupCamera();
            setupEnvironment();

            modelBatch = new ModelBatch();
            hud = new HUD(config);
            levelManager = new LevelManager();
            levelFactory = new LevelFactory();
            ghostManager = new GhostManager(8);
            shotController = new ShotController();
            particleManager = new ParticleManager();
            windManager = new WindManager();

            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
                inputProcessor = new MobileInputProcessor();
                hud.setupMobileUI((MobileInputProcessor) inputProcessor);
            } else {
                inputProcessor = new DesktopInputProcessor();
            }

            setupHighlight();
            setupInputProcessor();
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        if (currentState == GameState.COMPETITIVE && !competitiveCourse.isEmpty()) {
            currentLevelData = competitiveCourse.get(currentHoleIndex);
            ITerrainGenerator generator = new ClassicGenerator(currentLevelData);
            result = new LevelFactory.LevelCreationResult(generator, currentLevelData, Club.DRIVER, currentLevelData.getWaterLevel(), currentLevelData.getDistance());
        } else {
            result = levelFactory.createLevel(mode, manualSeed);
            currentLevelData = result.data;
        }

        currentClub = result.defaultClub;
        levelManager.buildLevel(result.generator, result.waterLevel, result.distance);

        setupSpawnAndPins();
        setupInputProcessor();

        hud.resetShots();
        isVictory = false;
        resetTimer = 0f;
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

        hasCurrentBallBeenHit = false;
        refreshCameraController(hole);
    }

    private void refreshCameraController(Vector3 lookAtTarget) {
        cameraController = new CameraController(camera, 12f, ball.getPosition(), config.cameraSettings);
        cameraController.setOrientation(lookAtTarget);
        cameraController.update(ball.getPosition(), inputProcessor);
    }

    private void setupInputProcessor() {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            com.badlogic.gdx.InputMultiplexer multiplexer = new com.badlogic.gdx.InputMultiplexer();

            // Add stages only if they are not null OR HUD can provide a placeholder
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
            // For INSTRUCTIONS and CAMERA_CONFIG, we don't add a stage, 
            // only the GestureDetector for scrolling via inputProcessor
            multiplexer.addProcessor(new com.badlogic.gdx.input.GestureDetector((com.badlogic.gdx.input.GestureDetector.GestureListener) inputProcessor));
            Gdx.input.setInputProcessor(multiplexer);
        } else {
            Gdx.input.setInputProcessor((com.badlogic.gdx.InputProcessor) inputProcessor);
        }
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        inputProcessor.update(delta);

        // 1. Process Input first
        handleInput();

        // 2. Clear Screen
        gameViewport.apply();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        boolean showMouse = (currentState == GameState.START || currentState == GameState.PAUSED ||
                currentState == GameState.INSTRUCTIONS || currentState == GameState.CAMERA_CONFIG || isVictory);
        Gdx.input.setCursorCatched(!showMouse);

        if (currentState == GameState.START) {
            hud.renderStartMenu(menuSelection);
        } else if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (currentState == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else {
            // 3. Update & Profile Physics/Logic
            updateLogic(delta);

            // 4. Render the 3D Scene
            renderScene();

            // 5. Render HUD on top
            renderUI();
        }

//        PhysicsProfiler.updateAndLog(delta);

        if (inputProcessor instanceof MobileInputProcessor) {
            ((MobileInputProcessor) inputProcessor).resetDrags();
        }
    }

    private void updateLogic(float delta) {
        Terrain terrain = levelManager.getTerrain();
        if (!isVictory && isGameplayState()) {
            float currentMult = config.getGameSpeed();
            float effDelta = delta * currentMult;

            Vector3 currentWind = (currentState == GameState.PUTTING_GREEN || currentLevelData == null) ? zeroWind : currentLevelData.getWind();

            windManager.update(effDelta, currentWind, camera.position);

            if (hud.wasMinigameCanceled()) shotController.reset();

            // Profile the Shot Charging logic (input handling/vector math)
            PhysicsProfiler.startSection("ShotControllerCharge");
            if (ball.getState() == Ball.State.STATIONARY || shotController.isCharging()) {
                if (shotController.update(delta, ball, camera.direction, currentClub, hud, terrain, inputProcessor)) {
                    hud.incrementShots();
                    hasCurrentBallBeenHit = true;
                    shotController.update(0, ball, camera.direction, currentClub, hud, terrain, inputProcessor);
                }
            }
            PhysicsProfiler.endSection("ShotControllerCharge");

            // This call now internally profiles several sub-sections
            ball.update(effDelta, terrain, currentWind);

            cameraController.update(ball.getPosition(), inputProcessor);

            if (config.particlesEnabled) {
                // Profile the "global" particle interaction check
                PhysicsProfiler.startSection("ParticleInteractions");
                particleManager.handleBallInteraction(ball, terrain);
                PhysicsProfiler.endSection("ParticleInteractions");
            }

            handleBallStationaryLogic(delta);
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

    private void handleBallStationaryLogic(float delta) {
        Terrain terrain = levelManager.getTerrain();

        // LOGIC FIX: Don't wipe the timer if we are in the air BUT in water.
        // This allows the timer to persist through skips/bounces.
        if (ball.getState() == Ball.State.AIR && !ball.isInWater(terrain)) {
            resetTimer = 0f;
            return;
        }

        if (currentState != GameState.PRACTICE_RANGE) {
            if (checkInstantHazards(terrain)) return;
        }

        updateResetTimer(delta, terrain);
    }

    private boolean checkInstantHazards(Terrain terrain) {
        Vector3 pos = ball.getPosition();

        if (terrain.isPointOutOfBounds(pos.x, pos.z)) {
            hud.showOutOfBounds();
            hud.incrementShots();
            resetBallToLastShot();
            resetTimer = 0f;
            return true;
        }
        return false;
    }

    private void updateResetTimer(float delta, Terrain terrain) {
        if (shouldActiveTimer(terrain)) {
            resetTimer += delta;

            if (resetTimer >= RESET_DELAY) {
                handleTimerThresholdReached(terrain);
            }
        } else {
            resetTimer = 0f;
        }
    }

    private boolean shouldActiveTimer(Terrain terrain) {
        if (!hasCurrentBallBeenHit) return false;

        boolean inWater = ball.isInWater(terrain);
        boolean isStationary = ball.getState() == Ball.State.STATIONARY;
        boolean isPractice = (currentState == GameState.PRACTICE_RANGE);

        return inWater || isPractice || isStationary;
    }

    private void handleTimerThresholdReached(Terrain terrain) {
        if (currentState == GameState.PRACTICE_RANGE) {
            resetPracticeBall(terrain);
        } else if (ball.isInWater(terrain)) {
            hud.showWaterHazard();
            hud.incrementShots();
            resetBallToLastShot();
            resetTimer = 0f;
        }
    }

    private void resetPracticeBall(Terrain terrain) {
        ghostManager.archiveBall(ball);
        Vector3 tee = terrain.getTeePosition();
        ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager, config);
        hasCurrentBallBeenHit = false;
        resetTimer = 0f;
    }

    private void resetBallToLastShot() {
        if (ball != null) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.RESET_BALL) && hasCurrentBallBeenHit) {
                hud.incrementShots();
            }
            ball.resetToLastPosition();
            ball.setState(Ball.State.STATIONARY);
            shotController.reset();
            hud.cancelMinigame();
            hasCurrentBallBeenHit = false;
            isVictory = false;
            resetTimer = 0f;
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

        if (currentState == GameState.COMPETITIVE && competitiveScore != null) {
            competitiveScore.recordStroke(currentHoleIndex, hud.getShotCount());
        }
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
        boolean isComp = (currentState == GameState.COMPETITIVE);

        if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions(inputProcessor);
        } else if (currentState == GameState.CAMERA_CONFIG) {
            hud.renderCameraConfig(inputProcessor);
        } else if (isVictory) {
            hud.renderVictory(hud.getShotCount(), currentLevelData, isComp ? competitiveScore : null);
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu(currentLevelData, inputProcessor);
        } else {
            hud.renderPlayingHUD(currentClub, ball, isPractice, currentLevelData, camera, levelManager.getTerrain(), isComp ? competitiveScore : null, inputProcessor, showClubInfo, shotController);
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
            handleStartMenuInput();
        } else if (currentState == GameState.INSTRUCTIONS) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU)) {
                currentState = returnState;
                if (cameraController != null) {
                    cameraController.updateCursorState();
                }
            }
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
                // Use the stage/HUD's viewport to unproject touch coordinates
                tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                hud.getStage().getViewport().unproject(tempV3);
                if (!hud.isTouchInsideInstructions(tempV3.x, tempV3.y)) {
                    System.out.println("closing from hud");
                    currentState = returnState;
                    if (cameraController != null) cameraController.updateCursorState();
                }
            }
        } else if (currentState == GameState.CAMERA_CONFIG) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CANCEL_MENU)) {
                currentState = returnState;
                if (cameraController != null) {
                    cameraController.updateCursorState();
                }
            }
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
                tempV3.set(Gdx.input.getX(), Gdx.input.getY(), 0);
                hud.getStage().getViewport().unproject(tempV3);
                if (!hud.isTouchInsideCameraConfig(tempV3.x, tempV3.y)) {
                    currentState = returnState;
                    if (cameraController != null) cameraController.updateCursorState();
                }
            }
        } else {
            handleGameplayInput();
        }

        if (currentState != oldState && Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            setupInputProcessor();
        }
    }

    private void exitToMainMenu() {
        shotController.reset();
        hud.resetShots();
        currentState = GameState.START;
        currentHoleIndex = 0;
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

    private void handleStartMenuInput() {
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.MENU_UP))
            menuSelection = (menuSelection - 1 + 6) % 6;
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.MENU_DOWN))
            menuSelection = (menuSelection + 1) % 6;

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.MENU_SELECT)) {
            switch (menuSelection) {
                case 0 -> currentState = GameState.PLAYING;
                case 1 -> {
                    currentState = GameState.COMPETITIVE;
                    currentHoleIndex = 0;
                    competitiveCourse = LevelDataGenerator.generate18Holes();
                    competitiveScore = new CompetitiveScore(competitiveCourse);
                    shotController.setGuidelineEnabled(false);
                }
                case 2 -> {
                    enterInstructions();
                    return;
                }
                case 3 -> currentState = GameState.PRACTICE_RANGE;
                case 4 -> currentState = GameState.PUTTING_GREEN;
                case 5 -> {
                    long seed = -1;
                    String clip = Gdx.app.getClipboard().getContents();
                    try {
                        if (clip != null) seed = Long.parseLong(clip.trim());
                    } catch (Exception ignored) {
                    }
                    currentState = GameState.PLAYING;
                    initLevel(seed);
                    return;
                }
            }
            initLevel();
            return;
        }

        for (int i = 0; i < 6; i++) {
            GameInputProcessor.Action selectAction = GameInputProcessor.Action.valueOf("SELECT_OPTION_" + i);
            if (inputProcessor.isActionJustPressed(selectAction)) {
                menuSelection = i;
                // Re-trigger MENU_SELECT logic for the new selection
                handleStartMenuSelection();
                return;
            }
        }
    }

    private void handleStartMenuSelection() {
        switch (menuSelection) {
            case 0 -> currentState = GameState.PLAYING;
            case 1 -> {
                currentState = GameState.COMPETITIVE;
                currentHoleIndex = 0;
                competitiveCourse = LevelDataGenerator.generate18Holes();
                competitiveScore = new CompetitiveScore(competitiveCourse);
                shotController.setGuidelineEnabled(false);
            }
            case 2 -> {
                enterInstructions();
                return;
            }
            case 3 -> currentState = GameState.PRACTICE_RANGE;
            case 4 -> currentState = GameState.PUTTING_GREEN;
            case 5 -> {
                long seed = -1;
                String clip = Gdx.app.getClipboard().getContents();
                try {
                    if (clip != null) seed = Long.parseLong(clip.trim());
                } catch (Exception ignored) {
                }
                currentState = GameState.PLAYING;
                initLevel(seed);
                return;
            }
        }
        initLevel();
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
            if (scroll != 0) {
                boolean isMod = inputProcessor.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) ||
                        inputProcessor.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW);

                if (!shotController.isCharging() && !isMod) {
                    // Normal scroll: change club
                    int index = MathUtils.clamp(currentClub.ordinal() + (scroll > 0 ? 1 : -1), 0, Club.values().length - 1);
                    currentClub = Club.values()[index];
                }
            }
        }

        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            if (!shotController.isCharging()) {
                if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_UP)) {
                    int index = MathUtils.clamp(currentClub.ordinal() - 1, 0, Club.values().length - 1);
                    currentClub = Club.values()[index];
                } else if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.CLUB_DOWN)) {
                    int index = MathUtils.clamp(currentClub.ordinal() + 1, 0, Club.values().length - 1);
                    currentClub = Club.values()[index];
                }
            }
        }

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.PAUSE)) {
            if (currentState == GameState.PAUSED) {
                currentState = previousState;
                cameraController.setPaused(false);
                cameraController.update(ball.getPosition(), inputProcessor);
            } else {
                previousState = currentState;
                currentState = GameState.PAUSED;
                cameraController.setPaused(true);
            }
            if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
                setupInputProcessor();
            }
        }

        // Toggles that only work while NOT in a menu
        if (currentState != GameState.PAUSED && currentState != GameState.INSTRUCTIONS && currentState != GameState.CAMERA_CONFIG) {
            if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.HELP)) showClubInfo = !showClubInfo;
        }

        // Global Gameplay hotkeys (work in Pause/Victory/Play)
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.COPY_SEED) && currentLevelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(currentLevelData.getSeed()));
        }
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.RESET_BALL)) resetBallToLastShot();
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.NEW_LEVEL)) handleNewLevelInput();

        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_UP)) {
            config.adjustGameSpeed(true);
        }
        if (inputProcessor.isActionJustPressed(GameInputProcessor.Action.SPEED_DOWN)) {
            config.adjustGameSpeed(false);
        }
    }

    private void handleNewLevelInput() {
        if (currentState == GameState.COMPETITIVE || (currentState == GameState.PAUSED && previousState == GameState.COMPETITIVE)) {
            if (isVictory) {
                if (currentHoleIndex + 1 < competitiveCourse.size()) {
                    currentHoleIndex++;
                    competitiveScore.setCurrentHoleIndex(currentHoleIndex);
                    currentState = GameState.COMPETITIVE;
                    initLevel();
                }
            }
        } else {
            if (currentState == GameState.PAUSED) currentState = previousState;
            initLevel();
        }
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));
        Gdx.gl.glClearColor(0.53f, 0.81f, 0.92f, 1f);
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());

        camera.near = 0.4f;

        camera.far = 950f;

        gameViewport = new ExtendViewport(1280, 720, camera);
    }

    private boolean isGameplayState() {
        return currentState == GameState.PLAYING || currentState == GameState.COMPETITIVE ||
                currentState == GameState.PRACTICE_RANGE || currentState == GameState.PUTTING_GREEN;
    }

    private boolean isVictoryCourseComplete() {
        return isVictory && currentState == GameState.COMPETITIVE && competitiveScore != null && competitiveScore.isCourseComplete() && inputProcessor.isActionJustPressed(GameInputProcessor.Action.MAIN_MENU);
    }

    @Override
    public void resize(int width, int height) {
        if (gameViewport != null) {
            gameViewport.update(width, height, true);
        }
        if (hud != null) {
            hud.resize(width, height);
        }
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
}