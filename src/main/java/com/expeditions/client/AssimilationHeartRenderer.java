package com.expeditions.client;

import com.expeditions.block.BloomSourceBlock;
import com.expeditions.block.entity.AssimilationHeartBlockEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.resources.ResourceLocation;

/** A narrow amber resin seam inside the shutters; no surrounding cube or halo. */
public final class AssimilationHeartRenderer implements BlockEntityRenderer<AssimilationHeartBlockEntity> {
    private static final float[][] CORNERS={{.375f,.157f,.375f,0,0},{.375f,.157f,.625f,0,1},{.625f,.157f,.625f,1,1},{.625f,.157f,.375f,1,0}};
    private static final ResourceLocation SEAM=ResourceLocation.parse("expeditions:textures/block/bloom_seam.png");
    public AssimilationHeartRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(AssimilationHeartBlockEntity be,float partial,PoseStack stack,MultiBufferSource buffers,int light,int overlay){
        int opening=be.getBlockState().getValue(BloomSourceBlock.OPEN);
        if(be.getLevel()==null || opening==0)return;
        float strength=.25f+.1f*opening;
        stack.pushPose();stack.translate(.5,0,.5);
        int rotation=switch(be.getBlockState().getValue(BloomSourceBlock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        stack.mulPose(com.mojang.math.Axis.YP.rotationDegrees(-rotation));stack.translate(-.5,0,-.5);
        var out=buffers.getBuffer(RenderType.eyes(SEAM));var pose=stack.last();
        // Black transparent texels remain black in the additive pass; only resin pixels emit.
        for(var p:CORNERS)out.addVertex(pose,p[0],p[1],p[2]).setColor(strength,strength,strength,1f).setUv(p[3],p[4])
                .setOverlay(overlay).setLight(15728880).setNormal(pose,0,1,0);
        stack.popPose();
    }
}
