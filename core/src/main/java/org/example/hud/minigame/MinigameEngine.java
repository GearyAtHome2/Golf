package org.example.hud.minigame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import org.example.ball.MinigameResult;

import static org.example.ball.MinigameResult.Rating.*;

public class MinigameEngine {
    public static class Zone {
        public MinigameResult.Rating label;
        public Color color;
        public float center;
        public float widthRatio;
        public float powerMod;
        public float lerpMult;

        public Zone(MinigameResult.Rating label, Color color, float widthRatio, float powerMod, float lerpMult) {
            this.label = label;
            this.color = color;
            this.center = 0.5f;
            this.widthRatio = widthRatio;
            this.powerMod = powerMod;
            this.lerpMult = lerpMult;
        }
    }

    public float needlePos = 0f;
    public float greenCenter = 0.5f;
    public float barWidthMult = 1.0f;
    public float displayedWidthMult = 1.0f;
    public boolean needleMovingRight = true;

    public final Array<Zone> sweetSpots = new Array<>();

    public void reset(float baseDifficulty) {
        needlePos = 0f;
        needleMovingRight = true;
        greenCenter = 0.5f;
        barWidthMult = 1.0f;
        displayedWidthMult = 1.0f;

        sweetSpots.clear();

        if (baseDifficulty >= 1.0f) {
            sweetSpots.add(new Zone(GREAT, new Color(1f, 0.9f, 0f, 1f), 0.4f, 1.10f, 1.5f));
        }
        if (baseDifficulty >= 1.3f) {
            sweetSpots.add(new Zone(SUPER, new Color(1f, 0.4f, 0.7f, 1f), 0.4f, 1.25f, 1.8f));
        }
        if (baseDifficulty >= 1.6f) {
            sweetSpots.add(new Zone(PERFECTION, new Color(0.6f, 0.1f, 0.9f, 1f), 0.4f, 1.50f, 2.0f));
        }
    }

    public MinigameResult calculateResult(float needlePos) {
        MinigameResult result = new MinigameResult();
        float greenHalfW = (0.25f * barWidthMult) / 2f;

        float trueCenter = (sweetSpots.size > 0) ? sweetSpots.peek().center : greenCenter;

        float[] nestedWidths = new float[sweetSpots.size];
        float tempW = greenHalfW;
        for (int i = 0; i < sweetSpots.size; i++) {
            tempW *= sweetSpots.get(i).widthRatio;
            nestedWidths[i] = tempW;
        }

// 1. Check Sweet Spots (Inner to Outer)
        for (int i = sweetSpots.size - 1; i >= 0; i--) {
            Zone z = sweetSpots.get(i);
            float zoneHalfW = nestedWidths[i];

            if (Math.abs(needlePos - z.center) <= zoneHalfW) {
                float accuracyPenalty = 0f;

                if (i < sweetSpots.size - 1) {
                    float distFromBullseye = Math.abs(needlePos - trueCenter);
                    accuracyPenalty = (distFromBullseye / greenHalfW) * 0.02f;
                }

                setupResult(result, z.powerMod, z.label, accuracyPenalty);
                logShotDetail(needlePos, trueCenter, result, accuracyPenalty);
                return result;
            }
        }

        // 2. Check Green Zone (GOOD)
        if (Math.abs(needlePos - greenCenter) <= greenHalfW) {
            // Accuracy is calculated relative to the innermost sweet spot
            // Normalized to the distance from that center to the edge of the green bar
            float distFromBullseye = needlePos - trueCenter;

            // To normalize, we find the max possible distance inside green from the trueCenter
            float maxPossibleDist = (distFromBullseye > 0)
                    ? (greenCenter + greenHalfW) - trueCenter
                    : trueCenter - (greenCenter - greenHalfW);

            float normalizedOffset = distFromBullseye / maxPossibleDist;

            // Apply a slight 'Pro Tour' drift for GOOD shots
            float goodAccuracy = normalizedOffset * 0.08f;

            setupResult(result, 1.00f, GOOD, goodAccuracy);
            logShotDetail(needlePos, trueCenter, result, normalizedOffset);
            return result;
        }

        // 3. Penalty Zones (Red/Orange)
        float side = Math.signum(needlePos - greenCenter);
        float distFromEdge = Math.abs(needlePos - greenCenter) - greenHalfW;
        float orangeThreshold = greenHalfW * 0.5f;
        float redThreshold = greenHalfW * 1.0f;

        if (distFromEdge <= orangeThreshold) {
            result.rating = POOR;
            result.powerMod = 0.95f;
            result.accuracy = side * 0.18f;
        } else if (distFromEdge <= redThreshold) {
            result.rating = WANK;
            result.powerMod = 0.85f;
            result.accuracy = side * 0.40f;
        } else {
            result.rating = SHIT;
            float missDist = MathUtils.clamp(distFromEdge / (0.5f - greenHalfW), 0, 1);
            result.accuracy = side * missDist;
            result.powerMod = MathUtils.lerp(0.80f, 0.35f, missDist);
        }

        logShotDetail(needlePos, trueCenter, result, result.accuracy);
        return result;
    }

    private void logShotDetail(float needle, float target, MinigameResult res, float rawOffset) {
        Gdx.app.log("MINIGAME_CALC", String.format(
                "Rating: %s | Needle: %.3f | Target (Bullseye): %.3f | Raw Offset: %.3f | Final Accuracy: %.4f | Power: %.2f",
                res.rating, needle, target, rawOffset, res.accuracy, res.powerMod
        ));
    }

    private void setupResult(MinigameResult res, float power, MinigameResult.Rating rating, float accuracy) {
        res.powerMod = power;
        res.rating = rating;
        res.accuracy = accuracy;
    }

    public void updateZones(float delta, float sidePush, float spinX, float randomness) {
        displayedWidthMult = MathUtils.lerp(displayedWidthMult, barWidthMult, delta * 6f);
        float greenHalfW = (0.25f * displayedWidthMult) / 2f;

        float targetGreen = MathUtils.clamp(0.5f + (sidePush * 0.4f), greenHalfW, 1.0f - greenHalfW);
        greenCenter = MathUtils.lerp(greenCenter, targetGreen, delta * 6f);

        float rawSpinForce = (spinX * 0.8f + randomness) * 0.1f;
        float lastCenter = greenCenter;
        float lastHalfW = greenHalfW;

        for (Zone z : sweetSpots) {
            float target = wrapZone(lastCenter, rawSpinForce * z.lerpMult, lastHalfW, z.widthRatio);
            z.center = MathUtils.lerp(z.center, target, delta * 6f);

            lastCenter = z.center;
            lastHalfW = lastHalfW * z.widthRatio;
        }
    }

    private float wrapZone(float parentCenter, float force, float parentHalfW, float childRatio) {
        float childHalfW = parentHalfW * childRatio;
        return MathUtils.clamp(parentCenter + force, parentCenter - (parentHalfW - childHalfW), parentCenter + (parentHalfW - childHalfW));
    }
}