package com.expeditions.block;

import com.expeditions.registry.ModBlocks;
import net.minecraft.core.*;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.*;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.*;

/** Shared live/cold shell. Shape and render geometry use the same native models. */
public abstract class BloomSourceBlock extends HorizontalDirectionalBlock {
    public static final IntegerProperty OPEN=IntegerProperty.create("open",0,4);
    protected BloomSourceBlock(Properties p){
        super(p);registerDefaultState(stateDefinition.any().setValue(FACING,Direction.NORTH).setValue(OPEN,0));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block,BlockState> builder){builder.add(FACING,OPEN);}
    @Override public BlockState getStateForPlacement(BlockPlaceContext c){return defaultBlockState().setValue(FACING,c.getHorizontalDirection().getOpposite());}
    @Override protected VoxelShape getShape(BlockState s,BlockGetter l,BlockPos p,CollisionContext context){
        String model=s.is(ModBlocks.DORMANT_HEART.get())?"bloom_shutter_dormant":"bloom_shutter_"+s.getValue(OPEN);
        return FieldMachineShapes.shape(model,s.getValue(FACING));
    }
    @Override protected VoxelShape getOcclusionShape(BlockState s,BlockGetter l,BlockPos p){return Shapes.empty();}
}
