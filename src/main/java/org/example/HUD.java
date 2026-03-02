package org.example;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.ball.Ball;
import org.example.ball.MinigameResult;
import org.example.ball.ShotDifficulty;
import org.example.terrain.Terrain;

public class HUD {
    public enum GameDifficulty {
        EASY(0.3f), MEDIUM(0.75f), HARD(1.0f);
        public final float speedMult;
        GameDifficulty(float speed) { this.speedMult = speed; }
    }

    public enum AnimSpeed {
        NONE(0f), SLOW(0.6f), FAST(1.4f);
        public final float mult;
        AnimSpeed(float mult) { this.mult = mult; }
    }

    private static class ModAnimation {
        String text;
        float yOffset;
        float alpha;
        float scale;
        Color color;
        float life;
        boolean hitBar = false;

        public ModAnimation(String text, Color color) {
            this.text = text;
            this.color = color;
            this.yOffset = -5f;
            this.alpha = 0f;
            this.scale = 0.8f;
            this.life = 1.0f;
        }
    }

    private final SpriteBatch batch;
    private final ShapeRenderer shapeRenderer;
    private final BitmapFont font;
    private final Viewport viewport;
    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;

    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;

    private float seedFeedbackTimer = 0;
    private float shotFeedbackTimer = 0;
    private String shotFeedbackText = "";
    private Color shotFeedbackColor = Color.WHITE;

    private final Vector3 camForward = new Vector3();
    private final Vector3 camRight = new Vector3();

    // --- Settings ---
    public AnimSpeed animSetting = AnimSpeed.SLOW;
    public GameDifficulty currentDifficulty = GameDifficulty.EASY;

    // --- Minigame Variables ---
    private boolean minigameActive = false;
    private boolean needleStopped = false;
    private boolean minigameCanceled = false;
    private float minigameTimer = 0f;
    private int minigameStep = 0;

    private float barWidthMult = 1.0f;
    private float targetBarWidthMult = 1.0f;

    // Centers are stored as 0.0 to 1.0 (screen width percentage)
    private float greenCenter = 0.5f;
    private float targetGreenCenter = 0.5f;
    private float amberCenter = 0.5f;
    private float goldCenter = 0.5f;
    private float purpleCenter = 0.5f;

    private float shotRandomness = 0f;

    // Smooth Swell Logic
    private float barSwellTimer = 0f;
    private final float SWELL_DURATION = 0.3f;

    private float needlePos = 0f;
    private float needleSpeed = 0f;
    private boolean needleMovingRight = true;
    private String currentModText = "";
    private final Array<ModAnimation> activeAnims = new Array<>();

    private float glowTimer = 0f;
    private Color glowColor = new Color(0, 0, 0, 0);

    private ShotDifficulty activeDiff;
    private float activePowerMod;
    private Vector3 activeBallPos = new Vector3();

    private MinigameResult lastResult = null;

    public HUD() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void renderStartMenu(int selection) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;
        font.getData().setScale(3f);
        font.setColor(Color.WHITE);
        font.draw(batch, "GEARY GOLF", centerX - 180, centerY + 150);
        font.getData().setScale(1.5f);
        font.setColor(selection == 0 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 0 ? "> " : "  ") + "PLAY GAME", centerX - 80, centerY);
        font.setColor(selection == 1 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 1 ? "> " : "  ") + "PRACTICE RANGE", centerX - 80, centerY - 60);
        font.setColor(selection == 2 ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selection == 2 ? "> " : "  ") + "PLAY SEED FROM CLIPBOARD", centerX - 80, centerY - 120);
        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 210);
        batch.end();
    }

    public void renderPauseMenu(boolean isPractice, long seed) {
        batch.setProjectionMatrix(viewport.getCamera().combined);

        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            int next = (animSetting.ordinal() + 1) % AnimSpeed.values().length;
            animSetting = AnimSpeed.values()[next];
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            int next = (currentDifficulty.ordinal() + 1) % GameDifficulty.values().length;
            currentDifficulty = GameDifficulty.values()[next];
        }

        batch.begin();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        font.getData().setScale(2.5f);
        font.setColor(Color.WHITE);
        font.draw(batch, "PAUSED", centerX - 80, viewport.getWorldHeight() - 100);

        font.getData().setScale(1.2f);
        font.setColor(Color.YELLOW);
        font.draw(batch, "--- SETTINGS ---", centerX - 80, centerY + 60);
        font.setColor(Color.WHITE);
        font.draw(batch, "[A] ANIMATION: " + animSetting.name(), centerX - 120, centerY + 20);
        font.draw(batch, "[D] DIFFICULTY: " + currentDifficulty.name(), centerX - 120, centerY - 20);

        font.setColor(Color.GRAY);
        font.draw(batch, "----------------", centerX - 80, centerY - 60);
        font.setColor(Color.WHITE);
        String menuText = "[R] RESET BALL  |  [N] NEW LEVEL  |  [ESC] RESUME";
        if (!isPractice) {
            menuText += "  |  [C] COPY SEED";
            if (Gdx.input.isKeyJustPressed(Input.Keys.C)) {
                Gdx.app.getClipboard().setContents(String.valueOf(seed));
                seedFeedbackTimer = 2.0f;
            }
        }
        font.draw(batch, menuText, 50, 100);

        if (seedFeedbackTimer > 0) {
            seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
            font.setColor(Color.GREEN);
            font.draw(batch, "SEED COPIED TO CLIPBOARD: " + seed, 50, 150);
        }
        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain) {
        updateSpinInput(Gdx.graphics.getDeltaTime());
        float delta = Gdx.graphics.getDeltaTime();

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        // Spin UI
        float spinX = 80;
        float spinY = 80;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 3);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(spinX + (spinDot.x * (SPIN_UI_RADIUS - 10)),
                spinY + (spinDot.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();

        if (levelData != null) {
            renderWindIndicator(levelData.getWind(), gameCamera);
        }

        batch.begin();
        if (shotFeedbackTimer > 0) {
            shotFeedbackTimer -= delta;
            font.getData().setScale(2.5f * (shotFeedbackTimer / 1.5f + 0.5f));
            font.setColor(shotFeedbackColor.r, shotFeedbackColor.g, shotFeedbackColor.b, Math.min(1, shotFeedbackTimer * 2));
            font.draw(batch, shotFeedbackText, viewport.getWorldWidth()/2f - 150, viewport.getWorldHeight()/2f + 150);
        }

        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTACT POINT", spinX - 45, spinY - 60);

        font.getData().setScale(1.5f);
        if (isPractice) {
            font.setColor(Color.CYAN);
            font.draw(batch, "PRACTICE RANGE", 40, viewport.getWorldHeight() - 40);
        } else if (levelData != null) {
            font.setColor(Color.GOLD);
            font.draw(batch, levelData.getArchetype().name().replace("_", " "), 40, viewport.getWorldHeight() - 40);
        }

        font.setColor(Color.WHITE);
        font.draw(batch, "Shots: " + shotCount, 40, viewport.getWorldHeight() - 80);

        float rightX = viewport.getWorldWidth() - 250;
        font.draw(batch, "Club: " + currentClub.name(), rightX, 140);

        float currentSpinMag = ball.getSpin().len();
        if (currentSpinMag > 0.1f) {
            font.setColor(currentSpinMag > lastDisplayedSpin ? Color.GREEN : Color.RED);
            font.draw(batch, String.format("Spin: %.1fk RPM", (currentSpinMag * 100f) / 1000f), rightX, 100);
        } else {
            font.setColor(Color.GRAY);
            font.draw(batch, "Spin: 0k RPM", rightX, 100);
        }
        lastDisplayedSpin = currentSpinMag;

        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Speed: %.1fx", gameSpeed), rightX, 60);

        if (ball.getState() == Ball.State.STATIONARY && !minigameActive) {
            renderShotDebug(ball.getPosition(), gameCamera.direction, terrain);
        }

        batch.end();

        if (minigameActive) {
            updateMinigame(delta, gameCamera, terrain);
            drawAccuracyBar();
        }
    }

    private void updateMinigame(float delta, Camera camera, Terrain terrain) {
        if (Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            minigameActive = false;
            needleStopped = false;
            minigameCanceled = true;
            lastResult = null;
            activeAnims.clear();
            return;
        }

        for (int i = activeAnims.size - 1; i >= 0; i--) {
            ModAnimation anim = activeAnims.get(i);
            anim.life -= delta * 1.8f;
            float progress = 1.0f - anim.life;
            float motion = Interpolation.swingIn.apply(progress);
            anim.yOffset = MathUtils.lerp(-5f, 45f, motion);
            anim.alpha = MathUtils.clamp(anim.life * 5f, 0, 1);
            anim.scale = 0.8f + (progress * 0.4f);
            if (!anim.hitBar && anim.yOffset > 15f) {
                anim.hitBar = true;
                barSwellTimer = SWELL_DURATION;
            }
            if (anim.life <= 0 || anim.hitBar) activeAnims.removeIndex(i);
        }

        if (barSwellTimer > 0) {
            barSwellTimer -= delta;
            if (barSwellTimer < 0) barSwellTimer = 0;
        }

        if (!needleStopped) {
            minigameTimer += delta;
            float stepDuration = (animSetting == AnimSpeed.NONE) ? 0.01f : (0.7f / animSetting.mult);

            // 1. Terrain Slope pushes the GREEN zone
            Vector3 normal = terrain.getNormalAt(activeBallPos.x, activeBallPos.z);
            Vector3 rightOfAim = new Vector3(camera.direction).crs(Vector3.Y).nor();
            float sidePush = normal.dot(rightOfAim);

            float greenHalfW = (0.25f * barWidthMult) / 2f;
            targetGreenCenter = 0.5f + (sidePush * 0.4f);
            targetGreenCenter = MathUtils.clamp(targetGreenCenter, greenHalfW, 1.0f - greenHalfW);
            greenCenter = MathUtils.lerp(greenCenter, targetGreenCenter, delta * 6f);

            // 2. Side Spin + Randomness pushes the Nested Children
            // We define the widths here for calculation
            float amberHalfW = greenHalfW * 0.6f;
            float goldHalfW = greenHalfW * 0.15f;
            float purpleHalfW = goldHalfW * 0.20f;

            float rawSpinForce = (spinDot.x * 0.8f + shotRandomness) * 0.1f;

            // Nested Constraint Logic:
            // Amber follows spin but capped by Green bounds
            float targetAmber = greenCenter + rawSpinForce;
            amberCenter = MathUtils.lerp(amberCenter, MathUtils.clamp(targetAmber, greenCenter - (greenHalfW - amberHalfW), greenCenter + (greenHalfW - amberHalfW)), delta * 6f);

            // Gold follows spin but capped by Amber bounds
            float targetGold = amberCenter + rawSpinForce * 1.5f; // Extra sensitivity for inner zones
            goldCenter = MathUtils.lerp(goldCenter, MathUtils.clamp(targetGold, amberCenter - (amberHalfW - goldHalfW), amberCenter + (amberHalfW - goldHalfW)), delta * 6f);

            // Purple follows spin but capped by Gold bounds
            float targetPurple = goldCenter + rawSpinForce * 2.0f;
            purpleCenter = MathUtils.lerp(purpleCenter, MathUtils.clamp(targetPurple, goldCenter - (goldHalfW - purpleHalfW), goldCenter + (goldHalfW - purpleHalfW)), delta * 6f);

            barWidthMult = MathUtils.lerp(barWidthMult, targetBarWidthMult, delta * 6f);

            if (minigameStep < 4 && minigameTimer >= stepDuration) {
                minigameStep++;
                minigameTimer = 0;
                String text = "";
                float mult = 1.0f;
                switch(minigameStep) {
                    case 1: mult = activeDiff.terrainDifficulty; text = "TERRAIN x" + String.format("%.2f", mult); targetBarWidthMult *= (1.0f / mult); break;
                    case 2: mult = activeDiff.clubDifficulty; text = "WEIGHT x" + String.format("%.2f", mult); targetBarWidthMult *= (1.0f / mult); break;
                    case 3: mult = activeDiff.swingDifficulty; text = "PRECISION x" + String.format("%.2f", mult); targetBarWidthMult *= (1.0f / mult); break;
                    case 4: text = "GO!"; needleSpeed = activePowerMod * 1.5f * currentDifficulty.speedMult; break;
                }
                if (animSetting != AnimSpeed.NONE) {
                    Color c = (mult <= 1.05f) ? Color.GREEN : Color.RED;
                    if (minigameStep == 4) c = Color.GOLD;
                    activeAnims.add(new ModAnimation(text, c));
                }
            }

            if (minigameStep == 4) {
                currentModText = "SPACE TO STOP";
                float move = delta * needleSpeed;
                if (needleMovingRight) {
                    needlePos += move;
                    if (needlePos >= 1.0f) { needlePos = 1.0f; needleMovingRight = false; }
                } else {
                    needlePos -= move;
                    if (needlePos <= 0f) { needlePos = 0f; needleMovingRight = true; }
                }
                if (Gdx.input.isKeyJustPressed(Input.Keys.SPACE)) stopNeedle();
            }
        } else {
            glowTimer -= delta;
            if (glowTimer <= 0) {
                minigameActive = false;
                activeAnims.clear();
            }
        }
    }

    private void stopNeedle() {
        needleStopped = true;
        glowTimer = 1.0f;
        lastResult = new MinigameResult();

        float greenHalfW = (0.25f * barWidthMult) / 2f;
        float amberHalfW = greenHalfW * 0.6f;
        float goldHalfW = greenHalfW * 0.15f;
        float purpleHalfW = goldHalfW * 0.20f;

        if (Math.abs(needlePos - purpleCenter) <= purpleHalfW) {
            glowColor = Color.PURPLE.cpy();
            currentModText = "SUPER!";
            lastResult.powerMod = 1.50f;
            lastResult.rating = "SUPER";
        } else if (Math.abs(needlePos - goldCenter) <= goldHalfW) {
            glowColor = Color.GOLD.cpy();
            currentModText = "PERFECT!";
            lastResult.powerMod = 1.30f;
            lastResult.rating = "GOLD";
        } else if (Math.abs(needlePos - amberCenter) <= amberHalfW) {
            glowColor = Color.ORANGE.cpy();
            currentModText = "GREAT";
            lastResult.powerMod = 1.10f;
            lastResult.rating = "AMBER";
        } else if (Math.abs(needlePos - greenCenter) <= greenHalfW) {
            glowColor = Color.GREEN.cpy();
            currentModText = "GOOD";
            lastResult.powerMod = 1.00f;
            lastResult.rating = "GREEN";
        } else {
            glowColor = Color.RED.cpy();
            currentModText = "MISS!";
            lastResult.rating = "MISS";
            float side = Math.signum(needlePos - greenCenter);
            float missDist = (Math.abs(needlePos - greenCenter) - greenHalfW) / (1.0f - greenHalfW);
            lastResult.accuracy = side * missDist;
            lastResult.powerMod = MathUtils.lerp(0.90f, 0.35f, missDist);
        }

        if (lastResult.rating != "MISS") lastResult.accuracy = 0f;
        shotFeedbackTimer = 1.5f;
        shotFeedbackColor = glowColor.cpy();
        shotFeedbackText = lastResult.rating + " (" + (int)(lastResult.powerMod * 100) + "%)";
        minigameStep++;
    }

    private void drawAccuracyBar() {
        float width = viewport.getWorldWidth() * 0.6f;
        float baseHeight = 30f;
        float swellProgress = 1.0f - (barSwellTimer / SWELL_DURATION);
        float swellMag = (barSwellTimer > 0) ? MathUtils.sin(swellProgress * MathUtils.PI) : 0;
        float height = baseHeight + (swellMag * 15f);
        float x = (viewport.getWorldWidth() - width) / 2f;
        float y = (viewport.getWorldHeight() - 150f) - (height - baseHeight) / 2f;

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.gl.glBlendFunc(GL20.GL_SRC_ALPHA, GL20.GL_ONE_MINUS_SRC_ALPHA);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);

        shapeRenderer.setColor(0, 0, 0, 0.6f);
        shapeRenderer.rect(x - 4, y - 4, width + 8, height + 8);
        shapeRenderer.setColor(0.2f, 0.2f, 0.2f, 1f);
        shapeRenderer.rect(x, y, width, height);

        float greenHalfW = (0.25f * barWidthMult) / 2f;
        float amberHalfW = greenHalfW * 0.6f;
        float goldHalfW = greenHalfW * 0.15f;
        float purpleHalfW = goldHalfW * 0.20f;

        // Draw layers using their specific centers
        shapeRenderer.setColor(0.1f, 0.8f, 0.1f, 0.7f); // GREEN
        shapeRenderer.rect(x + (greenCenter * width) - (greenHalfW * width), y, greenHalfW * 2 * width, height);

        shapeRenderer.setColor(1f, 0.6f, 0f, 0.8f); // AMBER
        shapeRenderer.rect(x + (amberCenter * width) - (amberHalfW * width), y, amberHalfW * 2 * width, height);

        shapeRenderer.setColor(1f, 0.9f, 0f, 1f); // GOLD
        shapeRenderer.rect(x + (goldCenter * width) - (goldHalfW * width), y, goldHalfW * 2 * width, height);

        shapeRenderer.setColor(0.6f, 0.1f, 0.9f, 1f); // PURPLE
        shapeRenderer.rect(x + (purpleCenter * width) - (purpleHalfW * width), y, purpleHalfW * 2 * width, height);

        if (needleStopped && glowTimer > 0) {
            shapeRenderer.setColor(glowColor.r, glowColor.g, glowColor.b, glowTimer * 0.8f);
            shapeRenderer.rect(x - 10, y - 10, width + 20, height + 20);
        }

        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.rect(x + (width * needlePos) - 3, y - 15, 6, height + 30);

        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        batch.begin();
        for (ModAnimation anim : activeAnims) {
            font.getData().setScale(1.5f * anim.scale);
            font.setColor(anim.color.r, anim.color.g, anim.color.b, anim.alpha);
            font.draw(batch, anim.text, x + (greenCenter * width) - 80, (viewport.getWorldHeight() - 150f) + anim.yOffset);
        }
        batch.end();
    }

    private void renderShotDebug(Vector3 ballPos, Vector3 aimDir, Terrain terrain) {
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Terrain.TerrainType type = terrain.getTerrainTypeAt(ballPos.x, ballPos.z);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
        Vector3 rightOfAim = new Vector3(aimDir).crs(Vector3.Y).nor();
        float sidePush = normal.dot(rightOfAim);

        font.getData().setScale(1f);
        font.setColor(Color.YELLOW);
        float debugY = 250;
        font.draw(batch, "--- PRE-SHOT DEBUG ---", 40, debugY);
        font.draw(batch, "Terrain: " + type.name() + " (x" + String.format("%.2f", diff.terrainDifficulty) + ")", 40, debugY - 20);
        font.draw(batch, String.format("Slope Multiplier: %.2fx", diff.slopeDifficulty), 40, debugY - 40);

        if (Math.abs(sidePush) > 0.05f) {
            font.setColor(sidePush > 0 ? Color.RED : Color.CYAN);
            font.draw(batch, String.format("Terrain Kick: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), 40, debugY - 60);
        }
    }

    public void logShotInitiated(Vector3 ballPos, Club club, Terrain terrain, ShotDifficulty diff, float powerMod) {
        this.activeDiff = diff;
        this.activePowerMod = powerMod;
        this.activeBallPos.set(ballPos);
        this.minigameActive = true;
        this.needleStopped = false;
        this.minigameCanceled = false;
        this.lastResult = null;
        this.minigameStep = 0;
        this.barWidthMult = 1.0f;
        this.targetBarWidthMult = 1.0f;

        this.greenCenter = 0.5f;
        this.targetGreenCenter = 0.5f;
        this.amberCenter = 0.5f;
        this.goldCenter = 0.5f;
        this.purpleCenter = 0.5f;

        this.shotRandomness = MathUtils.random(-0.1f, 0.1f);
        this.needlePos = 0f;
        this.needleMovingRight = true;
        this.currentModText = "READY";
        this.barSwellTimer = 0f;
        activeAnims.clear();

        if (animSetting == AnimSpeed.NONE) {
            targetBarWidthMult = (1.0f / activeDiff.terrainDifficulty) * (1.0f / activeDiff.clubDifficulty) * (1.0f / activeDiff.swingDifficulty);
            barWidthMult = targetBarWidthMult;
            minigameStep = 4;
            needleSpeed = activePowerMod * 1.5f * currentDifficulty.speedMult;
        } else {
            this.minigameTimer = (0.7f / animSetting.mult);
        }
    }

    public boolean isMinigameComplete() {
        return !minigameActive && needleStopped && glowTimer <= 0 && lastResult != null;
    }

    public boolean wasMinigameCanceled() {
        if (minigameCanceled) {
            minigameCanceled = false;
            return true;
        }
        return false;
    }

    public MinigameResult getMinigameResult() { return lastResult; }
    public float getAccuracyResult() { return needlePos - greenCenter; }

    private void renderWindIndicator(Vector3 worldWind, Camera camera) {
        float uiX = viewport.getWorldWidth() - 80;
        float uiY = viewport.getWorldHeight() - 80;
        float radius = 40f;
        float windSpeed = worldWind.len();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(uiX, uiY, radius);
        shapeRenderer.end();

        if (windSpeed > 0.1f) {
            camForward.set(camera.direction.x, 0, camera.direction.z).nor();
            camRight.set(camera.direction).crs(camera.up).nor();
            camRight.y = 0;
            camRight.nor();
            float screenX = worldWind.dot(camRight);
            float screenY = worldWind.dot(camForward);
            float angle = MathUtils.atan2(screenY, screenX);
            float headAngle = 0.5f;
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rectLine(uiX - MathUtils.cos(angle) * (radius - 10), uiY - MathUtils.sin(angle) * (radius - 10), uiX + MathUtils.cos(angle) * (radius - 10), uiY + MathUtils.sin(angle) * (radius - 10), 3f);
            shapeRenderer.triangle(uiX + MathUtils.cos(angle) * radius, uiY + MathUtils.sin(angle) * radius, uiX + MathUtils.cos(angle + MathUtils.PI - headAngle) * 15f, uiY + MathUtils.sin(angle + MathUtils.PI - headAngle) * 15f, uiX + MathUtils.cos(angle + MathUtils.PI + headAngle) * 15f, uiY + MathUtils.sin(angle + MathUtils.PI + headAngle) * 15f);
            shapeRenderer.end();
        }

        batch.begin();
        font.getData().setScale(1.2f);
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("%.0f MPH", windSpeed), uiX - 30, uiY - radius - 10);
        batch.end();
    }

    public void renderVictory(int finalShots) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        font.getData().setScale(4f);
        font.setColor(Color.GOLD);
        font.draw(batch, "HOLE IN ONE!", viewport.getWorldWidth() / 2f - 200, viewport.getWorldHeight() / 2f + 100);
        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        font.draw(batch, "Total Strokes: " + finalShots, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() / 2f);
        font.getData().setScale(1.2f);
        font.draw(batch, "Press [N] for a New Level", viewport.getWorldWidth() / 2f - 110, viewport.getWorldHeight() / 2f - 100);
        batch.end();
    }

    public void updateSpinInput(float delta) {
        float speed = 2f;
        if (Gdx.input.isKeyPressed(Input.Keys.W)) spinDot.y = MathUtils.clamp(spinDot.y + delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) spinDot.y = MathUtils.clamp(spinDot.y - delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) spinDot.x = MathUtils.clamp(spinDot.x - delta * speed, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) spinDot.x = MathUtils.clamp(spinDot.x + delta * speed, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public Vector2 getSpinOffset() { return spinDot; }
    public void dispose() { batch.dispose(); shapeRenderer.dispose(); font.dispose(); }
}