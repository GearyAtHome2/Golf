package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

public class FreeCameraController {

    private final PerspectiveCamera camera;

    private float yaw = 180f;
    private float pitch = 20f;
    private float distance = 10f;
    private float targetPitch = 20f;
    private float targetDistance = 12f;

    private float overheadZoom = 300f;
    private final Vector3 overheadCenter = new Vector3();
    private boolean wasOverheadLastFrame = false;

    private final Vector3 currentLookAt = new Vector3();
    private boolean introActive = true;
    private final Vector3 tempTargetPos = new Vector3();

    private final float mouseSensitivity = 0.3f;
    private boolean isOverhead = false;

    public FreeCameraController(PerspectiveCamera camera, float initialDistance, Vector3 startLookAt) {
        this.camera = camera;
        this.distance = initialDistance;
        this.targetDistance = 12f;
        this.currentLookAt.set(startLookAt);
    }

    public void setIntroActive(boolean active) {
        this.introActive = active;
    }

    public boolean handleScroll(float amountY) {
        if (isOverhead) {
            overheadZoom += amountY * 25.0f;
            overheadZoom = MathUtils.clamp(overheadZoom, 50f, 1200f);
        } else {
            targetDistance += amountY * 3.0f;
            targetDistance = MathUtils.clamp(targetDistance, 2.1f, 150f);
        }
        return true;
    }

    public void update(Vector3 ballPos) {
        float delta = Gdx.graphics.getDeltaTime();
        isOverhead = Gdx.input.isKeyPressed(Input.Keys.TAB);

        if (isOverhead) {
            updateOverheadMode(ballPos, delta);
            wasOverheadLastFrame = true;
        } else {
            if (wasOverheadLastFrame) {
                currentLookAt.set(ballPos);
                camera.up.set(0, 1f, 0);
            }

            updateNormalMode(ballPos, delta);
            wasOverheadLastFrame = false;
        }

        camera.update();
    }

    private void updateOverheadMode(Vector3 ballPos, float delta) {
        if (!wasOverheadLastFrame) {
            overheadCenter.set(ballPos);
            introActive = false;
        }

        // 1. Handle Input
        if (Gdx.input.isButtonPressed(Buttons.LEFT)) {
            yaw += -Gdx.input.getDeltaX() * mouseSensitivity;
        } else if (Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            float panScale = overheadZoom * 1.4f / Gdx.graphics.getHeight();

            float radYaw = -MathUtils.degreesToRadians * yaw;
            float sinYaw = MathUtils.sin(radYaw);
            float cosYaw = MathUtils.cos(radYaw);

            // Forward vector (pointing "away" from camera on XZ plane)
            float forwardX = -sinYaw;
            float forwardZ = -cosYaw;

            // Right vector (perpendicular to forward)
            // Corrected signs to ensure screen-right matches world-right relative to view
            float rightX = -cosYaw;
            float rightZ = sinYaw;

            float mouseX = Gdx.input.getDeltaX();
            float mouseY = Gdx.input.getDeltaY();

            // Apply movement
            overheadCenter.x += (rightX * mouseX * panScale) + (forwardX * mouseY * panScale);
            overheadCenter.z += (rightZ * mouseX * panScale) + (forwardZ * mouseY * panScale);
        }

        // 2. Calculate Position (Using your specific 70 degree pitchRad)
        float pitchRad = 70f * MathUtils.degreesToRadians;
        float yawRad = -MathUtils.degreesToRadians * yaw;

        float verticalDist = overheadZoom * MathUtils.sin(pitchRad);
        float horizontalDist = overheadZoom * MathUtils.cos(pitchRad);

        float tx = overheadCenter.x + horizontalDist * MathUtils.sin(yawRad);
        float ty = overheadCenter.y + verticalDist;
        float tz = overheadCenter.z + horizontalDist * MathUtils.cos(yawRad);

        tempTargetPos.set(tx, ty, tz);

        // 3. Remove Lerp for active movement to stop the "laggy" feel
        // We only lerp if we just switched to overhead, otherwise we snap for precision
        if (camera.position.dst(tempTargetPos) > 50f) {
            camera.position.lerp(tempTargetPos, 8f * delta);
        } else {
            camera.position.set(tempTargetPos);
        }

        camera.up.set(0, 1, 0);
        camera.lookAt(overheadCenter);
    }

    public boolean isOverhead() {
        introActive = false;
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

        float tx = ballPos.x + distance * MathUtils.cos(radPitch) * MathUtils.sin(radYaw);
        float ty = ballPos.y + distance * MathUtils.sin(radPitch);
        float tz = ballPos.z + distance * MathUtils.cos(radPitch) * MathUtils.cos(radYaw);
        tempTargetPos.set(tx, ty, tz);

        camera.position.lerp(tempTargetPos, lerpSpeed);
        camera.up.set(0, 1, 0);
        camera.lookAt(currentLookAt);

        if (introActive && dstToTarget < 3.0f) {
            introActive = false;
        }
    }
}