package org.example.session;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

public class SessionPersistenceFileRoutingTest {

    // --- No-uid (anonymous / standard) filenames ---

    @Test
    public void testStandard18UsesCorrectFile() {
        assertEquals("save_standard.json", SessionPersistence.getFileName(GameSession.GameMode.STANDARD_18));
    }

    @Test
    public void testDaily18UsesCorrectFileWhenNoUid() {
        assertEquals("save_daily_18.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_18));
    }

    @Test
    public void testDaily9UsesCorrectFileWhenNoUid() {
        assertEquals("save_daily_9.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_9));
    }

    @Test
    public void testDaily1UsesCorrectFileWhenNoUid() {
        assertEquals("save_daily_1.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_1));
    }

    @Test
    public void testAllModesHaveUniqueFileNamesWhenNoUid() {
        Set<String> fileNames = new HashSet<>();
        for (GameSession.GameMode mode : GameSession.GameMode.values()) {
            String fileName = SessionPersistence.getFileName(mode);
            assertTrue(fileNames.add(fileName), "Duplicate filename found: " + fileName + " for mode " + mode);
        }
    }

    // --- Uid-scoped daily filenames ---

    @Test
    public void testDaily18IncludesUidInFileName() {
        assertEquals("save_daily_18_abc123.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_18, "abc123"));
    }

    @Test
    public void testDaily9IncludesUidInFileName() {
        assertEquals("save_daily_9_abc123.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_9, "abc123"));
    }

    @Test
    public void testDaily1IncludesUidInFileName() {
        assertEquals("save_daily_1_abc123.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_1, "abc123"));
    }

    @Test
    public void testStandard18IgnoresUid() {
        assertEquals("save_standard.json", SessionPersistence.getFileName(GameSession.GameMode.STANDARD_18, "abc123"));
    }

    @Test
    public void testDifferentUidsProduceDifferentFileNames() {
        String fileA = SessionPersistence.getFileName(GameSession.GameMode.DAILY_1, "userA");
        String fileB = SessionPersistence.getFileName(GameSession.GameMode.DAILY_1, "userB");
        assertNotEquals(fileA, fileB);
    }

    @Test
    public void testNullUidFallsBackToAnonymousFileName() {
        assertEquals("save_daily_1.json", SessionPersistence.getFileName(GameSession.GameMode.DAILY_1, null));
    }
}
