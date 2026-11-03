package com.expeditions.server;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import net.minecraft.core.BlockPos;

/** Canonical whole-block template shared with the native asset generator. */
public final class RecallTreeLayout {
    public record Piece(BlockPos offset, String kind) { }
    public static final int LEGACY_COUNT = 8;
    public static final List<Piece> PIECES;
    public static final List<BlockPos> OFFSETS;
    public static final List<BlockPos> CLEARANCE;
    public static final List<BlockPos> FLOORS;
    static {
        var pieces=new ArrayList<Piece>();
        for(var p:List.of(new BlockPos(-1,0,0),new BlockPos(1,0,0),new BlockPos(-1,1,0),new BlockPos(1,1,0),
                new BlockPos(-1,2,0),new BlockPos(0,2,0),new BlockPos(1,2,0),new BlockPos(0,1,0)))pieces.add(new Piece(p,"legacy"));
        try(var stream=RecallTreeLayout.class.getResourceAsStream("/assets/expeditions/route/recall_tree.json")) {
            var data=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(stream),StandardCharsets.UTF_8)).getAsJsonObject();
            for(var value:data.getAsJsonArray("cells")) {
                var a=value.getAsJsonArray();pieces.add(new Piece(pos(a),a.get(3).getAsString()));
            }
            var clearance=new ArrayList<BlockPos>();for(var value:data.getAsJsonArray("clearance"))clearance.add(pos(value.getAsJsonArray()));
            var floors=new ArrayList<BlockPos>();for(var value:data.getAsJsonArray("floors"))floors.add(pos(value.getAsJsonArray()));
            PIECES=List.copyOf(pieces);OFFSETS=PIECES.stream().map(Piece::offset).toList();
            CLEARANCE=List.copyOf(clearance);FLOORS=List.copyOf(floors);
        } catch(Exception e) {throw new IllegalStateException("Unable to read Stillwood block template",e);}
    }
    private RecallTreeLayout() { }
    private static BlockPos pos(com.google.gson.JsonArray a) {return new BlockPos(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());}
    public static boolean portal(int part) {return part==7||PIECES.get(part).kind().equals("portal");}
    public static boolean wholeBlock(int part) {return part>=LEGACY_COUNT&&!portal(part);}
    public static String model(int part) {return part<LEGACY_COUNT?"recall_tree_"+part:"stillwood_"+PIECES.get(part).kind();}
}
