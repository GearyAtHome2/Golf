package org.example.hud;

/** Immutable descriptor for a single menu button — label, locked state, and sparkle. */
public final class MenuButtonDescriptor {
    public final String label;
    public final boolean locked;
    public final boolean sparkle;

    public MenuButtonDescriptor(String label, boolean locked, boolean sparkle) {
        this.label = label;
        this.locked = locked;
        this.sparkle = sparkle;
    }

    public static MenuButtonDescriptor enabled(String label)  { return new MenuButtonDescriptor(label, false, false); }
    public static MenuButtonDescriptor locked(String label)   { return new MenuButtonDescriptor(label, true,  false); }
    public static MenuButtonDescriptor sparkled(String label) { return new MenuButtonDescriptor(label, false, true);  }
}
