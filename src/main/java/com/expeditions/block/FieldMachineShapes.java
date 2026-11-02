package com.expeditions.block;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.shapes.*;
/** Selection and collision are voxelized from the very same native model geometry, including tilted boards. */
public final class FieldMachineShapes {
    private static final Map<String,VoxelShape> CACHE=new ConcurrentHashMap<>();
    private FieldMachineShapes() { }
    public static VoxelShape shape(String model,Direction facing){return shape(model,facing,true);}
    public static VoxelShape shape(String model,Direction facing,boolean core){return CACHE.computeIfAbsent(model+"/"+facing+"/"+core,key->build(model,facing,core));}
    private static VoxelShape build(String name,Direction facing,boolean core){
        try(var stream=FieldMachineShapes.class.getResourceAsStream("/assets/expeditions/models/block/"+name+".json")){
            if(stream==null)throw new IOException("Missing model "+name);
            var elements=JsonParser.parseReader(new InputStreamReader(stream,StandardCharsets.UTF_8)).getAsJsonObject().getAsJsonArray("elements");
            if(!core){
                var visible=new JsonArray();
                for(var element:elements){
                    boolean placeholder=element.getAsJsonObject().getAsJsonObject("faces").entrySet().stream()
                            .allMatch(face->face.getValue().getAsJsonObject().get("texture").getAsString().equals("#empty"));
                    if(!placeholder)visible.add(element);
                }
                elements=visible;
            }
            VoxelShape result=Shapes.empty();
            for(int x=0;x<16;x++)for(int z=0;z<16;z++){
                int first=-1;
                for(int y=0;y<=16;y++){
                    boolean solid=y<16 && inside(elements,x+.5,y+.5,z+.5);
                    if(solid && first<0)first=y;
                    if(!solid && first>=0){
                        double x0=x/16.,x1=(x+1)/16.,z0=z/16.,z1=(z+1)/16.;
                        double a=x0,b=x1,c=z0,d=z1;
                        switch(facing){case EAST-> {a=1-z1;b=1-z0;c=x0;d=x1;}case SOUTH->{a=1-x1;b=1-x0;c=1-z1;d=1-z0;}case WEST->{a=z0;b=z1;c=1-x1;d=1-x0;}default->{}}
                        result=Shapes.or(result,Shapes.box(a,first/16.,c,b,y/16.,d));first=-1;
                    }
                }
            }
            return result.optimize();
        }catch(IOException e){throw new IllegalStateException(e);}
    }
    private static boolean inside(JsonArray elements,double x,double y,double z){
        for(var value:elements){var e=value.getAsJsonObject();double a=x,b=y,c=z;
            if(e.has("rotation")){
                var r=e.getAsJsonObject("rotation");var o=r.getAsJsonArray("origin");double ox=o.get(0).getAsDouble(),oy=o.get(1).getAsDouble(),oz=o.get(2).getAsDouble();
                double angle=Math.toRadians(r.get("angle").getAsDouble()),co=Math.cos(angle),si=Math.sin(angle),dx=x-ox,dy=y-oy,dz=z-oz;
                switch(r.get("axis").getAsString()){
                    case "x"->{b=oy+dy*co+dz*si;c=oz-dy*si+dz*co;}
                    case "z"->{a=ox+dx*co+dy*si;b=oy-dx*si+dy*co;}
                    case "y"->{a=ox+dx*co-dz*si;c=oz+dx*si+dz*co;}
                }
            }
            var from=e.getAsJsonArray("from");var to=e.getAsJsonArray("to");
            if(a>=from.get(0).getAsDouble()&&a<=to.get(0).getAsDouble()&&b>=from.get(1).getAsDouble()&&b<=to.get(1).getAsDouble()&&c>=from.get(2).getAsDouble()&&c<=to.get(2).getAsDouble())return true;
        }return false;
    }
}
