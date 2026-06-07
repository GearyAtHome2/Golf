package com.gearygolf.golf.scoreBoard;

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
 *
 * Also maintains a local on-disk store (via LocalSubmissionStore) that is
 * populated immediately at the start of fetch(), before Firebase responds.
 * This ensures buttons are locked correctly even during the Firebase round-trip.
 */
public class DailySubmissionCache {

    private static final String API_KEY = com.gearygolf.golf.FirebaseConfig.API_KEY;

    public interface Callback {
        void onComplete();
    }

    private final Set<CourseType> submitted = EnumSet.noneOf(CourseType.class);
    /** Populated from the local file at the start of fetch(). Never cleared mid-session. */
    private final Set<CourseType> localConfirmed = EnumSet.noneOf(CourseType.class);
    private String uid = "";
    private boolean fetched = false;
    private int pendingRequests = 0;
    private int networkFailures = 0;  // counts requests that failed at network level (no connectivity)
    private int fetchGeneration = 0;  // incremented each fetch; callbacks from stale fetches are ignored

    /** True once all three collection checks have returned (success or failure). */
    public boolean isFetched() { return fetched; }

    /** Returns true if the user has already submitted a score for this course type today. */
    public boolean hasSubmitted(CourseType type) { return submitted.contains(type); }

    /**
     * Returns true if the local on-disk store (loaded at the start of fetch()) shows this
     * type as submitted for today. This is available immediately — before Firebase responds.
     */
    public boolean isLocallyConfirmed(CourseType type) { return localConfirmed.contains(type); }

    /** Returns true if all five daily course types have been submitted today. */
    public boolean allSubmittedToday() {
        return submitted.contains(CourseType.HOLES_1_PAR3)
            && submitted.contains(CourseType.HOLES_1_PAR4)
            && submitted.contains(CourseType.HOLES_1_PAR5)
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

        this.uid = uid;

        // Load local store first — this is the initial source of truth before Firebase responds.
        localConfirmed.clear();
        localConfirmed.addAll(LocalSubmissionStore.load(uid));
        Gdx.app.log("DailySubmissionCache", "Local store loaded: " + localConfirmed);

        submitted.clear();
        fetched = false;
        pendingRequests = CourseType.values().length;
        networkFailures = 0;
        final int generation = ++fetchGeneration;

        String todayStart = todayUtcStart();

        for (CourseType type : CourseType.values()) {
            checkCollection(type, uid, todayStart, generation, onComplete);
        }
    }

    /** Call this after a successful submission to immediately update the in-memory and local cache. */
    public void markSubmitted(CourseType type) {
        submitted.add(type);
        localConfirmed.add(type);
        LocalSubmissionStore.save(uid, localConfirmed);
    }

    /** Clears all cached state. Call on logout so a subsequent login fetches fresh data. */
    public void clear() {
        submitted.clear();
        localConfirmed.clear();
        uid = "";
        fetched = false;
        pendingRequests = 0;
        networkFailures = 0;
    }

    // -------------------------------------------------------------------------

    private void checkCollection(CourseType type, String uid, String todayStart, int generation, Callback onComplete) {
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
                    if (generation != fetchGeneration) return; // stale fetch — discard
                    if (hasEntry) {
                        submitted.add(type);
                        // Firebase confirmed this submission — persist to local store so future
                        // app opens don't need to wait for Firebase to lock the button.
                        if (localConfirmed.add(type)) {
                            LocalSubmissionStore.save(uid, localConfirmed);
                        }
                    }
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
                    if (generation != fetchGeneration) return;
                    networkFailures++;
                    pendingRequests--;
                    if (pendingRequests <= 0) {
                        if (networkFailures < CourseType.values().length) {
                            // At least one query reached the server — partial data is usable
                            fetched = true;
                            if (onComplete != null) onComplete.onComplete();
                        }
                        // else: all failed at network level = offline; leave fetched=false so
                        // guards don't treat an empty submitted set as "definitely not submitted"
                    }
                });
            }

            @Override public void cancelled() {
                Gdx.app.postRunnable(() -> {
                    if (generation != fetchGeneration) return;
                    networkFailures++;
                    pendingRequests--;
                    if (pendingRequests <= 0) {
                        if (networkFailures < CourseType.values().length) {
                            fetched = true;
                            if (onComplete != null) onComplete.onComplete();
                        }
                    }
                });
            }
        });
    }

    private static String buildQuery(String uid, String todayStart) {
        String safeUid = uid.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{ \"structuredQuery\": {"
            + "\"from\": [{\"collectionId\": \"entries\"}],"
            + "\"where\": { \"compositeFilter\": { \"op\": \"AND\", \"filters\": ["
            + "  { \"fieldFilter\": { \"field\": {\"fieldPath\": \"uid\"}, \"op\": \"EQUAL\","
            + "    \"value\": {\"stringValue\": \"" + safeUid + "\"} } },"
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
