package com.expeditions.client;

import com.expeditions.entity.RootboundGuardian;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Original articulated heartwood effigy; UV layout lives in gen_guardian_texture.py. */
public final class GuardianModel extends HierarchicalModel<RootboundGuardian> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("expeditions", "rootbound_guardian"), "main");
    private final ModelPart root, body, head, leftArm, rightArm, leftLeg, rightLeg;
    public GuardianModel(ModelPart root) {
        this.root = root;
        body = root.getChild("body"); head = root.getChild("head");
        leftArm = root.getChild("left_arm"); rightArm = root.getChild("right_arm");
        leftLeg = root.getChild("left_leg"); rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        var body = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-6, -16, -3, 12, 18, 6).texOffs(0, 44)
                .addBox(-7, -14, -4, 14, 3, 8).texOffs(0, 56)
                .addBox(-2, -10, -4, 4, 6, 1), PartPose.offset(0, -4, 0));
        body.addOrReplaceChild("root_rib", CubeListBuilder.create().texOffs(54, 16)
                .addBox(-1, 0, -1, 2, 8, 2), PartPose.offsetAndRotation(-4, -8, -3, 0, 0, -0.4f));
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 24)
                .addBox(-5, -10, -4, 10, 10, 8), PartPose.offset(0, -20, 0));
        head.addOrReplaceChild("crown_left", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-1, -10, -1, 2, 10, 2), PartPose.offsetAndRotation(-4, -8, 0, 0, 0, -0.55f));
        head.addOrReplaceChild("crown_right", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-1, -11, -1, 2, 11, 2), PartPose.offsetAndRotation(4, -9, 1, 0.15f, 0, 0.48f));
        head.addOrReplaceChild("crown_center", CubeListBuilder.create().texOffs(54, 0)
                .addBox(-1, -8, -1, 2, 8, 2), PartPose.offsetAndRotation(0, -10, 2, -0.3f, 0, 0.12f));
        root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(36, 0)
                .addBox(-2, -2, -2, 4, 25, 4).texOffs(54, 16)
                .addBox(-2, 23, -2, 2, 8, 2), PartPose.offset(-8, -18, 0));
        root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(36, 0).mirror()
                .addBox(-2, -2, -2, 4, 25, 4).texOffs(54, 16)
                .addBox(0, 23, -2, 2, 8, 2), PartPose.offset(8, -17, 0));
        root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(36, 30)
                .addBox(-2, 0, -2, 4, 26, 4), PartPose.offset(-3, -2, 0));
        root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(36, 30).mirror()
                .addBox(-2, 0, -2, 4, 26, 4), PartPose.offset(3, -2, 0));
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(RootboundGuardian entity, float limbSwing, float limbAmount,
            float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD;
        head.xRot = pitch * Mth.DEG_TO_RAD * 0.5f;
        head.zRot = Mth.sin(age * 0.035f) * 0.045f;
        leftLeg.xRot = Mth.cos(limbSwing * 0.65f) * 0.55f * limbAmount;
        rightLeg.xRot = Mth.cos(limbSwing * 0.65f + Mth.PI) * 0.55f * limbAmount;
        leftArm.xRot = rightLeg.xRot * 0.65f;
        rightArm.xRot = leftLeg.xRot * 0.65f;
        leftArm.zRot = 0.1f + Mth.sin(age * 0.06f) * 0.04f;
        rightArm.zRot = -0.1f - Mth.sin(age * 0.06f) * 0.04f;
        if (entity.windupTicks() > 0) {
            float lift = Math.min(1, (entity.attack().warningTicks() - entity.windupTicks() + 1) / 12f);
            switch (entity.attack()) {
                case SWEEP -> {
                    leftArm.yRot = -0.85f * lift;
                    rightArm.yRot = 0.85f * lift;
                    leftArm.xRot = rightArm.xRot = -1.2f * lift;
                }
                case ROOT_BURST -> {
                    rightArm.xRot = -2.1f * lift;
                    body.xRot = 0.22f * lift;
                }
                case ROOT_RING -> {
                    leftArm.xRot = rightArm.xRot = -2.6f * lift;
                    head.xRot -= 0.28f * lift;
                }
            }
        }
        float tremor = Mth.sin(age * 2.55f) * (0.55f + 0.45f * Mth.sin(age * 0.42f));
        float strength = entity.enraged() ? 0.13f : 0.085f;
        body.zRot += tremor * strength;
        head.zRot += Mth.sin(age * 3.2f) * strength;
        leftArm.zRot += tremor * strength;
        rightArm.zRot -= tremor * strength;
    }
}
