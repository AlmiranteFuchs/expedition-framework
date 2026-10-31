package com.expeditions.core;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/** Shared flat coverage for tissue and host bleaching; N/E/S/W then NW/NE/SE/SW bits. */
public final class BloomBoundary {
    public record Region(int x0,int y0,int x1,int y1,float alpha) { }
    private static final ConcurrentHashMap<Integer,List<Region>> CACHE = new ConcurrentHashMap<>();
    private BloomBoundary() { }

    public static List<Region> regions(int neighbors,int variant) {
        int key=(neighbors&255)*4+(variant&3);
        return CACHE.computeIfAbsent(key,k->build(k/4,k%4));
    }

    public static float coverage(int neighbors,int variant,int x,int y) {
        if(x<0 || x>=8 || y<0 || y>=8) throw new IllegalArgumentException("Boundary coordinate outside 8x8 mask");
        int[] distance={y,7-x,7-y,x};
        int[] along={x,y,x,y};
        float alpha=1;
        for(int side=0;side<4;side++) if((neighbors&(1<<side))==0) {
            // Shared edge endpoints never vary: adjacent boundary masks join exactly.
            int fray=along[side]>=2 && along[side]<=5 && (along[side]+variant+side*2)%5==0?1:0;
            alpha=Math.min(alpha,level(distance[side]-fray));
        }
        int[][] sides={{0,3},{0,1},{2,1},{2,3}};
        for(int corner=0;corner<4;corner++) {
            int a=sides[corner][0],b=sides[corner][1];
            boolean connectedA=(neighbors&(1<<a))!=0,connectedB=(neighbors&(1<<b))!=0;
            if(connectedA && connectedB && (neighbors&(16<<corner))==0)
                alpha=Math.min(alpha,level(distance[a]+distance[b]));
            if(!connectedA && !connectedB)
                alpha=Math.min(alpha,level(distance[a]+distance[b]-2));
        }
        return alpha;
    }

    private static float level(int depth) {
        return depth<=0?0:depth==1?.35f:depth==2?.8f:1;
    }

    private static List<Region> build(int mask,int variant) {
        List<Region> result=new ArrayList<>();
        for(int y=0;y<8;y++) {
            int x=0;
            while(x<8) {
                float alpha=coverage(mask,variant,x,y);
                int end=x+1;
                while(end<8 && coverage(mask,variant,end,y)==alpha) end++;
                if(alpha>0) {
                    boolean merged=false;
                    for(int i=0;i<result.size();i++) {
                        Region previous=result.get(i);
                        if(previous.x0==x && previous.x1==end && previous.y1==y && previous.alpha==alpha) {
                            result.set(i,new Region(x,previous.y0,end,y+1,alpha)); merged=true; break;
                        }
                    }
                    if(!merged) result.add(new Region(x,y,end,y+1,alpha));
                }
                x=end;
            }
        }
        return List.copyOf(result);
    }
}
