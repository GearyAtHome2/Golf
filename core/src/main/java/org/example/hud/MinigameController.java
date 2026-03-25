package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.GameConfig;
import org.example.ball.MinigameResult;
import org.example.ball.ShotDifficulty;
import org.example.hud.renderer.NotificationManager;
import org.example.input.GameInputProcessor;
import org.example.terrain.Terrain;

public class MinigameController {
    private final MinigameEngine engine = new MinigameEngine();
    private final MinigameUI minigameUI = new MinigameUI();
    private final Array<ModAnimation> activeAnims = new Array<>();
    private NotificationManager notificationManager;

    private boolean active = false;
    private boolean needleStopped = false;
    private boolean canceled = false;
    private int step = 0;
    private float timer = 0;
    private float needleSpeed = 0;
    private float shotRandomness = 0;
    private float activePowerMod;
    private ShotDifficulty activeDiff;
    private MinigameResult lastResult = null;

    private float barSwellTimer = 0;
    private float glowTimer = 0;
    private Color glowColor = new Color(0, 0, 0, 0);
    private final Vector3 activeBallPos = new Vector3();

    private Runnable onShotFinalized;

    public void setNotificationManager(NotificationManager notificationManager) {
        this.notificationManager = notificationManager;
    }

    public void start(Vector3 ballPos, Club club, ShotDifficulty diff, float powerMod, GameConfig.AnimSpeed animSetting, GameConfig.Difficulty gameDiff) {
        this.activeDiff = diff;
        this.activePowerMod = powerMod;
        this.activeBallPos.set(ballPos);
        this.active = true;
        this.needleStopped = false;
        this.canceled = false;
        this.lastResult = null;
        this.step = 0;
        this.shotRandomness = MathUtils.random(-0.1f, 0.1f);

        engine.reset(club.baseDifficulty);
        activeAnims.clear();

        if (animSetting == GameConfig.AnimSpeed.NONE) {
            engine.barWidthMult = (1.0f / activeDiff.terrainDifficulty) * (1.0f / activeDiff.clubDifficulty) * (1.0f / activeDiff.swingDifficulty);
            step = 4;
            needleSpeed = activePowerMod * 1.5f * gameDiff.needleSpeedMult;
        } else {
            timer = (0.7f / animSetting.mult);
        }
    }

    public void updateAndDraw(float delta, Camera camera, Terrain terrain, Vector2 spinDot,
                              GameConfig.AnimSpeed animSetting, GameConfig.Difficulty gameDiff,
                              ShapeRenderer shapeRenderer, SpriteBatch batch, BitmapFont font,
                              Viewport viewport, GameInputProcessor input,
                              org.example.ball.ShotController shotController) {
        boolean shouldCancel;
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android) {
            shouldCancel = input.isActionJustPressed(GameInputProcessor.Action.TAP);
        } else {
            shouldCancel = Gdx.input.isButtonJustPressed(Input.Buttons.LEFT);
        }

        if (!needleStopped && shouldCancel) {
            cancel();
            return;
        }

        updateAnimations(delta);

        if (!needleStopped) {
            timer += delta;
            float stepDuration = (animSetting == GameConfig.AnimSpeed.NONE) ? 0.01f : (0.7f / animSetting.mult);
            Vector3 normal = terrain.getNormalAt(activeBallPos.x, activeBallPos.z);
            Vector3 referenceDir = (shotController != null) ? shotController.getLockedCamDir() : camera.direction;
            Vector3 rightOfAim = new Vector3(referenceDir).crs(Vector3.Y).nor();

            engine.updateZones(delta, normal.dot(rightOfAim), spinDot.x, shotRandomness);

            if (step < 4 && timer >= stepDuration) advanceStep(animSetting, gameDiff);

            if (step == 4) {
                float move = delta * needleSpeed;
                engine.needlePos += (engine.needleMovingRight ? move : -move);
                if (engine.needlePos >= 1.0f) {
                    engine.needlePos = 1.0f;
                    engine.needleMovingRight = false;
                } else if (engine.needlePos <= 0f) {
                    engine.needlePos = 0f;
                    engine.needleMovingRight = true;
                }

                if (input.isActionJustPressed(GameInputProcessor.Action.STOP_NEEDLE)) {
                    stopNeedle(spinDot, shotController);
                }
            }
        } else {
            glowTimer -= delta;
            if (glowTimer <= 0) {
                active = false;
                activeAnims.clear();
            }
        }

        minigameUI.draw(shapeRenderer, batch, font, viewport.getWorldWidth(), viewport.getWorldHeight(), engine, barSwellTimer, glowTimer, glowColor, activeAnims);
    }

    public void setOnShotFinalized(Runnable callback) {
        this.onShotFinalized = callback;
    }

    private void advanceStep(GameConfig.AnimSpeed animSetting, GameConfig.Difficulty gameDiff) {
        step++;
        timer = 0;
        String text = "";
        float mult = 1.0f;
        switch (step) {
            case 1 -> {
                mult = activeDiff.terrainDifficulty;
                text = "TERRAIN x" + String.format("%.2f", mult);
                engine.barWidthMult *= (1.0f / mult);
            }
            case 2 -> {
                mult = activeDiff.clubDifficulty;
                text = "WEIGHT x" + String.format("%.2f", mult);
                engine.barWidthMult *= (1.0f / mult);
            }
            case 3 -> {
                mult = activeDiff.swingDifficulty;
                text = "PRECISION x" + String.format("%.2f", mult);
                engine.barWidthMult *= (1.0f / mult);
            }
            case 4 -> {
                text = "GO!";
                needleSpeed = activePowerMod * 1.5f * gameDiff.needleSpeedMult;
            }
        }
        if (animSetting != GameConfig.AnimSpeed.NONE) {
            Color c = (mult <= 1.05f) ? Color.GREEN : Color.RED;
            if (step == 4) c = Color.GOLD;
            activeAnims.add(new ModAnimation(text, c));
        }
    }

    private void stopNeedle(Vector2 currentSpin, org.example.ball.ShotController shotController) {
        needleStopped = true;
        glowTimer = 1.0f;
        lastResult = engine.calculateResult(engine.needlePos);
        glowColor = lastResult.rating.color;

        // Trigger the callback if it exists
        if (onShotFinalized != null) {
            onShotFinalized.run();
        }

        if (notificationManager != null) {
            notificationManager.showFeedback(lastResult, 2.0f);
        }

        if (shotController != null) {
            shotController.snapshotFinalSpin(currentSpin);
        }
    }

    private void updateAnimations(float delta) {
        for (int i = activeAnims.size - 1; i >= 0; i--) {
            ModAnimation anim = activeAnims.get(i);
            anim.life -= delta * 1.8f;
            float progress = 1.0f - anim.life;
            anim.yOffset = MathUtils.lerp(-5f, 45f, Interpolation.swingIn.apply(progress));
            anim.alpha = MathUtils.clamp(anim.life * 5f, 0, 1);
            anim.scale = 0.8f + (progress * 0.4f);
            if (!anim.hitBar && anim.yOffset > 15f) {
                anim.hitBar = true;
                barSwellTimer = 0.3f;
            }
            if (anim.life <= 0 || anim.hitBar) activeAnims.removeIndex(i);
        }
        if (barSwellTimer > 0) barSwellTimer = Math.max(0, barSwellTimer - delta);
    }

    public void reset() {
        active = false;
        needleStopped = false;
        canceled = false;
        lastResult = null;
        step = 0;
        timer = 0;
        glowTimer = 0;
        activeAnims.clear();
        engine.needlePos = 0.5f;
    }

    public void cancel() {
        active = false;
        needleStopped = false;
        canceled = true;
        lastResult = null;
        activeAnims.clear();
    }

    public boolean isActive() {
        return active;
    }

    public boolean isNeedleStopped() {
        return needleStopped;
    }

    public boolean wasCanceled() {
        boolean c = canceled;
        canceled = false;
        return c;
    }

    public MinigameResult getResult() {
        return lastResult;
    }

    public float getGlowTimer() {
        return glowTimer;
    }

    public Color getGlowColor() {
        return glowColor;
    }
}