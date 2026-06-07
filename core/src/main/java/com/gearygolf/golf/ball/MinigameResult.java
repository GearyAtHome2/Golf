package com.gearygolf.golf.ball;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

public class MinigameResult {
    public float powerMod;       // 1.5, 1.25, 1.1, 1.0, or 0.9->0.35
    public float accuracy;       // 0 in green, +/- outside
    public Rating rating;
    /** Multiplier on effective club loft. 1.0 = normal, <1 = thin/skull, fat uses loftDeltaDeg instead. */
    public float loftMult = 1.0f;
    /** Additive degrees on effective loft (fat/chunk only). 0 = normal. */
    public float loftDeltaDeg = 0.0f;
    /** Multiplier on all spin output. 1.0 = normal, <1 = less spin, negative = topspin. */
    public float tempoSpinMult = 1.0f;
    /**
     * Shank angle in degrees (0 = normal shot). When >0, the shot direction is rotated
     * this many degrees rightward after all other calculations, overriding accuracy-based
     * direction. Hosel contact only — triggered by extreme heel contact in new swing mode.
     */
    public float shankAngleDeg = 0f;
    /**
     * Attack angle in degrees passed from the new swing gesture (-ve = descending).
     * 0 in old swing mode — executeShot falls back to spin-dial derivation in that case.
     */
    public float attackAngleDeg = 0f;

    /**
     * Follow-through sidespin contribution: positive = flip side (hook bias),
     * negative = extension side (fade bias).  Injected directly into sidespin in
     * executeShot, does NOT affect launch direction (unlike accuracy).
     * Units match accuracy: ±1.0 = full sidespin contribution.
     */
    public float ftSpinContrib = 0f;

    public enum Rating {
        PERFECTION(new String[]{"OUT OF THIS WORLD!", "YOU ARE GOLF", "UNIDENTIFIABLE!", "GIVE ME BACK MY SON!", "BEAM ME UP", "WHAT!?!"}, new float[]{0.3f, 0.1f, 0.1f, 0.1f, 0.1f, 0.1f}, Color.PURPLE),
        SUPER(new String[]{"CRACKING!", "YOU BEAUTY!", "MASHED POTATOES!", "LIGHT THE CANDLE", "VERY CHEEKY", "THAT'S ELECTRIC!"}, new float[]{0.3f, 0.2f, 0.1f, 0.1f, 0.3f, 0.3f}, Color.PINK),
        GREAT(new String[]{"SOLID", "BLAZIN'", "ABOVE AVERAGE", "HOT HOT"}, new float[]{0.5f, 0.4f, 0.1f, 0.3f}, Color.GOLD),
        GOOD(new String[]{"She'll play", "Decent", "Nice", "That'll do, Pig", "That's Golf"}, new float[]{1f, 1f, 1f, 0.3f, 0.8f}, Color.GREEN),
        POOR(new String[]{"Meh.", "Sloppy", "Weak", "Does your boyfriend play golf?", "Not good enough"}, new float[]{0.6f, 0.3f, 0.2f, 0.1f, 0.3f}, Color.GRAY),
        TERRIBLE(new String[]{"TRAGIC!", "HORRIFIC!", "FOOOOOORE!", "Tiger Wouldn't"}, new float[]{0.4f, 0.4f, 0.2f, 0.5f}, Color.ORANGE),
        ABYSMAL(new String[]{"HORRIBLE", "DELETE GAME", "WHIFFED IT", "GO HOME"}, new float[]{0.5f, 0.4f, 0.1f, 0.3f}, new Color(0.5f, 0.25f, 0f, 1f));

        private final String[] phrases;
        private final float[] weights;
        private final float totalWeight;
        public final Color color;

        Rating(String[] phrases, float[] weights, Color color) {
            this.phrases = phrases;
            this.weights = weights;
            this.color = color;

            // Automatically calculate the sum of weights for normalization
            float sum = 0;
            for (float w : weights) {
                sum += w;
            }
            this.totalWeight = sum;
        }

        /**
         * Picks a phrase based on relative weights.
         * The weights no longer need to sum to 1.
         */
        public String getRandomPhrase() {
            // Roll between 0 and the total sum of all weights
            float roll = MathUtils.random(totalWeight);
            float cumulative = 0;

            for (int i = 0; i < weights.length; i++) {
                cumulative += weights[i];
                if (roll <= cumulative) {
                    return phrases[i];
                }
            }
            return phrases[0];
        }

        // Getters preserved as per requirements
        public String[] getPhrases() {
            return phrases;
        }

        public float[] getWeights() {
            return weights;
        }

        public float getTotalWeight() {
            return totalWeight;
        }

        public Color getColor() {
            return color;
        }
    }

    public float getPowerMod() {
        return powerMod;
    }

    public void setPowerMod(float powerMod) {
        this.powerMod = powerMod;
    }

    public float getAccuracy() {
        return accuracy;
    }

    public void setAccuracy(float accuracy) {
        this.accuracy = accuracy;
    }

    public Rating getRating() {
        return rating;
    }

    public void setRating(Rating rating) {
        this.rating = rating;
    }
}