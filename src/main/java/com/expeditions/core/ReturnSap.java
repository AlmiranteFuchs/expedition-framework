package com.expeditions.core;

import com.expeditions.registry.ModDataComponents;
import com.expeditions.registry.ModFluids;
import net.neoforged.neoforge.fluids.FluidStack;

/**
 * Return sap carries the dimension its knot remembers as a fluid
 * component — one fluid type, many memories. Pipes and foreign tanks
 * move it like any fluid; the typing rides inside the stack data.
 */
public final class ReturnSap {
    private ReturnSap() { }

    public static String dimension(FluidStack stack) {
        return stack.getOrDefault(ModDataComponents.RETURN_DIMENSION.get(), "");
    }

    public static FluidStack of(String dimension, int amount) {
        var stack = new FluidStack(ModFluids.SAP.get(), amount);
        if (!dimension.isBlank()) {
            stack.set(ModDataComponents.RETURN_DIMENSION.get(), dimension);
        }
        return stack;
    }

    /** Same fluid carrying the same memory — different dimensions never mix. */
    public static boolean same(FluidStack a, FluidStack b) {
        return a.is(ModFluids.SAP.get()) && b.is(ModFluids.SAP.get())
                && dimension(a).equals(dimension(b));
    }
}
