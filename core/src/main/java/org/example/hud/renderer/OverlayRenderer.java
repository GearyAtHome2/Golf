package org.example.hud.renderer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.viewport.Viewport;
import org.example.Club;
import org.example.GameConfig;
import org.example.hud.ClubInfoManager;
import org.example.input.GameInputProcessor;

public class OverlayRenderer {
    private final InstructionRenderer instructionRenderer = new InstructionRenderer();
    private final CameraConfigRenderer cameraConfigRenderer = new CameraConfigRenderer();
    private final ClubInfoRenderer clubInfoRenderer = new ClubInfoRenderer();
    private final Vector3 touchPoint = new Vector3();

    public void renderInstructions(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameInputProcessor input, Runnable onClose) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            if (isClickOutside(viewport, instructionRenderer::isClickInside)) {
                System.out.println("closing in overlay");
                onClose.run();
            }
        }

        batch.begin();
        instructionRenderer.render(batch, shapeRenderer, font, viewport, input);
        batch.end();
    }

    public void renderCameraConfig(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, GameConfig config, GameInputProcessor input, Runnable onClose) {
        if (Gdx.app.getType() == com.badlogic.gdx.Application.ApplicationType.Android && Gdx.input.justTouched()) {
            if (isClickOutside(viewport, cameraConfigRenderer::isClickInside)) {
                onClose.run();
            }
        }

        batch.begin();
        cameraConfigRenderer.render(batch, shapeRenderer, font, viewport, config, input);
        batch.end();
    }

    public void renderClubInfo(SpriteBatch batch, ShapeRenderer shapeRenderer, BitmapFont font, Viewport viewport, Club club) {
        if (batch.isDrawing()) batch.end();
        if (shapeRenderer.isDrawing()) shapeRenderer.end();

        clubInfoRenderer.render(
                batch,
                shapeRenderer,
                font,
                viewport,
                club.name(),
                ClubInfoManager.getClubDescription(club),
                ClubInfoManager.getCarryDistanceInfo(club),
                ClubInfoManager.getPowerInfo(club),
                ClubInfoManager.getLoftInfo(club),
                220f
        );
    }

    private boolean isClickOutside(Viewport viewport, ClickChecker checker) {
        touchPoint.set(Gdx.input.getX(), Gdx.input.getY(), 0);
        viewport.unproject(touchPoint);
        return !checker.isInside(touchPoint.x, touchPoint.y);
    }

    private interface ClickChecker {
        boolean isInside(float x, float y);
    }

    public void resetScrolls() {
        instructionRenderer.resetScroll();
        cameraConfigRenderer.resetScroll();
    }

    public InstructionRenderer getInstructionRenderer() {
        return instructionRenderer;
    }

    public CameraConfigRenderer getCameraConfigRenderer() {
        return cameraConfigRenderer;
    }
}