package org.example.hud;

import org.example.Club;

public class ClubInfoManager {

    public static String getClubDescription(Club club) {
        return switch (club) {
            case DRIVER ->
                    "The big stick. Massive distance but highly unforgiving. Best reserved for the tee box where its bonus shines.";
            case WOOD_3 ->
                    "A versatile long-range option. Ideal for reaching par 5s in two or safe tee shots on narrow fairways.";
            case WOOD_5 -> "Much more predictable and consistent than the 3-Wood, with a higher loft and lower power.";
            case IRON_2 ->
                    "The specialist's choice. Hits a low, piercing 'stinger' that ignores the wind but requires a perfect strike.";
            case HYBRID_3 ->
                    "A rescue club designed for versatility. Easier to launch than a long iron, even when buried in the rough.";
            case IRON_5 ->
                    "The bridge between power and precision. Good for long approaches where you still need a bit of stop.";
            case IRON_6 ->
                    "A standard mid-iron. Reliable for finding the center of the green from a decent distance out.";
            case IRON_7 ->
                    "The go-to club for scoring. High enough to clear obstacles, yet long enough to cover significant ground.";
            case IRON_8 ->
                    "A short-iron that begins to prioritize vertical descent over forward roll for better control.";
            case IRON_9 ->
                    "Designed to attack the pin. The steep angle of descent helps the ball settle quickly near the target.";
            case PWEDGE ->
                    "The primary scoring wedge. Perfect for full shots into the green with high backspin and accuracy.";
            case GWEDGE ->
                    "Fills the gap between your pitching and sand wedges. Excellent for controlled, mid-range pitch shots.";
            case SWEDGE ->
                    "Features a heavy sole and high loft. Essential for escaping bunkers and thick grass hazards.";
            case LWEDGE ->
                    "The ultimate 'flop' club. High risk, high reward shots that stop dead, but require extreme precision.";
            case PUTTER -> "Precision engineered for the green. No loft, no spin—just a pure roll toward the cup.";
            default -> "A standard utility club for various course conditions.";
        };
    }

    public static String getCarryDistanceInfo(Club club) {
        return switch (club) {
            case DRIVER -> "275-350 yds";
            case IRON_2, WOOD_3 -> "260-330 yds";
            case WOOD_5 -> "240-270 yds";
            case HYBRID_3 -> "230-260 yds";
            case IRON_5 -> "210-240 yds";
            case IRON_6 -> "180-220 yds";
            case IRON_7 -> "160-180 yds";
            case IRON_8 -> "150-160 yds";
            case IRON_9 -> "130-140 yds";
            case PWEDGE -> "100 yds";
            case GWEDGE -> "70 yds";
            case SWEDGE -> "50 yds";
            case LWEDGE -> "30 yds";
            case PUTTER -> "20 yds";
            default -> "Typical Distance: --";
        };
    }

    public static String getPowerInfo(Club club) {
        return "Power: " + club.powerMult;
    }

    public static String getLoftInfo(Club club) {
        return "Loft: " + club.loft + "°";
    }
}