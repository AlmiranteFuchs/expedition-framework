package com.expeditions.block;

import com.expeditions.core.ExpeditionRun;
import com.expeditions.core.Waypoint;
import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModItems;
import com.expeditions.server.ExpeditionManager;
import com.expeditions.server.ExpeditionMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Expedition terminal: the in-world entry point to the expedition framework.
 *
 * <p>Right-clicking opens the expedition options menu (its start button
 * launches the run) when the player has no active run, or reports the current
 * run's progress otherwise. All state changes happen on the server.
 */
public class ExpeditionTerminalBlock extends Block {
    public static final net.minecraft.world.level.block.state.properties.DirectionProperty FACING =
            net.minecraft.world.level.block.state.properties.BlockStateProperties.HORIZONTAL_FACING;

    public ExpeditionTerminalBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(net.minecraft.world.level.block.state.StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(net.minecraft.world.item.context.BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected BlockState rotate(BlockState state, net.minecraft.world.level.block.Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected BlockState mirror(BlockState state, net.minecraft.world.level.block.Mirror mirror) {
        return rotate(state, mirror.getRotation(state.getValue(FACING)));
    }

    /**
     * Attunement: a sigil held against the terminal reads the progression
     * key it carries and grants it permanently. The item is never
     * consumed — the artifact IS the proof; the grant is idempotent.
     */
    @Override
    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state,
            Level level, BlockPos pos, Player player, InteractionHand hand,
            BlockHitResult hitResult) {
        if(stack.is(ModItems.SEVERANCE_CORE.get())){
            if(player instanceof ServerPlayer server){
                String dim=level.dimension().location().toString();
                boolean ready=ExpeditionManager.get(server.server).canAttuneCore(dim);
                if(ready)com.expeditions.item.SeveranceCoreItem.bind(stack,dim);
                server.playNotifySound(ready?com.expeditions.registry.ModSounds.TERMINAL_ACTIVATE.get()
                        :com.expeditions.registry.ModSounds.TERMINAL_REFUSE.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.0f);
                player.displayClientMessage(Component.translatable(ready?"machine.expeditions.core_attuned":"machine.expeditions.core_refused"),true);
                server.inventoryMenu.broadcastChanges();
            }
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!stack.is(ModItems.ATTUNEMENT_SIGIL.get())) {
            return super.useItemOn(stack, state, level, pos, player, hand, hitResult);
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            String key = stack.get(ModDataComponents.ATTUNEMENT_KEY.get());
            if (key == null || key.isBlank()) {
                serverPlayer.playNotifySound(com.expeditions.registry.ModSounds.TERMINAL_REFUSE.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.0f);
                serverPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.attunement_inert"));
            } else if (ExpeditionManager.get(level.getServer())
                    .grantUnlock(serverPlayer.getUUID(), key)) {
                serverPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.attuned", key));
            } else {
                serverPlayer.playNotifySound(com.expeditions.registry.ModSounds.TERMINAL_REFUSE.get(),
                        net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.0f);
                serverPlayer.sendSystemMessage(Component.translatable(
                        "message.expeditions.already_attuned", key));
            }
        }
        return ItemInteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            ExpeditionManager manager = ExpeditionManager.get(level.getServer());
            com.expeditions.server.JournalDiscovery.unlock(serverPlayer,
                    com.expeditions.core.JournalChapter.TERMINAL);
            // Apply the recovery policy on interaction too: a run whose world
            // state vanished is failed closed instead of trapping the player.
            manager.resumeOrFail(serverPlayer).ifPresentOrElse(
                    run -> reportProgress(serverPlayer, run),
                    // The terminal's job is configuring the expedition: any
                    // click opens the options menu; its start button launches.
                    () -> {
                        serverPlayer.playNotifySound(com.expeditions.registry.ModSounds.TERMINAL_ACTIVATE.get(),
                                net.minecraft.sounds.SoundSource.BLOCKS,.6f,1.0f);
                        serverPlayer.openMenu(new SimpleMenuProvider(
                            (id, inventory, p) -> new ExpeditionMenu(
                                    id, inventory, manager, pos,
                                    level.dimension().location().toString()),
                            Component.translatable("gui.expeditions.terminal")));
                    });
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    private static void reportProgress(ServerPlayer player, ExpeditionRun run) {
        run.nextCheckpoint().ifPresentOrElse(
                next -> player.sendSystemMessage(Component.translatable(
                        "message.expeditions.next_checkpoint",
                        run.completedCheckpoints() + 1, run.totalCheckpoints(), format(next))),
                () -> player.sendSystemMessage(Component.translatable(
                        "message.expeditions.all_checkpoints", format(run.exit()))));
    }

    static String format(Waypoint w) {
        return w.x() + ", " + w.y() + ", " + w.z();
    }    @Override protected net.minecraft.world.phys.shapes.VoxelShape getShape(BlockState state,net.minecraft.world.level.BlockGetter level,BlockPos pos,net.minecraft.world.phys.shapes.CollisionContext context){
        return FieldMachineShapes.shape("expedition_terminal",state.getValue(FACING));
    }

}
