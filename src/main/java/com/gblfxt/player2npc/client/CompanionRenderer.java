package com.gblfxt.player2npc.client;

import com.gblfxt.player2npc.entity.CompanionEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidArmorModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.layers.HumanoidArmorLayer;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.resources.ResourceLocation;

public class CompanionRenderer extends HumanoidMobRenderer<CompanionEntity, CompanionModel> {

    // Fallback to Steve texture until dynamic skin loading is implemented
    private static final ResourceLocation STEVE_TEXTURE =
            ResourceLocation.withDefaultNamespace("textures/entity/player/wide/steve.png");

    public CompanionRenderer(EntityRendererProvider.Context context) {
        super(context, new CompanionModel(context.bakeLayer(CompanionModel.LAYER_LOCATION)), 0.5F);

        // Add armor layer
        this.addLayer(new HumanoidArmorLayer<>(
                this,
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_INNER_ARMOR)),
                new HumanoidArmorModel<>(context.bakeLayer(ModelLayers.PLAYER_OUTER_ARMOR)),
                context.getModelManager()
        ));

        // Add held item layer
        this.addLayer(new ItemInHandLayer<>(this, context.getItemInHandRenderer()));
    }

    @Override
    public ResourceLocation getTextureLocation(CompanionEntity entity) {
        // TODO: Implement dynamic skin loading from URL
        // For now, use Steve texture
        return STEVE_TEXTURE;
    }

    @Override
    public void render(CompanionEntity entity, float entityYaw, float partialTicks,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        // Set model properties based on entity state
        this.model.crouching = entity.isCrouching();
        this.model.young = entity.isBaby();

        super.render(entity, entityYaw, partialTicks, poseStack, buffer, packedLight);
    }

    @Override
    protected void scale(CompanionEntity entity, PoseStack poseStack, float partialTicks) {
        // Standard player size
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
