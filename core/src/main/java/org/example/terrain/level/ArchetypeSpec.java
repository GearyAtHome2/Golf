package org.example.terrain.level;

import org.example.terrain.objects.Tree.TreeScheme;

/**
 * Defines all generation parameters for one course archetype.
 *
 * Edit the inline spec in each LevelData.Archetype constant to tune or experiment.
 * Range fields: actual = base + random * var.  Set var to 0 for a fixed value.
 */
public class ArchetypeSpec {

    // ── Visuals ──────────────────────────────────────────────────────────────
    /** Primary terrain algorithm. Swap this to test different shapes. */
    public LevelData.TerrainAlgorithm algo    = LevelData.TerrainAlgorithm.MULTI_WAVE;
    /** If set, the algorithm is chosen randomly between algo and algoAlt each generation. */
    public LevelData.TerrainAlgorithm algoAlt = null;

    /** Primary tree scheme. */
    public TreeScheme treeScheme              = TreeScheme.OAK;
    /** If set, the tree scheme is chosen randomly between treeScheme and treeSchemeAlt. */
    public TreeScheme treeSchemeAlt           = null;

    // ── Heights ───────────────────────────────────────────────────────────────
    public float teeH = 10f,   teeHVar = 0f;   // tee elevation
    public float greenH = 10f, greenHVar = 0f;  // green elevation

    // ── Wind ──────────────────────────────────────────────────────────────────
    public float windMin = 0f, windMax = 15f;

    // ── Trees ─────────────────────────────────────────────────────────────────
    public float treeH = 7f,   treeHVar = 0f;   // base height (±20% also applied globally)
    public float foliageR = 2.5f, trunkR = 0.4f;
    public float treeDensity = 0.15f;

    // ── Terrain shape ─────────────────────────────────────────────────────────
    public float hillFreq = 0.035f, maxH = 7f;
    public float fairwayWiggle = 0.3f, fairwayWiggleVar = 0f;
    public float islands  = 0.1f;   // rough island frequency along fairway
    public float cohesion = 0.5f;
    public float maxFairwayWidth = 45f, minFairwayWidth = 0f;
    /** Explicit undulation value. -1 = random per generation (0.2–0.6). */
    public float undulation = -1f;

    // ── Bunkers ───────────────────────────────────────────────────────────────
    public int   bunkerMin = 0, bunkerMax = 0;
    public float bunkerDepth = 2f, bunkerDepthVar = 0f;

    // ── Green size ────────────────────────────────────────────────────────────
    /** Base green tile radius. -1 = use the default (26f). */
    public float greenRadiusMin = -1f, greenRadiusMax = -1f;

    // ── Distance & Par ────────────────────────────────────────────────────────
    /** Hole length in yards: distanceMin + random * (distanceMax - distanceMin). */
    public int distanceMin = 500, distanceMax = 500;
    /** Override terrain grid width. 0 = default (160).  -1 = match hole distance. */
    public int mapWidthOverride = 0;
    /**
     * parFixed > 0  → use that par directly.
     * parFixed == 0 → derive from distance thresholds:
     *   par3 if dist < parThreshold34  (set to 0 to skip par3)
     *   par4 if dist < parThreshold45  (set to 0 to always use par5 when parFixed==0)
     *   par5 otherwise
     */
    public int parFixed = 0, parThreshold34 = 0, parThreshold45 = 0;

    // ── Difficulty ────────────────────────────────────────────────────────────
    public float baseDifficultyIndex = 8f;
    /** If true, longer holes are penalised in the shotIndex difficulty calculation. */
    public boolean distancePenalty  = false;

    public boolean isActive = true;

    // ── Fluent builder methods ────────────────────────────────────────────────

    public ArchetypeSpec algo(LevelData.TerrainAlgorithm a)    { algo = a;          return this; }
    public ArchetypeSpec algoAlt(LevelData.TerrainAlgorithm a) { algoAlt = a;       return this; }
    public ArchetypeSpec treeScheme(TreeScheme s)               { treeScheme = s;    return this; }
    public ArchetypeSpec treeSchemeAlt(TreeScheme s)            { treeSchemeAlt = s; return this; }

    public ArchetypeSpec teeH(float base, float var)   { teeH = base;   teeHVar = var;   return this; }
    public ArchetypeSpec greenH(float base, float var) { greenH = base; greenHVar = var; return this; }
    public ArchetypeSpec wind(float min, float max)    { windMin = min; windMax = max;   return this; }

    public ArchetypeSpec treeH(float base, float var)  { treeH = base;  treeHVar = var;  return this; }
    public ArchetypeSpec trees(float density, float foliage, float trunk) {
        treeDensity = density; foliageR = foliage; trunkR = trunk; return this;
    }

    public ArchetypeSpec terrain(float freq, float maxHeight) {
        hillFreq = freq; maxH = maxHeight; return this;
    }
    public ArchetypeSpec fairway(float max, float min)    { maxFairwayWidth = max; minFairwayWidth = min; return this; }
    public ArchetypeSpec wiggle(float base, float var)    { fairwayWiggle = base; fairwayWiggleVar = var; return this; }
    public ArchetypeSpec islands(float i)                 { islands = i;     return this; }
    public ArchetypeSpec cohesion(float c)                { cohesion = c;    return this; }
    public ArchetypeSpec undulation(float u)              { undulation = u;  return this; }

    public ArchetypeSpec bunkers(int min, int max, float depth) {
        bunkerMin = min; bunkerMax = max; bunkerDepth = depth; return this;
    }
    public ArchetypeSpec bunkers(int min, int max, float depth, float dVar) {
        bunkerMin = min; bunkerMax = max; bunkerDepth = depth; bunkerDepthVar = dVar; return this;
    }

    public ArchetypeSpec greenSize(float min, float max) { greenRadiusMin = min; greenRadiusMax = max; return this; }

    public ArchetypeSpec distance(int min, int max) { distanceMin = min; distanceMax = max; return this; }
    public ArchetypeSpec mapWidth(int w)            { mapWidthOverride = w; return this; }

    /** Fixed par value (3, 4, or 5). */
    public ArchetypeSpec par(int fixed)             { parFixed = fixed; return this; }
    /** Par4 if distance < threshold, else par5. */
    public ArchetypeSpec par45(int threshold)       { parFixed = 0; parThreshold45 = threshold; return this; }
    /** Par3 if dist < t34, par4 if dist < t45, else par5. */
    public ArchetypeSpec par345(int t34, int t45)   { parFixed = 0; parThreshold34 = t34; parThreshold45 = t45; return this; }

    public ArchetypeSpec difficulty(float d)        { baseDifficultyIndex = d; return this; }
    public ArchetypeSpec distancePenalty()          { distancePenalty = true; return this; }

    public ArchetypeSpec isActive(boolean d)        { isActive = d; return this; }
}
