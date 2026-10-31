package com.expeditions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Small cached bark ribs and a resin knot, entirely within the layer's thin profile. */
final class BloomRelief {
    private static final ResourceLocation BARK = ResourceLocation.parse("expeditions:textures/block/assimilation_relief.png");
    private static final ResourceLocation RESIN = ResourceLocation.parse("expeditions:textures/block/assimilation_relief_resin.png");
    private record Point(float u, float v, float h) { }
    private record Quad(Point a, Point b, Point c, Point d,float nu,float nv,float nh) { }
    private record Mesh(List<Quad> bark,Quad resin) { }
    private static final List<Mesh> MESHES = new ArrayList<>();
    private static final int[][] CENTERS = {{7,8},{8,7},{6,7},{8,9}};
    static {
        for (int mask=0;mask<16;mask++) for (int variant=0;variant<4;variant++) MESHES.add(build(mask,variant));
    }
    private BloomRelief() { }
    private static Mesh build(int mask,int variant) {
        List<Quad> mesh = new ArrayList<>();
        float u=(CENTERS[variant][0]+.5f)/16, v=(CENTERS[variant][1]+.5f)/16;
        float[][] ends={{6+variant%2,5},{10,6+variant%2},{9-variant%2,11},{4,9-variant%2}};
        int ribs=0;
        for(int i=0;i<4 && ribs<2;i++) if((mask&(1<<i))!=0) {
            prism(mesh,u,v,(ends[i][0]+.5f)/16,(ends[i][1]+.5f)/16,.022f,.022f);
            ribs++;
        }
        // An offset secondary bud gives isolated patches a real silhouette too.
        if(mask==0) prism(mesh,u,v,u-.1f,v+.09f,.018f,.031f);
        prism(mesh,u-.035f,v,u+.035f,v,.052f,.043f);
        float r=.047f,h=.044f;
        var cap=new Quad(new Point(u-r,v+r,h),new Point(u+r,v+r,h),new Point(u+r,v-r,h),new Point(u-r,v-r,h),0,0,1);
        return new Mesh(List.copyOf(mesh),cap);
    }
    private static void prism(List<Quad> mesh,float u,float v,float endU,float endV,float radius,float height) {
        float dx=endU-u,dy=endV-v, length=(float)Math.sqrt(dx*dx+dy*dy);
        float du=-dy/length*radius,dv=dx/length*radius;
        Point a=new Point(u+du,v+dv,.007f), b=new Point(endU+du,endV+dv,.007f);
        Point c=new Point(endU-du,endV-dv,.007f), d=new Point(u-du,v-dv,.007f);
        Point A=new Point(a.u,a.v,height),B=new Point(b.u,b.v,height),C=new Point(c.u,c.v,height),D=new Point(d.u,d.v,height);
        mesh.add(new Quad(A,B,C,D,0,0,1)); mesh.add(new Quad(a,b,B,A,du/radius,dv/radius,0));
        mesh.add(new Quad(b,c,C,B,dx/length,dy/length,0));
        mesh.add(new Quad(c,d,D,C,-du/radius,-dv/radius,0)); mesh.add(new Quad(d,a,A,D,-dx/length,-dy/length,0));
    }
    static void render(PoseStack pose,MultiBufferSource buffers,Direction normal,int mask,int variant,
            int light,int overlay,float grow,float shade,float pulse,float distance) {
        float alpha=grow*Math.clamp((24-distance)/4,0,1);
        if(alpha<=0) return;
        var bark=buffers.getBuffer(RenderType.entityTranslucent(BARK));
        float breath=1+.10f*pulse;
        var mesh=MESHES.get(mask*4+variant);
        for(var quad:mesh.bark) emit(bark,pose,normal,quad,grow,breath,shade,alpha,light,overlay);
        // Eyes use additive blending: RGB must fade with alpha or dead knots still glow.
        float heat=alpha*pulse*.65f;
        if(heat>0) emit(buffers.getBuffer(RenderType.eyes(RESIN)),pose,normal,mesh.resin,grow,breath,heat,
                heat,AssimilationLayerRenderer.FULLBRIGHT,overlay);
    }
    private static void emit(VertexConsumer out,PoseStack stack,Direction normal,Quad quad,float grow,
            float breath,float shade,float alpha,int light,int overlay) {
        boolean reverse=normal==Direction.DOWN || normal==Direction.WEST || normal==Direction.EAST;
        float nx,ny,nz;
        switch(normal) {
            case UP -> {nx=quad.nu;ny=quad.nh;nz=quad.nv;}
            case DOWN -> {nx=quad.nu;ny=-quad.nh;nz=quad.nv;}
            case NORTH -> {nx=-quad.nu;ny=-quad.nv;nz=-quad.nh;}
            case SOUTH -> {nx=quad.nu;ny=-quad.nv;nz=quad.nh;}
            case WEST -> {nx=-quad.nh;ny=-quad.nv;nz=-quad.nu;}
            default -> {nx=quad.nh;ny=-quad.nv;nz=quad.nu;}
        }
        for(int i=0;i<4;i++) {
            int index=reverse?(4-i)%4:i;
            Point p=switch(index){case 0->quad.a;case 1->quad.b;case 2->quad.c;default->quad.d;};
            float u=.5f+(p.u-.5f)*grow,v=.5f+(p.v-.5f)*grow,h=p.h*grow*breath;
            float x,y,z;
            switch(normal) {
                case UP -> {x=u;y=h;z=v;} case DOWN -> {x=u;y=1-h;z=v;}
                case NORTH -> {x=1-u;y=1-v;z=1-h;} case SOUTH -> {x=u;y=1-v;z=h;}
                case WEST -> {x=1-h;y=1-v;z=1-u;} default -> {x=h;y=1-v;z=u;}
            }
            out.addVertex(stack.last(),x,y,z).setColor(shade,shade,shade,alpha)
                    .setUv(index==1||index==2?1:0,index>=2?1:0).setOverlay(overlay).setLight(light)
                    .setNormal(stack.last(),nx,ny,nz);
        }
    }
}
