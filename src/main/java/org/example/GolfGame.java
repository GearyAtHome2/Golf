package org.example;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.environment.DirectionalLight;
import com.badlogic.gdx.math.Vector3;

import static java.lang.Long.SIZE;
import static org.example.Terrain.SIZE_Z;

public class GolfGame extends ApplicationAdapter {

    private PerspectiveCamera camera;
    private ModelBatch modelBatch;
    private Environment environment;

    private Terrain terrain;
    private Ball ball;
    private FreeCameraController cameraController;

    @Override
    public void create() {
        modelBatch = new ModelBatch();

        environment = new Environment();
        environment.set(new ColorAttribute(
                ColorAttribute.AmbientLight,
                0.4f, 0.4f, 0.4f, 1f));
        environment.add(new DirectionalLight().set(Color.WHITE, -1f, -0.8f, -0.2f));

        camera = new PerspectiveCamera(67,
                Gdx.graphics.getWidth(),
                Gdx.graphics.getHeight());
        camera.position.set(0f, 50f, 0f);   // raise and pull back a bit
        camera.lookAt(0f, 0f, SIZE_Z / 2f);  // look down the course
        camera.near = 0.1f;
        camera.far = 600f;
        camera.update();

        terrain = new Terrain();
        ball = new Ball(terrain.getTeePosition());
        Gdx.gl.glClearColor(0.5f, 0.7f, 1f, 1f);
        cameraController = new FreeCameraController(camera, 40f);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        // --- Input for hitting the ball ---
        if (Gdx.input.isKeyJustPressed(com.badlogic.gdx.Input.Keys.SPACE)) {
            Vector3 shootDir = camera.direction.cpy();
            shootDir.y = 0;          // flatten to horizontal
            shootDir.nor();          // normalize
            ball.hit(shootDir);      // apply 60° upward inside hit()
        }

        // --- Update physics ---
        ball.update(delta, terrain);
        cameraController.update(ball.getPosition());

        // --- Render ---
        Gdx.gl.glViewport(0, 0, Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT | GL20.GL_DEPTH_BUFFER_BIT);
        Gdx.gl.glEnable(GL20.GL_DEPTH_TEST);

        modelBatch.begin(camera);
        terrain.render(modelBatch, environment);
        ball.render(modelBatch, environment);
        modelBatch.end();
    }

    @Override
    public void dispose() {
        modelBatch.dispose();
        terrain.dispose();
        ball.dispose();
    }
}