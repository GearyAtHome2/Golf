package com.gearygolf.golf.terrain.level;

import com.gearygolf.golf.terrain.objects.Tree.TreeScheme;

/**
 * Defines all generation parameters for one course archetype.
 *
 * Edit the inline spec in each LevelData.Archetype constant to tune or experiment.
 * Range fields: actual = base + random * var.  Set var to 0 for a fixed value.
 */
public class ArchetypeSpec {

    // ── Feature generator selection ──────────────────────────────────────────
    /**
     * Identifies the specialist generator (if any) that runs during post-processing.
     * NONE = standard pipeline only.  Add a new value here when adding a new archetype
     * that requires a dedicated generator; wire it up in ClassicGenerator.applyPostProcessing.
     */
    public enum TerrainFeature {
        NONE,
        BADLANDS,
        TABLE_MOUNTAIN,
        GOLDSMITH_BOWL,
        CENOTES,
        BEACH_BLUFFS,
        WHISTLING_ISLES,
        CRATERS,
        VINEYARDS,
        CLIPPERTON_ROCK,
        OASIS_DUNES,
        FOREST_EDGE,
        STONE_RUN,
        CYPRESS_BAYOU,
        DOGLEG_RIVER
    }

    /** Selects the tree-placement algorithm used by ClassicGenerator. */
    public enum TreeStrategy { RANDOM, VINEYARD, OASIS }

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
    /**
     * Tee X position as a fraction of SIZE_X. Default 0.5 = centre.
     * Used by mapStandardFeatures (tile placement) and finalizePositionsAndTrees (world position).
     */
    public float teeXFraction = 0.5f;
    /**
     * Green X position as a fraction of SIZE_X. -1 = random (default).
     * When >= 0 the green is placed deterministically instead of randomly offset from centre.
     */
    public float greenXFraction = -1f;
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

    // ── Archetype feature generator ───────────────────────────────────────────
    /** Specialist generator to run during post-processing. NONE = standard pipeline only. */
    public TerrainFeature terrainFeature = TerrainFeature.NONE;

    /**
     * Water level applied at the start of post-processing.
     * Float.NaN = no override; LevelDataGenerator's default (typically 0) is kept.
     * The SUNKEN_FAIRWAY algorithm also sets water level later (computed from heights);
     * that override is independent and will still apply.
     */
    public float waterLevelOverride = Float.NaN;

    // ── Crater generation (terrainFeature == CRATERS) ─────────────────────────
    /** Craters per SIZE_Z unit. craterCount = (int)(SIZE_Z * craterDensityPerLength) + rng.nextInt(craterCountVariance). */
    public float craterDensityPerLength = 2.5f / 50f;
    public int   craterCountVariance    = 15;

    // ── Wave angle orientation ────────────────────────────────────────────────
    /**
     * If true, wave angles are biased to near-perpendicular (producing vineyard-style
     * terrain rows). Default false = fully random wave angles.
     */
    public boolean cardinalWaveAngles = false;

    // ── Tree placement ────────────────────────────────────────────────────────
    /** Selects which tree-placement algorithm ClassicGenerator uses for this archetype. */
    public TreeStrategy treeStrategy = TreeStrategy.RANDOM;

    // Options for the RANDOM tree placement strategy:
    /** Z clearance zone around the tee where trees are not placed. */
    public int teeTreeBufferZ = 40;
    /** X clearance zone around the tee where trees are not placed. */
    public int teeTreeBufferX = 30;
    /** If true, MUD tiles are valid terrain for tree placement (e.g. mangroves). */
    public boolean allowMudTrees = false;
    /**
     * Trees must be at height >= (waterLevel + treeWaterWading).
     * Positive = trees stay above water (default 0.1).
     * Negative = trees may stand in shallow water (e.g. -0.5 for bayou mangroves).
     */
    public float treeWaterWading = 0.1f;
    /** If true, trees may only be placed on DEEP_ROUGH tiles (e.g. TABLE_MOUNTAIN mesa top). */
    public boolean deepRoughTreesOnly = false;
    /** If true, tree density falls off with distance from the green (BushWorld effect). */
    public boolean bushDensityFalloff = false;
    /**
     * If true, ClassicGenerator will consult the active TerrainFeature generator's gap-cell
     * set and exclude those cells from tree placement (used by CYPRESS_BAYOU water crossings).
     */
    public boolean excludeFeatureGapCells = false;

    // ── Height generation overrides ───────────────────────────────────────────
    /**
     * If true, uses a steep sigmoid base-elevation curve (cliff terrain) instead of the
     * standard power curve. Also causes trees to scale height from tee/green elevation
     * delta rather than from the treeH spec field.
     */
    public boolean useCliffElevation = false;
    /** If true, uses the mogul-specific noise function and pre-builds a distance cache. */
    public boolean useMogulNoise = false;
    /**
     * If true, the coastline generator wraps the heightmap in an island shape and the
     * fairway generator treats the map as water-surrounded (ISLAND_COAST).
     */
    public boolean islandCoastline = false;
    /**
     * Fraction of SIZE_Z used as the green-area radius in the heightmap taper calculation.
     * Default 0.22.  OASIS_DUNES uses 0.44 to push the surrounding dunes further back.
     */
    public float greenHmapRadiusFraction = 0.22f;

    // ── Pipeline behaviour ────────────────────────────────────────────────────
    /** If true, the fairway generation pass is skipped (archetype provides its own layout). */
    public boolean skipFairway = false;
    /**
     * If true, ROUGH tiles at the green border are also included in the green-border
     * smoothing pass (ISLAND_COAST blends rough into the island shoreline).
     */
    public boolean smoothRoughAtGreenBorder = false;
    /** If true, monoliths are scattered across the map after post-processing. */
    public boolean spawnMonoliths = false;
    /**
     * Coverage fraction for the sand-scatter pass (0 = disabled).
     * When > 0, roughly this fraction of ROUGH tiles are converted to SAND.
     * Used by BADLANDS (0.22).
     */
    public float sandPassCoverage = 0f;
    /**
     * If true, any remaining ROUGH tiles are converted to STONE after the sand pass.
     * Gives Badlands maps their bare cracked-rock appearance.
     */
    public boolean roughToStone = false;

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
    public ArchetypeSpec teeXFraction(float f)      { teeXFraction = f;    return this; }
    public ArchetypeSpec greenXFraction(float f)    { greenXFraction = f;  return this; }

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

    // ── Archetype feature builder methods ─────────────────────────────────────

    public ArchetypeSpec feature(TerrainFeature f)  { terrainFeature = f;           return this; }
    public ArchetypeSpec waterLevel(float w)        { waterLevelOverride = w;        return this; }
    public ArchetypeSpec craterDensity(float perLength, int variance) {
        craterDensityPerLength = perLength; craterCountVariance = variance; return this;
    }
    public ArchetypeSpec cardinalWaveAngles()       { cardinalWaveAngles = true;     return this; }
    public ArchetypeSpec treeStrategy(TreeStrategy s) { treeStrategy = s;            return this; }
    public ArchetypeSpec teeTreeBuffer(int bufZ, int bufX) {
        teeTreeBufferZ = bufZ; teeTreeBufferX = bufX; return this;
    }
    public ArchetypeSpec allowMudTrees()            { allowMudTrees = true;          return this; }
    public ArchetypeSpec treeWaterWading(float w)   { treeWaterWading = w;           return this; }
    public ArchetypeSpec deepRoughTreesOnly()       { deepRoughTreesOnly = true;     return this; }
    public ArchetypeSpec bushDensityFalloff()       { bushDensityFalloff = true;     return this; }
    public ArchetypeSpec excludeFeatureGapCells()   { excludeFeatureGapCells = true; return this; }
    public ArchetypeSpec useCliffElevation()        { useCliffElevation = true;      return this; }
    public ArchetypeSpec useMogulNoise()            { useMogulNoise = true;          return this; }
    public ArchetypeSpec islandCoastline()          { islandCoastline = true;        return this; }
    public ArchetypeSpec smoothRoughAtGreenBorder() { smoothRoughAtGreenBorder = true; return this; }
    public ArchetypeSpec greenHmapRadius(float f)   { greenHmapRadiusFraction = f;   return this; }
    public ArchetypeSpec skipFairway()              { skipFairway = true;            return this; }
    public ArchetypeSpec spawnMonoliths()           { spawnMonoliths = true;         return this; }
    public ArchetypeSpec sandPass(float coverage)   { sandPassCoverage = coverage;   return this; }
    public ArchetypeSpec roughToStone()             { roughToStone = true;           return this; }
}
