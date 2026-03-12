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
    private final float fineTuneDivider = 5.0f;
    boolean isOverhead = false;
    private boolean isPaused = false;

    public FreeCameraController(PerspectiveCamera camera, float initialDistance, Vector3 startLookAt) {
        this.camera = camera;
        this.distance = initialDistance;
        this.targetDistance = 12f;
        this.currentLookAt.set(startLookAt);
        Gdx.input.setCursorCatched(true);
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
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
        float delta = Gdx.graphics.getDeltaTime();
        if (isPaused) return;

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

        // Fetch deltas once to ensure consistency
        float deltaX = Gdx.input.getDeltaX();
        float deltaY = Gdx.input.getDeltaY();

        // LOGIC GATE: Prioritize one action at a time to prevent "Ghost Rotation"
        if (Gdx.input.isButtonPressed(Buttons.LEFT)) {
            // PANNING ONLY
            float panScale = (overheadZoom / Gdx.graphics.getHeight()) * 1.5f;
            float radYaw = -MathUtils.degreesToRadians * yaw;
            float sinYaw = MathUtils.sin(radYaw);
            float cosYaw = MathUtils.cos(radYaw);

            float forwardX = -sinYaw;
            float forwardZ = -cosYaw;
            float rightX = -cosYaw;
            float rightZ = sinYaw;

            overheadCenter.x += (rightX * deltaX * panScale) + (forwardX * deltaY * panScale);
            overheadCenter.z += (rightZ * deltaX * panScale) + (forwardZ * deltaY * panScale);
        }
        else if (Gdx.input.isButtonPressed(Buttons.RIGHT)) {
            // ROTATION ONLY
            yaw += -deltaX * mouseSensitivity;
        }

        float pitchRad = 70f * MathUtils.degreesToRadians;
        float yawRad = -MathUtils.degreesToRadians * yaw;
        float verticalDist = overheadZoom * MathUtils.sin(pitchRad);
        float horizontalDist = overheadZoom * MathUtils.cos(pitchRad);

        float tx = overheadCenter.x + horizontalDist * MathUtils.sin(yawRad);
        float ty = overheadCenter.y + verticalDist;
        float tz = overheadCenter.z + horizontalDist * MathUtils.cos(yawRad);

        tempTargetPos.set(tx, ty, tz);

        // Snap immediately during interaction for a crisp feel, lerp only on entry
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
        float lerpBase = introActive ? 1.5f : 6.0f;
        float lerpSpeed = lerpBase * delta;
        float lookSpeed = (introActive && dstToTarget > 15f) ? 0.8f : 8.0f;

        currentLookAt.lerp(ballPos, lookSpeed * delta);
        distance = MathUtils.lerp(distance, targetDistance, lerpSpeed);
        pitch = MathUtils.lerp(pitch, targetPitch, lerpSpeed);

        if (!introActive) {
            boolean isFineTuning = Gdx.input.isButtonPressed(Buttons.RIGHT);
            float currentSensitivity = isFineTuning ? mouseSensitivity / fineTuneDivider : mouseSensitivity;

            yaw += -Gdx.input.getDeltaX() * currentSensitivity;
            targetPitch = MathUtils.clamp(targetPitch + (-Gdx.input.getDeltaY() * currentSensitivity), -10f, 85f);
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

    public void setOrientation(Vector3 target) {
        Vector3 dir = new Vector3(target).sub(currentLookAt).nor();
        this.yaw = MathUtils.atan2(-dir.x, -dir.z) * MathUtils.radiansToDegrees;
        this.pitch = 15f;
        this.targetPitch = 15f;
        this.introActive = false;
    }

    public float getYaw() {
        return yaw;
    }

    public boolean isOverhead() {
        return isOverhead;
    }
}