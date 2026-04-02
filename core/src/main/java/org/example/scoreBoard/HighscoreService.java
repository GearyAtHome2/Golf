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

public class HighscoreService {

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

        public HighscoreEntry(String name, int score, String difficulty, String date) {
            this(name, score, difficulty, date, 0f);
        }

        public HighscoreEntry(String name, int score, String difficulty, String date, float elapsedTime) {
            this.name = name;
            this.score = score;
            this.difficulty = difficulty;
            this.date = date;
            this.elapsedTime = elapsedTime;
        }
    }

    /** Backward-compatible overload: submits to the 18-hole collection. */
    public void submitScore(String name, int score, String difficulty) {
        submitScore(name, score, difficulty, CourseType.HOLES_18, 0f);
    }

    public void submitScore(String name, int score, String difficulty, CourseType courseType, float elapsedTime) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"fields\": {");
        json.append("\"playerName\": { \"stringValue\": \"").append(name).append("\" },");
        json.append("\"score\": { \"integerValue\": ").append(score).append(" },");
        json.append("\"difficulty\": { \"stringValue\": \"").append(difficulty).append("\" },");
        json.append("\"submissionTime\": { \"timestampValue\": \"").append(timestamp).append("\" }");
        if (courseType == CourseType.HOLES_1) {
            json.append(",\"elapsedTime\": { \"doubleValue\": ").append(elapsedTime).append(" }");
        }
        json.append("}}");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.baseUrl);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override public void handleHttpResponse(Net.HttpResponse r) { Gdx.app.log("Highscore", "POST Status: " + r.getStatus().getStatusCode()); }
            @Override public void failed(Throwable t) { Gdx.app.error("Highscore", "POST Failed", t); }
            @Override public void cancelled() {}
        });
    }

    /** Backward-compatible overload: fetches from the 18-hole collection. */
    public void fetchHighscores(String difficulty, final HighscoreListener listener) {
        fetchHighscores(difficulty, CourseType.HOLES_18, listener);
    }

    public void fetchHighscores(String difficulty, CourseType courseType, final HighscoreListener listener) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.UK);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String todayStart = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"structuredQuery\": {");
        json.append("\"from\": [{\"collectionId\": \"").append(courseType.collectionId).append("\"}],");
        json.append("\"where\": { \"compositeFilter\": { \"op\": \"AND\", \"filters\": [");
        json.append("{ \"fieldFilter\": { \"field\": {\"fieldPath\": \"difficulty\"}, \"op\": \"EQUAL\", \"value\": {\"stringValue\": \"").append(difficulty).append("\"} } },");
        json.append("{ \"fieldFilter\": { \"field\": {\"fieldPath\": \"submissionTime\"}, \"op\": \"GREATER_THAN_OR_EQUAL\", \"value\": {\"timestampValue\": \"").append(todayStart).append("\"} } }");
        json.append("] } },");
        json.append("\"orderBy\": [");
        if (courseType == CourseType.HOLES_1) {
            json.append("{ \"field\": {\"fieldPath\": \"elapsedTime\"}, \"direction\": \"ASCENDING\" },");
            json.append("{ \"field\": {\"fieldPath\": \"score\"}, \"direction\": \"ASCENDING\" },");
        } else {
            json.append("{ \"field\": {\"fieldPath\": \"score\"}, \"direction\": \"ASCENDING\" },");
        }
        json.append("{ \"field\": {\"fieldPath\": \"submissionTime\"}, \"direction\": \"ASCENDING\" }");
        json.append("],");
        json.append("\"limit\": 10");
        json.append("} }");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(courseType.queryUrl);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                try {
                    JsonValue root = new JsonReader().parse(httpResponse.getResultAsString());
                    final Array<HighscoreEntry> entries = new Array<>();
                    if (root.isArray()) {
                        for (JsonValue wrapper : root) {
                            if (wrapper == null || !wrapper.has("document")) continue;
                            JsonValue fields = wrapper.get("document").get("fields");
                            if (fields == null) continue;
                            float elapsed = 0f;
                            if (fields.has("elapsedTime")) {
                                elapsed = fields.get("elapsedTime").getFloat("doubleValue");
                            }
                            entries.add(new HighscoreEntry(
                                fields.get("playerName").getString("stringValue"),
                                fields.get("score").getInt("integerValue"),
                                fields.get("difficulty").getString("stringValue"),
                                fields.get("submissionTime").getString("timestampValue"),
                                elapsed
                            ));
                        }
                    }
                    Gdx.app.postRunnable(() -> listener.onSuccess(entries));
                } catch (Exception e) {
                    Gdx.app.postRunnable(() -> listener.onFailure(e));
                }
            }
            @Override public void failed(Throwable t) {}
            @Override public void cancelled() {}
        });
    }
}
