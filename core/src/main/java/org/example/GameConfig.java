package org.example;

public class GameConfig {
    public CameraConfig cameraSettings = new CameraConfig();
    public CameraType cameraType = CameraType.FREE;

    public AnimSpeed animSpeed = AnimSpeed.NONE;
    public Difficulty difficulty = Difficulty.NOVICE;
    public boolean particlesEnabled = true;

    // Game Speed cycle: 0.25, 0.5, 1, 2, 3, 5
    private final float[] speedValues = {0.25f, 0.5f, 1.0f, 2.0f, 3.0f, 5.0f};
    private int speedIndex = 2; // Default to 1.0f

    public enum CameraType {FREE, DRAG, FIXED}

    public enum AnimSpeed {
        NONE(0f), SLOW(0.6f), FAST(1.4f);
        public final float mult;

        AnimSpeed(float m) {
            this.mult = m;
        }
    }

    public enum Difficulty {
        NOVICE(0.17f), INTERMEDIATE(0.26f), ADVANCED(0.45f), PRO(0.67f), TOUR_PRO(0.88f);
        public final float needleSpeedMult;

        Difficulty(float s) {
            this.needleSpeedMult = s;
        }

        // Utility availability: each level above NOVICE removes one aid (in order of utility index)
        public boolean hasClubInfo()       { return ordinal() < 1; } // NOVICE only
        public boolean hasShotProjection() { return ordinal() < 2; } // NOVICE, INTERMEDIATE
        public boolean hasRangeFinder()    { return ordinal() < 3; } // NOVICE, INTERMEDIATE, ADVANCED
        public boolean hasWindicator()     { return ordinal() < 4; } // all except TOUR_PRO

        public static String[] getNames() {
            Difficulty[] values = Difficulty.values();
            String[] names = new String[values.length];
            for (int i = 0; i < values.length; i++) {
                names[i] = values[i].name();
            }
            return names;
        }
    }

    public void cycleAnimation() {
        animSpeed = AnimSpeed.values()[(animSpeed.ordinal() + 1) % AnimSpeed.values().length];
    }

    public void cycleDifficulty() {
        difficulty = Difficulty.values()[(difficulty.ordinal() + 1) % Difficulty.values().length];
    }

    public void setDifficulty(Difficulty diff) {
        difficulty = diff;
    }

    public void cycleCamera() {
        cameraType = CameraType.values()[(cameraType.ordinal() + 1) % CameraType.values().length];
    }

    public void adjustGameSpeed(boolean increase) {
        if (increase) {
            speedIndex = Math.min(speedIndex + 1, speedValues.length - 1);
        } else {
            speedIndex = Math.max(speedIndex - 1, 0);
        }
    }

    public float getGameSpeed() {
        return speedValues[speedIndex];
    }

    public class CameraConfig {
        public enum ControlStyle {FREE, DRAG} // FREE = Always active, DRAG = Right Click to move

        public ControlStyle controlStyle = ControlStyle.FREE;
        public float mouseSensitivity = 0.3f;
        public float lerpSpeed = 6.0f;
        public float fineTuneDivider = 5.0f;

        public boolean invertX = false;
        public boolean invertY = false;

        public float getXMult() {
            return invertX ? 1f : -1f;
        }

        public float getYMult() {
            return invertY ? 1f : -1f;
        }
    }
}