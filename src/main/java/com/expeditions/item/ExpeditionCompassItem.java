package com.expeditions.item;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import java.util.List;

/**
 * Expedition compass: granted to each player when a run starts and removed
 * when the run ends. The needle target is driven entirely server-side via the
 * {@code LODESTONE_TRACKER} data component — the server retargets it to the
 * next checkpoint, then the exit, as the run progresses. It carries no
 * run logic of its own.
 */
public class ExpeditionCompassItem extends Item {
    public static final int ATTUNEMENT_TICKS = 24;
    public static final int ATTUNEMENT_COOLDOWN = 40;

    public ExpeditionCompassItem(Properties properties) {
        super(properties);
    }

    /** Shared tracker validation; the compass never invents a target across dimensions. */
    public static GlobalPos localTarget(ItemStack stack, Level level) {
        if (!(stack.getItem() instanceof ExpeditionCompassItem)) {
            return null;
        }
        var tracker = stack.get(DataComponents.LODESTONE_TRACKER);
        if (tracker == null || tracker.target().isEmpty()) {
            return null;
        }
        GlobalPos target = tracker.target().get();
        return target.dimension().equals(level.dimension()) ? target : null;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (localTarget(stack, level) == null || player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }
        player.startUsingItem(hand);
        player.getCooldowns().addCooldown(this, ATTUNEMENT_COOLDOWN);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return ATTUNEMENT_TICKS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context,
            List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.expeditions.compass_ember_hint")
                .withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.expeditions.compass_attune_hint")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
