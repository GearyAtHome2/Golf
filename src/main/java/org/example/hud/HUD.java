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
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.ScoreShout;
import org.example.terrain.level.LevelData;
import org.example.ball.Ball;
import org.example.ball.MinigameResult;
import org.example.ball.ShotDifficulty;
import org.example.terrain.Terrain;

public class HUD {
    public enum GameDifficulty {
        EASY(0.3f), MEDIUM(0.75f), HARD(1.0f);
        public final float speedMult;

        GameDifficulty(float speed) {
            this.speedMult = speed;
        }
    }

    public enum AnimSpeed {
        NONE(0f), SLOW(0.6f), FAST(1.4f);
        public final float mult;

        AnimSpeed(float mult) {
            this.mult = mult;
        }
    }

    public static class ModAnimation {
        String text;
        float yOffset, alpha, scale, life;
        Color color;
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

    private final MinigameEngine engine = new MinigameEngine();
    private final MinigameUI minigameUI = new MinigameUI();

    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;
    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;

    private float seedFeedbackTimer = 0, shotFeedbackTimer = 0, barSwellTimer = 0, glowTimer = 0, minigameTimer = 0;
    private String shotFeedbackText = "";
    private Color shotFeedbackColor = Color.WHITE, glowColor = new Color(0, 0, 0, 0);

    private final Vector3 camForward = new Vector3(), camRight = new Vector3(), activeBallPos = new Vector3();
    private final float SWELL_DURATION = 0.3f;

    public AnimSpeed animSetting = AnimSpeed.NONE;
    public GameDifficulty currentDifficulty = GameDifficulty.EASY;

    private boolean mainMenuRequested = false, minigameActive = false, needleStopped = false, minigameCanceled = false;
    private int minigameStep = 0;
    private float needleSpeed = 0, shotRandomness = 0, activePowerMod;
    private ShotDifficulty activeDiff;
    private MinigameResult lastResult = null;
    private final Array<ModAnimation> activeAnims = new Array<>();

    public HUD() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    public void incrementShots() {
        shotCount++;
    }

    public void resetShots() {
        shotCount = 0;
    }

    public int getShotCount() {
        return shotCount;
    }

    public void resize(int width, int height) {
        viewport.update(width, height, true);
    }

    public void renderStartMenu(int selection) {
        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();
        float centerX = viewport.getWorldWidth() / 2f;
        float centerY = viewport.getWorldHeight() / 2f;

        String clipboardContent = Gdx.app.getClipboard().getContents();
        boolean hasValidSeed = false;
        if (clipboardContent != null && !clipboardContent.isEmpty()) {
            try {
                Long.parseLong(clipboardContent.trim());
                hasValidSeed = true;
            } catch (NumberFormatException ignored) {
            }
        }

        font.getData().setScale(3f);
        font.draw(batch, "GEARY GOLF", centerX - 180, centerY + 150);

        font.getData().setScale(1.5f);
        drawOption(selection == 0, true, "PLAY GAME", centerX - 80, centerY);
        drawOption(selection == 1, true, "PRACTICE RANGE", centerX - 80, centerY - 60);

        String seedText = hasValidSeed ? "PLAY SEED [" + clipboardContent.trim() + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(selection == 2, hasValidSeed, seedText, centerX - 80, centerY - 120);

        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 210);
        batch.end();
    }

    private void drawOption(boolean selected, boolean enabled, String text, float x, float y) {
        if (!enabled) {
            font.setColor(Color.DARK_GRAY);
        } else {
            font.setColor(selected ? Color.YELLOW : Color.WHITE);
        }
        font.draw(batch, (selected && enabled ? "> " : "  ") + text, x, y);
    }

    public void renderPauseMenu(boolean isPractice, LevelData levelData) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) {
            animSetting = AnimSpeed.values()[(animSetting.ordinal() + 1) % AnimSpeed.values().length];
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) {
            currentDifficulty = GameDifficulty.values()[(currentDifficulty.ordinal() + 1) % GameDifficulty.values().length];
        }
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) {
            mainMenuRequested = true;
        }
        if (!isPractice && Gdx.input.isKeyJustPressed(Input.Keys.C) && levelData != null) {
            String seedString = String.valueOf(levelData.getSeed());
            Gdx.app.getClipboard().setContents(seedString);
            seedFeedbackTimer = 1.0f;
            Gdx.app.log("HUD", "Seed copied to clipboard: " + seedString);
        }

        batch.setProjectionMatrix(viewport.getCamera().combined);
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
        font.draw(batch, "[R] RESET BALL  |  [N] NEW LEVEL  |  [M] MAIN MENU  |  [ESC] RESUME" + (isPractice ? "" : "  |  [C] COPY SEED"), 50, 100);
        if (seedFeedbackTimer > 0) {
            seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
            font.setColor(Color.GREEN);
            font.draw(batch, "SEED COPIED", 50, 150);
        }
        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain) {
        if (!minigameActive) {
            updateSpinInput(Gdx.graphics.getDeltaTime());
        }

        float delta = Gdx.graphics.getDeltaTime();
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        drawSpinUI();
        if (levelData != null) renderWindIndicator(levelData.getWind(), gameCamera);

        batch.begin();
        renderFeedbackMessages(delta);
        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, gameSpeed);
        if (ball.getState() == Ball.State.STATIONARY && !minigameActive)
            renderShotDebug(ball.getPosition(), gameCamera.direction, terrain);
        batch.end();

        if (minigameActive) {
            updateMinigame(delta, gameCamera, terrain);
            minigameUI.draw(shapeRenderer, batch, font, viewport.getWorldWidth(), viewport.getWorldHeight(), engine, barSwellTimer, glowTimer, glowColor, activeAnims);
        }
    }

    private void drawSpinUI() {
        float spinX = 80, spinY = 80;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS);
        shapeRenderer.setColor(0.1f, 0.1f, 0.1f, 1f);
        shapeRenderer.circle(spinX, spinY, SPIN_UI_RADIUS - 3);
        shapeRenderer.setColor(Color.RED);
        shapeRenderer.circle(spinX + (spinDot.x * (SPIN_UI_RADIUS - 10)), spinY + (spinDot.y * (SPIN_UI_RADIUS - 10)), 5);
        shapeRenderer.end();
    }

    private void renderFeedbackMessages(float delta) {
        if (shotFeedbackTimer > 0) {
            shotFeedbackTimer -= delta;
            font.getData().setScale(2.5f * (shotFeedbackTimer / 1.5f + 0.5f));
            font.setColor(shotFeedbackColor.r, shotFeedbackColor.g, shotFeedbackColor.b, Math.min(1, shotFeedbackTimer * 2));
            font.draw(batch, shotFeedbackText, viewport.getWorldWidth() / 2f - 150, viewport.getWorldHeight() / 2f + 150);
        }
    }

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, float speed) {
        font.getData().setScale(1f);
        font.setColor(Color.WHITE);
        font.draw(batch, "CONTACT POINT", 35, 20);

        font.getData().setScale(1.5f);
        if (isPractice) {
            font.setColor(Color.CYAN);
            font.draw(batch, "PRACTICE RANGE", 40, viewport.getWorldHeight() - 40);
        } else if (levelData != null) {
            font.setColor(Color.GOLD);
            font.draw(batch, levelData.getArchetype().name().replace("_", " "), 40, viewport.getWorldHeight() - 40);

            font.setColor(Color.WHITE);
            // Display Par next to the Archetype
            font.draw(batch, "Par " + levelData.getPar(), 40, viewport.getWorldHeight() - 80);
        }

        // Color coding shots based on par performance
        if (!isPractice && levelData != null) {
            if (shotCount > levelData.getPar()) font.setColor(Color.RED);
            else if (shotCount < levelData.getPar() && shotCount > 0) font.setColor(Color.GREEN);
            else font.setColor(Color.WHITE);
        } else {
            font.setColor(Color.WHITE);
        }

        float shotY = isPractice ? viewport.getWorldHeight() - 80 : viewport.getWorldHeight() - 120;
        font.draw(batch, "Shots: " + shotCount, 40, shotY);

        font.setColor(Color.WHITE);
        float rightX = viewport.getWorldWidth() - 250;
        font.draw(batch, "Club: " + club.name(), rightX, 140);
        float currentSpinMag = ball.getSpin().len();
        font.setColor(currentSpinMag > 0.1f ? (currentSpinMag > lastDisplayedSpin ? Color.GREEN : Color.RED) : Color.GRAY);
        font.draw(batch, String.format("Spin: %.1fk RPM", (currentSpinMag * 100f) / 1000f), rightX, 100);
        lastDisplayedSpin = currentSpinMag;
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Speed: %.1fx", speed), rightX, 60);
    }

    private void updateMinigame(float delta, Camera camera, Terrain terrain) {
        if (!needleStopped && Gdx.input.isButtonJustPressed(Input.Buttons.LEFT)) {
            cancelMinigame();
            return;
        }

        updateAnimations(delta);

        if (!needleStopped) {
            minigameTimer += delta;
            float stepDuration = (animSetting == AnimSpeed.NONE) ? 0.01f : (0.7f / animSetting.mult);

            Vector3 normal = terrain.getNormalAt(activeBallPos.x, activeBallPos.z);
            Vector3 rightOfAim = new Vector3(camera.direction).crs(Vector3.Y).nor();
            engine.updateZones(delta, normal.dot(rightOfAim), spinDot.x, shotRandomness);

            if (minigameStep < 4 && minigameTimer >= stepDuration) advanceMinigameStep();

            if (minigameStep == 4) {
                float move = delta * needleSpeed;
                engine.needlePos += (engine.needleMovingRight ? move : -move);
                if (engine.needlePos >= 1.0f) {
                    engine.needlePos = 1.0f;
                    engine.needleMovingRight = false;
                } else if (engine.needlePos <= 0f) {
                    engine.needlePos = 0f;
                    engine.needleMovingRight = true;
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

    private void advanceMinigameStep() {
        minigameStep++;
        minigameTimer = 0;
        String text = "";
        float mult = 1.0f;
        switch (minigameStep) {
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
                needleSpeed = activePowerMod * 1.5f * currentDifficulty.speedMult;
            }
        }
        if (animSetting != AnimSpeed.NONE) {
            Color c = (mult <= 1.05f) ? Color.GREEN : Color.RED;
            if (minigameStep == 4) c = Color.GOLD;
            activeAnims.add(new ModAnimation(text, c));
        }
    }

    private void stopNeedle() {
        needleStopped = true;
        glowTimer = 1.0f;
        lastResult = engine.calculateResult(engine.needlePos);
        glowColor = lastResult.rating.color;
        shotFeedbackTimer = 1.5f;
        shotFeedbackColor = glowColor.cpy();
        shotFeedbackText = lastResult.rating.getRandomPhrase() + " (" + (int) (lastResult.powerMod * 100) + "%)";
        minigameStep++;
    }

    private void cancelMinigame() {
        minigameActive = false;
        needleStopped = false;
        minigameCanceled = true;
        lastResult = null;
        activeAnims.clear();
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
                barSwellTimer = SWELL_DURATION;
            }
            if (anim.life <= 0 || anim.hitBar) activeAnims.removeIndex(i);
        }
        if (barSwellTimer > 0) barSwellTimer = Math.max(0, barSwellTimer - delta);
    }

    private void renderShotDebug(Vector3 ballPos, Vector3 aimDir, Terrain terrain) {
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
        float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());
        font.getData().setScale(1f);
        font.setColor(Color.YELLOW);
        float dy = 250;
        font.draw(batch, "--- PRE-SHOT DEBUG ---", 40, dy);
        font.draw(batch, "Terrain: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z) + " (x" + String.format("%.2f", diff.terrainDifficulty) + ")", 40, dy - 20);
        font.draw(batch, String.format("Slope Multiplier: %.2fx", diff.slopeDifficulty), 40, dy - 40);
        if (Math.abs(sidePush) > 0.05f) {
            font.setColor(sidePush > 0 ? Color.RED : Color.CYAN);
            font.draw(batch, String.format("Terrain Kick: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), 40, dy - 60);
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
        this.shotRandomness = MathUtils.random(-0.1f, 0.1f);
        engine.reset(club.baseDifficulty);
        activeAnims.clear();
        if (animSetting == AnimSpeed.NONE) {
            engine.barWidthMult = (1.0f / activeDiff.terrainDifficulty) * (1.0f / activeDiff.clubDifficulty) * (1.0f / activeDiff.swingDifficulty);
            minigameStep = 4;
            needleSpeed = activePowerMod * 1.5f * currentDifficulty.speedMult;
        } else minigameTimer = (0.7f / animSetting.mult);
    }

    private void renderWindIndicator(Vector3 worldWind, Camera camera) {
        float uiX = viewport.getWorldWidth() - 80, uiY = viewport.getWorldHeight() - 80, radius = 40f;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.WHITE);
        shapeRenderer.circle(uiX, uiY, radius);
        shapeRenderer.end();
        if (worldWind.len() > 0.1f) {
            camForward.set(camera.direction.x, 0, camera.direction.z).nor();
            camRight.set(camera.direction).crs(camera.up).nor();
            camRight.y = 0;
            camRight.nor();
            float angle = MathUtils.atan2(worldWind.dot(camForward), worldWind.dot(camRight));
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
            shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rectLine(uiX - MathUtils.cos(angle) * 30, uiY - MathUtils.sin(angle) * 30, uiX + MathUtils.cos(angle) * 30, uiY + MathUtils.sin(angle) * 30, 3f);
            shapeRenderer.triangle(uiX + MathUtils.cos(angle) * radius, uiY + MathUtils.sin(angle) * radius, uiX + MathUtils.cos(angle + 2.6f) * 15, uiY + MathUtils.sin(angle + 2.6f) * 15, uiX + MathUtils.cos(angle - 2.6f) * 15, uiY + MathUtils.sin(angle - 2.6f) * 15);
            shapeRenderer.end();
        }
        batch.begin();
        font.getData().setScale(1.2f);
        font.draw(batch, String.format("%.0f MPH", worldWind.len()), uiX - 30, uiY - radius - 10);
        batch.end();
    }

    public void renderVictory(int shots, LevelData levelData) {
        batch.begin();

        int par = (levelData != null) ? levelData.getPar() : 3;
        String shout = org.example.ScoreShout.getShout(shots, par);
        if (shots == 1) shout = ScoreShout.HIO.shout;

        int diff = shots - par;

        Color shoutColor;
        if (shots == 1 || diff <= -2) shoutColor = Color.GOLD;
        else if (diff == -1) shoutColor = Color.CYAN;
        else if (diff == 0) shoutColor = Color.WHITE;
        else if (diff == 1) shoutColor = Color.ORANGE;
        else shoutColor = Color.RED;

        font.getData().setScale(4f);
        font.setColor(shoutColor);
        float centerX = viewport.getWorldWidth() / 2f;
        font.draw(batch, shout, centerX - 200, viewport.getWorldHeight() / 2f + 100);

        font.getData().setScale(2f);
        font.setColor(Color.WHITE);
        String scoreType = (diff == 0) ? "Even Par" : (diff > 0 ? "+" + diff : "" + diff);
        font.draw(batch, "Total Strokes: " + shots + " (" + scoreType + ")", centerX - 180, viewport.getWorldHeight() / 2f);

        batch.end();
    }

    public void updateSpinInput(float d) {
        if (Gdx.input.isKeyPressed(Input.Keys.W)) spinDot.y = MathUtils.clamp(spinDot.y + d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) spinDot.y = MathUtils.clamp(spinDot.y - d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) spinDot.x = MathUtils.clamp(spinDot.x - d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) spinDot.x = MathUtils.clamp(spinDot.x + d * 2, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public boolean isMinigameComplete() {
        return !minigameActive && needleStopped && glowTimer <= 0 && lastResult != null;
    }

    public boolean wasMinigameCanceled() {
        boolean c = minigameCanceled;
        minigameCanceled = false;
        return c;
    }

    public boolean wasMainMenuRequested() {
        boolean m = mainMenuRequested;
        mainMenuRequested = false;
        return m;
    }

    public MinigameResult getMinigameResult() {
        return lastResult;
    }

    public Vector2 getSpinOffset() {
        return spinDot;
    }

    public void dispose() {
        batch.dispose();
        shapeRenderer.dispose();
        font.dispose();
    }
}