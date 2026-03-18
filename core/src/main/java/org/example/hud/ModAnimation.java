package org.example.hud;

import com.badlogic.gdx.graphics.Color;

public class ModAnimation {
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