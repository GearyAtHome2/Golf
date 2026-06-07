package com.gearygolf.golf.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.gearygolf.golf.Platform;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Rectangle;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.scenes.scene2d.utils.ScissorStack;
import com.badlogic.gdx.utils.viewport.Viewport;
import com.gearygolf.golf.hud.renderer.instructions.DesktopInstructionContent;
import com.gearygolf.golf.hud.renderer.instructions.InstructionContent;
import com.gearygolf.golf.hud.renderer.instructions.MobileInstructionContent;
import com.gearygolf.golf.input.GameInputProcessor;

public class InstructionRenderer {

    private float instructionScrollY = 0;
    private final Rectangle boxBounds = new Rectangle();
    private final InstructionContent desktopContent = new DesktopInstructionContent();
    private final InstructionContent mobileContent = new MobileInstructionContent();
    private final GlyphLayout layout = new GlyphLayout();

    public void render(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameInputProcessor input) {
        float delta = Gdx.graphics.getDeltaTime();
        boolean isAndroid = Platform.isAndroid();
        InstructionContent content = isAndroid ? mobileContent : desktopContent;

        if (!isAndroid) {
            float scrollSpeed = 450f * delta;
            if (input.isActionPressed(GameInputProcessor.Action.SPIN_UP))
                instructionScrollY = Math.max(0, instructionScrollY - scrollSpeed);
            if (input.isActionPressed(GameInputProcessor.Action.SPIN_DOWN))
                instructionScrollY = Math.min(content.getMaxScroll(), instructionScrollY + scrollSpeed);
        } else if (Gdx.input.isTouched()) {
            float dragY = Gdx.input.getDeltaY();
            if (Math.abs(dragY) > 0.1f)
                instructionScrollY = MathUtils.clamp(instructionScrollY - dragY * 2.0f, 0, content.getMaxScroll());
        }

        float boxW = viewport.getWorldWidth() * (isAndroid ? 0.85f : 0.65f);
        float boxH = viewport.getWorldHeight() * (isAndroid ? 0.70f : 0.70f);
        float boxX = (viewport.getWorldWidth() - boxW) / 2f;
        float boxY = (viewport.getWorldHeight() - boxH) / 2f;
        boxBounds.set(boxX, boxY, boxW, boxH);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        shapeRenderer.setProjectionMatrix(viewport.getCamera().combined);
        shapeRenderer.begin(ShapeRenderer.ShapeType.Filled);
        shapeRenderer.setColor(0.05f, 0.05f, 0.05f, 0.92f);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();

        shapeRenderer.begin(ShapeRenderer.ShapeType.Line);
        shapeRenderer.setColor(Color.GOLD);
        shapeRenderer.rect(boxX, boxY, boxW, boxH);
        shapeRenderer.end();

        if (batch.isDrawing()) batch.end();
        Rectangle scissor = new Rectangle();
        ScissorStack.calculateScissors(viewport.getCamera(), batch.getTransformMatrix(), boxBounds, scissor);

        if (ScissorStack.pushScissors(scissor)) {
            batch.begin();

            float titleScale  = isAndroid ? 1.5f : 0.8f;
            float headerScale = isAndroid ? 1.2f : 0.55f;
            float bodyScale   = isAndroid ? 0.85f : 0.45f;

            float currentY  = boxY + boxH - (boxH * 0.05f) + instructionScrollY;
            float margin    = boxX + (boxW * 0.05f);
            float lineSpace = isAndroid ? (viewport.getWorldHeight() * 0.06f) : (boxH * 0.05f);
            float lineGap   = lineSpace * 0.35f;  // padding added after each wrapped block

            // Width available for indented body text (leaves a right margin too)
            float textIndent = margin + boxW * 0.02f;
            float textWidth  = (boxX + boxW) - textIndent - boxW * 0.04f;

            // Two-column layout: description column starts further right
            float descXFrac = isAndroid ? 0.35f : 0.25f;
            float descX     = margin + boxW * descXFrac;
            float descWidth = (boxX + boxW) - descX - boxW * 0.04f;

            // ── Title ──────────────────────────────────────────────────────────
            font.getData().setScale(titleScale);
            font.setColor(Color.GOLD);
            layout.setText(font, content.getTitle());
            font.draw(batch, content.getTitle(), (viewport.getWorldWidth() - layout.width) / 2f, currentY);
            currentY -= layout.height + lineSpace;

            // ── Controls ───────────────────────────────────────────────────────
            font.getData().setScale(headerScale);
            font.setColor(Color.CYAN);
            layout.setText(font, content.getControlsHeader());
            font.draw(batch, content.getControlsHeader(), margin, currentY);
            currentY -= layout.height + lineGap;

            font.getData().setScale(bodyScale);
            for (String line : content.getControlLines()) {
                layout.setText(font, line, Color.WHITE, textWidth, Align.left, true);
                font.draw(batch, layout, textIndent, currentY);
                currentY -= layout.height + lineGap;
            }
            currentY -= lineSpace;

            // ── Clubs ──────────────────────────────────────────────────────────
            font.getData().setScale(headerScale);
            font.setColor(Color.CYAN);
            layout.setText(font, content.getClubsHeader());
            font.draw(batch, content.getClubsHeader(), margin, currentY);
            currentY -= layout.height + lineGap;

            font.getData().setScale(bodyScale);
            for (String[] club : content.getClubInfo()) {
                font.setColor(Color.GOLD);
                font.draw(batch, club[0] + ": ", textIndent, currentY);
                layout.setText(font, club[1], Color.WHITE, descWidth, Align.left, true);
                font.draw(batch, layout, descX, currentY);
                currentY -= layout.height + lineGap;
            }
            currentY -= lineSpace;

            // ── Gameplay ───────────────────────────────────────────────────────
            font.getData().setScale(headerScale);
            font.setColor(Color.CYAN);
            layout.setText(font, content.getGameplayHeader());
            font.draw(batch, content.getGameplayHeader(), margin, currentY);
            currentY -= layout.height + lineGap;

            font.getData().setScale(bodyScale);
            for (String line : content.getGameplayLines()) {
                layout.setText(font, line, Color.WHITE, textWidth, Align.left, true);
                font.draw(batch, layout, textIndent, currentY);
                currentY -= layout.height + lineGap;
            }
            currentY -= lineSpace;

            // ── Difficulty ─────────────────────────────────────────────────────
            font.getData().setScale(headerScale);
            font.setColor(Color.CYAN);
            layout.setText(font, content.getDifficultyHeader());
            font.draw(batch, content.getDifficultyHeader(), margin, currentY);
            currentY -= layout.height + lineGap;

            font.getData().setScale(bodyScale);
            for (String[] diff : content.getDifficultyInfo()) {
                font.setColor(Color.GOLD);
                font.draw(batch, diff[0], textIndent, currentY);
                layout.setText(font, diff[1], Color.WHITE, descWidth, Align.left, true);
                font.draw(batch, layout, descX, currentY);
                currentY -= layout.height + lineGap;
            }

            batch.flush();
            batch.end();
            ScissorStack.popScissors();
            batch.begin();
        }

        font.getData().setScale(isAndroid ? 1.4f : 0.45f);
        font.setColor(Color.YELLOW);
        float footerY = isAndroid ? (boxY - 30f) : (boxY - (viewport.getWorldHeight() * 0.05f));
        layout.setText(font, content.getFooter());
        font.draw(batch, content.getFooter(), (viewport.getWorldWidth() - layout.width) / 2f, footerY);
    }

    public boolean isClickInside(float x, float y) {
        return boxBounds.contains(x, y);
    }

    public void resetScroll() {
        this.instructionScrollY = 0;
    }
}