package com.gearygolf.golf.ball;

import com.gearygolf.golf.hud.SwingResult;
import com.gearygolf.golf.terrain.Terrain;

/**
 * Per-terrain hook into the swing result conversion pipeline.
 *
 * Override only the zones that require non-default behaviour.
 *
 * handle* methods:  return true to REPLACE the default zone logic entirely.
 *                   Return false to fall through to the standard fairway behaviour.
 * on*     methods:  always called AFTER the base calculation; use for additive tweaks.
 */
public abstract class TerrainSwingModifier {

    /**
     * Called when LATE_FAT tempo is detected (either fat or chunk zone).
     * q = tempoQuality (0–1, higher = better timing).
     * Return true if this modifier handled the zone; false to use default fat/chunk curves.
     */
    public boolean handleLateFat(SwingResult s, MinigameResult r, float q) { return false; }

    /**
     * Called after the default LATE_FAT curves have run (only when handleLateFat returned false).
     * Use for additive / softening tweaks on top of the base fat/chunk result.
     */
    public void onAfterLateFat(SwingResult s, MinigameResult r, float q) {}

    /**
     * Called when EARLY_THIN tempo is detected (either thin or top zone).
     * Return true if this modifier handled the zone; false to use default thin/top curves.
     */
    public boolean handleEarlyThin(SwingResult s, MinigameResult r, float q) { return false; }

    /**
     * Called after the PERFECT tempo base calculation (r.powerMod already set from compositeQuality).
     * Use for additive tweaks — loft, spin, power adjustments on top of the base result.
     * compositeQuality = tempoQuality × contactFactor.
     */
    public void onPerfect(SwingResult s, MinigameResult r, float compositeQuality) {}

    // ── Registry ──────────────────────────────────────────────────────────────

    private static final TerrainSwingModifier NO_OP = new TerrainSwingModifier() {};

    /**
     * Applies only tempoSeverity-based softening after the default fat/chunk curves.
     * Used for terrains (FRINGE, GREEN) that need no special behaviour beyond the
     * general severity scaling already defined on their TerrainType data.
     */
    private static final class SeverityModifier extends TerrainSwingModifier {
        private final float severity;
        SeverityModifier(float severity) { this.severity = severity; }

        @Override
        public void onAfterLateFat(SwingResult s, MinigameResult r, float q) {
            r.powerMod      = 1f - (1f - r.powerMod)      * severity;
            r.loftDeltaDeg *=                               severity;
            r.tempoSpinMult = 1f - (1f - r.tempoSpinMult) * severity;
        }
    }

    /** Returns the modifier for the given terrain, or a no-op for unregistered types. */
    public static TerrainSwingModifier forTerrain(Terrain.TerrainType type) {
        return switch (type) {
            case TEE        -> TeeSwingModifier.INSTANCE;
            case STONE      -> StoneSwingModifier.INSTANCE;
            case SAND       -> SandSwingModifier.INSTANCE;
            case ROUGH      -> RoughSwingModifier.INSTANCE;
            case DEEP_ROUGH -> DeepRoughSwingModifier.INSTANCE;
            case MUD        -> MudSwingModifier.INSTANCE;
            case FRINGE     -> new SeverityModifier(Terrain.TerrainType.FRINGE.tempoSeverity); // 0.85
            case GREEN      -> new SeverityModifier(Terrain.TerrainType.GREEN.tempoSeverity);  // 0.80
            default         -> NO_OP;
        };
    }
}
