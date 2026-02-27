package org.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;
import org.example.terrain.Terrain;

public class GolfGame extends ApplicationAdapter {

    private enum GameState { START, PLAYING, PAUSED }
    private GameState currentState = GameState.START;

    // Core Rendering
    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;
    private SpriteBatch spriteBatch;
    private BitmapFont font;

    // Game Objects
    private Terrain terrain;
    private Ball ball;
    private FreeCameraController cameraController;

    // Power Bar
    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private final float POWER_BAR_MAX_HEIGHT = 3f;
    private final float POWER_BAR_OFFSET_Y = 2f;

    // Game Settings
    private float gameSpeed = 1.0f;
    private boolean spacePressed = false;
    private float spaceHoldTime = 0f;
    private float shotPower = 0f;

    @Override
    public void create() {
        modelBatch = new ModelBatch();
        spriteBatch = new SpriteBatch();
        font = new BitmapFont();

        setupEnvironment();
        setupCamera();
        setupPowerBar();
        initLevel(); // Initial level load
    }

    private void initLevel() {
        if (terrain != null) terrain.dispose();
        if (ball != null) ball.dispose();

        terrain = new Terrain();
        ball = new Ball(terrain.getTeePosition());
        cameraController = new FreeCameraController(camera, 40f);
    }

    private void resetBall() {
        ball.getPosition().set(terrain.getTeePosition());
        ball.getVelocity().setZero();
        // Force the ball state to STATIONARY via internal logic if needed,
        // but setting velocity to zero usually triggers it in Ball.update
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handleGlobalInput();

        switch (currentState) {
            case START -> renderStartMenu();
            case PLAYING -> {
                updateGame(delta);
                renderScene();
                renderHUD();
            }
            case PAUSED -> {
                renderScene(); // Still render the world behind the menu
                renderPauseMenu();
            }
        }
    }

    private void handleGlobalInput() {
        if (Gdx.input.isKeyJustPressed(Input.Keys.ESCAPE)) {
            if (currentState == GameState.PLAYING) currentState = GameState.PAUSED;
            else if (currentState == GameState.PAUSED) currentState = GameState.PLAYING;
        }

        if (currentState == GameState.PLAYING) {
            if (Gdx.input.isKeyJustPressed(Input.Keys.UP)) gameSpeed = Math.min(gameSpeed + 0.5f, 5f);
            if (Gdx.input.isKeyJustPressed(Input.Keys.DOWN)) gameSpeed = Math.max(gameSpeed - 0.5f, 0.5f);
        }
    }

    private void updateGame(float delta) {
        // 1. Handle Shot Input
        if (Gdx.input.isKeyPressed(Input.Keys.SPACE)) {
            if (!spacePressed) { spacePressed = true; spaceHoldTime = 0f; }
            spaceHoldTime += delta;
            if (spaceHoldTime > POWER_BAR_MAX_HEIGHT) spaceHoldTime = POWER_BAR_MAX_HEIGHT;
        } else if (spacePressed) {
            spacePressed = false;
            shotPower = spaceHoldTime;
            Vector3 shootDir = camera.direction.cpy().set(camera.direction.x, 0, camera.direction.z).nor();
            ball.hit(shootDir, shotPower);
            spaceHoldTime = 0f;
        }

        // 2. Physics & Camera
        float effectiveDelta = delta * gameSpeed;
        ball.update(effectiveDelta, terrain);
        cameraController.update(ball.getPosition());

        // 3. UI logic (Shot power drain)
        if (!spacePressed && shotPower > 0) {
            shotPower -= delta * 6f; // Raw delta for consistent UI feel
            if (shotPower < 0) shotPower = 0;
        }
    }

    // ===================== Rendering Methods =====================

    private void renderScene() {
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);

        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);
        ball.render(modelBatch, environment);

        float barHeight = spacePressed ? spaceHoldTime : shotPower;
        if (barHeight > 0) {
            updatePowerBarTransform(barHeight);
            modelBatch.render(powerBarInstance, environment);
        }
        modelBatch.end();
    }

    private void renderStartMenu() {
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        spriteBatch.begin();
        font.getData().setScale(3f);
        font.draw(spriteBatch, "ULTIMATE GOLF", 100, Gdx.graphics.getHeight() / 2f + 100);
        font.getData().setScale(1.5f);
        font.draw(spriteBatch, "PRESS ENTER TO TEE OFF", 100, Gdx.graphics.getHeight() / 2f);
        spriteBatch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.ENTER)) currentState = GameState.PLAYING;
    }

    private void renderPauseMenu() {
        // Dim the background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        spriteBatch.begin();
        font.getData().setScale(2.5f);
        font.draw(spriteBatch, "PAUSED", Gdx.graphics.getWidth()/2f - 80, Gdx.graphics.getHeight() - 100);
        font.getData().setScale(1.2f);
        font.draw(spriteBatch, "[R] RESET BALL  |  [N] NEW LEVEL  |  [ESC] RESUME", 50, 100);
        spriteBatch.end();

        if (Gdx.input.isKeyJustPressed(Input.Keys.R)) { resetBall(); currentState = GameState.PLAYING; }
        if (Gdx.input.isKeyJustPressed(Input.Keys.N)) { initLevel(); currentState = GameState.PLAYING; }
    }

    private void renderHUD() {
        spriteBatch.begin();
        font.getData().setScale(1.2f);
        font.draw(spriteBatch, String.format("Speed: %.1fx", gameSpeed), Gdx.graphics.getWidth() - 140, 40);
        spriteBatch.end();
    }

    // ===================== Setup Helpers =====================

    private void setupPowerBar() {
        ModelBuilder mb = new ModelBuilder();
        powerBarModel = mb.createBox(0.5f, 1f, 0.5f,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        powerBarInstance = new ModelInstance(powerBarModel);
    }

    private void updatePowerBarTransform(float height) {
        Color barColor = height < 1.5f ? Color.GREEN.cpy().lerp(Color.YELLOW, height/1.5f)
                : Color.YELLOW.cpy().lerp(Color.RED, (height-1.5f)/1.5f);

        powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(barColor));
        powerBarInstance.transform.setToTranslation(ball.getPosition().x, ball.getPosition().y + POWER_BAR_OFFSET_Y + height/2f, ball.getPosition().z);
        powerBarInstance.transform.scale(1f, height, 1f);
    }

    private void setupCamera() {
        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 50f, 0f);
        camera.near = 0.1f;
        camera.far = 1000f;
    }

    private void setupEnvironment() {
        environment = new Environment();
        environment.set(new ColorAttribute(ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));
        Gdx.gl.glClearColor(0.53f, 0.81f, 0.92f, 1f); // Sky Blue
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        spriteBatch.dispose();
        font.dispose();
        terrain.dispose();
        ball.dispose();
        powerBarModel.dispose();
    }
}