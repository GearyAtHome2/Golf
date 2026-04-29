package org.example.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import org.example.FirebaseConfig;

public class HighscoreService {

    private static final String API_KEY = FirebaseConfig.API_KEY;

    public interface HighscoreListener {
        void onSuccess(Array<HighscoreEntry> entries);
        void onFailure(Throwable t);
    }

    public static class HighscoreEntry {
        public String name;
        public int score;
        public String difficulty;
        public String date;
        public float elapsedTime;
        public int[] pars;
        public int[] scores;

        public HighscoreEntry(String name, int score, String difficulty, String date) {
            this(name, score, difficulty, date, 0f, null, null);
        }

        public HighscoreEntry(String name, int score, String difficulty, String date, float elapsedTime) {
            this(name, score, difficulty, date, elapsedTime, null, null);
        }

        public HighscoreEntry(String name, int score, String difficulty, String date, float elapsedTime, int[] pars, int[] scores) {
            this.name = name;
            this.score = score;
            this.difficulty = difficulty;
            this.date = date;
            this.elapsedTime = elapsedTime;
            this.pars = pars;
            this.scores = scores;
        }

        public boolean hasScorecard() {
            return pars != null && scores != null && pars.length > 0;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public int getScore() {
            return score;
        }

        public void setScore(int score) {
            this.score = score;
        }

        public String getDifficulty() {
            return difficulty;
        }

        public void setDifficulty(String difficulty) {
            this.difficulty = difficulty;
        }

        public String getDate() {
            return date;
        }

        public void setDate(String date) {
            this.date = date;
        }

        public float getElapsedTime() {
            return elapsedTime;
        }

        public void setElapsedTime(float elapsedTime) {
            this.elapsedTime = elapsedTime;
        }

        public int[] getPars() {
            return pars;
        }

        public void setPars(int[] pars) {
            this.pars = pars;
        }

        public int[] getScores() {
            return scores;
        }

        public void setScores(int[] scores) {
            this.scores = scores;
        }
    }

    public interface CountListener {
        void onSuccess(int count);
        void onFailure(Throwable t);
    }

    /**
     * Builds the Firestore "where" JSON fragment scoped to today.
     * Uses a composite filter (difficulty + timestamp) when difficulty is non-null and courseType is not HOLES_1;
     * otherwise uses a simple timestamp-only filter.
     */
    private static String buildTodayWhereJson(String difficulty, CourseType courseType, String todayStart) {
        if (difficulty == null || courseType == CourseType.HOLES_1) {
            return "\"where\":{\"fieldFilter\":{\"field\":{\"fieldPath\":\"submissionTime\"},"
                 + "\"op\":\"GREATER_THAN_OR_EQUAL\",\"value\":{\"timestampValue\":\"" + todayStart + "\"}}}";
        } else {
            return "\"where\":{\"compositeFilter\":{\"op\":\"AND\",\"filters\":["
                 + "{\"fieldFilter\":{\"field\":{\"fieldPath\":\"difficulty\"},\"op\":\"EQUAL\",\"value\":{\"stringValue\":\"" + difficulty + "\"}}},"
                 + "{\"fieldFilter\":{\"field\":{\"fieldPath\":\"submissionTime\"},\"op\":\"GREATER_THAN_OR_EQUAL\",\"value\":{\"timestampValue\":\"" + todayStart + "\"}}}"
                 + "]}}";
        }
    }

    /**
     * Fires a Firestore aggregation COUNT query for today's entries.
     * @param difficulty null to count across all difficulties (used for CourseType tab counts and HOLES_1).
     */
    public void fetchCount(String difficulty, CourseType courseType, CountListener listener) {
        if (listener == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.UK);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String todayStart = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{\"structuredAggregationQuery\":{");
        json.append("\"structuredQuery\":{");
        json.append("\"from\":[{\"collectionId\":\"").append(courseType.collectionId).append("\"}],");
        json.append(buildTodayWhereJson(difficulty, courseType, todayStart));
        json.append("},");
        json.append("\"aggregations\":[{\"count\":{},\"alias\":\"count\"}]");
        json.append("}}");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.aggregationQueryUrl + "?key=" + API_KEY);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int status = httpResponse.getStatus().getStatusCode();
                if (status < 200 || status >= 300) {
                    Gdx.app.postRunnable(() -> listener.onFailure(new Exception("HTTP " + status)));
                    return;
                }
                try {
                    JsonValue root = new JsonReader().parse(httpResponse.getResultAsString());
                    JsonValue first = root.isArray() ? root.get(0) : root;
                    // integerValue is serialised as a string in Firestore REST responses
                    String countStr = first.get("result").get("aggregateFields").get("count").getString("integerValue");
                    int count = Integer.parseInt(countStr);
                    Gdx.app.postRunnable(() -> listener.onSuccess(count));
                } catch (Exception e) {
                    Gdx.app.error("Highscore", "Count parse error: " + e.getMessage());
                    Gdx.app.postRunnable(() -> listener.onFailure(e));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.postRunnable(() -> listener.onFailure(t));
            }
            @Override public void cancelled() {}
        });
    }

    public interface SubmitCallback {
        void onSuccess();
        void onFailure(int statusCode);
    }

    public void submitScore(String name, String uid, String idToken, int score, String difficulty, CourseType courseType, float elapsedTime, int[] pars, int[] scores, SubmitCallback callback) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"fields\": {");
        json.append("\"playerName\": { \"stringValue\": \"").append(FirebaseConfig.escJson(name)).append("\" },");
        if (uid != null && !uid.isEmpty()) {
            json.append("\"uid\": { \"stringValue\": \"").append(FirebaseConfig.escJson(uid)).append("\" },");
        }
        json.append("\"score\": { \"integerValue\": ").append(score).append(" },");
        json.append("\"difficulty\": { \"stringValue\": \"").append(difficulty).append("\" },");
        json.append("\"submissionTime\": { \"timestampValue\": \"").append(timestamp).append("\" }");
        if (courseType == CourseType.HOLES_1) {
            json.append(",\"elapsedTime\": { \"doubleValue\": ").append(elapsedTime).append(" }");
        }
        if (courseType != CourseType.HOLES_1 && pars != null && scores != null) {
            json.append(",\"pars\": ").append(toFirestoreIntArray(pars));
            json.append(",\"scores\": ").append(toFirestoreIntArray(scores));
        }
        json.append("}}");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.baseUrl + "?key=" + API_KEY);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");
        if (idToken != null && !idToken.isEmpty()) {
            request.setHeader("Authorization", "Bearer " + idToken);
        }

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                int status = r.getStatus().getStatusCode();
                if (status == 200 || status == 201) {
                    Gdx.app.log("Highscore", "POST Status: " + status);
                    Gdx.app.postRunnable(() -> { if (callback != null) callback.onSuccess(); });
                } else {
                    Gdx.app.error("Highscore", "POST Error " + status + ": " + r.getResultAsString());
                    Gdx.app.postRunnable(() -> { if (callback != null) callback.onFailure(status); });
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.error("Highscore", "POST Failed", t);
                Gdx.app.postRunnable(() -> { if (callback != null) callback.onFailure(-1); });
            }
            @Override public void cancelled() {}
        });
    }

    static String toFirestoreIntArray(int[] arr) {
        StringBuilder sb = new StringBuilder("{\"arrayValue\":{\"values\":[");
        for (int i = 0; i < arr.length; i++) {
            if (i > 0) sb.append(',');
            sb.append("{\"integerValue\":").append(arr[i]).append('}');
        }
        sb.append("]}}");
        return sb.toString();
    }

    static int[] fromFirestoreIntArray(JsonValue arrayField) {
        JsonValue values = arrayField.get("arrayValue").get("values");
        if (values == null || values.size == 0) return null;
        int[] arr = new int[values.size];
        int i = 0;
        for (JsonValue v : values) arr[i++] = v.getInt("integerValue");
        return arr;
    }

    /**
     * Fetches today's entries for a CourseType across all difficulties.
     * Used to count entries per difficulty tab client-side.
     * Capped at 55 documents (5 difficulties × 10 each + 5 buffer).
     */
    public void fetchHighscoresAllDifficulties(CourseType courseType, final HighscoreListener listener) {
        if (listener == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.UK);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String todayStart = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"structuredQuery\": {");
        json.append("\"from\": [{\"collectionId\": \"").append(courseType.collectionId).append("\"}],");
        json.append(buildTodayWhereJson(null, courseType, todayStart)).append(",");
        json.append("\"orderBy\": [{ \"field\": {\"fieldPath\": \"submissionTime\"}, \"direction\": \"ASCENDING\" }],");
        json.append("\"limit\": 55");
        json.append("} }");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.queryUrl + "?key=" + API_KEY);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int status = httpResponse.getStatus().getStatusCode();
                String body = httpResponse.getResultAsString();
                if (status < 200 || status >= 300) {
                    Gdx.app.error("Highscore", "AllDiff fetch HTTP " + status + ": " + body);
                    Gdx.app.postRunnable(() -> listener.onFailure(new Exception("HTTP " + status)));
                    return;
                }
                try {
                    JsonValue root = new JsonReader().parse(body);
                    final Array<HighscoreEntry> entries = new Array<>();
                    if (root.isArray()) {
                        for (JsonValue wrapper : root) {
                            if (wrapper == null || !wrapper.has("document")) continue;
                            JsonValue fields = wrapper.get("document").get("fields");
                            if (fields == null || !fields.has("difficulty")) continue;
                            String diff = fields.get("difficulty").getString("stringValue");
                            int score = fields.has("score") ? fields.get("score").getInt("integerValue") : 0;
                            entries.add(new HighscoreEntry(null, score, diff, null));
                        }
                    }
                    Gdx.app.postRunnable(() -> listener.onSuccess(entries));
                } catch (Exception e) {
                    Gdx.app.postRunnable(() -> listener.onFailure(e));
                }
            }
            @Override public void failed(Throwable t) { Gdx.app.postRunnable(() -> listener.onFailure(t)); }
            @Override public void cancelled() {}
        });
    }

    public void fetchHighscores(String difficulty, final HighscoreListener listener) {
        fetchHighscores(difficulty, CourseType.HOLES_18, listener);
    }

    public void fetchHighscores(String difficulty, CourseType courseType, final HighscoreListener listener) {
        if (listener == null) return;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.UK);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String todayStart = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"structuredQuery\": {");
        json.append("\"from\": [{\"collectionId\": \"").append(courseType.collectionId).append("\"}],");
        json.append(buildTodayWhereJson(difficulty, courseType, todayStart)).append(",");
        json.append("\"orderBy\": [");
        if (courseType == CourseType.HOLES_1) {
            // Order by submissionTime only — Firestore can handle this without a composite index.
            // score/elapsedTime sorting is done client-side after fetching.
            json.append("{ \"field\": {\"fieldPath\": \"submissionTime\"}, \"direction\": \"ASCENDING\" }");
            json.append("],");
            json.append("\"limit\": 100");
        } else {
            json.append("{ \"field\": {\"fieldPath\": \"score\"}, \"direction\": \"ASCENDING\" },");
            json.append("{ \"field\": {\"fieldPath\": \"submissionTime\"}, \"direction\": \"ASCENDING\" }");
            json.append("],");
            json.append("\"limit\": 10");
        }
        json.append("} }");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.queryUrl + "?key=" + API_KEY);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int status = httpResponse.getStatus().getStatusCode();
                String body = httpResponse.getResultAsString();
                if (status < 200 || status >= 300) {
                    Gdx.app.error("Highscore", "Fetch HTTP " + status + ": " + body);
                    Gdx.app.postRunnable(() -> listener.onFailure(new Exception("HTTP " + status)));
                    return;
                }
                try {
                    JsonValue root = new JsonReader().parse(body);
                    final Array<HighscoreEntry> entries = new Array<>();
                    if (root.isArray()) {
                        for (JsonValue wrapper : root) {
                            if (wrapper == null || !wrapper.has("document")) continue;
                            JsonValue fields = wrapper.get("document").get("fields");
                            if (fields == null) continue;
                            if (!fields.has("playerName") || !fields.has("score") || !fields.has("submissionTime")) continue;
                            float elapsed = 0f;
                            if (fields.has("elapsedTime")) {
                                elapsed = fields.get("elapsedTime").getFloat("doubleValue");
                            }
                            int[] parsArr = fields.has("pars") ? fromFirestoreIntArray(fields.get("pars")) : null;
                            int[] scoresArr = fields.has("scores") ? fromFirestoreIntArray(fields.get("scores")) : null;
                            String difficulty = fields.has("difficulty") ? fields.get("difficulty").getString("stringValue") : "NORMAL";
                            entries.add(new HighscoreEntry(
                                    fields.get("playerName").getString("stringValue"),
                                    fields.get("score").getInt("integerValue"),
                                    difficulty,
                                    fields.get("submissionTime").getString("timestampValue"),
                                    elapsed,
                                    parsArr,
                                    scoresArr
                            ));
                        }
                    }
                    Gdx.app.log("Highscore", "Fetched " + entries.size + " entries for " + courseType.collectionId);
                    Gdx.app.postRunnable(() -> listener.onSuccess(entries));
                } catch (Exception e) {
                    Gdx.app.error("Highscore", "Fetch parse error: " + e.getMessage());
                    Gdx.app.postRunnable(() -> listener.onFailure(e));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.error("Highscore", "Fetch network error", t);
                Gdx.app.postRunnable(() -> listener.onFailure(t));
            }
            @Override public void cancelled() {}
        });
    }
}