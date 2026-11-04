package com.expeditions.client;

import com.expeditions.block.PaleChestBlock;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.ChestBlock;

/** Renders the existing pale chest mesh around its rear hinge using vanilla lid state. */
public final class PaleChestRenderer implements BlockEntityRenderer<PaleChestBlock.Storage> {
    static final ModelResourceLocation BODY = model("pale_chest_body");
    static final ModelResourceLocation LID = model("pale_chest_lid");

    public PaleChestRenderer(BlockEntityRendererProvider.Context context) { }

    private static ModelResourceLocation model(String name) {
        return ModelResourceLocation.standalone(ResourceLocation.fromNamespaceAndPath("expeditions", "block/" + name));
    }

    @Override
    public void render(PaleChestBlock.Storage chest, float partialTick, PoseStack pose,
            MultiBufferSource buffers, int light, int overlay) {
        var client = Minecraft.getInstance();
        var models = client.getModelManager();
        var renderer = client.getBlockRenderer().getModelRenderer();
        var vertices = buffers.getBuffer(Sheets.cutoutBlockSheet());
        var state = chest.getBlockState();
        pose.pushPose();
        pose.translate(.5, 0, .5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - state.getValue(ChestBlock.FACING).toYRot()));
        pose.translate(-.5, 0, -.5);
        renderer.renderModel(pose.last(), vertices, null, models.getModel(BODY), 1, 1, 1, light, overlay);
        float closed = 1 - chest.getOpenNess(partialTick);
        pose.translate(0, 9.0 / 16, 15.0 / 16);
        pose.mulPose(Axis.XP.rotationDegrees(90 * (1 - closed * closed * closed)));
        pose.translate(0, -9.0 / 16, -15.0 / 16);
        renderer.renderModel(pose.last(), vertices, null, models.getModel(LID), 1, 1, 1, light, overlay);
        pose.popPose();
    }
}
