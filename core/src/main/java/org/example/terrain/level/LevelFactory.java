package org.example.terrain.level;

import com.badlogic.gdx.math.MathUtils;
import org.example.Club;
import org.example.terrain.ClassicGenerator;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.features.PuttingGreenGenerator;
import org.example.terrain.practiceRange.PracticeRangeGenerator;

public class LevelFactory {

    public LevelCreationResult createLevel(GameMode mode, long manualSeed) {
        ITerrainGenerator generator;
        LevelData data = null;
        Club defaultClub;
        float waterLevel;
        int distance;

        switch (mode) {
            case PRACTICE_RANGE:
                generator = new PracticeRangeGenerator();
                defaultClub = Club.DRIVER;
                waterLevel = -2.0f;
                distance = 600;
                break;
            case PUTTING_GREEN:
                generator = new PuttingGreenGenerator(System.nanoTime());
                defaultClub = Club.PUTTER;
                waterLevel = -5.0f;
                distance = 160;
                break;
            default:
                data = (LevelDataGenerator.createFixedLevelData(manualSeed));
                generator = new ClassicGenerator(data);
                defaultClub = Club.DRIVER;
                waterLevel = data.getWaterLevel();
                distance = data.getDistance();
                break;
        }

        return new LevelCreationResult(generator, data, defaultClub, waterLevel, distance);
    }

    public enum GameMode {START, PLAYING, COMPETITIVE, PRACTICE_RANGE, PUTTING_GREEN}

    public static class LevelCreationResult {
        public final ITerrainGenerator generator;
        public final LevelData data;
        public final Club defaultClub;
        public final float waterLevel;
        public final int distance;

        public LevelCreationResult(ITerrainGenerator generator, LevelData data, Club defaultClub, float waterLevel, int distance) {
            this.generator = generator;
            this.data = data;
            this.defaultClub = defaultClub;
            this.waterLevel = waterLevel;
            this.distance = distance;
        }
    }
}
