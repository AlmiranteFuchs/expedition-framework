package com.expeditions.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Vector3f;

/** Charter instruments share a material language, never a party's progress state. */
public abstract class RouteMarkerBlock extends Block {
    public static final DustParticleOptions AMBER = new DustParticleOptions(new Vector3f(.72f, .43f, .16f), .65f);
    private final String model;

    protected RouteMarkerBlock(Properties properties, String model) {
        super(properties);
        this.model = model;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return FieldMachineShapes.shape(model, Direction.NORTH);
    }

    @Override
    protected VoxelShape getOcclusionShape(BlockState state, BlockGetter level, BlockPos pos) {
        return Shapes.empty();
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (random.nextInt(6) == 0) {
            level.addParticle(AMBER, pos.getX() + .5 + (random.nextDouble() - .5) * .16,
                    pos.getY() + .55, pos.getZ() + .5 + (random.nextDouble() - .5) * .16, 0, .008, 0);
        }
    }
}
