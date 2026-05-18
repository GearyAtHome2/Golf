package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Net;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.gearygolf.golf.FirebaseConfig;

/**
 * Firebase Realtime Database room management via REST polling.
 * No Firebase SDK — uses Gdx.net.sendHttpRequest like AuthService.
 *
 * RTDB paths:
 *   rooms/{code}                              — full room object
 *   rooms/{code}/players                      — uid → displayName
 *   rooms/{code}/playerMeta/{uid}             — {difficulty, ready} per player
 *   rooms/{code}/shots/{hole}/{uid}/{stroke}  — ShotPacket nodes
 *   rooms/{code}/scores/{uid}/{holeIndex}     — stroke counts
 */
public class RoomService {

    private static final String BASE = FirebaseConfig.RTDB_URL + "/rooms/";

    // -------------------------------------------------------------------------
    // Data types
    // -------------------------------------------------------------------------

    public static class PlayerMeta {
        public String  difficulty;
        public boolean ready;
        public PlayerMeta() { difficulty = "NOVICE"; ready = false; }
    }

    public static class RoomState {
        public String              roomCode;
        public String              hostUid;
        public long                seed;
        public Map<String, String>     players;    // uid → displayName
        public Map<String, PlayerMeta> playerMeta; // uid → {difficulty, ready}
        public int                 currentHole;
        public String              state;          // "lobby" | "active" | "finished"
        /** scores[uid][holeIndex] = stroke count. Only present once a player holes out. */
        public Map<String, Map<Integer, Integer>> scores;

        public RoomState() {
            players    = new LinkedHashMap<>();
            playerMeta = new LinkedHashMap<>();
            scores     = new LinkedHashMap<>();
        }
    }

    public static class RestPacket {
        public float   x, y, z;
        public int     strokeNum;
        public boolean holed;
        public transient String uid;

        public String toJson() {
            return "{\"x\":" + x + ",\"y\":" + y + ",\"z\":" + z
                 + ",\"n\":" + strokeNum + ",\"h\":" + holed + "}";
        }

        public static RestPacket fromJson(JsonValue j, String uid) {
            RestPacket p = new RestPacket();
            p.uid       = uid;
            p.x         = j.getFloat  ("x", 0);
            p.y         = j.getFloat  ("y", 0);
            p.z         = j.getFloat  ("z", 0);
            p.strokeNum = j.getInt    ("n", 0);
            p.holed     = j.getBoolean("h", false);
            return p;
        }
    }

    public interface RestCallback {
        void onSuccess(List<RestPacket> packets);
        void onFailure(String message);
    }

    public interface RoomCallback {
        void onSuccess(RoomState room);
        void onFailure(String message);
    }

    public interface SimpleCallback {
        void onSuccess();
        void onFailure(String message);
    }

    // -------------------------------------------------------------------------
    // createRoom — PUT rooms/{code} with initial room data including host meta
    // -------------------------------------------------------------------------

    public void createRoom(String idToken, String uid, String displayName,
                           long seed, RoomCallback callback) {
        String code = generateCode();
        String body = "{"
            + "\"host\":\"" + FirebaseConfig.escJson(uid) + "\","
            + "\"seed\":" + seed + ","
            + "\"currentHole\":0,"
            + "\"state\":\"lobby\","
            + "\"players\":{\"" + FirebaseConfig.escJson(uid) + "\":\"" + FirebaseConfig.escJson(displayName) + "\"},"
            + "\"playerMeta\":{\"" + FirebaseConfig.escJson(uid) + "\":{\"difficulty\":\"NOVICE\",\"ready\":false}}"
            + "}";

        put(url(code, idToken), body, response -> {
            RoomState room = parseRoom(code, response);
            Gdx.app.postRunnable(() -> callback.onSuccess(room));
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // joinRoom — PATCH playerMeta first, PATCH players second, then GET full room
    //
    // Order matters: playerMeta is written BEFORE the player appears in the players
    // list. This means the host can never poll the room and see a player without
    // metadata — they either see nothing yet (safe) or see both together (correct).
    // The old order (players first) caused allGuestsReady() to permanently return
    // false because meta == null for a brief but observable window.
    // -------------------------------------------------------------------------

    public void joinRoom(String idToken, String uid, String displayName,
                         String code, RoomCallback callback) {
        String upperCode = code.trim().toUpperCase();
        String playerBody = "{\"" + FirebaseConfig.escJson(uid) + "\":\"" + FirebaseConfig.escJson(displayName) + "\"}";
        String metaBody   = "{\"difficulty\":\"NOVICE\",\"ready\":false}";

        patch(url(upperCode + "/playerMeta/" + uid, idToken), metaBody, ignored1 -> {
            patch(url(upperCode + "/players", idToken), playerBody, ignored2 -> {
                get(url(upperCode, idToken), response -> {
                    RoomState room = parseRoom(upperCode, response);
                    Gdx.app.postRunnable(() -> callback.onSuccess(room));
                }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
            }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // setPlayerMeta — PATCH rooms/{code}/playerMeta/{uid}
    // -------------------------------------------------------------------------

    public void setPlayerMeta(String idToken, String code, String uid,
                              String difficulty, boolean ready, SimpleCallback callback) {
        String body = "{\"difficulty\":\"" + FirebaseConfig.escJson(difficulty) + "\",\"ready\":" + ready + "}";
        patch(url(code + "/playerMeta/" + uid, idToken), body,
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // pollRoom — GET rooms/{code}
    // -------------------------------------------------------------------------

    public void pollRoom(String idToken, String code, RoomCallback callback) {
        get(url(code, idToken), response -> {
            if (response == null) {
                Gdx.app.postRunnable(() -> callback.onFailure("Room not found."));
                return;
            }
            RoomState room = parseRoom(code, response);
            Gdx.app.postRunnable(() -> callback.onSuccess(room));
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // leaveRoom — DELETE own players entry and playerMeta entry
    // -------------------------------------------------------------------------

    public void leaveRoom(String idToken, String uid, String code, SimpleCallback callback) {
        delete(url(code + "/players/" + uid, idToken), () -> {
            delete(url(code + "/playerMeta/" + uid, idToken),
                () -> Gdx.app.postRunnable(callback::onSuccess),
                msg -> Gdx.app.postRunnable(callback::onSuccess)); // best-effort
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // deleteRoom — removes the entire room node
    // -------------------------------------------------------------------------

    public void deleteRoom(String idToken, String code, SimpleCallback callback) {
        delete(url(code, idToken),
            () -> Gdx.app.postRunnable(callback::onSuccess),
            msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // setRoomState — host writes state:"active" and currentHole:0 to start game.
    // Difficulty is now per-player in playerMeta; write your own meta before calling this.
    // -------------------------------------------------------------------------

    public void setRoomState(String idToken, String code, String state,
                             int currentHole, SimpleCallback callback) {
        String body = "{\"state\":\"" + FirebaseConfig.escJson(state) + "\","
                    + "\"currentHole\":" + currentHole + "}";
        patch(url(code, idToken), body,
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // broadcastShot — PUT rooms/{code}/shots/{hole}/{uid}/{strokeNum}
    // -------------------------------------------------------------------------

    public void broadcastShot(String idToken, String code, int holeIndex,
                               String uid, ShotPacket packet, SimpleCallback callback) {
        String path = code + "/shots/h" + holeIndex + "/" + uid + "/s" + packet.strokeNum;
        put(url(path, idToken), packet.toJson(),
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // fetchShotsForHole — GET rooms/{code}/shots/{hole}
    // -------------------------------------------------------------------------

    public interface ShotsCallback {
        void onSuccess(List<ShotPacket> packets);
        void onFailure(String message);
    }

    public void fetchShotsForHole(String idToken, String code, int holeIndex,
                                   ShotsCallback callback) {
        get(url(code + "/shots/h" + holeIndex, idToken), response -> {
            List<ShotPacket> packets = new ArrayList<>();
            if (response != null) {
                for (JsonValue uidNode = response.child; uidNode != null; uidNode = uidNode.next) {
                    String uid = uidNode.name;
                    for (JsonValue strokeNode = uidNode.child; strokeNode != null; strokeNode = strokeNode.next) {
                        ShotPacket p = ShotPacket.fromJson(strokeNode, uid);
                        packets.add(p);
                    }
                }
            }
            Gdx.app.postRunnable(() -> callback.onSuccess(packets));
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // submitScore — PUT rooms/{code}/scores/{uid}/{holeIndex}
    // -------------------------------------------------------------------------

    public void submitScore(String idToken, String code, String uid,
                            int holeIndex, int strokes, SimpleCallback callback) {
        String path = code + "/scores/" + uid + "/h" + holeIndex;
        put(url(path, idToken), String.valueOf(strokes),
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // advanceHole — host PATCHes currentHole
    // -------------------------------------------------------------------------

    public void advanceHole(String idToken, String code, int newHole, SimpleCallback callback) {
        String body = "{\"currentHole\":" + newHole + "}";
        patch(url(code, idToken), body,
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // setFinished — host marks match finished
    // -------------------------------------------------------------------------

    public void setFinished(String idToken, String code, SimpleCallback callback) {
        String body = "{\"state\":\"finished\"}";
        patch(url(code, idToken), body,
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // broadcastRest / fetchRestsForHole
    // -------------------------------------------------------------------------

    public void broadcastRest(String idToken, String code, int holeIndex,
                               String uid, RestPacket packet, SimpleCallback callback) {
        String path = code + "/rests/h" + holeIndex + "/" + uid;
        put(url(path, idToken), packet.toJson(),
            ignored -> Gdx.app.postRunnable(callback::onSuccess),
            msg     -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    public void fetchRestsForHole(String idToken, String code, int holeIndex,
                                   RestCallback callback) {
        get(url(code + "/rests/h" + holeIndex, idToken), response -> {
            List<RestPacket> packets = new ArrayList<>();
            if (response != null) {
                for (JsonValue node = response.child; node != null; node = node.next) {
                    packets.add(RestPacket.fromJson(node, node.name));
                }
            }
            Gdx.app.postRunnable(() -> callback.onSuccess(packets));
        }, msg -> Gdx.app.postRunnable(() -> callback.onFailure(msg)));
    }

    // -------------------------------------------------------------------------
    // generateCode — 4 uppercase alpha chars
    // -------------------------------------------------------------------------

    public static String generateCode() {
        char[] chars = new char[4];
        for (int i = 0; i < 4; i++) chars[i] = (char) ('A' + (int) (Math.random() * 26));
        return new String(chars);
    }

    // -------------------------------------------------------------------------
    // Internal: parse room JSON into RoomState
    // -------------------------------------------------------------------------

    private RoomState parseRoom(String code, JsonValue json) {
        RoomState room = new RoomState();
        room.roomCode    = code;
        room.hostUid     = json.getString("host",        "");
        room.seed        = json.getLong  ("seed",        0L);
        room.currentHole = json.getInt   ("currentHole", 0);
        room.state       = json.getString("state",       "lobby");

        JsonValue players = json.get("players");
        if (players != null) {
            for (JsonValue entry = players.child; entry != null; entry = entry.next) {
                room.players.put(entry.name, entry.asString());
            }
        }

        JsonValue metaNode = json.get("playerMeta");
        if (metaNode != null) {
            for (JsonValue entry = metaNode.child; entry != null; entry = entry.next) {
                PlayerMeta m = new PlayerMeta();
                m.difficulty = entry.getString("difficulty", "NOVICE");
                m.ready      = entry.getBoolean("ready", false);
                room.playerMeta.put(entry.name, m);
            }
        }

        JsonValue scoresNode = json.get("scores");
        if (scoresNode != null) {
            for (JsonValue uidNode = scoresNode.child; uidNode != null; uidNode = uidNode.next) {
                Map<Integer, Integer> holeMap = new LinkedHashMap<>();
                for (JsonValue holeNode = uidNode.child; holeNode != null; holeNode = holeNode.next) {
                    String key = holeNode.name; // "h0", "h1", …
                    int idx = Integer.parseInt(key.startsWith("h") ? key.substring(1) : key);
                    holeMap.put(idx, holeNode.asInt());
                }
                room.scores.put(uidNode.name, holeMap);
            }
        }

        return room;
    }

    // -------------------------------------------------------------------------
    // Internal: URL builder
    // -------------------------------------------------------------------------

    private String url(String path, String idToken) {
        return BASE + path + ".json?auth=" + idToken;
    }

    // -------------------------------------------------------------------------
    // Internal: HTTP helpers
    // -------------------------------------------------------------------------

    private void put(String url, String body, SuccessHandler onSuccess, FailHandler onFailure) {
        sendRequest(Net.HttpMethods.PUT, url, body, onSuccess, onFailure);
    }

    private void patch(String url, String body, SuccessHandler onSuccess, FailHandler onFailure) {
        // Desktop Java (HttpURLConnection) rejects PATCH as an invalid method.
        // Firebase RTDB honours X-HTTP-Method-Override, so we tunnel PATCH over POST.
        Net.HttpRequest req = new Net.HttpRequest(Net.HttpMethods.POST);
        req.setUrl(url);
        req.setHeader("Content-Type", "application/json");
        req.setHeader("X-HTTP-Method-Override", "PATCH");
        if (body != null) req.setContent(body);
        sendRaw(req, onSuccess, onFailure);
    }

    private void get(String url, SuccessHandler onSuccess, FailHandler onFailure) {
        sendRequest(Net.HttpMethods.GET, url, null, onSuccess, onFailure);
    }

    private void delete(String url, Runnable onSuccess, FailHandler onFailure) {
        sendRequest(Net.HttpMethods.DELETE, url, null,
            ignored -> onSuccess.run(), onFailure);
    }

    private void sendRequest(String method, String url, String body,
                              SuccessHandler onSuccess, FailHandler onFailure) {
        Net.HttpRequest req = new Net.HttpRequest(method);
        req.setUrl(url);
        req.setHeader("Content-Type", "application/json");
        if (body != null) req.setContent(body);
        sendRaw(req, onSuccess, onFailure);
    }

    private void sendRaw(Net.HttpRequest req, SuccessHandler onSuccess, FailHandler onFailure) {
        String method = req.getMethod();
        String url    = req.getUrl();
        Gdx.net.sendHttpRequest(req, new Net.HttpResponseListener() {
            @Override
            public void handleHttpResponse(Net.HttpResponse r) {
                String raw    = r.getResultAsString();
                int    status = r.getStatus().getStatusCode();
                if (status < 200 || status >= 300) {
                    String safeUrl = url.replaceAll("\\?auth=[^&]*", "?auth=***");
                    Gdx.app.error("RoomService", method + " " + safeUrl + " → HTTP " + status);
                    Gdx.app.postRunnable(() -> onFailure.fail("Server error " + status));
                    return;
                }
                if (raw == null || raw.trim().equals("null") || raw.isEmpty()) {
                    onSuccess.handle(null);
                    return;
                }
                try {
                    JsonValue json = new JsonReader().parse(raw);
                    onSuccess.handle(json);
                } catch (Exception e) {
                    Gdx.app.error("RoomService", "JSON parse error: " + raw);
                    Gdx.app.postRunnable(() -> onFailure.fail("Unexpected server response."));
                }
            }
            @Override public void failed(Throwable t) {
                Gdx.app.error("RoomService", "Network error: " + t.getMessage());
                Gdx.app.postRunnable(() -> onFailure.fail("Network error — check your connection."));
            }
            @Override public void cancelled() {}
        });
    }

    private interface SuccessHandler { void handle(JsonValue response); }
    private interface FailHandler    { void fail(String message); }

}
