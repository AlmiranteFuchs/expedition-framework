package com.expeditions.block;

import com.expeditions.item.ReturnSapBucketItem;
import com.expeditions.registry.ModItems;
import com.expeditions.server.PlacedSap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jetbrains.annotations.Nullable;

/** A placed source retains its trace until the source is removed. */
public final class ReturnSapLiquidBlock extends LiquidBlock {
    public ReturnSapLiquidBlock(BaseFlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        String trace = level instanceof ServerLevel server && PlacedSap.isSource(state)
                ? PlacedSap.source(server, pos) : "";
        ItemStack picked = super.pickupBlock(player, level, pos, state);
        if (picked.is(ModItems.RETURN_SAP_BUCKET.get())) {
            ReturnSapBucketItem.bind(picked, trace);
        }
        return picked;
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState next, boolean moved) {
        if (level instanceof ServerLevel server && PlacedSap.isSource(state) && !PlacedSap.isSource(next)) {
            PlacedSap.forget(server, pos);
        }
        super.onRemove(state, level, pos, next, moved);
    }
}
