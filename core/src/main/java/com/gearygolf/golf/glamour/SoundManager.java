package com.gearygolf.golf.glamour;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.gearygolf.golf.ball.MinigameResult;
import com.gearygolf.golf.terrain.Terrain;

/**
 * SoundManager — central audio controller for Geary Golf.
 * <p>
 * Phases:
 * 1  Menu:    birdsong ambient cross-fade, button click sounds
 * 2  Ambient: wind (speed/altitude-driven, pitch-adjustable), spatial water/foliage/wood
 * 3  Active:  ball strikes (club-specific, power-driven), bounce, roll loop
 * 4  Polish:  software echo for canyon maps, multiplayer remote shot audio
 * <p>
 * Asset layout expected under assets/:
 * sounds/menu/birdsong_1.wav … birdsong_N.wav
 * sounds/ui/button_down.wav, button_up.wav
 * sounds/ambient/wind_1.wav … wind_N.wav   (short loops, ~10–30 s — NOT long streaming tracks,
 * must be Sound not Music so pitch is adjustable)
 * sounds/ambient/water_lapping.wav          (short loop)
 * sounds/ambient/foliage_rustle.wav         (short loop)
 * sounds/ambient/wood_creak.wav             (short loop)
 * sounds/strikes/driver_1.wav … driver_N.wav
 * sounds/strikes/iron_1.wav … iron_N.wav
 * sounds/strikes/wedge_1.wav … wedge_N.wav
 * sounds/strikes/putter_1.wav … putter_N.wav
 * sounds/ball/bounce/{grass,mud,sand,stone}_N.wav
 * sounds/ball/roll/{grass,mud,sand,stone}_N.wav
 * <p>
 * Echo (Phase 4): no extra assets required. When reverbEnabled is true, strikes and bounces
 * schedule 1–2 delayed repeats of the same sound at reduced volume using the echo queue,
 * processed each frame in update().
 */
public class SoundManager implements Disposable {

    // ── Tuning constants ─────────────────────────────────────────────────────

    private static final float BIRDSONG_VOLUME = 0.30f;
    private static final float BIRDSONG_FADE_DURATION = 4f;
    private static final float BIRDSONG_PLAY_DURATION = 60f;
    private static final float PITCH_VARIANCE = 0.12f;

    private static final float SPATIAL_MAX_DISTANCE = 100f;
    // Reference distance: full volume within this radius; beyond it volume ∝ 1/d^ROLLOFF.
    // This matches the inverse-square behaviour of real sound — steep close-range gradient,
    // rapid falloff at distance — rather than the flat-near-source shape of (1-d/MAX)^n.
    private static final float SPATIAL_REF_DISTANCE = 4f;
    private static final float SPATIAL_ROLLOFF = 1.5f;

    private static final float WIND_MIN_VOLUME = 0.05f;
    private static final float WIND_ALTITUDE_REF = 60f;
    private static final float WIND_ALTITUDE_CONTRIBUTION = 0.3f;

    private static final float WIND_SPEED_LOW_MAX        = 8f;   // always use low pool below this speed
    private static final float WIND_SPEED_HIGH_MIN       = 13f;  // always use high pool above this speed

    private static final float WIND_XFADE_DURATION      = 3.5f;
    private static final float WIND_LAYER_MIN            = 18f;
    private static final float WIND_LAYER_MAX            = 35f;
    private static final float WIND_GUST_INTERVAL_MIN    = 15f;
    private static final float WIND_GUST_INTERVAL_MAX    = 40f;
    private static final float WIND_GUST_DURATION_MIN    = 1.5f;
    private static final float WIND_GUST_DURATION_MAX    = 4f;
    private static final float WIND_GUST_MAX_BOOST       = 0.45f;
    private static final float WIND_BREATH_INTERVAL_MIN  = 25f;
    private static final float WIND_BREATH_INTERVAL_MAX  = 55f;
    private static final float WIND_BREATH_DURATION_MIN  = 5f;
    private static final float WIND_BREATH_DURATION_MAX  = 8f;
    private static final float WIND_BREATH_ATTENUATION   = 0.12f;

    private static final float ROLL_STOP_THRESHOLD = 0.5f;
    private static final float ROLL_MAX_SPEED = 12f;

    /**
     * Delay in seconds before the first echo repeat.
     */
    private static final float ECHO_DELAY_1 = 0.18f;
    /**
     * Delay in seconds before the second (quieter) echo repeat.
     */
    private static final float ECHO_DELAY_2 = 0.38f;
    /**
     * Volume of the first echo relative to the original.
     */
    private static final float ECHO_VOLUME_1 = 0.28f;
    /**
     * Volume of the second echo relative to the original.
     */
    private static final float ECHO_VOLUME_2 = 0.10f;
    /**
     * Slight pitch drop on echoes (canyon reflections are slightly lower).
     */
    private static final float ECHO_PITCH_FACTOR = 0.96f;

    // ── Volume buses ─────────────────────────────────────────────────────────

    private float masterVolume = 1f;
    private float sfxVolume = 1f;
    private float ambientVolume = 1f;

    // ── Per-channel fine-tune scales (advanced settings) ─────────────────────
    private float bounceScale         = 1f;
    private float arcadeAirborneScale = 1f;
    private float airWhooshScale      = 1f;
    private float ambientTreesScale   = 1f;
    private float ambientWaterScale   = 1f;
    private float birdsongScale       = 1f;

    // ── Phase 1: menu ────────────────────────────────────────────────────────

    private final Array<Music> birdsongTracks = new Array<>();
    private int birdsongIndex = 0;
    private float birdsongTimer = 0f;
    private float birdsongFadeT = 0f;
    private boolean birdsongFading = false;
    private boolean menuActive = false;

    private final Array<Sound> buttonDownSounds = new Array<>();
    private final Array<Sound> buttonUpSounds = new Array<>();

    // ── Phase 2: ambient ─────────────────────────────────────────────────────

    // Wind uses Sound (short loops) so setPitch() is available.
    // Single-layer crossfade system: one active loop, one fading-in loop during transitions.
    private final Array<Sound> windLowSounds  = new Array<>();
    private final Array<Sound> windHighSounds = new Array<>();
    private Sound   windActiveSound    = null;
    private boolean windActiveIsHigh   = false;
    private long    windActiveId       = -1;
    private float   windActivePitch    = 1f;   // per-file random pitch ratio (set once per layer)
    private Sound   windNextSound      = null;
    private boolean windNextIsHigh     = false;
    private long    windNextId         = -1;
    private float   windNextPitch      = 1f;
    private float   windXfadeT         = 0f;   // 0→1 over WIND_XFADE_DURATION
    private boolean windXfading        = false;
    private float   windLayerTimer     = 0f;   // counts down to next file swap

    private float   windGustTimer      = 0f;
    private boolean windGusting        = false;
    private float   windGustT          = 0f;
    private float   windGustPeak       = 0f;
    private float   windGustDuration   = 0f;

    private float   windBreathTimer    = 0f;
    private boolean windBreathing      = false;
    private float   windBreathT        = 0f;
    private float   windBreathDuration = 0f;

    private float   windGroundLevel    = 0f;   // (tee.y + hole.y) / 2 — set by GolfGame

    private float windVolumeCurrent = 0f;
    private float windPitchCurrent = 1f;

    private final Array<Sound> waterSounds   = new Array<>();
    private final Array<Sound> foliageSounds = new Array<>();
    private Sound activeWaterSound   = null;
    private Sound activeFoliageSound = null;
    private Sound woodCreakSound;
    private long waterLoopId     = -1;
    private long foliageLoopId   = -1;
    private long woodCreakLoopId = -1;

    private float windTime         = 0f;
    private float currentWindSpeed = 0f;

    private static final float GAME_BIRDSONG_VOLUME = 0.12f;
    private boolean gameBirdsongActive   = false;
    private Music   activeGameBirdsong   = null;

    private boolean gameActive = false;

    // ── Phase 3: active ──────────────────────────────────────────────────────

    private final Array<Sound> driverSounds = new Array<>();
    private final Array<Sound> ironSounds = new Array<>();
    private final Array<Sound> wedgeSounds = new Array<>();
    private final Array<Sound> putterSounds = new Array<>();

    // Surface-keyed bounce pools: grass, mud, sand, stone, wood
    private final Array<Sound> bounceGrass = new Array<>();
    private final Array<Sound> bounceMud = new Array<>();
    private final Array<Sound> bounceSand = new Array<>();
    private final Array<Sound> bounceStone = new Array<>();
    private final Array<Sound> bounceWood = new Array<>();

    // Surface-keyed roll pools: grass, mud, sand, stone
    private final Array<Sound> rollGrass = new Array<>();
    private final Array<Sound> rollMud = new Array<>();
    private final Array<Sound> rollSand = new Array<>();
    private final Array<Sound> rollStone = new Array<>();

    private Sound cupSound = null;

    private final Array<Sound> splashSounds = new Array<>();

    private final Array<Sound> foliageRustleSounds = new Array<>();
    private final Array<Sound> twigSnapSounds = new Array<>();

    // Per-rating in-flight sounds (looping, speed-scaled)
    private Sound flightGood    = null;
    private Sound flightGreat   = null;
    private Sound flightSuper   = null;
    private Sound flightPerfect = null;
    private Sound activeFlightSound  = null;
    private long  activeFlightId     = -1;
    private MinigameResult.Rating activeFlightRating = null;
    private float flightTimeElapsed  = 0f;
    private float currentFlightVolume = 0f;

    // Always-on whoosh layer — plays under the rating sound for all airborne shots
    private Sound flightWhoosh      = null;
    private long  flightWhooshId    = -1;
    private float currentWhooshVolume = 0f;

    private static final float FLIGHT_SPEED_MAX      = 50f;   // m/s — full volume at this
    private static final float FLIGHT_SPEED_MIN      = 12f;   // m/s — rating sound threshold
    private static final float FLIGHT_PULSE_FREQ     = 10f;   // rad/s — matches BallRenderer pulse
    private static final float FLIGHT_VOLUME_LERP    = 8f;    // lerp rate — ~0.37s to reach target
    private static final float WHOOSH_VOLUME_LERP    = 5f;    // slightly slower ramp for whoosh
    private static final float WHOOSH_MAX_VOLUME     = 0.06f;  // whoosh sits quietly under the rated sound

    // Tracks playing rustle one-shots so they can be faded out when the ball leaves foliage
    private final Array<Sound> activeFolSounds = new Array<>();
    private final Array<Long>  activeFolIds    = new Array<>();
    private final Array<Float> activeFolVols   = new Array<>();
    private float foliageFadeTimer = 0f;
    private static final float FOLIAGE_FADE_DURATION = 0.2f;

    private Sound activeRollSound = null;
    private long activeRollId = -1;
    private Sound activeRollSoundB = null;
    private long activeRollIdB = -1;
    private float rollLayerTimer = 0f;
    private boolean rollLayerBStarted = false;
    private Terrain.TerrainType activeRollSurface = null;
    private float rollPitchOffsetA = 0f;
    private float rollPitchOffsetB = 0f;

    private static final float ROLL_PITCH_JITTER = 0.04f;

    /**
     * Seconds after the first loop starts before the second (offset) layer kicks in.
     */
    private static final float ROLL_LAYER_OFFSET = 0.75f;
    /**
     * Volume of each layer — two overlapping loops at 0.65x combined feel roughly continuous.
     */
    private static final float ROLL_LAYER_VOLUME = 0.65f;

    /**
     * Accumulator for granular stone roll triggering (seconds since last contact fire).
     */
    private float stoneGranularTimer = 0f;

    // ── Debug overlay ────────────────────────────────────────────────────────

    /**
     * Label of the most recently triggered one-shot sound (strike or bounce).
     */
    private String lastOneShotLabel = "";
    /**
     * Seconds remaining to display the last one-shot label.
     */
    private float lastOneShotTimer = 0f;
    private static final float ONE_SHOT_DISPLAY_DURATION = 1.2f;

    // ── Phase 4: echo queue ──────────────────────────────────────────────────

    private boolean reverbEnabled = false;
    private boolean arcadeFlightSoundsEnabled = true;

    /**
     * A pending echo: same sound to be re-played after a delay at reduced volume.
     */
    private static class PendingEcho {
        final Sound sound;
        final float volume;
        final float pitch;
        float timeLeft;

        PendingEcho(Sound sound, float volume, float pitch, float delay) {
            this.sound = sound;
            this.volume = volume;
            this.pitch = pitch;
            this.timeLeft = delay;
        }
    }

    private final Array<PendingEcho> echoQueue = new Array<>();

    // ────────────────────────────────────────────────────────────────────────
    // Constructor / asset loading
    // ────────────────────────────────────────────────────────────────────────

    public SoundManager() {
        loadAssets();
        Gdx.app.log("SoundManager", "Loaded:"
                + " birdsong=" + birdsongTracks.size
                + " btnDown=" + buttonDownSounds.size
                + " btnUp=" + buttonUpSounds.size
                + " windLow=" + windLowSounds.size + " windHigh=" + windHighSounds.size
                + " driver=" + driverSounds.size
                + " iron=" + ironSounds.size
                + " wedge=" + wedgeSounds.size
                + " putter=" + putterSounds.size
                + " bounce(grass/mud/sand/stone/wood)=" + bounceGrass.size + "/" + bounceMud.size + "/" + bounceSand.size + "/" + bounceStone.size + "/" + bounceWood.size
                + " roll(grass/mud/sand/stone)=" + rollGrass.size + "/" + rollMud.size + "/" + rollSand.size + "/" + rollStone.size
                + " splash=" + splashSounds.size);
    }

    private void loadAssets() {
        loadMusicList(birdsongTracks, "sounds/menu/birdsong_", 4);
        loadSoundList(buttonDownSounds, "sounds/ui/button_down_", 4);
        loadSoundList(buttonUpSounds, "sounds/ui/button_up_", 4);

        loadSoundList(windLowSounds,  "sounds/ambient/wind_low_",  3);
        loadSoundList(windHighSounds, "sounds/ambient/wind_high_", 3);
        loadSoundList(waterSounds,   "sounds/ambient/water_lapping_",  4);
        loadSoundList(foliageSounds, "sounds/ambient/foliage_rustle_", 4);
        woodCreakSound = loadSound("sounds/ambient/wood_creak.wav");

        loadSoundList(driverSounds, "sounds/strikes/driver_", 5);
        loadSoundList(ironSounds, "sounds/strikes/iron_", 5);
        loadSoundList(wedgeSounds, "sounds/strikes/wedge_", 5);
        loadSoundList(putterSounds, "sounds/strikes/putter_", 3);

        loadSoundList(bounceGrass, "sounds/ball/bounce/grass_", 4);
        loadSoundList(bounceMud, "sounds/ball/bounce/mud_", 4);
        loadSoundList(bounceSand, "sounds/ball/bounce/sand_", 4);
        loadSoundList(bounceStone, "sounds/ball/bounce/stone_", 4);
        loadSoundList(bounceWood, "sounds/ball/bounce/wood_", 4);

        loadSoundList(rollGrass, "sounds/ball/roll/grass_", 4);
        loadSoundList(rollMud, "sounds/ball/roll/mud_", 4);
        loadSoundList(rollSand, "sounds/ball/roll/sand_", 4);
        loadSoundList(rollStone, "sounds/ball/roll/stone_", 4);

        loadSoundDir(splashSounds, "sounds/ball/splash");
        cupSound = loadSoundMono("sounds/ball/bounce/cup.wav");

        loadSoundList(foliageRustleSounds, "sounds/ball/bounce/foliage_", 3);
        loadSoundList(twigSnapSounds, "sounds/ball/bounce/twig_snap_", 3);

        flightGood    = loadSound("sounds/ball/flight/good.wav");
        flightGreat   = loadSound("sounds/ball/flight/great.wav");
        flightSuper   = loadSound("sounds/ball/flight/super.wav");
        flightPerfect = loadSound("sounds/ball/flight/perfect.wav");
        flightWhoosh  = loadSound("sounds/ball/flight/ball_whoosh.wav");
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 1 — Menu API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Pre-warm all sounds by playing them silently. Call once after construction.
     * Forces SoundPool to fully decode each sound so first real plays have no scheduling delay.
     */
    public void prewarm() {
        for (Sound s : buttonDownSounds) s.play(0f);
        for (Sound s : buttonUpSounds) s.play(0f);
    }

    /**
     * Call when entering the main menu state.
     */
    public void onMenuEnter() {
        Gdx.app.log("SoundManager", "onMenuEnter — birdsongTracks=" + birdsongTracks.size);
        stopAllGameSounds();
        menuActive = true;
        birdsongIndex = 0;
        birdsongTimer = 0f;
        birdsongFading = false;
        startBirdsong(birdsongIndex, 0f);
    }

    /**
     * Call when leaving the menu.
     */
    public void onMenuExit() {
        menuActive = false;
        for (Music m : birdsongTracks) m.stop();
        birdsongFading = false;
    }

    /**
     * Update menu audio. Call every frame while in the menu.
     *
     * @param delta frame time in seconds
     */
    public void updateMenu(float delta) {
        if (!menuActive || birdsongTracks.isEmpty()) return;

        birdsongTimer += delta;

        if (birdsongFading) {
            birdsongFadeT += delta / BIRDSONG_FADE_DURATION;
            if (birdsongFadeT > 1f) birdsongFadeT = 1f;

            int nextIndex = (birdsongIndex + 1) % birdsongTracks.size;
            setMusicVolume(birdsongTracks.get(birdsongIndex), 1f - birdsongFadeT);
            setMusicVolume(birdsongTracks.get(nextIndex), birdsongFadeT);

            if (birdsongFadeT >= 1f) {
                birdsongTracks.get(birdsongIndex).stop();
                birdsongIndex = nextIndex;
                birdsongTimer = 0f;
                birdsongFading = false;
            }
        } else {
            // Fade in over the first BIRDSONG_FADE_DURATION seconds of each track
            float fadedInVol = Math.min(birdsongTimer / BIRDSONG_FADE_DURATION, 1f);
            setMusicVolume(birdsongTracks.get(birdsongIndex), fadedInVol);

            // Start cross-fade when approaching the end of the play window
            if (birdsongTimer >= BIRDSONG_PLAY_DURATION - BIRDSONG_FADE_DURATION
                    && birdsongTracks.size > 1) {
                int nextIndex = (birdsongIndex + 1) % birdsongTracks.size;
                startBirdsong(nextIndex, 0f);
                birdsongFading = true;
                birdsongFadeT = 0f;
            }
        }
    }

    /**
     * Play button-down sound. Wire to TouchDown event on menu buttons.
     */
    public void playButtonDown() {
        Gdx.app.log("SoundManager", "playButtonDown — pool size=" + buttonDownSounds.size);
        playWithVariance(pickRandom(buttonDownSounds), 1f);
    }

    /**
     * Play button-up/release sound. Wire to TouchUp or ChangeListener on menu buttons.
     */
    public void playButtonUp() {
        playWithVariance(pickRandom(buttonUpSounds), 0.8f);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 2 — Game ambient API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Call when a new hole begins.
     *
     * @param hasReverb true for canyon/enclosed maps (enables software echo on strikes and bounces)
     */
    public void onHoleEnter(boolean hasReverb) {
        stopAllMenuSounds();
        gameActive = true;
        reverbEnabled = hasReverb;
        echoQueue.clear();
        startWindLoops();
        startSpatialLoops();
        startGameBirdsong();
    }

    /**
     * Call when leaving the game (victory, menu return, etc.).
     */
    public void onHoleExit() {
        gameActive = false;
        stopAllGameSounds();
    }

    /**
     * Master per-frame update. Call every frame during active gameplay.
     * Handles wind, echo queue, and any other time-driven audio.
     *
     * @param delta     frame delta in seconds
     * @param wind      current wind vector; length = speed (0–30 m/s typical)
     * @param cameraPos current camera world position (Y = altitude)
     */
    public void update(float delta, Vector3 wind, Vector3 cameraPos) {
        if (!gameActive) return;
        updateWind(delta, wind, cameraPos);
        updateEchoQueue(delta);
        if (lastOneShotTimer > 0f) lastOneShotTimer -= delta;
    }

    /**
     * Returns a short string describing currently active sounds for the debug overlay.
     * Only meaningful during gameplay; returns empty string otherwise.
     */
    public String getActiveSoundDebug() {
        StringBuilder sb = new StringBuilder();
        if (lastOneShotTimer > 0f && !lastOneShotLabel.isEmpty()) sb.append(lastOneShotLabel);
        if (activeRollId != -1) {
            if (sb.length() > 0) sb.append(" | ");
            sb.append("ROLL");
        }
        return sb.toString();
    }

    /**
     * Update all spatially-placed ambient sounds. Call every frame during play.
     * Pass null for any source not present on the current hole.
     *
     * @param cameraPos    listener position
     * @param waterPos     world position of nearest water feature, or null
     * @param foliagePos   world position of nearest foliage cluster, or null
     * @param woodCreakPos world position of nearest wooden structure, or null
     */
    public void updateSpatialAmbient(Vector3 cameraPos,
                                     Vector3 waterPos,
                                     Vector3 foliagePos,
                                     Vector3 woodCreakPos) {
        if (!gameActive) return;
        // Water: apply 0.7x scale — naturally louder on water-heavy holes due to proximity
        if (activeWaterSound != null && waterLoopId != -1) {
            float spatial = waterPos != null ? calcSpatialVolume(cameraPos, waterPos) : 0f;
            activeWaterSound.setVolume(waterLoopId, spatial * 0.7f * ambientVolume * masterVolume * ambientWaterScale);
        }
        setSpatialVolume(woodCreakSound,     woodCreakLoopId, cameraPos, woodCreakPos);
        // Foliage ambient is additionally gated by wind — silent in still air
        float windFrac = MathUtils.clamp(currentWindSpeed / 10f, 0f, 1f);
        if (activeFoliageSound != null && foliageLoopId != -1) {
            float spatial = foliagePos != null ? calcSpatialVolume(cameraPos, foliagePos) : 0f;
            activeFoliageSound.setVolume(foliageLoopId, spatial * windFrac * ambientVolume * masterVolume * ambientTreesScale);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 3 — Active game sound API
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Play a ball strike sound. When reverb is enabled, schedules 2 delayed echo repeats.
     *
     * @param clubCategory  "driver", "iron", "wedge", or "putter"
     * @param powerFraction 0.0 (lightest tap) → 1.0 (full power)
     */
    public void playBallStrike(String clubCategory, float powerFraction) {
        Sound s = pickRandom(poolFor(clubCategory));
        if (s == null) return;

        // Hard hits: louder, slightly lower pitched (thud). Soft hits: quieter, higher pitch (tap).
        float volume = MathUtils.clamp(0.35f + powerFraction * 0.65f, 0.35f, 1f)
                * sfxVolume * masterVolume;
        float pitchBase = MathUtils.lerp(1.15f, 0.9f, powerFraction);
        float pitch = pitchBase + MathUtils.random(-PITCH_VARIANCE * 0.5f, PITCH_VARIANCE * 0.5f);

        s.play(volume, pitch, 0f);
        scheduleEchoes(s, volume, pitch);
        lastOneShotLabel = "STRIKE: " + clubCategory.toUpperCase();
        lastOneShotTimer = ONE_SHOT_DISPLAY_DURATION;
    }

    /**
     * Play a ball bounce sound appropriate for the surface.
     *
     * @param cameraPos   listener position
     * @param bouncePos   world position of the bounce
     * @param impactSpeed ball speed at impact (world units/s)
     * @param surface     terrain type at bounce point (null falls back to grass)
     */
    public void playBallBounce(Vector3 cameraPos, Vector3 bouncePos, float impactSpeed,
                               Terrain.TerrainType surface) {
        Sound s = pickRandom(bouncePoolFor(surface));
        if (s == null) {
            Gdx.app.log("BOUNCE", "playBallBounce: sound pool null for surface=" + surface);
            return;
        }

        float spatial = calcSpatialVolume(cameraPos, bouncePos);
        float velocity = MathUtils.clamp(impactSpeed / 20f, 0.1f, 1f);
        float surfaceScale = (surface == Terrain.TerrainType.STONE) ? 3.0f : 1f;
        float volume = velocity * spatial * sfxVolume * masterVolume * surfaceScale * bounceScale;
        float pitch = 1f + MathUtils.random(-PITCH_VARIANCE, PITCH_VARIANCE);
        Gdx.app.log("BOUNCE", String.format(
                "playBallBounce surface=%s impact=%.3f spatial=%.3f velFrac=%.3f sfx=%.2f master=%.2f surfScale=%.1f bounce=%.2f → vol=%.4f",
                surface, impactSpeed, spatial, velocity, sfxVolume, masterVolume, surfaceScale, bounceScale, volume));

        s.play(volume, pitch, 0f);
        scheduleEchoes(s, volume, pitch);
        lastOneShotLabel = "BOUNCE";
        lastOneShotTimer = ONE_SHOT_DISPLAY_DURATION;
    }

    /**
     * Play a wood object bounce (tree trunk). Silent until wood assets are present.
     */
    public void playBallBounceWood(Vector3 cameraPos, Vector3 bouncePos, float impactSpeed) {
        Sound s = pickRandom(bounceWood);
        if (s == null) return;
        float spatial = calcSpatialVolume(cameraPos, bouncePos);
        float velocity = MathUtils.clamp(impactSpeed / 20f, 0.1f, 1f);
        float volume = velocity * spatial * sfxVolume * masterVolume * bounceScale * 2.0f;
        float pitch = 1f + MathUtils.random(-PITCH_VARIANCE, PITCH_VARIANCE);
        s.play(volume, pitch, 0f);
        scheduleEchoes(s, volume, pitch);
        lastOneShotLabel = "BOUNCE(wood)";
        lastOneShotTimer = ONE_SHOT_DISPLAY_DURATION;
    }

    /**
     * Update the continuous ball roll sound. Call every frame while the ball is moving.
     * Automatically starts, adjusts, and stops the looping roll sound.
     * Switches surface pool mid-roll if the terrain changes.
     *
     * @param cameraPos listener position
     * @param ballPos   current ball position
     * @param rollSpeed current ball speed (world units/s); pass 0 when at rest
     * @param surface   current terrain type under the ball (null falls back to grass)
     */
    public void updateBallRoll(float delta, Vector3 cameraPos, Vector3 ballPos, float rollSpeed,
                               Terrain.TerrainType surface) {
        Array<Sound> pool = rollPoolFor(surface);
        if (pool.isEmpty()) return;

        float spatial = calcSpatialVolume(cameraPos, ballPos);
        float speedFrac = MathUtils.clamp(rollSpeed / ROLL_MAX_SPEED, 0f, 1f);
        float targetVol = rollSpeed > ROLL_STOP_THRESHOLD
                ? speedFrac * spatial * sfxVolume * masterVolume
                : 0f;

        if (targetVol <= 0f) {
            stopRollSound();
            return;
        }

        // Stone: granular contact-sound triggering instead of a looped sample.
        // Fires the stone contact clip at speed-proportional intervals with pitch/volume jitter,
        // producing a natural rolling rattle without a static loop artefact.
        if (surface == Terrain.TerrainType.STONE) {
            if (activeRollId != -1) stopRollSound();       // stop any prior loop from a different surface
            activeRollSurface = Terrain.TerrainType.STONE; // track so surfaceChanged fires on next transition
            stoneGranularTimer += delta;
            float interval = MathUtils.lerp(0.07f, 0.0275f, speedFrac); // 14 Hz (slow) → 36 Hz (fast)
            if (stoneGranularTimer >= interval) {
                stoneGranularTimer -= interval; // keep remainder so rate stays even
                Sound s = pickRandom(bounceStone); // use bounce clip — same sound as an actual bounce, just rapid-fired
                if (s != null) {
                    float vol = targetVol * MathUtils.random(0.11f, 0.22f);
                    float pitch = 0.7f + speedFrac * 0.5f + MathUtils.random(-0.12f, 0.14f);
                    s.play(vol, pitch, 0f);
                }
            }
            return;
        }

        // Non-stone: reset granular timer so it starts clean if we return to stone
        stoneGranularTimer = 0f;

        float pitch = 0.8f + speedFrac * 0.4f;
        float layerVol = targetVol * ROLL_LAYER_VOLUME;

        // Restart both layers when surface changes
        boolean surfaceChanged = surface != activeRollSurface;
        if (activeRollId == -1 || surfaceChanged) {
            stopRollSound();
            activeRollSound = pickRandom(pool);
            activeRollSurface = surface;
            rollPitchOffsetA = MathUtils.random(-ROLL_PITCH_JITTER, ROLL_PITCH_JITTER);
            if (activeRollSound != null) {
                activeRollId = activeRollSound.loop(layerVol, pitch * (1f + rollPitchOffsetA), 0f);
                Gdx.app.log("SoundManager", "Roll started — surface=" + surface
                        + " speed=" + rollSpeed + " vol=" + layerVol + " id=" + activeRollId);
            }
        } else {
            activeRollSound.setVolume(activeRollId, layerVol);
            activeRollSound.setPitch(activeRollId, pitch * (1f + rollPitchOffsetA));
        }

        // Second layer starts after ROLL_LAYER_OFFSET seconds, looping offset from the first.
        // The two overlapping loops fill each other's end-of-file gaps.
        rollLayerTimer += delta;
        if (!rollLayerBStarted && rollLayerTimer >= ROLL_LAYER_OFFSET) {
            activeRollSoundB = pickRandom(pool);
            rollPitchOffsetB = MathUtils.random(-ROLL_PITCH_JITTER, ROLL_PITCH_JITTER);
            if (activeRollSoundB != null) {
                activeRollIdB = activeRollSoundB.loop(layerVol, pitch * (1f + rollPitchOffsetB), 0f);
            }
            rollLayerBStarted = true;
        }
        if (activeRollSoundB != null && activeRollIdB != -1) {
            activeRollSoundB.setVolume(activeRollIdB, layerVol);
            activeRollSoundB.setPitch(activeRollIdB, pitch * (1f + rollPitchOffsetB));
        }
    }

    /**
     * Play a water splash sound. Call when water particles are spawned (both full splash and skim).
     *
     * @param cameraPos listener position
     * @param splashPos world position of the splash
     * @param speed     ball speed at the moment of contact
     */
    public void playSplash(Vector3 cameraPos, Vector3 splashPos, float speed) {
        Sound s = pickRandom(splashSounds);
        if (s == null) return;
        float spatial = calcSpatialVolume(cameraPos, splashPos);
        float velocity = MathUtils.clamp(speed / 20f, 0.2f, 1f);
        float volume = velocity * spatial * sfxVolume * masterVolume;
        float pitch = 1f + MathUtils.random(-PITCH_VARIANCE * 0.5f, PITCH_VARIANCE * 0.5f);
        s.play(volume, pitch, 0f);
    }

    /**
     * Play the ball-in-cup sound when the hole is completed.
     * Pitch and volume both scale with entry speed: a gentle putt gives a quiet
     * low thud; a fast shot rattles the cup louder and at higher pitch.
     *
     * @param entrySpeed ball speed (world units/s) at the moment of entering the cup
     */
    public void playBallCup(float entrySpeed) {
        if (cupSound == null) return;
        // Normalise against a "hard" entry of 12 m/s — anything faster is clamped
        float t      = MathUtils.clamp(entrySpeed / 12f, 0f, 1f);
        float volume = MathUtils.lerp(0.45f, 1.0f, t) * sfxVolume * masterVolume;
        float pitch  = MathUtils.lerp(0.85f, 1.25f, t);
        cupSound.play(volume, pitch, 0f);
    }

    /**
     * Play a foliage rustle one-shot. Caller is responsible for rate-limiting.
     *
     * @param cameraPos listener position
     * @param ballPos   current ball position
     * @param speed     ball speed (world units/s) — scales volume
     */
    public void playFoliageRustle(Vector3 cameraPos, Vector3 ballPos, float speed) {
        Sound s = pickRandom(foliageRustleSounds);
        if (s == null) return;
        float spatial = calcSpatialVolume(cameraPos, ballPos);
        float velocity = MathUtils.clamp(speed / 20f, 0.1f, 1f);
        float volume = velocity * spatial * sfxVolume * masterVolume * 0.7f;
        float pitch = 0.7f + MathUtils.random(-PITCH_VARIANCE, PITCH_VARIANCE);
        long id = s.play(volume, pitch, 0f);
        activeFolSounds.add(s);
        activeFolIds.add(id);
        activeFolVols.add(volume);
        foliageFadeTimer = FOLIAGE_FADE_DURATION;
    }

    /**
     * Call every frame during gameplay. Fades out any playing rustle sounds within
     * {@code FOLIAGE_FADE_DURATION} seconds of the ball leaving foliage.
     *
     * @param delta    frame time in seconds
     * @param inFoliage true if the ball is currently inside foliage
     */
    public void updateFoliageRustle(float delta, boolean inFoliage) {
        if (activeFolSounds.isEmpty()) return;
        if (inFoliage) {
            foliageFadeTimer = FOLIAGE_FADE_DURATION;
        } else {
            foliageFadeTimer -= delta;
            if (foliageFadeTimer <= 0f) {
                for (int i = 0; i < activeFolSounds.size; i++) {
                    activeFolSounds.get(i).stop(activeFolIds.get(i));
                }
                activeFolSounds.clear();
                activeFolIds.clear();
                activeFolVols.clear();
            } else {
                float fraction = foliageFadeTimer / FOLIAGE_FADE_DURATION;
                for (int i = 0; i < activeFolSounds.size; i++) {
                    activeFolSounds.get(i).setVolume(activeFolIds.get(i), activeFolVols.get(i) * fraction);
                }
            }
        }
    }

    /**
     * Play a twig-snap one-shot triggered by a significant foliage deflection.
     *
     * @param cameraPos listener position
     * @param ballPos   current ball position
     * @param speed     ball speed at deflection moment — scales volume
     */
    public void playTwigSnap(Vector3 cameraPos, Vector3 ballPos, float speed) {
        Sound s = pickRandom(twigSnapSounds);
        if (s == null) return;
        float spatial = calcSpatialVolume(cameraPos, ballPos);
        float velocity = MathUtils.clamp(speed / 20f, 0.1f, 1f);
        float volume = velocity * spatial * sfxVolume * masterVolume * 0.5f;
        float pitch = 1f + MathUtils.random(-PITCH_VARIANCE * 0.5f, PITCH_VARIANCE * 0.5f);
        s.play(volume, pitch, 0f);
        lastOneShotLabel = "FOLIAGE(snap)";
        lastOneShotTimer = ONE_SHOT_DISPLAY_DURATION;
    }

    public void setArcadeFlightSoundsEnabled(boolean enabled) {
        arcadeFlightSoundsEnabled = enabled;
        if (!enabled) {
            // Stop only the rated-sound layer; leave the whoosh running
            if (activeFlightSound != null && activeFlightId != -1) activeFlightSound.stop(activeFlightId);
            activeFlightSound   = null;
            activeFlightId      = -1;
            currentFlightVolume = 0f;
        }
    }

    public boolean isArcadeFlightSoundsEnabled() { return arcadeFlightSoundsEnabled; }

    /**
     * Start the in-flight looping sound for the given shot rating. Call immediately after ball.hit().
     * Replaces any currently playing flight sound.
     */
    public void startFlightSound(MinigameResult.Rating rating) {
        stopFlightSound();
        // Rated layer (good/great/super/perfect) — only when arcade sounds enabled
        if (arcadeFlightSoundsEnabled) {
            Sound s = flightSoundFor(rating);
            if (s != null) {
                activeFlightId    = s.loop(0f);
                activeFlightSound = s;
            }
        }
        activeFlightRating  = rating;
        flightTimeElapsed   = 0f;
        currentFlightVolume = 0f;
        // Whoosh layer always plays regardless of arcade-sounds setting
        if (flightWhoosh != null) {
            flightWhooshId      = flightWhoosh.loop(0f);
            currentWhooshVolume = 0f;
        }
    }

    /**
     * Update the in-flight sound every frame. Call while ball is airborne; pass {@code isAirborne=false}
     * to stop immediately.
     *
     * @param delta      frame time in seconds
     * @param speed      current ball speed (world units/s)
     * @param isAirborne true while ball state is AIR
     * @param cameraPos  listener position for spatial attenuation
     * @param ballPos    current ball world position
     */
    public void updateFlightSound(float delta, float speed, boolean isAirborne,
                                  Vector3 cameraPos, Vector3 ballPos) {
        if (activeFlightRating == null) return;
        if (!isAirborne) {
            stopFlightSound();
            return;
        }

        flightTimeElapsed += delta;
        float spatial = calcSpatialVolume(cameraPos, ballPos);
        float pitch   = MathUtils.lerp(0.8f, 1.3f, MathUtils.clamp(speed / FLIGHT_SPEED_MAX, 0f, 1f));

        // Whoosh layer: smoothstep on speed so it tapers continuously rather than cutting off
        if (flightWhoosh != null && flightWhooshId != -1) {
            float t = MathUtils.clamp(speed / FLIGHT_SPEED_MAX, 0f, 1f);
            float smoothT = t * t * (3f - 2f * t); // smoothstep S-curve
            float whooshTarget = smoothT * spatial * sfxVolume * masterVolume * WHOOSH_MAX_VOLUME * airWhooshScale;
            currentWhooshVolume = lerp(currentWhooshVolume, whooshTarget, delta * WHOOSH_VOLUME_LERP);
            flightWhoosh.setVolume(flightWhooshId, currentWhooshVolume);
            flightWhoosh.setPitch(flightWhooshId, pitch);
        }

        // Rated sound layer: only active above the speed threshold
        if (activeFlightSound != null && activeFlightId != -1) {
            float speedFrac  = MathUtils.clamp((speed - FLIGHT_SPEED_MIN) / (FLIGHT_SPEED_MAX - FLIGHT_SPEED_MIN), 0f, 1f);
            float ratingScale;
            switch (activeFlightRating) {
                case GOOD:      ratingScale = 0.35f;  break;
                case GREAT:     ratingScale = 0.825f; break; // +10%
                case SUPER:     ratingScale = 0.78f;  break; // +30%
                case PERFECTION: ratingScale = 0.975f; break; // +30%
                default:        ratingScale = 0.50f; break;
            }
            float baseVolume  = speedFrac * speedFrac * spatial * sfxVolume * masterVolume * ratingScale * arcadeAirborneScale;
            float volume;

            if (activeFlightRating == MinigameResult.Rating.PERFECTION) {
                float sharpPulse = (float) Math.pow(Math.max(0f, MathUtils.sin(flightTimeElapsed * FLIGHT_PULSE_FREQ)), 1.5f);
                float variation  = 0.5f + 0.5f * Math.abs(MathUtils.sin(flightTimeElapsed * 2.3f));
                volume = baseVolume * (0.08f + 0.92f * sharpPulse * variation);
                currentFlightVolume = volume;
            } else {
                currentFlightVolume = lerp(currentFlightVolume, baseVolume, delta * FLIGHT_VOLUME_LERP);
                volume = currentFlightVolume;
            }

            activeFlightSound.setVolume(activeFlightId, volume);
            activeFlightSound.setPitch(activeFlightId, pitch);
        }
    }

    /** Stop the in-flight sound immediately. */
    public void stopFlightSound() {
        if (activeFlightSound != null && activeFlightId != -1) activeFlightSound.stop(activeFlightId);
        if (flightWhoosh != null && flightWhooshId != -1) flightWhoosh.stop(flightWhooshId);
        activeFlightSound   = null;
        activeFlightId      = -1;
        activeFlightRating  = null;
        flightTimeElapsed   = 0f;
        currentFlightVolume = 0f;
        flightWhooshId      = -1;
        currentWhooshVolume = 0f;
    }

    private Sound flightSoundFor(MinigameResult.Rating rating) {
        if (rating == null) return null;
        switch (rating) {
            case PERFECTION: return flightPerfect;
            case SUPER:      return flightSuper;
            case GREAT:      return flightGreat;
            case GOOD:       return flightGood;
            default:         return null; // POOR/TERRIBLE/ABYSMAL get no flight sound
        }
    }

    /**
     * Immediately stop the active roll sound. Call on hole-out, ball reset, etc.
     */
    public void stopRollSound() {
        if (activeRollSound != null && activeRollId != -1) activeRollSound.stop(activeRollId);
        if (activeRollSoundB != null && activeRollIdB != -1) activeRollSoundB.stop(activeRollIdB);
        activeRollSound = null;
        activeRollId = -1;
        activeRollSoundB = null;
        activeRollIdB = -1;
        activeRollSurface = null;
        rollLayerTimer = 0f;
        rollLayerBStarted = false;
        rollPitchOffsetA = 0f;
        rollPitchOffsetB = 0f;
        stoneGranularTimer = 0f;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 4 — Multiplayer remote shot audio
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Play a remote player's ball strike, attenuated by distance from camera.
     * Also schedules echoes if reverb is enabled.
     *
     * @param clubCategory  "driver", "iron", "wedge", or "putter"
     * @param powerFraction 0.0–1.0
     * @param cameraPos     listener
     * @param remotePos     remote ball spawn position
     */
    public void playRemoteBallStrike(String clubCategory, float powerFraction,
                                     Vector3 cameraPos, Vector3 remotePos, float pan) {
        Sound s = pickRandom(poolFor(clubCategory));
        if (s == null) return;

        float spatial = calcSpatialVolume(cameraPos, remotePos);
        float volume = MathUtils.clamp(0.35f + powerFraction * 0.65f, 0.35f, 1f)
                * spatial * sfxVolume * masterVolume * 0.75f;
        float pitchBase = MathUtils.lerp(1.15f, 0.9f, powerFraction);
        float pitch = pitchBase + MathUtils.random(-PITCH_VARIANCE * 0.5f, PITCH_VARIANCE * 0.5f);

        s.play(volume, pitch, pan);
        scheduleEchoes(s, volume, pitch);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Phase 4 — Remote player spatial audio
    // ────────────────────────────────────────────────────────────────────────

    private static class RemoteRollState {
        Sound soundA = null, soundB = null;
        long  idA = -1, idB = -1;
        float layerTimer = 0f;
        boolean layerBStarted = false;
        Terrain.TerrainType surface = null;
        float stoneGranularTimer = 0f;
    }
    private final java.util.Map<String, Long>            remoteWhooshIds  = new java.util.HashMap<>();
    private final java.util.Map<String, RemoteRollState> remoteRollStates = new java.util.HashMap<>();

    /** Start a per-player flight whoosh loop at zero volume. */
    public void startRemoteWhoosh(String uid) {
        stopRemoteWhoosh(uid);
        if (flightWhoosh == null) return;
        remoteWhooshIds.put(uid, flightWhoosh.loop(0f));
    }

    /** Update volume/pitch/pan of an active remote whoosh each frame. */
    public void updateRemoteWhoosh(String uid, float speed, Vector3 cameraPos, Vector3 ballPos, float pan) {
        Long id = remoteWhooshIds.get(uid);
        if (flightWhoosh == null || id == null) return;
        float spatial = calcSpatialVolume(cameraPos, ballPos);
        float t       = MathUtils.clamp(speed / FLIGHT_SPEED_MAX, 0f, 1f);
        float smoothT = t * t * (3f - 2f * t);
        float vol     = smoothT * spatial * sfxVolume * masterVolume * WHOOSH_MAX_VOLUME * airWhooshScale;
        flightWhoosh.setVolume(id, vol);
        flightWhoosh.setPitch(id, MathUtils.lerp(0.8f, 1.3f, t));
        flightWhoosh.setPan(id, pan, vol);
    }

    public void stopRemoteWhoosh(String uid) {
        Long id = remoteWhooshIds.remove(uid);
        if (flightWhoosh != null && id != null) flightWhoosh.stop(id);
    }

    /** Spatially-panned bounce for a remote ball. */
    public void playRemoteBounce(Vector3 cameraPos, Vector3 bouncePos, float impactSpeed,
                                  Terrain.TerrainType surface, float pan) {
        Sound s = pickRandom(bouncePoolFor(surface));
        if (s == null) return;
        float spatial     = calcSpatialVolume(cameraPos, bouncePos);
        float velFrac     = MathUtils.clamp(impactSpeed / 20f, 0.1f, 1f);
        float surfScale   = (surface == Terrain.TerrainType.STONE) ? 3.0f : 1f;
        float volume      = velFrac * spatial * sfxVolume * masterVolume * surfScale * bounceScale;
        float pitch       = 1f + MathUtils.random(-PITCH_VARIANCE, PITCH_VARIANCE);
        s.play(volume, pitch, pan);
        scheduleEchoes(s, volume, pitch);
    }

    /** Update the continuous roll sound for a remote ball. Call every frame while the ball moves. */
    public void updateRemoteRoll(String uid, float delta, float speed, Vector3 cameraPos,
                                  Vector3 ballPos, float pan, Terrain.TerrainType surface) {
        Array<Sound> pool = rollPoolFor(surface);
        if (pool.isEmpty()) { stopRemoteRoll(uid); return; }

        float spatial    = calcSpatialVolume(cameraPos, ballPos);
        float speedFrac  = MathUtils.clamp(speed / ROLL_MAX_SPEED, 0f, 1f);
        float targetVol  = speed > ROLL_STOP_THRESHOLD
                ? speedFrac * spatial * sfxVolume * masterVolume : 0f;

        if (targetVol <= 0f) { stopRemoteRoll(uid); return; }

        RemoteRollState rs = remoteRollStates.computeIfAbsent(uid, k -> new RemoteRollState());

        // Stone: granular contact bursts instead of a loop
        if (surface == Terrain.TerrainType.STONE) {
            if (rs.idA != -1 && rs.soundA != null) { rs.soundA.stop(rs.idA); rs.soundA = null; rs.idA = -1; }
            rs.surface = Terrain.TerrainType.STONE;
            rs.stoneGranularTimer += delta;
            float interval = MathUtils.lerp(0.07f, 0.0275f, speedFrac);
            if (rs.stoneGranularTimer >= interval) {
                rs.stoneGranularTimer -= interval;
                Sound s = pickRandom(bounceStone);
                if (s != null) s.play(targetVol * MathUtils.random(0.11f, 0.22f),
                        0.7f + speedFrac * 0.5f + MathUtils.random(-0.12f, 0.14f), pan);
            }
            return;
        }
        rs.stoneGranularTimer = 0f;

        float pitch    = 0.8f + speedFrac * 0.4f;
        float layerVol = targetVol * ROLL_LAYER_VOLUME;
        boolean surfaceChanged = surface != rs.surface;

        if (rs.idA == -1 || surfaceChanged) {
            if (rs.soundA != null && rs.idA != -1) rs.soundA.stop(rs.idA);
            if (rs.soundB != null && rs.idB != -1) rs.soundB.stop(rs.idB);
            rs.soundA = null; rs.idA = -1; rs.soundB = null; rs.idB = -1;
            rs.layerTimer = 0f; rs.layerBStarted = false; rs.surface = surface;
            rs.soundA = pickRandom(pool);
            if (rs.soundA != null) rs.idA = rs.soundA.loop(layerVol, pitch, pan);
        } else {
            rs.soundA.setVolume(rs.idA, layerVol);
            rs.soundA.setPitch(rs.idA, pitch);
            rs.soundA.setPan(rs.idA, pan, layerVol);
        }

        rs.layerTimer += delta;
        if (!rs.layerBStarted && rs.layerTimer >= ROLL_LAYER_OFFSET) {
            rs.soundB = pickRandom(pool);
            if (rs.soundB != null) rs.idB = rs.soundB.loop(layerVol, pitch, pan);
            rs.layerBStarted = true;
        }
        if (rs.soundB != null && rs.idB != -1) {
            rs.soundB.setVolume(rs.idB, layerVol);
            rs.soundB.setPitch(rs.idB, pitch);
            rs.soundB.setPan(rs.idB, pan, layerVol);
        }
    }

    public void stopRemoteRoll(String uid) {
        RemoteRollState rs = remoteRollStates.remove(uid);
        if (rs == null) return;
        if (rs.soundA != null && rs.idA != -1) rs.soundA.stop(rs.idA);
        if (rs.soundB != null && rs.idB != -1) rs.soundB.stop(rs.idB);
    }

    /** Stop all remote audio — call on hole exit or game stop. */
    public void stopAllRemoteAudio() {
        for (String uid : new java.util.ArrayList<>(remoteWhooshIds.keySet())) stopRemoteWhoosh(uid);
        for (String uid : new java.util.ArrayList<>(remoteRollStates.keySet())) stopRemoteRoll(uid);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Volume controls
    // ────────────────────────────────────────────────────────────────────────

    public void setMasterVolume(float v) {
        masterVolume = clamp01(v);
    }

    public void setSfxVolume(float v) {
        sfxVolume = clamp01(v);
    }

    public void setAmbientVolume(float v) {
        ambientVolume = clamp01(v);
    }

    public float getMasterVolume() {
        return masterVolume;
    }

    public float getSfxVolume() {
        return sfxVolume;
    }

    public float getAmbientVolume() {
        return ambientVolume;
    }

    /** Set the terrain ground-level reference for altitude-based wind boost.
     *  Pass {@code (tee.y + hole.y) / 2f} once when the hole terrain is ready. */
    public void setWindGroundLevel(float groundY) { windGroundLevel = groundY; }

    public void  setBounceScale(float v)         { bounceScale         = clamp01(v); }
    public float getBounceScale()                { return bounceScale; }
    public void  setArcadeAirborneScale(float v) { arcadeAirborneScale = clamp01(v); }
    public float getArcadeAirborneScale()        { return arcadeAirborneScale; }
    public void  setAirWhooshScale(float v)      { airWhooshScale      = clamp01(v); }
    public float getAirWhooshScale()             { return airWhooshScale; }
    public void  setAmbientTreesScale(float v)   { ambientTreesScale   = clamp01(v); }
    public float getAmbientTreesScale()          { return ambientTreesScale; }
    public void  setAmbientWaterScale(float v)   { ambientWaterScale   = clamp01(v); }
    public float getAmbientWaterScale()          { return ambientWaterScale; }
    public void  setBirdsongScale(float v)       { birdsongScale       = clamp01(v); }
    public float getBirdsongScale()              { return birdsongScale; }

    // ────────────────────────────────────────────────────────────────────────
    // App lifecycle (Android)
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Call from ApplicationListener.pause().
     * LibGDX automatically pauses Music, but Sound loops must be paused manually.
     */
    public void onAppPause() {
        if (windActiveSound != null && windActiveId != -1) windActiveSound.pause(windActiveId);
        if (windNextSound   != null && windNextId   != -1) windNextSound.pause(windNextId);
        if (activeWaterSound   != null && waterLoopId   != -1) activeWaterSound.pause(waterLoopId);
        if (activeFoliageSound != null && foliageLoopId != -1) activeFoliageSound.pause(foliageLoopId);
        if (woodCreakSound != null && woodCreakLoopId != -1) woodCreakSound.pause(woodCreakLoopId);
        if (activeRollSound != null && activeRollId != -1) activeRollSound.pause(activeRollId);
        if (activeRollSoundB != null && activeRollIdB != -1) activeRollSoundB.pause(activeRollIdB);
    }

    /**
     * Call from ApplicationListener.resume().
     * Resumes Sound loops that were paused by onAppPause().
     */
    public void onAppResume() {
        if (windActiveSound != null && windActiveId != -1) windActiveSound.resume(windActiveId);
        if (windNextSound   != null && windNextId   != -1) windNextSound.resume(windNextId);
        if (activeWaterSound   != null && waterLoopId   != -1) activeWaterSound.resume(waterLoopId);
        if (activeFoliageSound != null && foliageLoopId != -1) activeFoliageSound.resume(foliageLoopId);
        if (woodCreakSound != null && woodCreakLoopId != -1) woodCreakSound.resume(woodCreakLoopId);
        if (activeRollSound != null && activeRollId != -1) activeRollSound.resume(activeRollId);
        if (activeRollSoundB != null && activeRollIdB != -1) activeRollSoundB.resume(activeRollIdB);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Dispose
    // ────────────────────────────────────────────────────────────────────────

    @Override
    public void dispose() {
        for (Music m : birdsongTracks) safeDispose(m);
        for (Sound s : buttonDownSounds) safeDispose(s);
        for (Sound s : buttonUpSounds) safeDispose(s);
        for (Sound s : windLowSounds)  safeDispose(s);
        for (Sound s : windHighSounds) safeDispose(s);
        for (Sound s : waterSounds)   safeDispose(s);
        for (Sound s : foliageSounds) safeDispose(s);
        safeDispose(woodCreakSound);
        safeDispose(flightGood);
        safeDispose(flightGreat);
        safeDispose(flightSuper);
        safeDispose(flightPerfect);
        safeDispose(flightWhoosh);
        safeDispose(cupSound);
        for (Sound s : driverSounds) safeDispose(s);
        for (Sound s : ironSounds) safeDispose(s);
        for (Sound s : wedgeSounds) safeDispose(s);
        for (Sound s : putterSounds) safeDispose(s);
        for (Sound s : bounceGrass) safeDispose(s);
        for (Sound s : bounceMud) safeDispose(s);
        for (Sound s : bounceSand) safeDispose(s);
        for (Sound s : bounceStone) safeDispose(s);
        for (Sound s : bounceWood) safeDispose(s);
        for (Sound s : rollGrass) safeDispose(s);
        for (Sound s : rollMud) safeDispose(s);
        for (Sound s : rollSand) safeDispose(s);
        for (Sound s : rollStone) safeDispose(s);
        for (Sound s : splashSounds) safeDispose(s);
        for (Sound s : foliageRustleSounds) safeDispose(s);
        for (Sound s : twigSnapSounds) safeDispose(s);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private — echo queue
    // ────────────────────────────────────────────────────────────────────────

    /**
     * If reverb is enabled, add 1–2 delayed echo entries to the queue.
     * The echo queue is processed each frame in updateEchoQueue().
     */
    private void scheduleEchoes(Sound sound, float originalVolume, float originalPitch) {
        if (!reverbEnabled) return;
        float echoVol1 = originalVolume * ECHO_VOLUME_1;
        float echoVol2 = originalVolume * ECHO_VOLUME_2;
        float echoPitch = originalPitch * ECHO_PITCH_FACTOR;
        if (echoVol1 > 0.01f) echoQueue.add(new PendingEcho(sound, echoVol1, echoPitch, ECHO_DELAY_1));
        if (echoVol2 > 0.01f)
            echoQueue.add(new PendingEcho(sound, echoVol2, echoPitch * ECHO_PITCH_FACTOR, ECHO_DELAY_2));
    }

    private void updateEchoQueue(float delta) {
        for (int i = echoQueue.size - 1; i >= 0; i--) {
            PendingEcho e = echoQueue.get(i);
            e.timeLeft -= delta;
            if (e.timeLeft <= 0f) {
                e.sound.play(e.volume, e.pitch, 0f);
                echoQueue.removeIndex(i);
            }
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private — wind
    // ────────────────────────────────────────────────────────────────────────

    private void startWindLoops() {
        if (windLowSounds.isEmpty() && windHighSounds.isEmpty()) return;
        windActiveIsHigh = false;
        windActiveSound  = pickRandom(windLowSounds.isEmpty() ? windHighSounds : windLowSounds);
        windActivePitch  = 1f + MathUtils.random(-0.08f, 0.08f);
        windActiveId     = windActiveSound != null ? windActiveSound.loop(0f, windActivePitch, 0f) : -1;
        windNextSound    = null;
        windNextId       = -1;
        windXfading      = false;
        windXfadeT       = 0f;
        windLayerTimer   = MathUtils.random(WIND_LAYER_MIN, WIND_LAYER_MAX);

        windGustTimer    = MathUtils.random(WIND_GUST_INTERVAL_MIN, WIND_GUST_INTERVAL_MAX);
        windGusting      = false;
        windGustT        = 0f;
        windBreathTimer  = MathUtils.random(WIND_BREATH_INTERVAL_MIN, WIND_BREATH_INTERVAL_MAX);
        windBreathing    = false;
        windBreathT      = 0f;

        windVolumeCurrent = WIND_MIN_VOLUME;
        windPitchCurrent  = 1f;
    }

    private void updateWind(float delta, Vector3 wind, Vector3 cameraPos) {
        if (windLowSounds.isEmpty() && windHighSounds.isEmpty()) return;
        float speed = wind.len();
        currentWindSpeed = speed;
        windTime += delta;

        // ── Layer crossfade timer ─────────────────────────────────────────────
        windLayerTimer -= delta;
        if (windLayerTimer <= 0f && !windXfading) {
            Array<Sound> pool = pickWindPool(speed);
            Sound next = pickRandom(pool);
            if (next != null) {
                windNextSound  = next;
                windNextIsHigh = (pool == windHighSounds);
                windNextPitch  = 1f + MathUtils.random(-0.08f, 0.08f);
                windNextId     = windNextSound.loop(0f, windNextPitch, 0f);
                windXfading    = true;
                windXfadeT     = 0f;
            }
            windLayerTimer = MathUtils.random(WIND_LAYER_MIN, WIND_LAYER_MAX);
        }
        if (windXfading) {
            windXfadeT += delta / WIND_XFADE_DURATION;
            if (windXfadeT >= 1f) {
                if (windActiveSound != null && windActiveId != -1) windActiveSound.stop(windActiveId);
                windActiveSound  = windNextSound;
                windActiveId     = windNextId;
                windActivePitch  = windNextPitch;
                windActiveIsHigh = windNextIsHigh;
                windNextSound    = null;
                windNextId       = -1;
                windXfading      = false;
                windXfadeT       = 0f;
            }
        }

        // ── Gust events ───────────────────────────────────────────────────────
        if (!windGusting) {
            windGustTimer -= delta;
            if (windGustTimer <= 0f) {
                windGusting      = true;
                windGustT        = 0f;
                windGustDuration = MathUtils.random(WIND_GUST_DURATION_MIN, WIND_GUST_DURATION_MAX);
                windGustPeak     = MathUtils.random(0.2f, WIND_GUST_MAX_BOOST);
                windGustTimer    = MathUtils.random(WIND_GUST_INTERVAL_MIN, WIND_GUST_INTERVAL_MAX);
            }
        }
        float gustBoost = 0f;
        if (windGusting) {
            windGustT += delta / windGustDuration;
            if (windGustT >= 1f) {
                windGusting = false;
                windGustT   = 0f;
            } else {
                // Triangle envelope: quick rise in first 20%, slow decay the rest
                float env = windGustT < 0.2f
                        ? windGustT / 0.2f
                        : 1f - (windGustT - 0.2f) / 0.8f;
                gustBoost = windGustPeak * env;
            }
        }

        // ── Breath events (quiet periods) ─────────────────────────────────────
        if (!windBreathing) {
            windBreathTimer -= delta;
            if (windBreathTimer <= 0f) {
                windBreathing      = true;
                windBreathT        = 0f;
                windBreathDuration = MathUtils.random(WIND_BREATH_DURATION_MIN, WIND_BREATH_DURATION_MAX);
                windBreathTimer    = MathUtils.random(WIND_BREATH_INTERVAL_MIN, WIND_BREATH_INTERVAL_MAX);
            }
        }
        float breathMul = 1f;
        if (windBreathing) {
            windBreathT += delta / windBreathDuration;
            if (windBreathT >= 1f) {
                windBreathing = false;
                windBreathT   = 0f;
            } else {
                // Cosine envelope: smooth fade to quiet at midpoint, recover by end
                float cos = (float) Math.cos(windBreathT * Math.PI * 2.0);
                breathMul = MathUtils.lerp(WIND_BREATH_ATTENUATION, 1f, (cos + 1f) * 0.5f);
            }
        }

        // ── Target volume and pitch ───────────────────────────────────────────
        // Fade wind in from 0 m/s — naturally inaudible at dead calm, soft breeze at ~3–4 m/s
        float windFadeIn = MathUtils.clamp(speed / 10f, 0f, 1f);
        float windFrac   = MathUtils.clamp(speed / 50f, 0f, 1f);
        float altAboveGround  = Math.max(0f, cameraPos.y - windGroundLevel);
        float altitudeFactor  = MathUtils.clamp(altAboveGround / WIND_ALTITUDE_REF, 0f, 1f)
                * WIND_ALTITUDE_CONTRIBUTION;
        float targetVolume = MathUtils.clamp(
                WIND_MIN_VOLUME + windFrac + altitudeFactor, WIND_MIN_VOLUME, 1f)
                * windFadeIn * ambientVolume * masterVolume * 0.7f;
        float targetPitch = MathUtils.clamp(0.8f + windFrac * 0.4f, 0.8f, 1.3f);

        windVolumeCurrent = lerp(windVolumeCurrent, targetVolume, delta * 2f);
        windPitchCurrent  = lerp(windPitchCurrent,  targetPitch,  delta * 1f);

        // Three-wave aperiodic modulation — three irrational-ratio frequencies give an
        // effectively non-repeating envelope for the duration of a hole.
        float freq1 = lerp(0.12f, 0.45f, windFrac);
        float freq2 = lerp(0.19f, 0.73f, windFrac);
        float freq3 = lerp(0.05f, 0.18f, windFrac);   // very slow roll
        float depth = lerp(0.15f, 0.42f, windFrac);
        float mod   = (MathUtils.sin(windTime * freq1)
                     + MathUtils.sin(windTime * freq2)
                     + MathUtils.sin(windTime * freq3)) / 3f;
        float baseVol = Math.max(0f, windVolumeCurrent * (1f + depth * mod));

        float gustBoostedVol = baseVol + gustBoost * windFadeIn * ambientVolume * masterVolume;
        float finalVol = gustBoostedVol * breathMul;

        // ── Apply to layers ───────────────────────────────────────────────────
        if (windActiveSound != null && windActiveId != -1) {
            float layerVol = finalVol * (windXfading ? (1f - windXfadeT) : 1f);
            windActiveSound.setVolume(windActiveId, layerVol);
            windActiveSound.setPitch(windActiveId, windPitchCurrent * windActivePitch);
        }
        if (windXfading && windNextSound != null && windNextId != -1) {
            windNextSound.setVolume(windNextId, finalVol * windXfadeT);
            windNextSound.setPitch(windNextId, windPitchCurrent * windNextPitch);
        }

        // ── Birdsong: full volume below 3 m/s, silent at and above 7 m/s ─────
        if (activeGameBirdsong != null && gameBirdsongActive) {
            // Birdsong: full at ≤2 m/s, gone by 6 m/s — overlaps lightly with breeze at 3–4
            float birdsongFade = 1f - MathUtils.clamp((speed - 2f) / 4f, 0f, 1f);
            activeGameBirdsong.setVolume(birdsongFade * GAME_BIRDSONG_VOLUME * ambientVolume * masterVolume * birdsongScale);
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Private — spatial / lifecycle helpers
    // ────────────────────────────────────────────────────────────────────────

    private void startBirdsong(int index, float initialVolume) {
        if (birdsongTracks.isEmpty()) return;
        Music m = birdsongTracks.get(index);
        m.setVolume(initialVolume * BIRDSONG_VOLUME * ambientVolume * masterVolume * birdsongScale);
        m.setLooping(false);
        m.play();
    }

    private Array<Sound> pickWindPool(float speed) {
        if (speed >= WIND_SPEED_HIGH_MIN && !windHighSounds.isEmpty()) return windHighSounds;
        if (speed <= WIND_SPEED_LOW_MAX  && !windLowSounds.isEmpty())  return windLowSounds;
        if (windLowSounds.isEmpty())  return windHighSounds;
        if (windHighSounds.isEmpty()) return windLowSounds;
        // Transition zone 8–13 m/s: weighted random — higher speed favours high pool
        float t = (speed - WIND_SPEED_LOW_MAX) / (WIND_SPEED_HIGH_MIN - WIND_SPEED_LOW_MAX);
        return MathUtils.random() < t ? windHighSounds : windLowSounds;
    }

    private void startSpatialLoops() {
        activeWaterSound   = pickRandom(waterSounds);
        activeFoliageSound = pickRandom(foliageSounds);
        if (activeWaterSound   != null) waterLoopId   = activeWaterSound.loop(0f);
        if (activeFoliageSound != null) foliageLoopId = activeFoliageSound.loop(0f);
        if (woodCreakSound     != null) woodCreakLoopId = woodCreakSound.loop(0f);
    }

    private void startGameBirdsong() {
        if (birdsongTracks.isEmpty()) return;
        int idx = MathUtils.random(birdsongTracks.size - 1);
        activeGameBirdsong = birdsongTracks.get(idx);
        activeGameBirdsong.setLooping(true);
        activeGameBirdsong.setVolume(GAME_BIRDSONG_VOLUME * ambientVolume * masterVolume);
        activeGameBirdsong.play();
        gameBirdsongActive = true;
    }

    private void setSpatialVolume(Sound sound, long id, Vector3 listener, Vector3 source) {
        if (sound == null || id == -1) return;
        float vol = source != null
                ? calcSpatialVolume(listener, source) * ambientVolume * masterVolume
                : 0f;
        sound.setVolume(id, vol);
    }

    private void stopAllMenuSounds() {
        for (Music m : birdsongTracks) m.stop();
        menuActive = false;
        birdsongFading = false;
    }

    private void stopAllGameSounds() {
        for (Sound s : windLowSounds)  s.stop();
        for (Sound s : windHighSounds) s.stop();
        if (windActiveSound != null) { windActiveSound.stop(windActiveId); windActiveSound = null; }
        windActiveId = -1;
        if (windNextSound != null) { windNextSound.stop(windNextId); windNextSound = null; }
        windNextId  = -1;
        windXfading = false;
        if (activeWaterSound != null) {
            activeWaterSound.stop(waterLoopId);
            waterLoopId = -1;
            activeWaterSound = null;
        }
        if (activeFoliageSound != null) {
            activeFoliageSound.stop(foliageLoopId);
            foliageLoopId = -1;
            activeFoliageSound = null;
        }
        for (Music m : birdsongTracks) m.stop();
        gameBirdsongActive = false;
        if (woodCreakSound != null) {
            woodCreakSound.stop(woodCreakLoopId);
            woodCreakLoopId = -1;
        }
        stopFlightSound();
        stopRollSound();
        stopAllRemoteAudio();
        for (int i = 0; i < activeFolSounds.size; i++) activeFolSounds.get(i).stop(activeFolIds.get(i));
        activeFolSounds.clear();
        activeFolIds.clear();
        activeFolVols.clear();
        foliageFadeTimer = 0f;
        echoQueue.clear();
    }

    private float calcSpatialVolume(Vector3 listener, Vector3 source) {
        float dist = listener.dst(source);
        if (dist >= SPATIAL_MAX_DISTANCE) return 0f;
        // t = refDist / dist (clamped to 1 within the reference radius).
        // This gives inverse-distance attenuation: 1.0 up close, 0.5 at 2×ref, 0.25 at 4×ref.
        // Raising to ROLLOFF > 1 steepens the falloff toward inverse-square.
        float t = Math.min(1f, SPATIAL_REF_DISTANCE / Math.max(dist, 0.01f));
        return (float) Math.pow(t, SPATIAL_ROLLOFF);
    }

    private float randomPitch() {
        return 1f + MathUtils.random(-PITCH_VARIANCE, PITCH_VARIANCE);
    }

    private void playWithVariance(Sound sound, float baseVolume) {
        if (sound == null) return;
        sound.play(baseVolume * sfxVolume * masterVolume, randomPitch(), 0f);
    }

    private void setMusicVolume(Music m, float fraction) {
        m.setVolume(MathUtils.clamp(fraction, 0f, 1f) * BIRDSONG_VOLUME * ambientVolume * masterVolume * birdsongScale);
    }

    private Array<Sound> poolFor(String clubCategory) {
        switch (clubCategory) {
            case "driver":
                return driverSounds;
            case "wedge":
                return wedgeSounds;
            case "putter":
                return putterSounds;
            default:
                return ironSounds;
        }
    }

    private Array<Sound> bouncePoolFor(Terrain.TerrainType surface) {
        if (surface == null) return bounceGrass;
        switch (surface) {
            case MUD:
                return bounceMud;
            case SAND:
                return bounceSand;
            case STONE:
                return bounceStone;
            default:
                return bounceGrass; // TEE, FAIRWAY, FRINGE, ROUGH, DEEP_ROUGH, GREEN
        }
    }

    private Array<Sound> rollPoolFor(Terrain.TerrainType surface) {
        if (surface == null) return rollGrass;
        switch (surface) {
            case MUD:
                return rollMud;
            case SAND:
                return rollSand;
            case STONE:
                return rollStone;
            default:
                return rollGrass; // TEE, FAIRWAY, FRINGE, ROUGH, DEEP_ROUGH, GREEN
        }
    }

    private <T> T pickRandom(Array<T> arr) {
        if (arr == null || arr.isEmpty()) return null;
        return arr.get(MathUtils.random(arr.size - 1));
    }

    private float lerp(float a, float b, float t) {
        return a + (b - a) * MathUtils.clamp(t, 0f, 1f);
    }

    private float clamp01(float v) {
        return MathUtils.clamp(v, 0f, 1f);
    }

    // ── Asset loading ────────────────────────────────────────────────────────

    private void loadMusicList(Array<Music> out, String prefix, int maxCount) {
        for (int i = 1; i <= maxCount; i++) {
            String path = prefix + i + ".wav";
            if (!Gdx.files.internal(path).exists()) break;
            try {
                out.add(Gdx.audio.newMusic(Gdx.files.internal(path)));
            } catch (Exception e) {
                Gdx.app.log("SoundManager", "Failed to load music: " + path + " — " + e.getMessage());
            }
        }
    }

    private void loadSoundDir(Array<Sound> out, String dir) {
        com.badlogic.gdx.files.FileHandle dirHandle = Gdx.files.internal(dir);
        if (!dirHandle.exists()) return;
        for (com.badlogic.gdx.files.FileHandle file : dirHandle.list(".wav")) {
            Sound s = loadSound(file.path());
            if (s != null) out.add(s);
        }
    }

    private void loadSoundList(Array<Sound> out, String prefix, int maxCount) {
        for (int i = 1; i <= maxCount; i++) {
            String path = prefix + i + ".wav";
            if (!Gdx.files.internal(path).exists()) break;
            Sound s = loadSound(path);
            if (s != null) out.add(s);
        }
    }

    private Sound loadSound(String path) {
        if (!Gdx.files.internal(path).exists()) return null;
        try {
            return Gdx.audio.newSound(Gdx.files.internal(path));
        } catch (Exception e) {
            Gdx.app.log("SoundManager", "Failed to load sound: " + path + " — " + e.getMessage());
            return null;
        }
    }

    /**
     * Like loadSound but ensures the result is mono.  Stereo WAV files break
     * LibGDX spatial panning (the engine plays raw channel data, so a left-heavy
     * stereo file always comes out of the left speaker).  This mixes 16-bit stereo
     * PCM down to mono at load time; other formats are loaded as-is.
     */
    private Sound loadSoundMono(String path) {
        com.badlogic.gdx.files.FileHandle fh = Gdx.files.internal(path);
        if (!fh.exists()) return null;
        try {
            byte[] mixed = mixStereoToMono(fh.readBytes());
            if (mixed == null) {
                // Already mono (or unrecognised format) — load directly
                return Gdx.audio.newSound(fh);
            }
            // Write the mono data to a temp local file, load it, then delete
            com.badlogic.gdx.files.FileHandle tmp =
                Gdx.files.local("tmp_mono_" + fh.name());
            tmp.writeBytes(mixed, false);
            Sound s = Gdx.audio.newSound(tmp);
            tmp.delete();
            return s;
        } catch (Exception e) {
            Gdx.app.log("SoundManager", "loadSoundMono failed for " + path + ": " + e.getMessage());
            return loadSound(path);
        }
    }

    /**
     * Converts a 16-bit stereo PCM WAV to mono by averaging the two channels.
     * Returns null if the data is already mono, not PCM, or not 16-bit.
     */
    private byte[] mixStereoToMono(byte[] wav) {
        if (wav.length < 44) return null;
        // Verify RIFF/WAVE magic
        if (wav[0]!='R'||wav[1]!='I'||wav[2]!='F'||wav[3]!='F') return null;
        if (wav[8]!='W'||wav[9]!='A'||wav[10]!='V'||wav[11]!='E') return null;

        int audioFormat   = (wav[20] & 0xFF) | ((wav[21] & 0xFF) << 8); // 1 = PCM
        int channels      = (wav[22] & 0xFF) | ((wav[23] & 0xFF) << 8);
        int sampleRate    = (wav[24]&0xFF)|((wav[25]&0xFF)<<8)|((wav[26]&0xFF)<<16)|((wav[27]&0xFF)<<24);
        int bitsPerSample = (wav[34] & 0xFF) | ((wav[35] & 0xFF) << 8);

        if (audioFormat != 1 || channels != 2 || bitsPerSample != 16) return null;

        // Locate the "data" chunk (may not be at a fixed offset if there are extra chunks)
        int dataOffset = -1, dataSize = -1;
        for (int i = 12; i < wav.length - 8; i++) {
            if (wav[i]=='d'&&wav[i+1]=='a'&&wav[i+2]=='t'&&wav[i+3]=='a') {
                dataSize   = (wav[i+4]&0xFF)|((wav[i+5]&0xFF)<<8)|((wav[i+6]&0xFF)<<16)|((wav[i+7]&0xFF)<<24);
                dataOffset = i + 8;
                break;
            }
        }
        if (dataOffset < 0 || dataSize <= 0) return null;

        int frameCount   = dataSize / 4;          // 4 bytes per stereo 16-bit frame
        int monoDataSize = frameCount * 2;         // 2 bytes per mono 16-bit frame
        int newFileSize  = 44 + monoDataSize;

        byte[] out = new byte[newFileSize];

        // ── Build a fresh 44-byte PCM WAV header ──────────────────────────────
        // RIFF chunk
        out[0]='R'; out[1]='I'; out[2]='F'; out[3]='F';
        int riffSize = newFileSize - 8;
        out[4]=(byte)riffSize; out[5]=(byte)(riffSize>>8);
        out[6]=(byte)(riffSize>>16); out[7]=(byte)(riffSize>>24);
        out[8]='W'; out[9]='A'; out[10]='V'; out[11]='E';
        // fmt  chunk
        out[12]='f'; out[13]='m'; out[14]='t'; out[15]=' ';
        out[16]=16; // chunk size = 16
        out[20]=1;  // PCM
        out[22]=1;  // 1 channel
        out[24]=(byte)sampleRate; out[25]=(byte)(sampleRate>>8);
        out[26]=(byte)(sampleRate>>16); out[27]=(byte)(sampleRate>>24);
        int byteRate = sampleRate * 2;
        out[28]=(byte)byteRate; out[29]=(byte)(byteRate>>8);
        out[30]=(byte)(byteRate>>16); out[31]=(byte)(byteRate>>24);
        out[32]=2;  // block align = 2
        out[34]=16; // bits per sample
        // data chunk
        out[36]='d'; out[37]='a'; out[38]='t'; out[39]='a';
        out[40]=(byte)monoDataSize; out[41]=(byte)(monoDataSize>>8);
        out[42]=(byte)(monoDataSize>>16); out[43]=(byte)(monoDataSize>>24);

        // ── Mix stereo samples ────────────────────────────────────────────────
        for (int i = 0; i < frameCount; i++) {
            int src  = dataOffset + i * 4;
            int left  = (short)((wav[src]   & 0xFF) | ((wav[src+1] & 0xFF) << 8));
            int right = (short)((wav[src+2] & 0xFF) | ((wav[src+3] & 0xFF) << 8));
            int mono  = (left + right) >> 1;
            int dst   = 44 + i * 2;
            out[dst]   = (byte)(mono & 0xFF);
            out[dst+1] = (byte)((mono >> 8) & 0xFF);
        }

        return out;
    }

    private void safeDispose(Sound s) {
        if (s != null) s.dispose();
    }

    private void safeDispose(Music m) {
        if (m != null) m.dispose();
    }
}
