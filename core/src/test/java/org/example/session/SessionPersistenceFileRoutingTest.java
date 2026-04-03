package org.example.session;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPersistenceFileRoutingTest {

    @Test
    public void testStandard18UsesCorrectFile() {
        assertEquals("save_standard.json", SessionPersistence.getFileName(GameSession.GameMode.STANDARD_18));
    }

    @Test
    public void testDaily18UsesCorrectFile() {
        assertEquals("save_daily_18.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_18));
    }

    @Test
    public void testDaily9UsesCorrectFile() {
        assertEquals("save_daily_9.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_9));
    }

    @Test
    public void testDaily1UsesCorrectFile() {
        assertEquals("save_daily_1.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_1));
    }

    @Test
    public void testAllModesHaveUniqueFileNames() {
        Set<String> fileNames = new HashSet<>();
        for (GameSession.GameMode mode : GameSession.GameMode.values()) {
            String fileName = SessionPersistence.getFileName(mode);
            assertTrue(fileNames.add(fileName), "Duplicate filename found: " + fileName + " for mode " + mode);
        }
    }
}
