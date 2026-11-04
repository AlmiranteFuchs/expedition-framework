package com.expeditions.client;

import com.expeditions.entity.RootboundGuardian;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public final class GuardianRenderer extends MobRenderer<RootboundGuardian, GuardianModel> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(
            "expeditions", "textures/entity/rootbound_guardian.png");
    private static final RenderType GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
            "expeditions", "textures/entity/rootbound_guardian_glow.png"));
    public GuardianRenderer(EntityRendererProvider.Context context) {
        super(context, new GuardianModel(context.bakeLayer(GuardianModel.LAYER)), 0.75f);
        addLayer(new EyesLayer<RootboundGuardian, GuardianModel>(this) {
            @Override public RenderType renderType() { return GLOW; }
        });
    }
    @Override public ResourceLocation getTextureLocation(RootboundGuardian entity) { return SKIN; }
}
