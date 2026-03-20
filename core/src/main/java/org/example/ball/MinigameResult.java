package org.example.ball;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

public class MinigameResult {
    public float powerMod;    // 1.5, 1.25, 1.1, 1.0, or 0.9->0.35
    public float accuracy;    // 0 in green, +/- outside
    public Rating rating;

    public enum Rating {
        PERFECTION(new String[]{"MONSTROUS!", "GOD-LIKE!", "YOU ARE GOLF", "BABABOOEY!", "GIVE ME BACK MY SON!", "I'VE ABANDONED MY CHILD!"}, new float[]{0.3f, 0.1f, 0.1f, 0.1f, 0.1f}, Color.PURPLE),
        SUPER(new String[]{"CRACKING!", "YOU BEAUTY!", "MASHED POTATOES!", "LIGHT THE CANDLE", "VERY CHEEKY"}, new float[]{0.3f, 0.2f, 0.1f, 0.1f, 0.3f}, Color.PINK),
        GREAT(new String[]{"SOLID", "LOVELY", "CHEEKY"}, new float[]{0.5f, 0.4f, 0.1f}, Color.GOLD),
        GOOD(new String[]{"She'll play", "Decent", "Nice", "That'll do, Pig"}, new float[]{7f, 2f, 1f, 0.2f}, Color.GREEN),
        POOR(new String[]{"Meh.", "Sloppy", "Weak", "Does your husband play golf?"}, new float[]{0.6f, 0.3f, 0.1f, 0.2f}, Color.GRAY),
        WANK(new String[]{"TRAGIC!", "HORRIFIC!", "FOOOOOORE!"}, new float[]{0.4f, 0.4f, 0.2f}, Color.ORANGE),
        SHIT(new String[]{"ABYSMAL", "DELETE GAME", "WHIFFED IT", "GO HOME"}, new float[]{0.5f, 0.4f, 0.1f, 0.3f}, new Color(0.5f, 0.25f, 0f, 1f));

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