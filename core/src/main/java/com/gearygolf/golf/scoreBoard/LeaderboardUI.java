package com.gearygolf.golf.scoreBoard;

import com.gearygolf.golf.Platform;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.*;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.badlogic.gdx.utils.Align;
import com.badlogic.gdx.utils.Array;
import com.gearygolf.golf.GameConfig;
import com.gearygolf.golf.hud.SparkleButton;
import com.gearygolf.golf.hud.UIUtils;
import com.gearygolf.golf.session.GameSession;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class LeaderboardUI extends Table {
    private final HighscoreService service;
    private final Skin skin;
    private final BitmapFont font;
    private final Table scoreTable;
    private String currentDifficulty = GameConfig.Difficulty.NOVICE.name();
    private CourseType currentCourseType = CourseType.HOLES_1_PAR3;
    private float lastAppliedScale = 1.0f;
    private Stage boundStage;
    private ScorecardPopup currentPopup;

    // Injected after construction — used for 1-hole par tab gating
    private DailySubmissionCache dailyCache;
    // True only after notifyCacheReady() is explicitly called, regardless of dailyCache.isFetched()
    private boolean cacheReadyNotified = false;
    // Called when user presses "PLAY DAILY PAR X" in a gated par tab
    private Consumer<GameSession.GameMode> onPlayDaily;

    // Count caches — absent key means "not yet loaded"
    private final Map<CourseType, Integer> courseTypeCounts = new EnumMap<>(CourseType.class);
    private final Map<String, Integer> difficultyCounts = new HashMap<>();

    // Live label references updated in-place when counts arrive
    private final Map<CourseType, Label> courseTabLabels = new EnumMap<>(CourseType.class);
    private final Map<String, Label> diffTabLabels = new HashMap<>();
    // Separate reference for the outer "1-Hole" aggregate tab (HOLES_1_PAR3 key is reused by the inner par tab)
    private Label oneHoleOuterLabel;

    // Incremented on each refresh to drop stale async callbacks
    private int gen = 0;

    public LeaderboardUI(Skin skin, BitmapFont font, HighscoreService service) {
        this.skin = skin;
        this.font = font;
        this.service = service;
        ensureStylesExist();
        this.scoreTable = new Table();
        this.right();
        this.scoreTable.top().left();
        this.setBackground(UIUtils.createGoldBorderedPanel(new Color(0, 0, 0, 0.88f), 4));
        rebuild(1.0f);
        refresh();
    }

    /** Must be called after adding to a stage so scorecard popups have a stage to attach to. */
    public void bindStage(Stage stage) {
        this.boundStage = stage;
    }

    /** Injects the submission cache so 1-hole par tabs can gate their content. */
    public void setDailySubmissionCache(DailySubmissionCache cache) {
        this.dailyCache = cache;
    }

    /**
     * Sets the callback invoked when the user presses a "PLAY DAILY PAR X" button
     * in a gated 1-hole par tab. The consumer receives the game mode to launch.
     */
    public void setPlayDailyCallback(Consumer<GameSession.GameMode> callback) {
        this.onPlayDaily = callback;
    }

    /**
     * Called externally when the DailySubmissionCache finishes its async fetch.
     * Re-evaluates the current tab — if it was showing "Loading..." for a gated
     * par tab, it will now either show the play button or fire the leaderboard query.
     */
    public void notifyCacheReady() {
        cacheReadyNotified = true;
        if (currentCourseType.isOneHole()) {
            gen++;
            final int g = gen;
            rebuild(lastAppliedScale); // re-evaluates sparkle state on par tabs
            fireDataQuery(g);
        }
    }

    private void ensureStylesExist() {
        if (!skin.has("default", Label.LabelStyle.class)) {
            Label.LabelStyle labelStyle = new Label.LabelStyle();
            labelStyle.font = font;
            labelStyle.fontColor = Color.WHITE;
            skin.add("default", labelStyle);
        }
        if (!skin.has("default", ScrollPane.ScrollPaneStyle.class)) {
            skin.add("default", new ScrollPane.ScrollPaneStyle());
        }
    }

    public void rebuild(float uiScale) {
        this.lastAppliedScale = uiScale;
        this.clearChildren();
        courseTabLabels.clear();
        diffTabLabels.clear();

        float padding = 20 * uiScale;
        this.pad(padding);
        this.right();

        Label title = new Label("DAILY LEADERBOARD", skin, "default");
        title.setAlignment(Align.center);
        title.setFontScale(uiScale * 1.0f);

        Table courseTabTable = buildCourseTypeTabs(uiScale);
        Table parTabTable = currentCourseType.isOneHole() ? buildParTabs(uiScale) : null;
        Table diffTabTable = !currentCourseType.isOneHole() ? buildDifficultyTabs(uiScale) : null;

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
        if (parTabTable != null) {
            this.add(parTabTable).expandX().fillX().padBottom(10 * uiScale).row();
        }
        if (diffTabTable != null) {
            this.add(diffTabTable).expandX().fillX().padBottom(10 * uiScale).row();
        }
        this.add(scroll).expand().fill().row();
        this.add(refreshBtn).width(140 * uiScale).height(40 * uiScale).right().padTop(10 * uiScale);
    }

    private Table buildCourseTypeTabs(float uiScale) {
        Table courseTabTable = new Table();
        ButtonGroup<TextButton> courseGroup = new ButtonGroup<>();
        boolean isAndroid = Platform.isAndroid();
        float btnScale = uiScale * (isAndroid ? 0.75f : 1.0f);
        float btnW = (this.getWidth() - 40 * uiScale) / 3f * 0.88f;

        // F-021: 1-Hole first, 18-Hole last
        // "1-Hole" maps to whichever par variant is currently active (or defaults to PAR3)
        CourseType[] outerTypes = {CourseType.HOLES_1_PAR3, CourseType.HOLES_9, CourseType.HOLES_18};
        String[] baseLabels = {"1-Hole", "9-Hole", "18-Hole"};

        for (int i = 0; i < outerTypes.length; i++) {
            final CourseType ct = outerTypes[i];
            final boolean isOneHoleTab = (i == 0);
            // For the outer 1-hole tab, count across all three par types combined
            Integer count = isOneHoleTab
                ? sumOneHoleCounts()
                : courseTypeCounts.get(ct);
            String text = baseLabels[i] + " " + countSuffix(count);
            TextButton btn = new TextButton(text, skin, "default");
            btn.getLabel().setFontScale(fitBtnScale(text, btnScale, btnW));
            if (isOneHoleTab) {
                oneHoleOuterLabel = btn.getLabel();
            } else {
                courseTabLabels.put(ct, btn.getLabel());
            }
            boolean isCurrentTab = isOneHoleTab ? currentCourseType.isOneHole() : ct == currentCourseType;
            if (isCurrentTab) {
                btn.setChecked(true);
                btn.setColor(Color.GRAY);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    CourseType target = isOneHoleTab ? CourseType.HOLES_1_PAR3 : ct;
                    boolean alreadyActive = isOneHoleTab ? currentCourseType.isOneHole() : currentCourseType == ct;
                    if (alreadyActive) return;
                    currentCourseType = target;
                    difficultyCounts.clear();
                    gen++;
                    final int g = gen;
                    rebuild(lastAppliedScale);
                    if (!isOneHoleTab) {
                        fireDiffCounts(g);
                    }
                    fireDataQuery(g);
                }
            });
            courseTabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            courseGroup.add(btn);
        }
        return courseTabTable;
    }

    /** Builds the Par 3 / Par 4 / Par 5 inner tabs shown when the 1-Hole outer tab is active. */
    private Table buildParTabs(float uiScale) {
        Table parTabTable = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        boolean isAndroid = Platform.isAndroid();
        float btnScale = uiScale * (isAndroid ? 0.75f : 1.0f);
        float btnW = (this.getWidth() - 40 * uiScale) / 3f * 0.88f;

        CourseType[] parTypes = {CourseType.HOLES_1_PAR3, CourseType.HOLES_1_PAR4, CourseType.HOLES_1_PAR5};
        String[] labels = {"Par 3", "Par 4", "Par 5"};

        TextButton.TextButtonStyle btnStyle = skin.get("default", TextButton.TextButtonStyle.class);
        for (int i = 0; i < parTypes.length; i++) {
            final CourseType pt = parTypes[i];
            String text = labels[i] + " " + countSuffix(courseTypeCounts.get(pt));
            boolean isCurrentTab = (pt == currentCourseType);
            // Sparkle until the cache positively confirms the user submitted today.
            // If cache isn't fetched yet we assume unplayed (stops when notifyCacheReady rebuilds).
            boolean unplayed = !isCurrentTab
                && (dailyCache == null || !dailyCache.isFetched() || !dailyCache.hasSubmitted(pt));
            SparkleButton btn = new SparkleButton(text, btnStyle);
            btn.getLabel().setFontScale(fitBtnScale(text, btnScale, btnW));
            btn.setSparkleEnabled(unplayed);
            courseTabLabels.put(pt, btn.getLabel());
            if (isCurrentTab) {
                btn.setChecked(true);
                btn.setColor(Color.GRAY);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (pt == currentCourseType) return;
                    currentCourseType = pt;
                    gen++;
                    final int g = gen;
                    rebuild(lastAppliedScale);
                    fireDataQuery(g);
                }
            });
            parTabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            group.add(btn);
        }
        return parTabTable;
    }

    private Table buildDifficultyTabs(float uiScale) {
        Table tabTable = new Table();
        ButtonGroup<TextButton> group = new ButtonGroup<>();
        boolean isAndroid = Platform.isAndroid();
        float btnScale = uiScale * (isAndroid ? 0.65f : 1.0f);
        int cols = isAndroid ? 2 : 3;
        float btnW = (this.getWidth() - 40 * uiScale) / cols * 0.88f;
        int currentCell = 0;

        for (final String diff : GameConfig.Difficulty.getNames()) {
            String text = diff + " " + countSuffix(difficultyCounts.get(diff));
            TextButton btn = new TextButton(text, skin, "default");
            btn.getLabel().setFontScale(fitBtnScale(diff, btnScale, btnW));
            diffTabLabels.put(diff, btn.getLabel());
            if (diff.equals(currentDifficulty)) {
                btn.setChecked(true);
                btn.setColor(Color.GRAY);
            }
            btn.addListener(new ClickListener() {
                @Override public void clicked(InputEvent event, float x, float y) {
                    if (diff.equals(currentDifficulty)) return;
                    currentDifficulty = diff;
                    gen++;
                    final int g = gen;
                    rebuild(lastAppliedScale);
                    fireDataQuery(g);
                }
            });
            tabTable.add(btn).expandX().fillX().height(40 * uiScale).pad(2 * uiScale);
            group.add(btn);
            currentCell++;
            if (currentCell % cols == 0) tabTable.row();
        }
        return tabTable;
    }

    /**
     * Full refresh — clears all cached counts and re-fetches everything.
     * Called on first load and by the Refresh button.
     */
    public void refresh() {
        courseTypeCounts.clear();
        difficultyCounts.clear();
        gen++;
        final int g = gen;
        rebuild(lastAppliedScale);
        fireCourseTypeCounts(g);
        fireDiffCounts(g);
        fireDataQuery(g);
    }

    // -------------------------------------------------------------------------
    // Query dispatch
    // -------------------------------------------------------------------------

    /** Fires count queries for all CourseTypes. Updates tab labels in place. */
    private void fireCourseTypeCounts(int g) {
        for (CourseType type : CourseType.values()) {
            final CourseType ct = type;
            service.fetchCount(null, ct, new HighscoreService.CountListener() {
                @Override public void onSuccess(int count) {
                    if (gen != g) return;
                    courseTypeCounts.put(ct, count);
                    Label lbl = courseTabLabels.get(ct);
                    if (lbl != null) lbl.setText(courseTypeBaseLabel(ct) + " " + countSuffix(count));
                    if (ct.isOneHole() && oneHoleOuterLabel != null) {
                        oneHoleOuterLabel.setText("1-Hole " + countSuffix(sumOneHoleCounts()));
                    }
                }
                @Override public void onFailure(Throwable err) {
                    if (gen != g) return;
                    // Treat failure as 0 so sumOneHoleCounts() can resolve without staying at (...)
                    if (ct.isOneHole()) courseTypeCounts.put(ct, 0);
                    Label lbl = courseTabLabels.get(ct);
                    if (lbl != null) lbl.setText(courseTypeBaseLabel(ct));
                    if (ct.isOneHole() && oneHoleOuterLabel != null) {
                        oneHoleOuterLabel.setText("1-Hole " + countSuffix(sumOneHoleCounts()));
                    }
                }
            });
        }
    }

    /**
     * Fires one query (all difficulties, today, limit 55) for the current CourseType,
     * then counts client-side per difficulty to update the difficulty tab labels.
     * No-op for 1-hole par types (which have no difficulty tabs).
     */
    private void fireDiffCounts(int g) {
        if (currentCourseType.isOneHole()) return;
        service.fetchHighscoresAllDifficulties(currentCourseType, new HighscoreService.HighscoreListener() {
            @Override public void onSuccess(Array<HighscoreService.HighscoreEntry> entries) {
                if (gen != g) return;
                Map<String, Integer> counts = new HashMap<>();
                for (HighscoreService.HighscoreEntry e : entries) {
                    if (e.difficulty != null) {
                        Integer prev = counts.get(e.difficulty);
                        counts.put(e.difficulty, prev != null ? prev + 1 : 1);
                    }
                }
                for (String diff : GameConfig.Difficulty.getNames()) {
                    Integer count = counts.get(diff);
                    int n = count != null ? count : 0;
                    difficultyCounts.put(diff, n);
                    Label lbl = diffTabLabels.get(diff);
                    if (lbl != null) lbl.setText(diff + " " + countSuffix(n));
                }
            }
            @Override public void onFailure(Throwable err) {
                if (gen != g) return;
                for (String diff : GameConfig.Difficulty.getNames()) {
                    Label lbl = diffTabLabels.get(diff);
                    if (lbl != null) lbl.setText(diff);
                }
            }
        });
    }

    /**
     * For 1-hole par tabs, checks the submission gate before firing the query:
     * - Cache not fetched yet  → show "Loading..." (already shown above)
     * - Cache fetched, not submitted → show "PLAY DAILY PAR X" button
     * - Cache fetched, submitted     → fire the leaderboard query as normal
     *
     * For 9/18-hole tabs, always fires the query.
     */
    private void fireDataQuery(int g) {
        scoreTable.clear();
        Label loading = new Label("Loading...", skin, "default");
        loading.setFontScale(lastAppliedScale * 0.7f);
        scoreTable.add(loading).center().padTop(40 * lastAppliedScale);

        if (currentCourseType.isOneHole()) {
            // Not enough information yet — leave "Loading..." until notifyCacheReady() is called
            if (!cacheReadyNotified) return;

            // Gate: user hasn't played this par variant today
            if (!dailyCache.hasSubmitted(currentCourseType)) {
                showPlayDailyButton(g);
                return;
            }
        }

        service.fetchHighscores(currentDifficulty, currentCourseType, new HighscoreService.HighscoreListener() {
            @Override public void onSuccess(Array<HighscoreService.HighscoreEntry> entries) {
                if (gen != g) return;
                updateTable(entries, lastAppliedScale);
            }
            @Override public void onFailure(Throwable t) {
                if (gen != g) return;
                scoreTable.clear();
                Label error = new Label("Offline", skin, "default");
                error.setColor(Color.RED);
                error.setFontScale(lastAppliedScale * 0.7f);
                scoreTable.add(error).center().padTop(40 * lastAppliedScale);
            }
        });
    }

    /** Replaces scoreTable content with a large sparkle-animated play button. */
    private void showPlayDailyButton(int g) {
        if (gen != g) return;
        scoreTable.clear();

        int parNum = currentCourseType == CourseType.HOLES_1_PAR4 ? 4
                   : currentCourseType == CourseType.HOLES_1_PAR5 ? 5 : 3;
        String btnText = "PLAY DAILY PAR " + parNum;

        GameSession.GameMode mode = currentCourseType == CourseType.HOLES_1_PAR4 ? GameSession.GameMode.DAILY_PAR4
                                  : currentCourseType == CourseType.HOLES_1_PAR5 ? GameSession.GameMode.DAILY_PAR5
                                  : GameSession.GameMode.DAILY_PAR3;

        SparkleButton playBtn = new SparkleButton(btnText, skin.get("default", TextButton.TextButtonStyle.class));
        playBtn.getLabel().setFontScale(lastAppliedScale * 1.6f);
        playBtn.setSparkleEnabled(true);
        playBtn.addListener(new ClickListener() {
            @Override public void clicked(InputEvent event, float x, float y) {
                if (onPlayDaily != null) onPlayDaily.accept(mode);
            }
        });

        scoreTable.add(playBtn).expand().fill().pad(20 * lastAppliedScale);
    }

    // -------------------------------------------------------------------------
    // Table population
    // -------------------------------------------------------------------------

    private void updateTable(Array<HighscoreService.HighscoreEntry> entries, float uiScale) {
        scoreTable.clear();
        scoreTable.top().left().padLeft(10 * uiScale);

        float tablePadding = scoreTable.getPadLeft() + scoreTable.getPadRight();
        float totalWidth = (this.getWidth() - (this.getPadLeft() + this.getPadRight())) - tablePadding;
        boolean isAndroid = Platform.isAndroid();
        float textScale = uiScale * (isAndroid ? 0.70f : 0.88f);

        if (currentCourseType.isOneHole()) {
            if (entries != null) {
                entries.sort((a, b) -> {
                    if (a.score != b.score) return Integer.compare(a.score, b.score);
                    return Float.compare(a.elapsedTime, b.elapsedTime);
                });
                while (entries.size > 10) entries.removeIndex(entries.size - 1);
            }
            updateTableOneHole(entries, uiScale, totalWidth, textScale);
        } else {
            updateTableStandard(entries, uiScale, totalWidth, textScale);
        }
    }

    // -------------------------------------------------------------------------
    // Standard (9/18-hole) table
    // -------------------------------------------------------------------------

    private void updateTableStandard(Array<HighscoreService.HighscoreEntry> entries, float uiScale, float totalWidth, float textScale) {
        float rkW     = totalWidth * 0.20f;
        float playerW = totalWidth * 0.39f;
        float scoreW  = totalWidth * 0.23f;
        float timeW   = totalWidth * 0.18f;

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
        if (hoverBg instanceof BaseDrawable) {
            ((BaseDrawable) hoverBg).setMinWidth(0);
            ((BaseDrawable) hoverBg).setMinHeight(0);
        }

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
        float rkW        = totalWidth * 0.10f;
        float playerW    = totalWidth * 0.23f;
        float diffW      = totalWidth * 0.10f;
        float strokesW   = totalWidth * 0.17f;
        float timeW      = totalWidth * 0.20f;
        float submittedW = totalWidth * 0.20f;

        boolean isAndroid = Platform.isAndroid();
        float scale       = textScale * 0.90f * (isAndroid ? 0.8f : 1.0f);

        Table hdr = buildRowTable6(rkW, playerW, diffW, strokesW, timeW, submittedW,
            makeLabel("RANK",      scale, Color.LIGHT_GRAY),
            makeLabel("PLAYER",    scale, Color.LIGHT_GRAY),
            makeLabel("DIFF",      scale, Color.LIGHT_GRAY),
            makeLabel("STROKES",   scale, Color.LIGHT_GRAY),
            makeLabel("TIME",      scale, Color.LIGHT_GRAY),
            makeLabel("SUBMITTED", scale, Color.LIGHT_GRAY));
        scoreTable.add(hdr).expandX().fillX().row();
        addDivider(1, uiScale);

        if (entries == null || entries.size == 0) {
            scoreTable.add(new Label("No scores yet", skin, "default")).center().padTop(30 * uiScale);
            return;
        }

        int rank = 1;
        for (HighscoreService.HighscoreEntry entry : entries) {
            Color rc = rankColor(rank);
            Label nL = makeLabel(entry.name, scale, rc);
            nL.setEllipsis("...");
            String diffLetter = (entry.difficulty != null && !entry.difficulty.isEmpty())
                    ? String.valueOf(entry.difficulty.charAt(0)) : "?";

            Table row = buildRowTable6(rkW, playerW, diffW, strokesW, timeW, submittedW,
                makeLabel(rank + ".",                            scale, rc),
                nL,
                makeLabel(diffLetter,                            scale, rc),
                makeLabel(String.valueOf(entry.score),           scale, rc),
                makeLabel(formatElapsedTime(entry.elapsedTime),  scale, rc),
                makeLabel(formatSubmittedTime(entry.date),       scale, rc));

            scoreTable.add(row).expandX().fillX().padBottom(4 * uiScale).row();
            rank++;
        }
    }

    private Table buildRowTable6(float rkW, float playerW, float diffW, float strokesW, float timeW, float submittedW,
                                 Label rankLbl, Label nameLbl, Label diffLbl, Label strokesLbl, Label timeLbl, Label submittedLbl) {
        Table row = new Table();
        row.add(rankLbl).width(rkW).left();
        row.add(nameLbl).width(playerW).left();
        row.add(diffLbl).width(diffW).center();
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

    private String courseTypeBaseLabel(CourseType type) {
        return switch (type) {
            case HOLES_1_PAR3 -> "Par 3";
            case HOLES_1_PAR4 -> "Par 4";
            case HOLES_1_PAR5 -> "Par 5";
            case HOLES_9      -> "9-Hole";
            default           -> "18-Hole";
        };
    }

    /** Sums counts across all three 1-hole par types for the outer "1-Hole" tab label. */
    private Integer sumOneHoleCounts() {
        Integer p3 = courseTypeCounts.get(CourseType.HOLES_1_PAR3);
        Integer p4 = courseTypeCounts.get(CourseType.HOLES_1_PAR4);
        Integer p5 = courseTypeCounts.get(CourseType.HOLES_1_PAR5);
        if (p3 == null && p4 == null && p5 == null) return null; // none loaded yet
        return (p3 != null ? p3 : 0) + (p4 != null ? p4 : 0) + (p5 != null ? p5 : 0);
    }

    private String countSuffix(Integer count) {
        if (count == null) return "(...)";
        if (count >= 10)   return "(10+)";
        return "(" + count + ")";
    }

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
        GlyphLayout gl = new GlyphLayout();
        gl.setText(font, text);
        float textW = gl.width * desired;
        return textW > maxWidth ? desired * (maxWidth / textW) : desired;
    }
}
