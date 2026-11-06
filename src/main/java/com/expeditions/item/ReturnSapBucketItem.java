package com.expeditions.item;

import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModFluids;
import com.expeditions.registry.ModItems;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandlerItem;
import net.neoforged.neoforge.fluids.capability.IFluidHandler.FluidAction;

/** A bucket of return sap; the stack's dimension component is the trace it can reclaim. */
public final class ReturnSapBucketItem extends BucketItem {
    public ReturnSapBucketItem(Properties p) { super(ModFluids.SAP.get(), p); }

    public static String source(ItemStack stack) {
        return stack.is(ModItems.RETURN_SAP_BUCKET.get())
                ? stack.getOrDefault(ModDataComponents.RETURN_DIMENSION.get(), "") : "";
    }

    public static void bind(ItemStack stack, String dimension) {
        if (!dimension.isBlank()) {
            stack.set(ModDataComponents.RETURN_DIMENSION.get(), dimension);
        }
    }

    @Override
    public Component getName(ItemStack stack) {
        return Component.translatable(source(stack).isBlank()
                ? "item.expeditions.return_sap_bucket" : "item.expeditions.return_sap_bucket.bound");
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flag) {
        String source = source(stack);
        lines.add(Component.translatable(
                source.isBlank() ? "tooltip.expeditions.sap_unbound" : "tooltip.expeditions.sap_bound",
                source).withStyle(ChatFormatting.GRAY));
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.registerItem(net.neoforged.neoforge.capabilities.Capabilities.FluidHandler.ITEM,
                (stack, context) -> new Wrapper(stack), ModItems.RETURN_SAP_BUCKET.get());
    }

    /** FluidBucketWrapper semantics with the dimension component riding the stored stack. */
    private static final class Wrapper implements IFluidHandlerItem {
        private ItemStack container;
        private FluidStack fluid;

        Wrapper(ItemStack stack) {
            container = stack.copyWithCount(1);
            fluid = com.expeditions.core.ReturnSap.of(source(stack), 1000);
        }

        @Override public ItemStack getContainer() { return container; }
        @Override public int getTanks() { return 1; }
        @Override public FluidStack getFluidInTank(int tank) { return fluid.copy(); }
        @Override public int getTankCapacity(int tank) { return 1000; }
        @Override public boolean isFluidValid(int tank, FluidStack stack) { return false; }

        @Override public int fill(FluidStack resource, FluidAction action) { return 0; }

        @Override public FluidStack drain(FluidStack resource, FluidAction action) {
            if (resource.isEmpty() || !com.expeditions.core.ReturnSap.same(resource, fluid)) {
                return FluidStack.EMPTY;
            }
            return drain(resource.getAmount(), action);
        }

        @Override public FluidStack drain(int maxDrain, FluidAction action) {
            int moved = Math.min(maxDrain, fluid.getAmount());
            if (moved <= 0) {
                return FluidStack.EMPTY;
            }
            var drained = com.expeditions.core.ReturnSap.of(
                    com.expeditions.core.ReturnSap.dimension(fluid), moved);
            if (action.execute()) {
                if (fluid.getAmount() - moved <= 0) {
                    container = new ItemStack(net.minecraft.world.item.Items.BUCKET);
                } else {
                    fluid.shrink(moved);
                }
            }
            return drained;
        }
    }
}
