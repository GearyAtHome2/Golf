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
    private static final String BASE_URL = "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents/highscores";

    public interface HighscoreListener {
        void onSuccess(Array<HighscoreEntry> entries);
        void onFailure(Throwable t);
    }

    // HighscoreEntry as a static inner class for simplicity if you haven't made the record yet
    public static class HighscoreEntry {
        public String name;
        public int score;
        public String difficulty;
        public String date;

        public HighscoreEntry(String name, int score, String difficulty, String date) {
            this.name = name;
            this.score = score;
            this.difficulty = difficulty;
            this.date = date;
        }
    }

    public void submitScore(String name, int score, String difficulty) {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String timestamp = sdf.format(new Date());

        StringBuilder json = new StringBuilder();
        json.append("{ \"fields\": {");
        json.append("\"playerName\": { \"stringValue\": \"").append(name).append("\" },");
        json.append("\"score\": { \"integerValue\": ").append(score).append(" },");
        json.append("\"difficulty\": { \"stringValue\": \"").append(difficulty).append("\" },");
        json.append("\"submissionTime\": { \"timestampValue\": \"").append(timestamp).append("\" }");
        json.append("}}");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(BASE_URL);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                Gdx.app.log("Highscore", "POST Status: " + httpResponse.getStatus().getStatusCode());
            }

            @Override
            public void failed(Throwable t) { Gdx.app.error("Highscore", "POST Failed", t); }

            @Override
            public void cancelled() {}
        });
    }

    public void fetchHighscores(String difficulty, final HighscoreListener listener) {
        // Calculate Today's Start (00:00:00 UTC)
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.UK);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        String todayStart = sdf.format(new Date());

        Gdx.app.log("Highscore", "Querying scores since: " + todayStart);
        // Firestore runQuery endpoint
        String url = "https://firestore.googleapis.com/v1/projects/geary-golf/databases/(default)/documents:runQuery";

        StringBuilder json = new StringBuilder();
        json.append("{ \"structuredQuery\": {");
        json.append("\"from\": [{\"collectionId\": \"highscores\"}],");
        json.append("\"where\": { \"compositeFilter\": { \"op\": \"AND\", \"filters\": [");
        // Filter 1: Difficulty
        json.append("{ \"fieldFilter\": { \"field\": {\"fieldPath\": \"difficulty\"}, \"op\": \"EQUAL\", \"value\": {\"stringValue\": \"").append(difficulty).append("\"} } },");
        // Filter 2: Today (SubmissionTime >= Start of Today)
        json.append("{ \"fieldFilter\": { \"field\": {\"fieldPath\": \"submissionTime\"}, \"op\": \"GREATER_THAN_OR_EQUAL\", \"value\": {\"timestampValue\": \"").append(todayStart).append("\"} } }");
        json.append("] } },");

        // Sorting: 1st by Score (Ascending), 2nd by Time (Ascending)
        json.append("\"orderBy\": [");
        json.append("{ \"field\": {\"fieldPath\": \"score\"}, \"direction\": \"ASCENDING\" },");
        json.append("{ \"field\": {\"fieldPath\": \"submissionTime\"}, \"direction\": \"ASCENDING\" }");
        json.append("],");

        json.append("\"limit\": 10");
        json.append("} }");

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(url);
        request.setContent(json.toString());
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse httpResponse) {
                int statusCode = httpResponse.getStatus().getStatusCode();
                String result = httpResponse.getResultAsString();

                // DEBUG: See exactly what Google is complaining about
                Gdx.app.log("Highscore", "Status: " + statusCode);
                if (statusCode != 200) {
                    Gdx.app.error("Highscore", "Error Body: " + result);
                }

                try {
                    JsonValue root = new JsonReader().parse(result);
                    final Array<HighscoreEntry> entries = new Array<>();

                    // Firestore runQuery returns an array of objects
                    if (root.isArray()) {
                        for (JsonValue wrapper : root) {
                            if (wrapper == null || !wrapper.has("document")) continue;

                            JsonValue doc = wrapper.get("document");
                            if (doc == null || !doc.has("fields")) continue;

                            JsonValue fields = doc.get("fields");

                            entries.add(new HighscoreEntry(
                                    fields.get("playerName").getString("stringValue"),
                                    fields.get("score").getInt("integerValue"),
                                    fields.get("difficulty").getString("stringValue"),
                                    fields.get("submissionTime").getString("timestampValue")
                            ));
                        }
                    }

                    Gdx.app.postRunnable(() -> listener.onSuccess(entries));
                } catch (Exception e) {
                    Gdx.app.error("Highscore", "Parsing Error", e);
                    Gdx.app.postRunnable(() -> listener.onFailure(e));
                }
            }

            @Override
            public void failed(Throwable t) { /* handle failure */ }
            @Override
            public void cancelled() { /* handle cancel */ }
        });
    }
}