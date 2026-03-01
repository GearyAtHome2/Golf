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
import org.example.ball.ShotController;
import org.example.glamour.ParticleManager;
import org.example.terrain.*;

import java.util.ArrayList;
import java.util.List;

public class GolfGame extends ApplicationAdapter {

    private enum GameState { START, PLAYING, PRACTICE, PAUSED }
    private GameState currentState = GameState.START;
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

    private int menuSelection = 0;
    private Club currentClub = Club.WOOD_5;
    private ParticleManager particleManager;
    private boolean isVictory = false;
    private float gameSpeed = 1.0f;

    private Model highlightModel;
    private ModelInstance highlightInstance;

    private Model markerLineModel;
    private final List<ModelInstance> yardageMarkers = new ArrayList<>();

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        hud = new HUD();
        shotController = new ShotController();
        particleManager = new ParticleManager();

        setupEnvironment();
        setupCamera();
        setupHighlight();
    }

    private void setupHighlight() {
        ModelBuilder mb = new ModelBuilder();
        highlightModel = mb.createSphere(8f, 8f, 8f, 24, 24,
                new Material(
                        ColorAttribute.createDiffuse(Color.WHITE),
                        new BlendingAttribute(0.4f)
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        highlightInstance = new ModelInstance(highlightModel);
    }

    private void createYardageMarkers(PracticeRangeGenerator gen) {
        if (markerLineModel != null) markerLineModel.dispose();
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
        if (terrain != null) terrain.dispose();
        if (ball != null) ball.dispose();
        for (DistanceSign s : rangeSigns) s.dispose();
        rangeSigns.clear();
        yardageMarkers.clear();

        for (Ball ghost : shotHistory) ghost.dispose();
        shotHistory.clear();

        ITerrainGenerator generator;
        if (currentState == GameState.PRACTICE) {
            generator = new PracticeRangeGenerator();
        } else {
            generator = new ClassicGenerator();
        }

        terrain = new Terrain(generator);
        Vector3 tee = terrain.getTeePosition();
        Vector3 hole = terrain.getHolePosition();
        ball = new Ball(tee);

        if (currentState == GameState.PRACTICE && generator instanceof PracticeRangeGenerator gen) {
            for (PracticeRangeGenerator.SignData data : gen.getSignPositions()) {
                rangeSigns.add(new DistanceSign(data.position, data.distance));
            }
            createYardageMarkers(gen);
        }

        hasCurrentBallBeenHit = false;

        Vector3 dirToHole = new Vector3(hole).sub(tee).nor();
        if (dirToHole.len() < 0.1f) dirToHole.set(0, 0, 1);

        camera.position.set(tee.x - dirToHole.x * 10f, tee.y + 5f, tee.z - dirToHole.z * 10f);
        camera.lookAt(tee);
        camera.update();

        cameraController = new FreeCameraController(camera, 40f, tee);

        if (currentState == GameState.PRACTICE) {
            cameraController.setIntroActive(false);
        }

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

        if (currentState == GameState.START) {
            hud.renderStartMenu(menuSelection);
        } else {
            updateLogic(delta);
            renderScene();
            renderUI();
        }
    }

    private void updateLogic(float delta) {
        if ((currentState == GameState.PLAYING || currentState == GameState.PRACTICE) && !isVictory) {
            float effDelta = delta * gameSpeed;

            if (shotController.update(delta, ball, camera.direction, currentClub, hud)) {
                hud.incrementShots();
                hasCurrentBallBeenHit = true;
            }

            ball.update(effDelta, terrain);
            cameraController.update(ball.getPosition());
            particleManager.handleBallInteraction(ball, terrain);

            if (currentState == GameState.PRACTICE) {
                if (hasCurrentBallBeenHit && ball.getState() == Ball.State.STATIONARY) {
                    practiceResetTimer += delta;
                    if (practiceResetTimer >= RESET_DELAY) {
                        ball.setGhostColor(new Color(0.7f, 0.7f, 0.7f, 0.5f));
                        shotHistory.add(ball);

                        if (shotHistory.size() > MAX_GHOSTS) {
                            Ball oldest = shotHistory.remove(0);
                            oldest.dispose();
                        }

                        ball = new Ball(terrain.getTeePosition());
                        hasCurrentBallBeenHit = false;
                        practiceResetTimer = 0f;
                    }
                } else {
                    practiceResetTimer = 0f;
                }
            }

            if (ball.checkVictory(terrain.getHolePosition(), terrain.getHoleSize())) {
                triggerVictory();
            }
        }

        particleManager.update(delta, terrain);
        if (terrain != null) terrain.updateFlag(camera.position);
    }

    private void triggerVictory() {
        isVictory = true;
        float vel = ball.getVelocity().len();
        ball.getVelocity().setZero();
        ball.getPosition().set(terrain.getHolePosition());
        particleManager.spawn(terrain.getHolePosition(), Color.GOLD, 40, vel+5, 50.0f, 4.0f);
    }

    private void renderScene() {
        if (terrain == null) return;
        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);

        for (ModelInstance marker : yardageMarkers) {
            modelBatch.render(marker, environment);
        }

        ball.render(modelBatch, environment);
        ball.renderTrail(modelBatch, environment);

        for (Ball ghost : shotHistory) {
            ghost.render(modelBatch, environment);
            ghost.renderTrail(modelBatch, environment);
        }

        for (DistanceSign s : rangeSigns) {
            s.render(modelBatch, environment);
        }

        if (cameraController.isOverhead()) {
            highlightInstance.transform.setToTranslation(ball.getPosition());
            modelBatch.render(highlightInstance, environment);
        }

        shotController.render(modelBatch, environment, ball.getPosition(), camera.position);
        particleManager.render(modelBatch, environment);
        modelBatch.end();
    }

    private void renderUI() {
        if (isVictory) {
            hud.renderVictory(hud.getShotCount());
        } else if (currentState == GameState.PAUSED) {
            hud.renderPauseMenu();
        } else {
            boolean isPractice = (currentState == GameState.PRACTICE);
            hud.renderPlayingHUD(gameSpeed, currentClub, ball, isPractice);
        }
    }

    private void handleInput() {
        if (currentState == GameState.START) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP) || Gdx.input.isKeyJustPressed(Input.Keys.W)) menuSelection = 0;
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN) || Gdx.input.isKeyJustPressed(Input.Keys.S)) menuSelection = 1;

            if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) || Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) {
                if (menuSelection == 0) currentState = GameState.PLAYING;
                else currentState = GameState.PRACTICE;
                initLevel();
            }
            return;
        }

        // --- GLOBAL KEYS ---
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PAUSED) currentState = GameState.PLAYING;
            else if (currentState == GameState.PLAYING || currentState == GameState.PRACTICE) currentState = GameState.PAUSED;
        }

        // R - Reset Level / Ball
        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
            initLevel();
        }

        // N - New Course
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
            initLevel();
        }

        // C - Clear Ghosts (Practice only)
        if (currentState == GameState.PRACTICE && Gdx.input.isKeyJustPressed(Input.Keys.C)) {
            for (Ball ghost : shotHistory) ghost.dispose();
            shotHistory.clear();
        }

        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(1280, 720);
            else Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
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