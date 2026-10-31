package com.expeditions.core;

import java.util.ArrayList;
import java.util.List;

/** World-anchored, overlapping art patches. Coordinates are block-face UVs, including negatives. */
public final class BloomSurfacePattern {
    public static final int SIZE = 8;
    private static final int SPACING = 6;
    public record Patch(int u, int v, int variant, float strength) {
        public boolean covers(int x, int y) {
            return x >= u && x < u + SIZE && y >= v && y < v + SIZE;
        }
    }
    private BloomSurfacePattern() { }

    public static List<Patch> overlapping(int u, int v, long plane) {
        List<Patch> result = new ArrayList<>(4);
        int x = Math.floorDiv(u, SPACING), y = Math.floorDiv(v, SPACING);
        for (int dy=-1; dy<=1; dy++) for (int dx=-1; dx<=1; dx++) {
            Patch patch = at(x+dx,y+dy,plane);
            if (patch.covers(u,v)) result.add(patch);
        }
        return result;
    }

    public static Patch at(int cellU, int cellV, long plane) {
        long hash = mix(cellU*0x632be59bd9b4e019L ^ cellV*0x9e3779b97f4a7c15L ^ plane);
        int du=(int)Math.floorMod(hash,3)-1, dv=(int)Math.floorMod(hash>>>9,3)-1;
        int variant=(int)(hash>>>20 & 7);
        return new Patch(cellU*SPACING+du-4,cellV*SPACING+dv-4,variant,
                .66f+(hash>>>32 & 255)/255f*.34f);
    }

    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
}
