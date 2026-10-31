package com.expeditions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.MultifaceBlock;

/** The same three surface paths used by vanilla MultifaceSpreader: flat, folded and wrapped. */
public final class AssimilationConnections {
    private AssimilationConnections() { }
    /** Ports are top/right/bottom/left in the renderer's canonical face UVs. */
    public static Direction[] ports(Direction normal) {
        return switch (normal) {
            case UP, DOWN -> new Direction[]{Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
            case NORTH -> new Direction[]{Direction.UP, Direction.WEST, Direction.DOWN, Direction.EAST};
            case SOUTH -> new Direction[]{Direction.UP, Direction.EAST, Direction.DOWN, Direction.WEST};
            case WEST -> new Direction[]{Direction.UP, Direction.NORTH, Direction.DOWN, Direction.SOUTH};
            case EAST -> new Direction[]{Direction.UP, Direction.SOUTH, Direction.DOWN, Direction.NORTH};
        };
    }
    public static boolean connects(BlockGetter level, BlockPos cell, Direction support, Direction tangent) {
        return covered(level, cell.relative(tangent), support)
                || covered(level, cell, tangent)
                || covered(level, cell.relative(support).relative(tangent), tangent.getOpposite());
    }
    private static boolean covered(BlockGetter level, BlockPos pos, Direction face) {
        var state = level.getBlockState(pos);
        return state.getBlock() instanceof AssimilationLayerBlock && MultifaceBlock.hasFace(state, face);
    }
    public static int mask(BlockGetter level, BlockPos cell, Direction normal) {
        int mask = 0;
        var ports = ports(normal);
        for (int i = 0; i < 4; i++) if (connects(level, cell, normal.getOpposite(), ports[i])) mask |= 1 << i;
        return mask;
    }

    /** Flat neighbours share an edge decision, preventing an all-four-way square lattice.
     * Folded/wrapped edges always continue around the corner. */
    public static int veinMask(BlockGetter level, BlockPos cell, Direction normal) {
        int mask = mask(level,cell,normal);
        var ports = ports(normal);
        for (int i=0;i<4;i++) {
            if ((mask & (1<<i)) == 0 || !covered(level,cell.relative(ports[i]),normal.getOpposite())) continue;
            long a = net.minecraft.util.Mth.getSeed(cell), b = net.minecraft.util.Mth.getSeed(cell.relative(ports[i]));
            long edge = a ^ b ^ (normal.ordinal()*0x9e3779b97f4a7c15L);
            edge = (edge ^ (edge>>>30))*0xbf58476d1ce4e5b9L;
            edge ^= edge>>>27;
            if ((edge & 255) >= 144) mask &= ~(1<<i);
        }
        return mask;
    }
}
