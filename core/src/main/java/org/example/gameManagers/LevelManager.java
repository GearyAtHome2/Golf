package org.example.gameManagers;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.VertexAttributes;
import com.badlogic.gdx.graphics.g3d.*;
import com.badlogic.gdx.graphics.g3d.attributes.BlendingAttribute;
import com.badlogic.gdx.graphics.g3d.attributes.ColorAttribute;
import com.badlogic.gdx.graphics.g3d.utils.ModelBuilder;
import com.badlogic.gdx.utils.Disposable;
import org.example.terrain.ITerrainGenerator;
import org.example.terrain.Terrain;
import org.example.terrain.practiceRange.DistanceSign;
import org.example.terrain.practiceRange.PracticeRangeGenerator;
import java.util.ArrayList;
import java.util.List;

public class LevelManager implements Disposable {
    private Terrain terrain;
    private Model markerLineModel;
    private final List<ModelInstance> yardageMarkers = new ArrayList<>();
    private final List<DistanceSign> rangeSigns = new ArrayList<>();

    public void buildLevel(ITerrainGenerator generator, float waterLevel, int distance) {
        disposeCurrentAssets();
        
        terrain = new Terrain(generator, waterLevel, distance);

        if (generator instanceof PracticeRangeGenerator gen) {
            createPracticeAssets(gen);
        }
    }

    private void createPracticeAssets(PracticeRangeGenerator gen) {
        ModelBuilder mb = new ModelBuilder();
        markerLineModel = mb.createBox(70f, 0.05f, 0.3f,
                new Material(ColorAttribute.createDiffuse(new Color(1, 1, 1, 0.6f)), new BlendingAttribute(0.6f)),
                VertexAttributes.Usage.Position | VertexAttributes.Usage.Normal);
        
        for (Float zPos : gen.getMarkerZPositions()) {
            ModelInstance marker = new ModelInstance(markerLineModel);
            marker.transform.setToTranslation(0, 0.02f, zPos);
            yardageMarkers.add(marker);
        }

        for (PracticeRangeGenerator.SignData data : gen.getSignPositions()) {
            rangeSigns.add(new DistanceSign(data.position, data.distance));
        }
    }

    public void render(ModelBatch batch, Environment env) {
        if (terrain != null) terrain.render(batch, env);
        for (ModelInstance marker : yardageMarkers) batch.render(marker, env);
        for (DistanceSign s : rangeSigns) s.render(batch, env);
    }

    public void disposeCurrentAssets() {
        if (terrain != null) { terrain.dispose(); terrain = null; }
        if (markerLineModel != null) { markerLineModel.dispose(); markerLineModel = null; }
        for (DistanceSign s : rangeSigns) s.dispose();
        
        yardageMarkers.clear();
        rangeSigns.clear();
    }

    @Override
    public void dispose() {
        disposeCurrentAssets();
    }

    // Getters
    public Terrain getTerrain() { return terrain; }
}