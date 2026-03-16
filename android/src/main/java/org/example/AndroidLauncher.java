package org.example;

import android.os.Bundle;
import com.badlogic.gdx.backends.android.AndroidApplication;
import com.badlogic.gdx.backends.android.AndroidApplicationConfiguration;
import org.example.GolfGame;

public class AndroidLauncher extends AndroidApplication {
    @Override
    protected void onCreate (Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        AndroidApplicationConfiguration config = new AndroidApplicationConfiguration();
        
        // Mobile-specific battery/sensor optimizations
        config.useAccelerometer = false;
        config.useCompass = false;
        config.useImmersiveMode = true; // Hides navigation bar for full-screen play
        
        // This launches your core game class
        initialize(new GolfGame(), config);
    }
}