package com.gearygolf.golf.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.gearygolf.golf.FirebaseConfig;
import com.gearygolf.golf.GameConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores and retrieves a player's 18-hole round history in Firestore.
 *
 * Firestore path: users/{uid}/roundHistory/{difficulty}
 * Document field: rounds — array of { score (integerValue), timestamp (integerValue, epoch ms) }
 *
 * Rounds are trimmed to the most recent MAX_ROUNDS entries on each save.
 * calculateHandicap() is pure Java with no network dependency and can be unit tested directly.
 */
public class RoundHistoryService {

    private static final int MAX_ROUNDS      = 20;
    private static final int HANDICAP_BEST_OF = 8;
    private static final int PAR              = 72;

    private static final String BASE_URL =
            "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/users/";

    // ── Data model ────────────────────────────────────────────────────────────

    public static class Round {
        public final int  score;
        public final long timestamp; // epoch ms

        public Round(int score, long timestamp) {
            this.score     = score;
            this.timestamp = timestamp;
        }
    }

    // ── Callbacks ─────────────────────────────────────────────────────────────

    public interface SaveCallback {
        void onSuccess();
        void onFailure(String reason);
    }

    public interface FetchCallback {
        void onSuccess(List<Round> rounds);
        void onFailure(String reason);
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Saves a new round: GET existing → append → trim to MAX_ROUNDS → PATCH.
     * A 404 (no document yet) is treated as an empty list so the first save always works.
     */
    public void saveRound(String uid, String idToken, GameConfig.Difficulty difficulty,
                          int totalScore, SaveCallback callback) {
        String docUrl = BASE_URL + uid + "/roundHistory/" + difficulty.name();
        fetchRoundsFromUrl(docUrl, idToken, new FetchCallback() {
            @Override public void onSuccess(List<Round> existing) {
                doSave(docUrl, idToken, existing, totalScore, callback);
            }
            @Override public void onFailure(String reason) {
                Gdx.app.error("RoundHistory", "GET before save failed: " + reason);
                if (callback != null) callback.onFailure("Fetch failed: " + reason);
            }
        });
    }

    /**
     * Fetches the stored rounds for a given difficulty. Returns an empty list if
     * the player has no history yet at that difficulty.
     */
    public void fetchRounds(String uid, String idToken, GameConfig.Difficulty difficulty,
                            FetchCallback callback) {
        String docUrl = BASE_URL + uid + "/roundHistory/" + difficulty.name();
        fetchRoundsFromUrl(docUrl, idToken, callback);
    }

    // ── Handicap calculation (pure Java — no network, unit-testable) ──────────

    /**
     * Calculates handicap from a list of rounds.
     * Returns null if fewer than HANDICAP_BEST_OF (8) rounds are provided.
     *
     * Formula:
     *   1. Sort rounds by score ascending (lowest score = best round).
     *   2. Average the score-over-par of the best HANDICAP_BEST_OF rounds.
     *   3. Handicap = -(average score over par).
     *
     * Scale: 0 = averaging par, -1 = averaging one over par.
     * Status threshold: handicap > -5 means the player qualifies at this difficulty.
     */
    public static Double calculateHandicap(List<Round> rounds) {
        if (rounds == null || rounds.size() < HANDICAP_BEST_OF) return null;

        List<Round> sorted = new ArrayList<>(rounds);
        sorted.sort((a, b) -> Integer.compare(a.score, b.score)); // ascending = best first

        double sumOverPar = 0;
        for (int i = 0; i < HANDICAP_BEST_OF; i++) {
            sumOverPar += (sorted.get(i).score - PAR);
        }
        return -(sumOverPar / HANDICAP_BEST_OF);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private void doSave(String docUrl, String idToken, List<Round> existing,
                        int totalScore, SaveCallback callback) {
        List<Round> updated = new ArrayList<>(existing);
        updated.add(new Round(totalScore, System.currentTimeMillis()));
        // Newest first so the trim below keeps the most recent MAX_ROUNDS rounds
        updated.sort((a, b) -> Long.compare(b.timestamp, a.timestamp));
        if (updated.size() > MAX_ROUNDS) updated = updated.subList(0, MAX_ROUNDS);
        patchRounds(docUrl, idToken, updated, callback);
    }

    private void fetchRoundsFromUrl(String docUrl, String idToken, FetchCallback callback) {
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.GET);
        request.setUrl(docUrl + "?key=" + FirebaseConfig.API_KEY);
        if (idToken != null && !idToken.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + idToken);
        }
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                int status = r.getStatus().getStatusCode();
                if (status == 404) {
                    // Document doesn't exist yet — no rounds recorded at this difficulty
                    Gdx.app.postRunnable(() -> callback.onSuccess(new ArrayList<>()));
                    return;
                }
                if (status < 200 || status >= 300) {
                    Gdx.app.error("RoundHistory", "GET error " + status);
                    Gdx.app.postRunnable(() -> callback.onFailure("HTTP " + status));
                    return;
                }
                try {
                    List<Round> rounds = parseRoundsDocument(r.getResultAsString());
                    Gdx.app.postRunnable(() -> callback.onSuccess(rounds));
                } catch (Exception e) {
                    Gdx.app.error("RoundHistory", "Parse error: " + e.getMessage());
                    Gdx.app.postRunnable(() -> callback.onFailure("Parse: " + e.getMessage()));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> callback.onFailure(t.getMessage()));
            }
            @Override public void cancelled() {}
        });
    }

    private void patchRounds(String docUrl, String idToken, List<Round> rounds, SaveCallback callback) {
        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.PATCH);
        request.setUrl(docUrl + "?updateMask.fieldPaths=rounds&key=" + FirebaseConfig.API_KEY);
        request.setContent(buildRoundsDocument(rounds));
        request.setHeader("Content-Type", "application/json");
        if (idToken != null && !idToken.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + idToken);
        }
        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                int status = r.getStatus().getStatusCode();
                if (status >= 200 && status < 300) {
                    Gdx.app.log("RoundHistory", "Saved round OK (status " + status + ")");
                    Gdx.app.postRunnable(() -> { if (callback != null) callback.onSuccess(); });
                } else {
                    Gdx.app.error("RoundHistory", "PATCH error " + status + ": " + r.getResultAsString());
                    Gdx.app.postRunnable(() -> { if (callback != null) callback.onFailure("HTTP " + status); });
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> { if (callback != null) callback.onFailure(t.getMessage()); });
            }
            @Override public void cancelled() {}
        });
    }

    // Firestore REST returns integerValue fields as strings for int64; parse accordingly.
    static List<Round> parseRoundsDocument(String json) {
        List<Round> rounds = new ArrayList<>();
        JsonValue doc = new JsonReader().parse(json);
        JsonValue fields = doc.get("fields");
        if (fields == null || !fields.has("rounds")) return rounds;
        JsonValue values = fields.get("rounds").get("arrayValue").get("values");
        if (values == null) return rounds;
        for (JsonValue entry : values) {
            JsonValue map = entry.get("mapValue").get("fields");
            int  score     = Integer.parseInt(map.get("score").getString("integerValue"));
            long timestamp = Long.parseLong(map.get("timestamp").getString("integerValue"));
            rounds.add(new Round(score, timestamp));
        }
        return rounds;
    }

    // Timestamps are int64 so written as quoted strings per Firestore REST convention.
    static String buildRoundsDocument(List<Round> rounds) {
        StringBuilder sb = new StringBuilder("{\"fields\":{\"rounds\":{\"arrayValue\":{\"values\":[");
        for (int i = 0; i < rounds.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"mapValue\":{\"fields\":{");
            sb.append("\"score\":{\"integerValue\":\"").append(rounds.get(i).score).append("\"},");
            sb.append("\"timestamp\":{\"integerValue\":\"").append(rounds.get(i).timestamp).append("\"}");
            sb.append("}}}");
        }
        sb.append("]}}}}");
        return sb.toString();
    }
}
