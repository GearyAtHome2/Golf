package com.gearygolf.golf.tutorial;

import com.badlogic.gdx.Gdx;

public class TutorialController {

    public enum Step {
        STEP_1_DISTANCE,    // tap DISTANCE button
        STEP_2_AIM,         // rotate camera to account for wind
        STEP_3_INFO,        // tap INFO button
        STEP_4_CLUB,        // switch to 9 Iron
        STEP_5_POWER,       // tap MAX POWER
        STEP_6_HIT,         // hit during minigame
        STEP_7_WATCH,       // silent — wait for ball to land on green
        STEP_8_PUTTER,      // ball on green — prompt putter
        STEP_9_PROJECT,     // ball on green — prompt projection
        STEP_10_AIM,        // tell user to aim at hole and putt (no screen dim — border-only highlight)
        STEP_11_PUTT,       // silent — wait for ball to go in
        STEP_12_COMPLETE_1, // post-hole info box 1 (advance via tap)
        STEP_13_COMPLETE_2, // post-hole info box 2 (advance via tap) → level 2

        // --- Level 2: par-4, tailwind from right, teaches driver + spindicator ---
        STEP_L2_1_SPINDICATOR, // spotlight small spindicator — advance when player taps it (opens big view)
        STEP_L2_1B_AIM,        // force big overlay — text at top — wait for dot lower-left
        STEP_L2_2_HIT,         // hint bar "Hit at the right time!" — advance when shot fires
        STEP_L2_3_WATCH,       // silent — wait for tee shot to land (not green/tee)
        STEP_L2_4_APPROACH,    // hint "Pick the right club" — advance when approach shot fires
        STEP_L2_5_WATCH,       // silent — wait for ball to go in hole (triggerVictory)
        STEP_L2_6_COMPLETE_1,  // closing info box 1 (advance via tap)
        STEP_L2_7_COMPLETE_2,  // closing info box 2 (advance via tap) → DONE

        STEP_L3_1_SPINDICATOR, // spotlight spindicator — advance when player taps it
        STEP_L3_2_WATCH,       // silent — wait for tee shot to land (not green/tee)
        STEP_L3_3_LIE,         // hint about projection + slope — advance on NEXT tap
        STEP_L3_4_TIP,         // hint about club choice + low flight — advance on NEXT tap
        STEP_L3_4B_WIND_TIP,   // hint about club selection vs headwind — advance on NEXT tap
        STEP_L3_5_WAIT_HOLE,   // silent — wait for ball to go in hole (triggerVictory)
        STEP_L3_6_CONGRATS,    // "Congrats!" completion box (advance via tap)
        STEP_L3_7_DAILY_PROMPT, // "Try the daily challenge!" box — shown if daily-1 not submitted today (advance via tap → DONE)

        DONE;

        /**
         * Whether to show an overlay this step.
         */
        public boolean isOverlayVisible() {
            return this != STEP_7_WATCH && this != STEP_11_PUTT
                && this != STEP_L2_3_WATCH && this != STEP_L2_5_WATCH
                && this != STEP_L3_2_WATCH && this != STEP_L3_5_WAIT_HOLE
                && this != DONE;
        }

        /**
         * Whether to block non-target inputs this step (mobile: button Touchable; desktop: handled by early return).
         * Level 2 only blocks during the putter step — the rest are contextual hints.
         */
        public boolean isBlockingInput() {
            return this == STEP_1_DISTANCE || this == STEP_2_AIM || this == STEP_3_INFO
                    || this == STEP_4_CLUB || this == STEP_5_POWER || this == STEP_6_HIT
                    || this == STEP_8_PUTTER || this == STEP_9_PROJECT || this == STEP_10_AIM
                    || this == STEP_L2_1_SPINDICATOR || this == STEP_L2_1B_AIM
                    || this == STEP_L3_1_SPINDICATOR;
        }

        /**
         * Whether to use the full spotlight (dim + button cutout) style.
         * False = hint bar only (no full dim), suitable when the player needs to see the course.
         * Note: STEP_10_AIM is excluded — it uses a border-only highlight with no dim.
         */
        public boolean isSpotlightStyle() {
            return this == STEP_1_DISTANCE || this == STEP_3_INFO
                    || this == STEP_4_CLUB || this == STEP_5_POWER || this == STEP_6_HIT
                    || this == STEP_8_PUTTER || this == STEP_9_PROJECT
                    || this == STEP_L2_1_SPINDICATOR || this == STEP_L3_1_SPINDICATOR;
        }

        /**
         * Whether the big spin overlay handles dimming this step (skip TutorialOverlayRenderer dim).
         */
        public boolean isDimHandledExternally() {
            return this == STEP_L2_1B_AIM;
        }

        /**
         * Hint-only steps that need no screen dim at all — overlay disappears when charging begins.
         */
        public boolean isNoDimHint() {
            return this == STEP_L2_2_HIT || this == STEP_L2_4_APPROACH
                || this == STEP_L3_3_LIE || this == STEP_L3_4_TIP || this == STEP_L3_4B_WIND_TIP;
        }

        /**
         * Whether to show a "NEXT >" button in the hint box (advance by tapping it).
         */
        public boolean isNextButtonStep() {
            return this == STEP_12_COMPLETE_1 || this == STEP_13_COMPLETE_2
                || this == STEP_L2_6_COMPLETE_1 || this == STEP_L2_7_COMPLETE_2
                || this == STEP_L3_3_LIE || this == STEP_L3_4_TIP || this == STEP_L3_4B_WIND_TIP
                || this == STEP_L3_6_CONGRATS || this == STEP_L3_7_DAILY_PROMPT;
        }

        /**
         * Whether this is a post-hole info box that advances on any tap.
         * Used by handleVictoryInput to intercept input for both level 1 and level 2 completion steps.
         */
        public boolean isCompletionStep() {
            return this == STEP_12_COMPLETE_1 || this == STEP_13_COMPLETE_2
                || this == STEP_L2_6_COMPLETE_1 || this == STEP_L2_7_COMPLETE_2
                || this == STEP_L3_6_CONGRATS || this == STEP_L3_7_DAILY_PROMPT;
        }

        /**
         * Whether this is the first step of a new tutorial level (triggers level load).
         */
        public boolean isNewLevelStep() {
            return this == STEP_L2_1_SPINDICATOR || this == STEP_L3_1_SPINDICATOR;
        }
    }

    private Step currentStep = Step.STEP_1_DISTANCE;

    public Step getCurrentStep() {
        return currentStep;
    }

    public boolean isActive() {
        return currentStep != Step.DONE;
    }

    /**
     * Advance to the next tutorial step.
     */
    public void advance() {
        Gdx.app.log("tutorial", "advance from step " + currentStep);
        currentStep = switch (currentStep) {
            case STEP_1_DISTANCE  -> Step.STEP_2_AIM;
            case STEP_2_AIM       -> Step.STEP_3_INFO;
            case STEP_3_INFO      -> Step.STEP_4_CLUB;
            case STEP_4_CLUB      -> Step.STEP_5_POWER;
            case STEP_5_POWER     -> Step.STEP_6_HIT;
            case STEP_6_HIT       -> Step.STEP_7_WATCH;
            case STEP_7_WATCH     -> Step.STEP_8_PUTTER;
            case STEP_8_PUTTER    -> Step.STEP_9_PROJECT;
            case STEP_9_PROJECT   -> Step.STEP_10_AIM;
            case STEP_10_AIM      -> Step.STEP_11_PUTT;
            case STEP_11_PUTT     -> Step.STEP_12_COMPLETE_1;
            case STEP_12_COMPLETE_1 -> Step.STEP_13_COMPLETE_2;
            case STEP_13_COMPLETE_2    -> Step.STEP_L2_1_SPINDICATOR;  // → level 2
            case STEP_L2_1_SPINDICATOR -> Step.STEP_L2_1B_AIM;
            case STEP_L2_1B_AIM        -> Step.STEP_L2_2_HIT;
            case STEP_L2_2_HIT         -> Step.STEP_L2_3_WATCH;
            case STEP_L2_3_WATCH       -> Step.STEP_L2_4_APPROACH;
            case STEP_L2_4_APPROACH    -> Step.STEP_L2_5_WATCH;
            case STEP_L2_5_WATCH       -> Step.STEP_L2_6_COMPLETE_1;
            case STEP_L2_6_COMPLETE_1  -> Step.STEP_L2_7_COMPLETE_2;
            case STEP_L2_7_COMPLETE_2  -> Step.STEP_L3_1_SPINDICATOR;  // → level 3
            case STEP_L3_1_SPINDICATOR -> Step.STEP_L3_2_WATCH;
            case STEP_L3_2_WATCH       -> Step.STEP_L3_3_LIE;
            case STEP_L3_3_LIE         -> Step.STEP_L3_4_TIP;
            case STEP_L3_4_TIP         -> Step.STEP_L3_4B_WIND_TIP;
            case STEP_L3_4B_WIND_TIP   -> Step.STEP_L3_5_WAIT_HOLE;
            case STEP_L3_5_WAIT_HOLE   -> Step.STEP_L3_6_CONGRATS;
            case STEP_L3_6_CONGRATS    -> Step.STEP_L3_7_DAILY_PROMPT;
            case STEP_L3_7_DAILY_PROMPT -> Step.DONE;
            default -> Step.DONE;
        };
    }
}
