package com.expeditions.item;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

public final class DisruptionCharmItem extends Item {
    public DisruptionCharmItem(Properties properties) { super(properties); }
    @Override public void appendHoverText(ItemStack stack,TooltipContext context,List<Component> lines,TooltipFlag flag) {
        lines.add(Component.translatable("item.expeditions.disruption_charm.equip").withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("item.expeditions.disruption_charm.protect").withStyle(ChatFormatting.GOLD));
        lines.add(Component.translatable("item.expeditions.disruption_charm.limit").withStyle(ChatFormatting.DARK_GRAY));
    }
}
