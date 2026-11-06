package com.expeditions.item;

import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

/** The knot cut from the traveler's tether at extraction — it remembers the dimension it was tied in. */
public final class ReturnKnotItem extends Item {
    public ReturnKnotItem(Properties p) { super(p); }

    public static String source(ItemStack stack) {
        return stack.is(ModItems.RETURN_KNOT.get())
                ? stack.getOrDefault(ModDataComponents.RETURN_DIMENSION.get(), "") : "";
    }

    public static void bind(ItemStack stack, String dimension) {
        stack.set(ModDataComponents.RETURN_DIMENSION.get(), dimension);
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(source(stack).isBlank()
                ? "item.expeditions.return_knot" : "item.expeditions.return_knot.bound");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String source = source(stack);
        lines.add(Component.translatable(
                source.isBlank() ? "tooltip.expeditions.knot_unbound" : "tooltip.expeditions.knot_bound",
                source).withStyle(ChatFormatting.GRAY));
    }
}
