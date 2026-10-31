package com.expeditions.gametest;

import com.expeditions.block.AssimilationConnections;
import com.expeditions.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.MultifaceBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

@GameTestHolder("expeditions_mic")
@PrefixGameTestTemplate(false)
public final class BloomSurfaceTests {
    private BloomSurfaceTests() { }
    private static net.minecraft.world.level.block.state.BlockState face(Direction d) {
        return ModBlocks.ASSIMILATION_LAYER.get().defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true);
    }
    @GameTest(template="arena", batch="bloom_connected_surfaces")
    public static void allFaceOrientationsConnectReciprocallyAndDisconnectOnRemoval(GameTestHelper helper) {
        var level = helper.getLevel();
        var cell = helper.absolutePos(new BlockPos(4, 4, 4));
        for (Direction normal : Direction.values()) {
            Direction support = normal.getOpposite();
            for (Direction tangent : AssimilationConnections.ports(normal)) {
                var next = cell.relative(tangent);
                try {
                    level.setBlockAndUpdate(cell.relative(support), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(next.relative(support), Blocks.STONE.defaultBlockState());
                    level.setBlockAndUpdate(cell, face(support));
                    helper.assertTrue(!AssimilationConnections.connects(level, cell, support, tangent),
                            "a plain support block is not a connected vein");
                    level.setBlockAndUpdate(next, face(support));
                    helper.assertTrue(AssimilationConnections.connects(level, cell, support, tangent)
                            && AssimilationConnections.connects(level, next, support, tangent.getOpposite()),
                            "flat growth must connect from both sides on " + normal + "/" + tangent);
                    int first = java.util.Arrays.asList(AssimilationConnections.ports(normal)).indexOf(tangent);
                    int opposite = java.util.Arrays.asList(AssimilationConnections.ports(normal)).indexOf(tangent.getOpposite());
                    helper.assertTrue(((AssimilationConnections.veinMask(level,cell,normal)>>>first)&1)
                            == ((AssimilationConnections.veinMask(level,next,normal)>>>opposite)&1),
                            "visual branches must agree at every shared flat edge");
                    level.removeBlock(next, false);
                    helper.assertTrue(!AssimilationConnections.connects(level, cell, support, tangent),
                            "the visual port must close immediately when its neighbour is removed");
                } finally {
                    for (var p : java.util.List.of(cell,next,cell.relative(support),next.relative(support))) level.removeBlock(p,false);
                }
            }
        }
        helper.succeed();
    }
    @GameTest(template="arena", batch="bloom_connected_corners")
    public static void growthFoldsIntoCornersAndWrapsAroundHostEdges(GameTestHelper helper) {
        var level = helper.getLevel(); var cell = helper.absolutePos(new BlockPos(4,4,4));
        var wrap = cell.below().north();
        try {
            level.setBlockAndUpdate(cell.below(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cell.north(), Blocks.STONE.defaultBlockState());
            level.setBlockAndUpdate(cell, face(Direction.DOWN).setValue(MultifaceBlock.getFaceProperty(Direction.NORTH),true));
            helper.assertTrue(AssimilationConnections.connects(level,cell,Direction.DOWN,Direction.NORTH)
                    && AssimilationConnections.connects(level,cell,Direction.NORTH,Direction.DOWN),
                    "two faces inside a corner must share a port");
            level.setBlockAndUpdate(cell,face(Direction.DOWN));
            level.removeBlock(cell.north(),false);
            level.setBlockAndUpdate(wrap,face(Direction.SOUTH));
            helper.assertTrue(AssimilationConnections.connects(level,cell,Direction.DOWN,Direction.NORTH)
                    && AssimilationConnections.connects(level,wrap,Direction.SOUTH,Direction.UP),
                    "growth on adjacent sides of the same host must wrap around its convex edge");
        } finally {
            for (var p : java.util.List.of(cell,wrap,cell.below(),cell.north())) level.removeBlock(p,false);
        }
        helper.succeed();
    }
}
