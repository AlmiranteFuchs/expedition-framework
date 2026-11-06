package com.expeditions.block;

import com.expeditions.item.ReturnSapBucketItem;
import com.expeditions.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import org.jetbrains.annotations.Nullable;

/**
 * Sap poured into the world forgets where it came from — a blockstate
 * can't hold the memory. Scooping it back up rebinds the bucket to the
 * dimension it stands in, so unmarked sap always assumes local trace.
 */
public final class ReturnSapLiquidBlock extends LiquidBlock {
    public ReturnSapLiquidBlock(BaseFlowingFluid fluid, Properties properties) {
        super(fluid, properties);
    }

    @Override
    public ItemStack pickupBlock(@Nullable Player player, LevelAccessor level, BlockPos pos, BlockState state) {
        ItemStack picked = super.pickupBlock(player, level, pos, state);
        if (picked.is(ModItems.RETURN_SAP_BUCKET.get()) && level instanceof Level world) {
            ReturnSapBucketItem.bind(picked, world.dimension().location().toString());
        }
        return picked;
    }
}
