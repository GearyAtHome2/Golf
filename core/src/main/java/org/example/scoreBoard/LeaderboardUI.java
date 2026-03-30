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
        this.scoreTable.top();

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
        this.pad(25 * uiScale);

        Table tabTable = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();

        boolean isAndroid = Gdx.app.getType() == Application.ApplicationType.Android;
        int buttonsPerRow = isAndroid ? 2 : 4;
        int count = 0;

        for (final String diff : GameConfig.Difficulty.getNames()) {
            TextButton btn = new TextButton(diff, skin, "default");
            btn.getLabel().setFontScale(uiScale * (isAndroid ? 0.90f : 0.80f));

            if (diff.equals(currentDifficulty)) btn.setChecked(true);

            btn.addListener(new ClickListener() {
                @Override
                public void clicked(InputEvent event, float x, float y) {
                    currentDifficulty = diff;
                    refresh();
                }
            });

            tabTable.add(btn).expandX().fillX().height(50 * uiScale).pad(4 * uiScale);
            group.add(btn);

            count++;
            if (count % buttonsPerRow == 0) {
                tabTable.row();
            }
        }

        Label title = new Label("DAILY LEADERBOARD", skin, "default");
        title.setAlignment(Align.center);
        title.setFontScale(uiScale * 1.3f);

        ScrollPane scroll = new ScrollPane(scoreTable, skin);
        scroll.setFadeScrollBars(false);

        TextButton refreshBtn = new TextButton("REFRESH", skin, "default");
        refreshBtn.getLabel().setFontScale(uiScale);
        refreshBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refresh();
            }
        });

        this.add(title).padBottom(20 * uiScale).expandX().fillX().row();
        this.add(tabTable).expandX().fillX().padBottom(15 * uiScale).row();
        this.add(scroll).expand().fill().row();
        this.add(refreshBtn).padTop(20 * uiScale).width(200 * uiScale).height(50 * uiScale).center();
    }

    public void refresh() {
        scoreTable.clear();
        Label loading = new Label("Loading scores...", skin, "default");
        loading.setFontScale(lastAppliedScale);
        scoreTable.add(loading).center().padTop(50 * lastAppliedScale);

        service.fetchHighscores(currentDifficulty, new HighscoreService.HighscoreListener() {
            @Override
            public void onSuccess(Array<HighscoreService.HighscoreEntry> entries) {
                updateTable(entries, lastAppliedScale);
            }

            @Override
            public void onFailure(Throwable t) {
                scoreTable.clear();
                Label error = new Label("Failed to load scores.", skin, "default");
                error.setColor(Color.RED);
                error.setFontScale(lastAppliedScale);
                scoreTable.add(error).center().padTop(50 * lastAppliedScale);
            }
        });
    }

    private void updateTable(Array<HighscoreService.HighscoreEntry> entries, float uiScale) {
        scoreTable.clear();
        // Smaller padding to keep columns tighter together
        scoreTable.defaults().pad(5 * uiScale).padLeft(10 * uiScale).padRight(10 * uiScale);

        // Header - Using expand/fill on all columns makes them share the table width equally
        // This prevents one column from "running away" with the space.
        scoreTable.add(new Label("RANK", skin, "default")).expandX().fillX().left();
        scoreTable.add(new Label("PLAYER", skin, "default")).expandX().fillX().left();
        scoreTable.add(new Label("SCORE", skin, "default")).expandX().fillX().center();
        scoreTable.add(new Label("TIME", skin, "default")).expandX().fillX().right();
        scoreTable.row();

        if (skin.has("white", com.badlogic.gdx.scenes.scene2d.utils.Drawable.class)) {
            scoreTable.add(new Image(skin.newDrawable("white", Color.GRAY)))
                    .colspan(4).fillX().height(1.5f).pad(0).padBottom(8 * uiScale).row();
        }

        if (entries == null || entries.size == 0) {
            Label none = new Label("No scores for today yet!", skin, "default");
            none.setFontScale(uiScale);
            scoreTable.add(none).colspan(4).center().pad(50 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Label rankLabel = new Label(rank + ".", skin, "default");
            rankLabel.setFontScale(uiScale);
            scoreTable.add(rankLabel).expandX().fillX().left();

            Label nameLabel = new Label(entry.name, skin, "default");
            nameLabel.setFontScale(uiScale);
            nameLabel.setEllipsis(true);
            scoreTable.add(nameLabel).expandX().fillX().left();

            Label scoreLabel = new Label(String.valueOf(entry.score), skin, "default");
            scoreLabel.setFontScale(uiScale);
            scoreTable.add(scoreLabel).expandX().fillX().center();

            String displayTime = "N/A";
            if (entry.date != null && entry.date.contains("T")) {
                try {
                    displayTime = entry.date.split("T")[1].substring(0, 5);
                } catch (Exception e) {
                    displayTime = "??:??";
                }
            }
            Label timeLabel = new Label(displayTime, skin, "default");
            timeLabel.setFontScale(uiScale);
            scoreTable.add(timeLabel).expandX().fillX().right();

            scoreTable.row();
            rank++;
        }
    }
}