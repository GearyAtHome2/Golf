package com.gearygolf.golf;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

public class DesktopLauncher {

    public static void main(String[] args) {
        loadFirebaseKey();

        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Geary Golf");
        config.setWindowedMode(1280, 720);
        config.setForegroundFPS(60);
        config.useVsync(true);

        new Lwjgl3Application(new GolfGame(), config);
    }

    /** Reads android/key.properties (gitignored) and injects the Firebase API key. */
    private static void loadFirebaseKey() {
        System.out.println("[DesktopLauncher] Working directory: " + System.getProperty("user.dir"));

        // Try both the project root and one level up (Gradle run task cwd varies).
        String[] candidates = { "android/key.properties", "../android/key.properties" };
        for (String path : candidates) {
            File keyFile = new File(path);
            System.out.println("[DesktopLauncher] Trying: " + keyFile.getAbsolutePath() + " — exists: " + keyFile.exists());
            if (!keyFile.exists()) continue;
            try (FileInputStream in = new FileInputStream(keyFile)) {
                Properties props = new Properties();
                props.load(in);
                String key = props.getProperty("firebaseApiKey", "");
                System.out.println("[DesktopLauncher] firebaseApiKey loaded: " + (key.isEmpty() ? "EMPTY" : "OK (" + key.length() + " chars)"));
                if (!key.isEmpty()) FirebaseConfig.API_KEY = key;
            } catch (Exception e) {
                System.err.println("[DesktopLauncher] Failed to load " + path + ": " + e.getMessage());
            }
            return;
        }
        System.err.println("[DesktopLauncher] android/key.properties not found in any candidate path — auth will fail.");
    }
}