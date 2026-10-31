package com.expeditions.core;

/** One native 16px material tile per block face, selected by shared world-corner labels. */
public final class BloomSurfacePattern {
    public record Tile(int corners, int variant, boolean resin) {
        public int index() { return corners*2+variant+(resin?32:0); }
        public int corner(int n) { return corners>>>n & 1; }
    }
    private BloomSurfacePattern() { }

    public static Tile at(int u, int v, long plane) {
        int corners=corner(u,v,plane)|corner(u+1,v,plane)<<1
                |corner(u+1,v+1,plane)<<2|corner(u,v+1,plane)<<3;
        long detail=mix(u*0x632be59bd9b4e019L ^ v*0x9e3779b97f4a7c15L ^ plane ^ 0x51ed2705L);
        return new Tile(corners,(int)(detail>>>32 & 1),(detail & 31)==0);
    }

    private static int corner(int u,int v,long plane) {
        return (int)(mix(u*0x632be59bd9b4e019L ^ v*0x9e3779b97f4a7c15L ^ plane)>>>20 & 1);
    }

    private static long mix(long x) {
        x=(x^(x>>>30))*0xbf58476d1ce4e5b9L;
        x=(x^(x>>>27))*0x94d049bb133111ebL;
        return x^(x>>>31);
    }
}
