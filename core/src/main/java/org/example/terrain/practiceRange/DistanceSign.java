package org.example.terrain.practiceRange;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.MeshPartBuilder;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.math.Vector3;

public class DistanceSign {
    private final Model model;
    private final ModelInstance instance;

    public DistanceSign(Vector3 pos, int distance) {
        ModelBuilder mb = new ModelBuilder();

        // Color coding by distance
        Color color = switch (distance) {
            case 50 -> Color.WHITE;
            case 100 -> Color.RED;
            case 150 -> Color.BLUE;
            case 200 -> Color.YELLOW;
            case 250 -> Color.GREEN;
            default -> Color.ORANGE;
        };

        // Combine a tiny post and a flat board into one model
        mb.begin();
        // The Post
        MeshPartBuilder post = mb.part("post", 1, 3, new Material(ColorAttribute.createDiffuse(Color.DARK_GRAY)));
        post.box(0.1f, 1.5f, 0.1f);
        // The Sign Board
        MeshPartBuilder board = mb.part("board", 1, 3, new Material(ColorAttribute.createDiffuse(color)));
        board.box(1.5f, 1.0f, 0.1f);

        model = mb.end();
        instance = new ModelInstance(model);

        // Offset the board so it sits on top of the post
        instance.transform.setToTranslation(pos.x, 0.75f, pos.z);
    }

    public void render(ModelBatch batch, Environment env) {
        batch.render(instance, env);
    }

    public void dispose() {
        model.dispose();
    }
}