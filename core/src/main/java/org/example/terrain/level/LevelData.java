package org.example.terrain.level;

import com.badlogic.gdx.math.Vector3;
import org.example.terrain.objects.Tree;
import org.example.terrain.objects.Tree.TreeScheme;

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
                .wind(0f, 19f)
                .wiggle(0.05f, 0.15f).islands(0.25f).fairway(49f, 0f).cohesion(1.3f)
                .bunkers(2, 5, 2.3f)
                .distance(300, 500)
                .par345(435, 710)
        ),

        RAZORBACK_RIDGE(new ArchetypeSpec()
                .algo(TerrainAlgorithm.RAISED_FAIRWAY)
                .treeScheme(TreeScheme.AUTUMN_MAPLE)
                .difficulty(1f)
                .teeH(0f, 0f).greenH(18f, 8f)
                .wind(5f, 13f)
                .treeH(12f, 0f).trees(0.18f, 5f, 0.8f)
                .terrain(0.02f, 5f)
                .fairway(51f, 0f).undulation(1.2f).wiggle(0.22f, 0.08f).cohesion(0.75f)
                .distance(470, 110)
                .par(5)
        ),

        WHISTLING_ISLES(new ArchetypeSpec()
                .algo(TerrainAlgorithm.CRAGGY_RIDGES)
                .treeScheme(TreeScheme.OAK)
                .difficulty(1f)
                .teeH(0.2f, 0f).greenH(1.0f, 0f)
                .wind(11f, 18f)
                .treeH(8f, 2f).trees(0.98f, 2.7f, 0.4f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(1.4f).wiggle(0.1f, 0.1f).cohesion(1.0f)
                .distance(600, 100)
                .par(5)
        ),

        // ── Mid-range ─────────────────────────────────────────────────────────

        ISLAND_COAST(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.BIRCH)
                .difficulty(3f)
                .teeH(15f, 10f).greenH(15f, 5f)
                .wind(6f, 23f)
                .trees(0.03f, 2.5f, 0.4f).terrain(0.02f, 15f)
                .fairway(55f, 35f).islands(0f).wiggle(0.25f, 0.06f).cohesion(0.9f)
                .distance(500, 200)
                .par45(600)
        ),

        CLIFFSIDE_BLUFF(new ArchetypeSpec()
                .algo(TerrainAlgorithm.TERRACED).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.FIR)
                .difficulty(2f).distancePenalty()
                .teeH(60f, 30f).greenH(5f, 10f)
                .wind(7f, 18f)
                .trees(0.25f, 8f, 1.2f).terrain(0.012f, 10f)
                .fairway(40f, 0f).islands(0.05f).wiggle(0.20f, 0.1f).cohesion(0.9f)
                .bunkers(1, 2, 0.8f)
                .distance(450, 80)
                .par(4)
        ),

        REDWOOD_FOREST(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.REDWOOD)
                .difficulty(3f).distancePenalty()
                .teeH(5f, 5f).greenH(5f, 5f)
                .wind(0f, 7f)
                .treeH(45f, 0f).trees(0.26f, 10f, 1.8f)
                .terrain(0.025f, 5f)
                .fairway(45f, 25f).islands(0f).wiggle(0.06f, 0.02f).cohesion(0.95f)
                .distance(550, 200)
                .par(5)
        ),

        CRATER_FIELDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE)
                .treeScheme(TreeScheme.DEAD_GRAY)
                .difficulty(2f)
                .teeH(8f, 0f).greenH(12f, 0f)
                .wind(5f, 18f)
                .treeH(8f, 0f).trees(0.10f, 3f, 0.6f)
                .terrain(0.06f, 12f)
                .fairway(50f, 0f).undulation(0.28f).wiggle(0.1f, 0.08f).islands(0.4f).cohesion(0.9f)
                .distance(500, 150)
                .par45(600)
        ),

        PLUNGE_CENOTES(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE)
                .treeScheme(TreeScheme.BIRCH)
                .difficulty(2f)
                .teeH(5f, 5f).greenH(0f, 0f)
                .wind(6f, 12f)
                .treeH(4f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.02f, 4f)
                .fairway(35f, 0f).wiggle(0.1f, 0.1f)
                .distance(480, 100)
                .par(4)
        ),

        ROUGH_HOUGH_BLUFFS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .difficulty(2f)
                .teeH(17f, 2f).greenH(13f, 3f)
                .wind(8f, 14f)
                .treeH(6f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(0.4f).wiggle(0.1f, 0.1f)
                .distance(400, 100)
                .par(4)
        ),

        // ── Challenging ───────────────────────────────────────────────────────

        BUSH_WORLD(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SMOOTH_SINE).algoAlt(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.AUTUMN_MAPLE)
                .difficulty(3f)
                .wind(2f, 15f)
                .treeH(6f, 0f).trees(0.82f, 3f, 0.5f)
                .terrain(0.045f, 7f)
                .fairway(35f, 0f).wiggle(0.4f, 0.3f).islands(0.7f).cohesion(0.42f)
                .bunkers(1, 1, 2.2f)
                .distance(430, 160)
                .par45(515)
        ),

        SHADOW_CANYON(new ArchetypeSpec()
                .algo(TerrainAlgorithm.SUNKEN_FAIRWAY)
                .treeScheme(TreeScheme.OAK)
                .difficulty(7f)
                .teeH(0f, 0f).greenH(15f, 8f)
                .wind(0f, 7f)
                .treeH(4f, 2f).trees(0.06f, 3f, 0.5f)
                .terrain(0.02f, 4f)
                .fairway(40f, 10f).wiggle(0.1f, 0.1f)
                .distance(420, 120)
                .par(4)
        ),

        MONOLITH_PLAINS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MULTI_WAVE)
                .treeScheme(TreeScheme.CHERRY_BLOSSOM)
                .difficulty(5f)
                .teeH(20f, 0f).greenH(15f, 10f)
                .wind(0f, 12f)
                .terrain(0.008f, 15f)
                .fairway(60f, 0f).undulation(0.9f).wiggle(0.05f, 0.1f).islands(0.25f).cohesion(1.3f)
                .bunkers(1, 4, 2.5f, 1.0f)
                .distance(600, 200)
                .par45(690)
        ),

        MOGUL_HIGHLANDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.MOUNDS)
                .treeScheme(TreeScheme.BIRCH)
                .difficulty(6f)
                .teeH(20f, 0f).greenH(20f, 0f)
                .wind(1f, 16f)
                .trees(0.05f, 2.5f, 0.4f).terrain(0.08f, 18f)
                .fairway(40f, 27f).undulation(0.2f).wiggle(0.16f, 0.08f).islands(0.3f).cohesion(0.6f)
                .bunkers(1, 1, 3.5f)
                .distance(650, 100)
                .par45(700)
        ),

        WETLANDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.OAK)
                .difficulty(6f)
                .teeH(17f, 2f).greenH(13f, 3f)
                .wind(6f, 12f)
                .treeH(6f, 2f).trees(0.13f, 3f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 28f).undulation(1.4f).wiggle(0.1f, 0.1f)
                .bunkers(1, 1, 3.4f)
                .distance(250, 80)
                .par(3)
        ),

        BIG_GRAPE_VINEYARDS(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.GRAPEVINE)
                .difficulty(7f)
                .teeH(22f, 0f).greenH(1.0f, 0f)
                .wind(2f, 8f)
                .treeH(1.5f, 0.2f).trees(0.2f, 1f, 0.2f)
                .terrain(0.06f, 4f)
                .fairway(50f, 0f).undulation(0.7f).wiggle(0.5f, 0.1f).cohesion(1.0f)
                .bunkers(2, 3, 1.1f)
                .distance(550, 60)
                .par(4)
        ),

        CLIPPERTON_ROCK(new ArchetypeSpec()
                .algo(TerrainAlgorithm.DUNES)
                .treeScheme(TreeScheme.PALM)
                .difficulty(4f)
                .teeH(18f, 5f).greenH(4f, 2f)
                .wind(12f, 20f)
                .treeH(11.5f, 2f).trees(0.4f, 4.2f, 0.5f)
                .terrain(0.06f, 4f)
                .fairway(50f, 0f).undulation(0.2f).wiggle(0.5f, 0.1f).cohesion(1.0f)
                .distance(450, 50)
                .mapWidth(-1)   // terrain grid width matches hole distance
                .par(4)
        ),

        // ── New archetypes — uses the previously-unused terrain algorithms ─────
        // Swap .algo() between ROLLING_DUNES and PLATEAU to compare them.

        DUNES_VALLEY(new ArchetypeSpec()
                .algo(TerrainAlgorithm.ROLLING_DUNES)   // ← swap to PLATEAU to compare
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.PALM)
                .difficulty(3f)
                .teeH(5f, 0f).greenH(12f, 8f)
                .wind(5f, 16f)
                .treeH(13.5f, 4f).trees(0.4f, 4.2f, 0.5f)
                .terrain(0.04f, 8f)
                .fairway(56f, 0f).wiggle(0.15f, 0.1f).islands(0.2f).cohesion(0.5f)
                .distance(220, 320)
                .par(3)
                .undulation(1.8f)
        ),

        STONE_RUN(new ArchetypeSpec()
                .algo(TerrainAlgorithm.PLATEAU)          // ← swap to ROLLING_DUNES to compare
                .treeScheme(TreeScheme.OAK).treeSchemeAlt(TreeScheme.BIRCH)
                .difficulty(6f)
                .teeH(2f, 2f).greenH(64f, 15f)
                .wind(5f, 9f)
                .treeH(7f, 1f).trees(0.10f, 3f, 0.5f)
                .terrain(0.03f, 6f)
                .fairway(48f, 0f).wiggle(0.12f, 0.08f).islands(0.15f).cohesion(0.9f)
                .distance(650, 1200)
                .par45(750)
                .undulation(0.3f)//todo: M key to return to menu works everywhere
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

    public float getShotIndex() {
        return shotIndex;
    }

    public void setShotIndex(float shotIndex) {
        this.shotIndex = shotIndex;
    }
}
