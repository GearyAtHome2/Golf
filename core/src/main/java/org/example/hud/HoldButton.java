package org.example.hud;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import org.example.input.GameInputProcessor;
import org.example.input.MobileInputProcessor;

public class HoldButton extends TextButton {
    private float holdTime = 0;
    private final float requiredTime = 1.2f;
    private final GameInputProcessor.Action action;
    private final MobileInputProcessor mobileInput;
    private final Drawable fillDrawable;

    public HoldButton(String text, TextButtonStyle style, GameInputProcessor.Action action, MobileInputProcessor mobileInput, Drawable fillDrawable) {
        super(text, style);
        this.action = action;
        this.mobileInput = mobileInput;
        this.fillDrawable = fillDrawable;

        addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (isDisabled()) return false;
                holdTime = 0;
                return true;
            }

            @Override
            public void touchUp(InputEvent event, float x, float y, int pointer, int button) {
                if (holdTime >= requiredTime) {
                    mobileInput.triggerAction(action);
                }
                holdTime = 0;
            }
        });
    }

    @Override
    public void act(float delta) {
        super.act(delta);
        if (isPressed() && !isDisabled()) {
            holdTime += delta;
        } else {
            holdTime = 0;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        super.draw(batch, parentAlpha);
        if (isPressed() && !isDisabled() && holdTime > 0) {
            float progress = Math.min(holdTime / requiredTime, 1f);
            float barW = getWidth() * progress;
            fillDrawable.draw(batch, getX(), getY(), barW, getHeight());
        }
    }
}