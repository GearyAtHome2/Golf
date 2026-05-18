package com.gearygolf.golf.multiplayer;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.math.Interpolation;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.actions.Actions;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.FocusListener;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.viewport.ExtendViewport;

import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.Platform;
import com.gearygolf.golf.auth.UserSession;
import com.gearygolf.golf.hud.UIUtils;

import java.util.Map;

/**
 * Multiplayer lobby screen.
 *
 * Views:
 *   MAIN   — Create Room / Join Room / Back
 *   LOBBY  — room code, player list (name + difficulty + ready), own difficulty selector,
 *             Ready/Unready button (guests) or START button (host, enabled when all guests ready)
 *   JOIN   — 4-letter code entry
 */
public class MultiplayerLobbyScreen {

    private enum View { MAIN, LOBBY, JOIN }

    public interface Callback {
        void onStartGame(RoomService.RoomState room);
        void onBack();
    }

    private final Stage       stage;
    private final Skin        skin;
    private final RoomService roomService;
    private final UserSession userSession;
    private final Callback    callback;

    private final BitmapFont              fieldFont;
    private final TextField.TextFieldStyle fieldStyle;

    private View    view       = View.MAIN;
    private boolean busy       = false;
    private Table   innerPanel = null;
    private final Vector2 tempVec = new Vector2();

    private RoomService.RoomState  currentRoom        = null;
    private GameConfig.Difficulty  selectedDifficulty = GameConfig.Difficulty.NOVICE;
    private boolean                isReady            = false;

    // Live label references updated in-place during polls
    private Label      statusLabel    = null;
    private Table      playersTable   = null;
    private Label      difficultyLabel = null;
    private TextButton startBtn        = null;
    private TextButton readyToggleBtn  = null;
    private TextButton prevDiffBtn     = null;
    private TextButton nextDiffBtn     = null;

    private float pollTimer = 0f;
    private static final float POLL_INTERVAL = 0.5f;

    public MultiplayerLobbyScreen(Skin skin, RoomService roomService,
                                   UserSession userSession, Callback callback) {
        this.skin        = skin;
        this.roomService = roomService;
        this.userSession = userSession;
        this.callback    = callback;
        this.stage       = new Stage(new ExtendViewport(1280, 720));

        this.fieldFont = new BitmapFont(Gdx.files.internal("font/golf.fnt"));
        this.fieldFont.getData().setScale(0.60f);

        TextField.TextFieldStyle base = skin.get(TextField.TextFieldStyle.class);
        this.fieldStyle = new TextField.TextFieldStyle();
        fieldStyle.font             = fieldFont;
        fieldStyle.fontColor        = base.fontColor != null ? base.fontColor : Color.WHITE;
        fieldStyle.background       = base.background;
        fieldStyle.cursor           = base.cursor;
        fieldStyle.selection        = base.selection;
        fieldStyle.messageFontColor = Color.GRAY;
        fieldStyle.messageFont      = fieldFont;

        buildUI();
    }

    public Stage getStage() { return stage; }

    public void reset() {
        currentRoom        = null;
        selectedDifficulty = GameConfig.Difficulty.NOVICE;
        isReady            = false;
        view               = View.MAIN;
        buildUI();
    }

    public void resize(int w, int h) { stage.getViewport().update(w, h, true); }

    public void render(float delta) {
        if (view == View.LOBBY && currentRoom != null) {
            pollTimer += delta;
            if (pollTimer >= POLL_INTERVAL) {
                pollTimer = 0f;
                doPoll();
            }
        }
        stage.act(delta);
        stage.draw();
    }

    public void dispose() {
        stage.dispose();
        fieldFont.dispose();
    }

    // -------------------------------------------------------------------------
    // UI construction
    // -------------------------------------------------------------------------

    private void buildUI() {
        stage.clear();
        statusLabel     = null;
        playersTable    = null;
        difficultyLabel = null;
        startBtn        = null;
        readyToggleBtn  = null;
        prevDiffBtn     = null;
        nextDiffBtn     = null;
        innerPanel      = null;
        busy            = false;
        pollTimer       = 0f;

        boolean isAndroid  = Platform.isAndroid();
        float panelW       = isAndroid ? 680f : 480f;
        float btnH         = isAndroid ? 90f  : 54f;
        float pad          = 28f;
        float sp           = 12f;
        float titleScale   = isAndroid ? 1.40f : 1.35f;
        float labelScale   = isAndroid ? 1.15f : 1.05f;
        float btnScale     = isAndroid ? 1.20f : 0.95f;

        Table root = new Table();
        root.setFillParent(true);
        root.setTouchable(Touchable.enabled);
        root.setBackground(UIUtils.createRoundedRectDrawable(new Color(0f, 0f, 0f, 0.65f), 0));

        Table panel = new Table();
        this.innerPanel = panel;
        panel.setBackground(UIUtils.createGoldBorderedPanel(new Color(0.05f, 0.05f, 0.05f, 0.97f), 3));
        panel.pad(pad);

        Label title = new Label("GEARY GOLF", skin, "default");
        title.setFontScale(titleScale);
        title.setColor(Color.GOLD);
        title.setAlignment(Align.center);
        panel.add(title).expandX().fillX().padBottom(sp).row();

        Label heading = new Label("MULTIPLAYER", skin, "default");
        heading.setFontScale(labelScale * 0.88f);
        heading.setColor(Color.LIGHT_GRAY);
        heading.setAlignment(Align.center);
        panel.add(heading).expandX().fillX().padBottom(pad).row();

        Table content = new Table();
        switch (view) {
            case MAIN  -> buildMainView (content, panelW - 60f, btnH, sp, labelScale, btnScale);
            case LOBBY -> buildLobbyView(content, panelW - 60f, btnH, sp, labelScale, btnScale);
            case JOIN  -> buildJoinView (content, panelW - 60f, btnH, sp, labelScale, btnScale);
        }
        panel.add(content).width(panelW - 60f).row();

        Label errLbl = new Label("", skin, "default");
        errLbl.setFontScale(labelScale * 0.82f);
        errLbl.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        errLbl.setAlignment(Align.center);
        errLbl.setWrap(true);
        panel.add(errLbl).width(panelW - 60f).padTop(sp).row();
        statusLabel = errLbl;

        root.add(panel).width(panelW).expand().center();
        stage.addActor(root);

        root.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent event, float x, float y, int pointer, int button) {
                if (!(event.getTarget() instanceof TextField)) {
                    stage.setKeyboardFocus(null);
                    Gdx.input.setOnscreenKeyboardVisible(false);
                    shiftPanelToActor(null);
                }
                return true;
            }
        });
    }

    private void shiftPanelToActor(Actor target) {
        if (innerPanel == null) return;
        innerPanel.clearActions();
        if (target == null) {
            innerPanel.addAction(Actions.moveToAligned(
                stage.getWidth() / 2f, stage.getHeight() / 2f, Align.center, 0.25f, Interpolation.sineOut));
            return;
        }
        float targetHeight = stage.getHeight() * 0.80f;
        tempVec.set(target.getWidth() / 2f, target.getHeight() / 2f);
        target.localToStageCoordinates(tempVec);
        float dy = targetHeight - tempVec.y;
        innerPanel.addAction(Actions.moveBy(0, dy, 0.25f, Interpolation.sineOut));
    }

    private void buildMainView(Table t, float w, float bh, float sp,
                                float ls, float bs) {
        TextButton createBtn = makeButton("CREATE ROOM", bs);
        TextButton joinBtn   = makeButton("JOIN ROOM",   bs);
        TextButton backBtn   = makeButton("< BACK",      bs);

        createBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) doCreateRoom();
            }
        });
        joinBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { view = View.JOIN; buildUI(); }
            }
        });
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) { callback.onBack(); }
        });

        t.add(createBtn).width(w).height(bh).padBottom(sp).row();
        t.add(joinBtn  ).width(w).height(bh).padBottom(sp).row();
        t.add(backBtn  ).width(w).height(bh).row();
    }

    private void buildLobbyView(Table t, float w, float bh, float sp,
                                 float ls, float bs) {
        if (currentRoom == null) return;

        boolean isHost    = userSession.getUid().equals(currentRoom.hostUid);
        boolean canChange = isHost || !isReady;

        // Room code
        Label codeLabel = new Label("ROOM CODE", skin, "default");
        codeLabel.setFontScale(ls * 0.78f);
        codeLabel.setColor(Color.LIGHT_GRAY);
        codeLabel.setAlignment(Align.center);

        Label codeValue = new Label(currentRoom.roomCode, skin, "default");
        codeValue.setFontScale(ls * 1.6f);
        codeValue.setColor(Color.GOLD);
        codeValue.setAlignment(Align.center);

        t.add(codeLabel).expandX().fillX().padBottom(4f).row();
        t.add(codeValue).expandX().fillX().padBottom(sp * 1.5f).row();

        // Players table
        Label playersTitle = new Label("PLAYERS", skin, "default");
        playersTitle.setFontScale(ls * 0.78f);
        playersTitle.setColor(Color.LIGHT_GRAY);
        playersTitle.setAlignment(Align.center);
        t.add(playersTitle).expandX().fillX().padBottom(4f).row();

        playersTable = new Table();
        populatePlayersTable(playersTable, w, ls, isHost);
        t.add(playersTable).width(w).padBottom(sp * 1.5f).row();

        // Own difficulty selector (everyone has one; locked for guests when ready)
        Label diffTitle = new Label("YOUR DIFFICULTY", skin, "default");
        diffTitle.setFontScale(ls * 0.78f);
        diffTitle.setColor(Color.LIGHT_GRAY);
        diffTitle.setAlignment(Align.center);
        t.add(diffTitle).expandX().fillX().padBottom(4f).row();

        GameConfig.Difficulty[] diffs = GameConfig.Difficulty.values();
        prevDiffBtn = makeButton("<", bs * 0.85f);
        nextDiffBtn = makeButton(">", bs * 0.85f);
        Label dLbl  = new Label(selectedDifficulty.name(), skin, "default");
        dLbl.setFontScale(ls * 1.0f);
        dLbl.setColor(canChange ? Color.YELLOW : Color.GRAY);
        dLbl.setAlignment(Align.center);
        difficultyLabel = dLbl;

        setDiffBtnEnabled(prevDiffBtn, canChange);
        setDiffBtnEnabled(nextDiffBtn, canChange);

        prevDiffBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!canChange || busy) return;
                int idx = (selectedDifficulty.ordinal() - 1 + diffs.length) % diffs.length;
                selectedDifficulty = diffs[idx];
                difficultyLabel.setText(selectedDifficulty.name());
                pushPlayerMeta();
            }
        });
        nextDiffBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!canChange || busy) return;
                int idx = (selectedDifficulty.ordinal() + 1) % diffs.length;
                selectedDifficulty = diffs[idx];
                difficultyLabel.setText(selectedDifficulty.name());
                pushPlayerMeta();
            }
        });

        Table diffRow = new Table();
        float arrowW = bh * 1.2f;
        diffRow.add(prevDiffBtn).width(arrowW).height(bh);
        diffRow.add(dLbl).expandX().fillX().height(bh).padLeft(sp).padRight(sp);
        diffRow.add(nextDiffBtn).width(arrowW).height(bh);
        t.add(diffRow).width(w).padBottom(sp * 1.5f).row();

        // Host: START (gated on all guests ready) | Guest: READY / UNREADY toggle
        if (isHost) {
            boolean allReady = allGuestsReady();
            startBtn = makeButton("START GAME", bs);
            startBtn.setDisabled(!allReady);
            startBtn.setTouchable(allReady ? Touchable.enabled : Touchable.disabled);
            startBtn.getLabel().setColor(allReady ? Color.WHITE : Color.GRAY);
            startBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    if (!busy && allGuestsReady()) doStartGame();
                }
            });
            t.add(startBtn).width(w).height(bh).padBottom(sp).row();

            if (!allReady) {
                Label waiting = new Label("Waiting for players to ready up...", skin, "default");
                waiting.setFontScale(ls * 0.75f);
                waiting.setColor(Color.LIGHT_GRAY);
                waiting.setAlignment(Align.center);
                t.add(waiting).expandX().fillX().padBottom(sp).row();
            }
        } else {
            String readyText = isReady ? "UNREADY" : "READY";
            readyToggleBtn = makeButton(readyText, bs);
            readyToggleBtn.getLabel().setColor(isReady ? Color.ORANGE : Color.GREEN);
            readyToggleBtn.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor a) {
                    if (!busy) doToggleReady();
                }
            });
            t.add(readyToggleBtn).width(w).height(bh).padBottom(sp).row();
        }

        TextButton leaveBtn = makeButton("LEAVE ROOM", bs);
        leaveBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) doLeaveRoom();
            }
        });
        t.add(leaveBtn).width(w).height(bh).row();
    }

    /** Clears and repopulates the players table. Called on initial build and each poll update. */
    private void populatePlayersTable(Table t, float w, float ls, boolean isHost) {
        t.clear();
        if (currentRoom == null || currentRoom.players.isEmpty()) {
            Label none = new Label("(no players)", skin, "default");
            none.setFontScale(ls * 0.82f);
            none.setColor(Color.GRAY);
            none.setAlignment(Align.center);
            t.add(none).expandX().fillX().row();
            return;
        }

        String localUid   = userSession.getUid();
        float  nameW      = w * 0.55f;
        float  diffW      = w * 0.15f;
        float  statusW    = w * 0.30f;
        float  rowScale   = ls * 0.85f;

        for (Map.Entry<String, String> entry : currentRoom.players.entrySet()) {
            String uid  = entry.getKey();
            String name = entry.getValue();

            RoomService.PlayerMeta meta = currentRoom.playerMeta.get(uid);
            String diffName = meta != null ? meta.difficulty : "NOVICE";
            boolean ready   = meta != null && meta.ready;
            boolean isThisHost = uid.equals(currentRoom.hostUid);

            // Name
            String displayName = name + (isThisHost ? " \u265a" : ""); // ♚ for host
            Label nameLbl = new Label(displayName, skin, "default");
            nameLbl.setFontScale(rowScale);
            nameLbl.setColor(uid.equals(localUid) ? Color.YELLOW : Color.WHITE);
            nameLbl.setEllipsis("...");

            // Difficulty first letter
            String diffLetter = diffName.isEmpty() ? "N" : String.valueOf(diffName.charAt(0));
            Label diffLbl = new Label(diffLetter, skin, "default");
            diffLbl.setFontScale(rowScale);
            diffLbl.setColor(Color.LIGHT_GRAY);
            diffLbl.setAlignment(Align.center);

            // Ready / role indicator
            String statusText;
            Color  statusColor;
            if (isThisHost) {
                statusText  = "HOST";
                statusColor = Color.GOLD;
            } else if (ready) {
                statusText  = "READY";
                statusColor = Color.GREEN;
            } else {
                statusText  = "NOT READY";
                statusColor = Color.RED;
            }
            Label statusLbl = new Label(statusText, skin, "default");
            statusLbl.setFontScale(rowScale * 0.85f);
            statusLbl.setColor(statusColor);
            statusLbl.setAlignment(Align.right);

            t.add(nameLbl).width(nameW).left().padBottom(4f);
            t.add(diffLbl).width(diffW).center().padBottom(4f);
            t.add(statusLbl).width(statusW).right().padBottom(4f).row();
        }
    }

    private void buildJoinView(Table t, float w, float bh, float sp,
                                float ls, float bs) {
        TextField codeField = new TextField("", fieldStyle);
        codeField.setMessageText("ABCD");
        codeField.setMaxLength(4);

        codeField.addListener(new FocusListener() {
            @Override
            public void keyboardFocusChanged(FocusEvent event, Actor actor, boolean focused) {
                if (focused) {
                    Gdx.input.setOnscreenKeyboardVisible(true);
                    shiftPanelToActor(actor);
                } else {
                    Gdx.app.postRunnable(() -> {
                        if (!(stage.getKeyboardFocus() instanceof TextField)) {
                            shiftPanelToActor(null);
                        }
                    });
                }
            }
        });

        TextButton pasteBtn = makeButton("PASTE", bs * 0.78f);
        pasteBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                String clip = Gdx.app.getClipboard().getContents();
                if (clip != null) codeField.setText(clip.trim().toUpperCase());
            }
        });

        Table codeRow = new Table();
        codeRow.add(codeField).width(w - bh - 8f).height(bh);
        codeRow.add(pasteBtn ).width(bh).height(bh).padLeft(8f);

        TextButton joinBtn = makeButton("JOIN", bs);
        TextButton backBtn = makeButton("< BACK", bs);

        joinBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) {
                    String code = codeField.getText().trim().toUpperCase();
                    if (code.length() != 4) { showError("Please enter a 4-letter room code."); return; }
                    doJoinRoom(code);
                }
            }
        });
        backBtn.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent e, Actor a) {
                if (!busy) { view = View.MAIN; buildUI(); }
            }
        });

        Label hint = new Label("Enter the 4-letter room code", skin, "default");
        hint.setFontScale(ls * 0.82f);
        hint.setColor(Color.LIGHT_GRAY);
        hint.setAlignment(Align.center);

        t.add(hint    ).expandX().fillX().padBottom(sp).row();
        t.add(codeRow ).width(w).padBottom(sp).row();
        t.add(joinBtn ).width(w).height(bh).padBottom(sp).row();
        t.add(backBtn ).width(w).height(bh).row();
    }

    // -------------------------------------------------------------------------
    // Network actions
    // -------------------------------------------------------------------------

    private void doCreateRoom() {
        busy = true;
        showStatus("Creating room...");
        long seed = System.nanoTime();
        roomService.createRoom(
            userSession.getIdToken(), userSession.getUid(), userSession.getDisplayName(), seed,
            new RoomService.RoomCallback() {
                @Override public void onSuccess(RoomService.RoomState room) {
                    busy               = false;
                    currentRoom        = room;
                    selectedDifficulty = GameConfig.Difficulty.NOVICE;
                    isReady            = false;
                    view               = View.LOBBY;
                    buildUI();
                }
                @Override public void onFailure(String msg) { busy = false; showError(msg); }
            });
    }

    private void doJoinRoom(String code) {
        busy = true;
        showStatus("Joining room...");
        roomService.joinRoom(
            userSession.getIdToken(), userSession.getUid(), userSession.getDisplayName(), code,
            new RoomService.RoomCallback() {
                @Override public void onSuccess(RoomService.RoomState room) {
                    busy               = false;
                    currentRoom        = room;
                    selectedDifficulty = GameConfig.Difficulty.NOVICE;
                    isReady            = false;
                    view               = View.LOBBY;
                    buildUI();
                }
                @Override public void onFailure(String msg) { busy = false; showError(msg); }
            });
    }

    private void doLeaveRoom() {
        if (currentRoom == null) { view = View.MAIN; buildUI(); return; }
        busy = true;
        showStatus("Leaving room...");
        boolean isHost = userSession.getUid().equals(currentRoom.hostUid);
        RoomService.SimpleCallback onDone = new RoomService.SimpleCallback() {
            @Override public void onSuccess() { onLeftRoom(); }
            @Override public void onFailure(String msg) { onLeftRoom(); } // best-effort
        };
        if (isHost) {
            roomService.deleteRoom(userSession.getIdToken(), currentRoom.roomCode, onDone);
        } else {
            roomService.leaveRoom(userSession.getIdToken(), userSession.getUid(),
                currentRoom.roomCode, onDone);
        }
    }

    private void onLeftRoom() {
        busy               = false;
        currentRoom        = null;
        selectedDifficulty = GameConfig.Difficulty.NOVICE;
        isReady            = false;
        view               = View.MAIN;
        buildUI();
    }

    private void doToggleReady() {
        if (currentRoom == null) return;
        boolean newReady = !isReady;
        busy = true;
        showStatus(newReady ? "Readying up..." : "Unreadying...");
        roomService.setPlayerMeta(
            userSession.getIdToken(), currentRoom.roomCode, userSession.getUid(),
            selectedDifficulty.name(), newReady,
            new RoomService.SimpleCallback() {
                @Override public void onSuccess() {
                    busy    = false;
                    isReady = newReady;
                    buildUI(); // rebuild to lock/unlock difficulty selector
                }
                @Override public void onFailure(String msg) { busy = false; showError(msg); }
            });
    }

    /** Pushes the local player's current difficulty and ready state to RTDB. Disables arrows while in flight. */
    private void pushPlayerMeta() {
        if (currentRoom == null) return;
        setDiffBtnEnabled(prevDiffBtn, false);
        setDiffBtnEnabled(nextDiffBtn, false);
        roomService.setPlayerMeta(
            userSession.getIdToken(), currentRoom.roomCode, userSession.getUid(),
            selectedDifficulty.name(), isReady,
            new RoomService.SimpleCallback() {
                @Override public void onSuccess() {
                    boolean canChange = userSession.getUid().equals(currentRoom.hostUid) || !isReady;
                    setDiffBtnEnabled(prevDiffBtn, canChange);
                    setDiffBtnEnabled(nextDiffBtn, canChange);
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.error("LobbyScreen", "setPlayerMeta failed: " + msg);
                    boolean canChange = userSession.getUid().equals(currentRoom.hostUid) || !isReady;
                    setDiffBtnEnabled(prevDiffBtn, canChange);
                    setDiffBtnEnabled(nextDiffBtn, canChange);
                }
            });
    }

    private void doStartGame() {
        if (currentRoom == null) return;
        busy = true;
        showStatus("Starting game...");
        // Write host's final difficulty then flip room to active
        roomService.setPlayerMeta(
            userSession.getIdToken(), currentRoom.roomCode, userSession.getUid(),
            selectedDifficulty.name(), true,
            new RoomService.SimpleCallback() {
                @Override public void onSuccess() {
                    roomService.setRoomState(
                        userSession.getIdToken(), currentRoom.roomCode, "active", 0,
                        new RoomService.SimpleCallback() {
                            @Override public void onSuccess() {
                                busy = false;
                                RoomService.RoomState room = currentRoom;
                                room.state = "active";
                                // Ensure host's own meta is reflected locally
                                RoomService.PlayerMeta hostMeta = new RoomService.PlayerMeta();
                                hostMeta.difficulty = selectedDifficulty.name();
                                hostMeta.ready      = true;
                                room.playerMeta.put(userSession.getUid(), hostMeta);
                                currentRoom        = null;
                                selectedDifficulty = GameConfig.Difficulty.NOVICE;
                                isReady            = false;
                                view               = View.MAIN;
                                callback.onStartGame(room);
                            }
                            @Override public void onFailure(String msg) { busy = false; showError(msg); }
                        });
                }
                @Override public void onFailure(String msg) { busy = false; showError(msg); }
            });
    }

    private void doPoll() {
        if (currentRoom == null) return;
        roomService.pollRoom(userSession.getIdToken(), currentRoom.roomCode,
            new RoomService.RoomCallback() {
                @Override public void onSuccess(RoomService.RoomState room) {
                    currentRoom = room;
                    updateLobbyUI();
                    if ("active".equals(room.state)) {
                        currentRoom        = null;
                        selectedDifficulty = GameConfig.Difficulty.NOVICE;
                        isReady            = false;
                        view               = View.MAIN;
                        callback.onStartGame(room);
                    }
                }
                @Override public void onFailure(String msg) {
                    Gdx.app.log("LobbyScreen", "poll failed: " + msg);
                }
            });
    }

    // -------------------------------------------------------------------------
    // In-place lobby update (called each poll — avoids full rebuild)
    // -------------------------------------------------------------------------

    private void updateLobbyUI() {
        if (currentRoom == null) return;
        boolean isHost = userSession.getUid().equals(currentRoom.hostUid);

        // Repopulate the players table in place
        if (playersTable != null) {
            float approxW = Platform.isAndroid() ? 620f : 420f;
            float ls = Platform.isAndroid() ? 1.15f : 1.05f;
            populatePlayersTable(playersTable, approxW, ls, isHost);
        }

        // Update START button enabled state for host
        if (isHost && startBtn != null) {
            boolean allReady = allGuestsReady();
            startBtn.setDisabled(!allReady);
            startBtn.setTouchable(allReady ? Touchable.enabled : Touchable.disabled);
            startBtn.getLabel().setColor(allReady ? Color.WHITE : Color.GRAY);
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private boolean allGuestsReady() {
        if (currentRoom == null) return false;
        for (Map.Entry<String, String> entry : currentRoom.players.entrySet()) {
            String uid = entry.getKey();
            if (uid.equals(currentRoom.hostUid)) continue;
            RoomService.PlayerMeta meta = currentRoom.playerMeta.get(uid);
            if (meta == null || !meta.ready) return false;
        }
        return true; // no guests, or all guests ready
    }

    private void setDiffBtnEnabled(TextButton btn, boolean enabled) {
        btn.setTouchable(enabled ? Touchable.enabled : Touchable.disabled);
        btn.getLabel().setColor(enabled ? Color.WHITE : Color.GRAY);
    }

    private void showError(String msg) {
        if (statusLabel == null) return;
        statusLabel.setColor(new Color(1f, 0.35f, 0.35f, 1f));
        statusLabel.setText(msg);
    }

    private void showStatus(String msg) {
        if (statusLabel == null) return;
        statusLabel.setColor(Color.LIGHT_GRAY);
        statusLabel.setText(msg);
    }

    private TextButton makeButton(String text, float scale) {
        TextButton btn = new TextButton(text, skin);
        btn.getLabel().setFontScale(scale);
        return btn;
    }
}
