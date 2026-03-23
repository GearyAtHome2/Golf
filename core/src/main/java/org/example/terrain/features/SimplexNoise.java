package org.example.terrain.features;

import java.util.Random;

/**
 * A lightweight 2D Simplex Noise implementation for terrain ruggedness.
 * Updated to use float for direct compatibility with terrain generation.
 */
public class SimplexNoise {
    private final int[] p = new int[512];
    private final int[] permutation = new int[256];

    // Skewing and unskewing factors
    private static final float F2 = 0.5f * ((float)Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float)Math.sqrt(3.0)) / 6.0f;

    // Gradient vectors for 2D
    private static final int[][] grad2 = {
            {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
            {1, 0}, {-1, 0}, {1, 0}, {-1, 0},
            {0, 1}, {0, -1}, {0, 1}, {0, -1}
    };

    public SimplexNoise(long seed) {
        Random rand = new Random(seed);
        for (int i = 0; i < 256; i++) {
            permutation[i] = i;
        }
        for (int i = 0; i < 256; i++) {
            int j = rand.nextInt(256);
            int temp = permutation[i];
            permutation[i] = permutation[j];
            permutation[j] = temp;
        }
        for (int i = 0; i < 512; i++) {
            p[i] = permutation[i & 255];
        }
    }

    public float noise(float x, float y) {
        float n0, n1, n2;

        // Skew the input space
        float s = (x + y) * F2;
        int i = (int) Math.floor(x + s);
        int j = (int) Math.floor(y + s);

        float t = (i + j) * G2;
        float X0 = i - t;
        float Y0 = j - t;
        float x0 = x - X0;
        float y0 = y - Y0;

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1.0f + 2.0f * G2;
        float y2 = y0 - 1.0f + 2.0f * G2;

        int ii = i & 255;
        int jj = j & 255;
        int gi0 = p[ii + p[jj]] % 12;
        int gi1 = p[ii + i1 + p[jj + j1]] % 12;
        int gi2 = p[ii + 1 + p[jj + 1]] % 12;

        float t0 = 0.5f - x0 * x0 - y0 * y0;
        if (t0 < 0) n0 = 0.0f;
        else {
            t0 *= t0;
            n0 = t0 * t0 * dot(grad2[gi0], x0, y0);
        }

        float t1 = 0.5f - x1 * x1 - y1 * y1;
        if (t1 < 0) n1 = 0.0f;
        else {
            t1 *= t1;
            n1 = t1 * t1 * dot(grad2[gi1], x1, y1);
        }

        float t2 = 0.5f - x2 * x2 - y2 * y2;
        if (t2 < 0) n2 = 0.0f;
        else {
            t2 *= t2;
            n2 = t2 * t2 * dot(grad2[gi2], x2, y2);
        }

        return 70.0f * (n0 + n1 + n2);
    }

    private static float dot(int[] g, float x, float y) {
        return g[0] * x + g[1] * y;
    }
}