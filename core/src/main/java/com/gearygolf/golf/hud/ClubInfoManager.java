package com.gearygolf.golf.hud;

import com.gearygolf.golf.Club;

public class ClubInfoManager {

    public static String getClubDescription(Club club) {
        return switch (club) {
            case DRIVER ->
                    "The big stick. Massive distance but highly unforgiving. Best reserved for the tee box where its bonus shines.";
            case WOOD_3 ->
                    "A versatile long-range option. Ideal for reaching par 5s in two or safe tee shots on narrow fairways.";
            case WOOD_5 -> "Much more predictable and consistent than the 3-Wood, with a higher loft and lower power.";
            case IRON_2 -> "The lowest, longest iron with excellent spin and a high skill ceiling";
            case HYBRID_3 -> "A rescue club designed for versatility. Easy to launch, considering the distance.";
            case IRON_3 -> "A challenging club that can hit a long, low, piercing flight path under the wind";
            case IRON_4 -> "Enough power to get good distance with enough spin to hold the green - ideal for the longest par-3's";
            case IRON_5 ->
                    "The bridge between power and precision. Good for long approaches where you still need a bit of stop.";
            case IRON_6 -> "Geary's favourite club. Reliable for finding the green from a decent distance out.";
            case IRON_7 -> "High enough to clear obstacles, long enough to cover significant ground.";
            case IRON_8 -> "A short-iron that balances vertical angle with forward roll for good control.";
            case IRON_9 ->
                    "Designed to attack the pin. The steep angle of descent helps the ball settle quickly near the target.";
            case PWEDGE -> "The primary scoring wedge. Perfect for full shots into the green with high backspin.";
            case GWEDGE ->
                    "Fills the gap between your pitching and sand wedges. Excellent for controlled, short-range pitches onto the green.";
            case SWEDGE -> "Great for escaping deep pits and bunkers.";
            case LWEDGE -> "Floppy boy. Extremely high loft, just watch out for the wind.";
            case PUTTER -> "Precision engineered for the green. No loft, no spin, just a pure roll toward the cup.";
            default -> "A standard utility club for various course conditions.";
        };
    }

    public static String getCarryDistanceInfo(Club club) {
        return switch (club) {
            case DRIVER -> "360 yds";
            case WOOD_3 -> "340 yds";
            case WOOD_5 -> "305 yds";
            case HYBRID_3 -> "275 yds";
            case IRON_2 -> "300 yds";
            case IRON_3 -> "285 yds";
            case IRON_4 -> "268 yds";
            case IRON_5 -> "250 yds";
            case IRON_6 -> "236 yds";
            case IRON_7 -> "215 yds";
            case IRON_8 -> "190 yds";
            case IRON_9 -> "165 yds";
            case PWEDGE -> "135 yds";
            case GWEDGE -> "115 yds";
            case SWEDGE -> "92 yds";
            case LWEDGE -> "72 yds";
            case PUTTER -> "20 yds";
            default -> "Typical Distance: --";
        };
    }

    public static String getPowerInfo(Club club) {
        return "Power: " + club.powerMult;
    }

    public static String getLoftInfo(Club club) {
        return "Loft: " + club.loft;
    }
}