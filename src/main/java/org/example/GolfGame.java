package org.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
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
import com.badlogic.gdx.utils.viewport.FitViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ball.Ball;
import org.example.ball.CompetitiveScore;
import org.example.ball.ShotController;
import org.example.glamour.ParticleManager;
import org.example.glamour.WindManager;
import org.example.hud.HUD;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.features.PuttingGreenGenerator;
import org.example.terrain.level.LevelData;
import org.example.terrain.level.LevelDataGenerator;
import org.example.terrain.practiceRange.DistanceSign;
import org.example.terrain.practiceRange.PracticeRangeGenerator;

import java.util.ArrayList;
import java.util.List;

public class GolfGame extends ApplicationAdapter {

    private enum GameState {START, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN, PAUSED, INSTRUCTIONS}

    private GameState currentState = GameState.START;
    private GameState previousState = GameState.PLAYING;
    private GameState returnState = GameState.PLAYING;

    private final List<DistanceSign> rangeSigns = new ArrayList<>();
    private final List<Ball> shotHistory = new ArrayList<>();
    private final int MAX_GHOSTS = 8;
    private float practiceResetTimer = 0f;
    private static final float RESET_DELAY = 1.0f;
    private boolean hasCurrentBallBeenHit = false;

    private PerspectiveCamera camera;
    private Viewport gameViewport;
    private ModelBatch modelBatch;
    private Environment environment;

    private Terrain terrain;
    private Ball ball;
    private FreeCameraController cameraController;
    private HUD hud;
    private ShotController shotController;
    private LevelData currentLevelData;

    private int menuSelection = 0;
    private Club currentClub = Club.DRIVER;
    private ParticleManager particleManager;
    private WindManager windManager;
    private boolean isVictory = false;
    private float gameSpeed = 1.0f;

    private Model highlightModel;
    private ModelInstance highlightInstance;
    private Model markerLineModel;
    private final List<ModelInstance> yardageMarkers = new ArrayList<>();

    private final Vector3 zeroWind = new Vector3(0, 0, 0);

    private List<LevelData> competitiveCourse = new ArrayList<>();
    private int currentHoleIndex = 0;
    private CompetitiveScore competitiveScore;

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        hud = new HUD();
        shotController = new ShotController();
        particleManager = new ParticleManager();
        windManager = new WindManager();
        setupEnvironment();
        setupCamera();
        setupHighlight();
    }

    private void setupHighlight() {
        ModelBuilder mb = new ModelBuilder();
        highlightModel = mb.createSphere(8f, 8f, 8f, 24, 24,
                new Material(ColorAttribute.createDiffuse(Color.WHITE), new BlendingAttribute(0.4f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        highlightInstance = new ModelInstance(highlightModel);
    }

    private void createYardageMarkers(PracticeRangeGenerator gen) {
        if (markerLineModel != null) {
            markerLineModel.dispose();
            markerLineModel = null;
        }
        yardageMarkers.clear();
        ModelBuilder mb = new ModelBuilder();
        markerLineModel = mb.createBox(70f, 0.05f, 0.3f,
                new Material(ColorAttribute.createDiffuse(new Color(1, 1, 1, 0.6f)), new BlendingAttribute(0.6f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        for (Float zPos : gen.getMarkerZPositions()) {
            ModelInstance marker = new ModelInstance(markerLineModel);
            marker.transform.setToTranslation(0, 0.02f, zPos);
            yardageMarkers.add(marker);
        }
    }

    private void initLevel() {
        initLevel(-1);
    }

    private void initLevel(long manualSeed) {
        if (terrain != null) { terrain.dispose(); terrain = null; }
        if (ball != null) { ball.dispose(); ball = null; }
        for (DistanceSign s : rangeSigns) s.dispose();
        rangeSigns.clear();
        yardageMarkers.clear();
        if (markerLineModel != null) { markerLineModel.dispose(); markerLineModel = null; }
        for (Ball ghost : shotHistory) ghost.dispose();
        shotHistory.clear();

        shotController.reset();
        hud.reset();

        ITerrainGenerator generator;
        float waterLevel = -1.0f;
        int distance = 500;

        if (currentState == GameState.PRACTICE_RANGE) {
            generator = new PracticeRangeGenerator();
            currentLevelData = null;
            waterLevel = -2.0f;
            distance = 500;
            currentClub = Club.DRIVER;
        } else if (currentState == GameState.PUTTING_GREEN) {
            long seed = (manualSeed == -1) ? MathUtils.random(Long.MAX_VALUE) : manualSeed;
            generator = new PuttingGreenGenerator(seed);
            currentLevelData = null;
            waterLevel = -5.0f;
            distance = 160;
            currentClub = Club.PUTTER;
        } else if (currentState == GameState.COMPETITIVE) {
            generator = initCompetitiveLevel();
            waterLevel = currentLevelData.getWaterLevel();
            distance = currentLevelData.getDistance();
            currentClub = Club.DRIVER;
        } else {
            if (manualSeed != -1) currentLevelData = LevelDataGenerator.createFixedLevelData(manualSeed);
            else currentLevelData = LevelDataGenerator.createRandomLevelData();
            generator = new ClassicGenerator(currentLevelData);
            waterLevel = currentLevelData.getWaterLevel();
            distance = currentLevelData.getDistance();
            currentClub = Club.DRIVER;
        }

        terrain = new Terrain(generator, waterLevel, distance);

        if (currentState == GameState.PUTTING_GREEN) {
            Vector3 tee = terrain.getTeePosition();
            Vector3 hole = terrain.getHolePosition();

            terrain.setTeePosition(new Vector3(tee.x, terrain.getHeightAt(tee.x, tee.z), tee.z));
            terrain.setHolePosition(new Vector3(hole.x, terrain.getHeightAt(hole.x, hole.z), hole.z));
        }

        Vector3 tee = terrain.getTeePosition();
        Vector3 hole = terrain.getHolePosition();

        if (currentState == GameState.PRACTICE_RANGE && generator instanceof PracticeRangeGenerator gen) {
            for (PracticeRangeGenerator.SignData data : gen.getSignPositions()) {
                rangeSigns.add(new DistanceSign(data.position, data.distance));
            }
            createYardageMarkers(gen);
        }

        // --- FIXED SPAWN LOGIC ---
        if (currentState == GameState.PUTTING_GREEN) {
            ball = new Ball(new Vector3(tee.x, tee.y + 0.5f, tee.z), particleManager);
            ball.setState(Ball.State.AIR);
        } else {
            ball = new Ball(new Vector3(tee.x, tee.y + 0.17f, tee.z), particleManager);
        }

        hasCurrentBallBeenHit = false;

        cameraController = new FreeCameraController(camera, 12f, ball.getPosition());
        cameraController.setOrientation(hole);
        cameraController.update(ball.getPosition());

        hud.resetShots();
        isVictory = false;
        practiceResetTimer = 0f;
        particleManager.clear();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.TAB)) {
                    return cameraController.handleScroll(amountY);
                }
                if (shotController.isCharging()) return false;
                int index = currentClub.ordinal();
                if (amountY > 0) index++;
                else if (amountY < 0) index--;
                index = MathUtils.clamp(index, 0, Club.values().length - 1);
                currentClub = Club.values()[index];
                return true;
            }
        });
    }

    private ITerrainGenerator initCompetitiveLevel() {
        if (competitiveCourse == null || competitiveCourse.isEmpty() || currentHoleIndex >= competitiveCourse.size()) {
            currentLevelData = LevelDataGenerator.createRandomLevelData();
        } else {
            currentLevelData = competitiveCourse.get(currentHoleIndex);
        }
        return new ClassicGenerator(currentLevelData);
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.near = 0.1f;
        camera.far = 1000f;
        gameViewport = new FitViewport(1280, 720, camera);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();
        handleInput();
        gameViewport.apply();
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        boolean showMouse = (currentState == GameState.START
                || currentState == GameState.PAUSED
                || currentState == GameState.INSTRUCTIONS
                || isVictory);

        Gdx.input.setCursorCatched(!showMouse);

        if (currentState == GameState.START) {
            hud.renderStartMenu(menuSelection);
        } else if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions();
        } else {
            updateLogic(delta);
            renderScene();
            renderUI();
        }
    }

    private void updateLogic(float delta) {
        if ((currentState == GameState.PLAYING || currentState == GameState.COMPETITIVE ||
                currentState == GameState.PRACTICE_RANGE || currentState == GameState.PUTTING_GREEN) && !isVictory) {

            float effDelta = delta * gameSpeed;
            Vector3 currentWind = (currentLevelData != null) ? currentLevelData.getWind() : zeroWind;

            if (currentState == GameState.PUTTING_GREEN) {
                currentWind = zeroWind;
            }

            windManager.update(effDelta, currentWind, camera.position);

            if (hud.wasMinigameCanceled()) {
                shotController.reset();
            }

            // Only allow shooting if the ball is actually stationary.
            if (ball.getState() == Ball.State.STATIONARY) {
                if (shotController.update(delta, ball, camera.direction, currentClub, hud, terrain)) {
                    hud.incrementShots();
                    hasCurrentBallBeenHit = true;
                }
            }

            ball.update(effDelta, terrain, currentWind);
            cameraController.update(ball.getPosition());
            particleManager.handleBallInteraction(ball, terrain);

            if (hasCurrentBallBeenHit && ball.getState() == Ball.State.STATIONARY) {
                practiceResetTimer += delta;
                if (practiceResetTimer >= RESET_DELAY) {
                    if (currentState == GameState.PRACTICE_RANGE) {
                        archiveGhostBall();
                        Vector3 tee = terrain.getTeePosition();
                        ball = new Ball(new Vector3(tee.x, tee.y+0.17f, tee.z), particleManager);
                        hasCurrentBallBeenHit = false;
                        practiceResetTimer = 0f;
                    } else if (terrain.isPointOutOfBounds(ball.getPosition().x, ball.getPosition().z)) {
                        hud.showOutOfBounds();
                        resetBallToLastShot();
                    } else if (ball.isInWater(terrain)) {
                        hud.showWaterHazard();
                        resetBallToLastShot();
                    }
                }
            } else {
                practiceResetTimer = 0f;
            }

            if (ball.checkVictory(terrain)) {
                triggerVictory();
            }
        }

        particleManager.update(delta, terrain);
        if (terrain != null) terrain.updateFlag(camera.position);
    }

    private void archiveGhostBall() {
        shotHistory.add(ball);
        if (shotHistory.size() > MAX_GHOSTS) {
            Ball oldest = shotHistory.remove(0);
            oldest.dispose();
        }
    }

    private void resetBallToLastShot() {
        if (ball != null) {
            ball.resetToLastPosition();
            ball.setState(Ball.State.STATIONARY);
            hasCurrentBallBeenHit = false;
            isVictory = false;
            practiceResetTimer = 0f;
            cameraController.update(ball.getPosition());
        }
    }

    private void triggerVictory() {
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
        if (terrain == null) return;
        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);
        for (ModelInstance marker : yardageMarkers) modelBatch.render(marker, environment);
        ball.render(modelBatch, environment);
        ball.renderTrail(modelBatch, environment);
        for (Ball ghost : shotHistory) {
            ghost.render(modelBatch, environment);
            ghost.renderTrail(modelBatch, environment);
        }
        for (DistanceSign s : rangeSigns) s.render(modelBatch, environment);
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
        boolean isCompetitive = (currentState == GameState.COMPETITIVE);

        if (currentState == GameState.INSTRUCTIONS) {
            hud.renderInstructions();
        } else if (isVictory) {
            hud.renderVictory(hud.getShotCount(), currentLevelData, isCompetitive ? competitiveScore : null);
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu(isPractice, currentLevelData);
        } else {
            hud.renderPlayingHUD(gameSpeed, currentClub, ball, isPractice, currentLevelData, camera, terrain, isCompetitive ? competitiveScore : null);
        }
    }

    private void handleInput() {
        boolean manualMenuPress = isVictory &&
                currentState == GameState.COMPETITIVE &&
                competitiveScore != null &&
                competitiveScore.isCourseComplete() &&
                Gdx.input.isKeyJustPressed(Input.Keys.M);

        if (hud.wasMainMenuRequested() || manualMenuPress) {
            shotController.reset();
            hud.resetShots();
            currentState = GameState.START;
            currentHoleIndex = 0;
            isVictory = false;
            return;
        }

        if (hud.wasInstructionsRequested()) {
            hud.clearInstructionsRequest();
            hud.resetInstructionScroll();
            returnState = currentState;
            currentState = GameState.INSTRUCTIONS;
            return;
        }

        if (currentState == GameState.START) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W))
                menuSelection = (menuSelection - 1 + 6) % 6;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S))
                menuSelection = (menuSelection + 1) % 6;

            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) {
                if (menuSelection == 0) {
                    currentState = GameState.PLAYING;
                    initLevel();
                } else if (menuSelection == 1) {
                    currentState = GameState.COMPETITIVE;
                    currentHoleIndex = 0;
                    competitiveCourse = LevelDataGenerator.generate18Holes();
                    competitiveScore = new CompetitiveScore(competitiveCourse);
                    shotController.setGuidelineEnabled(false);
                    initLevel();
                } else if (menuSelection == 2) {
                    returnState = GameState.START;
                    currentState = GameState.INSTRUCTIONS;
                    hud.resetInstructionScroll();
                } else if (menuSelection == 3) {
                    currentState = GameState.PRACTICE_RANGE;
                    initLevel();
                } else if (menuSelection == 4) {
                    currentState = GameState.PUTTING_GREEN;
                    initLevel();
                } else if (menuSelection == 5) {
                    long seed = -1;
                    String clip = Gdx.app.getClipboard().getContents();
                    try {
                        if (clip != null) seed = Long.parseLong(clip.trim());
                    } catch (NumberFormatException ignored) {}
                    currentState = GameState.PLAYING;
                    initLevel(seed);
                }
                shotController.reset();
            }
            return;
        }

        if (currentState == GameState.INSTRUCTIONS) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE) || Gdx.input.isKeyJustPressed(Input.Keys.BACKSPACE)) {
                currentState = returnState;
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PAUSED) {
                currentState = previousState;
                cameraController.setPaused(false);
            } else {
                previousState = currentState;
                currentState = GameState.PAUSED;
                cameraController.setPaused(true);
            }
            return;
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.P)) {
            if (currentState != GameState.COMPETITIVE) {
                shotController.toggleGuideline();
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            if (currentState == GameState.COMPETITIVE || (currentState == GameState.PAUSED && previousState == GameState.COMPETITIVE)) {
                if (isVictory && currentHoleIndex + 1 < competitiveCourse.size()) {
                    currentHoleIndex++;
                    competitiveScore.setCurrentHoleIndex(currentHoleIndex);
                    if (currentState == GameState.PAUSED) currentState = GameState.COMPETITIVE;
                    initLevel();
                }
            } else {
                if (currentState == GameState.PAUSED) currentState = previousState;
                initLevel();
            }
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) resetBallToLastShot();

        if (Gdx.input.isKeyJustPressed(Input.Keys.C) && currentLevelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(currentLevelData.getSeed()));
        }

        if (currentState == GameState.PLAYING || currentState == GameState.COMPETITIVE ||
                currentState == GameState.PRACTICE_RANGE || currentState == GameState.PUTTING_GREEN) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) gameSpeed = Math.min(gameSpeed + 0.5f, 5.0f);
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) gameSpeed = Math.max(gameSpeed - 0.5f, 0.5f);
        }
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));
        Gdx.gl.glClearColor(0.53f, 0.81f, 0.92f, 1f);
    }

    @Override
    public void resize(int width, int height) {
        gameViewport.update(width, height);
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        hud.dispose();
        shotController.dispose();
        if (terrain != null) terrain.dispose();
        if (ball != null) ball.dispose();
        for (Ball ghost : shotHistory) ghost.dispose();
        particleManager.dispose();
        if (highlightModel != null) highlightModel.dispose();
        if (markerLineModel != null) markerLineModel.dispose();
        for (DistanceSign s : rangeSigns) s.dispose();
    }
}