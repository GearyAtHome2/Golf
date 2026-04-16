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
import java.util.HashMap;
import java.util.Map;

/**
 * Sets Gdx.app to a no-op stub so that classes calling Gdx.app.log (etc.) can
 * be instantiated and exercised in unit tests without a GL context.
 * Call GdxTestSetup.init() in a @BeforeAll or @BeforeEach.
 */
public class GdxTestSetup {

    /** In-memory Preferences stub — returns default values; writes are silently ignored. */
    private static final Preferences STUB_PREFS = new Preferences() {
        private final Map<String, Object> data = new HashMap<>();
        @Override public Preferences putBoolean(String key, boolean val) { data.put(key, val); return this; }
        @Override public Preferences putInteger(String key, int val)     { data.put(key, val); return this; }
        @Override public Preferences putLong(String key, long val)       { data.put(key, val); return this; }
        @Override public Preferences putFloat(String key, float val)     { data.put(key, val); return this; }
        @Override public Preferences putString(String key, String val)   { data.put(key, val); return this; }
        @Override public Preferences put(Map<String, ?> vals)            { data.putAll(vals); return this; }
        @Override public boolean getBoolean(String key)                  { return getBoolean(key, false); }
        @Override public int     getInteger(String key)                  { return getInteger(key, 0); }
        @Override public long    getLong(String key)                     { return getLong(key, 0L); }
        @Override public float   getFloat(String key)                    { return getFloat(key, 0f); }
        @Override public String  getString(String key)                   { return getString(key, ""); }
        @Override public boolean getBoolean(String key, boolean def)     { return data.containsKey(key) ? (Boolean) data.get(key) : def; }
        @Override public int     getInteger(String key, int def)         { return data.containsKey(key) ? (Integer) data.get(key) : def; }
        @Override public long    getLong(String key, long def)           { return data.containsKey(key) ? (Long)    data.get(key) : def; }
        @Override public float   getFloat(String key, float def)         { return data.containsKey(key) ? (Float)   data.get(key) : def; }
        @Override public String  getString(String key, String def)       { return data.containsKey(key) ? (String)  data.get(key) : def; }
        @Override public Map<String, ?> get()                            { return data; }
        @Override public boolean contains(String key)                    { return data.containsKey(key); }
        @Override public void clear()                                    { data.clear(); }
        @Override public void remove(String key)                         { data.remove(key); }
        @Override public void flush()                                    {}
    };

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
            @Override public Preferences getPreferences(String name) { return STUB_PREFS; }
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
