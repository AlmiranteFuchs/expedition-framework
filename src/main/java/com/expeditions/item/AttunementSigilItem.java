package com.expeditions.item;

import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Crouching bypasses ordinary block use, so the held sigil owns that interaction too. */
public final class AttunementSigilItem extends Item {
    public AttunementSigilItem(Properties p){super(p);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();var level=context.getLevel();
        if(player==null||!player.isShiftKeyDown()||!(level.getBlockEntity(context.getClickedPos()) instanceof ResonanceMachineBlockEntity be))return InteractionResult.PASS;
        if(!level.isClientSide){
            boolean changed=be.toggleBreach();
            player.displayClientMessage(Component.translatable(changed?"machine.expeditions.breach_changed":"machine.expeditions.breach_refused"),true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
