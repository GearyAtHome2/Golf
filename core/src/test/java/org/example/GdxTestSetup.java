package org.example;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.ApplicationLogger;
import com.badlogic.gdx.Audio;
import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.LifecycleListener;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.Preferences;
import com.badlogic.gdx.utils.Clipboard;

/**
 * Sets Gdx.app to a no-op stub so that classes calling Gdx.app.log (etc.) can
 * be instantiated and exercised in unit tests without a GL context.
 * Call GdxTestSetup.init() in a @BeforeAll or @BeforeEach.
 */
public class GdxTestSetup {

    public static void init() {
        if (Gdx.app != null) return;
        Gdx.app = new Application() {
            @Override public void log(String tag, String message) {}
            @Override public void log(String tag, String message, Throwable e) {}
            @Override public void error(String tag, String message) {}
            @Override public void error(String tag, String message, Throwable e) {}
            @Override public void debug(String tag, String message) {}
            @Override public void debug(String tag, String message, Throwable e) {}
            @Override public void setLogLevel(int logLevel) {}
            @Override public int getLogLevel() { return Application.LOG_NONE; }
            @Override public void setApplicationLogger(ApplicationLogger logger) {}
            @Override public ApplicationLogger getApplicationLogger() { return null; }
            @Override public ApplicationType getType() { return ApplicationType.HeadlessDesktop; }
            @Override public int getVersion() { return 0; }
            @Override public long getJavaHeap() { return 0; }
            @Override public long getNativeHeap() { return 0; }
            @Override public Preferences getPreferences(String name) { return null; }
            @Override public Clipboard getClipboard() { return null; }
            @Override public void postRunnable(Runnable runnable) { runnable.run(); }
            @Override public void exit() {}
            @Override public void addLifecycleListener(LifecycleListener listener) {}
            @Override public void removeLifecycleListener(LifecycleListener listener) {}
            @Override public Net getNet() { return null; }
            @Override public Files getFiles() { return null; }
            @Override public Graphics getGraphics() { return null; }
            @Override public Audio getAudio() { return null; }
            @Override public Input getInput() { return null; }
            @Override public ApplicationListener getApplicationListener() { return null; }
        };
    }
}
