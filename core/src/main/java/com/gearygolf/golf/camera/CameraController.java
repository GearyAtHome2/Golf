package com.gearygolf.golf.camera;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input.Buttons;
import com.gearygolf.golf.Platform;
import com.badlogic.gdx.graphics.PerspectiveCamera;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.input.GameInputProcessor;

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

    private static final float ZOOM_SENS_THRESHOLD = 6f;   // default distance — full sensitivity at or beyond this
    private static final float ZOOM_SENS_MIN_DIST  = 2.1f; // closest zoom — sensitivity floored here
    private static final float ZOOM_SENS_FLOOR     = 0.55f; // 55% at closest zoom

    private float zoomSensScale = 1f; // 0.1–1.0, updated each normal-mode frame

    // Swing view (Step 1): animated top-down zoom when a shot is being taken.
    // The camera sits to the LEFT of the ball relative to ball motion (golfer's stance side),
    // offset by SWING_SIDE_DIST. Looking at the ball from this position creates a natural tilt.
    // Left of aimDir (horizontal) = (aimDir.z, 0, -aimDir.x).
    private static final float SWING_HEIGHT    = 3.2f; // units above ball
    private static final float SWING_SIDE_DIST = 1.0f; // units to the left of ball (produces ~16° tilt)
    // Baseline aim offset (world units) that centres the view on a 7-iron (-4° natural attack).
    // Calculated as: 7i_naturalAttack * -CAM_SCALE = -(-4.0) * 0.10 = 0.40.
    // Subtracted from swingCamAimOffset each frame so the attack-angle scale stays unchanged
    // while the screen framing shifts: driver appears left, 7i central, wedges slightly right.
    private static final float SWING_AIM_CENTER_OFFSET = 0.40f;
    private boolean swingViewActive = false;
    private float swingProgress = 0f; // 0 = normal camera, 1 = fully in swing view
    private final Vector3 swingAimDir = new Vector3(0, 0, -1); // horizontal aim direction when shot started
    private final Vector3 swingCamTarget = new Vector3();
    private final Vector3 swingUpTarget = new Vector3(1, 0, 0);
    private final Vector3 tempSwing = new Vector3();
    /** World-space offset applied to swingCamTarget along the aim axis for attack-angle control.
     *  Set each frame by GolfGame from SwingOverlay.getCamAimOffset(). */
    private float swingCamAimOffset = 0f;

    private boolean isOverhead = false;
    private boolean isPaused = false;
    private boolean skipRotation = true; // discard stale mouse delta on first update after load
    private boolean detached = false;    // trailer mode: camera orbits fixed point, ignores ball position

    public CameraController(PerspectiveCamera camera, float initialDistance, Vector3 startLookAt, GameConfig.CameraConfig config) {
        this.camera = camera;
        this.config = config;
        this.distance = initialDistance;
        this.targetDistance = 12f;
        this.currentLookAt.set(startLookAt);
        updateCursorState();
    }

    public void updateCursorState() {
        if (swingViewActive) {
            Gdx.input.setCursorCatched(false); // release cursor so user can drag the swing gesture
        } else if (config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE && !isPaused) {
            Gdx.input.setCursorCatched(true);
        } else {
            Gdx.input.setCursorCatched(false);
        }
    }

    public void setPaused(boolean paused) {
        this.isPaused = paused;
        updateCursorState();
    }

    public void setDetached(boolean d) {
        this.detached = d;
        if (!d) introActive = false; // re-attaching: skip intro lerp, snap back to tracking
    }

    public boolean isDetached() { return detached; }

    /**
     * Shift the detached focal point relative to the camera's current yaw.
     * @param forward  +1 = pan away from camera, -1 = pan toward camera
     * @param right    +1 = pan right, -1 = pan left
     * @param delta    frame delta (seconds)
     */
    public void panFocalPoint(float forward, float right, float delta) {
        if (!detached) return;
        float speed = distance * 1.8f; // pan speed scales with zoom distance
        float radYaw = -MathUtils.degreesToRadians * yaw;
        // Forward direction (camera looks toward -sin(yaw), -cos(yaw) on XZ plane)
        float fwdX = -MathUtils.sin(radYaw);
        float fwdZ = -MathUtils.cos(radYaw);
        // Right direction: rotate forward 90° clockwise
        currentLookAt.x += (fwdX * forward - fwdZ * right) * speed * delta;
        currentLookAt.z += (fwdZ * forward + fwdX * right) * speed * delta;
    }


    public void setSwingViewActive(boolean active) {
        this.swingViewActive = active;
        updateCursorState();
    }

    /** Call once when charging starts, passing the camera's horizontal aim direction. */
    public void setSwingAimDir(Vector3 dir) {
        float len = (float) Math.sqrt(dir.x * dir.x + dir.z * dir.z);
        if (len > 0.001f) swingAimDir.set(dir.x / len, 0f, dir.z / len);
    }

    public boolean isInSwingView() { return swingProgress > 0.5f; }

    public Vector3 getSwingAimDir() { return swingAimDir; }

    /** Updated each frame from SwingOverlay.getCamAimOffset() to shift the swing camera
     *  along the aim axis for attack-angle (ball-placement) control. */
    public void setSwingCamAimOffset(float offset) { this.swingCamAimOffset = offset; }

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

        // Swing view animation: blend camera toward top-down close-up over ~0.5s
        float swingTarget = swingViewActive ? 1f : 0f;
        swingProgress = MathUtils.lerp(swingProgress, swingTarget, 8f * delta);

        if (swingProgress > 0.001f) {
            // Left of aimDir (when facing the target) = (aimDir.z, 0, -aimDir.x)
            float leftX = swingAimDir.z;
            float leftZ = -swingAimDir.x;

            // Camera is to the LEFT of the ball and above it, offset along aim axis for attack angle.
            // SWING_AIM_CENTER_OFFSET shifts the baseline so 7-iron appears centred on screen;
            // attack-angle offsets are then applied symmetrically around that point.
            float aimOffset = swingCamAimOffset - SWING_AIM_CENTER_OFFSET;
            swingCamTarget.set(
                    ballPos.x + leftX * SWING_SIDE_DIST + swingAimDir.x * aimOffset,
                    ballPos.y + SWING_HEIGHT,
                    ballPos.z + leftZ * SWING_SIDE_DIST + swingAimDir.z * aimOffset
            );

            // Derived direction and up so that: target (aimDir) appears to the LEFT of screen.
            // With camera at (leftX*d, h, leftZ*d) relative to ball, looking at ball:
            //   direction = (-leftX*d, -h, -leftZ*d).nor()
            //   up        = (-aimDir.z * h/L,  d/L,  aimDir.x * h/L)
            // where L = sqrt(h²+d²). Both derived analytically from the constraint
            // that camera.right = direction × up = -aimDir.
            float d = SWING_SIDE_DIST;
            float h = SWING_HEIGHT;
            float L = (float) Math.sqrt(h * h + d * d);

            tempSwing.set(-leftX * d / L, -h / L, -leftZ * d / L); // direction toward ball
            swingUpTarget.set(-swingAimDir.z * h / L, d / L, swingAimDir.x * h / L);

            camera.position.lerp(swingCamTarget, swingProgress);
            camera.direction.lerp(tempSwing, swingProgress).nor();
            camera.up.lerp(swingUpTarget, swingProgress).nor();
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

        boolean isAndroid = Platform.isAndroid();
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

        if (!detached) currentLookAt.lerp(ballPos, lookSpeed * delta);
        distance = MathUtils.lerp(distance, targetDistance, lerpSpeed);
        pitch = MathUtils.lerp(pitch, targetPitch, lerpSpeed);

        float scrollY = input.getActionValue(GameInputProcessor.Action.SCROLL_Y);
        if (scrollY != 0) {
            boolean isMod = input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) ||
                    input.isActionPressed(GameInputProcessor.Action.OVERHEAD_VIEW) ||
                    Platform.isAndroid();

            System.out.println("[DEBUG_LOG] Normal Zoom check: " + scrollY + " | Mods: " + isMod);

            if (isMod) {
                targetDistance += scrollY * 3.0f;
                targetDistance = MathUtils.clamp(targetDistance, 2.1f, 150f);
                System.out.println("[DEBUG_LOG] Target Distance: " + targetDistance);
            }
        }

        boolean canRotate = (config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE && !introActive)
                || (config.controlStyle == GameConfig.CameraConfig.ControlStyle.DRAG && (input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) || Platform.isAndroid()));

        // Scale sensitivity down when zoomed in close — full speed at ZOOM_SENS_THRESHOLD or beyond.
        zoomSensScale = MathUtils.lerp(ZOOM_SENS_FLOOR, 1f,
                MathUtils.clamp((distance - ZOOM_SENS_MIN_DIST) / (ZOOM_SENS_THRESHOLD - ZOOM_SENS_MIN_DIST), 0f, 1f));

        if (canRotate && !skipRotation && swingProgress < 0.3f) {
            float sens = input.isActionPressed(GameInputProcessor.Action.SECONDARY_ACTION) && config.controlStyle == GameConfig.CameraConfig.ControlStyle.FREE
                    ? config.mouseSensitivity / config.fineTuneDivider
                    : config.mouseSensitivity;

            float deltaX = input.getActionValue(GameInputProcessor.Action.DRAG_X);
            float deltaY = input.getActionValue(GameInputProcessor.Action.DRAG_Y);

            yaw += deltaX * sens * zoomSensScale * config.getXMult();
            targetPitch = MathUtils.clamp(targetPitch + (deltaY * sens * zoomSensScale * config.getYMult()), -10f, 85f);
        }
        skipRotation = false;

        float radYaw = -MathUtils.degreesToRadians * yaw;
        float radPitch = MathUtils.degreesToRadians * pitch;

        // In detached mode orbit around the frozen look-at rather than the live ball position.
        Vector3 orbitCenter = detached ? currentLookAt : ballPos;
        tempTargetPos.set(
                orbitCenter.x + distance * MathUtils.cos(radPitch) * MathUtils.sin(radYaw),
                orbitCenter.y + distance * MathUtils.sin(radPitch),
                orbitCenter.z + distance * MathUtils.cos(radPitch) * MathUtils.cos(radYaw)
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

    /** Returns zoom-relative sensitivity as an integer 10–100 for debug display. */
    public int getZoomSensitivityPct() { return Math.round(zoomSensScale * 100f); }

    public float getYaw() {
        return yaw;
    }

    public boolean isOverhead() {
        return isOverhead;
    }
}