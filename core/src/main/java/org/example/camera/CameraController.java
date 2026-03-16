package org.example.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.GameConfig;

public class CameraController {

    private final PerspectiveCamera camera;
    private final GameConfig.CameraConfig config;

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

    private boolean isOverhead = false;
    private boolean isPaused = false;

    public CameraController(PerspectiveCamera camera, float initialDistance, Vector3 startLookAt, GameConfig.CameraConfig config) {
        this.camera = camera;
        this.config = config;
        this.distance = initialDistance;
        this.targetDistance = 12f;
        this.currentLookAt.set(startLookAt);
        updateCursorState();
    }

    public void updateCursorState() {
        // Only catch if in FREE mode AND not paused
        if (config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE && !isPaused) {
            Gdx.input.setCursorCatched(true);
        } else {
            Gdx.input.setCursorCatched(false);
        }
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        updateCursorState(); // Ensure cursor is released immediately on pause
    }

    public boolean handleScroll(float amountY) {
        if (isPaused) return false;

        if (isOverhead) {
            overheadZoom += amountY * 25.0f;
            overheadZoom = MathUtils.clamp(overheadZoom, 50f, 1200f);
            return true;
        }

        if (Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            targetDistance += amountY * 3.0f;
            targetDistance = MathUtils.clamp(targetDistance, 2.1f, 150f);
            return true;
        }
        return false;
    }

    public void update(Vector3 ballPos) {
        if (isPaused) return;
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

        float deltaX = Gdx.input.getDeltaX();
        float deltaY = Gdx.input.getDeltaY();

        if (Gdx.input.isButtonPressed(Buttons.LEFT)) {
            float panScale = (overheadZoom / Gdx.graphics.getHeight()) * 1.5f;
            float radYaw = -MathUtils.degreesToRadians * yaw;
            float sinYaw = MathUtils.sin(radYaw);
            float cosYaw = MathUtils.cos(radYaw);

            overheadCenter.x += ((-cosYaw) * deltaX * panScale) + ((-sinYaw) * deltaY * panScale);
            overheadCenter.z += (sinYaw * deltaX * panScale) + ((-cosYaw) * deltaY * panScale);
        } else if (Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            yaw += deltaX * config.mouseSensitivity * config.getXMult();
        }

        float pitchRad = 70f * MathUtils.degreesToRadians;
        float yawRad = -MathUtils.degreesToRadians * yaw;
        float verticalDist = overheadZoom * MathUtils.sin(pitchRad);
        float horizontalDist = overheadZoom * MathUtils.cos(pitchRad);

        tempTargetPos.set(
                overheadCenter.x + horizontalDist * MathUtils.sin(yawRad),
                overheadCenter.y + verticalDist,
                overheadCenter.z + horizontalDist * MathUtils.cos(yawRad)
        );

        if (Gdx.input.isButtonPressed(Buttons.LEFT) || Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            camera.position.set(tempTargetPos);
        } else {
            camera.position.lerp(tempTargetPos, 8f * delta);
        }

        camera.up.set(0, 1, 0);
        camera.lookAt(overheadCenter);
    }

    private void updateNormalMode(Vector3 ballPos, float delta) {
        float dstToTarget = camera.position.dst(tempTargetPos);
        float lerpBase = introActive ? 1.5f : config.lerpSpeed;
        float lerpSpeed = lerpBase * delta;
        float lookSpeed = (introActive && dstToTarget > 15f) ? 0.8f : 8.0f;

        currentLookAt.lerp(ballPos, lookSpeed * delta);
        distance = MathUtils.lerp(distance, targetDistance, lerpSpeed);
        pitch = MathUtils.lerp(pitch, targetPitch, lerpSpeed);

        boolean canRotate = (config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE && !introActive)
                || (config.controlStyle == GameConfig.CameraConfig.ControlStyle.DRAG && Gdx.input.isButtonPressed(Buttons.RIGHT));

        if (canRotate) {
            float sens = Gdx.input.isButtonPressed(Buttons.RIGHT) && config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE
                    ? config.mouseSensitivity / config.fineTuneDivider
                    : config.mouseSensitivity;

            yaw += Gdx.input.getDeltaX() * sens * config.getXMult();
            targetPitch = MathUtils.clamp(targetPitch + (Gdx.input.getDeltaY() * sens * config.getYMult()), -10f, 85f);
        }

        float radYaw = -MathUtils.degreesToRadians * yaw;
        float radPitch = MathUtils.degreesToRadians * pitch;

        tempTargetPos.set(
                ballPos.x + distance * MathUtils.cos(radPitch) * MathUtils.sin(radYaw),
                ballPos.y + distance * MathUtils.sin(radPitch),
                ballPos.z + distance * MathUtils.cos(radPitch) * MathUtils.cos(radYaw)
        );

        camera.position.lerp(tempTargetPos, lerpSpeed);
        camera.up.set(0, 1, 0);
        camera.lookAt(currentLookAt);

        if (introActive && dstToTarget < 3.0f) introActive = false;
    }

    public void setOrientation(Vector3 target) {
        Vector3 dir = new Vector3(target).sub(currentLookAt).nor();
        this.yaw = MathUtils.atan2(-dir.x, -dir.z) * MathUtils.radiansToDegrees;
        this.pitch = 15f;
        this.targetPitch = 15f;
        this.introActive = false;
    }

    public float getYaw() { return yaw; }
    public boolean isOverhead() { return isOverhead; }
}