package com.expeditions.client;
import com.expeditions.block.ResonanceMachineBlock;
import com.expeditions.block.entity.ResonanceMachineBlockEntity;
import com.expeditions.core.ResonancePower.Kind;
import com.mojang.blaze3d.vertex.*;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.blockentity.*;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
/** Small world-lit knot and flexing ribs. No world edits, custom packets or rotating chunk meshes. */
public final class ResonanceRenderer implements BlockEntityRenderer<ResonanceMachineBlockEntity> {
    private static final ResourceLocation BONE=ResourceLocation.parse("expeditions:textures/block/machine_bone.png");
    public ResonanceRenderer(BlockEntityRendererProvider.Context context) { }
    @Override public void render(ResonanceMachineBlockEntity be,float partial,PoseStack pose,MultiBufferSource buffers,int light,int overlay){
        if(be.getLevel()==null || be.kind()==Kind.CONDENSER || !be.getBlockState().getValue(ResonanceMachineBlock.HAS_CORE))return;
        boolean working=be.getBlockState().getValue(ResonanceMachineBlock.ACTIVE);
        double time=be.getLevel().getGameTime()+partial;
        float breath=working?(float)Math.sin(time*(be.kind()==Kind.LIVING?.12:.04)):0;
        String skin=switch(be.kind()){case DORMANT->"machine_knot";case LIVING->"machine_heart";default->"machine_severed";};
        var texture=ResourceLocation.parse("expeditions:textures/block/"+skin+".png");
        pose.pushPose();pose.translate(.5,11/16.,.5);
        int angle=switch(be.getBlockState().getValue(ResonanceMachineBlock.FACING)){case EAST->90;case SOUTH->180;case WEST->270;default->0;};
        pose.mulPose(Axis.YP.rotationDegrees(-angle));
        if(be.kind()==Kind.DORMANT){
            float shift=breath*.012f;
            box(pose,buffers.getBuffer(RenderType.entityCutout(BONE)),-.25f+shift,-.25f,-.07f,-.20f+shift,.12f,.07f,light,overlay);
            box(pose,buffers.getBuffer(RenderType.entityCutout(BONE)),.20f-shift,-.25f,-.07f,.25f-shift,.12f,.07f,light,overlay);
        }
        if(be.kind()==Kind.LIVING){
            pose.translate(-.14,-.11,-.14);pose.scale(.28f,.28f,.28f);
            var specimen=com.expeditions.registry.ModBlocks.ASSIMILATION_HEART.get().defaultBlockState()
                    .setValue(com.expeditions.block.BloomSourceBlock.OPEN,com.expeditions.core.BloomShutter.stage(time,be.getBlockPos().asLong(),working));
            net.minecraft.client.Minecraft.getInstance().getBlockRenderer().renderSingleBlock(specimen,pose,buffers,light,overlay);
            pose.popPose();return;
        }
        pose.mulPose(Axis.YP.rotationDegrees(breath*8));pose.mulPose(Axis.ZP.rotationDegrees(45));
        float scale=1+breath*.04f;pose.scale(scale,scale,scale);
        box(pose,buffers.getBuffer(RenderType.entityCutout(texture)),-.09f,-.09f,-.075f,.09f,.09f,.075f,light,overlay);
        pose.popPose();
    }
    private static final int[][] CORNERS={{1,5,4,0},{7,3,2,6},{1,0,2,3},{4,5,7,6},{0,4,6,2},{5,1,3,7}};
    private static final Direction[] NORMALS={Direction.DOWN,Direction.UP,Direction.NORTH,Direction.SOUTH,Direction.WEST,Direction.EAST};
    private static final float[][] UV={{0,1},{1,1},{1,0},{0,0}};
    private static void box(PoseStack stack,VertexConsumer out,float a,float b,float c,float d,float e,float f,int light,int overlay){
        var pose=stack.last();
        for(int face=0;face<6;face++)for(int i=0;i<4;i++){
            int corner=CORNERS[face][i];var n=NORMALS[face];
            out.addVertex(pose,(corner&1)==0?a:d,(corner&2)==0?b:e,(corner&4)==0?c:f)
                    .setColor(1f,1f,1f,1f).setUv(UV[i][0],UV[i][1]).setOverlay(overlay).setLight(light).setNormal(pose,n.getStepX(),n.getStepY(),n.getStepZ());
        }
    }
}
