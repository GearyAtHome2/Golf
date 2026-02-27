package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class FreeCameraController {

    private final PerspectiveCamera camera;

    // --- Gameplay View ---
    private float yaw = 180f;
    private float pitch = 20f;
    private float distance = 10f;
    private float targetPitch = 20f;
    private float targetDistance = 12f;

    // --- Bird's Eye (Overhead) ---
    private float overheadZoom = 300f;
    private final Vector3 overheadCenter = new Vector3();
    private boolean wasOverheadLastFrame = false;

    // --- Cinematic Transition ---
    private final Vector3 currentLookAt = new Vector3();
    private boolean introActive = true;
    private final Vector3 tempTargetPos = new Vector3();

    private final float mouseSensitivity = 0.3f;
    private boolean isOverhead = false;

    public FreeCameraController(PerspectiveCamera camera, float initialDistance, Vector3 startLookAt) {
        this.camera = camera;
        this.distance = initialDistance;
        this.targetDistance = 12f; // Snap to a comfortable play distance
        this.currentLookAt.set(startLookAt);

        Gdx.input.setInputProcessor(new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                if (isOverhead) {
                    overheadZoom += amountY * 25.0f;
                    overheadZoom = MathUtils.clamp(overheadZoom, 50f, 1200f);
                } else {
                    targetDistance += amountY * 3.0f;
                    targetDistance = MathUtils.clamp(targetDistance, 2.1f, 150f);
                }
                return true;
            }
        });
    }

    public void update(Vector3 ballPos) {
        float delta = Gdx.graphics.getDeltaTime();
        isOverhead = Gdx.input.isKeyPressed(Input.Keys.TAB);

        if (isOverhead) {
            updateOverheadMode(ballPos, delta);
            wasOverheadLastFrame = true;
        } else {
            updateNormalMode(ballPos, delta);
            wasOverheadLastFrame = false;
        }

        camera.update();
    }

    private void updateOverheadMode(Vector3 ballPos, float delta) {
        if (!wasOverheadLastFrame) overheadCenter.set(ballPos);

        if (Gdx.input.isButtonPressed(Buttons.LEFT) || Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            float panScale = overheadZoom*1.4f / Gdx.graphics.getHeight();
            overheadCenter.x += Gdx.input.getDeltaX() * panScale;
            overheadCenter.z += Gdx.input.getDeltaY() * panScale;
        }

        camera.position.lerp(new Vector3(overheadCenter.x, overheadCenter.y + overheadZoom, overheadCenter.z), 8f * delta);
        camera.up.set(0, 0, 1);
        camera.lookAt(overheadCenter);
    }

    public boolean isOverhead() {
        return isOverhead;
    }

    private void updateNormalMode(Vector3 ballPos, float delta) {
        float dstToTarget = camera.position.dst(tempTargetPos);

        float lerpBase = introActive ? 1.5f : 6.0f;
        float lerpSpeed = lerpBase * delta;

        float lookSpeed = (introActive && dstToTarget > 15f) ? 0.8f : 6.0f;
        currentLookAt.lerp(ballPos, lookSpeed * delta);

        distance = MathUtils.lerp(distance, targetDistance, lerpSpeed);
        pitch = MathUtils.lerp(pitch, targetPitch, lerpSpeed);

        if (Gdx.input.isButtonPressed(Buttons.RIGHT) && !introActive) {
            yaw += -Gdx.input.getDeltaX() * mouseSensitivity;
            targetPitch = MathUtils.clamp(targetPitch + (-Gdx.input.getDeltaY() * mouseSensitivity), -10f, 85f);
        }

        float radYaw = -MathUtils.degreesToRadians * yaw;
        float radPitch = MathUtils.degreesToRadians * pitch;

        // Desired gameplay position
        float tx = ballPos.x + distance * MathUtils.cos(radPitch) * MathUtils.sin(radYaw);
        float ty = ballPos.y + distance * MathUtils.sin(radPitch);
        float tz = ballPos.z + distance * MathUtils.cos(radPitch) * MathUtils.cos(radYaw);
        tempTargetPos.set(tx, ty, tz);

        // 3. APPLY POSITION
        camera.position.lerp(tempTargetPos, lerpSpeed);
        camera.up.set(0, 1, 0);
        camera.lookAt(currentLookAt);

        // 4. THE RELEASE: If we are close enough, give control back to player
        if (introActive && dstToTarget < 3.0f) {
            introActive = false;
        }
    }
}