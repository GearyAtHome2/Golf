package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.input.GameInputProcessor;

public class InstructionRenderer {

    private float instructionScrollY = 0;
    private final float MAX_SCROLL = 1400f;
    private Rectangle boxBounds = new Rectangle();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameInputProcessor input) {
        float delta = Gdx.graphics.getDeltaTime();
        float scrollSpeed = 450f * delta;

        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;

        if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP)) {
            instructionScrollY = Math.max(0, instructionScrollY - scrollSpeed);
        }
        if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN)) {
            instructionScrollY = Math.min(MAX_SCROLL, instructionScrollY + scrollSpeed);
        }

        // Touch-drag scrolling for Android
        if (isAndroid && Gdx.input.isTouched()) {
            float dragY = Gdx.input.getDeltaY();
            if (Math.abs(dragY) > 0.1f) {
                // Inverted: Drag finger UP (negative deltaY) should move content UP (increase scroll)
                // User expects content to follow finger. If finger moves UP, content should move UP.
                // instructionScrollY is the offset from top. Increasing it moves text UP.
                instructionScrollY = MathUtils.clamp(instructionScrollY - dragY * 1.5f, 0, MAX_SCROLL);
            }
        }

        float centerX = viewport.getWorldWidth() / 2f;
        float boxW = 800;
        float boxH = viewport.getWorldHeight() - 180;
        float boxX = centerX - boxW / 2f;
        float boxY = 120;
        boxBounds.set(boxX, boxY, boxW, boxH);

        // Draw Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 0.9f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Prepare Scissor for Clipping
        batch.end();
        Rectangle scissor = new Rectangle();
        Rectangle area = new Rectangle(boxX, boxY, boxW, boxH);
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), area, scissor);

        if (ScissorStack.pushScissors(scissor)) {
            batch.begin();
            float currentY = boxY + boxH - 50 + instructionScrollY;

            font.getData().setScale(2.5f);
            font.setColor(Color.GOLD);
            font.draw(batch, "HOW TO PLAY", centerX - 140, currentY);
            currentY -= 80;

            // --- CONTROLS ---
            font.getData().setScale(1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, "CONTROLS", centerX - 350, currentY);
            currentY -= 40;

            font.getData().setScale(1.1f);
            font.setColor(Color.WHITE);
            String[] controls = {
                    "[WASD] - Aim Contact Point (Spin and loft)",
                    "[RCLICK + DRAG] - Aim Direction",
                    "[SCROLL] - Change Clubs",
                    "[SPACE] - Hold for power / Lock in shot minigame",
                    "[LTAB] - Map Oversight View",
                    " * You can use LClick and drag to rotate, RClick to pan",
                    "[F] - Use Rangefinder",
                    "[P] - Toggle shot angle projection (Disabled during competitive play)",
                    "[i] - Show club information",
                    "[R] - Reset Ball to last position",
                    "[N] - Next map (Disabled during competitive play)",
                    "[ESC] - Pause Menu - contains additional keybinds",
                    "[UP/DOWN] - Change game speed"
            };
            for (String line : controls) {
                font.draw(batch, line, centerX - 330, currentY);
                currentY -= 35;
            }

            currentY -= 50;

            // --- CLUBS ---
            font.getData().setScale(1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, "THE BAG (CLUBS)", centerX - 350, currentY);
            currentY -= 40;

            font.getData().setScale(1.1f);
            font.setColor(Color.WHITE);
            String[][] clubInfo = {
                    {"DRIVER", "Maximum power and Tee bonus. High difficulty from the fairway."},
                    {"WOODS (3/5)", "Long range from the fairway. Lower loft for more roll-out."},
                    {"2-IRON", "Long and piercing flight. Very high skill requirement."},
                    {"HYBRID", "Mid-range precision club. Easy to hit with respectable, predictable distance and a high flight trajectory."},
                    {"IRONS (5-9)", "Old faithfuls. Higher numbers mean more loft and control."},
                    {"WEDGES (P/G/S)", "Precision clubs. Very forgiving sweet spots, high loft, very high backspin"},
                    {"LOB WEDGE", "Extreme loft for clearing obstacles. Requires high precision."},
                    {"PUTTER", "Zero loft. Designed for the green, but works for 'Texas Wedges' if you get under it."}
            };

            for (String[] club : clubInfo) {
                font.setColor(Color.GOLD);
                font.draw(batch, club[0] + ": ", centerX - 330, currentY);
                font.setColor(Color.WHITE);
                font.draw(batch, club[1], centerX - 180, currentY);
                currentY -= 35;
            }

            currentY -= 50;

            // --- GAMEPLAY ---
            font.getData().setScale(1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, "GAMEPLAY", centerX - 350, currentY);
            currentY -= 40;

            font.getData().setScale(1.1f);
            font.setColor(Color.WHITE);
            String[] gameplay = {
                    "SPIN PHYSICS: 'Bottom hits' result in high loft/low spin.",
                    "'Middle hits' result in mid loft/mid spin.",
                    "'Top hits' result in low loft/high spin.",
                    "--------------------------------------------------------------",
                    "Physics: The physics engine simulates lift based on ball",
                    "velocity and spin magnitude consistently. This will allow for",
                    "some interesting shots - experiment with spin!",
                    "--------------------------------------------------------------",
                    "POWER METER: Time your SPACE press in the sweet spots",
                    "to maximize power and minimize shot randomness. Hitting the",
                    "innermost sweet spot gives spin+power boosts and 100% accuracy.",
                    "You can left click to cancel the minigame at any time, and ",
                    "adjust your aim freely within the minigame.",
                    "--------------------------------------------------------------",
                    "TERRAIN: Different surfaces affect ball friction and the",
                    "difficulty of the swing. A sloped surface will kick the ",
                    "ball trajectory left or right.",
                    "--------------------------------------------------------------",
                    "WIND: High wind speeds apply additional drag as well as",
                    "ball displacement. Wind is more effective at higher altitudes.",
                    "--------------------------------------------------------------",
                    "RANGE: Liberal use of the club info [i], the rangefinder [F] and",
                    "the overview [LTAB] is recommended."
            };
            for (String line : gameplay) {
                font.draw(batch, line, centerX - 330, currentY);
                currentY -= 35;
            }

            batch.flush();
            batch.end();
            ScissorStack.popScissors();
            batch.begin();
        }

        font.getData().setScale(1.1f);
        font.setColor(Color.YELLOW);
        if (isAndroid) {
            font.draw(batch, "Drag to Scroll | Tap outside to Close", centerX - 210, boxY - 30);
        } else {
            font.draw(batch, "W/S or UP/DOWN to Scroll | [ESC] to Return", centerX - 200, boxY - 30);
        }
    }

    public boolean isClickInside(float x, float y) {
        return boxBounds.contains(x, y);
    }

    public void resetScroll() {
        this.instructionScrollY = 0;
    }
}