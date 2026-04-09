package org.example.scoreBoard;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.EnumSet;
import java.util.Locale;
import java.util.Set;
import java.util.TimeZone;

/**
 * Checks Firestore once per session to find out which daily course types the
 * current user has already submitted a score for today.
 *
 * Call fetch() after login. Read submittedToday() anywhere — it's synchronous
 * after the initial fetch completes, so there's no per-call lag.
 */
public class DailySubmissionCache {

    private static final String API_KEY = "AIzaSyAgtF4QdIY1IsxMvYKUHGi8SVT-ZQzLsDI";

    public interface Callback {
        void onComplete();
    }

    private final Set<CourseType> submitted = EnumSet.noneOf(CourseType.class);
    private boolean fetched = false;
    private int pendingRequests = 0;

    /** True once all three collection checks have returned (success or failure). */
    public boolean isFetched() { return fetched; }

    /** Returns true if the user has already submitted a score for this course type today. */
    public boolean hasSubmitted(CourseType type) { return submitted.contains(type); }

    /** Returns true if all three daily course types have been submitted today. */
    public boolean allSubmittedToday() {
        return submitted.contains(CourseType.HOLES_1)
            && submitted.contains(CourseType.HOLES_9)
            && submitted.contains(CourseType.HOLES_18);
    }

    /**
     * Fires three parallel Firestore queries (one per daily collection) to check
     * whether uid has a submission for today. Calls back on the GL thread when all
     * three have resolved. Safe to call again after a successful submission to refresh.
     */
    public void fetch(String uid, Callback onComplete) {
        if (uid == null || uid.isEmpty()) {
            Gdx.app.log("DailySubmissionCache", "No uid — skipping fetch");
            fetched = true;
            if (onComplete != null) Gdx.app.postRunnable(onComplete::onComplete);
            return;
        }

        submitted.clear();
        fetched = false;
        pendingRequests = CourseType.values().length;

        String todayStart = todayUtcStart();

        for (CourseType type : CourseType.values()) {
            checkCollection(type, uid, todayStart, onComplete);
        }
    }

    /** Call this after a successful submission to immediately update the local cache. */
    public void markSubmitted(CourseType type) {
        submitted.add(type);
    }

    /** Clears all cached state. Call on logout so a subsequent login fetches fresh data. */
    public void clear() {
        submitted.clear();
        fetched = false;
        pendingRequests = 0;
    }

    // -------------------------------------------------------------------------

    private void checkCollection(CourseType type, String uid, String todayStart, Callback onComplete) {
        String body = buildQuery(uid, todayStart);

        Net.HttpRequest request = new Net.HttpRequest(Net.HttpMethods.POST);
        request.setUrl(type.queryUrl + "?key=" + API_KEY);
        request.setContent(body);
        request.setHeader("Content-Type", "application/json");

        Gdx.net.sendHttpRequest(request, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                int status = r.getStatus().getStatusCode();
                boolean found = false;
                if (status >= 200 && status < 300) {
                    try {
                        JsonValue root = new JsonReader().parse(r.getResultAsString());
                        // Firestore runQuery returns an array; a real document result has a "document" key
                        if (root.isArray()) {
                            for (JsonValue item : root) {
                                if (item != null && item.has("document")) { found = true; break; }
                            }
                        }
                    } catch (Exception e) {
                        Gdx.app.error("DailySubmissionCache", "Parse error for " + type + ": " + e.getMessage());
                    }
                } else {
                    Gdx.app.error("DailySubmissionCache", "HTTP " + status + " checking " + type);
                }

                final boolean hasEntry = found;
                Gdx.app.postRunnable(() -> {
                    if (hasEntry) submitted.add(type);
                    Gdx.app.log("DailySubmissionCache", type + " submitted today: " + hasEntry);
                    pendingRequests--;
                    if (pendingRequests <= 0) {
                        fetched = true;
                        Gdx.app.log("DailySubmissionCache", "Fetch complete. Submitted: " + submitted);
                        if (onComplete != null) onComplete.onComplete();
                    }
                });
            }

            @Override
            public void failed(Throwable t) {
                Gdx.app.error("DailySubmissionCache", "Network error checking " + type + ": " + t.getMessage());
                Gdx.app.postRunnable(() -> {
                    pendingRequests--;
                    if (pendingRequests <= 0) {
                        fetched = true;
                        if (onComplete != null) onComplete.onComplete();
                    }
                });
            }

            @Override public void cancelled() {
                Gdx.app.postRunnable(() -> {
                    pendingRequests--;
                    if (pendingRequests <= 0) {
                        fetched = true;
                        if (onComplete != null) onComplete.onComplete();
                    }
                });
            }
        });
    }

    private static String buildQuery(String uid, String todayStart) {
        return "{ \"structuredQuery\": {"
            + "\"from\": [{\"collectionId\": \"entries\"}],"
            + "\"where\": { \"compositeFilter\": { \"op\": \"AND\", \"filters\": ["
            + "  { \"fieldFilter\": { \"field\": {\"fieldPath\": \"uid\"}, \"op\": \"EQUAL\","
            + "    \"value\": {\"stringValue\": \"" + uid + "\"} } },"
            + "  { \"fieldFilter\": { \"field\": {\"fieldPath\": \"submissionTime\"}, \"op\": \"GREATER_THAN_OR_EQUAL\","
            + "    \"value\": {\"timestampValue\": \"" + todayStart + "\"} } }"
            + "] } },"
            + "\"limit\": 1"
            + "} }";
    }

    private static String todayUtcStart() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'00:00:00'Z'", Locale.US);
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf.format(new Date());
    }
}
