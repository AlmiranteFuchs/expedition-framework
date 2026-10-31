package com.expeditions.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;

/** Rooted, tapered claws. The surface remains the harvest target; the reaching tips have no collision. */
final class BloomRelief {
    private static final ResourceLocation BARK = ResourceLocation.parse("expeditions:textures/block/assimilation_relief.png");
    private static final ResourceLocation RESIN = ResourceLocation.parse("expeditions:textures/block/assimilation_relief_resin.png");
    private record Point(float u, float v, float h) { }
    private record Quad(Point a, Point b, Point c, Point d,float nu,float nv,float nh) { }
    private record Mesh(List<Quad> bark, List<Quad> fingers, Quad resin) { }
    private static final List<Mesh> MESHES = new ArrayList<>();
    private static final int[][] CENTERS = {{7,9},{8,6},{6,7},{9,9}};
    static {
        for (int mask=0;mask<16;mask++) for (int variant=0;variant<4;variant++) MESHES.add(build(mask,variant));
    }
    private BloomRelief() { }
    private static Mesh build(int mask,int variant) {
        List<Quad> mesh = new ArrayList<>();
        float u=(CENTERS[variant][0]+.5f)/16, v=(CENTERS[variant][1]+.5f)/16;
        // A low rooted palm anchors three knuckled fingers. Connectivity belongs
        // to the surface atlas; these hooks never rotate the shared edge ports.
        prism(mesh,u-.055f,v,u+.055f,v,.085f,.055f);
        List<Quad> fingers = new ArrayList<>();
        finger(fingers,u,v,variant,new float[][] {
            {-.07f,0,.045f,.060f},{-.18f,-.12f,.10f,.047f},{-.16f,-.28f,.18f,.032f},
            {-.07f,-.33f,.23f,.017f},{-.01f,-.26f,.19f,.003f}});
        finger(fingers,u,v,variant,new float[][] {
            {.04f,-.02f,.045f,.062f},{.12f,-.15f,.13f,.045f},{.24f,-.20f,.23f,.027f},
            {.29f,-.12f,.28f,.012f},{.23f,-.07f,.23f,.003f}});
        // Unequal finger counts and lengths break repeated hand-shaped stamps.
        if ((mask+variant)%3!=0) finger(fingers,u,v,variant,new float[][] {
            {.02f,.04f,.04f,.053f},{.08f,.18f,.09f,.038f},{.23f,.24f,.15f,.021f},
            {.28f,.19f,.19f,.007f}});
        float r=.047f,h=.056f;
        var cap=new Quad(new Point(u-r,v+r,h),new Point(u+r,v+r,h),new Point(u+r,v-r,h),new Point(u-r,v-r,h),0,0,1);
        return new Mesh(List.copyOf(mesh),List.copyOf(fingers),cap);
    }

    /** Square bark sections follow a bent centreline, tapering to a hooked point. */
    private static void finger(List<Quad> mesh,float u,float v,int variant,float[][] joints) {
        Point[][] rings = new Point[joints.length][4];
        for(int i=0;i<joints.length;i++) {
            var p=joints[i];
            var prev=joints[Math.max(0,i-1)]; var next=joints[Math.min(joints.length-1,i+1)];
            float dx=next[0]-prev[0],dy=next[1]-prev[1],length=(float)Math.sqrt(dx*dx+dy*dy);
            float sideU=-dy/length*p[3],sideV=dx/length*p[3];
            for(int corner=0;corner<4;corner++) {
                float sign=corner==1||corner==2?1:-1;
                float x=p[0]+sideU*sign,y=p[1]+sideV*sign;
                // Rotate only the free fingers around their palm, never the atlas.
                float ru=switch(variant){case 1->-y;case 2->-x;case 3->y;default->x;};
                float rv=switch(variant){case 1->x;case 2->-y;case 3->-x;default->y;};
                rings[i][corner]=new Point(u+ru,v+rv,p[2]+(corner>=2?1:-1)*p[3]*.65f);
            }
        }
        for(int i=0;i<rings.length-1;i++) for(int side=0;side<4;side++) {
            int next=(side+1)%4;
            surface(mesh,rings[i][side],rings[i+1][side],rings[i+1][next],rings[i][next]);
        }
        var tip=rings[rings.length-1];
        surface(mesh,tip[3],tip[2],tip[1],tip[0]);
    }

    private static void surface(List<Quad> mesh,Point a,Point b,Point c,Point d) {
        float x=b.u-a.u,y=b.v-a.v,z=b.h-a.h;
        float X=c.u-a.u,Y=c.v-a.v,Z=c.h-a.h;
        // The canonical u/v/height frame is left-handed (x/z/y on a floor).
        float nu=z*Y-y*Z,nv=x*Z-z*X,nh=y*X-x*Y;
        float length=(float)Math.sqrt(nu*nu+nv*nv+nh*nh);
        mesh.add(new Quad(a,b,c,d,nu/length,nv/length,nh/length));
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
            int light,int overlay,float grow,float shade,float pulse,float distance,boolean living,
            double viewerX,double viewerY,double viewerZ) {
        float alpha=grow*Math.clamp((24-distance)/4,0,1);
        if(alpha<=0) return;
        var bark=buffers.getBuffer(RenderType.entityTranslucent(BARK));
        float playerDistance=(float)Math.sqrt(viewerX*viewerX+viewerY*viewerY+viewerZ*viewerZ);
        float reach=living?Math.clamp((4-playerDistance)/3,0,1):0;
        float breath=1+.12f*pulse+.4f*reach;
        float towardU=switch(normal){case NORTH->(float)-viewerX;case WEST->(float)-viewerZ;
            case EAST->(float)viewerZ;default->(float)viewerX;};
        float towardV=normal.getAxis()==Direction.Axis.Y?(float)viewerZ:(float)-viewerY;
        float tangent=(float)Math.sqrt(towardU*towardU+towardV*towardV);
        float curlU=towardU/Math.max(1,tangent)*reach*.12f;
        float curlV=towardV/Math.max(1,tangent)*reach*.12f;
        var mesh=MESHES.get(mask*4+variant);
        for(var quad:mesh.bark) emit(bark,pose,normal,quad,grow,breath,shade,alpha,light,overlay,curlU,curlV);
        float fingerAlpha=grow*Math.clamp((14-distance)/4,0,1);
        if(fingerAlpha>0) for(var quad:mesh.fingers)
            emit(bark,pose,normal,quad,grow,breath,shade,fingerAlpha,light,overlay,curlU,curlV);
        // Eyes use additive blending: RGB must fade with alpha or dead knots still glow.
        float heat=alpha*pulse*.65f;
        if(heat>0) emit(buffers.getBuffer(RenderType.eyes(RESIN)),pose,normal,mesh.resin,grow,breath,heat,
                heat,AssimilationLayerRenderer.FULLBRIGHT,overlay,curlU,curlV);
    }
    private static void emit(VertexConsumer out,PoseStack stack,Direction normal,Quad quad,float grow,
            float breath,float shade,float alpha,int light,int overlay,float curlU,float curlV) {
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
            float tip=p.h/.3f;
            u+=curlU*tip*tip; v+=curlV*tip*tip;
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
