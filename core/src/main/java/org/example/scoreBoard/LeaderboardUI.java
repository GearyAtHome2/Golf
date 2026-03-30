package org.example.scoreBoard;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
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
        this.scoreTable = new Table();
        this.scoreTable.top();

        this.setBackground(skin.newDrawable("white", new Color(0, 0, 0, 0.85f)));

        // Initial build with default scale
        rebuild(1.0f);
        refresh();
    }

    public void rebuild(float uiScale) {
        this.lastAppliedScale = uiScale;
        this.clearChildren();
        this.pad(20 * uiScale);

        // 1. Difficulty Tabs
        Table tabTable = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        for (final String diff : GameConfig.Difficulty.getNames()) {
            TextButton btn = new TextButton(diff, skin, "toggle");
            btn.getLabel().setFontScale(uiScale * 0.85f);
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
        }

        Label title = new Label("DAILY LEADERBOARD", skin);
        title.setFontScale(uiScale * 1.2f);

        ScrollPane scroll = new ScrollPane(scoreTable, skin);
        scroll.setFadeScrollBars(false);

        TextButton refreshBtn = new TextButton("REFRESH", skin);
        refreshBtn.getLabel().setFontScale(uiScale);
        refreshBtn.addListener(new ClickListener() {
            @Override
            public void clicked(InputEvent event, float x, float y) {
                refresh();
            }
        });

        this.add(title).padBottom(15 * uiScale).row();
        this.add(tabTable).expandX().fillX().padBottom(10 * uiScale).row();
        this.add(scroll).expand().fill().row();
        this.add(refreshBtn).padTop(15 * uiScale).width(200 * uiScale).height(50 * uiScale);
    }

    public void refresh() {
        scoreTable.clear();
        Label loading = new Label("Loading scores...", skin);
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
                Label error = new Label("Failed to load scores.", skin);
                error.setColor(Color.RED);
                error.setFontScale(lastAppliedScale);
                scoreTable.add(error).center().padTop(50 * lastAppliedScale);
            }
        });
    }

    private void updateTable(Array<HighscoreService.HighscoreEntry> entries, float uiScale) {
        scoreTable.clear();
        scoreTable.defaults().pad(8 * uiScale).left();

        // Fixed column widths that scale with the UI factor
        scoreTable.add(new Label("RANK", skin)).width(55 * uiScale);
        scoreTable.add(new Label("PLAYER", skin)).width(185 * uiScale);
        scoreTable.add(new Label("SCORE", skin)).width(75 * uiScale);
        scoreTable.add(new Label("TIME", skin)).width(105 * uiScale);
        scoreTable.row();

        scoreTable.add(new Image(skin.newDrawable("white", Color.GRAY)))
                .colspan(4).fillX().height(1).pad(0).padBottom(5 * uiScale).row();

        if (entries == null || entries.size == 0) {
            Label none = new Label("No scores for today yet!", skin);
            none.setFontScale(uiScale);
            scoreTable.add(none).colspan(4).center().pad(40 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Label rankLabel = new Label(rank + ".", skin);
            rankLabel.setFontScale(uiScale);
            scoreTable.add(rankLabel);

            Label nameLabel = new Label(entry.name, skin);
            nameLabel.setFontScale(uiScale);
            nameLabel.setEllipsis(true);
            scoreTable.add(nameLabel).width(185 * uiScale);

            Label scoreLabel = new Label(String.valueOf(entry.score), skin);
            scoreLabel.setFontScale(uiScale);
            scoreTable.add(scoreLabel);

            String displayTime = "N/A";
            if (entry.date != null && entry.date.contains("T")) {
                displayTime = entry.date.split("T")[1].substring(0, 5);
            }
            Label timeLabel = new Label(displayTime, skin);
            timeLabel.setFontScale(uiScale);
            scoreTable.add(timeLabel);

            scoreTable.row();
            rank++;
        }
    }
}