package com.expeditions.registry;

import com.expeditions.ExpeditionsMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.BaseFlowingFluid;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

public final class ModFluids {
    public static final DeferredRegister<FluidType> TYPES = DeferredRegister.create(NeoForgeRegistries.Keys.FLUID_TYPES, ExpeditionsMod.MOD_ID);
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, ExpeditionsMod.MOD_ID);
    public static final net.neoforged.neoforge.registries.DeferredHolder<FluidType, FluidType> SAP_TYPE =
            TYPES.register("return_sap", () -> new FluidType(FluidType.Properties.create()
                    .descriptionId("fluid.expeditions.return_sap").density(1150).viscosity(1700).canConvertToSource(false)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_FILL, net.minecraft.sounds.SoundEvents.BUCKET_FILL)
                    .sound(net.neoforged.neoforge.common.SoundActions.BUCKET_EMPTY, net.minecraft.sounds.SoundEvents.BUCKET_EMPTY)));
    public static final net.neoforged.neoforge.registries.DeferredHolder<Fluid, BaseFlowingFluid.Source> SAP =
            FLUIDS.register("return_sap", () -> new BaseFlowingFluid.Source(properties()));
    public static final net.neoforged.neoforge.registries.DeferredHolder<Fluid, BaseFlowingFluid.Flowing> FLOWING_SAP =
            FLUIDS.register("flowing_return_sap", () -> new BaseFlowingFluid.Flowing(properties()));

    private static BaseFlowingFluid.Properties properties() {
        return new BaseFlowingFluid.Properties(SAP_TYPE, SAP, FLOWING_SAP)
                .bucket(ModItems.RETURN_SAP_BUCKET).block(ModBlocks.RETURN_SAP).tickRate(12).slopeFindDistance(2);
    }

    private ModFluids() { }
}
