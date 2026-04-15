package org.example;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;

public final class Platform {
    private Platform() {}

    public static boolean isAndroid() {
        return Gdx.app.getType() == Application.ApplicationType.Android;
    }
}
