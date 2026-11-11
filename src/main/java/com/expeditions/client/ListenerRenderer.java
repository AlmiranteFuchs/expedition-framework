package com.expeditions.client;

import com.expeditions.entity.BloomListener;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.layers.EyesLayer;
import net.minecraft.resources.ResourceLocation;

public final class ListenerRenderer extends MobRenderer<BloomListener, ListenerModel> {
    private static final ResourceLocation SKIN = ResourceLocation.fromNamespaceAndPath(
            "expeditions", "textures/entity/bloom_listener.png");
    private static final RenderType GLOW = RenderType.eyes(ResourceLocation.fromNamespaceAndPath(
            "expeditions", "textures/entity/bloom_listener_glow.png"));
    public ListenerRenderer(EntityRendererProvider.Context context) {
        super(context, new ListenerModel(context.bakeLayer(ListenerModel.LAYER)), .45f);
        addLayer(new EyesLayer<BloomListener, ListenerModel>(this) {
            @Override public RenderType renderType() { return GLOW; }
        });
    }
    @Override public ResourceLocation getTextureLocation(BloomListener listener) { return SKIN; }
}
