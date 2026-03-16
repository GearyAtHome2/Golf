package org.example;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

public class DesktopLauncher {

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Golf Game 2026 - Alpha");

        config.setWindowedMode(1280, 720);

        config.setForegroundFPS(60);

        config.useVsync(true);

        new Lwjgl3Application(new GolfGame(), config);
    }
}