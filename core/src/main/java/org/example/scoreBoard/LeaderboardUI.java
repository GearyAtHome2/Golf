package org.example.scoreBoard;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import org.example.GameConfig;

public class LeaderboardUI extends Table {
    private final HighscoreService service;
    private final Skin skin;
    private final Table scoreTable;
    private String currentDifficulty = GameConfig.Difficulty.NOVICE.name();
    private CourseType currentCourseType = CourseType.HOLES_18;
    private float lastAppliedScale = 1.0f;
    private Stage boundStage;
    private ScorecardPopup currentPopup;

    public LeaderboardUI(Skin skin, HighscoreService service) {
        this.skin = skin;
        this.service = service;
        ensureStylesExist();
        this.scoreTable = new Table();
        this.right();
        this.scoreTable.top().left();

        if (skin.has("white", com.badlogic.gdx.scenes.scene2d.utils.Drawable.class)) {
            this.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.85f)));
        }

        rebuild(1.0f);
        refresh();
    }

    /** Must be called after adding to a stage so scorecard popups have a stage to attach to. */
    public void bindStage(Stage stage) {
        this.boundStage = stage;
    }

    private void ensureStylesExist() {
        if (!skin.has("default", Label.LabelStyle.class)) {
            Label.LabelStyle labelStyle = new Label.LabelStyle();
            labelStyle.font = skin.getFont("default-font");
            labelStyle.fontColor = Color.WHITE;
            skin.add("default", labelStyle);
        }
        if (!skin.has("default", ScrollPane.ScrollPaneStyle.class)) {
            ScrollPane.ScrollPaneStyle scrollStyle = new ScrollPane.ScrollPaneStyle();
            skin.add("default", scrollStyle);
        }
    }

    public void rebuild(float uiScale) {
        this.lastAppliedScale = uiScale;
        this.clearChildren();

        float padding = 20 * uiScale;
        this.pad(padding);
        this.right();

        Label title = new Label("DAILY LEADERBOARD", skin, "default");
        title.setAlignment(Align.center);
        title.setFontScale(uiScale * 1.0f);

        // Course type tab row (above difficulty tabs)
        Table courseTabTable = buildCourseTypeTabs(uiScale);

        // Difficulty tab row
        Table tabTable = buildDifficultyTabs(uiScale);

        ScrollPane scroll = new ScrollPane(scoreTable, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        TextButton refreshBtn = new TextButton("REFRESH", skin, "default");
        refreshBtn.getLabel().setFontScale(uiScale * 0.75f);
        refreshBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) { refresh(); }
        });

        this.add(title).expandX().fillX().padBottom(10 * uiScale).row();
        this.add(courseTabTable).expandX().fillX().padBottom(6 * uiScale).row();
        this.add(tabTable).expandX().fillX().padBottom(10 * uiScale).row();
        this.add(scroll).expand().fill().row();
        this.add(refreshBtn).width(140 * uiScale).height(40 * uiScale).right().padTop(10 * uiScale);
    }

    private Table buildCourseTypeTabs(float uiScale) {
        Table courseTabTable = new Table();
        ButtonGroup<TextButton> courseGroup = new ButtonGroup<>();
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float btnScale = uiScale * (isAndroid ? 0.75f : 1.0f);
        float btnW = (this.getWidth() - 40 * uiScale) / 3f * 0.88f;

        CourseType[] types = {CourseType.HOLES_18, CourseType.HOLES_9, CourseType.HOLES_1};
        String[] labels = {"18-Hole", "9-Hole", "1-Hole"};

        for (int i = 0; i < types.length; i++) {
            final CourseType type = types[i];
            TextButton btn = new TextButton(labels[i], skin, "default");
            btn.getLabel().setFontScale(fitBtnScale(labels[i], btnScale, btnW));
            if (type == currentCourseType) {
                btn.setChecked(true);
                btn.setColor(Color.GRAY);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (type == currentCourseType) return;
                    currentCourseType = type;
                    rebuild(lastAppliedScale);
                    refresh();
                }
            });
            courseTabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            courseGroup.add(btn);
        }
        return courseTabTable;
    }

    private Table buildDifficultyTabs(float uiScale) {
        Table tabTable = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float btnScale = uiScale * (isAndroid ? 0.65f : 1.0f);
        int cols = isAndroid ? 2 : 3;
        float btnW = (this.getWidth() - 40 * uiScale) / cols * 0.88f;
        int currentCell = 0;

        for (final String diff : GameConfig.Difficulty.getNames()) {
            TextButton btn = new TextButton(diff, skin, "default");
            btn.getLabel().setFontScale(fitBtnScale(diff, btnScale, btnW));
            if (diff.equals(currentDifficulty)) {
                btn.setChecked(true);
                btn.setColor(Color.GRAY);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (diff.equals(currentDifficulty)) return;
                    currentDifficulty = diff;
                    rebuild(lastAppliedScale);
                    refresh();
                }
            });
            tabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            group.add(btn);
            currentCell++;
            if (currentCell % cols == 0) tabTable.row();
        }
        return tabTable;
    }

    public void refresh() {
        scoreTable.clear();
        Label loading = new Label("Loading...", skin, "default");
        loading.setFontScale(lastAppliedScale * 0.7f);
        scoreTable.add(loading).center().padTop(40 * lastAppliedScale);

        service.fetchHighscores(currentDifficulty, currentCourseType, new HighscoreService.HighscoreListener() {
            @Override public void onSuccess(Array<HighscoreService.HighscoreEntry> entries) { updateTable(entries, lastAppliedScale); }
            @Override public void onFailure(Throwable t) {
                scoreTable.clear();
                Label error = new Label("Offline", skin, "default");
                error.setColor(Color.RED);
                error.setFontScale(lastAppliedScale * 0.7f);
                scoreTable.add(error).center().padTop(40 * lastAppliedScale);
            }
        });
    }

    private void updateTable(Array<HighscoreService.HighscoreEntry> entries, float uiScale) {
        scoreTable.clear();
        scoreTable.top().left().padLeft(10 * uiScale);

        float tablePadding = scoreTable.getPadLeft() + scoreTable.getPadRight();
        float totalWidth = (this.getWidth() - (this.getPadLeft() + this.getPadRight())) - tablePadding;
        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float textScale = uiScale * (isAndroid ? 0.70f : 0.88f);

        if (currentCourseType == CourseType.HOLES_1) {
            updateTableOneHole(entries, uiScale, totalWidth, textScale);
        } else {
            updateTableStandard(entries, uiScale, totalWidth, textScale);
        }
    }

    // -------------------------------------------------------------------------
    // Standard (9/18-hole) table — rows are sub-Tables for consistent alignment
    // -------------------------------------------------------------------------

    private void updateTableStandard(Array<HighscoreService.HighscoreEntry> entries, float uiScale, float totalWidth, float textScale) {
        float rkW     = totalWidth * 0.20f;
        float playerW = totalWidth * 0.39f;
        float scoreW  = totalWidth * 0.23f;
        float timeW   = totalWidth * 0.18f;

        // Header row
        Table hdr = buildRowTable(rkW, playerW, scoreW, timeW,
            makeLabel("RANK",   textScale, Color.LIGHT_GRAY),
            makeLabel("PLAYER", textScale, Color.LIGHT_GRAY),
            makeLabel("SCORE",  textScale, Color.LIGHT_GRAY),
            makeLabel("TIME",   textScale, Color.LIGHT_GRAY));
        scoreTable.add(hdr).expandX().fillX().row();
        addDivider(1, uiScale);

        if (entries == null || entries.size == 0) {
            scoreTable.add(new Label("No scores yet", skin, "default")).colspan(1).center().padTop(30 * uiScale);
            return;
        }

        Drawable hoverBg = skin.has("white", Drawable.class)
            ? skin.newDrawable("white", new Color(1f, 1f, 1f, 0.10f)) : null;

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            final HighscoreService.HighscoreEntry e = entry;
            boolean clickable = entry.hasScorecard();
            Color rc = rankColor(rank);

            Label nL = makeLabel(entry.name, textScale, rc);
            nL.setEllipsis("...");

            Table row = buildRowTable(rkW, playerW, scoreW, timeW,
                makeLabel(rank + ".",                       textScale, rc),
                nL,
                makeLabel(String.valueOf(entry.score),     textScale, rc),
                makeLabel(formatSubmittedTime(entry.date), textScale, rc));

            if (clickable) {
                final Drawable hBg = hoverBg;
                row.addListener(new ClickListener() {
                    @Override public void clicked(InputEvent event, float x, float y) { showScorecard(e); }
                });
                row.addListener(new InputListener() {
                    @Override public void enter(InputEvent event, float x, float y, int pointer, Actor from) {
                        if (hBg != null) row.setBackground(hBg);
                    }
                    @Override public void exit(InputEvent event, float x, float y, int pointer, Actor to) {
                        row.setBackground((Drawable) null);
                    }
                });
            }

            scoreTable.add(row).expandX().fillX().padBottom(4 * uiScale).row();
            rank++;
        }
    }

    private Table buildRowTable(float rkW, float playerW, float scoreW, float timeW,
                                Label rankLbl, Label nameLbl, Label scoreLbl, Label timeLbl) {
        Table row = new Table();
        row.add(rankLbl).width(rkW).left();
        row.add(nameLbl).width(playerW).left();
        row.add(scoreLbl).width(scoreW).center();
        row.add(timeLbl).width(timeW).right();
        return row;
    }

    private void updateTableOneHole(Array<HighscoreService.HighscoreEntry> entries, float uiScale, float totalWidth, float textScale) {
        float rkW        = totalWidth * 0.12f;
        float playerW    = totalWidth * 0.28f;
        float strokesW   = totalWidth * 0.20f;
        float timeW      = totalWidth * 0.20f;
        float submittedW = totalWidth * 0.20f;

        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        float headerScale = textScale * (isAndroid ? 0.8f : 1.0f);
        float rowScale    = textScale * (isAndroid ? 0.8f : 1.0f);

        // Header row
        Table hdr = buildRowTable5(rkW, playerW, strokesW, timeW, submittedW,
            makeLabel("RANK",      headerScale, Color.LIGHT_GRAY),
            makeLabel("PLAYER",    headerScale, Color.LIGHT_GRAY),
            makeLabel("STROKES",   headerScale, Color.LIGHT_GRAY),
            makeLabel("TIME",      headerScale, Color.LIGHT_GRAY),
            makeLabel("SUBMITTED", headerScale, Color.LIGHT_GRAY));
        scoreTable.add(hdr).expandX().fillX().row();
        addDivider(1, uiScale);

        if (entries == null || entries.size == 0) {
            scoreTable.add(new Label("No scores yet", skin, "default")).center().padTop(30 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Color rc = rankColor(rank);
            Label nL = makeLabel(entry.name, rowScale, rc);
            nL.setEllipsis("...");

            Table row = buildRowTable5(rkW, playerW, strokesW, timeW, submittedW,
                makeLabel(rank + ".",                          rowScale, rc),
                nL,
                makeLabel(String.valueOf(entry.score),         rowScale, rc),
                makeLabel(formatElapsedTime(entry.elapsedTime), rowScale, rc),
                makeLabel(formatSubmittedTime(entry.date),      rowScale, rc));

            scoreTable.add(row).expandX().fillX().padBottom(4 * uiScale).row();
            rank++;
        }
    }

    /** Builds a 5-cell horizontal row Table with given column widths and labels. */
    private Table buildRowTable5(float rkW, float playerW, float strokesW, float timeW, float submittedW,
                                 Label rankLbl, Label nameLbl, Label strokesLbl, Label timeLbl, Label submittedLbl) {
        Table row = new Table();
        row.add(rankLbl).width(rkW).left();
        row.add(nameLbl).width(playerW).left();
        row.add(strokesLbl).width(strokesW).center();
        row.add(timeLbl).width(timeW).center();
        row.add(submittedLbl).width(submittedW).right();
        return row;
    }

    // -------------------------------------------------------------------------
    // Scorecard popup
    // -------------------------------------------------------------------------

    private void showScorecard(HighscoreService.HighscoreEntry entry) {
        if (boundStage == null) return;
        if (currentPopup != null) currentPopup.dismiss(boundStage);
        currentPopup = new ScorecardPopup(boundStage, skin, entry);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void addDivider(int colspan, float uiScale) {
        if (skin.has("white", com.badlogic.gdx.scenes.scene2d.utils.Drawable.class)) {
            scoreTable.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(skin.newDrawable("white", Color.GRAY)))
                    .colspan(colspan).fillX().height(1f).padTop(2).padBottom(8 * uiScale).row();
        }
    }

    private Label makeLabel(String text, float fontScale) {
        return makeLabel(text, fontScale, Color.WHITE);
    }

    private Label makeLabel(String text, float fontScale, Color color) {
        Label lbl = new Label(text, skin, "default");
        lbl.setFontScale(fontScale);
        lbl.setColor(color);
        return lbl;
    }

    private Color rankColor(int rank) {
        if (rank == 1) return Color.GOLD;
        if (rank == 2) return new Color(0.78f, 0.78f, 0.78f, 1f);
        if (rank == 3) return new Color(0.80f, 0.50f, 0.20f, 1f);
        return Color.WHITE;
    }

    private String formatSubmittedTime(String date) {
        if (date != null && date.contains("T")) {
            try { return date.split("T")[1].substring(0, 5); } catch (Exception ignored) {}
        }
        return "--:--";
    }

    private String formatElapsedTime(float seconds) {
        int total = (int) seconds;
        int tenths = (int) (seconds * 10) % 10;
        return String.format("%02d:%02d.%d", total / 60, total % 60, tenths);
    }

    /** Returns a font scale that fits {@code text} within {@code maxWidth}, capped at {@code desired}. */
    private float fitBtnScale(String text, float desired, float maxWidth) {
        if (maxWidth <= 0) return desired;
        BitmapFont font = skin.getFont("default-font");
        GlyphLayout gl = new GlyphLayout();
        gl.setText(font, text);
        float textW = gl.width * desired;
        return textW > maxWidth ? desired * (maxWidth / textW) : desired;
    }
}
