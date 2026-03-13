package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Camera;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.*;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.ball.Ball;
import org.example.ball.CompetitiveScore;
import org.example.ball.MinigameResult;
import org.example.ball.ShotDifficulty;
import org.example.terrain.Terrain;
import org.example.terrain.level.LevelData;

public class HUD {
    private float hazardTimer = 0;
    private String hazardText = "";
    private Color hazardColor = Color.WHITE;
    private boolean instructionsRequested = false;

    private float persistentShotDistance = 0f;
    private float maxDistanceSeen = 0f;
    private float shotDistanceDisplayTimer = 0f;
    private boolean ballWasActive = false;

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

    public static class ModAnimation {
        public String text;
        public float yOffset, alpha, scale, life;
        public Color color;
        public boolean hitBar = false;

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

    private final MinigameController minigameController = new MinigameController();
    private final InstructionRenderer instructionRenderer = new InstructionRenderer();
    private final VictoryRenderer victoryRenderer = new VictoryRenderer();
    private final ClubInfoRenderer clubInfoRenderer = new ClubInfoRenderer();

    private int shotCount = 0;
    private float lastDisplayedSpin = 0f;
    private final Vector2 spinDot = new Vector2(0, 0);
    private final float SPIN_UI_RADIUS = 50f;

    private float distanceDisplayTimer = 0;
    private String distanceText = "";

    private float seedFeedbackTimer = 0, shotFeedbackTimer = 0;
    private String shotFeedbackText = "";
    private Color shotFeedbackColor = Color.WHITE;

    public AnimSpeed animSetting = AnimSpeed.NONE;
    public GameDifficulty currentDifficulty = GameDifficulty.EASY;
    private boolean mainMenuRequested = false;

    public HUD() {
        batch = new SpriteBatch();
        shapeRenderer = new ShapeRenderer();
        font = new BitmapFont();
        viewport = new ScreenViewport();
    }

    public void incrementShots() { shotCount++; }
    public void resetShots() { shotCount = 0; }
    public int getShotCount() { return shotCount; }
    public void resize(int width, int height) { viewport.update(width, height, true); }

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
            } catch (NumberFormatException ignored) {}
        }

        font.getData().setScale(3f);
        font.draw(batch, "GEARY GOLF", centerX - 180, centerY + 180);

        font.getData().setScale(1.5f);
        drawOption(selection == 0, true, "PLAY GAME", centerX - 80, centerY + 80);
        drawOption(selection == 1, true, "18 HOLES (COMPETITIVE)", centerX - 80, centerY + 40);
        drawOption(selection == 2, true, "INSTRUCTIONS", centerX - 80, centerY);
        drawOption(selection == 3, true, "PRACTICE RANGE", centerX - 80, centerY - 40);
        drawOption(selection == 4, true, "PUTTING GREEN", centerX - 80, centerY - 80);
        String seedText = hasValidSeed ? "PLAY SEED [" + clipboardContent.trim() + "]" : "PLAY SEED (CLIPBOARD EMPTY)";
        drawOption(selection == 5, hasValidSeed, seedText, centerX - 80, centerY - 120);

        font.getData().setScale(1f);
        font.setColor(Color.GRAY);
        font.draw(batch, "Use UP/DOWN to select, ENTER to start", centerX - 130, centerY - 240);
        batch.end();
    }

    private void drawOption(boolean selected, boolean enabled, String text, float x, float y) {
        if (!enabled) font.setColor(Color.DARK_GRAY);
        else font.setColor(selected ? Color.YELLOW : Color.WHITE);
        font.draw(batch, (selected && enabled ? "> " : "  ") + text, x, y);
    }

    public void renderPauseMenu(boolean isPractice, LevelData levelData) {
        if (Gdx.input.isKeyJustPressed(Input.Keys.A)) animSetting = AnimSpeed.values()[(animSetting.ordinal() + 1) % AnimSpeed.values().length];
        if (Gdx.input.isKeyJustPressed(Input.Keys.D)) currentDifficulty = GameDifficulty.values()[(currentDifficulty.ordinal() + 1) % GameDifficulty.values().length];
        if (Gdx.input.isKeyJustPressed(Input.Keys.M)) mainMenuRequested = true;
        if (Gdx.input.isKeyJustPressed(Input.Keys.I)) instructionsRequested = true;

        if (!isPractice && Gdx.input.isKeyJustPressed(Input.Keys.C) && levelData != null) {
            Gdx.app.getClipboard().setContents(String.valueOf(levelData.getSeed()));
            seedFeedbackTimer = 1.0f;
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
        font.draw(batch, "[R] RESET BALL  |  [N] NEW LEVEL  |  [M] MAIN MENU  |  [I] HELP", 50, 100);
        if (seedFeedbackTimer > 0) {
            seedFeedbackTimer -= Gdx.graphics.getDeltaTime();
            font.setColor(Color.GREEN);
            font.draw(batch, "SEED COPIED", 50, 150);
        }
        batch.end();
    }

    public void renderPlayingHUD(float gameSpeed, Club currentClub, Ball ball, boolean isPractice, LevelData levelData, Camera gameCamera, Terrain terrain, CompetitiveScore compScore) {
        float delta = Gdx.graphics.getDeltaTime();

        if (!minigameController.isActive()) {
            updateSpinInput(delta);
            if (Gdx.input.isKeyJustPressed(Input.Keys.F)) {
                distanceText = String.format("RANGE: %.1f yds", ball.getFlatDistanceToHole(terrain));
                distanceDisplayTimer = 3.0f;
            }
        }

        if (isPractice) {
            Ball.State s = ball.getState();
            boolean isMoving = (s == Ball.State.AIR || s == Ball.State.ROLLING || s == Ball.State.CONTACT);
            if (isMoving) {
                ballWasActive = true;
                float currentShotDist = ball.getShotDistance();
                if (currentShotDist > maxDistanceSeen) maxDistanceSeen = currentShotDist;
                shotDistanceDisplayTimer = 0f;
            } else if (s == Ball.State.STATIONARY && ballWasActive) {
                persistentShotDistance = maxDistanceSeen;
                shotDistanceDisplayTimer = 5.0f;
                ballWasActive = false;
                maxDistanceSeen = 0f;
            }
        }

        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        drawSpinUI();
        if (levelData != null) renderWindIndicator(levelData.getWind(), gameCamera);

        batch.begin();
        renderFeedbackMessages(delta);
        renderDistanceDisplay(delta);
        renderHazardPopUp(delta);
        if (isPractice) renderShotDistance(ball, delta);
        renderClubAndBallInfo(isPractice, levelData, currentClub, ball, gameSpeed, compScore);
        if (ball.getState() == Ball.State.STATIONARY && !minigameController.isActive())
            renderShotDebug(ball.getPosition(), gameCamera.direction, terrain);
        batch.end();

        if (minigameController.isActive()) {
            minigameController.updateAndDraw(delta, gameCamera, terrain, spinDot, animSetting, currentDifficulty, shapeRenderer, batch, font, viewport);
            if (minigameController.isNeedleStopped() && minigameController.getGlowTimer() >= 0.98f) {
                MinigameResult res = minigameController.getResult();
                shotFeedbackTimer = 1.5f;
                shotFeedbackColor = res.rating.color.cpy();
                shotFeedbackText = res.rating.getRandomPhrase() + " (" + (int) (res.powerMod * 100) + "%)";
            }
        }
    }

    /**
     * Replaces the previous placeholder or broken implementation.
     * Uses ClubInfoManager to fetch data and ensures the SpriteBatch is closed.
     */
    public void renderClubInfo(Club club) {
        // Ensure 2D projection is set before drawing
        batch.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);

        clubInfoRenderer.render(
                batch,
                shapeRenderer,
                font,
                viewport,
                club.name(),
                ClubInfoManager.getClubDescription(club),
                ClubInfoManager.getCarryDistanceInfo(club),
                ClubInfoManager.getPowerInfo(club),
                ClubInfoManager.getLoftInfo(club)
        );

        // ClubInfoRenderer.render() leaves the batch open; we must close it here.
        if (batch.isDrawing()) {
            batch.end();
        }
    }

    private void renderShotDistance(Ball ball, float delta) {
        float toRender = 0;
        Ball.State state = ball.getState();
        boolean isMoving = (state == Ball.State.AIR || state == Ball.State.ROLLING || state == Ball.State.CONTACT);
        if (isMoving) { toRender = ball.getShotDistance(); font.setColor(Color.WHITE); }
        else if (shotDistanceDisplayTimer > 0) {
            toRender = persistentShotDistance;
            shotDistanceDisplayTimer -= delta;
            font.setColor(1f, 0.85f, 0f, MathUtils.clamp(shotDistanceDisplayTimer, 0, 1));
        }
        if (toRender > 0.1f) {
            font.getData().setScale(1.4f);
            font.draw(batch, String.format("SHOT DISTANCE: %.1f yds", toRender), viewport.getWorldWidth() - 300, 200);
        }
    }

    private void renderDistanceDisplay(float delta) {
        if (distanceDisplayTimer > 0) {
            distanceDisplayTimer -= delta;
            font.getData().setScale(1.8f);
            font.setColor(1, 1, 0, Math.min(1, distanceDisplayTimer));
            font.draw(batch, distanceText, viewport.getWorldWidth() / 2f - 100, viewport.getWorldHeight() - 50);
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

    private void renderClubAndBallInfo(boolean isPractice, LevelData levelData, Club club, Ball ball, float speed, CompetitiveScore compScore) {
        float topY = viewport.getWorldHeight() - 40;
        font.getData().setScale(1.5f);
        if (isPractice) {
            font.setColor(Color.CYAN);
            String practiceLabel = "PRACTICE";
            font.draw(batch, practiceLabel, 40, topY);
        }
        else if (levelData != null) {
            font.setColor(Color.GOLD);
            String header = levelData.getArchetype().name().replace("_", " ") + (compScore != null ? " - HOLE " + compScore.getCurrentHoleNumber() : "");
            font.draw(batch, header, 40, topY);
            font.setColor(Color.WHITE);
            font.draw(batch, "Par " + levelData.getPar(), 40, topY - 40);
            if (compScore != null) { font.setColor(Color.YELLOW); font.draw(batch, "TO PAR: " + compScore.getToParString(), 40, topY - 80); }
        }
        font.setColor(Color.WHITE);
        float shotsY = (!isPractice && levelData != null) ? (compScore != null ? topY - 120 : topY - 80) : topY - 40;
        font.draw(batch, "Shots: " + shotCount, 40, shotsY);
        float rightX = viewport.getWorldWidth() - 250;
        font.draw(batch, "Club: " + club.name(), rightX, 140);
        float currentSpinMag = ball.getSpin().len();
        font.setColor(currentSpinMag > 0.1f ? (currentSpinMag > lastDisplayedSpin ? Color.GREEN : Color.RED) : Color.GRAY);
        font.draw(batch, String.format("Spin: %.1fk RPM", (currentSpinMag * 100f) / 1000f), rightX, 100);
        lastDisplayedSpin = currentSpinMag;
        font.setColor(Color.WHITE);
        font.draw(batch, String.format("Speed: %.1fx", speed), rightX, 60);
    }

    private void renderShotDebug(Vector3 ballPos, Vector3 aimDir, Terrain terrain) {
        ShotDifficulty diff = terrain.getShotDifficulty(ballPos.x, ballPos.z, aimDir);
        Vector3 normal = terrain.getNormalAt(ballPos.x, ballPos.z);
        float sidePush = normal.dot(new Vector3(aimDir).crs(Vector3.Y).nor());
        font.getData().setScale(1f); font.setColor(Color.YELLOW);
        float dy = 250;
        font.draw(batch, "--- PRE-SHOT DEBUG ---", 40, dy);
        font.draw(batch, "Terrain: " + terrain.getTerrainTypeAt(ballPos.x, ballPos.z) + " (x" + String.format("%.2f", diff.terrainDifficulty) + ")", 40, dy - 20);
        font.draw(batch, String.format("Slope Multiplier: %.2fx", diff.slopeDifficulty), 40, dy - 40);
        if (Math.abs(sidePush) > 0.05f) { font.setColor(sidePush > 0 ? Color.RED : Color.CYAN); font.draw(batch, String.format("Terrain Kick: %s (%.2f)", (sidePush > 0 ? "RIGHT" : "LEFT"), sidePush), 40, dy - 60); }
    }

    public void logShotInitiated(Vector3 ballPos, Club club, Terrain terrain, ShotDifficulty diff, float powerMod) {
        minigameController.start(ballPos, club, diff, powerMod, animSetting, currentDifficulty);
        this.maxDistanceSeen = 0f;
    }

    private void renderWindIndicator(Vector3 worldWind, Camera camera) {
        float uiX = viewport.getWorldWidth() - 80, uiY = viewport.getWorldHeight() - 80, radius = 40f;
        shapeRenderer.begin(ShapeRenderer.ShapeType.Line); shapeRenderer.setColor(Color.WHITE); shapeRenderer.circle(uiX, uiY, radius); shapeRenderer.end();
        if (worldWind.len() > 0.1f) {
            Vector3 camForward = new Vector3(camera.direction.x, 0, camera.direction.z).nor();
            Vector3 camRight = new Vector3(camera.direction).crs(camera.up).nor();
            camRight.y = 0; camRight.nor();
            float angle = MathUtils.atan2(worldWind.dot(camForward), worldWind.dot(camRight));
            shapeRenderer.begin(ShapeRenderer.ShapeType.Filled); shapeRenderer.setColor(Color.YELLOW);
            shapeRenderer.rectLine(uiX - MathUtils.cos(angle) * 30, uiY - MathUtils.sin(angle) * 30, uiX + MathUtils.cos(angle) * 30, uiY + MathUtils.sin(angle) * 30, 3f);
            shapeRenderer.triangle(uiX + MathUtils.cos(angle) * radius, uiY + MathUtils.sin(angle) * radius, uiX + MathUtils.cos(angle + 2.6f) * 15, uiY + MathUtils.sin(angle + 2.6f) * 15, uiX + MathUtils.cos(angle - 2.6f) * 15, uiY + MathUtils.sin(angle - 2.6f) * 15);
            shapeRenderer.end();
        }
        batch.begin(); font.getData().setScale(1.2f); font.draw(batch, String.format("%.0f MPH", worldWind.len()), uiX - 30, uiY - radius - 10); batch.end();
    }

    public void showWaterHazard() { this.hazardText = "WATER HAZARD"; this.hazardColor = Color.CYAN; this.hazardTimer = 1.0f; }
    public void showOutOfBounds() { this.hazardText = "OUT OF BOUNDS"; this.hazardColor = Color.RED; this.hazardTimer = 1.1f; }

    private void renderHazardPopUp(float delta) {
        if (hazardTimer > 0) {
            hazardTimer -= delta;
            font.getData().setScale(3.0f * (1.0f + (MathUtils.sin(hazardTimer * 10f) * 0.1f)));
            font.setColor(hazardColor.r, hazardColor.g, hazardColor.b, MathUtils.clamp(hazardTimer * 2f, 0, 1));
            font.draw(batch, hazardText, viewport.getWorldWidth() / 2f - 200, viewport.getWorldHeight() / 2f + 100);
        }
    }

    public void renderVictory(int shots, LevelData levelData, CompetitiveScore compScore) {
        victoryRenderer.render(batch, shapeRenderer, font, viewport, shots, levelData, compScore);
    }

    public void reset() {
        minigameController.reset();
        hazardTimer = 0;
        shotFeedbackTimer = 0;
        distanceDisplayTimer = 0;
        mainMenuRequested = false;
        instructionsRequested = false;
        spinDot.set(0, 0);
    }

    public void updateSpinInput(float d) {
        if (Gdx.input.isKeyPressed(Input.Keys.W)) spinDot.y = MathUtils.clamp(spinDot.y + d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.S)) spinDot.y = MathUtils.clamp(spinDot.y - d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.A)) spinDot.x = MathUtils.clamp(spinDot.x - d * 2, -1f, 1f);
        if (Gdx.input.isKeyPressed(Input.Keys.D)) spinDot.x = MathUtils.clamp(spinDot.x + d * 2, -1f, 1f);
        if (spinDot.len() > 1f) spinDot.nor();
    }

    public void renderInstructions() { instructionRenderer.render(batch, shapeRenderer, font, viewport); }
    public boolean wasInstructionsRequested() { return instructionsRequested; }
    public void clearInstructionsRequest() { instructionsRequested = false; }
    public void resetInstructionScroll() { instructionRenderer.resetScroll(); }
    public boolean isMinigameComplete() { return !minigameController.isActive() && minigameController.isNeedleStopped() && minigameController.getGlowTimer() <= 0 && minigameController.getResult() != null; }
    public boolean wasMinigameCanceled() { return minigameController.wasCanceled(); }
    public boolean wasMainMenuRequested() { boolean m = mainMenuRequested; mainMenuRequested = false; return m; }
    public MinigameResult getMinigameResult() { return minigameController.getResult(); }
    public Vector2 getSpinOffset() { return spinDot; }
    public void dispose() { batch.dispose(); shapeRenderer.dispose(); font.dispose(); }
}