package org.example.gameManagers;

import com.badlogic.gdx.graphics.g3d.Environment;
import com.badlogic.gdx.graphics.g3d.ModelBatch;
import com.badlogic.gdx.utils.Disposable;
import org.example.ball.Ball;

import java.util.ArrayList;
import java.util.List;

public class GhostManager implements Disposable {
    private final List<Ball> shotHistory = new ArrayList<>();
    private final int maxGhosts;

    public GhostManager(int maxGhosts) {
        this.maxGhosts = maxGhosts;
    }

    public void archiveBall(Ball ball) {
        shotHistory.add(ball);
        if (shotHistory.size() > maxGhosts) {
            Ball oldest = shotHistory.remove(0);
            oldest.dispose();
        }
    }

    public void render(ModelBatch batch, Environment env) {
        for (Ball ghost : shotHistory) {
            ghost.render(batch, env);
            ghost.renderTrail(batch, env);
        }
    }

    public void clear() {
        for (Ball ghost : shotHistory) {
            ghost.dispose();
        }
        shotHistory.clear();
    }

    @Override
    public void dispose() {
        clear();
    }
}