package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class FreeCameraController {

    private final PerspectiveCamera camera;

    private float yaw = 0f;    // horizontal angle around target
    private float pitch = 20f; // vertical angle
    private float distance = 10f; // distance from target
    private final float mouseSensitivity = 0.3f;

    public FreeCameraController(PerspectiveCamera camera, float distance) {
        this.camera = camera;
        this.distance = distance;

        // Setup scroll handling
        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Use the class field 'distance', not a local variable
                FreeCameraController.this.distance += amountY * 2.5f; // zoom speed
                FreeCameraController.this.distance = MathUtils.clamp(FreeCameraController.this.distance, 2f, 50f);
                return true;
            }
        });
    }

    public void update(Vector3 target) {
        // --- Mouse rotation around target ---
        if (Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            float deltaX = -Gdx.input.getDeltaX() * mouseSensitivity;
            float deltaY = -Gdx.input.getDeltaY() * mouseSensitivity;

            yaw += deltaX;
            pitch += deltaY;

            // Clamp pitch to avoid flipping over
            pitch = MathUtils.clamp(pitch, -89f, 89f);
        }

        // --- Calculate camera position from spherical coordinates ---
        float radYaw = -MathUtils.degreesToRadians * yaw;
        float radPitch = MathUtils.degreesToRadians * pitch;

        float x = target.x + distance * MathUtils.cos(radPitch) * MathUtils.sin(radYaw);
        float y = target.y + distance * MathUtils.sin(radPitch);
        float z = target.z + distance * MathUtils.cos(radPitch) * MathUtils.cos(radYaw);

        camera.position.set(x, y, z);
        camera.lookAt(target);
        camera.up.set(Vector3.Y);
        camera.update();
    }
}