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
import org.example.glamour.ParticleManager;
import org.example.terrain.Terrain;

public class GolfGame extends ApplicationAdapter {
    private enum GameState { START, PLAYING, PAUSED }
    private GameState currentState = GameState.START;

    private PerspectiveCamera camera;
    private Viewport gameViewport;
    private ModelBatch modelBatch;
    private Environment environment;

    private Terrain terrain;
    private Ball ball;
    private FreeCameraController cameraController;
    private HUD hud;
    private ShotController shotController;


    private Club currentClub = Club.WOOD_5;

    private ParticleManager particleManager;
    private boolean isVictory = false;
    private float gameSpeed = 1.0f;

    // --- NEW: BEV Highlight ---
    private Model highlightModel;
    private ModelInstance highlightInstance;

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        hud = new HUD();
        shotController = new ShotController();
        particleManager = new ParticleManager();

        setupEnvironment();
        setupCamera();
        setupHighlight();
        initLevel();

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Rule 1: Right-Click or Tab = Camera Zoom/Overhead Zoom
                if (Gdx.input.isButtonPressed(Input.Buttons.RIGHT) || Gdx.input.isKeyPressed(Input.Keys.TAB)) {
                    return cameraController.handleScroll(amountY);
                }

                // Rule 2: No scrolling clubs while charging space
                if (shotController.isCharging()) {
                    return false;
                }

                // Rule 3: Clamped club selection
                int index = currentClub.ordinal();
                if (amountY > 0) index++;
                else if (amountY < 0) index--;

                index = MathUtils.clamp(index, 0, Club.values().length - 1);
                currentClub = Club.values()[index];
                return true;
            }
        });
    }

    private void setupHighlight() {
        ModelBuilder mb = new ModelBuilder();
        // Create a large sphere with transparency
        highlightModel = mb.createSphere(8f, 8f, 8f, 24, 24,
                new Material(
                        ColorAttribute.createDiffuse(Color.WHITE),
                        new BlendingAttribute(0.4f) // 40% opacity
                ),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        highlightInstance = new ModelInstance(highlightModel);
    }

    private void initLevel() {
        if (terrain != null) terrain.dispose();
        if (ball != null) ball.dispose();

        terrain = new Terrain();
        Vector3 tee = terrain.getTeePosition();
        Vector3 hole = terrain.getHolePosition();
        ball = new Ball(tee);

        Vector3 dirToHole = new Vector3(hole).sub(tee).nor();
        camera.position.set(hole.x + dirToHole.x * 10f, hole.y + 15f, hole.z + dirToHole.z * 10f);
        camera.up.set(0, 1, 0);
        camera.lookAt(hole);
        camera.update();

        cameraController = new FreeCameraController(camera, 40f, hole);
        hud.resetShots();
        isVictory = false;
        particleManager.clear();
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
            hud.renderStartMenu();
        } else {
            updateLogic(delta);
            renderScene();
            renderUI();
        }
    }

    private void updateLogic(float delta) {
        if (currentState == GameState.PLAYING && !isVictory) {
            float effDelta = delta * gameSpeed;

            if (shotController.update(delta, effDelta, ball, camera.direction, currentClub)) {
                hud.incrementShots();
            }

            ball.update(effDelta, terrain);
            cameraController.update(ball.getPosition());

            // Much cleaner!
            particleManager.handleBallInteraction(ball, terrain);

            if (ball.checkVictory(terrain.getHolePosition(), terrain.getHoleSize())) {
                triggerVictory();
            }
        }

        particleManager.update(delta, terrain);
        terrain.updateFlag(camera.position);
    }

    private void triggerVictory() {
        isVictory = true;
        ball.getVelocity().setZero();
        ball.getPosition().set(terrain.getHolePosition());
        particleManager.spawn(terrain.getHolePosition(), Color.GOLD, 40, 8f, 2.0f, 4.0f);
    }

    private void handleEnvironmentalParticles() {
        Ball.Interaction interaction = ball.getLastInteraction();
        if (interaction == Ball.Interaction.NONE) return;

        float speed = ball.getVelocity().len();
        if (speed < 1.0f) {
            ball.clearInteraction();
            return;
        }

        Color pColor;
        int count;
        float forceMult;
        float life = 0.6f;

        // Define behavior based on interaction type
        switch (interaction) {
            case WATER -> {
                pColor = Color.CYAN;
                count = (int)(speed * 3); // Very High
                forceMult = 0.4f;
                life = 1.2f;
            }
            case LEAVES -> {
                pColor = Color.FOREST;
                count = (int)(speed * 2.5f); // High
                forceMult = 0.3f;
            }
            case TERRAIN -> {
                Terrain.TerrainType type = terrain.getTerrainTypeAt(ball.getPosition().x, ball.getPosition().z);
                Ball.State state = ball.getState(); // We need this to check ROLLING vs CONTACT/AIR

                // Default values
                pColor = new Color(0.45f, 0.35f, 0.25f, 1f); // Soil Brown
                float traitScale = 0.5f;

                switch (type) {
                    case BUNKER -> {
                        pColor = Color.TAN;
                        traitScale = 1.2f;
                    }
                    case GREEN, FAIRWAY, ROUGH -> {
                        // Determine base green color based on surface
                        Color surfaceGreen = switch(type) {
                            case GREEN -> new Color(0.2f, 0.8f, 0.2f, 1f);   // Bright/Light Green
                            case FAIRWAY -> new Color(0.1f, 0.6f, 0.1f, 1f); // Standard Green
                            case ROUGH -> new Color(0.05f, 0.4f, 0.05f, 1f); // Dark Green
                            default -> Color.GREEN;
                        };

                        // Set Trait Scale
                        traitScale = (type == Terrain.TerrainType.GREEN) ? 0.15f :
                                (type == Terrain.TerrainType.FAIRWAY) ? 0.4f : 0.8f;

                        // COLOR LOGIC:
                        // If rolling, 100% green. If bouncing (CONTACT/AIR), 80% chance green, 20% brown.
                        if (state == Ball.State.ROLLING) {
                            pColor = surfaceGreen;
                        } else {
                            // Mix in dirt on impacts
                            pColor = (MathUtils.random() < 0.8f) ? surfaceGreen : new Color(0.4f, 0.25f, 0.15f, 1f);
                        }
                    }
                }

                count = (int)(speed * traitScale * 3); // Slightly increased count for better visuals
                forceMult = 0.15f * traitScale;
            }
            default -> {
                pColor = Color.WHITE;
                count = 0;
                forceMult = 0;
            }
        }

        if (count > 0) {
            // Calculate "Backwards" direction: Reverse of velocity
            Vector3 splashDir = ball.getVelocity().cpy().scl(-1).nor();

            // Add a slight upward bias so it doesn't just go into the ground
            splashDir.add(0, 0.5f, 0).nor();

            // Spawn using the new direction-based logic (we'll update ParticleManager next)
            particleManager.spawnDirectional(ball.getPosition(), splashDir, pColor, count, speed * forceMult, life);
        }

        ball.clearInteraction();
    }

    private void renderScene() {
        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);
        ball.render(modelBatch, environment);

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
            hud.renderPlayingHUD(gameSpeed, currentClub);
        }
    }

    private void handleInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER) && currentState == GameState.START) currentState = GameState.PLAYING;
        if (Gdx.input.isKeyJustPressed(Input.Keys.F11)) {
            if (Gdx.graphics.isFullscreen()) Gdx.graphics.setWindowedMode(1280, 720);
            else Gdx.graphics.setFullscreenMode(Gdx.graphics.getDisplayMode());
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PLAYING) currentState = GameState.PAUSED;
            else if (currentState == GameState.PAUSED) currentState = GameState.PLAYING;
        }

        if (currentState == GameState.PAUSED || isVictory) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.N)) {
                initLevel();
                currentState = GameState.PLAYING;
            }
            if (Gdx.input.isKeyJustPressed(Input.Keys.R)) {
                hud.resetShots();
                ball.getPosition().set(terrain.getTeePosition());
                ball.getVelocity().setZero();
                isVictory = false;
                particleManager.clear();
                currentState = GameState.PLAYING;
            }
        }

        if (currentState == GameState.PLAYING) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) gameSpeed = Math.min(gameSpeed + 0.5f, 5f);
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
        terrain.dispose();
        ball.dispose();
        particleManager.dispose();
        if (highlightModel != null) highlightModel.dispose();
    }
}