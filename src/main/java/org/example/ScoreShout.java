package org.example;

public enum ScoreShout {
    HIO(0, "HOLE IN ONE!!!"),
    ALBATROSS(-3, "ALBATROSS!!"),
    EAGLE(-2, "EAGLE!"),
    BIRDIE(-1, "BIRDIE"),
    PAR(0, "PAR"),
    BOGEY(1, "BOGEY"),
    DOUBLE_BOGEY(2, "DOUBLE BOGEY"),
    TRIPLE_BOGEY(3, "TRIPLE BOGEY"),
    QUAD_BOGEY(4, "QUAD BOGEY..."),
    BAD_LUCK(5, "OOF!");

    public int score;
    public String shout;

    ScoreShout(int score, String shout) {
        this.score = score;
        this.shout = shout;
    }

    public static String getShout(int totalStrokes, int par) {
        if (totalStrokes == 1) return HIO.shout;

        int diff = totalStrokes - par;

        for (ScoreShout s : ScoreShout.values()) {
            if (s != HIO && s.score == diff) {
                return s.shout;
            }
        }

        return diff > 0 ? "STILL COUNTS!" : "GREAT ROUND!";
    }
}