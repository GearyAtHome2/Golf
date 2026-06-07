package com.gearygolf.golf.hud.minigame;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import com.gearygolf.golf.ball.MinigameResult;

import static com.gearygolf.golf.ball.MinigameResult.Rating.*;

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

    /**
     * powerMod for a GOOD-zone hit with this club. Used to scale all penalty zones
     * so that harder clubs (more tiers of potential) receive bigger absolute punishment
     * for landing outside the good zone.
     *
     * Tier layout (ordinal: PERFECTION=0, SUPER=1, GREAT=2, GOOD=3):
     *   baseDiff >= 1.6 → max is PERFECTION → GOOD is 3 tiers below → 0.70
     *   baseDiff >= 1.3 → max is SUPER      → GOOD is 2 tiers below → 0.80
     *   baseDiff >= 1.0 → max is GREAT      → GOOD is 1 tier below  → 0.90
     *   baseDiff < 1.0  → max is GOOD       → GOOD is max tier      → 1.00
     */
    private float goodPowerMod = 1.00f;
    /** Tiers between the club's best achievable rating and GOOD; drives accuracy scaling. */
    private int goodTiersBelow = 0;

    public void reset(float baseDifficulty) {
        needlePos = 0f;
        needleMovingRight = true;
        greenCenter = 0.5f;
        barWidthMult = 1.0f;
        displayedWidthMult = 1.0f;

        sweetSpots.clear();

        // Determine the best achievable rating tier for this club.
        // Ordinal mapping: PERFECTION=0, SUPER=1, GREAT=2, GOOD=3
        int maxTierOrdinal = baseDifficulty >= 1.6f ? 0
                           : baseDifficulty >= 1.3f ? 1
                           : baseDifficulty >= 1.0f ? 2
                           : 3;

        // GOOD is always at ordinal 3 — tiers below max determines its powerMod penalty.
        // Each tier below best costs 10% power so that club.powerMult (already baked with
        // the old top-tier multiplier) acts as the true maximum and worse results scale down.
        goodTiersBelow = 3 - maxTierOrdinal;
        goodPowerMod   = 1.0f - goodTiersBelow * 0.10f;

        if (baseDifficulty >= 1.0f) {
            // GREAT is at ordinal 2 — (2 - maxTierOrdinal) tiers below max (0, 1, or 2)
            float pm = 1.0f - (2 - maxTierOrdinal) * 0.10f;
            sweetSpots.add(new Zone(GREAT, new Color(1f, 0.9f, 0f, 1f), 0.4f, pm, 1.5f));
        }
        if (baseDifficulty >= 1.3f) {
            // SUPER is at ordinal 1 — (1 - maxTierOrdinal) tiers below max (0 or 1)
            float pm = 1.0f - (1 - maxTierOrdinal) * 0.10f;
            sweetSpots.add(new Zone(SUPER, new Color(1f, 0.4f, 0.7f, 1f), 0.4f, pm, 1.8f));
        }
        if (baseDifficulty >= 1.6f) {
            // PERFECTION is always max tier — no penalty
            sweetSpots.add(new Zone(PERFECTION, new Color(0.6f, 0.1f, 0.9f, 1f), 0.4f, 1.00f, 2.0f));
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
            float distFromBullseye = needlePos - trueCenter;

            float maxPossibleDist = (distFromBullseye > 0)
                    ? (greenCenter + greenHalfW) - trueCenter
                    : trueCenter - (greenCenter - greenHalfW);

            float normalizedOffset = distFromBullseye / maxPossibleDist;

            // Accuracy drift scales slightly with how many tiers below the club's best this is —
            // a GOOD hit on a driver (3 tiers below PERFECTION) drifts more than on a wedge (0 below).
            float accuracyScale = 0.08f + goodTiersBelow * 0.015f;
            float goodAccuracy = normalizedOffset * accuracyScale;

            setupResult(result, goodPowerMod, GOOD, goodAccuracy);
            logShotDetail(needlePos, trueCenter, result, normalizedOffset);
            return result;
        }

        // 3. Penalty Zones (Red/Orange)
        // All powerMods expressed relative to goodPowerMod so that driver mishits are
        // punished more in absolute terms than wedge mishits — matches the new baked-in
        // club.powerMult values where the driver's powerMult is already ~1.5× its old value.
        float side = Math.signum(needlePos - greenCenter);
        float distFromEdge = Math.abs(needlePos - greenCenter) - greenHalfW;
        float orangeThreshold = greenHalfW * 0.5f;
        float redThreshold = greenHalfW * 1.0f;

        if (distFromEdge <= orangeThreshold) {
            result.rating = POOR;
            result.powerMod = goodPowerMod * 0.90f;
            result.accuracy = side * 0.18f;
        } else if (distFromEdge <= redThreshold) {
            result.rating = TERRIBLE;
            result.powerMod = goodPowerMod * 0.75f;
            result.accuracy = side * 0.40f;
        } else {
            result.rating = ABYSMAL;
            float missDist = MathUtils.clamp(distFromEdge / (0.5f - greenHalfW), 0, 1);
            result.accuracy = side * missDist;
            result.powerMod = goodPowerMod * MathUtils.lerp(0.65f, 0.25f, missDist);
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