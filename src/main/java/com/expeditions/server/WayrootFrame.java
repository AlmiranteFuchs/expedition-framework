package com.expeditions.server;

import com.expeditions.block.ExpeditionPortalBlock;
import com.expeditions.block.ExpeditionTerminalBlock;
import com.expeditions.registry.ModBlocks;
import com.expeditions.server.dimension.DestinationChecks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.RotatedPillarBlock;

/** The collar binds a Wayroot to the player-built frame on its right. */
public final class WayrootFrame {
    public enum Condition {
        UNBOUND, READY, TIMBER, FLOOR, BLOCKED, BUSY, MISALIGNED;

        public boolean canOpen() { return this == UNBOUND || this == READY; }
        public String messageKey() { return "gui.expeditions.wayroot_frame_" + name().toLowerCase(java.util.Locale.ROOT); }
    }

    private WayrootFrame() { }

    public static BlockPos opening(BlockPos desk, Direction front) {
        return desk.relative(front.getCounterClockWise(), 2);
    }

    public static Condition inspect(ServerLevel level, BlockPos desk) {
        var state = level.getBlockState(desk);
        if (!state.is(ModBlocks.EXPEDITION_TERMINAL.get())) return Condition.UNBOUND;
        var front = state.getValue(ExpeditionTerminalBlock.FACING);
        var portal = boundOpening(level, desk, front);
        if (!level.getBlockState(portal.above(2)).is(ModBlocks.GRAFT_COLLAR.get())) {
            for (int side = -3; side <= 3; side++) for (int height = 1; height <= 3; height++) {
                if (level.getBlockState(desk.relative(front.getClockWise(), side).above(height))
                        .is(ModBlocks.GRAFT_COLLAR.get())) return Condition.MISALIGNED;
            }
            return Condition.UNBOUND;
        }
        return inspectOpening(level, portal, front.getClockWise().getAxis(), false);
    }

    public static BlockPos boundOpening(ServerLevel level, BlockPos desk, Direction front) {
        var right = opening(desk, front);
        var left = desk.relative(front.getClockWise(), 2);
        return !level.getBlockState(right.above(2)).is(ModBlocks.GRAFT_COLLAR.get())
                && level.getBlockState(left.above(2)).is(ModBlocks.GRAFT_COLLAR.get()) ? left : right;
    }

    public static boolean activeIntact(ServerLevel level, BlockPos desk, BlockPos portal) {
        var state = level.getBlockState(desk);
        if (!state.is(ModBlocks.EXPEDITION_TERMINAL.get())) return false;
        var front = state.getValue(ExpeditionTerminalBlock.FACING);
        return (opening(desk, front).equals(portal) || desk.relative(front.getClockWise(), 2).equals(portal))
                && inspectOpening(level, portal, front.getClockWise().getAxis(), true) == Condition.READY;
    }

    private static Condition inspectOpening(ServerLevel level, BlockPos opening, Direction.Axis axis, boolean active) {
        if (!level.getBlockState(opening.above(2)).is(ModBlocks.GRAFT_COLLAR.get())) return Condition.TIMBER;
        for (int side : new int[] {-1, 1}) {
            for (int height = 0; height < 3; height++) {
                var post = level.getBlockState(opening.offset(axis == Direction.Axis.X ? side : 0,
                        height, axis == Direction.Axis.Z ? side : 0));
                if (!post.is(ModBlocks.GRAFT_POST.get())
                        || post.getValue(RotatedPillarBlock.AXIS) != Direction.Axis.Y) return Condition.TIMBER;
            }
        }
        if (!DestinationChecks.isSturdyFloor(level, opening.below())
                || DestinationChecks.isHazard(level, opening.below())) return Condition.FLOOR;
        var lower = level.getBlockState(opening);
        if (!active && lower.is(ModBlocks.EXPEDITION_PORTAL.get())) return Condition.BUSY;
        boolean lowerClear = active ? lower.is(ModBlocks.EXPEDITION_PORTAL.get())
                && lower.getValue(ExpeditionPortalBlock.FRAMED) && lower.getValue(ExpeditionPortalBlock.AXIS) == axis
                : lower.isAir();
        return lowerClear && level.getBlockState(opening.above()).isAir() ? Condition.READY : Condition.BLOCKED;
    }
}
