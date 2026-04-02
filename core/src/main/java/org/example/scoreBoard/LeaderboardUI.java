package org.example.scoreBoard;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
        float btnScale = uiScale * 0.75f;

        CourseType[] types = {CourseType.HOLES_18, CourseType.HOLES_9, CourseType.HOLES_1};
        String[] labels = {"18H", "9H", "1H"};

        for (int i = 0; i < types.length; i++) {
            final CourseType type = types[i];
            TextButton btn = new TextButton(labels[i], skin, "default");
            btn.getLabel().setFontScale(btnScale);
            if (type == currentCourseType) btn.setChecked(true);
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    currentCourseType = type;
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
        float btnScale = uiScale * (isAndroid ? 0.65f : 0.75f);
        int cols = isAndroid ? 2 : 3;
        int currentCell = 0;

        for (final String diff : GameConfig.Difficulty.getNames()) {
            TextButton btn = new TextButton(diff, skin, "default");
            btn.getLabel().setFontScale(btnScale);
            if (diff.equals(currentDifficulty)) btn.setChecked(true);
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    currentDifficulty = diff;
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
        float textScale = uiScale * 0.70f;

        if (currentCourseType == CourseType.HOLES_1) {
            updateTableOneHole(entries, uiScale, totalWidth, textScale);
        } else {
            updateTableStandard(entries, uiScale, totalWidth, textScale);
        }
    }

    private void updateTableStandard(Array<HighscoreService.HighscoreEntry> entries, float uiScale, float totalWidth, float textScale) {
        float rkW = totalWidth * 0.20f;
        float playerW = totalWidth * 0.39f;
        float scoreW = totalWidth * 0.23f;
        float timeW = totalWidth * 0.18f;

        scoreTable.add(new Label("RANK", skin, "default")).width(rkW).left();
        scoreTable.add(new Label("PLAYER", skin, "default")).width(playerW).left();
        scoreTable.add(new Label("SCORE", skin, "default")).width(scoreW).center();
        scoreTable.add(new Label("TIME", skin, "default")).width(timeW).right();
        scoreTable.row();
        addDivider(4, uiScale);

        if (entries == null || entries.size == 0) {
            scoreTable.add(new Label("No scores yet", skin, "default")).colspan(4).center().padTop(30 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Label rL = makeLabel(rank + ".", textScale);
            scoreTable.add(rL).width(rkW).left();
            Label nL = makeLabel(entry.name, textScale);
            nL.setEllipsis("...");
            scoreTable.add(nL).width(playerW).left();
            scoreTable.add(makeLabel(String.valueOf(entry.score), textScale)).width(scoreW).center();
            scoreTable.add(makeLabel(formatSubmittedTime(entry.date), textScale)).width(timeW).right();
            scoreTable.row().padBottom(4 * uiScale);
            rank++;
        }
    }

    private void updateTableOneHole(Array<HighscoreService.HighscoreEntry> entries, float uiScale, float totalWidth, float textScale) {
        float rkW = totalWidth * 0.12f;
        float playerW = totalWidth * 0.33f;
        float timeW = totalWidth * 0.18f;
        float strokesW = totalWidth * 0.15f;
        float submittedW = totalWidth * 0.22f;

        scoreTable.add(new Label("RANK", skin, "default")).width(rkW).left();
        scoreTable.add(new Label("PLAYER", skin, "default")).width(playerW).left();
        scoreTable.add(new Label("TIME", skin, "default")).width(timeW).center();
        scoreTable.add(new Label("STROKES", skin, "default")).width(strokesW).center();
        scoreTable.add(new Label("SUBMITTED", skin, "default")).width(submittedW).right();
        scoreTable.row();
        addDivider(5, uiScale);

        if (entries == null || entries.size == 0) {
            scoreTable.add(new Label("No scores yet", skin, "default")).colspan(5).center().padTop(30 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            scoreTable.add(makeLabel(rank + ".", textScale)).width(rkW).left();
            Label nL = makeLabel(entry.name, textScale);
            nL.setEllipsis("...");
            scoreTable.add(nL).width(playerW).left();
            scoreTable.add(makeLabel(formatElapsedTime(entry.elapsedTime), textScale)).width(timeW).center();
            scoreTable.add(makeLabel(String.valueOf(entry.score), textScale)).width(strokesW).center();
            scoreTable.add(makeLabel(formatSubmittedTime(entry.date), textScale)).width(submittedW).right();
            scoreTable.row().padBottom(4 * uiScale);
            rank++;
        }
    }

    private void addDivider(int colspan, float uiScale) {
        if (skin.has("white", com.badlogic.gdx.scenes.scene2d.utils.Drawable.class)) {
            scoreTable.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(skin.newDrawable("white", Color.GRAY)))
                    .colspan(colspan).fillX().height(1f).padTop(2).padBottom(8 * uiScale).row();
        }
    }

    private Label makeLabel(String text, float fontScale) {
        Label lbl = new Label(text, skin, "default");
        lbl.setFontScale(fontScale);
        return lbl;
    }

    private String formatSubmittedTime(String date) {
        if (date != null && date.contains("T")) {
            try { return date.split("T")[1].substring(0, 5); } catch (Exception ignored) {}
        }
        return "--:--";
    }

    private String formatElapsedTime(float seconds) {
        int total = (int) seconds;
        return String.format("%02d:%02d", total / 60, total % 60);
    }
}
