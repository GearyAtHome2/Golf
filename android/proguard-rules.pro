# LibGDX
-keep class com.badlogic.gdx.** { *; }

# Firebase / Google
-keep class com.google.firebase.** { *; }
-keep class com.google.android.gms.** { *; }

# Game auth and score classes (used via reflection or serialisation)
-keep class com.gearygolf.golf.auth.** { *; }
-keep class com.gearygolf.golf.scoreBoard.** { *; }

# Enums
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Java 21 switch expressions reference MatchException which R8 can't find on older minSdk
-dontwarn java.lang.MatchException

# Strip verbose logging in release
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
}
