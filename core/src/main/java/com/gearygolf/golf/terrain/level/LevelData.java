package com.gearygolf.golf.terrain.level;

import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.gearygolf.golf.terrain.features.TableMountainGenerator;
import com.gearygolf.golf.terrain.objects.Tree;
import com.gearygolf.golf.terrain.objects.Tree.TreeScheme;

public class LevelData {

    // TerrainAlgorithm must be declared before Archetype so its constants are
    // initialised first (Archetype constructors reference them).
    public enum TerrainAlgorithm {
        SMOOTH_SINE,
        MULTI_WAVE,
        TERRACED,
        MOUNDS,
        RAISED_FAIRWAY,
        SUNKEN_FAIRWAY,
        CRAGGY_RIDGES,
        DUNES,
        ROLLING_DUNES,
        PLATEAU
    }

    /**
     * Each archetype carries a self-describing {@link ArchetypeSpec} that controls
     * everything the generator needs. To tune a hole type, edit the spec inline.
     * To test a different terrain shape, change the .algo() call — e.g. swap
     * ROLLING_DUNES for PLATEAU on DUNES_VALLEY or MESA_RIDGE to compare.
     * <p>
     * 18 archetypes → one of each per 18-hole course (no random fill needed).
     */
    public enum Archetype {

        // ── Standard / easy ───────────────────────────────────────────────────

        STANDARD_LINKS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .difficulty(8f)
                .wind(1.5f, 10f)
                .wiggle(0.05f, 0.15f).islands(0.25f).fairway(49f, 0f).cohesion(1.3f)
                .bunkers(2, 5, 2.3f)
                .par345(260, 320, 450, 690, 730, 950)
        ),

        RAZORBACK_RIDGE(new ArchetypeSpec()
                .algo(TerrainAlgorithm.RAISED_FAIRWAY)
                .treeScheme(TreeScheme.CYPRESS)
                .difficulty(1f)
                .teeH(0f, 0f).greenH(18f, 8f)
                .wind(5.5f, 10.5f)
                .treeH(12f, 0f).trees(0.18f, 5f, 0.8f)
                .terrain(0.02f, 5f)
                .fairway(51f, 0f).undulation(1.2f).wiggle(0.22f, 0.08f).cohesion(0.75f)
                .distance(470, 610)
                .par(5)
                .greenSize(31f, 34f)
        ),

        WHISTLING_ISLES(new ArchetypeSpec()
                .algo(TerrainAlgorithm.CRAGGY_RIDGES)
                .treeScheme(TreeScheme.OAK)
                .roughDeepCover(1.3f)
                .mudHeight(-99f)
                .difficulty(1f)
                .teeH(0.2f, 0f).greenH(1.0f, 0f)
                .wind(7.5f, 12f)
                .treeH(8f, 2f).trees(0.98f, 2.7f, 0.4f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(1.4f).wiggle(0.1f, 0.1f).cohesion(1.0f)
                .distance(520, 720)
                .par(5)
                .greenSize(28f, 30f)
                .feature(ArchetypeSpec.TerrainFeature.WHISTLING_ISLES).waterLevel(0f)
        ),

        // ── Mid-range ─────────────────────────────────────────────────────────

        ISLAND_COAST(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.BIRCH)
                .mudHeight(-99f)
                .difficulty(3f)
                .teeH(15f, 10f).greenH(15f, 5f)
                .wind(6f, 12f)
                .trees(0.03f, 2.5f, 0.4f).terrain(0.02f, 15f)
                .fairway(55f, 35f).islands(0f).wiggle(0.25f, 0.06f).cohesion(0.9f)
                .par45(500, 570, 630, 730)
                .islandCoastline().smoothRoughAtGreenBorder()
        ),

        CLIFFSIDE_BLUFF(new ArchetypeSpec()
                .algo(TerrainAlgorithm.TERRACED).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.FIR)
                .roughDeepCover(1.6f)
                .difficulty(2f).distancePenalty()
                .teeH(50f, 15f).greenH(5f, 10f)
                .wind(5f, 10f)
                .trees(0.25f, 8f, 1.2f).terrain(0.012f, 10f)
                .fairway(40f, 0f).islands(0.05f).wiggle(0.20f, 0.1f).cohesion(0.9f)
                .bunkers(1, 2, 0.8f)
                .distance(370, 450)
                .par(4)
                .useCliffElevation()
        ),

        REDWOOD_FOREST(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.REDWOOD)
                .roughDeepCover(1.6f)
                .difficulty(3f).distancePenalty()
                .teeH(5f, 5f).greenH(5f, 5f)
                .wind(0f, 3.5f)
                .treeH(45f, 0f).trees(0.26f, 10f, 1.8f)
                .terrain(0.025f, 5f)
                .fairway(45f, 25f).islands(0f).wiggle(0.06f, 0.02f).cohesion(0.95f)
                .distance(550, 750)
                .par(5)
        ),

        CRATER_FIELDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE)
                .treeScheme(TreeScheme.DEAD_GRAY)
                .mudHeight(-99f)
                .difficulty(3f)
                .teeH(8f, 0f).greenH(12f, 0f)
                .wind(3.5f, 12f)
                .treeH(8f, 0f).trees(0.10f, 3f, 0.6f)
                .terrain(0.06f, 12f)
                .fairway(50f, 0f).undulation(0.28f).wiggle(0.1f, 0.08f).islands(0.4f).cohesion(0.9f)
                .par45(500, 600, 690, 810)
                .feature(ArchetypeSpec.TerrainFeature.CRATERS).waterLevel(-10f)
        ),

        PLUNGE_CENOTES(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE)
                .treeScheme(TreeScheme.BIRCH)
                .difficulty(3f)
                .teeH(5f, 5f).greenH(0f, 0f)
                .wind(4f, 8f)
                .treeH(4f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.02f, 4f)
                .fairway(35f, 0f).wiggle(0.1f, 0.1f)
                .distance(480, 580)
                .par(4)
                .feature(ArchetypeSpec.TerrainFeature.CENOTES).waterLevel(-8f)
        ),

        ROUGH_HOUGH_BLUFFS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .roughDeepCover(1.4f)
                .difficulty(2f)
                .teeH(17f, 2f).greenH(13f, 3f)
                .wind(5.5f, 9.5f)
                .treeH(6f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(0.4f).wiggle(0.1f, 0.1f)
                .distance(400, 500)
                .par(4)
                .feature(ArchetypeSpec.TerrainFeature.BEACH_BLUFFS).waterLevel(-1f)
        ),

        // ── Challenging ───────────────────────────────────────────────────────

        BUSH_WORLD(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .roughDeepCover(2.0f)
                .difficulty(3f)
                .wind(1.5f, 10f)
                .treeH(6f, 0f).trees(0.82f, 3f, 0.5f)
                .terrain(0.045f, 7f)
                .fairway(35f, 0f).wiggle(0.4f, 0.3f).islands(0.7f).cohesion(0.42f)
                .bunkers(1, 1, 2.2f)
                .par45(580, 680, 750, 860)
                .bushDensityFalloff()
        ),

        SHADOW_CANYON(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SUNKEN_FAIRWAY)
                .treeScheme(TreeScheme.OAK)
                .roughDeepCover(0.6f)
                .difficulty(7f)
                .teeH(0f, 0f).greenH(15f, 8f)
                .wind(0f, 5f)
                .treeH(4f, 2f).trees(0.06f, 3f, 0.5f)
                .terrain(0.02f, 4f)
                .fairway(40f, 10f).wiggle(0.1f, 0.1f)
                .distance(450, 530)
                .par(4)
        ),

        MONOLITH_PLAINS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.CHERRY_BLOSSOM)
                .difficulty(5f)
                .teeH(20f, 0f).greenH(15f, 10f)
                .wind(0f, 8f)
                .terrain(0.008f, 15f)
                .fairway(60f, 0f).undulation(0.9f).wiggle(0.05f, 0.1f).islands(0.25f).cohesion(1.3f)
                .bunkers(1, 4, 2.5f, 1.0f)
                .par45(600, 660, 720, 800)
                .waterLevel(-2f).spawnMonoliths()
        ),

        MOGUL_HIGHLANDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MOUNDS)
                .treeScheme(TreeScheme.BIRCH)
                .roughDeepCover(1.3f)
                .mudHeight(0.5f)
                .difficulty(6f)
                .teeH(20f, 0f).greenH(20f, 0f)
                .wind(3.5f, 5.5f)
                .trees(0.05f, 2.5f, 0.4f).terrain(0.08f, 18f)
                .fairway(40f, 27f).undulation(0.2f).wiggle(0.16f, 0.08f).islands(0.3f).cohesion(0.6f)
                .bunkers(1, 1, 3.5f)
                .par45(600, 660, 720, 750)
                .useMogulNoise()
        ),

        WETLANDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.OAK)
                .roughDeepCover(1.4f)
                .mudHeight(2.5f)
                .difficulty(5f)
                .teeH(17f, 2f).greenH(13f, 3f)
                .wind(0f, 3.5f)
                .treeH(6f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(1.4f).wiggle(0.1f, 0.1f)
                .bunkers(1, 1, 3.4f)
                .distance(230, 345)
                .par(3)
                .greenSize(21f, 25f)
        ),

        BIG_GRAPE_VINEYARDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.GRAPEVINE)
                .roughDeepCover(0.7f)
                .difficulty(7f)
                .teeH(22f, 0f).greenH(1.0f, 0f)
                .wind(4f, 7.5f)
                .treeH(1.5f, 0.2f).trees(0.2f, 1f, 0.2f)
                .terrain(0.06f, 4f)
                .fairway(50f, 0f).undulation(0.7f).wiggle(0.5f, 0.1f).cohesion(1.0f)
                .bunkers(2, 3, 1.1f)
                .distance(520, 660)
                .par(4)
                .feature(ArchetypeSpec.TerrainFeature.VINEYARDS).cardinalWaveAngles()
                .treeStrategy(ArchetypeSpec.TreeStrategy.VINEYARD)
        ),

        CLIPPERTON_ROCK(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.PALM)
                .roughDeepCover(0.6f)
                .mudHeight(-99f)
                .difficulty(4f)
                .teeH(18f, 5f).greenH(4f, 2f)
                .wind(5f, 10f)
                .treeH(11.5f, 2f).trees(0.4f, 4.2f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 0f).undulation(0.2f).wiggle(0.5f, 0.1f).cohesion(1.0f)
                .distance(450, 500)
                .mapWidth(-1)   // terrain grid width matches hole distance
                .par(4)
                .feature(ArchetypeSpec.TerrainFeature.CLIPPERTON_ROCK).waterLevel(0f)
        ),

        // ── New archetypes — uses the previously-unused terrain algorithms ─────
        // Swap .algo() between ROLLING_DUNES and PLATEAU to compare them.

        OASIS_DUNES(new ArchetypeSpec()
                .algo(TerrainAlgorithm.ROLLING_DUNES)   // ← swap to PLATEAU to compare
                .treeScheme(TreeScheme.ACACIA).treeSchemeAlt(TreeScheme.PALM)
                .roughDeepCover(0.5f)
                .difficulty(3f)
                .teeH(5f, 0f).greenH(12f, 8f)
                .wind(3.5f, 6f)
                .treeH(13.5f, 4f).trees(0.4f, 4.2f, 0.5f)
                .terrain(0.04f, 8f)
                .fairway(56f, 0f).wiggle(0.15f, 0.1f).islands(0.2f).cohesion(0.5f)
                .distance(220, 330)
                .par(3)
                .greenSize(20f, 24f)
                .undulation(1.8f)
                .feature(ArchetypeSpec.TerrainFeature.OASIS_DUNES)
                .treeStrategy(ArchetypeSpec.TreeStrategy.OASIS).greenHmapRadius(0.44f)
        ),

        STONE_RUN(new ArchetypeSpec()
                .algo(TerrainAlgorithm.PLATEAU)          // ← swap to ROLLING_DUNES to compare
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.BIRCH)
                .deepRough(-1f)   // rough is not placed relative to fairway — skip deep rough pass
                .mudHeight(-99f)
                .difficulty(2f)
                .teeH(2f, 2f).greenH(64f, 15f)
                .wind(1.5f, 5f)
                .treeH(7f, 1f).trees(0.10f, 3f, 0.5f)
                .terrain(0.03f, 6f)
                .fairway(48f, 0f).wiggle(0.12f, 0.08f).islands(0.15f).cohesion(0.9f)
                .par45(600, 700, 860, 1150)
                .greenSize(29f, 32f)
                .undulation(0.3f)
                .feature(ArchetypeSpec.TerrainFeature.STONE_RUN)
        ),
        WOODLAND_EDGE(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.BIRCH)
                .roughDeepCover(3.5f)   // dense forest: most rough converts to deep rough
                .mudHeight(1.5f)
                .difficulty(4f)
                .teeH(3f, 1f).greenH(1f, 0.2f)
                .wind(2.5f, 5.5f)
                .treeH(16f, 4f).trees(0.78f, 5f, 0.82f)
                .terrain(0.03f, 6f)
                .fairway(48f, 0f).wiggle(0.12f, 0.08f).islands(0.15f).cohesion(0.9f)
                .distance(170, 205)
                .par(3)
                .greenSize(18f, 22f)
                .undulation(0.2f)
                .feature(ArchetypeSpec.TerrainFeature.FOREST_EDGE).teeTreeBuffer(20, 5)
        ),
        CYPRESS_BAYOU(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.BALD_CYPRESS)
                .roughDeepCover(1.3f)
                .mudHeight(0.6f)     // cells above 0.6 stay ROUGH (~23% of rough) for trees; rest become MUD
                .difficulty(1f)
                .teeH(2.0f, 0.2f).greenH(2.0f, 0.2f)
                .wind(0.8f, 2.5f)
                .treeH(28f, 1.6f).trees(0.17f, 7.2f, 0.88f)
                .terrain(0.05f, 1.4f)
                .fairway(42f, 20f).undulation(0.9f).wiggle(0.28f, 0.12f)
                .bunkers(0, 0, 0f)
                .par34(260, 320, 480, 690)
                .greenSize(28f, 34f)
                .deepRough(14f)
                .feature(ArchetypeSpec.TerrainFeature.CYPRESS_BAYOU).waterLevel(0f)
                .allowMudTrees().treeWaterWading(-0.5f).excludeFeatureGapCells()
        ),

        // ── Badlands — dry cracked earth with fissure network and stone patches ──
        // BadlandsGenerator carves a graph-based crack network across the whole map:
        // branching/recombining fissures with STONE walls and SAND floors, plus
        // scattered stone patches throughout the rough. Very sparse trees.
        BADLANDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.PLATEAU)
                .treeScheme(TreeScheme.DEAD_GRAY)
                .mudHeight(-99f)
                .deepRough(-1f)     // stone/sand passes handle rough differentiation instead
                .difficulty(5f)
                .teeH(4f, 2f).greenH(30f, 2f)
                .wind(0.7f, 2f)
                .treeH(5f, 1f).trees(0.06f, 3f, 0.5f)
                .terrain(0.025f, 6f)
                .fairway(46f, 0f).wiggle(0.08f, 0.05f)
                .bunkers(4, 6, 1.5f)
                .distance(610, 750)
                .par(4)
                .greenSize(22f, 26f)
                .undulation(0.3f)
                .feature(ArchetypeSpec.TerrainFeature.BADLANDS).waterLevel(-15f)
                .sandPass(0.22f).roughToStone()
        ),

//         ── Table Mountain — custom TableMountainGenerator ────────────────────
//         Steep-walled plateau crossing the full map width. Par 4, two-shot strategy.
//         TableMountainGenerator ignores algo/teeH/greenH for height — uses its own
//         seeded parameters — but reads bunkers, greenRadius, trees, wind from data.
        TABLE_MOUNTAIN(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .mudHeight(-99f)
                .roughDeepCover(1.0f)
                .difficulty(4f)
                .teeH(3f, 2f).greenH(3f, 2f)
                .wind(0.7f, 2f)
                .treeH(10f, 3f).trees(0.52f, 6f, 0.7f)
                .terrain(0.03f, 6f)
                .fairway(50f, 0f).wiggle(0.1f, 0.05f)
                .bunkers(3, 5, 2.0f)
                .distance(500, 625)
                .par(4)
                .greenSize(20f, 24f)
                .undulation(0.4f)
                .feature(ArchetypeSpec.TerrainFeature.TABLE_MOUNTAIN).skipFairway().deepRoughTreesOnly()
        ),

//         ── Goldsmith Bowl — radial bowl centred on the green ────────────────
//         GoldsmithBowlGenerator overrides heights with a 5-zone radial profile:
//         flat green platform → ascending wall → rim plateau → outer descent → tee flats.
//         The rim always sits above greenH so the ascending-wall shape is guaranteed.
//         Standard pipeline (bunkers, deep-rough, trees, green undulation) runs after.
        GOLDSMITH_BOWL(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.DEAD_GRAY)
                .mudHeight(-99f)
                .difficulty(3f)
                .teeH(3f, 1.5f).greenH(28f, 2.5f)
                .wind(2f, 3.5f)
                .treeH(8f, 1f).trees(0.10f, 3f, 0.6f)
                .terrain(0.04f, 8f)
                .fairway(50f, 0f).undulation(0.06f).wiggle(0.08f, 0.06f).islands(0.2f).cohesion(0.9f)
                .par(3)
                .distance(260, 360)
                .bunkers(1, 3, 2.0f)
                .greenSize(18f, 20f)
                .feature(ArchetypeSpec.TerrainFeature.GOLDSMITH_BOWL)
        ),

//         ── Dogleg River — wide map, tee right, green left (40% mirrored) ────
//         DoglegRiverGenerator carves 2–3 straight fairway legs: first leg(s) run
//         north along the right side, final leg angles diagonally to the top-left
//         green.  The centre-left is drowned by a large water zone with a steep
//         cliff edge.  1–3 flat-topped fairway islands dot the water for a risky
//         2-shot route.  Tall tree rows guard the inner corner.
        DOGLEG_RIVER(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.BIRCH)
                .mudHeight(-99f)
                .roughDeepCover(1.2f)
                .difficulty(5f)
                .teeH(2.5f, 0.5f).greenH(2.5f, 0.5f)
                .wind(2f, 5f)
                .treeH(10f, 3f).trees(0.10f, 3f, 0.5f)
                .terrain(0.02f, 3f)
                .fairway(48f, 0f).wiggle(0.05f, 0f).undulation(0.15f).cohesion(1f)
                .bunkers(1, 2, 1.5f)
                .distance(660, 770)
                .mapWidth(540)
                .par(5)
                .greenSize(22f, 26f)
                .teeXFraction(0.78f)
                .greenXFraction(0.12f)
                .waterLevel(1.0f)
                .feature(ArchetypeSpec.TerrainFeature.DOGLEG_RIVER)
                .skipFairway()
        );

        private final ArchetypeSpec spec;

        Archetype(ArchetypeSpec spec) {
            this.spec = spec;
        }

        public ArchetypeSpec spec() {
            return spec;
        }
    }

    private long seed;
    private Archetype archetype;
    private TerrainAlgorithm terrainAlgorithm;
    private Tree.TreeScheme treeScheme;
    private float teeHeight;
    private float greenHeight;
    private float treeHeight;
    private float foliageRadius;
    private float trunkRadius;
    private float hillFrequency;
    private float maxHeight;
    private float treeDensity;
    private float maxFairwayWidth;
    private float minFairwayWidth;
    private float undulation;
    private float waterLevel;
    private float holeSize;
    private float greenRadius;
    private int nBunkers;
    private float bunkerDepth;
    private Vector3 wind;
    private int distance;
    private int mapWidth; // The horizontal size of the terrain grid
    private int par;
    private float shotIndex; // Tracks which hole/shot sequence we are on

    private float fairwayWiggle;
    private float fairwayRoughIslands;
    private float fairwayCohesion;
    private float deepRoughThreshold = 10f;
    private float roughDeepCover = 1.0f;
    private float mudHeight = -99f;

    public LevelData() {
    }

    public long getSeed() {
        return seed;
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public Archetype getArchetype() {
        return archetype;
    }

    public void setArchetype(Archetype archetype) {
        this.archetype = archetype;
    }

    public TerrainAlgorithm getTerrainAlgorithm() {
        return terrainAlgorithm;
    }

    public void setTerrainAlgorithm(TerrainAlgorithm terrainAlgorithm) {
        this.terrainAlgorithm = terrainAlgorithm;
    }

    public Tree.TreeScheme getTreeScheme() {
        return treeScheme;
    }

    public void setTreeScheme(Tree.TreeScheme treeScheme) {
        this.treeScheme = treeScheme;
    }

    public float getTeeHeight() {
        return teeHeight;
    }

    public void setTeeHeight(float teeHeight) {
        this.teeHeight = teeHeight;
    }

    public float getGreenHeight() {
        return greenHeight;
    }

    public void setGreenHeight(float greenHeight) {
        this.greenHeight = greenHeight;
    }

    public float getTreeHeight() {
        return treeHeight;
    }

    public void setTreeHeight(float treeHeight) {
        this.treeHeight = treeHeight;
    }

    public float getFoliageRadius() {
        return foliageRadius;
    }

    public void setFoliageRadius(float foliageRadius) {
        this.foliageRadius = foliageRadius;
    }

    public float getTrunkRadius() {
        return trunkRadius;
    }

    public void setTrunkRadius(float trunkRadius) {
        this.trunkRadius = trunkRadius;
    }

    public float getHillFrequency() {
        return hillFrequency;
    }

    public void setHillFrequency(float hillFrequency) {
        this.hillFrequency = hillFrequency;
    }

    public float getMaxHeight() {
        return maxHeight;
    }

    public void setMaxHeight(float maxHeight) {
        this.maxHeight = maxHeight;
    }

    public float getTreeDensity() {
        return treeDensity;
    }

    public void setTreeDensity(float treeDensity) {
        this.treeDensity = treeDensity;
    }

    public float getMaxFairwayWidth() {
        return maxFairwayWidth;
    }

    public void setMaxFairwayWidth(float fairwayWidth) {
        this.maxFairwayWidth = fairwayWidth;
    }

    public float getMinFairwayWidth() {
        return minFairwayWidth;
    }

    public void setMinFairwayWidth(float fairwayWidth) {
        this.minFairwayWidth = fairwayWidth;
    }

    public float getUndulation() {
        return undulation;
    }

    public void setUndulation(float undulation) {
        this.undulation = undulation;
    }

    public float getWaterLevel() {
        return waterLevel;
    }

    public void setWaterLevel(float waterLevel) {
        this.waterLevel = waterLevel;
    }

    public float getHoleSize() {
        return holeSize;
    }

    public void setHoleSize(float holeSize) {
        this.holeSize = holeSize;
    }

    public float getGreenRadius() {
        return greenRadius;
    }

    public void setGreenRadius(float greenRadius) {
        this.greenRadius = greenRadius;
    }

    public int getnBunkers() {
        return nBunkers;
    }

    public void setnBunkers(int nBunkers) {
        this.nBunkers = nBunkers;
    }

    public float getBunkerDepth() {
        return bunkerDepth;
    }

    public void setBunkerDepth(float bunkerDepth) {
        this.bunkerDepth = bunkerDepth;
    }

    public Vector3 getWind() {
        return wind;
    }

    public void setWind(Vector3 wind) {
        this.wind = wind;
    }

    public int getDistance() {
        return distance;
    }

    public void setDistance(int distance) {
        this.distance = distance;
    }

    public int getMapWidth() {
        return mapWidth;
    }

    public void setMapWidth(int mapWidth) {
        this.mapWidth = mapWidth;
    }

    public int getPar() {
        return par;
    }

    public void setPar(int par) {
        this.par = par;
    }

    public float getFairwayWiggle() {
        return fairwayWiggle;
    }

    public void setFairwayWiggle(float fairwayWiggle) {
        this.fairwayWiggle = fairwayWiggle;
    }

    public float getFairwayRoughIslands() {
        return fairwayRoughIslands;
    }

    public void setFairwayRoughIslands(float fairwayRoughIslands) {
        this.fairwayRoughIslands = fairwayRoughIslands;
    }

    public float getFairwayCohesion() {
        return fairwayCohesion;
    }

    public void setFairwayCohesion(float fairwayCohesion) {
        this.fairwayCohesion = fairwayCohesion;
    }

    public float getDeepRoughThreshold() { return deepRoughThreshold; }
    public void setDeepRoughThreshold(float t) { this.deepRoughThreshold = t; }
    public float getRoughDeepCover() { return roughDeepCover; }
    public void setRoughDeepCover(float c) { this.roughDeepCover = c; }
    public float getMudHeight() { return mudHeight; }
    public void setMudHeight(float h) { this.mudHeight = h; }

    public float getShotIndex() {
        return shotIndex;
    }

    public void setShotIndex(float shotIndex) {
        this.shotIndex = shotIndex;
    }
}
