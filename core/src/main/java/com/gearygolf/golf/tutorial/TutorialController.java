package com.gearygolf.golf.tutorial;

import com.badlogic.gdx.Gdx;

public class TutorialController {

    public enum Step {
        STEP_1_DISTANCE,    // tap DISTANCE button
        STEP_2_AIM,         // rotate camera to account for wind
        STEP_3_INFO,        // tap INFO button
        STEP_4_CLUB,        // switch to 6 Iron
        STEP_5_POWER,       // tap MAX POWER
        STEP_6_HIT,         // hit during minigame
        STEP_7_WATCH,       // silent — wait for ball to land on green
        STEP_8_PUTTER,      // ball on green — prompt putter
        STEP_9_PROJECT,     // ball on green — prompt projection
        STEP_10_AIM,        // tell user to aim at hole and putt (no screen dim — border-only highlight)
        STEP_11_PUTT,       // silent — wait for ball to go in
        STEP_12_COMPLETE_1, // post-hole info box 1 (advance via Next Map tap)
        STEP_13_COMPLETE_2, // post-hole info box 2 (advance via Next Map tap)
        DONE;

        /**
         * Whether to show an overlay this step.
         */
        public boolean isOverlayVisible() {
            return this != STEP_7_WATCH && this != STEP_11_PUTT && this != DONE;
        }

        /**
         * Whether to block non-target inputs this step (mobile: button Touchable; desktop: handled by early return).
         */
        public boolean isBlockingInput() {
            return this == STEP_1_DISTANCE || this == STEP_2_AIM || this == STEP_3_INFO
                    || this == STEP_4_CLUB || this == STEP_5_POWER || this == STEP_6_HIT
                    || this == STEP_8_PUTTER || this == STEP_9_PROJECT || this == STEP_10_AIM;
        }

        /**
         * Whether to use the full spotlight (dim + button cutout) style.
         * False = hint bar only (no full dim), suitable when the player needs to see the course.
         * Note: STEP_10_AIM is excluded — it uses a border-only highlight with no dim.
         */
        public boolean isSpotlightStyle() {
            return this == STEP_1_DISTANCE || this == STEP_3_INFO
                    || this == STEP_4_CLUB || this == STEP_5_POWER || this == STEP_6_HIT
                    || this == STEP_8_PUTTER || this == STEP_9_PROJECT;
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
            case STEP_1_DISTANCE -> Step.STEP_2_AIM;
            case STEP_2_AIM -> Step.STEP_3_INFO;
            case STEP_3_INFO -> Step.STEP_4_CLUB;
            case STEP_4_CLUB -> Step.STEP_5_POWER;
            case STEP_5_POWER -> Step.STEP_6_HIT;
            case STEP_6_HIT -> Step.STEP_7_WATCH;
            case STEP_7_WATCH -> Step.STEP_8_PUTTER;
            case STEP_8_PUTTER -> Step.STEP_9_PROJECT;
            case STEP_9_PROJECT -> Step.STEP_10_AIM;
            case STEP_10_AIM -> Step.STEP_11_PUTT;
            case STEP_11_PUTT -> Step.STEP_12_COMPLETE_1;
            case STEP_12_COMPLETE_1 -> Step.STEP_13_COMPLETE_2;
            case STEP_13_COMPLETE_2 -> Step.DONE;
            default -> Step.DONE;
        };
    }
}
