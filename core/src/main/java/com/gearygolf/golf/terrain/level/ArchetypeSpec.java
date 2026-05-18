package com.gearygolf.golf.terrain.level;

import com.gearygolf.golf.terrain.objects.Tree.TreeScheme;

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

    // ── Deep rough ────────────────────────────────────────────────────────────
    /** Tile distance from FAIRWAY/GREEN beyond which ROUGH becomes DEEP_ROUGH. -1 = disabled. */
    public float deepRoughThreshold = 10f;
    /** Multiplier on base patch coverage (0.15). 1.0 = default; >1 = more deep rough. */
    public float roughDeepCover = 1.0f;
    /** Height above water level up to which ROUGH/DEEP_ROUGH converts to MUD. -99 = disabled. */
    public float mudHeight = 0.8f;

    // ── Distance & Par ────────────────────────────────────────────────────────
    /** Hole length in yards: distanceMin + random * (distanceMax - distanceMin). */
    public int distanceMin = 500, distanceMax = 500;
    /** Override terrain grid width. 0 = default (160).  -1 = match hole distance. */
    public int mapWidthOverride = 0;
    /** Fixed par (3, 4, or 5).  0 = variable — use per-par distance ranges below. */
    public int parFixed = 0;

    // Per-par distance ranges for variable-par archetypes (parFixed == 0).
    // Each [min, max] pair defines the yard range for that par; both 0 = par unavailable.
    // Gaps between ranges are intentional — edit these values to tune feel per archetype.
    // ─────────────────────────────────────────────────────────────────────────────────────
    //                       Par 3 range    gap    Par 4 range    gap    Par 5 range
    // STANDARD_LINKS:       280–400        ~49    450–690        ~39    730–950
    // ISLAND_COAST:         —                     500–570        ~49    620–700
    // CRATER_FIELDS:        —                     500–600        ~49    650–750
    // BUSH_WORLD:           —                     450–530        ~49    580–680
    // MONOLITH_PLAINS:      —                     600–660        ~59    720–800
    // MOGUL_HIGHLANDS:      —                     600–660        ~59    720–750
    // STONE_RUN:            —                     650–710        ~49    760–1200
    // ─────────────────────────────────────────────────────────────────────────────────────
    public int par3DistMin = 0, par3DistMax = 0;
    public int par4DistMin = 0, par4DistMax = 0;
    public int par5DistMin = 0, par5DistMax = 0;

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
    public ArchetypeSpec deepRough(float threshold)      { deepRoughThreshold = threshold; return this; }
    public ArchetypeSpec roughDeepCover(float c)         { roughDeepCover = c;             return this; }
    public ArchetypeSpec mudHeight(float h)              { mudHeight = h;                  return this; }

    public ArchetypeSpec distance(int min, int max) { distanceMin = min; distanceMax = max; return this; }
    public ArchetypeSpec mapWidth(int w)            { mapWidthOverride = w; return this; }

    /** Fixed par value (3, 4, or 5).  Pair with distance(min, max). */
    public ArchetypeSpec par(int fixed)             { parFixed = fixed; return this; }
    /** Variable par 4/5: par chosen uniformly, then distance sampled from the matching range. */
    public ArchetypeSpec par45(int p4min, int p4max, int p5min, int p5max) {
        parFixed = 0;
        par4DistMin = p4min; par4DistMax = p4max;
        par5DistMin = p5min; par5DistMax = p5max;
        return this;
    }

    public ArchetypeSpec par34(int p3min, int p3max, int p4min, int p4max) {
        parFixed = 0;
        par3DistMin = p3min; par3DistMax = p3max;
        par4DistMin = p4min; par4DistMax = p4max;
        return this;
    }
    /** Variable par 3/4/5: par chosen uniformly, then distance sampled from the matching range. */
    public ArchetypeSpec par345(int p3min, int p3max, int p4min, int p4max, int p5min, int p5max) {
        parFixed = 0;
        par3DistMin = p3min; par3DistMax = p3max;
        par4DistMin = p4min; par4DistMax = p4max;
        par5DistMin = p5min; par5DistMax = p5max;
        return this;
    }

    public ArchetypeSpec difficulty(float d)        { baseDifficultyIndex = d; return this; }
    public ArchetypeSpec distancePenalty()          { distancePenalty = true; return this; }

    public ArchetypeSpec isActive(boolean d)        { isActive = d; return this; }
}
