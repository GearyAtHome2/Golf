package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.hud.renderer.instructions.DesktopInstructionContent;
import org.example.hud.renderer.instructions.InstructionContent;
import org.example.hud.renderer.instructions.MobileInstructionContent;
import org.example.input.GameInputProcessor;

public class InstructionRenderer {

    private float instructionScrollY = 0;
    private final Rectangle boxBounds = new Rectangle();
    private final InstructionContent desktopContent = new DesktopInstructionContent();
    private final InstructionContent mobileContent = new MobileInstructionContent();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameInputProcessor input) {
        float delta = Gdx.graphics.getDeltaTime();
        boolean isAndroid = Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android;
        InstructionContent content = isAndroid ? mobileContent : desktopContent;

        // Input Logic
        if (!isAndroid) {
            float scrollSpeed = 450f * delta;
            if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP))
                instructionScrollY = Math.max(0, instructionScrollY - scrollSpeed);
            if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN))
                instructionScrollY = Math.min(content.getMaxScroll(), instructionScrollY + scrollSpeed);
        } else if (Gdx.input.isTouched()) {
            float dragY = Gdx.input.getDeltaY();
            if (Math.abs(dragY) > 0.1f)
                instructionScrollY = MathUtils.clamp(instructionScrollY - dragY * 3.5f, 0, content.getMaxScroll());
        }

        // Layout Calculations
        float centerX = viewport.getWorldWidth() / 2f;
        float boxW = isAndroid ? viewport.getWorldWidth() * 0.85f : 800f;
        float boxH = viewport.getWorldHeight() - (isAndroid ? 120f : 180f);
        float boxX = centerX - boxW / 2f;
        float boxY = isAndroid ? 80f : 120f;
        boxBounds.set(boxX, boxY, boxW, boxH);

        // Background
        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.08f, 0.08f, 0.08f, 0.95f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();

        if (batch.isDrawing()) batch.end();
        Rectangle scissor = new Rectangle();
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), boxBounds, scissor);

        if (ScissorStack.pushScissors(scissor)) {
            batch.begin();
            float currentY = boxY + boxH - (isAndroid ? 100 : 50) + instructionScrollY;
            float margin = boxX + (isAndroid ? 40 : 50);

            // Title
            font.getData().setScale(isAndroid ? 7.2f : 2.5f);
            font.setColor(Color.GOLD);
            font.draw(batch, content.getTitle(), centerX - (isAndroid ? 400 : 140), currentY);
            currentY -= (isAndroid ? 200 : 80);

            // Controls
            font.getData().setScale(isAndroid ? 4.0f : 1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, content.getControlsHeader(), margin, currentY);
            currentY -= (isAndroid ? 100 : 40);

            font.getData().setScale(isAndroid ? 3.2f : 1.1f);
            font.setColor(Color.WHITE);
            for (String line : content.getControlLines()) {
                font.draw(batch, line, margin + 20, currentY);
                currentY -= (isAndroid ? 90 : 35);
            }
            currentY -= (isAndroid ? 120 : 50);

            // Clubs
            font.getData().setScale(isAndroid ? 4.0f : 1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, content.getClubsHeader(), margin, currentY);
            currentY -= (isAndroid ? 100 : 40);

            font.getData().setScale(isAndroid ? 3.2f : 1.1f);
            for (String[] club : content.getClubInfo()) {
                font.setColor(Color.GOLD);
                font.draw(batch, club[0] + ": ", margin + 20, currentY);
                font.setColor(Color.WHITE);
                font.draw(batch, club[1], margin + (isAndroid ? 500 : 180), currentY);
                currentY -= (isAndroid ? 90 : 35);
            }
            currentY -= (isAndroid ? 120 : 50);

            // Gameplay
            font.getData().setScale(isAndroid ? 4.0f : 1.5f);
            font.setColor(Color.CYAN);
            font.draw(batch, content.getGameplayHeader(), margin, currentY);
            currentY -= (isAndroid ? 100 : 40);

            font.getData().setScale(isAndroid ? 3.2f : 1.1f);
            font.setColor(Color.WHITE);
            for (String line : content.getGameplayLines()) {
                font.draw(batch, line, margin + 20, currentY);
                currentY -= (isAndroid ? 90 : 35);
            }

            batch.flush();
            batch.end();
            ScissorStack.popScissors();
            batch.begin();
        }

        font.getData().setScale(isAndroid ? 3.2f : 1.1f);
        font.setColor(Color.YELLOW);
        font.draw(batch, content.getFooter(), centerX - (isAndroid ? 550 : 200), boxY - 40);
    }

    public boolean isClickInside(float x, float y) {
        return boxBounds.contains(x, y);
    }

    public void resetScroll() {
        this.instructionScrollY = 0;
    }
}