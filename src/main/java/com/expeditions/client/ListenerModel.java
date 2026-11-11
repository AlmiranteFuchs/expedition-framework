package com.expeditions.client;

import com.expeditions.entity.BloomListener;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/** Low bark carapace and two hinged mask plates; the amber throat appears during the warning. */
public final class ListenerModel extends HierarchicalModel<BloomListener> {
    public static final ModelLayerLocation LAYER = new ModelLayerLocation(
            ResourceLocation.fromNamespaceAndPath("expeditions", "bloom_listener"), "main");
    private final ModelPart root, head, leftMask, rightMask;

    public ListenerModel(ModelPart root) {
        this.root = root;
        head = root.getChild("head");
        leftMask = head.getChild("left_mask");
        rightMask = head.getChild("right_mask");
    }

    public static LayerDefinition createBodyLayer() {
        var mesh = new MeshDefinition();
        var root = mesh.getRoot();
        root.addOrReplaceChild("carapace", CubeListBuilder.create().texOffs(0, 0)
                .addBox(-4, -3, -3, 8, 5, 9).texOffs(0, 42)
                .addBox(-3, -5, -2, 6, 2, 7), PartPose.offsetAndRotation(0, 16, 2, -.28f, 0, 0));
        var head = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 17)
                .addBox(-5, -4, -2, 10, 7, 3).texOffs(0, 32)
                .addBox(-4, -3, -2.6f, 8, 6, 1), PartPose.offset(0, 13, -3));
        head.addOrReplaceChild("left_mask", CubeListBuilder.create().texOffs(28, 17)
                .addBox(-5, -4, -1, 5, 8, 1), PartPose.offsetAndRotation(0, 0, -2.8f, 0, -.08f, -.10f));
        head.addOrReplaceChild("right_mask", CubeListBuilder.create().texOffs(28, 17).mirror()
                .addBox(0, -4, -1, 5, 8, 1), PartPose.offsetAndRotation(0, 0, -2.8f, 0, .08f, .10f));
        for (int side : new int[]{-1, 1}) {
            root.addOrReplaceChild("front_" + side, CubeListBuilder.create().texOffs(36, 0)
                    .addBox(-1, 0, -1, 2, 7, 2), PartPose.offsetAndRotation(side * 4, 17, -2, -.12f, 0, side * -.20f));
            root.addOrReplaceChild("back_" + side, CubeListBuilder.create().texOffs(36, 0)
                    .addBox(-1, 0, -1, 2, 7, 2), PartPose.offsetAndRotation(side * 4, 17, 6, .2f, 0, side * -.24f));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    @Override public ModelPart root() { return root; }
    @Override public void setupAnim(BloomListener entity, float swing, float amount, float age, float yaw, float pitch) {
        root.getAllParts().forEach(ModelPart::resetPose);
        head.yRot = yaw * Mth.DEG_TO_RAD * .6f;
        head.xRot = pitch * Mth.DEG_TO_RAD * .35f;
        float open = entity.opening(age - entity.tickCount);
        leftMask.yRot = -.08f - open * 1.17f;
        rightMask.yRot = .08f + open * 1.17f;
        float tremor = Mth.sin(age * 2.8f) * (.012f + .025f * open);
        head.zRot = tremor;
        root.getChild("carapace").zRot = tremor * .7f;
        float stride = Mth.cos(swing) * amount * .3f;
        root.getChild("front_-1").xRot += stride;
        root.getChild("back_1").xRot += stride;
        root.getChild("front_1").xRot -= stride;
        root.getChild("back_-1").xRot -= stride;
    }
}
