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
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    currentDifficulty = diff;
                    refresh();
                }
            });

            tabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            group.add(btn);

            currentCell++;
            if (currentCell % cols == 0) tabTable.row();
        }

        ScrollPane scroll = new ScrollPane(scoreTable, skin);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        TextButton refreshBtn = new TextButton("REFRESH", skin, "default");
        refreshBtn.getLabel().setFontScale(uiScale * 0.75f);
        refreshBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refresh();
            }
        });

        this.add(title).expandX().fillX().padBottom(15 * uiScale).row();
        this.add(tabTable).expandX().fillX().padBottom(10 * uiScale).row();
        this.add(scroll).expand().fill().row();
        this.add(refreshBtn).width(140 * uiScale).height(40 * uiScale).right().padTop(10 * uiScale);
    }

    public void refresh() {
        scoreTable.clear();
        Label loading = new Label("Loading...", skin, "default");
        loading.setFontScale(lastAppliedScale * 0.7f);
        scoreTable.add(loading).center().padTop(40 * lastAppliedScale);

        service.fetchHighscores(currentDifficulty, new HighscoreService.HighscoreListener() {
            @Override
            public void onSuccess(Array<HighscoreService.HighscoreEntry> entries) {
                updateTable(entries, lastAppliedScale);
            }
            @Override
            public void onFailure(Throwable t) {
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
        // Force a small gutter on the left to prevent the 'R' in RANK from clipping
        scoreTable.top().left().padLeft(10 * uiScale);

        // Calculate width while strictly subtracting the table's own padding
        float tablePadding = scoreTable.getPadLeft() + scoreTable.getPadRight();
        float totalWidth = (this.getWidth() - (this.getPadLeft() + this.getPadRight())) - tablePadding;

        // Adjusted percentages to give RANK slightly more breathing room
        float rkW = totalWidth * 0.2f;
        float playerW = totalWidth * 0.39f;
        float scoreW = totalWidth * 0.23f;
        float timeW = totalWidth * 0.18f;

        float textScale = uiScale * 0.70f;

        // Header - Note the use of expandX() to ensure the table fills the available width
        scoreTable.add(new Label("RANK", skin, "default")).width(rkW).left();
        scoreTable.add(new Label("PLAYER", skin, "default")).width(playerW).left();
        scoreTable.add(new Label("SCORE", skin, "default")).width(scoreW).center();
        scoreTable.add(new Label("TIME", skin, "default")).width(timeW).right();
        scoreTable.row();

        if (skin.has("white", com.badlogic.gdx.scenes.scene2d.utils.Drawable.class)) {
            scoreTable.add(new com.badlogic.gdx.scenes.scene2d.ui.Image(skin.newDrawable("white", Color.GRAY)))
                    .colspan(4).fillX().height(1f).padTop(2).padBottom(8 * uiScale).row();
        }

        if (entries == null || entries.size == 0) {
            Label none = new Label("No scores yet", skin, "default");
            none.setFontScale(textScale);
            scoreTable.add(none).colspan(4).center().padTop(30 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Label rL = new Label(rank + ".", skin, "default");
            rL.setFontScale(textScale);
            scoreTable.add(rL).width(rkW).left();

            Label nL = new Label(entry.name, skin, "default");
            nL.setFontScale(textScale);
            nL.setEllipsis("..."); // Explicitly setting ellipsis string
            scoreTable.add(nL).width(playerW).left();

            Label sL = new Label(String.valueOf(entry.score), skin, "default");
            sL.setFontScale(textScale);
            scoreTable.add(sL).width(scoreW).center();

            String displayTime = "--:--";
            if (entry.date != null && entry.date.contains("T")) {
                try { displayTime = entry.date.split("T")[1].substring(0, 5); } catch (Exception e) {}
            }
            Label tL = new Label(displayTime, skin, "default");
            tL.setFontScale(textScale);
            scoreTable.add(tL).width(timeW).right();

            scoreTable.row().padBottom(4 * uiScale);
            rank++;
        }
    }
}