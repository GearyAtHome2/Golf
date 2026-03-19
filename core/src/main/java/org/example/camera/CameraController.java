package org.example.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import org.example.GameConfig;
import org.example.input.GameInputProcessor;

public class CameraController {

    private final PerspectiveCamera camera;
    private final GameConfig.CameraConfig config;

    private float yaw = 180f;
    private float pitch = 20f;
    private float distance;
    private static final float ROTATION_DEADZONE = 1.5f;
    private float targetPitch = 20f;
    private float targetDistance;

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


    public void update(Vector3 ballPos, GameInputProcessor input) {
        if (isPaused) return;
        float delta = Gdx.graphics.getDeltaTime();

        isOverhead = input.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW);

        if (isOverhead) {
            updateOverheadMode(ballPos, delta, input);
            wasOverheadLastFrame = true;
        } else {
            if (wasOverheadLastFrame) {
                currentLookAt.set(ballPos);
                camera.up.set(0, 1f, 0);
            }
            updateNormalMode(ballPos, delta, input);
            wasOverheadLastFrame = false;
        }

        camera.update();
    }

    private void updateOverheadMode(Vector3 ballPos, float delta, GameInputProcessor input) {
        if (!wasOverheadLastFrame) {
            overheadCenter.set(ballPos);
            introActive = false;
        }

        float deltaX = input.getActionValue(GameInputProcessor.Action.DRAG_X);
        float deltaY = input.getActionValue(GameInputProcessor.Action.DRAG_Y);
        float scrollY = input.getActionValue(GameInputProcessor.Action.SCROLL_Y);

        if (scrollY != 0) {
            overheadZoom += scrollY * 25.0f;
            overheadZoom = MathUtils.clamp(overheadZoom, 50f, 1200f);
        }

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        boolean isMultiTouch = input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION);

        boolean shouldPan = isAndroid ? isMultiTouch : Gdx.input.isButtonPressed(Buttons.LEFT);
        // Only rotate if we aren't panning and there's an active touch
        boolean shouldRotate = isAndroid ? (!isMultiTouch && Gdx.input.isTouched()) : input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION);

        if (shouldPan) {
            float panScale = (overheadZoom / Gdx.graphics.getHeight()) * 1.5f;
            float radYaw = -MathUtils.degreesToRadians * yaw;
            float sinYaw = MathUtils.sin(radYaw);
            float cosYaw = MathUtils.cos(radYaw);

            overheadCenter.x += ((-cosYaw) * deltaX * panScale) + ((-sinYaw) * deltaY * panScale);
            overheadCenter.z += (sinYaw * deltaX * panScale) + ((-cosYaw) * deltaY * panScale);
        } else if (shouldRotate) {
            // Apply deadzone to prevent jittering during finger placement/lifting
            if (Math.abs(deltaX) > ROTATION_DEADZONE) {
                yaw += deltaX * config.mouseSensitivity * config.getXMult();
            }
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

        if (shouldPan || shouldRotate) {
            camera.position.set(tempTargetPos);
        } else {
            camera.position.lerp(tempTargetPos, 8f * delta);
        }

        camera.up.set(0, 1, 0);
        camera.lookAt(overheadCenter);
    }
    private void updateNormalMode(Vector3 ballPos, float delta, GameInputProcessor input) {
        float dstToTarget = camera.position.dst(tempTargetPos);
        float lerpBase = introActive ? 1.5f : config.lerpSpeed;
        float lerpSpeed = lerpBase * delta;
        float lookSpeed = (introActive && dstToTarget > 15f) ? 0.8f : 8.0f;

        currentLookAt.lerp(ballPos, lookSpeed * delta);
        distance = MathUtils.lerp(distance, targetDistance, lerpSpeed);
        pitch = MathUtils.lerp(pitch, targetPitch, lerpSpeed);

        float scrollY = input.getActionValue(GameInputProcessor.Action.SCROLL_Y);
        if (scrollY != 0) {
            boolean isMod = input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) || 
                            input.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW) || 
                            Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
            
            System.out.println("[DEBUG_LOG] Normal Zoom check: " + scrollY + " | Mods: " + isMod);
            
            if (isMod) {
                targetDistance += scrollY * 3.0f;
                targetDistance = MathUtils.clamp(targetDistance, 2.1f, 150f);
                System.out.println("[DEBUG_LOG] Target Distance: " + targetDistance);
            }
        }

        boolean canRotate = (config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE && !introActive)
                || (config.controlStyle == GameConfig.CameraConfig.ControlStyle.DRAG && (input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) || Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android));

        if (canRotate) {
            float sens = input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) && config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE
                    ? config.mouseSensitivity / config.fineTuneDivider
                    : config.mouseSensitivity;

            float deltaX = input.getActionValue(GameInputProcessor.Action.DRAG_X);
            float deltaY = input.getActionValue(GameInputProcessor.Action.DRAG_Y);

            yaw += deltaX * sens * config.getXMult();
            targetPitch = MathUtils.clamp(targetPitch + (deltaY * sens * config.getYMult()), -10f, 85f);
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