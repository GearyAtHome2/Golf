package org.example.hud;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;

public class InstructionRenderer {

    private float instructionScrollY = 0;
    private final float MAX_SCROLL = 1400f; // Increased to accommodate the Clubs section

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport) {
        float delta = Gdx.graphics.getDeltaTime();
        float scrollSpeed = 450f * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.UP) || Gdx.input.isKeyPressed(Input.Keys.W)) {
            instructionScrollY = Math.max(0, instructionScrollY - scrollSpeed);
        }
        if (Gdx.input.isKeyPressed(Input.Keys.DOWN) || Gdx.input.isKeyPressed(Input.Keys.S)) {
            instructionScrollY = Math.min(MAX_SCROLL, instructionScrollY + scrollSpeed);
        }

        float centerX = viewport.getWorldWidth() / 2f;
        float boxW = 800;
        float boxH = viewport.getWorldHeight() - 180;
        float boxX = centerX - boxW / 2f;
        float boxY = 120;

        // Draw Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 0.9f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();
        Gdx.gl.glDisable(GL20.GL_BLEND);

        // Prepare Scissor for Clipping
        Rectangle scissor = new Rectangle();
        Rectangle area = new Rectangle(boxX, boxY, boxW, boxH);
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), area, scissor);

        batch.setProjectionMatrix(viewport.getCamera().combined);
        batch.begin();

        if (ScissorStack.pushScissors(scissor)) {
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
                    {"HYBRID", "The 'rescue' club. Easy to hit with respectable, predictable distance."},
                    {"IRONS (5-9)", "Old faithfuls. Higher numbers mean more loft and control."},
                    {"WEDGES (P/G/S)", "Precision clubs. Very forgiving sweet spots, high loft."},
                    {"LOB WEDGE", "Extreme loft for clearing obstacles. Requires high precision."},
                    {"PUTTER", "Zero loft. Designed for the green, but works for 'Texas Wedges'."}
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
                    "ball displacement. Wind is more effective at higher altitudes."
            };
            for (String line : gameplay) {
                font.draw(batch, line, centerX - 330, currentY);
                currentY -= 35;
            }

            batch.flush();
            ScissorStack.popScissors();
        }

        font.getData().setScale(1.1f);
        font.setColor(Color.YELLOW);
        font.draw(batch, "W/S or UP/DOWN to Scroll | [ESC] to Close", centerX - 180, boxY - 30);

        batch.end();
    }

    public void resetScroll() {
        this.instructionScrollY = 0;
    }
}