package org.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
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
import org.example.Ball;
import org.example.FreeCameraController;
import org.example.Terrain;

import static org.example.Terrain.SIZE_Z;

public class GolfGame extends ApplicationAdapter {

    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;

    private Terrain terrain;
    private Ball ball;
    private FreeCameraController cameraController;

    // --- Game state ---
    private boolean gameStarted = false; // <-- Track if the start menu is active

    // --- Space bar tracking ---
    private boolean spacePressed = false;
    private float spaceHoldTime = 0f;
    private float shotPower = 0f;

    // --- Power bar ---
    private Model powerBarModel;
    private ModelInstance powerBarInstance;
    private final float POWER_BAR_WIDTH = 0.5f;
    private final float POWER_BAR_MAX_HEIGHT = 3f;
    private final float POWER_BAR_OFFSET_Y = 2f;

    // --- Max height wireframe ---
    private Model maxBarModel;
    private ModelInstance maxBarInstance;

    // --- Font for menu ---
    private BitmapFont font;
    private SpriteBatch spriteBatch;

    @Override
    public void create() {
        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight, 0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));

        camera = new PerspectiveCamera(67, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        camera.position.set(0f, 50f, 0f);
        camera.lookAt(0f, 0f, SIZE_Z / 2f);
        camera.near = 0.1f;
        camera.far = 600f;
        camera.update();

        terrain = new Terrain();
        ball = new Ball(terrain.getTeePosition());
        cameraController = new FreeCameraController(camera, 40f);

        Gdx.gl.glClearColor(0.5f, 0.7f, 1f, 1f);

        ModelBuilder modelBuilder = new ModelBuilder();

        // --- Power bar ---
        powerBarModel = modelBuilder.createBox(
                POWER_BAR_WIDTH, 1f, POWER_BAR_WIDTH,
                new Material(ColorAttribute.createDiffuse(Color.RED)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        powerBarInstance = new ModelInstance(powerBarModel);

        // --- Max height wireframe ---
        maxBarModel = modelBuilder.createBox(
                POWER_BAR_WIDTH, POWER_BAR_MAX_HEIGHT, POWER_BAR_WIDTH,
                new Material(ColorAttribute.createDiffuse(Color.WHITE)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        maxBarInstance = new ModelInstance(maxBarModel);

        // --- Font & sprite batch for menu ---
        font = new BitmapFont(); // default font
        spriteBatch = new SpriteBatch();
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        if (!gameStarted) {
            // --- Start menu ---
            if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.ENTER)) {
                gameStarted = true; // Start the game
            }

            // Render start menu text
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            spriteBatch.begin();
            font.getData().setScale(3f);
            font.draw(spriteBatch, "MINI GOLF GAME", Gdx.graphics.getWidth() / 2f - 150, Gdx.graphics.getHeight() / 2f + 50);
            font.getData().setScale(2f);
            font.draw(spriteBatch, "Press ENTER to Start", Gdx.graphics.getWidth() / 2f - 130, Gdx.graphics.getHeight() / 2f - 20);
            spriteBatch.end();

            return; // Skip rest of render until game starts
        }

        // --- Game input & update ---
        if (Gdx.input.isKeyPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            if (!spacePressed) {
                spacePressed = true;
                spaceHoldTime = 0f;
            }
            spaceHoldTime += delta;
            if (spaceHoldTime > POWER_BAR_MAX_HEIGHT) spaceHoldTime = POWER_BAR_MAX_HEIGHT;
        } else if (spacePressed) {
            spacePressed = false;
            shotPower = spaceHoldTime;

            Vector3 shootDir = camera.direction.cpy();
            shootDir.y = 0;
            shootDir.nor();
            ball.hit(shootDir, shotPower);

            spaceHoldTime = 0f;
        }

        ball.update(delta, terrain);
        cameraController.update(ball.getPosition());

        // --- Update power bar ---
        float currentBarHeight = spacePressed ? spaceHoldTime : shotPower;
        if (currentBarHeight > 0f) {
            if (!spacePressed) {
                shotPower -= delta * 6f;
                currentBarHeight = Math.max(shotPower, 0f);
            }

            Color barColor = new Color();
            if (currentBarHeight < POWER_BAR_MAX_HEIGHT / 2f) {
                barColor.set(Color.GREEN).lerp(Color.YELLOW, currentBarHeight / (POWER_BAR_MAX_HEIGHT / 2f));
            } else {
                barColor.set(Color.YELLOW).lerp(Color.RED, (currentBarHeight - POWER_BAR_MAX_HEIGHT / 2f) / (POWER_BAR_MAX_HEIGHT / 2f));
            }
            powerBarInstance.materials.get(0).set(ColorAttribute.createDiffuse(barColor));

            powerBarInstance.transform.idt();
            powerBarInstance.transform.setToTranslation(
                    ball.getPosition().x,
                    ball.getPosition().y + POWER_BAR_OFFSET_Y + currentBarHeight / 2f,
                    ball.getPosition().z
            );
            powerBarInstance.transform.scale(1f, currentBarHeight, 1f);
        }

        // --- Render scene ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);
        ball.render(modelBatch, environment);
        if (currentBarHeight > 0f) modelBatch.render(powerBarInstance, environment);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        terrain.dispose();
        ball.dispose();
        powerBarModel.dispose();
        maxBarModel.dispose();
        spriteBatch.dispose();
        font.dispose();
    }
}